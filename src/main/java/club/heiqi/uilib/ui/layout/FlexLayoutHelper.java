package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.AbsoluteContainingBlock;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.LayoutChildrenResult;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.LayoutRuntimeValueResolver;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiAlignSelf;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * Flex 布局辅助类。
 *
 * <p>从 {@link DocumentLayoutEngine} 提取的 flex 布局方法群，承担：</p>
 * <ul>
 *     <li>row / column 两种主轴方向布局（含 wrap 多行）</li>
 *     <li>flex-grow / flex-shrink / flex-basis 主轴空间分配</li>
 *     <li>justify-content / align-items / align-self 对齐与 auto margin 吸收</li>
 *     <li>order 重排（按 order 决定主轴布局顺序与盒树视觉顺序）</li>
 *     <li>flex 容器的固有宽度测量（用于嵌套 flex 的 auto 宽度推导）</li>
 * </ul>
 *
 * <p>本类通过 {@link FlexItem} 数据载体在分配阶段串联 ComputedStyle、margin/border/padding、
 * 主/交叉轴尺寸；几何落地仍走 {@code DocumentLayoutEngine.layoutElement(...)}。</p>
 */
final class FlexLayoutHelper {

    private FlexLayoutHelper() {}

    /**
     * 委托式入口：对 display:flex 容器执行子元素布局。
     */
    static LayoutChildrenResult layoutFlexChildren(ElementNode element, ComputedStyle parentStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, boolean createsAbsoluteContainingBlock,
            AbsoluteContainingBlock fixedContainingBlock, TextMeasureService textMeasureService,
            LayoutRuntimeValueResolver layoutValueResolver) {
        List<ElementNode> absoluteChildren = DocumentLayoutEngine.getVisibleAbsoluteElementChildren(element);
        List<ElementNode> fixedChildren = DocumentLayoutEngine.getVisibleFixedElementChildren(element);
        List<ElementNode> visibleChildren = sortElementsByFlexOrder(element,
                DocumentLayoutEngine.getVisibleInFlowElementChildren(element));
        LayoutChildrenResult flowResult;
        if (visibleChildren.isEmpty()) {
            flowResult = new LayoutChildrenResult(new ArrayList<DocumentLayoutBox>(),
                    new ArrayList<DocumentLayoutTextRun>(), new ArrayList<DocumentLayoutInlineFragment>(),
                    Math.max(0, specifiedContentHeight));
        } else if (parentStyle.getFlexDirection() == UiFlexDirection.COLUMN) {
            flowResult = layoutColumnFlexChildren(visibleChildren, parentStyle, contentLeft, contentTop, contentWidth,
                    specifiedContentHeight, absoluteContainingBlock, fixedContainingBlock, textMeasureService,
                    layoutValueResolver);
        } else {
            flowResult = layoutRowFlexChildren(visibleChildren, parentStyle, contentLeft, contentTop, contentWidth,
                    specifiedContentHeight, absoluteContainingBlock, fixedContainingBlock, textMeasureService,
                    layoutValueResolver);
        }
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>(flowResult.children);
        DocumentLayoutEngine.appendAbsoluteChildren(childBoxes, absoluteChildren,
                DocumentLayoutEngine.resolveDirectAbsoluteContainingBlock(absoluteContainingBlock,
                        createsAbsoluteContainingBlock, specifiedContentHeight, flowResult.contentHeight),
                fixedContainingBlock, textMeasureService, layoutValueResolver);
        DocumentLayoutEngine.appendFixedChildren(childBoxes, fixedChildren, fixedContainingBlock, textMeasureService,
                layoutValueResolver);
        return new LayoutChildrenResult(sortFlexChildBoxesByOrder(element, childBoxes), flowResult.textRuns,
                flowResult.inlineFragments, flowResult.contentHeight);
    }

