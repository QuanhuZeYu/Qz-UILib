package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene FBO 性能基线实测页宿主壳。
 */
final class SceneFboPerfDemoScreen extends McScreenBridge {

    /**
     * 创建 FBO 性能基线实测页。
     *
     * @param parentScreen 父界面，ESC 回退到它
     */
    SceneFboPerfDemoScreen(GuiScreen parentScreen) {
        super(parentScreen, new SceneFboPerfHostWidget(new LwjglInputSource(new LwjglStateReader())));
    }

    /**
     * 打开 FBO 性能基线实测页（供 test hub 按钮调用）。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneFboPerfDemoScreen demoScreen = new SceneFboPerfDemoScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(demoScreen);
            }
        });
    }
}
