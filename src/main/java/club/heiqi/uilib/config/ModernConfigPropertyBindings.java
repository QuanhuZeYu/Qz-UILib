package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.component.UiComponentRuntime;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置属性绑定工厂。
 */
final class ModernConfigPropertyBindings {

    static final int DEFAULT_TEXT_MAX_LENGTH = 512;
    static final int DEFAULT_LONG_TEXT_MAX_LENGTH = 4096;
    static final int DEFAULT_OBJECT_INLINE_DEPTH = 5;
    static final double NUMERIC_EPSILON = 1.0E-9D;
    private static final int SUMMARY_MAX_LENGTH = 120;

    private ModernConfigPropertyBindings() {
    }

    /**
     * 创建基础类型绑定列表。
     *
     * @param config 可变配置对象
     * @param fields 字段规格列表
     * @param changeListener 草稿变更监听器
     * @return 基础类型绑定列表
     */
    static List<ConfigPropertyBinding> createBindings(MutableConfig config,
            List<ModernConfigTemplateScreen.FieldSpec> fields, ChangeListener changeListener,
            UiComponentRuntime runtime) {
        List<ConfigPropertyBinding> bindings = new ArrayList<ConfigPropertyBinding>();
        if (config == null) {
            return bindings;
        }
        Map<String, ModernConfigTemplateScreen.FieldSpec> fieldsByPath = indexFields(fields);
        ConfigNode root = config.asImmutable();
        List<String> paths = collectPaths(root, fieldsByPath);
        if (paths.isEmpty()) {
            return bindings;
        }
        if (root != null && root.getType() == ConfigNode.NodeType.MAP) {
            bindings.add(new ModernNestedCategoryBinding(config, root, fieldsByPath, changeListener, runtime));
            return bindings;
        }
        for (String path : paths) {
            ConfigNode node = config.get(path);
            ModernConfigTemplateScreen.FieldSpec fieldSpec = fieldsByPath.get(path);
            ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(path, node, fieldSpec);
            bindings.add(createBinding(config, path, node, fieldSpec, inference, changeListener));
        }
        return bindings;
    }

