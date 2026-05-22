package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素动画单次迭代完成事件。
 */
public final class DocumentElementAnimationIterationEvent extends AbstractDocumentElementEvent {

    private final String animationName;
    private final long elapsedTimeNanos;
    private final long timeNanos;
    private final long iterationIndex;

    public DocumentElementAnimationIterationEvent(ElementNode target, ElementNode currentTarget, String animationName,
            long elapsedTimeNanos, long timeNanos, long iterationIndex) {
        this(target, currentTarget, animationName, elapsedTimeNanos, timeNanos, iterationIndex,
                new DocumentEventControl());
    }

    public DocumentElementAnimationIterationEvent(ElementNode target, ElementNode currentTarget, String animationName,
            long elapsedTimeNanos, long timeNanos, long iterationIndex, DocumentEventControl eventControl) {
        super(Objects.requireNonNull(target, "target"), Objects.requireNonNull(currentTarget, "currentTarget"),
                eventControl);
        this.animationName = Objects.requireNonNull(animationName, "animationName");
        this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        this.timeNanos = timeNanos;
        this.iterationIndex = Math.max(0L, iterationIndex);
    }

    public String getAnimationName() { return animationName; }
    public long getElapsedTimeNanos() { return elapsedTimeNanos; }
    public long getTimeNanos() { return timeNanos; }
    public long getIterationIndex() { return iterationIndex; }
}
