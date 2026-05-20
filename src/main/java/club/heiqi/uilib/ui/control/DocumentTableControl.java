package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.UiBorderStyle;
import club.heiqi.uilib.ui.style.UiBorderCollapse;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 基于 HTML-like table/tr/th/td 元素实现的表格控件适配器。
 */
public final class DocumentTableControl {

    private static final int DEFAULT_HEADER_BACKGROUND = 0xCC263449;
    private static final int DEFAULT_BODY_BACKGROUND = 0xAA162033;
    private static final int DEFAULT_BORDER_COLOR = 0xFF4E617C;
    private static final int DEFAULT_TEXT_COLOR = 0xFFEAF2FF;
    private static final int DEFAULT_HEADER_TEXT_COLOR = 0xFFFFFFFF;

    private final UiDocument document;
    private final ElementNode element;
    private final ElementNode headerSection;
    private final ElementNode bodySection;
    private final List<ElementNode> headerCells = new ArrayList<ElementNode>();
    private final List<List<ElementNode>> bodyRows = new ArrayList<List<ElementNode>>();
    private final List<UiStyleLength> columnWidths = new ArrayList<UiStyleLength>();

    private int columnGap = 0;
    private int rowGap = 0;
    private int cellPadding = 6;
    private int borderWidth = 1;
    private boolean borderCollapse;
    private int headerBackgroundColor = DEFAULT_HEADER_BACKGROUND;
    private int bodyBackgroundColor = DEFAULT_BODY_BACKGROUND;
    private int borderColor = DEFAULT_BORDER_COLOR;
    private int textColor = DEFAULT_TEXT_COLOR;
    private int headerTextColor = DEFAULT_HEADER_TEXT_COLOR;

    /**
     * 创建表格控件。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentTableControl(UiDocument document) {
        this.document = Objects.requireNonNull(document, "document");
        this.element = document.table();
        this.headerSection = document.thead();
        this.bodySection = document.tbody();
        configureTableElement();
        element.append(bodySection);
    }

    /**
     * 返回 table 根元素。
     *
     * @return table 元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回 thead 元素。
     *
     * @return thead 元素
     */
    public ElementNode getHeaderSectionElement() {
        return headerSection;
    }

    /**
     * 返回 tbody 元素。
     *
     * @return tbody 元素
     */
    public ElementNode getBodySectionElement() {
        return bodySection;
    }

    /**
     * 设置列宽。
     *
     * @param columnIndex 列索引
     * @param width 列宽；当前由每行同列单元格宽度共同参与 table 布局
     * @return 当前表格控件
     */
    public DocumentTableControl setColumnWidth(int columnIndex, UiStyleLength width) {
        if (columnIndex < 0) {
            throw new IllegalArgumentException("columnIndex cannot be negative");
        }
        UiStyleLength resolvedWidth = Objects.requireNonNull(width, "width");
        ensureColumnWidthCapacity(columnIndex);
        columnWidths.set(columnIndex, resolvedWidth);
        if (columnIndex < headerCells.size()) {
            headerCells.get(columnIndex).style().setWidth(resolvedWidth);
        }
        for (List<ElementNode> row : bodyRows) {
            if (columnIndex < row.size()) {
                row.get(columnIndex).style().setWidth(resolvedWidth);
            }
        }
        return this;
    }

    /**
     * 设置单元格间距。
     *
     * @param rowGap 行间距
     * @param columnGap 列间距
     * @return 当前表格控件
     */
    public DocumentTableControl setCellGap(int rowGap, int columnGap) {
        this.rowGap = Math.max(0, rowGap);
        this.columnGap = Math.max(0, columnGap);
        element.style()
                .setRowGap(UiStyleLength.px(this.rowGap))
                .setColumnGap(UiStyleLength.px(this.columnGap));
        return this;
    }

    /**
     * 设置所有单元格内边距。
     *
     * @param cellPadding 内边距像素
     * @return 当前表格控件
     */
    public DocumentTableControl setCellPadding(int cellPadding) {
        this.cellPadding = Math.max(0, cellPadding);
        applyCellStyles();
        return this;
    }

    /**
     * 设置所有单元格边框宽度。
     *
     * @param borderWidth 边框宽度像素
     * @return 当前表格控件
     */
    public DocumentTableControl setBorderWidth(int borderWidth) {
        this.borderWidth = Math.max(0, borderWidth);
        applyCellStyles();
        return this;
    }

    /**
     * 设置表格边框是否合并。
     *
     * @param borderCollapse true 时使用 collapse 语义，false 时保持 separate
     * @return 当前表格控件
     */
    public DocumentTableControl setBorderCollapse(boolean borderCollapse) {
        this.borderCollapse = borderCollapse;
        configureTableElement();
        return this;
    }

