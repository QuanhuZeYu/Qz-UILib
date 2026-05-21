package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.AbsoluteContainingBlock;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.LayoutChildrenResult;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.LayoutContext;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.VisibleElementChildren;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiBorderCollapse;
import club.heiqi.uilib.ui.style.props.UiDisplay;

/**
 * Table 布局辅助类。
 *
 * <p>从 {@link DocumentLayoutEngine} 提取的 table 布局方法群，负责 table/thead/tbody/tfoot/tr/td
 * 的列宽分配、行高测量和单元格定位。</p>
 */
final class TableLayoutHelper {

    private TableLayoutHelper() {}

    /**
     * 对 table 元素执行子元素布局。
     */
    static LayoutChildrenResult layoutTableChildren(ElementNode element, ComputedStyle tableStyle,
            int contentLeft, int contentTop, int contentWidth, int specifiedContentHeight,
            AbsoluteContainingBlock absoluteContainingBlock, boolean createsAbsoluteContainingBlock,
            AbsoluteContainingBlock fixedContainingBlock, LayoutContext layoutContext) {
        VisibleElementChildren visibleElementChildren = DocumentLayoutEngine.getVisibleElementChildren(element,
                layoutContext);
        List<ElementNode> absoluteChildren = visibleElementChildren.absoluteChildren;
        List<ElementNode> fixedChildren = visibleElementChildren.fixedChildren;
        List<TableRowPlan> rowPlans = collectTableRows(visibleElementChildren.inFlowChildren, layoutContext);
        List<DocumentLayoutBox> childBoxes = new ArrayList<DocumentLayoutBox>();
        int columnCount = resolveTableColumnCount(rowPlans);
        boolean collapsedBorders = tableStyle.getBorderCollapse() == UiBorderCollapse.COLLAPSE;
        int rowGap = collapsedBorders ? 0 : Math.max(0, tableStyle.getRowGap().resolve(contentWidth, 0));
        int columnGap = collapsedBorders ? 0 : Math.max(0, tableStyle.getColumnGap().resolve(contentWidth, 0));
        int childFlowTop = contentTop;
        if (columnCount > 0) {
            int[] columnWidths = resolveTableColumnWidths(rowPlans, columnCount, contentWidth, columnGap,
                    layoutContext);
            boolean hasPreviousTableChild = false;
            for (ElementNode childElement : visibleElementChildren.inFlowChildren) {
                ComputedStyle childStyle = layoutContext.computeStyle(childElement);
                DocumentLayoutBox childBox = null;
                if (DocumentLayoutEngine.isTableRowGroupDisplay(childStyle.getDisplay())) {
                    childBox = layoutTableSection(childElement, contentLeft, childFlowTop, contentWidth, columnWidths,
                            rowGap, columnGap, absoluteContainingBlock, fixedContainingBlock, layoutContext);
                } else if (childStyle.getDisplay() == UiDisplay.TABLE_ROW) {
                    childBox = layoutTableRow(new TableRowPlan(childElement, collectTableRowCells(childElement,
                            layoutContext)),
                            contentLeft, childFlowTop, contentWidth, columnWidths, columnGap,
                            absoluteContainingBlock, fixedContainingBlock, layoutContext);
                }
                if (childBox == null) {
                    continue;
                }
                if (hasPreviousTableChild) {
                    int shiftedTop = childFlowTop + rowGap;
                    childBox = relayoutTableDirectChild(childBox, shiftedTop, contentLeft, contentWidth, columnWidths,
                            rowGap, columnGap, absoluteContainingBlock, fixedContainingBlock, layoutContext);
                }
                childBoxes.add(childBox);
                childFlowTop = childBox.getBottom();
                hasPreviousTableChild = true;
            }
        }
        int contentHeight = Math.max(0, childFlowTop - contentTop);
        PositionedLayoutHelper.appendAbsoluteChildren(childBoxes, absoluteChildren,
                PositionedLayoutHelper.resolveDirectAbsoluteContainingBlock(absoluteContainingBlock,
                        createsAbsoluteContainingBlock, specifiedContentHeight, contentHeight),
                fixedContainingBlock, layoutContext);
        PositionedLayoutHelper.appendFixedChildren(childBoxes, fixedChildren, fixedContainingBlock, layoutContext);
        return new LayoutChildrenResult(DocumentLayoutEngine.sortByDocumentChildOrder(element, childBoxes),
                new ArrayList<DocumentLayoutTextRun>(), new ArrayList<DocumentLayoutInlineFragment>(), contentHeight);
    }

