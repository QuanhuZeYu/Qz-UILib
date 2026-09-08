package club.heiqi.uilib.font.latex.layout;

/**
 * 数学字形的可见矩形，相对 glyph origin，单位为实际字号下的 logical px。
 * 不得再乘 GlyphElem.sizeScale；适配边界只做 logical 到 screen 的换算。
 * 左/上可为负无穷，右/下可为正无穷，表示该方向不裁剪。
 * 此值只限制绘制范围，不改变完整字形的栅格化、采样 padding 或 MathBox 几何。
 */
public final class MathGlyphClip {
    private final float left;
    private final float top;
    private final float right;
    private final float bottom;

    public MathGlyphClip(float left, float top, float right, float bottom) {
        if (Float.isNaN(left) || Float.isNaN(top) || Float.isNaN(right) || Float.isNaN(bottom)
                || left == Float.POSITIVE_INFINITY || top == Float.POSITIVE_INFINITY
                || right == Float.NEGATIVE_INFINITY || bottom == Float.NEGATIVE_INFINITY
                || left > right || top > bottom) {
            throw new IllegalArgumentException("clip 必须有序且无 NaN，无限边界只能朝外");
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public float getLeft() { return left; }
    public float getTop() { return top; }
    public float getRight() { return right; }
    public float getBottom() { return bottom; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof MathGlyphClip)) { return false; }
        MathGlyphClip that = (MathGlyphClip) other;
        return Float.compare(left, that.left) == 0 && Float.compare(top, that.top) == 0
                && Float.compare(right, that.right) == 0 && Float.compare(bottom, that.bottom) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(left);
        result = 31 * result + Float.floatToIntBits(top);
        result = 31 * result + Float.floatToIntBits(right);
        return 31 * result + Float.floatToIntBits(bottom);
    }
}
