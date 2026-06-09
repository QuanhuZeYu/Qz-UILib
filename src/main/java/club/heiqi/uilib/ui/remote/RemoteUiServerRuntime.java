package club.heiqi.uilib.ui.remote;

import club.heiqi.uilib.net.api.NetRequest;
import club.heiqi.uilib.net.api.NetResponse;
import club.heiqi.uilib.net.api.NetStreamCall;
import club.heiqi.uilib.net.api.NetStreamEndpoint;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * 远程 UI 服务端 runtime 骨架，统一调度 session、asset 与 Stream 校验。
 */
final class RemoteUiServerRuntime<T> {

    private final String featureName;
    private final RemoteUiProtocol.SurfaceType surfaceType;
    private final RemoteUiSessionManager<T> sessionManager;

    RemoteUiServerRuntime(String featureName, RemoteUiProtocol.SurfaceType surfaceType) {
        this.featureName = featureName == null ? "远程 UI" : featureName;
        this.surfaceType = surfaceType;
        this.sessionManager = new RemoteUiSessionManager<T>(featureName);
    }

    /**
     * 创建新的远程 UI session。
     */
    RemoteUiSessionManager.RemoteUiSession<T> createSession(Object player, String surfaceId, T payload, String html) {
        return sessionManager.createSession(player, surfaceType, surfaceId, payload, html);
    }

    /**
     * 构造 HTML Stream 调用。
     */
    NetStreamCall callStream(NetStreamEndpoint endpoint, RemoteUiProtocol.OpenSurfacePayload offer) {
        RemoteUiProtocol.FetchHtmlRequest request = new RemoteUiProtocol.FetchHtmlRequest();
        if (offer != null) {
            request.sessionId = offer.sessionId;
            request.surfaceType = offer.surfaceType;
            request.surfaceId = offer.surfaceId;
            request.contentRevision = offer.contentRevision;
            request.assetId = offer.assetId;
        }
        return endpoint.call(NetRequest.json(RemoteUiProtocol.toJson(request)));
    }

    /**
     * 兼容现有内部测试入口：按 session 自动补齐 asset 与 revision。
     */
    NetStreamCall callStream(NetStreamEndpoint endpoint, String sessionId) {
        RemoteUiSessionManager.RemoteUiSession<T> session = sessionManager.getSession(sessionId);
        RemoteUiProtocol.FetchHtmlRequest request = new RemoteUiProtocol.FetchHtmlRequest();
        request.sessionId = sessionId == null ? "" : sessionId;
        if (session != null) {
            request.surfaceType = session.getSurfaceType().name();
            request.surfaceId = session.getSurfaceId();
            request.contentRevision = session.getContentRevision();
            request.assetId = session.getAssetId();
        }
        return endpoint.call(NetRequest.json(RemoteUiProtocol.toJson(request)));
    }

    /**
     * 处理 HTML Stream 请求。
     */
    void handleStreamRequest(NetRequest request, NetStreamEndpoint.NetStreamRequestContext context,
            HeaderContributor<T> headerContributor, final SessionRemovalListener<T> removalListener) {
        if (context.getReceiveContext().getSide() != NetSide.SERVER) {
            context.reply(NetResponse.error(400, featureName + " Stream 仅接受客户端请求"));
            return;
        }
        RemoteUiProtocol.FetchHtmlRequest fetchRequest;
        try {
            fetchRequest = RemoteUiProtocol.fromJson(request.getBody().asUtf8String(),
                    RemoteUiProtocol.FetchHtmlRequest.class);
        } catch (IllegalArgumentException exception) {
            context.reply(NetResponse.error(400, exception.getMessage()));
            return;
        }
        RemoteUiSessionManager.ValidationResult<T> result = sessionManager.validateFetch(fetchRequest,
                context.getReceiveContext().getSenderPlayer(), adapt(removalListener));
        if (!result.isOk()) {
            context.reply(NetResponse.error(result.getStatusCode(), result.getMessage()));
            return;
        }
        RemoteUiSessionManager.RemoteUiSession<T> session = result.getSession();
        RemoteUiAssetStore.StreamData streamData = result.getStreamData();
        NetResponse response = streamData.toResponse()
                .withHeader("x-qz-session-id", session.getSessionId())
                .withHeader("x-qz-surface-type", session.getSurfaceType().name())
                .withHeader("x-qz-surface-id", session.getSurfaceId())
                .withHeader("x-qz-content-revision", Long.toString(session.getContentRevision()))
                .withHeader("x-qz-asset-id", session.getAssetId())
                .withHeader("x-qz-sha256", streamData.getSha256())
                .withHeader("x-qz-html-bytes", Integer.toString(streamData.getByteCount()));
        if (headerContributor != null) {
            response = headerContributor.addHeaders(response, session);
        }
        context.reply(response);
    }

    /**
     * 校验提交 payload。
     */
    RemoteUiSessionManager.ValidationResult<T> validateSubmit(RemoteUiProtocol.SubmitPayload payload,
            Object senderPlayer, SessionRemovalListener<T> removalListener) {
        return sessionManager.validateSubmit(payload, senderPlayer, adapt(removalListener));
    }

    /**
     * 按 session 精确关闭。
     */
    RemoteUiSessionManager.CloseResult<T> closeSession(Object player, String surfaceId, String sessionId,
            long contentRevision) {
        return sessionManager.closeSession(player, surfaceType, surfaceId, sessionId, contentRevision);
    }

    /**
     * 按 surface 关闭当前 session。
     */
    RemoteUiSessionManager.CloseResult<T> closeSurface(Object player, String surfaceId) {
        return sessionManager.closeSurface(player, surfaceType, surfaceId);
    }

    /**
     * 移除指定 session。
     */
    RemoteUiSessionManager.RemoteUiSession<T> removeSession(String sessionId) {
        return sessionManager.removeSession(sessionId);
    }

    /**
     * 获取 session。
     */
    RemoteUiSessionManager.RemoteUiSession<T> getSession(String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    /**
     * 清理过期 session。
     */
    void cleanupExpiredSessions(SessionRemovalListener<T> listener) {
        sessionManager.cleanupExpiredSessions(adapt(listener));
    }

    /**
     * 设置测试时钟。
     */
    void setClockForTests(java.util.function.LongSupplier clock) {
        sessionManager.setClockForTests(clock);
    }

    /**
     * 清空 runtime。
     */
    void clear() {
        sessionManager.clear();
    }

    private RemoteUiSessionManager.SessionRemovalListener<T> adapt(final SessionRemovalListener<T> listener) {
        if (listener == null) {
            return null;
        }
        return new RemoteUiSessionManager.SessionRemovalListener<T>() {
            @Override
            public void onSessionRemoved(RemoteUiSessionManager.RemoteUiSession<T> session) {
                listener.onSessionRemoved(session);
            }
        };
    }

    /**
     * HTML Stream 响应头追加器。
     */
    interface HeaderContributor<T> {
        NetResponse addHeaders(NetResponse response, RemoteUiSessionManager.RemoteUiSession<T> session);
    }

    /**
     * session 移除监听器。
     */
    interface SessionRemovalListener<T> {
        void onSessionRemoved(RemoteUiSessionManager.RemoteUiSession<T> session);
    }
}
