package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene Table demo 宿主壳。
 */
final class SceneTableDemoScreen extends McScreenBridge {

    /**
     * 创建新栈 Table demo 页。
     *
     * @param parentScreen 父界面，ESC 回退到它
     */
    SceneTableDemoScreen(GuiScreen parentScreen) {
        super(parentScreen, new SceneTableHostWidget(new LwjglInputSource(new LwjglStateReader())));
    }

    /**
     * 打开新栈 ui.scene Table demo 页（供 SCENE_DEMO 组样例按钮调用）。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneTableDemoScreen demoScreen = new SceneTableDemoScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(demoScreen);
            }
        });
    }
}
