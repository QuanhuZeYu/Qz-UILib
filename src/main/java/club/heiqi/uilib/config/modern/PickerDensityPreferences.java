package club.heiqi.uilib.config.modern;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.search.PickerDensityPreference;

/**
 * 选择器密度偏好的<b>进程级信号载体</b>——P5 §1.4「配置 → 信号 → 面板」链路的中间段。
 *
 * <p>P5 把三档密度（{@code compact32 / standard40 / roomy48} + {@code auto} 阶梯）做成了
 * {@code ScenePickerPanel.Props.densityPreference(ReadableSignal)} 这一<b>动态注入面</b>，
 * 但当时没有任何装配点传入偏好，面板恒为 {@link PickerDensityPreference#AUTO}（能力空洞）。
 * 本类是补上该空洞的 UILib 侧落点：把配置项 {@code general.pickerDensity} 的值变成一条
 * 可失效的信号，供面板经依赖追踪消费。</p>
 *
 * <h3>形态（为什么是信号而不是静态字段）</h3>
 * <ul>
 *   <li><b>变更信号</b>：{@link #signal()} 是进程级唯一实例，面板侧只读消费；配置保存 / 磁盘热更
 *       只改它的值，<b>不重建</b>它，也不重建任何面板（改档位无需关闭重开配置页或选择器）。</li>
 *   <li><b>帧末口径</b>：写入口 {@link #applyConfigured(String)} 经
 *       {@link club.heiqi.uilib.ui.reactive.ReactiveScheduler} 批处理，与仓库内所有 signal 写入同源
 *       （守 I9「一帧一写入合并」）；宿主帧末 flush 后新值生效并驱动下游重派生。</li>
 *   <li><b>失效通道</b>：{@code null} / 空白 / 未知名称一律回落 {@link PickerDensityPreference#AUTO}
 *       （= 现状档），并留下 WARN —— 配置坏值不得让 UI 起不来，也不得静默吞掉。</li>
 * </ul>
 *
 * <h3>生命周期</h3>
 * <p>归属 = UILib 配置接入层（{@code uilib.config.modern}）。信号本体与进程同寿命
 * （{@code static final}，不随配置页 / 选择器面板开关增删），<b>不持任何面板/节点引用</b>；
 * 面板侧的依赖边由面板自身 Owner 持有，随面板 dispose 自动解绑 ⇒ 无跨面板泄漏，
 * 故本类不需要（也不提供）显式释放。通用装配层侧的接线可撤：见
 * {@link club.heiqi.config.ui.field.PickerDensityPreferenceSource#release()}。</p>
 *
 * <h3>唯一写入口</h3>
 * <p>{@link ConfigValueBridge#applyFromAuthority} 在三条既有配置通道上回灌本信号：
 * 启动加载（{@link ModernConfigBootstrap}）、保存回调与磁盘重载（{@link ConfigSaveListener} →
 * {@link ModernConfigApplyCoordinator}）、配置页打开时的 initial apply（register）。
 * <b>不存在第二套事件</b>：档位不另建监听器，也不逐帧轮询。</p>
 */
public final class PickerDensityPreferences {

    /** 配置项全路径（schema 声明处，唯一真值来源）。 */
    public static final String CONFIG_PATH = "general.pickerDensity";

    /**
     * 进程级唯一偏好信号：初值 {@link PickerDensityPreference#AUTO}
     * （未配置/读取失败 = 现状档，行为与 P5 之前逐值一致）。
     */
    private static final Signal<PickerDensityPreference> SIGNAL =
            Signal.create(PickerDensityPreference.AUTO);

    private PickerDensityPreferences() {
    }

    /**
     * 偏好只读信号（面板经 {@code Props.densityPreference(...)} 消费）。
     *
     * <p>返回的实例是进程级单例：调用方<b>不得</b>据此创建/替换信号，也不得在只读消费处写入。
     * 每次调用返回同一对象，故「每次开面板重建信号」在这一形态下不可能发生。</p>
     *
     * @return 非 null 的偏好只读信号
     */
    public static ReadableSignal<PickerDensityPreference> signal() {
        return SIGNAL;
    }

    /**
     * 配置值 → 偏好（大小写不敏感、忽略首尾空白）；{@code null}/空白/未知名称回落 {@code auto}。
     *
     * <p>纯函数、可单独复用（例如接入方自行解析同名档位）；非法值走 WARN 失效通道，
     * 便于现场定位手改配置的错误。</p>
     *
     * @param configuredName 配置里的档位名（可为 null）
     * @return 非 null 偏好；无法解析时为 {@link PickerDensityPreference#AUTO}
     */
    public static PickerDensityPreference resolve(String configuredName) {
        PickerDensityPreference parsed = PickerDensityPreference.byName(configuredName);
        if (parsed == PickerDensityPreference.AUTO && configuredName != null) {
            String trimmed = configuredName.trim();
            if (!trimmed.isEmpty() && !PickerDensityPreference.AUTO.name().equalsIgnoreCase(trimmed)) {
                MyMod.LOG.warn("配置项 {} 的值 \"{}\" 不是合法档位（auto/compact/standard/roomy），"
                        + "已回落 auto（现状档）", CONFIG_PATH, configuredName);
            }
        }
        return parsed;
    }

    /**
     * 回灌入口：把配置里的档位名写进进程级信号（唯一写入口，由
     * {@link ConfigValueBridge#applyFromAuthority} 调用）。
     *
     * <p>写入经 {@link Signal#set(Object)} 进调度器，帧末 flush 生效并只通知真正变化的订阅者
     * （同值写入被净变化判据吸收，不产生空转重派生）。</p>
     *
     * @param configuredName 配置里的档位名（可为 null = 未配置 → auto）
     */
    public static void applyConfigured(String configuredName) {
        SIGNAL.set(resolve(configuredName));
    }

    /**
     * 测试隔离：把进程级信号复位为 {@link PickerDensityPreference#AUTO}。
     *
     * <p>包级可见（生产调用方只有 {@link #applyConfigured(String)}）；写入同样走调度器，
     * 调用方需自行 flush 后断言。</p>
     */
    static void resetForTest() {
        SIGNAL.set(PickerDensityPreference.AUTO);
    }
}
