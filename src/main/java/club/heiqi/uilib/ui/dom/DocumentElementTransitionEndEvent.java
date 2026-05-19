package club.heiqi.uilib.ui.dom;

import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;

/**
 * HTML-like 元素过渡结束事件。
 */
public final class DocumentElementTransitionEndEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final DocumentAnimationProperty property;
    private final long elapsedTimeNanos;
    private final long timeNanos;
    private final DocumentEventControl eventControl;

    public DocumentElementTransitionEndEvent(ElementNode target, ElementNode currentTarget,
            DocumentAnimationProperty property, long elapsedTimeNanos, long timeNanos) {
        this(target, currentTarget, property, elapsedTimeNanos, timeNanos, new DocumentEventControl());
    }

    public DocumentElementTransitionEndEvent(ElementNode target, ElementNode currentTarget,
            DocumentAnimationProperty property, long elapsedTimeNanos, long timeNanos,
            DocumentEventControl eventControl) {
        this.target = Objects.requireNonNull(target, "target");
        this.currentTarget = Objects.requireNonNull(currentTarget, "currentTarget");
        this.property = Objects.requireNonNull(property, "property");
        this.elapsedTimeNanos = Math.max(0L, elapsedTimeNanos);
        this.timeNanos = timeNanos;
        this.eventControl = Objects.requireNonNull(eventControl, "eventControl");
    }

    public ElementNode getTarget() { return target; }
    public ElementNode getCurrentTarget() { return currentTarget; }
    public DocumentAnimationProperty getProperty() { return property; }
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
