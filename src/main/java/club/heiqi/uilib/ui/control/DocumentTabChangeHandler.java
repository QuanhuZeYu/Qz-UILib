package club.heiqi.uilib.ui.control;

/**
 * HTML-like 标签页切换处理器。
 */
public interface DocumentTabChangeHandler {

    /**
     * 处理标签页切换。
     *
     * @param event 标签页切换事件
     */
    void onTabChanged(DocumentTabChangeEvent event);
}
