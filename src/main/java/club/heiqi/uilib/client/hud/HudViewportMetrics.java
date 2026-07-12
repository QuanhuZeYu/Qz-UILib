package club.heiqi.uilib.client.hud;

/** HUD 一帧唯一的 framebuffer 视口尺寸。 */
public final class HudViewportMetrics {
    private final int width;
    private final int height;

    HudViewportMetrics(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** 返回 framebuffer 宽度。 */
    public int getWidth() { return width; }

    /** 返回 framebuffer 高度。 */
    public int getHeight() { return height; }
}
