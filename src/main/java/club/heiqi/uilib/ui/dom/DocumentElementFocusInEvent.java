package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素焦点进入事件（冒泡版 focus）。
 */
public final class DocumentElementFocusInEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final boolean focused;
    private final boolean focusVisible;

    public DocumentElementFocusInEvent(ElementNode target, ElementNode currentTarget, boolean focused,
            boolean focusVisible) {
        this.target = Objects.requireNonNull(target, "target");
        this.currentTarget = Objects.requireNonNull(currentTarget, "currentTarget");
        this.focused = focused;
        this.focusVisible = focusVisible;
    }

    public ElementNode getTarget() { return target; }
    public ElementNode getCurrentTarget() { return currentTarget; }
    public boolean isFocused() { return focused; }
    public boolean isFocusVisible() { return focusVisible; }
}
