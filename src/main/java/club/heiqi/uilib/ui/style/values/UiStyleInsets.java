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

    /**
     * 按 CSS 双值简写创建四边值（垂直/水平）。
     *
     * @param vertical 上下边长度
     * @param horizontal 左右边长度
     * @return 四边值
     */
    public static UiStyleInsets symmetric(UiStyleLength vertical, UiStyleLength horizontal) {
        UiStyleLength v = Objects.requireNonNull(vertical, "vertical");
        UiStyleLength h = Objects.requireNonNull(horizontal, "horizontal");
        return new UiStyleInsets(v, h, v, h);
    }

    /**
     * 创建仅设置上下边、左右为 0 的值。
     *
     * @param value 上下边长度
     * @return 四边值
     */
    public static UiStyleInsets vertical(UiStyleLength value) {
        UiStyleLength v = Objects.requireNonNull(value, "value");
        return new UiStyleInsets(v, UiStyleLength.px(0), v, UiStyleLength.px(0));
    }

    /**
     * 创建仅设置左右边、上下为 0 的值。
     *
     * @param value 左右边长度
     * @return 四边值
     */
    public static UiStyleInsets horizontal(UiStyleLength value) {
        UiStyleLength h = Objects.requireNonNull(value, "value");
        return new UiStyleInsets(UiStyleLength.px(0), h, UiStyleLength.px(0), h);
    }

    /**
     * 创建仅设置上边、其余为 0 的值。
     *
     * @param value 上边长度
     * @return 四边值
     */
    public static UiStyleInsets top(UiStyleLength value) {
        UiStyleLength v = Objects.requireNonNull(value, "value");
        UiStyleLength zero = UiStyleLength.px(0);
        return new UiStyleInsets(v, zero, zero, zero);
    }

    /**
     * 创建仅设置右边、其余为 0 的值。
     *
     * @param value 右边长度
     * @return 四边值
     */
    public static UiStyleInsets right(UiStyleLength value) {
        UiStyleLength v = Objects.requireNonNull(value, "value");
        UiStyleLength zero = UiStyleLength.px(0);
        return new UiStyleInsets(zero, v, zero, zero);
    }

    /**
     * 创建仅设置下边、其余为 0 的值。
     *
     * @param value 下边长度
     * @return 四边值
     */
    public static UiStyleInsets bottom(UiStyleLength value) {
        UiStyleLength v = Objects.requireNonNull(value, "value");
        UiStyleLength zero = UiStyleLength.px(0);
        return new UiStyleInsets(zero, zero, v, zero);
    }

    /**
     * 创建仅设置左边、其余为 0 的值。
     *
     * @param value 左边长度
     * @return 四边值
     */
    public static UiStyleInsets left(UiStyleLength value) {
        UiStyleLength v = Objects.requireNonNull(value, "value");
        UiStyleLength zero = UiStyleLength.px(0);
        return new UiStyleInsets(zero, zero, zero, v);
    }

    /**
     * 在当前值基础上替换上边长度。
     *
     * @param value 新的上边长度
     * @return 新的四边值
     */
    public UiStyleInsets withTop(UiStyleLength value) {
        return new UiStyleInsets(Objects.requireNonNull(value, "value"), right, bottom, left);
    }

    /**
     * 在当前值基础上替换右边长度。
     *
     * @param value 新的右边长度
     * @return 新的四边值
     */
    public UiStyleInsets withRight(UiStyleLength value) {
        return new UiStyleInsets(top, Objects.requireNonNull(value, "value"), bottom, left);
    }

    /**
     * 在当前值基础上替换下边长度。
     *
     * @param value 新的下边长度
     * @return 新的四边值
     */
    public UiStyleInsets withBottom(UiStyleLength value) {
        return new UiStyleInsets(top, right, Objects.requireNonNull(value, "value"), left);
    }

    /**
     * 在当前值基础上替换左边长度。
     *
     * @param value 新的左边长度
     * @return 新的四边值
     */
    public UiStyleInsets withLeft(UiStyleLength value) {
        return new UiStyleInsets(top, right, bottom, Objects.requireNonNull(value, "value"));
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