    /**
     * 设置表格颜色。
     *
     * @param headerBackgroundColor 表头背景色
     * @param bodyBackgroundColor 表体背景色
     * @param borderColor 单元格边框色
     * @param textColor 普通文本色
     * @param headerTextColor 表头文本色
     * @return 当前表格控件
     */
    public DocumentTableControl setColors(int headerBackgroundColor, int bodyBackgroundColor, int borderColor,
            int textColor, int headerTextColor) {
        this.headerBackgroundColor = headerBackgroundColor;
        this.bodyBackgroundColor = bodyBackgroundColor;
        this.borderColor = borderColor;
        this.textColor = textColor;
        this.headerTextColor = headerTextColor;
        applyCellStyles();
        return this;
    }

    /**
     * 写入表头行。
     *
     * @param labels 表头文本
     * @return 当前表格控件
     */
    public DocumentTableControl setHeader(String... labels) {
        headerCells.clear();
        headerSection.clearChildren();
        if (labels == null || labels.length <= 0) {
            detachHeaderSection();
            return this;
        }
        ensureHeaderSectionAttached();
        ElementNode row = document.tr();
        headerSection.append(row);
        for (String label : labels) {
            ElementNode cell = document.th();
            cell.appendText(normalizeText(label));
            configureCell(cell, true);
            applyColumnWidth(cell, headerCells.size());
            headerCells.add(cell);
            row.append(cell);
        }
        return this;
    }

    /**
     * 追加表体行。
     *
     * @param values 单元格文本
     * @return 当前表格控件
     */
    public DocumentTableControl addRow(String... values) {
        ElementNode row = document.tr();
        bodySection.append(row);
        List<ElementNode> cells = new ArrayList<ElementNode>();
        if (values != null) {
            for (String value : values) {
                ElementNode cell = document.td();
                cell.appendText(normalizeText(value));
                configureCell(cell, false);
                applyColumnWidth(cell, cells.size());
                cells.add(cell);
                row.append(cell);
            }
        }
        bodyRows.add(cells);
        return this;
    }

    /**
     * 追加一个无文本内容的表体行，并返回该行单元格元素。
     *
     * <p>该入口用于图标格、状态格等仍需真实 table 结构但单元格内容由调用方继续装配的场景。</p>
     *
     * @param cellCount 单元格数量
     * @return 新行中的单元格元素快照
     */
    public List<ElementNode> addEmptyRow(int cellCount) {
        ElementNode row = document.tr();
        bodySection.append(row);
        List<ElementNode> cells = new ArrayList<ElementNode>();
        int resolvedCellCount = Math.max(0, cellCount);
        for (int columnIndex = 0; columnIndex < resolvedCellCount; columnIndex++) {
            ElementNode cell = document.td();
            configureCell(cell, false);
            applyColumnWidth(cell, cells.size());
            cells.add(cell);
            row.append(cell);
        }
        bodyRows.add(cells);
        return new ArrayList<ElementNode>(cells);
    }

    /**
     * 清空表体行。
     *
     * @return 当前表格控件
     */
    public DocumentTableControl clearRows() {
        bodyRows.clear();
        bodySection.clearChildren();
        return this;
    }

    private void configureTableElement() {
        element.style()
                .setRowGap(UiStyleLength.px(rowGap))
                .setColumnGap(UiStyleLength.px(columnGap))
                .setBorderCollapse(borderCollapse ? UiBorderCollapse.COLLAPSE : UiBorderCollapse.SEPARATE);
    }

    private void ensureHeaderSectionAttached() {
        if (headerSection.getParent() == element) {
            return;
        }
        element.clearChildren();
        element.append(headerSection).append(bodySection);
    }

    private void detachHeaderSection() {
        if (headerSection.getParent() == element) {
            element.removeChild(headerSection);
        }
    }

    private void configureCell(ElementNode cell, boolean header) {
        cell.style()
                .setPadding(UiStyleInsets.of(UiStyleLength.px(cellPadding), UiStyleLength.px(cellPadding),
                        UiStyleLength.px(cellPadding), UiStyleLength.px(cellPadding)))
                .setBorderWidth(UiStyleLength.px(borderWidth))
                .setBorderColor(borderColor)
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBackgroundColor(header ? headerBackgroundColor : bodyBackgroundColor)
                .setTextColor(header ? headerTextColor : textColor)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
    }

    private void applyCellStyles() {
        for (ElementNode headerCell : headerCells) {
            configureCell(headerCell, true);
        }
        for (List<ElementNode> row : bodyRows) {
            for (ElementNode cell : row) {
                configureCell(cell, false);
            }
        }
    }

    private void applyColumnWidth(ElementNode cell, int columnIndex) {
        if (columnIndex < columnWidths.size() && columnWidths.get(columnIndex) != null) {
            cell.style().setWidth(columnWidths.get(columnIndex));
        }
    }

    private void ensureColumnWidthCapacity(int columnIndex) {
        while (columnWidths.size() <= columnIndex) {
            columnWidths.add(null);
        }
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text;
    }
}
