package club.heiqi.uilib.ui.event;

/**
 * UI 层使用的文本输入事件。
 */
public class UiTextInputEvent {

    private final String text;
    private final long timeNanos;

    /**
     * 创建文本输入事件。
     *
     * @param text 输入文本
     * @param timeNanos 事件时间戳
     */
    public UiTextInputEvent(String text, long timeNanos) {
        this.text = text;
        this.timeNanos = timeNanos;
    }

    public String getText() {
        return text;
    }

    public long getTimeNanos() {
        return timeNanos;
    }
}
