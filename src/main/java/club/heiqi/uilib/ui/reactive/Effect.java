package club.heiqi.uilib.ui.reactive;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 响应式 effect：依赖变化时自动重跑的副作用单元（I3，信条二）。
 * <p>创建时注册到 {@link ReactiveScheduler}，首次 {@link ReactiveScheduler#flush()} 时执行。
 * 此后任何被追踪的 {@link Signal} 变化都会将本 effect 标脏，并在下一次 flush 时重跑。</p>
 */
public final class Effect {

    private final Runnable body;
    /** 本 effect 当前追踪的 signal 依赖集。 */
    private final Set<Signal<?>> dependencies = new LinkedHashSet<>();
    /** 是否需要在下次 flush 时重跑。初始为 true，确保首次 flush 执行一次。 */
    private boolean dirty = true;
    private boolean disposed = false;

    /**
     * 本 effect 当前是否登记在调度器的 effect 表内（P1-3 dirty 计数不变量的锚）。
     *
     * <p>调度器的 {@code dirtyEffectCount} 只在「已登记」的 effect 上增减，故
     * {@code reset()} 之后再 dispose / markDirty 的迟到调用不会把计数带偏——
     * 计数偏低会让早退跳过真脏的 effect（正确性事故），必须有结构性防线。</p>
     */
    private boolean registered;

    Effect(Runnable body) {
        this.body = body;
        // 自动归属：若处于某 Owner 作用域内（如组件 mount 期），attach 到当前 owner，
        // 随该作用域 dispose 一并清理（I3：effect 不泄漏）。无作用域时由调度器管理。
        Owner owner = ReactiveContext.getCurrentOwner();
        if (owner != null) {
            owner.attach(this);
        }
        // 若 attach 到一个已 dispose 的 owner，本 effect 已被立即 dispose——不再注册进调度器（避免残留）。
        if (!disposed) {
            ReactiveScheduler.get().registerEffect(this);
        }
    }

    /**
     * 创建一个 effect：若处于某 {@link Owner} 作用域内则自动归属该作用域，否则为独立 effect
     * （由调度器管理生命周期）。
     */
    public static Effect create(Runnable body) { return new Effect(body); }

    /**
     * 在<b>非追踪</b>上下文中执行 {@code body}：期间对任何 {@link Signal}/{@link Computed} 的读取
     * <b>都不会</b>登记为当前 effect 的依赖。
     *
     * <p>用途（守 I5 红线）：keyed 列表协调（{@code forEach}）的 reconcile effect 只应订阅「列表本身」，
     * 对每一项的构建/更新若直接读取 item 内部的 signal，必须用本方法隔离，否则单项 signal 变化会反向
     * 触发整个列表重协调——退化成「全列表 diff」，违反信条三红线。SolidJS {@code untrack} 的等价物。</p>
     *
     * @param body 在非追踪上下文中执行的逻辑
     */
    public static void untrack(Runnable body) {
        Effect prev = ReactiveContext.setCurrent(null);
        try {
            body.run();
        } finally {
            ReactiveContext.setCurrent(prev);
        }
    }

    /** 由 signal 在值变化时调用，标记本 effect 需要重跑（clean→dirty 时同步调度器早退计数）。 */
    void markDirty() {
        if (disposed || dirty) {
            return;
        }
        dirty = true;
        if (registered) {
            ReactiveScheduler.get().__effectBecameDirty();
        }
    }

    /** 由 {@link Signal#get()} 在追踪期间调用，登记依赖关系。 */
    void trackDependency(Signal<?> s) { dependencies.add(s); }

    /**
     * 由 {@link ReactiveScheduler#flush()} 调用。
     * 清空旧依赖，以追踪模式重新执行 body 以建立新依赖集。
     */
    void run() {
        if (disposed || !dirty) return;
        dirty = false;
        if (registered) {
            ReactiveScheduler.get().__effectBecameClean();
        }
        // 取消旧订阅，防止订阅泄漏
        for (Signal<?> dep : dependencies) dep.subscribers.remove(this);
        dependencies.clear();
        // 在追踪上下文中执行 body（自动重建依赖）
        Effect prev = ReactiveContext.setCurrent(this);
        try { body.run(); }
        finally { ReactiveContext.setCurrent(prev); }
    }

    /**
     * 释放：取消所有订阅，从调度器中注销，后续不再重跑。
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (dirty) {
            // 已释放的 effect 无待跑工作；避免其残留在调度器列表里被不动点循环误判为脏。
            // 同步回收早退计数（仅登记在册者计入过）。
            dirty = false;
            if (registered) {
                ReactiveScheduler.get().__effectBecameClean();
            }
        }
        for (Signal<?> dep : dependencies) dep.subscribers.remove(this);
        dependencies.clear();
        ReactiveScheduler.get().unregisterEffect(this);
    }

    public boolean isDirty() { return dirty; }
    public boolean isDisposed() { return disposed; }

    /** @return 本 effect 是否登记在调度器 effect 表内（P1-3 计数探针） */
    boolean isRegistered() { return registered; }

    /**
     * 由调度器在登记时调用（幂等）。
     *
     * @return 本次是否由「未在册」翻转为「在册」（false = 已在册，调用方不得重复计数）
     */
    boolean __markRegistered() {
        if (registered) {
            return false;
        }
        registered = true;
        return true;
    }

    /**
     * 由调度器在注销/重置时调用（幂等）。
     *
     * @return 本次是否由「在册」翻转为「未在册」（false = 本就不在册）
     */
    boolean __markUnregistered() {
        if (!registered) {
            return false;
        }
        registered = false;
        return true;
    }
}
