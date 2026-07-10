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
 *       依赖 {@link MainThreadDispatcher} 入口快照预算：drain 期间 enqueue 的任务绝不本轮消费</li>
 *   <li>静态队列 Runnable <strong>不得</strong>闭包持旧 listener</li>
 *   <li>旧 listener 晚到事件 no-op；新页面注册后旧任务不得回灌旧 Authority</li>
 * </ul>
 *
 * <h3>线性化域</h3>
 * <p>{@link #register} 与 {@link #submit} 在同一私有锁域内核 current registration 并写 pending，
 * 保证 stale A 不能在 B register/submit 后覆盖 B。</p>
 *
 * <h3>失败与重试</h3>
 * <p>apply 前取走 pending；失败仅当 pending 仍为 null（无更新）时 reoffer 失败值，新事件优先；
 * owner 最终释放。失败期间新事件不丢，下一 tick retry；last snapshot 仅成功 apply 后推进。
 * per-task 捕获 {@link RuntimeException}/{@link Error}
 *（不含 {@link VirtualMachineError}/{@link ThreadDeath}/{@link LinkageError}）并日志隔离。</p>
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
     * register/submit 线性化域：核 current registration 与写 pending 同一锁，
     * 防止 stale A 在 B register/submit 后覆盖 B。
     */
    private final Object linearizeLock = new Object();

    /** 测试：记录最近一次 coordinator DISPATCH 执行线程（包级探针）。 */
    private final AtomicReference<Thread> lastDispatchThread = new AtomicReference<Thread>(null);
    /** 测试：coordinator DISPATCH 执行次数（reset 后清零）。 */
    private final AtomicLong dispatchRunCount = new AtomicLong(0L);

    private ModernConfigApplyCoordinator() {
    }

    public static ModernConfigApplyCoordinator getInstance() {
        return INSTANCE;
    }

    /**
     * listener 构造时注册：分配单调 generation，原子发布不可变 {@link Registration}。
     * 并发 register 单调不可回退（只推进到更大 generation）。
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
            // 单调发布：只接受 generation 更大的 Registration
            for (;;) {
                Registration cur = currentRegistration.get();
                if (cur != null && cur.generation >= gen) {
                    // 理论上 gen 全局递增，不应发生；防御：不回退
                    break;
                }
                if (currentRegistration.compareAndSet(cur, reg)) {
                    break;
                }
            }
            MyMod.LOG.debug("ModernConfigApplyCoordinator register gen={} manager={}",
                    Long.valueOf(gen), manager);
            // 测试缝：B registration 发布后、返回前（stale A submit 可卡在此）
            Runnable afterPublish = TEST_AFTER_REGISTRATION_PUBLISH.getAndSet(null);
            if (afterPublish != null) {
                try {
                    afterPublish.run();
                } catch (RuntimeException e) {
                    MyMod.LOG.error("ModernConfigApplyCoordinator after-registration hook failed", e);
                } catch (AssertionError e) {
                    MyMod.LOG.error("ModernConfigApplyCoordinator after-registration hook assertion", e);
                }
            }
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
     * 提交回灌请求：仅 registration 等于当前世代时生效；否则 no-op。
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
            Registration cur = currentRegistration.get();
            if (cur == null || registration.generation != cur.generation
                    || registration.manager != cur.manager) {
                MyMod.LOG.debug("ModernConfigApplyCoordinator ignore stale gen={} current={}",
                        Long.valueOf(registration.generation),
                        Long.valueOf(cur == null ? 0L : cur.generation));
                return;
            }
            pending.set(new PendingApply(registration, reason));
            // 新事件优先：清除失败重试标记（pending 已是最新）
            needsRetry.set(false);
            scheduleIfNeededUnlocked();
        }
    }

    /**
     * tick 驱动：若仍有有效 pending 且<strong>尚未</strong>占有 enqueueOwner，
     * 调度一次（每 CLIENT END 最多一次）。owner true 时 no-op，避免与已有 queued 重复。
     *
     * <p>Forge CLIENT END 应在 drain 前调用本方法，确保上一 tick 剩余/失败 pending 再排一次。
     * 与 {@link MainThreadDispatcher} 入口预算配合：本方法 enqueue 的任务若发生在 drain 期间，
     * 仅 next-drain 消费。</p>
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
                    || p.registration.generation != cur.generation
                    || p.registration.manager != cur.manager) {
                // 世代已过期：丢弃
                pending.compareAndSet(p, null);
                needsRetry.set(false);
                return;
            }
            // 有有效 pending 且 owner false 时才排；owner true 说明已有 queued，不重复
            scheduleIfNeededUnlocked();
        }
    }

    /**
     * 仅在 owner false 时 enqueue 一次；owner true 只保留 pending（调用方已写）。
     * 必须在 {@link #linearizeLock} 内调用。
     */
    private void scheduleIfNeededUnlocked() {
        if (!enqueueOwner.compareAndSet(false, true)) {
            // owner 已持有：不重排，pending 已更新
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
     * 主线程消费：取走 latest pending 一次 apply；失败仅无更新时 reoffer。
     * <b>不</b>在 finally 中因仍有 pending 而同 drain 自重排——仅 release owner；
     * 新 submit（owner false）或下一 tick {@link #retryPendingOnce} 再调度。
     * 即使本方法内 enqueue，也因 dispatcher 入口预算而留 next-drain。
     */
    private void drainOnceOnClient() {
        lastDispatchThread.set(Thread.currentThread());
        dispatchRunCount.incrementAndGet();
        try {
            // apply 前取走 pending
            PendingApply p = pending.getAndSet(null);
            if (p == null) {
                needsRetry.set(false);
                return;
            }
            // 世代复核：旧任务不得回灌
            Registration cur = currentRegistration.get();
            if (cur == null
                    || p.registration.generation != cur.generation
                    || p.registration.manager != cur.manager) {
                MyMod.LOG.debug("ModernConfigApplyCoordinator drop stale pending gen={}",
                        Long.valueOf(p.registration.generation));
                needsRetry.set(false);
                return;
            }
            try {
                applyOnMainThread(p.registration.manager, p.reason);
                needsRetry.set(false);
            } catch (RuntimeException e) {
                MyMod.LOG.error("ModernConfigApplyCoordinator apply failed, will retry: reason="
                        + p.reason, e);
                reofferOnFailure(p);
            } catch (AssertionError e) {
                MyMod.LOG.error("ModernConfigApplyCoordinator apply assertion, will retry: reason="
                        + p.reason, e);
                reofferOnFailure(p);
            } catch (Error e) {
                if (e instanceof VirtualMachineError
                        || e instanceof ThreadDeath
                        || e instanceof LinkageError) {
                    throw e;
                }
                MyMod.LOG.error("ModernConfigApplyCoordinator apply error, will retry: reason="
                        + p.reason, e);
                reofferOnFailure(p);
            }
        } finally {
            // 真实 pending 检查 → owner 释放窗口：hook 在此点可 submit 最后事件
            Runnable hook = TEST_BEFORE_OWNER_RELEASE.getAndSet(null);
            if (hook != null) {
                try {
                    hook.run();
                } catch (RuntimeException e) {
                    MyMod.LOG.error("ModernConfigApplyCoordinator test hook failed", e);
                } catch (AssertionError e) {
                    MyMod.LOG.error("ModernConfigApplyCoordinator test hook assertion", e);
                }
            }
            // owner 最终释放；no-spin：不在此立即 scheduleIfNeeded
            enqueueOwner.set(false);
        }
    }

    /**
     * 失败 reoffer：仅当无更新 pending 时写回失败值；新事件优先则保留更新。
     * reoffer CAS 前后为测试 hook 窗口。
     */
    private void reofferOnFailure(PendingApply failed) {
        // 可选：在 CAS 前卡死，供 failure+new-event 精确卡在 reoffer 窗口
        Runnable beforeCas = TEST_BEFORE_REOFFER_CAS.getAndSet(null);
        if (beforeCas != null) {
            try {
                beforeCas.run();
            } catch (RuntimeException e) {
                MyMod.LOG.error("ModernConfigApplyCoordinator before-reoffer-cas hook failed", e);
            } catch (AssertionError e) {
                MyMod.LOG.error("ModernConfigApplyCoordinator before-reoffer-cas hook assertion", e);
            }
        }
        if (pending.compareAndSet(null, failed)) {
            needsRetry.set(true);
        } else {
            // 期间已有新事件：新事件优先，不覆盖；仍标记 needsRetry 以便 tick 收敛
            needsRetry.set(true);
        }
        // reoffer 完成后、owner 仍 true 的窗口
        Runnable afterReoffer = TEST_AFTER_REOFFER_BEFORE_OWNER_RELEASE.getAndSet(null);
        if (afterReoffer != null) {
            try {
                afterReoffer.run();
            } catch (RuntimeException e) {
                MyMod.LOG.error("ModernConfigApplyCoordinator after-reoffer test hook failed", e);
            } catch (AssertionError e) {
                MyMod.LOG.error("ModernConfigApplyCoordinator after-reoffer test hook assertion", e);
            }
        }
    }

    private void applyOnMainThread(ConfigManager manager, String reason) {
        RuntimeException fault = TEST_APPLY_FAULT.getAndSet(null);
        if (fault != null) {
            throw fault;
        }
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
        // last 快照仅成功 apply 后推进
        FontConfig.onConfigReload();
        MyMod.LOG.debug("配置事件主线程应用完成: reason={}", reason);
    }

    // ---- 测试探针（包级）----

    /** 当前 pending 是否非空（测试）。 */
    boolean hasPending() {
        return pending.get() != null;
    }

    /** 是否等待 tick 重试（测试）。 */
    boolean needsRetry() {
        return needsRetry.get();
    }

    /**
     * pending 是否不含 listener 引用（结构保证：PendingApply 仅 Registration+reason）。
     * 有 pending 时 registration 与 manager 非 null，且 registration 身份可核 current。
     */
    boolean pendingHoldsNoListener() {
        PendingApply p = pending.get();
        if (p == null) {
            return true;
        }
        // 结构断言：PendingApply 无 listener 字段；registration/manager/reason 齐全
        return p.registration != null
                && p.registration.manager != null
                && p.reason != null;
    }

    /** 是否已有 dispatcher 任务占位（测试）。 */
    boolean isEnqueueOwned() {
        return enqueueOwner.get();
    }

    /**
     * 最近一次 DISPATCH 执行线程（测试）；未执行过为 null。
     *
     * @return 执行线程
     */
    Thread lastDispatchThread() {
        return lastDispatchThread.get();
    }

    /**
     * DISPATCH 累计执行次数（自 reset 起，测试）。
     *
     * @return 次数
     */
    long dispatchRunCount() {
        return dispatchRunCount.get();
    }

    /**
     * 测试用：在 apply 路径注入一次失败。
     * 生产路径无。
     */
    static final AtomicReference<RuntimeException> TEST_APPLY_FAULT =
            new AtomicReference<RuntimeException>(null);

    /**
     * 包级 hook：在 pending 检查完成、owner 释放前的窗口调用（ConfigSaveListener release race）。
     * 生产为 null；测试可设置，drain 后自动清空。
     */
    static final AtomicReference<Runnable> TEST_BEFORE_OWNER_RELEASE =
            new AtomicReference<Runnable>(null);

    /**
     * 包级 hook：失败 reoffer 的 CAS <strong>之前</strong>调用，可卡在 reoffer 窗口提交新事件。
     * 生产为 null；测试可设置，触发后自动清空。
     */
    static final AtomicReference<Runnable> TEST_BEFORE_REOFFER_CAS =
            new AtomicReference<Runnable>(null);

    /**
     * 包级 hook：失败 reoffer 完成、owner 释放前调用。
     * 生产为 null；测试可设置，触发后自动清空。
     */
    static final AtomicReference<Runnable> TEST_AFTER_REOFFER_BEFORE_OWNER_RELEASE =
            new AtomicReference<Runnable>(null);

    /**
     * 包级 hook：在 register 发布新 Registration 之后、返回之前调用（stale submit 卡 B 发布后）。
     * 生产为 null；测试可设置，触发后自动清空。
     */
    static final AtomicReference<Runnable> TEST_AFTER_REGISTRATION_PUBLISH =
            new AtomicReference<Runnable>(null);

    /** 测试重置协调器状态（勿用于生产）。 */
    void resetForTest() {
        synchronized (linearizeLock) {
            pending.set(null);
            enqueueOwner.set(false);
            needsRetry.set(false);
            currentRegistration.set(null);
            // 不重置 generationSeq，保持单调
            TEST_APPLY_FAULT.set(null);
            TEST_BEFORE_OWNER_RELEASE.set(null);
            TEST_BEFORE_REOFFER_CAS.set(null);
            TEST_AFTER_REOFFER_BEFORE_OWNER_RELEASE.set(null);
            TEST_AFTER_REGISTRATION_PUBLISH.set(null);
            lastDispatchThread.set(null);
            dispatchRunCount.set(0L);
        }
    }

    /**
     * 不可变注册：generation + manager 原子一体发布。
     */
    public static final class Registration {
        final long generation;
        final ConfigManager manager;

        Registration(long generation, ConfigManager manager) {
            this.generation = generation;
            this.manager = manager;
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
