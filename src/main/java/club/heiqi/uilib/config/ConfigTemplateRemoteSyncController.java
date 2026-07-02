package club.heiqi.uilib.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetStoreView;
import club.heiqi.uilib.net.api.NetStoreView.NetStoreSubscriber;
import club.heiqi.uilib.net.transport.NetSide;
import club.heiqi.uilib.net.api.NetService;

/**
 * Forge 配置模板的服务端权威同步控制器。
 */
public final class ConfigTemplateRemoteSyncController implements ForgeConfigTemplateScreen.RemoteSyncController {

    /**
     * 创建默认的服务端权威同步控制器。
     */
    public ConfigTemplateRemoteSyncController() {}

    @Override
    public ForgeConfigTemplateScreen.RemoteSyncSession create(ForgeConfigTemplateScreen owner,
            ForgeConfigTemplateScreen.Spec spec) {
        return new Session(owner, spec);
    }

    private static final class Session implements ForgeConfigTemplateScreen.RemoteSyncSession {

        private final ForgeConfigTemplateScreen owner;
        private final Map<String, String> lastSentDrafts = new LinkedHashMap<String, String>();
        private final String screenId;
        private NetStoreView subscribedView;
        private NetStoreSubscriber stateSubscriber;
        private boolean remoteModeActive;
        private ConfigSyncModels.ConfigSessionState latestState;
        private boolean stateSubscribed;
        private boolean closed;

        private Session(ForgeConfigTemplateScreen owner, ForgeConfigTemplateScreen.Spec spec) {
            this.owner = owner;
            this.screenId = spec == null || spec.getRemoteSyncScreenId().isEmpty()
                    ? ConfigTemplateSyncManager.QZ_UI_LIB_SCREEN_ID : spec.getRemoteSyncScreenId();
        }

        @Override
        public void onScreenOpened() {
            closed = false;
            if (!ConfigTemplateSyncManager.getInstance().isClientRemoteAvailable()) {
                return;
            }
            owner.showStatusMessage("正在连接服务端配置会话...");
            ConfigTemplateSyncManager.getInstance().openClientSessionAsync(screenId)
                    .whenComplete(new java.util.function.BiConsumer<ConfigSyncModels.ConfigSessionOpenResponse,
                            Throwable>() {
                        @Override
                        public void accept(final ConfigSyncModels.ConfigSessionOpenResponse response,
                                final Throwable throwable) {
                            NetService.getInstance().runOnMainThread(NetSide.CLIENT, new Runnable() {
                                @Override
                                public void run() {
                                    handleOpenResponse(response, throwable);
                                }
                            });
                        }
                    });
        }

        @Override
        public void onScreenClosed() {
            boolean shouldCloseRemote = remoteModeActive;
            closed = true;
            if (shouldCloseRemote) {
                ConfigTemplateSyncManager.getInstance().closeClientSessionAsync();
            }
            lastSentDrafts.clear();
            latestState = null;
            remoteModeActive = false;
            unsubscribeStateStore();
            stateSubscribed = false;
        }

        @Override
        public boolean isRemoteModeActive() {
            return remoteModeActive;
        }

