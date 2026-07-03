package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;
import club.heiqi.uilib.ui.scene.host.lwjgl.SceneLwjgl3ifyTextBridge;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.scene.UiSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene 对象字段 demo 宿主壳。
 */
final class SceneObjectFieldDemoScreen extends McScreenBridge {

    /** lwjgl3ify 文本旁路桥，确保 ObjectField 内部 TextInput 支持完整文本事件。 */
    private final SceneLwjgl3ifyTextBridge textBridge;

    /**
     * 创建新栈对象字段 demo 页。
     *
     * @param returnHubScreen 返回的 Hub 界面
     */
    SceneObjectFieldDemoScreen(GuiScreen returnHubScreen) {
        super(returnHubScreen, new SceneObjectFieldHostWidget(new LwjglInputSource(new LwjglStateReader())));
        this.textBridge = new SceneLwjgl3ifyTextBridge(getSceneObjectFieldHostWidget()::pushText);
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

    @Override
    public void initGui() {
        super.initGui();
        if (SceneLwjgl3ifyTextBridge.isAvailable() && textBridge.register()) {
            getSceneObjectFieldHostWidget().setExternalTextMode(true);
        } else {
            getSceneObjectFieldHostWidget().setExternalTextMode(false);
        }
    }

    @Override
    public void onGuiClosed() {
        try {
            textBridge.unregister();
            getSceneObjectFieldHostWidget().setExternalTextMode(false);
        } finally {
            super.onGuiClosed();
        }
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
