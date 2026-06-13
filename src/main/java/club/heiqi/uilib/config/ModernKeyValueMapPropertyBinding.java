package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentKeyValueEditorChangeEvent;
import club.heiqi.uilib.ui.control.DocumentKeyValueEditorChangeHandler;
import club.heiqi.uilib.ui.control.DocumentKeyValueEditorControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置动态 map 字段绑定，仅处理显式声明的 key/value/type 结构。
 */
final class ModernKeyValueMapPropertyBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding {

    private final List<DocumentKeyValueEditorControl.Row> draftRows =
            new ArrayList<DocumentKeyValueEditorControl.Row>();
    private DocumentKeyValueEditorControl editorControl;

    ModernKeyValueMapPropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, path, node, fieldSpec, inference, changeListener);
        replaceDraftRows(readRowsFromNode(node));
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        editorControl = new DocumentKeyValueEditorControl(document)
                .setRows(draftRows)
                .setChangeHandler(new DocumentKeyValueEditorChangeHandler() {
                    @Override
                    public void onRowsChanged(DocumentKeyValueEditorChangeEvent event) {
                        replaceDraftRows(event.getRows());
                        notifyDraftChanged();
                    }
                });
        editorControl.getElement().setAttribute("data-modern-config-control", "key-value-map");
        editorControl.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        return editorControl.getElement();
    }

    @Override
    protected String buildHelperText() {
        String inherited = super.buildHelperText();
        String suffix = "动态 map：支持新增、删除和修改 key/value/type；key 不能重复或包含点号。";
        return inherited.isEmpty() ? suffix : inherited + " " + suffix;
    }

    @Override
    boolean isDirty() {
        return !Objects.equals(readCurrentMapValues(), readDraftMapValues());
    }

    @Override
    void restoreCurrentValue() {
        replaceDraftRows(readRowsFromNode(getCurrentNode()));
        syncRowsToControl();
    }

    @Override
    void restoreDefaultValue() {
        replaceDraftRows(readRowsFromDefaultValue());
        syncRowsToControl();
        notifyDraftChanged();
    }

    @Override
    String validateDraft() {
        syncDraftRowsFromControl();
        Map<String, Integer> seenKeys = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < draftRows.size(); index++) {
            DocumentKeyValueEditorControl.Row row = draftRows.get(index);
            String key = normalizeKey(row.getKey());
            if (key.isEmpty()) {
                return "第 " + (index + 1) + " 行 key 不能为空。";
            }
            if (key.indexOf('.') >= 0) {
                return "第 " + (index + 1) + " 行 key 不能包含点号。";
            }
            Integer previousIndex = seenKeys.get(key);
            if (previousIndex != null) {
                return "第 " + (index + 1) + " 行 key 与第 " + (previousIndex.intValue() + 1)
                        + " 行重复：" + key + "。";
            }
            seenKeys.put(key, Integer.valueOf(index));
            ModernConfigListModels.ParsedValue parsed = parseRowValue(row);
            if (parsed.hasError()) {
                return "第 " + (index + 1) + " 行 value " + parsed.getError();
            }
        }
        return null;
    }

    @Override
    void applyDraft() {
        getConfig().set(getPath(), readDraftMapValues());
    }

    void replaceDraftRows(List<DocumentKeyValueEditorControl.Row> rows) {
        draftRows.clear();
        if (rows != null) {
            for (DocumentKeyValueEditorControl.Row row : rows) {
                draftRows.add(new DocumentKeyValueEditorControl.Row(row.getKey(), row.getValue(), row.getType()));
            }
        }
    }

    private void syncRowsToControl() {
        if (editorControl != null) {
            editorControl.setRows(draftRows);
        }
    }

    private void syncDraftRowsFromControl() {
        if (editorControl != null) {
            replaceDraftRows(editorControl.getRowsSnapshot());
        }
    }

    private List<DocumentKeyValueEditorControl.Row> readRowsFromNode(ConfigNode node) {
        List<DocumentKeyValueEditorControl.Row> rows = new ArrayList<DocumentKeyValueEditorControl.Row>();
        if (node == null || node.getType() != ConfigNode.NodeType.MAP || node.asMap() == null) {
            return rows;
        }
        List<String> keys = new ArrayList<String>(node.asMap().keySet());
        Collections.sort(keys);
        for (String key : keys) {
            ConfigNode valueNode = node.asMap().get(key);
            rows.add(new DocumentKeyValueEditorControl.Row(key, ModernConfigListModels.formatNodeValue(valueNode),
                    resolveValueType(valueNode)));
        }
        return rows;
    }

    private List<DocumentKeyValueEditorControl.Row> readRowsFromDefaultValue() {
        List<DocumentKeyValueEditorControl.Row> rows = new ArrayList<DocumentKeyValueEditorControl.Row>();
        Object defaultValue = getDefaultValue();
        if (!(defaultValue instanceof Map)) {
            return rows;
        }
        Map<?, ?> map = (Map<?, ?>) defaultValue;
        List<String> keys = new ArrayList<String>();
        for (Object key : map.keySet()) {
            keys.add(String.valueOf(key));
        }
        Collections.sort(keys);
        for (String key : keys) {
            Object value = map.get(key);
            rows.add(new DocumentKeyValueEditorControl.Row(key, ModernConfigListModels.formatRawPrimitiveValue(value),
                    resolveRawValueType(value)));
        }
        return rows;
    }

    private Map<String, Object> readCurrentMapValues() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        ConfigNode node = getCurrentNode();
        if (node == null || node.getType() != ConfigNode.NodeType.MAP || node.asMap() == null) {
            return values;
        }
        List<String> keys = new ArrayList<String>(node.asMap().keySet());
        Collections.sort(keys);
        for (String key : keys) {
            values.put(key, ModernConfigListModels.convertNodeValue(node.asMap().get(key)));
        }
        return values;
    }

    private Map<String, Object> readDraftMapValues() {
        syncDraftRowsFromControl();
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (DocumentKeyValueEditorControl.Row row : draftRows) {
            ModernConfigListModels.ParsedValue parsed = parseRowValue(row);
            values.put(normalizeKey(row.getKey()), parsed.hasError() ? row.getValue() : parsed.getValue());
        }
        return values;
    }

    private ModernConfigListModels.ParsedValue parseRowValue(DocumentKeyValueEditorControl.Row row) {
        return ModernConfigListModels.parseDraftValue(resolveValueKind(row.getType()), row.getValue());
    }

    private static ModernConfigListModels.ValueKind resolveValueKind(DocumentKeyValueEditorControl.ValueType type) {
        if (type == DocumentKeyValueEditorControl.ValueType.NUMBER) {
            return ModernConfigListModels.ValueKind.NUMBER;
        }
        if (type == DocumentKeyValueEditorControl.ValueType.BOOLEAN) {
            return ModernConfigListModels.ValueKind.BOOLEAN;
        }
        if (type == DocumentKeyValueEditorControl.ValueType.NULL) {
            return ModernConfigListModels.ValueKind.NULL;
        }
        return ModernConfigListModels.ValueKind.STRING;
    }

    private static DocumentKeyValueEditorControl.ValueType resolveValueType(ConfigNode node) {
        ModernConfigListModels.ValueKind kind = ModernConfigListModels.ValueKind.fromNode(node);
        if (kind == ModernConfigListModels.ValueKind.NUMBER) {
            return DocumentKeyValueEditorControl.ValueType.NUMBER;
        }
        if (kind == ModernConfigListModels.ValueKind.BOOLEAN) {
            return DocumentKeyValueEditorControl.ValueType.BOOLEAN;
        }
        if (kind == ModernConfigListModels.ValueKind.NULL) {
            return DocumentKeyValueEditorControl.ValueType.NULL;
        }
        return DocumentKeyValueEditorControl.ValueType.STRING;
    }

    private static DocumentKeyValueEditorControl.ValueType resolveRawValueType(Object value) {
        ModernConfigListModels.ValueKind kind = ModernConfigListModels.resolveRawPrimitiveKind(value,
                ModernConfigListModels.ValueKind.STRING);
        if (kind == ModernConfigListModels.ValueKind.NUMBER) {
            return DocumentKeyValueEditorControl.ValueType.NUMBER;
        }
        if (kind == ModernConfigListModels.ValueKind.BOOLEAN) {
            return DocumentKeyValueEditorControl.ValueType.BOOLEAN;
        }
        if (kind == ModernConfigListModels.ValueKind.NULL) {
            return DocumentKeyValueEditorControl.ValueType.NULL;
        }
        return DocumentKeyValueEditorControl.ValueType.STRING;
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim();
    }
}
