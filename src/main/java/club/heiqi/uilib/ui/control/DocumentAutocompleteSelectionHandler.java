package club.heiqi.uilib.ui.control;

/**
 * 自动完成输入框候选选择处理器。
 */
public interface DocumentAutocompleteSelectionHandler {

    /**
     * 用户选择候选项时触发。
     *
     * @param event 候选选择事件
     */
    void onSuggestionSelected(DocumentAutocompleteSelectionEvent event);
}
