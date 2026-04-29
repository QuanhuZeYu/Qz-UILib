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
 * ancestor 时回退根 content box；fixed 元素相对当前 HTML-like 视口定位。当前已支持 positioned 元素
 * 在横向或纵向两侧 inset 同时存在且尺寸为 auto 时进行 stretch 求解，并支持包含 inline 元素的
 * text/span 初版混排和 inline fragment 盒边；更完整 inline box、多行 flex wrap 和滚动布局会在后续阶段继续扩展。</p>
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
                    new ArrayList<DocumentLayoutTextRun>(), new ArrayList<DocumentLayoutInlineFragment>(),
                    DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), containingLeft,
                    flowTop, 0, 0, 0, 0);
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
                childrenResult.inlineFragments, margin, border, padding, borderBoxLeft, borderBoxTop, borderBoxWidth,
                borderBoxHeight, positionOffsetX, positionOffsetY);
    }

    private static LayoutChildrenResult layoutBlockChildren(ElementNode element, int contentLeft, int contentTop,
            int contentWidth, int specifiedContentHeight, AbsoluteContainingBlock absoluteContainingBlock,
            boolean createsAbsoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService) {
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        List<DocumentLayoutTextRun> textRuns = new ArrayList<DocumentLayoutTextRun>();
        List<DocumentLayoutInlineFragment> inlineFragments = new ArrayList<DocumentLayoutInlineFragment>();
        List<ElementNode> absoluteChildren = new ArrayList<ElementNode>();
        List<ElementNode> fixedChildren = new ArrayList<ElementNode>();
        boolean usesInlineFormatting = hasVisibleInlineElementChild(element);
        InlineLayoutContext inlineLayoutContext = usesInlineFormatting
                ? new InlineLayoutContext(contentLeft, contentTop, contentWidth,
                        resolveTextLineHeight(textMeasureService), textRuns, inlineFragments)
                : null;
        int childFlowTop = contentTop;
        for (DocumentNode child : element.getChildren()) {
            if (child instanceof TextNode) {
                if (usesInlineFormatting) {
                    appendInlineTextRun((TextNode) child, element, new ArrayList<InlineFragmentOwner>(),
                            inlineLayoutContext, textMeasureService);
                    childFlowTop = inlineLayoutContext.getFlowBottom();
                } else {
                    childFlowTop = appendTextRun((TextNode) child, element, textRuns, contentLeft, childFlowTop,
                            contentWidth, textMeasureService);
                }
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
            if (usesInlineFormatting && childStyle.getDisplay() == UiDisplay.INLINE) {
                appendInlineElementTextRuns(childElement, inlineLayoutContext, textMeasureService);
                childFlowTop = inlineLayoutContext.getFlowBottom();
                continue;
            }
            if (usesInlineFormatting) {
                childFlowTop = inlineLayoutContext.finishLineAndGetBottom();
                inlineLayoutContext.reset(childFlowTop);
            }
            DocumentLayoutBox childBox = layoutElement(childElement, contentLeft, childFlowTop, contentWidth,
                    AUTO_SIZE, AUTO_SIZE, absoluteContainingBlock, fixedContainingBlock, textMeasureService);
            childBoxes.add(childBox);
            childFlowTop = childBox.getMarginBoxBottom();
            if (usesInlineFormatting) {
                inlineLayoutContext.reset(childFlowTop);
            }
        }
        if (usesInlineFormatting) {
            childFlowTop = Math.max(childFlowTop, inlineLayoutContext.finishLineAndGetBottom());
        }
        int contentHeight = Math.max(0, childFlowTop - contentTop);
        appendAbsoluteChildren(childBoxes, absoluteChildren, resolveDirectAbsoluteContainingBlock(
                absoluteContainingBlock, createsAbsoluteContainingBlock, specifiedContentHeight, contentHeight),
                fixedContainingBlock, textMeasureService);
        appendFixedChildren(childBoxes, fixedChildren, fixedContainingBlock, textMeasureService);
        return new LayoutChildrenResult(sortByDocumentChildOrder(element, childBoxes), textRuns,
                mergeInlineFragments(inlineFragments), contentHeight);
    }

    private static int appendTextRun(TextNode textNode, ElementNode ownerElement,
            List<DocumentLayoutTextRun> textRuns, int left, int top, int availableWidth,
            TextMeasureService textMeasureService) {
        String text = textNode.getText();
        if (text == null || text.isEmpty()) {
            return top;
        }
        int lineHeight = resolveTextLineHeight(textMeasureService);
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

    private static void appendInlineElementTextRuns(ElementNode inlineElement, InlineLayoutContext inlineLayoutContext,
            TextMeasureService textMeasureService) {
        appendInlineElementTextRuns(inlineElement, inlineLayoutContext, textMeasureService,
                new ArrayList<InlineFragmentOwner>());
    }

    private static void appendInlineElementTextRuns(ElementNode inlineElement, InlineLayoutContext inlineLayoutContext,
            TextMeasureService textMeasureService, List<InlineFragmentOwner> ancestorInlineElements) {
        InlineElementEdges edges = resolveInlineElementEdges(inlineElement, inlineLayoutContext.getLineWidth());
        List<InlineFragmentOwner> fragmentOwners = new ArrayList<InlineFragmentOwner>(ancestorInlineElements);
        fragmentOwners.add(new InlineFragmentOwner(inlineElement, edges.getVerticalTop(),
                edges.getVerticalBottom()));
        appendInlineSpacing(ancestorInlineElements, inlineLayoutContext, edges.margin.getLeft());
        appendInlineSpacing(fragmentOwners, inlineLayoutContext,
                edges.border.getLeft() + edges.padding.getLeft());
        for (DocumentNode child : inlineElement.getChildren()) {
            if (child instanceof TextNode) {
                appendInlineTextRun((TextNode) child, inlineElement, fragmentOwners, inlineLayoutContext,
                        textMeasureService);
                continue;
            }
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = UiStyleResolver.compute(childElement);
            if (childStyle.getDisplay() == UiDisplay.NONE || isOutOfFlowPositioned(childStyle)) {
                continue;
            }
            appendInlineElementTextRuns(childElement, inlineLayoutContext, textMeasureService, fragmentOwners);
        }
        appendInlineSpacing(fragmentOwners, inlineLayoutContext,
                edges.padding.getRight() + edges.border.getRight());
        appendInlineSpacing(ancestorInlineElements, inlineLayoutContext, edges.margin.getRight());
    }

    private static void appendInlineTextRun(TextNode textNode, ElementNode ownerElement,
            List<InlineFragmentOwner> fragmentOwners, InlineLayoutContext inlineLayoutContext,
            TextMeasureService textMeasureService) {
        String remainingText = textNode.getText();
        if (remainingText == null || remainingText.isEmpty() || inlineLayoutContext.getLineWidth() <= 0) {
            return;
        }
        while (!remainingText.isEmpty()) {
            if (inlineLayoutContext.getRemainingWidth() <= 0 && inlineLayoutContext.hasLineContent()) {
                inlineLayoutContext.nextLine();
            }
            int remainingWidth = inlineLayoutContext.getRemainingWidth();
            if (remainingWidth <= 0) {
                return;
            }
            String segment = textMeasureService.trimStringToWidth(remainingText, toRawTextSize(remainingWidth));
            if (segment == null) {
                segment = "";
            }
            if (segment.isEmpty()) {
                segment = firstCodePoint(remainingText);
            }
            int width = Math.max(0, toUiTextSize(textMeasureService.getStringWidth(segment)));
            if (width > remainingWidth && inlineLayoutContext.hasLineContent()) {
                inlineLayoutContext.nextLine();
                continue;
            }
            width = Math.min(width, inlineLayoutContext.getRemainingWidth());
            inlineLayoutContext.appendTextRun(textNode, ownerElement, segment, inlineLayoutContext.getCursorLeft(),
                    width);
            appendInlineFragments(fragmentOwners, inlineLayoutContext.getCursorLeft(), width, inlineLayoutContext);
            inlineLayoutContext.advance(width);
            remainingText = remainingText.substring(segment.length());
            if (!remainingText.isEmpty() && inlineLayoutContext.getRemainingWidth() <= 0) {
                inlineLayoutContext.nextLine();
            }
        }
    }

    private static void appendInlineSpacing(List<InlineFragmentOwner> fragmentOwners,
            InlineLayoutContext inlineLayoutContext, int width) {
        int remainingSpacing = Math.max(0, width);
        while (remainingSpacing > 0 && inlineLayoutContext.getLineWidth() > 0) {
            if (inlineLayoutContext.getRemainingWidth() <= 0 && inlineLayoutContext.hasLineContent()) {
                inlineLayoutContext.nextLine();
            }
            int remainingWidth = inlineLayoutContext.getRemainingWidth();
            if (remainingWidth <= 0) {
                return;
            }
            int chunkWidth = Math.min(remainingSpacing, remainingWidth);
            appendInlineFragments(fragmentOwners, inlineLayoutContext.getCursorLeft(), chunkWidth, inlineLayoutContext);
            inlineLayoutContext.advance(chunkWidth);
            remainingSpacing -= chunkWidth;
        }
    }

    private static void appendInlineFragments(List<InlineFragmentOwner> fragmentOwners, int left, int width,
            InlineLayoutContext inlineLayoutContext) {
        if (width <= 0) {
            return;
        }
        for (InlineFragmentOwner fragmentOwner : fragmentOwners) {
            inlineLayoutContext.appendInlineFragment(fragmentOwner, left, width);
        }
    }

    private static List<DocumentLayoutInlineFragment> mergeInlineFragments(
            List<DocumentLayoutInlineFragment> inlineFragments) {
        List<DocumentLayoutInlineFragment> mergedFragments = new ArrayList<DocumentLayoutInlineFragment>();
        for (DocumentLayoutInlineFragment inlineFragment : inlineFragments) {
            int mergeIndex = findMergeableInlineFragment(mergedFragments, inlineFragment);
            if (mergeIndex < 0) {
                mergedFragments.add(inlineFragment);
                continue;
            }
            DocumentLayoutInlineFragment existingFragment = mergedFragments.get(mergeIndex);
            int left = Math.min(existingFragment.getLeft(), inlineFragment.getLeft());
            int top = Math.min(existingFragment.getTop(), inlineFragment.getTop());
            int right = Math.max(existingFragment.getRight(), inlineFragment.getRight());
            int bottom = Math.max(existingFragment.getBottom(), inlineFragment.getBottom());
            mergedFragments.set(mergeIndex, new DocumentLayoutInlineFragment(existingFragment.getOwnerElement(), left,
                    top, right - left, bottom - top));
        }
        return mergedFragments;
    }

    private static int findMergeableInlineFragment(List<DocumentLayoutInlineFragment> mergedFragments,
            DocumentLayoutInlineFragment inlineFragment) {
        for (int index = mergedFragments.size() - 1; index >= 0; index--) {
            DocumentLayoutInlineFragment existingFragment = mergedFragments.get(index);
            if (existingFragment.getOwnerElement() != inlineFragment.getOwnerElement()
                    || existingFragment.getTop() != inlineFragment.getTop()
                    || existingFragment.getBottom() != inlineFragment.getBottom()) {
                continue;
            }
            if (inlineFragment.getLeft() <= existingFragment.getRight()
                    && inlineFragment.getRight() >= existingFragment.getLeft()) {
                return index;
            }
        }
        return -1;
    }

    private static String firstCodePoint(String text) {
        int endIndex = Math.min(text.length(), Character.charCount(text.codePointAt(0)));
        return text.substring(0, endIndex);
    }

    private static InlineElementEdges resolveInlineElementEdges(ElementNode inlineElement, int lineWidth) {
        ComputedStyle style = UiStyleResolver.compute(inlineElement);
        return new InlineElementEdges(resolveInsets(style.getMargin(), lineWidth, false),
                resolveUniformEdge(style.getBorderWidth(), lineWidth), resolveInsets(style.getPadding(), lineWidth,
                        true));
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
                    new ArrayList<DocumentLayoutTextRun>(), new ArrayList<DocumentLayoutInlineFragment>(),
                    Math.max(0, specifiedContentHeight));
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
                flowResult.inlineFragments, flowResult.contentHeight);
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
        return new LayoutChildrenResult(childBoxes, new ArrayList<DocumentLayoutTextRun>(),
                new ArrayList<DocumentLayoutInlineFragment>(), contentHeight);
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
        return new LayoutChildrenResult(childBoxes, new ArrayList<DocumentLayoutTextRun>(),
                new ArrayList<DocumentLayoutInlineFragment>(), contentHeight);
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

    private static boolean hasVisibleInlineElementChild(ElementNode element) {
        for (DocumentNode child : element.getChildren()) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = UiStyleResolver.compute(childElement);
            if (childStyle.getDisplay() == UiDisplay.INLINE && !isOutOfFlowPositioned(childStyle)) {
                return true;
            }
        }
        return false;
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
        int forcedContentWidth = resolveStretchContentWidth(style, containingBlock);
        int forcedContentHeight = resolveStretchContentHeight(style, containingBlock);
        DocumentLayoutBox measuredBox = layoutElement(element, 0, 0, containingBlock.width, forcedContentWidth,
                forcedContentHeight, containingBlock, fixedContainingBlock, textMeasureService);
        int marginBoxWidth = measuredBox.getWidth() + measuredBox.getMargin().getHorizontal();
        int marginBoxHeight = measuredBox.getHeight() + measuredBox.getMargin().getVertical();
        int marginBoxLeft = resolveAbsoluteMarginBoxLeft(style, containingBlock, marginBoxWidth);
        int marginBoxTop = resolveAbsoluteMarginBoxTop(style, containingBlock, marginBoxHeight);
        return layoutElement(element, marginBoxLeft, marginBoxTop, containingBlock.width, forcedContentWidth,
                forcedContentHeight, containingBlock, fixedContainingBlock, textMeasureService);
    }

    private static int resolveStretchContentWidth(ComputedStyle style, AbsoluteContainingBlock containingBlock) {
        if (!isAuto(style.getWidth()) || isAuto(style.getLeft()) || isAuto(style.getRight())) {
            return AUTO_SIZE;
        }
        DocumentLayoutEdges margin = resolveInsets(style.getMargin(), containingBlock.width, false);
        DocumentLayoutEdges border = resolveUniformEdge(style.getBorderWidth(), containingBlock.width);
        DocumentLayoutEdges padding = resolveInsets(style.getPadding(), containingBlock.width, true);
        int leftInset = style.getLeft().resolve(containingBlock.width, 0);
        int rightInset = style.getRight().resolve(containingBlock.width, 0);
        return Math.max(0, containingBlock.width - leftInset - rightInset - margin.getHorizontal()
                - border.getHorizontal() - padding.getHorizontal());
    }

    private static int resolveStretchContentHeight(ComputedStyle style, AbsoluteContainingBlock containingBlock) {
        if (!isAuto(style.getHeight()) || isAuto(style.getTop()) || isAuto(style.getBottom())) {
            return AUTO_SIZE;
        }
        int containingHeight = Math.max(0, containingBlock.height);
        DocumentLayoutEdges margin = resolveInsets(style.getMargin(), containingBlock.width, false);
        DocumentLayoutEdges border = resolveUniformEdge(style.getBorderWidth(), containingBlock.width);
        DocumentLayoutEdges padding = resolveInsets(style.getPadding(), containingBlock.width, true);
        int topInset = style.getTop().resolve(containingHeight, 0);
        int bottomInset = style.getBottom().resolve(containingHeight, 0);
        return Math.max(0, containingHeight - topInset - bottomInset - margin.getVertical()
                - border.getVertical() - padding.getVertical());
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

    private static int resolveTextLineHeight(TextMeasureService textMeasureService) {
        return Math.max(1, toUiTextSize(textMeasureService.getLineHeight()));
    }

    /**
     * block 容器内的首期 inline 文本排版游标。
     */
    private static final class InlineLayoutContext {

        private final int lineLeft;
        private final int lineWidth;
        private final int baseLineHeight;
        private final List<DocumentLayoutTextRun> textRuns;
        private final List<DocumentLayoutInlineFragment> inlineFragments;
        private final List<PendingInlineTextRun> pendingTextRuns = new ArrayList<PendingInlineTextRun>();
        private final List<PendingInlineFragment> pendingInlineFragments = new ArrayList<PendingInlineFragment>();
        private int lineTop;
        private int cursorLeft;
        private int lineTopInset;
        private int lineBottomInset;
        private boolean lineContentPresent;

        private InlineLayoutContext(int lineLeft, int lineTop, int lineWidth, int lineHeight,
                List<DocumentLayoutTextRun> textRuns, List<DocumentLayoutInlineFragment> inlineFragments) {
            this.lineLeft = lineLeft;
            this.lineTop = lineTop;
            this.lineWidth = Math.max(0, lineWidth);
            this.baseLineHeight = Math.max(1, lineHeight);
            this.textRuns = textRuns;
            this.inlineFragments = inlineFragments;
            this.cursorLeft = lineLeft;
        }

        private int getLineWidth() {
            return lineWidth;
        }

        private int getCursorLeft() {
            return cursorLeft;
        }

        private int getRemainingWidth() {
            return Math.max(0, lineLeft + lineWidth - cursorLeft);
        }

        private boolean hasLineContent() {
            return lineContentPresent;
        }

        private void appendTextRun(TextNode textNode, ElementNode ownerElement, String text, int left, int width) {
            if (width <= 0) {
                return;
            }
            pendingTextRuns.add(new PendingInlineTextRun(textNode, ownerElement, text, left, width));
        }

        private void appendInlineFragment(InlineFragmentOwner owner, int left, int width) {
            if (width <= 0) {
                return;
            }
            pendingInlineFragments.add(new PendingInlineFragment(owner, left, width));
            lineTopInset = Math.max(lineTopInset, owner.topEdge);
            lineBottomInset = Math.max(lineBottomInset, owner.bottomEdge);
        }

        private void advance(int width) {
            cursorLeft += Math.max(0, width);
            lineContentPresent = true;
        }

        private void nextLine() {
            int nextLineTop = getFlowBottom();
            flushCurrentLine();
            lineTop = nextLineTop;
            resetCurrentLineState();
        }

        private int getFlowBottom() {
            return lineContentPresent ? lineTop + getCurrentLineHeight() : lineTop;
        }

        private int finishLineAndGetBottom() {
            int bottom = getFlowBottom();
            flushCurrentLine();
            lineTop = bottom;
            resetCurrentLineState();
            return bottom;
        }

        private void reset(int nextLineTop) {
            flushCurrentLine();
            lineTop = nextLineTop;
            resetCurrentLineState();
        }

        private int getCurrentLineHeight() {
            return lineTopInset + baseLineHeight + lineBottomInset;
        }

        private void flushCurrentLine() {
            if (!lineContentPresent) {
                return;
            }
            int textTop = lineTop + lineTopInset;
            for (PendingInlineFragment pendingInlineFragment : pendingInlineFragments) {
                int fragmentTop = textTop - pendingInlineFragment.owner.topEdge;
                int fragmentHeight = pendingInlineFragment.owner.topEdge + baseLineHeight
                        + pendingInlineFragment.owner.bottomEdge;
                inlineFragments.add(new DocumentLayoutInlineFragment(pendingInlineFragment.owner.element,
                        pendingInlineFragment.left, fragmentTop, pendingInlineFragment.width, fragmentHeight));
            }
            for (PendingInlineTextRun pendingTextRun : pendingTextRuns) {
                textRuns.add(new DocumentLayoutTextRun(pendingTextRun.textNode, pendingTextRun.ownerElement,
                        pendingTextRun.text, pendingTextRun.left, textTop, pendingTextRun.width, baseLineHeight));
            }
        }

        private void resetCurrentLineState() {
            cursorLeft = lineLeft;
            lineTopInset = 0;
            lineBottomInset = 0;
            lineContentPresent = false;
            pendingTextRuns.clear();
            pendingInlineFragments.clear();
        }
    }

    /**
     * inline fragment 所属元素及其垂直表面边距。
     */
    private static final class InlineFragmentOwner {

        private final ElementNode element;
        private final int topEdge;
        private final int bottomEdge;

        private InlineFragmentOwner(ElementNode element, int topEdge, int bottomEdge) {
            this.element = element;
            this.topEdge = Math.max(0, topEdge);
            this.bottomEdge = Math.max(0, bottomEdge);
        }
    }

    /**
     * 延迟到行高确定后生成的 inline 文本片段。
     */
    private static final class PendingInlineTextRun {

        private final TextNode textNode;
        private final ElementNode ownerElement;
        private final String text;
        private final int left;
        private final int width;

        private PendingInlineTextRun(TextNode textNode, ElementNode ownerElement, String text, int left, int width) {
            this.textNode = textNode;
            this.ownerElement = ownerElement;
            this.text = text;
            this.left = left;
            this.width = width;
        }
    }

    /**
     * 延迟到行高确定后生成的 inline 元素表面片段。
     */
    private static final class PendingInlineFragment {

        private final InlineFragmentOwner owner;
        private final int left;
        private final int width;

        private PendingInlineFragment(InlineFragmentOwner owner, int left, int width) {
            this.owner = owner;
            this.left = left;
            this.width = width;
        }
    }

    private static final class LayoutChildrenResult {

        private final List<DocumentLayoutBox> children;
        private final List<DocumentLayoutTextRun> textRuns;
        private final List<DocumentLayoutInlineFragment> inlineFragments;
        private final int contentHeight;

        private LayoutChildrenResult(List<DocumentLayoutBox> children, List<DocumentLayoutTextRun> textRuns,
                List<DocumentLayoutInlineFragment> inlineFragments, int contentHeight) {
            this.children = children;
            this.textRuns = textRuns;
            this.inlineFragments = inlineFragments;
            this.contentHeight = Math.max(0, contentHeight);
        }
    }

    /**
     * inline 元素在行内流中参与占位和表面扩展的盒边。
     */
    private static final class InlineElementEdges {

        private final DocumentLayoutEdges margin;
        private final DocumentLayoutEdges border;
        private final DocumentLayoutEdges padding;

        private InlineElementEdges(DocumentLayoutEdges margin, DocumentLayoutEdges border,
                DocumentLayoutEdges padding) {
            this.margin = margin;
            this.border = border;
            this.padding = padding;
        }

        private int getVerticalTop() {
            return border.getTop() + padding.getTop();
        }

        private int getVerticalBottom() {
            return padding.getBottom() + border.getBottom();
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
