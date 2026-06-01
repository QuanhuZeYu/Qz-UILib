package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素焦点离开处理器（冒泡版 blur）。
 */
public interface DocumentElementFocusOutHandler {
    /**
     * 处理元素焦点离开事件（冒泡）。
     *
     * @param event 焦点离开事件
     * @return 是否消费事件
     */
    boolean onFocusOut(DocumentElementFocusOutEvent event);
}
