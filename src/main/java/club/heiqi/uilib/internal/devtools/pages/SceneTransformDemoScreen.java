package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene Transform+Clip demo 宿主壳。
 */
final class SceneTransformDemoScreen extends McScreenBridge {

    /**
     * 创建新栈 Transform+Clip demo 页。
     *
     * @param parentScreen 父界面，ESC 回退到它
     */
    SceneTransformDemoScreen(GuiScreen parentScreen) {
        super(parentScreen, new SceneTransformHostWidget(new LwjglInputSource(new LwjglStateReader())));
    }

    /**
     * 打开新栈 ui.scene Transform+Clip demo 页（供 test hub 按钮调用）。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneTransformDemoScreen demoScreen = new SceneTransformDemoScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(demoScreen);
            }
        });
    }
}
