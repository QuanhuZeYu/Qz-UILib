package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentImageElementSupport;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.layout.TextLayoutHelper.TextWrapSegment;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.selector.UiPseudoElement;
import club.heiqi.uilib.ui.style.values.UiPseudoElementContent;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.props.UiTextAlign;
import club.heiqi.uilib.ui.style.props.UiTextOverflow;
import club.heiqi.uilib.ui.style.props.UiVerticalAlign;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * HTML-like 文档布局引擎初版。
 *
 * <p>当前实现覆盖元素盒、box model、block flow、最小 flex flow、table flow、relative 定位偏移、absolute 脱流定位
 * 与 fixed 视口定位。absolute 元素会相对最近 positioned ancestor 的 padding box 定位，没有 positioned
 * ancestor 时回退根 padding box；fixed 元素相对当前 HTML-like 视口定位。当前已支持 positioned 元素
 * 在横向或纵向两侧 inset 同时存在且尺寸为 auto 时进行 stretch 求解，并支持包含 inline 元素的
 * text/span 初版混排和 inline fragment 盒边；更完整 inline box、多行 flex wrap 和滚动布局会在后续阶段继续扩展。</p>
 */
public final class DocumentLayoutEngine {

    static final int AUTO_SIZE = -1;
    private static final TextMeasureService FALLBACK_TEXT_MEASURE_SERVICE = new FixedTextMeasureService();
    private static final LayoutRuntimeValueResolver STATIC_LAYOUT_VALUE_RESOLVER = new LayoutRuntimeValueResolver() {
        @Override
        public int resolve(ElementNode element, DocumentAnimationProperty property, int baseValue) {
            return baseValue;
        }
    };

    private DocumentLayoutEngine() {}

    /**
     * 布局阶段运行态值解析器。
     */
    public interface LayoutRuntimeValueResolver {

