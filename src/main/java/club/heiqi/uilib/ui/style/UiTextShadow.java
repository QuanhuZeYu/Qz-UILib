package club.heiqi.uilib.ui.style;

/**
 * CSS-like text-shadow 单值描述。
 */
public final class UiTextShadow {

    private final int offsetX;
    private final int offsetY;
    private final int blurRadius;
    private final int color;

    private UiTextShadow(int offsetX, int offsetY, int blurRadius, int color) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.blurRadius = Math.max(0, blurRadius);
        this.color = color;
    }

    /**
     * 创建文本阴影。
     *
     * @param offsetX X 轴偏移（像素）
     * @param offsetY Y 轴偏移（像素）
     * @param blurRadius 模糊半径（像素，非负）
     * @param color 阴影颜色（ARGB）
     * @return 文本阴影值
     */
    public static UiTextShadow of(int offsetX, int offsetY, int blurRadius, int color) {
        return new UiTextShadow(offsetX, offsetY, blurRadius, color);
    }

    /**
     * 创建无模糊文本阴影。
     *
     * @param offsetX X 轴偏移（像素）
     * @param offsetY Y 轴偏移（像素）
     * @param color 阴影颜色（ARGB）
     * @return 文本阴影值
     */
    public static UiTextShadow of(int offsetX, int offsetY, int color) {
        return of(offsetX, offsetY, 0, color);
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public int getBlurRadius() {
        return blurRadius;
    }

    public int getColor() {
        return color;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UiTextShadow)) {
            return false;
        }
        UiTextShadow other = (UiTextShadow) obj;
        return offsetX == other.offsetX && offsetY == other.offsetY
                && blurRadius == other.blurRadius && color == other.color;
    }

    @Override
    public int hashCode() {
        int result = offsetX;
        result = 31 * result + offsetY;
        result = 31 * result + blurRadius;
        result = 31 * result + color;
        return result;
    }

    @Override
    public String toString() {
        return offsetX + "px " + offsetY + "px " + blurRadius + "px "
                + String.format("#%08X", color);
    }
}
