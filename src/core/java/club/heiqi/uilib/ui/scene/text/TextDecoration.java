package club.heiqi.uilib.ui.scene.text;

import java.util.Objects;

/** baseline相对几何；flags统一在TextEffects，不在此处建立第二套状态。 */
public final class TextDecoration {
    public static final TextDecoration NONE = new TextDecoration(0, 0, 0, 0, 0);
    private final boolean underlineFromEm, strikeFromEm;
    public final float underlineTop,
            underlineThickness,
            underlineStartOffset,
            strikeTop,
            strikeThickness;

    public TextDecoration(
            float underlineTop,
            float underlineThickness,
            float underlineStartOffset,
            float strikeTop,
            float strikeThickness) {
        this(underlineTop, underlineThickness, underlineStartOffset, strikeTop, strikeThickness, false, false);
    }
    private TextDecoration(float underlineTop, float underlineThickness, float underlineStartOffset,
            float strikeTop, float strikeThickness, boolean underlineFromEm, boolean strikeFromEm) {
        this.underlineFromEm = underlineFromEm;
        this.strikeFromEm = strikeFromEm;
        if (!Float.isFinite(underlineTop)
                || !Float.isFinite(underlineThickness)
                || !Float.isFinite(underlineStartOffset)
                || !Float.isFinite(strikeTop)
                || !Float.isFinite(strikeThickness)
                || underlineThickness < 0
                || strikeThickness < 0) throw new IllegalArgumentException("无效装饰几何");
        this.underlineTop = underlineTop;
        this.underlineThickness = underlineThickness;
        this.underlineStartOffset = underlineStartOffset;
        this.strikeTop = strikeTop;
        this.strikeThickness = strikeThickness;
    }

    /** DSL 派生装饰随最终脚本 em 更新；显式几何保持原声明。 */
    public TextDecoration fromEm(float em, boolean underline, boolean strike) {
        boolean u = underline || underlineFromEm, s = strike || strikeFromEm;
        float thickness = Math.max(1, em / 16f);
        return new TextDecoration(u ? em / 12f : underlineTop, u ? thickness : underlineThickness,
                underlineStartOffset, s ? -em / 3f : strikeTop, s ? thickness : strikeThickness, u, s);
    }
    public TextDecoration resolveEm(float em) { return fromEm(em, false, false); }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TextDecoration)) return false;
        TextDecoration d = (TextDecoration) o;
        return underlineTop == d.underlineTop
                && underlineThickness == d.underlineThickness
                && underlineStartOffset == d.underlineStartOffset
                && strikeTop == d.strikeTop
                && strikeThickness == d.strikeThickness
                && underlineFromEm == d.underlineFromEm && strikeFromEm == d.strikeFromEm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                underlineTop, underlineThickness, underlineStartOffset, strikeTop, strikeThickness, underlineFromEm, strikeFromEm);
    }
}
