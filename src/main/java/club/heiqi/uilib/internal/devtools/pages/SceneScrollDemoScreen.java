package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.screen.BaseScreen;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 新栈 ui.scene 滚动 demo 宿主壳 —— Phase 4 批 4 步骤 B（滚动/视口基础设施地基）真机验收屏。
 *
 * <h3>真机交互</h3>
 * <ul>
 *   <li>鼠标滚轮在视口区域内滚动长列表：内容上下移动，超出视口部分被裁剪（视口窗口固定不动）</li>
 *   <li>滚到顶/底自动 clamp（不溢出）</li>
 *   <li>ESC 返回 SCENE_DEMO 组页面</li>
 * </ul>
 *
 * <h3>关闭收口</h3>
 * <p>{@link #onGuiClosed()} → {@link #cleanupResources()} 调 {@code hostWidget.dispose()}
 * 回收 runtime（bind effect + SCROLL handler 作用域），防止 effect 泄漏到全局调度器被其它页帧循环持续重跑。</p>
 */
final class SceneScrollDemoScreen extends BaseScreen {

    private final GuiScreen parentScreen;
    private final SceneScrollHostWidget hostWidget;

    /**
     * 创建新栈滚动 demo 页。
     *
     * @param parentScreen 父界面（test 页），ESC / 返回按钮回退到它
     */
    SceneScrollDemoScreen(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
        this.hostWidget = new SceneScrollHostWidget(new LwjglInputSource(new LwjglStateReader()));
    }

    /**
     * 打开新栈 ui.scene 滚动 demo 页（供 SCENE_DEMO 组样例按钮调用）。
     *
     * <p>以当前界面为 parentScreen，经 {@link UiScreenManager} 在下一帧切换。</p>
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final SceneScrollDemoScreen demoScreen = new SceneScrollDemoScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(demoScreen);
            }
        });
    }

    @Override
    protected void buildUi(Widget root) {
        root.addChild(hostWidget);
    }

    @Override
    protected void onResize(int width, int height) {
        super.onResize(width, height);
        setRootPadding(0, 0, 0, 0);
        hostWidget.applyLayoutBounds(0, 0, Math.max(0, width), Math.max(0, height));
    }

    @Override
    public void handleInputFrame(UiInputFrame frame) {
        if (handleEscapeShortcut(frame)) {
            return;
        }
        super.handleInputFrame(frame);
    }

    @Override
    public void onGuiClosed() {
        try {
            cleanupResources();
        } finally {
            super.onGuiClosed();
        }
    }

    private void cleanupResources() {
        // 回收响应式运行时：退订所有 bind effect 与 SCROLL handler（防止泄漏）
        hostWidget.dispose();
    }

    private boolean handleEscapeShortcut(UiInputFrame frame) {
        if (frame == null) {
            return false;
        }
        for (UiKeyEvent keyEvent : frame.getKeyEvents()) {
            if (keyEvent == null || keyEvent.getAction() != UiKeyEvent.Action.PRESSED) {
                continue;
            }
            if (keyEvent.getKeyCode() == UiKeyCodes.KEY_ESCAPE) {
                requestClose();
                return true;
            }
        }
        return false;
    }

    private void requestClose() {
        final GuiScreen targetScreen = parentScreen;
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft != null) {
                    minecraft.displayGuiScreen(targetScreen);
                }
            }
        });
    }
}
