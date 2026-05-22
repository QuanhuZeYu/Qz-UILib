package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * HTML-like 复选框状态变更事件。
 */
public final class DocumentCheckboxChangeEvent {

    private final DocumentCheckboxControl source;
    private final ElementNode element;
    private final boolean checked;
    private final boolean indeterminate;

    DocumentCheckboxChangeEvent(DocumentCheckboxControl source, ElementNode element, boolean checked,
            boolean indeterminate) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.checked = checked;
        this.indeterminate = indeterminate;
    }

    /**
     * 返回触发事件的复选框控件。
     *
     * @return 复选框控件
     */
    public DocumentCheckboxControl getSource() {
        return source;
    }

    /**
     * 返回复选框控件对应的 HTML-like 元素。
     *
     * @return HTML-like 元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 判断复选框是否已选中。
     *
     * @return 是否已选中
     */
    public boolean isChecked() {
        return checked;
    }

    /**
     * 判断复选框是否处于半选状态。
     *
     * @return 是否半选
     */
    public boolean isIndeterminate() {
        return indeterminate;
    }
}
