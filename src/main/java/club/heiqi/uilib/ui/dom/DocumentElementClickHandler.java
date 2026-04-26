package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素点击处理器。
 */
public interface DocumentElementClickHandler {

    /**
     * 处理元素点击事件。
     *
     * @param event 点击事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onClick(DocumentElementClickEvent event);
}
