package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 配置同步模型与副本工具。
 */
final class ConfigSyncModels {

    private ConfigSyncModels() {}

    /**
     * 从 Forge 配置构建可传输的配置定义快照。
     *
     * @param configuration 原始配置
     * @param categories 目标分类
     * @return 配置定义
     */
    static ConfigDefinitionSnapshot captureDefinition(Configuration configuration,
            List<ConfigSyncCategorySpec> categories) {
        ConfigDefinitionSnapshot definition = new ConfigDefinitionSnapshot();
        if (configuration == null || categories == null) {
            return definition;
        }
        for (ConfigSyncCategorySpec categorySpec : categories) {
            if (categorySpec == null) {
                continue;
            }
            ConfigCategory category = resolveCategory(configuration, categorySpec.getCategoryName());
            if (category == null || !category.showInGui()) {
                continue;
            }
            ConfigCategorySnapshot categorySnapshot = new ConfigCategorySnapshot();
            categorySnapshot.categoryName = categorySpec.getCategoryName();
            categorySnapshot.displayTitle = categorySpec.getDisplayTitle();
            categorySnapshot.description = mergeCategoryDescription(categorySpec, category);
            categorySnapshot.showInGui = category.showInGui();
            categorySnapshot.requiresMcRestart = category.requiresMcRestart();
            categorySnapshot.requiresWorldRestart = category.requiresWorldRestart();
            for (Property property : category.getOrderedValues()) {
                if (property == null || !property.showInGui()) {
                    continue;
                }
                categorySnapshot.properties.add(ConfigFieldSnapshot.fromProperty(categorySnapshot.categoryName,
                        property));
            }
            definition.categories.add(categorySnapshot);
        }
        return definition;
    }

    static ConfigDefinitionSnapshot captureDefinitionFromTemplate(Configuration configuration,
            List<ForgeConfigTemplateScreen.CategorySpec> categories) {
        List<ConfigSyncCategorySpec> converted = new ArrayList<ConfigSyncCategorySpec>();
        if (categories != null) {
            for (ForgeConfigTemplateScreen.CategorySpec categorySpec : categories) {
                if (categorySpec == null) {
                    continue;
                }
                converted.add(new ConfigSyncCategorySpec(categorySpec.getCategoryName(),
                        categorySpec.getDisplayTitle(), categorySpec.getDescription()));
            }
        }
        return captureDefinition(configuration, converted);
    }

    /**
     * 复制当前配置为仅用于草稿与展示的内存配置。
     *
     * @param configuration 原始配置
     * @param definition 配置定义
     * @return 副本配置
     */
    static Configuration copyConfiguration(Configuration configuration, ConfigDefinitionSnapshot definition) {
        Configuration copy = new Configuration();
        if (configuration == null || definition == null) {
            return copy;
        }
        for (ConfigCategorySnapshot categorySnapshot : definition.categories) {
            if (categorySnapshot == null) {
                continue;
            }
            ConfigCategory originalCategory = resolveCategory(configuration, categorySnapshot.categoryName);
            if (originalCategory == null) {
                continue;
            }
            ConfigCategory copiedCategory = copy.getCategory(categorySnapshot.categoryName);
            copiedCategory.setShowInGui(categorySnapshot.showInGui);
            copiedCategory.setRequiresMcRestart(categorySnapshot.requiresMcRestart);
            if (!categorySnapshot.requiresMcRestart) {
                copiedCategory.setRequiresWorldRestart(categorySnapshot.requiresWorldRestart);
            }
            copiedCategory.setComment(originalCategory.getComment());
            copiedCategory.setLanguageKey(originalCategory.getLanguagekey());
            copiedCategory.setPropertyOrder(new ArrayList<String>(originalCategory.getPropertyOrder()));
            for (ConfigFieldSnapshot field : categorySnapshot.properties) {
                copiedCategory.put(field.propertyName, copyProperty(field));
            }
        }
        return copy;
    }

    /**
     * 读取当前配置副本中的字段草稿文本。
     *
     * @param configuration 配置副本
     * @param definition 定义快照
     * @return 草稿快照
     */
    static ConfigDraftSnapshot captureDraft(Configuration configuration, ConfigDefinitionSnapshot definition) {
        ConfigDraftSnapshot snapshot = new ConfigDraftSnapshot();
        if (configuration == null || definition == null) {
            return snapshot;
        }
        for (ConfigCategorySnapshot category : definition.categories) {
            ConfigCategory resolvedCategory = resolveCategory(configuration, category.categoryName);
            if (resolvedCategory == null) {
                continue;
            }
            for (ConfigFieldSnapshot field : category.properties) {
                Property property = resolvedCategory.get(field.propertyName);
                if (property == null) {
                    continue;
                }
                snapshot.values.put(buildFieldKey(category.categoryName, field.propertyName),
                        field.readDraftValue(property));
            }
        }
        return snapshot;
    }

