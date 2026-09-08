package club.heiqi.uilib.font.latex.layout;

/** 字形度量；坐标相对推进原点/基线，y 向下，单位为有效字号下 logical px。 */
public final class MathGlyphMetrics {
    private final float advance;
    private final float inkLeft;
    private final float inkTop;
    private final float inkRight;
    private final float inkBottom;
    private final float italicCorrection;
    private final float topAccentAttachment;
    private final boolean hasTopAccentAttachment;

    public MathGlyphMetrics(float advance, float inkLeft, float inkTop, float inkRight, float inkBottom,
            float italicCorrection, boolean hasTopAccentAttachment, float topAccentAttachment) {
        requireFinite(advance, "advance");
        requireFinite(inkLeft, "inkLeft");
        requireFinite(inkTop, "inkTop");
        requireFinite(inkRight, "inkRight");
        requireFinite(inkBottom, "inkBottom");
        requireFinite(italicCorrection, "italicCorrection");
        requireFinite(topAccentAttachment, "topAccentAttachment");
        if (advance < 0 || inkRight < inkLeft || inkBottom < inkTop) {
            throw new IllegalArgumentException("advance 和 ink 尺寸不得为负");
        }
        this.advance = advance;
        this.inkLeft = inkLeft;
        this.inkTop = inkTop;
        this.inkRight = inkRight;
        this.inkBottom = inkBottom;
        this.italicCorrection = italicCorrection;
        this.topAccentAttachment = topAccentAttachment;
        this.hasTopAccentAttachment = hasTopAccentAttachment;
    }

    public float getAdvance() { return advance; }
    public float getInkLeft() { return inkLeft; }
    public float getInkTop() { return inkTop; }
    public float getInkRight() { return inkRight; }
    public float getInkBottom() { return inkBottom; }
    public float getItalicCorrection() { return italicCorrection; }
    public float getTopAccentAttachment() { return topAccentAttachment; }
    public boolean hasTopAccentAttachment() { return hasTopAccentAttachment; }

    private static void requireFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " 必须为有限值");
        }
    }
}
