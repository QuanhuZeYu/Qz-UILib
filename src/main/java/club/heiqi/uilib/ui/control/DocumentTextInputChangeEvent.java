package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * 文本输入变更事件。
 */
public final class DocumentTextInputChangeEvent {

    private final DocumentTextInputControl source;
    private final ElementNode element;
    private final String text;

    DocumentTextInputChangeEvent(DocumentTextInputControl source, ElementNode element, String text) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.text = Objects.requireNonNull(text, "text");
    }

    /**
     * 返回触发事件的文本输入控件。
     *
     * @return 文本输入控件
     */
    public DocumentTextInputControl getSource() {
        return source;
    }

    /**
     * 返回文本输入控件对应的 HTML-like 元素。
     *
     * @return HTML-like 元素
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
