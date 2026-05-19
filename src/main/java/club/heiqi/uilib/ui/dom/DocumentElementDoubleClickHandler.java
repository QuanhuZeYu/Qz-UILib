package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素双击处理器。
 */
public interface DocumentElementDoubleClickHandler {

    /**
     * 处理元素双击事件。
     *
     * @param event 双击事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onDoubleClick(DocumentElementDoubleClickEvent event);
}
