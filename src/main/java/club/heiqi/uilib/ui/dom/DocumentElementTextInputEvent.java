package club.heiqi.uilib.ui.dom;

import java.util.Objects;

import club.heiqi.uilib.ui.event.UiTextInputEvent;

/**
 * HTML-like 元素文本输入事件。
 */
public final class DocumentElementTextInputEvent extends AbstractDocumentElementEvent {

    private final UiTextInputEvent sourceEvent;

    /**
     * 创建元素文本输入事件。
     *
     * @param target 当前聚焦元素
     * @param currentTarget 当前冒泡到的元素
     * @param sourceEvent UI 层原始文本输入事件
     */
    public DocumentElementTextInputEvent(ElementNode target, ElementNode currentTarget,
            UiTextInputEvent sourceEvent) {
        this(target, currentTarget, sourceEvent, new DocumentEventControl());
    }

    /**
     * 创建元素文本输入事件（共享传播控制器）。
     *
     * @param target 当前聚焦元素
     * @param currentTarget 当前冒泡到的元素
     * @param sourceEvent UI 层原始文本输入事件
     * @param eventControl 共享传播控制器
     */
    public DocumentElementTextInputEvent(ElementNode target, ElementNode currentTarget,
            UiTextInputEvent sourceEvent, DocumentEventControl eventControl) {
        super(target, currentTarget, Objects.requireNonNull(eventControl, "eventControl"));
        this.sourceEvent = sourceEvent;
    }

    /**
     * 返回 UI 层原始文本输入事件。
     *
     * @return 原始文本输入事件
     */
    public UiTextInputEvent getSourceEvent() {
        return sourceEvent;
    }

    /**
     * 返回输入文本。
     *
     * @return 输入文本
     */
    public String getText() {
        return sourceEvent.getText();
    }

    /**
     * 返回事件时间戳。
     *
     * @return 时间戳
     */
    public long getTimeNanos() {
        return sourceEvent.getTimeNanos();
    }
}
