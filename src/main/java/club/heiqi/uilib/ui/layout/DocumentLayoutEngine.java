package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.style.ComputedStyle;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.style.UiStyleResolver;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * HTML-like 文档布局引擎初版。
 *
 * <p>当前实现覆盖元素盒、box model、block flow、最小 flex flow、relative 定位偏移、absolute 脱流定位
 * 与 fixed 视口定位。absolute 元素会相对最近 positioned ancestor 的 content box 定位，没有 positioned
 * ancestor 时回退根 content box；fixed 元素相对当前 HTML-like 视口定位。文本 inline formatting、
 * 多行 flex wrap 和滚动布局会在后续阶段继续扩展。</p>
 */
public final class DocumentLayoutEngine {

    private static final int AUTO_SIZE = -1;
    private static final float UI_TEXT_SCALE = 2.0F;
    private static final TextMeasureService FALLBACK_TEXT_MEASURE_SERVICE = new FixedTextMeasureService();

    private DocumentLayoutEngine() {}

    /**
     * 对根元素执行布局。
     *
     * @param rootElement 根元素
     * @param viewportWidth 视口宽度
     * @param viewportHeight 视口高度，用作 fixed 定位的视口 containing block 高度
     * @return 根布局盒
     */
    public static DocumentLayoutBox layout(ElementNode rootElement, int viewportWidth, int viewportHeight) {
        return layout(rootElement, viewportWidth, viewportHeight, FALLBACK_TEXT_MEASURE_SERVICE);
    }

    /**
     * 使用指定文本测量服务对根元素执行布局。
     *
     * @param rootElement 根元素
     * @param viewportWidth 视口宽度
     * @param viewportHeight 视口高度；当前仅作为后续视口约束扩展预留
     * @param textMeasureService 文本测量服务
     * @return 根布局盒
     */
    public static DocumentLayoutBox layout(ElementNode rootElement, int viewportWidth, int viewportHeight,
            TextMeasureService textMeasureService) {
        Objects.requireNonNull(rootElement, "rootElement");
        int safeViewportWidth = Math.max(0, viewportWidth);
        int safeViewportHeight = Math.max(0, viewportHeight);
        AbsoluteContainingBlock fixedContainingBlock = new AbsoluteContainingBlock(0, 0, safeViewportWidth,
                safeViewportHeight);
        return layoutElement(rootElement, 0, 0, safeViewportWidth, AUTO_SIZE, AUTO_SIZE,
                null, fixedContainingBlock, Objects.requireNonNull(textMeasureService, "textMeasureService"));
    }

    /**
     * 对根元素执行视口布局，让根 border box 固定为传入视口尺寸。
     *
     * <p>该入口用于页面级 HTML-like 滚动：根元素本身保持固定视口，超出的子内容由
     * `DocumentScrollState` 根据 overflow 语义滚动，而不是继续扩大外层 retained widget。</p>
     *
     * @param rootElement 根元素
     * @param viewportWidth 视口宽度
     * @param viewportHeight 视口高度
     * @param textMeasureService 文本测量服务
     * @return 根布局盒
     */
    public static DocumentLayoutBox layoutViewportRoot(ElementNode rootElement, int viewportWidth, int viewportHeight,
            TextMeasureService textMeasureService) {
        Objects.requireNonNull(rootElement, "rootElement");
        TextMeasureService resolvedTextMeasureService = Objects.requireNonNull(textMeasureService,
                "textMeasureService");
        int safeViewportWidth = Math.max(0, viewportWidth);
        int safeViewportHeight = Math.max(0, viewportHeight);
        ComputedStyle rootStyle = UiStyleResolver.compute(rootElement);
        DocumentLayoutEdges margin = resolveInsets(rootStyle.getMargin(), safeViewportWidth, false);
        DocumentLayoutEdges border = resolveUniformEdge(rootStyle.getBorderWidth(), safeViewportWidth);
        DocumentLayoutEdges padding = resolveInsets(rootStyle.getPadding(), safeViewportWidth, true);
        int forcedContentWidth = Math.max(0,
                safeViewportWidth - margin.getHorizontal() - border.getHorizontal() - padding.getHorizontal());
        int forcedContentHeight = Math.max(0,
                safeViewportHeight - margin.getVertical() - border.getVertical() - padding.getVertical());
        AbsoluteContainingBlock fixedContainingBlock = new AbsoluteContainingBlock(0, 0, safeViewportWidth,
                safeViewportHeight);
        return layoutElement(rootElement, 0, 0, safeViewportWidth, forcedContentWidth, forcedContentHeight,
                null, fixedContainingBlock, resolvedTextMeasureService);
    }

