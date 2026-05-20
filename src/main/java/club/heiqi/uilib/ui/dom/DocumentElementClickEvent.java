package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素点击事件。
 */
public final class DocumentElementClickEvent extends AbstractDocumentElementEvent {

    private final int documentX;
    private final int documentY;
    private final int button;
    private final long timeNanos;

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
        super(Objects.requireNonNull(target, "target"),
                Objects.requireNonNull(currentTarget, "currentTarget"),
                eventControl);
        this.documentX = documentX;
        this.documentY = documentY;
        this.button = button;
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
}
