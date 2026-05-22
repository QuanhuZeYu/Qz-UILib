package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素动画迭代处理器。
 */
public interface DocumentElementAnimationIterationHandler {

    /**
     * 处理元素 animationiteration 事件。
     *
     * @param event 动画迭代事件
     * @return 是否消费事件；返回 false 时事件会继续向父元素冒泡
     */
    boolean onAnimationIteration(DocumentElementAnimationIterationEvent event);
}
