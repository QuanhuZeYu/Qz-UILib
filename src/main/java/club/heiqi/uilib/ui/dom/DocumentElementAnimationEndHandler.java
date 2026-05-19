package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素动画结束处理器。
 */
public interface DocumentElementAnimationEndHandler {

    /**
     * 处理元素 animationend 事件。
     *
     * @param event 动画结束事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onAnimationEnd(DocumentElementAnimationEndEvent event);
}
