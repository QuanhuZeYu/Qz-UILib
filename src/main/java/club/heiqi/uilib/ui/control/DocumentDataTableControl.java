package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 固定列表头、行内文本编辑的数据表格控件。
 */
public final class DocumentDataTableControl {

    private static final int DEFAULT_CELL_MAX_LENGTH = 512;

    private final UiDocument document;
    private final ElementNode element;
    private final ElementNode toolbarElement;
    private final ElementNode stateElement;
    private final ElementNode tableShellElement;
    private final DocumentTableControl tableControl;
    private final TextNode stateText;
    private final List<String> columns = new ArrayList<String>();
    private final List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
    private DocumentDataTableChangeHandler changeHandler;
    private int selectedRowIndex = -1;

    /**
     * 创建数据表格控件。
     *
     * @param document 所属 HTML-like 文档
     * @param columns 固定列集合
     */
    public DocumentDataTableControl(UiDocument document, List<String> columns) {
        this.document = Objects.requireNonNull(document, "document");
        this.element = document.div();
        this.toolbarElement = document.div();
        this.stateElement = document.div();
        this.tableShellElement = document.div();
        this.tableControl = new DocumentTableControl(document)
                .setCellGap(0, 0)
                .setCellPadding(4)
                .setBorderCollapse(true)
                .setColors(0xFF263449, 0xFF0F172A, 0xFF334155, 0xFFE2E8F0, 0xFFFFFFFF);
        configureRoot();
        configureToolbar();
        configureStateElement();
        configureTableShell();
        this.stateText = stateElement.appendText("");
        element.append(toolbarElement);
        tableShellElement.append(tableControl.getElement());
        element.append(tableShellElement);
        setColumns(columns);
    }

    /**
     * 返回控件根元素。
     *
     * @return 根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 设置固定列集合。
     *
     * @param columns 固定列集合
     * @return 当前控件
     */
    public DocumentDataTableControl setColumns(List<String> columns) {
        this.columns.clear();
        if (columns != null) {
            for (String column : columns) {
                String normalized = column == null ? "" : column.trim();
                if (!normalized.isEmpty() && !this.columns.contains(normalized)) {
                    this.columns.add(normalized);
                }
            }
        }
        selectedRowIndex = -1;
        normalizeRowsToColumns();
        refreshToolbar();
        refreshTable();
        return this;
    }

    /**
     * 替换行数据。
     *
     * @param rows 行数据
     * @return 当前控件
     */
    public DocumentDataTableControl setRows(List<Map<String, String>> rows) {
        this.rows.clear();
        if (rows != null) {
            for (Map<String, String> row : rows) {
                this.rows.add(copyRow(row));
            }
        }
        selectedRowIndex = -1;
        normalizeRowsToColumns();
        refreshTable();
        return this;
    }