    private static DocumentLayoutBox relayoutTableDirectChild(DocumentLayoutBox previousBox, int top,
            int contentLeft, int contentWidth, int[] columnWidths, int rowGap, int columnGap,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            LayoutContext layoutContext) {
        ElementNode element = previousBox.getElement();
        ComputedStyle style = layoutContext.computeStyle(element);
        if (DocumentLayoutEngine.isTableRowGroupDisplay(style.getDisplay())) {
            return layoutTableSection(element, contentLeft, top, contentWidth, columnWidths, rowGap, columnGap,
                    absoluteContainingBlock, fixedContainingBlock, layoutContext);
        }
        return layoutTableRow(new TableRowPlan(element, collectTableRowCells(element, layoutContext)), contentLeft,
                top, contentWidth, columnWidths, columnGap, absoluteContainingBlock, fixedContainingBlock,
                layoutContext);
    }

    private static DocumentLayoutBox layoutTableSection(ElementNode sectionElement, int contentLeft, int sectionTop,
            int contentWidth, int[] columnWidths, int rowGap, int columnGap,
            AbsoluteContainingBlock absoluteContainingBlock, AbsoluteContainingBlock fixedContainingBlock,
            LayoutContext layoutContext) {
        List<DocumentLayoutBox> rowBoxes = new ArrayList<DocumentLayoutBox>();
        int rowTop = sectionTop;
        boolean hasPreviousRow = false;
        for (ElementNode rowElement : DocumentLayoutEngine.getVisibleInFlowElementChildren(sectionElement,
                layoutContext)) {
            ComputedStyle rowStyle = layoutContext.computeStyle(rowElement);
            if (rowStyle.getDisplay() != UiDisplay.TABLE_ROW) {
                continue;
            }
            if (hasPreviousRow) {
                rowTop += rowGap;
            }
            DocumentLayoutBox rowBox = layoutTableRow(new TableRowPlan(rowElement, collectTableRowCells(rowElement,
                    layoutContext)),
                    contentLeft, rowTop, contentWidth, columnWidths, columnGap, absoluteContainingBlock,
                    fixedContainingBlock, layoutContext);
            rowBoxes.add(rowBox);
            rowTop = rowBox.getBottom();
            hasPreviousRow = true;
        }
        if (rowBoxes.isEmpty()) {
            return null;
        }
        return new DocumentLayoutBox(sectionElement, layoutContext.computeStyle(sectionElement), rowBoxes,
                new ArrayList<DocumentLayoutTextRun>(), new ArrayList<DocumentLayoutInlineFragment>(),
                DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), contentLeft,
                sectionTop, contentWidth, Math.max(0, rowTop - sectionTop), 0, 0, 0, 0, 0, 0);
    }

    private static DocumentLayoutBox layoutTableRow(TableRowPlan rowPlan, int contentLeft, int rowTop,
            int contentWidth, int[] columnWidths, int columnGap, AbsoluteContainingBlock absoluteContainingBlock,
            AbsoluteContainingBlock fixedContainingBlock, LayoutContext layoutContext) {
        int rowHeight = resolveTableRowSpecifiedHeight(rowPlan.element, layoutContext);
        for (int columnIndex = 0; columnIndex < columnWidths.length; columnIndex++) {
            ElementNode cellElement = rowPlan.getCell(columnIndex);
            if (cellElement == null) {
                continue;
            }
            int columnWidth = columnWidths[columnIndex];
            int forcedContentWidth = resolveTableCellForcedContentWidth(cellElement, columnWidth, layoutContext);
            DocumentLayoutBox measuredBox = DocumentLayoutEngine.layoutElement(cellElement, 0, 0, columnWidth,
                    rowHeight, forcedContentWidth, DocumentLayoutEngine.AUTO_SIZE, absoluteContainingBlock,
                    fixedContainingBlock, layoutContext);
            rowHeight = Math.max(rowHeight, DocumentLayoutEngine.getOuterBlockHeight(measuredBox));
        }

        List<DocumentLayoutBox> cellBoxes = new ArrayList<DocumentLayoutBox>();
        int cellLeft = contentLeft;
        for (int columnIndex = 0; columnIndex < columnWidths.length; columnIndex++) {
            ElementNode cellElement = rowPlan.getCell(columnIndex);
            int columnWidth = columnWidths[columnIndex];
            if (cellElement != null) {
                int forcedContentWidth = resolveTableCellForcedContentWidth(cellElement, columnWidth, layoutContext);
                int forcedContentHeight = resolveTableCellForcedContentHeight(cellElement, rowHeight, columnWidth,
                        layoutContext);
                cellBoxes.add(DocumentLayoutEngine.layoutElement(cellElement, cellLeft, rowTop, columnWidth, rowHeight,
                        forcedContentWidth, forcedContentHeight, absoluteContainingBlock, fixedContainingBlock,
                        layoutContext));
            }
            cellLeft += columnWidth + columnGap;
        }
        return new DocumentLayoutBox(rowPlan.element, layoutContext.computeStyle(rowPlan.element), cellBoxes,
                new ArrayList<DocumentLayoutTextRun>(), new ArrayList<DocumentLayoutInlineFragment>(),
                DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), DocumentLayoutEdges.zero(), contentLeft,
                rowTop, contentWidth, rowHeight, 0, 0, 0, 0, 0, 0);
    }

    private static List<TableRowPlan> collectTableRows(List<ElementNode> tableChildren,
            LayoutContext layoutContext) {
        List<TableRowPlan> rows = new ArrayList<TableRowPlan>();
        for (ElementNode childElement : tableChildren) {
            ComputedStyle childStyle = layoutContext.computeStyle(childElement);
            if (DocumentLayoutEngine.isTableRowGroupDisplay(childStyle.getDisplay())) {
                collectTableSectionRows(childElement, rows, layoutContext);
            } else if (childStyle.getDisplay() == UiDisplay.TABLE_ROW) {
                rows.add(new TableRowPlan(childElement, collectTableRowCells(childElement, layoutContext)));
            }
        }
        return rows;
    }

    private static void collectTableSectionRows(ElementNode sectionElement, List<TableRowPlan> rows,
            LayoutContext layoutContext) {
        for (ElementNode rowElement : DocumentLayoutEngine.getVisibleInFlowElementChildren(sectionElement,
                layoutContext)) {
            ComputedStyle rowStyle = layoutContext.computeStyle(rowElement);
            if (rowStyle.getDisplay() == UiDisplay.TABLE_ROW) {
                rows.add(new TableRowPlan(rowElement, collectTableRowCells(rowElement, layoutContext)));
            }
        }
    }

    private static List<ElementNode> collectTableRowCells(ElementNode rowElement, LayoutContext layoutContext) {
        List<ElementNode> cells = new ArrayList<ElementNode>();
        for (ElementNode cellElement : DocumentLayoutEngine.getVisibleInFlowElementChildren(rowElement,
                layoutContext)) {
            ComputedStyle cellStyle = layoutContext.computeStyle(cellElement);
            if (cellStyle.getDisplay() == UiDisplay.TABLE_CELL) {
                cells.add(cellElement);
            }
        }
        return cells;
    }

    private static int resolveTableColumnCount(List<TableRowPlan> rows) {
        int columnCount = 0;
        for (TableRowPlan row : rows) {
            columnCount = Math.max(columnCount, row.cells.size());
        }
        return columnCount;
    }

    private static int[] resolveTableColumnWidths(List<TableRowPlan> rows, int columnCount, int contentWidth,
            int columnGap, LayoutContext layoutContext) {
        int[] widths = new int[columnCount];
        boolean[] explicitColumns = new boolean[columnCount];
        int availableWidth = Math.max(0, contentWidth - Math.max(0, columnCount - 1) * columnGap);
        for (TableRowPlan row : rows) {
            for (int columnIndex = 0; columnIndex < row.cells.size() && columnIndex < columnCount; columnIndex++) {
                int preferredWidth = resolveSpecifiedTableCellOuterWidth(row.cells.get(columnIndex), availableWidth,
                        layoutContext);
                if (preferredWidth < 0) {
                    continue;
                }
                widths[columnIndex] = Math.max(widths[columnIndex], preferredWidth);
                explicitColumns[columnIndex] = true;
            }
        }
        fitTableColumnWidths(widths, explicitColumns, availableWidth);
        return widths;
    }

    private static int resolveSpecifiedTableCellOuterWidth(ElementNode cellElement, int availableWidth,
            LayoutContext layoutContext) {
        ComputedStyle style = layoutContext.computeStyle(cellElement);
        if (DocumentLayoutEngine.isAuto(style.getWidth())) {
            return -1;
        }
        DocumentLayoutEdges margin = DocumentLayoutEngine.resolveMarginInsets(cellElement, style, availableWidth,
                layoutContext.layoutValueResolver);
        DocumentLayoutEdges border = DocumentLayoutEngine.resolveBorderInsets(style, availableWidth);
        DocumentLayoutEdges padding = DocumentLayoutEngine.resolvePaddingInsets(cellElement, style, availableWidth,
                layoutContext.layoutValueResolver);
        int baseWidth = Math.max(0, style.getWidth().resolve(availableWidth, 0));
        int resolvedWidth = Math.max(0, layoutContext.layoutValueResolver.resolve(cellElement,
                DocumentAnimationProperty.WIDTH, baseWidth));
        resolvedWidth = DocumentLayoutEngine.resolveBoxSizingContentWidth(style, resolvedWidth, border, padding);
        return resolvedWidth + margin.getHorizontal() + border.getHorizontal() + padding.getHorizontal();
    }

    private static void fitTableColumnWidths(int[] widths, boolean[] explicitColumns, int availableWidth) {
        int usedWidth = DocumentLayoutEngine.sum(widths);
        if (usedWidth < availableWidth) {
            distributeTableWidth(widths, explicitColumns, availableWidth - usedWidth);
            return;
        }
        if (usedWidth > availableWidth) {
            shrinkTableWidths(widths, usedWidth - availableWidth);
        }
    }

    private static void distributeTableWidth(int[] widths, boolean[] explicitColumns, int extraWidth) {
        int targetCount = 0;
        for (boolean explicitColumn : explicitColumns) {
            if (!explicitColumn) {
                targetCount++;
            }
        }
        boolean useAutoColumns = targetCount > 0;
        if (!useAutoColumns) {
            targetCount = widths.length;
        }
        if (targetCount <= 0 || extraWidth <= 0) {
            return;
        }
        int distributed = 0;
        int targetIndex = 0;
        for (int columnIndex = 0; columnIndex < widths.length; columnIndex++) {
            if (useAutoColumns && explicitColumns[columnIndex]) {
                continue;
            }
            targetIndex++;
            int addition = targetIndex == targetCount ? extraWidth - distributed
                    : extraWidth / targetCount;
            widths[columnIndex] += Math.max(0, addition);
            distributed += Math.max(0, addition);
        }
    }

    private static void shrinkTableWidths(int[] widths, int overflow) {
        int usedWidth = DocumentLayoutEngine.sum(widths);
        if (usedWidth <= 0 || overflow <= 0) {
            return;
        }
        int removed = 0;
        for (int columnIndex = 0; columnIndex < widths.length; columnIndex++) {
            int cut = columnIndex == widths.length - 1 ? overflow - removed
                    : Math.round(overflow * widths[columnIndex] / (float) usedWidth);
            cut = Math.max(0, Math.min(cut, widths[columnIndex]));
            widths[columnIndex] -= cut;
            removed += cut;
        }
    }

    private static int resolveTableCellForcedContentWidth(ElementNode cellElement, int columnWidth,
            LayoutContext layoutContext) {
        ComputedStyle style = layoutContext.computeStyle(cellElement);
        DocumentLayoutEdges margin = DocumentLayoutEngine.resolveMarginInsets(cellElement, style, columnWidth,
                layoutContext.layoutValueResolver);
        DocumentLayoutEdges border = DocumentLayoutEngine.resolveBorderInsets(style, columnWidth);
        DocumentLayoutEdges padding = DocumentLayoutEngine.resolvePaddingInsets(cellElement, style, columnWidth,
                layoutContext.layoutValueResolver);
        return Math.max(0, columnWidth - margin.getHorizontal() - border.getHorizontal()
                - padding.getHorizontal());
    }

    private static int resolveTableCellForcedContentHeight(ElementNode cellElement, int rowHeight, int columnWidth,
            LayoutContext layoutContext) {
        ComputedStyle style = layoutContext.computeStyle(cellElement);
        DocumentLayoutEdges margin = DocumentLayoutEngine.resolveMarginInsets(cellElement, style, columnWidth,
                layoutContext.layoutValueResolver);
        DocumentLayoutEdges border = DocumentLayoutEngine.resolveBorderInsets(style, columnWidth);
        DocumentLayoutEdges padding = DocumentLayoutEngine.resolvePaddingInsets(cellElement, style, columnWidth,
                layoutContext.layoutValueResolver);
        return Math.max(0, rowHeight - margin.getVertical() - border.getVertical() - padding.getVertical());
    }

    private static int resolveTableRowSpecifiedHeight(ElementNode rowElement, LayoutContext layoutContext) {
        ComputedStyle rowStyle = layoutContext.computeStyle(rowElement);
        if (DocumentLayoutEngine.isAuto(rowStyle.getHeight())) {
            return 0;
        }
        int baseHeight = Math.max(0, rowStyle.getHeight().resolve(0, 0));
        return Math.max(0, layoutContext.layoutValueResolver.resolve(rowElement, DocumentAnimationProperty.HEIGHT,
                baseHeight));
    }

    /**
     * table 布局阶段使用的行与单元格计划。
     */
    static final class TableRowPlan {

        final ElementNode element;
        final List<ElementNode> cells;

        TableRowPlan(ElementNode element, List<ElementNode> cells) {
            this.element = element;
            this.cells = cells;
        }

        ElementNode getCell(int columnIndex) {
            if (columnIndex < 0 || columnIndex >= cells.size()) {
                return null;
            }
            return cells.get(columnIndex);
        }
    }
}
