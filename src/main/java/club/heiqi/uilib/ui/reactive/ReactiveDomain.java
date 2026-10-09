package club.heiqi.uilib.ui.reactive;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** 串行状态上下文；状态立即生效，副作用在操作结束后推进，完全独立于 UI 帧。 */
public final class ReactiveDomain {
    private static final ThreadLocal<ReactiveDomain> CURRENT = new ThreadLocal<>();
    private final Executor executor;
    private final ArrayDeque<Runnable> actions = new ArrayDeque<>();
    private final ArrayDeque<Effect> effects = new ArrayDeque<>();
    private boolean scheduled;
    private int depth;
    private int readOnlyDepth;
    private boolean drainingEffects;
    Tracker tracker;

    public ReactiveDomain(Executor executor) { this.executor = Objects.requireNonNull(executor, "executor"); }
    public static ReactiveDomain direct() { return new ReactiveDomain(Runnable::run); }
    public static ReactiveDomain current() {
        ReactiveDomain domain = CURRENT.get();
        if (domain == null) throw new IllegalStateException("状态访问不在状态域内");
        return domain;
    }
    private static final class FailureBoundary implements Runnable {
        final Runnable action;
        final Consumer<Throwable> failed;
        FailureBoundary(Runnable action, Consumer<Throwable> failed) { this.action = action; this.failed = failed; }
        @Override public void run() { action.run(); }
    }
    /** 递交独立操作，并在该操作或其结束时Effect结算失败后于域内通知清理者。
     * 同步和外部executor使用同一边界；通知不吞原异常，不重试业务操作。
     * 清理失败追加为suppressed，原始Throwable仍交既有executor错误通道。 */
    public void execute(Runnable action, Consumer<Throwable> failed) {
        execute(new FailureBoundary(Objects.requireNonNull(action, "action"), Objects.requireNonNull(failed, "failed")));
    }
    public void execute(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (CURRENT.get() == this) { operation(action); return; }
        enqueue(action);
    }
    /** 总是排入独立顶层操作，即使调用者已在本域内；保证每事件Effect结算。 */
    public void enqueue(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (CURRENT.get() == this) checkWrite();
        boolean start;
        synchronized (actions) {
            actions.add(action);
            start = !scheduled;
            if (start) scheduled = true;
        }
        if (start) executor.execute(this::drainActions);
    }
    public void enqueue(Runnable action, Consumer<Throwable> failed) {
        enqueue(new FailureBoundary(Objects.requireNonNull(action, "action"), Objects.requireNonNull(failed, "failed")));
    }
    private void drainActions() {
        ReactiveDomain previous = CURRENT.get();
        CURRENT.set(this);
        try {
            for (;;) {
                Runnable action;
                synchronized (actions) {
                    action = actions.poll();
                    if (action == null) { scheduled = false; return; }
                }
                operation(action);
            }
        } catch (RuntimeException | Error failure) {
            synchronized (actions) { scheduled = false; }
            throw failure;
        } finally { CURRENT.set(previous); }
    }
    private void operation(Runnable action) {
        try {
            depth++;
            try { action.run(); }
            finally { depth--; }
            if (depth == 0) drainEffects();
        } catch (RuntimeException | Error failure) {
            if (action instanceof FailureBoundary) {
                try { ((FailureBoundary) action).failed.accept(failure); }
                catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }
    public void batch(Runnable action) { checkAccess(); operation(Objects.requireNonNull(action, "action")); }
    public <T> Signal<T> signal(T initial) { checkAccess(); return new Signal<>(this, initial); }
    public <T> Computed<T> computed(Supplier<T> derivation) {
        checkAccess(); return new Computed<>(this, Objects.requireNonNull(derivation, "derivation"));
    }
    public <T> Computed<T> computed(Owner owner, Supplier<T> derivation) {
        Computed<T> value = computed(derivation); owner.own(value); return value;
    }
    public Effect effect(Owner owner, Runnable action) {
        checkWrite();
        Effect effect = new Effect(this, Objects.requireNonNull(action, "action"));
        owner.own(effect);
        enqueue(effect);
        return effect;
    }
    public Observation observe(Owner owner, Runnable read, Runnable changed) {
        checkAccess();
        Observation observation = new Observation(this, Objects.requireNonNull(read, "read"), Objects.requireNonNull(changed, "changed"));
        owner.own(observation);
        return observation;
    }
    public Owner owner() { checkAccess(); return new Owner(this); }
    public void checkAccess() {
        if (CURRENT.get() != this) throw new IllegalStateException("不可跨域直接访问状态");
    }
    public <T> T readOnly(Supplier<T> read) {
        checkAccess(); readOnlyDepth++;
        try { return read.get(); } finally { readOnlyDepth--; }
    }
    /** 校验域内受控写命令；测量、绘制和纯派生阶段不能改变交互或业务权威。 */
    public void checkWrite() {
        checkAccess();
        if (readOnlyDepth != 0) throw new IllegalStateException("测量或绘制不能写业务状态");
        if (tracker instanceof Computed<?>) throw new IllegalStateException("纯派生不能写状态");
    }
    void enqueue(Effect effect) { if (!effect.queued && !effect.disposed) { effect.queued = true; effects.add(effect); } }
    private void drainEffects() {
        if (drainingEffects) return;
        drainingEffects = true;
        try {
            Effect effect;
            while ((effect = effects.poll()) != null) { effect.queued = false; if (!effect.disposed) effect.run(); }
        } finally { drainingEffects = false; }
    }
}
