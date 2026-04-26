package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素焦点变化处理器。
 */
public interface DocumentElementFocusHandler {

    /**
     * 处理元素焦点变化。
     *
     * @param event 焦点变化事件
     */
    void onFocusChanged(DocumentElementFocusEvent event);
}
