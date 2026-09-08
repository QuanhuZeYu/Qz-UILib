package club.heiqi.uilib.font.latex.layout;

/**
 * 当前数学字体常量。除 radicalDegreeBottomRaisePercent 外均为有效字号下 logical px。
 * 指数抬升百分比需在根号选变体/拼接后乘实际根号高度，不能提前按字号换算。
 */
public final class MathFontParameters {
    private final float axisHeight;
    private final float ruleThickness;
    private final float radicalVerticalGap;
    private final float radicalDisplayStyleVerticalGap;
    private final float radicalExtraAscender;
    private final float accentBaseHeight;
    private final int radicalDegreeBottomRaisePercent;

    public MathFontParameters(float axisHeight, float ruleThickness, float radicalVerticalGap,
            float radicalDisplayStyleVerticalGap, float radicalExtraAscender,
            int radicalDegreeBottomRaisePercent, float accentBaseHeight) {
        requireNonNegative(axisHeight, "axisHeight");
        requireNonNegative(ruleThickness, "ruleThickness");
        requireNonNegative(radicalVerticalGap, "radicalVerticalGap");
        requireNonNegative(radicalDisplayStyleVerticalGap, "radicalDisplayStyleVerticalGap");
        requireNonNegative(radicalExtraAscender, "radicalExtraAscender");
        requireNonNegative(accentBaseHeight, "accentBaseHeight");
        if (radicalDegreeBottomRaisePercent < 0 || radicalDegreeBottomRaisePercent > 100) {
            throw new IllegalArgumentException("radicalDegreeBottomRaisePercent 必须在 0..100");
        }
        this.axisHeight = axisHeight;
        this.ruleThickness = ruleThickness;
        this.radicalVerticalGap = radicalVerticalGap;
        this.radicalDisplayStyleVerticalGap = radicalDisplayStyleVerticalGap;
        this.radicalExtraAscender = radicalExtraAscender;
        this.accentBaseHeight = accentBaseHeight;
        this.radicalDegreeBottomRaisePercent = radicalDegreeBottomRaisePercent;
    }

    public float getAxisHeight() { return axisHeight; }
    public float getRuleThickness() { return ruleThickness; }
    public float getRadicalVerticalGap() { return radicalVerticalGap; }
    public float getRadicalDisplayStyleVerticalGap() { return radicalDisplayStyleVerticalGap; }
    public float getRadicalExtraAscender() { return radicalExtraAscender; }
    public float getAccentBaseHeight() { return accentBaseHeight; }
    public int getRadicalDegreeBottomRaisePercent() { return radicalDegreeBottomRaisePercent; }

    private static void requireNonNegative(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " 必须为非负有限值");
        }
    }
}
