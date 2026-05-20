package club.heiqi.uilib.ui.control;

/**
 * 文本输入变更处理器。
 */
public interface DocumentTextInputChangeHandler {

    /**
     * 文本内容变更时触发。
     *
     * @param event 变更事件
     */
    void onTextChanged(DocumentTextInputChangeEvent event);
}
