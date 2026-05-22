package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * HTML-like 滑块数值变更事件。
 */
public final class DocumentSliderChangeEvent {

    private final DocumentSliderControl source;
    private final ElementNode element;
    private final double value;
    private final boolean committing;
    private final boolean userTriggered;

    DocumentSliderChangeEvent(DocumentSliderControl source, ElementNode element, double value, boolean committing,
            boolean userTriggered) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.value = value;
        this.committing = committing;
        this.userTriggered = userTriggered;
    }

    /**
     * 返回触发事件的滑块控件。
     *
     * @return 滑块控件
     */
    public DocumentSliderControl getSource() {
        return source;
    }

    /**
     * 返回滑块控件根元素。
     *
     * @return 滑块控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回当前数值。
     *
     * @return 当前数值
     */
    public double getValue() {
        return value;
    }

    /**
     * 判断当前变更是否为提交态。
     *
     * @return 是否提交
     */
    public boolean isCommitting() {
        return committing;
    }

    /**
     * 判断当前变更是否由用户交互触发。
     *
     * @return 是否由用户触发
     */
    public boolean isUserTriggered() {
        return userTriggered;
    }
}
