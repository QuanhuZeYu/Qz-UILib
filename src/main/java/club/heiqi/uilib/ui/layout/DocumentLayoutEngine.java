package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentImageElementSupport;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.selector.UiPseudoElement;
import club.heiqi.uilib.ui.style.values.UiPseudoElementContent;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
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
        LayoutContext layoutContext = new LayoutContext(Objects.requireNonNull(textMeasureService,
                "textMeasureService"), resolveLayoutValueResolver(layoutValueResolver));
        AbsoluteContainingBlock fixedContainingBlock = new AbsoluteContainingBlock(0, 0, safeViewportWidth,
                safeViewportHeight);
        return layoutElement(rootElement, 0, 0, safeViewportWidth, safeViewportHeight, AUTO_SIZE, AUTO_SIZE,
                null, fixedContainingBlock, layoutContext);
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
        LayoutContext layoutContext = new LayoutContext(resolvedTextMeasureService, resolvedLayoutValueResolver);
        int safeViewportWidth = Math.max(0, viewportWidth);
        int safeViewportHeight = Math.max(0, viewportHeight);
        ComputedStyle rootStyle = layoutContext.computeStyle(rootElement);
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
                forcedContentHeight, null, fixedContainingBlock, layoutContext);
    }

    static DocumentLayoutBox layoutElement(ElementNode element, int containingLeft, int flowTop,
            int containingWidth, int containingHeight, int forcedContentWidth, int forcedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            LayoutContext layoutContext) {
        ComputedStyle computedStyle = layoutContext.computeStyle(element);
        if (computedStyle.getDisplay() == UiDisplay.NONE) {
            return new DocumentLayoutBox(element, computedStyle, Collections.<DocumentLayoutBox>emptyList(),
                    Collections.<DocumentLayoutTextRun>emptyList(), Collections.<DocumentLayoutInlineFragment>emptyList(),
                    DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), containingLeft,
                    flowTop, 0, 0, 0, 0);
        }

        DocumentLayoutEdges margin = resolveMarginInsets(element, computedStyle, containingWidth,
                layoutContext.layoutValueResolver);
        DocumentLayoutEdges border = resolveBorderInsets(computedStyle, containingWidth);
        DocumentLayoutEdges padding = resolvePaddingInsets(element, computedStyle, containingWidth,
                layoutContext.layoutValueResolver);

        int availableBorderBoxWidth = Math.max(0, containingWidth - margin.getHorizontal());
        int autoContentWidth = Math.max(0, availableBorderBoxWidth - border.getHorizontal() - padding.getHorizontal());
        int contentWidth = resolveContentWidth(element, computedStyle, containingWidth, autoContentWidth,
                forcedContentWidth, border, padding, layoutContext);
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
                containingHeight, layoutContext.layoutValueResolver);
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
                    fixedContainingBlock, layoutContext);
        } else if (computedStyle.getDisplay() == UiDisplay.TABLE) {
            childrenResult = layoutTableChildren(element, computedStyle, contentLeft, contentTop, contentWidth,
                    specifiedContentHeight, childrenAbsoluteContainingBlock, createsAbsoluteContainingBlock,
                    fixedContainingBlock, layoutContext);
        } else {
            childrenResult = layoutBlockChildren(element, contentLeft, contentTop, contentWidth, specifiedContentHeight,
                    childrenAbsoluteContainingBlock, createsAbsoluteContainingBlock, fixedContainingBlock,
                    layoutContext);
        }

        int autoContentHeight = childrenResult.contentHeight;
        int contentHeight = resolveContentHeight(element, computedStyle, forcedContentHeight, autoContentHeight,
                contentWidth, containingHeight, layoutContext.layoutValueResolver);
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
            LayoutContext layoutContext) {
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        List<DocumentLayoutTextRun> textRuns = new ArrayList<DocumentLayoutTextRun>();
        List<DocumentLayoutInlineFragment> inlineFragments = new ArrayList<DocumentLayoutInlineFragment>();
        List<ElementNode> absoluteChildren = new ArrayList<ElementNode>();
        List<ElementNode> fixedChildren = new ArrayList<ElementNode>();
        boolean usesInlineFormatting = InlineLayoutHelper.hasVisibleInlineElementChild(element, layoutContext);
        ComputedStyle elementStyle = layoutContext.computeStyle(element);
        int textIndent = TextLayoutHelper.resolveTextIndent(elementStyle, contentWidth);
        InlineLayoutHelper.InlineLayoutContext inlineLayoutContext = usesInlineFormatting
                ? new InlineLayoutHelper.InlineLayoutContext(contentLeft, contentTop, contentWidth,
                        TextLayoutHelper.resolveTextLineHeight(layoutContext.textMeasureService, elementStyle), textIndent,
                        textRuns, inlineFragments)
                : null;
        int childFlowTop = contentTop;
        boolean textIndentPending = true;
        for (DocumentNode child : getGeneratedChildNodes(element, layoutContext)) {
            if (child instanceof TextNode) {
                if (usesInlineFormatting) {
                    InlineLayoutHelper.appendInlineTextRun((TextNode) child, element, inlineLayoutContext,
                            layoutContext);
                    childFlowTop = inlineLayoutContext.getFlowBottom();
                } else {
                    TextNode textNode = (TextNode) child;
                    int firstLineIndent = textIndentPending ? textIndent : 0;
                    childFlowTop = InlineLayoutHelper.appendTextRun(textNode, element, elementStyle, textRuns,
                            contentLeft, childFlowTop, contentWidth, firstLineIndent, layoutContext.textMeasureService);
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
            ComputedStyle childStyle = layoutContext.computeStyle(childElement);
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
                InlineLayoutHelper.appendInlineElementTextRuns(childElement, inlineLayoutContext, layoutContext);
                childFlowTop = inlineLayoutContext.getFlowBottom();
                continue;
            }
            if (usesInlineFormatting && childStyle.getDisplay() == UiDisplay.INLINE_BLOCK) {
                DocumentLayoutBox measuredChildBox = layoutElement(childElement, 0, 0, contentWidth,
                        specifiedContentHeight, AUTO_SIZE, AUTO_SIZE, absoluteContainingBlock, fixedContainingBlock,
                        layoutContext);
                int measuredOuterWidth = measuredChildBox.getWidth() + measuredChildBox.getMargin().getHorizontal();
                if (inlineLayoutContext.hasLineContent()
                        && measuredOuterWidth > inlineLayoutContext.getRemainingWidth()) {
                    childFlowTop = inlineLayoutContext.finishLineAndGetBottom();
                    inlineLayoutContext.reset(childFlowTop);
                }
                DocumentLayoutBox childBox = layoutElement(childElement, inlineLayoutContext.getCursorLeft(),
                        inlineLayoutContext.getLineTop(), contentWidth, specifiedContentHeight, AUTO_SIZE, AUTO_SIZE,
                        absoluteContainingBlock, fixedContainingBlock, layoutContext);
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
            int childMarginTop = resolveMarginInsets(childElement, childStyle, contentWidth,
                    layoutContext.layoutValueResolver).getTop();
            int collapsedMargin = Math.max(previousMarginBottom, childMarginTop);
            int marginCollapseAdjustment = childBoxes.isEmpty() ? 0
                    : Math.min(previousMarginBottom, childMarginTop);
            int adjustedFlowTop = childFlowTop - marginCollapseAdjustment;
            DocumentLayoutBox childBox = layoutElement(childElement, contentLeft, adjustedFlowTop, contentWidth,
                    specifiedContentHeight, AUTO_SIZE, AUTO_SIZE, absoluteContainingBlock, fixedContainingBlock,
                    layoutContext);
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
        PositionedLayoutHelper.appendAbsoluteChildren(childBoxes, absoluteChildren,
                PositionedLayoutHelper.resolveDirectAbsoluteContainingBlock(
                        absoluteContainingBlock, createsAbsoluteContainingBlock, specifiedContentHeight, contentHeight),
                fixedContainingBlock, layoutContext);
        PositionedLayoutHelper.appendFixedChildren(childBoxes, fixedChildren, fixedContainingBlock, layoutContext);
        return new LayoutChildrenResult(sortByDocumentChildOrder(element, childBoxes), textRuns,
                InlineLayoutHelper.markInlineFragmentSequence(InlineLayoutHelper.mergeInlineFragments(inlineFragments)),
                contentHeight);
    }

    /**
     * 委托 table 布局到 {@link TableLayoutHelper}。
     */
    static LayoutChildrenResult layoutTableChildren(ElementNode element, ComputedStyle tableStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, boolean createsAbsoluteContainingBlock,
            AbsoluteContainingBlock fixedContainingBlock, LayoutContext layoutContext) {
        return TableLayoutHelper.layoutTableChildren(element, tableStyle, contentLeft, contentTop, contentWidth,
                specifiedContentHeight, absoluteContainingBlock, createsAbsoluteContainingBlock,
                fixedContainingBlock, layoutContext);
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

    /**
     * 委托 flex 布局到 {@link FlexLayoutHelper}。
     */
    private static LayoutChildrenResult layoutFlexChildren(ElementNode element, ComputedStyle parentStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, boolean createsAbsoluteContainingBlock,
            AbsoluteContainingBlock fixedContainingBlock, LayoutContext layoutContext) {
        return FlexLayoutHelper.layoutFlexChildren(element, parentStyle, contentLeft, contentTop, contentWidth,
                specifiedContentHeight, absoluteContainingBlock, createsAbsoluteContainingBlock,
                fixedContainingBlock, layoutContext);
    }

    /**
     * 递归测量元素内容的固有宽度。
     */
    static int measureIntrinsicContentWidth(ElementNode element, int containingWidth, LayoutContext layoutContext) {
        ComputedStyle style = layoutContext.computeStyle(element);
        if (DocumentImageElementSupport.isImageTag(element.getTagName())) {
            return DocumentImageElementSupport.resolveIntrinsicWidth(element);
        }
        if (style.getDisplay() == UiDisplay.FLEX) {
            return FlexLayoutHelper.measureIntrinsicFlexContentWidth(element, style, containingWidth, layoutContext);
        }

        int maxWidth = 0;
        int inlineWidth = 0;
        for (DocumentNode child : getGeneratedChildNodes(element, layoutContext)) {
            if (child instanceof TextNode) {
                TextNode textNode = (TextNode) child;
                inlineWidth += TextLayoutHelper.measureIntrinsicTextWidth(textNode, style,
                        layoutContext.textMeasureService);
                continue;
            }
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = layoutContext.computeStyle(childElement);
            if (childStyle.getDisplay() == UiDisplay.NONE || isOutOfFlowPositioned(childStyle)) {
                continue;
            }
            if (isInlineFormattingDisplay(childStyle.getDisplay())) {
                inlineWidth += measureIntrinsicOuterWidth(childElement, childStyle, containingWidth, layoutContext);
                continue;
            }
            maxWidth = Math.max(maxWidth, inlineWidth);
            inlineWidth = 0;
            maxWidth = Math.max(maxWidth, measureIntrinsicOuterWidth(childElement, childStyle, containingWidth,
                    layoutContext));
        }
        return Math.max(maxWidth, inlineWidth);
    }

    static int measureIntrinsicOuterWidth(ElementNode element, ComputedStyle style, int containingWidth,
            LayoutContext layoutContext) {
        DocumentLayoutEdges margin = resolveMarginInsets(element, style, containingWidth,
                layoutContext.layoutValueResolver);
        DocumentLayoutEdges border = resolveBorderInsets(style, containingWidth);
        DocumentLayoutEdges padding = resolvePaddingInsets(element, style, containingWidth,
                layoutContext.layoutValueResolver);
        int contentWidth;
        if (isAuto(style.getWidth())) {
            contentWidth = measureIntrinsicContentWidth(element, containingWidth, layoutContext);
        } else {
            int baseWidth = Math.max(0, style.getWidth().resolve(containingWidth, 0));
            contentWidth = Math.max(0, layoutContext.layoutValueResolver.resolve(element,
                    DocumentAnimationProperty.WIDTH, baseWidth));
            contentWidth = resolveBoxSizingContentWidth(style, contentWidth, border, padding);
        }
        return margin.getHorizontal() + border.getHorizontal() + padding.getHorizontal() + contentWidth;
    }

    private static int resolveContentWidth(ElementNode element, ComputedStyle computedStyle, int containingWidth,
            int autoContentWidth, int forcedContentWidth, DocumentLayoutEdges border, DocumentLayoutEdges padding,
            LayoutContext layoutContext) {
        if (forcedContentWidth >= 0) {
            return forcedContentWidth;
        }
        UiStyleLength width = computedStyle.getWidth();
        if (isAuto(width) && DocumentImageElementSupport.isImageTag(element.getTagName())) {
            return DocumentImageElementSupport.resolveContentWidth(element, computedStyle, containingWidth,
                    autoContentWidth);
        }
        int autoFallback = computedStyle.getDisplay() == UiDisplay.INLINE_BLOCK
                ? Math.min(measureIntrinsicContentWidth(element, containingWidth, layoutContext), autoContentWidth)
                : autoContentWidth;
        int baseWidth = Math.max(0, width.resolve(containingWidth, autoFallback));
        int resolvedWidth = Math.max(0, layoutContext.layoutValueResolver.resolve(element,
                DocumentAnimationProperty.WIDTH, baseWidth));
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

    static List<ElementNode> getVisibleInFlowElementChildren(ElementNode element, LayoutContext layoutContext) {
        return getVisibleElementChildren(element, layoutContext).inFlowChildren;
    }

    static List<ElementNode> getVisibleAbsoluteElementChildren(ElementNode element, LayoutContext layoutContext) {
        return getVisibleElementChildren(element, layoutContext).absoluteChildren;
    }

    static List<ElementNode> getVisibleFixedElementChildren(ElementNode element, LayoutContext layoutContext) {
        return getVisibleElementChildren(element, layoutContext).fixedChildren;
    }

    static VisibleElementChildren getVisibleElementChildren(ElementNode element, LayoutContext layoutContext) {
        return layoutContext.getVisibleElementChildren(element);
    }

    private static VisibleElementChildren createVisibleElementChildren(ElementNode element,
            LayoutContext layoutContext) {
        List<DocumentNode> generatedChildren = getGeneratedChildNodes(element, layoutContext);
        List<ElementNode> inFlowChildren = new ArrayList<ElementNode>(generatedChildren.size());
        List<ElementNode> absoluteChildren = new ArrayList<ElementNode>();
        List<ElementNode> fixedChildren = new ArrayList<ElementNode>();
        for (DocumentNode child : generatedChildren) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = layoutContext.computeStyle(childElement);
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
            inFlowChildren.add(childElement);
        }
        return new VisibleElementChildren(inFlowChildren, absoluteChildren, fixedChildren);
    }

    static List<DocumentLayoutBox> sortByDocumentChildOrder(final ElementNode parentElement,
            List<DocumentLayoutBox> childBoxes) {
        if (childBoxes.size() <= 1) {
            return childBoxes;
        }
        List<DocumentChildOrderEntry> orderedBoxes = new ArrayList<DocumentChildOrderEntry>(childBoxes.size());
        for (DocumentLayoutBox childBox : childBoxes) {
            orderedBoxes.add(new DocumentChildOrderEntry(childBox,
                    getChildOrder(parentElement, childBox.getElement())));
        }
        Collections.sort(orderedBoxes, new Comparator<DocumentChildOrderEntry>() {
            @Override
            public int compare(DocumentChildOrderEntry first, DocumentChildOrderEntry second) {
                return Integer.compare(first.documentOrder, second.documentOrder);
            }
        });
        List<DocumentLayoutBox> sortedBoxes = new ArrayList<DocumentLayoutBox>(orderedBoxes.size());
        for (DocumentChildOrderEntry orderedBox : orderedBoxes) {
            sortedBoxes.add(orderedBox.layoutBox);
        }
        return sortedBoxes;
    }

    /**
     * 文档顺序排序预计算项，避免 Comparator 中反复扫描父节点 children。
     */
    private static final class DocumentChildOrderEntry {

        private final DocumentLayoutBox layoutBox;
        private final int documentOrder;

        private DocumentChildOrderEntry(DocumentLayoutBox layoutBox, int documentOrder) {
            this.layoutBox = layoutBox;
            this.documentOrder = documentOrder;
        }
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

    static List<DocumentNode> getGeneratedChildNodes(ElementNode element, LayoutContext layoutContext) {
        return layoutContext.getGeneratedChildNodes(element);
    }

    private static List<DocumentNode> createGeneratedChildNodes(ElementNode element, LayoutContext layoutContext) {
        if (element.isPseudoElement()) {
            return element.getChildren();
        }
        List<DocumentNode> sourceChildren = element.getChildren();
        List<DocumentNode> generatedNodes = new ArrayList<DocumentNode>(sourceChildren.size() + 2);
        ElementNode before = createGeneratedPseudoElement(element, UiPseudoElement.BEFORE, layoutContext);
        if (before != null) {
            generatedNodes.add(before);
        }
        generatedNodes.addAll(sourceChildren);
        ElementNode after = createGeneratedPseudoElement(element, UiPseudoElement.AFTER, layoutContext);
        if (after != null) {
            generatedNodes.add(after);
        }
        return generatedNodes;
    }

    private static ElementNode createGeneratedPseudoElement(ElementNode originElement, UiPseudoElement pseudoElement,
            LayoutContext layoutContext) {
        if (originElement == null || originElement.isPseudoElement()) {
            return null;
        }
        ElementNode pseudoNode = originElement.getOwnerDocument().__createPseudoElementRuntime(originElement,
                pseudoElement);
        ComputedStyle pseudoStyle = layoutContext.computeStyle(pseudoNode);
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

    /**
     * 单次布局 pass 的热路径上下文，集中复用样式与伪元素子节点计算结果。
     */
    static final class LayoutContext {

        final TextMeasureService textMeasureService;
        final LayoutRuntimeValueResolver layoutValueResolver;
        private final Map<ElementNode, ComputedStyle> styleCache = new IdentityHashMap<ElementNode, ComputedStyle>();
        private final Map<ElementNode, List<DocumentNode>> generatedChildNodesCache =
                new IdentityHashMap<ElementNode, List<DocumentNode>>();
        private final Map<ElementNode, VisibleElementChildren> visibleElementChildrenCache =
                new IdentityHashMap<ElementNode, VisibleElementChildren>();

        LayoutContext(TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
            this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
            this.layoutValueResolver = Objects.requireNonNull(layoutValueResolver, "layoutValueResolver");
        }

        ComputedStyle computeStyle(ElementNode element) {
            ComputedStyle cachedStyle = styleCache.get(element);
            if (cachedStyle != null) {
                return cachedStyle;
            }
            ComputedStyle parentStyle = resolveParentStyle(element);
            ComputedStyle computedStyle = UiStyleResolver.computeWithParentStyle(element, parentStyle);
            styleCache.put(element, computedStyle);
            return computedStyle;
        }

        List<DocumentNode> getGeneratedChildNodes(ElementNode element) {
            if (element.isPseudoElement()) {
                return element.getChildren();
            }
            List<DocumentNode> cachedNodes = generatedChildNodesCache.get(element);
            if (cachedNodes != null) {
                return cachedNodes;
            }
            List<DocumentNode> generatedNodes = createGeneratedChildNodes(element, this);
            generatedChildNodesCache.put(element, generatedNodes);
            return generatedNodes;
        }

        VisibleElementChildren getVisibleElementChildren(ElementNode element) {
            VisibleElementChildren cachedChildren = visibleElementChildrenCache.get(element);
            if (cachedChildren != null) {
                return cachedChildren;
            }
            VisibleElementChildren visibleChildren = createVisibleElementChildren(element, this);
            visibleElementChildrenCache.put(element, visibleChildren);
            return visibleChildren;
        }

        private ComputedStyle resolveParentStyle(ElementNode element) {
            if (element.isPseudoElement()) {
                ElementNode originElement = element.getPseudoOriginElement();
                return originElement == null ? null : computeStyle(originElement);
            }
            DocumentNode parent = element.getParent();
            return parent instanceof ElementNode ? computeStyle((ElementNode) parent) : null;
        }
    }

    /**
     * 可见元素子节点分桶结果，避免 flex/table 布局重复遍历同一批子节点。
     */
    static final class VisibleElementChildren {

        final List<ElementNode> inFlowChildren;
        final List<ElementNode> absoluteChildren;
        final List<ElementNode> fixedChildren;

        VisibleElementChildren(List<ElementNode> inFlowChildren, List<ElementNode> absoluteChildren,
                List<ElementNode> fixedChildren) {
            this.inFlowChildren = inFlowChildren;
            this.absoluteChildren = absoluteChildren;
            this.fixedChildren = fixedChildren;
        }
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