        /**
         * 返回指定 layout-affecting 属性的运行态布局值。
         *
         * @param element 元素
         * @param property 动画属性
         * @param baseValue computed style 解析后的基准布局值
         * @return 运行态布局值
         */
        int resolve(ElementNode element, DocumentAnimationProperty property, int baseValue);
    }

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
        return layout(rootElement, viewportWidth, viewportHeight, textMeasureService, STATIC_LAYOUT_VALUE_RESOLVER);
    }

    /**
     * 使用指定文本测量服务和运行态布局值解析器对根元素执行布局。
     *
     * @param rootElement 根元素
     * @param viewportWidth 视口宽度
     * @param viewportHeight 视口高度；当前仅作为后续视口约束扩展预留
     * @param textMeasureService 文本测量服务
     * @param layoutValueResolver 运行态布局值解析器
     * @return 根布局盒
     */
    public static DocumentLayoutBox layout(ElementNode rootElement, int viewportWidth, int viewportHeight,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        Objects.requireNonNull(rootElement, "rootElement");
        int safeViewportWidth = Math.max(0, viewportWidth);
        int safeViewportHeight = Math.max(0, viewportHeight);
        AbsoluteContainingBlock fixedContainingBlock = new AbsoluteContainingBlock(0, 0, safeViewportWidth,
                safeViewportHeight);
        return layoutElement(rootElement, 0, 0, safeViewportWidth, safeViewportHeight, AUTO_SIZE, AUTO_SIZE,
                null, fixedContainingBlock, Objects.requireNonNull(textMeasureService, "textMeasureService"),
                resolveLayoutValueResolver(layoutValueResolver));
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
        return layoutViewportRoot(rootElement, viewportWidth, viewportHeight, textMeasureService,
                STATIC_LAYOUT_VALUE_RESOLVER);
    }

    /**
     * 使用运行态布局值解析器对视口根元素执行布局。
     *
     * @param rootElement 根元素
     * @param viewportWidth 视口宽度
     * @param viewportHeight 视口高度
     * @param textMeasureService 文本测量服务
     * @param layoutValueResolver 运行态布局值解析器
     * @return 根布局盒
     */
    public static DocumentLayoutBox layoutViewportRoot(ElementNode rootElement, int viewportWidth, int viewportHeight,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        Objects.requireNonNull(rootElement, "rootElement");
        TextMeasureService resolvedTextMeasureService = Objects.requireNonNull(textMeasureService,
                "textMeasureService");
        LayoutRuntimeValueResolver resolvedLayoutValueResolver = resolveLayoutValueResolver(layoutValueResolver);
        int safeViewportWidth = Math.max(0, viewportWidth);
        int safeViewportHeight = Math.max(0, viewportHeight);
        ComputedStyle rootStyle = UiStyleResolver.compute(rootElement);
        DocumentLayoutEdges margin = resolveMarginInsets(rootElement, rootStyle, safeViewportWidth,
                resolvedLayoutValueResolver);
        DocumentLayoutEdges border = resolveBorderInsets(rootStyle, safeViewportWidth);
        DocumentLayoutEdges padding = resolvePaddingInsets(rootElement, rootStyle, safeViewportWidth,
                resolvedLayoutValueResolver);
        int forcedContentWidth = Math.max(0,
                safeViewportWidth - margin.getHorizontal() - border.getHorizontal() - padding.getHorizontal());
        int forcedContentHeight = Math.max(0,
                safeViewportHeight - margin.getVertical() - border.getVertical() - padding.getVertical());
        AbsoluteContainingBlock fixedContainingBlock = new AbsoluteContainingBlock(0, 0, safeViewportWidth,
                safeViewportHeight);
        return layoutElement(rootElement, 0, 0, safeViewportWidth, safeViewportHeight, forcedContentWidth,
                forcedContentHeight,
                null, fixedContainingBlock, resolvedTextMeasureService, resolvedLayoutValueResolver);
    }

    static DocumentLayoutBox layoutElement(ElementNode element, int containingLeft, int flowTop,
            int containingWidth, int containingHeight, int forcedContentWidth, int forcedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        ComputedStyle computedStyle = UiStyleResolver.compute(element);
        if (computedStyle.getDisplay() == UiDisplay.NONE) {
            return new DocumentLayoutBox(element, computedStyle, new ArrayList<DocumentLayoutBox>(),
                    new ArrayList<DocumentLayoutTextRun>(), new ArrayList<DocumentLayoutInlineFragment>(),
                    DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), containingLeft,
                    flowTop, 0, 0, 0, 0);
        }

        DocumentLayoutEdges margin = resolveMarginInsets(element, computedStyle, containingWidth,
                layoutValueResolver);
        DocumentLayoutEdges border = resolveBorderInsets(computedStyle, containingWidth);
        DocumentLayoutEdges padding = resolvePaddingInsets(element, computedStyle, containingWidth,
                layoutValueResolver);

        int availableBorderBoxWidth = Math.max(0, containingWidth - margin.getHorizontal());
        int autoContentWidth = Math.max(0, availableBorderBoxWidth - border.getHorizontal() - padding.getHorizontal());
        int contentWidth = resolveContentWidth(element, computedStyle, containingWidth, autoContentWidth,
                forcedContentWidth, border, padding, textMeasureService, layoutValueResolver);
        int borderBoxWidth = contentWidth + border.getHorizontal() + padding.getHorizontal();

        // #3 修复：block 元素 margin:auto 水平居中
        int resolvedMarginLeft = margin.getLeft();
        int resolvedMarginRight = margin.getRight();
        if (forcedContentWidth < 0 && !isOutOfFlowPositioned(computedStyle)
                && computedStyle.getDisplay() != UiDisplay.INLINE
                && computedStyle.getDisplay() != UiDisplay.INLINE_BLOCK) {
            UiStyleInsets rawMargin = computedStyle.getMargin();
            boolean autoLeft = isAuto(rawMargin.getLeft());
            boolean autoRight = isAuto(rawMargin.getRight());
            if (autoLeft || autoRight) {
                int remainingSpace = Math.max(0, containingWidth - borderBoxWidth);
                if (autoLeft && autoRight) {
                    resolvedMarginLeft = remainingSpace / 2;
                    resolvedMarginRight = remainingSpace - resolvedMarginLeft;
                } else if (autoLeft) {
                    resolvedMarginLeft = remainingSpace;
                } else {
                    resolvedMarginRight = remainingSpace;
                }
            }
        }

        int borderBoxLeft = containingLeft + resolvedMarginLeft;
        int borderBoxTop = flowTop + margin.getTop();
        int contentLeft = borderBoxLeft + border.getLeft() + padding.getLeft();
        int contentTop = borderBoxTop + border.getTop() + padding.getTop();

        int specifiedContentHeight = resolveSpecifiedHeight(element, computedStyle, forcedContentHeight, contentWidth,
                containingHeight, layoutValueResolver);
        boolean createsAbsoluteContainingBlock = absoluteContainingBlock == null || isPositioned(computedStyle);
        AbsoluteContainingBlock childrenAbsoluteContainingBlock = createsAbsoluteContainingBlock
                ? AbsoluteContainingBlock.paddingBox(borderBoxLeft + border.getLeft(), borderBoxTop + border.getTop(),
                        contentWidth + padding.getHorizontal(), resolveInitialAbsoluteContainingBlockHeight(
                                specifiedContentHeight), padding.getVertical())
                : absoluteContainingBlock;
        LayoutChildrenResult childrenResult;
        if (computedStyle.getDisplay() == UiDisplay.FLEX) {
            childrenResult = layoutFlexChildren(element, computedStyle, contentLeft, contentTop, contentWidth,
                    specifiedContentHeight, childrenAbsoluteContainingBlock, createsAbsoluteContainingBlock,
                    fixedContainingBlock, textMeasureService, layoutValueResolver);
        } else if (computedStyle.getDisplay() == UiDisplay.TABLE) {
            childrenResult = layoutTableChildren(element, computedStyle, contentLeft, contentTop, contentWidth,
                    specifiedContentHeight, childrenAbsoluteContainingBlock, createsAbsoluteContainingBlock,
                    fixedContainingBlock, textMeasureService, layoutValueResolver);
        } else {
            childrenResult = layoutBlockChildren(element, contentLeft, contentTop, contentWidth, specifiedContentHeight,
                    childrenAbsoluteContainingBlock, createsAbsoluteContainingBlock, fixedContainingBlock,
                    textMeasureService, layoutValueResolver);
        }

        int autoContentHeight = childrenResult.contentHeight;
        int contentHeight = resolveContentHeight(element, computedStyle, forcedContentHeight, autoContentHeight,
                contentWidth, containingHeight,
                layoutValueResolver);
        int borderBoxHeight = contentHeight + border.getVertical() + padding.getVertical();
        int positionOffsetX = resolveRelativeOffsetX(computedStyle, containingWidth);
        int positionOffsetY = resolveRelativeOffsetY(computedStyle, containingHeight);
        return new DocumentLayoutBox(element, computedStyle, childrenResult.children, childrenResult.textRuns,
                childrenResult.inlineFragments, margin, border, padding, borderBoxLeft, borderBoxTop, borderBoxWidth,
                borderBoxHeight, positionOffsetX, positionOffsetY);
    }

    private static LayoutChildrenResult layoutBlockChildren(ElementNode element, int contentLeft, int contentTop,
            int contentWidth, int specifiedContentHeight, AbsoluteContainingBlock absoluteContainingBlock,
            boolean createsAbsoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        List<DocumentLayoutTextRun> textRuns = new ArrayList<DocumentLayoutTextRun>();
        List<DocumentLayoutInlineFragment> inlineFragments = new ArrayList<DocumentLayoutInlineFragment>();
        List<ElementNode> absoluteChildren = new ArrayList<ElementNode>();
        List<ElementNode> fixedChildren = new ArrayList<ElementNode>();
        boolean usesInlineFormatting = hasVisibleInlineElementChild(element);
        ComputedStyle elementStyle = UiStyleResolver.compute(element);
        int textIndent = TextLayoutHelper.resolveTextIndent(elementStyle, contentWidth);
        InlineLayoutContext inlineLayoutContext = usesInlineFormatting
                ? new InlineLayoutContext(contentLeft, contentTop, contentWidth,
                        TextLayoutHelper.resolveTextLineHeight(textMeasureService, elementStyle), textIndent,
                        textRuns, inlineFragments)
                : null;
        int childFlowTop = contentTop;
        boolean textIndentPending = true;
        for (DocumentNode child : getGeneratedChildNodes(element)) {
            if (child instanceof TextNode) {
                if (usesInlineFormatting) {
                    appendInlineTextRun((TextNode) child, element, new ArrayList<InlineFragmentOwner>(),
                            inlineLayoutContext, textMeasureService);
                    childFlowTop = inlineLayoutContext.getFlowBottom();
                } else {
                    TextNode textNode = (TextNode) child;
                    int firstLineIndent = textIndentPending ? textIndent : 0;
                    childFlowTop = appendTextRun(textNode, element, elementStyle, textRuns, contentLeft, childFlowTop,
                            contentWidth, firstLineIndent, textMeasureService);
                    if (textNode.getText() != null && !textNode.getText().isEmpty()) {
                        textIndentPending = false;
                    }
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
                appendInlineElementTextRuns(childElement, inlineLayoutContext, textMeasureService,
                        layoutValueResolver);
                childFlowTop = inlineLayoutContext.getFlowBottom();
                continue;
            }
            if (usesInlineFormatting && childStyle.getDisplay() == UiDisplay.INLINE_BLOCK) {
                DocumentLayoutBox measuredChildBox = layoutElement(childElement, 0, 0, contentWidth,
                        specifiedContentHeight, AUTO_SIZE, AUTO_SIZE, absoluteContainingBlock, fixedContainingBlock,
                        textMeasureService, layoutValueResolver);
                int measuredOuterWidth = measuredChildBox.getWidth() + measuredChildBox.getMargin().getHorizontal();
                if (inlineLayoutContext.hasLineContent()
                        && measuredOuterWidth > inlineLayoutContext.getRemainingWidth()) {
                    childFlowTop = inlineLayoutContext.finishLineAndGetBottom();
                    inlineLayoutContext.reset(childFlowTop);
                }
                DocumentLayoutBox childBox = layoutElement(childElement, inlineLayoutContext.getCursorLeft(),
                        inlineLayoutContext.getLineTop(), contentWidth, specifiedContentHeight, AUTO_SIZE, AUTO_SIZE,
                        absoluteContainingBlock, fixedContainingBlock, textMeasureService, layoutValueResolver);
                childBoxes.add(childBox);
                inlineLayoutContext.appendInlineBlock(childBox.getWidth() + childBox.getMargin().getHorizontal(),
                        childBox.getHeight() + childBox.getMargin().getVertical());
                childFlowTop = inlineLayoutContext.getFlowBottom();
                continue;
            }
            if (usesInlineFormatting) {
                childFlowTop = inlineLayoutContext.finishLineAndGetBottom();
                inlineLayoutContext.reset(childFlowTop);
            }
            // #1 修复：相邻兄弟垂直 margin collapse（取较大值而非叠加）
            int previousMarginBottom = 0;
            if (!childBoxes.isEmpty()) {
                DocumentLayoutBox previousBox = childBoxes.get(childBoxes.size() - 1);
                previousMarginBottom = previousBox.getMargin().getBottom();
            }
            int childMarginTop = resolveMarginInsets(childElement, childStyle, contentWidth, layoutValueResolver).getTop();
            int collapsedMargin = Math.max(previousMarginBottom, childMarginTop);
            int marginCollapseAdjustment = childBoxes.isEmpty() ? 0
                    : Math.min(previousMarginBottom, childMarginTop);
            int adjustedFlowTop = childFlowTop - marginCollapseAdjustment;
            DocumentLayoutBox childBox = layoutElement(childElement, contentLeft, adjustedFlowTop, contentWidth,
                    specifiedContentHeight, AUTO_SIZE, AUTO_SIZE, absoluteContainingBlock, fixedContainingBlock,
                    textMeasureService, layoutValueResolver);
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
                fixedContainingBlock, textMeasureService, layoutValueResolver);
        appendFixedChildren(childBoxes, fixedChildren, fixedContainingBlock, textMeasureService,
                layoutValueResolver);
        return new LayoutChildrenResult(sortByDocumentChildOrder(element, childBoxes), textRuns,
                markInlineFragmentSequence(mergeInlineFragments(inlineFragments)), contentHeight);
    }

    /**
     * 委托 table 布局到 {@link TableLayoutHelper}。
     */
    static LayoutChildrenResult layoutTableChildren(ElementNode element, ComputedStyle tableStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, boolean createsAbsoluteContainingBlock,
            AbsoluteContainingBlock fixedContainingBlock, TextMeasureService textMeasureService,
            LayoutRuntimeValueResolver layoutValueResolver) {
        return TableLayoutHelper.layoutTableChildren(element, tableStyle, contentLeft, contentTop, contentWidth,
                specifiedContentHeight, absoluteContainingBlock, createsAbsoluteContainingBlock,
                fixedContainingBlock, textMeasureService, layoutValueResolver);
    }

    static int getOuterBlockHeight(DocumentLayoutBox box) {
        return Math.max(0, box.getMarginBoxBottom() - box.getMarginBoxTop());
    }

    static int sum(int[] values) {
        int result = 0;
        for (int value : values) {
            result += Math.max(0, value);
        }
        return result;
    }

    private static int appendTextRun(TextNode textNode, ElementNode ownerElement,
            List<DocumentLayoutTextRun> textRuns, int left, int top, int availableWidth,
            TextMeasureService textMeasureService) {
        return appendTextRun(textNode, ownerElement, null, textRuns, left, top, availableWidth, 0, textMeasureService);
    }

    private static int appendTextRun(TextNode textNode, ElementNode ownerElement, ComputedStyle ownerStyle,
            List<DocumentLayoutTextRun> textRuns, int left, int top, int availableWidth, int firstLineIndent,
            TextMeasureService textMeasureService) {
        TextContentMode textContentMode = textNode.getTextContentMode();
        String text = TextLayoutHelper.normalizeTextForLayout(textNode.getText(), ownerStyle, textContentMode);
        if (text == null || text.isEmpty()) {
            return top;
        }
        int lineHeight = TextLayoutHelper.resolveTextLineHeight(textMeasureService, ownerStyle);
        UiWhiteSpace whiteSpace = ownerStyle != null ? ownerStyle.getWhiteSpace() : UiWhiteSpace.NORMAL;
        UiTextOverflow textOverflow = ownerStyle != null ? ownerStyle.getTextOverflow() : UiTextOverflow.CLIP;
        UiTextAlign textAlign = ownerStyle != null ? ownerStyle.getTextAlign() : UiTextAlign.START;

        String remainingText = text;
        int lineTop = top;
        int lineIndex = 0;
        while (!remainingText.isEmpty()) {
            int lineIndent = lineIndex == 0 ? firstLineIndent : 0;
            int lineAvailableWidth = Math.max(0, availableWidth - lineIndent);
            int segmentAvailableWidth = TextLayoutHelper.allowsSoftWrapping(whiteSpace)
                    ? lineAvailableWidth
                    : Integer.MAX_VALUE / 2;
            TextWrapSegment segment = TextLayoutHelper.takeNextTextSegment(remainingText, segmentAvailableWidth,
                    ownerStyle, textContentMode, textMeasureService);
            if (segment.consumedLength <= 0) {
                break;
            }
            String resolvedLine = segment.text == null ? "" : segment.text;
            int rawWidth = TextLayoutHelper.toUiTextSize(TextLayoutHelper.measureTextWidth(textMeasureService,
                    resolvedLine, textContentMode, ownerStyle));
            // text-overflow: ellipsis 处理（仅 nowrap 且内容超出时）
            if (whiteSpace == UiWhiteSpace.NOWRAP && textOverflow == UiTextOverflow.ELLIPSIS
                    && rawWidth > availableWidth && availableWidth > 0) {
                // 计算省略号宽度
                String ellipsis = "\u2026";
                int ellipsisWidth = TextLayoutHelper.toUiTextSize(TextLayoutHelper.measureTextWidth(textMeasureService,
                        ellipsis, textContentMode, ownerStyle));
                int targetWidth = Math.max(0, availableWidth - ellipsisWidth);
                String trimmed = TextLayoutHelper.trimTextToWidth(textMeasureService, resolvedLine,
                        TextLayoutHelper.toRawTextSize(targetWidth), textContentMode, ownerStyle);
                if (trimmed == null) {
                    trimmed = "";
                }
                resolvedLine = trimmed + ellipsis;
                rawWidth = Math.min(availableWidth,
                        TextLayoutHelper.toUiTextSize(TextLayoutHelper.measureTextWidth(textMeasureService,
                                resolvedLine, textContentMode, ownerStyle)));
            }
            int width = Math.max(0, Math.min(lineAvailableWidth, rawWidth));
            // text-align 偏移
            int lineLeft = TextLayoutHelper.resolveTextAlignOffset(textAlign, lineAvailableWidth, width)
                    + left + lineIndent;
            textRuns.add(new DocumentLayoutTextRun(textNode, ownerElement, resolvedLine, textContentMode, lineLeft,
                    lineTop, width, lineHeight));
            lineTop += lineHeight;
            lineIndex++;
            remainingText = remainingText.substring(Math.min(segment.consumedLength, remainingText.length()));
        }
        return lineTop;
    }

    private static void appendInlineElementTextRuns(ElementNode inlineElement, InlineLayoutContext inlineLayoutContext,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        appendInlineElementTextRuns(inlineElement, inlineLayoutContext, textMeasureService, layoutValueResolver,
                new ArrayList<InlineFragmentOwner>());
    }

    private static void appendInlineElementTextRuns(ElementNode inlineElement, InlineLayoutContext inlineLayoutContext,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver,
            List<InlineFragmentOwner> ancestorInlineElements) {
        InlineElementEdges edges = resolveInlineElementEdges(inlineElement, inlineLayoutContext.getLineWidth(),
                layoutValueResolver);
        List<InlineFragmentOwner> fragmentOwners = new ArrayList<InlineFragmentOwner>(ancestorInlineElements);
        fragmentOwners.add(new InlineFragmentOwner(inlineElement, edges.getVerticalTop(),
                edges.getVerticalBottom(), UiStyleResolver.compute(inlineElement).getVerticalAlign()));
        appendInlineSpacing(ancestorInlineElements, inlineLayoutContext, edges.margin.getLeft());
        appendInlineSpacing(fragmentOwners, inlineLayoutContext,
                edges.border.getLeft() + edges.padding.getLeft());
        for (DocumentNode child : getGeneratedChildNodes(inlineElement)) {
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
            appendInlineElementTextRuns(childElement, inlineLayoutContext, textMeasureService, layoutValueResolver,
                    fragmentOwners);
        }
        appendInlineSpacing(fragmentOwners, inlineLayoutContext,
                edges.padding.getRight() + edges.border.getRight());
        appendInlineSpacing(ancestorInlineElements, inlineLayoutContext, edges.margin.getRight());
    }

    private static void appendInlineTextRun(TextNode textNode, ElementNode ownerElement,
            List<InlineFragmentOwner> fragmentOwners, InlineLayoutContext inlineLayoutContext,
            TextMeasureService textMeasureService) {
        ComputedStyle ownerStyle = UiStyleResolver.compute(ownerElement);
        TextContentMode textContentMode = textNode.getTextContentMode();
        String remainingText = TextLayoutHelper.normalizeTextForLayout(textNode.getText(), ownerStyle, textContentMode);
        if (remainingText == null || remainingText.isEmpty() || inlineLayoutContext.getLineWidth() <= 0) {
            return;
        }
        UiWhiteSpace whiteSpace = ownerStyle.getWhiteSpace();
        while (!remainingText.isEmpty()) {
            if (TextLayoutHelper.allowsSoftWrapping(whiteSpace)
                    && inlineLayoutContext.getRemainingWidth() <= 0 && inlineLayoutContext.hasLineContent()) {
                inlineLayoutContext.nextLine();
            }
            int remainingWidth = inlineLayoutContext.getRemainingWidth();
            if (remainingWidth <= 0 && TextLayoutHelper.allowsSoftWrapping(whiteSpace)) {
                return;
            }
            int segmentAvailableWidth = TextLayoutHelper.allowsSoftWrapping(whiteSpace)
                    ? remainingWidth : Integer.MAX_VALUE / 2;
            TextWrapSegment segmentResult = TextLayoutHelper.takeNextTextSegment(remainingText, segmentAvailableWidth,
                    ownerStyle, textContentMode, textMeasureService);
            String segment = segmentResult.text;
            if (segment.isEmpty()) {
                remainingText = remainingText.substring(Math.min(segmentResult.consumedLength, remainingText.length()));
                if (segmentResult.forceLineBreak) {
                    inlineLayoutContext.forceLineBreak();
                }
                continue;
            }
            int width = Math.max(0, TextLayoutHelper.toUiTextSize(TextLayoutHelper.measureTextWidth(textMeasureService,
                    segment, textContentMode, ownerStyle)));
            if (TextLayoutHelper.allowsSoftWrapping(whiteSpace)
                    && width > remainingWidth && inlineLayoutContext.hasLineContent()) {
                inlineLayoutContext.nextLine();
                continue;
            }
            if (TextLayoutHelper.allowsSoftWrapping(whiteSpace)) {
                width = Math.min(width, inlineLayoutContext.getRemainingWidth());
            }
            inlineLayoutContext.appendTextRun(textNode, ownerElement, segment, inlineLayoutContext.getCursorLeft(),
                    width, fragmentOwners);
            appendInlineFragments(fragmentOwners, inlineLayoutContext.getCursorLeft(), width, inlineLayoutContext);
            inlineLayoutContext.advance(width);
            remainingText = remainingText.substring(Math.min(segmentResult.consumedLength, remainingText.length()));
            if (segmentResult.forceLineBreak) {
                inlineLayoutContext.forceLineBreak();
                continue;
            }
            if (TextLayoutHelper.allowsSoftWrapping(whiteSpace)
                    && !remainingText.isEmpty() && inlineLayoutContext.getRemainingWidth() <= 0) {
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
                    top, right - left, bottom - top, existingFragment.isFirstForElement(),
                    existingFragment.isLastForElement()));
        }
        return mergedFragments;
    }

    private static List<DocumentLayoutInlineFragment> markInlineFragmentSequence(
            List<DocumentLayoutInlineFragment> inlineFragments) {
        List<DocumentLayoutInlineFragment> markedFragments = new ArrayList<DocumentLayoutInlineFragment>();
        for (DocumentLayoutInlineFragment inlineFragment : inlineFragments) {
            boolean first = true;
            boolean last = true;
            for (DocumentLayoutInlineFragment otherFragment : inlineFragments) {
                if (otherFragment == inlineFragment || otherFragment.getOwnerElement() != inlineFragment.getOwnerElement()) {
                    continue;
                }
                if (isInlineFragmentBefore(otherFragment, inlineFragment)) {
                    first = false;
                }
                if (isInlineFragmentBefore(inlineFragment, otherFragment)) {
                    last = false;
                }
            }
            markedFragments.add(new DocumentLayoutInlineFragment(inlineFragment.getOwnerElement(), inlineFragment.getLeft(),
                    inlineFragment.getTop(), inlineFragment.getWidth(), inlineFragment.getHeight(), first, last));
        }
        return markedFragments;
    }

    private static boolean isInlineFragmentBefore(DocumentLayoutInlineFragment first,
            DocumentLayoutInlineFragment second) {
        if (first.getTop() != second.getTop()) {
            return first.getTop() < second.getTop();
        }
        return first.getLeft() < second.getLeft();
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

    private static InlineElementEdges resolveInlineElementEdges(ElementNode inlineElement, int lineWidth,
            LayoutRuntimeValueResolver layoutValueResolver) {
        ComputedStyle style = UiStyleResolver.compute(inlineElement);
        return new InlineElementEdges(resolveMarginInsets(inlineElement, style, lineWidth, layoutValueResolver),
                resolveBorderInsets(style, lineWidth), resolvePaddingInsets(inlineElement, style,
                        lineWidth, layoutValueResolver));
    }

    /**
     * 委托 flex 布局到 {@link FlexLayoutHelper}。
     */
    private static LayoutChildrenResult layoutFlexChildren(ElementNode element, ComputedStyle parentStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, boolean createsAbsoluteContainingBlock,
            AbsoluteContainingBlock fixedContainingBlock, TextMeasureService textMeasureService,
            LayoutRuntimeValueResolver layoutValueResolver) {
        return FlexLayoutHelper.layoutFlexChildren(element, parentStyle, contentLeft, contentTop, contentWidth,
                specifiedContentHeight, absoluteContainingBlock, createsAbsoluteContainingBlock,
                fixedContainingBlock, textMeasureService, layoutValueResolver);
    }

    /**
     * 递归测量元素内容的固有宽度。
     */
    static int measureIntrinsicContentWidth(ElementNode element, TextMeasureService textMeasureService,
            int containingWidth, LayoutRuntimeValueResolver layoutValueResolver) {
        ComputedStyle style = UiStyleResolver.compute(element);
        if (DocumentImageElementSupport.isImageTag(element.getTagName())) {
            return DocumentImageElementSupport.resolveIntrinsicWidth(element);
        }
        if (style.getDisplay() == UiDisplay.FLEX) {
            return FlexLayoutHelper.measureIntrinsicFlexContentWidth(element, style, textMeasureService,
                    containingWidth, layoutValueResolver);
        }

        int maxWidth = 0;
        int inlineWidth = 0;
        for (DocumentNode child : getGeneratedChildNodes(element)) {
            if (child instanceof TextNode) {
                TextNode textNode = (TextNode) child;
                inlineWidth += TextLayoutHelper.measureIntrinsicTextWidth(textNode, style, textMeasureService);
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
            if (isInlineFormattingDisplay(childStyle.getDisplay())) {
                inlineWidth += measureIntrinsicOuterWidth(childElement, childStyle, textMeasureService,
                        containingWidth, layoutValueResolver);
                continue;
            }
            maxWidth = Math.max(maxWidth, inlineWidth);
            inlineWidth = 0;
            maxWidth = Math.max(maxWidth, measureIntrinsicOuterWidth(childElement, childStyle, textMeasureService,
                    containingWidth, layoutValueResolver));
        }
        return Math.max(maxWidth, inlineWidth);
    }

    static int measureIntrinsicOuterWidth(ElementNode element, ComputedStyle style,
            TextMeasureService textMeasureService, int containingWidth,
            LayoutRuntimeValueResolver layoutValueResolver) {
        DocumentLayoutEdges margin = resolveMarginInsets(element, style, containingWidth, layoutValueResolver);
        DocumentLayoutEdges border = resolveBorderInsets(style, containingWidth);
        DocumentLayoutEdges padding = resolvePaddingInsets(element, style, containingWidth, layoutValueResolver);
        int contentWidth;
        if (isAuto(style.getWidth())) {
            contentWidth = measureIntrinsicContentWidth(element, textMeasureService, containingWidth,
                    layoutValueResolver);
        } else {
            int baseWidth = Math.max(0, style.getWidth().resolve(containingWidth, 0));
            contentWidth = Math.max(0, layoutValueResolver.resolve(element, DocumentAnimationProperty.WIDTH,
                    baseWidth));
            contentWidth = resolveBoxSizingContentWidth(style, contentWidth, border, padding);
        }
        return margin.getHorizontal() + border.getHorizontal() + padding.getHorizontal() + contentWidth;
    }

    private static int resolveContentWidth(ElementNode element, ComputedStyle computedStyle, int containingWidth,
            int autoContentWidth, int forcedContentWidth, DocumentLayoutEdges border, DocumentLayoutEdges padding,
            TextMeasureService textMeasureService,
            LayoutRuntimeValueResolver layoutValueResolver) {
        if (forcedContentWidth >= 0) {
            return forcedContentWidth;
        }
        UiStyleLength width = computedStyle.getWidth();
        if (isAuto(width) && DocumentImageElementSupport.isImageTag(element.getTagName())) {
            return DocumentImageElementSupport.resolveContentWidth(element, computedStyle, containingWidth,
                    autoContentWidth);
        }
        int autoFallback = computedStyle.getDisplay() == UiDisplay.INLINE_BLOCK
                ? Math.min(measureIntrinsicContentWidth(element, textMeasureService, containingWidth,
                        layoutValueResolver), autoContentWidth)
                : autoContentWidth;
        int baseWidth = Math.max(0, width.resolve(containingWidth, autoFallback));
        int resolvedWidth = Math.max(0, layoutValueResolver.resolve(element, DocumentAnimationProperty.WIDTH,
                baseWidth));
        int contentWidth = resolveBoxSizingContentWidth(computedStyle, resolvedWidth, border, padding);
        // 应用 min/max-width 约束（规范：min-width > max-width > width）
        contentWidth = applyWidthConstraints(computedStyle, contentWidth, containingWidth, border, padding);
        return contentWidth;
    }

    /**
     * 将 min-width / max-width 约束应用到内容宽度。
     */
    private static int applyWidthConstraints(ComputedStyle style, int contentWidth, int containingWidth,
            DocumentLayoutEdges border, DocumentLayoutEdges padding) {
        int minW = Math.max(0, style.getMinWidth().resolve(containingWidth, 0));
        int result = Math.max(contentWidth, minW);
        if (!isAuto(style.getMaxWidth())) {
            int maxW = style.getMaxWidth().resolve(containingWidth, Integer.MAX_VALUE);
            if (maxW >= 0) {
                maxW = resolveBoxSizingContentWidth(style, maxW, border, padding);
                result = Math.min(result, maxW);
            }
        }
        return Math.max(0, result);
    }

    static int resolveBoxSizingContentWidth(ComputedStyle computedStyle, int resolvedWidth,
            DocumentLayoutEdges border, DocumentLayoutEdges padding) {
        if (computedStyle.getBoxSizing() != UiBoxSizing.BORDER_BOX || isAuto(computedStyle.getWidth())) {
            return resolvedWidth;
        }
        return Math.max(0, resolvedWidth - border.getHorizontal() - padding.getHorizontal());
    }

    /**
     * 将声明高度从 border-box 转换为 content height。
     *
     * <p>仅在 box-sizing:border-box 且 height 非 auto 时扣除 border/padding；
     * forcedContentHeight 已经是 content height，不经过此方法。</p>
     */
    static int resolveBoxSizingContentHeight(ComputedStyle computedStyle, int resolvedHeight,
            DocumentLayoutEdges border, DocumentLayoutEdges padding) {
        if (computedStyle.getBoxSizing() != UiBoxSizing.BORDER_BOX || isAuto(computedStyle.getHeight())) {
            return resolvedHeight;
        }
        return Math.max(0, resolvedHeight - border.getVertical() - padding.getVertical());
    }

    private static int resolveContentHeight(ElementNode element, ComputedStyle computedStyle, int forcedContentHeight,
            int autoContentHeight, int contentWidth, LayoutRuntimeValueResolver layoutValueResolver) {
        return resolveContentHeight(element, computedStyle, forcedContentHeight, autoContentHeight, contentWidth,
                AUTO_SIZE, layoutValueResolver);
    }

    /**
     * 解析最终内容高度，支持百分比相对 containingHeight 解析。
     */
    private static int resolveContentHeight(ElementNode element, ComputedStyle computedStyle, int forcedContentHeight,
            int autoContentHeight, int contentWidth, int containingHeight,
            LayoutRuntimeValueResolver layoutValueResolver) {
        if (forcedContentHeight >= 0) {
            return forcedContentHeight;
        }
        if (isAuto(computedStyle.getHeight()) && DocumentImageElementSupport.isImageTag(element.getTagName())) {
            return DocumentImageElementSupport.resolveContentHeight(element, computedStyle, contentWidth);
        }
        if (isAuto(computedStyle.getHeight()) && hasAspectRatio(computedStyle)) {
            return Math.max(autoContentHeight, applyHeightConstraints(computedStyle,
                    resolveAspectRatioContentHeight(computedStyle, contentWidth), contentWidth, containingHeight,
                    resolveBorderInsets(computedStyle, contentWidth),
                    resolveInsets(computedStyle.getPadding(), contentWidth, true)));
        }
        // 百分比高度：当包含块高度为 auto 时，视为 auto（使用内容高度）
        if (computedStyle.getHeight().getType() == UiStyleLength.Type.PERCENT && containingHeight < 0) {
            return applyHeightConstraints(computedStyle, autoContentHeight, contentWidth, containingHeight,
                    resolveBorderInsets(computedStyle, contentWidth),
                    resolveInsets(computedStyle.getPadding(), contentWidth, true));
        }
        int resolveBase = containingHeight >= 0 ? containingHeight : 0;
        int baseHeight = Math.max(0, computedStyle.getHeight().resolve(resolveBase, autoContentHeight));
        int resolvedHeight = Math.max(0, layoutValueResolver.resolve(element, DocumentAnimationProperty.HEIGHT, baseHeight));
        DocumentLayoutEdges border = resolveBorderInsets(computedStyle, contentWidth);
        DocumentLayoutEdges padding = resolveInsets(computedStyle.getPadding(), contentWidth, true);
        int contentHeight = resolveBoxSizingContentHeight(computedStyle, resolvedHeight, border, padding);
        // 应用 min/max-height 约束
        contentHeight = applyHeightConstraints(computedStyle, contentHeight, contentWidth, containingHeight, border, padding);
        return contentHeight;
    }

    /**
     * 将 min-height / max-height 约束应用到内容高度。
     *
     * <p>百分比 min/max-height 相对 containingHeight 解析；containingHeight 为 AUTO_SIZE 时百分比约束不生效。</p>
     */
    private static int applyHeightConstraints(ComputedStyle style, int contentHeight, int contentWidth,
            int containingHeight, DocumentLayoutEdges border, DocumentLayoutEdges padding) {
        int minH = resolveHeightConstraintValue(style.getMinHeight(), containingHeight, 0);
        int result = Math.max(contentHeight, minH);
        if (!isAuto(style.getMaxHeight())) {
            int maxH = resolveHeightConstraintValue(style.getMaxHeight(), containingHeight, Integer.MAX_VALUE);
            if (maxH >= 0 && maxH < Integer.MAX_VALUE) {
                maxH = resolveBoxSizingContentHeight(style, maxH, border, padding);
                result = Math.min(result, maxH);
            }
        }
        return Math.max(0, result);
    }

    /**
     * 解析 min/max-height 的约束值，百分比相对 containingHeight。
     */
    private static int resolveHeightConstraintValue(UiStyleLength length, int containingHeight, int autoFallback) {
        if (isAuto(length)) {
            return autoFallback;
        }
        if (length.getType() == UiStyleLength.Type.PERCENT) {
            // 包含块高度为 auto 时，百分比约束不生效
            if (containingHeight < 0) {
                return autoFallback;
            }
            return Math.max(0, length.resolve(containingHeight, autoFallback));
        }
        return Math.max(0, length.resolve(0, autoFallback));
    }

    private static int resolveSpecifiedHeight(ElementNode element, ComputedStyle computedStyle, int forcedContentHeight,
            int contentWidth, LayoutRuntimeValueResolver layoutValueResolver) {
        return resolveSpecifiedHeight(element, computedStyle, forcedContentHeight, contentWidth, AUTO_SIZE,
                layoutValueResolver);
    }

    /**
     * 解析指定高度，支持百分比相对 containingHeight 解析。
     *
     * <p>当 containingHeight 为 AUTO_SIZE（-1）时，百分比高度视为 auto（返回 AUTO_SIZE）。</p>
     */
    private static int resolveSpecifiedHeight(ElementNode element, ComputedStyle computedStyle, int forcedContentHeight,
            int contentWidth, int containingHeight, LayoutRuntimeValueResolver layoutValueResolver) {
        if (forcedContentHeight >= 0) {
            return forcedContentHeight;
        }
        if (isAuto(computedStyle.getHeight())) {
            if (DocumentImageElementSupport.isImageTag(element.getTagName())) {
                return DocumentImageElementSupport.resolveContentHeight(element, computedStyle, contentWidth);
            }
            if (hasAspectRatio(computedStyle)) {
                return resolveAspectRatioContentHeight(computedStyle, contentWidth);
            }
            return AUTO_SIZE;
        }
        // 百分比高度：当包含块高度为 auto 时，视为 auto
        if (computedStyle.getHeight().getType() == UiStyleLength.Type.PERCENT && containingHeight < 0) {
            return AUTO_SIZE;
        }
        int resolveBase = containingHeight >= 0 ? containingHeight : 0;
        int baseHeight = Math.max(0, computedStyle.getHeight().resolve(resolveBase, 0));
        int resolvedHeight = Math.max(0, layoutValueResolver.resolve(element, DocumentAnimationProperty.HEIGHT, baseHeight));
        DocumentLayoutEdges border = resolveBorderInsets(computedStyle, contentWidth);
        DocumentLayoutEdges padding = resolveInsets(computedStyle.getPadding(), contentWidth, true);
        return resolveBoxSizingContentHeight(computedStyle, resolvedHeight, border, padding);
    }

    static List<ElementNode> getVisibleInFlowElementChildren(ElementNode element) {
        List<ElementNode> children = new ArrayList<ElementNode>();
        for (DocumentNode child : getGeneratedChildNodes(element)) {
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
        for (DocumentNode child : getGeneratedChildNodes(element)) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = UiStyleResolver.compute(childElement);
            if (isInlineFormattingDisplay(childStyle.getDisplay()) && !isOutOfFlowPositioned(childStyle)) {
                return true;
            }
        }
        return false;
    }

    static List<ElementNode> getVisibleAbsoluteElementChildren(ElementNode element) {
        List<ElementNode> children = new ArrayList<ElementNode>();
        for (DocumentNode child : getGeneratedChildNodes(element)) {
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

    static List<ElementNode> getVisibleFixedElementChildren(ElementNode element) {
        List<ElementNode> children = new ArrayList<ElementNode>();
        for (DocumentNode child : getGeneratedChildNodes(element)) {
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

    static void appendAbsoluteChildren(List<DocumentLayoutBox> childBoxes, List<ElementNode> absoluteChildren,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        for (ElementNode child : absoluteChildren) {
            childBoxes.add(layoutPositionedElement(child, absoluteContainingBlock, fixedContainingBlock,
                    textMeasureService, layoutValueResolver));
        }
    }

    static void appendFixedChildren(List<DocumentLayoutBox> childBoxes, List<ElementNode> fixedChildren,
            AbsoluteContainingBlock fixedContainingBlock, TextMeasureService textMeasureService,
            LayoutRuntimeValueResolver layoutValueResolver) {
        for (ElementNode child : fixedChildren) {
            childBoxes.add(layoutPositionedElement(child, fixedContainingBlock, fixedContainingBlock,
                    textMeasureService, layoutValueResolver));
        }
    }

    static DocumentLayoutBox layoutPositionedElement(ElementNode element,
            AbsoluteContainingBlock containingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        ComputedStyle style = UiStyleResolver.compute(element);
        int forcedContentWidth = resolveStretchContentWidth(element, style, containingBlock, layoutValueResolver);
        int forcedContentHeight = resolveStretchContentHeight(element, style, containingBlock, layoutValueResolver);
        DocumentLayoutBox measuredBox = layoutElement(element, 0, 0, containingBlock.width, containingBlock.height,
                forcedContentWidth, forcedContentHeight, containingBlock, fixedContainingBlock, textMeasureService,
                layoutValueResolver);
        int marginBoxWidth = measuredBox.getWidth() + measuredBox.getMargin().getHorizontal();
        int marginBoxHeight = measuredBox.getHeight() + measuredBox.getMargin().getVertical();
        int marginBoxLeft = resolveAbsoluteMarginBoxLeft(style, containingBlock, marginBoxWidth);
        int marginBoxTop = resolveAbsoluteMarginBoxTop(style, containingBlock, marginBoxHeight);
        return layoutElement(element, marginBoxLeft, marginBoxTop, containingBlock.width, containingBlock.height,
                forcedContentWidth, forcedContentHeight, containingBlock, fixedContainingBlock, textMeasureService,
                layoutValueResolver);
    }

    private static int resolveStretchContentWidth(ElementNode element, ComputedStyle style,
            AbsoluteContainingBlock containingBlock, LayoutRuntimeValueResolver layoutValueResolver) {
        if (!isAuto(style.getWidth()) || isAuto(style.getLeft()) || isAuto(style.getRight())) {
            return AUTO_SIZE;
        }
        DocumentLayoutEdges margin = resolveMarginInsets(element, style, containingBlock.width, layoutValueResolver);
        DocumentLayoutEdges border = resolveBorderInsets(style, containingBlock.width);
        DocumentLayoutEdges padding = resolvePaddingInsets(element, style, containingBlock.width,
                layoutValueResolver);
        int leftInset = style.getLeft().resolve(containingBlock.width, 0);
        int rightInset = style.getRight().resolve(containingBlock.width, 0);
        return Math.max(0, containingBlock.width - leftInset - rightInset - margin.getHorizontal()
                - border.getHorizontal() - padding.getHorizontal());
    }

    private static int resolveStretchContentHeight(ElementNode element, ComputedStyle style,
            AbsoluteContainingBlock containingBlock, LayoutRuntimeValueResolver layoutValueResolver) {
        if (!isAuto(style.getHeight()) || isAuto(style.getTop()) || isAuto(style.getBottom())) {
            return AUTO_SIZE;
        }
        int containingHeight = Math.max(0, containingBlock.height);
        DocumentLayoutEdges margin = resolveMarginInsets(element, style, containingBlock.width, layoutValueResolver);
        DocumentLayoutEdges border = resolveBorderInsets(style, containingBlock.width);
        DocumentLayoutEdges padding = resolvePaddingInsets(element, style, containingBlock.width,
                layoutValueResolver);
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

    static AbsoluteContainingBlock resolveDirectAbsoluteContainingBlock(
            AbsoluteContainingBlock absoluteContainingBlock, boolean createsAbsoluteContainingBlock,
            int specifiedContentHeight, int contentHeight) {
        if (!createsAbsoluteContainingBlock) {
            return absoluteContainingBlock;
        }
        int contentBoxHeight = specifiedContentHeight >= 0 ? specifiedContentHeight : contentHeight;
        return absoluteContainingBlock.withContentHeight(contentBoxHeight);
    }

    static List<DocumentLayoutBox> sortByDocumentChildOrder(final ElementNode parentElement,
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

    static int getChildOrder(ElementNode parentElement, ElementNode targetElement) {
        if (targetElement != null && targetElement.isPseudoElement()
                && targetElement.getPseudoOriginElement() == parentElement) {
            return targetElement.getPseudoElement() == UiPseudoElement.BEFORE ? -1
                    : parentElement.getChildren().size() + 1;
        }
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

    static boolean isOutOfFlowPositioned(ComputedStyle style) {
        return isAbsolutePositioned(style) || isFixedPositioned(style);
    }

    private static boolean isInlineFormattingDisplay(UiDisplay display) {
        return display == UiDisplay.INLINE || display == UiDisplay.INLINE_BLOCK;
    }

    static boolean isTableRowGroupDisplay(UiDisplay display) {
        return display == UiDisplay.TABLE_HEADER_GROUP || display == UiDisplay.TABLE_ROW_GROUP
                || display == UiDisplay.TABLE_FOOTER_GROUP;
    }

    private static boolean isPositioned(ComputedStyle style) {
        return style.getPosition() != UiPosition.STATIC;
    }

    private static int resolveInitialAbsoluteContainingBlockHeight(int specifiedContentHeight) {
        return specifiedContentHeight >= 0 ? specifiedContentHeight : 0;
    }

    static DocumentLayoutEdges resolveMarginInsets(ElementNode element, ComputedStyle style,
            int containingWidth, LayoutRuntimeValueResolver layoutValueResolver) {
        DocumentLayoutEdges margin = resolveInsets(style.getMargin(), containingWidth, false);
        int left = layoutValueResolver.resolve(element, DocumentAnimationProperty.MARGIN_LEFT, margin.getLeft());
        int right = layoutValueResolver.resolve(element, DocumentAnimationProperty.MARGIN_RIGHT, margin.getRight());
        return DocumentLayoutEdges.of(margin.getTop(), right, margin.getBottom(), left);
    }

    static DocumentLayoutEdges resolvePaddingInsets(ElementNode element, ComputedStyle style,
            int containingWidth, LayoutRuntimeValueResolver layoutValueResolver) {
        DocumentLayoutEdges padding = resolveInsets(style.getPadding(), containingWidth, true);
        int left = layoutValueResolver.resolve(element, DocumentAnimationProperty.PADDING_LEFT, padding.getLeft());
        int right = layoutValueResolver.resolve(element, DocumentAnimationProperty.PADDING_RIGHT, padding.getRight());
        return DocumentLayoutEdges.of(padding.getTop(), Math.max(0, right), padding.getBottom(), Math.max(0, left));
    }

    static DocumentLayoutEdges resolveBorderInsets(ComputedStyle style, int containingWidth) {
        UiStyleInsets borderWidthSides = style.getBorderWidthSides();
        if (borderWidthSides != null) {
            return resolveInsets(borderWidthSides, containingWidth, true);
        }
        return resolveUniformEdge(style.getBorderWidth(), containingWidth);
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

    static boolean isAuto(UiStyleLength length) {
        return length.getType() == UiStyleLength.Type.AUTO;
    }

    static List<DocumentNode> getGeneratedChildNodes(ElementNode element) {
        if (element.isPseudoElement()) {
            return element.getChildren();
        }
        List<DocumentNode> generatedNodes = new ArrayList<DocumentNode>();
        ElementNode before = createGeneratedPseudoElement(element, UiPseudoElement.BEFORE);
        if (before != null) {
            generatedNodes.add(before);
        }
        generatedNodes.addAll(element.getChildren());
        ElementNode after = createGeneratedPseudoElement(element, UiPseudoElement.AFTER);
        if (after != null) {
            generatedNodes.add(after);
        }
        return generatedNodes;
    }

    private static ElementNode createGeneratedPseudoElement(ElementNode originElement, UiPseudoElement pseudoElement) {
        if (originElement == null || originElement.isPseudoElement()) {
            return null;
        }
        ElementNode pseudoNode = originElement.getOwnerDocument().__createPseudoElementRuntime(originElement,
                pseudoElement);
        ComputedStyle pseudoStyle = UiStyleResolver.compute(pseudoNode);
        UiPseudoElementContent content = pseudoStyle.getContent();
        if (content == null || content.isNone()) {
            return null;
        }
        if (!content.getText().isEmpty()) {
            pseudoNode.__appendGeneratedChild(originElement.getOwnerDocument().rawText(content.getText()));
        }
        return pseudoNode;
    }

    private static boolean hasAspectRatio(ComputedStyle style) {
        return style.getAspectRatio() != null && style.getAspectRatio().floatValue() > 0.0F;
    }

    private static int resolveAspectRatioContentHeight(ComputedStyle style, int contentWidth) {
        float aspectRatio = style.getAspectRatio().floatValue();
        return Math.max(0, Math.round(Math.max(0, contentWidth) / aspectRatio));
    }

    private static LayoutRuntimeValueResolver resolveLayoutValueResolver(LayoutRuntimeValueResolver layoutValueResolver) {
        return layoutValueResolver == null ? STATIC_LAYOUT_VALUE_RESOLVER : layoutValueResolver;
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

    private static int resolveRelativeOffsetY(ComputedStyle computedStyle, int containingHeight) {
        if (computedStyle.getPosition() != UiPosition.RELATIVE) {
            return 0;
        }
        if (!isAuto(computedStyle.getTop())) {
            return computedStyle.getTop().resolve(containingHeight, 0);
        }
        if (!isAuto(computedStyle.getBottom())) {
            return -computedStyle.getBottom().resolve(containingHeight, 0);
        }
        return 0;
    }

    /**
     * block 容器内的首期 inline 文本排版游标。
     */
    private static final class InlineLayoutContext {

        private final int baseLineLeft;
        private final int baseLineWidth;
        private final int baseLineHeight;
        private final int firstLineIndent;
        private final List<DocumentLayoutTextRun> textRuns;
        private final List<DocumentLayoutInlineFragment> inlineFragments;
        private final List<PendingInlineTextRun> pendingTextRuns = new ArrayList<PendingInlineTextRun>();
        private final List<PendingInlineFragment> pendingInlineFragments = new ArrayList<PendingInlineFragment>();
        private int lineLeft;
        private int lineWidth;
        private int lineTop;
        private int cursorLeft;
        private int maxBaselineTopEdge;
        private int maxBaselineBottomEdge;
        private int maxAlignedItemHeight;
        private boolean lineContentPresent;
        private boolean firstLineActive;

        private InlineLayoutContext(int lineLeft, int lineTop, int lineWidth, int lineHeight, int firstLineIndent,
                List<DocumentLayoutTextRun> textRuns, List<DocumentLayoutInlineFragment> inlineFragments) {
            this.baseLineLeft = lineLeft;
            this.baseLineWidth = Math.max(0, lineWidth);
            this.lineTop = lineTop;
            this.baseLineHeight = Math.max(1, lineHeight);
            this.firstLineIndent = firstLineIndent;
            this.textRuns = textRuns;
            this.inlineFragments = inlineFragments;
            this.firstLineActive = true;
            applyLineStart(true);
        }

        private void applyLineStart(boolean firstLine) {
            int indent = firstLine ? firstLineIndent : 0;
            lineLeft = baseLineLeft + indent;
            lineWidth = Math.max(0, baseLineWidth - indent);
            cursorLeft = lineLeft;
        }

        private int getLineWidth() {
            return lineWidth;
        }

        private int getCursorLeft() {
            return cursorLeft;
        }

        private int getLineTop() {
            return lineTop;
        }

        private int getRemainingWidth() {
            return Math.max(0, lineLeft + lineWidth - cursorLeft);
        }

        private boolean hasLineContent() {
            return lineContentPresent;
        }

        private void appendTextRun(TextNode textNode, ElementNode ownerElement, String text, int left, int width,
                List<InlineFragmentOwner> fragmentOwners) {
            if (width <= 0) {
                return;
            }
            InlineFragmentOwner innermostOwner = fragmentOwners.isEmpty() ? null
                    : fragmentOwners.get(fragmentOwners.size() - 1);
            pendingTextRuns.add(new PendingInlineTextRun(textNode, ownerElement, text, left, width,
                    innermostOwner));
        }

        private void appendInlineFragment(InlineFragmentOwner owner, int left, int width) {
            if (width <= 0) {
                return;
            }
            pendingInlineFragments.add(new PendingInlineFragment(owner, left, width));
            // #6 修复：inline 元素的垂直 padding/border 不影响行盒高度计算
            // 浏览器中 inline 元素的垂直 padding 只影响视觉渲染，不撑大行盒
            // 行盒高度仅由 line-height 和 vertical-align 决定
            // 因此这里不再将 topEdge/bottomEdge 计入行高
        }

        private void advance(int width) {
            cursorLeft += Math.max(0, width);
            lineContentPresent = true;
        }

        private void appendInlineBlock(int width, int height) {
            cursorLeft += Math.max(0, width);
            maxAlignedItemHeight = Math.max(maxAlignedItemHeight, Math.max(1, height));
            lineContentPresent = true;
        }

        private void nextLine() {
            breakLine(false);
        }

        private void forceLineBreak() {
            breakLine(true);
        }

        private void breakLine(boolean forceAdvanceIfEmpty) {
            int nextLineTop = lineContentPresent ? getFlowBottom()
                    : forceAdvanceIfEmpty ? lineTop + baseLineHeight : lineTop;
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
            int baselineHeight = maxBaselineTopEdge + baseLineHeight + maxBaselineBottomEdge;
            return Math.max(Math.max(baseLineHeight, baselineHeight), maxAlignedItemHeight);
        }

        private void flushCurrentLine() {
            if (!lineContentPresent) {
                return;
            }
            int lineHeight = getCurrentLineHeight();
            for (PendingInlineFragment pendingInlineFragment : pendingInlineFragments) {
                InlineFragmentOwner owner = pendingInlineFragment.owner;
                int itemHeight = owner.getHeight(baseLineHeight);
                int fragmentOffset = owner.verticalAlign == UiVerticalAlign.BASELINE
                        ? maxBaselineTopEdge - owner.topEdge
                        : resolveInlineVerticalOffset(owner.verticalAlign, lineHeight, itemHeight);
                int fragmentTop = lineTop + fragmentOffset;
                int fragmentHeight = pendingInlineFragment.owner.topEdge + baseLineHeight
                        + pendingInlineFragment.owner.bottomEdge;
                inlineFragments.add(new DocumentLayoutInlineFragment(pendingInlineFragment.owner.element,
                        pendingInlineFragment.left, fragmentTop, pendingInlineFragment.width, fragmentHeight));
            }
            for (PendingInlineTextRun pendingTextRun : pendingTextRuns) {
                int textTop = resolveInlineTextTop(lineHeight, pendingTextRun);
                textRuns.add(new DocumentLayoutTextRun(pendingTextRun.textNode, pendingTextRun.ownerElement,
                        pendingTextRun.text, pendingTextRun.left, textTop, pendingTextRun.width, baseLineHeight));
            }
        }

        private int resolveInlineTextTop(int lineHeight, PendingInlineTextRun pendingTextRun) {
            if (pendingTextRun.verticalAlign == UiVerticalAlign.BASELINE) {
                return lineTop + maxBaselineTopEdge;
            }
            int itemHeight = pendingTextRun.topEdge + baseLineHeight + pendingTextRun.bottomEdge;
            return lineTop + resolveInlineVerticalOffset(pendingTextRun.verticalAlign, lineHeight, itemHeight)
                    + pendingTextRun.topEdge;
        }

        private static int resolveInlineVerticalOffset(UiVerticalAlign verticalAlign, int lineHeight, int itemHeight) {
            int safeLineHeight = Math.max(1, lineHeight);
            int safeItemHeight = Math.max(1, itemHeight);
            int remaining = Math.max(0, safeLineHeight - safeItemHeight);
            if (verticalAlign == UiVerticalAlign.TOP) {
                return 0;
            }
            if (verticalAlign == UiVerticalAlign.MIDDLE) {
                return remaining / 2;
            }
            if (verticalAlign == UiVerticalAlign.BOTTOM) {
                return remaining;
            }
            return 0;
        }

        private void resetCurrentLineState() {
            firstLineActive = false;
            applyLineStart(false);
            maxBaselineTopEdge = 0;
            maxBaselineBottomEdge = 0;
            maxAlignedItemHeight = 0;
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
        private final UiVerticalAlign verticalAlign;

        private InlineFragmentOwner(ElementNode element, int topEdge, int bottomEdge, UiVerticalAlign verticalAlign) {
            this.element = element;
            this.topEdge = Math.max(0, topEdge);
            this.bottomEdge = Math.max(0, bottomEdge);
            this.verticalAlign = verticalAlign == null ? UiVerticalAlign.BASELINE : verticalAlign;
        }

        private int getHeight(int baseLineHeight) {
            return topEdge + Math.max(1, baseLineHeight) + bottomEdge;
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
        private final int topEdge;
        private final int bottomEdge;
        private final UiVerticalAlign verticalAlign;

        private PendingInlineTextRun(TextNode textNode, ElementNode ownerElement, String text, int left, int width,
                InlineFragmentOwner innermostOwner) {
            this.textNode = textNode;
            this.ownerElement = ownerElement;
            this.text = text;
            this.left = left;
            this.width = width;
            this.topEdge = innermostOwner == null ? 0 : innermostOwner.topEdge;
            this.bottomEdge = innermostOwner == null ? 0 : innermostOwner.bottomEdge;
            this.verticalAlign = innermostOwner == null ? UiVerticalAlign.BASELINE : innermostOwner.verticalAlign;
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

    static final class LayoutChildrenResult {

        final List<DocumentLayoutBox> children;
        final List<DocumentLayoutTextRun> textRuns;
        final List<DocumentLayoutInlineFragment> inlineFragments;
        final int contentHeight;

        LayoutChildrenResult(List<DocumentLayoutBox> children, List<DocumentLayoutTextRun> textRuns,
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
    static final class AbsoluteContainingBlock {

        final int left;
        final int top;
        final int width;
        final int height;
        final int verticalPadding;

        AbsoluteContainingBlock(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
            this.verticalPadding = 0;
        }

        AbsoluteContainingBlock(int left, int top, int width, int height, int verticalPadding) {
            this.left = left;
            this.top = top;
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
            this.verticalPadding = Math.max(0, verticalPadding);
        }

        static AbsoluteContainingBlock paddingBox(int left, int top, int width, int contentHeight,
                int verticalPadding) {
            int safeVerticalPadding = Math.max(0, verticalPadding);
            return new AbsoluteContainingBlock(left, top, width, Math.max(0, contentHeight) + safeVerticalPadding,
                    safeVerticalPadding);
        }

        AbsoluteContainingBlock withContentHeight(int contentHeight) {
            return new AbsoluteContainingBlock(left, top, width, Math.max(0, contentHeight) + verticalPadding,
                    verticalPadding);
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
