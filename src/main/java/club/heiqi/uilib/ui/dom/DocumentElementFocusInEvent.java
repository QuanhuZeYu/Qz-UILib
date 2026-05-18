package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素焦点进入事件（冒泡版 focus）。
 */
public final class DocumentElementFocusInEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final boolean focused;
    private final boolean focusVisible;
    private final DocumentEventControl eventControl;

    public DocumentElementFocusInEvent(ElementNode target, ElementNode currentTarget, boolean focused,
            boolean focusVisible) {
        this(target, currentTarget, focused, focusVisible, new DocumentEventControl());
    }

    public DocumentElementFocusInEvent(ElementNode target, ElementNode currentTarget, boolean focused,
            boolean focusVisible, DocumentEventControl eventControl) {
        this.target = Objects.requireNonNull(target, "target");
        this.currentTarget = Objects.requireNonNull(currentTarget, "currentTarget");
        this.focused = focused;
        this.focusVisible = focusVisible;
        this.eventControl = Objects.requireNonNull(eventControl, "eventControl");
    }

    public ElementNode getTarget() { return target; }
    public ElementNode getCurrentTarget() { return currentTarget; }
    public boolean isFocused() { return focused; }
    public boolean isFocusVisible() { return focusVisible; }

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
