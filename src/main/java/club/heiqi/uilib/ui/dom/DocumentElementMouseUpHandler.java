package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素鼠标抬起处理器。
 */
public interface DocumentElementMouseUpHandler {
    /**
     * 处理元素鼠标抬起事件。
     *
     * @param event 鼠标抬起事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onMouseUp(DocumentElementMouseUpEvent event);
}
