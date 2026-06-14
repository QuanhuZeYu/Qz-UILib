package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;

/**
 * 现代配置模板的基础类型推断器。
 */
final class ModernConfigTypeInference {

    static final int SEGMENTED_CHOICE_MAX_OPTIONS = 4;
    private static final int LONG_TEXT_LENGTH_THRESHOLD = 160;

    private ModernConfigTypeInference() {
    }

    /**
     * 推断指定配置节点应使用的基础模板。
     *
     * <p>等价于 {@link #infer(String, ConfigNode, ModernConfigTemplateScreen.FieldSpec, ConfigFormat)}
     * 传入 {@code null} 作为 fallback 格式；raw hint 时 rawFormat 默认 JSON。</p>
     *
     * @param path 配置路径
     * @param node 当前配置节点
     * @param fieldSpec 字段规格
     * @return 推断结果
     */
    static Result infer(String path, ConfigNode node, ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        return infer(path, node, fieldSpec, null);
    }

    /**
     * 推断指定配置节点应使用的基础模板，并允许调用方传入格式回退值。
     *
     * <p>对于 raw/code/source 等未明确格式的源码 hint，rawFormat 取 fallbackFormat；
     * fallbackFormat 为 null 时回退到 JSON。json/yaml 类 hint 始终按 hint 决定格式。</p>
     *
     * @param path 配置路径
     * @param node 当前配置节点
     * @param fieldSpec 字段规格
     * @param fallbackFormat raw hint 时的回退格式；可为 null
     * @return 推断结果
     */
    static Result infer(String path, ConfigNode node, ModernConfigTemplateScreen.FieldSpec fieldSpec,
            ConfigFormat fallbackFormat) {
        String hint = normalizeHint(fieldSpec == null ? "" : fieldSpec.getTemplateHint());
        if (isDynamicMapHint(hint)) {
            return createResult(path, TemplateType.KEY_VALUE_MAP, false, Collections.<String>emptyList());
        }
        // 高级编辑 hint 早返回：先匹配明确格式，再匹配通用 raw hint
        ConfigFormat specificRawFormat = matchSpecificRawFormat(hint);
        if (specificRawFormat != null) {
            return createRawEditorResult(path, specificRawFormat);
        }
        if (isGenericRawHint(hint)) {
            ConfigFormat effectiveFormat = fallbackFormat == null ? ConfigFormat.JSON : fallbackFormat;
            return createRawEditorResult(path, effectiveFormat);
        }
        // 增强选择器 hint 早返回
        PickerKind pickerKind = matchEnhancedPickerHint(hint);
        if (pickerKind != null) {
            return createEnhancedPickerResult(path, pickerKind);
        }
        List<String> validValues = fieldSpec == null ? Collections.<String>emptyList() : fieldSpec.getValidValues();
        if (!validValues.isEmpty()) {
            return createResult(path, TemplateType.CHOICE, false, validValues);
        }

        ConfigNode.NodeType nodeType = node == null ? ConfigNode.NodeType.NULL : node.getType();
        if (nodeType == ConfigNode.NodeType.STRING) {
            String value = node.asString("");
            TemplateType type = shouldUseLongText(value, fieldSpec) ? TemplateType.LONG_TEXT : TemplateType.STRING;
            return createResult(path, type, false, Collections.<String>emptyList());
        }
        if (nodeType == ConfigNode.NodeType.NUMBER) {
            return createResult(path, TemplateType.NUMBER, isIntegerNumber(node), Collections.<String>emptyList());
        }
        if (nodeType == ConfigNode.NodeType.BOOLEAN) {
            return createResult(path, TemplateType.BOOLEAN, false, Collections.<String>emptyList());
        }
        if (nodeType == ConfigNode.NodeType.NULL) {
            return inferNullNode(path, fieldSpec);
        }
        if (nodeType == ConfigNode.NodeType.LIST) {
            return inferListNode(path, node);
        }
        if (nodeType == ConfigNode.NodeType.MAP) {
            if (hasPresetDefinitions(node)) {
                return createResult(path, TemplateType.PRESET_SELECTOR, false, Collections.<String>emptyList());
            }
            return createResult(path, TemplateType.OBJECT, false, Collections.<String>emptyList());
        }
        return createResult(path, TemplateType.READ_ONLY, false, Collections.<String>emptyList());
    }

