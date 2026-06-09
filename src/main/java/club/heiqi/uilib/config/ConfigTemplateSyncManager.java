package club.heiqi.uilib.config;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetChannel;
import club.heiqi.uilib.net.api.NetChannelId;
import club.heiqi.uilib.net.api.NetEndpointId;
import club.heiqi.uilib.net.api.NetFetchEndpoint;
import club.heiqi.uilib.net.api.NetRequest;
import club.heiqi.uilib.net.api.NetResponse;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.api.NetStore;
import club.heiqi.uilib.net.api.NetStoreId;
import club.heiqi.uilib.net.api.NetStoreScope;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * 配置模板服务端权威同步管理器。
 */
public final class ConfigTemplateSyncManager {

    private static final String NAMESPACE = MyMod.MODID;
    public static final String QZ_UI_LIB_SCREEN_ID = "mod-config";
    private static final NetEndpointId OPEN_SESSION_ID =
            NetEndpointId.of(NAMESPACE, "config_template_sync_open");
    private static final NetEndpointId SAVE_SESSION_ID =
            NetEndpointId.of(NAMESPACE, "config_template_sync_save");
    private static final NetChannelId FIELD_CHANGE_ID =
            NetChannelId.of(NAMESPACE, "config_template_sync_change");
    private static final NetStoreId SESSION_STATE_STORE_ID =
            NetStoreId.of(NAMESPACE, "config_template_sync_state");

    private static final ConfigTemplateSyncManager INSTANCE = new ConfigTemplateSyncManager();

    private final Map<String, ConfigSyncTarget> targets = new ConcurrentHashMap<String, ConfigSyncTarget>();
    private final Map<String, ConfigTemplateRemoteSession> sessions =
            new ConcurrentHashMap<String, ConfigTemplateRemoteSession>();
    private volatile boolean registered;
    private volatile boolean clientRemoteAvailable;
    private volatile String latestClientSessionId = "";
    private volatile String latestClientScreenId = "";
    private NetFetchEndpoint openEndpoint;
    private NetFetchEndpoint saveEndpoint;
    private NetChannel fieldChangeChannel;
    private NetStore stateStore;

    private ConfigTemplateSyncManager() {}

    /**
     * 返回单例。
     *
     * @return 单例
     */
    public static ConfigTemplateSyncManager getInstance() {
        return INSTANCE;
    }

