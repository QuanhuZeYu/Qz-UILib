package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 自动完成输入框候选选择事件。
 */
public final class DocumentAutocompleteSelectionEvent {

    private final DocumentAutocompleteInputControl source;
    private final ElementNode element;
    private final int selectedIndex;
    private final String selectedSuggestion;
    private final String query;
    private final boolean keyboardTriggered;
    private final int keyCode;
    private final int button;
    private final long timeNanos;

    DocumentAutocompleteSelectionEvent(DocumentAutocompleteInputControl source, ElementNode element, int selectedIndex,
            String selectedSuggestion, String query, boolean keyboardTriggered, int keyCode, int button,
            long timeNanos) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.selectedIndex = selectedIndex;
        this.selectedSuggestion = Objects.requireNonNull(selectedSuggestion, "selectedSuggestion");
        this.query = Objects.requireNonNull(query, "query");
        this.keyboardTriggered = keyboardTriggered;
        this.keyCode = keyCode;
        this.button = button;
        this.timeNanos = timeNanos;
    }

    /**
     * 返回触发事件的自动完成输入框。
     *
     * @return 自动完成输入框
     */
    public DocumentAutocompleteInputControl getSource() {
        return source;
    }

    /**
     * 返回控件根元素。
     *
     * @return 控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回候选在选择发生时结果列表中的索引。
     *
     * @return 选择发生时的结果索引
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * 返回被选中的候选文本。
     *
     * @return 候选文本
     */
    public String getSelectedSuggestion() {
        return selectedSuggestion;
    }

    /**
     * 返回选择发生前的查询文本。
     *
     * @return 查询文本
     */
    public String getQuery() {
        return query;
    }

    /**
     * 判断本次选择是否由键盘触发。
     *
     * @return 是否由键盘触发
     */
    public boolean isKeyboardTriggered() {
        return keyboardTriggered;
    }

    /**
     * 返回键盘触发时的键码。
     *
     * @return 键码；非键盘触发为 0
     */
    public int getKeyCode() {
        return keyCode;
    }

    /**
     * 返回鼠标触发时的按钮编号。
     *
     * @return 按钮编号；非鼠标触发为 -1
     */
    public int getButton() {
        return button;
    }

    /**
     * 返回事件时间戳。
     *
     * @return 时间戳
     */
    public long getTimeNanos() {
        return timeNanos;
    }
}
