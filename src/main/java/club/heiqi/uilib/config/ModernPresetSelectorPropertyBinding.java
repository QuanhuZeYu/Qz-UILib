package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentSelectChangeEvent;
import club.heiqi.uilib.ui.control.DocumentSelectChangeHandler;
import club.heiqi.uilib.ui.control.DocumentSelectControl;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置预设选择字段绑定，读取同级 _presets 或 presets 批量应用目标字段。
 */
final class ModernPresetSelectorPropertyBinding extends ModernConfigPropertyBindings.ConfigPropertyBinding {

    private static final String CUSTOM_OPTION = "自定义（已修改）";
    private static final String PRESETS_KEY = "presets";
    private static final String PRIVATE_PRESETS_KEY = "_presets";

    private final List<PresetDefinition> presetDefinitions = new ArrayList<PresetDefinition>();
    private final Map<String, Object> draftTargetValues = new LinkedHashMap<String, Object>();
    private DocumentSelectControl presetSelect;
    private TextNode stateText;
    private String selectedPresetName = "";

    ModernPresetSelectorPropertyBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ModernConfigPropertyBindings.ChangeListener changeListener) {
        super(config, path, node, fieldSpec, inference, changeListener);
        replaceModelFromNode(node);
    }

    @Override
    protected ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
        ElementNode root = document.div();
        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8));

        ElementNode toolbar = document.div();
        toolbar.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setRowGap(UiStyleLength.px(6));
        ElementNode label = document.div();
        label.style().setTextColor(0xFFCBD5E1);
        label.appendText("选择预设");
        toolbar.append(label);

        presetSelect = new DocumentSelectControl(document, buildSelectOptions())
                .setSelectedIndex(resolveSelectedOptionIndex())
                .setEnabled(!presetDefinitions.isEmpty())
                .setChangeHandler(new DocumentSelectChangeHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSelectChangeEvent event) {
                        handlePresetSelection(event.getSelectedIndex());
                    }
                });
        presetSelect.getElement().setAttribute("data-modern-config-control", "preset-selector");
        presetSelect.getElement().style().setWidth(UiStyleLength.px(220));
        toolbar.append(presetSelect.getElement());

        ElementNode stateElement = document.div();
        stateElement.style()
                .setPadding(UiStyleLength.px(4))
                .setTextColor(0xFFCBD5E1);
        stateText = stateElement.appendText("");
        toolbar.append(stateElement);
        root.append(toolbar);
        root.append(createSummaryBlock(document));
        updateStateText();
        return root;
    }

    @Override
    protected String buildHelperText() {
        String inherited = super.buildHelperText();
        String suffix = "预设选择：读取同级 _presets 或 presets；选择预设后批量覆盖该预设声明的目标字段。";
        return inherited.isEmpty() ? suffix : inherited + " " + suffix;
    }

    @Override
    boolean isDirty() {
        return !mapEquals(readCurrentTargetValues(), draftTargetValues);
    }

    @Override
    void restoreCurrentValue() {
        replaceModelFromNode(getCurrentNode());
        syncSelectState();
    }

    @Override
    void restoreDefaultValue() {
        Object defaultValue = getDefaultValue();
        if (defaultValue instanceof Map) {
            draftTargetValues.clear();
            draftTargetValues.putAll(readRawTargetValues((Map<?, ?>) defaultValue));
            selectedPresetName = findMatchingPresetName(draftTargetValues);
            syncSelectState();
            notifyDraftChanged();
        }
    }

    @Override
    String validateDraft() {
        return presetDefinitions.isEmpty() ? "未找到 _presets 或 presets 预设定义。" : null;
    }

    @Override
    void applyDraft() {
        getConfig().set(getPath(), readMergedObjectValues());
    }

    boolean selectPreset(String presetName) {
        PresetDefinition definition = findPreset(presetName);
        if (definition == null) {
            return false;
        }
        applyPresetDefinition(definition);
        selectedPresetName = definition.name;
        syncSelectState();
        notifyDraftChanged();
        return true;
    }

    boolean isPresetModified() {
        PresetDefinition selected = findPreset(selectedPresetName);
        if (selected != null) {
            return !containsPresetValues(draftTargetValues, selected.values);
        }
        return findMatchingPresetName(draftTargetValues).isEmpty();
    }

    private void handlePresetSelection(int selectedIndex) {
        if (selectedIndex <= 0 || selectedIndex > presetDefinitions.size()) {
            selectedPresetName = "";
            updateStateText();
            return;
        }
        PresetDefinition definition = presetDefinitions.get(selectedIndex - 1);
        applyPresetDefinition(definition);
        selectedPresetName = definition.name;
        updateStateText();
        notifyDraftChanged();
    }

    private void replaceModelFromNode(ConfigNode node) {
        presetDefinitions.clear();
        presetDefinitions.addAll(readPresetDefinitions(node));
        draftTargetValues.clear();
        draftTargetValues.putAll(readTargetValues(node));
        selectedPresetName = findMatchingPresetName(draftTargetValues);
    }

    private void applyPresetDefinition(PresetDefinition definition) {
        for (Map.Entry<String, Object> entry : definition.values.entrySet()) {
            draftTargetValues.put(entry.getKey(), copyRawValue(entry.getValue()));
        }
    }

    private void syncSelectState() {
        if (presetSelect != null) {
            presetSelect.setSelectedIndex(resolveSelectedOptionIndex());
        }
        updateStateText();
    }

    private int resolveSelectedOptionIndex() {
        String resolvedName = selectedPresetName.isEmpty() ? findMatchingPresetName(draftTargetValues)
                : selectedPresetName;
        for (int index = 0; index < presetDefinitions.size(); index++) {
            if (presetDefinitions.get(index).name.equals(resolvedName)) {
                return index + 1;
            }
        }
        return 0;
    }

    private String[] buildSelectOptions() {
        String[] options = new String[presetDefinitions.size() + 1];
        options[0] = CUSTOM_OPTION;
        for (int index = 0; index < presetDefinitions.size(); index++) {
            options[index + 1] = presetDefinitions.get(index).name;
        }
        return options;
    }

    private ElementNode createSummaryBlock(UiDocument document) {
        ElementNode summary = document.div();
        summary.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFCBD5E1);
        if (presetDefinitions.isEmpty()) {
            summary.appendText("未找到可用预设定义。");
            return summary;
        }
        for (PresetDefinition definition : presetDefinitions) {
            ElementNode line = document.div();
            line.appendText(definition.name + "：" + formatValuesSummary(definition.values));
            summary.append(line);
        }
        return summary;
    }

    private void updateStateText() {
        if (stateText == null) {
            return;
        }
        String matchingName = findMatchingPresetName(draftTargetValues);
        PresetDefinition selected = findPreset(selectedPresetName);
        if (selected != null && containsPresetValues(draftTargetValues, selected.values)) {
            stateText.setText("当前预设：" + selected.name + "（已应用）");
            return;
        }
        if (!matchingName.isEmpty()) {
            stateText.setText("当前预设：" + matchingName + "（已应用）");
            return;
        }
        stateText.setText("当前预设：自定义（已修改）");
    }

    private Map<String, Object> readMergedObjectValues() {
        Map<String, Object> merged = readObjectValues(getCurrentNode());
        for (Map.Entry<String, Object> entry : draftTargetValues.entrySet()) {
            merged.put(entry.getKey(), copyRawValue(entry.getValue()));
        }
        return merged;
    }

    private Map<String, Object> readCurrentTargetValues() {
        return readTargetValues(getCurrentNode());
    }

    private Map<String, Object> readTargetValues(ConfigNode node) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        if (node == null || node.getType() != ConfigNode.NodeType.MAP || node.asMap() == null) {
            return values;
        }
        List<String> keys = new ArrayList<String>(node.asMap().keySet());
        Collections.sort(keys);
        for (String key : keys) {
            if (isPresetStorageKey(key)) {
                continue;
            }
            values.put(key, convertNodeValue(node.asMap().get(key)));
        }
        return values;
    }

    private Map<String, Object> readObjectValues(ConfigNode node) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        if (node == null || node.getType() != ConfigNode.NodeType.MAP || node.asMap() == null) {
            return values;
        }
        List<String> keys = new ArrayList<String>(node.asMap().keySet());
        Collections.sort(keys);
        for (String key : keys) {
            values.put(key, convertNodeValue(node.asMap().get(key)));
        }
        return values;
    }

    private Map<String, Object> readRawTargetValues(Map<?, ?> source) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        List<String> keys = new ArrayList<String>();
        for (Object key : source.keySet()) {
            keys.add(String.valueOf(key));
        }
        Collections.sort(keys);
        for (String key : keys) {
            if (!isPresetStorageKey(key)) {
                values.put(key, copyRawValue(source.get(key)));
            }
        }
        return values;
    }

    private List<PresetDefinition> readPresetDefinitions(ConfigNode node) {
        if (node == null || node.getType() != ConfigNode.NodeType.MAP || node.asMap() == null) {
            return Collections.emptyList();
        }
        ConfigNode storage = resolvePresetStorage(node.asMap());
        if (storage == null || storage.isNull()) {
            return Collections.emptyList();
        }
        if (storage.getType() == ConfigNode.NodeType.MAP) {
            return readMapPresetDefinitions(storage);
        }
        if (storage.getType() == ConfigNode.NodeType.LIST) {
            return readListPresetDefinitions(storage);
        }
        return Collections.emptyList();
    }

    private List<PresetDefinition> readMapPresetDefinitions(ConfigNode storage) {
        Map<String, ConfigNode> map = storage.asMap();
        if (map == null || map.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>(map.keySet());
        Collections.sort(names);
        List<PresetDefinition> definitions = new ArrayList<PresetDefinition>();
        for (String name : names) {
            ConfigNode presetNode = map.get(name);
            if (presetNode != null && presetNode.getType() == ConfigNode.NodeType.MAP && presetNode.asMap() != null) {
                definitions.add(new PresetDefinition(name, readTargetValues(presetNode)));
            }
        }
        return definitions;
    }

    private List<PresetDefinition> readListPresetDefinitions(ConfigNode storage) {
        List<ConfigNode> list = storage.asList();
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<PresetDefinition> definitions = new ArrayList<PresetDefinition>();
        for (int index = 0; index < list.size(); index++) {
            ConfigNode item = list.get(index);
            if (item == null || item.getType() != ConfigNode.NodeType.MAP || item.asMap() == null) {
                continue;
            }
            String name = resolvePresetListName(item, index);
            ConfigNode valuesNode = item.asMap().get("values");
            Map<String, Object> values = valuesNode != null && valuesNode.getType() == ConfigNode.NodeType.MAP
                    ? readTargetValues(valuesNode) : readListItemPresetValues(item);
            definitions.add(new PresetDefinition(name, values));
        }
        return definitions;
    }

    private Map<String, Object> readListItemPresetValues(ConfigNode item) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        List<String> keys = new ArrayList<String>(item.asMap().keySet());
        Collections.sort(keys);
        for (String key : keys) {
            if ("name".equals(key) || "id".equals(key) || "label".equals(key) || "description".equals(key)
                    || isPresetStorageKey(key)) {
                continue;
            }
            values.put(key, convertNodeValue(item.asMap().get(key)));
        }
        return values;
    }

    private String resolvePresetListName(ConfigNode item, int index) {
        ConfigNode nameNode = item.asMap().get("name");
        if (nameNode == null || nameNode.isNull()) {
            nameNode = item.asMap().get("id");
        }
        String name = nameNode == null || nameNode.isNull() ? "" : nameNode.asString("").trim();
        return name.isEmpty() ? "preset-" + (index + 1) : name;
    }

    private ConfigNode resolvePresetStorage(Map<String, ConfigNode> map) {
        ConfigNode privateStorage = map.get(PRIVATE_PRESETS_KEY);
        if (isPresetStorage(privateStorage)) {
            return privateStorage;
        }
        ConfigNode publicStorage = map.get(PRESETS_KEY);
        return isPresetStorage(publicStorage) ? publicStorage : null;
    }

    private PresetDefinition findPreset(String presetName) {
        String normalized = presetName == null ? "" : presetName.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        for (PresetDefinition definition : presetDefinitions) {
            if (definition.name.equals(normalized)) {
                return definition;
            }
        }
        return null;
    }

    private String findMatchingPresetName(Map<String, Object> targetValues) {
        for (PresetDefinition definition : presetDefinitions) {
            if (containsPresetValues(targetValues, definition.values)) {
                return definition.name;
            }
        }
        return "";
    }

    private static boolean containsPresetValues(Map<String, Object> targetValues, Map<String, Object> presetValues) {
        for (Map.Entry<String, Object> entry : presetValues.entrySet()) {
            if (!targetValues.containsKey(entry.getKey()) || !valueEquals(targetValues.get(entry.getKey()),
                    entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean mapEquals(Map<String, Object> first, Map<String, Object> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : first.entrySet()) {
            if (!second.containsKey(entry.getKey()) || !valueEquals(entry.getValue(), second.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean valueEquals(Object first, Object second) {
        if (first instanceof Number && second instanceof Number) {
            return Double.compare(((Number) first).doubleValue(), ((Number) second).doubleValue()) == 0;
        }
        if (first instanceof Map && second instanceof Map) {
            return rawMapEquals((Map<?, ?>) first, (Map<?, ?>) second);
        }
        if (first instanceof List && second instanceof List) {
            return rawListEquals((List<?>) first, (List<?>) second);
        }
        return Objects.equals(first, second);
    }

    private static boolean rawMapEquals(Map<?, ?> first, Map<?, ?> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (Map.Entry<?, ?> entry : first.entrySet()) {
            if (!second.containsKey(entry.getKey()) || !valueEquals(entry.getValue(), second.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean rawListEquals(List<?> first, List<?> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!valueEquals(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static Object convertNodeValue(ConfigNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.getType() == ConfigNode.NodeType.LIST) {
            List<Object> values = new ArrayList<Object>();
            List<ConfigNode> children = node.asList();
            if (children != null) {
                for (ConfigNode child : children) {
                    values.add(convertNodeValue(child));
                }
            }
            return values;
        }
        if (node.getType() == ConfigNode.NodeType.MAP) {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            Map<String, ConfigNode> children = node.asMap();
            if (children != null) {
                List<String> keys = new ArrayList<String>(children.keySet());
                Collections.sort(keys);
                for (String key : keys) {
                    values.put(key, convertNodeValue(children.get(key)));
                }
            }
            return values;
        }
        return ModernConfigListModels.convertNodeValue(node);
    }

    private static Object copyRawValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copied = new LinkedHashMap<String, Object>();
            Map<?, ?> source = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                copied.put(String.valueOf(entry.getKey()), copyRawValue(entry.getValue()));
            }
            return copied;
        }
        if (value instanceof List) {
            List<Object> copied = new ArrayList<Object>();
            for (Object item : (List<?>) value) {
                copied.add(copyRawValue(item));
            }
            return copied;
        }
        return ModernConfigListModels.convertRawPrimitiveValue(value);
    }

    private static boolean isPresetStorage(ConfigNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.getType() == ConfigNode.NodeType.MAP) {
            return node.asMap() != null && !node.asMap().isEmpty();
        }
        if (node.getType() == ConfigNode.NodeType.LIST) {
            return node.asList() != null && !node.asList().isEmpty();
        }
        return false;
    }

    private static boolean isPresetStorageKey(String key) {
        return PRIVATE_PRESETS_KEY.equals(key) || PRESETS_KEY.equals(key);
    }

    private static String formatValuesSummary(Map<String, Object> values) {
        if (values.isEmpty()) {
            return "无目标字段";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (count > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(String.valueOf(entry.getValue()));
            count++;
            if (count >= 4 && values.size() > count) {
                builder.append(", ...");
                break;
            }
        }
        String result = builder.toString();
        return result.length() <= 120 ? result : result.substring(0, 117) + "...";
    }

    private static final class PresetDefinition {

        private final String name;
        private final Map<String, Object> values;

        private PresetDefinition(String name, Map<String, Object> values) {
            this.name = name == null ? "" : name.trim();
            this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
        }
    }
}
