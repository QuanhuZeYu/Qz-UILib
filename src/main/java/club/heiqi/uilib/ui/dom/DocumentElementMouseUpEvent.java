package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素鼠标抬起事件。
 */
public final class DocumentElementMouseUpEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final int documentX;
    private final int documentY;
    private final int button;
    private final long timeNanos;

    public DocumentElementMouseUpEvent(ElementNode target, ElementNode currentTarget, int documentX, int documentY,
            int button, long timeNanos) {
        this.target = Objects.requireNonNull(target, "target");
        this.currentTarget = Objects.requireNonNull(currentTarget, "currentTarget");
        this.documentX = documentX;
        this.documentY = documentY;
        this.button = button;
        this.timeNanos = timeNanos;
    }

    public ElementNode getTarget() { return target; }
    public ElementNode getCurrentTarget() { return currentTarget; }
    public int getDocumentX() { return documentX; }
    public int getDocumentY() { return documentY; }
    public int getButton() { return button; }
    public long getTimeNanos() { return timeNanos; }
}
