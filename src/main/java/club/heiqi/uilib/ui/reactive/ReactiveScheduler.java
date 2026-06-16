package club.heiqi.uilib.ui.reactive;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
 */
public final class ReactiveScheduler {

    private static final ReactiveScheduler INSTANCE = new ReactiveScheduler();

    /** 全局单例入口。 */
    public static ReactiveScheduler get() { return INSTANCE; }

    /** 待应用的写入队列，每项为 [signal, newValue]。 */
    private final Deque<Object[]> writeQueue = new ArrayDeque<>();
    /** 已注册的 effect 列表（注册顺序即粗略拓扑序）。 */
    private final List<Effect> effects = new ArrayList<>();
    private boolean flushing = false;

    /** 单次 flush 阶段2 的最大迭代轮数，超过判为 effect 循环依赖（防死循环）。 */
    private static final int MAX_FLUSH_PASSES = 1000;

    private ReactiveScheduler() {}

    /** 由 Signal.set() 调用，将写入排入队列。 */
    <T> void queueWrite(Signal<T> signal, T value) {
        writeQueue.add(new Object[]{signal, value});
    }

    void registerEffect(Effect e) { effects.add(e); }
    void unregisterEffect(Effect e) { effects.remove(e); }

    /**
     * 帧末批量刷新：
     * <ol>
     *   <li>应用所有待写入，触发脏标记传播</li>
     *   <li>按注册顺序重跑脏 effect，<b>迭代到不动点</b></li>
     * </ol>
     *
     * <p>阶段2 迭代到不动点的意义：flush 期间新建或被重新标脏的 effect（典型来源是
     * {@code mount}/{@code forEach} 在协调 effect 内动态挂载子组件、其 {@code bind} effect 在本帧才诞生）
     * 仍能在<b>同一次 flush</b> 内首跑，避免新挂载项在首帧显示默认值再于下一帧跳变。按注册顺序扫描，
     * 保留「注册顺序即粗略拓扑序」契约（{@link Computed} 先于其下游消费方运行）。</p>
     *
     * <p>可重入保护：flush 过程中不允许递归调用。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void flush() {
        if (flushing) return;
        flushing = true;
        try {
            // 阶段1：应用写入，同步传播脏标记
            while (!writeQueue.isEmpty()) {
                Object[] pair = writeQueue.poll();
                ((Signal) pair[0]).applyAndNotify(pair[1]);
            }
            // 阶段2：按注册顺序重跑脏 effect，迭代到本轮无任何 effect 需要重跑为止。
            // 快照列表避免遍历期并发修改；新建/重新标脏的 effect 在下一轮被纳入。
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
        } finally {
            flushing = false;
        }
    }

    /**
     * 重置所有状态（仅用于单元测试的 setUp/tearDown）。
     */
    public void reset() {
        writeQueue.clear();
        effects.clear();
        flushing = false;
    }
}
