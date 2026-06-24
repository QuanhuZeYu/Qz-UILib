package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene Select demo 宿主壳。
 */
final class SceneSelectDemoScreen extends McScreenBridge {

    /**
     * 创建新栈 Select demo 页。
     *
     * @param parentScreen 父界面，ESC 回退到它
     */
    SceneSelectDemoScreen(GuiScreen parentScreen) {
        super(parentScreen, new SceneSelectHostWidget(new LwjglInputSource(new LwjglStateReader())));
    }

    /**
     * 打开新栈 ui.scene Select demo 页（供 SCENE_DEMO 组样例按钮调用）。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneSelectDemoScreen demoScreen = new SceneSelectDemoScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(demoScreen);
            }
        });
    }
}
