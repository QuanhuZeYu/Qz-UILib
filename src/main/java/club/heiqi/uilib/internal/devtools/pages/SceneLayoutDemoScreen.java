package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene Layout demo 宿主壳。
 */
final class SceneLayoutDemoScreen extends McScreenBridge {

    /**
     * 创建新栈 Layout demo 页。
     *
     * @param parentScreen 父界面，ESC 回退到它
     */
    SceneLayoutDemoScreen(GuiScreen parentScreen) {
        super(parentScreen, new SceneLayoutHostWidget(new LwjglInputSource(new LwjglStateReader())));
    }

    /**
     * 打开新栈 ui.scene Layout demo 页（供 SCENE_DEMO 组样例按钮调用）。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneLayoutDemoScreen demoScreen = new SceneLayoutDemoScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(demoScreen);
            }
        });
    }
}
