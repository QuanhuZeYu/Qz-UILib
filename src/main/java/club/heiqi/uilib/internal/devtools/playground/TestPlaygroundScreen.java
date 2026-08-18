package club.heiqi.uilib.internal.devtools.playground;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.screen.McScreenBridge;

/**
 * 测试场地 MC GuiScreen 宿主壳。
 *
 * <p>把 {@link TestPlaygroundHost}（{@link UiSurface}）包进 {@link McScreenBridge}，
 * 接入 MC GuiScreen 生命周期：渲染帧驱动、键盘/鼠标回调旁路、lwjgl3ify 文本桥与
 * 关闭时的 surface 资源释放。结构沿用 {@code ModernConfigScreen} 同型壳。</p>
 */
public class TestPlaygroundScreen extends McScreenBridge {

    /**
     * 创建测试场地宿主壳。
     *
     * @param parentScreen 关闭后返回的父界面，可为 null
     * @param surface      测试场地渲染面（{@link TestPlaygroundHost}）
     */
    public TestPlaygroundScreen(GuiScreen parentScreen, UiSurface surface) {
        super(parentScreen, surface);
    }

    @Override
    public void drawDefaultBackground() {
        if (!hasWorldContext()) {
            super.drawDefaultBackground();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static boolean hasWorldContext() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null && minecraft.theWorld != null;
    }
}