    private static DocumentLayoutBox layoutElement(ElementNode element, int containingLeft, int flowTop,
            int containingWidth, int forcedContentWidth, int forcedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService) {
        ComputedStyle computedStyle = UiStyleResolver.compute(element);
        if (computedStyle.getDisplay() == UiDisplay.NONE) {
            return new DocumentLayoutBox(element, computedStyle, new ArrayList<DocumentLayoutBox>(),
                    new ArrayList<DocumentLayoutTextRun>(), DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(),
                    DocumentLayoutEdges.zero(), containingLeft, flowTop, 0, 0, 0, 0);
        }

        DocumentLayoutEdges margin = resolveInsets(computedStyle.getMargin(), containingWidth, false);
        DocumentLayoutEdges border = resolveUniformEdge(computedStyle.getBorderWidth(), containingWidth);
        DocumentLayoutEdges padding = resolveInsets(computedStyle.getPadding(), containingWidth, true);

        int availableBorderBoxWidth = Math.max(0, containingWidth - margin.getHorizontal());
        int autoContentWidth = Math.max(0, availableBorderBoxWidth - border.getHorizontal() - padding.getHorizontal());
        int contentWidth = forcedContentWidth >= 0 ? forcedContentWidth
                : Math.max(0, computedStyle.getWidth().resolve(containingWidth, autoContentWidth));
        int borderBoxWidth = contentWidth + border.getHorizontal() + padding.getHorizontal();

        int borderBoxLeft = containingLeft + margin.getLeft();
        int borderBoxTop = flowTop + margin.getTop();
        int contentLeft = borderBoxLeft + border.getLeft() + padding.getLeft();
        int contentTop = borderBoxTop + border.getTop() + padding.getTop();

        int specifiedContentHeight = resolveSpecifiedHeight(computedStyle, forcedContentHeight);
        boolean createsAbsoluteContainingBlock = absoluteContainingBlock == null || isPositioned(computedStyle);
        AbsoluteContainingBlock childrenAbsoluteContainingBlock = createsAbsoluteContainingBlock
                ? new AbsoluteContainingBlock(contentLeft, contentTop, contentWidth,
                        resolveInitialAbsoluteContainingBlockHeight(specifiedContentHeight))
                : absoluteContainingBlock;
        LayoutChildrenResult childrenResult = computedStyle.getDisplay() == UiDisplay.FLEX
                ? layoutFlexChildren(element, computedStyle, contentLeft, contentTop, contentWidth,
                        specifiedContentHeight, childrenAbsoluteContainingBlock, createsAbsoluteContainingBlock,
                        fixedContainingBlock, textMeasureService)
                : layoutBlockChildren(element, contentLeft, contentTop, contentWidth, specifiedContentHeight,
                        childrenAbsoluteContainingBlock, createsAbsoluteContainingBlock, fixedContainingBlock,
                        textMeasureService);

        int autoContentHeight = childrenResult.contentHeight;
        int contentHeight = forcedContentHeight >= 0 ? forcedContentHeight
                : Math.max(0, computedStyle.getHeight().resolve(0, autoContentHeight));
        int borderBoxHeight = contentHeight + border.getVertical() + padding.getVertical();
        int positionOffsetX = resolveRelativeOffsetX(computedStyle, containingWidth);
        int positionOffsetY = resolveRelativeOffsetY(computedStyle, borderBoxHeight);
        return new DocumentLayoutBox(element, computedStyle, childrenResult.children, childrenResult.textRuns,
                margin, border, padding, borderBoxLeft, borderBoxTop, borderBoxWidth, borderBoxHeight,
                positionOffsetX, positionOffsetY);
    }

