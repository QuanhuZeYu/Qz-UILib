package club.heiqi.uilib.ui.control;

/**
 * HTML-like 复选框状态变更处理器。
 */
public interface DocumentCheckboxChangeHandler {

    /**
     * 处理复选框选中状态变化。
     *
     * @param event 复选框状态变更事件
     */
    void onCheckboxChanged(DocumentCheckboxChangeEvent event);
}
