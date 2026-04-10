package club.heiqi.uilib.ui.layout;

/**
 * 响应式布局边距。
 */
public class UiInsets {

    public static final UiInsets ZERO = new UiInsets(0, 0, 0, 0);

    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    public UiInsets(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public static UiInsets all(int value) {
        return new UiInsets(value, value, value, value);
    }

    public static UiInsets of(int left, int top, int right, int bottom) {
        return new UiInsets(left, top, right, bottom);
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getRight() {
        return right;
    }

    public int getBottom() {
        return bottom;
    }
}
