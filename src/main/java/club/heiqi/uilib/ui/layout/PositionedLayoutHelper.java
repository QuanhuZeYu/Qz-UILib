package club.heiqi.uilib.ui.layout;

import java.util.List;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.AbsoluteContainingBlock;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.LayoutContext;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;

/**
 * absolute / fixed 脱流定位布局辅助器。
 *
 * <p>fixed 的 containing block 由调用方按浏览器语义传入：默认是视口，遇到 transform 祖先时为该祖先
 * padding box。</p>
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

    static DocumentLayoutBox layoutPositionedElement(ElementNode element,
            AbsoluteContainingBlock containingBlock, AbsoluteContainingBlock fixedContainingBlock,
            LayoutContext layoutContext) {
        ComputedStyle style = layoutContext.computeStyle(element);
        int forcedContentWidth = resolveStretchContentWidth(element, style, containingBlock, layoutContext);
        int forcedContentHeight = resolveStretchContentHeight(element, style, containingBlock, layoutContext);
        boolean needsMeasuredWidth = requiresMeasuredMarginBoxWidth(style);
        boolean needsMeasuredHeight = requiresMeasuredMarginBoxHeight(style);
        int marginBoxWidth = 0;
        int marginBoxHeight = 0;
        if (needsMeasuredWidth || needsMeasuredHeight) {
            DocumentLayoutBox measuredBox = DocumentLayoutEngine.layoutElement(element, 0, 0, containingBlock.width,
                    containingBlock.height, forcedContentWidth, forcedContentHeight, containingBlock,
                    fixedContainingBlock, layoutContext);
            if (needsMeasuredWidth) {
                marginBoxWidth = measuredBox.getWidth() + measuredBox.getMargin().getHorizontal();
            }
            if (needsMeasuredHeight) {
                marginBoxHeight = measuredBox.getHeight() + measuredBox.getMargin().getVertical();
            }
        }
        int marginBoxLeft = resolveAbsoluteMarginBoxLeft(element, style, containingBlock, marginBoxWidth,
                layoutContext);
        int marginBoxTop = resolveAbsoluteMarginBoxTop(element, style, containingBlock, marginBoxHeight,
                layoutContext);
        return DocumentLayoutEngine.layoutElement(element, marginBoxLeft, marginBoxTop, containingBlock.width,
                containingBlock.height, forcedContentWidth, forcedContentHeight, containingBlock, fixedContainingBlock,
                layoutContext);
    }

    /**
     * 仅 right 单边锚定时需要先测量 margin box 宽度来反推 left。
     */
    private static boolean requiresMeasuredMarginBoxWidth(ComputedStyle style) {
        if (DocumentLayoutEngine.isAuto(style.getLeft()) && !DocumentLayoutEngine.isAuto(style.getRight())) {
            return true;
        }
        return !DocumentLayoutEngine.isAuto(style.getLeft()) && !DocumentLayoutEngine.isAuto(style.getRight())
                && !DocumentLayoutEngine.isAuto(style.getWidth())
                && (DocumentLayoutEngine.isAuto(style.getMargin().getLeft())
                        || DocumentLayoutEngine.isAuto(style.getMargin().getRight()));
    }

    /**
     * 仅 bottom 单边锚定时需要先测量 margin box 高度来反推 top。
     */
    private static boolean requiresMeasuredMarginBoxHeight(ComputedStyle style) {
        return DocumentLayoutEngine.isAuto(style.getTop()) && !DocumentLayoutEngine.isAuto(style.getBottom());
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
        int leftInset = DocumentLayoutEngine.resolvePositionInsetValue(element, style.getLeft(),
                DocumentAnimationProperty.LEFT, containingBlock.width, layoutContext.layoutValueResolver);
        int rightInset = DocumentLayoutEngine.resolvePositionInsetValue(element, style.getRight(),
                DocumentAnimationProperty.RIGHT, containingBlock.width, layoutContext.layoutValueResolver);
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
        int topInset = DocumentLayoutEngine.resolvePositionInsetValue(element, style.getTop(),
                DocumentAnimationProperty.TOP, containingHeight, layoutContext.layoutValueResolver);
        int bottomInset = DocumentLayoutEngine.resolvePositionInsetValue(element, style.getBottom(),
                DocumentAnimationProperty.BOTTOM, containingHeight, layoutContext.layoutValueResolver);
        return Math.max(0, containingHeight - topInset - bottomInset - margin.getVertical()
                - border.getVertical() - padding.getVertical());
    }

    private static int resolveAbsoluteMarginBoxLeft(ElementNode element, ComputedStyle style,
            AbsoluteContainingBlock absoluteContainingBlock, int marginBoxWidth, LayoutContext layoutContext) {
        if (!DocumentLayoutEngine.isAuto(style.getLeft()) && !DocumentLayoutEngine.isAuto(style.getRight())
                && !DocumentLayoutEngine.isAuto(style.getWidth())) {
            DocumentLayoutEdges margin = DocumentLayoutEngine.resolveMarginInsets(element, style,
                    absoluteContainingBlock.width, layoutContext.layoutValueResolver);
            boolean autoLeft = DocumentLayoutEngine.isAuto(style.getMargin().getLeft());
            boolean autoRight = DocumentLayoutEngine.isAuto(style.getMargin().getRight());
            if (autoLeft || autoRight) {
                int leftInset = DocumentLayoutEngine.resolvePositionInsetValue(element, style.getLeft(),
                        DocumentAnimationProperty.LEFT, absoluteContainingBlock.width, layoutContext.layoutValueResolver);
                int rightInset = DocumentLayoutEngine.resolvePositionInsetValue(element, style.getRight(),
                        DocumentAnimationProperty.RIGHT, absoluteContainingBlock.width, layoutContext.layoutValueResolver);
                int remaining = Math.max(0, absoluteContainingBlock.width - leftInset - rightInset - marginBoxWidth);
                int resolvedLeftMargin = autoLeft && autoRight ? remaining / 2 : autoLeft ? remaining : margin.getLeft();
                return absoluteContainingBlock.left + leftInset + resolvedLeftMargin;
            }
        }
        if (!DocumentLayoutEngine.isAuto(style.getLeft())) {
            return absoluteContainingBlock.left + DocumentLayoutEngine.resolvePositionInsetValue(element,
                    style.getLeft(), DocumentAnimationProperty.LEFT, absoluteContainingBlock.width,
                    layoutContext.layoutValueResolver);
        }
        if (!DocumentLayoutEngine.isAuto(style.getRight())) {
            return absoluteContainingBlock.left + absoluteContainingBlock.width
                    - DocumentLayoutEngine.resolvePositionInsetValue(element, style.getRight(),
                            DocumentAnimationProperty.RIGHT, absoluteContainingBlock.width,
                            layoutContext.layoutValueResolver) - marginBoxWidth;
        }
        return absoluteContainingBlock.left;
    }

    private static int resolveAbsoluteMarginBoxTop(ElementNode element, ComputedStyle style,
            AbsoluteContainingBlock absoluteContainingBlock, int marginBoxHeight, LayoutContext layoutContext) {
        int safeContainingHeight = Math.max(0, absoluteContainingBlock.height);
        if (!DocumentLayoutEngine.isAuto(style.getTop())) {
            return absoluteContainingBlock.top + DocumentLayoutEngine.resolvePositionInsetValue(element, style.getTop(),
                    DocumentAnimationProperty.TOP, safeContainingHeight, layoutContext.layoutValueResolver);
        }
        if (!DocumentLayoutEngine.isAuto(style.getBottom())) {
            return absoluteContainingBlock.top + safeContainingHeight
                    - DocumentLayoutEngine.resolvePositionInsetValue(element, style.getBottom(),
                            DocumentAnimationProperty.BOTTOM, safeContainingHeight,
                            layoutContext.layoutValueResolver) - marginBoxHeight;
        }
        return absoluteContainingBlock.top;
    }
}
