package club.heiqi.uilib.ui.scene.input;

/**
 * 帧内不可变文本输入事件。
 *
 * <p>由 {@link InputFrameBuilder} 在封板时从 {@link RawInputEvent} 投影生成，
 * 对象不可变，仅通过 getter 读取字段。</p>
 */
public class SceneTextEvent {

    private final String text;
    private final long timeNanos;

    /**
     * 包级构造器，仅供 {@link InputFrameBuilder} 封板使用。
     *
     * @param text 输入的文本内容
     * @param timeNanos 事件时间戳（纳秒）
     */
    SceneTextEvent(String text, long timeNanos) {
        this.text = text;
        this.timeNanos = timeNanos;
    }

    /** @return 输入的文本内容 */
    public String getText() { return text; }

    /** @return 事件时间戳（纳秒） */
    public long getTimeNanos() { return timeNanos; }
}
