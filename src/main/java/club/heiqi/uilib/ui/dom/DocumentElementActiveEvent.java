package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素 active 状态变化事件。
 */
public final class DocumentElementActiveEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final boolean active;
    private final int button;
    private final long timeNanos;
    private final DocumentEventControl eventControl;

    /**
     * 创建元素 active 状态变化事件。
     *
     * @param target active 状态变化的目标元素
     * @param currentTarget 当前冒泡到的元素
     * @param active 是否处于 active 状态
     * @param button 鼠标按钮编号
     * @param timeNanos 事件时间戳
     */
    public DocumentElementActiveEvent(ElementNode target, ElementNode currentTarget, boolean active, int button,
            long timeNanos) {
        this(target, currentTarget, active, button, timeNanos, new DocumentEventControl());
    }

    /**
     * 创建元素 active 状态变化事件（共享传播控制器）。
     *
     * @param target active 状态变化的目标元素
     * @param currentTarget 当前冒泡到的元素
     * @param active 是否处于 active 状态
     * @param button 鼠标按钮编号
     * @param timeNanos 事件时间戳
     * @param eventControl 共享传播控制器
     */
    public DocumentElementActiveEvent(ElementNode target, ElementNode currentTarget, boolean active, int button,
            long timeNanos, DocumentEventControl eventControl) {
        this.target = target;
        this.currentTarget = currentTarget;
        this.active = active;
        this.button = button;
        this.timeNanos = timeNanos;
        this.eventControl = Objects.requireNonNull(eventControl, "eventControl");
    }

    /**
     * 返回 active 状态变化的目标元素。
     *
     * @return 目标元素
     */
    public ElementNode getTarget() {
        return target;
    }

    /**
     * 返回当前冒泡到的元素。
     *
     * @return 当前处理元素
     */
    public ElementNode getCurrentTarget() {
        return currentTarget;
    }

    /**
     * 判断元素是否处于 active 状态。
     *
     * @return 是否 active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * 返回鼠标按钮编号。
     *
     * @return 按钮编号
     */
    public int getButton() {
        return button;
    }

    /**
     * 返回事件时间戳。
     *
     * @return 时间戳
     */
    public long getTimeNanos() {
        return timeNanos;
    }

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
