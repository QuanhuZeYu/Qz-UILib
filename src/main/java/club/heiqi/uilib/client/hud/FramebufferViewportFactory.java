package club.heiqi.uilib.client.hud;

/** 把平台 display 尺寸收口为 HUD framebuffer 视口。 */
public final class FramebufferViewportFactory {
    private FramebufferViewportFactory() {}

    /**
     * 创建 HUD 视口；输入只允许是 Minecraft display framebuffer 尺寸。
     *
     * @param displayWidth framebuffer 宽度
     * @param displayHeight framebuffer 高度
     * @return 至少为 1x1 的视口
     */
    public static HudViewportMetrics create(int displayWidth, int displayHeight) {
        return new HudViewportMetrics(Math.max(1, displayWidth), Math.max(1, displayHeight));
    }
}
