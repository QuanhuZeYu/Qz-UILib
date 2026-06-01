package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素滚轮事件处理器。
 */
public interface DocumentElementWheelHandler {

    /**
     * 处理元素滚轮事件。
     *
     * <p>返回 true 会停止后续传播；如需阻止默认滚动，应调用 {@link DocumentElementWheelEvent#preventDefault()}。</p>
     *
     * @param event 滚轮事件
     * @return 是否消费事件并停止传播
     */
    boolean onWheel(DocumentElementWheelEvent event);
}
