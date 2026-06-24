package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.scene.UiSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene 压力测试 demo 宿主壳。
 */
final class SceneStressTestDemoScreen extends McScreenBridge {

    /** lwjgl3ify 文本旁路桥，确保行内 TextInput 支持完整文本事件。 */
    private final SceneLwjgl3ifyTextBridge textBridge;

    /**
     * 创建新栈压力测试 demo 页。
     *
     * @param returnHubScreen 返回的 hub 界面
     */
    SceneStressTestDemoScreen(GuiScreen returnHubScreen) {
        super(returnHubScreen, new SceneStressTestHostWidget(new LwjglInputSource(new LwjglStateReader())));
        this.textBridge = new SceneLwjgl3ifyTextBridge(getSceneStressTestHostWidget()::pushText);
    }

    /**
     * 打开新栈 ui.scene 压力测试 demo 页。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneStressTestDemoScreen demoScreen = new SceneStressTestDemoScreen(parentScreen);
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
            getSceneStressTestHostWidget().setExternalTextMode(true);
        } else {
            getSceneStressTestHostWidget().setExternalTextMode(false);
        }
    }

    @Override
    public void onGuiClosed() {
        try {
            textBridge.unregister();
            getSceneStressTestHostWidget().setExternalTextMode(false);
        } finally {
            super.onGuiClosed();
        }
    }

    /**
     * 获取压力测试宿主 Widget。
     *
     * @return 压力测试宿主 Widget
     */
    private SceneStressTestHostWidget getSceneStressTestHostWidget() {
        UiSurface surface = getSurface();
        return (SceneStressTestHostWidget) surface;
    }
}
