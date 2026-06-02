package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.AbsoluteContainingBlock;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.LayoutContext;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.LayoutChildrenResult;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.props.UiAlignContent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiAlignSelf;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextContentMode;

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

    static final String ANONYMOUS_FLEX_ITEM_TAG = "qz-anonymous-flex-item";

    private FlexLayoutHelper() {}

    /**
     * 委托式入口：对 display:flex 容器执行子元素布局。
     */
    static LayoutChildrenResult layoutFlexChildren(ElementNode element, ComputedStyle parentStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, boolean createsAbsoluteContainingBlock,
            AbsoluteContainingBlock fixedContainingBlock, boolean createsFixedContainingBlock,
            LayoutContext layoutContext) {
        FlexChildren flexChildren = collectFlexChildren(element, parentStyle, layoutContext);
        List<ElementNode> absoluteChildren = flexChildren.absoluteChildren;
        List<ElementNode> fixedChildren = flexChildren.fixedChildren;
        List<FlexChild> visibleChildren = sortFlexChildrenByOrder(flexChildren.inFlowChildren);
        LayoutChildrenResult flowResult;
        if (visibleChildren.isEmpty()) {
            flowResult = new LayoutChildrenResult(new ArrayList<DocumentLayoutBox>(),
                    new ArrayList<DocumentLayoutTextRun>(), new ArrayList<DocumentLayoutInlineFragment>(),
                    Math.max(0, specifiedContentHeight));
        } else if (parentStyle.getFlexDirection() == UiFlexDirection.COLUMN) {
            flowResult = layoutColumnFlexChildren(visibleChildren, parentStyle, contentLeft, contentTop, contentWidth,
                    specifiedContentHeight, absoluteContainingBlock, fixedContainingBlock, layoutContext);
        } else {
            flowResult = layoutRowFlexChildren(visibleChildren, parentStyle, contentLeft, contentTop, contentWidth,
                    specifiedContentHeight, absoluteContainingBlock, fixedContainingBlock, layoutContext);
        }
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>(flowResult.children);
        PositionedLayoutHelper.appendAbsoluteChildren(childBoxes, absoluteChildren,
                PositionedLayoutHelper.resolveDirectAbsoluteContainingBlock(absoluteContainingBlock,
                        createsAbsoluteContainingBlock, specifiedContentHeight, flowResult.contentHeight),
                fixedContainingBlock, layoutContext);
        PositionedLayoutHelper.appendFixedChildren(childBoxes, fixedChildren,
                PositionedLayoutHelper.resolveDirectAbsoluteContainingBlock(fixedContainingBlock,
                        createsFixedContainingBlock, specifiedContentHeight, flowResult.contentHeight),
                layoutContext);
        return new LayoutChildrenResult(sortFlexChildBoxesByOrder(element, childBoxes, flexChildren, layoutContext),
                flowResult.textRuns, flowResult.inlineFragments, flowResult.contentHeight);
    }

    private static LayoutChildrenResult layoutRowFlexChildren(List<FlexChild> children, ComputedStyle parentStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            LayoutContext layoutContext) {
        boolean wrap = parentStyle.getFlexWrap() == UiFlexWrap.WRAP;
        int gap = Math.max(0, parentStyle.getColumnGap().resolve(contentWidth, 0));
        int rowGap = Math.max(0, parentStyle.getRowGap().resolve(contentWidth, 0));

        // 第一步：按换行规则将 items 分成若干行
        List<List<FlexItem>> lines = new ArrayList<List<FlexItem>>();
        List<FlexItem> currentLine = new ArrayList<FlexItem>();
        int currentLineOccupied = 0;
        for (FlexChild child : children) {
            FlexItem item = createFlexItem(child, parentStyle, contentWidth, layoutContext);
            item.contentMainSize = resolveContentMainSize(item, contentWidth, true, layoutContext);
            item.minContentMainSize = resolveRowFlexItemMinMainSize(item, contentWidth, layoutContext);
            item.contentMainSize = Math.max(item.contentMainSize, item.minContentMainSize);
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

        List<FlexLine> plannedLines = new ArrayList<FlexLine>();
        int naturalCrossSize = Math.max(0, lines.size() - 1) * rowGap;
        for (List<FlexItem> lineItems : lines) {
            distributeMainSpace(lineItems, contentWidth, gap, true);
            int naturalLineCrossSize = 0;
            for (FlexItem item : lineItems) {
                item.box = layoutFlexItem(item, 0, 0, contentWidth,
                        specifiedContentHeight, item.contentMainSize, DocumentLayoutEngine.AUTO_SIZE,
                        absoluteContainingBlock, fixedContainingBlock, layoutContext);
                naturalLineCrossSize = Math.max(naturalLineCrossSize, item.getOuterCrossSize(true));
            }
            plannedLines.add(new FlexLine(lineItems, naturalLineCrossSize));
            naturalCrossSize += naturalLineCrossSize;
        }

        int targetCrossSize = specifiedContentHeight >= 0 ? specifiedContentHeight : naturalCrossSize;
        applyAlignContent(plannedLines, parentStyle.getAlignContent(), targetCrossSize, naturalCrossSize, rowGap);

        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        int measuredContentBottom = contentTop;
        for (FlexLine line : plannedLines) {
            List<FlexItem> lineItems = line.items;
            int lineCrossSize = line.crossSize;
            for (FlexItem item : lineItems) {
                if (shouldStretchRowItem(item, parentStyle.getAlignItems())) {
                    item.forcedCrossSize = Math.max(0, lineCrossSize
                            - item.margin.getVertical() - item.border.getVertical() - item.padding.getVertical());
                    item.box = layoutFlexItem(item, 0, 0, contentWidth, lineCrossSize, item.contentMainSize,
                            item.forcedCrossSize, absoluteContainingBlock, fixedContainingBlock, layoutContext);
                }
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
                    crossOffset = 0;
                }
                int borderLeft = contentLeft + cursor + marginLeft;
                int borderTop = contentTop + line.crossOffset + crossOffset + marginTop;
                DocumentLayoutBox childBox = layoutFlexItem(item, borderLeft - item.margin.getLeft(),
                        borderTop - item.margin.getTop(), contentWidth, lineCrossSize, item.contentMainSize,
                        item.forcedCrossSize, absoluteContainingBlock, fixedContainingBlock, layoutContext);
                childBoxes.add(childBox);
                cursor += marginLeft + childBox.getWidth() + marginRight + dynamicGap;
                measuredContentBottom = Math.max(measuredContentBottom, childBox.getBottom() + marginBottom);
            }
        }

        int contentHeight;
        if (specifiedContentHeight >= 0) {
            contentHeight = specifiedContentHeight;
        } else {
            contentHeight = Math.max(0, measuredContentBottom - contentTop);
        }
        return new LayoutChildrenResult(childBoxes, new ArrayList<DocumentLayoutTextRun>(),
                new ArrayList<DocumentLayoutInlineFragment>(), contentHeight);
    }

    private static LayoutChildrenResult layoutColumnFlexChildren(List<FlexChild> children, ComputedStyle parentStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            LayoutContext layoutContext) {
        List<FlexItem> items = new ArrayList<FlexItem>();
        for (FlexChild child : children) {
            FlexItem item = createFlexItem(child, parentStyle, contentWidth, false, layoutContext);
            item.forcedCrossSize = resolveColumnCrossContentWidth(item, parentStyle.getAlignItems(),
                    parentStyle.getFlexWrap(), contentWidth, layoutContext);
            item.box = layoutFlexItem(item, 0, 0, contentWidth, specifiedContentHeight,
                    item.forcedCrossSize, DocumentLayoutEngine.AUTO_SIZE, absoluteContainingBlock,
                    fixedContainingBlock, layoutContext);
            item.contentMainSize = resolveContentMainSize(item, contentWidth, false, layoutContext);
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
                crossOffset = 0;
            }
            int borderLeft = contentLeft + crossOffset + marginLeft;
            int borderTop = contentTop + cursor + marginTop;
            int forcedMainSize = shouldKeepAutoHeightInFinalColumnLayout(item, specifiedContentHeight)
                    ? DocumentLayoutEngine.AUTO_SIZE
                    : item.contentMainSize;
            DocumentLayoutBox childBox = layoutFlexItem(item, borderLeft - item.margin.getLeft(),
                    borderTop - item.margin.getTop(), contentWidth, contentHeight, item.forcedCrossSize,
                    forcedMainSize, absoluteContainingBlock, fixedContainingBlock, layoutContext);
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

    private static void applyAlignContent(List<FlexLine> lines, UiAlignContent alignContent,
            int targetCrossSize, int naturalCrossSize, int baseGap) {
        if (lines.isEmpty()) {
            return;
        }
        int remaining = Math.max(0, targetCrossSize - naturalCrossSize);
        UiAlignContent resolvedAlignContent = alignContent == null ? UiAlignContent.STRETCH : alignContent;
        if (resolvedAlignContent == UiAlignContent.STRETCH && remaining > 0) {
            int applied = 0;
            for (int index = 0; index < lines.size(); index++) {
                int addition = index == lines.size() - 1 ? remaining - applied : remaining / lines.size();
                lines.get(index).crossSize += Math.max(0, addition);
                applied += Math.max(0, addition);
            }
            remaining = 0;
        }
        int dynamicGap = resolveAlignContentGap(resolvedAlignContent, baseGap, remaining, lines.size());
        int cursor = resolveAlignContentLeadingOffset(resolvedAlignContent, remaining, lines.size());
        for (FlexLine line : lines) {
            line.crossOffset = cursor;
            cursor += line.crossSize + dynamicGap;
        }
    }

    private static int resolveAlignContentGap(UiAlignContent alignContent, int baseGap, int remaining, int lineCount) {
        if (remaining <= 0) {
            return baseGap;
        }
        if (alignContent == UiAlignContent.SPACE_BETWEEN && lineCount > 1) {
            return baseGap + remaining / (lineCount - 1);
        }
        if (alignContent == UiAlignContent.SPACE_AROUND && lineCount > 0) {
            return baseGap + remaining / lineCount;
        }
        if (alignContent == UiAlignContent.SPACE_EVENLY && lineCount > 0) {
            return baseGap + remaining / (lineCount + 1);
        }
        return baseGap;
    }

    private static int resolveAlignContentLeadingOffset(UiAlignContent alignContent, int remaining, int lineCount) {
        if (remaining <= 0) {
            return 0;
        }
        if (alignContent == UiAlignContent.CENTER) {
            return remaining / 2;
        }
        if (alignContent == UiAlignContent.END) {
            return remaining;
        }
        if (alignContent == UiAlignContent.SPACE_AROUND && lineCount > 0) {
            return (remaining / lineCount) / 2;
        }
        if (alignContent == UiAlignContent.SPACE_EVENLY && lineCount > 0) {
            return remaining / (lineCount + 1);
        }
        return 0;
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
            LayoutContext layoutContext) {
        // flex-basis 优先：非 auto 时用 flex-basis 作为主轴初始尺寸
        UiStyleLength flexBasis = item.style.getFlexBasis();
        if (!DocumentLayoutEngine.isAuto(flexBasis)) {
            int baseSize = Math.max(0, flexBasis.resolve(containingWidth, 0));
            if (row) {
                return DocumentLayoutEngine.resolveBoxSizingContentWidth(item.style, baseSize, item.border,
                        item.padding, true);
            }
            return DocumentLayoutEngine.resolveBoxSizingContentHeight(item.style, baseSize, item.border, item.padding,
                    true);
        }
        // flex-basis:auto 退回 width/height
        UiStyleLength length = row ? item.style.getWidth() : item.style.getHeight();
        if (DocumentLayoutEngine.isAuto(length)) {
            if (row) {
                return measureAutoFlexAutoWidth(item, containingWidth, layoutContext);
            }
            return 0;
        }
        int baseSize = Math.max(0, length.resolve(containingWidth, 0));
        DocumentAnimationProperty property = row ? DocumentAnimationProperty.WIDTH : DocumentAnimationProperty.HEIGHT;
        int resolvedSize = Math.max(0, layoutContext.layoutValueResolver.resolve(item.element, property, baseSize));
        if (row) {
            return DocumentLayoutEngine.resolveBoxSizingContentWidth(item.style, resolvedSize, item.border,
                    item.padding);
        }
        return DocumentLayoutEngine.resolveBoxSizingContentHeight(item.style, resolvedSize, item.border, item.padding);
    }

    /**
     * 测量 auto 宽度元素的内容宽，统一用于 flex row 主轴与 flex column 交叉轴。
     */
    private static int measureAutoFlexAutoWidth(FlexItem item, int containingWidth,
            LayoutContext layoutContext) {
        int measuredWidth = item.anonymousText
                ? TextLayoutHelper.measureIntrinsicTextWidth(item.textNode, item.style, layoutContext.textMeasureService)
                : DocumentLayoutEngine.measureIntrinsicContentWidth(item.element, containingWidth, layoutContext);
        if (measuredWidth <= 0) {
            return 0;
        }
        int availableContentWidth = Math.max(0, containingWidth
                - item.margin.getHorizontal()
                - item.border.getHorizontal()
                - item.padding.getHorizontal());
        return Math.min(measuredWidth, availableContentWidth);
    }

    private static int resolveRowFlexItemMinMainSize(FlexItem item, int containingWidth,
            LayoutContext layoutContext) {
        if (!DocumentLayoutEngine.isAuto(item.style.getMinWidth())) {
            int minWidth = Math.max(0, item.style.getMinWidth().resolve(containingWidth, 0));
            return DocumentLayoutEngine.resolveBoxSizingContentWidth(item.style, minWidth, item.border, item.padding,
                    true);
        }
        if (item.style.getOverflowX() != UiOverflow.VISIBLE) {
            return 0;
        }
        if (!DocumentLayoutEngine.isAuto(item.style.getWidth())) {
            return Math.min(item.contentMainSize, DocumentLayoutEngine.measureMinContentWidth(item.element,
                    containingWidth, layoutContext));
        }
        if (item.anonymousText) {
            return TextLayoutHelper.measureMinContentTextWidth(item.textNode, item.style,
                    layoutContext.textMeasureService);
        }
        return DocumentLayoutEngine.measureMinContentWidth(item.element, containingWidth, layoutContext);
    }

    /**
     * 测量 flex 容器的固有宽度，供嵌套 flex 容器在父级 auto 宽度下推导内容宽。
     */
    static int measureIntrinsicFlexContentWidth(ElementNode element, ComputedStyle style,
            int containingWidth, LayoutContext layoutContext) {
        List<FlexChild> children = sortFlexChildrenByOrder(collectFlexChildren(element, style,
                layoutContext).inFlowChildren);
        if (children.isEmpty()) {
            return 0;
        }
        int gap = Math.max(0, (style.getFlexDirection() == UiFlexDirection.COLUMN
                ? style.getRowGap()
                : style.getColumnGap()).resolve(containingWidth, 0));
        int measuredWidth = 0;
        int itemIndex = 0;
        for (FlexChild child : children) {
            int childWidth = child.anonymousText
                    ? measureAnonymousTextIntrinsicWidth(child, style, layoutContext)
                    : DocumentLayoutEngine.measureIntrinsicOuterWidth(child.element,
                            layoutContext.computeStyle(child.element), containingWidth, layoutContext);
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

    /**
     * 测量 flex 容器的 min-content 宽度，供 flex item auto 最小宽度使用。
     */
    static int measureMinContentFlexWidth(ElementNode element, ComputedStyle style,
            int containingWidth, LayoutContext layoutContext) {
        List<FlexChild> children = sortFlexChildrenByOrder(collectFlexChildren(element, style,
                layoutContext).inFlowChildren);
        if (children.isEmpty()) {
            return 0;
        }
        int gap = Math.max(0, (style.getFlexDirection() == UiFlexDirection.COLUMN
                ? style.getRowGap()
                : style.getColumnGap()).resolve(containingWidth, 0));
        int measuredWidth = 0;
        int itemIndex = 0;
        for (FlexChild child : children) {
            int childWidth = child.anonymousText
                    ? measureAnonymousTextMinContentWidth(child, style, layoutContext)
                    : DocumentLayoutEngine.measureMinContentWidth(child.element, containingWidth, layoutContext);
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
            int contentWidth, LayoutContext layoutContext) {
        if (!DocumentLayoutEngine.isAuto(item.style.getWidth())) {
            int baseWidth = Math.max(0, item.style.getWidth().resolve(contentWidth, 0));
            int resolvedWidth = Math.max(0, layoutContext.layoutValueResolver.resolve(item.element,
                    DocumentAnimationProperty.WIDTH, baseWidth));
            return DocumentLayoutEngine.resolveBoxSizingContentWidth(item.style, resolvedWidth, item.border,
                    item.padding);
        }
        // align-self 覆盖 align-items
        boolean stretch = shouldStretchColumnItem(item, alignItems);
        if (stretch) {
            return Math.max(0, contentWidth - item.margin.getHorizontal() - item.border.getHorizontal()
                    - item.padding.getHorizontal());
        }
        return measureAutoFlexAutoWidth(item, contentWidth, layoutContext);
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

    private static final java.util.concurrent.atomic.AtomicBoolean BASELINE_FALLBACK_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger("QzUiLib/FlexLayout");

    private static int resolveCrossOffset(UiAlignItems alignItems, int availableCrossSize, int itemOuterCrossSize) {
        int remaining = Math.max(0, availableCrossSize - itemOuterCrossSize);
        if (alignItems == UiAlignItems.CENTER) {
            return remaining / 2;
        }
        if (alignItems == UiAlignItems.END) {
            return remaining;
        }
        if (alignItems == UiAlignItems.BASELINE && BASELINE_FALLBACK_LOGGED.compareAndSet(false, true)) {
            LOG.warn("flex align-items: baseline 当前等价于 START；首版未实现完整基线对齐，请改用 padding/transform 偏移");
        }
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

    private static boolean shouldStretchRowItem(FlexItem item, UiAlignItems parentAlignItems) {
        return !item.hasAutoMarginCrossStart && !item.hasAutoMarginCrossEnd
                && isItemCrossStretch(item.style.getAlignSelf(), parentAlignItems)
                && DocumentLayoutEngine.isAuto(item.style.getHeight());
    }

    private static boolean shouldStretchColumnItem(FlexItem item, UiAlignItems parentAlignItems) {
        return !item.hasAutoMarginCrossStart && !item.hasAutoMarginCrossEnd
                && isItemCrossStretch(item.style.getAlignSelf(), parentAlignItems);
    }

    private static FlexChildren collectFlexChildren(ElementNode element, ComputedStyle parentStyle,
            LayoutContext layoutContext) {
        List<DocumentNode> generatedChildren = DocumentLayoutEngine.getGeneratedChildNodes(element, layoutContext);
        List<FlexChild> inFlowChildren = new ArrayList<FlexChild>();
        List<ElementNode> absoluteChildren = new ArrayList<ElementNode>();
        List<ElementNode> fixedChildren = new ArrayList<ElementNode>();
        StringBuilder pendingText = new StringBuilder();
        TextContentMode pendingTextContentMode = null;
        int pendingTextOrder = -1;
        for (int childIndex = 0; childIndex < generatedChildren.size(); childIndex++) {
            DocumentNode child = generatedChildren.get(childIndex);
            if (child instanceof TextNode) {
                TextNode textNode = (TextNode) child;
                if (pendingText.length() > 0 && pendingTextContentMode != textNode.getTextContentMode()) {
                    flushAnonymousTextItem(element, parentStyle, inFlowChildren, pendingText,
                            pendingTextContentMode, pendingTextOrder);
                    pendingText.setLength(0);
                    pendingTextContentMode = null;
                    pendingTextOrder = -1;
                }
                if (pendingText.length() == 0) {
                    pendingTextOrder = childIndex;
                    pendingTextContentMode = textNode.getTextContentMode();
                }
                pendingText.append(textNode.getText());
                continue;
            }
            flushAnonymousTextItem(element, parentStyle, inFlowChildren, pendingText,
                    pendingTextContentMode, pendingTextOrder);
            pendingText.setLength(0);
            pendingTextContentMode = null;
            pendingTextOrder = -1;
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = layoutContext.computeStyle(childElement);
            if (childStyle.getDisplay() == UiDisplay.NONE) {
                continue;
            }
            if (childStyle.getPosition() == UiPosition.FIXED) {
                fixedChildren.add(childElement);
                continue;
            }
            if (childStyle.getPosition() == UiPosition.ABSOLUTE) {
                absoluteChildren.add(childElement);
                continue;
            }
            inFlowChildren.add(FlexChild.element(childElement, childStyle.getOrder(), childIndex));
        }
        flushAnonymousTextItem(element, parentStyle, inFlowChildren, pendingText,
                pendingTextContentMode, pendingTextOrder);
        return new FlexChildren(inFlowChildren, absoluteChildren, fixedChildren);
    }

    private static void flushAnonymousTextItem(ElementNode ownerElement, ComputedStyle parentStyle,
            List<FlexChild> inFlowChildren, StringBuilder pendingText,
            TextContentMode textContentMode, int documentOrder) {
        if (pendingText.length() <= 0) {
            return;
        }
        TextContentMode resolvedMode = textContentMode == null ? TextContentMode.UILIB_RAW : textContentMode;
        String mergedText = pendingText.toString();
        String normalizedText = TextLayoutHelper.normalizeTextForLayout(mergedText, parentStyle, resolvedMode);
        if (normalizedText == null || normalizedText.isEmpty()) {
            return;
        }
        ElementNode anonymousElement = ownerElement.getOwnerDocument().element(ANONYMOUS_FLEX_ITEM_TAG);
        anonymousElement.setAttribute("data-hit-test-hidden", "true");
        TextNode textNode = ownerElement.getOwnerDocument().text(mergedText, resolvedMode);
        inFlowChildren.add(FlexChild.anonymousText(anonymousElement, textNode, ownerElement, documentOrder));
    }

    private static DocumentLayoutBox layoutFlexItem(FlexItem item, int containingLeft, int flowTop,
            int containingWidth, int containingHeight, int forcedContentWidth, int forcedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            LayoutContext layoutContext) {
        if (item.anonymousText) {
            return layoutAnonymousTextItem(item, containingLeft, flowTop, forcedContentWidth, forcedContentHeight,
                    layoutContext);
        }
        return DocumentLayoutEngine.layoutElement(item.element, containingLeft, flowTop, containingWidth,
                containingHeight, forcedContentWidth, forcedContentHeight, absoluteContainingBlock,
                fixedContainingBlock, layoutContext);
    }

    private static DocumentLayoutBox layoutAnonymousTextItem(FlexItem item, int left, int top, int forcedContentWidth,
            int forcedContentHeight, LayoutContext layoutContext) {
        int contentWidth = Math.max(0, forcedContentWidth);
        List<DocumentLayoutTextRun> textRuns = new ArrayList<DocumentLayoutTextRun>();
        int textBottom = InlineLayoutHelper.appendTextRun(item.textNode, item.textOwnerElement, item.style, textRuns,
                left, top, contentWidth, 0, layoutContext.textMeasureService);
        int contentHeight = forcedContentHeight >= 0 ? forcedContentHeight : Math.max(0, textBottom - top);
        return new DocumentLayoutBox(item.element, item.style, new ArrayList<DocumentLayoutBox>(), textRuns,
                new ArrayList<DocumentLayoutInlineFragment>(), item.margin, item.border, item.padding, left, top,
                contentWidth, Math.max(0, contentHeight), 0, 0, 0, 0, 0, 0);
    }

    private static int measureAnonymousTextIntrinsicWidth(FlexChild child, ComputedStyle parentStyle,
            LayoutContext layoutContext) {
        ComputedStyle anonymousStyle = UiStyleResolver.computeAnonymousFlexItemStyle(child.element, parentStyle);
        return TextLayoutHelper.measureIntrinsicTextWidth(child.textNode, anonymousStyle,
                layoutContext.textMeasureService);
    }

    private static int measureAnonymousTextMinContentWidth(FlexChild child, ComputedStyle parentStyle,
            LayoutContext layoutContext) {
        ComputedStyle anonymousStyle = UiStyleResolver.computeAnonymousFlexItemStyle(child.element, parentStyle);
        return TextLayoutHelper.measureMinContentTextWidth(child.textNode, anonymousStyle,
                layoutContext.textMeasureService);
    }

    private static List<FlexChild> sortFlexChildrenByOrder(List<FlexChild> children) {
        if (children.size() <= 1) {
            return children;
        }
        List<FlexChild> sortedChildren = new ArrayList<FlexChild>(children);
        Collections.sort(sortedChildren, new Comparator<FlexChild>() {
            @Override
            public int compare(FlexChild first, FlexChild second) {
                int orderCompare = Integer.compare(first.order, second.order);
                if (orderCompare != 0) {
                    return orderCompare;
                }
                return Integer.compare(first.documentOrder, second.documentOrder);
            }
        });
        return sortedChildren;
    }

    private static List<DocumentLayoutBox> sortFlexChildBoxesByOrder(final ElementNode parentElement,
            List<DocumentLayoutBox> childBoxes, final FlexChildren flexChildren, final LayoutContext layoutContext) {
        if (childBoxes.size() <= 1) {
            return childBoxes;
        }
        List<DocumentLayoutBox> sortedBoxes = new ArrayList<DocumentLayoutBox>(childBoxes);
        Collections.sort(sortedBoxes, new Comparator<DocumentLayoutBox>() {
            @Override
            public int compare(DocumentLayoutBox first, DocumentLayoutBox second) {
                FlexBoxOrder firstOrder = resolveFlexBoxOrder(parentElement, first, flexChildren, layoutContext);
                FlexBoxOrder secondOrder = resolveFlexBoxOrder(parentElement, second, flexChildren, layoutContext);
                int orderCompare = Integer.compare(firstOrder.order, secondOrder.order);
                if (orderCompare != 0) {
                    return orderCompare;
                }
                return Integer.compare(firstOrder.documentOrder, secondOrder.documentOrder);
            }
        });
        return sortedBoxes;
    }

    private static FlexBoxOrder resolveFlexBoxOrder(ElementNode parentElement, DocumentLayoutBox box,
            FlexChildren flexChildren, LayoutContext layoutContext) {
        for (FlexChild child : flexChildren.inFlowChildren) {
            if (child.element == box.getElement()) {
                return new FlexBoxOrder(child.order, child.documentOrder);
            }
        }
        ElementNode element = box.getElement();
        int documentOrder = DocumentLayoutEngine.getChildOrder(parentElement, element);
        int order = layoutContext.computeStyle(element).getOrder();
        return new FlexBoxOrder(order, documentOrder);
    }

    private static FlexItem createFlexItem(FlexChild child, ComputedStyle parentStyle, int containingWidth,
            LayoutContext layoutContext) {
        return createFlexItem(child, parentStyle, containingWidth, true, layoutContext);
    }

    private static FlexItem createFlexItem(FlexChild child, ComputedStyle parentStyle, int containingWidth, boolean row,
            LayoutContext layoutContext) {
        ComputedStyle style = child.anonymousText
                ? UiStyleResolver.computeAnonymousFlexItemStyle(child.element, parentStyle)
                : layoutContext.computeStyle(child.element);
        return new FlexItem(child.element, style, child.anonymousText, child.textNode, child.textOwnerElement,
                child.anonymousText ? DocumentLayoutEdges.zero()
                        : DocumentLayoutEngine.resolveMarginInsets(child.element, style, containingWidth,
                        layoutContext.layoutValueResolver),
                child.anonymousText ? DocumentLayoutEdges.zero()
                        : DocumentLayoutEngine.resolveBorderInsets(style, containingWidth),
                child.anonymousText ? DocumentLayoutEdges.zero()
                        : DocumentLayoutEngine.resolvePaddingInsets(child.element, style, containingWidth,
                                layoutContext.layoutValueResolver),
                row);
    }

    /**
     * flex 容器直接子项收集结果。
     */
    private static final class FlexChildren {

        private final List<FlexChild> inFlowChildren;
        private final List<ElementNode> absoluteChildren;
        private final List<ElementNode> fixedChildren;

        private FlexChildren(List<FlexChild> inFlowChildren, List<ElementNode> absoluteChildren,
                List<ElementNode> fixedChildren) {
            this.inFlowChildren = inFlowChildren;
            this.absoluteChildren = absoluteChildren;
            this.fixedChildren = fixedChildren;
        }
    }

    /**
     * flex item 输入子项，包含真实元素和直接文本生成的匿名 flex item。
     */
    private static final class FlexChild {

        private final ElementNode element;
        private final boolean anonymousText;
        private final TextNode textNode;
        private final ElementNode textOwnerElement;
        private final int order;
        private final int documentOrder;

        private static FlexChild element(ElementNode element, int order, int documentOrder) {
            return new FlexChild(element, false, null, null, order, documentOrder);
        }

        private static FlexChild anonymousText(ElementNode element, TextNode textNode, ElementNode textOwnerElement,
                int documentOrder) {
            return new FlexChild(element, true, textNode, textOwnerElement, 0, documentOrder);
        }

        private FlexChild(ElementNode element, boolean anonymousText, TextNode textNode,
                ElementNode textOwnerElement, int order, int documentOrder) {
            this.element = element;
            this.anonymousText = anonymousText;
            this.textNode = textNode;
            this.textOwnerElement = textOwnerElement;
            this.order = order;
            this.documentOrder = documentOrder;
        }
    }

    /**
     * 盒树最终顺序排序键，兼容真实元素和匿名 flex item。
     */
    private static final class FlexBoxOrder {

        private final int order;
        private final int documentOrder;

        private FlexBoxOrder(int order, int documentOrder) {
            this.order = order;
            this.documentOrder = documentOrder;
        }
    }

    /**
     * row flex 多行布局计划。
     */
    private static final class FlexLine {

        private final List<FlexItem> items;
        private int crossSize;
        private int crossOffset;

        private FlexLine(List<FlexItem> items, int crossSize) {
            this.items = items;
            this.crossSize = Math.max(0, crossSize);
        }
    }

    /**
     * Flex item 数据载体：承载 ComputedStyle、margin/border/padding、主/交叉轴运行尺寸与 auto margin 标记。
     */
    private static final class FlexItem {

        private final ElementNode element;
        private final ComputedStyle style;
        private final boolean anonymousText;
        private final TextNode textNode;
        private final ElementNode textOwnerElement;
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

        private FlexItem(ElementNode element, ComputedStyle style, boolean anonymousText, TextNode textNode,
                ElementNode textOwnerElement, DocumentLayoutEdges margin, DocumentLayoutEdges border,
                DocumentLayoutEdges padding, boolean row) {
            this.element = element;
            this.style = style;
            this.anonymousText = anonymousText;
            this.textNode = textNode;
            this.textOwnerElement = textOwnerElement;
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
