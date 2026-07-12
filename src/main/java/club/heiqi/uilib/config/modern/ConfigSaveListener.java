package club.heiqi.uilib.config.modern;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigChangeListener;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.uilib.MyMod;

/**
 * 新栈配置页保存/重载回调监听器：监听
 * {@link ConfigChangeEvent.ChangeType#BATCH_SAVE} 与
 * {@link ConfigChangeEvent.ChangeType#RELOAD}，
 * 委托 {@link ModernConfigApplyCoordinator} 在<strong>客户端主线程</strong>触发
 * {@link ConfigValueBridge#applyFromAuthority} 全量回灌静态字段
 * → {@link club.heiqi.uilib.font.config.FontConfig#affectsFontRuntime()} 判断字体配置是否变化
 * → 变化则字体 reload → last* 快照。
 *
 * <p>语义等价迁移自旧栈 {@code club.heiqi.uilib.Config.saveAndReload()}：
 * 去掉 Forge {@code Configuration.save} + {@code load}，换成 Bridge 回灌；
 * reload reason 用 {@code "modern_config_saved"} / {@code "modern_config_reloaded"}
 * 区分保存与磁盘重载。</p>
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
 * <h3>跨 listener 全局协调（Registration）</h3>
 * <p>构造时向 {@link ModernConfigApplyCoordinator} 注册不可变 {@link ModernConfigApplyCoordinator.Registration}
 *（generation + manager）。仅当前 Registration 事件可 submit；旧 listener 晚事件 no-op；
 * 新页面后旧任务不得回灌。协调器持最新 manager 作为 UILib 全局配置当前 Authority 入口。</p>
 *
 * <h3>线程模型</h3>
 * <p>event 回调<strong>不</strong>直接 Bridge/font；写入 coordinator pending 并最多入队一个
 * 主线程任务。静态队列 Runnable 不闭包本 listener。</p>
 *
 * <h3>生命周期</h3>
 * <p>本 listener 由 {@link ModernConfigEntry#createScreen} 内
 * {@code new ConfigSaveListener(manager)} 创建并 subscribe。每次打开配置页都会
 * new ConfigManager + 新 listener（新 Registration）；旧 generation 事件自动失效。
 * listener 不被静态表强持（pending 只持 Registration/reason）。</p>
 */
public final class ConfigSaveListener implements ConfigChangeListener {

    private final ConfigManager manager;
    /** 构造时分配的 Registration；仅等于 coordinator 当前世代时可 submit */
    private final ModernConfigApplyCoordinator.Registration registration;

    /**
     * 构造保存回调监听器并注册到全局协调器。
     *
     * @param manager 新栈配置管理器（用于拿 {@link ConfigManager#authority()} 权威源）
     */
    public ConfigSaveListener(ConfigManager manager) {
        if (manager == null) {
            throw new IllegalArgumentException("manager must not be null");
        }
        this.manager = manager;
        this.registration = ModernConfigApplyCoordinator.getInstance().register(manager);
    }

    /**
     * 本 listener 的 generation（测试用）。
     *
     * @return generation
     */
    long generation() {
        return registration.generation();
    }

    /**
     * 本 listener 的 Registration（测试用）。
     *
     * @return Registration
     */
    ModernConfigApplyCoordinator.Registration registration() {
        return registration;
    }

    /**
     * 配置变更事件回调。处理 {@link ConfigChangeEvent.ChangeType#BATCH_SAVE} 与
     * {@link ConfigChangeEvent.ChangeType#RELOAD}，其他类型一律忽略。
     *
     * <p>本方法<strong>不</strong>直接 Bridge/font；委托协调器 submit。</p>
     *
     * @param event 变更事件
     */
    @Override
    public void onConfigChanged(ConfigChangeEvent event) {
        MyMod.LOG.debug("ConfigSaveListener 收到事件: type={} gen={}", event.getType(),
                Long.valueOf(registration.generation()));
        ConfigChangeEvent.ChangeType type = event.getType();
        if (type != ConfigChangeEvent.ChangeType.BATCH_SAVE
                && type != ConfigChangeEvent.ChangeType.RELOAD) {
            MyMod.LOG.debug("非 BATCH_SAVE/RELOAD 事件忽略: type={}", type);
            return;
        }
        String reason = type == ConfigChangeEvent.ChangeType.RELOAD
                ? ModernConfigApplyCoordinator.RELOAD_REASON_RELOADED
                : ModernConfigApplyCoordinator.RELOAD_REASON_SAVED;
        ModernConfigApplyCoordinator.getInstance().submit(registration, reason);
    }
}
