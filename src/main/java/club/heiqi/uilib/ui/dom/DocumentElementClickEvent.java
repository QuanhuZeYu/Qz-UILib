package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素点击事件。
 */
public final class DocumentElementClickEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final int documentX;
    private final int documentY;
    private final int button;
    private final long timeNanos;
    private final DocumentEventControl eventControl;

    /**
     * 创建元素点击事件。
     *
     * @param target 初始命中元素
     * @param currentTarget 当前处理事件的元素
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @param button 鼠标按钮
     * @param timeNanos 事件时间戳
     */
    public DocumentElementClickEvent(ElementNode target, ElementNode currentTarget, int documentX, int documentY,
            int button, long timeNanos) {
        this(target, currentTarget, documentX, documentY, button, timeNanos, new DocumentEventControl());
    }

    /**
     * 创建元素点击事件（共享传播控制器）。
     *
     * @param target 初始命中元素
     * @param currentTarget 当前处理事件的元素
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @param button 鼠标按钮
     * @param timeNanos 事件时间戳
     * @param eventControl 共享传播控制器
     */
    public DocumentElementClickEvent(ElementNode target, ElementNode currentTarget, int documentX, int documentY,
            int button, long timeNanos, DocumentEventControl eventControl) {
        this.target = Objects.requireNonNull(target, "target");
        this.currentTarget = Objects.requireNonNull(currentTarget, "currentTarget");
        this.documentX = documentX;
        this.documentY = documentY;
        this.button = button;
        this.timeNanos = timeNanos;
        this.eventControl = Objects.requireNonNull(eventControl, "eventControl");
    }

    /**
     * 返回初始命中元素。
     *
     * @return 初始命中元素
     */
    public ElementNode getTarget() {
        return target;
    }

    /**
     * 返回当前处理事件的元素。
     *
     * @return 当前处理事件的元素
     */
    public ElementNode getCurrentTarget() {
        return currentTarget;
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
     * 返回鼠标按钮。
     *
     * @return 鼠标按钮
     */
    public int getButton() {
        return button;
    }

    /**
     * 返回事件时间戳。
     *
     * @return 事件时间戳
     */
    public long getTimeNanos() {
        return timeNanos;
    }

    /**
     * 返回当前事件传播阶段。
     *
     * @return 事件阶段
     */
    public DocumentEventPhase getEventPhase() {
        return eventControl.getEventPhase();
    }

    /**
     * 阻止事件继续向后续元素传播。
     */
    public void stopPropagation() {
        eventControl.stopPropagation();
    }

    /**
     * 阻止事件继续传播，并阻止当前元素上的后续 handler 执行。
     */
    public void stopImmediatePropagation() {
        eventControl.stopImmediatePropagation();
    }

    /**
     * 阻止事件的默认行为。
     */
    public void preventDefault() {
        eventControl.preventDefault();
    }

    /**
     * 判断传播是否已被阻止。
     *
     * @return 是否已阻止传播
     */
    public boolean isPropagationStopped() {
        return eventControl.isPropagationStopped();
    }

    /**
     * 判断默认行为是否已被阻止。
     *
     * @return 是否已阻止默认行为
     */
    public boolean isDefaultPrevented() {
        return eventControl.isDefaultPrevented();
    }

    /**
     * 返回内部传播控制器（框架内部使用）。
     *
     * @return 传播控制器
     */
    DocumentEventControl getEventControl() {
        return eventControl;
    }
}
