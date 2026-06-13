package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
     * @param path 配置路径
     * @param node 当前配置节点
     * @param fieldSpec 字段规格
     * @return 推断结果
     */
    static Result infer(String path, ConfigNode node, ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        List<String> validValues = fieldSpec == null ? Collections.<String>emptyList() : fieldSpec.getValidValues();
        if (!validValues.isEmpty()) {
            return new Result(path, TemplateType.CHOICE, false, validValues);
        }

        ConfigNode.NodeType nodeType = node == null ? ConfigNode.NodeType.NULL : node.getType();
        if (nodeType == ConfigNode.NodeType.STRING) {
            String value = node.asString("");
            TemplateType type = shouldUseLongText(value, fieldSpec) ? TemplateType.LONG_TEXT : TemplateType.STRING;
            return new Result(path, type, false, Collections.<String>emptyList());
        }
        if (nodeType == ConfigNode.NodeType.NUMBER) {
            return new Result(path, TemplateType.NUMBER, isIntegerNumber(node), Collections.<String>emptyList());
        }
        if (nodeType == ConfigNode.NodeType.BOOLEAN) {
            return new Result(path, TemplateType.BOOLEAN, false, Collections.<String>emptyList());
        }
        if (nodeType == ConfigNode.NodeType.NULL) {
            return inferNullNode(path, fieldSpec);
        }
        return new Result(path, TemplateType.READ_ONLY, false, Collections.<String>emptyList());
    }

    private static Result inferNullNode(String path, ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        Object defaultValue = fieldSpec == null || !fieldSpec.hasDefaultValue() ? null : fieldSpec.getDefaultValue();
        String hint = normalizeHint(fieldSpec == null ? "" : fieldSpec.getTemplateHint());
        if (defaultValue instanceof Boolean || "boolean".equals(hint) || "bool".equals(hint)) {
            return new Result(path, TemplateType.BOOLEAN, false, Collections.<String>emptyList());
        }
        if (defaultValue instanceof Number || "number".equals(hint) || "numeric".equals(hint)) {
            return new Result(path, TemplateType.NUMBER, isIntegerDefault(defaultValue), Collections.<String>emptyList());
        }
        if (defaultValue instanceof String) {
            String value = (String) defaultValue;
            TemplateType type = shouldUseLongText(value, fieldSpec) ? TemplateType.LONG_TEXT : TemplateType.STRING;
            return new Result(path, type, false, Collections.<String>emptyList());
        }
        if (isLongTextHint(hint)) {
            return new Result(path, TemplateType.LONG_TEXT, false, Collections.<String>emptyList());
        }
        if ("string".equals(hint) || "text".equals(hint)) {
            return new Result(path, TemplateType.STRING, false, Collections.<String>emptyList());
        }
        return new Result(path, TemplateType.NULL, false, Collections.<String>emptyList());
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
        READ_ONLY
    }

    /**
     * 类型推断结果。
     */
    static final class Result {

        private final String path;
        private final TemplateType templateType;
        private final boolean integerNumber;
        private final List<String> choiceOptions;

        private Result(String path, TemplateType templateType, boolean integerNumber, List<String> choiceOptions) {
            this.path = path == null ? "" : path;
            this.templateType = templateType == null ? TemplateType.READ_ONLY : templateType;
            this.integerNumber = integerNumber;
            this.choiceOptions = Collections.unmodifiableList(new ArrayList<String>(choiceOptions));
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
    }
}