    private static Result inferNullNode(String path, ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        Object defaultValue = fieldSpec == null || !fieldSpec.hasDefaultValue() ? null : fieldSpec.getDefaultValue();
        String hint = normalizeHint(fieldSpec == null ? "" : fieldSpec.getTemplateHint());
        if (defaultValue instanceof Boolean || "boolean".equals(hint) || "bool".equals(hint)) {
            return createResult(path, TemplateType.BOOLEAN, false, Collections.<String>emptyList());
        }
        if (defaultValue instanceof Number || "number".equals(hint) || "numeric".equals(hint)) {
            return createResult(path, TemplateType.NUMBER, isIntegerDefault(defaultValue),
                    Collections.<String>emptyList());
        }
        if (defaultValue instanceof String) {
            String value = (String) defaultValue;
            TemplateType type = shouldUseLongText(value, fieldSpec) ? TemplateType.LONG_TEXT : TemplateType.STRING;
            return createResult(path, type, false, Collections.<String>emptyList());
        }
        if (defaultValue instanceof List) {
            return new Result(path, TemplateType.SIMPLE_LIST, false, Collections.<String>emptyList(),
                    ModernConfigListModels.ValueKind.STRING, Collections.<String>emptyList(),
                    Collections.<String, ModernConfigListModels.ValueKind>emptyMap(), null, null);
        }
        if (isLongTextHint(hint)) {
            return createResult(path, TemplateType.LONG_TEXT, false, Collections.<String>emptyList());
        }
        if ("string".equals(hint) || "text".equals(hint)) {
            return createResult(path, TemplateType.STRING, false, Collections.<String>emptyList());
        }
        return createResult(path, TemplateType.NULL, false, Collections.<String>emptyList());
    }

    private static Result inferListNode(String path, ConfigNode node) {
        ModernConfigListModels.ListAnalysis analysis = ModernConfigListModels.analyze(node);
        if (analysis.getTemplateKind() == ModernConfigListModels.TemplateKind.SIMPLE) {
            return new Result(path, TemplateType.SIMPLE_LIST, false, Collections.<String>emptyList(),
                    analysis.getPrimitiveKind(), Collections.<String>emptyList(),
                    Collections.<String, ModernConfigListModels.ValueKind>emptyMap(), null, null);
        }
        if (analysis.getTemplateKind() == ModernConfigListModels.TemplateKind.TABLE) {
            return new Result(path, TemplateType.TABLE, false, Collections.<String>emptyList(),
                    ModernConfigListModels.ValueKind.STRING, analysis.getTableColumns(),
                    analysis.getTableColumnKinds(), null, null);
        }
        return createResult(path, TemplateType.READ_ONLY, false, Collections.<String>emptyList());
    }

    private static Result createResult(String path, TemplateType templateType, boolean integerNumber,
            List<String> choiceOptions) {
        return new Result(path, templateType, integerNumber, choiceOptions, ModernConfigListModels.ValueKind.STRING,
                Collections.<String>emptyList(), Collections.<String, ModernConfigListModels.ValueKind>emptyMap(),
                null, null);
    }

    /**
     * 创建源码编辑器推断结果。
     *
     * @param path 配置路径
     * @param rawFormat 源码格式
     * @return RAW_EDITOR 推断结果
     */
    private static Result createRawEditorResult(String path, ConfigFormat rawFormat) {
        return new Result(path, TemplateType.RAW_EDITOR, false, Collections.<String>emptyList(),
                ModernConfigListModels.ValueKind.STRING, Collections.<String>emptyList(),
                Collections.<String, ModernConfigListModels.ValueKind>emptyMap(), rawFormat, null);
    }

