package club.heiqi.uilib.ui.control;

/**
 * 数据表格行数据变更处理器。
 */
public interface DocumentDataTableChangeHandler {

    /**
     * 数据表格行数据发生变化。
     *
     * @param event 表格变更事件
     */
    void onTableChanged(DocumentDataTableChangeEvent event);
}
