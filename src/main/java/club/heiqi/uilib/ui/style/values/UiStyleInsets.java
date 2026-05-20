package club.heiqi.uilib.ui.style.values;

import java.util.Objects;

/**
 * 样式层四边长度值。
 */
public final class UiStyleInsets {

    private static final UiStyleInsets ZERO = new UiStyleInsets(UiStyleLength.px(0), UiStyleLength.px(0),
            UiStyleLength.px(0), UiStyleLength.px(0));

    private final UiStyleLength top;
    private final UiStyleLength right;
    private final UiStyleLength bottom;
    private final UiStyleLength left;

    private UiStyleInsets(UiStyleLength top, UiStyleLength right, UiStyleLength bottom, UiStyleLength left) {
        this.top = Objects.requireNonNull(top, "top");
        this.right = Objects.requireNonNull(right, "right");
        this.bottom = Objects.requireNonNull(bottom, "bottom");
        this.left = Objects.requireNonNull(left, "left");
    }

    /**
     * 返回四边为 0 的值。
     *
     * @return 零边距
     */
    public static UiStyleInsets zero() {
        return ZERO;
    }

    /**
     * 创建四边统一的值。
     *
     * @param value 四边长度
     * @return 四边值
     */
    public static UiStyleInsets all(UiStyleLength value) {
        UiStyleLength resolvedValue = Objects.requireNonNull(value, "value");
        if (UiStyleLength.px(0).equals(resolvedValue)) {
            return ZERO;
        }
        return new UiStyleInsets(resolvedValue, resolvedValue, resolvedValue, resolvedValue);
    }

    /**
     * 创建四边独立的值。
     *
     * @param top 上边
     * @param right 右边
     * @param bottom 下边
     * @param left 左边
     * @return 四边值
     */
    public static UiStyleInsets of(UiStyleLength top, UiStyleLength right, UiStyleLength bottom, UiStyleLength left) {
        return new UiStyleInsets(top, right, bottom, left);
    }

    public UiStyleLength getTop() {
        return top;
    }

    public UiStyleLength getRight() {
        return right;
    }

    public UiStyleLength getBottom() {
        return bottom;
    }

    public UiStyleLength getLeft() {
        return left;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UiStyleInsets)) {
            return false;
        }
        UiStyleInsets other = (UiStyleInsets) obj;
        return top.equals(other.top) && right.equals(other.right) && bottom.equals(other.bottom) && left.equals(other.left);
    }

    @Override
    public int hashCode() {
        int result = top.hashCode();
        result = 31 * result + right.hashCode();
        result = 31 * result + bottom.hashCode();
        result = 31 * result + left.hashCode();
        return result;
    }
}
