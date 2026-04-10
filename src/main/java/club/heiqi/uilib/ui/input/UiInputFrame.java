package club.heiqi.uilib.ui.input;

import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;

/**
 * UI 输入层在一帧内收集到的原始事件快照。
 */
public class UiInputFrame {

    private final int mouseX;
    private final int mouseY;
    private final List<UiMouseEvent> mouseEvents;
    private final List<UiKeyEvent> keyEvents;
    private final List<UiTextInputEvent> textEvents;

    /**
     * 创建一帧输入快照。
     *
     * @param mouseX 当前鼠标 X
     * @param mouseY 当前鼠标 Y
     * @param mouseEvents 鼠标事件列表
     * @param keyEvents 键盘事件列表
     * @param textEvents 文本输入事件列表
     */
    public UiInputFrame(int mouseX, int mouseY, List<UiMouseEvent> mouseEvents, List<UiKeyEvent> keyEvents,
            List<UiTextInputEvent> textEvents) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.mouseEvents = Collections.unmodifiableList(mouseEvents);
        this.keyEvents = Collections.unmodifiableList(keyEvents);
        this.textEvents = Collections.unmodifiableList(textEvents);
    }

    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    public List<UiMouseEvent> getMouseEvents() {
        return mouseEvents;
    }

    public List<UiKeyEvent> getKeyEvents() {
        return keyEvents;
    }

    public List<UiTextInputEvent> getTextEvents() {
        return textEvents;
    }
}
