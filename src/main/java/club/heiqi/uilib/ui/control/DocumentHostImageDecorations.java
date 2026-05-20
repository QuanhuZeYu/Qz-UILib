package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 宿主图片装饰辅助入口。
 */
public final class DocumentHostImageDecorations {

    private DocumentHostImageDecorations() {}

    /**
     * 在目标元素内容区后方挂载一层宿主背景贴图。
     *
     * <p>该装饰层使用 absolute 定位、负 z-index 和命中隐藏属性，行为更接近 CSS 背景图，
     * 不会截获目标元素本身的点击、悬停或键盘交互。</p>
     *
     * @param target 目标元素
     * @param source 图片源
     * @return 创建出的背景图片元素
     */
    public static ElementNode attachBackground(ElementNode target, HostImageSource source) {
        ElementNode resolvedTarget = Objects.requireNonNull(target, "target");
        HostImageSource resolvedSource = Objects.requireNonNull(source, "source");
        DocumentHostImageControl imageControl = new DocumentHostImageControl(resolvedTarget.getOwnerDocument(), resolvedSource);
        ElementNode imageElement = imageControl.getElement();
        imageElement.setAttribute("data-host-background-image", "true");
        imageElement.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setZIndex(-1);
        if (resolvedTarget.style().getPosition() == null) {
            resolvedTarget.style().setPosition(UiPosition.RELATIVE);
        }
        if (resolvedTarget.style().getOverflowX() == null) {
            resolvedTarget.style().setOverflowX(UiOverflow.HIDDEN);
        }
        if (resolvedTarget.style().getOverflowY() == null) {
            resolvedTarget.style().setOverflowY(UiOverflow.HIDDEN);
        }
        resolvedTarget.append(imageElement);
        return imageElement;
    }
}
