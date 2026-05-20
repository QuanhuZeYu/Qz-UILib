package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 通用页面级 overlay 层控件。
 */
public final class DocumentOverlayLayerControl {

    private static final int HIDDEN_OFFSET = -10000;

    private final ElementNode element;

    /**
     * 创建指定标签名的 overlay 层。
     *
     * @param document 所属文档
     * @param tagName 标签名
     */
    public DocumentOverlayLayerControl(UiDocument document, String tagName) {
        this(Objects.requireNonNull(document, "document").element(tagName));
    }

    /**
     * 把现有元素升级为 overlay 层。
     *
     * @param element 目标元素
     */
    public DocumentOverlayLayerControl(ElementNode element) {
        this.element = Objects.requireNonNull(element, "element");
        configureElement();
        hideOffscreen();
    }

    /**
     * 返回 overlay 层根元素。
     *
     * @return 根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 设置层级。
     *
     * @param zIndex 层级
     * @return 当前层
     */
    public DocumentOverlayLayerControl setZIndex(int zIndex) {
        element.style().setZIndex(zIndex);
        return this;
    }

    /**
     * 设置是否命中隐藏。
     *
     * @param hitTestHidden 是否命中隐藏
     * @return 当前层
     */
    public DocumentOverlayLayerControl setHitTestHidden(boolean hitTestHidden) {
        if (hitTestHidden) {
            element.setAttribute("data-hit-test-hidden", "true");
        } else {
            element.removeAttribute("data-hit-test-hidden");
        }
        return this;
    }

    /**
     * 设置 overlay 层左上角位置。
     *
     * @param left 左边界
     * @param top 上边界
     * @return 当前层
     */
    public DocumentOverlayLayerControl setOverlayPosition(int left, int top) {
        element.style()
                .setLeft(UiStyleLength.px(left))
                .setTop(UiStyleLength.px(top));
        return this;
    }

    /**
     * 设置 overlay 层尺寸。
     *
     * @param width 宽度
     * @param height 高度
     * @return 当前层
     */
    public DocumentOverlayLayerControl setOverlaySize(int width, int height) {
        element.style()
                .setWidth(UiStyleLength.px(Math.max(0, width)))
                .setHeight(UiStyleLength.px(Math.max(0, height)));
        return this;
    }

    /**
     * 将 overlay 层移到离屏位置。
     *
     * @return 当前层
     */
    public DocumentOverlayLayerControl hideOffscreen() {
        return setOverlayPosition(HIDDEN_OFFSET, HIDDEN_OFFSET);
    }

    /**
     * 将 overlay 层缩成 0x0 并移到离屏位置。
     *
     * @return 当前层
     */
    public DocumentOverlayLayerControl collapseOffscreen() {
        return setOverlaySize(0, 0).hideOffscreen();
    }

    private void configureElement() {
        element.setAttribute("data-overlay-layer", "true");
        element.style()
                .setPosition(UiPosition.FIXED)
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
    }
}
