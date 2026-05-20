package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 页面级鼠标跟随图片浮层控件。
 */
public final class DocumentCursorOverlayControl {

    /**
     * 指针位置提供器。
     */
    public interface PointerProvider {

        /**
         * 返回当前视口内指针 X。
         *
         * @return 指针 X
         */
        int getPointerX();

        /**
         * 返回当前视口内指针 Y。
         *
         * @return 指针 Y
         */
        int getPointerY();
    }

    private final DocumentOverlayLayerControl overlayLayer;
    private final DocumentHostImageControl imageControl;
    private final PointerProvider pointerProvider;
    private int size = 24;
    private int anchorOffset = 12;
    private HostImageSource source;

    /**
     * 创建鼠标跟随图片浮层控件。
     *
     * @param document 所属文档
     * @param pointerProvider 指针位置提供器
     * @param placeholderSource 占位图片源
     */
    public DocumentCursorOverlayControl(UiDocument document, PointerProvider pointerProvider,
            HostImageSource placeholderSource) {
        this.pointerProvider = Objects.requireNonNull(pointerProvider, "pointerProvider");
        this.imageControl = new DocumentHostImageControl(Objects.requireNonNull(document, "document"),
                Objects.requireNonNull(placeholderSource, "placeholderSource"));
        this.overlayLayer = new DocumentOverlayLayerControl(imageControl.getElement())
                .setHitTestHidden(true)
                .setZIndex(1001)
                .hideOffscreen();
        configureElement();
    }

    /**
     * 返回浮层根元素。
     *
     * @return 根元素
     */
    public ElementNode getElement() {
        return imageControl.getElement();
    }

    /**
     * 返回底层 overlay 层。
     *
     * @return overlay 层
     */
    public DocumentOverlayLayerControl getOverlayLayer() {
        return overlayLayer;
    }

    /**
     * 设置浮层尺寸。
     *
     * @param size 尺寸
     * @return 当前控件
     */
    public DocumentCursorOverlayControl setSize(int size) {
        this.size = Math.max(1, size);
        imageControl.setSize(this.size);
        imageControl.getElement().style()
                .setWidth(UiStyleLength.px(this.size))
                .setHeight(UiStyleLength.px(this.size));
        return this;
    }

    /**
     * 设置锚点偏移。
     *
     * @param anchorOffset 指针相对左上角偏移
     * @return 当前控件
     */
    public DocumentCursorOverlayControl setAnchorOffset(int anchorOffset) {
        this.anchorOffset = Math.max(0, anchorOffset);
        return this;
    }

    /**
     * 设置层级。
     *
     * @param zIndex 层级
     * @return 当前控件
     */
    public DocumentCursorOverlayControl setZIndex(int zIndex) {
        overlayLayer.setZIndex(zIndex);
        return this;
    }

    /**
     * 设置当前图片源。
     *
     * @param source 图片源
     * @return 当前控件
     */
    public DocumentCursorOverlayControl setSource(HostImageSource source) {
        this.source = source;
        return this;
    }

    /**
     * 刷新当前可见性与位置。
     *
     * @return 当前控件
     */
    public DocumentCursorOverlayControl refresh() {
        ElementNode element = imageControl.getElement();
        if (source == null) {
            element.style().setDisplay(UiDisplay.NONE);
            overlayLayer.hideOffscreen();
            return this;
        }
        imageControl.setSource(source);
        element.style().setDisplay(UiDisplay.BLOCK);
        overlayLayer.setOverlayPosition(pointerProvider.getPointerX() - anchorOffset,
                pointerProvider.getPointerY() - anchorOffset);
        return this;
    }

    private void configureElement() {
        ElementNode element = imageControl.getElement();
        element.setAttribute("data-cursor-overlay", "true");
        element.style()
                .setWidth(UiStyleLength.px(size))
                .setHeight(UiStyleLength.px(size))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE)
                .setDisplay(UiDisplay.NONE);
    }
}