    private static LayoutChildrenResult layoutBlockChildren(ElementNode element, int contentLeft, int contentTop,
            int contentWidth, int specifiedContentHeight, AbsoluteContainingBlock absoluteContainingBlock,
            boolean createsAbsoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService) {
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        List<DocumentLayoutTextRun> textRuns = new ArrayList<DocumentLayoutTextRun>();
        List<ElementNode> absoluteChildren = new ArrayList<ElementNode>();
        List<ElementNode> fixedChildren = new ArrayList<ElementNode>();
        int childFlowTop = contentTop;
        for (DocumentNode child : element.getChildren()) {
            if (child instanceof TextNode) {
                childFlowTop = appendTextRun((TextNode) child, element, textRuns, contentLeft, childFlowTop,
                        contentWidth, textMeasureService);
                continue;
            }
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = UiStyleResolver.compute(childElement);
            if (childStyle.getDisplay() == UiDisplay.NONE) {
                continue;
            }
            if (isFixedPositioned(childStyle)) {
                fixedChildren.add(childElement);
                continue;
            }
            if (isAbsolutePositioned(childStyle)) {
                absoluteChildren.add(childElement);
                continue;
            }
            DocumentLayoutBox childBox = layoutElement(childElement, contentLeft, childFlowTop, contentWidth,
                    AUTO_SIZE, AUTO_SIZE, absoluteContainingBlock, fixedContainingBlock, textMeasureService);
            childBoxes.add(childBox);
            childFlowTop = childBox.getMarginBoxBottom();
        }
        int contentHeight = Math.max(0, childFlowTop - contentTop);
        appendAbsoluteChildren(childBoxes, absoluteChildren, resolveDirectAbsoluteContainingBlock(
                absoluteContainingBlock, createsAbsoluteContainingBlock, specifiedContentHeight, contentHeight),
                fixedContainingBlock, textMeasureService);
        appendFixedChildren(childBoxes, fixedChildren, fixedContainingBlock, textMeasureService);
        return new LayoutChildrenResult(sortByDocumentChildOrder(element, childBoxes), textRuns, contentHeight);
    }

    private static int appendTextRun(TextNode textNode, ElementNode ownerElement,
            List<DocumentLayoutTextRun> textRuns, int left, int top, int availableWidth,
            TextMeasureService textMeasureService) {
        String text = textNode.getText();
        if (text == null || text.isEmpty()) {
            return top;
        }
        int lineHeight = Math.max(1, toUiTextSize(textMeasureService.getLineHeight()));
        List<String> lines = textMeasureService.listFormattedStringToWidth(text, toRawTextSize(availableWidth));
        if (lines == null || lines.isEmpty()) {
            return top;
        }
        int lineTop = top;
        for (String line : lines) {
            String resolvedLine = line == null ? "" : line;
            int width = Math.max(0, Math.min(availableWidth, toUiTextSize(textMeasureService.getStringWidth(resolvedLine))));
            textRuns.add(new DocumentLayoutTextRun(textNode, ownerElement, resolvedLine, left, lineTop, width,
                    lineHeight));
            lineTop += lineHeight;
        }
        return lineTop;
    }

    private static LayoutChildrenResult layoutFlexChildren(ElementNode element, ComputedStyle parentStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, boolean createsAbsoluteContainingBlock,
            AbsoluteContainingBlock fixedContainingBlock, TextMeasureService textMeasureService) {
        List<ElementNode> absoluteChildren = getVisibleAbsoluteElementChildren(element);
        List<ElementNode> fixedChildren = getVisibleFixedElementChildren(element);
        List<ElementNode> visibleChildren = getVisibleInFlowElementChildren(element);
        LayoutChildrenResult flowResult;
        if (visibleChildren.isEmpty()) {
            flowResult = new LayoutChildrenResult(new ArrayList<DocumentLayoutBox>(),
                    new ArrayList<DocumentLayoutTextRun>(), Math.max(0, specifiedContentHeight));
        } else if (parentStyle.getFlexDirection() == UiFlexDirection.COLUMN) {
            flowResult = layoutColumnFlexChildren(visibleChildren, parentStyle, contentLeft, contentTop, contentWidth,
                    specifiedContentHeight, absoluteContainingBlock, fixedContainingBlock, textMeasureService);
        } else {
            flowResult = layoutRowFlexChildren(visibleChildren, parentStyle, contentLeft, contentTop, contentWidth,
                    specifiedContentHeight, absoluteContainingBlock, fixedContainingBlock, textMeasureService);
        }
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>(flowResult.children);
        appendAbsoluteChildren(childBoxes, absoluteChildren, resolveDirectAbsoluteContainingBlock(
                absoluteContainingBlock, createsAbsoluteContainingBlock, specifiedContentHeight,
                flowResult.contentHeight), fixedContainingBlock, textMeasureService);
        appendFixedChildren(childBoxes, fixedChildren, fixedContainingBlock, textMeasureService);
        return new LayoutChildrenResult(sortByDocumentChildOrder(element, childBoxes), flowResult.textRuns,
                flowResult.contentHeight);
    }

