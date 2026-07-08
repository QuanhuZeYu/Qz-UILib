package club.heiqi.uilib.ui.base.layout;

/**
 * 响应式布局长度描述。
 */
public class UiLength {

    private final Type type;
    private final float value;

    private UiLength(Type type, float value) {
        this.type = type;
        this.value = value;
    }

    public static UiLength auto() {
        return new UiLength(Type.AUTO, 0.0F);
    }

    public static UiLength px(float value) {
        return new UiLength(Type.PIXEL, value);
    }

    public static UiLength percent(float value) {
        return new UiLength(Type.PERCENT, value);
    }

    public Type getType() {
        return type;
    }

    public float getValue() {
        return value;
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
        return Math.round(value);
    }

    public enum Type {
        AUTO,
        PIXEL,
        PERCENT
    }
}
