package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素过渡取消处理器。
 */
public interface DocumentElementTransitionCancelHandler {

    /**
     * 处理元素 transitioncancel 事件。
     *
     * @param event 过渡取消事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onTransitionCancel(DocumentElementTransitionCancelEvent event);
}
