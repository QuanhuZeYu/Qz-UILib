package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.scene.UiSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene 对象字段 demo 宿主壳。
 */
final class SceneObjectFieldDemoScreen extends McScreenBridge {

    /**
     * 创建新栈对象字段 demo 页。
     *
     * @param returnHubScreen 返回的 Hub 界面
     */
    SceneObjectFieldDemoScreen(GuiScreen returnHubScreen) {
        super(returnHubScreen, new SceneObjectFieldHostWidget(new LwjglInputSource(new LwjglStateReader())));
    }

    /**
     * 打开新栈 ui.scene 对象字段 demo 页。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneObjectFieldDemoScreen demoScreen = new SceneObjectFieldDemoScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(demoScreen);
            }
        });
    }

    /**
     * 获取对象字段 demo 宿主 Widget。
     *
     * @return 对象字段 demo 宿主 Widget
     */
    private SceneObjectFieldHostWidget getSceneObjectFieldHostWidget() {
        UiSurface surface = getSurface();
        return (SceneObjectFieldHostWidget) surface;
    }
}
