package club.heiqi.uilib.ui.event;

/**
 * UI 层使用的键盘按键事件。
 */
public class UiKeyEvent {

    /**
     * 按键动作。
     */
    public enum Action {
        PRESSED,
        REPEATED,
        RELEASED
    }

    private final int keyCode;
    private final int glfwKeyCode;
    private final int glfwScanCode;
    private final Action action;
    private final boolean controlPressed;
    private final boolean shiftPressed;
    private final boolean altPressed;
    private final boolean superPressed;
    private final long timeNanos;

    /**
     * 创建 UI 键盘事件。
     *
     * @param keyCode LWJGL2 映射键码
     * @param glfwKeyCode GLFW 键码
     * @param glfwScanCode GLFW 扫描码
     * @param action 按键动作
     * @param controlPressed 是否按下 Ctrl
     * @param shiftPressed 是否按下 Shift
     * @param altPressed 是否按下 Alt
     * @param superPressed 是否按下 Super
     * @param timeNanos 事件时间戳
     */
    public UiKeyEvent(int keyCode, int glfwKeyCode, int glfwScanCode, Action action, boolean controlPressed,
            boolean shiftPressed, boolean altPressed, boolean superPressed, long timeNanos) {
        this.keyCode = keyCode;
        this.glfwKeyCode = glfwKeyCode;
        this.glfwScanCode = glfwScanCode;
        this.action = action;
        this.controlPressed = controlPressed;
        this.shiftPressed = shiftPressed;
        this.altPressed = altPressed;
        this.superPressed = superPressed;
        this.timeNanos = timeNanos;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public int getGlfwKeyCode() {
        return glfwKeyCode;
    }

    public int getGlfwScanCode() {
        return glfwScanCode;
    }

    public Action getAction() {
        return action;
    }

    public boolean isControlPressed() {
        return controlPressed;
    }

    public boolean isShiftPressed() {
        return shiftPressed;
    }

    public boolean isAltPressed() {
        return altPressed;
    }

    public boolean isSuperPressed() {
        return superPressed;
    }

    public long getTimeNanos() {
        return timeNanos;
    }
}
