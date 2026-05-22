package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素动画开始事件。
 */
public final class DocumentElementAnimationStartEvent extends AbstractDocumentElementEvent {

    private final String animationName;
    private final long elapsedTimeNanos;
    private final long timeNanos;

    public DocumentElementAnimationStartEvent(ElementNode target, ElementNode currentTarget, String animationName,
            long elapsedTimeNanos, long timeNanos) {
        this(target, currentTarget, animationName, elapsedTimeNanos, timeNanos, new DocumentEventControl());
    }

    public DocumentElementAnimationStartEvent(ElementNode target, ElementNode currentTarget, String animationName,
            long elapsedTimeNanos, long timeNanos, DocumentEventControl eventControl) {
        super(Objects.requireNonNull(target, "target"), Objects.requireNonNull(currentTarget, "currentTarget"),
                eventControl);
        this.animationName = Objects.requireNonNull(animationName, "animationName");
        this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        this.timeNanos = timeNanos;
    }

    public String getAnimationName() { return animationName; }
    public long getElapsedTimeNanos() { return elapsedTimeNanos; }
    public long getTimeNanos() { return timeNanos; }
}
