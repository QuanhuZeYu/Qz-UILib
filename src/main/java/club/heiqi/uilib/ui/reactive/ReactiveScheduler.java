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
     *   <li>重跑所有脏 effect</li>
     * </ol>
     * 可重入保护：flush 过程中不允许递归调用。
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
            // 阶段2：运行所有脏 effect（快照列表避免并发修改）
            for (Effect e : new ArrayList<>(effects)) {
                e.run();
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