    static ConfigPropertyBinding createBinding(MutableConfig config, String path, ConfigNode node,
            ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
            ChangeListener changeListener) {
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.PRESET_SELECTOR) {
            return new ModernPresetSelectorPropertyBinding(config, path, node, fieldSpec, inference, changeListener);
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.KEY_VALUE_MAP) {
            return new ModernKeyValueMapPropertyBinding(config, path, node, fieldSpec, inference, changeListener);
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.OBJECT) {
            return new ModernObjectPropertyBinding(config, path, node, fieldSpec, inference, changeListener);
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.TABLE) {
            return new ModernTablePropertyBinding(config, path, node, fieldSpec, inference, changeListener);
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.SIMPLE_LIST) {
            return new ModernSimpleListPropertyBinding(config, path, node, fieldSpec, inference, changeListener);
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.CHOICE) {
            return new ModernChoicePropertyBinding(config, path, node, fieldSpec, inference, changeListener);
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.LONG_TEXT) {
            return new ModernMultilineTextPropertyBinding(config, path, node, fieldSpec, inference, changeListener);
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.RAW_EDITOR) {
            return new RawEditorPropertyBinding(config, path, node, fieldSpec, inference, changeListener);
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.ENHANCED_PICKER) {
            return new EnhancedPickerPropertyBinding(config, path, node, fieldSpec, inference, changeListener);
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.STRING
                || inference.getTemplateType() == ModernConfigTypeInference.TemplateType.NUMBER
                || inference.getTemplateType() == ModernConfigTypeInference.TemplateType.BOOLEAN
                || inference.getTemplateType() == ModernConfigTypeInference.TemplateType.NULL) {
            return new ModernPrimitivePropertyBinding(config, path, node, fieldSpec, inference, changeListener);
        }
        return new ModernReadOnlyPathBinding(config, path, node, fieldSpec, inference, changeListener);
    }

    static Map<String, ModernConfigTemplateScreen.FieldSpec> indexFields(
            List<ModernConfigTemplateScreen.FieldSpec> fields) {
        Map<String, ModernConfigTemplateScreen.FieldSpec> fieldsByPath =
                new LinkedHashMap<String, ModernConfigTemplateScreen.FieldSpec>();
        if (fields == null) {
            return fieldsByPath;
        }
        for (ModernConfigTemplateScreen.FieldSpec field : fields) {
            if (field != null && !field.getPath().isEmpty()) {
                fieldsByPath.put(field.getPath(), field);
            }
        }
        return fieldsByPath;
    }

    private static List<String> collectPaths(ConfigNode root,
            Map<String, ModernConfigTemplateScreen.FieldSpec> fieldsByPath) {
        List<String> paths = new ArrayList<String>();
        collectNodePaths("", root, paths);
        for (String fieldPath : fieldsByPath.keySet()) {
            if (!paths.contains(fieldPath)) {
                paths.add(fieldPath);
            }
        }
        Collections.sort(paths, new Comparator<String>() {
            @Override
            public int compare(String first, String second) {
                return first.compareTo(second);
            }
        });
        return paths;
    }

    private static void collectNodePaths(String path, ConfigNode node, List<String> paths) {
        if (node == null) {
            return;
        }
        if (!path.isEmpty()) {
            paths.add(path);
        }
        if (node.getType() != ConfigNode.NodeType.MAP) {
            return;
        }
        Map<String, ConfigNode> map = node.asMap();
        if (map == null || map.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<String>(map.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            collectNodePaths(path.isEmpty() ? key : path + "." + key, map.get(key), paths);
        }
    }

    static String formatSummary(ConfigNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.getType() == ConfigNode.NodeType.MAP) {
            Map<String, ConfigNode> map = node.asMap();
            int size = map == null ? 0 : map.size();
            return "子项 " + size + " 个";
        }
        if (node.getType() == ConfigNode.NodeType.LIST) {
            List<ConfigNode> list = node.asList();
            int size = list == null ? 0 : list.size();
            return "元素 " + size + " 个";
        }
        return truncate(node.asString(""));
    }

    static int resolveMaxLength(ModernConfigTemplateScreen.FieldSpec fieldSpec, int defaultValue) {
        Integer maxLength = fieldSpec == null ? null : fieldSpec.getMaxLength();
        return maxLength == null ? defaultValue : Math.max(1, maxLength.intValue());
    }

    static boolean hasFiniteRange(ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        return fieldSpec != null && fieldSpec.getMinValue() != null && fieldSpec.getMaxValue() != null
                && fieldSpec.getMaxValue().doubleValue() > fieldSpec.getMinValue().doubleValue();
    }

    static double resolveStep(ModernConfigTemplateScreen.FieldSpec fieldSpec, boolean integerType) {
        if (fieldSpec != null && fieldSpec.getStep() != null && fieldSpec.getStep().doubleValue() > 0.0D) {
            return fieldSpec.getStep().doubleValue();
        }
        return integerType ? 1.0D : 0.0D;
    }

    static String formatNumber(double value, boolean integerType) {
        if (integerType) {
            return Long.toString(Math.round(value));
        }
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    static String formatDisplayLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String leaf = trimmed;
        int dotIndex = leaf.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < leaf.length() - 1) {
            leaf = leaf.substring(dotIndex + 1);
        }
        StringBuilder builder = new StringBuilder(leaf.length() + 8);
        char previous = 0;
        for (int index = 0; index < leaf.length(); index++) {
            char current = leaf.charAt(index);
            if (current == '_' || current == '-' || current == '.') {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ' ') {
                    builder.append(' ');
                }
                previous = current;
                continue;
            }
            if (Character.isUpperCase(current) && builder.length() > 0 && previous != ' '
                    && Character.isLowerCase(previous)) {
                builder.append(' ');
            }
            builder.append(current);
            previous = current;
        }
        String[] words = builder.toString().trim().split("\\s+");
        StringBuilder result = new StringBuilder(builder.length() + 4);
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            if (word.length() == 1) {
                result.append(word.toUpperCase(Locale.ENGLISH));
                continue;
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            result.append(word.substring(1));
        }
        return result.toString();
    }

    private static String formatType(ConfigNode node, ModernConfigTypeInference.Result inference) {
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.CHOICE) {
            return "选项";
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.LONG_TEXT) {
            return "长文本";
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.SIMPLE_LIST) {
            return "列表";
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.TABLE) {
            return "表格";
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.KEY_VALUE_MAP) {
            return "动态 Map";
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.PRESET_SELECTOR) {
            return "预设";
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.OBJECT) {
            return "对象";
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.RAW_EDITOR) {
            return "源码";
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.ENHANCED_PICKER) {
            return "颜色/资源/声音";
        }
        if (inference.getTemplateType() == ModernConfigTypeInference.TemplateType.NULL) {
            return "空值";
        }
        if (node == null || node.isNull()) {
            return "空值";
        }
        if (node.getType() == ConfigNode.NodeType.STRING) {
            return "文本";
        }
        if (node.getType() == ConfigNode.NodeType.NUMBER) {
            return "数值";
        }
        if (node.getType() == ConfigNode.NodeType.BOOLEAN) {
            return "开关";
        }
        if (node.getType() == ConfigNode.NodeType.MAP) {
            return "对象";
        }
        if (node.getType() == ConfigNode.NodeType.LIST) {
            return "列表";
        }
        return node.getType().name();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = normalizeInlineText(value);
        if (normalized.length() <= SUMMARY_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, SUMMARY_MAX_LENGTH - 3) + "...";
    }

    /**
     * 草稿变更监听器。
     */
    interface ChangeListener {

        /**
         * 草稿内容发生变化。
         */
        void onDraftChanged();
    }

    /**
     * 现代配置字段绑定。
     */
    static abstract class ConfigPropertyBinding implements ModernConfigBindingLifecycle {

        private final MutableConfig config;
        private final String path;
        private final ModernConfigTemplateScreen.FieldSpec fieldSpec;
        private final ModernConfigTypeInference.Result inference;
        private final ChangeListener changeListener;
        private final String displayName;
        private TextNode validationText;

        ConfigPropertyBinding(MutableConfig config, String path, ConfigNode node,
                ModernConfigTemplateScreen.FieldSpec fieldSpec, ModernConfigTypeInference.Result inference,
                ChangeListener changeListener) {
            this.config = Objects.requireNonNull(config, "config");
            this.path = path == null ? "" : path;
            this.fieldSpec = fieldSpec;
            this.inference = inference;
            this.changeListener = changeListener;
            String label = fieldSpec == null ? "" : fieldSpec.getLabel();
            this.displayName = label == null || label.isEmpty() ? formatDisplayLabel(this.path) : label;
        }

        @Override
        public boolean canReuse(String targetPath, ModernConfigTypeInference.TemplateType targetTemplateType) {
            return false;
        }

        @Override
        public void reset() {
            setValidationError("");
        }

        @Override
        public void dispose() {
        }

        final ElementNode createCard(UiDocument document, ForgeConfigTemplateScreen.Theme theme) {
            return createCardElement(document, theme, createEditorElement(document, theme));
        }

        protected abstract ElementNode createEditorElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme);

        abstract boolean isDirty();

        abstract void restoreCurrentValue();

        abstract void restoreDefaultValue();

        abstract String validateDraft();

        abstract void applyDraft();

        int getDirtyCount() {
            return isDirty() ? 1 : 0;
        }

        boolean canRestoreDefaultValue() {
            return fieldSpec != null && fieldSpec.hasDefaultValue();
        }

        final String getDisplayName() {
            return displayName;
        }

        final void setValidationError(String message) {
            if (validationText != null) {
                validationText.setText(message == null ? "" : message);
            }
        }

        protected final MutableConfig getConfig() {
            return config;
        }

        protected final String getPath() {
            return path;
        }

        protected final ModernConfigTemplateScreen.FieldSpec getFieldSpec() {
            return fieldSpec;
        }

        protected final ModernConfigTypeInference.Result getInference() {
            return inference;
        }

        protected final ConfigNode getCurrentNode() {
            return config.get(path);
        }

        protected final void notifyDraftChanged() {
            setValidationError("");
            if (changeListener != null) {
                changeListener.onDraftChanged();
            }
        }

        protected final Object getDefaultValue() {
            return fieldSpec != null && fieldSpec.hasDefaultValue() ? fieldSpec.getDefaultValue() : null;
        }

        protected String buildMetadataText() {
            StringBuilder builder = new StringBuilder();
            builder.append("路径：").append(path.isEmpty() ? "根配置" : path);
            builder.append(" | 类型：").append(formatType(getCurrentNode(), inference));
            if (canRestoreDefaultValue()) {
                builder.append(" | 默认：").append(String.valueOf(getDefaultValue()));
            }
            if (hasFiniteRange(fieldSpec)) {
                builder.append(" | 范围：").append(fieldSpec.getMinValue()).append(" ~ ")
                        .append(fieldSpec.getMaxValue());
            }
            return builder.toString();
        }

        protected String buildHelperText() {
            String description = fieldSpec == null ? "" : fieldSpec.getDescription();
            return normalizeInlineText(description);
        }

        private ElementNode createCardElement(UiDocument document, ForgeConfigTemplateScreen.Theme theme,
                ElementNode editorElement) {
            ElementNode card = document.div();
            card.setAttribute("data-modern-config-path", path);
            card.style()
                    .setPadding(UiStyleLength.px(14))
                    .setBackgroundColor(0xFF162132)
                    .setBorderColor(0xFF334155)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderRadius(UiStyleLength.px(14));

            ElementNode title = document.div();
            title.style().setTextColor(0xFFF8FAFC);
            title.appendText(displayName);
            card.append(title);

            ElementNode metadata = document.div();
            metadata.style().setMargin(UiStyleLength.px(6)).setTextColor(0xFF93C5FD);
            metadata.appendText(buildMetadataText());
            card.append(metadata);

            String helperText = buildHelperText();
            if (!helperText.isEmpty()) {
                ElementNode helper = document.div();
                helper.style().setMargin(UiStyleLength.px(6)).setTextColor(0xFFCBD5E1);
                helper.appendText(helperText);
                card.append(helper);
            }

            ElementNode editorShell = document.div();
            editorShell.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.COLUMN)
                    .setRowGap(UiStyleLength.px(6))
                    .setMargin(UiStyleLength.px(8));
            editorShell.append(editorElement);
            ElementNode validationElement = document.div();
            validationElement.style().setTextColor(0xFFFCA5A5);
            validationText = validationElement.appendText("");
            editorShell.append(validationElement);
            card.append(editorShell);
            return card;
        }
    }

    private static String normalizeInlineText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', ' ').replace('\n', ' ').trim().replaceAll("\\s+", " ");
    }
}
