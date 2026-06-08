package club.heiqi.uilib.ui.remote;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetContentType;
import club.heiqi.uilib.net.api.NetRequest;
import club.heiqi.uilib.net.api.NetResponse;
import club.heiqi.uilib.net.api.NetStreamCall;
import club.heiqi.uilib.net.api.NetStreamEndpoint;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * 远程 HTML 页面与 HUD 共用的服务端 session / Stream 网关。
 *
 * <p>该类只承载通用传输职责：session 生命周期、HTML 字节缓存、SHA-256、
 * Stream 请求解析、玩家归属校验和公共响应头。页面与 HUD 的业务字段、提交事件和关闭语义
 * 仍由各自公开入口维护。</p>
 */
final class RemoteHtmlSessionGateway<T> {

    static final NetContentType REMOTE_HTML_CONTENT_TYPE =
            NetContentType.of("text/vnd.qzuilib.remote-html; charset=utf-8");
    /** HTML 拉取与后续表单提交共享同一服务端 session TTL。 */
    static final long DEFAULT_SESSION_TTL_MILLIS = Duration.ofMinutes(10L).toMillis();
    static final long DEFAULT_STREAM_MAX_BYTES = 256L * 1024L * 1024L;
    private static final LongSupplier SYSTEM_CLOCK = new LongSupplier() {
        @Override
        public long getAsLong() {
            return System.currentTimeMillis();
        }
    };

    private final String featureName;
    private final Map<String, RemoteHtmlSession<T>> sessions =
            new ConcurrentHashMap<String, RemoteHtmlSession<T>>();
    private volatile LongSupplier clock = SYSTEM_CLOCK;

    RemoteHtmlSessionGateway(String featureName) {
        this.featureName = featureName == null ? "远程 HTML" : featureName;
    }

