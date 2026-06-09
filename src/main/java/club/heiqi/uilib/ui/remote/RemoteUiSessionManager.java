package club.heiqi.uilib.ui.remote;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 远程 UI 服务端 session、surface、lease 与状态机管理器。
 */
final class RemoteUiSessionManager<T> {

    static final long DEFAULT_LEASE_MILLIS = RemoteHtmlSessionGateway.DEFAULT_SESSION_TTL_MILLIS;

    private static final LongSupplier SYSTEM_CLOCK = new LongSupplier() {
        @Override
        public long getAsLong() {
            return System.currentTimeMillis();
        }
    };

    private final String featureName;
    private final RemoteUiAssetStore assetStore;
    private final Map<String, RemoteUiSession<T>> sessions =
            new ConcurrentHashMap<String, RemoteUiSession<T>>();
    private final Map<SurfaceKey, String> activeSessionIdsBySurface =
            new ConcurrentHashMap<SurfaceKey, String>();
    private volatile LongSupplier clock = SYSTEM_CLOCK;

    RemoteUiSessionManager(String featureName) {
        this(featureName, new RemoteUiAssetStore());
    }

    RemoteUiSessionManager(String featureName, RemoteUiAssetStore assetStore) {
        this.featureName = featureName == null ? "远程 UI" : featureName;
        this.assetStore = assetStore == null ? new RemoteUiAssetStore() : assetStore;
    }

