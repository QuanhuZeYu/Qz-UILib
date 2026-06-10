package club.heiqi.uilib.config;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 配置模板的服务端权威会话。
 */
final class ConfigTemplateRemoteSession {

    private final String sessionId;
    private final ConfigSyncTarget target;
    private final Configuration authoritativeConfiguration;
    private final Configuration draftConfiguration;
    private final ConfigSyncModels.ConfigDefinitionSnapshot definition;
    private final Object ownerPlayer;
    private final ConfigSyncModels.ConfigSessionState state = new ConfigSyncModels.ConfigSessionState();

    ConfigTemplateRemoteSession(ConfigSyncTarget target, Object ownerPlayer) {
        this.sessionId = UUID.randomUUID().toString();
        this.target = Objects.requireNonNull(target, "target");
        this.authoritativeConfiguration = Objects.requireNonNull(target.getConfiguration(), "configuration");
        List<ConfigSyncCategorySpec> categories = target.getCategories();
        this.definition = ConfigSyncModels.captureDefinition(authoritativeConfiguration, categories);
        this.draftConfiguration = ConfigSyncModels.copyConfiguration(authoritativeConfiguration, definition);
        this.ownerPlayer = ownerPlayer;
        state.sessionId = sessionId;
        state.screenId = target.getScreenId();
        state.remoteAvailable = true;
        state.statusMessage = "已连接服务端配置会话。";
        state.definition = definition;
        state.draft = ConfigSyncModels.captureDraft(draftConfiguration, definition);
    }

    String getSessionId() {
        return sessionId;
    }

    Object getOwnerPlayer() {
        return ownerPlayer;
    }

    String getScreenId() {
        return target.getScreenId();
    }

    synchronized ConfigSyncModels.ConfigSessionState snapshotState() {
        return state.copy();
    }

    synchronized ConfigSyncModels.ConfigFieldValidationResult applyChange(
            ConfigSyncModels.ConfigFieldChange change) {
        ConfigSyncModels.ConfigFieldValidationResult result =
                ConfigSyncModels.validateChange(draftConfiguration, definition, change, target);
        if (!result.accepted) {
            state.fieldErrors.put(result.fieldKey, result.message == null ? "" : result.message);
            state.statusMessage = "字段校验未通过。";
            return result;
        }
        ConfigSyncModels.ConfigDraftSnapshot nextDraft = snapshotDraft();
        nextDraft.values.put(change.fieldKey, change.draftValue == null ? "" : change.draftValue);
        ConfigSyncModels.applyDraft(draftConfiguration, definition, nextDraft);
        state.draft = snapshotDraft();
        state.fieldErrors.remove(change.fieldKey);
        state.statusMessage = "草稿已同步到服务端。";
        return result;
    }

    synchronized void applyDraftSnapshot(ConfigSyncModels.ConfigDraftSnapshot draft) {
        if (draft == null || draft.values == null || draft.values.isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<String, String> entry : draft.values.entrySet()) {
            ConfigSyncModels.ConfigFieldChange change = new ConfigSyncModels.ConfigFieldChange();
            change.fieldKey = entry.getKey();
            change.draftValue = entry.getValue();
            applyChange(change);
        }
        if (state.fieldErrors.isEmpty()) {
            state.statusMessage = "草稿已同步到服务端。";
        } else {
            state.statusMessage = "部分字段校验未通过，草稿未完全同步。";
        }
    }

