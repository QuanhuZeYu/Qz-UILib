package club.heiqi.uilib.ui.control;

/**
 * 动态键值编辑器行数据变更处理器。
 */
public interface DocumentKeyValueEditorChangeHandler {

    /**
     * 动态键值编辑器行数据发生变化。
     *
     * @param event 键值编辑器变更事件
     */
    void onRowsChanged(DocumentKeyValueEditorChangeEvent event);
}
