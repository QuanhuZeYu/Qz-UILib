package club.heiqi.uilib.ui.dom;

import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;

/**
 * HTML-like 元素过渡取消事件。
 */
public final class DocumentElementTransitionCancelEvent extends AbstractDocumentElementEvent {

    private final DocumentAnimationProperty property;
    private final long elapsedTimeNanos;
    private final long timeNanos;

    public DocumentElementTransitionCancelEvent(ElementNode target, ElementNode currentTarget,
            DocumentAnimationProperty property, long elapsedTimeNanos, long timeNanos) {
        this(target, currentTarget, property, elapsedTimeNanos, timeNanos, new DocumentEventControl());
    }

    public DocumentElementTransitionCancelEvent(ElementNode target, ElementNode currentTarget,
            DocumentAnimationProperty property, long elapsedTimeNanos, long timeNanos,
            DocumentEventControl eventControl) {
        super(Objects.requireNonNull(target, "target"), Objects.requireNonNull(currentTarget, "currentTarget"),
                eventControl);
        this.property = Objects.requireNonNull(property, "property");
        this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        this.timeNanos = timeNanos;
    }

    public DocumentAnimationProperty getProperty() { return property; }
    public long getElapsedTimeNanos() { return elapsedTimeNanos; }
    public long getTimeNanos() { return timeNanos; }
}
