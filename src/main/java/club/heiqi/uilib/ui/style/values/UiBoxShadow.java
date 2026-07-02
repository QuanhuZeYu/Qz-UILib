package club.heiqi.uilib.ui.style.values;

/**
 * CSS box-shadow 值类型。
 *
 * <p>描述元素的阴影效果，包括偏移、模糊半径、扩展半径和颜色。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * element.style().setBoxShadow(UiBoxShadow.of(2, 2, 8, 0, 0x80000000));
 * element.style().setBoxShadow(UiBoxShadow.of(0, 4, 12, 0xFF1A1A2E));
 * }</pre>
 */
public final class UiBoxShadow {

    private final int offsetX;
    private final int offsetY;
    private final int blurRadius;
    private final int spreadRadius;
    private final int color;
    private final boolean inset;

    private UiBoxShadow(int offsetX, int offsetY, int blurRadius, int spreadRadius, int color, boolean inset) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.blurRadius = Math.max(0, blurRadius);
        this.spreadRadius = spreadRadius;
        this.color = color;
        this.inset = inset;
    }

    /**
     * 创建外阴影。
     *
     * @param offsetX X 轴偏移（像素）
     * @param offsetY Y 轴偏移（像素）
     * @param blurRadius 模糊半径（像素，非负）
     * @param spreadRadius 扩展半径（像素）
     * @param color 阴影颜色（ARGB）
     * @return 阴影值
     */
    public static UiBoxShadow of(int offsetX, int offsetY, int blurRadius, int spreadRadius, int color) {
        return new UiBoxShadow(offsetX, offsetY, blurRadius, spreadRadius, color, false);
    }

    /**
     * 创建外阴影（无扩展）。
     *
     * @param offsetX X 轴偏移（像素）
     * @param offsetY Y 轴偏移（像素）
     * @param blurRadius 模糊半径（像素，非负）
     * @param color 阴影颜色（ARGB）
     * @return 阴影值
     */
    public static UiBoxShadow of(int offsetX, int offsetY, int blurRadius, int color) {
        return new UiBoxShadow(offsetX, offsetY, blurRadius, 0, color, false);
    }

    /**
     * 创建内阴影（inset）。
     *
     * @param offsetX X 轴偏移（像素）
     * @param offsetY Y 轴偏移（像素）
     * @param blurRadius 模糊半径（像素，非负）
     * @param spreadRadius 扩展半径（像素）
     * @param color 阴影颜色（ARGB）
     * @return 阴影值
     */
    public static UiBoxShadow inset(int offsetX, int offsetY, int blurRadius, int spreadRadius, int color) {
        return new UiBoxShadow(offsetX, offsetY, blurRadius, spreadRadius, color, true);
    }

    /**
     * 返回 X 轴偏移。
     *
     * @return X 偏移（像素）
     */
    public int getOffsetX() {
        return offsetX;
    }

    /**
     * 返回 Y 轴偏移。
     *
     * @return Y 偏移（像素）
     */
    public int getOffsetY() {
        return offsetY;
    }

    /**
     * 返回模糊半径。
     *
     * @return 模糊半径（像素，非负）
     */
    public int getBlurRadius() {
        return blurRadius;
    }

    /**
     * 返回扩展半径。
     *
     * @return 扩展半径（像素）
     */
    public int getSpreadRadius() {
        return spreadRadius;
    }

    /**
     * 返回阴影颜色。
     *
     * @return 颜色（ARGB）
     */
    public int getColor() {
        return color;
    }

    /**
     * 判断是否为内阴影。
     *
     * @return 是否 inset
     */
    public boolean isInset() {
        return inset;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UiBoxShadow)) return false;
        UiBoxShadow other = (UiBoxShadow) obj;
        return offsetX == other.offsetX && offsetY == other.offsetY
                && blurRadius == other.blurRadius && spreadRadius == other.spreadRadius
                && color == other.color && inset == other.inset;
    }

    @Override
    public int hashCode() {
        int result = offsetX;
        result = 31 * result + offsetY;
        result = 31 * result + blurRadius;
        result = 31 * result + spreadRadius;
        result = 31 * result + color;
        result = 31 * result + (inset ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (inset) sb.append("inset ");
        sb.append(offsetX).append("px ").append(offsetY).append("px ");
        sb.append(blurRadius).append("px ");
        if (spreadRadius != 0) sb.append(spreadRadius).append("px ");
        sb.append(String.format("#%08X", color));
        return sb.toString();
    }
}
