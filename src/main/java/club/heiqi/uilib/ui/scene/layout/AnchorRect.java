package club.heiqi.uilib.ui.scene.layout;

/**
 * 锚点盒子。
 *
 * <p>坐标使用 host 逻辑像素坐标系，width/height 必须非负。</p>
 */
public final class AnchorRect {
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /**
     * 创建锚点盒子。
     *
     * @param x 左上角 X
     * @param y 左上角 Y
     * @param width 宽度，必须非负
     * @param height 高度，必须非负
     */
    public AnchorRect(int x, int y, int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("anchor size must be non-negative");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** @return 左上角 X */
    public int getX() {
        return x;
    }

    /** @return 左上角 Y */
    public int getY() {
        return y;
    }

    /** @return 宽度 */
    public int getWidth() {
        return width;
    }

    /** @return 高度 */
    public int getHeight() {
        return height;
    }

    /** @return 底边 Y */
    public int getBottom() {
        return y + height;
    }
}
