package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentDataTableChangeEvent;
import club.heiqi.uilib.ui.control.DocumentDataTableChangeHandler;
import club.heiqi.uilib.ui.control.DocumentDataTableControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置稳定列 map list 表格字段绑定。
 */
final class ModernTablePropertyBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding {

    private final List<String> columns;
    private final Map<String, ModernConfigListModels.ValueKind> columnKinds;
    private final List<Map<String, String>> draftRows = new ArrayList<Map<String, String>>();
    private DocumentDataTableControl tableControl;

    ModernTablePropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, path, node, fieldSpec, inference, changeListener);
        this.columns = new ArrayList<String>(inference.getTableColumns());
        this.columnKinds = new LinkedHashMap<String, ModernConfigListModels.ValueKind>(inference.getTableColumnKinds());
        replaceDraftRows(readCurrentTableRows());
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        tableControl = new DocumentDataTableControl(document, columns)
                .setRows(draftRows)
                .setChangeHandler(new DocumentDataTableChangeHandler() {
                    @Override
                    public void onTableChanged(DocumentDataTableChangeEvent event) {
                        replaceDraftRows(event.getRows());
                        notifyDraftChanged();
                    }
                });
        tableControl.getElement().setAttribute("data-modern-config-control", "data-table");
        tableControl.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        return tableControl.getElement();
    }

    @Override
    protected String buildHelperText() {
        String inherited = super.buildHelperText();
        String suffix = "稳定列对象列表：" + String.valueOf(columns);
        return inherited.isEmpty() ? suffix : inherited + " " + suffix;
    }

    @Override
    boolean isDirty() {
        return !Objects.equals(readCurrentTableValues(), readDraftTableValues());
    }

    @Override
    void restoreCurrentValue() {
        replaceDraftRows(readCurrentTableRows());
        if (tableControl != null) {
            tableControl.setRows(draftRows);
        }
    }

    @Override
    void restoreDefaultValue() {
        replaceDraftRows(readDefaultTableRows());
        if (tableControl != null) {
            tableControl.setRows(draftRows);
        }
        notifyDraftChanged();
    }

    @Override
    String validateDraft() {
        syncDraftRowsFromControl();
        for (int rowIndex = 0; rowIndex < draftRows.size(); rowIndex++) {
            Map<String, String> row = draftRows.get(rowIndex);
            for (String column : columns) {
                ModernConfigListModels.ParsedValue parsed = ModernConfigListModels.parseDraftValue(
                        columnKinds.get(column), row.get(column));
                if (parsed.hasError()) {
                    return "第 " + (rowIndex + 1) + " 行列 " + column + parsed.getError();
                }
            }
        }
        return null;
    }

    @Override
    void applyDraft() {
        getConfig().set(getPath(), readDraftTableValues());
    }

    private List<Map<String, String>> readCurrentTableRows() {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        ConfigNode node = getCurrentNode();
        if (node == null || node.getType() != ConfigNode.NodeType.LIST || node.asList() == null) {
            return rows;
        }
        for (ConfigNode item : node.asList()) {
            rows.add(formatRow(item));
        }
        return rows;
    }

    private List<Map<String, String>> readDefaultTableRows() {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        Object defaultValue = getDefaultValue();
        if (!(defaultValue instanceof List)) {
            return rows;
        }
        List<?> values = (List<?>) defaultValue;
        for (Object value : values) {
            rows.add(formatRawRow(value));
        }
        return rows;
    }

    private Map<String, String> formatRow(ConfigNode item) {
        Map<String, String> row = createEmptyRow();
        if (item == null || item.getType() != ConfigNode.NodeType.MAP || item.asMap() == null) {
            return row;
        }
        Map<String, ConfigNode> itemMap = item.asMap();
        for (String column : columns) {
            row.put(column, ModernConfigListModels.formatNodeValue(itemMap.get(column)));
        }
        return row;
    }

    private Map<String, String> formatRawRow(Object value) {
        Map<String, String> row = createEmptyRow();
        if (!(value instanceof Map)) {
            return row;
        }
        Map<?, ?> source = (Map<?, ?>) value;
        for (String column : columns) {
            row.put(column, ModernConfigListModels.formatRawPrimitiveValue(source.get(column)));
        }
        return row;
    }

    private List<Map<String, Object>> readCurrentTableValues() {
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        ConfigNode node = getCurrentNode();
        if (node == null || node.getType() != ConfigNode.NodeType.LIST || node.asList() == null) {
            return values;
        }
        for (ConfigNode item : node.asList()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            Map<String, ConfigNode> itemMap = item == null ? null : item.asMap();
            for (String column : columns) {
                row.put(column, itemMap == null ? null : ModernConfigListModels.convertNodeValue(itemMap.get(column)));
            }
            values.add(row);
        }
        return values;
    }

    private List<Map<String, Object>> readDraftTableValues() {
        syncDraftRowsFromControl();
        List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        for (Map<String, String> rowDraft : draftRows) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (String column : columns) {
                ModernConfigListModels.ParsedValue parsed = ModernConfigListModels.parseDraftValue(
                        columnKinds.get(column), rowDraft.get(column));
                row.put(column, parsed.hasError() ? rowDraft.get(column) : parsed.getValue());
            }
            values.add(row);
        }
        return values;
    }

    private void replaceDraftRows(List<Map<String, String>> rows) {
        draftRows.clear();
        if (rows == null) {
            return;
        }
        for (Map<String, String> row : rows) {
            draftRows.add(new LinkedHashMap<String, String>(row));
        }
    }

    private void syncDraftRowsFromControl() {
        if (tableControl != null) {
            replaceDraftRows(tableControl.getRowsSnapshot());
        }
    }

    private Map<String, String> createEmptyRow() {
        Map<String, String> row = new LinkedHashMap<String, String>();
        for (String column : columns) {
            row.put(column, "");
        }
        return row;
    }
}
