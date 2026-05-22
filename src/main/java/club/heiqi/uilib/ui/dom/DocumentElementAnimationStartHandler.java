package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素动画开始处理器。
 */
public interface DocumentElementAnimationStartHandler {

    /**
     * 处理元素 animationstart 事件。
     *
     * @param event 动画开始事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onAnimationStart(DocumentElementAnimationStartEvent event);
}
