package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
 * ancestor 时回退根 padding box；fixed 元素默认相对当前 HTML-like 视口定位，遇到 transform 祖先时相对该祖先
 * padding box 定位。当前已支持 positioned 元素在横向或纵向两侧 inset 同时存在且尺寸为 auto 时进行 stretch 求解，
 * 并支持包含 inline 元素的 text/span 初版混排和 inline fragment 盒边；更完整 inline box、多行 flex wrap
 * 和滚动布局会在后续阶段继续扩展。</p>
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

        /**
         * 判断当前运行态样式是否为 fixed 后代建立 containing block。
         *
         * @param element 元素
         * @param computedStyle computed style 基准值
         * @return 是否建立 fixed containing block
         */
        default boolean createsFixedContainingBlock(ElementNode element, ComputedStyle computedStyle) {
            return DocumentEffectChain.createsFixedContainingBlock(computedStyle);
        }
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
     * @param viewportHeight 视口高度；用作根元素初始 containingHeight 与 {@code position:fixed} 的固定 containing block 高度。
     *                       当前不会强制收缩根 border box；如需让根 border box 等于视口尺寸，请改用 {@link #layoutViewportRoot}。
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
     * @param viewportHeight 视口高度；用作根元素初始 containingHeight 与 {@code position:fixed} 的固定 containing block 高度。
     *                       当前不会强制收缩根 border box；如需让根 border box 等于视口尺寸，请改用 {@link #layoutViewportRoot}。
     * @param textMeasureService 文本测量服务
     * @param layoutValueResolver 运行态布局值解析器
     * @return 根布局盒
     */
    public static DocumentLayoutBox layout(ElementNode rootElement, int viewportWidth, int viewportHeight,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        return layout(rootElement, viewportWidth, viewportHeight, textMeasureService, layoutValueResolver, null);
    }

    /**
     * 使用上一轮布局盒作为候选缓存对根元素执行布局。
     *
     * @param rootElement 根元素
     * @param viewportWidth 视口宽度
     * @param viewportHeight 视口高度
     * @param textMeasureService 文本测量服务
     * @param layoutValueResolver 运行态布局值解析器
     * @param previousRootBox 上一轮根布局盒；为 null 时不启用子树复用
     * @return 根布局盒
     */
    public static DocumentLayoutBox layout(ElementNode rootElement, int viewportWidth, int viewportHeight,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver,
            DocumentLayoutBox previousRootBox) {
        Objects.requireNonNull(rootElement, "rootElement");
        int safeViewportWidth = Math.max(0, viewportWidth);
        int safeViewportHeight = Math.max(0, viewportHeight);
        LayoutContext layoutContext = new LayoutContext(Objects.requireNonNull(textMeasureService,
                "textMeasureService"), resolveLayoutValueResolver(layoutValueResolver), previousRootBox);
        AbsoluteContainingBlock fixedContainingBlock = new AbsoluteContainingBlock(0, 0, safeViewportWidth,
                safeViewportHeight);
        return layoutElement(rootElement, 0, 0, safeViewportWidth, safeViewportHeight, AUTO_SIZE, AUTO_SIZE,
                null, fixedContainingBlock, layoutContext);
    }

    /**
     * 按浏览器 top-layer 语义布局单个顶层元素。
     *
     * <p>顶层元素的 DOM 归属不变，但其布局 containing block 为当前 HTML-like 视口，不受原父级
     * overflow、stacking context 或滚动容器影响。</p>
     *
     * @param element 顶层元素
     * @param viewportWidth 视口宽度
     * @param viewportHeight 视口高度
     * @param textMeasureService 文本测量服务
     * @param layoutValueResolver 运行态布局值解析器
     * @return 顶层元素布局盒
     */
    public static DocumentLayoutBox layoutTopLayerElement(ElementNode element, int viewportWidth, int viewportHeight,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        Objects.requireNonNull(element, "element");
        int safeViewportWidth = Math.max(0, viewportWidth);
        int safeViewportHeight = Math.max(0, viewportHeight);
        LayoutContext layoutContext = new LayoutContext(Objects.requireNonNull(textMeasureService,
                "textMeasureService"), resolveLayoutValueResolver(layoutValueResolver));
        AbsoluteContainingBlock fixedContainingBlock = new AbsoluteContainingBlock(0, 0, safeViewportWidth,
                safeViewportHeight);
        return PositionedLayoutHelper.layoutPositionedElement(element, fixedContainingBlock, fixedContainingBlock,
                layoutContext);
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
        return layoutViewportRoot(rootElement, viewportWidth, viewportHeight, textMeasureService, layoutValueResolver,
                null);
    }

    /**
     * 使用上一轮布局盒作为候选缓存对视口根元素执行布局。
     *
     * @param rootElement 根元素
     * @param viewportWidth 视口宽度
     * @param viewportHeight 视口高度
     * @param textMeasureService 文本测量服务
     * @param layoutValueResolver 运行态布局值解析器
     * @param previousRootBox 上一轮根布局盒；为 null 时不启用子树复用
     * @return 根布局盒
     */
    public static DocumentLayoutBox layoutViewportRoot(ElementNode rootElement, int viewportWidth, int viewportHeight,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver,
            DocumentLayoutBox previousRootBox) {
        Objects.requireNonNull(rootElement, "rootElement");
        TextMeasureService resolvedTextMeasureService = Objects.requireNonNull(textMeasureService,
                "textMeasureService");
        LayoutRuntimeValueResolver resolvedLayoutValueResolver = resolveLayoutValueResolver(layoutValueResolver);
        LayoutContext layoutContext = new LayoutContext(resolvedTextMeasureService, resolvedLayoutValueResolver,
                previousRootBox);
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
        DocumentLayoutBox reusableBox = resolveReusableLayoutBox(element, containingLeft, flowTop, containingWidth,
                containingHeight, forcedContentWidth, forcedContentHeight, computedStyle, absoluteContainingBlock,
                fixedContainingBlock, layoutContext);
        if (reusableBox != null) {
            layoutContext.recordReusedLayoutSubtree();
            return reusableBox;
        }
        if (computedStyle.getDisplay() == UiDisplay.NONE) {
            return createLayoutBox(element, computedStyle, Collections.<DocumentLayoutBox>emptyList(),
                    Collections.<DocumentLayoutTextRun>emptyList(), Collections.<DocumentLayoutInlineFragment>emptyList(),
                    DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), containingLeft,
                    flowTop, 0, 0, 0, 0, 0, 0, 0, 0, layoutContext, containingLeft, flowTop, containingWidth,
                    containingHeight, forcedContentWidth, forcedContentHeight);
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
        int firstChildTopMargin = resolveCollapsibleFirstChildTopMargin(element, computedStyle, contentWidth,
                layoutContext);
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
        int collapsedTopMargin = collapseVerticalMargins(margin.getTop(), firstChildTopMargin);
        int marginTopAdjustment = allowsFirstChildTopMarginCollapse(computedStyle) ? firstChildTopMargin : 0;
        int borderBoxTop = flowTop + collapsedTopMargin;
        int contentLeft = borderBoxLeft + border.getLeft() + padding.getLeft();
        int contentTop = borderBoxTop + border.getTop() + padding.getTop();

        int specifiedContentHeight = resolveSpecifiedHeight(element, computedStyle, forcedContentHeight, contentWidth,
                containingHeight, layoutContext.layoutValueResolver);
        AbsoluteContainingBlock directContainingBlock = AbsoluteContainingBlock.paddingBox(
                borderBoxLeft + border.getLeft(), borderBoxTop + border.getTop(),
                contentWidth + padding.getHorizontal(), resolveInitialAbsoluteContainingBlockHeight(
                        specifiedContentHeight), padding.getVertical());
        boolean createsAbsoluteContainingBlock = absoluteContainingBlock == null || isPositioned(computedStyle);
        boolean createsFixedContainingBlock = layoutContext.layoutValueResolver.createsFixedContainingBlock(element,
                computedStyle);
        AbsoluteContainingBlock childrenAbsoluteContainingBlock = createsAbsoluteContainingBlock
                ? directContainingBlock : absoluteContainingBlock;
        AbsoluteContainingBlock childrenFixedContainingBlock = createsFixedContainingBlock
                ? directContainingBlock : fixedContainingBlock;
        LayoutChildrenResult childrenResult;
        if (computedStyle.getDisplay() == UiDisplay.FLEX) {
            childrenResult = layoutFlexChildren(element, computedStyle, contentLeft, contentTop, contentWidth,
                    specifiedContentHeight, childrenAbsoluteContainingBlock, createsAbsoluteContainingBlock,
                    childrenFixedContainingBlock, createsFixedContainingBlock, layoutContext);
        } else if (computedStyle.getDisplay() == UiDisplay.TABLE) {
            childrenResult = layoutTableChildren(element, computedStyle, contentLeft, contentTop, contentWidth,
                    specifiedContentHeight, childrenAbsoluteContainingBlock, createsAbsoluteContainingBlock,
                    childrenFixedContainingBlock, createsFixedContainingBlock, layoutContext);
        } else {
            childrenResult = layoutBlockChildren(element, contentLeft, contentTop, contentWidth, specifiedContentHeight,
                    childrenAbsoluteContainingBlock, createsAbsoluteContainingBlock, childrenFixedContainingBlock,
                    createsFixedContainingBlock, marginTopAdjustment, layoutContext);
        }

        int autoContentHeight = resolveNativeTextControlAutoContentHeight(element, computedStyle,
                childrenResult.contentHeight, layoutContext);
        int contentHeight = resolveContentHeight(element, computedStyle, forcedContentHeight, autoContentHeight,
                contentWidth, containingHeight, layoutContext.layoutValueResolver);
        if (isEmptyBlockWithCollapsibleOwnMargins(element, computedStyle, forcedContentHeight, autoContentHeight,
                border, padding, layoutContext)) {
            contentHeight = 0;
        }
        int borderBoxHeight = contentHeight + border.getVertical() + padding.getVertical();
        int resolvedTopInset = resolvePositionInsetValue(element, computedStyle.getTop(),
                DocumentAnimationProperty.TOP, containingHeight, layoutContext.layoutValueResolver);
        int resolvedRightInset = resolvePositionInsetValue(element, computedStyle.getRight(),
                DocumentAnimationProperty.RIGHT, containingWidth, layoutContext.layoutValueResolver);
        int resolvedBottomInset = resolvePositionInsetValue(element, computedStyle.getBottom(),
                DocumentAnimationProperty.BOTTOM, containingHeight, layoutContext.layoutValueResolver);
        int resolvedLeftInset = resolvePositionInsetValue(element, computedStyle.getLeft(),
                DocumentAnimationProperty.LEFT, containingWidth, layoutContext.layoutValueResolver);
        int positionOffsetX = resolveRelativeOffsetX(computedStyle, resolvedLeftInset, resolvedRightInset);
        int positionOffsetY = resolveRelativeOffsetY(computedStyle, resolvedTopInset, resolvedBottomInset);
        return createLayoutBox(element, computedStyle, childrenResult.children, childrenResult.textRuns,
                childrenResult.inlineFragments, margin, border, padding, borderBoxLeft, borderBoxTop, borderBoxWidth,
                borderBoxHeight, positionOffsetX, positionOffsetY, resolvedTopInset, resolvedRightInset,
                resolvedBottomInset, resolvedLeftInset, layoutContext, containingLeft, flowTop, containingWidth,
                containingHeight, forcedContentWidth, forcedContentHeight);
    }

    /**
     * 为原生文本输入控件提供浏览器式 auto 高度下限。
     */
    private static int resolveNativeTextControlAutoContentHeight(ElementNode element, ComputedStyle computedStyle,
            int autoContentHeight, LayoutContext layoutContext) {
        if (!"input".equals(element.getTagName()) || !isAuto(computedStyle.getHeight())) {
            return autoContentHeight;
        }
        int textLineHeight = TextLayoutHelper.resolveTextLineHeight(layoutContext.textMeasureService, computedStyle);
        return Math.max(autoContentHeight, textLineHeight);
    }

    private static DocumentLayoutBox createLayoutBox(ElementNode element, ComputedStyle computedStyle,
            List<DocumentLayoutBox> children, List<DocumentLayoutTextRun> textRuns,
            List<DocumentLayoutInlineFragment> inlineFragments, DocumentLayoutEdges margin, DocumentLayoutEdges border,
            DocumentLayoutEdges padding, int left, int top, int width, int height, int positionOffsetX,
            int positionOffsetY, int resolvedTopInset, int resolvedRightInset, int resolvedBottomInset,
            int resolvedLeftInset) {
        return createLayoutBox(element, computedStyle, children, textRuns, inlineFragments, margin, border, padding,
                left, top, width, height, positionOffsetX, positionOffsetY, resolvedTopInset, resolvedRightInset,
                resolvedBottomInset, resolvedLeftInset, null, AUTO_SIZE, AUTO_SIZE, AUTO_SIZE, AUTO_SIZE,
                AUTO_SIZE, AUTO_SIZE);
    }

    static DocumentLayoutBox createLayoutBox(ElementNode element, ComputedStyle computedStyle,
            List<DocumentLayoutBox> children, List<DocumentLayoutTextRun> textRuns,
            List<DocumentLayoutInlineFragment> inlineFragments, DocumentLayoutEdges margin, DocumentLayoutEdges border,
            DocumentLayoutEdges padding, int left, int top, int width, int height, int positionOffsetX,
            int positionOffsetY, int resolvedTopInset, int resolvedRightInset, int resolvedBottomInset,
            int resolvedLeftInset, LayoutContext layoutContext, int containingLeft, int flowTop, int containingWidth,
            int containingHeight, int forcedContentWidth, int forcedContentHeight) {
        int textMeasureEpoch = layoutContext == null ? -1 : layoutContext.textMeasureService.getEpoch();
        DocumentLayoutBox box = new DocumentLayoutBox(element, computedStyle, children, textRuns, inlineFragments,
                margin, border, padding, left, top, width, height, positionOffsetX, positionOffsetY, resolvedTopInset,
                resolvedRightInset, resolvedBottomInset, resolvedLeftInset, element.__getLayoutMutationVersion(),
                element.__getSubtreeLayoutMutationVersion(), textMeasureEpoch, containingLeft, flowTop, containingWidth,
                containingHeight, forcedContentWidth, forcedContentHeight);
        if (layoutContext != null && element.getParent() == null) {
            box.setLayoutPassReusedSubtreeCountForDiagnostics(layoutContext.getReusedLayoutSubtreeCount());
        }
        return box;
    }

    private static DocumentLayoutBox resolveReusableLayoutBox(ElementNode element, int containingLeft, int flowTop,
            int containingWidth, int containingHeight, int forcedContentWidth, int forcedContentHeight,
            ComputedStyle computedStyle, AbsoluteContainingBlock absoluteContainingBlock,
            AbsoluteContainingBlock fixedContainingBlock, LayoutContext layoutContext) {
        if (!layoutContext.canReuseLayoutSubtrees()) {
            return null;
        }
        if (element.getParent() == null) {
            return null;
        }
        if (!isReusableLayoutDisplay(computedStyle) || isOutOfFlowPositioned(computedStyle)
                || DocumentEffectChain.createsFixedContainingBlock(computedStyle)) {
            return null;
        }
        DocumentLayoutBox previousBox = layoutContext.getPreviousLayoutBox(element);
        if (previousBox == null || previousBox.getElement() != element || previousBox.containsOutOfFlowPositionedBox()) {
            return null;
        }
        if (previousBox.getLayoutMutationVersion() != element.__getLayoutMutationVersion()
                || previousBox.getSubtreeLayoutMutationVersion() != element.__getSubtreeLayoutMutationVersion()
                || previousBox.getLayoutTextMeasureEpoch() != layoutContext.textMeasureService.getEpoch()
                || previousBox.getLayoutContainingWidth() != containingWidth
                || previousBox.getLayoutContainingHeight() != containingHeight
                || previousBox.getLayoutForcedContentWidth() != forcedContentWidth
                || previousBox.getLayoutForcedContentHeight() != forcedContentHeight) {
            return null;
        }
        int deltaX = containingLeft - previousBox.getLayoutContainingLeft();
        int deltaY = flowTop - previousBox.getLayoutFlowTop();
        return previousBox.translatedTo(previousBox.getLeft() + deltaX, previousBox.getTop() + deltaY);
    }

    private static boolean isReusableLayoutDisplay(ComputedStyle computedStyle) {
        return computedStyle.getDisplay() == UiDisplay.BLOCK || computedStyle.getDisplay() == UiDisplay.FLEX
                || computedStyle.getDisplay() == UiDisplay.TABLE || computedStyle.getDisplay() == UiDisplay.INLINE_BLOCK
                || computedStyle.getDisplay() == UiDisplay.NONE;
    }

    private static LayoutChildrenResult layoutBlockChildren(ElementNode element, int contentLeft, int contentTop,
            int contentWidth, int specifiedContentHeight, AbsoluteContainingBlock absoluteContainingBlock,
            boolean createsAbsoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            boolean createsFixedContainingBlock, int firstChildTopMarginAdjustment,
            LayoutContext layoutContext) {
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        List<DocumentLayoutTextRun> textRuns = new ArrayList<DocumentLayoutTextRun>();
        List<DocumentLayoutInlineFragment> inlineFragments = new ArrayList<DocumentLayoutInlineFragment>();
        List<ElementNode> absoluteChildren = new ArrayList<ElementNode>();
        List<ElementNode> fixedChildren = new ArrayList<ElementNode>();
        boolean usesInlineFormatting = InlineLayoutHelper.hasVisibleInlineElementChild(element, layoutContext);
        ComputedStyle elementStyle = layoutContext.computeStyle(element);
        boolean parentAllowsFirstChildMarginCollapse = allowsFirstChildTopMarginCollapse(elementStyle);
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
            if (childElement.getOwnerDocument().__isTopLayerElement(childElement)) {
                continue;
            }
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
            boolean previousAllowsSiblingMarginCollapse = false;
            if (!childBoxes.isEmpty()) {
                DocumentLayoutBox previousBox = childBoxes.get(childBoxes.size() - 1);
                previousMarginBottom = previousBox.getMargin().getBottom();
                previousAllowsSiblingMarginCollapse = allowsSiblingMarginCollapse(previousBox.getComputedStyle());
            }
            int childMarginTop = resolveMarginInsets(childElement, childStyle, contentWidth,
                    layoutContext.layoutValueResolver).getTop();
            int childMarginBottom = resolveMarginInsets(childElement, childStyle, contentWidth,
                    layoutContext.layoutValueResolver).getBottom();
            boolean childEmptyBlockCollapsesOwnMargins = isEmptyBlockWithCollapsibleOwnMargins(childElement, childStyle,
                    AUTO_SIZE, AUTO_SIZE,
                    resolveBorderInsets(childStyle, contentWidth), resolvePaddingInsets(childElement, childStyle,
                            contentWidth, layoutContext.layoutValueResolver), layoutContext);
            if (childEmptyBlockCollapsesOwnMargins) {
                childMarginTop = collapseVerticalMargins(childMarginTop, childMarginBottom);
            }
            boolean childAllowsSiblingMarginCollapse = allowsSiblingMarginCollapse(childStyle);
            int marginCollapseAdjustment = 0;
            if (childBoxes.isEmpty()) {
                if (parentAllowsFirstChildMarginCollapse && childAllowsSiblingMarginCollapse) {
                    marginCollapseAdjustment = collapseVerticalMargins(firstChildTopMarginAdjustment, childMarginTop);
                }
            } else if (previousAllowsSiblingMarginCollapse && childAllowsSiblingMarginCollapse) {
                marginCollapseAdjustment = resolveSiblingMarginCollapseAdjustment(previousMarginBottom, childMarginTop);
            }
            int adjustedFlowTop = childFlowTop - marginCollapseAdjustment;
            DocumentLayoutBox childBox = layoutElement(childElement, contentLeft, adjustedFlowTop, contentWidth,
                    specifiedContentHeight, AUTO_SIZE, AUTO_SIZE, absoluteContainingBlock, fixedContainingBlock,
                    layoutContext);
            childBoxes.add(childBox);
            childFlowTop = childEmptyBlockCollapsesOwnMargins ? adjustedFlowTop + childMarginTop
                    : childBox.getMarginBoxBottom();
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
        PositionedLayoutHelper.appendFixedChildren(childBoxes, fixedChildren,
                PositionedLayoutHelper.resolveDirectAbsoluteContainingBlock(
                        fixedContainingBlock, createsFixedContainingBlock, specifiedContentHeight, contentHeight),
                layoutContext);
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
            AbsoluteContainingBlock fixedContainingBlock, boolean createsFixedContainingBlock,
            LayoutContext layoutContext) {
        return TableLayoutHelper.layoutTableChildren(element, tableStyle, contentLeft, contentTop, contentWidth,
                specifiedContentHeight, absoluteContainingBlock, createsAbsoluteContainingBlock,
                fixedContainingBlock, createsFixedContainingBlock, layoutContext);
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
            AbsoluteContainingBlock fixedContainingBlock, boolean createsFixedContainingBlock,
            LayoutContext layoutContext) {
        return FlexLayoutHelper.layoutFlexChildren(element, parentStyle, contentLeft, contentTop, contentWidth,
                specifiedContentHeight, absoluteContainingBlock, createsAbsoluteContainingBlock,
                fixedContainingBlock, createsFixedContainingBlock, layoutContext);
    }

    /**
     * 递归测量元素内容的固有宽度。
     */
    static int measureIntrinsicContentWidth(ElementNode element, int containingWidth, LayoutContext layoutContext) {
        Integer cachedWidth = layoutContext.getIntrinsicContentWidth(element, containingWidth);
        if (cachedWidth != null) {
            return cachedWidth.intValue();
        }
        int measuredWidth = computeIntrinsicContentWidth(element, containingWidth, layoutContext);
        layoutContext.putIntrinsicContentWidth(element, containingWidth, measuredWidth);
        return measuredWidth;
    }

    /**
     * 测量元素的 CSS-like min-content 内容宽度，用于 flex item 自动最小尺寸。
     */
    static int measureMinContentWidth(ElementNode element, int containingWidth, LayoutContext layoutContext) {
        ComputedStyle style = layoutContext.computeStyle(element);
        if (DocumentImageElementSupport.isImageTag(element.getTagName())) {
            return DocumentImageElementSupport.resolveIntrinsicWidth(element);
        }
        if (style.getDisplay() == UiDisplay.FLEX) {
            return FlexLayoutHelper.measureMinContentFlexWidth(element, style, containingWidth, layoutContext);
        }

        int maxWidth = 0;
        int inlineWidth = 0;
        for (DocumentNode child : getGeneratedChildNodes(element, layoutContext)) {
            if (child instanceof TextNode) {
                TextNode textNode = (TextNode) child;
                inlineWidth += TextLayoutHelper.measureMinContentTextWidth(textNode, style,
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
                inlineWidth += measureMinContentOuterWidth(childElement, childStyle, containingWidth, layoutContext);
                continue;
            }
            maxWidth = Math.max(maxWidth, inlineWidth);
            inlineWidth = 0;
            maxWidth = Math.max(maxWidth, measureMinContentOuterWidth(childElement, childStyle, containingWidth,
                    layoutContext));
        }
        return Math.max(maxWidth, inlineWidth);
    }

    private static int computeIntrinsicContentWidth(ElementNode element, int containingWidth,
            LayoutContext layoutContext) {
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
        Integer cachedWidth = layoutContext.getIntrinsicOuterWidth(element, containingWidth);
        if (cachedWidth != null) {
            return cachedWidth.intValue();
        }
        int measuredWidth = computeIntrinsicOuterWidth(element, style, containingWidth, layoutContext);
        layoutContext.putIntrinsicOuterWidth(element, containingWidth, measuredWidth);
        return measuredWidth;
    }

    private static int computeIntrinsicOuterWidth(ElementNode element, ComputedStyle style, int containingWidth,
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

    private static int measureMinContentOuterWidth(ElementNode element, ComputedStyle style, int containingWidth,
            LayoutContext layoutContext) {
        DocumentLayoutEdges margin = resolveMarginInsets(element, style, containingWidth,
                layoutContext.layoutValueResolver);
        DocumentLayoutEdges border = resolveBorderInsets(style, containingWidth);
        DocumentLayoutEdges padding = resolvePaddingInsets(element, style, containingWidth,
                layoutContext.layoutValueResolver);
        int contentWidth;
        if (isAuto(style.getWidth())) {
            contentWidth = measureMinContentWidth(element, containingWidth, layoutContext);
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
        int result = contentWidth;
        if (!isAuto(style.getMaxWidth())) {
            int maxW = style.getMaxWidth().resolve(containingWidth, Integer.MAX_VALUE);
            if (maxW >= 0) {
                maxW = resolveBoxSizingContentWidth(style, maxW, border, padding, true);
                result = Math.min(result, maxW);
            }
        }
        int minW = Math.max(0, style.getMinWidth().resolve(containingWidth, 0));
        minW = resolveBoxSizingContentWidth(style, minW, border, padding, true);
        result = Math.max(result, minW);
        return Math.max(0, result);
    }

    static int resolveBoxSizingContentWidth(ComputedStyle computedStyle, int resolvedWidth,
            DocumentLayoutEdges border, DocumentLayoutEdges padding) {
        return resolveBoxSizingContentWidth(computedStyle, resolvedWidth, border, padding,
                !isAuto(computedStyle.getWidth()));
    }

    static int resolveBoxSizingContentWidth(ComputedStyle computedStyle, int resolvedWidth,
            DocumentLayoutEdges border, DocumentLayoutEdges padding, boolean declaredBorderBoxSize) {
        if (computedStyle.getBoxSizing() != UiBoxSizing.BORDER_BOX || !declaredBorderBoxSize) {
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
        return resolveBoxSizingContentHeight(computedStyle, resolvedHeight, border, padding,
                !isAuto(computedStyle.getHeight()));
    }

    static int resolveBoxSizingContentHeight(ComputedStyle computedStyle, int resolvedHeight,
            DocumentLayoutEdges border, DocumentLayoutEdges padding, boolean declaredBorderBoxSize) {
        if (computedStyle.getBoxSizing() != UiBoxSizing.BORDER_BOX || !declaredBorderBoxSize) {
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
        int result = contentHeight;
        if (!isAuto(style.getMaxHeight())) {
            int maxH = resolveHeightConstraintValue(style.getMaxHeight(), containingHeight, Integer.MAX_VALUE);
            if (maxH >= 0 && maxH < Integer.MAX_VALUE) {
                maxH = resolveBoxSizingContentHeight(style, maxH, border, padding, true);
                result = Math.min(result, maxH);
            }
        }
        int minH = resolveHeightConstraintValue(style.getMinHeight(), containingHeight, 0);
        minH = resolveBoxSizingContentHeight(style, minH, border, padding, true);
        result = Math.max(result, minH);
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

    private static boolean allowsSiblingMarginCollapse(ComputedStyle style) {
        if (style == null) {
            return false;
        }
        if (style.getDisplay() == UiDisplay.INLINE || style.getDisplay() == UiDisplay.INLINE_BLOCK
                || style.getDisplay() == UiDisplay.FLEX || style.getDisplay() == UiDisplay.TABLE) {
            return false;
        }
        if (isOutOfFlowPositioned(style)) {
            return false;
        }
        return !createsBlockFormattingContext(style);
    }

    private static int resolveSiblingMarginCollapseAdjustment(int previousMarginBottom, int childMarginTop) {
        return previousMarginBottom + childMarginTop - collapseVerticalMargins(previousMarginBottom, childMarginTop);
    }

    private static int collapseVerticalMargins(int firstMargin, int secondMargin) {
        if (firstMargin >= 0 && secondMargin >= 0) {
            return Math.max(firstMargin, secondMargin);
        }
        if (firstMargin <= 0 && secondMargin <= 0) {
            return Math.min(firstMargin, secondMargin);
        }
        return firstMargin + secondMargin;
    }

    private static boolean allowsFirstChildTopMarginCollapse(ComputedStyle style) {
        if (!allowsSiblingMarginCollapse(style)) {
            return false;
        }
        return resolveBorderInsets(style, 0).getTop() == 0 && resolvePaddingInsets(null, style, 0,
                STATIC_LAYOUT_VALUE_RESOLVER).getTop() == 0;
    }

    private static int resolveCollapsibleFirstChildTopMargin(ElementNode element, ComputedStyle style, int contentWidth,
            LayoutContext layoutContext) {
        if (!allowsFirstChildTopMarginCollapse(style)) {
            return 0;
        }
        for (DocumentNode child : getGeneratedChildNodes(element, layoutContext)) {
            if (child instanceof TextNode) {
                String text = ((TextNode) child).getText();
                if (text != null && !text.trim().isEmpty()) {
                    return 0;
                }
                continue;
            }
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            if (childElement.getOwnerDocument().__isTopLayerElement(childElement)) {
                continue;
            }
            ComputedStyle childStyle = layoutContext.computeStyle(childElement);
            if (childStyle.getDisplay() == UiDisplay.NONE || isOutOfFlowPositioned(childStyle)) {
                continue;
            }
            if (!allowsSiblingMarginCollapse(childStyle)) {
                return 0;
            }
            int childTopMargin = resolveMarginInsets(childElement, childStyle, contentWidth,
                    layoutContext.layoutValueResolver).getTop();
            int childBottomMargin = resolveMarginInsets(childElement, childStyle, contentWidth,
                    layoutContext.layoutValueResolver).getBottom();
            int descendantTopMargin = resolveCollapsibleFirstChildTopMargin(childElement, childStyle, contentWidth,
                    layoutContext);
            int collapsedTopMargin = collapseVerticalMargins(childTopMargin, descendantTopMargin);
            if (isEmptyBlockWithCollapsibleOwnMargins(childElement, childStyle, AUTO_SIZE, AUTO_SIZE,
                    resolveBorderInsets(childStyle, contentWidth), resolvePaddingInsets(childElement, childStyle,
                            contentWidth, layoutContext.layoutValueResolver), layoutContext)) {
                collapsedTopMargin = collapseVerticalMargins(collapsedTopMargin, childBottomMargin);
            }
            return collapsedTopMargin;
        }
        return 0;
    }

    private static boolean isEmptyBlockWithCollapsibleOwnMargins(ElementNode element, ComputedStyle style,
            int forcedContentHeight, int autoContentHeight, DocumentLayoutEdges border, DocumentLayoutEdges padding,
            LayoutContext layoutContext) {
        if (!allowsSiblingMarginCollapse(style) || forcedContentHeight >= 0 || autoContentHeight > 0) {
            return false;
        }
        if (!isAuto(style.getHeight()) || hasAspectRatio(style) || border.getVertical() != 0
                || padding.getVertical() != 0) {
            return false;
        }
        for (DocumentNode child : getGeneratedChildNodes(element, layoutContext)) {
            if (child instanceof TextNode) {
                String text = ((TextNode) child).getText();
                if (text != null && !text.trim().isEmpty()) {
                    return false;
                }
                continue;
            }
            if (child instanceof ElementNode) {
                ComputedStyle childStyle = layoutContext.computeStyle((ElementNode) child);
                if (childStyle.getDisplay() != UiDisplay.NONE && !isOutOfFlowPositioned(childStyle)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean createsBlockFormattingContext(ComputedStyle style) {
        if (style == null) {
            return false;
        }
        UiDisplay display = style.getDisplay();
        if (display == UiDisplay.INLINE_BLOCK || display == UiDisplay.FLEX || display == UiDisplay.TABLE) {
            return true;
        }
        if (isOutOfFlowPositioned(style)) {
            return true;
        }
        return style.getOverflowX() != club.heiqi.uilib.ui.style.props.UiOverflow.VISIBLE
                || style.getOverflowY() != club.heiqi.uilib.ui.style.props.UiOverflow.VISIBLE;
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
        private final Map<ElementNode, DocumentLayoutBox> previousLayoutBoxCache =
                new IdentityHashMap<ElementNode, DocumentLayoutBox>();
        private final Map<ElementNode, ComputedStyle> styleCache = new IdentityHashMap<ElementNode, ComputedStyle>();
        private final Map<ElementNode, List<DocumentNode>> generatedChildNodesCache =
                new IdentityHashMap<ElementNode, List<DocumentNode>>();
        private final Map<ElementNode, VisibleElementChildren> visibleElementChildrenCache =
                new IdentityHashMap<ElementNode, VisibleElementChildren>();
        private final Map<ElementNode, Map<Integer, Integer>> intrinsicContentWidthCache =
                new IdentityHashMap<ElementNode, Map<Integer, Integer>>();
        private final Map<ElementNode, Map<Integer, Integer>> intrinsicOuterWidthCache =
                new IdentityHashMap<ElementNode, Map<Integer, Integer>>();
        private int reusedLayoutSubtreeCount;

        LayoutContext(TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
            this(textMeasureService, layoutValueResolver, null);
        }

        LayoutContext(TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver,
                DocumentLayoutBox previousRootBox) {
            this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
            this.layoutValueResolver = Objects.requireNonNull(layoutValueResolver, "layoutValueResolver");
            indexPreviousLayoutBox(previousRootBox);
        }

        boolean canReuseLayoutSubtrees() {
            return !previousLayoutBoxCache.isEmpty() && layoutValueResolver == STATIC_LAYOUT_VALUE_RESOLVER;
        }

        DocumentLayoutBox getPreviousLayoutBox(ElementNode element) {
            return previousLayoutBoxCache.get(element);
        }

        void recordReusedLayoutSubtree() {
            reusedLayoutSubtreeCount++;
        }

        int getReusedLayoutSubtreeCount() {
            return reusedLayoutSubtreeCount;
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

        Integer getIntrinsicContentWidth(ElementNode element, int containingWidth) {
            return getCachedIntrinsicWidth(intrinsicContentWidthCache, element, containingWidth);
        }

        void putIntrinsicContentWidth(ElementNode element, int containingWidth, int measuredWidth) {
            putCachedIntrinsicWidth(intrinsicContentWidthCache, element, containingWidth, measuredWidth);
        }

        Integer getIntrinsicOuterWidth(ElementNode element, int containingWidth) {
            return getCachedIntrinsicWidth(intrinsicOuterWidthCache, element, containingWidth);
        }

        void putIntrinsicOuterWidth(ElementNode element, int containingWidth, int measuredWidth) {
            putCachedIntrinsicWidth(intrinsicOuterWidthCache, element, containingWidth, measuredWidth);
        }

        private ComputedStyle resolveParentStyle(ElementNode element) {
            if (element.isPseudoElement()) {
                ElementNode originElement = element.getPseudoOriginElement();
                return originElement == null ? null : computeStyle(originElement);
            }
            DocumentNode parent = element.getParent();
            return parent instanceof ElementNode ? computeStyle((ElementNode) parent) : null;
        }

        private void indexPreviousLayoutBox(DocumentLayoutBox box) {
            if (box == null) {
                return;
            }
            previousLayoutBoxCache.put(box.getElement(), box);
            for (DocumentLayoutBox child : box.getChildren()) {
                indexPreviousLayoutBox(child);
            }
        }

        private static Integer getCachedIntrinsicWidth(Map<ElementNode, Map<Integer, Integer>> cache,
                ElementNode element, int containingWidth) {
            Map<Integer, Integer> widthCache = cache.get(element);
            return widthCache == null ? null : widthCache.get(Integer.valueOf(containingWidth));
        }

        private static void putCachedIntrinsicWidth(Map<ElementNode, Map<Integer, Integer>> cache,
                ElementNode element, int containingWidth, int measuredWidth) {
            Map<Integer, Integer> widthCache = cache.get(element);
            if (widthCache == null) {
                widthCache = new HashMap<Integer, Integer>();
                cache.put(element, widthCache);
            }
            widthCache.put(Integer.valueOf(containingWidth), Integer.valueOf(measuredWidth));
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

    private static int resolveRelativeOffsetX(ComputedStyle computedStyle, int resolvedLeftInset,
            int resolvedRightInset) {
        if (computedStyle.getPosition() != UiPosition.RELATIVE) {
            return 0;
        }
        if (!isAuto(computedStyle.getLeft())) {
            return resolvedLeftInset;
        }
        if (!isAuto(computedStyle.getRight())) {
            return -resolvedRightInset;
        }
        return 0;
    }

    private static int resolveRelativeOffsetY(ComputedStyle computedStyle, int resolvedTopInset,
            int resolvedBottomInset) {
        if (computedStyle.getPosition() != UiPosition.RELATIVE) {
            return 0;
        }
        if (!isAuto(computedStyle.getTop())) {
            return resolvedTopInset;
        }
        if (!isAuto(computedStyle.getBottom())) {
            return -resolvedBottomInset;
        }
        return 0;
    }

    static int resolvePositionInsetValue(ElementNode element, UiStyleLength inset,
            DocumentAnimationProperty property, int containingSize, LayoutRuntimeValueResolver layoutValueResolver) {
        if (isAuto(inset)) {
            return 0;
        }
        int baseValue = inset.resolve(Math.max(0, containingSize), 0);
        return layoutValueResolver.resolve(element, property, baseValue);
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
