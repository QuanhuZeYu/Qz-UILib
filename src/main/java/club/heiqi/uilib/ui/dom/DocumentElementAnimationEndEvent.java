package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素动画结束事件。
 */
public final class DocumentElementAnimationEndEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final String animationName;
    private final long elapsedTimeNanos;
    private final long timeNanos;
    private final DocumentEventControl eventControl;

    public DocumentElementAnimationEndEvent(ElementNode target, ElementNode currentTarget, String animationName,
            long elapsedTimeNanos, long timeNanos) {
        this(target, currentTarget, animationName, elapsedTimeNanos, timeNanos, new DocumentEventControl());
    }

    public DocumentElementAnimationEndEvent(ElementNode target, ElementNode currentTarget, String animationName,
            long elapsedTimeNanos, long timeNanos, DocumentEventControl eventControl) {
        this.target = Objects.requireNonNull(target, "target");
        this.currentTarget = Objects.requireNonNull(currentTarget, "currentTarget");
        this.animationName = Objects.requireNonNull(animationName, "animationName");
        this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        this.timeNanos = timeNanos;
        this.eventControl = Objects.requireNonNull(eventControl, "eventControl");
    }

    public ElementNode getTarget() { return target; }
    public ElementNode getCurrentTarget() { return currentTarget; }
    public String getAnimationName() { return animationName; }
    public long getElapsedTimeNanos() { return elapsedTimeNanos; }
    public long getTimeNanos() { return timeNanos; }
    public DocumentEventPhase getEventPhase() { return eventControl.getEventPhase(); }
    public void stopPropagation() { eventControl.stopPropagation(); }
    public void stopImmediatePropagation() { eventControl.stopImmediatePropagation(); }
    public void preventDefault() { eventControl.preventDefault(); }
    public boolean isPropagationStopped() { return eventControl.isPropagationStopped(); }
    public boolean isDefaultPrevented() { return eventControl.isDefaultPrevented(); }

    DocumentEventControl getEventControl() { return eventControl; }
}