    synchronized ConfigSyncModels.ConfigSaveResult save() {
        ConfigSyncModels.ConfigSaveResult result = new ConfigSyncModels.ConfigSaveResult();
        state.saving = true;
        try {
            if (!state.fieldErrors.isEmpty()) {
                state.statusMessage = "保存前校验失败。";
                result.success = false;
                result.message = state.statusMessage;
                result.committedDraft = snapshotDraft();
                return result;
            }
            for (ConfigSyncModels.ConfigCategorySnapshot category : definition.categories) {
                ConfigCategory authoritativeCategory =
                        ConfigSyncModels.resolveDefinitionCategory(authoritativeConfiguration, category);
                if (authoritativeCategory == null) {
                    String message = "配置同步分类不存在：" + category.categoryName;
                    state.statusMessage = "保存前校验失败。";
                    for (ConfigSyncModels.ConfigFieldSnapshot field : category.properties) {
                        state.fieldErrors.put(ConfigSyncModels.buildFieldKey(category.categoryName,
                                field.propertyName), message);
                    }
                    result.success = false;
                    result.message = message;
                    result.committedDraft = snapshotDraft();
                    return result;
                }
                for (ConfigSyncModels.ConfigFieldSnapshot field : category.properties) {
                    String fieldKey = ConfigSyncModels.buildFieldKey(category.categoryName, field.propertyName);
                    String draftValue = state.draft.values.get(fieldKey);
                    Property authoritativeProperty = authoritativeCategory.get(field.propertyName);
                    if (authoritativeProperty == null) {
                        String message = "配置同步字段不存在：" + fieldKey;
                        state.fieldErrors.put(fieldKey, "配置同步字段不存在。");
                        state.statusMessage = "保存前校验失败。";
                        result.success = false;
                        result.message = message;
                        result.committedDraft = snapshotDraft();
                        return result;
                    }
                    String validationError = ConfigSyncModels.validateDraft(target, category.categoryName,
                            field.propertyName, authoritativeProperty, draftValue);
                    if (validationError != null && !validationError.isEmpty()) {
                        state.fieldErrors.put(fieldKey, validationError);
                        state.statusMessage = "保存前校验失败。";
                        result.success = false;
                        result.message = validationError;
                        result.committedDraft = snapshotDraft();
                        return result;
                    }
                }
            }
            ForgeConfigTemplatePropertyDrafts.runWithRollback(collectBoundProperties(), new Runnable() {
                @Override
                public void run() {
                    ConfigSyncModels.applyDraft(authoritativeConfiguration, definition, state.draft);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    target.save();
                }
            });
            draftFromAuthoritative();
            state.fieldErrors.clear();
            state.statusMessage = "服务端配置已保存。";
            result.success = true;
            result.message = state.statusMessage;
            result.committedDraft = snapshotDraft();
            return result;
        } catch (RuntimeException exception) {
            draftFromAuthoritative();
            state.statusMessage = "服务端保存失败：" + readableMessage(exception);
            result.success = false;
            result.message = state.statusMessage;
            result.committedDraft = snapshotDraft();
            return result;
        } finally {
            state.saving = false;
        }
    }

    synchronized void refreshFromAuthoritative() {
        draftFromAuthoritative();
        state.fieldErrors.clear();
        state.statusMessage = "已从服务端权威配置刷新。";
    }

    private ConfigSyncModels.ConfigDraftSnapshot snapshotDraft() {
        return ConfigSyncModels.captureDraft(draftConfiguration, definition);
    }

    private void draftFromAuthoritative() {
        ConfigSyncModels.ConfigDraftSnapshot committedDraft =
                ConfigSyncModels.captureDraft(authoritativeConfiguration, definition);
        ConfigSyncModels.applyDraft(draftConfiguration, definition, committedDraft);
        state.draft = committedDraft;
    }

    private java.util.List<net.minecraftforge.common.config.Property> collectBoundProperties() {
        java.util.List<net.minecraftforge.common.config.Property> properties =
                new java.util.ArrayList<net.minecraftforge.common.config.Property>();
        for (ConfigSyncModels.ConfigCategorySnapshot category : definition.categories) {
            net.minecraftforge.common.config.ConfigCategory authoritativeCategory =
                    ConfigSyncModels.resolveDefinitionCategory(authoritativeConfiguration, category);
            if (authoritativeCategory == null) {
                continue;
            }
            for (ConfigSyncModels.ConfigFieldSnapshot field : category.properties) {
                net.minecraftforge.common.config.Property property = authoritativeCategory.get(field.propertyName);
                if (property != null) {
                    properties.add(property);
                }
            }
        }
        return properties;
    }

    private static String readableMessage(RuntimeException exception) {
        String message = exception == null ? "" : exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception == null ? "未知错误" : exception.getClass().getSimpleName();
        }
        return message;
    }
}
