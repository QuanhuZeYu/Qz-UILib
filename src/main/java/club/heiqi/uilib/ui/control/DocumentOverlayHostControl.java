package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 页面级 overlay 子树宿主。
 */
public final class DocumentOverlayHostControl {

    private final ElementNode element;

    /**
     * 创建 overlay 宿主。
     *
     * @param document 所属文档
     */
    public DocumentOverlayHostControl(UiDocument document) {
        this.element = Objects.requireNonNull(document, "document").div();
        configureElement();
    }

    /**
     * 返回宿主根元素。
     *
     * @return 根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 追加 overlay 层元素。
     *
     * @param overlayElement overlay 层元素
     * @return 当前宿主
     */
    public DocumentOverlayHostControl appendOverlay(ElementNode overlayElement) {
        element.append(Objects.requireNonNull(overlayElement, "overlayElement"));
        return this;
    }

    private void configureElement() {
        element.setAttribute("data-overlay-host", "true");
        element.style()
                .setWidth(UiStyleLength.px(0))
                .setHeight(UiStyleLength.px(0))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
    }
}
