package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * 支持 key、value、type 三列编辑的动态键值控件。
 */
public final class DocumentKeyValueEditorControl {

    private static final int DEFAULT_CELL_MAX_LENGTH = 512;

    private final UiDocument document;
    private final ElementNode element;
    private final ElementNode toolbarElement;
    private final ElementNode stateElement;
    private final ElementNode tableShellElement;
    private final DocumentTableControl tableControl;
    private final TextNode stateText;
    private final List<RowDraft> rows = new ArrayList<RowDraft>();
    private DocumentKeyValueEditorChangeHandler changeHandler;
    private int selectedRowIndex = -1;

    /**
     * 创建动态键值编辑器。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentKeyValueEditorControl(UiDocument document) {
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
        refreshToolbar();
        refreshTable();
    }

    /**
     * 返回控件根元素。
     *
     * @return 控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 替换行数据。
     *
     * @param rows 行数据
     * @return 当前控件
     */
    public DocumentKeyValueEditorControl setRows(List<Row> rows) {
        this.rows.clear();
        if (rows != null) {
            for (Row row : rows) {
                this.rows.add(new RowDraft(row));
            }
        }
        selectedRowIndex = -1;
        refreshTable();
        return this;
    }

    /**
     * 设置行数据变更处理器。
     *
     * @param changeHandler 行数据变更处理器
     * @return 当前控件
     */
    public DocumentKeyValueEditorControl setChangeHandler(DocumentKeyValueEditorChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 返回当前行数据快照。
     *
     * @return 行数据快照
     */
    public List<Row> getRowsSnapshot() {
        List<Row> snapshot = new ArrayList<Row>();
        for (RowDraft row : rows) {
            snapshot.add(row.toRow());
        }
        return snapshot;
    }

    /**
     * 追加一行空键值。
     *
     * @return 当前控件
     */
    public DocumentKeyValueEditorControl addRow() {
        rows.add(new RowDraft("", "", ValueType.STRING));
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
    public DocumentKeyValueEditorControl selectRow(int rowIndex) {
        selectedRowIndex = rowIndex < 0 || rowIndex >= rows.size() ? -1 : rowIndex;
        refreshTable();
        return this;
    }

    /**
     * 删除当前选中行。
     *
     * @return 当前控件
     */
    public DocumentKeyValueEditorControl deleteSelectedRow() {
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
     * 更新指定行，便于外部模型同步行数据。
     *
     * @param rowIndex 行索引
     * @param key 键名
     * @param value 值文本
     * @param type 值类型
     * @return 当前控件
     */
    public DocumentKeyValueEditorControl updateRow(int rowIndex, String key, String value, ValueType type) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return this;
        }
        RowDraft row = rows.get(rowIndex);
        row.key = key == null ? "" : key;
        row.type = type == null ? ValueType.STRING : type;
        row.value = row.type == ValueType.NULL ? "" : value == null ? "" : value;
        refreshTable();
        fireChange();
        return this;
    }

    private void refreshToolbar() {
        toolbarElement.clearChildren();
        toolbarElement.append(createButton("添加项", new DocumentButtonActionHandler() {
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
        toolbarElement.append(stateElement);
    }

    private void refreshTable() {
        tableControl.setHeader("选择", "Key", "Value", "Type").clearRows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            appendTableRow(rowIndex, rows.get(rowIndex));
        }
        updateStateText();
    }

    private void appendTableRow(final int rowIndex, final RowDraft row) {
        List<ElementNode> cells = tableControl.addEmptyRow(4);
        DocumentButtonControl selectButton = createButton(rowIndex == selectedRowIndex ? "已选" : "选择",
                new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        selectRow(rowIndex);
                    }
                });
        cells.get(0).append(selectButton.getElement());

        final DocumentTextInputControl keyInput = new DocumentTextInputControl(document)
                .setMaxLength(DEFAULT_CELL_MAX_LENGTH)
                .setPlaceholder("key")
                .setText(row.key);
        keyInput.setChangeHandler(new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                row.key = keyInput.getText();
                fireChange();
            }
        });
        keyInput.getElement().style().setWidth(UiStyleLength.px(140));
        cells.get(1).append(keyInput.getElement());

