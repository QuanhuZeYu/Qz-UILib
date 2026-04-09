package club.heiqi.uilib.font.page;

/**
 * 字符页中的槽位信息。
 */
public class GlyphPageSlot {

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /**
     * 创建页槽位信息。
     *
     * @param x 左上角 X 坐标
     * @param y 左上角 Y 坐标
     * @param width 槽位宽度
     * @param height 槽位高度
     */
    public GlyphPageSlot(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
