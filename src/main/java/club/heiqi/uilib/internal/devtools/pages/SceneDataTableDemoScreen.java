package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.scene.UiSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene DataTable demo 宿主壳。
 */
final class SceneDataTableDemoScreen extends McScreenBridge {

    /**
     * 创建新栈 DataTable demo 页。
     *
     * @param returnHubScreen 返回界面，ESC 回退到它
     */
    SceneDataTableDemoScreen(GuiScreen returnHubScreen) {
        super(returnHubScreen, new SceneDataTableHostWidget(new LwjglInputSource(new LwjglStateReader())));
    }

    /**
     * 打开新栈 ui.scene DataTable demo 页（供 SCENE_DEMO 组样例按钮调用）。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneDataTableDemoScreen demoScreen = new SceneDataTableDemoScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(demoScreen);
            }
        });
    }

    /**
     * 获取 DataTable 宿主 Widget。
     *
     * @return DataTable 宿主 Widget
     */
    private SceneDataTableHostWidget getHostWidget() {
        UiSurface surface = getSurface();
        return (SceneDataTableHostWidget) surface;
    }
}
