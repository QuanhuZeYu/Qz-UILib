package club.heiqi.uilib.ui.style.values;

/**
 * CSS border-color 分边值类型。
 *
 * <p>支持分别设置四边的边框颜色。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * element.style().setBorderColors(UiBorderColors.all(0xFFFF0000));
 * element.style().setBorderColors(UiBorderColors.of(
 *     0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00));
 * }</pre>
 */
public final class UiBorderColors {

    private final int top;
    private final int right;
    private final int bottom;
    private final int left;

    private UiBorderColors(int top, int right, int bottom, int left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
    }

    /**
     * 创建四边统一颜色。
     *
     * @param color 统一颜色（ARGB）
     * @return 边框颜色值
     */
    public static UiBorderColors all(int color) {
        return new UiBorderColors(color, color, color, color);
    }

    /**
     * 创建分边颜色。
     *
     * @param top 上边颜色
     * @param right 右边颜色
     * @param bottom 下边颜色
     * @param left 左边颜色
     * @return 边框颜色值
     */
    public static UiBorderColors of(int top, int right, int bottom, int left) {
        return new UiBorderColors(top, right, bottom, left);
    }

    /**
     * 创建上下/左右对称颜色。
     *
     * @param vertical 上下颜色
     * @param horizontal 左右颜色
     * @return 边框颜色值
     */
    public static UiBorderColors symmetric(int vertical, int horizontal) {
        return new UiBorderColors(vertical, horizontal, vertical, horizontal);
    }

    /** 返回上边颜色。 */
    public int getTop() { return top; }
    /** 返回右边颜色。 */
    public int getRight() { return right; }
    /** 返回下边颜色。 */
    public int getBottom() { return bottom; }
    /** 返回左边颜色。 */
    public int getLeft() { return left; }

    /**
     * 判断四边是否统一。
     *
     * @return 四边是否相同
     */
    public boolean isUniform() {
        return top == right && right == bottom && bottom == left;
    }

    /**
     * 返回统一值（仅当 isUniform() 为 true 时有意义）。
     *
     * @return 统一颜色
     */
    public int getUniformColor() {
        return top;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UiBorderColors)) return false;
        UiBorderColors other = (UiBorderColors) obj;
        return top == other.top && right == other.right
                && bottom == other.bottom && left == other.left;
    }

    @Override
    public int hashCode() {
        int result = top;
        result = 31 * result + right;
        result = 31 * result + bottom;
        result = 31 * result + left;
        return result;
    }

    @Override
    public String toString() {
        if (isUniform()) {
            return String.format("#%08X", top);
        }
        return String.format("#%08X #%08X #%08X #%08X", top, right, bottom, left);
    }
}
