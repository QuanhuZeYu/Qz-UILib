package club.heiqi.uilib.ui.input;

/**
 * 统一维护 UILib 对宿主键盘的接管状态。
 */
public final class UiKeyboardCaptureState {

    private static final UiKeyboardCaptureState INSTANCE = new UiKeyboardCaptureState();

    private boolean screenKeyboardCaptured;
    private boolean hudKeyboardCaptured;
    private boolean screenTextInputRequested;
    private boolean hudTextInputRequested;

    private UiKeyboardCaptureState() {}

    /**
     * 返回键盘接管状态单例。
     *
     * @return 状态单例
     */
    public static UiKeyboardCaptureState getInstance() {
        return INSTANCE;
    }

    /**
     * 更新屏幕宿主是否接管键盘。
     *
     * @param captured 是否接管
     */
    public synchronized void setScreenKeyboardCaptured(boolean captured) {
        screenKeyboardCaptured = captured;
    }

    /**
     * 更新 HUD 宿主是否接管键盘。
     *
     * @param captured 是否接管
     */
    public synchronized void setHudKeyboardCaptured(boolean captured) {
        hudKeyboardCaptured = captured;
    }

    /**
     * 判断当前是否由任一 UILib 宿主接管键盘。
     *
     * @return 是否已接管
     */
    public synchronized boolean isUiLibKeyboardCaptured() {
        return screenKeyboardCaptured || hudKeyboardCaptured;
    }

    /**
     * 判断当前是否需要阻断宿主原生键盘分发。
     *
     * @return 是否阻断原生键盘
     */
    public synchronized boolean shouldCancelNativeKeyboardInput() {
        return isUiLibKeyboardCaptured();
    }

    /**
     * 更新屏幕宿主是否请求底层文本输入模式。
     *
     * @param requested 是否请求文本输入模式
     */
    public synchronized void setScreenTextInputRequested(boolean requested) {
        screenTextInputRequested = requested;
    }

    /**
     * 更新 HUD 宿主是否请求底层文本输入模式。
     *
     * @param requested 是否请求文本输入模式
     */
    public synchronized void setHudTextInputRequested(boolean requested) {
        hudTextInputRequested = requested;
    }

    /**
     * 判断当前是否仍有任一 UILib 宿主需要底层文本输入模式。
     *
     * @return 是否需要文本输入模式
     */
    public synchronized boolean shouldKeepTextInputActive() {
        return screenTextInputRequested || hudTextInputRequested;
    }

    /**
     * 清空全部接管状态。
     */
    public synchronized void clear() {
        screenKeyboardCaptured = false;
        hudKeyboardCaptured = false;
        screenTextInputRequested = false;
        hudTextInputRequested = false;
    }
}
