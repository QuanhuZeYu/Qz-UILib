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

    public enum Type {
        AUTO,
        PIXEL,
        PERCENT
    }
}
