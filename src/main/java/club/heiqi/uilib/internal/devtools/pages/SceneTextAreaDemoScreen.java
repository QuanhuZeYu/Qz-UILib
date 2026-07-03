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
 * 新栈 ui.scene TextArea 多行文本输入 demo 宿主壳。
 */
final class SceneTextAreaDemoScreen extends McScreenBridge {

    /** lwjgl3ify 文本旁路桥，确保 TextArea 支持完整文本事件。 */
    private final SceneLwjgl3ifyTextBridge textBridge;

    /**
     * 创建 TextArea demo 页。
     *
     * @param parentScreen 父界面，ESC 回退到它
     */
    SceneTextAreaDemoScreen(GuiScreen parentScreen) {
        super(parentScreen, new SceneTextAreaHostWidget(new LwjglInputSource(new LwjglStateReader())));
        this.textBridge = new SceneLwjgl3ifyTextBridge(getHostWidget()::pushText);
    }

    /**
     * 打开 TextArea demo 页（供 SCENE_DEMO 组样例按钮调用）。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneTextAreaDemoScreen demoScreen = new SceneTextAreaDemoScreen(parentScreen);
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
            getHostWidget().setExternalTextMode(true);
        } else {
            getHostWidget().setExternalTextMode(false);
        }
    }

    @Override
    public void onGuiClosed() {
        try {
            textBridge.unregister();
            getHostWidget().setExternalTextMode(false);
        } finally {
            super.onGuiClosed();
        }
    }

    private SceneTextAreaHostWidget getHostWidget() {
        UiSurface surface = getSurface();
        return (SceneTextAreaHostWidget) surface;
    }
}
