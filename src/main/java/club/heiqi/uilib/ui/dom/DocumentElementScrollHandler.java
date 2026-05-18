package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素滚动事件处理器。
 *
 * <p>当元素内部滚动位置变化时回调。</p>
 */
public interface DocumentElementScrollHandler {

    /**
     * 处理滚动事件。
     *
     * @param event 滚动事件
     */
    void onScroll(DocumentElementScrollEvent event);
}
