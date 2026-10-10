package club.heiqi.uilib.ui.scene.text.font.nativefont;

/** 相对glyph原点的保守墨迹，Y向下；UNKNOWN不得用于拒绝可见需求。 */
public final class InkBounds {
    public static final InkBounds UNKNOWN = new InkBounds(false, 0, 0, 0, 0);
    public final boolean known;
    public final float left, top, right, bottom;

    public InkBounds(boolean known, float left, float top, float right, float bottom) {
        this.known = known;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
