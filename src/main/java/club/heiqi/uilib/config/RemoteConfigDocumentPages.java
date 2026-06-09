package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import club.heiqi.uilib.ui.remote.RemoteDocumentPage;
import club.heiqi.uilib.ui.remote.RemoteDocumentPages;
import club.heiqi.uilib.ui.remote.RemoteDocumentResourcePolicy;
import club.heiqi.uilib.ui.remote.RemoteDocumentSessionCloseHandler;
import club.heiqi.uilib.ui.remote.RemoteDocumentSubmitEvent;
import club.heiqi.uilib.ui.remote.RemoteDocumentSubmitHandler;

/**
 * 基于 RemoteDocumentPages 的服务端权威配置远程页面入口。
 *
 * <p>该入口复用 `ConfigTemplateSyncManager` 的配置目标、会话、草稿与保存模型，
 * 远程 HTML 页只负责展示字段和采集表单，不单独维护配置规则。</p>
 */
public final class RemoteConfigDocumentPages {

    private static final String FORM_ID = "qz-config-session-form";
    private static final String ACTION_SAVE = "config-save";
    private static final String ACTION_REFRESH = "config-refresh";
    private static final String ACTION_RESTORE = "config-restore";
    private static final Map<String, String> configSessionIdsByRemotePageSession =
            new ConcurrentHashMap<String, String>();
    private static final Map<String, String> remotePageSessionIdsByConfigSession =
            new ConcurrentHashMap<String, String>();
    private static final Map<String, Object> playersByConfigSession =
            new ConcurrentHashMap<String, Object>();
    private static final Set<String> replacingConfigSessionIds =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private RemoteConfigDocumentPages() {}

    /**
     * 向指定玩家打开配置远程页。
     *
     * @param player 目标玩家
     * @param screenId 配置目标标识
     * @return 远程页面会话标识
     */
    public static String open(Object player, String screenId) {
        ConfigTemplateRemoteSession session = ConfigTemplateSyncManager.getInstance()
                .openServerSession(screenId, player);
        return openSession(player, session, null);
    }

    private static String openSession(final Object player, final ConfigTemplateRemoteSession session,
            String statusOverride) {
        ConfigSyncTarget target = ConfigTemplateSyncManager.getInstance().getTarget(session.getScreenId());
        if (target == null) {
            throw new IllegalArgumentException("配置同步目标不存在: " + session.getScreenId());
        }
        ConfigSyncModels.ConfigSessionState state = session.snapshotState();
        RemoteDocumentPage page = buildPage(target, state, statusOverride);
        final String configSessionId = session.getSessionId();
        final boolean replacing = remotePageSessionIdsByConfigSession.containsKey(configSessionId);
        if (replacing) {
            replacingConfigSessionIds.add(configSessionId);
        }
        String remotePageSessionId;
        try {
            remotePageSessionId = RemoteDocumentPages.open(player, page, new RemoteDocumentSubmitHandler() {
                @Override
                public void onSubmit(RemoteDocumentSubmitEvent event) {
                    handleSubmit(player, session, event);
                }
            }, new RemoteDocumentSessionCloseHandler() {
                @Override
                public void onClosed(Object closedPlayer, String remotePageSessionId) {
                    handleRemotePageClosed(session, closedPlayer, remotePageSessionId);
                }
            });
        } finally {
            if (replacing) {
                replacingConfigSessionIds.remove(configSessionId);
            }
        }
        remotePageSessionIdsByConfigSession.put(configSessionId, remotePageSessionId);
        configSessionIdsByRemotePageSession.put(remotePageSessionId, configSessionId);
        playersByConfigSession.put(configSessionId, player);
        return remotePageSessionId;
    }

    /**
     * 玩家离线时清理远程配置页绑定索引。
     *
     * @param player 离线玩家
     */
    public static void onServerPlayerLeft(Object player) {
        if (player == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : playersByConfigSession.entrySet()) {
            if (matchesPlayer(entry.getValue(), player)) {
                String remotePageSessionId = remotePageSessionIdsByConfigSession.get(entry.getKey());
                if (remotePageSessionId != null) {
                    RemoteDocumentPages.closeSession(player, remotePageSessionId);
                }
                removeBinding(entry.getKey());
            }
        }
    }

