package club.heiqi.uilib.ui.reactive;

import java.util.LinkedHashSet;
import java.util.Objects;
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
     * @param newValue 新值
     */
    public void set(T newValue) {
        ReactiveScheduler.get().queueWrite(this, newValue);
    }

    /**
     * 同步写入：绕过 {@link ReactiveScheduler} 队列，立即应用新值并标记订阅者变脏。
     *
     * <p><b>与 {@link #set(Object) 的区别</b>：{@code set} 经队列，下次 {@link ReactiveScheduler#flush()} 才生效；
     * {@code setImmediate} 当场生效，下游订阅 effect 在<b>同一次 flush</b> 的阶段2 内即被重跑。</p>
     *
     * <p><b>用途</b>：effect 内写 signal 需要同帧传播的场景。常规 {@code set} 在 effect 内调用时，
     * 写入要等下次 flush（effect 在阶段2 跑，阶段1 已过），导致下游延迟一帧——对读 signal 做断言的测试
     * 或对时序敏感的 UI（如 autocomplete 浮层随打字展开）不可接受。{@link Computed} 的内部 recompute effect
     * 已用同款 {@code applyAndNotify} 同步刷新 cell，本方法把这一能力对独立 Signal 暴露。</p>
     *
     * <p><b>不入事务日志</b>：绕过队列意味着不进 {@link ReactiveScheduler} 的 TransactionLog。
     * 仅适用于派生/瞬态 UI 状态（如浮层显隐 expanded），业务数据应继续用 {@code set} 以保留审计/撤销能力。</p>
     *
     * <p>相等去重：新旧值 {@link Objects#equals} 相等时不 apply、不通知（与 Computed recompute 一致）。</p>
     *
     * @param newValue 新值
     */
    public void setImmediate(T newValue) {
        if (Objects.equals(this.value, newValue)) {
            return;
        }
        applyAndNotify(newValue);
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
