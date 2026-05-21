package club.heiqi.uilib.ui.layout;

import java.util.List;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.AbsoluteContainingBlock;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.LayoutContext;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;

/**
 * absolute / fixed 脱流定位布局辅助器。
 */
final class PositionedLayoutHelper {

    private PositionedLayoutHelper() {}

    static void appendAbsoluteChildren(List<DocumentLayoutBox> childBoxes, List<ElementNode> absoluteChildren,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            LayoutContext layoutContext) {
        for (ElementNode child : absoluteChildren) {
            childBoxes.add(layoutPositionedElement(child, absoluteContainingBlock, fixedContainingBlock,
                    layoutContext));
        }
    }

    static void appendFixedChildren(List<DocumentLayoutBox> childBoxes, List<ElementNode> fixedChildren,
            AbsoluteContainingBlock fixedContainingBlock, LayoutContext layoutContext) {
        for (ElementNode child : fixedChildren) {
            childBoxes.add(layoutPositionedElement(child, fixedContainingBlock, fixedContainingBlock,
                    layoutContext));
        }
    }

    static AbsoluteContainingBlock resolveDirectAbsoluteContainingBlock(
            AbsoluteContainingBlock absoluteContainingBlock, boolean createsAbsoluteContainingBlock,
            int specifiedContentHeight, int contentHeight) {
        if (!createsAbsoluteContainingBlock) {
            return absoluteContainingBlock;
        }
        int contentBoxHeight = specifiedContentHeight >= 0 ? specifiedContentHeight : contentHeight;
        return absoluteContainingBlock.withContentHeight(contentBoxHeight);
    }

    private static DocumentLayoutBox layoutPositionedElement(ElementNode element,
            AbsoluteContainingBlock containingBlock, AbsoluteContainingBlock fixedContainingBlock,
            LayoutContext layoutContext) {
        ComputedStyle style = layoutContext.computeStyle(element);
        int forcedContentWidth = resolveStretchContentWidth(element, style, containingBlock, layoutContext);
        int forcedContentHeight = resolveStretchContentHeight(element, style, containingBlock, layoutContext);
        DocumentLayoutBox measuredBox = DocumentLayoutEngine.layoutElement(element, 0, 0, containingBlock.width,
                containingBlock.height, forcedContentWidth, forcedContentHeight, containingBlock, fixedContainingBlock,
                layoutContext);
        int marginBoxWidth = measuredBox.getWidth() + measuredBox.getMargin().getHorizontal();
        int marginBoxHeight = measuredBox.getHeight() + measuredBox.getMargin().getVertical();
        int marginBoxLeft = resolveAbsoluteMarginBoxLeft(style, containingBlock, marginBoxWidth);
        int marginBoxTop = resolveAbsoluteMarginBoxTop(style, containingBlock, marginBoxHeight);
        return DocumentLayoutEngine.layoutElement(element, marginBoxLeft, marginBoxTop, containingBlock.width,
                containingBlock.height, forcedContentWidth, forcedContentHeight, containingBlock, fixedContainingBlock,
                layoutContext);
    }

    private static int resolveStretchContentWidth(ElementNode element, ComputedStyle style,
            AbsoluteContainingBlock containingBlock, LayoutContext layoutContext) {
        if (!DocumentLayoutEngine.isAuto(style.getWidth()) || DocumentLayoutEngine.isAuto(style.getLeft())
                || DocumentLayoutEngine.isAuto(style.getRight())) {
            return DocumentLayoutEngine.AUTO_SIZE;
        }
        DocumentLayoutEdges margin = DocumentLayoutEngine.resolveMarginInsets(element, style, containingBlock.width,
                layoutContext.layoutValueResolver);
        DocumentLayoutEdges border = DocumentLayoutEngine.resolveBorderInsets(style, containingBlock.width);
        DocumentLayoutEdges padding = DocumentLayoutEngine.resolvePaddingInsets(element, style, containingBlock.width,
                layoutContext.layoutValueResolver);
        int leftInset = style.getLeft().resolve(containingBlock.width, 0);
        int rightInset = style.getRight().resolve(containingBlock.width, 0);
        return Math.max(0, containingBlock.width - leftInset - rightInset - margin.getHorizontal()
                - border.getHorizontal() - padding.getHorizontal());
    }

    private static int resolveStretchContentHeight(ElementNode element, ComputedStyle style,
            AbsoluteContainingBlock containingBlock, LayoutContext layoutContext) {
        if (!DocumentLayoutEngine.isAuto(style.getHeight()) || DocumentLayoutEngine.isAuto(style.getTop())
                || DocumentLayoutEngine.isAuto(style.getBottom())) {
            return DocumentLayoutEngine.AUTO_SIZE;
        }
        int containingHeight = Math.max(0, containingBlock.height);
        DocumentLayoutEdges margin = DocumentLayoutEngine.resolveMarginInsets(element, style, containingBlock.width,
                layoutContext.layoutValueResolver);
        DocumentLayoutEdges border = DocumentLayoutEngine.resolveBorderInsets(style, containingBlock.width);
        DocumentLayoutEdges padding = DocumentLayoutEngine.resolvePaddingInsets(element, style, containingBlock.width,
                layoutContext.layoutValueResolver);
        int topInset = style.getTop().resolve(containingHeight, 0);
        int bottomInset = style.getBottom().resolve(containingHeight, 0);
        return Math.max(0, containingHeight - topInset - bottomInset - margin.getVertical()
                - border.getVertical() - padding.getVertical());
    }

    private static int resolveAbsoluteMarginBoxLeft(ComputedStyle style,
            AbsoluteContainingBlock absoluteContainingBlock, int marginBoxWidth) {
        if (!DocumentLayoutEngine.isAuto(style.getLeft())) {
            return absoluteContainingBlock.left + style.getLeft().resolve(absoluteContainingBlock.width, 0);
        }
        if (!DocumentLayoutEngine.isAuto(style.getRight())) {
            return absoluteContainingBlock.left + absoluteContainingBlock.width
                    - style.getRight().resolve(absoluteContainingBlock.width, 0) - marginBoxWidth;
        }
        return absoluteContainingBlock.left;
    }

    private static int resolveAbsoluteMarginBoxTop(ComputedStyle style,
            AbsoluteContainingBlock absoluteContainingBlock, int marginBoxHeight) {
        int safeContainingHeight = Math.max(0, absoluteContainingBlock.height);
        if (!DocumentLayoutEngine.isAuto(style.getTop())) {
            return absoluteContainingBlock.top + style.getTop().resolve(safeContainingHeight, 0);
        }
        if (!DocumentLayoutEngine.isAuto(style.getBottom())) {
            return absoluteContainingBlock.top + safeContainingHeight
                    - style.getBottom().resolve(safeContainingHeight, 0) - marginBoxHeight;
        }
        return absoluteContainingBlock.top;
    }
}