    private static void handleSubmit(Object player, ConfigTemplateRemoteSession session, RemoteDocumentSubmitEvent event) {
        if (event == null) {
            return;
        }
        String submitter = normalize(event.getFirstValue("submitter"));
        if ("refresh".equals(submitter) || ACTION_REFRESH.equals(event.getAction())
                || "restore".equals(submitter) || ACTION_RESTORE.equals(event.getAction())) {
            session.refreshFromAuthoritative();
            openSession(player, session, "已从服务端权威配置刷新。");
            return;
        }

        ConfigSyncModels.ConfigDraftSnapshot draft = extractDraftFromSubmit(session.snapshotState(), event.getValues());
        ConfigSyncModels.ConfigSaveResult saveResult =
                ConfigTemplateSyncManager.getInstance().saveServerSession(session.getSessionId(), player, draft);
        String status = saveResult.message == null || saveResult.message.isEmpty()
                ? (saveResult.success ? "服务端配置已保存。" : "服务端保存失败。")
                : saveResult.message;
        openSession(player, session, status);
    }

    static void resetForTests() {
        configSessionIdsByRemotePageSession.clear();
        remotePageSessionIdsByConfigSession.clear();
        playersByConfigSession.clear();
        replacingConfigSessionIds.clear();
    }

    private static void handleRemotePageClosed(ConfigTemplateRemoteSession session, Object player,
            String remotePageSessionId) {
        if (session == null || remotePageSessionId == null) {
            return;
        }
        String configSessionId = session.getSessionId();
        configSessionIdsByRemotePageSession.remove(remotePageSessionId, configSessionId);
        remotePageSessionIdsByConfigSession.remove(configSessionId, remotePageSessionId);
        if (replacingConfigSessionIds.contains(configSessionId)) {
            return;
        }
        playersByConfigSession.remove(configSessionId);
        ConfigTemplateSyncManager.getInstance().closeServerSession(configSessionId,
                player == null ? session.getOwnerPlayer() : player);
    }

    private static void removeBinding(String configSessionId) {
        String remotePageSessionId = remotePageSessionIdsByConfigSession.remove(configSessionId);
        if (remotePageSessionId != null) {
            configSessionIdsByRemotePageSession.remove(remotePageSessionId, configSessionId);
        }
        playersByConfigSession.remove(configSessionId);
        replacingConfigSessionIds.remove(configSessionId);
    }

    private static ConfigSyncModels.ConfigDraftSnapshot extractDraftFromSubmit(
            ConfigSyncModels.ConfigSessionState state, Map<String, List<String>> values) {
        ConfigSyncModels.ConfigDraftSnapshot draft = state == null || state.draft == null
                ? new ConfigSyncModels.ConfigDraftSnapshot() : state.draft.copy();
        if (state == null || state.definition == null || values == null) {
            return draft;
        }
        for (ConfigSyncModels.ConfigCategorySnapshot category : state.definition.categories) {
            for (ConfigSyncModels.ConfigFieldSnapshot field : category.properties) {
                String fieldKey = ConfigSyncModels.buildFieldKey(category.categoryName, field.propertyName);
                String formName = buildFormFieldName(fieldKey);
                String submitted = firstValue(values, formName);
                if (field.list) {
                    draft.values.put(fieldKey, joinSubmitted(values.get(formName)));
                    continue;
                }
                if (field.type == net.minecraftforge.common.config.Property.Type.BOOLEAN) {
                    draft.values.put(fieldKey, values.containsKey(formName) ? "true" : "false");
                    continue;
                }
                if (submitted != null) {
                    draft.values.put(fieldKey, submitted);
                }
            }
        }
        return draft;
    }

    private static RemoteDocumentPage buildPage(ConfigSyncTarget target, ConfigSyncModels.ConfigSessionState state,
            String statusOverride) {
        String title = target == null ? "配置" : target.getTitle();
        String html = buildHtml(target, state, statusOverride);
        return RemoteDocumentPage.builder("qz-config-sync-remote")
                .title(title)
                .resourcePolicy(RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY)
                .metadata("screenId", state == null ? "" : state.screenId)
                .metadata("sessionId", state == null ? "" : state.sessionId)
                .html(html)
                .build();
    }

