package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.scene.UiSurface;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene KeyValueMap demo 宿主壳。
 */
final class SceneKeyValueMapDemoScreen extends McScreenBridge {

    /**
     * 创建新栈 KeyValueMap demo 页。
     *
     * @param returnHubScreen 返回 hub 界面，ESC 回退到它
     */
    SceneKeyValueMapDemoScreen(GuiScreen returnHubScreen) {
        super(returnHubScreen, new SceneKeyValueMapHostWidget(new LwjglInputSource(new LwjglStateReader())));
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
