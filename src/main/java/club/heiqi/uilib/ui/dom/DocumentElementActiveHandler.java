package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素 active 状态处理器。
 */
public interface DocumentElementActiveHandler {

    /**
     * 处理元素 active 状态变化。
     *
     * @param event active 状态变化事件
     * @return 是否消费该事件；true 会停止向父元素冒泡
     */
    boolean onActiveChanged(DocumentElementActiveEvent event);
}