    private static String buildHtml(ConfigSyncTarget target, ConfigSyncModels.ConfigSessionState state,
            String statusOverride) {
        StringBuilder html = new StringBuilder(8192);
        html.append("<html><head><title>")
                .append(escapeHtml(target == null ? "配置" : target.getTitle()))
                .append("</title><style>")
                .append("body{background:#0b1120;color:#e5e7eb;font-family:sans-serif;margin:0;}")
                .append(".page{padding:18px;max-width:960px;margin:0 auto;}")
                .append(".hero,.status,.category,.toolbar{background:#111827;border:1px solid #334155;border-radius:8px;box-sizing:border-box;}")
                .append(".hero,.status,.toolbar{padding:14px;margin:0 0 12px 0;}")
                .append(".category{padding:14px;margin:0 0 14px 0;}")
                .append(".field{margin:0 0 12px 0;}")
                .append(".field label{display:block;color:#e2e8f0;margin:0 0 4px 0;}")
                .append(".meta,.hint{color:#94a3b8;font-size:12px;line-height:1.5;}")
                .append(".error{color:#fca5a5;font-size:12px;margin-top:4px;}")
                .append("input[type=text],textarea,select{width:100%;box-sizing:border-box;padding:8px;border:1px solid #475569;border-radius:6px;background:#0f172a;color:#f8fafc;}")
                .append("textarea{min-height:76px;}")
                .append(".toolbar button{margin-right:8px;padding:8px 12px;}")
                .append("</style></head><body><div class=\"page\">");
        appendHero(html, target, state);
        appendStatus(html, state, statusOverride);
        html.append("<form id=\"").append(FORM_ID).append("\" action=\"").append(ACTION_SAVE).append("\">");
        html.append("<input type=\"hidden\" name=\"sessionId\" value=\"")
                .append(escapeHtml(state == null ? "" : state.sessionId)).append("\">");
        if (state != null && state.definition != null) {
            for (ConfigSyncModels.ConfigCategorySnapshot category : state.definition.categories) {
                appendCategory(html, state, category);
            }
        }
        appendToolbar(html);
        html.append("</form></div></body></html>");
        return html.toString();
    }

    private static void appendHero(StringBuilder html, ConfigSyncTarget target, ConfigSyncModels.ConfigSessionState state) {
        html.append("<div class=\"hero\"><h1>")
                .append(escapeHtml(target == null ? "配置" : target.getTitle()))
                .append("</h1>");
        if (target != null && !target.getSubtitle().isEmpty()) {
            html.append("<p class=\"hint\">").append(escapeHtml(target.getSubtitle())).append("</p>");
        }
        if (target != null && !target.getDescription().isEmpty()) {
            html.append("<p class=\"hint\">").append(escapeHtml(target.getDescription())).append("</p>");
        }
        html.append("<p class=\"meta\">screenId: ")
                .append(escapeHtml(state == null ? "" : state.screenId))
                .append(" | sessionId: ")
                .append(escapeHtml(state == null ? "" : state.sessionId))
                .append("</p></div>");
    }

    private static void appendStatus(StringBuilder html, ConfigSyncModels.ConfigSessionState state, String statusOverride) {
        String status = normalize(statusOverride);
        if (status.isEmpty() && state != null) {
            status = normalize(state.statusMessage);
        }
        html.append("<div class=\"status\"><strong>状态</strong><p class=\"hint\">")
                .append(escapeHtml(status.isEmpty() ? "等待服务端配置会话。" : status))
                .append("</p></div>");
    }

    private static void appendCategory(StringBuilder html, ConfigSyncModels.ConfigSessionState state,
            ConfigSyncModels.ConfigCategorySnapshot category) {
        html.append("<section class=\"category\"><h2>")
                .append(escapeHtml(category.displayTitle.isEmpty() ? category.categoryName : category.displayTitle))
                .append("</h2>");
        if (!normalize(category.description).isEmpty()) {
            html.append("<p class=\"hint\">").append(escapeHtml(category.description)).append("</p>");
        }
        for (ConfigSyncModels.ConfigFieldSnapshot field : category.properties) {
            appendField(html, state, category, field);
        }
        html.append("</section>");
    }

    private static void appendField(StringBuilder html, ConfigSyncModels.ConfigSessionState state,
            ConfigSyncModels.ConfigCategorySnapshot category, ConfigSyncModels.ConfigFieldSnapshot field) {
        String fieldKey = ConfigSyncModels.buildFieldKey(category.categoryName, field.propertyName);
        String formName = buildFormFieldName(fieldKey);
        String draftValue = state != null && state.draft != null && state.draft.values.containsKey(fieldKey)
                ? state.draft.values.get(fieldKey) : defaultDraftValue(field);
        String fieldError = state == null || state.fieldErrors == null ? ""
                : normalize(state.fieldErrors.get(fieldKey));
        html.append("<div class=\"field\"><label for=\"")
                .append(escapeHtml(formName)).append("\">")
                .append(escapeHtml(field.propertyName)).append("</label>");
        html.append("<p class=\"meta\">")
                .append(escapeHtml(buildFieldMeta(field)))
                .append("</p>");
        appendFieldInput(html, formName, field, draftValue);
        if (!normalize(field.comment).isEmpty()) {
            html.append("<p class=\"hint\">").append(escapeHtml(ForgeConfigTemplateScreen.normalizeInlineText(field.comment)))
                    .append("</p>");
        }
        if (!fieldError.isEmpty()) {
            html.append("<p class=\"error\">").append(escapeHtml(fieldError)).append("</p>");
        }
        html.append("</div>");
    }

