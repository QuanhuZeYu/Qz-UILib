package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.style.ComputedStyle;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.style.UiStyleResolver;

/**
 * HTML-like 文档布局引擎初版。
 *
 * <p>当前只实现 block formatting 的最小闭环：元素盒、box model、px/% 长度、auto 高度、
 * `display: none` 过滤与子元素垂直流式排布。</p>
 */
public final class DocumentLayoutEngine {

    private DocumentLayoutEngine() {}

    /**
     * 对根元素执行布局。
     *
     * @param rootElement 根元素
     * @param viewportWidth 视口宽度
     * @param viewportHeight 视口高度；当前初版仅作为后续扩展预留
     * @return 根布局盒
     */
    public static DocumentLayoutBox layout(ElementNode rootElement, int viewportWidth, int viewportHeight) {
        Objects.requireNonNull(rootElement, "rootElement");
        return layoutElement(rootElement, 0, 0, Math.max(0, viewportWidth));
    }

    private static DocumentLayoutBox layoutElement(ElementNode element, int containingLeft, int flowTop,
            int containingWidth) {
        ComputedStyle computedStyle = UiStyleResolver.compute(element);
        if (computedStyle.getDisplay() == UiDisplay.NONE) {
            return new DocumentLayoutBox(element, computedStyle, new ArrayList<DocumentLayoutBox>(),
                    DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), containingLeft,
                    flowTop, 0, 0);
        }

        DocumentLayoutEdges margin = resolveInsets(computedStyle.getMargin(), containingWidth, false);
        DocumentLayoutEdges border = resolveUniformEdge(computedStyle.getBorderWidth(), containingWidth);
        DocumentLayoutEdges padding = resolveInsets(computedStyle.getPadding(), containingWidth, true);

        int availableBorderBoxWidth = Math.max(0, containingWidth - margin.getHorizontal());
        int autoContentWidth = Math.max(0, availableBorderBoxWidth - border.getHorizontal() - padding.getHorizontal());
        int contentWidth = Math.max(0, computedStyle.getWidth().resolve(containingWidth, autoContentWidth));
        int borderBoxWidth = contentWidth + border.getHorizontal() + padding.getHorizontal();

        int borderBoxLeft = containingLeft + margin.getLeft();
        int borderBoxTop = flowTop + margin.getTop();
        int contentLeft = borderBoxLeft + border.getLeft() + padding.getLeft();
        int contentTop = borderBoxTop + border.getTop() + padding.getTop();

        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        int childFlowTop = contentTop;
        for (DocumentNode child : element.getChildren()) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            if (UiStyleResolver.compute(childElement).getDisplay() == UiDisplay.NONE) {
                continue;
            }
            DocumentLayoutBox childBox = layoutElement(childElement, contentLeft, childFlowTop, contentWidth);
            childBoxes.add(childBox);
            childFlowTop = childBox.getMarginBoxBottom();
        }

        int childrenContentHeight = Math.max(0, childFlowTop - contentTop);
        int contentHeight = Math.max(0, computedStyle.getHeight().resolve(0, childrenContentHeight));
        int borderBoxHeight = contentHeight + border.getVertical() + padding.getVertical();
        return new DocumentLayoutBox(element, computedStyle, childBoxes, margin, border, padding, borderBoxLeft,
                borderBoxTop, borderBoxWidth, borderBoxHeight);
    }

    private static DocumentLayoutEdges resolveInsets(UiStyleInsets insets, int containingWidth, boolean clampNonNegative) {
        int top = resolveEdge(insets.getTop(), containingWidth, clampNonNegative);
        int right = resolveEdge(insets.getRight(), containingWidth, clampNonNegative);
        int bottom = resolveEdge(insets.getBottom(), containingWidth, clampNonNegative);
        int left = resolveEdge(insets.getLeft(), containingWidth, clampNonNegative);
        return DocumentLayoutEdges.of(top, right, bottom, left);
    }

    private static DocumentLayoutEdges resolveUniformEdge(UiStyleLength length, int containingWidth) {
        int resolved = resolveEdge(length, containingWidth, true);
        return DocumentLayoutEdges.of(resolved, resolved, resolved, resolved);
    }

    private static int resolveEdge(UiStyleLength length, int containingWidth, boolean clampNonNegative) {
        int resolved = length.resolve(containingWidth, 0);
        return clampNonNegative ? Math.max(0, resolved) : resolved;
    }
}
