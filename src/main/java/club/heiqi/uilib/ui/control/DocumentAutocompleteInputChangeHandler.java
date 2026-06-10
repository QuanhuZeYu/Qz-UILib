package club.heiqi.uilib.ui.control;

/**
 * 自动完成输入框文本变更处理器。
 */
public interface DocumentAutocompleteInputChangeHandler {

    /**
     * 文本内容因用户交互变化时触发。
     *
     * @param event 文本变更事件
     */
    void onTextChanged(DocumentAutocompleteInputChangeEvent event);
}