    /**
     * 创建增强选择器推断结果。
     *
     * @param path 配置路径
     * @param pickerKind 选择器子类
     * @return ENHANCED_PICKER 推断结果
     */
    private static Result createEnhancedPickerResult(String path, PickerKind pickerKind) {
        return new Result(path, TemplateType.ENHANCED_PICKER, false, Collections.<String>emptyList(),
                ModernConfigListModels.ValueKind.STRING, Collections.<String>emptyList(),
                Collections.<String, ModernConfigListModels.ValueKind>emptyMap(), null, pickerKind);
    }

    /**
     * 匹配明确指定格式的源码编辑 hint。
     *
     * @param hint 已 normalize 的 hint
     * @return 匹配到的 JSON/YAML 格式；未匹配返回 null
     */
    private static ConfigFormat matchSpecificRawFormat(String hint) {
        if ("json".equals(hint) || "json-editor".equals(hint)) {
            return ConfigFormat.JSON;
        }
        if ("yaml".equals(hint) || "yaml-editor".equals(hint)) {
            return ConfigFormat.YAML;
        }
        return null;
    }

    /**
     * 判断 hint 是否为通用源码编辑 hint（不指定格式）。
     *
     * @param hint 已 normalize 的 hint
     * @return 命中 raw/raw-editor/code/source 时返回 true
     */
    private static boolean isGenericRawHint(String hint) {
        return "raw".equals(hint) || "raw-editor".equals(hint) || "code".equals(hint) || "source".equals(hint);
    }

    /**
     * 匹配增强选择器 hint。
     *
     * @param hint 已 normalize 的 hint
     * @return 命中 color/resource/sound 系列 hint 时返回对应 PickerKind；未命中返回 null
     */
    private static PickerKind matchEnhancedPickerHint(String hint) {
        if ("color".equals(hint) || "colour".equals(hint) || "hex".equals(hint)) {
            return PickerKind.COLOR;
        }
        if ("resource".equals(hint) || "asset".equals(hint)) {
            return PickerKind.RESOURCE;
        }
        if ("sound".equals(hint) || "audio".equals(hint)) {
            return PickerKind.SOUND;
        }
        return null;
    }

    private static boolean shouldUseLongText(String value, ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        if (isLongTextHint(normalizeHint(fieldSpec == null ? "" : fieldSpec.getTemplateHint()))) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.length() >= LONG_TEXT_LENGTH_THRESHOLD;
    }

    private static boolean isIntegerNumber(ConfigNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        String text = node.asString("").trim().toLowerCase(Locale.ENGLISH);
        return !text.contains(".") && !text.contains("e");
    }

    private static boolean isIntegerDefault(Object defaultValue) {
        if (defaultValue instanceof Byte || defaultValue instanceof Short || defaultValue instanceof Integer
                || defaultValue instanceof Long) {
            return true;
        }
        if (defaultValue instanceof Float || defaultValue instanceof Double) {
            double value = ((Number) defaultValue).doubleValue();
            return value == Math.floor(value) && !Double.isInfinite(value);
        }
        return false;
    }

    private static boolean isLongTextHint(String hint) {
        return "longtext".equals(hint) || "long-text".equals(hint) || "multiline".equals(hint)
                || "textarea".equals(hint) || "text-area".equals(hint);
    }

    private static boolean isDynamicMapHint(String hint) {
        return "dynamic-map".equals(hint) || "dynamic_map".equals(hint) || "map-dynamic".equals(hint)
                || "key-value".equals(hint) || "key_value".equals(hint) || "keyvalue".equals(hint)
                || "key-value-map".equals(hint) || "key_value_map".equals(hint) || "kv-map".equals(hint);
    }

