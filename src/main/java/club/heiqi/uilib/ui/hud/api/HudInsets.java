package club.heiqi.uilib.ui.hud.api;

import java.util.Objects;

/** HUD 安全区或占位边距，单位为 UILib logical px。 */
public final class HudInsets {
    public static final HudInsets NONE = new HudInsets(0, 0, 0, 0);
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    /** 创建四向非负边距。 */
    public HudInsets(int left, int top, int right, int bottom) {
        this.left = nonNegative(left, "left");
        this.top = nonNegative(top, "top");
        this.right = nonNegative(right, "right");
        this.bottom = nonNegative(bottom, "bottom");
    }

    private static int nonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be >= 0");
        return value;
    }

    public int getLeft() { return left; }
    public int getTop() { return top; }
    public int getRight() { return right; }
    public int getBottom() { return bottom; }

    /** 逐边相加两个占位。 */
    public HudInsets plus(HudInsets other) {
        Objects.requireNonNull(other, "other");
        return new HudInsets(left + other.left, top + other.top, right + other.right, bottom + other.bottom);
    }
}