        @Override
        public void pushCurrentDraftToRemote() {
            if (!remoteModeActive) {
                return;
            }
            Map<String, String> currentDrafts = collectCurrentDrafts();
            for (Map.Entry<String, String> entry : currentDrafts.entrySet()) {
                String previous = lastSentDrafts.get(entry.getKey());
                if (entry.getValue().equals(previous)) {
                    continue;
                }
                ConfigSyncModels.ConfigFieldChange change = new ConfigSyncModels.ConfigFieldChange();
                change.fieldKey = entry.getKey();
                change.draftValue = entry.getValue();
                ConfigTemplateSyncManager.getInstance().submitClientFieldChange(change);
                lastSentDrafts.put(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public void restoreFromRemoteSnapshot() {
            if (!remoteModeActive || latestState == null) {
                return;
            }
            applyRemoteDraftToUi(latestState.draft);
            owner.requestStatusRefresh();
        }

        @Override
        public void saveRemoteDraft() {
            if (!remoteModeActive) {
                return;
            }
            pushCurrentDraftToRemote();
            owner.showStatusMessage("正在将草稿提交到服务端...");
            ConfigTemplateSyncManager.getInstance().saveClientSessionAsync(collectCurrentDraftSnapshot())
                    .whenComplete(new java.util.function.BiConsumer<ConfigSyncModels.ConfigSaveResult, Throwable>() {
                        @Override
                        public void accept(final ConfigSyncModels.ConfigSaveResult result, final Throwable throwable) {
                            NetService.getInstance().runOnMainThread(NetSide.CLIENT, new Runnable() {
                                @Override
                                public void run() {
                                    handleSaveResponse(result, throwable);
                                }
                            });
                        }
                    });
        }

        private void subscribeStateStore() {
            if (stateSubscribed) {
                return;
            }
            stateSubscribed = true;
            subscribedView = ConfigTemplateSyncManager.getInstance().getClientStateView();
            stateSubscriber = new NetStoreSubscriber() {
                @Override
                public void onSnapshot(NetBody snapshot) {
                    if (snapshot == null) {
                        return;
                    }
                    ConfigSyncModels.ConfigSessionState state = ConfigSyncJson.fromJson(snapshot.asUtf8String(),
                            ConfigSyncModels.ConfigSessionState.class);
                    if (state == null || state.sessionId == null
                            || !state.sessionId.equals(ConfigTemplateSyncManager.getInstance().getLatestClientSessionId())) {
                        return;
                    }
                    latestState = state;
                    if (closed) {
                        return;
                    }
                    if (state.statusMessage != null && !state.statusMessage.isEmpty()) {
                        owner.showStatusMessage(state.statusMessage);
                    }
                }
            };
            subscribedView.subscribe(stateSubscriber);
        }

        private Map<String, String> collectCurrentDrafts() {
            Map<String, String> drafts = new LinkedHashMap<String, String>();
            List<ForgeConfigTemplateScreen.PropertyBinding> bindings = owner.getBindingsForRemoteSync();
            for (ForgeConfigTemplateScreen.PropertyBinding binding : bindings) {
                drafts.put(binding.getBindingKey(), binding.exportDraftValue());
            }
            return drafts;
        }

        private ConfigSyncModels.ConfigDraftSnapshot collectCurrentDraftSnapshot() {
            ConfigSyncModels.ConfigDraftSnapshot snapshot = new ConfigSyncModels.ConfigDraftSnapshot();
            snapshot.values.putAll(collectCurrentDrafts());
            return snapshot;
        }

        private void applyRemoteDraftToUi(ConfigSyncModels.ConfigDraftSnapshot draft) {
            if (draft == null) {
                return;
            }
            for (ForgeConfigTemplateScreen.PropertyBinding binding : owner.getBindingsForRemoteSync()) {
                String fieldKey = binding.getBindingKey();
                if (!draft.values.containsKey(fieldKey)) {
                    continue;
                }
                binding.applyRemoteDraftValue(draft.values.get(fieldKey));
                lastSentDrafts.put(fieldKey, draft.values.get(fieldKey));
            }
        }

        private void handleOpenResponse(ConfigSyncModels.ConfigSessionOpenResponse response, Throwable throwable) {
            if (closed) {
                closeOpenedRemoteSession(response);
                return;
            }
            if (throwable != null) {
                remoteModeActive = false;
                owner.showStatusMessage("服务端配置同步不可用，已回退本地草稿模式。");
                return;
            }
            if (response == null || !response.remoteAvailable) {
                remoteModeActive = false;
                owner.showStatusMessage("当前服务端未启用配置同步，已使用本地草稿模式。");
                return;
            }
            remoteModeActive = true;
            ConfigSyncModels.ConfigSessionState state = new ConfigSyncModels.ConfigSessionState();
            state.sessionId = response.sessionId;
            state.screenId = response.screenId;
            state.remoteAvailable = true;
            state.statusMessage = response.message;
            state.definition = response.definition;
            state.draft = response.draft;
            latestState = state;
            applyRemoteDraftToUi(response.draft);
            subscribeStateStore();
            owner.showStatusMessage(response.message == null || response.message.isEmpty()
                    ? "已连接服务端配置会话。" : response.message);
            owner.requestStatusRefresh();
        }

        private void closeOpenedRemoteSession(ConfigSyncModels.ConfigSessionOpenResponse response) {
            if (response == null || !response.remoteAvailable || response.sessionId == null
                    || response.sessionId.trim().isEmpty()) {
                return;
            }
            ConfigTemplateSyncManager.getInstance().closeClientSessionAsync(response.sessionId);
        }

        private void handleSaveResponse(ConfigSyncModels.ConfigSaveResult result, Throwable throwable) {
            if (closed) {
                return;
            }
            if (throwable != null) {
                owner.showStatusMessage("服务端保存失败：" + readableMessage(throwable));
                return;
            }
            if (result == null) {
                owner.showStatusMessage("服务端保存失败：未收到结果。");
                return;
            }
            applyRemoteDraftToUi(result.committedDraft);
            owner.showStatusMessage(result.message == null || result.message.isEmpty()
                    ? (result.success ? "服务端配置已保存。" : "服务端保存失败。") : result.message);
            owner.requestStatusRefresh();
        }

        private String readableMessage(Throwable throwable) {
            Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                    && throwable.getCause() != null ? throwable.getCause() : throwable;
            String message = cause == null ? "" : cause.getMessage();
            if (message == null || message.trim().isEmpty()) {
                return cause == null ? "未知错误" : cause.getClass().getSimpleName();
            }
            return message;
        }

        private void unsubscribeStateStore() {
            if (subscribedView != null && stateSubscriber != null) {
                subscribedView.unsubscribe(stateSubscriber);
            }
            subscribedView = null;
            stateSubscriber = null;
        }
    }
}