    /**
     * 将草稿快照写回配置副本。
     *
     * @param configuration 配置副本
     * @param definition 定义快照
     * @param draft 草稿快照
     */
    static void applyDraft(Configuration configuration, ConfigDefinitionSnapshot definition, ConfigDraftSnapshot draft) {
        if (configuration == null || definition == null || draft == null) {
            return;
        }
        for (ConfigCategorySnapshot category : definition.categories) {
            ConfigCategory resolvedCategory = resolveCategory(configuration, category.categoryName);
            if (resolvedCategory == null) {
                continue;
            }
            for (ConfigFieldSnapshot field : category.properties) {
                String key = buildFieldKey(category.categoryName, field.propertyName);
                if (!draft.values.containsKey(key)) {
                    continue;
                }
                Property property = resolvedCategory.get(field.propertyName);
                if (property == null) {
                    continue;
                }
                ForgeConfigTemplatePropertyDrafts.applyDraft(property, draft.values.get(key));
            }
        }
    }

    /**
     * 校验字段草稿。
     *
     * @param configuration 配置副本
     * @param definition 定义快照
     * @param change 变更
     * @return 校验结果
     */
    static ConfigFieldValidationResult validateChange(Configuration configuration,
            ConfigDefinitionSnapshot definition, ConfigFieldChange change) {
        ConfigFieldValidationResult result = new ConfigFieldValidationResult();
        result.fieldKey = change == null ? "" : change.fieldKey;
        result.accepted = false;
        if (configuration == null || definition == null || change == null) {
            result.message = "配置同步字段不存在。";
            return result;
        }
        ConfigFieldRef fieldRef = findField(definition, change.fieldKey);
        if (fieldRef == null) {
            result.message = "配置同步字段不存在。";
            return result;
        }
        Property property = resolveProperty(configuration, fieldRef);
        if (property == null) {
            result.message = "配置同步字段不存在。";
            return result;
        }
        String validationError = ForgeConfigTemplatePropertyDrafts.validateDraft(property, change.draftValue);
        if (validationError != null && !validationError.isEmpty()) {
            result.message = validationError;
            return result;
        }
        result.accepted = true;
        result.message = "";
        return result;
    }

    /**
     * 构建字段键。
     *
     * @param categoryName 分类名
     * @param propertyName 属性名
     * @return 规范化字段键
     */
    static String buildFieldKey(String categoryName, String propertyName) {
        return normalizeKeyPart(categoryName) + ":" + normalizeKeyPart(propertyName);
    }

    /**
     * 解析字段键。
     *
     * @param definition 定义快照
     * @param fieldKey 字段键
     * @return 字段引用；不存在时返回 null
     */
    static ConfigFieldRef findField(ConfigDefinitionSnapshot definition, String fieldKey) {
        if (definition == null || fieldKey == null) {
            return null;
        }
        for (ConfigCategorySnapshot category : definition.categories) {
            for (ConfigFieldSnapshot field : category.properties) {
                if (buildFieldKey(category.categoryName, field.propertyName).equals(fieldKey)) {
                    return new ConfigFieldRef(category.categoryName, field.propertyName, field);
                }
            }
        }
        return null;
    }

    private static Property resolveProperty(Configuration configuration, ConfigFieldRef fieldRef) {
        ConfigCategory category = resolveCategory(configuration, fieldRef.categoryName);
        return category == null ? null : category.get(fieldRef.propertyName);
    }

    private static ConfigCategory resolveCategory(Configuration configuration, String categoryName) {
        if (configuration == null || categoryName == null || categoryName.trim().isEmpty()) {
            return null;
        }
        String requestedName = categoryName.trim();
        if (configuration.hasCategory(requestedName)) {
            return configuration.getCategory(requestedName);
        }
        String lowerCaseName = requestedName.toLowerCase(Locale.ENGLISH);
        if (configuration.hasCategory(lowerCaseName)) {
            return configuration.getCategory(lowerCaseName);
        }
        return null;
    }

    private static String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private static String mergeCategoryDescription(ConfigSyncCategorySpec categorySpec,
            ConfigCategory category) {
        String specDescription = normalizeInlineText(categorySpec.getDescription());
        String categoryComment = category == null ? "" : normalizeInlineText(category.getComment());
        if (specDescription.isEmpty()) {
            return categoryComment;
        }
        if (categoryComment.isEmpty() || specDescription.equals(categoryComment)) {
            return specDescription;
        }
        return specDescription + " " + categoryComment;
    }

