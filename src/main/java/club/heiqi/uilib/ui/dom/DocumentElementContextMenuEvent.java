package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素右键菜单事件。
 */
public final class DocumentElementContextMenuEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final int documentX;
    private final int documentY;
    private final int button;
    private final long timeNanos;
    private final DocumentEventControl eventControl;

    public DocumentElementContextMenuEvent(ElementNode target, ElementNode currentTarget, int documentX,
            int documentY, int button, long timeNanos) {
        this(target, currentTarget, documentX, documentY, button, timeNanos, new DocumentEventControl());
    }

    public DocumentElementContextMenuEvent(ElementNode target, ElementNode currentTarget, int documentX,
            int documentY, int button, long timeNanos, DocumentEventControl eventControl) {
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
    public DocumentEventPhase getEventPhase() { return eventControl.getEventPhase(); }
    public void stopPropagation() { eventControl.stopPropagation(); }
    public void stopImmediatePropagation() { eventControl.stopImmediatePropagation(); }
    public void preventDefault() { eventControl.preventDefault(); }
    public boolean isPropagationStopped() { return eventControl.isPropagationStopped(); }
    public boolean isDefaultPrevented() { return eventControl.isDefaultPrevented(); }

    DocumentEventControl getEventControl() { return eventControl; }
}
