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
 *   <li>全局 {@link AtomicReference} pending 携带 registration identity + reason</li>
 *   <li>{@link AtomicBoolean} enqueueOwner：CLIENT 队列最多一个 coordinator Runnable</li>
 *   <li><b>no-spin / next-drain</b>：一次 CLIENT dispatcher drain 中 coordinator task 最多执行一次；
 *       不在 finally 立即重排到同队列。owner false 时 submit/retry 可排一次；
 *       owner true 只更新 pending。剩余/失败 pending 由下一 tick {@link #retryPendingOnce()} 再排。
 *       依赖 {@link MainThreadDispatcher} <strong>批次交换</strong>：drain 期间 enqueue 的任务绝不本轮消费</li>
 *   <li>静态队列 Runnable <strong>不得</strong>闭包持旧 listener</li>
 *   <li>旧 listener 晚到事件 no-op；新页面注册后旧任务不得回灌旧 Authority</li>
 * </ul>
 *
 * <h3>强线性化（register / current / pending / apply）</h3>
 * <p>{@link #register}、{@link #submit}、apply 前资格复核均在同一私有 {@link #linearizeLock} 域内：</p>
 * <ul>
 *   <li>新 Registration 一旦 {@link #register} 返回，之后旧 apply <strong>不得</strong>写（active=false）</li>
 *   <li>register 使旧 Registration 失活，发布新 Registration，并自动 submit initial apply</li>
 *   <li>apply 评估回调<strong>不得</strong>反向 register（注释/测试）；锁外 apply 前已持 apply lease</li>
 *   <li>若 apply 可能重入：{@link #applyLease} + register 在 lease 期间 wait/condition，避免死锁
 *       （register 不在持锁时调用可能重入 coordinator 的评估回调）</li>
 * </ul>
 *
 * <h3>失败与重试</h3>
 * <p>apply 前取走 pending；失败仅当 pending 仍为 null（无更新）时 reoffer 失败值，新事件优先；
 * owner 最终释放。失败期间新事件不丢，下一 tick retry；last snapshot 仅成功 apply 后推进。
 * per-task 捕获 {@link RuntimeException}/{@link Error}
 *（不含 {@link VirtualMachineError}/{@link ThreadDeath}/{@link LinkageError}）并日志隔离。</p>
 *
 * <h3>测试 hook 与 AssertionError</h3>
 * <p>测试 hook <strong>不得吞</strong> {@link AssertionError}——直接 rethrow，保证 JUnit 失败可见。
 * RuntimeException 仅日志（非断言路径）。</p>
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

    private final AtomicLong generationSeq = new AtomicLong(0L);
    /** 当前不可变 Registration（generation + manager），原子发布 */
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
     * register/submit/apply 资格线性化域。
     * 禁止在持本锁时调用可能重入 coordinator 的评估回调（apply 在锁外执行）。
     */
    private final Object linearizeLock = new Object();

    /**
     * apply lease：true 表示主线程正在锁外 apply。
     * register 若在 lease 期间进入，在 linearizeLock 上 wait，直到 apply 结束再推进，
     * 保证「register 返回后旧 apply 不得写」且不在持锁时调用重入路径。
     */
    private final AtomicBoolean applyLease = new AtomicBoolean(false);

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
     * listener 构造时注册：分配单调 generation，原子发布不可变 {@link Registration}，
     * 使旧 Registration 失活，并自动 submit initial apply。
     *
     * <p>新 registration 一旦返回，之后旧 apply 不得写（active=false + 世代复核）。
     * 若当前持有 apply lease，等待 lease 释放后再发布，避免旧 apply 与新 register 交错写。</p>
     *
     * @param manager 新页面的 ConfigManager，非 null
     * @return 分配给该 listener 的 Registration
     */
    public Registration register(ConfigManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("manager must not be null");
        }
        long gen = generationSeq.incrementAndGet();
        Registration reg = new Registration(gen, manager);
        synchronized (linearizeLock) {
            // 等待进行中的 apply 结束，避免持锁调用重入；保证 register 返回后旧 apply 不得写
            while (applyLease.get()) {
                try {
                    linearizeLock.wait(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            // 使旧 Registration 失活
            Registration old = currentRegistration.get();
            if (old != null) {
                old.deactivate();
            }
            // 单调发布
            for (;;) {
                Registration cur = currentRegistration.get();
                if (cur != null && cur.generation >= gen) {
                    break;
                }
                if (currentRegistration.compareAndSet(cur, reg)) {
                    break;
                }
            }
            reg.activate();
            // 丢弃旧 pending（世代已过期）
            pending.set(null);
            needsRetry.set(false);
            // 自动 initial apply（保证最终新值）
            pending.set(new PendingApply(reg, RELOAD_REASON_REGISTER));
            scheduleIfNeededUnlocked();

            MyMod.LOG.debug("ModernConfigApplyCoordinator register gen={} manager={}",
                    Long.valueOf(gen), manager);
            // 测试缝：B registration 发布后、返回前（stale A submit / 旧 apply 可卡在此）
            runTestHook(TEST_AFTER_REGISTRATION_PUBLISH, "after-registration");
        }
        return reg;
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
        synchronized (linearizeLock) {
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
        synchronized (linearizeLock) {
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
     * 必须在 {@link #linearizeLock} 内调用。
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
     * 主线程消费：在 linearizeLock 内取 pending + 复核资格；
     * <strong>锁外</strong>跑 eligibility hook（允许 C register）；再回锁复核并取得 apply lease，
     * 锁外 apply（评估回调不得反向 register）。
     */
    private void drainOnceOnClient() {
        lastDispatchThread.set(Thread.currentThread());
        dispatchRunCount.incrementAndGet();
        PendingApply candidate = null;
        try {
            // phase 1：取 pending + 初次资格（持锁短）
            synchronized (linearizeLock) {
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
                candidate = p;
            }

            // phase 2：锁外 hook——B 可暂停，C 可 register 返回（不与 linearizeLock 死锁）
            runTestHook(TEST_AFTER_ELIGIBILITY_BEFORE_APPLY, "after-eligibility-before-apply");

            // phase 3：回锁复核 + apply lease
            PendingApply toApply = null;
            synchronized (linearizeLock) {
                Registration cur = currentRegistration.get();
                if (cur == null
                        || !candidate.registration.isActive()
                        || candidate.registration.generation != cur.generation
                        || candidate.registration.manager != cur.manager) {
                    MyMod.LOG.debug("ModernConfigApplyCoordinator drop after hook gen={}",
                            Long.valueOf(candidate.registration.generation));
                    needsRetry.set(false);
                    return;
                }
                applyLease.set(true);
                toApply = candidate;
            }

            if (toApply != null) {
                try {
                    applyOnMainThread(toApply.registration.manager, toApply.reason);
                    successfulApplyCount.incrementAndGet();
                    needsRetry.set(false);
                } catch (RuntimeException e) {
                    MyMod.LOG.error("ModernConfigApplyCoordinator apply failed, will retry: reason="
                            + toApply.reason, e);
                    reofferOnFailure(toApply);
                } catch (AssertionError e) {
                    MyMod.LOG.error("ModernConfigApplyCoordinator apply assertion, will retry: reason="
                            + toApply.reason, e);
                    reofferOnFailure(toApply);
                } catch (Error e) {
                    if (e instanceof VirtualMachineError
                            || e instanceof ThreadDeath
                            || e instanceof LinkageError) {
                        throw e;
                    }
                    MyMod.LOG.error("ModernConfigApplyCoordinator apply error, will retry: reason="
                            + toApply.reason, e);
                    reofferOnFailure(toApply);
                }
            }
        } finally {
            synchronized (linearizeLock) {
                applyLease.set(false);
                linearizeLock.notifyAll();
            }
            // hook 在锁外：允许 submit 不与 linearizeLock 死锁
            runTestHook(TEST_BEFORE_OWNER_RELEASE, "before-owner-release");
            enqueueOwner.set(false);
        }
    }

    /**
     * 失败 reoffer：仅当无更新 pending 时写回失败值；新事件优先则保留更新。
     */
    private void reofferOnFailure(PendingApply failed) {
        runTestHook(TEST_BEFORE_REOFFER_CAS, "before-reoffer-cas");
        if (pending.compareAndSet(null, failed)) {
            needsRetry.set(true);
        } else {
            needsRetry.set(true);
        }
        runTestHook(TEST_AFTER_REOFFER_BEFORE_OWNER_RELEASE, "after-reoffer");
    }

    private void applyOnMainThread(ConfigManager manager, String reason) {
        RuntimeException fault = TEST_APPLY_FAULT.getAndSet(null);
        if (fault != null) {
            throw fault;
        }
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

    boolean isApplyLeaseHeld() {
        return applyLease.get();
    }

    static final AtomicReference<RuntimeException> TEST_APPLY_FAULT =
            new AtomicReference<RuntimeException>(null);

    static final AtomicReference<Runnable> TEST_BEFORE_OWNER_RELEASE =
            new AtomicReference<Runnable>(null);

    static final AtomicReference<Runnable> TEST_BEFORE_REOFFER_CAS =
            new AtomicReference<Runnable>(null);

    static final AtomicReference<Runnable> TEST_AFTER_REOFFER_BEFORE_OWNER_RELEASE =
            new AtomicReference<Runnable>(null);

    static final AtomicReference<Runnable> TEST_AFTER_REGISTRATION_PUBLISH =
            new AtomicReference<Runnable>(null);

    /**
     * 包级 hook：在 linearizeLock 内资格复核通过后、取得 apply lease 前调用。
     * 用于「B 复核后暂停，C register 返回，B 恢复，断言 B 不 apply 且 C 最终 apply」。
     * AssertionError 直接 rethrow。
     */
    static final AtomicReference<Runnable> TEST_AFTER_ELIGIBILITY_BEFORE_APPLY =
            new AtomicReference<Runnable>(null);

    void resetForTest() {
        synchronized (linearizeLock) {
            pending.set(null);
            enqueueOwner.set(false);
            needsRetry.set(false);
            applyLease.set(false);
            Registration cur = currentRegistration.getAndSet(null);
            if (cur != null) {
                cur.deactivate();
            }
            TEST_APPLY_FAULT.set(null);
            TEST_BEFORE_OWNER_RELEASE.set(null);
            TEST_BEFORE_REOFFER_CAS.set(null);
            TEST_AFTER_REOFFER_BEFORE_OWNER_RELEASE.set(null);
            TEST_AFTER_REGISTRATION_PUBLISH.set(null);
            TEST_AFTER_ELIGIBILITY_BEFORE_APPLY.set(null);
            lastDispatchThread.set(null);
            dispatchRunCount.set(0L);
            successfulApplyCount.set(0L);
            linearizeLock.notifyAll();
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
