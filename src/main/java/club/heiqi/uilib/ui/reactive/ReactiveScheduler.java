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

    /** 单次 flush 阶段2 的最大迭代轮数，超过判为 effect 循环依赖（防死循环）。 */
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

    /** 中央事务日志（信条四：审计路径 + 时间旅行的事实源）。 */
    public TransactionLog transactionLog() { return log; }

    /**
     * 为下一次 {@link #flush()} 提交的事务附加标签（审计：因何而改）。一次性，提交后自动清空。
     *
     * @param label 业务语义标签，如 {@code "search.query"}；{@code null} 表示不带标签
     */
    public void labelNextTransaction(String label) { this.pendingLabel = label; }

    /**
     * 帧末批量刷新：
     * <ol>
     *   <li>应用所有待写入，触发脏标记传播，并把本帧写入合并记入一个事务日志条目</li>
     *   <li>按注册顺序重跑脏 effect，<b>迭代到不动点</b></li>
     * </ol>
     *
     * <p>阶段2 迭代到不动点的意义：flush 期间新建或被重新标脏的 effect（典型来源是
     * {@code mount}/{@code forEach} 在协调 effect 内动态挂载子组件、其 {@code bind} effect 在本帧才诞生）
     * 仍能在<b>同一次 flush</b> 内首跑，避免新挂载项在首帧显示默认值再于下一帧跳变。按注册顺序扫描，
     * 保留「注册顺序即粗略拓扑序」契约（{@link Computed} 先于其下游消费方运行）。</p>
     *
     * <p><b>事务日志</b>：阶段1 已按 signal 合并了写入（{@link #pendingWrites} 同 signal 同帧多次写只留最后一次值）。
     * 去重在此处统一裁定：对比「帧初值」（{@link Signal#peek()}，此时尚未 apply）与「合并终值」，仅当二者不相等
     * （净变化）才 apply 并记一条 {@code before→after}。这样既吸收「set 同值」，也吸收「set 到中间值再 set 回帧初值」
     * 两种无净变化情形。仅记真正发生净变化的源 signal；{@link Computed} 的派生值不入日志（其值可由源 signal 重放后
     * 自动重算）。日志关闭时本段零额外开销。</p>
     *
     * <p>可重入保护：flush 过程中不允许递归调用。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void flush() {
        if (flushing) return;
        flushing = true;
        try {
            // 阶段1：快照并清空待写入表（阶段1 不跑 effect，期间不会有新 put 进来）。
            // 对每个 signal 对比帧初值与合并终值，仅净变化才 apply 并记日志。
            Map<Signal<?>, Object> snapshot = new LinkedHashMap<>(pendingWrites);
            pendingWrites.clear();
            List<TransactionLog.Entry> entries =
                    log.isEnabled() ? new ArrayList<TransactionLog.Entry>(snapshot.size()) : null;
            for (Map.Entry<Signal<?>, Object> e : snapshot.entrySet()) {
                Signal signal = (Signal) e.getKey();
                Object after = e.getValue();
                Object before = signal.peek();          // flush 前现值 = 帧初值
                if (Objects.equals(before, after)) {
                    continue;                           // 无净变化：不 apply、不 markDirty、不入日志
                }
                signal.applyAndNotify(after);           // apply + 标脏订阅者
                if (entries != null) {
                    entries.add(new TransactionLog.Entry(signal, before, after));
                }
            }
            commitTransaction(entries);
            // 阶段2：重跑脏 effect 到不动点
            runEffectsToFixpoint();
        } finally {
            flushing = false;
        }
    }

    /** 把本帧合并后的净变化写入提交为一个事务（仅记净变化的源 signal）。 */
    private void commitTransaction(List<TransactionLog.Entry> entries) {
        if (entries != null && !entries.isEmpty()) {
            log.commit(System.currentTimeMillis(), pendingLabel, entries);
        }
        pendingLabel = null;
    }

    /**
     * 阶段2：按注册顺序重跑脏 effect，迭代到本轮无任何 effect 需要重跑为止。
     * 快照列表避免遍历期并发修改；新建/重新标脏的 effect 在下一轮被纳入。
     */
    private void runEffectsToFixpoint() {
        int pass = 0;
        boolean ranAny = true;
        while (ranAny) {
            if (++pass > MAX_FLUSH_PASSES) {
                throw new IllegalStateException(
                        "响应式 flush 阶段2 超过 " + MAX_FLUSH_PASSES + " 轮仍未收敛，疑似 effect 循环依赖");
            }
            ranAny = false;
            for (Effect e : new ArrayList<>(effects)) {
                if (e.isDirty()) {
                    e.run();
                    ranAny = true;
                }
            }
        }
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

    /** 直接应用事务的 before/after 值并重跑 effect 到不动点（供 undo/redo 复用）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyAndRerun(TransactionLog.Transaction txn, boolean useBefore) {
        flushing = true;
        try {
            for (TransactionLog.Entry entry : txn.entries()) {
                Object value = useBefore ? entry.before() : entry.after();
                ((Signal) entry.signal()).applyAndNotify(value);
            }
            runEffectsToFixpoint();
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