    /**
     * 创建并保存新的服务端 HTML session。
     */
    RemoteHtmlSession<T> createSession(Object player, T payload, String html) {
        cleanupExpiredSessions(null);
        String sessionId = UUID.randomUUID().toString();
        byte[] htmlBytes = (html == null ? "" : html).getBytes(StandardCharsets.UTF_8);
        RemoteHtmlSession<T> session = new RemoteHtmlSession<T>(sessionId, player, payload, htmlBytes,
                sha256Hex(htmlBytes), nowMillis() + DEFAULT_SESSION_TTL_MILLIS);
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * 使用统一的 Stream request payload 拉取远程 HTML。
     */
    NetStreamCall callStream(NetStreamEndpoint streamEndpoint, String sessionId) {
        StreamRequest request = new StreamRequest();
        request.sessionId = isBlank(sessionId) ? "" : sessionId;
        return streamEndpoint.call(NetRequest.json(RemoteJson.toJson(request)));
    }

    /**
     * 处理服务端 HTML Stream 请求，并允许调用方追加页面/HUD 专属响应头。
     */
    void handleStreamRequest(NetRequest request, NetStreamEndpoint.NetStreamRequestContext context,
            HeaderContributor<T> headerContributor) {
        handleStreamRequest(request, context, headerContributor, null);
    }

    /**
     * 处理服务端 HTML Stream 请求，并在请求触发过期移除时通知调用方。
     */
    void handleStreamRequest(NetRequest request, NetStreamEndpoint.NetStreamRequestContext context,
            HeaderContributor<T> headerContributor, SessionRemovalListener<T> removalListener) {
        if (context.getReceiveContext().getSide() != NetSide.SERVER) {
            context.reply(NetResponse.error(400, featureName + " Stream 仅接受客户端请求"));
            return;
        }
        StreamRequest streamRequest;
        try {
            streamRequest = RemoteJson.fromJson(request.getBody().asUtf8String(), StreamRequest.class);
        } catch (IllegalArgumentException exception) {
            context.reply(NetResponse.error(400, exception.getMessage()));
            return;
        }
        RemoteHtmlSession<T> session = streamRequest == null ? null : sessions.get(streamRequest.sessionId);
        long now = nowMillis();
        if (session == null || session.isExpired(now)) {
            if (session != null && sessions.remove(session.getSessionId(), session)
                    && removalListener != null) {
                removalListener.onSessionRemoved(session);
            }
            context.reply(NetResponse.error(404, featureName + " session 已失效"));
            return;
        }
        if (!session.matchesPlayer(context.getReceiveContext().getSenderPlayer())) {
            context.reply(NetResponse.error(403, featureName + " session 不属于当前玩家"));
            return;
        }
        NetResponse response = NetResponse.ok(NetBody.of(REMOTE_HTML_CONTENT_TYPE, session.getHtmlBytes()))
                .withHeader("x-qz-session-id", session.getSessionId())
                .withHeader("x-qz-sha256", session.getSha256())
                .withHeader("x-qz-html-bytes", Integer.toString(session.getHtmlByteCount()));
        if (headerContributor != null) {
            response = headerContributor.addHeaders(response, session);
        }
        context.reply(response);
    }

    /**
     * 获取指定 session，不做过期清理。
     */
    RemoteHtmlSession<T> getSession(String sessionId) {
        return isBlank(sessionId) ? null : sessions.get(sessionId);
    }

    /**
     * 移除指定 session。
     */
    RemoteHtmlSession<T> removeSession(String sessionId) {
        return isBlank(sessionId) ? null : sessions.remove(sessionId);
    }

    /**
     * 清理过期 session。
     */
    void cleanupExpiredSessions(SessionRemovalListener<T> listener) {
        long now = nowMillis();
        for (RemoteHtmlSession<T> session : sessions.values()) {
            if (session.isExpired(now) && sessions.remove(session.getSessionId(), session)
                    && listener != null) {
                listener.onSessionRemoved(session);
            }
        }
    }

    /**
     * 设置测试时钟。
     */
    void setClockForTests(LongSupplier testClock) {
        clock = testClock == null ? SYSTEM_CLOCK : testClock;
    }

    private long nowMillis() {
        return clock.getAsLong();
    }

    /**
     * 清空全部 session，供测试重置使用。
     */
    void clear() {
        sessions.clear();
    }

    /**
     * 计算字节内容的 SHA-256 十六进制文本。
     */
    static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                String hex = Integer.toHexString(value & 0xFF);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    /**
     * 判断字符串是否为空白。
     */
    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * HTML Stream 响应头追加器。
     */
    interface HeaderContributor<T> {
        NetResponse addHeaders(NetResponse response, RemoteHtmlSession<T> session);
    }

    /**
     * session 移除监听器。
     */
    interface SessionRemovalListener<T> {
        void onSessionRemoved(RemoteHtmlSession<T> session);
    }

    /**
     * 服务端保存的远程 HTML session。
     */
    static final class RemoteHtmlSession<T> {

        private final String sessionId;
        private final Object player;
        private final T payload;
        private final byte[] htmlBytes;
        private final String sha256;
        private final long expiresAtMillis;

        private RemoteHtmlSession(String sessionId, Object player, T payload, byte[] htmlBytes, String sha256,
                long expiresAtMillis) {
            this.sessionId = sessionId;
            this.player = player;
            this.payload = payload;
            this.htmlBytes = htmlBytes == null ? new byte[0] : htmlBytes.clone();
            this.sha256 = sha256;
            this.expiresAtMillis = expiresAtMillis;
        }

        String getSessionId() {
            return sessionId;
        }

        Object getPlayer() {
            return player;
        }

        T getPayload() {
            return payload;
        }

        byte[] getHtmlBytes() {
            return htmlBytes.clone();
        }

        int getHtmlByteCount() {
            return htmlBytes.length;
        }

        String getSha256() {
            return sha256;
        }

        boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }

        boolean matchesPlayer(Object candidate) {
            return player == candidate || (player != null && player.equals(candidate));
        }
    }

    private static final class StreamRequest {
        String sessionId;
    }
}
