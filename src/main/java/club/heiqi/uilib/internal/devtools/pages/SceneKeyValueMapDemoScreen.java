package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;
import club.heiqi.uilib.ui.scene.host.lwjgl.SceneLwjgl3ifyTextBridge;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.scene.UiSurface;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene KeyValueMap demo 宿主壳。
 */
final class SceneKeyValueMapDemoScreen extends McScreenBridge {

    /** lwjgl3ify 文本旁路桥，确保行内 TextInput 支持完整文本事件。 */
    private final SceneLwjgl3ifyTextBridge textBridge;

    /**
     * 创建新栈 KeyValueMap demo 页。
     *
     * @param returnHubScreen 返回 hub 界面，ESC 回退到它
     */
    SceneKeyValueMapDemoScreen(GuiScreen returnHubScreen) {
        super(returnHubScreen, new SceneKeyValueMapHostWidget(new LwjglInputSource(new LwjglStateReader())));
        this.textBridge = new SceneLwjgl3ifyTextBridge(getHostWidget()::pushText);
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
     * 获取 KeyValueMap 宿主 Widget。
     *
     * @return KeyValueMap 宿主 Widget
     */
    private SceneKeyValueMapHostWidget getHostWidget() {
        UiSurface surface = getSurface();
        return (SceneKeyValueMapHostWidget) surface;
    }
}