    private static LayoutChildrenResult layoutRowFlexChildren(List<ElementNode> children, ComputedStyle parentStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService) {
        List<FlexItem> items = new ArrayList<FlexItem>();
        for (ElementNode child : children) {
            FlexItem item = createFlexItem(child, contentWidth);
            item.contentMainSize = resolveContentMainSize(item, contentWidth, true);
            items.add(item);
        }

        int gap = Math.max(0, parentStyle.getColumnGap().resolve(contentWidth, 0));
        distributeMainSpace(items, contentWidth, gap, true);

        int lineCrossSize = 0;
        for (FlexItem item : items) {
            item.box = layoutElement(item.element, 0, 0, contentWidth, item.contentMainSize, AUTO_SIZE,
                    absoluteContainingBlock, fixedContainingBlock, textMeasureService);
            lineCrossSize = Math.max(lineCrossSize, item.getOuterCrossSize(true));
        }

        int contentHeight = specifiedContentHeight >= 0 ? specifiedContentHeight : lineCrossSize;
        for (FlexItem item : items) {
            if (parentStyle.getAlignItems() == UiAlignItems.STRETCH && isAuto(item.style.getHeight())) {
                item.forcedCrossSize = Math.max(0,
                        contentHeight - item.margin.getVertical() - item.border.getVertical() - item.padding.getVertical());
                item.box = layoutElement(item.element, 0, 0, contentWidth, item.contentMainSize,
                        item.forcedCrossSize, absoluteContainingBlock, fixedContainingBlock, textMeasureService);
            }
        }

        int occupiedMain = getOccupiedMainSize(items, gap, true);
        int remaining = Math.max(0, contentWidth - occupiedMain);
        int dynamicGap = resolveDynamicGap(parentStyle.getJustifyContent(), gap, remaining, items.size());
        int cursor = resolveLeadingOffset(parentStyle.getJustifyContent(), remaining);
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        for (FlexItem item : items) {
            int outerCrossSize = item.getOuterCrossSize(true);
            int crossOffset = resolveCrossOffset(parentStyle.getAlignItems(), contentHeight, outerCrossSize);
            int borderLeft = contentLeft + cursor + item.margin.getLeft();
            int borderTop = contentTop + crossOffset + item.margin.getTop();
            DocumentLayoutBox childBox = layoutElement(item.element, borderLeft - item.margin.getLeft(),
                    borderTop - item.margin.getTop(), contentWidth, item.contentMainSize, item.forcedCrossSize,
                    absoluteContainingBlock, fixedContainingBlock, textMeasureService);
            childBoxes.add(childBox);
            cursor += item.margin.getLeft() + childBox.getWidth() + item.margin.getRight() + dynamicGap;
        }
        return new LayoutChildrenResult(childBoxes, new ArrayList<DocumentLayoutTextRun>(), contentHeight);
    }

