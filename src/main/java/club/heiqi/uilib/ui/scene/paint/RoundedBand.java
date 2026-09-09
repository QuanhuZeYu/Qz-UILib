package club.heiqi.uilib.ui.scene.paint;

import java.util.Arrays;

/** 不可变的解析圆角带；几何相对命令原点，平移与缓存不改变曲线。 */
final class RoundedBand {
    private final int[] outer;
    private final int[] inner;
    private final int[] bounds;
    private final int[] colors;

    RoundedBand(int[] outer, int[] inner, int[] bounds, int[] colors) {
        this.outer = outer.clone();
        this.inner = inner == null ? null : inner.clone();
        this.bounds = bounds == null ? null : bounds.clone();
        this.colors = colors.clone();
    }

    int[] outer() { return outer.clone(); }
    int[] inner() { return inner == null ? null : inner.clone(); }
    int[] bounds() { return bounds == null ? null : bounds.clone(); }
    int[] colors() { return colors.clone(); }

    @Override
    public boolean equals(Object value) {
        if (!(value instanceof RoundedBand)) return false;
        RoundedBand other = (RoundedBand) value;
        return Arrays.equals(outer, other.outer) && Arrays.equals(inner, other.inner)
                && Arrays.equals(bounds, other.bounds) && Arrays.equals(colors, other.colors);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(new Object[] {outer, inner, bounds, colors});
    }
}
