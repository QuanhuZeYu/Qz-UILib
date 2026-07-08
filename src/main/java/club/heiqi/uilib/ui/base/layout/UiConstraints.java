package club.heiqi.uilib.ui.base.layout;

/**
 * 父容器传递给子组件的测量约束。
 */
public class UiConstraints {

    private final int minWidth;
    private final int maxWidth;
    private final int minHeight;
    private final int maxHeight;

    private UiConstraints(int minWidth, int maxWidth, int minHeight, int maxHeight) {
        this.minWidth = Math.max(0, minWidth);
        this.maxWidth = normalizeMax(this.minWidth, maxWidth);
        this.minHeight = Math.max(0, minHeight);
        this.maxHeight = normalizeMax(this.minHeight, maxHeight);
    }

    /**
     * 创建无上限测量约束。
     *
     * @return 无上限约束
     */
    public static UiConstraints unbounded() {
        return new UiConstraints(0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
    }

    /**
     * 创建指定宽度的测量约束。
     *
     * @param width 固定宽度
     * @return 宽度固定约束
     */
    public static UiConstraints fixedWidth(int width) {
        int safeWidth = Math.max(0, width);
        return new UiConstraints(safeWidth, safeWidth, 0, Integer.MAX_VALUE);
    }

    /**
     * 创建指定宽高的测量约束。
     *
     * @param width 固定宽度
     * @param height 固定高度
     * @return 宽高固定约束
     */
    public static UiConstraints exact(int width, int height) {
        int safeWidth = Math.max(0, width);
        int safeHeight = Math.max(0, height);
        return new UiConstraints(safeWidth, safeWidth, safeHeight, safeHeight);
    }

    /**
     * 创建完整区间约束。
     *
     * @param minWidth 最小宽度
     * @param maxWidth 最大宽度
     * @param minHeight 最小高度
     * @param maxHeight 最大高度
     * @return 区间约束
     */
    public static UiConstraints range(int minWidth, int maxWidth, int minHeight, int maxHeight) {
        return new UiConstraints(minWidth, maxWidth, minHeight, maxHeight);
    }

    public int getMinWidth() {
        return minWidth;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public boolean hasBoundedWidth() {
        return maxWidth < Integer.MAX_VALUE;
    }

    public boolean hasBoundedHeight() {
        return maxHeight < Integer.MAX_VALUE;
    }

    public int constrainWidth(int width) {
        return Math.max(minWidth, Math.min(Math.max(0, width), maxWidth));
    }

    public int constrainHeight(int height) {
        return Math.max(minHeight, Math.min(Math.max(0, height), maxHeight));
    }

    private static int normalizeMax(int min, int max) {
        if (max < 0) {
            return min;
        }
        return Math.max(min, max);
    }
}
