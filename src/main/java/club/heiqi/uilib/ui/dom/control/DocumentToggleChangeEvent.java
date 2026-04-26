package club.heiqi.uilib.ui.dom.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 开关状态变更事件。
 */
public final class DocumentToggleChangeEvent {

    private final DocumentToggleSwitchControl source;
    private final ElementNode element;
    private final boolean toggled;

    DocumentToggleChangeEvent(DocumentToggleSwitchControl source, ElementNode element, boolean toggled) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.toggled = toggled;
    }

    /**
     * 返回触发事件的开关控件。
     *
     * @return 开关控件
     */
    public DocumentToggleSwitchControl getSource() {
        return source;
    }

    /**
     * 返回开关控件对应的 HTML-like 元素。
     *
     * @return HTML-like 元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回当前开关状态。
     *
     * @return 是否已切换为开
     */
    public boolean isToggled() {
        return toggled;
    }
}