    /**
     * 创建新的固定 TTL session，并替换同玩家同 surface 的旧 session。
     */
    RemoteUiSession<T> createSession(Object player, RemoteUiProtocol.SurfaceType surfaceType, String surfaceId,
            T payload, String html) {
        cleanupExpiredSessions(null);
        RemoteUiProtocol.SurfaceType resolvedType = requireSurfaceType(surfaceType);
        String resolvedSurfaceId = requireSurfaceId(surfaceId);
        long nowMillis = nowMillis();
        long expiresAtMillis = nowMillis + DEFAULT_LEASE_MILLIS;
        RemoteUiAssetStore.Asset asset = assetStore.putHtml(html, expiresAtMillis);
        String sessionId = UUID.randomUUID().toString();
        SurfaceKey surfaceKey = SurfaceKey.of(player, resolvedType, resolvedSurfaceId);
        String previousSessionId = activeSessionIdsBySurface.put(surfaceKey, sessionId);
        if (previousSessionId != null) {
            removeSession(previousSessionId, RemoteUiProtocol.SessionState.CLOSED);
        }
        RemoteUiSession<T> session = new RemoteUiSession<T>(sessionId, player, resolvedType, resolvedSurfaceId,
                payload, asset.getAssetId(), asset.getSha256(), asset.getByteCount(), 1L, expiresAtMillis,
                RemoteUiProtocol.LeasePolicy.FIXED);
        session.state = RemoteUiProtocol.SessionState.OFFER_SENT;
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * 校验 HTML 拉取请求。
     */
    ValidationResult<T> validateFetch(RemoteUiProtocol.FetchHtmlRequest request, Object senderPlayer,
            SessionRemovalListener<T> removalListener) {
        try {
            RemoteUiProtocol.validateFetchHtml(request);
        } catch (IllegalArgumentException exception) {
            return ValidationResult.error(400, exception.getMessage());
        }
        RemoteUiSession<T> session = sessions.get(request.sessionId);
        ValidationResult<T> common = validateCommon(session, senderPlayer, removalListener);
        if (!common.isOk()) {
            return common;
        }
        if (!session.matchesSurface(RemoteUiProtocol.parseSurfaceType(request.surfaceType), request.surfaceId)
                || session.getContentRevision() != request.contentRevision
                || !session.getAssetId().equals(request.assetId)) {
            return ValidationResult.error(409, featureName + " 请求版本已过期");
        }
        RemoteUiAssetStore.Asset asset = assetStore.get(session.getAssetId());
        if (asset == null || asset.isExpired(nowMillis())) {
            removeSession(session.getSessionId(), RemoteUiProtocol.SessionState.EXPIRED);
            notifyRemoved(session, removalListener);
            return ValidationResult.error(404, featureName + " HTML asset 已失效");
        }
        session.state = RemoteUiProtocol.SessionState.ACTIVE;
        return ValidationResult.ok(session, asset.toStreamData());
    }

    /**
     * 校验表单提交请求。
     */
    ValidationResult<T> validateSubmit(RemoteUiProtocol.SubmitPayload payload, Object senderPlayer,
            SessionRemovalListener<T> removalListener) {
        try {
            RemoteUiProtocol.validateSubmit(payload);
        } catch (IllegalArgumentException exception) {
            return ValidationResult.error(400, exception.getMessage());
        }
        RemoteUiSession<T> session = sessions.get(payload.sessionId);
        ValidationResult<T> common = validateCommon(session, senderPlayer, removalListener);
        if (!common.isOk()) {
            return common;
        }
        if (!session.matchesSurface(RemoteUiProtocol.parseSurfaceType(payload.surfaceType), payload.surfaceId)
                || session.getContentRevision() != payload.contentRevision) {
            return ValidationResult.error(409, featureName + " 提交版本已过期");
        }
        return ValidationResult.ok(session, null);
    }

    /**
     * 按 session 精确关闭。
     */
    CloseResult<T> closeSession(Object player, RemoteUiProtocol.SurfaceType surfaceType, String surfaceId,
            String sessionId, long contentRevision) {
        if (player == null || RemoteUiProtocol.isBlank(sessionId)) {
            return CloseResult.notFound();
        }
        RemoteUiSession<T> session = sessions.get(sessionId);
        if (session == null || !session.matchesPlayer(player)
                || !session.matchesSurface(requireSurfaceType(surfaceType), surfaceId)
                || session.getContentRevision() != contentRevision) {
            return CloseResult.notFound();
        }
        SurfaceKey key = SurfaceKey.of(player, session.getSurfaceType(), session.getSurfaceId());
        if (!activeSessionIdsBySurface.remove(key, sessionId)) {
            return CloseResult.notFound();
        }
        removeSession(sessionId, RemoteUiProtocol.SessionState.CLOSED);
        return CloseResult.closed(session);
    }

    /**
     * 按 surface 关闭当前活动 session。
     */
    CloseResult<T> closeSurface(Object player, RemoteUiProtocol.SurfaceType surfaceType, String surfaceId) {
        if (player == null) {
            return CloseResult.notFound();
        }
        SurfaceKey key = SurfaceKey.of(player, requireSurfaceType(surfaceType), requireSurfaceId(surfaceId));
        String sessionId = activeSessionIdsBySurface.remove(key);
        if (sessionId == null) {
            return CloseResult.notFound();
        }
        RemoteUiSession<T> session = sessions.get(sessionId);
        removeSession(sessionId, RemoteUiProtocol.SessionState.CLOSED);
        return session == null ? CloseResult.notFound() : CloseResult.closed(session);
    }

    /**
     * 获取 session，不触发过期清理。
     */
    RemoteUiSession<T> getSession(String sessionId) {
        return RemoteUiProtocol.isBlank(sessionId) ? null : sessions.get(sessionId);
    }

    /**
     * 移除指定 session。
     */
    RemoteUiSession<T> removeSession(String sessionId) {
        return removeSession(sessionId, RemoteUiProtocol.SessionState.CLOSED);
    }

    /**
     * 清理过期 session。
     */
    void cleanupExpiredSessions(SessionRemovalListener<T> listener) {
        long nowMillis = nowMillis();
        assetStore.cleanupExpired(nowMillis);
        for (RemoteUiSession<T> session : sessions.values()) {
            if (session.isExpired(nowMillis)) {
                RemoteUiSession<T> removed = removeSession(session.getSessionId(), RemoteUiProtocol.SessionState.EXPIRED);
                if (removed != null) {
                    notifyRemoved(removed, listener);
                }
            }
        }
    }

    /**
     * 设置测试时钟。
     */
    void setClockForTests(LongSupplier testClock) {
        clock = testClock == null ? SYSTEM_CLOCK : testClock;
    }

    /**
     * 清空所有 session 与 asset。
     */
    void clear() {
        sessions.clear();
        activeSessionIdsBySurface.clear();
        assetStore.clear();
    }

    private ValidationResult<T> validateCommon(RemoteUiSession<T> session, Object senderPlayer,
            SessionRemovalListener<T> removalListener) {
        long nowMillis = nowMillis();
        if (session == null) {
            return ValidationResult.error(404, featureName + " session 已失效");
        }
        if (session.isExpired(nowMillis)) {
            RemoteUiSession<T> removed = removeSession(session.getSessionId(), RemoteUiProtocol.SessionState.EXPIRED);
            if (removed != null) {
                notifyRemoved(removed, removalListener);
            }
            return ValidationResult.error(404, featureName + " session 已失效");
        }
        if (!session.matchesPlayer(senderPlayer)) {
            return ValidationResult.error(403, featureName + " session 不属于当前玩家");
        }
        return ValidationResult.ok(session, null);
    }

    private RemoteUiSession<T> removeSession(String sessionId, RemoteUiProtocol.SessionState terminalState) {
        if (RemoteUiProtocol.isBlank(sessionId)) {
            return null;
        }
        RemoteUiSession<T> session = sessions.remove(sessionId);
        if (session == null) {
            return null;
        }
        session.state = terminalState;
        activeSessionIdsBySurface.remove(SurfaceKey.of(session.getPlayer(), session.getSurfaceType(),
                session.getSurfaceId()), sessionId);
        assetStore.remove(session.getAssetId());
        return session;
    }

    private long nowMillis() {
        return clock.getAsLong();
    }

    private static void notifyRemoved(RemoteUiSession<?> session, SessionRemovalListener<?> listener) {
        if (session != null && listener != null) {
            notifyRemovedUnchecked(session, listener);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void notifyRemovedUnchecked(RemoteUiSession session, SessionRemovalListener listener) {
        listener.onSessionRemoved(session);
    }

    private static RemoteUiProtocol.SurfaceType requireSurfaceType(RemoteUiProtocol.SurfaceType surfaceType) {
        if (surfaceType == null) {
            throw new IllegalArgumentException("surfaceType must not be null");
        }
        return surfaceType;
    }

    private static String requireSurfaceId(String surfaceId) {
        if (RemoteUiProtocol.isBlank(surfaceId)) {
            throw new IllegalArgumentException("surfaceId must not be blank");
        }
        return surfaceId;
    }

    /**
     * session 移除监听器。
     */
    interface SessionRemovalListener<T> {
        void onSessionRemoved(RemoteUiSession<T> session);
    }

    /**
     * 校验结果。
     */
    static final class ValidationResult<T> {

        private final boolean ok;
        private final int statusCode;
        private final String message;
        private final RemoteUiSession<T> session;
        private final RemoteUiAssetStore.StreamData streamData;

        private ValidationResult(boolean ok, int statusCode, String message, RemoteUiSession<T> session,
                RemoteUiAssetStore.StreamData streamData) {
            this.ok = ok;
            this.statusCode = statusCode;
            this.message = message == null ? "" : message;
            this.session = session;
            this.streamData = streamData;
        }

        static <T> ValidationResult<T> ok(RemoteUiSession<T> session, RemoteUiAssetStore.StreamData streamData) {
            return new ValidationResult<T>(true, 200, "", session, streamData);
        }

        static <T> ValidationResult<T> error(int statusCode, String message) {
            return new ValidationResult<T>(false, statusCode, message, null, null);
        }

        boolean isOk() {
            return ok;
        }

        int getStatusCode() {
            return statusCode;
        }

        String getMessage() {
            return message;
        }

        RemoteUiSession<T> getSession() {
            return session;
        }

        RemoteUiAssetStore.StreamData getStreamData() {
            return streamData;
        }
    }

    /**
     * 关闭结果。
     */
    static final class CloseResult<T> {

        private final boolean closed;
        private final RemoteUiSession<T> session;

        private CloseResult(boolean closed, RemoteUiSession<T> session) {
            this.closed = closed;
            this.session = session;
        }

        static <T> CloseResult<T> closed(RemoteUiSession<T> session) {
            return new CloseResult<T>(true, session);
        }

        static <T> CloseResult<T> notFound() {
            return new CloseResult<T>(false, null);
        }

        boolean isClosed() {
            return closed;
        }

        RemoteUiSession<T> getSession() {
            return session;
        }
    }

    /**
     * 服务端保存的远程 UI session。
     */
    static final class RemoteUiSession<T> {

        private final String sessionId;
        private final Object player;
        private final RemoteUiProtocol.SurfaceType surfaceType;
        private final String surfaceId;
        private final T payload;
        private final String assetId;
        private final String sha256;
        private final int htmlByteCount;
        private final long contentRevision;
        private final long leaseExpiresAtMillis;
        private final RemoteUiProtocol.LeasePolicy leasePolicy;
        private volatile RemoteUiProtocol.SessionState state = RemoteUiProtocol.SessionState.CREATED;

        private RemoteUiSession(String sessionId, Object player, RemoteUiProtocol.SurfaceType surfaceType,
                String surfaceId, T payload, String assetId, String sha256, int htmlByteCount,
                long contentRevision, long leaseExpiresAtMillis, RemoteUiProtocol.LeasePolicy leasePolicy) {
            this.sessionId = sessionId;
            this.player = player;
            this.surfaceType = surfaceType;
            this.surfaceId = surfaceId;
            this.payload = payload;
            this.assetId = assetId;
            this.sha256 = sha256;
            this.htmlByteCount = htmlByteCount;
            this.contentRevision = contentRevision;
            this.leaseExpiresAtMillis = leaseExpiresAtMillis;
            this.leasePolicy = leasePolicy == null ? RemoteUiProtocol.LeasePolicy.FIXED : leasePolicy;
        }

        String getSessionId() {
            return sessionId;
        }

        Object getPlayer() {
            return player;
        }

        RemoteUiProtocol.SurfaceType getSurfaceType() {
            return surfaceType;
        }

        String getSurfaceId() {
            return surfaceId;
        }

        T getPayload() {
            return payload;
        }

        String getAssetId() {
            return assetId;
        }

        String getSha256() {
            return sha256;
        }

        int getHtmlByteCount() {
            return htmlByteCount;
        }

        long getContentRevision() {
            return contentRevision;
        }

        long getLeaseExpiresAtMillis() {
            return leaseExpiresAtMillis;
        }

        RemoteUiProtocol.LeasePolicy getLeasePolicy() {
            return leasePolicy;
        }

        RemoteUiProtocol.SessionState getState() {
            return state;
        }

        boolean isExpired(long nowMillis) {
            return nowMillis >= leaseExpiresAtMillis;
        }

        boolean matchesPlayer(Object candidate) {
            return player == candidate || (player != null && player.equals(candidate));
        }

        boolean matchesSurface(RemoteUiProtocol.SurfaceType candidateType, String candidateSurfaceId) {
            return surfaceType == candidateType && surfaceId.equals(candidateSurfaceId == null ? "" : candidateSurfaceId);
        }
    }

    private static final class SurfaceKey {

        private final Object player;
        private final RemoteUiProtocol.SurfaceType surfaceType;
        private final String surfaceId;

        private SurfaceKey(Object player, RemoteUiProtocol.SurfaceType surfaceType, String surfaceId) {
            this.player = player;
            this.surfaceType = surfaceType;
            this.surfaceId = surfaceId == null ? "" : surfaceId;
        }

        static SurfaceKey of(Object player, RemoteUiProtocol.SurfaceType surfaceType, String surfaceId) {
            return new SurfaceKey(player, surfaceType, surfaceId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SurfaceKey)) {
                return false;
            }
            SurfaceKey other = (SurfaceKey) obj;
            return java.util.Objects.equals(player, other.player)
                    && surfaceType == other.surfaceType
                    && surfaceId.equals(other.surfaceId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(player, surfaceType, surfaceId);
        }
    }
}
