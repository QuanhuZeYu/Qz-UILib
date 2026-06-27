package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.screen.McScreenBridge;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene 布局/绘制并行性能真机实测页宿主壳。
 */
final class SceneParallelPerfDemoScreen extends McScreenBridge {

    /**
     * 创建 Scene 并行性能真机实测页。
     *
     * @param parentScreen 父界面，ESC 回退到它
     */
    SceneParallelPerfDemoScreen(GuiScreen parentScreen) {
        super(parentScreen, new SceneParallelPerfHostWidget(new LwjglInputSource(new LwjglStateReader())));
    }

    /**
     * 打开 Scene 并行性能真机实测页（供 test hub 按钮调用）。
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneParallelPerfDemoScreen demoScreen = new SceneParallelPerfDemoScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(demoScreen);
            }
        });
    }
}
