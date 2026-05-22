package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * HTML-like 单选组选择变更事件。
 */
public final class DocumentRadioChangeEvent {

    private final DocumentRadioGroupControl source;
    private final ElementNode element;
    private final int selectedIndex;
    private final String selectedOption;
    private final boolean keyboardTriggered;

    DocumentRadioChangeEvent(DocumentRadioGroupControl source, ElementNode element, int selectedIndex,
            String selectedOption, boolean keyboardTriggered) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.selectedIndex = selectedIndex;
        this.selectedOption = selectedOption == null ? "" : selectedOption;
        this.keyboardTriggered = keyboardTriggered;
    }

    /**
     * 返回触发事件的单选组控件。
     *
     * @return 单选组控件
     */
    public DocumentRadioGroupControl getSource() {
        return source;
    }

    /**
     * 返回单选组根元素。
     *
     * @return 单选组根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回当前选中索引。
     *
     * @return 当前选中索引
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * 返回当前选中文本。
     *
     * @return 当前选中文本
     */
    public String getSelectedOption() {
        return selectedOption;
    }

    /**
     * 判断事件是否由键盘触发。
     *
     * @return 是否由键盘触发
     */
    public boolean isKeyboardTriggered() {
        return keyboardTriggered;
    }
}