    private static void appendFieldInput(StringBuilder html, String formName,
            ConfigSyncModels.ConfigFieldSnapshot field, String draftValue) {
        String value = draftValue == null ? "" : draftValue;
        if (field.type == net.minecraftforge.common.config.Property.Type.BOOLEAN && !field.list) {
            html.append("<label><input type=\"checkbox\" name=\"")
                    .append(escapeHtml(formName))
                    .append("\" value=\"true\"");
            if (Boolean.parseBoolean(value)) {
                html.append(" checked");
            }
            html.append("> 启用</label>");
            return;
        }
        if (!field.validValues.isEmpty() && !field.list && field.validValues.contains(value)) {
            html.append("<select name=\"").append(escapeHtml(formName)).append("\" id=\"")
                    .append(escapeHtml(formName)).append("\">");
            for (String option : field.validValues) {
                html.append("<option value=\"").append(escapeHtml(option)).append("\"");
                if (option != null && option.equals(value)) {
                    html.append(" selected");
                }
                html.append(">").append(escapeHtml(option)).append("</option>");
            }
            html.append("</select>");
            return;
        }
        if (field.list) {
            html.append("<textarea name=\"").append(escapeHtml(formName)).append("\" id=\"")
                    .append(escapeHtml(formName)).append("\">")
                    .append(escapeHtml(value))
                    .append("</textarea>");
            return;
        }
        html.append("<input type=\"text\" name=\"").append(escapeHtml(formName)).append("\" id=\"")
                .append(escapeHtml(formName)).append("\" value=\"")
                .append(escapeHtml(value)).append("\">");
    }

    private static void appendToolbar(StringBuilder html) {
        html.append("<div class=\"toolbar\">")
                .append("<button type=\"submit\" name=\"submitter\" value=\"save\">保存</button>")
                .append("<button type=\"submit\" name=\"submitter\" value=\"refresh\">刷新权威值</button>")
                .append("<button type=\"submit\" name=\"submitter\" value=\"restore\">放弃本次草稿</button>")
                .append("</div>");
    }

    private static String buildFieldMeta(ConfigSyncModels.ConfigFieldSnapshot field) {
        List<String> parts = new ArrayList<String>();
        parts.add("类型：" + resolveTypeLabel(field));
        if (!normalize(field.defaultValue).isEmpty() || !field.defaultValues.isEmpty()) {
            parts.add("默认：" + escapeMetaValue(defaultDraftValue(field)));
        }
        if (!normalize(field.minValue).isEmpty() || !normalize(field.maxValue).isEmpty()) {
            parts.add("范围：" + normalize(field.minValue) + " ~ " + normalize(field.maxValue));
        }
        if (field.requiresMcRestart) {
            parts.add("需重启 Minecraft");
        } else if (field.requiresWorldRestart) {
            parts.add("需重进世界");
        }
        return join(parts, " | ");
    }

    private static String resolveTypeLabel(ConfigSyncModels.ConfigFieldSnapshot field) {
        if (field == null) {
            return "未知";
        }
        String prefix = field.list ? "列表·" : "";
        if (field.type == net.minecraftforge.common.config.Property.Type.BOOLEAN) {
            return prefix + "开关";
        }
        if (field.type == net.minecraftforge.common.config.Property.Type.INTEGER) {
            return prefix + "整数";
        }
        if (field.type == net.minecraftforge.common.config.Property.Type.DOUBLE) {
            return prefix + "小数";
        }
        if (field.type == net.minecraftforge.common.config.Property.Type.COLOR) {
            return prefix + "颜色";
        }
        return prefix + "文本";
    }

    private static String defaultDraftValue(ConfigSyncModels.ConfigFieldSnapshot field) {
        if (field == null) {
            return "";
        }
        if (field.list) {
            return join(field.currentValues, ", ");
        }
        return normalize(field.currentValue);
    }

    private static String buildFormFieldName(String fieldKey) {
        return "field_" + fieldKey.replace(':', '_');
    }

    private static String firstValue(Map<String, List<String>> values, String name) {
        List<String> resolved = values == null ? null : values.get(name);
        return resolved == null || resolved.isEmpty() ? null : resolved.get(0);
    }

    private static String joinSubmitted(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return join(values, ", ");
    }

    private static String join(List<String> values, String delimiter) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(delimiter);
            }
            builder.append(normalized);
        }
        return builder.toString();
    }

    private static String escapeMetaValue(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean matchesPlayer(Object expected, Object actual) {
        return expected == actual || (expected != null && expected.equals(actual));
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