    private static LayoutChildrenResult layoutColumnFlexChildren(List<ElementNode> children, ComputedStyle parentStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService) {
        List<FlexItem> items = new ArrayList<FlexItem>();
        for (ElementNode child : children) {
            FlexItem item = createFlexItem(child, contentWidth);
            item.forcedCrossSize = resolveColumnCrossContentWidth(item, parentStyle.getAlignItems(), contentWidth);
            item.box = layoutElement(item.element, 0, 0, contentWidth, item.forcedCrossSize, AUTO_SIZE,
                    absoluteContainingBlock, fixedContainingBlock, textMeasureService);
            item.contentMainSize = resolveContentMainSize(item, contentWidth, false);
            if (isAuto(item.style.getHeight())) {
                item.contentMainSize = item.box.getContentHeight();
            }
            items.add(item);
        }

        int gap = Math.max(0, parentStyle.getRowGap().resolve(contentWidth, 0));
        if (specifiedContentHeight >= 0) {
            distributeMainSpace(items, specifiedContentHeight, gap, false);
        }

        int occupiedMain = getOccupiedMainSize(items, gap, false);
        int contentHeight = specifiedContentHeight >= 0 ? specifiedContentHeight : occupiedMain;
        int remaining = Math.max(0, contentHeight - occupiedMain);
        int dynamicGap = resolveDynamicGap(parentStyle.getJustifyContent(), gap, remaining, items.size());
        int cursor = resolveLeadingOffset(parentStyle.getJustifyContent(), remaining);
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        for (FlexItem item : items) {
            int crossOffset = resolveCrossOffset(parentStyle.getAlignItems(), contentWidth, item.getOuterCrossSize(false));
            int borderLeft = contentLeft + crossOffset + item.margin.getLeft();
            int borderTop = contentTop + cursor + item.margin.getTop();
            DocumentLayoutBox childBox = layoutElement(item.element, borderLeft - item.margin.getLeft(),
                    borderTop - item.margin.getTop(), contentWidth, item.forcedCrossSize, item.contentMainSize,
                    absoluteContainingBlock, fixedContainingBlock, textMeasureService);
            childBoxes.add(childBox);
            cursor += item.margin.getTop() + childBox.getHeight() + item.margin.getBottom() + dynamicGap;
        }
        return new LayoutChildrenResult(childBoxes, new ArrayList<DocumentLayoutTextRun>(), contentHeight);
    }

    private static void distributeMainSpace(List<FlexItem> items, int availableMainSize, int gap, boolean row) {
        int occupiedMain = getOccupiedMainSize(items, gap, row);
        int freeSpace = availableMainSize - occupiedMain;
        if (freeSpace > 0) {
            distributeGrowth(items, freeSpace);
        } else if (freeSpace < 0) {
            distributeShrink(items, -freeSpace);
        }
    }

    private static void distributeGrowth(List<FlexItem> items, int freeSpace) {
        float totalGrow = 0.0F;
        for (FlexItem item : items) {
            totalGrow += item.style.getFlexGrow();
        }
        if (totalGrow <= 0.0F) {
            return;
        }

        int applied = 0;
        int growableIndex = 0;
        int growableCount = 0;
        for (FlexItem item : items) {
            if (item.style.getFlexGrow() > 0.0F) {
                growableCount++;
            }
        }
        for (FlexItem item : items) {
            if (item.style.getFlexGrow() <= 0.0F) {
                continue;
            }
            growableIndex++;
            int addition = growableIndex == growableCount ? freeSpace - applied
                    : Math.round(freeSpace * item.style.getFlexGrow() / totalGrow);
            addition = Math.max(0, addition);
            item.contentMainSize += addition;
            applied += addition;
        }
    }

    private static void distributeShrink(List<FlexItem> items, int overflow) {
        float totalShrinkWeight = 0.0F;
        for (FlexItem item : items) {
            totalShrinkWeight += item.style.getFlexShrink() * item.contentMainSize;
        }
        if (totalShrinkWeight <= 0.0F) {
            return;
        }

        int removed = 0;
        int shrinkableIndex = 0;
        int shrinkableCount = 0;
        for (FlexItem item : items) {
            if (item.style.getFlexShrink() > 0.0F && item.contentMainSize > 0) {
                shrinkableCount++;
            }
        }
        for (FlexItem item : items) {
            if (item.style.getFlexShrink() <= 0.0F || item.contentMainSize <= 0) {
                continue;
            }
            shrinkableIndex++;
            int cut = shrinkableIndex == shrinkableCount ? overflow - removed
                    : Math.round(overflow * item.style.getFlexShrink() * item.contentMainSize / totalShrinkWeight);
            cut = Math.max(0, Math.min(cut, item.contentMainSize));
            item.contentMainSize -= cut;
            removed += cut;
        }
    }

    private static int getOccupiedMainSize(List<FlexItem> items, int gap, boolean row) {
        int occupied = Math.max(0, items.size() - 1) * gap;
        for (FlexItem item : items) {
            occupied += item.getOuterMainSize(row);
        }
        return occupied;
    }