    /**
     * 注册配置同步网络端点。
     */
    public synchronized void register() {
        if (registered) {
            return;
        }
        registerQzUiLibTargetIfReady();
        NetService service = NetService.getInstance();
        stateStore = service.store(SESSION_STATE_STORE_ID)
                .scope(NetStoreScope.PER_PLAYER)
                .initial(NetBody.json(ConfigSyncJson.toJson(new ConfigSyncModels.ConfigSessionState())))
                .register();
        openEndpoint = service.fetch(OPEN_SESSION_ID)
                .timeout(Duration.ofSeconds(10L))
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        handleOpenRequest(request, context);
                    }
                })
                .register();
        saveEndpoint = service.fetch(SAVE_SESSION_ID)
                .timeout(Duration.ofSeconds(10L))
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        handleSaveRequest(request, context);
                    }
                })
                .register();
        fieldChangeChannel = service.channel(FIELD_CHANGE_ID)
                .onReceive(new NetChannel.NetChannelHandler() {
                    @Override
                    public void onReceive(club.heiqi.uilib.net.api.NetMessage message,
                            club.heiqi.uilib.net.api.NetReceiveContext context) {
                        handleFieldChange(message.getBody().asUtf8String(), context.getSenderPlayer(),
                                context.getSide());
                    }
                })
                .register();
        registered = true;
    }

    /**
     * 注册一个服务端权威配置同步目标。
     *
     * @param target 配置同步目标
     */
    public void registerTarget(ConfigSyncTarget target) {
        if (target == null) {
            return;
        }
        targets.put(target.getScreenId(), target);
    }

    /**
     * 客户端是否已知服务端支持配置同步。
     *
     * @return true 表示可尝试远端配置会话
     */
    public boolean isClientRemoteAvailable() {
        return clientRemoteAvailable;
    }

    /**
     * 客户端最近一次打开的远端会话标识。
     *
     * @return sessionId
     */
    public String getLatestClientSessionId() {
        return latestClientSessionId == null ? "" : latestClientSessionId;
    }

    /**
     * 客户端最近一次打开的配置目标标识。
     *
     * @return screenId
     */
    public String getLatestClientScreenId() {
        return latestClientScreenId == null ? "" : latestClientScreenId;
    }

    /**
     * 记录客户端能力握手结果。
     *
     * @param available 是否可用
     */
    public void setClientRemoteAvailable(boolean available) {
        this.clientRemoteAvailable = available;
        if (!available) {
            this.latestClientSessionId = "";
            this.latestClientScreenId = "";
        }
    }

    /**
     * 客户端发起打开配置会话请求。
     *
     * @return 打开结果
     */
    public ConfigSyncModels.ConfigSessionOpenResponse openClientSession() {
        return openClientSession(QZ_UI_LIB_SCREEN_ID);
    }

    /**
     * 客户端发起打开指定配置会话请求。
     *
     * @param screenId 配置目标标识
     * @return 打开结果
     */
    public ConfigSyncModels.ConfigSessionOpenResponse openClientSession(String screenId) {
        try {
            return openClientSessionAsync(screenId).join();
        } catch (java.util.concurrent.CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw exception;
        }
    }

    /**
     * 客户端异步打开指定配置会话。
     *
     * @param screenId 配置目标标识
     * @return 打开结果 future
     */
    CompletableFuture<ConfigSyncModels.ConfigSessionOpenResponse> openClientSessionAsync(String screenId) {
        ensureRegistered();
        ConfigSyncModels.ConfigSessionOpenRequest request = new ConfigSyncModels.ConfigSessionOpenRequest();
        request.screenId = normalizeScreenId(screenId);
        return openEndpoint.callJson(ConfigSyncJson.toJson(request))
                .thenApply(new java.util.function.Function<NetResponse, ConfigSyncModels.ConfigSessionOpenResponse>() {
                    @Override
                    public ConfigSyncModels.ConfigSessionOpenResponse apply(NetResponse response) {
                        if (!response.isOk()) {
                            throw new IllegalStateException(response.getBody().asUtf8String());
                        }
                        ConfigSyncModels.ConfigSessionOpenResponse openResponse =
                                ConfigSyncJson.fromJson(response.getBody().asUtf8String(),
                                        ConfigSyncModels.ConfigSessionOpenResponse.class);
                        latestClientSessionId = openResponse.sessionId == null ? "" : openResponse.sessionId;
                        latestClientScreenId = openResponse.screenId == null ? "" : openResponse.screenId;
                        clientRemoteAvailable = openResponse.remoteAvailable;
                        return openResponse;
                    }
                });
    }

    /**
     * 客户端发送字段草稿变更。
     *
     * @param change 变更
     */
    public void submitClientFieldChange(ConfigSyncModels.ConfigFieldChange change) {
        ensureRegistered();
        if (change == null || latestClientSessionId == null || latestClientSessionId.isEmpty()) {
            return;
        }
        ClientFieldChangeMessage message = new ClientFieldChangeMessage();
        message.sessionId = latestClientSessionId;
        message.change = change;
        fieldChangeChannel.toServer().send(club.heiqi.uilib.net.api.NetMessage.json(ConfigSyncJson.toJson(message)));
    }

    /**
     * 客户端执行显式保存。
     *
     * @return 保存结果
     */
    public ConfigSyncModels.ConfigSaveResult saveClientSession() {
        return saveClientSession(null);
    }

    ConfigSyncModels.ConfigSaveResult saveClientSession(ConfigSyncModels.ConfigDraftSnapshot draft) {
        try {
            return saveClientSessionAsync(draft).join();
        } catch (java.util.concurrent.CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw exception;
        }
    }

    CompletableFuture<ConfigSyncModels.ConfigSaveResult> saveClientSessionAsync(
            ConfigSyncModels.ConfigDraftSnapshot draft) {
        ensureRegistered();
        ConfigSyncModels.ConfigSaveRequest request = new ConfigSyncModels.ConfigSaveRequest();
        request.sessionId = latestClientSessionId;
        request.draft = draft;
        return saveEndpoint.callJson(ConfigSyncJson.toJson(request))
                .thenApply(new java.util.function.Function<NetResponse, ConfigSyncModels.ConfigSaveResult>() {
                    @Override
                    public ConfigSyncModels.ConfigSaveResult apply(NetResponse response) {
                        if (!response.isOk()) {
                            throw new IllegalStateException(response.getBody().asUtf8String());
                        }
                        return ConfigSyncJson.fromJson(response.getBody().asUtf8String(),
                                ConfigSyncModels.ConfigSaveResult.class);
                    }
                });
    }

    /**
     * 返回客户端会话状态视图。
     *
     * @return store 视图
     */
    public club.heiqi.uilib.net.api.NetStoreView getClientStateView() {
        ensureRegistered();
        return stateStore.view();
    }

    /**
     * 测试重置。
     */
    void resetForTests() {
        targets.clear();
        sessions.clear();
        registered = false;
        clientRemoteAvailable = false;
        latestClientSessionId = "";
        latestClientScreenId = "";
        openEndpoint = null;
        saveEndpoint = null;
        fieldChangeChannel = null;
        stateStore = null;
    }

    /**
     * 客户端断连清理。
     */
    public void onClientDisconnected() {
        clientRemoteAvailable = false;
        latestClientSessionId = "";
        latestClientScreenId = "";
    }

    /**
     * 运行配置同步客户端 smoke。
     *
     * @return smoke 结果
     */
    public CompletableFuture<String> runClientSmokeCheck() {
        return runClientSmokeCheck(QZ_UI_LIB_SCREEN_ID);
    }

    /**
     * 运行指定配置目标的客户端 smoke。
     *
     * @param screenId 配置目标标识
     * @return smoke 结果
     */
    public CompletableFuture<String> runClientSmokeCheck(final String screenId) {
        return openClientSessionAsync(screenId)
                .thenCompose(new java.util.function.Function<ConfigSyncModels.ConfigSessionOpenResponse,
                        CompletableFuture<ConfigSyncModels.ConfigSaveResult>>() {
                    @Override
                    public CompletableFuture<ConfigSyncModels.ConfigSaveResult> apply(
                            ConfigSyncModels.ConfigSessionOpenResponse openResponse) {
                        if (openResponse == null || !openResponse.remoteAvailable) {
                            throw new IllegalStateException("配置同步目标不可用: " + screenId);
                        }
                        ConfigSyncModels.ConfigFieldChange change = firstNoopChange(openResponse.draft);
                        submitClientFieldChange(change);
                        ConfigSyncModels.ConfigDraftSnapshot draft = openResponse.draft == null
                                ? new ConfigSyncModels.ConfigDraftSnapshot() : openResponse.draft.copy();
                        draft.values.put(change.fieldKey, change.draftValue);
                        return saveClientSessionAsync(draft);
                    }
                })
                .thenApply(new java.util.function.Function<ConfigSyncModels.ConfigSaveResult, String>() {
                    @Override
                    public String apply(ConfigSyncModels.ConfigSaveResult result) {
                        if (result == null || !result.success) {
                            throw new IllegalStateException(result == null ? "配置同步保存无结果" : result.message);
                        }
                        return "配置同步 open/change/save/result 已完成：" + result.message;
                    }
                });
    }

    ConfigTemplateRemoteSession openServerSession(String screenId, Object player) {
        ConfigSyncTarget target = resolveTarget(screenId);
        if (target == null) {
            throw new IllegalArgumentException("配置同步目标不存在: " + screenId);
        }
        invalidateExistingSession(player, target.getScreenId());
        ConfigTemplateRemoteSession session = new ConfigTemplateRemoteSession(target, player);
        sessions.put(session.getSessionId(), session);
        publishState(player, session.snapshotState());
        return session;
    }

    /**
     * 关闭指定服务端配置会话，并清理或回退对应玩家的 Store 快照。
     *
     * @param sessionId 会话标识
     * @param player 会话所属玩家
     * @return true 表示找到并关闭了会话
     */
    public boolean closeServerSession(String sessionId, Object player) {
        if (sessionId == null || sessionId.trim().isEmpty() || player == null) {
            return false;
        }
        ConfigTemplateRemoteSession session = sessions.get(sessionId);
        if (session == null || !matchesPlayer(session.getOwnerPlayer(), player)) {
            return false;
        }
        if (sessions.remove(sessionId, session)) {
            refreshPublishedStateAfterRemoval(player);
            return true;
        }
        return false;
    }

    /**
     * 玩家离线时清理该玩家所有服务端配置会话与 per-player Store 快照。
     *
     * @param player 离线玩家
     */
    public void onServerPlayerLeft(Object player) {
        if (player == null) {
            return;
        }
        for (Map.Entry<String, ConfigTemplateRemoteSession> entry : sessions.entrySet()) {
            ConfigTemplateRemoteSession session = entry.getValue();
            if (session != null && matchesPlayer(session.getOwnerPlayer(), player)) {
                sessions.remove(entry.getKey(), session);
            }
        }
        removePublishedState(player);
    }

    ConfigSyncModels.ConfigSaveResult saveServerSession(String sessionId, Object player,
            ConfigSyncModels.ConfigDraftSnapshot draft) {
        ConfigTemplateRemoteSession session = requireSession(sessionId, player);
        session.applyDraftSnapshot(draft);
        ConfigSyncModels.ConfigSaveResult result = session.save();
        publishState(player, session.snapshotState());
        return result;
    }

    ConfigSyncModels.ConfigSessionState getServerSessionState(String sessionId, Object player) {
        ConfigTemplateRemoteSession session = requireSession(sessionId, player);
        return session.snapshotState();
    }

    ConfigSyncTarget getTarget(String screenId) {
        return resolveTarget(screenId);
    }

    int getServerSessionCountForTests() {
        return sessions.size();
    }

    ConfigSyncModels.ConfigSessionState getPublishedServerStateForTests(Object player) {
        if (stateStore == null || player == null) {
            return new ConfigSyncModels.ConfigSessionState();
        }
        return ConfigSyncJson.fromJson(stateStore.getForPlayer(player).asUtf8String(),
                ConfigSyncModels.ConfigSessionState.class);
    }

    private void handleOpenRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
        if (context.getReceiveContext().getSide() != NetSide.SERVER) {
            context.reply(NetResponse.error(400, "配置同步 open 仅接受客户端请求"));
            return;
        }
        Object player = context.getReceiveContext().getSenderPlayer();
        ConfigSyncModels.ConfigSessionOpenRequest openRequest = ConfigSyncJson.fromJson(
                request.getBody().asUtf8String(), ConfigSyncModels.ConfigSessionOpenRequest.class);
        ConfigTemplateRemoteSession session;
        try {
            session = openServerSession(openRequest == null ? "" : openRequest.screenId, player);
        } catch (IllegalArgumentException exception) {
            context.reply(NetResponse.error(404, exception.getMessage()));
            return;
        }
        ConfigSyncModels.ConfigSessionOpenResponse response = new ConfigSyncModels.ConfigSessionOpenResponse();
        response.remoteAvailable = true;
        response.sessionId = session.getSessionId();
        response.screenId = session.getScreenId();
        response.message = "服务端配置会话已打开。";
        ConfigSyncModels.ConfigSessionState state = session.snapshotState();
        response.definition = state.definition;
        response.draft = state.draft;
        context.replyJson(ConfigSyncJson.toJson(response));
    }

    private void handleSaveRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
        if (context.getReceiveContext().getSide() != NetSide.SERVER) {
            context.reply(NetResponse.error(400, "配置同步 save 仅接受客户端请求"));
            return;
        }
        ConfigSyncModels.ConfigSaveRequest saveRequest = ConfigSyncJson.fromJson(request.getBody().asUtf8String(),
                ConfigSyncModels.ConfigSaveRequest.class);
        try {
            ConfigSyncModels.ConfigSaveResult result = saveServerSession(saveRequest == null ? "" : saveRequest.sessionId,
                    context.getReceiveContext().getSenderPlayer(), saveRequest == null ? null : saveRequest.draft);
            context.replyJson(ConfigSyncJson.toJson(result));
        } catch (IllegalArgumentException exception) {
            context.reply(NetResponse.error(404, exception.getMessage()));
        } catch (SecurityException exception) {
            context.reply(NetResponse.error(403, "配置同步会话不属于当前玩家"));
        }
    }

    private void handleFieldChange(String json, Object senderPlayer, NetSide side) {
        if (side != NetSide.SERVER) {
            return;
        }
        ClientFieldChangeMessage message = ConfigSyncJson.fromJson(json, ClientFieldChangeMessage.class);
        ConfigTemplateRemoteSession session = sessions.get(message.sessionId);
        if (session == null || !matchesPlayer(session.getOwnerPlayer(), senderPlayer)) {
            return;
        }
        session.applyChange(message.change);
        publishState(senderPlayer, session.snapshotState());
    }

    private ConfigTemplateRemoteSession requireSession(String sessionId, Object player) {
        ConfigTemplateRemoteSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("配置同步会话不存在");
        }
        if (!matchesPlayer(session.getOwnerPlayer(), player)) {
            throw new SecurityException("配置同步会话不属于当前玩家");
        }
        return session;
    }

    private void invalidateExistingSession(Object player, String screenId) {
        for (Map.Entry<String, ConfigTemplateRemoteSession> entry : sessions.entrySet()) {
            ConfigTemplateRemoteSession session = entry.getValue();
            if (session != null && matchesPlayer(session.getOwnerPlayer(), player)
                    && screenId.equals(session.getScreenId())) {
                sessions.remove(entry.getKey());
            }
        }
    }

    private ConfigSyncTarget resolveTarget(String screenId) {
        registerQzUiLibTargetIfReady();
        return targets.get(normalizeScreenId(screenId));
    }

    private void registerQzUiLibTargetIfReady() {
        if (targets.containsKey(QZ_UI_LIB_SCREEN_ID) || Config.configuration == null) {
            return;
        }
        registerTarget(ConfigSyncTarget.builder(QZ_UI_LIB_SCREEN_ID, Config.configuration)
                .modId(MyMod.MODID)
                .title(QzUiLibConfigSchema.title())
                .subtitle(QzUiLibConfigSchema.subtitle())
                .description(QzUiLibConfigSchema.description())
                .configPath(Config.getConfigPath())
                .categories(QzUiLibConfigSchema.categories())
                .saveAction(new ConfigSyncTarget.SaveAction() {
                    @Override
                    public void save(net.minecraftforge.common.config.Configuration configuration) {
                        Config.saveAndReload();
                    }
                })
                .build());
    }

    private static String normalizeScreenId(String screenId) {
        String normalized = screenId == null ? "" : screenId.trim();
        return normalized.isEmpty() ? QZ_UI_LIB_SCREEN_ID : normalized;
    }

    private static ConfigSyncModels.ConfigFieldChange firstNoopChange(ConfigSyncModels.ConfigDraftSnapshot draft) {
        if (draft == null || draft.values == null || draft.values.isEmpty()) {
            throw new IllegalStateException("配置同步目标没有可自检字段");
        }
        Map.Entry<String, String> first = draft.values.entrySet().iterator().next();
        ConfigSyncModels.ConfigFieldChange change = new ConfigSyncModels.ConfigFieldChange();
        change.fieldKey = first.getKey();
        change.draftValue = first.getValue();
        return change;
    }

    private void publishState(Object player, ConfigSyncModels.ConfigSessionState state) {
        if (stateStore == null || player == null || state == null) {
            return;
        }
        stateStore.setForPlayer(player, NetBody.json(ConfigSyncJson.toJson(state)));
    }

    private void refreshPublishedStateAfterRemoval(Object player) {
        if (stateStore == null || player == null) {
            return;
        }
        for (ConfigTemplateRemoteSession session : sessions.values()) {
            if (session != null && matchesPlayer(session.getOwnerPlayer(), player)) {
                publishState(player, session.snapshotState());
                return;
            }
        }
        resetPublishedState(player);
    }

    private void removePublishedState(Object player) {
        if (stateStore == null || player == null) {
            return;
        }
        stateStore.removeForPlayer(player);
    }

    private void resetPublishedState(Object player) {
        if (stateStore == null || player == null) {
            return;
        }
        stateStore.resetForPlayer(player);
    }

    private void ensureRegistered() {
        if (!registered || openEndpoint == null || saveEndpoint == null || fieldChangeChannel == null
                || stateStore == null) {
            throw new IllegalStateException("配置同步网络端点尚未注册");
        }
    }

    private static boolean matchesPlayer(Object expected, Object actual) {
        return expected == actual || (expected != null && expected.equals(actual));
    }

    private static final class ClientFieldChangeMessage {
        String sessionId = "";
        ConfigSyncModels.ConfigFieldChange change = new ConfigSyncModels.ConfigFieldChange();
    }
}
