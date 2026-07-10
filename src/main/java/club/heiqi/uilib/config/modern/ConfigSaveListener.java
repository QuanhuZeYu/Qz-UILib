package club.heiqi.uilib.config.modern;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigChangeListener;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.event.FontReloadRequest;

/**
 * 新栈配置页保存回调监听器：监听 {@link ConfigChangeEvent.ChangeType#BATCH_SAVE}，
 * 触发 {@link ConfigValueBridge#applyFromAuthority} 全量回灌静态字段
 * → {@link FontConfig#affectsFontRuntime()} 判断字体配置是否变化
 * → 变化则 {@link FontService#reload(FontReloadRequest)} 重载字体系统（守 I1）
 * → {@link FontConfig#onConfigReload()} 刷 last* 快照。
 *
 * <p>语义等价迁移自旧栈 {@code club.heiqi.uilib.Config.saveAndReload()}：
 * 去掉 Forge {@code Configuration.save} + {@code load}（新栈 ConfigManager.save
 * 三阶段乐观事务已写盘并完成 Authority 引用交换），换成 Bridge 回灌；
 * 保留 affectsFontRuntime 判断 + reload + onConfigReload 三段。
 * reload reason 用 {@code "modern_config_saved"} 区分旧栈的 {@code "config_changed"}。</p>
 *
 * <h3>事件过滤</h3>
 * <p>只认 {@link ConfigChangeEvent.ChangeType#BATCH_SAVE}：这是 {@link ConfigManager#save}
 * 在事务完成并释放锁后 publish 的唯一事件（path 空、value null、
 * 无增量信息），listener 收到后做全量回灌。SET/REMOVE/CLEAR/RELOAD 等其他事件类型
 * 本监听器一律忽略。通知期间不得同步重入同一 manager.save；此类内层调用稳定返回 INVALID
 * 且不再发布事件。</p>
 *
 * <h3>守 I1</h3>
 * <p>listener → {@link FontService#reload} → {@code performReloadLocked} →
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
 *
 * <h3>线程模型</h3>
 * <p>生产路径：listener ← saveChanges（按钮 handler）← ConfigEventBus.publish 同步调用 ← Client thread，
 * {@link FontService#reload} 线程闸（{@code isCurrentThreadAllowedToReload}）能过，
 * reload 真执行。L1 测试线程非 Client thread，reload 会被静默丢弃（不崩但不真执行）。</p>
 */
public final class ConfigSaveListener implements ConfigChangeListener {

    /** reload reason，区分旧栈 "config_changed"。 */
    private static final String RELOAD_REASON = "modern_config_saved";

    private final ConfigManager manager;

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
     * 配置变更事件回调。只处理 {@link ConfigChangeEvent.ChangeType#BATCH_SAVE}，
     * 其他类型一律忽略。
     *
     * <p>BATCH_SAVE 处理流程（语义等价旧栈 Config.saveAndReload）：</p>
     * <ol>
     *   <li>{@link ConfigValueBridge#applyFromAuthority} 全量回灌静态字段</li>
     *   <li>{@link FontConfig#affectsFontRuntime()} 判断字体配置是否变化</li>
     *   <li>变化则 {@link FontService#reload}（守 I1）</li>
     *   <li>{@link FontConfig#onConfigReload()} 刷 last* 快照</li>
     * </ol>
     *
     * @param event 变更事件
     */
    @Override
    public void onConfigChanged(ConfigChangeEvent event) {
        MyMod.LOG.debug("ConfigSaveListener 收到事件: type={}", event.getType());
        // 只认批量保存（ConfigManager.save 三阶段事务提交并释放锁后 publish BATCH_SAVE）
        if (event.getType() != ConfigChangeEvent.ChangeType.BATCH_SAVE) {
            MyMod.LOG.debug("非 BATCH_SAVE 事件忽略: type={}", event.getType());
            return;
        }
        // 1. 全量回灌静态字段（C1 Bridge，不判 affectsFontRuntime/不调 reload/不刷快照）
        ConfigValueBridge.applyFromAuthority(manager.authority());
        MyMod.LOG.debug("Bridge 值回灌完成（保存回调）: fontSort.length={}, fontSortConfigured={}",
                Integer.valueOf(FontConfig.fontSort.length),
                Boolean.valueOf(FontConfig.fontSortConfigured));
        // 2. 判断字体配置是否变了
        boolean fontRuntimeChanged = FontConfig.affectsFontRuntime();
        MyMod.LOG.debug("affectsFontRuntime={}", Boolean.valueOf(fontRuntimeChanged));
        // 3. 变了则重载字体系统（守 I1：reload → invalidateAll 失效注册表，非命令式改节点）
        if (fontRuntimeChanged) {
            MyMod.LOG.info("保存触发字体 reload: reason={}", RELOAD_REASON);
            FontService.getInstance().reload(new FontReloadRequest(RELOAD_REASON));
        }
        // 4. 刷 last* 快照
        FontConfig.onConfigReload();
        MyMod.LOG.debug("保存回调处理完成");
    }
}
