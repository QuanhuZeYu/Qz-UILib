package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.scene.UiSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene DataTable demo 宿主壳。
 */
final class SceneDataTableDemoScreen extends McScreenBridge {

    /** lwjgl3ify 文本旁路桥，确保行内 TextInput 支持完整文本事件。 */
    private final SceneLwjgl3ifyTextBridge textBridge;

    /**
     * 创建新栈 DataTable demo 页。
     *
     * @param returnHubScreen 返回界面，ESC 回退到它
     */
    SceneDataTableDemoScreen(GuiScreen returnHubScreen) {
        super(returnHubScreen, new SceneDataTableHostWidget(new LwjglInputSource(new LwjglStateReader())));
        this.textBridge = new SceneLwjgl3ifyTextBridge(getHostWidget()::pushText);
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
     * 初始化界面并启用文本旁路。
     */
    @Override
    public void initGui() {
        super.initGui();
        if (SceneLwjgl3ifyTextBridge.isAvailable() && textBridge.register()) {
            getHostWidget().setExternalTextMode(true);
        } else {
            getHostWidget().setExternalTextMode(false);
        }
    }

    /**
     * 关闭界面时释放文本旁路。
     */
    @Override
    public void onGuiClosed() {
        try {
            textBridge.unregister();
            getHostWidget().setExternalTextMode(false);
        } finally {
            super.onGuiClosed();
        }
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
