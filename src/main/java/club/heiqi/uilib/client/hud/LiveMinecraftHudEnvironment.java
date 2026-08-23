package club.heiqi.uilib.client.hud;

import net.minecraft.client.Minecraft;

import club.heiqi.uilib.ui.host.NativeDisplaySize;

/**
 * 从当前客户端读取 HUD 平台环境。
 *
 * <p>视口尺寸取<b>原生窗口物理分辨率</b>(Display.getWidth/getHeight),不依赖 MC 的 scaled
 * {@code displayWidth/displayHeight}(用户要求:UILib 渲染用原生分辨率,不抄 MC 陋习)。</p>
 */
public final class LiveMinecraftHudEnvironment implements MinecraftHudEnvironment {
    private Minecraft minecraft() {
        return Minecraft.getMinecraft();
    }

    @Override public int displayWidth() { return NativeDisplaySize.width(); }
    @Override public int displayHeight() { return NativeDisplaySize.height(); }
    @Override public int guiScale() { return minecraft().gameSettings.guiScale; }
}
