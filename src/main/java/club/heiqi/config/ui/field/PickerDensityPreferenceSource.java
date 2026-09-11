package club.heiqi.config.ui.field;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.search.PickerDensityPreference;

/**
 * 密度偏好<b>源接入点</b>（通用装配层的接线缝）：把「谁提供偏好」与「面板怎么用偏好」解耦。
 *
 * <h3>为什么需要这条缝</h3>
 * <p>{@link SearchPickerFieldSupport} 是 config 核心层里的通用装配件，被任意 mod 的配置页复用；
 * 它<b>不得</b>反向 import 某个 mod 的配置接入包（{@code club.heiqi.uilib.config.modern}）。
 * 因此通用层只承认「有一个可选的偏好源」这件事，具体来源由接入方在启动装配期
 * {@link #install(ReadableSignal)} 注入（UILib 自身在
 * {@code ModernConfigBootstrap#bootstrapAndApply} 内安装）。</p>
 *
 * <h3>未接线 = 现状（AUTO）</h3>
 * <p>{@link #installed()} 在未安装时返回 {@code null}，面板据此走
 * {@link PickerDensityPreference#AUTO}（P5 既有语义：{@code null} 偏好 = 自动档），
 * 与接线前的行为逐值一致；因此本缝对既有调用方是纯加法。</p>
 *
 * <h3>生命周期与可释放性</h3>
 * <ul>
 *   <li>本类<b>只持一个引用</b>（{@code volatile} 字段），不订阅、不加监听、不复制值：
 *       源信号的生命周期完全归其提供方（UILib 侧为进程级
 *       {@code PickerDensityPreferences.signal()}），面板读取时在自身 Owner 内建立依赖边，
 *       面板 dispose 即解绑 ⇒ 无泄漏。</li>
 *   <li>{@link #release()} 撤线（回到未接线 = AUTO）：供接入方在源生命周期结束时断开，
 *       以及测试隔离用。<b>不</b>随屏幕/面板开关调用 —— 它不代表「关一次 UI 就撤一次」。</li>
 * </ul>
 *
 * <h3>线程模型</h3>
 * <p>安装/撤线发生在装配期与主线程（{@code preInit} / 客户端线程）；字段为 {@code volatile}，
 * 跨线程可见性由它保证。写入偏好值不经过本类（那是信号自身的事）。</p>
 */
public final class PickerDensityPreferenceSource {

    /** 已安装的偏好源；{@code null} = 未接线（面板按 AUTO 处理）。 */
    private static volatile ReadableSignal<PickerDensityPreference> installed;

    private PickerDensityPreferenceSource() {
    }

    /**
     * 安装（或替换）偏好源。可重复调用：后装者生效（装配期换线语义）。
     *
     * @param source 偏好只读信号，非 null
     * @throws IllegalArgumentException source 为 null（撤线请用 {@link #release()}，
     *                                  避免把「忘记接线」与「显式撤线」写成同一个调用）
     */
    public static void install(ReadableSignal<PickerDensityPreference> source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null; use release() to unwire");
        }
        installed = source;
    }

    /**
     * 当前已安装的偏好源。
     *
     * @return 偏好只读信号；未接线时为 {@code null}（面板按 {@link PickerDensityPreference#AUTO} 处理）
     */
    public static ReadableSignal<PickerDensityPreference> installed() {
        return installed;
    }

    /** @return 是否已接线（诊断/守卫用） */
    public static boolean isInstalled() {
        return installed != null;
    }

    /** 撤线：回到未接线（面板按 {@link PickerDensityPreference#AUTO} 处理）。 */
    public static void release() {
        installed = null;
    }
}
