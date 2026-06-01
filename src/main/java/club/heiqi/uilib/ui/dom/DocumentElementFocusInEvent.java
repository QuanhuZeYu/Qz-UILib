package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素焦点进入事件（冒泡版 focus）。
 */
public final class DocumentElementFocusInEvent extends AbstractDocumentElementEvent {

    private final boolean focused;
    private final boolean focusVisible;

    public DocumentElementFocusInEvent(ElementNode target, ElementNode currentTarget, boolean focused,
            boolean focusVisible) {
        this(target, currentTarget, focused, focusVisible, new DocumentEventControl());
    }

    public DocumentElementFocusInEvent(ElementNode target, ElementNode currentTarget, boolean focused,
            boolean focusVisible, DocumentEventControl eventControl) {
        super(Objects.requireNonNull(target, "target"),
                Objects.requireNonNull(currentTarget, "currentTarget"),
                eventControl);
        this.focused = focused;
        this.focusVisible = focusVisible;
    }

    /**
     * 判断元素是否获得焦点。
     *
     * @return 始终为 true；失焦请使用 {@link DocumentElementFocusOutEvent}
     */
    public boolean isFocused() { return focused; }
    public boolean isFocusVisible() { return focusVisible; }
}
