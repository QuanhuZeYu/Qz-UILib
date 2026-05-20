package club.heiqi.uilib.ui.dom;

/**
 * 自定义 DOM 事件处理器。
 */
public interface DocumentCustomEventHandler {

    /**
     * 处理自定义事件。
     *
     * <p>返回 true 时，等价于当前 listener 主动消费事件并停止后续传播。</p>
     *
     * @param event 自定义事件
     * @return 是否消费并停止传播
     */
    boolean onEvent(DocumentCustomEvent event);
}
