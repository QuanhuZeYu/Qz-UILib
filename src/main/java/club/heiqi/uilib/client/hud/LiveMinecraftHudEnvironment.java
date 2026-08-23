package club.heiqi.uilib.client.hud;

import net.minecraft.client.Minecraft;

import club.heiqi.uilib.ui.host.NativeDisplaySize;

/**
 * 从当前客户端读取 HUD 平台环境。
 *
 * <p>视口尺寸取<b>原生窗口物理分辨率</b>:MC resize 回调维护的 {@code displayWidth/displayHeight}
 * 即窗口物理像素(非 scaled;scaled = displayWidth / scaleFactor 才受 GUI Scale 影响)。
 * 真机上 lwjgl3ify 的 Display 反射在窗口模式不可靠(曾返回桌面宽),仅作兜底。</p>
 */
public final class LiveMinecraftHudEnvironment implements MinecraftHudEnvironment {
    private Minecraft minecraft() {
        return Minecraft.getMinecraft();
    }

    @Override
    public int displayWidth() {
        Minecraft mc = minecraft();
        return mc != null && mc.displayWidth > 0 ? mc.displayWidth : NativeDisplaySize.width();
    }

    @Override
    public int displayHeight() {
        Minecraft mc = minecraft();
        return mc != null && mc.displayHeight > 0 ? mc.displayHeight : NativeDisplaySize.height();
    }

    @Override public int guiScale() { return minecraft().gameSettings.guiScale; }
}
