package club.heiqi.uilib.client.hud;

/** Minecraft HUD 平台环境快照；GUI scale 仅供诊断，不参与视口计算。 */
public interface MinecraftHudEnvironment {
    /** @return framebuffer/display 宽度 */
    int displayWidth();

    /** @return framebuffer/display 高度 */
    int displayHeight();

    /** @return Minecraft GUI scale 诊断值，0 表示 Auto */
    int guiScale();
}