        final DocumentTextInputControl valueInput = new DocumentTextInputControl(document)
                .setMaxLength(DEFAULT_CELL_MAX_LENGTH)
                .setPlaceholder(row.type == ValueType.NULL ? "null" : "value")
                .setText(row.value);
        valueInput.setChangeHandler(new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                row.value = valueInput.getText();
                fireChange();
            }
        });
        if (row.type == ValueType.NUMBER) {
            valueInput.setType(DocumentInputType.NUMBER);
        }
        if (row.type == ValueType.NULL) {
            valueInput.setText("").setReadOnly(true);
        }
        valueInput.getElement().style().setWidth(UiStyleLength.px(180));
        cells.get(2).append(valueInput.getElement());

        final DocumentSelectControl typeSelect = new DocumentSelectControl(document, ValueType.optionLabels())
                .setSelectedIndex(row.type.ordinal())
                .setChangeHandler(new DocumentSelectChangeHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSelectChangeEvent event) {
                        row.type = ValueType.fromOption(event.getSelectedOption());
                        if (row.type == ValueType.NULL) {
                            row.value = "";
                        }
                        refreshTable();
                        fireChange();
                    }
                });
        typeSelect.getElement().style().setWidth(UiStyleLength.px(120));
        cells.get(3).append(typeSelect.getElement());
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

    private void updateStateText() {
        stateText.setText("键值项 " + rows.size() + " 个" + (selectedRowIndex >= 0 ? "，已选第 "
                + (selectedRowIndex + 1) + " 行" : "，未选择行"));
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.onRowsChanged(new DocumentKeyValueEditorChangeEvent(this, element, getRowsSnapshot()));
        }
    }

    private void configureRoot() {
        element.setAttribute("data-document-control", "key-value-editor");
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

    /**
     * 键值行的值类型。
     */
    public enum ValueType {
        STRING("string"),
        NUMBER("number"),
        BOOLEAN("boolean"),
        NULL("null");

        private final String optionLabel;

        ValueType(String optionLabel) {
            this.optionLabel = optionLabel;
        }

        /**
         * 返回下拉选项文本。
         *
         * @return 下拉选项文本
         */
        public String getOptionLabel() {
            return optionLabel;
        }

        static String[] optionLabels() {
            ValueType[] values = values();
            String[] labels = new String[values.length];
            for (int index = 0; index < values.length; index++) {
                labels[index] = values[index].optionLabel;
            }
            return labels;
        }

        static ValueType fromOption(String option) {
            String normalized = option == null ? "" : option.trim().toLowerCase(Locale.ENGLISH);
            for (ValueType value : values()) {
                if (value.optionLabel.equals(normalized)) {
                    return value;
                }
            }
            return STRING;
        }
    }

    /**
     * 键值行快照。
     */
    public static final class Row {

        private final String key;
        private final String value;
        private final ValueType type;

        /**
         * 创建键值行快照。
         *
         * @param key 键名
         * @param value 值文本
         * @param type 值类型
         */
        public Row(String key, String value, ValueType type) {
            this.key = key == null ? "" : key;
            this.type = type == null ? ValueType.STRING : type;
            this.value = this.type == ValueType.NULL ? "" : value == null ? "" : value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public ValueType getType() {
            return type;
        }
    }

    private static final class RowDraft {

        private String key;
        private String value;
        private ValueType type;

        private RowDraft(String key, String value, ValueType type) {
            this.key = key == null ? "" : key;
            this.type = type == null ? ValueType.STRING : type;
            this.value = this.type == ValueType.NULL ? "" : value == null ? "" : value;
        }

        private RowDraft(Row row) {
            this(row == null ? "" : row.getKey(), row == null ? "" : row.getValue(),
                    row == null ? ValueType.STRING : row.getType());
        }

        private Row toRow() {
            return new Row(key, value, type);
        }
    }
}
