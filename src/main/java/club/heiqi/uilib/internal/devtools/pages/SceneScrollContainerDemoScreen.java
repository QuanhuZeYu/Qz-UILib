package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import net.minecraft.client.gui.GuiScreen;

/**
 * SceneScrollContainer.attach 一行工厂 demo 宿主壳。
 *
 * <p>挂载 {@link SceneScrollContainerHostWidget}，承接 ESC 回退到 hub。</p>
 */
final class SceneScrollContainerDemoScreen extends McScreenBridge {

    /**
     * 创建 SceneScrollContainer.attach demo 页。
     *
     * @param parentScreen 父界面，ESC 回退到它
     */
    SceneScrollContainerDemoScreen(GuiScreen parentScreen) {
        super(parentScreen, new SceneScrollContainerHostWidget(
                new LwjglInputSource(new LwjglStateReader())));
    }
}
