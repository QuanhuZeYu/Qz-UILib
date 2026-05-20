package club.heiqi.uilib.ui.control;

/**
 * 多行文本输入内容变更处理器。
 */
public interface DocumentTextAreaChangeHandler {

    /**
     * 当用户交互导致多行文本内容变化时触发。
     *
     * @param event 文本变更事件
     */
    void onTextChanged(DocumentTextAreaChangeEvent event);
}
