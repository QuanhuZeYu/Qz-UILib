package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素悬停状态变化事件。
 */
public final class DocumentElementHoverEvent extends AbstractDocumentElementEvent {

    private final boolean hovered;
    private final int documentX;
    private final int documentY;
    private final long timeNanos;

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
        super(target, currentTarget, eventControl);
        this.hovered = hovered;
        this.documentX = documentX;
        this.documentY = documentY;
        this.timeNanos = timeNanos;
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
}
