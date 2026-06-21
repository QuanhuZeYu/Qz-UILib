package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.scene.UiSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene 端到端 demo 宿主壳。
 */
final class SceneDemoScreen extends McScreenBridge {

    /** Bug2：lwjgl3ify onTextEvent 文本旁路桥，反射探测可用性，不可用则降级 char 路径。 */
    private final SceneLwjgl3ifyTextBridge textBridge;

    /**
     * 创建新栈 demo 页。
     *
     * @param parentScreen 父界面，ESC 回退到它
     */
    SceneDemoScreen(GuiScreen parentScreen) {
        super(parentScreen, new SceneHostWidget(new LwjglInputSource(new LwjglStateReader())));
        this.textBridge = new SceneLwjgl3ifyTextBridge(getSceneHostWidget()::pushText);
    }

    /**
     * 打开新栈 ui.scene demo 页（供 SCENE_DEMO 组样例按钮调用）。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneDemoScreen demoScreen = new SceneDemoScreen(parentScreen);
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
            getSceneHostWidget().setExternalTextMode(true);
        } else {
            getSceneHostWidget().setExternalTextMode(false);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == UiKeyCodes.KEY_SPACE) {
            int currentBg = getSceneHostWidget().getBgColor();
            int newBg = (currentBg == 0xFF333333) ? 0xFF1E3A5F : 0xFF333333;
            getSceneHostWidget().setBgColor(newBg);
        } else if (keyCode == UiKeyCodes.KEY_T) {
            String currentLabel = getSceneHostWidget().getLabel();
            String newLabel = "Scene Demo: Hello".equals(currentLabel)
                    ? "Scene Demo: Updated!" : "Scene Demo: Hello";
            getSceneHostWidget().setLabel(newLabel);
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        try {
            textBridge.unregister();
            getSceneHostWidget().setExternalTextMode(false);
        } finally {
            super.onGuiClosed();
        }
    }

    private SceneHostWidget getSceneHostWidget() {
        UiSurface surface = getSurface();
        return (SceneHostWidget) surface;
    }
}
