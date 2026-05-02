package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素悬停处理器。
 */
public interface DocumentElementHoverHandler {

    /**
     * 处理元素悬停状态变化。
     *
     * @param event 悬停事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onHoverChanged(DocumentElementHoverEvent event);
}
