package club.heiqi.uilib.ui.dom;

import java.util.Objects;

import club.heiqi.uilib.ui.event.UiKeyEvent;

/**
 * HTML-like 元素键盘按键事件。
 */
public final class DocumentElementKeyEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final UiKeyEvent sourceEvent;
    private final DocumentEventControl eventControl;
    /** key handler 请求的焦点移动目标；null 表示无请求。 */
    private ElementNode pendingFocusTarget;
    /** 请求焦点移动时是否以 focus-visible 方式聚焦。 */
    private boolean pendingFocusVisible;

    /**
     * 创建元素键盘按键事件。
     *
     * @param target 当前聚焦元素
     * @param currentTarget 当前冒泡到的元素
     * @param sourceEvent UI 层原始按键事件
     */
    public DocumentElementKeyEvent(ElementNode target, ElementNode currentTarget, UiKeyEvent sourceEvent) {
        this(target, currentTarget, sourceEvent, new DocumentEventControl());
    }

    /**
     * 创建元素键盘按键事件（共享传播控制器）。
     *
     * @param target 当前聚焦元素
     * @param currentTarget 当前冒泡到的元素
     * @param sourceEvent UI 层原始按键事件
     * @param eventControl 共享传播控制器
     */
    public DocumentElementKeyEvent(ElementNode target, ElementNode currentTarget, UiKeyEvent sourceEvent,
            DocumentEventControl eventControl) {
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
     * 返回 UI 层原始按键事件。
     *
     * @return 原始按键事件
     */
    public UiKeyEvent getSourceEvent() {
        return sourceEvent;
    }

    /**
     * 返回 LWJGL2 映射键码。
     *
     * @return 键码
     */
    public int getKeyCode() {
        return sourceEvent.getKeyCode();
    }

    /**
     * 返回按键动作。
     *
     * @return 按键动作
     */
    public UiKeyEvent.Action getAction() {
        return sourceEvent.getAction();
    }

    /**
     * 判断 Ctrl 是否按下。
     *
     * @return 是否按下 Ctrl
     */
    public boolean isControlPressed() {
        return sourceEvent.isControlPressed();
    }

    /**
     * 判断 Shift 是否按下。
     *
     * @return 是否按下 Shift
     */
    public boolean isShiftPressed() {
        return sourceEvent.isShiftPressed();
    }

    /**
     * 判断 Alt 是否按下。
     *
     * @return 是否按下 Alt
     */
    public boolean isAltPressed() {
        return sourceEvent.isAltPressed();
    }

    /**
     * 判断 Super 是否按下。
     *
     * @return 是否按下 Super
     */
    public boolean isSuperPressed() {
        return sourceEvent.isSuperPressed();
    }

    /**
     * 返回事件时间戳。
     *
     * @return 时间戳
     */
    public long getTimeNanos() {
        return sourceEvent.getTimeNanos();
    }

    /**
     * key handler 请求将焦点移动到指定元素。
     *
     * <p>仅在 key handler 返回 true 消费事件时，宿主 widget 才会处理此请求。</p>
     *
     * @param element 目标焦点元素；为 null 时清除请求
     * @param focusVisible 是否以 focus-visible 方式聚焦
     */
    public void requestFocus(ElementNode element, boolean focusVisible) {
        this.pendingFocusTarget = element;
        this.pendingFocusVisible = focusVisible;
    }

    /**
     * 返回 key handler 请求的焦点移动目标。
     *
     * @return 目标元素；无请求时返回 null
     */
    public ElementNode getPendingFocusTarget() {
        return pendingFocusTarget;
    }

    /**
     * 返回焦点移动请求是否以 focus-visible 方式聚焦。
     *
     * @return 是否 focus-visible
     */
    public boolean isPendingFocusVisible() {
        return pendingFocusVisible;
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
