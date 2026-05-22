package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * HTML-like 标签页切换事件。
 */
public final class DocumentTabChangeEvent {

    private final DocumentTabControl source;
    private final ElementNode element;
    private final int activeIndex;
    private final String activeLabel;
    private final boolean keyboardTriggered;

    DocumentTabChangeEvent(DocumentTabControl source, ElementNode element, int activeIndex, String activeLabel,
            boolean keyboardTriggered) {
        this.source = Objects.requireNonNull(source, "source");
        this.element = Objects.requireNonNull(element, "element");
        this.activeIndex = activeIndex;
        this.activeLabel = activeLabel == null ? "" : activeLabel;
        this.keyboardTriggered = keyboardTriggered;
    }

    /**
     * 返回触发事件的标签页控件。
     *
     * @return 标签页控件
     */
    public DocumentTabControl getSource() {
        return source;
    }

    /**
     * 返回标签页控件根元素。
     *
     * @return 标签页控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回当前活动标签索引。
     *
     * @return 当前活动标签索引
     */
    public int getActiveIndex() {
        return activeIndex;
    }

    /**
     * 返回当前活动标签文本。
     *
     * @return 当前活动标签文本
     */
    public String getActiveLabel() {
        return activeLabel;
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
