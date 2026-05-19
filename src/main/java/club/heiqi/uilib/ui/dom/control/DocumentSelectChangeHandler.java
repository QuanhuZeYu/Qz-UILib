package club.heiqi.uilib.ui.dom.control;

/**
 * 下拉选择控件变更处理器。
 */
public interface DocumentSelectChangeHandler {

    /**
     * 当用户交互导致当前选择变化时触发。
     *
     * @param event 选择变更事件
     */
    void onSelectionChanged(DocumentSelectChangeEvent event);
}
