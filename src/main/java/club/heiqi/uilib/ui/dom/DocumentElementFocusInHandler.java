package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素焦点进入处理器（冒泡版 focus）。
 */
public interface DocumentElementFocusInHandler {
    /**
     * 处理元素焦点进入事件（冒泡）。
     *
     * @param event 焦点进入事件
     * @return 是否消费事件
     */
    boolean onFocusIn(DocumentElementFocusInEvent event);
}
