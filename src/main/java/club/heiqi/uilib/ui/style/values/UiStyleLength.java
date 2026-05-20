package club.heiqi.uilib.ui.style.values;

/**
 * 样式层长度值。
 */
public final class UiStyleLength {

    private static final UiStyleLength AUTO = new UiStyleLength(Type.AUTO, 0.0F, 0.0F);
    private static final UiStyleLength ZERO = new UiStyleLength(Type.PIXEL, 0.0F, 0.0F);

    private final Type type;
    private final float value;
    private final float pixelOffset;

    private UiStyleLength(Type type, float value, float pixelOffset) {
        this.type = type;
        this.value = value;
        this.pixelOffset = pixelOffset;
    }

    /**
     * 创建 auto 长度。
     *
     * @return auto 长度
     */
    public static UiStyleLength auto() {
        return AUTO;
    }

    /**
     * 创建像素长度。
     *
     * @param value 像素值
     * @return 像素长度
     */
    public static UiStyleLength px(float value) {
        if (value == 0.0F) {
            return ZERO;
        }
        return new UiStyleLength(Type.PIXEL, value, 0.0F);
    }

    /**
     * 创建百分比长度。
     *
     * @param value 百分比小数值，1.0 表示 100%
     * @return 百分比长度
     */
    public static UiStyleLength percent(float value) {
        return new UiStyleLength(Type.PERCENT, value, 0.0F);
    }

    /**
     * 创建最小 calc 长度。
     *
     * <p>当前表达式固定为 {@code availableSpace * percent + pixelOffset}，用于覆盖
     * {@code calc(100% - 16px)} 这类常用混合单位场景；更完整的 CSS 表达式解析器不在本类中展开。</p>
     *
     * @param percent 百分比小数值，1.0 表示 100%
     * @param pixelOffset 像素偏移，可为负数
     * @return calc 长度
     */
    public static UiStyleLength calc(float percent, float pixelOffset) {
        if (percent == 0.0F) {
            return px(pixelOffset);
        }
        if (pixelOffset == 0.0F) {
            return percent(percent);
        }
        return new UiStyleLength(Type.CALC, percent, pixelOffset);
    }

    public Type getType() {
        return type;
    }

    public float getValue() {
        return value;
    }

    /**
     * 返回 calc 的像素偏移量。
     *
     * @return 像素偏移；非 calc 长度返回 0
     */
    public float getPixelOffset() {
        return pixelOffset;
    }

    /**
     * 在给定可用空间下解析长度。
     *
     * @param availableSpace 可用空间
     * @param autoFallback auto 时使用的回退值
     * @return 解析后的整数像素值
     */
    public int resolve(int availableSpace, int autoFallback) {
        if (type == Type.AUTO) {
            return Math.max(0, autoFallback);
        }
        if (type == Type.PERCENT) {
            return Math.round(Math.max(0, availableSpace) * value);
        }
        if (type == Type.CALC) {
            return Math.round(Math.max(0, availableSpace) * value + pixelOffset);
        }
        return Math.round(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UiStyleLength)) {
            return false;
        }
        UiStyleLength other = (UiStyleLength) obj;
        return type == other.type && Float.compare(value, other.value) == 0
                && Float.compare(pixelOffset, other.pixelOffset) == 0;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + Float.floatToIntBits(value);
        result = 31 * result + Float.floatToIntBits(pixelOffset);
        return result;
    }

    /**
     * 样式长度类型。
     */
    public enum Type {
        AUTO,
        PIXEL,
        PERCENT,
        CALC
    }
}
