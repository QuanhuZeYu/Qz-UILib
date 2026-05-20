package club.heiqi.uilib.ui.control;

/**
 * HTML-like 分段选择控件的选择变更处理器。
 */
public interface DocumentSegmentedSelectionHandler {

    /**
     * 处理选择变更。
     *
     * @param event 选择变更事件
     */
    void onSelectionChanged(DocumentSegmentedSelectionEvent event);
}
