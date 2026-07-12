package club.heiqi.uilib.ui.scene.image;

/** 平台中立的节点局部图片目标矩形。 */
public final class SceneImageRect {
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    /**
     * 创建目标矩形。
     *
     * @param left 左边界
     * @param top 上边界
     * @param right 右边界
     * @param bottom 下边界
     */
    public SceneImageRect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = Math.max(left, right);
        this.bottom = Math.max(top, bottom);
    }

    /** @return 左边界 */
    public int getLeft() { return left; }
    /** @return 上边界 */
    public int getTop() { return top; }
    /** @return 右边界 */
    public int getRight() { return right; }
    /** @return 下边界 */
    public int getBottom() { return bottom; }
}
