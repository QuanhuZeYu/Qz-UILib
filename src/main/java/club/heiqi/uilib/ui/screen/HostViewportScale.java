package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.ui.scene.layout.LogicalBox;

/**
 * HostViewportScale —— 宿主视口缩放的<b>唯一合成点</b>（P5 §1.1.1）。
 *
 * <h3>为什么必须独立成一个类</h3>
 * <p>Qz-UILib AGENTS.md:28 的铁律是「Minecraft GUI Scale 不得混入内部闭环，缩放只在 host 边界
 * 成对转换」。把这段算术散落在 {@code drawScreen} 里，下一个维护者很容易"顺手"在控件里再读一次
 * {@code mc.gameSettings.guiScale}。把它收敛到一个文件、一个方法，内部闭环就只剩
 * {@link LogicalBox} 一个尺寸事实 —— 源码守卫可以对着这一个文件扫描 GUI Scale 符号。</p>
 *
 * <h3>策略（Q1 裁决：policy A）</h3>
 * <p>{@link #POLICY} = {@link #POLICY_NATIVE}：{@code uiScaleHost = 1}，逻辑盒 = 原生盒。
 * 这是现状口径，改动面为零；用户的 GUI Scale 目前只影响指针换算，不影响 UILib 尺寸。
 * policy B（折算 GUI Scale）在 {@link #composeFolded} 中给出可复算的实现与语义，但它<b>不是</b>
 * 默认值 —— 启用它属于宿主边界行为变更，需要用户另行确认（P5 §7-Q1 / X-3）。</p>
 *
 * <h3>与指针换算的成对性</h3>
 * <p>指针从 scaled 坐标换到本文件产出的逻辑坐标，用的是同一个 {@code uiScaleHost}
 * （见 {@link McScreenBridge}）。「成对转换」的含义就是：尺寸与指针必须同乘/同除同一个因子，
 * 任何一方单独改变都会立刻表现为命中偏移。</p>
 */
public final class HostViewportScale {

    /** policy A：逻辑盒 = 原生盒（现状口径，默认）。 */
    public static final int POLICY_NATIVE = 1;
    /** policy B：逻辑盒 = 原生盒 ÷ 折算后的 GUI Scale。 */
    public static final int POLICY_FOLDED = 2;

    /** 当前生效的宿主缩放策略（默认 policy A；改它属宿主边界行为变更，需用户确认）。 */
    public static final int POLICY = POLICY_NATIVE;

    /** policy B 的折算下界：逻辑盒不得被折算到该宽以下。 */
    public static final int FOLD_MIN_WIDTH = 1280;
    /** policy B 的折算下界：逻辑盒不得被折算到该高以下。 */
    public static final int FOLD_MIN_HEIGHT = 720;

    private HostViewportScale() {
    }

    /**
     * 合成逻辑盒（按 {@link #POLICY} 分派）。
     *
     * @param nativeWidthPx   原生窗口宽（物理像素；&lt;1 按 1）
     * @param nativeHeightPx  原生窗口高（物理像素；&lt;1 按 1）
     * @param guiScaleFactor  MC 的 GUI Scale（&lt;1 按 1）
     * @return 逻辑盒（非 null）
     */
    public static LogicalBox compose(int nativeWidthPx, int nativeHeightPx, int guiScaleFactor) {
        int w = Math.max(1, nativeWidthPx);
        int h = Math.max(1, nativeHeightPx);
        int guiScale = Math.max(1, guiScaleFactor);
        return POLICY == POLICY_FOLDED ? composeFolded(w, h, guiScale) : new LogicalBox(w, h);
    }

    /**
     * policy B 折算：{@code uiScaleHost = min(guiScale, max(1, min(floor(nativeW/1280), floor(nativeH/720))))}。
     *
     * <p>语义 = 「GUI Scale 决定 UI 元素的物理大小，但折算不得把逻辑盒压到 1280×720 以下」。
     * 折算后逻辑盒变小是"用户选择更大 UI"的必然结果（每单元占更多物理像素），
     * 红线 R1 的参照系始终是<b>逻辑盒</b>。</p>
     *
     * @param nativeWidthPx  原生窗口宽（≥1）
     * @param nativeHeightPx 原生窗口高（≥1）
     * @param guiScaleFactor GUI Scale（≥1）
     * @return 折算后的逻辑盒
     */
    public static LogicalBox composeFolded(int nativeWidthPx, int nativeHeightPx,
                                           int guiScaleFactor) {
        int w = Math.max(1, nativeWidthPx);
        int h = Math.max(1, nativeHeightPx);
        int guiScale = Math.max(1, guiScaleFactor);
        int headroom = Math.max(1, Math.min(w / FOLD_MIN_WIDTH, h / FOLD_MIN_HEIGHT));
        int uiScaleHost = Math.max(1, Math.min(guiScale, headroom));
        return new LogicalBox(Math.max(1, w / uiScaleHost), Math.max(1, h / uiScaleHost));
    }
}
