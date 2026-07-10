package club.heiqi.uilib.config.modern;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.event.FontReloadRequest;
import club.heiqi.uilib.net.core.MainThreadDispatcher;
import club.heiqi.uilib.net.transport.NetSide;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UILib 全局配置回灌协调器：跨 {@link ConfigSaveListener} 世代协调主线程 apply。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>每次 listener 构造注册单调 {@link Registration}（generation + manager 不可变封装）</li>
 *   <li>仅<strong>当前 Registration</strong> 的事件可 submit；register 并发单调不可回退</li>
 *   <li>全局 pending 携带 registration identity + reason</li>
 *   <li>{@link AtomicBoolean} enqueueOwner：CLIENT 队列最多一个 coordinator Runnable</li>
 *   <li><b>no-spin / next-drain</b>：一次 CLIENT dispatcher drain 中 coordinator task 最多执行一次；
 *       不在 finally 立即重排到同队列。owner false 时 submit/retry 可排一次；
 *       owner true 只更新 pending。剩余/失败 pending 由下一 tick {@link #retryPendingOnce()} 再排。
 *       依赖 {@link MainThreadDispatcher} <strong>批次交换</strong>：drain 期间 enqueue 的任务绝不本轮消费</li>
 *   <li>静态队列 Runnable <strong>不得</strong>闭包持旧 listener</li>
 *   <li>旧 listener 晚到事件 no-op；新页面注册后旧任务不得回灌旧 Authority</li>
 * </ul>
 *
 * <h3>强线性化（单一 monitor）</h3>
 * <p>{@link #register}、{@link #submit}、apply 资格复核与受控 {@code applyOnMainThread}
 * 均在同一私有 {@link #monitor} 域内，无 wait/condition：</p>
 * <ul>
 *   <li>{@code generation++} 仅在 monitor 内；新 Registration 一旦 {@link #register} 返回，
 *       之后旧 apply <strong>不得</strong>写（因旧 apply 若在跑则持同一 monitor，register 尚未返回）</li>
 *   <li>register：若<strong>当前线程</strong>正在 apply → {@link IllegalStateException} fail-fast 且零变化；
 *       否则其他线程因 monitor 自然阻塞至 apply 结束，再停用旧、创建/激活/发布新 Registration 并写 initial pending</li>
 *   <li>submit：同锁校验 registration identity，写 latest pending</li>
 *   <li>apply task：同锁复核 current/pending 后执行受控 apply；异常时同锁 reoffer，退出释放</li>
 *   <li><strong>生产路径 apply 回调不得反向 register</strong>（契约；测试覆盖 reentrant fail-fast）</li>
 * </ul>
 *
 * <h3>失败与重试</h3>
 * <p>apply 前取走 pending；失败仅当 pending 仍为 null（无更新）时 reoffer 失败值，新事件优先；
 * owner 最终释放。失败期间新事件不丢，下一 tick retry；last snapshot 仅成功 apply 后推进。
 * per-task 捕获 {@link RuntimeException}/{@link Error}
 *（不含 {@link VirtualMachineError}/{@link ThreadDeath}/{@link LinkageError}）并日志隔离。
 * {@link AssertionError} 同锁 reoffer 后 rethrow，保证 JUnit 可见。</p>
 *
 * <h3>测试 hook 与 AssertionError</h3>
 * <p>测试 hook <strong>不得</strong>在持有本 monitor 时 wait/condition；
 * {@link AssertionError} 直接 rethrow，保证 JUnit 失败可见。RuntimeException 仅日志（非断言路径）。</p>
 *
 * <h3>Forge CLIENT END 顺序</h3>
 * <p>{@code retryPendingOnce → drainClient}：retry 仅在 owner false 且有有效 pending 时 enqueue 一次，
 * 与已有 queued 任务不重复（owner true 时 CAS 失败）。每 tick 最多一个 coordinator apply
 *（drain 预算内该 task 恰一次；期间 re-enqueue 留 next-drain）。</p>
 *
 * <p>包级 API 供测试探针。</p>
 */
public final class ModernConfigApplyCoordinator {

    private static final ModernConfigApplyCoordinator INSTANCE = new ModernConfigApplyCoordinator();

    /** save 路径 reload reason */
    static final String RELOAD_REASON_SAVED = "modern_config_saved";
    /** 磁盘 reload 路径 reason */
    static final String RELOAD_REASON_RELOADED = "modern_config_reloaded";
    /** register 自动 initial apply */
    static final String RELOAD_REASON_REGISTER = "modern_config_register";

    /**
     * register / submit / apply 资格与受控 apply 的唯一线性化域。
     * <p>禁止 wait/condition；其他线程在 apply 持锁期间对 register 自然阻塞。</p>
     */
    private final Object monitor = new Object();

    /** 单调 generation；仅在 {@link #monitor} 内自增 */
    private long generationSeq = 0L;

    /** 当前不可变 Registration（generation + manager） */
    private final AtomicReference<Registration> currentRegistration =
            new AtomicReference<Registration>(null);

    /** latest-wins pending：携 registration identity + reason，不持 listener */
    private final AtomicReference<PendingApply> pending = new AtomicReference<PendingApply>(null);
    /** 是否已有 CLIENT 队列任务（最多一个；true 时 submit 只写 pending） */
    private final AtomicBoolean enqueueOwner = new AtomicBoolean(false);
    /**
     * 失败后需 tick 重试：true 表示上次 apply 失败且 reoffer 成功。
     * {@link #retryPendingOnce()} 每 tick 最多调度一次，禁止同 drain 自旋。
     */
    private final AtomicBoolean needsRetry = new AtomicBoolean(false);

    /**
     * 当前持 monitor 执行 apply 的线程；非 null 时同线程 reentrant register fail-fast。
     * 仅在 {@link #monitor} 内读写。
     */
    private Thread applyingThread;

    /** 测试：记录最近一次 coordinator DISPATCH 执行线程（包级探针）。 */
    private final AtomicReference<Thread> lastDispatchThread = new AtomicReference<Thread>(null);
    /** 测试：coordinator DISPATCH 执行次数（reset 后清零）。 */
    private final AtomicLong dispatchRunCount = new AtomicLong(0L);
    /** 测试：成功 apply 次数（仅 applyOnMainThread 正常返回后）。 */
    private final AtomicLong successfulApplyCount = new AtomicLong(0L);

    private ModernConfigApplyCoordinator() {
    }

    public static ModernConfigApplyCoordinator getInstance() {
        return INSTANCE;
    }

    /**
     * listener 构造时注册：在 monitor 内分配单调 generation，停用旧 Registration，
     * 发布新 Registration，并自动 submit initial apply。
     *
     * <p>若<strong>当前线程</strong>正在 apply：{@link IllegalStateException} 且零变化
     *（生产 apply 回调不得反向 register）。其他线程在 apply 期间会因 monitor 自然等待，
     * 保证 register 返回后旧 apply 绝不再写。</p>
     *
     * @param manager 新页面的 ConfigManager，非 null
     * @return 分配给该 listener 的 Registration
     * @throws IllegalStateException 当前线程正在 apply（reentrant reverse-register）
     */
    public Registration register(ConfigManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("manager must not be null");
        }
        synchronized (monitor) {
            if (applyingThread == Thread.currentThread()) {
                throw new IllegalStateException(
                        "apply callback must not reverse-register ModernConfigApplyCoordinator");
            }
            long gen = ++generationSeq;
            Registration reg = new Registration(gen, manager);
            Registration old = currentRegistration.get();
            if (old != null) {
                old.deactivate();
            }
            currentRegistration.set(reg);
            reg.activate();
            // 丢弃旧 pending（世代已过期）+ 自动 initial apply
            pending.set(new PendingApply(reg, RELOAD_REASON_REGISTER));
            needsRetry.set(false);
            scheduleIfNeededUnlocked();

            MyMod.LOG.debug("ModernConfigApplyCoordinator register gen={} manager={}",
                    Long.valueOf(gen), manager);
            return reg;
        }
    }

    /**
     * UILib 全局配置当前 Authority 对应的 manager（最新打开的配置页）。
     *
     * @return 当前 manager，可能为 null（尚未 register）
     */
    public ConfigManager currentManager() {
        Registration reg = currentRegistration.get();
        return reg == null ? null : reg.manager;
    }

    /**
     * 当前 generation（测试/诊断）。
     *
     * @return 单调 generation，未注册时 0
     */
    long currentGeneration() {
        Registration reg = currentRegistration.get();
        return reg == null ? 0L : reg.generation;
    }

    /**
     * 当前 Registration（测试/诊断）。
     *
     * @return 当前 Registration，可能 null
     */
    Registration currentRegistration() {
        return currentRegistration.get();
    }

    /**
     * 提交回灌请求：仅 registration 等于当前世代且 active 时生效；否则 no-op。
     * 与 register 同一线性化域：核 current 并写 pending，stale A 不能覆盖 B。
     *
     * <p>owner false 时可排一次 CLIENT 任务；owner true 只更新 pending（no-spin）。</p>
     *
     * @param registration listener 构造时分配的 Registration
     * @param reason       modern_config_saved / modern_config_reloaded
     */
    public void submit(Registration registration, String reason) {
        if (registration == null || reason == null || registration.manager == null) {
            return;
        }
        synchronized (monitor) {
            if (!registration.isActive()) {
                MyMod.LOG.debug("ModernConfigApplyCoordinator ignore inactive gen={}",
                        Long.valueOf(registration.generation));
                return;
            }
            Registration cur = currentRegistration.get();
            if (cur == null || registration.generation != cur.generation
                    || registration.manager != cur.manager) {
                MyMod.LOG.debug("ModernConfigApplyCoordinator ignore stale gen={} current={}",
                        Long.valueOf(registration.generation),
                        Long.valueOf(cur == null ? 0L : cur.generation));
                return;
            }
            pending.set(new PendingApply(registration, reason));
            needsRetry.set(false);
            scheduleIfNeededUnlocked();
        }
    }

    /**
     * tick 驱动：若仍有有效 pending 且<strong>尚未</strong>占有 enqueueOwner，
     * 调度一次（每 CLIENT END 最多一次）。owner true 时 no-op，避免与已有 queued 重复。
     */
    public void retryPendingOnce() {
        synchronized (monitor) {
            PendingApply p = pending.get();
            if (p == null) {
                needsRetry.set(false);
                return;
            }
            Registration cur = currentRegistration.get();
            if (cur == null
                    || !p.registration.isActive()
                    || p.registration.generation != cur.generation
                    || p.registration.manager != cur.manager) {
                pending.compareAndSet(p, null);
                needsRetry.set(false);
                return;
            }
            scheduleIfNeededUnlocked();
        }
    }

    /**
     * 仅在 owner false 时 enqueue 一次；owner true 只保留 pending（调用方已写）。
     * 必须在 {@link #monitor} 内调用。
     */
    private void scheduleIfNeededUnlocked() {
        if (!enqueueOwner.compareAndSet(false, true)) {
            return;
        }
        MainThreadDispatcher.getInstance().enqueue(NetSide.CLIENT, DISPATCH_RUNNABLE);
    }

    /** 单例 Runnable：不捕获 listener / 局部 manager */
    private static final Runnable DISPATCH_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            INSTANCE.drainOnceOnClient();
        }
    };

    /**
     * 主线程消费：在 monitor 内取 pending、复核资格、标记 applyingThread，
     * 并执行受控 apply（评估回调不得反向 register）。异常同锁 reoffer；退出释放。
     */
    private void drainOnceOnClient() {
        lastDispatchThread.set(Thread.currentThread());
        dispatchRunCount.incrementAndGet();
        try {
            synchronized (monitor) {
                PendingApply p = pending.getAndSet(null);
                if (p == null) {
                    needsRetry.set(false);
                    return;
                }
                Registration cur = currentRegistration.get();
                if (cur == null
                        || !p.registration.isActive()
                        || p.registration.generation != cur.generation
                        || p.registration.manager != cur.manager) {
                    MyMod.LOG.debug("ModernConfigApplyCoordinator drop stale pending gen={}",
                            Long.valueOf(p.registration.generation));
                    needsRetry.set(false);
                    return;
                }
                applyingThread = Thread.currentThread();
                try {
                    applyOnMainThread(p.registration.manager, p.reason);
                    successfulApplyCount.incrementAndGet();
                    needsRetry.set(false);
                } catch (RuntimeException e) {
                    MyMod.LOG.error("ModernConfigApplyCoordinator apply failed, will retry: reason="
                            + p.reason, e);
                    reofferOnFailureUnlocked(p);
                } catch (AssertionError e) {
                    MyMod.LOG.error("ModernConfigApplyCoordinator apply assertion, will retry: reason="
                            + p.reason, e);
                    reofferOnFailureUnlocked(p);
                    // 测试断言必须回传 JUnit
                    throw e;
                } catch (Error e) {
                    if (e instanceof VirtualMachineError
                            || e instanceof ThreadDeath
                            || e instanceof LinkageError) {
                        throw e;
                    }
                    MyMod.LOG.error("ModernConfigApplyCoordinator apply error, will retry: reason="
                            + p.reason, e);
                    reofferOnFailureUnlocked(p);
                } finally {
                    applyingThread = null;
                }
            }
        } finally {
            // hook 在 monitor 外：允许 submit 不与 monitor 死锁；hook 不得 wait 持本 monitor
            runTestHook(TEST_BEFORE_OWNER_RELEASE, "before-owner-release");
            enqueueOwner.set(false);
        }
    }

    /**
     * 失败 reoffer：仅当无更新 pending 时写回失败值；新事件优先则保留更新。
     * 必须在 {@link #monitor} 内调用。
     */
    private void reofferOnFailureUnlocked(PendingApply failed) {
        if (pending.get() == null) {
            pending.set(failed);
        }
        needsRetry.set(true);
    }

    private void applyOnMainThread(ConfigManager manager, String reason) {
        RuntimeException fault = TEST_APPLY_FAULT.getAndSet(null);
        if (fault != null) {
            throw fault;
        }
        // 测试缝：在受控 apply 内（仍持 monitor）；不得 wait 本 monitor
        runTestHook(TEST_DURING_APPLY, "during-apply");
        // 评估回调不得反向 register（契约；测试覆盖）
        Authority authority = manager.authority();
        ConfigValueBridge.applyFromAuthority(authority);
        MyMod.LOG.debug("Bridge 值回灌完成（{}）: fontSort.length={}, fontSortConfigured={}",
                reason,
                Integer.valueOf(FontConfig.fontSort.length),
                Boolean.valueOf(FontConfig.fontSortConfigured));
        boolean fontRuntimeChanged = FontConfig.affectsFontRuntime();
        MyMod.LOG.debug("affectsFontRuntime={}", Boolean.valueOf(fontRuntimeChanged));
        if (fontRuntimeChanged) {
            MyMod.LOG.info("配置事件触发字体 reload: reason={}", reason);
            FontService.getInstance().reload(new FontReloadRequest(reason));
        }
        FontConfig.onConfigReload();
        MyMod.LOG.debug("配置事件主线程应用完成: reason={}", reason);
    }

    /**
     * 运行测试 hook：AssertionError 直接 rethrow（不得吞）；其它 RuntimeException 日志。
     * <p>调用方须保证：若仍持 {@link #monitor}，hook 内不得对本 monitor wait/condition。</p>
     */
    private static void runTestHook(AtomicReference<Runnable> slot, String name) {
        Runnable hook = slot.getAndSet(null);
        if (hook == null) {
            return;
        }
        try {
            hook.run();
        } catch (AssertionError e) {
            // 测试断言必须回传，禁止吞掉
            throw e;
        } catch (RuntimeException e) {
            MyMod.LOG.error("ModernConfigApplyCoordinator test hook failed: " + name, e);
        }
    }

    // ---- 测试探针（包级）----

    boolean hasPending() {
        return pending.get() != null;
    }

    boolean needsRetry() {
        return needsRetry.get();
    }

    boolean pendingHoldsNoListener() {
        PendingApply p = pending.get();
        if (p == null) {
            return true;
        }
        return p.registration != null
                && p.registration.manager != null
                && p.reason != null;
    }

    boolean isEnqueueOwned() {
        return enqueueOwner.get();
    }

    Thread lastDispatchThread() {
        return lastDispatchThread.get();
    }

    long dispatchRunCount() {
        return dispatchRunCount.get();
    }

    long successfulApplyCount() {
        return successfulApplyCount.get();
    }

    /**
     * 当前是否有线程持 monitor 执行 apply（测试探针）。
     *
     * @return true 表示 apply 进行中
     */
    boolean isApplying() {
        synchronized (monitor) {
            return applyingThread != null;
        }
    }

    static final AtomicReference<RuntimeException> TEST_APPLY_FAULT =
            new AtomicReference<RuntimeException>(null);

    /**
     * 包级 hook：owner 释放前、monitor 外。可 submit；不得对本 monitor wait 死锁。
     * AssertionError 直接 rethrow。
     */
    static final AtomicReference<Runnable> TEST_BEFORE_OWNER_RELEASE =
            new AtomicReference<Runnable>(null);

    /**
     * 包级 hook：受控 apply 内（仍持 monitor）。
     * <p><b>不得</b>对本 monitor wait/condition；可 spawn 其他线程观察 BLOCKED。
     * AssertionError 直接 rethrow。</p>
     */
    static final AtomicReference<Runnable> TEST_DURING_APPLY =
            new AtomicReference<Runnable>(null);

    void resetForTest() {
        synchronized (monitor) {
            pending.set(null);
            enqueueOwner.set(false);
            needsRetry.set(false);
            applyingThread = null;
            Registration cur = currentRegistration.getAndSet(null);
            if (cur != null) {
                cur.deactivate();
            }
            TEST_APPLY_FAULT.set(null);
            TEST_BEFORE_OWNER_RELEASE.set(null);
            TEST_DURING_APPLY.set(null);
            lastDispatchThread.set(null);
            dispatchRunCount.set(0L);
            successfulApplyCount.set(0L);
        }
    }

    /**
     * 不可变注册：generation + manager 原子一体发布；active 控制旧 apply 资格。
     */
    public static final class Registration {
        final long generation;
        final ConfigManager manager;
        /** 仅当前世代为 true；register 新世代时旧者 deactivate */
        private final AtomicBoolean active = new AtomicBoolean(false);

        Registration(long generation, ConfigManager manager) {
            this.generation = generation;
            this.manager = manager;
        }

        void activate() {
            active.set(true);
        }

        void deactivate() {
            active.set(false);
        }

        boolean isActive() {
            return active.get();
        }

        long generation() {
            return generation;
        }

        ConfigManager manager() {
            return manager;
        }
    }

    private static final class PendingApply {
        final Registration registration;
        final String reason;

        PendingApply(Registration registration, String reason) {
            this.registration = registration;
            this.reason = reason;
        }
    }
}
