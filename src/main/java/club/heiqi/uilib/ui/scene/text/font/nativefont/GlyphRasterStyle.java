package club.heiqi.uilib.ui.scene.text.font.nativefont;

/** 不可变光栅变体；boldOffset 为 face 请求单位，italicShear 相对基线 x'=x-s*y。 */
public final class GlyphRasterStyle {
    public static final GlyphRasterStyle NORMAL = new GlyphRasterStyle(0, 0);
    public final float boldOffset, italicShear;
    public GlyphRasterStyle(float boldOffset, float italicShear) {
        if (!Float.isFinite(boldOffset) || boldOffset < 0 || !Float.isFinite(italicShear))
            throw new IllegalArgumentException("无效字形光栅样式");
        this.boldOffset = boldOffset == 0 ? 0 : boldOffset; this.italicShear = italicShear == 0 ? 0 : italicShear;
    }
    boolean normal() { return boldOffset == 0 && italicShear == 0; }
    @Override public boolean equals(Object value) {
        if (!(value instanceof GlyphRasterStyle)) return false;
        GlyphRasterStyle s = (GlyphRasterStyle) value;
        return Float.floatToIntBits(boldOffset) == Float.floatToIntBits(s.boldOffset)
            && Float.floatToIntBits(italicShear) == Float.floatToIntBits(s.italicShear);
    }
    @Override public int hashCode() { return 31 * Float.floatToIntBits(boldOffset) + Float.floatToIntBits(italicShear); }
    @Override public String toString() { return "bold=" + boldOffset + ",shear=" + italicShear; }

    /** native 像素格包络。双线性核留一像素保护；shear 也映射核的纵向支撑。
     * bounds 与 pixels 必须共用这里，不能用 em 经验比例代替真实 pixelScale。
     */
    double[] envelope(double left, double top, double right, double bottom, float pixelScale) {
        if (!Float.isFinite(pixelScale) || pixelScale <= 0) throw new IllegalArgumentException("无效 pixelScale");
        double s = italicShear, p = pixelScale;
        double a = left / p - s * top / p, b = left / p - s * bottom / p;
        double c = right / p - s * top / p, d = right / p - s * bottom / p;
        double padding = 1 + Math.abs(s);
        return new double[]{Math.floor(Math.min(Math.min(a,b),Math.min(c,d)) - padding),
            Math.floor(top / p - 1), Math.ceil(Math.max(Math.max(a,b),Math.max(c,d)) + boldOffset / p + padding),
            Math.ceil(bottom / p + 1)};
    }
}