    private static int resolveContentMainSize(FlexItem item, int containingWidth, boolean row) {
        UiStyleLength length = row ? item.style.getWidth() : item.style.getHeight();
        if (isAuto(length)) {
            return 0;
        }
        return Math.max(0, length.resolve(containingWidth, 0));
    }

    private static int resolveColumnCrossContentWidth(FlexItem item, UiAlignItems alignItems, int contentWidth) {
        if (!isAuto(item.style.getWidth())) {
            return Math.max(0, item.style.getWidth().resolve(contentWidth, 0));
        }
        if (alignItems == UiAlignItems.STRETCH) {
            return Math.max(0, contentWidth - item.margin.getHorizontal() - item.border.getHorizontal()
                    - item.padding.getHorizontal());
        }
        return 0;
    }

    private static int resolveSpecifiedHeight(ComputedStyle computedStyle, int forcedContentHeight) {
        if (forcedContentHeight >= 0) {
            return forcedContentHeight;
        }
        if (isAuto(computedStyle.getHeight())) {
            return AUTO_SIZE;
        }
        return Math.max(0, computedStyle.getHeight().resolve(0, 0));
    }

    private static int resolveLeadingOffset(UiJustifyContent justifyContent, int remaining) {
        if (remaining <= 0) {
            return 0;
        }
        if (justifyContent == UiJustifyContent.CENTER) {
            return remaining / 2;
        }
        if (justifyContent == UiJustifyContent.END) {
            return remaining;
        }
        return 0;
    }

    private static int resolveDynamicGap(UiJustifyContent justifyContent, int baseGap, int remaining, int itemCount) {
        if (justifyContent == UiJustifyContent.SPACE_BETWEEN && itemCount > 1 && remaining > 0) {
            return baseGap + remaining / (itemCount - 1);
        }
        return baseGap;
    }

    private static int resolveCrossOffset(UiAlignItems alignItems, int availableCrossSize, int itemOuterCrossSize) {
        int remaining = Math.max(0, availableCrossSize - itemOuterCrossSize);
        if (alignItems == UiAlignItems.CENTER) {
            return remaining / 2;
        }
        if (alignItems == UiAlignItems.END) {
            return remaining;
        }
        return 0;
    }

