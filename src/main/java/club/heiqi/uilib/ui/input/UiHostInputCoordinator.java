package club.heiqi.uilib.ui.input;

import net.minecraft.client.gui.GuiScreen;

import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.hud.UiHudDocumentHost;

/**
 * 统一协调宿主原生输入链路与 UILib 即时输入抢占。
 */
public final class UiHostInputCoordinator {

    private static final UiHostInputCoordinator INSTANCE = new UiHostInputCoordinator();

    private UiHostInputCoordinator() {}

    /**
     * 返回宿主输入协调器单例。
     *
     * @return 协调器单例
     */
    public static UiHostInputCoordinator getInstance() {
        return INSTANCE;
    }

    /**
     * 处理 `handleKeyboardInput()` 阶段的即时键盘抢占。
     *
     * @param currentScreen 当前宿主界面
     * @return 是否应阻断宿主继续处理当前键盘事件
     */
    public boolean shouldCancelNativeKeyboardInput(GuiScreen currentScreen) {
        UiInputFrame immediateFrame = UiInputService.getInstance().createImmediateKeyboardFrame();
        if (handleImmediateKeyboardFrame(currentScreen, immediateFrame)) {
            return true;
        }
        return UiKeyboardCaptureState.getInstance().shouldCancelNativeKeyboardInput();
    }

    /**
     * 处理 `handleMouseInput()` 阶段的即时鼠标抢占。
     *
     * @param currentScreen 当前宿主界面
     * @return 是否应阻断宿主继续处理当前鼠标事件
     */
    public boolean shouldCancelNativeMouseInput(GuiScreen currentScreen) {
        return handleImmediateMouseFrame(currentScreen, UiInputService.getInstance().createImmediateMouseFrame());
    }

    /**
     * 在 `handleInput()` 的键盘轮询阶段优先让 HUD 消化已经接管的键盘事件。
     *
     * @param currentScreen 当前宿主界面
     * @return 是否保留一个应继续交给宿主处理的原生键盘事件
     */
    public boolean advanceKeyboardEventForHudPriority(GuiScreen currentScreen) {
        while (Keyboard.next()) {
            UiInputFrame immediateFrame = UiInputService.getInstance().createImmediateKeyboardFrame();
            if (!handleImmediateKeyboardFrame(currentScreen, immediateFrame)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在 `handleInput()` 的鼠标轮询阶段优先让 HUD 消化应拦截的鼠标事件。
     *
     * @param currentScreen 当前宿主界面
     * @return 是否保留一个应继续交给宿主处理的原生鼠标事件
     */
    public boolean advanceMouseEventForHudPriority(GuiScreen currentScreen) {
        while (Mouse.next()) {
            UiInputFrame immediateFrame = UiInputService.getInstance().createImmediateMouseFrame();
            if (!handleImmediateMouseFrame(currentScreen, immediateFrame)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleImmediateKeyboardFrame(GuiScreen currentScreen, UiInputFrame immediateFrame) {
        if (!UiHudDocumentHost.getInstance().handleImmediateKeyboardInput(currentScreen, immediateFrame)) {
            return false;
        }
        suppressCollectedKeyboardFrame(immediateFrame);
        return true;
    }

    private boolean handleImmediateMouseFrame(GuiScreen currentScreen, UiInputFrame immediateFrame) {
        return UiHudDocumentHost.getInstance().handleImmediateMouseInput(currentScreen, immediateFrame);
    }

    private void suppressCollectedKeyboardFrame(UiInputFrame immediateFrame) {
        int keyCode = immediateFrame == null || immediateFrame.getKeyEvents().isEmpty() ? 0
                : immediateFrame.getKeyEvents().get(0).getKeyCode();
        UiKeyEvent.Action action = immediateFrame == null || immediateFrame.getKeyEvents().isEmpty() ? null
                : immediateFrame.getKeyEvents().get(0).getAction();
        String collectedText = immediateFrame == null || immediateFrame.getTextEvents().isEmpty() ? null
                : immediateFrame.getTextEvents().get(0).getText();
        UiInputService.getInstance().suppressNextCollectedKeyboardEvent(keyCode, action, collectedText);
    }
}
