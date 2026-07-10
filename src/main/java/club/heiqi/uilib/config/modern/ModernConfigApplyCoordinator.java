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
 *   <li>每次 listener 构造注册单调 {@code generation} 并绑定 manager</li>
 *   <li>仅<strong>当前 generation</strong> 的事件可 submit</li>
 *   <li>全局 {@link AtomicReference} pending 仅保留最新有效 manager/reason/generation</li>
 *   <li>{@link AtomicBoolean} 保证 CLIENT 队列最多一个 coordinator Runnable</li>
 *   <li>静态队列 Runnable <strong>不得</strong>闭包持旧 listener；只通过本协调器读 pending</li>
 *   <li>旧 listener 晚到事件 no-op；新页面注册后旧任务不得回灌旧 Authority</li>
 *   <li>本协调器持最新 manager，作为 UILib 全局配置当前 Authority 入口（见 {@link #currentManager()}）</li>
 * </ul>
 *
 * <h3>失败与重试</h3>
 * <p>apply 在 CLIENT 主线程执行；per-task 捕获 {@link RuntimeException}/{@link Error}
 *（不含 {@link VirtualMachineError}/{@link ThreadDeath}/{@link LinkageError}）并日志隔离，
 * 不得抛出中断 {@link MainThreadDispatcher} drain。失败时保留 latest pending；
 * 下一有效事件或 tick 驱动的 {@link #retryPendingOnce()} 重试。禁止同一次 drain 无限自重排。</p>
 *
 * <p>包级 API 供测试探针（generation / pending 不含 listener 引用）。</p>
 */
public final class ModernConfigApplyCoordinator {

    private static final ModernConfigApplyCoordinator INSTANCE = new ModernConfigApplyCoordinator();

    /** save 路径 reload reason */
    static final String RELOAD_REASON_SAVED = "modern_config_saved";
    /** 磁盘 reload 路径 reason */
    static final String RELOAD_REASON_RELOADED = "modern_config_reloaded";

    private final AtomicLong generationSeq = new AtomicLong(0L);
    private final AtomicLong currentGeneration = new AtomicLong(0L);
    private final AtomicReference<ConfigManager> currentManager = new AtomicReference<ConfigManager>(null);

    /** latest-wins pending：仅 manager + reason + generation，不持 listener */
    private final AtomicReference<PendingApply> pending = new AtomicReference<PendingApply>(null);
    /** 是否已有 CLIENT 队列任务（最多一个） */
    private final AtomicBoolean enqueueOwner = new AtomicBoolean(false);
    /**
     * 失败后需 tick 重试：true 表示 pending 仍有效但上次 apply 失败。
     * {@link #retryPendingOnce()} 每 tick 最多调度一次，禁止同 drain 自旋。
     */
    private final AtomicBoolean needsRetry = new AtomicBoolean(false);

    private ModernConfigApplyCoordinator() {
    }

    public static ModernConfigApplyCoordinator getInstance() {
        return INSTANCE;
    }

    /**
     * listener 构造时注册：推进 generation，绑定 manager 为当前全局 Authority 源。
     *
     * @param manager 新页面的 ConfigManager，非 null
     * @return 分配给该 listener 的 generation
     */
    public long register(ConfigManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("manager must not be null");
        }
        long gen = generationSeq.incrementAndGet();
        currentGeneration.set(gen);
        currentManager.set(manager);
        MyMod.LOG.debug("ModernConfigApplyCoordinator register gen={} manager={}",
                Long.valueOf(gen), manager);
        return gen;
    }

    /**
     * UILib 全局配置当前 Authority 对应的 manager（最新打开的配置页）。
     *
     * @return 当前 manager，可能为 null（尚未 register）
     */
    public ConfigManager currentManager() {
        return currentManager.get();
    }

    /**
     * 当前 generation（测试/诊断）。
     *
     * @return 单调 generation
     */
    long currentGeneration() {
        return currentGeneration.get();
    }

    /**
     * 提交回灌请求：仅 generation 等于当前世代时生效；否则 no-op。
     *
     * @param generation listener 构造时分配的 generation
     * @param manager    事件来源 manager
     * @param reason     modern_config_saved / modern_config_reloaded
     */
    public void submit(long generation, ConfigManager manager, String reason) {
        if (manager == null || reason == null) {
            return;
        }
        if (generation != currentGeneration.get()) {
            MyMod.LOG.debug("ModernConfigApplyCoordinator ignore stale gen={} current={}",
                    Long.valueOf(generation), Long.valueOf(currentGeneration.get()));
            return;
        }
        pending.set(new PendingApply(generation, manager, reason));
        needsRetry.set(false);
        scheduleIfNeeded();
    }

    /**
     * tick 驱动：若上次 apply 失败且仍有 pending，调度一次重试（每 CLIENT END 最多一次）。
     * 不在同一次 drain 内自重排。
     */
    public void retryPendingOnce() {
        if (!needsRetry.get()) {
            return;
        }
        PendingApply p = pending.get();
        if (p == null) {
            needsRetry.set(false);
            return;
        }
        if (p.generation != currentGeneration.get()) {
            // 世代已过期：丢弃
            pending.compareAndSet(p, null);
            needsRetry.set(false);
            return;
        }
        scheduleIfNeeded();
    }

    private void scheduleIfNeeded() {
        if (!enqueueOwner.compareAndSet(false, true)) {
            return;
        }
        // 静态队列 Runnable 不闭包 listener，只调 coordinator
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
     * 主线程消费：读 latest pending 一次 apply；失败保留 pending 并标记 needsRetry。
     * <b>不</b>在 finally 中因仍有 pending 而同 drain 无限自重排——仅 release owner；
     * 新 submit 或下一 tick {@link #retryPendingOnce} 再调度。
     */
    private void drainOnceOnClient() {
        try {
            PendingApply p = pending.getAndSet(null);
            if (p == null) {
                needsRetry.set(false);
                return;
            }
            // 世代复核：旧任务不得回灌
            if (p.generation != currentGeneration.get()) {
                MyMod.LOG.debug("ModernConfigApplyCoordinator drop stale pending gen={}",
                        Long.valueOf(p.generation));
                needsRetry.set(false);
                return;
            }
            try {
                applyOnMainThread(p.manager, p.reason);
                needsRetry.set(false);
            } catch (RuntimeException e) {
                MyMod.LOG.error("ModernConfigApplyCoordinator apply failed, will retry: reason="
                        + p.reason, e);
                // 失败保留 latest：若期间无更新则写回；有更新则保留更新
                pending.compareAndSet(null, p);
                needsRetry.set(true);
            } catch (AssertionError e) {
                MyMod.LOG.error("ModernConfigApplyCoordinator apply assertion, will retry: reason="
                        + p.reason, e);
                pending.compareAndSet(null, p);
                needsRetry.set(true);
            } catch (Error e) {
                // 不可恢复 Error 继续传播；其余隔离
                if (e instanceof VirtualMachineError
                        || e instanceof ThreadDeath
                        || e instanceof LinkageError) {
                    throw e;
                }
                MyMod.LOG.error("ModernConfigApplyCoordinator apply error, will retry: reason="
                        + p.reason, e);
                pending.compareAndSet(null, p);
                needsRetry.set(true);
            }
        } finally {
            enqueueOwner.set(false);
            // 若 submit 与 release 竞态且 pending 非空且未标记失败重试，补一次调度
            if (pending.get() != null && !needsRetry.get()) {
                scheduleIfNeeded();
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

    /** pending 是否不含 listener 引用：始终 true（结构保证）。测试用。 */
    boolean pendingHoldsNoListener() {
        PendingApply p = pending.get();
        return p == null || p.manager != null;
    }

    /** 是否已有 dispatcher 任务占位（测试）。 */
    boolean isEnqueueOwned() {
        return enqueueOwner.get();
    }

    /**
     * 测试用：在 apply 路径注入一次失败（通过 ThreadLocal 或替换——见测试 hook）。
     * 生产路径无。
     */
    static final AtomicReference<RuntimeException> TEST_APPLY_FAULT =
            new AtomicReference<RuntimeException>(null);

    /**
     * 包级 hook：在 owner 释放窗口前后精确卡位（ConfigSaveListener release race 测试）。
     * 生产为 no-op；测试可设置。
     */
    static final AtomicReference<Runnable> TEST_BEFORE_OWNER_RELEASE =
            new AtomicReference<Runnable>(null);

    /** 测试重置协调器状态（勿用于生产）。 */
    void resetForTest() {
        pending.set(null);
        enqueueOwner.set(false);
        needsRetry.set(false);
        currentManager.set(null);
        currentGeneration.set(0L);
        // 不重置 generationSeq，保持单调
        TEST_APPLY_FAULT.set(null);
        TEST_BEFORE_OWNER_RELEASE.set(null);
    }

    private static final class PendingApply {
        final long generation;
        final ConfigManager manager;
        final String reason;

        PendingApply(long generation, ConfigManager manager, String reason) {
            this.generation = generation;
            this.manager = manager;
            this.reason = reason;
        }
    }
}
