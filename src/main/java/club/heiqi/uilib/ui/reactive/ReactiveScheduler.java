package club.heiqi.uilib.ui.reactive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中央事务调度器：signal 写入的唯一收口（I2, I9，信条四）。
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li>{@link Signal#set(Object)} 调用 {@link #queueWrite} 将写入排入队列</li>
 *   <li>宿主帧循环调用 {@link #flush()}，统一应用写入并重跑所有脏 effect</li>
 * </ol>
 *
 * <p>I9 保证：一帧内多次写入合并为一次刷新，不逐次触发重排。</p>
 *
 * <p><b>信条四（中央事务）</b>：每次 {@link #flush()} 把本帧所有 signal 写入合并为一个原子事务记入
 * {@link TransactionLog}（默认开启的有界环形缓冲）。这换来：① 批处理（已有）；② {@link #undo()}/{@link #redo()}
 * 游标时间旅行；③ 单一审计路径——日志永远能回答「谁、何时、因何改了它」。</p>
 */
public final class ReactiveScheduler {

    private static final ReactiveScheduler INSTANCE = new ReactiveScheduler();

    /** 全局单例入口。 */
    public static ReactiveScheduler get() { return INSTANCE; }

    /**
     * 待应用的写入表：key 为 signal，value 为本帧对该 signal 的最后一次写入值。
     *
     * <p>用 {@link LinkedHashMap} 而非 FIFO 队列：① 同一 signal 同帧多次写只保留最后一次值
     * （put 同 key 覆盖值、不改插入顺序位置）；② 迭代顺序 = 各 signal 首次写入顺序，
     * 保证 apply 顺序稳定。去重不在此处做，而是在 flush 阶段1 对比「帧初值」与「合并终值」，
     * 这样「同帧 set 到中间值再 set 回帧初值」也能被正确吸收为「无净变化」。</p>
     */
    private final LinkedHashMap<Signal<?>, Object> pendingWrites = new LinkedHashMap<>();
    /** 已注册的 effect 列表（注册顺序即粗略拓扑序）。 */
    private final List<Effect> effects = new ArrayList<>();
    /** 中央事务日志（信条四：审计 + 时间旅行）。 */
    private final TransactionLog log = new TransactionLog();
    /** 下一次 flush 提交事务时附带的标签（一次性，提交后清空）。 */
    private String pendingLabel = null;
    private boolean flushing = false;

    /** 单次 flush 双通道交替到不动点的最大迭代轮数，超过判为 effect 循环依赖（防死循环）。 */
    private static final int MAX_FLUSH_PASSES = 1000;

    private ReactiveScheduler() {}

    /**
     * 由 {@link Signal#set(Object)} 调用，登记本帧对某 signal 的待应用写入。
     *
     * <p>同一 signal 同帧多次写入，{@link LinkedHashMap#put} 覆盖旧值、保留首次插入位置——
     * 即「只留最后一次写入值，apply 顺序不变」。不在此处做相等去重（见 {@link #flush()} 阶段1）。</p>
     *
     * @param signal 目标 signal
     * @param value  待写入的新值
     */
    <T> void queueWrite(Signal<T> signal, T value) {
        pendingWrites.put(signal, value);
    }

    void registerEffect(Effect e) { effects.add(e); }
    void unregisterEffect(Effect e) { effects.remove(e); }

    /**
     * 当前已注册（未 dispose）的 effect 数量。<b>仅供测试探针</b>断言 Owner 回收是否泄漏，
     * 不用于业务代码——业务不应依赖调度器内部计数。
     *
     * @return 当前注册的 effect 数
     */
    int registeredEffectCount() { return effects.size(); }

    /** 中央事务日志（信条四：审计路径 + 时间旅行的事实源）。 */
    public TransactionLog transactionLog() { return log; }

    /**
     * 为下一次 {@link #flush()} 提交的事务附加标签（审计：因何而改）。一次性，提交后自动清空。
     *
     * @param label 业务语义标签，如 {@code "search.query"}；{@code null} 表示不带标签
     */
    public void labelNextTransaction(String label) { this.pendingLabel = label; }

    /**
     * 帧末批量刷新：<b>drain-writes 与 run-effects 双通道交替推进到不动点</b>，一帧汇总为一个事务。
     *
     * <p>每一轮迭代执行两步：</p>
     * <ol>
     *   <li>{@link #drainPendingWrites(Map, Map)}：快照并清空 {@link #pendingWrites}，对每个 signal
     *       对比「当前现值」与「待应用值」，仅净变化才 {@link Signal#applyAndNotify}（apply + 标脏订阅者），
     *       并把本帧首次出现的 before 累积到 {@code firstBefore}、本帧覆盖性 after 累积到 {@code lastAfter}
     *       （多轮合并，守 I9 一帧一事务）</li>
     *   <li>{@link #runDirtyEffectsOneSweep()}：按注册顺序扫描一遍 effects，重跑所有 isDirty() 的</li>
     * </ol>
     *
     * <p>两步都无进展（既无净变化写入、也无脏 effect）即到达不动点，退出循环。一帧内 drain 写入和
     * effect 内 set 产生的写入都经 {@link #queueWrite} → {@link #pendingWrites}（再无任何绕过队列的路径），
     * 保证 effect 内 {@link Signal#set} 也能在<b>同一次 flush</b> 内被 drain、订阅者被 markDirty、
     * 下游 effect 在紧接的 sweep 内重跑——无需 {@code setImmediate} 这种绕过调度器的同步写入（守 I2）。</p>
     *
     * <p><b>不动点收敛保证</b>：① 相等去重——同值 set 不 apply、不 markDirty；{@link Computed} 的记忆化
     * 同款机制使其输出无变化时不向下游传播。② {@link #MAX_FLUSH_PASSES} 上限——超出抛
     * {@link IllegalStateException}，判定 effect 循环依赖。两者共同保证有限步内收敛。</p>
     *
     * <p><b>一帧一事务</b>（守 I9）：多轮 drain 的净变化在 {@code firstBefore}/{@code lastAfter} 中累积，
     * 循环结束统一提交为<b>一个</b> {@link TransactionLog} 条目；同一 signal 跨多轮 set 中间值再回帧初值
     * 的抖动会被 {@link #commitTransaction(Map, Map)} 的相等去重吸收为「无净变化、不入日志」。
     * {@link Computed} 的派生值不入日志（其值可由源 signal 重放后自动重算）。日志关闭时本段零额外开销。</p>
     *
     * <p><b>历史</b>：原实现把 flush 分两阶段——阶段1 一次性 drain 后清空 {@link #pendingWrites}、阶段2 只
     * 扫 markDirty 通道（{@code runEffectsToFixpoint}）而不再 drain {@link #pendingWrites}。这导致 effect 内
     * {@link Signal#set}（写进 {@link #pendingWrites}）的写入要等下次 flush 才生效，下游延迟一帧——
     * 违反「中央事务应在帧内消费完所有写入」的语义（I2）。为绕过此缺口曾引入 {@code Signal.setImmediate}
     * 直接 {@link Signal#applyAndNotify} 同步刷新，但 {@code applyAndNotify} 不经队列/日志，破坏 I2 单一收口。
     * 本方法改为双通道交替到不动点后，{@code setImmediate} 已撤回，effect 内 {@code set} 即同帧生效。</p>
     *
     * <p>可重入保护：flush 过程中不允许递归调用。</p>
     */
    public void flush() {
        if (flushing) return;
        flushing = true;
        try {
            // 一帧一个事务：firstBefore/lastAfter 跨多轮 drain 累积，循环结束后统一合并提交（守 I9）
            Map<Signal<?>, Object> firstBefore = new LinkedHashMap<>();
            Map<Signal<?>, Object> lastAfter = new LinkedHashMap<>();
            int pass = 0;
            while (true) {
                if (++pass > MAX_FLUSH_PASSES) {
                    throw new IllegalStateException(
                            "响应式 flush 超过 " + MAX_FLUSH_PASSES + " 轮仍未收敛，疑似 effect 循环依赖");
                }
                // 两个通道分别赋值后再合判，勿写 `drain() || sweep()`——短路会跳过 sweep
                boolean applied = drainPendingWrites(firstBefore, lastAfter);
                boolean ranAny = runDirtyEffectsOneSweep();
                if (!applied && !ranAny) break;       // 双通道均无进展 = 不动点
            }
            commitTransaction(firstBefore, lastAfter);
        } finally {
            flushing = false;
        }
    }

    /**
     * 单轮 drain：快照并清空 {@link #pendingWrites}，对每个 signal 对比「当前现值」与「待应用值」，
     * 仅净变化才 {@link Signal#applyAndNotify}（apply + 标脏订阅者），同时把本帧首次出现的 before 累积到
     * {@code firstBefore}（{@code putIfAbsent} 保留最早的帧初值）、本帧覆盖性 after 累积到 {@code lastAfter}。
     *
     * <p>累积语义对事务合并至关重要：同一 signal 跨多轮 drain 的中间值会被覆盖，最终只对比
     * 「本帧开始时的现值」（firstBefore）与「本帧最后一轮的终值」（lastAfter），净变化为零的不入事务——
     * 这就是原阶段1 「set 中间值再回帧初值」抖动去重能力在多轮版本下的等价物。</p>
     *
     * @param firstBefore 跨轮累积的「首次进入队列前的现值」表（调用方传入，本方法 putIfAbsent 写入）
     * @param lastAfter   跨轮累积的「最近一次 apply 的终值」表（调用方传入，本方法 覆盖写入）
     * @return 本轮是否发生 apply（存在净变化）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean drainPendingWrites(Map<Signal<?>, Object> firstBefore, Map<Signal<?>, Object> lastAfter) {
        if (pendingWrites.isEmpty()) {
            return false;
        }
        Map<Signal<?>, Object> snapshot = new LinkedHashMap<>(pendingWrites);
        pendingWrites.clear();
        boolean applied = false;
        for (Map.Entry<Signal<?>, Object> e : snapshot.entrySet()) {
            Signal signal = (Signal) e.getKey();
            Object after = e.getValue();
            Object before = signal.peek();          // 当前现值（上一轮 drain 后或帧初值）
            if (Objects.equals(before, after)) {
                continue;                           // 无净变化：不 apply、不 markDirty、不累积
            }
            firstBefore.putIfAbsent(signal, before); // 锁定本帧首次的帧初值
            lastAfter.put(signal, after);            // 每轮覆盖：最终留下最后一轮的终值
            signal.applyAndNotify(after);           // apply + 标脏订阅者
            applied = true;
        }
        return applied;
    }

    /**
     * 单轮扫描：按注册顺序跑一遍 dirty effect。新建/重新标脏的 effect 在下一轮被纳入。
     *
     * <p>不再自带 while——主循环在 {@link #flush()} 中由 drain/sweep 两通道交替推进。</p>
     *
     * @return 本轮是否跑过任一 effect
     */
    private boolean runDirtyEffectsOneSweep() {
        boolean ranAny = false;
        for (Effect e : new ArrayList<>(effects)) {
            if (e.isDirty()) {
                e.run();
                ranAny = true;
            }
        }
        return ranAny;
    }

    /**
     * 由 {@code firstBefore}/{@code lastAfter} 合并生成单条事务：跳过 before==after（无净变化）的，
     * 仅记真正发生净变化的源 signal。日志关闭时本方法零额外开销，仅清空一次性 label。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void commitTransaction(Map<Signal<?>, Object> firstBefore, Map<Signal<?>, Object> lastAfter) {
        if (!log.isEnabled()) {
            pendingLabel = null;
            return;
        }
        List<TransactionLog.Entry> entries = new ArrayList<TransactionLog.Entry>(lastAfter.size());
        for (Map.Entry<Signal<?>, Object> e : lastAfter.entrySet()) {
            Signal signal = (Signal) e.getKey();
            Object after = e.getValue();
            Object before = firstBefore.get(signal);
            if (Objects.equals(before, after)) {
                continue;                           // 多轮抖动回帧初值：净变化为零，不入日志
            }
            entries.add(new TransactionLog.Entry(signal, before, after));
        }
        if (!entries.isEmpty()) {
            log.commit(System.currentTimeMillis(), pendingLabel, entries);
        }
        pendingLabel = null;
    }

    /**
     * 时间旅行·撤销（信条四②）：游标后退一格，把上一个已应用事务的所有源 signal 回退到 {@code before} 值，
     * 并重跑受影响的 effect/computed。被撤销的事务保留在日志中可 {@link #redo()}。
     *
     * <p>撤销本身<b>不产生新事务</b>（直接应用、绕过队列与日志），是纯导航操作。{@link Computed} 派生值
     * 由源 signal 回退后自动重算，无需单独记录。</p>
     *
     * @return 是否执行了撤销（无可撤销事务时返回 {@code false}）
     */
    public boolean undo() {
        if (flushing) return false;
        TransactionLog.Transaction txn = log.stepBack();
        if (txn == null) return false;
        applyAndRerun(txn, /*useBefore=*/true);
        return true;
    }

    /**
     * 时间旅行·重做（信条四②）：游标前进一格，把下一个事务的所有源 signal 重新应用到 {@code after} 值，
     * 并重跑受影响的 effect/computed。
     *
     * @return 是否执行了重做（无可重做事务时返回 {@code false}）
     */
    public boolean redo() {
        if (flushing) return false;
        TransactionLog.Transaction txn = log.stepForward();
        if (txn == null) return false;
        applyAndRerun(txn, /*useBefore=*/false);
        return true;
    }

    /** 直接应用事务的 before/after 值并按双通道交替推进到不动点（供 undo/redo 复用）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyAndRerun(TransactionLog.Transaction txn, boolean useBefore) {
        flushing = true;
        try {
            // 直接 apply 事务的 before/after 值（绕过队列：undo/redo 是纯导航操作，不产生新事务）
            for (TransactionLog.Entry entry : txn.entries()) {
                Object value = useBefore ? entry.before() : entry.after();
                ((Signal) entry.signal()).applyAndNotify(value);
            }
            // 双通道交替到不动点：事务直接 apply 可能触发 effect，effect 内 set 进 pendingWrites
            // 仍能在本次 applyAndRerun 内同帧 drain 生效（与 flush 同款契约）
            Map<Signal<?>, Object> firstBefore = new LinkedHashMap<>();
            Map<Signal<?>, Object> lastAfter = new LinkedHashMap<>();
            int pass = 0;
            while (true) {
                if (++pass > MAX_FLUSH_PASSES) {
                    throw new IllegalStateException(
                            "响应式 applyAndRerun 超过 " + MAX_FLUSH_PASSES + " 轮仍未收敛，疑似 effect 循环依赖");
                }
                boolean applied = drainPendingWrites(firstBefore, lastAfter);
                boolean ranAny = runDirtyEffectsOneSweep();
                if (!applied && !ranAny) break;
            }
            // undo/redo 本身不产生新事务：effect 内 set 产生的累积净变化不提交日志
            pendingLabel = null;
        } finally {
            flushing = false;
        }
    }

    /**
     * 重置所有状态（仅用于单元测试的 setUp/tearDown）。
     */
    public void reset() {
        pendingWrites.clear();
        effects.clear();
        log.resetForTest();
        pendingLabel = null;
        flushing = false;
    }
}
