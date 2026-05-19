package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素过渡结束处理器。
 */
public interface DocumentElementTransitionEndHandler {

    /**
     * 处理元素 transitionend 事件。
     *
     * @param event 过渡结束事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onTransitionEnd(DocumentElementTransitionEndEvent event);
}
