package club.heiqi.uilib.ui.scene.text.font.nativefont;

/** 基线向上ascent、向下descent，均为请求em单位。 */
public final class FontMetrics {
    public final float ascent, descent, lineHeight, emSize;

    public FontMetrics(float ascent, float descent, float lineHeight, float emSize) {
        this.ascent = ascent;
        this.descent = descent;
        this.lineHeight = lineHeight;
        this.emSize = emSize;
    }
}
