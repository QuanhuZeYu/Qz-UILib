package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素过渡开始处理器。
 */
public interface DocumentElementTransitionStartHandler {

    /**
     * 处理元素 transitionstart 事件。
     *
     * @param event 过渡开始事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onTransitionStart(DocumentElementTransitionStartEvent event);
}
