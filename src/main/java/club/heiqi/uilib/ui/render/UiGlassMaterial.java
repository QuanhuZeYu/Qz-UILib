package club.heiqi.uilib.ui.render;

/**
 * iOS 风格磨玻璃材质档。
 *
 * <p>对应 {@code UIVisualEffectView} 的系统材质分级。每档封装一组经验材质参数
 * （vibrancy 强度、tint 蒙层、亮度补偿、边缘亮边、内侧柔光/暗带、抗 banding 噪点），
 * 调用方只表达"这是薄玻璃还是厚玻璃、浅色还是深色"，不逐个调 shader uniform。</p>
 *
 * <p>与"只传一个 saturation 倍率"的旧入口区别：旧入口是线性饱和度乘子，亮部一起
 * 过曝、暗部几乎不变，是廉价感的来源；本材质档走亮度域保护式 vibrancy，并按
 * vibrancy -> tint 蒙层 -> 亮度偏置 -> 边缘亮边 -> 噪点 的固定顺序合成。</p>
 *
 * <p>取值依据（2026-09-01 调研，均为对 iOS 截图/取色的公开复现，Apple 未公开官方
 * 数值表）：Light 材质蒙层基色白 0.72、alpha 0.3（有效叠加约 0.216）；Dark 材质
 * 蒙层基色是 <strong>systemBlack 0.096 而非纯黑</strong>，且黑色蒙层会吃掉亮度，
 * 需正向亮度补偿而非继续压暗。饱和度倍率实测区间 1.3~1.5，故各档不越过 1.5。
 * 亮度域曲线在 sRGB 口径下取 t = 1.289L - 0.289（线性域等价式 1.889L - 0.889）。
 * 噪点为最终加性幅度约 1~2/255，必须在大半径模糊之后叠加。</p>
 */
public enum UiGlassMaterial {

    /** 极薄：透出最多背景，仅一丝奶白。 */
    ULTRA_THIN(1.30F, 0x1AFFFFFF, 0.020F, 0.055F, 0.045F, 0.012F, 1.2F / 255.0F),

    /** 薄：常用悬浮面板。 */
    THIN(1.38F, 0x26FFFFFF, 0.028F, 0.065F, 0.055F, 0.016F, 1.4F / 255.0F),

    /** 常规：系统默认材质，观感基准档（对齐 Light 有效叠加约 0.216）。 */
    REGULAR(1.45F, 0x33FFFFFF, 0.040F, 0.075F, 0.065F, 0.020F, 1.6F / 255.0F),

    /** 厚：模态卡片、强隔离层。 */
    THICK(1.50F, 0x4DFFFFFF, 0.055F, 0.085F, 0.075F, 0.024F, 1.8F / 255.0F),

    /** 极薄深色。 */
    DARK_ULTRA_THIN(1.25F, 0x1A181818, 0.012F, 0.040F, 0.028F, 0.026F, 1.2F / 255.0F),

    /** 薄深色。 */
    DARK_THIN(1.30F, 0x26181818, 0.018F, 0.045F, 0.032F, 0.030F, 1.4F / 255.0F),

    /** 常规深色。 */
    DARK_REGULAR(1.38F, 0x33181818, 0.026F, 0.050F, 0.036F, 0.036F, 1.6F / 255.0F),

    /** 厚深色。 */
    DARK_THICK(1.45F, 0x4D181818, 0.034F, 0.055F, 0.040F, 0.042F, 1.8F / 255.0F);

    /** 蒙层基色：iOS systemBlack 是 0.096（约 0x18）而非纯黑，纯黑会让深色玻璃失去厚度。 */
    public static final int DARK_TINT_BASE_RGB = 0x181818;

    /** 亮度域保护式饱和提升倍率；1.0 为恒等。 */
    private final float vibrancy;
    /** 材质蒙层（ARGB，alpha 即叠加强度）。 */
    private final int tintArgb;
    /** 亮度补偿，抵消蒙层吃掉的亮度；深色档因黑蒙层吃得多，补偿幅度随厚度递增。 */
    private final float luminanceLift;
    /** 边缘亮边强度。 */
    private final float edgeHighlight;
    /** 内侧上缘柔光强度。 */
    private final float innerLightTop;
    /** 内侧下缘暗带强度。 */
    private final float innerShadowBottom;
    /** 抗 banding 噪点幅度（加性，约 1~2/255）。 */
    private final float noiseAmount;

    UiGlassMaterial(float vibrancy, int tintArgb, float luminanceLift, float edgeHighlight, float innerLightTop,
            float innerShadowBottom, float noiseAmount) {
        this.vibrancy = vibrancy;
        this.tintArgb = tintArgb;
        this.luminanceLift = luminanceLift;
        this.edgeHighlight = edgeHighlight;
        this.innerLightTop = innerLightTop;
        this.innerShadowBottom = innerShadowBottom;
        this.noiseAmount = noiseAmount;
    }

    public float getVibrancy() {
        return vibrancy;
    }

    public int getTintArgb() {
        return tintArgb;
    }

    public float getLuminanceLift() {
        return luminanceLift;
    }

    public float getEdgeHighlight() {
        return edgeHighlight;
    }

    public float getInnerLightTop() {
        return innerLightTop;
    }

    public float getInnerShadowBottom() {
        return innerShadowBottom;
    }

    public float getNoiseAmount() {
        return noiseAmount;
    }

    /** 蒙层 alpha（0~1）。 */
    public float getTintAlpha() {
        return ((float) ((tintArgb >>> 24) & 0xFF)) / 255.0F;
    }

    /** 蒙层红分量（0~1）。 */
    public float getTintRed() {
        return ((float) ((tintArgb >>> 16) & 0xFF)) / 255.0F;
    }

    /** 蒙层绿分量（0~1）。 */
    public float getTintGreen() {
        return ((float) ((tintArgb >>> 8) & 0xFF)) / 255.0F;
    }

    /** 蒙层蓝分量（0~1）。 */
    public float getTintBlue() {
        return ((float) (tintArgb & 0xFF)) / 255.0F;
    }

    /** 是否为深色材质（诊断与文案用）。 */
    public boolean dark() {
        return name().startsWith("DARK_");
    }
}
