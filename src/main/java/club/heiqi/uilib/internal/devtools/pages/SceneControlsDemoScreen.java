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
 * 新栈 ui.scene 控件 demo 宿主壳。
 */
final class SceneControlsDemoScreen extends McScreenBridge {

    /** lwjgl3ify onTextEvent 文本旁路桥，反射探测可用性，不可用则降级 char 路径。 */
    private final SceneLwjgl3ifyTextBridge textBridge;

    /**
     * 创建新栈控件 demo 页。
     *
     * @param parentScreen 父界面，ESC 回退到它
     */
    SceneControlsDemoScreen(GuiScreen parentScreen) {
        super(parentScreen, new SceneControlsHostWidget(new LwjglInputSource(new LwjglStateReader())));
        this.textBridge = new SceneLwjgl3ifyTextBridge(getSceneControlsHostWidget()::pushText);
    }

    /**
     * 打开新栈 ui.scene 控件 demo 页（供 SCENE_DEMO 组样例按钮调用）。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneControlsDemoScreen demoScreen = new SceneControlsDemoScreen(parentScreen);
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
            getSceneControlsHostWidget().setExternalTextMode(true);
        } else {
            getSceneControlsHostWidget().setExternalTextMode(false);
        }
    }

    @Override
    public void onGuiClosed() {
        try {
            textBridge.unregister();
            getSceneControlsHostWidget().setExternalTextMode(false);
        } finally {
            super.onGuiClosed();
        }
    }

    private SceneControlsHostWidget getSceneControlsHostWidget() {
        UiSurface surface = getSurface();
        return (SceneControlsHostWidget) surface;
    }
}
