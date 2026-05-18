package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素悬停状态变化事件。
 */
public final class DocumentElementHoverEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final boolean hovered;
    private final int documentX;
    private final int documentY;
    private final long timeNanos;
    private final DocumentEventControl eventControl;

    /**
     * 创建元素悬停状态变化事件。
     *
     * @param target 悬停状态变化的目标元素
     * @param currentTarget 当前冒泡到的元素
     * @param hovered 是否处于悬停状态
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @param timeNanos 事件时间戳
     */
    public DocumentElementHoverEvent(ElementNode target, ElementNode currentTarget, boolean hovered, int documentX,
            int documentY, long timeNanos) {
        this(target, currentTarget, hovered, documentX, documentY, timeNanos, new DocumentEventControl());
    }

    /**
     * 创建元素悬停状态变化事件（共享传播控制器）。
     *
     * @param target 悬停状态变化的目标元素
     * @param currentTarget 当前冒泡到的元素
     * @param hovered 是否处于悬停状态
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @param timeNanos 事件时间戳
     * @param eventControl 共享传播控制器
     */
    public DocumentElementHoverEvent(ElementNode target, ElementNode currentTarget, boolean hovered, int documentX,
            int documentY, long timeNanos, DocumentEventControl eventControl) {
        this.target = target;
        this.currentTarget = currentTarget;
        this.hovered = hovered;
        this.documentX = documentX;
        this.documentY = documentY;
        this.timeNanos = timeNanos;
        this.eventControl = Objects.requireNonNull(eventControl, "eventControl");
    }

    /**
     * 返回悬停状态变化的目标元素。
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
     * 判断元素是否处于悬停状态。
     *
     * @return 是否悬停
     */
    public boolean isHovered() {
        return hovered;
    }

    /**
     * 返回文档局部 X 坐标。
     *
     * @return 文档局部 X
     */
    public int getDocumentX() {
        return documentX;
    }

    /**
     * 返回文档局部 Y 坐标。
     *
     * @return 文档局部 Y
     */
    public int getDocumentY() {
        return documentY;
    }

    /**
     * 返回事件时间戳。
     *
     * @return 事件时间戳
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
