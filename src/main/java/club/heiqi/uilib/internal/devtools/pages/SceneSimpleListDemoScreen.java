package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.scene.UiSurface;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene SimpleList demo 宿主壳。
 */
final class SceneSimpleListDemoScreen extends McScreenBridge {

    /**
     * 创建新栈 SimpleList demo 页。
     *
     * @param returnHubScreen 返回 hub 界面，ESC 回退到它
     */
    SceneSimpleListDemoScreen(GuiScreen returnHubScreen) {
        super(returnHubScreen, new SceneSimpleListHostWidget(new LwjglInputSource(new LwjglStateReader())));
    }

    /**
     * 获取 SimpleList 宿主 Widget。
     *
     * @return SimpleList 宿主 Widget
     */
    private SceneSimpleListHostWidget getHostWidget() {
        UiSurface surface = getSurface();
        return (SceneSimpleListHostWidget) surface;
    }
}
