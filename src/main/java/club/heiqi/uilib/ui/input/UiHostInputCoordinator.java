package club.heiqi.uilib.ui.input;

import net.minecraft.client.gui.GuiScreen;

import club.heiqi.uilib.ui.event.UiKeyEvent;

/**
 * 统一协调宿主原生输入链路与 UILib 即时输入抢占。
 */
public final class UiHostInputCoordinator {

    private static final UiHostInputCoordinator INSTANCE = new UiHostInputCoordinator();

    private final LwjglInputRuntime.KeyboardRuntime keyboardRuntime = LwjglInputRuntime.keyboard();
    private final LwjglInputRuntime.MouseRuntime mouseRuntime = LwjglInputRuntime.mouse();
    private volatile UiHostInputCaptureParticipant captureParticipant;

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
     * 返回当前原生鼠标是否被宿主抓取。
     *
     * @return 鼠标是否被抓取
     */
    public static boolean isNativeMouseGrabbed() {
        return LwjglInputRuntime.mouse().isGrabbed();
    }

    /**
     * 注册当前宿主输入抢占参与者。
     *
     * <p>该入口由客户端宿主初始化调用；input 包不直接引用具体 HUD / screen 实现。</p>
     *
     * @param captureParticipant 输入抢占参与者；传入 null 表示禁用即时抢占
     */
    public void setCaptureParticipant(UiHostInputCaptureParticipant captureParticipant) {
        this.captureParticipant = captureParticipant;
    }

    /**
     * 处理 `handleKeyboardInput()` 阶段的即时键盘抢占。
     *
     * @param currentScreen 当前宿主界面
     * @return 是否应阻断宿主继续处理当前键盘事件
     */
    public boolean shouldCancelNativeKeyboardInput(GuiScreen currentScreen) {
        UiHostInputCaptureParticipant participant = captureParticipant;
        if (!isInteractiveInputEnabled(participant, currentScreen)) {
            return false;
        }
        UiInputFrame immediateFrame = UiInputService.getInstance().createImmediateKeyboardFrame();
        if (handleImmediateKeyboardFrame(participant, currentScreen, immediateFrame)) {
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
        UiHostInputCaptureParticipant participant = captureParticipant;
        if (!isInteractiveInputEnabled(participant, currentScreen)) {
            return false;
        }
        return handleImmediateMouseFrame(participant, currentScreen,
                UiInputService.getInstance().createImmediateMouseFrame());
    }

    /**
     * 在 `handleInput()` 的键盘轮询阶段优先让 HUD 消化已经接管的键盘事件。
     *
     * @param currentScreen 当前宿主界面
     * @return 是否保留一个应继续交给宿主处理的原生键盘事件
     */
    public boolean advanceKeyboardEventForHudPriority(GuiScreen currentScreen) {
        UiHostInputCaptureParticipant participant = captureParticipant;
        if (!isInteractiveInputEnabled(participant, currentScreen)) {
            return keyboardRuntime.next();
        }
        while (keyboardRuntime.next()) {
            UiInputFrame immediateFrame = UiInputService.getInstance().createImmediateKeyboardFrame();
            if (!handleImmediateKeyboardFrame(participant, currentScreen, immediateFrame)) {
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
        UiHostInputCaptureParticipant participant = captureParticipant;
        if (!isInteractiveInputEnabled(participant, currentScreen)) {
            return mouseRuntime.next();
        }
        while (mouseRuntime.next()) {
            UiInputFrame immediateFrame = UiInputService.getInstance().createImmediateMouseFrame();
            if (!handleImmediateMouseFrame(participant, currentScreen, immediateFrame)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInteractiveInputEnabled(UiHostInputCaptureParticipant participant, GuiScreen currentScreen) {
        return participant != null && participant.isHostInputCaptureEnabled(currentScreen,
                currentScreen == null ? null : currentScreen.getClass().getName(), mouseRuntime.isGrabbed());
    }

    private boolean handleImmediateKeyboardFrame(UiHostInputCaptureParticipant participant, GuiScreen currentScreen,
            UiInputFrame immediateFrame) {
        if (!participant.handleImmediateKeyboardInput(currentScreen, immediateFrame)) {
            return false;
        }
        suppressCollectedKeyboardFrame(immediateFrame);
        return true;
    }

    private boolean handleImmediateMouseFrame(UiHostInputCaptureParticipant participant, GuiScreen currentScreen,
            UiInputFrame immediateFrame) {
        return participant.handleImmediateMouseInput(currentScreen, immediateFrame);
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