    private static LayoutChildrenResult layoutRowFlexChildren(List<ElementNode> children, ComputedStyle parentStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        boolean wrap = parentStyle.getFlexWrap() == UiFlexWrap.WRAP;
        int gap = Math.max(0, parentStyle.getColumnGap().resolve(contentWidth, 0));
        int rowGap = Math.max(0, parentStyle.getRowGap().resolve(contentWidth, 0));

        // 第一步：按换行规则将 items 分成若干行
        List<List<FlexItem>> lines = new ArrayList<List<FlexItem>>();
        List<FlexItem> currentLine = new ArrayList<FlexItem>();
        int currentLineOccupied = 0;
        for (ElementNode child : children) {
            FlexItem item = createFlexItem(child, contentWidth, layoutValueResolver);
            item.contentMainSize = resolveContentMainSize(item, contentWidth, true, textMeasureService,
                    layoutValueResolver);
            int outerMain = item.getOuterMainSize(true);
            if (wrap && !currentLine.isEmpty()
                    && currentLineOccupied + gap + outerMain > contentWidth) {
                lines.add(currentLine);
                currentLine = new ArrayList<FlexItem>();
                currentLineOccupied = 0;
            }
            if (!currentLine.isEmpty()) {
                currentLineOccupied += gap;
            }
            currentLine.add(item);
            currentLineOccupied += outerMain;
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }

        // 第二步：对每行独立进行主轴空间分配与布局
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        int crossCursor = contentTop;
        int totalContentHeight = 0;

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            List<FlexItem> lineItems = lines.get(lineIndex);
            if (lineIndex > 0) {
                crossCursor += rowGap;
            }

            distributeMainSpace(lineItems, contentWidth, gap, true);

            // 测量每行各 item 的 cross size
            int lineCrossSize = 0;
            for (FlexItem item : lineItems) {
                item.box = DocumentLayoutEngine.layoutElement(item.element, 0, 0, contentWidth,
                        specifiedContentHeight, item.contentMainSize, DocumentLayoutEngine.AUTO_SIZE,
                        absoluteContainingBlock, fixedContainingBlock, textMeasureService, layoutValueResolver);
                lineCrossSize = Math.max(lineCrossSize, item.getOuterCrossSize(true));
            }

            // stretch 处理
            int lineAvailableCrossSize = specifiedContentHeight >= 0 && lines.size() == 1
                    ? specifiedContentHeight : lineCrossSize;
            for (FlexItem item : lineItems) {
                if (isItemCrossStretch(item.style.getAlignSelf(), parentStyle.getAlignItems())
                        && DocumentLayoutEngine.isAuto(item.style.getHeight())) {
                    item.forcedCrossSize = Math.max(0, lineAvailableCrossSize
                            - item.margin.getVertical() - item.border.getVertical() - item.padding.getVertical());
                    item.box = DocumentLayoutEngine.layoutElement(item.element, 0, 0, contentWidth,
                            lineAvailableCrossSize, item.contentMainSize, item.forcedCrossSize,
                            absoluteContainingBlock, fixedContainingBlock, textMeasureService, layoutValueResolver);
                    lineCrossSize = Math.max(lineCrossSize, item.getOuterCrossSize(true));
                }
            }
            if (specifiedContentHeight >= 0 && lines.size() == 1) {
                lineCrossSize = Math.max(lineCrossSize, lineAvailableCrossSize);
            }

            int occupiedMain = getOccupiedMainSize(lineItems, gap, true);
            int remaining = Math.max(0, contentWidth - occupiedMain);

            // #9 修复：auto margin 吸收主轴剩余空间，优先于 justify-content
            int totalAutoMarginMain = 0;
            for (FlexItem item : lineItems) {
                totalAutoMarginMain += item.getAutoMarginMainCount();
            }
            int autoMarginMainPerSlot = totalAutoMarginMain > 0 && remaining > 0
                    ? remaining / totalAutoMarginMain : 0;
            boolean hasAutoMargin = totalAutoMarginMain > 0;

            int dynamicGap = hasAutoMargin ? gap
                    : resolveDynamicGap(parentStyle.getJustifyContent(), gap, remaining, lineItems.size());
            int cursor = hasAutoMargin ? 0
                    : resolveLeadingOffset(parentStyle.getJustifyContent(), remaining)
                            + resolveLeadingOffsetForSpacing(parentStyle.getJustifyContent(), remaining,
                                    lineItems.size());

            for (FlexItem item : lineItems) {
                int outerCrossSize = item.getOuterCrossSize(true);
                int crossOffset = resolveItemCrossOffset(item.style.getAlignSelf(), parentStyle.getAlignItems(),
                        lineCrossSize, outerCrossSize);
                // auto margin 主轴起始侧
                int marginLeft = item.hasAutoMarginMainStart ? autoMarginMainPerSlot : item.margin.getLeft();
                int marginRight = item.hasAutoMarginMainEnd ? autoMarginMainPerSlot : item.margin.getRight();
                // auto margin 交叉轴（row 中 top/bottom）
                int marginTop = item.margin.getTop();
                int marginBottom = item.margin.getBottom();
                if (item.hasAutoMarginCrossStart || item.hasAutoMarginCrossEnd) {
                    int crossRemaining = Math.max(0, lineCrossSize - item.getOuterCrossSize(true));
                    int autoCount = (item.hasAutoMarginCrossStart ? 1 : 0) + (item.hasAutoMarginCrossEnd ? 1 : 0);
                    int autoMarginCross = autoCount > 0 ? crossRemaining / autoCount : 0;
                    marginTop = item.hasAutoMarginCrossStart ? autoMarginCross : item.margin.getTop();
                    marginBottom = item.hasAutoMarginCrossEnd ? autoMarginCross : item.margin.getBottom();
                    crossOffset = marginTop;
                }
                int borderLeft = contentLeft + cursor + marginLeft;
                int borderTop = crossCursor + crossOffset + marginTop;
                DocumentLayoutBox childBox = DocumentLayoutEngine.layoutElement(item.element, borderLeft - marginLeft,
                        borderTop - marginTop, contentWidth, lineAvailableCrossSize, item.contentMainSize,
                        item.forcedCrossSize, absoluteContainingBlock, fixedContainingBlock, textMeasureService,
                        layoutValueResolver);
                childBoxes.add(childBox);
                cursor += marginLeft + childBox.getWidth() + marginRight + dynamicGap;
            }
            crossCursor += lineCrossSize;
            totalContentHeight = crossCursor - contentTop;
        }

