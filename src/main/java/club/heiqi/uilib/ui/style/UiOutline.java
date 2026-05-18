package club.heiqi.uilib.ui.style;

import java.util.Objects;

/**
 * CSS outline 值类型。
 *
 * <p>描述元素的轮廓线，通常用于焦点指示。与 border 不同，outline 不占据布局空间。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * element.style().setOutline(UiOutline.of(2, 0xFF4488FF, UiBorderStyle.SOLID));
 * element.style().setOutline(UiOutline.of(1, 0xFFFFFFFF, UiBorderStyle.DASHED, 2));
 * }</pre>
 */
public final class UiOutline {

    private final int width;
    private final int color;
    private final UiBorderStyle style;
    private final int offset;

    private UiOutline(int width, int color, UiBorderStyle style, int offset) {
        this.width = Math.max(0, width);
        this.color = color;
        this.style = Objects.requireNonNull(style, "style");
        this.offset = offset;
    }

    /**
     * 创建轮廓线。
     *
     * @param width 轮廓线宽度（像素）
     * @param color 轮廓线颜色（ARGB）
     * @param style 轮廓线样式
     * @return 轮廓线值
     */
    public static UiOutline of(int width, int color, UiBorderStyle style) {
        return new UiOutline(width, color, style, 0);
    }

    /**
     * 创建带偏移的轮廓线。
     *
     * @param width 轮廓线宽度（像素）
     * @param color 轮廓线颜色（ARGB）
     * @param style 轮廓线样式
     * @param offset 轮廓线与边框之间的间距（像素）
     * @return 轮廓线值
     */
    public static UiOutline of(int width, int color, UiBorderStyle style, int offset) {
        return new UiOutline(width, color, style, offset);
    }

    /**
     * 创建无轮廓线。
     *
     * @return 无轮廓线值
     */
    public static UiOutline none() {
        return new UiOutline(0, 0, UiBorderStyle.NONE, 0);
    }

    /**
     * 返回轮廓线宽度。
     *
     * @return 宽度（像素）
     */
    public int getWidth() {
        return width;
    }

    /**
     * 返回轮廓线颜色。
     *
     * @return 颜色（ARGB）
     */
    public int getColor() {
        return color;
    }

    /**
     * 返回轮廓线样式。
     *
     * @return 线条样式
     */
    public UiBorderStyle getStyle() {
        return style;
    }

    /**
     * 返回轮廓线偏移。
     *
     * @return 偏移（像素）
     */
    public int getOffset() {
        return offset;
    }

    /**
     * 判断是否为无轮廓线。
     *
     * @return 是否无轮廓线
     */
    public boolean isNone() {
        return width <= 0 || style == UiBorderStyle.NONE;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UiOutline)) return false;
        UiOutline other = (UiOutline) obj;
        return width == other.width && color == other.color
                && style == other.style && offset == other.offset;
    }

    @Override
    public int hashCode() {
        int result = width;
        result = 31 * result + color;
        result = 31 * result + style.hashCode();
        result = 31 * result + offset;
        return result;
    }

    @Override
    public String toString() {
        if (isNone()) return "none";
        return width + "px " + style.name().toLowerCase(java.util.Locale.ROOT)
                + " " + String.format("#%08X", color)
                + (offset != 0 ? " offset=" + offset + "px" : "");
    }
}
