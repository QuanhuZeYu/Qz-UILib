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

    Effect(Runnable body) {
        this.body = body;
        ReactiveScheduler.get().registerEffect(this);
    }

    /** 创建一个独立 effect（无 Owner），由调度器管理生命周期。 */
    public static Effect create(Runnable body) { return new Effect(body); }

    /** 由 signal 在值变化时调用，标记本 effect 需要重跑。 */
    void markDirty() {
        if (!disposed) dirty = true;
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
        for (Signal<?> dep : dependencies) dep.subscribers.remove(this);
        dependencies.clear();
        ReactiveScheduler.get().unregisterEffect(this);
    }

    public boolean isDirty() { return dirty; }
    public boolean isDisposed() { return disposed; }
}
