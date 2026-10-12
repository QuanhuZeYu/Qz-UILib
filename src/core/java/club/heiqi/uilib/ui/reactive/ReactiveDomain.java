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
    private final ArrayDeque<Observation> observations = new ArrayDeque<>();
    private int invalidationDepth;
    private boolean drainingObservations;
    private boolean scheduled;
    private int depth;
    private int readOnlyDepth;
    private boolean drainingEffects;
    private boolean drainingCleanups;
    private final ArrayDeque<Runnable> cleanups = new ArrayDeque<>();
    Tracker tracker;
    // 依赖收集器可被嵌套Observation替换，纯求值边界必须贯穿整段调用。
    int computationDepth;

    public ReactiveDomain(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public static ReactiveDomain direct() {
        return new ReactiveDomain(Runnable::run);
    }

    public static ReactiveDomain current() {
        ReactiveDomain domain = CURRENT.get();
        if (domain == null) throw new IllegalStateException("状态访问不在状态域内");
        return domain;
    }

    private static final class FailureBoundary implements Runnable {
        final Runnable action;
        final Consumer<Throwable> failed;

        FailureBoundary(Runnable action, Consumer<Throwable> failed) {
            this.action = action;
            this.failed = failed;
        }

        @Override
        public void run() {
            action.run();
        }
    }

    /** 递交独立操作，并在该操作或其结束时Effect结算失败后于域内通知清理者。
     * 同步和外部executor使用同一边界；通知不吞原异常，不重试业务操作。
     * 清理失败追加为suppressed，原始Throwable仍交既有executor错误通道。 */
    public void execute(Runnable action, Consumer<Throwable> failed) {
        execute(
                new FailureBoundary(
                        Objects.requireNonNull(action, "action"),
                        Objects.requireNonNull(failed, "failed")));
    }

    public void execute(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (CURRENT.get() == this) {
            operation(action);
            return;
        }
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
        enqueue(
                new FailureBoundary(
                        Objects.requireNonNull(action, "action"),
                        Objects.requireNonNull(failed, "failed")));
    }

    private void drainActions() {
        ReactiveDomain previous = CURRENT.get();
        CURRENT.set(this);
        try {
            for (; ; ) {
                Runnable action;
                synchronized (actions) {
                    action = actions.poll();
                    if (action == null) {
                        scheduled = false;
                        return;
                    }
                }
                operation(action);
            }
        } catch (RuntimeException | Error failure) {
            synchronized (actions) {
                scheduled = false;
            }
            throw failure;
        } finally {
            CURRENT.set(previous);
        }
    }

    private void operation(Runnable action) {
        Throwable primary = null;
        try {
            depth++;
            try {
                action.run();
            } finally {
                depth--;
            }
            if (depth == 0) drainEffects();
        } catch (RuntimeException | Error failure) {
            primary = failure;
            if (action instanceof FailureBoundary) {
                try {
                    ((FailureBoundary) action).failed.accept(failure);
                } catch (RuntimeException | Error cleanup) {
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        } finally {
            if (depth == 0 && !drainingEffects && !drainingCleanups) {
                try {
                    drainCleanups();
                } catch (RuntimeException | Error cleanup) {
                    if (primary == null) throw cleanup;
                    if (cleanup != primary) primary.addSuppressed(cleanup);
                }
            }
        }
    }

    /** 生命周期释放延后到当前最外层操作和Effect结束，失败也执行；不推进后续业务队列。
     * 域外按普通操作递交；域内只登记清理，不能在输入handler/routing中直接卸载。 */
    public void deferCleanup(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        if (CURRENT.get() != this) {
            execute(() -> deferCleanup(cleanup));
            return;
        }
        checkWrite();
        cleanups.add(cleanup);
    }

    private void drainCleanups() {
        drainingCleanups = true;
        Throwable failure = null;
        try {
            while (!cleanups.isEmpty()) {
                try {
                    cleanups.remove().run();
                } catch (RuntimeException | Error next) {
                    if (failure == null) failure = next;
                    else if (next != failure) failure.addSuppressed(next);
                }
            }
        } finally {
            drainingCleanups = false;
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }

    public void batch(Runnable action) {
        checkAccess();
        operation(() -> invalidate(Objects.requireNonNull(action, "action")));
    }

    /** 先完成全部依赖失效，再同步通知呈现观察者；批次内跨键派生不能暴露旧缓存。
     * 回调错误不阻断其余观察者，已发布状态不撤销；沿原调用通道保留首错。 */
    void invalidate(Runnable action) {
        Throwable primary = null;
        invalidationDepth++;
        try {
            action.run();
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            invalidationDepth--;
            if (invalidationDepth == 0 && !drainingObservations) {
                try { drainObservations(); }
                catch (RuntimeException | Error failure) {
                    if (primary == null) throw failure;
                    if (primary != failure) primary.addSuppressed(failure);
                }
            }
        }
    }

    void invalidate(Observation observation) { observations.add(observation); }

    private void drainObservations() {
        drainingObservations = true;
        Throwable failure = null;
        try {
            Observation observation;
            while ((observation = observations.poll()) != null) {
                try { observation.notifyChanged(); }
                catch (RuntimeException | Error next) {
                    if (failure == null) failure = next;
                    else if (failure != next) failure.addSuppressed(next);
                }
            }
        } finally { drainingObservations = false; }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }

    public <T> Signal<T> signal(T initial) {
        checkAccess();
        return new Signal<>(this, initial);
    }

    public <T> Computed<T> computed(Supplier<T> derivation) {
        checkAccess();
        return new Computed<>(this, Objects.requireNonNull(derivation, "derivation"));
    }

    public <T> Computed<T> computed(Owner owner, Supplier<T> derivation) {
        Computed<T> value = computed(derivation);
        owner.own(value);
        return value;
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
        Observation observation =
                new Observation(
                        this,
                        Objects.requireNonNull(read, "read"),
                        Objects.requireNonNull(changed, "changed"));
        owner.own(observation);
        return observation;
    }

    public Owner owner() {
        checkAccess();
        return new Owner(this);
    }

    /** 宿主可从域外递交命令；域内纯派生/测量阶段不得藉排队改变生命周期。 */
    public void checkDispatch() {
        if (CURRENT.get() == this) checkWrite();
    }

    public void checkAccess() {
        if (CURRENT.get() != this) throw new IllegalStateException("不可跨域直接访问状态");
    }

    public <T> T readOnly(Supplier<T> read) {
        checkAccess();
        readOnlyDepth++;
        try {
            return read.get();
        } finally {
            readOnlyDepth--;
        }
    }

    /** 校验域内受控写命令；测量、绘制和纯派生阶段不能改变交互或业务权威。 */
    public void checkWrite() {
        checkAccess();
        if (readOnlyDepth != 0) throw new IllegalStateException("测量或绘制不能写业务状态");
        if (computationDepth != 0) throw new IllegalStateException("纯派生不能写状态");
    }

    void enqueue(Effect effect) {
        if (!effect.queued && !effect.disposed) {
            effect.queued = true;
            effects.add(effect);
        }
    }

    private void drainEffects() {
        if (drainingEffects) return;
        drainingEffects = true;
        try {
            Effect effect;
            while ((effect = effects.poll()) != null) {
                effect.queued = false;
                if (!effect.disposed) effect.run();
            }
        } finally {
            drainingEffects = false;
        }
    }
}