    /**
     * 设置表格变更处理器。
     *
     * @param changeHandler 表格变更处理器
     * @return 当前控件
     */
    public DocumentDataTableControl setChangeHandler(DocumentDataTableChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 返回当前行数据快照。
     *
     * @return 行数据快照
     */
    public List<Map<String, String>> getRowsSnapshot() {
        List<Map<String, String>> snapshot = new ArrayList<Map<String, String>>();
        for (Map<String, String> row : rows) {
            snapshot.add(new LinkedHashMap<String, String>(row));
        }
        return snapshot;
    }

    /**
     * 追加一行空数据。
     *
     * @return 当前控件
     */
    public DocumentDataTableControl addRow() {
        rows.add(createEmptyRow());
        selectedRowIndex = rows.size() - 1;
        refreshTable();
        fireChange();
        return this;
    }

    /**
     * 选中指定行。
     *
     * @param rowIndex 行索引
     * @return 当前控件
     */
    public DocumentDataTableControl selectRow(int rowIndex) {
        selectedRowIndex = rowIndex < 0 || rowIndex >= rows.size() ? -1 : rowIndex;
        refreshTable();
        return this;
    }

    /**
     * 删除当前选中行。
     *
     * @return 当前控件
     */
    public DocumentDataTableControl deleteSelectedRow() {
        if (selectedRowIndex < 0 || selectedRowIndex >= rows.size()) {
            return this;
        }
        rows.remove(selectedRowIndex);
        selectedRowIndex = Math.min(selectedRowIndex, rows.size() - 1);
        refreshTable();
        fireChange();
        return this;
    }

    /**
     * 按指定列升序排序。
     *
     * @param column 列名
     * @return 当前控件
     */
    public DocumentDataTableControl sortByColumn(final String column) {
        if (column == null || !columns.contains(column)) {
            return this;
        }
        Collections.sort(rows, new Comparator<Map<String, String>>() {
            @Override
            public int compare(Map<String, String> first, Map<String, String> second) {
                return valueOf(first, column).compareToIgnoreCase(valueOf(second, column));
            }
        });
        selectedRowIndex = -1;
        refreshTable();
        fireChange();
        return this;
    }

    private void refreshToolbar() {
        toolbarElement.clearChildren();
        toolbarElement.append(createButton("添加行", new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                addRow();
            }
        }).getElement());
        toolbarElement.append(createButton("删除选中", new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                deleteSelectedRow();
            }
        }).getElement());
        for (final String column : columns) {
            toolbarElement.append(createButton("按 " + column + " 排序", new DocumentButtonActionHandler() {
                @Override
                public void onAction(DocumentButtonActionEvent event) {
                    sortByColumn(column);
                }
            }).getElement());
        }
        toolbarElement.append(stateElement);
    }

    private void refreshTable() {
        List<String> headerLabels = new ArrayList<String>();
        headerLabels.add("选择");
        headerLabels.addAll(columns);
        tableControl.setHeader(headerLabels.toArray(new String[headerLabels.size()])).clearRows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            appendTableRow(rowIndex, rows.get(rowIndex));
        }
        updateStateText();
    }

    private void appendTableRow(final int rowIndex, Map<String, String> row) {
        List<ElementNode> cells = tableControl.addEmptyRow(columns.size() + 1);
        DocumentButtonControl selectButton = createButton(rowIndex == selectedRowIndex ? "已选" : "选择",
                new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        selectRow(rowIndex);
                    }
                });
        cells.get(0).append(selectButton.getElement());
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            final String column = columns.get(columnIndex);
            final Map<String, String> rowDraft = row;
            final DocumentTextInputControl input = new DocumentTextInputControl(document)
                    .setMaxLength(DEFAULT_CELL_MAX_LENGTH)
                    .setText(valueOf(row, column));
            input.setChangeHandler(new DocumentTextInputChangeHandler() {
                @Override
                public void onTextChanged(DocumentTextInputChangeEvent event) {
                    rowDraft.put(column, input.getText());
                    fireChange();
                }
            });
            input.getElement().style().setWidth(UiStyleLength.px(130));
            cells.get(columnIndex + 1).append(input.getElement());
        }
    }

    private DocumentButtonControl createButton(String label, DocumentButtonActionHandler handler) {
        DocumentButtonControl button = new DocumentButtonControl(document, label)
                .setBackgroundColors(0xFF334155, 0xFF475569, 0xFF1E293B)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(0xFFE2E8F0, 0xFF64748B)
                .setActionHandler(handler);
        button.getElement().style().setPadding(UiStyleLength.px(7));
        return button;
    }

    private void normalizeRowsToColumns() {
        for (int index = 0; index < rows.size(); index++) {
            rows.set(index, copyRow(rows.get(index)));
        }
    }

    private Map<String, String> createEmptyRow() {
        Map<String, String> row = new LinkedHashMap<String, String>();
        for (String column : columns) {
            row.put(column, "");
        }
        return row;
    }

    private Map<String, String> copyRow(Map<String, String> source) {
        Map<String, String> row = createEmptyRow();
        if (source != null) {
            for (String column : columns) {
                row.put(column, source.get(column) == null ? "" : source.get(column));
            }
        }
        return row;
    }

    private void updateStateText() {
        stateText.setText("行 " + rows.size() + " 条" + (selectedRowIndex >= 0 ? "，已选第 "
                + (selectedRowIndex + 1) + " 行" : "，未选择行"));
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.onTableChanged(new DocumentDataTableChangeEvent(this, element, getRowsSnapshot()));
        }
    }

    private void configureRoot() {
        element.setAttribute("data-document-control", "data-table");
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8))
                .setWidth(UiStyleLength.percent(1.0F));
    }

    private void configureToolbar() {
        toolbarElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setRowGap(UiStyleLength.px(6))
                .setTextColor(0xFFCBD5E1);
    }

    private void configureStateElement() {
        stateElement.style()
                .setPadding(UiStyleLength.px(4))
                .setTextColor(0xFFCBD5E1);
    }

    private void configureTableShell() {
        tableShellElement.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.AUTO)
                .setOverflowY(UiOverflow.HIDDEN);
        tableControl.getElement().style().setWidth(UiStyleLength.percent(1.0F));
    }

    private static String valueOf(Map<String, String> row, String column) {
        if (row == null || column == null) {
            return "";
        }
        String value = row.get(column);
        return value == null ? "" : value;
    }
}