    private static List<ElementNode> getVisibleInFlowElementChildren(ElementNode element) {
        List<ElementNode> children = new ArrayList<ElementNode>();
        for (DocumentNode child : element.getChildren()) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = UiStyleResolver.compute(childElement);
            if (childStyle.getDisplay() == UiDisplay.NONE || isOutOfFlowPositioned(childStyle)) {
                continue;
            }
            children.add(childElement);
        }
        return children;
    }

    private static List<ElementNode> getVisibleAbsoluteElementChildren(ElementNode element) {
        List<ElementNode> children = new ArrayList<ElementNode>();
        for (DocumentNode child : element.getChildren()) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = UiStyleResolver.compute(childElement);
            if (childStyle.getDisplay() == UiDisplay.NONE || !isAbsolutePositioned(childStyle)) {
                continue;
            }
            children.add(childElement);
        }
        return children;
    }

    private static List<ElementNode> getVisibleFixedElementChildren(ElementNode element) {
        List<ElementNode> children = new ArrayList<ElementNode>();
        for (DocumentNode child : element.getChildren()) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = UiStyleResolver.compute(childElement);
            if (childStyle.getDisplay() == UiDisplay.NONE || !isFixedPositioned(childStyle)) {
                continue;
            }
            children.add(childElement);
        }
        return children;
    }

    private static void appendAbsoluteChildren(List<DocumentLayoutBox> childBoxes, List<ElementNode> absoluteChildren,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService) {
        for (ElementNode child : absoluteChildren) {
            childBoxes.add(layoutPositionedElement(child, absoluteContainingBlock, fixedContainingBlock,
                    textMeasureService));
        }
    }

    private static void appendFixedChildren(List<DocumentLayoutBox> childBoxes, List<ElementNode> fixedChildren,
            AbsoluteContainingBlock fixedContainingBlock, TextMeasureService textMeasureService) {
        for (ElementNode child : fixedChildren) {
            childBoxes.add(layoutPositionedElement(child, fixedContainingBlock, fixedContainingBlock,
                    textMeasureService));
        }
    }

    private static DocumentLayoutBox layoutPositionedElement(ElementNode element,
            AbsoluteContainingBlock containingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService) {
        ComputedStyle style = UiStyleResolver.compute(element);
        DocumentLayoutBox measuredBox = layoutElement(element, 0, 0, containingBlock.width, AUTO_SIZE,
                AUTO_SIZE, containingBlock, fixedContainingBlock, textMeasureService);
        int marginBoxWidth = measuredBox.getWidth() + measuredBox.getMargin().getHorizontal();
        int marginBoxHeight = measuredBox.getHeight() + measuredBox.getMargin().getVertical();
        int marginBoxLeft = resolveAbsoluteMarginBoxLeft(style, containingBlock, marginBoxWidth);
        int marginBoxTop = resolveAbsoluteMarginBoxTop(style, containingBlock, marginBoxHeight);
        return layoutElement(element, marginBoxLeft, marginBoxTop, containingBlock.width, AUTO_SIZE,
                AUTO_SIZE, containingBlock, fixedContainingBlock, textMeasureService);
    }

    private static int resolveAbsoluteMarginBoxLeft(ComputedStyle style,
            AbsoluteContainingBlock absoluteContainingBlock, int marginBoxWidth) {
        if (!isAuto(style.getLeft())) {
            return absoluteContainingBlock.left + style.getLeft().resolve(absoluteContainingBlock.width, 0);
        }
        if (!isAuto(style.getRight())) {
            return absoluteContainingBlock.left + absoluteContainingBlock.width
                    - style.getRight().resolve(absoluteContainingBlock.width, 0) - marginBoxWidth;
        }
        return absoluteContainingBlock.left;
    }

    private static int resolveAbsoluteMarginBoxTop(ComputedStyle style,
            AbsoluteContainingBlock absoluteContainingBlock, int marginBoxHeight) {
        int safeContainingHeight = Math.max(0, absoluteContainingBlock.height);
        if (!isAuto(style.getTop())) {
            return absoluteContainingBlock.top + style.getTop().resolve(safeContainingHeight, 0);
        }
        if (!isAuto(style.getBottom())) {
            return absoluteContainingBlock.top + safeContainingHeight
                    - style.getBottom().resolve(safeContainingHeight, 0) - marginBoxHeight;
        }
        return absoluteContainingBlock.top;
    }

    private static AbsoluteContainingBlock resolveDirectAbsoluteContainingBlock(
            AbsoluteContainingBlock absoluteContainingBlock, boolean createsAbsoluteContainingBlock,
            int specifiedContentHeight, int contentHeight) {
        if (!createsAbsoluteContainingBlock) {
            return absoluteContainingBlock;
        }
        return absoluteContainingBlock.withHeight(specifiedContentHeight >= 0 ? specifiedContentHeight : contentHeight);
    }

    private static List<DocumentLayoutBox> sortByDocumentChildOrder(final ElementNode parentElement,
            List<DocumentLayoutBox> childBoxes) {
        List<DocumentLayoutBox> sortedBoxes = new ArrayList<DocumentLayoutBox>(childBoxes);
        Collections.sort(sortedBoxes, new Comparator<DocumentLayoutBox>() {
            @Override
            public int compare(DocumentLayoutBox first, DocumentLayoutBox second) {
                return Integer.compare(getChildOrder(parentElement, first.getElement()),
                        getChildOrder(parentElement, second.getElement()));
            }
        });
        return sortedBoxes;
    }

    private static int getChildOrder(ElementNode parentElement, ElementNode targetElement) {
        List<DocumentNode> children = parentElement.getChildren();
        for (int index = 0; index < children.size(); index++) {
            if (children.get(index) == targetElement) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static boolean isAbsolutePositioned(ComputedStyle style) {
        return style.getPosition() == UiPosition.ABSOLUTE;
    }

    private static boolean isFixedPositioned(ComputedStyle style) {
        return style.getPosition() == UiPosition.FIXED;
    }

    private static boolean isOutOfFlowPositioned(ComputedStyle style) {
        return isAbsolutePositioned(style) || isFixedPositioned(style);
    }

    private static boolean isPositioned(ComputedStyle style) {
        return style.getPosition() != UiPosition.STATIC;
    }

    private static int resolveInitialAbsoluteContainingBlockHeight(int specifiedContentHeight) {
        return specifiedContentHeight >= 0 ? specifiedContentHeight : 0;
    }

    private static FlexItem createFlexItem(ElementNode element, int containingWidth) {
        ComputedStyle style = UiStyleResolver.compute(element);
        return new FlexItem(element, style, resolveInsets(style.getMargin(), containingWidth, false),
                resolveUniformEdge(style.getBorderWidth(), containingWidth),
                resolveInsets(style.getPadding(), containingWidth, true));
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

    private static boolean isAuto(UiStyleLength length) {
        return length.getType() == UiStyleLength.Type.AUTO;
    }

    private static int resolveRelativeOffsetX(ComputedStyle computedStyle, int containingWidth) {
        if (computedStyle.getPosition() != UiPosition.RELATIVE) {
            return 0;
        }
        if (!isAuto(computedStyle.getLeft())) {
            return computedStyle.getLeft().resolve(containingWidth, 0);
        }
        if (!isAuto(computedStyle.getRight())) {
            return -computedStyle.getRight().resolve(containingWidth, 0);
        }
        return 0;
    }

    private static int resolveRelativeOffsetY(ComputedStyle computedStyle, int borderBoxHeight) {
        if (computedStyle.getPosition() != UiPosition.RELATIVE) {
            return 0;
        }
        if (!isAuto(computedStyle.getTop())) {
            return computedStyle.getTop().resolve(borderBoxHeight, 0);
        }
        if (!isAuto(computedStyle.getBottom())) {
            return -computedStyle.getBottom().resolve(borderBoxHeight, 0);
        }
        return 0;
    }

    private static int toUiTextSize(int rawSize) {
        return Math.round(Math.max(0, rawSize) * UI_TEXT_SCALE);
    }

    private static int toRawTextSize(int uiSize) {
        return Math.max(1, Math.round(Math.max(1, uiSize) / UI_TEXT_SCALE));
    }

    private static final class LayoutChildrenResult {

        private final List<DocumentLayoutBox> children;
        private final List<DocumentLayoutTextRun> textRuns;
        private final int contentHeight;

        private LayoutChildrenResult(List<DocumentLayoutBox> children, List<DocumentLayoutTextRun> textRuns,
                int contentHeight) {
            this.children = children;
            this.textRuns = textRuns;
            this.contentHeight = Math.max(0, contentHeight);
        }
    }

    /**
     * absolute/fixed 定位使用的包含块。
     */
    private static final class AbsoluteContainingBlock {

        private final int left;
        private final int top;
        private final int width;
        private final int height;

        private AbsoluteContainingBlock(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        private AbsoluteContainingBlock withHeight(int height) {
            return new AbsoluteContainingBlock(left, top, width, height);
        }
    }

    private static final class FlexItem {

        private final ElementNode element;
        private final ComputedStyle style;
        private final DocumentLayoutEdges margin;
        private final DocumentLayoutEdges border;
        private final DocumentLayoutEdges padding;
        private int contentMainSize;
        private int forcedCrossSize = AUTO_SIZE;
        private DocumentLayoutBox box;

        private FlexItem(ElementNode element, ComputedStyle style, DocumentLayoutEdges margin,
                DocumentLayoutEdges border, DocumentLayoutEdges padding) {
            this.element = element;
            this.style = style;
            this.margin = margin;
            this.border = border;
            this.padding = padding;
        }

        private int getOuterMainSize(boolean row) {
            if (row) {
                return margin.getHorizontal() + border.getHorizontal() + padding.getHorizontal() + contentMainSize;
            }
            return margin.getVertical() + border.getVertical() + padding.getVertical() + contentMainSize;
        }

        private int getOuterCrossSize(boolean row) {
            if (box == null) {
                return 0;
            }
            if (row) {
                return margin.getVertical() + box.getHeight();
            }
            return margin.getHorizontal() + box.getWidth();
        }
    }

    /**
     * 供无外部测量服务的纯布局调用使用的确定性文本测量实现。
     */
    private static final class FixedTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 4;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<String>();
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }
}
