package club.heiqi.uilib.config.modern;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigChangeListener;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.event.FontReloadRequest;
import club.heiqi.uilib.net.core.MainThreadDispatcher;
import club.heiqi.uilib.net.transport.NetSide;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 新栈配置页保存/重载回调监听器：监听
 * {@link ConfigChangeEvent.ChangeType#BATCH_SAVE} 与
 * {@link ConfigChangeEvent.ChangeType#RELOAD}，
 * 在<strong>客户端主线程</strong>触发 {@link ConfigValueBridge#applyFromAuthority} 全量回灌静态字段
 * → {@link FontConfig#affectsFontRuntime()} 判断字体配置是否变化
 * → 变化则 {@link FontService#reload(FontReloadRequest)} 重载字体系统（守 I1）
 * → {@link FontConfig#onConfigReload()} 刷 last* 快照。
 *
 * <p>语义等价迁移自旧栈 {@code club.heiqi.uilib.Config.saveAndReload()}：
 * 去掉 Forge {@code Configuration.save} + {@code load}（新栈 ConfigManager.save
 * 三阶段乐观事务已写盘并完成 Authority 引用交换），换成 Bridge 回灌；
 * 保留 affectsFontRuntime 判断 + reload + onConfigReload 三段。
 * reload reason 用 {@code "modern_config_saved"} / {@code "modern_config_reloaded"}
 * 区分保存与磁盘重载，并区分旧栈的 {@code "config_changed"}。</p>
 *
 * <h3>事件过滤</h3>
 * <p>认 {@link ConfigChangeEvent.ChangeType#BATCH_SAVE} 与
 * {@link ConfigChangeEvent.ChangeType#RELOAD}：二者都从已提交的 Authority 回灌运行态，
 * 但语义区分——BATCH_SAVE 来自 save 成功，RELOAD 来自 {@code reloadDraftFromDisk} 成功
 *（不得伪装为 BATCH_SAVE）。SET/REMOVE/CLEAR 等其他事件类型本监听器一律忽略。
 * 通知期间不得同步重入同一 manager.save/flushRaw/reload；此类内层调用稳定
 * {@code SAVE_DURING_NOTIFICATION} 且不再发布事件。</p>
 *
 * <p><b>他 mod 消费者</b>：若自写 listener，必须显式处理 RELOAD（与 BATCH_SAVE 同样回灌
 * 或按业务分流）；忽略 RELOAD 会导致磁盘重载后运行态陈旧。Qz-Miner 适配留后续发布后接入。</p>
 *
 * <h3>线程模型（主线程回灌）</h3>
 * <p>event 回调<strong>不</strong>直接 Bridge/font。用 {@link MainThreadDispatcher} CLIENT 队列：
 * per-listener {@link AtomicReference} 保留 latest reason/event + {@link AtomicBoolean} owner，
 * 最多一个排队任务；主线程消费时读取 manager 最新 Authority，apply bridge→font affects→reload→last snapshot。
 * submit/drain 释放竞态不丢最后事件（latest-wins）；同线程也统一入队。
 * 只有实际主线程应用后推进 last 快照。</p>
 *
 * <h3>守 I1</h3>
 * <p>listener → 主线程 apply → {@link FontService#reload} → {@code performReloadLocked} →
 * {@code UiLayoutInvalidationRegistry.invalidateAll}（失效注册表，非命令式改节点）。
 * Bridge 写静态字段是配置数据模型层（非 SceneNode 属性槽，非 UI 状态），I1 守。</p>
 *
 * <h3>生命周期与泄漏防护（P1 约束）</h3>
 * <p>本 listener 由 {@link ModernConfigEntry#createScreen} 内 {@code new ConfigSaveListener(manager)}
 * 创建并 {@code manager.eventBus().subscribe(...)} 挂载。每次打开配置页都会
 * new ConfigManager（{@link ConfigManager#bootstrap}）+ 新 listener + 新 eventBus；
 * 新 manager 的 listener 只挂在新 eventBus 上，旧 manager 连同其 eventBus/listener
 * 一起被 GC，<b>不累积泄漏</b>。</p>
 *
 * <p><b>守住：listener 不被任何静态注册表持有</b>。若未来需要在静态表登记 listener，
 * 必须配套 unsubscribe 或弱引用，否则会泄漏。</p>
 */
public final class ConfigSaveListener implements ConfigChangeListener {

    /** save 路径 reload reason，区分旧栈 "config_changed"。 */
    private static final String RELOAD_REASON_SAVED = "modern_config_saved";
    /** 磁盘 reload 路径 reason。 */
    private static final String RELOAD_REASON_RELOADED = "modern_config_reloaded";

    private final ConfigManager manager;

    /** latest-wins：排队任务消费时读取的 reason（SAVED/RELOADED）。 */
    private final AtomicReference<String> pendingReason = new AtomicReference<String>(null);
    /** 是否已有任务在 CLIENT 队列中（最多一个）。 */
    private final AtomicBoolean enqueueOwner = new AtomicBoolean(false);

    /**
     * 构造保存回调监听器。
     *
     * @param manager 新栈配置管理器（用于拿 {@link ConfigManager#authority()} 权威源；
     *                oracle 裁决传 ConfigManager 而非 Authority，因 {@code authority()}
     *                是稳定门面）
     */
    public ConfigSaveListener(ConfigManager manager) {
        this.manager = manager;
    }

    /**
     * 配置变更事件回调。处理 {@link ConfigChangeEvent.ChangeType#BATCH_SAVE} 与
     * {@link ConfigChangeEvent.ChangeType#RELOAD}，其他类型一律忽略。
     *
     * <p>本方法<strong>不</strong>直接 Bridge/font；写入 latest reason 并最多入队一个主线程任务。</p>
     *
     * @param event 变更事件
     */
    @Override
    public void onConfigChanged(ConfigChangeEvent event) {
        MyMod.LOG.debug("ConfigSaveListener 收到事件: type={}", event.getType());
        ConfigChangeEvent.ChangeType type = event.getType();
        if (type != ConfigChangeEvent.ChangeType.BATCH_SAVE
                && type != ConfigChangeEvent.ChangeType.RELOAD) {
            MyMod.LOG.debug("非 BATCH_SAVE/RELOAD 事件忽略: type={}", type);
            return;
        }
        String reason = type == ConfigChangeEvent.ChangeType.RELOAD
                ? RELOAD_REASON_RELOADED
                : RELOAD_REASON_SAVED;
        // latest-wins：覆盖 pending，再尝试占有入队权
        pendingReason.set(reason);
        scheduleApplyOnClient();
    }

    /**
     * 若尚无排队任务则入队一个；已有任务时仅 latest reason 被覆盖，由在途任务 drain 时读到。
     */
    private void scheduleApplyOnClient() {
        if (!enqueueOwner.compareAndSet(false, true)) {
            // 已有任务在途：pendingReason 已更新，在途任务会再读
            return;
        }
        MainThreadDispatcher.getInstance().enqueue(NetSide.CLIENT, new Runnable() {
            @Override
            public void run() {
                drainPendingOnClient();
            }
        });
    }

    /**
     * 主线程消费：循环读 latest reason 直到清空，处理 submit/drain 竞态不丢最后事件。
     * 每次应用读 manager 最新 Authority。
     */
    private void drainPendingOnClient() {
        try {
            while (true) {
                String reason = pendingReason.getAndSet(null);
                if (reason == null) {
                    break;
                }
                applyOnMainThread(reason);
            }
        } finally {
            // 释放 owner；若 release 与新 submit 竞态，补排一次
            enqueueOwner.set(false);
            if (pendingReason.get() != null) {
                scheduleApplyOnClient();
            }
        }
    }

    /**
     * 主线程实际应用：Bridge → font affects → reload → last snapshot。
     *
     * @param reason modern_config_saved / modern_config_reloaded
     */
    private void applyOnMainThread(String reason) {
        // 1. 全量回灌静态字段（读最新 Authority）
        ConfigValueBridge.applyFromAuthority(manager.authority());
        MyMod.LOG.debug("Bridge 值回灌完成（{}）: fontSort.length={}, fontSortConfigured={}",
                reason,
                Integer.valueOf(FontConfig.fontSort.length),
                Boolean.valueOf(FontConfig.fontSortConfigured));
        // 2. 判断字体配置是否变了
        boolean fontRuntimeChanged = FontConfig.affectsFontRuntime();
        MyMod.LOG.debug("affectsFontRuntime={}", Boolean.valueOf(fontRuntimeChanged));
        // 3. 变了则重载字体系统（守 I1）
        if (fontRuntimeChanged) {
            MyMod.LOG.info("配置事件触发字体 reload: reason={}", reason);
            FontService.getInstance().reload(new FontReloadRequest(reason));
        }
        // 4. 刷 last* 快照（仅主线程实际应用后推进）
        FontConfig.onConfigReload();
        MyMod.LOG.debug("配置事件主线程应用完成: reason={}", reason);
    }
}
