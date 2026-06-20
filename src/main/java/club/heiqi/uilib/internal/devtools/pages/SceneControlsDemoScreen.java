package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.screen.BaseScreen;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.lang.reflect.Method;

/**
 * 新栈 ui.scene 控件 demo 宿主壳 —— Phase 4 批 1（Checkbox + Toggle）。
 *
 * <h3>真机交互</h3>
 * <ul>
 *   <li>鼠标点击 Checkbox / Toggle 触发受控双向闭环：onChange 把期望新值 set 回外部 signal，控件随之重绘</li>
 *   <li>Tab 在两个控件间切焦点，Enter/Space 激活当前焦点控件</li>
 *   <li>ESC 返回 SCENE_DEMO 组页面</li>
 * </ul>
 *
 * <h3>关闭收口</h3>
 * <p>{@link #onGuiClosed()} → {@link #cleanupResources()} 调 {@code hostWidget.dispose()}
 * 回收所有 mount 作用域与 bind effect，防止 effect 泄漏到全局调度器被其它页帧循环持续重跑。</p>
 */
final class SceneControlsDemoScreen extends BaseScreen {

    private final GuiScreen parentScreen;
    private final SceneControlsHostWidget hostWidget;

    // ==================== enableRepeatEvents 反射桥（lwjglx 优先） ====================

    private static final Method KEYBOARD_ENABLE_REPEAT_EVENTS;

    static {
        Method m = null;
        // 优先 org.lwjglx（GTNH 升级扩展），降级 org.lwjgl
        Class<?> kc = null;
        try {
            kc = Class.forName("org.lwjglx.input.Keyboard");
        } catch (Exception e) {
            try {
                kc = Class.forName("org.lwjgl.input.Keyboard");
            } catch (Exception e2) {
                // 均不可用
            }
        }
        if (kc != null) {
            try {
                m = kc.getMethod("enableRepeatEvents", boolean.class);
            } catch (Exception e) {
                // 方法不存在
            }
        }
        KEYBOARD_ENABLE_REPEAT_EVENTS = m;
    }

    /**
     * 通过反射调用 {@code Keyboard.enableRepeatEvents(boolean)}（lwjglx 优先）。
     *
     * @param enable true 启用键盘重复，false 关闭
     */
    private static void enableRepeatEventsReflectively(boolean enable) {
        if (KEYBOARD_ENABLE_REPEAT_EVENTS == null) return;
        try {
            KEYBOARD_ENABLE_REPEAT_EVENTS.invoke(null, enable);
        } catch (Exception e) {
            // 静默降级
        }
    }

    /**
     * 创建新栈控件 demo 页。
     *
     * @param parentScreen 父界面（test 页），ESC / 返回按钮回退到它
     */
    SceneControlsDemoScreen(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
        this.hostWidget = new SceneControlsHostWidget(new LwjglInputSource(new LwjglStateReader()));
    }

    /**
     * 打开新栈 ui.scene 控件 demo 页（供 SCENE_DEMO 组样例按钮调用）。
     *
     * <p>以当前界面为 parentScreen，经 {@link UiScreenManager} 在下一帧切换。</p>
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
        // 为新栈 scene 层启用键盘重复事件（Enter/Space 激活、Tab 切焦点）
        enableRepeatEventsReflectively(true);
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
            // 关闭键盘重复事件
            enableRepeatEventsReflectively(false);
            super.onGuiClosed();
        }
    }

    private void cleanupResources() {
        // 回收响应式运行时：退订所有 bind effect 与 mount 作用域（防止泄漏）
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
