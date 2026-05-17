package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素鼠标按下处理器。
 */
public interface DocumentElementMouseDownHandler {
    /**
     * 处理元素鼠标按下事件。
     *
     * @param event 鼠标按下事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onMouseDown(DocumentElementMouseDownEvent event);
}
