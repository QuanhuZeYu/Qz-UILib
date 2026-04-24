package club.heiqi.uilib.ui.layout;

/**
 * HTML-like 布局盒四边像素值。
 */
public final class DocumentLayoutEdges {

    private static final DocumentLayoutEdges ZERO = new DocumentLayoutEdges(0, 0, 0, 0);

    private final int top;
    private final int right;
    private final int bottom;
    private final int left;

    private DocumentLayoutEdges(int top, int right, int bottom, int left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
    }

    /**
     * 返回四边为 0 的值。
     *
     * @return 零边
     */
    public static DocumentLayoutEdges zero() {
        return ZERO;
    }

    /**
     * 创建四边值。
     *
     * @param top 上边
     * @param right 右边
     * @param bottom 下边
     * @param left 左边
     * @return 四边值
     */
    public static DocumentLayoutEdges of(int top, int right, int bottom, int left) {
        if (top == 0 && right == 0 && bottom == 0 && left == 0) {
            return ZERO;
        }
        return new DocumentLayoutEdges(top, right, bottom, left);
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

    public int getLeft() {
        return left;
    }

    /**
     * 返回左右总和。
     *
     * @return 水平总和
     */
    public int getHorizontal() {
        return left + right;
    }

    /**
     * 返回上下总和。
     *
     * @return 垂直总和
     */
    public int getVertical() {
        return top + bottom;
    }
}
