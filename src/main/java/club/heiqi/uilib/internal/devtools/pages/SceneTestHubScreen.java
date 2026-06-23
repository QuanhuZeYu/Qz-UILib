package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.scene.UiSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * scene 新栈 qzui test 首页屏幕。
 */
public final class SceneTestHubScreen extends McScreenBridge {

    private final GuiScreen parentScreen;

    /**
     * 创建 scene test 首页。
     *
     * @param parentScreen 父界面，ESC 回退到它
     */
    public SceneTestHubScreen(GuiScreen parentScreen) {
        super(parentScreen, new SceneTestHubHostWidget(new LwjglInputSource(new LwjglStateReader())));
        this.parentScreen = parentScreen;
    }

    /**
     * 打开 scene 新栈 test 首页。
     */
    public static void openHub() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneTestHubScreen hubScreen = new SceneTestHubScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(hubScreen);
            }
        });
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        openRequestedDemo();
    }

    /**
     * 读取 host 的 signal 导航请求，并在 MC screen 适配层打开独立 demo。
     */
    private void openRequestedDemo() {
        SceneTestHubHostWidget hostWidget = getSceneTestHubHostWidget();
        String destination = hostWidget.consumeNavigationRequest();
        if (destination == null) {
            return;
        }
        GuiScreen returnHubScreen = new SceneTestHubScreen(parentScreen);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen targetScreen = createTargetScreen(destination, returnHubScreen);
        if (targetScreen == null) {
            return;
        }
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(targetScreen);
            }
        });
    }

    /**
     * 根据导航目标创建独立 demo 页。
     *
     * @param destination 目标标识
     * @param returnHubScreen demo 关闭后返回的全新 hub 页
     * @return 目标 demo 页；未知目标返回 null
     */
    private GuiScreen createTargetScreen(String destination, GuiScreen returnHubScreen) {
        if (SceneTestHubHostWidget.isSceneDestination(destination)) {
            return new SceneDemoScreen(returnHubScreen);
        }
        if (SceneTestHubHostWidget.isControlsDestination(destination)) {
            return new SceneControlsDemoScreen(returnHubScreen);
        }
        if (SceneTestHubHostWidget.isScrollDestination(destination)) {
            return new SceneScrollDemoScreen(returnHubScreen);
        }
        if (SceneTestHubHostWidget.isTableDestination(destination)) {
            return new SceneTableDemoScreen(returnHubScreen);
        }
        if (SceneTestHubHostWidget.isLayoutDestination(destination)) {
            return new SceneLayoutDemoScreen(returnHubScreen);
        }
        if (SceneTestHubHostWidget.isFormDestination(destination)) {
            return new SceneFormDemoScreen(returnHubScreen);
        }
        if (SceneTestHubHostWidget.isSelectDestination(destination)) {
            return new SceneSelectDemoScreen(returnHubScreen);
        }
        return null;
    }

    /**
     * 获取 scene test 首页 host。
     *
     * @return scene test 首页 host
     */
    private SceneTestHubHostWidget getSceneTestHubHostWidget() {
        UiSurface surface = getSurface();
        return (SceneTestHubHostWidget) surface;
    }
}
