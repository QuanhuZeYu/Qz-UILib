package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素焦点变化事件。
 */
public final class DocumentElementFocusEvent {

    private final ElementNode target;
    private final boolean focused;

    /**
     * 创建元素焦点变化事件。
     *
     * @param target 焦点变化的元素
     * @param focused 是否获得焦点
     */
    public DocumentElementFocusEvent(ElementNode target, boolean focused) {
        this.target = target;
        this.focused = focused;
    }

    /**
     * 返回焦点变化的元素。
     *
     * @return 焦点元素
     */
    public ElementNode getTarget() {
        return target;
    }

    /**
     * 判断元素是否获得焦点。
     *
     * @return true 表示获得焦点，false 表示失去焦点
     */
    public boolean isFocused() {
        return focused;
    }
}
