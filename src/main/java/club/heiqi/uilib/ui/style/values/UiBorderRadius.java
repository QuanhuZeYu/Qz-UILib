package club.heiqi.uilib.ui.style.values;

import java.util.Objects;

/**
 * CSS border-radius 分角值类型。
 *
 * <p>支持分别设置四个角的圆角半径，对应浏览器的
 * {@code border-top-left-radius}、{@code border-top-right-radius}、
 * {@code border-bottom-right-radius}、{@code border-bottom-left-radius}。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * // 四角统一
 * element.style().setBorderRadius(UiBorderRadius.all(UiStyleLength.px(8)));
 * // 分角设置
 * element.style().setBorderRadius(UiBorderRadius.of(
 *     UiStyleLength.px(12), UiStyleLength.px(12),
 *     UiStyleLength.px(0), UiStyleLength.px(0)));
 * }</pre>
 */
public final class UiBorderRadius {

    private final UiStyleLength topLeft;
    private final UiStyleLength topRight;
    private final UiStyleLength bottomRight;
    private final UiStyleLength bottomLeft;

    private UiBorderRadius(UiStyleLength topLeft, UiStyleLength topRight,
            UiStyleLength bottomRight, UiStyleLength bottomLeft) {
        this.topLeft = Objects.requireNonNull(topLeft, "topLeft");
        this.topRight = Objects.requireNonNull(topRight, "topRight");
        this.bottomRight = Objects.requireNonNull(bottomRight, "bottomRight");
        this.bottomLeft = Objects.requireNonNull(bottomLeft, "bottomLeft");
    }

    /**
     * 创建四角统一的圆角。
     *
     * @param radius 统一圆角半径
     * @return 圆角值
     */
    public static UiBorderRadius all(UiStyleLength radius) {
        Objects.requireNonNull(radius, "radius");
        return new UiBorderRadius(radius, radius, radius, radius);
    }

    /**
     * 创建分角圆角。
     *
     * @param topLeft 左上角半径
     * @param topRight 右上角半径
     * @param bottomRight 右下角半径
     * @param bottomLeft 左下角半径
     * @return 圆角值
     */
    public static UiBorderRadius of(UiStyleLength topLeft, UiStyleLength topRight,
            UiStyleLength bottomRight, UiStyleLength bottomLeft) {
        return new UiBorderRadius(topLeft, topRight, bottomRight, bottomLeft);
    }

    /**
     * 创建上下对称的圆角（上两角相同，下两角相同）。
     *
     * @param top 上两角半径
     * @param bottom 下两角半径
     * @return 圆角值
     */
    public static UiBorderRadius vertical(UiStyleLength top, UiStyleLength bottom) {
        return new UiBorderRadius(top, top, bottom, bottom);
    }

    /**
     * 创建左右对称的圆角（左两角相同，右两角相同）。
     *
     * @param left 左两角半径
     * @param right 右两角半径
     * @return 圆角值
     */
    public static UiBorderRadius horizontal(UiStyleLength left, UiStyleLength right) {
        return new UiBorderRadius(left, right, right, left);
    }

    /**
     * 创建无圆角。
     *
     * @return 零圆角值
     */
    public static UiBorderRadius zero() {
        return new UiBorderRadius(UiStyleLength.px(0), UiStyleLength.px(0),
                UiStyleLength.px(0), UiStyleLength.px(0));
    }

    /**
     * 返回左上角半径。
     *
     * @return 左上角半径
     */
    public UiStyleLength getTopLeft() {
        return topLeft;
    }

    /**
     * 返回右上角半径。
     *
     * @return 右上角半径
     */
    public UiStyleLength getTopRight() {
        return topRight;
    }

    /**
     * 返回右下角半径。
     *
     * @return 右下角半径
     */
    public UiStyleLength getBottomRight() {
        return bottomRight;
    }

    /**
     * 返回左下角半径。
     *
     * @return 左下角半径
     */
    public UiStyleLength getBottomLeft() {
        return bottomLeft;
    }

    /**
     * 判断四角是否统一。
     *
     * @return 四角是否相同
     */
    public boolean isUniform() {
        return topLeft.equals(topRight) && topRight.equals(bottomRight) && bottomRight.equals(bottomLeft);
    }

    /**
     * 返回统一值（仅当 isUniform() 为 true 时有意义）。
     *
     * @return 统一圆角半径
     */
    public UiStyleLength getUniformRadius() {
        return topLeft;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UiBorderRadius)) return false;
        UiBorderRadius other = (UiBorderRadius) obj;
        return topLeft.equals(other.topLeft) && topRight.equals(other.topRight)
                && bottomRight.equals(other.bottomRight) && bottomLeft.equals(other.bottomLeft);
    }

    @Override
    public int hashCode() {
        int result = topLeft.hashCode();
        result = 31 * result + topRight.hashCode();
        result = 31 * result + bottomRight.hashCode();
        result = 31 * result + bottomLeft.hashCode();
        return result;
    }

    @Override
    public String toString() {
        if (isUniform()) {
            return topLeft.toString();
        }
        return topLeft + " " + topRight + " " + bottomRight + " " + bottomLeft;
    }
}
