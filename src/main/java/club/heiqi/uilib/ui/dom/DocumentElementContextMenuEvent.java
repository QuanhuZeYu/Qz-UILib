package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素右键菜单事件。
 */
public final class DocumentElementContextMenuEvent extends AbstractDocumentElementEvent {

    private final int documentX;
    private final int documentY;
    private final int button;
    private final long timeNanos;

    public DocumentElementContextMenuEvent(ElementNode target, ElementNode currentTarget, int documentX,
            int documentY, int button, long timeNanos) {
        this(target, currentTarget, documentX, documentY, button, timeNanos, new DocumentEventControl());
    }

    public DocumentElementContextMenuEvent(ElementNode target, ElementNode currentTarget, int documentX,
            int documentY, int button, long timeNanos, DocumentEventControl eventControl) {
        super(Objects.requireNonNull(target, "target"),
                Objects.requireNonNull(currentTarget, "currentTarget"),
                eventControl);
        this.documentX = documentX;
        this.documentY = documentY;
        this.button = button;
        this.timeNanos = timeNanos;
    }

    public int getDocumentX() { return documentX; }
    public int getDocumentY() { return documentY; }
    public int getButton() { return button; }
    public long getTimeNanos() { return timeNanos; }
}