        int contentHeight;
        if (specifiedContentHeight >= 0) {
            contentHeight = specifiedContentHeight;
        } else {
            contentHeight = Math.max(0, totalContentHeight);
        }
        return new LayoutChildrenResult(childBoxes, new ArrayList<DocumentLayoutTextRun>(),
                new ArrayList<DocumentLayoutInlineFragment>(), contentHeight);
    }

    private static LayoutChildrenResult layoutColumnFlexChildren(List<ElementNode> children, ComputedStyle parentStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        List<FlexItem> items = new ArrayList<FlexItem>();
        for (ElementNode child : children) {
            FlexItem item = createFlexItem(child, contentWidth, false, layoutValueResolver);
            item.forcedCrossSize = resolveColumnCrossContentWidth(item, parentStyle.getAlignItems(),
                    parentStyle.getFlexWrap(), contentWidth, textMeasureService, layoutValueResolver);
            item.box = DocumentLayoutEngine.layoutElement(item.element, 0, 0, contentWidth, specifiedContentHeight,
                    item.forcedCrossSize, DocumentLayoutEngine.AUTO_SIZE, absoluteContainingBlock,
                    fixedContainingBlock, textMeasureService, layoutValueResolver);
            item.contentMainSize = resolveContentMainSize(item, contentWidth, false, textMeasureService,
                    layoutValueResolver);
            if (DocumentLayoutEngine.isAuto(item.style.getHeight())) {
                item.naturalContentMainSize = item.box.getContentHeight();
                item.contentMainSize = item.naturalContentMainSize;
            } else {
                item.naturalContentMainSize = item.contentMainSize;
            }
            item.minContentMainSize = resolveColumnFlexItemMinMainSize(item);
            items.add(item);
        }

        int gap = Math.max(0, parentStyle.getRowGap().resolve(contentWidth, 0));
        if (specifiedContentHeight >= 0) {
            distributeMainSpace(items, specifiedContentHeight, gap, false);
        }

        int occupiedMain = getOccupiedMainSize(items, gap, false);
        int contentHeight = specifiedContentHeight >= 0 ? specifiedContentHeight : occupiedMain;
        int remaining = Math.max(0, contentHeight - occupiedMain);

        // #9 修复：auto margin 吸收主轴剩余空间（column 中 top/bottom）
        int totalAutoMarginMain = 0;
        for (FlexItem item : items) {
            totalAutoMarginMain += item.getAutoMarginMainCount();
        }
        int autoMarginMainPerSlot = totalAutoMarginMain > 0 && remaining > 0
                ? remaining / totalAutoMarginMain : 0;
        boolean hasAutoMargin = totalAutoMarginMain > 0;

        int dynamicGap = hasAutoMargin ? gap
                : resolveDynamicGap(parentStyle.getJustifyContent(), gap, remaining, items.size());
        int cursor = hasAutoMargin ? 0
                : resolveLeadingOffset(parentStyle.getJustifyContent(), remaining)
                        + resolveLeadingOffsetForSpacing(parentStyle.getJustifyContent(), remaining, items.size());
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        int measuredContentBottom = contentTop;
        for (FlexItem item : items) {
            // auto margin 主轴（column 中 top/bottom）
            int marginTop = item.hasAutoMarginMainStart ? autoMarginMainPerSlot : item.margin.getTop();
            int marginBottom = item.hasAutoMarginMainEnd ? autoMarginMainPerSlot : item.margin.getBottom();
            // auto margin 交叉轴（column 中 left/right）
            int crossOffset = resolveItemCrossOffset(item.style.getAlignSelf(), parentStyle.getAlignItems(),
                    contentWidth, item.getOuterCrossSize(false));
            int marginLeft = item.margin.getLeft();
            int marginRight = item.margin.getRight();
            if (item.hasAutoMarginCrossStart || item.hasAutoMarginCrossEnd) {
                int crossRemaining = Math.max(0, contentWidth - item.getOuterCrossSize(false));
                int autoCount = (item.hasAutoMarginCrossStart ? 1 : 0) + (item.hasAutoMarginCrossEnd ? 1 : 0);
                int autoMarginCross = autoCount > 0 ? crossRemaining / autoCount : 0;
                marginLeft = item.hasAutoMarginCrossStart ? autoMarginCross : item.margin.getLeft();
                marginRight = item.hasAutoMarginCrossEnd ? autoMarginCross : item.margin.getRight();
                crossOffset = marginLeft;
            }
            int borderLeft = contentLeft + crossOffset + marginLeft;
            int borderTop = contentTop + cursor + marginTop;
            int forcedMainSize = shouldKeepAutoHeightInFinalColumnLayout(item, specifiedContentHeight)
                    ? DocumentLayoutEngine.AUTO_SIZE
                    : item.contentMainSize;
            DocumentLayoutBox childBox = DocumentLayoutEngine.layoutElement(item.element, borderLeft - marginLeft,
                    borderTop - marginTop, contentWidth, contentHeight, item.forcedCrossSize,
                    forcedMainSize, absoluteContainingBlock, fixedContainingBlock, textMeasureService,
                    layoutValueResolver);
            childBoxes.add(childBox);
            measuredContentBottom = Math.max(measuredContentBottom, childBox.getBottom() + marginBottom);
            cursor += marginTop + childBox.getHeight() + marginBottom + dynamicGap;
        }
        return new LayoutChildrenResult(childBoxes, new ArrayList<DocumentLayoutTextRun>(),
                new ArrayList<DocumentLayoutInlineFragment>(), specifiedContentHeight >= 0
                        ? contentHeight
                        : Math.max(0, measuredContentBottom - contentTop));
    }

    private static boolean shouldKeepAutoHeightInFinalColumnLayout(FlexItem item, int specifiedContentHeight) {
        if (!DocumentLayoutEngine.isAuto(item.style.getHeight())) {
            return false;
        }
        if (specifiedContentHeight < 0) {
            return true;
        }
        return item.contentMainSize == item.naturalContentMainSize;
    }

    private static int resolveColumnFlexItemMinMainSize(FlexItem item) {
        if (!DocumentLayoutEngine.isAuto(item.style.getHeight()) || item.style.getOverflowY() != UiOverflow.VISIBLE) {
            return 0;
        }
        return Math.max(0, item.naturalContentMainSize);
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
        int remainingOverflow = Math.max(0, overflow);
        while (remainingOverflow > 0) {
            float totalShrinkWeight = 0.0F;
            int shrinkableCount = 0;
            for (FlexItem item : items) {
                if (item.style.getFlexShrink() > 0.0F && item.contentMainSize > item.minContentMainSize) {
                    // #10 修复：shrink 权重应使用 flex-basis（初始主轴尺寸），非 auto 时用 flexBasis，否则用 contentMainSize
                    int basisSize = DocumentLayoutEngine.isAuto(item.style.getFlexBasis()) ? item.contentMainSize
                            : Math.max(0, item.style.getFlexBasis().resolve(0, item.contentMainSize));
                    totalShrinkWeight += item.style.getFlexShrink() * basisSize;
                    shrinkableCount++;
                }
            }
            if (totalShrinkWeight <= 0.0F || shrinkableCount <= 0) {
                return;
            }

            int removed = 0;
            int shrinkableIndex = 0;
            for (FlexItem item : items) {
                if (item.style.getFlexShrink() <= 0.0F || item.contentMainSize <= item.minContentMainSize) {
                    continue;
                }
                int pending = remainingOverflow - removed;
                if (pending <= 0) {
                    break;
                }
                int basisSize = DocumentLayoutEngine.isAuto(item.style.getFlexBasis()) ? item.contentMainSize
                        : Math.max(0, item.style.getFlexBasis().resolve(0, item.contentMainSize));
                shrinkableIndex++;
                int cut = shrinkableIndex == shrinkableCount ? pending
                        : Math.round(remainingOverflow * item.style.getFlexShrink() * basisSize
                                / totalShrinkWeight);
                int maxCut = Math.max(0, item.contentMainSize - item.minContentMainSize);
                cut = Math.max(0, Math.min(Math.min(cut, pending), maxCut));
                item.contentMainSize -= cut;
                removed += cut;
            }
            if (removed <= 0) {
                return;
            }
            remainingOverflow -= removed;
        }
    }

    private static int getOccupiedMainSize(List<FlexItem> items, int gap, boolean row) {
        int occupied = Math.max(0, items.size() - 1) * gap;
        for (FlexItem item : items) {
            occupied += item.getOuterMainSize(row);
        }
        return occupied;
    }

    private static int resolveContentMainSize(FlexItem item, int containingWidth, boolean row,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        // flex-basis 优先：非 auto 时用 flex-basis 作为主轴初始尺寸
        UiStyleLength flexBasis = item.style.getFlexBasis();
        if (!DocumentLayoutEngine.isAuto(flexBasis)) {
            int baseSize = Math.max(0, flexBasis.resolve(containingWidth, 0));
            if (row) {
                return DocumentLayoutEngine.resolveBoxSizingContentWidth(item.style, baseSize, item.border,
                        item.padding);
            }
            return DocumentLayoutEngine.resolveBoxSizingContentHeight(item.style, baseSize, item.border, item.padding);
        }
        // flex-basis:auto 退回 width/height
        UiStyleLength length = row ? item.style.getWidth() : item.style.getHeight();
        if (DocumentLayoutEngine.isAuto(length)) {
            if (row) {
                return measureAutoFlexAutoWidth(item.element, item.style, containingWidth, textMeasureService,
                        layoutValueResolver);
            }
            return 0;
        }
        int baseSize = Math.max(0, length.resolve(containingWidth, 0));
        DocumentAnimationProperty property = row ? DocumentAnimationProperty.WIDTH : DocumentAnimationProperty.HEIGHT;
        int resolvedSize = Math.max(0, layoutValueResolver.resolve(item.element, property, baseSize));
        if (row) {
            return DocumentLayoutEngine.resolveBoxSizingContentWidth(item.style, resolvedSize, item.border,
                    item.padding);
        }
        return DocumentLayoutEngine.resolveBoxSizingContentHeight(item.style, resolvedSize, item.border, item.padding);
    }

    /**
     * 测量 auto 宽度元素的内容宽，统一用于 flex row 主轴与 flex column 交叉轴。
     */
    private static int measureAutoFlexAutoWidth(ElementNode element, ComputedStyle style, int containingWidth,
            TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        int measuredWidth = DocumentLayoutEngine.measureIntrinsicContentWidth(element, textMeasureService,
                containingWidth, layoutValueResolver);
        if (measuredWidth <= 0) {
            return 0;
        }
        int availableContentWidth = Math.max(0, containingWidth
                - DocumentLayoutEngine.resolveMarginInsets(element, style, containingWidth, layoutValueResolver)
                        .getHorizontal()
                - DocumentLayoutEngine.resolveBorderInsets(style, containingWidth).getHorizontal()
                - DocumentLayoutEngine.resolvePaddingInsets(element, style, containingWidth, layoutValueResolver)
                        .getHorizontal());
        return Math.min(measuredWidth, availableContentWidth);
    }

    /**
     * 测量 flex 容器的固有宽度，供嵌套 flex 容器在父级 auto 宽度下推导内容宽。
     */
    static int measureIntrinsicFlexContentWidth(ElementNode element, ComputedStyle style,
            TextMeasureService textMeasureService, int containingWidth,
            LayoutRuntimeValueResolver layoutValueResolver) {
        List<ElementNode> children = sortElementsByFlexOrder(element,
                DocumentLayoutEngine.getVisibleInFlowElementChildren(element));
        if (children.isEmpty()) {
            return 0;
        }
        int gap = Math.max(0, (style.getFlexDirection() == UiFlexDirection.COLUMN
                ? style.getRowGap()
                : style.getColumnGap()).resolve(containingWidth, 0));
        int measuredWidth = 0;
        int itemIndex = 0;
        for (ElementNode child : children) {
            ComputedStyle childStyle = UiStyleResolver.compute(child);
            int childWidth = DocumentLayoutEngine.measureIntrinsicOuterWidth(child, childStyle, textMeasureService,
                    containingWidth, layoutValueResolver);
            if (style.getFlexDirection() == UiFlexDirection.COLUMN) {
                measuredWidth = Math.max(measuredWidth, childWidth);
            } else {
                if (itemIndex > 0) {
                    measuredWidth += gap;
                }
                measuredWidth += childWidth;
            }
            itemIndex++;
        }
        return measuredWidth;
    }

    private static int resolveColumnCrossContentWidth(FlexItem item, UiAlignItems alignItems, UiFlexWrap flexWrap,
            int contentWidth, TextMeasureService textMeasureService, LayoutRuntimeValueResolver layoutValueResolver) {
        if (!DocumentLayoutEngine.isAuto(item.style.getWidth())) {
            int baseWidth = Math.max(0, item.style.getWidth().resolve(contentWidth, 0));
            int resolvedWidth = Math.max(0, layoutValueResolver.resolve(item.element,
                    DocumentAnimationProperty.WIDTH, baseWidth));
            return DocumentLayoutEngine.resolveBoxSizingContentWidth(item.style, resolvedWidth, item.border,
                    item.padding);
        }
        // align-self 覆盖 align-items
        boolean stretch = isItemCrossStretch(item.style.getAlignSelf(), alignItems);
        if (stretch) {
            return Math.max(0, contentWidth - item.margin.getHorizontal() - item.border.getHorizontal()
                    - item.padding.getHorizontal());
        }
        return measureAutoFlexAutoWidth(item.element, item.style, contentWidth, textMeasureService,
                layoutValueResolver);
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
        if (justifyContent == UiJustifyContent.SPACE_AROUND && remaining > 0) {
            // space-around：首尾各留半个间距，leading = halfGap
            return 0; // 由 dynamicGap 处理，leading 在 resolveDynamicGap 中已含首尾
        }
        if (justifyContent == UiJustifyContent.SPACE_EVENLY && remaining > 0) {
            return 0; // 同上
        }
        return 0;
    }

    private static int resolveDynamicGap(UiJustifyContent justifyContent, int baseGap, int remaining, int itemCount) {
        if (itemCount <= 0 || remaining <= 0) {
            return baseGap;
        }
        if (justifyContent == UiJustifyContent.SPACE_BETWEEN && itemCount > 1) {
            return baseGap + remaining / (itemCount - 1);
        }
        if (justifyContent == UiJustifyContent.SPACE_AROUND) {
            // 每个 item 两侧各留相等间距：总间距 = remaining，分成 itemCount 份，每份两侧各半
            return baseGap + remaining / itemCount;
        }
        if (justifyContent == UiJustifyContent.SPACE_EVENLY) {
            // 每个间隙（含首尾）相等：共 itemCount+1 个间隙
            return baseGap + remaining / (itemCount + 1);
        }
        return baseGap;
    }

    /**
     * 解析 space-around / space-evenly 的首部偏移量。
     */
    private static int resolveLeadingOffsetForSpacing(UiJustifyContent justifyContent, int remaining, int itemCount) {
        if (remaining <= 0 || itemCount <= 0) {
            return 0;
        }
        if (justifyContent == UiJustifyContent.SPACE_AROUND) {
            return remaining / itemCount / 2;
        }
        if (justifyContent == UiJustifyContent.SPACE_EVENLY) {
            return remaining / (itemCount + 1);
        }
        return 0;
    }

    private static int resolveCrossOffset(UiAlignItems alignItems, int availableCrossSize, int itemOuterCrossSize) {
        int remaining = Math.max(0, availableCrossSize - itemOuterCrossSize);
        if (alignItems == UiAlignItems.CENTER) {
            return remaining / 2;
        }
        if (alignItems == UiAlignItems.END) {
            return remaining;
        }
        // BASELINE 暂时按 START 处理（完整 baseline 对齐需要收集每行基线偏移）
        return 0;
    }

    /**
     * 解析 flex item 的交叉轴对齐偏移，优先使用 item 自身的 align-self，AUTO 时退回父容器 align-items。
     */
    private static int resolveItemCrossOffset(UiAlignSelf alignSelf, UiAlignItems parentAlignItems,
            int availableCrossSize, int itemOuterCrossSize) {
        UiAlignItems effectiveAlign;
        if (alignSelf == UiAlignSelf.AUTO) {
            effectiveAlign = parentAlignItems;
        } else {
            switch (alignSelf) {
                case START:    effectiveAlign = UiAlignItems.START;   break;
                case CENTER:   effectiveAlign = UiAlignItems.CENTER;  break;
                case END:      effectiveAlign = UiAlignItems.END;     break;
                case STRETCH:  effectiveAlign = UiAlignItems.STRETCH; break;
                case BASELINE: effectiveAlign = UiAlignItems.BASELINE; break;
                default:       effectiveAlign = parentAlignItems;     break;
            }
        }
        return resolveCrossOffset(effectiveAlign, availableCrossSize, itemOuterCrossSize);
    }

    /**
     * 判断 flex item 是否应按 stretch 拉伸交叉轴（考虑 align-self 覆盖）。
     */
    private static boolean isItemCrossStretch(UiAlignSelf alignSelf, UiAlignItems parentAlignItems) {
        if (alignSelf == UiAlignSelf.AUTO) {
            return parentAlignItems == UiAlignItems.STRETCH;
        }
        return alignSelf == UiAlignSelf.STRETCH;
    }

    private static List<ElementNode> sortElementsByFlexOrder(final ElementNode parentElement,
            List<ElementNode> children) {
        List<ElementNode> sortedChildren = new ArrayList<ElementNode>(children);
        Collections.sort(sortedChildren, new Comparator<ElementNode>() {
            @Override
            public int compare(ElementNode first, ElementNode second) {
                int orderCompare = Integer.compare(UiStyleResolver.compute(first).getOrder(),
                        UiStyleResolver.compute(second).getOrder());
                if (orderCompare != 0) {
                    return orderCompare;
                }
                return Integer.compare(DocumentLayoutEngine.getChildOrder(parentElement, first),
                        DocumentLayoutEngine.getChildOrder(parentElement, second));
            }
        });
        return sortedChildren;
    }

    private static List<DocumentLayoutBox> sortFlexChildBoxesByOrder(final ElementNode parentElement,
            List<DocumentLayoutBox> childBoxes) {
        List<DocumentLayoutBox> sortedBoxes = new ArrayList<DocumentLayoutBox>(childBoxes);
        Collections.sort(sortedBoxes, new Comparator<DocumentLayoutBox>() {
            @Override
            public int compare(DocumentLayoutBox first, DocumentLayoutBox second) {
                int orderCompare = Integer.compare(first.getComputedStyle().getOrder(),
                        second.getComputedStyle().getOrder());
                if (orderCompare != 0) {
                    return orderCompare;
                }
                return Integer.compare(DocumentLayoutEngine.getChildOrder(parentElement, first.getElement()),
                        DocumentLayoutEngine.getChildOrder(parentElement, second.getElement()));
            }
        });
        return sortedBoxes;
    }

    private static FlexItem createFlexItem(ElementNode element, int containingWidth,
            LayoutRuntimeValueResolver layoutValueResolver) {
        return createFlexItem(element, containingWidth, true, layoutValueResolver);
    }

    private static FlexItem createFlexItem(ElementNode element, int containingWidth, boolean row,
            LayoutRuntimeValueResolver layoutValueResolver) {
        ComputedStyle style = UiStyleResolver.compute(element);
        return new FlexItem(element, style,
                DocumentLayoutEngine.resolveMarginInsets(element, style, containingWidth, layoutValueResolver),
                DocumentLayoutEngine.resolveBorderInsets(style, containingWidth),
                DocumentLayoutEngine.resolvePaddingInsets(element, style, containingWidth, layoutValueResolver), row);
    }

    /**
     * Flex item 数据载体：承载 ComputedStyle、margin/border/padding、主/交叉轴运行尺寸与 auto margin 标记。
     */
    private static final class FlexItem {

        private final ElementNode element;
        private final ComputedStyle style;
        private final DocumentLayoutEdges margin;
        private final DocumentLayoutEdges border;
        private final DocumentLayoutEdges padding;
        /** 主轴方向是否有 auto margin（row: left/right；column: top/bottom）。 */
        private final boolean hasAutoMarginMainStart;
        private final boolean hasAutoMarginMainEnd;
        /** 交叉轴方向是否有 auto margin（row: top/bottom；column: left/right）。 */
        private final boolean hasAutoMarginCrossStart;
        private final boolean hasAutoMarginCrossEnd;
        private int contentMainSize;
        private int naturalContentMainSize = DocumentLayoutEngine.AUTO_SIZE;
        private int minContentMainSize;
        private int forcedCrossSize = DocumentLayoutEngine.AUTO_SIZE;
        private DocumentLayoutBox box;

        private FlexItem(ElementNode element, ComputedStyle style, DocumentLayoutEdges margin,
                DocumentLayoutEdges border, DocumentLayoutEdges padding, boolean row) {
            this.element = element;
            this.style = style;
            this.margin = margin;
            this.border = border;
            this.padding = padding;
            UiStyleInsets rawMargin = style.getMargin();
            if (row) {
                this.hasAutoMarginMainStart = DocumentLayoutEngine.isAuto(rawMargin.getLeft());
                this.hasAutoMarginMainEnd = DocumentLayoutEngine.isAuto(rawMargin.getRight());
                this.hasAutoMarginCrossStart = DocumentLayoutEngine.isAuto(rawMargin.getTop());
                this.hasAutoMarginCrossEnd = DocumentLayoutEngine.isAuto(rawMargin.getBottom());
            } else {
                this.hasAutoMarginMainStart = DocumentLayoutEngine.isAuto(rawMargin.getTop());
                this.hasAutoMarginMainEnd = DocumentLayoutEngine.isAuto(rawMargin.getBottom());
                this.hasAutoMarginCrossStart = DocumentLayoutEngine.isAuto(rawMargin.getLeft());
                this.hasAutoMarginCrossEnd = DocumentLayoutEngine.isAuto(rawMargin.getRight());
            }
        }

        private int getAutoMarginMainCount() {
            return (hasAutoMarginMainStart ? 1 : 0) + (hasAutoMarginMainEnd ? 1 : 0);
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
}