    private static boolean hasPresetDefinitions(ConfigNode node) {
        if (node == null || node.getType() != ConfigNode.NodeType.MAP || node.asMap() == null) {
            return false;
        }
        Map<String, ConfigNode> map = node.asMap();
        return isPresetStorage(map.get("_presets")) || isPresetStorage(map.get("presets"));
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

    private static String normalizeHint(String hint) {
        return hint == null ? "" : hint.trim().toLowerCase(Locale.ENGLISH);
    }

    /**
     * 基础模板类型。
     */
    enum TemplateType {
        STRING,
        NUMBER,
        BOOLEAN,
        NULL,
        CHOICE,
        LONG_TEXT,
        SIMPLE_LIST,
        TABLE,
        KEY_VALUE_MAP,
        PRESET_SELECTOR,
        OBJECT,
        /** 源码编辑器模板（JSON/YAML 子树文本编辑） */
        RAW_EDITOR,
        /** 增强选择器模板（颜色/资源/声音） */
        ENHANCED_PICKER,
        READ_ONLY
    }

    /**
     * 增强选择器子类。
     */
    enum PickerKind {
        /** 颜色选择器 */
        COLOR,
        /** 资源选择器 */
        RESOURCE,
        /** 声音选择器 */
        SOUND
    }

    /**
     * 类型推断结果。
     */
    static final class Result {

        private final String path;
        private final TemplateType templateType;
        private final boolean integerNumber;
        private final List<String> choiceOptions;
        private final ModernConfigListModels.ValueKind listValueKind;
        private final List<String> tableColumns;
        private final Map<String, ModernConfigListModels.ValueKind> tableColumnKinds;
        private final ConfigFormat rawFormat;
        private final PickerKind pickerKind;

        private Result(String path, TemplateType templateType, boolean integerNumber, List<String> choiceOptions,
                ModernConfigListModels.ValueKind listValueKind, List<String> tableColumns,
                Map<String, ModernConfigListModels.ValueKind> tableColumnKinds, ConfigFormat rawFormat,
                PickerKind pickerKind) {
            this.path = path == null ? "" : path;
            this.templateType = templateType == null ? TemplateType.READ_ONLY : templateType;
            this.integerNumber = integerNumber;
            this.choiceOptions = Collections.unmodifiableList(new ArrayList<String>(choiceOptions));
            this.listValueKind = listValueKind == null ? ModernConfigListModels.ValueKind.STRING : listValueKind;
            this.tableColumns = Collections.unmodifiableList(new ArrayList<String>(tableColumns));
            this.tableColumnKinds = Collections.unmodifiableMap(
                    new LinkedHashMap<String, ModernConfigListModels.ValueKind>(tableColumnKinds));
            this.rawFormat = rawFormat == null ? ConfigFormat.JSON : rawFormat;
            this.pickerKind = pickerKind;
        }

        String getPath() {
            return path;
        }

        TemplateType getTemplateType() {
            return templateType;
        }

        boolean isIntegerNumber() {
            return integerNumber;
        }

        boolean isEditable() {
            return templateType != TemplateType.READ_ONLY;
        }

        boolean shouldUseSegmentedChoice() {
            return templateType == TemplateType.CHOICE && choiceOptions.size() <= SEGMENTED_CHOICE_MAX_OPTIONS;
        }

        List<String> getChoiceOptions() {
            return choiceOptions;
        }

        ModernConfigListModels.ValueKind getListValueKind() {
            return listValueKind;
        }

        List<String> getTableColumns() {
            return tableColumns;
        }

        Map<String, ModernConfigListModels.ValueKind> getTableColumnKinds() {
            return tableColumnKinds;
        }

        /**
         * 获取源码编辑器的目标格式。
         *
         * <p>仅在 {@link TemplateType#RAW_EDITOR} 时有意义；其他类型默认返回 JSON。</p>
         *
         * @return 源码格式
         */
        ConfigFormat getRawFormat() {
            return rawFormat;
        }

        /**
         * 获取增强选择器的子类。
         *
         * <p>仅在 {@link TemplateType#ENHANCED_PICKER} 时有意义；其他类型返回 null。</p>
         *
         * @return 选择器子类；未指定时为 null
         */
        PickerKind getPickerKind() {
            return pickerKind;
        }
    }
}
