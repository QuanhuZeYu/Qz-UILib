package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素鼠标按下事件。
 */
public final class DocumentElementMouseDownEvent extends AbstractDocumentElementEvent {

    private final int documentX;
    private final int documentY;
    private final int button;
    private final long timeNanos;

    public DocumentElementMouseDownEvent(ElementNode target, ElementNode currentTarget, int documentX, int documentY,
            int button, long timeNanos) {
        this(target, currentTarget, documentX, documentY, button, timeNanos, new DocumentEventControl());
    }

    public DocumentElementMouseDownEvent(ElementNode target, ElementNode currentTarget, int documentX, int documentY,
            int button, long timeNanos, DocumentEventControl eventControl) {
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