    private static String normalizeInlineText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', ' ').replace('\n', ' ').trim().replaceAll("\\s+", " ");
    }

    private static Property copyProperty(ConfigFieldSnapshot field) {
        Property copy = field.createPropertyCopy();
        copy.comment = field.comment == null ? "" : field.comment;
        copy.setShowInGui(field.showInGui);
        copy.setRequiresMcRestart(field.requiresMcRestart);
        if (!field.requiresMcRestart) {
            copy.setRequiresWorldRestart(field.requiresWorldRestart);
        }
        copy.setLanguageKey(field.languageKey);
        copy.setValidationPattern(field.validationRegex == null || field.validationRegex.isEmpty()
                ? null : java.util.regex.Pattern.compile(field.validationRegex));
        copy.setConfigEntryClass(null);
        copy.setArrayEntryClass(null);
        copy.setMaxListLength(field.maxListLength);
        copy.setIsListLengthFixed(field.listLengthFixed);
        if (field.validValues != null && !field.validValues.isEmpty()) {
            copy.setValidValues(field.validValues.toArray(new String[field.validValues.size()]));
        }
        if (field.minValue != null && !field.minValue.isEmpty()) {
            if (field.type == Property.Type.INTEGER) {
                copy.setMinValue(parseInteger(field.minValue, Integer.MIN_VALUE));
            } else if (field.type == Property.Type.DOUBLE) {
                copy.setMinValue(parseDouble(field.minValue, -Double.MAX_VALUE));
            }
        }
        if (field.maxValue != null && !field.maxValue.isEmpty()) {
            if (field.type == Property.Type.INTEGER) {
                copy.setMaxValue(parseInteger(field.maxValue, Integer.MAX_VALUE));
            } else if (field.type == Property.Type.DOUBLE) {
                copy.setMaxValue(parseDouble(field.maxValue, Double.MAX_VALUE));
            }
        }
        return copy;
    }

    private static int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value == null ? "" : value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /**
     * 配置定义快照。
     */
    static final class ConfigDefinitionSnapshot {
        List<ConfigCategorySnapshot> categories = new ArrayList<ConfigCategorySnapshot>();
    }

    /**
     * 分类快照。
     */
    static final class ConfigCategorySnapshot {
        String categoryName = "";
        String displayTitle = "";
        String description = "";
        boolean showInGui = true;
        boolean requiresMcRestart;
        boolean requiresWorldRestart;
        List<ConfigFieldSnapshot> properties = new ArrayList<ConfigFieldSnapshot>();
    }

    /**
     * 字段快照。
     */
    static final class ConfigFieldSnapshot {
        String categoryName = "";
        String propertyName = "";
        Property.Type type = Property.Type.STRING;
        boolean list;
        String comment = "";
        String languageKey = "";
        String currentValue = "";
        List<String> currentValues = new ArrayList<String>();
        String defaultValue = "";
        List<String> defaultValues = new ArrayList<String>();
        List<String> validValues = new ArrayList<String>();
        String minValue = "";
        String maxValue = "";
        boolean showInGui = true;
        boolean requiresMcRestart;
        boolean requiresWorldRestart;
        boolean listLengthFixed;
        int maxListLength = -1;
        String validationRegex = "";

        static ConfigFieldSnapshot fromProperty(String categoryName, Property property) {
            ConfigFieldSnapshot snapshot = new ConfigFieldSnapshot();
            snapshot.categoryName = categoryName == null ? "" : categoryName;
            snapshot.propertyName = property.getName();
            snapshot.type = property.getType();
            snapshot.list = property.isList();
            snapshot.comment = property.comment == null ? "" : property.comment;
            snapshot.languageKey = property.getLanguageKey() == null ? "" : property.getLanguageKey();
            snapshot.showInGui = property.showInGui();
            snapshot.requiresMcRestart = property.requiresMcRestart();
            snapshot.requiresWorldRestart = property.requiresWorldRestart();
            snapshot.listLengthFixed = property.isListLengthFixed();
            snapshot.maxListLength = property.getMaxListLength();
            snapshot.minValue = property.getMinValue() == null ? "" : property.getMinValue();
            snapshot.maxValue = property.getMaxValue() == null ? "" : property.getMaxValue();
            snapshot.defaultValue = property.getDefault() == null ? "" : property.getDefault();
            snapshot.defaultValues = toList(property.getDefaults());
            snapshot.validValues = toList(property.getValidValues());
            snapshot.validationRegex = property.getValidationPattern() == null ? ""
                    : property.getValidationPattern().pattern();
            if (snapshot.list) {
                snapshot.currentValues = toList(property.getStringList());
            } else {
                snapshot.currentValue = property.getString() == null ? "" : property.getString();
            }
            return snapshot;
        }

        String readDraftValue(Property property) {
            if (property == null) {
                return "";
            }
            if (list) {
                return ForgeConfigTemplatePropertyDrafts.readFullListDisplayValue(property);
            }
            return ForgeConfigTemplatePropertyDrafts.readCurrentDisplayValue(property);
        }

        Property createPropertyCopy() {
            Property copy = list
                    ? new Property(propertyName, currentValues.toArray(new String[currentValues.size()]), type,
                            languageKey)
                    : new Property(propertyName, currentValue, type,
                            validValues.toArray(new String[validValues.size()]), languageKey);
            if (list && validValues != null && !validValues.isEmpty()) {
                copy.setValidValues(validValues.toArray(new String[validValues.size()]));
            }
            if (list) {
                copy.setDefaultValues(defaultValues.toArray(new String[defaultValues.size()]));
            } else {
                copy.setDefaultValue(defaultValue);
            }
            return copy;
        }

        private static List<String> toList(String[] values) {
            if (values == null || values.length == 0) {
                return new ArrayList<String>();
            }
            List<String> result = new ArrayList<String>(values.length);
            Collections.addAll(result, values);
            return result;
        }
    }

    /**
     * 草稿快照。
     */
    static final class ConfigDraftSnapshot {
        Map<String, String> values = new LinkedHashMap<String, String>();

        ConfigDraftSnapshot copy() {
            ConfigDraftSnapshot copy = new ConfigDraftSnapshot();
            if (values != null) {
                copy.values = new LinkedHashMap<String, String>(values);
            }
            return copy;
        }
    }

    /**
     * 字段变更事件。
     */
    static final class ConfigFieldChange {
        String fieldKey = "";
        String draftValue = "";
    }

    /**
     * 字段校验结果。
     */
    static final class ConfigFieldValidationResult {
        String fieldKey = "";
        boolean accepted;
        String message = "";
    }

    /**
     * 保存请求。
     */
    static final class ConfigSaveRequest {
        String sessionId = "";
        ConfigDraftSnapshot draft;
    }

    /**
     * 保存结果。
     */
    static final class ConfigSaveResult {
        boolean success;
        String message = "";
        ConfigDraftSnapshot committedDraft = new ConfigDraftSnapshot();
    }

    /**
     * 会话状态 store 快照。
     */
    static final class ConfigSessionState {
        String sessionId = "";
        String screenId = "";
        boolean remoteAvailable;
        boolean readOnly;
        boolean saving;
        String statusMessage = "";
        ConfigDefinitionSnapshot definition = new ConfigDefinitionSnapshot();
        ConfigDraftSnapshot draft = new ConfigDraftSnapshot();
        Map<String, String> fieldErrors = new LinkedHashMap<String, String>();

        ConfigSessionState copy() {
            ConfigSessionState copy = new ConfigSessionState();
            copy.sessionId = sessionId;
            copy.screenId = screenId;
            copy.remoteAvailable = remoteAvailable;
            copy.readOnly = readOnly;
            copy.saving = saving;
            copy.statusMessage = statusMessage;
            copy.definition = ConfigSyncJson.fromJson(ConfigSyncJson.toJson(definition),
                    ConfigDefinitionSnapshot.class);
            copy.draft = draft == null ? new ConfigDraftSnapshot() : draft.copy();
            copy.fieldErrors = fieldErrors == null
                    ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(fieldErrors);
            return copy;
        }
    }

    /**
     * 打开会话请求。
     */
    static final class ConfigSessionOpenRequest {
        String screenId = "";
    }

    /**
     * 打开会话响应。
     */
    static final class ConfigSessionOpenResponse {
        boolean remoteAvailable;
        String sessionId = "";
        String screenId = "";
        String message = "";
        ConfigDefinitionSnapshot definition = new ConfigDefinitionSnapshot();
        ConfigDraftSnapshot draft = new ConfigDraftSnapshot();
    }

    /**
     * 字段引用。
     */
    static final class ConfigFieldRef {
        final String categoryName;
        final String propertyName;
        final ConfigFieldSnapshot field;

        ConfigFieldRef(String categoryName, String propertyName, ConfigFieldSnapshot field) {
            this.categoryName = categoryName;
            this.propertyName = propertyName;
            this.field = field;
        }
    }
}
