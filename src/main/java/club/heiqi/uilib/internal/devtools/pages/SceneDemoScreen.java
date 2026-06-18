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
 * 新栈 ui.scene 端到端 demo 宿主壳 —— BaseScreen 生命周期收口。
 *
 * <h3>真机交互</h3>
 * <ul>
 *   <li>按<b>空格</b>切换背景色（深灰 ↔ 深蓝），验证 PAINT 级失效</li>
 *   <li>按<b>T</b>键切换文本内容，验证 LAYOUT 级失效</li>
 *   <li>鼠标移到 demo 按钮上触发 <b>hover 高亮</b>，验证 I3.5 hover signal 闭环（指针 route → hover signal → bind 重绘）</li>
 *   <li>点击 demo 按钮更新 label 为 "Clicked! N"，验证 I3.5 click 闭环（命中 → CLICK 合成 → handler 写 signal → flush）</li>
 *   <li>ESC 返回 test 页</li>
 * </ul>
 *
 * <h3>关闭收口</h3>
 * <p>{@link #onGuiClosed()} → {@link #cleanupResources()} 调 {@code hostWidget.dispose()}
 * 回收所有 mount 作用域与 bind effect，防止 effect 泄漏到全局调度器被其它页帧循环持续重跑。</p>
 */
final class SceneDemoScreen extends BaseScreen {

    private final GuiScreen parentScreen;
    private final SceneHostWidget hostWidget;
    /** Bug2：lwjgl3ify onTextEvent 文本旁路桥，反射探测可用性，不可用则降级 char 路径 */
    private final SceneLwjgl3ifyTextBridge textBridge;

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
     * 创建新栈 demo 页。
     *
     * @param parentScreen 父界面（test 页），ESC / 返回按钮回退到它
     */
    SceneDemoScreen(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
        this.hostWidget = new SceneHostWidget(new LwjglInputSource(new LwjglStateReader()));
        // Bug2：构造文本桥，sink 接 hostWidget::pushText（hostWidget 已就绪）
        this.textBridge = new SceneLwjgl3ifyTextBridge(hostWidget::pushText);
    }

    /**
     * 打开新栈 ui.scene demo 页（供 SCENE_DEMO 组样例按钮调用）。
     *
     * <p>以当前界面为 parentScreen，经 {@link UiScreenManager} 在下一帧切换。</p>
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
        // I4b：为新栈 scene 层启用键盘重复事件
        enableRepeatEventsReflectively(true);
        // Bug2：探测并注册 lwjgl3ify onTextEvent 文本旁路；成功则切外部文本模式，否则降级 char 路径
        if (SceneLwjgl3ifyTextBridge.isAvailable() && textBridge.register()) {
            hostWidget.setExternalTextMode(true);
        } else {
            hostWidget.setExternalTextMode(false);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // I4b：将 MC keyTyped 回调转发到 scene 输入层旁路
        hostWidget.onKeyTyped(typedChar, keyCode);
        // 继续旧栈链路（含 ESC 关闭等）
        super.keyTyped(typedChar, keyCode);
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
        // 真机交互：按空格切换背景色，按 T 切换文本
        if (frame != null) {
            for (UiKeyEvent keyEvent : frame.getKeyEvents()) {
                if (keyEvent == null || keyEvent.getAction() != UiKeyEvent.Action.PRESSED) {
                    continue;
                }
                int code = keyEvent.getKeyCode();
                if (code == UiKeyCodes.KEY_SPACE) {
                    // 切换背景色：深灰 ↔ 深蓝
                    int currentBg = hostWidget.getBgColor();
                    int newBg = (currentBg == 0xFF333333) ? 0xFF1E3A5F : 0xFF333333;
                    hostWidget.setBgColor(newBg);
                } else if (code == UiKeyCodes.KEY_T) {
                    // 切换文本
                    String currentLabel = hostWidget.getLabel();
                    String newLabel = "Scene Demo: Hello".equals(currentLabel)
                            ? "Scene Demo: Updated!" : "Scene Demo: Hello";
                    hostWidget.setLabel(newLabel);
                }
            }
        }
        super.handleInputFrame(frame);
    }

    @Override
    public void onGuiClosed() {
        try {
            cleanupResources();
        } finally {
            // I4b：关闭键盘重复事件
            enableRepeatEventsReflectively(false);
            super.onGuiClosed();
        }
    }

    private void cleanupResources() {
        // Bug2：注销 lwjgl3ify 文本监听 + endTextInput，并回到降级模式（幂等，防监听器泄漏）
        textBridge.unregister();
        hostWidget.setExternalTextMode(false);
        // 回收响应式运行时：退订所有 bind effect（防止泄漏）
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
