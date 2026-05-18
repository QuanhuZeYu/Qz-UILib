package club.heiqi.uilib.ui.dom;

import java.util.Objects;

import club.heiqi.uilib.ui.event.UiTextInputEvent;

/**
 * HTML-like 元素文本输入事件。
 */
public final class DocumentElementTextInputEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final UiTextInputEvent sourceEvent;
    private final DocumentEventControl eventControl;

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
        this.target = target;
        this.currentTarget = currentTarget;
        this.sourceEvent = sourceEvent;
        this.eventControl = Objects.requireNonNull(eventControl, "eventControl");
    }

    /**
     * 返回当前聚焦元素。
     *
     * @return 事件目标元素
     */
    public ElementNode getTarget() {
        return target;
    }

    /**
     * 返回当前冒泡到的元素。
     *
     * @return 当前处理元素
     */
    public ElementNode getCurrentTarget() {
        return currentTarget;
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

    /** 返回当前事件传播阶段。 */
    public DocumentEventPhase getEventPhase() { return eventControl.getEventPhase(); }
    /** 阻止事件继续向后续元素传播。 */
    public void stopPropagation() { eventControl.stopPropagation(); }
    /** 阻止事件继续传播，并阻止当前元素上的后续 handler 执行。 */
    public void stopImmediatePropagation() { eventControl.stopImmediatePropagation(); }
    /** 阻止事件的默认行为。 */
    public void preventDefault() { eventControl.preventDefault(); }
    /** 判断传播是否已被阻止。 */
    public boolean isPropagationStopped() { return eventControl.isPropagationStopped(); }
    /** 判断默认行为是否已被阻止。 */
    public boolean isDefaultPrevented() { return eventControl.isDefaultPrevented(); }

    DocumentEventControl getEventControl() { return eventControl; }
}
