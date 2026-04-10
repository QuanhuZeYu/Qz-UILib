package club.heiqi.uilib.ui.event;

/**
 * UI 层使用的鼠标事件。
 */
public class UiMouseEvent {

    /**
     * 鼠标动作。
     */
    public enum Action {
        MOVE,
        BUTTON_DOWN,
        BUTTON_UP,
        SCROLL
    }

    private final Action action;
    private final int mouseX;
    private final int mouseY;
    private final int button;
    private final int wheelDelta;
    private final int deltaX;
    private final int deltaY;
    private final long timeNanos;

    /**
     * 创建 UI 鼠标事件。
     *
     * @param action 鼠标动作
     * @param mouseX 当前鼠标 X
     * @param mouseY 当前鼠标 Y
     * @param button 按钮编号，无按钮时为 -1
     * @param wheelDelta 滚轮增量
     * @param deltaX 鼠标 X 位移
     * @param deltaY 鼠标 Y 位移
     * @param timeNanos 事件时间戳
     */
    public UiMouseEvent(Action action, int mouseX, int mouseY, int button, int wheelDelta, int deltaX, int deltaY,
            long timeNanos) {
        this.action = action;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.button = button;
        this.wheelDelta = wheelDelta;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.timeNanos = timeNanos;
    }

    public Action getAction() {
        return action;
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    public int getButton() {
        return button;
    }

    public int getWheelDelta() {
        return wheelDelta;
    }

    public int getDeltaX() {
        return deltaX;
    }

    public int getDeltaY() {
        return deltaY;
    }

    public long getTimeNanos() {
        return timeNanos;
    }
}
