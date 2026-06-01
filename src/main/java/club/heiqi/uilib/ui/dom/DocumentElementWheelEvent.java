package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素滚轮事件。
 */
public final class DocumentElementWheelEvent extends AbstractDocumentElementEvent {

    private final int documentX;
    private final int documentY;
    private final int wheelDelta;
    private final long timeNanos;

    /**
     * 创建元素滚轮事件。
     *
     * @param target 初始命中元素
     * @param currentTarget 当前处理事件的元素
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @param wheelDelta 原始滚轮增量
     * @param timeNanos 事件时间戳
     */
    public DocumentElementWheelEvent(ElementNode target, ElementNode currentTarget, int documentX, int documentY,
            int wheelDelta, long timeNanos) {
        this(target, currentTarget, documentX, documentY, wheelDelta, timeNanos, new DocumentEventControl());
    }

    /**
     * 创建元素滚轮事件（共享传播控制器）。
     *
     * @param target 初始命中元素
     * @param currentTarget 当前处理事件的元素
     * @param documentX 文档局部 X
     * @param documentY 文档局部 Y
     * @param wheelDelta 原始滚轮增量
     * @param timeNanos 事件时间戳
     * @param eventControl 共享传播控制器
     */
    public DocumentElementWheelEvent(ElementNode target, ElementNode currentTarget, int documentX, int documentY,
            int wheelDelta, long timeNanos, DocumentEventControl eventControl) {
        super(Objects.requireNonNull(target, "target"),
                Objects.requireNonNull(currentTarget, "currentTarget"),
                eventControl);
        this.documentX = documentX;
        this.documentY = documentY;
        this.wheelDelta = wheelDelta;
        this.timeNanos = timeNanos;
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
     * 返回原始滚轮增量。
     *
     * <p>该值沿用 LWJGL / Minecraft 输入方向：向上滚动通常为正，向下滚动通常为负。</p>
     *
     * @return 原始滚轮增量
     */
    public int getWheelDelta() {
        return wheelDelta;
    }

    /**
     * 返回浏览器式纵向滚轮增量。
     *
     * <p>正值表示内容应向下滚动，负值表示内容应向上滚动。</p>
     *
     * @return 浏览器式纵向滚轮增量
     */
    public int getDeltaY() {
        return -wheelDelta;
    }

    /**
     * 返回事件时间戳。
     *
     * @return 事件时间戳
     */
    public long getTimeNanos() {
        return timeNanos;
    }
}
