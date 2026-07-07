package club.heiqi.uilib.ui.reactive;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 响应式 signal 原子：数据层最小状态单元（信条二，I1/I2）。
 * <ul>
 *   <li>{@link #get()} 在响应式上下文中自动注册订阅（依赖追踪）</li>
 *   <li>{@link #set(Object)} 经 {@link ReactiveScheduler} 批处理，帧末统一生效（I9）</li>
 * </ul>
 *
 * @param <T> 状态类型
 */
public final class Signal<T> implements ReadableSignal<T> {

    private T value;
    /** 直接订阅本 signal 的 effect 集合（依赖图的边）。 */
    final Set<Effect> subscribers = new LinkedHashSet<>();

    private Signal(T initial) { this.value = initial; }

    /** 创建初始值为 {@code initial} 的 signal。 */
    public static <T> Signal<T> create(T initial) { return new Signal<>(initial); }

    /**
     * 读取当前值；若处于响应式上下文中则自动建立订阅。
     *
     * @return 当前值
     */
    @Override
    public T get() {
        Effect current = ReactiveContext.getCurrent();
        if (current != null) {
            subscribers.add(current);
            current.trackDependency(this);
        }
        return value;
    }

    /**
     * 将新值排入 {@link ReactiveScheduler} 队列，帧末统一应用。
     *
     * <p>不在此处做相等去重：去重的正确时机是帧末（flush 阶段1）对比「帧初值」与
     * 「本帧合并后的终值」。若在 set 时拿「已 flush 旧值」做比较，会把「同帧 set 到中间值、
     * 再 set 回帧初值」的第二次 set 误判为无变化而丢弃，导致 flush 后落到错误的中间值
     * （I9「帧末批处理合并写入」要求按净变化生效）。详见 {@link ReactiveScheduler#queueWrite}
     * 与 {@link ReactiveScheduler#flush()}。</p>
     *
     * <p><b>effect 内调用同帧生效</b>：{@link ReactiveScheduler#flush()} 已改为「drain-writes 与 run-effects
     * 双通道交替到不动点」，effect 内 set 进入的 pendingWrites 在紧接的 drain 轮内即被应用、订阅者被 markDirty、
     * 下游 effect 在同一 flush 内重跑。无需任何绕过调度器的同步写入（守 I2 单一收口）。effect 内 set signal
     * 需要包 {@link Effect#untrack} 避免下游订阅反向触发本 effect 重订阅形成环（守 I1/I11）。</p>
     *
     * @param newValue 新值
     */
    public void set(T newValue) {
        ReactiveScheduler.get().queueWrite(this, newValue);
    }

    /**
     * 读取当前值但不建立订阅（不追踪依赖）。
     * 仅供包内 {@link Computed} 做相等判断，避免自订阅形成环。
     *
     * @return 当前值
     */
    T peek() {
        return value;
    }

    /**
     * 由 {@link ReactiveScheduler#flush()} 调用：直接应用新值并通知订阅者变脏。
     * 不经过队列，仅在 flush 内部使用。
     */
    @SuppressWarnings("unchecked")
    void applyAndNotify(Object newValue) {
        this.value = (T) newValue;
        for (Effect s : new LinkedHashSet<>(subscribers)) {
            s.markDirty();
        }
    }
}
