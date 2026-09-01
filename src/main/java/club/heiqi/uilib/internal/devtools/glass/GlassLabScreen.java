package club.heiqi.uilib.internal.devtools.glass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.screen.McScreenBridge;

/**
 * 磨玻璃实验室 MC GuiScreen 宿主壳。
 *
 * <p>把 {@link GlassLabHost} 包进 {@link McScreenBridge}（与
 * {@code TestPlaygroundScreen} 同型壳）：有世界上下文时跳过半透明底，
 * 让玻璃采样到的是游戏画面 + scene 内容的混合层；无世界上下文时保留原版底色。</p>
 */
public final class GlassLabScreen extends McScreenBridge {

    /**
     * 创建磨玻璃实验室壳。
     *
     * @param parentScreen 关闭后返回的父界面，可为 null
     * @param surface      磨玻璃实验室渲染面
     */
    public GlassLabScreen(GuiScreen parentScreen, UiSurface surface) {
        super(parentScreen, surface);
    }

    @Override
    public void drawDefaultBackground() {
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean hasWorld = minecraft != null && minecraft.theWorld != null;
        if (!hasWorld) {
            super.drawDefaultBackground();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
