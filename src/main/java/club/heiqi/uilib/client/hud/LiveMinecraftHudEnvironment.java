package club.heiqi.uilib.client.hud;

import net.minecraft.client.Minecraft;

/** 从当前 Minecraft 客户端读取 HUD 平台环境。 */
public final class LiveMinecraftHudEnvironment implements MinecraftHudEnvironment {
    private Minecraft minecraft() {
        return Minecraft.getMinecraft();
    }

    @Override public int displayWidth() { return minecraft().displayWidth; }
    @Override public int displayHeight() { return minecraft().displayHeight; }
    @Override public int guiScale() { return minecraft().gameSettings.guiScale; }
}
