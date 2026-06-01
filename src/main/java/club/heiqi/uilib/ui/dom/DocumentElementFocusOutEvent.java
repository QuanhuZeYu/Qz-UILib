package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素焦点离开事件（冒泡版 blur）。
 */
public final class DocumentElementFocusOutEvent extends AbstractDocumentElementEvent {

    private final boolean focusVisible;

    public DocumentElementFocusOutEvent(ElementNode target, ElementNode currentTarget, boolean focusVisible) {
        this(target, currentTarget, focusVisible, new DocumentEventControl());
    }

    public DocumentElementFocusOutEvent(ElementNode target, ElementNode currentTarget, boolean focusVisible,
            DocumentEventControl eventControl) {
        super(Objects.requireNonNull(target, "target"),
                Objects.requireNonNull(currentTarget, "currentTarget"),
                eventControl);
        this.focusVisible = focusVisible;
    }

    public boolean isFocusVisible() { return focusVisible; }
}
