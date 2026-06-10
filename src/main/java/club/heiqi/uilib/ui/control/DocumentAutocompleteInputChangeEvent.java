package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 自动完成输入框文本变更事件。
 */
public final class DocumentAutocompleteInputChangeEvent {

    private final DocumentAutocompleteInputControl source;
    private final ElementNode element;
    private final String text;

    DocumentAutocompleteInputChangeEvent(DocumentAutocompleteInputControl source, ElementNode element, String text) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.text = Objects.requireNonNull(text, "text");
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
     * 返回当前文本内容。
     *
     * @return 当前文本内容
     */
    public String getText() {
        return text;
    }
}
