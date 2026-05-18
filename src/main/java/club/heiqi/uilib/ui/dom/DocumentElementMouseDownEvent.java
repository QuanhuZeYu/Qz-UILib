package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素鼠标按下事件。
 */
public final class DocumentElementMouseDownEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final int documentX;
    private final int documentY;
    private final int button;
    private final long timeNanos;
    private final DocumentEventControl eventControl;

    public DocumentElementMouseDownEvent(ElementNode target, ElementNode currentTarget, int documentX, int documentY,
            int button, long timeNanos) {
        this(target, currentTarget, documentX, documentY, button, timeNanos, new DocumentEventControl());
    }

    public DocumentElementMouseDownEvent(ElementNode target, ElementNode currentTarget, int documentX, int documentY,
            int button, long timeNanos, DocumentEventControl eventControl) {
        this.target = Objects.requireNonNull(target, "target");
        this.currentTarget = Objects.requireNonNull(currentTarget, "currentTarget");
        this.documentX = documentX;
        this.documentY = documentY;
        this.button = button;
        this.timeNanos = timeNanos;
        this.eventControl = Objects.requireNonNull(eventControl, "eventControl");
    }

    public ElementNode getTarget() { return target; }
    public ElementNode getCurrentTarget() { return currentTarget; }
    public int getDocumentX() { return documentX; }
    public int getDocumentY() { return documentY; }
    public int getButton() { return button; }
    public long getTimeNanos() { return timeNanos; }

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
