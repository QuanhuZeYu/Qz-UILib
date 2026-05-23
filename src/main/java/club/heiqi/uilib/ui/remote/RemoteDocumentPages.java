package club.heiqi.uilib.ui.remote;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetChannel;
import club.heiqi.uilib.net.api.NetChannelId;
import club.heiqi.uilib.net.api.NetContentType;
import club.heiqi.uilib.net.api.NetEndpointId;
import club.heiqi.uilib.net.api.NetMessage;
import club.heiqi.uilib.net.api.NetReceiveContext;
import club.heiqi.uilib.net.api.NetRequest;
import club.heiqi.uilib.net.api.NetResponse;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.api.NetStreamCall;
import club.heiqi.uilib.net.api.NetStreamEndpoint;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * 服务端下发远程 HTML-like 文档页面的公开入口。
 *
 * <p>该入口基于 Qz 网络层的 Channel + Stream 实现：服务端先发送轻量打开 offer，
 * 客户端随后按 session 拉取 HTML 内容并显示为 `UiDocumentScreens` 文档页面。</p>
 */
public final class RemoteDocumentPages {

    public static final NetContentType REMOTE_HTML_CONTENT_TYPE =
            NetContentType.of("text/vnd.qzuilib.remote-html; charset=utf-8");
    private static final String CLIENT_BRIDGE_CLASS =
            "club.heiqi.uilib.ui.remote.RemoteDocumentClientBridge";
    private static final long SESSION_TTL_MILLIS = Duration.ofMinutes(10L).toMillis();
    private static final long STREAM_MAX_BYTES = 256L * 1024L * 1024L;
    private static final Map<String, ServerSession> SERVER_SESSIONS =
            new ConcurrentHashMap<String, ServerSession>();

    private static volatile boolean registered;
    private static NetChannel openChannel;
    private static NetChannel submitChannel;
    private static NetStreamEndpoint streamEndpoint;

    private RemoteDocumentPages() {}

    /**
     * 注册远程页面内置网络端点。
     *
     * <p>该方法由 Qz UILib 在 preInit 调用；业务方通常不需要手动调用。</p>
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        NetService service = NetService.getInstance();
        openChannel = service.channel(NetChannelId.of(MyMod.MODID, "remote_page_open"))
                .onReceive(new NetChannel.NetChannelHandler() {
                    @Override
                    public void onReceive(final NetMessage message, final NetReceiveContext context) {
                        if (context.getSide() != NetSide.CLIENT) {
                            return;
                        }
                        context.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                dispatchClientOpenOffer(message.getBody().asUtf8String());
                            }
                        });
                    }
                })
                .register();
        submitChannel = service.channel(NetChannelId.of(MyMod.MODID, "remote_page_submit"))
                .onReceive(new NetChannel.NetChannelHandler() {
                    @Override
                    public void onReceive(final NetMessage message, final NetReceiveContext context) {
                        if (context.getSide() != NetSide.SERVER) {
                            return;
                        }
                        context.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                handleSubmit(message.getBody().asUtf8String(), context.getSenderPlayer());
                            }
                        });
                    }
                })
                .register();
        streamEndpoint = service.stream(NetEndpointId.of(MyMod.MODID, "remote_page_html"))
                .timeout(Duration.ofSeconds(60L))
                .maxBytes(STREAM_MAX_BYTES)
                .onRequest(new NetStreamEndpoint.NetStreamHandler() {
                    @Override
                    public void onRequest(NetRequest request,
                            NetStreamEndpoint.NetStreamRequestContext context) {
                        handleStreamRequest(request, context);
                    }
                })
                .register();
        registered = true;
    }

    /**
     * 向玩家打开远程页面。
     *
     * @param player 目标玩家，服务端通常是 EntityPlayerMP
     * @param page 页面内容
     * @param handler 表单提交处理器；可为 null
     * @return 创建的远程页面 sessionId
     */
    public static String open(Object player, RemoteDocumentPage page, RemoteDocumentSubmitHandler handler) {
        ensureRegistered();
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        RemoteDocumentPage resolvedPage = requirePage(page);
        cleanupExpiredSessions();
        String sessionId = UUID.randomUUID().toString();
        byte[] htmlBytes = resolvedPage.getHtml().getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256Hex(htmlBytes);
        SERVER_SESSIONS.put(sessionId, new ServerSession(sessionId, player, resolvedPage, handler, htmlBytes,
                sha256, System.currentTimeMillis() + SESSION_TTL_MILLIS));
        openChannel.toPlayer(player).send(NetMessage.json(RemoteJson.toJson(OpenOffer.from(sessionId,
                resolvedPage, htmlBytes.length, sha256))));
        return sessionId;
    }

    /**
     * 向玩家打开不需要表单回调的远程页面。
     *
     * @param player 目标玩家
     * @param page 页面内容
     * @return 创建的远程页面 sessionId
     */
    public static String open(Object player, RemoteDocumentPage page) {
        return open(player, page, null);
    }

    static NetStreamCall callPageStream(String sessionId) {
        ensureRegistered();
        StreamRequest request = new StreamRequest();
        request.sessionId = sessionId == null ? "" : sessionId;
        return streamEndpoint.call(NetRequest.json(RemoteJson.toJson(request)));
    }

    static void submitFromClient(SubmitPayload payload) {
        ensureRegistered();
        submitChannel.toServer().send(NetMessage.json(RemoteJson.toJson(payload)));
    }

    static OpenOffer decodeOpenOffer(String json) {
        OpenOffer offer = RemoteJson.fromJson(json, OpenOffer.class);
        if (offer == null || isBlank(offer.sessionId)) {
            throw new IllegalArgumentException("远程页面 open offer 缺少 sessionId");
        }
        return offer;
    }

    static SubmitPayload decodeSubmitPayload(String json) {
        SubmitPayload payload = RemoteJson.fromJson(json, SubmitPayload.class);
        if (payload == null || isBlank(payload.sessionId)) {
            throw new IllegalArgumentException("远程页面提交缺少 sessionId");
        }
        if (payload.values == null) {
            payload.values = Collections.emptyMap();
        }
        return payload;
    }

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

    static void resetForTests() {
        SERVER_SESSIONS.clear();
        registered = false;
        openChannel = null;
        submitChannel = null;
        streamEndpoint = null;
    }

    private static void handleStreamRequest(NetRequest request,
            NetStreamEndpoint.NetStreamRequestContext context) {
        if (context.getReceiveContext().getSide() != NetSide.SERVER) {
            context.reply(NetResponse.error(400, "远程页面 Stream 仅接受客户端请求"));
            return;
        }
        StreamRequest streamRequest;
        try {
            streamRequest = RemoteJson.fromJson(request.getBody().asUtf8String(), StreamRequest.class);
        } catch (IllegalArgumentException exception) {
            context.reply(NetResponse.error(400, exception.getMessage()));
            return;
        }
        ServerSession session = streamRequest == null ? null : SERVER_SESSIONS.get(streamRequest.sessionId);
        if (session == null || session.isExpired(System.currentTimeMillis())) {
            context.reply(NetResponse.error(404, "远程页面 session 已失效"));
            return;
        }
        if (!session.matchesPlayer(context.getReceiveContext().getSenderPlayer())) {
            context.reply(NetResponse.error(403, "远程页面 session 不属于当前玩家"));
            return;
        }
        context.reply(NetResponse.ok(NetBody.of(REMOTE_HTML_CONTENT_TYPE, session.htmlBytes))
                .withHeader("x-qz-page-id", session.page.getPageId())
                .withHeader("x-qz-session-id", session.sessionId)
                .withHeader("x-qz-sha256", session.sha256)
                .withHeader("x-qz-resource-policy", session.page.getResourcePolicy().name())
                .withHeader("x-qz-html-bytes", Integer.toString(session.htmlBytes.length)));
    }

    private static void handleSubmit(String json, Object senderPlayer) {
        SubmitPayload payload;
        try {
            payload = decodeSubmitPayload(json);
        } catch (IllegalArgumentException exception) {
            MyMod.LOG.warn("远程页面提交协议无效", exception);
            return;
        }
        cleanupExpiredSessions();
        ServerSession session = SERVER_SESSIONS.get(payload.sessionId);
        if (session == null) {
            MyMod.LOG.warn("远程页面提交 session 不存在：{}", payload.sessionId);
            return;
        }
        if (!session.matchesPlayer(senderPlayer)) {
            MyMod.LOG.warn("远程页面提交玩家不匹配：session={} sender={}", payload.sessionId,
                    String.valueOf(senderPlayer));
            return;
        }
        if (!session.page.getPageId().equals(payload.pageId)) {
            MyMod.LOG.warn("远程页面提交 pageId 不匹配：session={} expected={} actual={}",
                    payload.sessionId, session.page.getPageId(), payload.pageId);
            return;
        }
        if (session.handler == null) {
            MyMod.LOG.debug("远程页面提交无 handler：session={} page={}", payload.sessionId, payload.pageId);
            return;
        }
        session.handler.onSubmit(new RemoteDocumentSubmitEvent(senderPlayer, payload.sessionId, payload.pageId,
                payload.action, payload.formId, payload.values, session.handler));
    }

    private static void dispatchClientOpenOffer(String json) {
        try {
            Class<?> bridgeClass = Class.forName(CLIENT_BRIDGE_CLASS);
            Method method = bridgeClass.getDeclaredMethod("receiveOpenOffer", String.class);
            method.setAccessible(true);
            method.invoke(null, json);
        } catch (ClassNotFoundException exception) {
            MyMod.LOG.warn("远程页面客户端桥缺失", exception);
        } catch (NoSuchMethodException exception) {
            MyMod.LOG.warn("远程页面客户端桥入口缺失", exception);
        } catch (IllegalAccessException exception) {
            MyMod.LOG.warn("远程页面客户端桥不可访问", exception);
        } catch (InvocationTargetException exception) {
            MyMod.LOG.warn("远程页面客户端桥执行失败", exception.getCause());
        }
    }

    private static void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        for (ServerSession session : SERVER_SESSIONS.values()) {
            if (session.isExpired(now)) {
                SERVER_SESSIONS.remove(session.sessionId, session);
            }
        }
    }

    private static RemoteDocumentPage requirePage(RemoteDocumentPage page) {
        if (page == null) {
            throw new IllegalArgumentException("page must not be null");
        }
        return page;
    }

    private static void ensureRegistered() {
        if (!registered || openChannel == null || submitChannel == null || streamEndpoint == null) {
            throw new IllegalStateException("远程页面网络端点尚未注册，请确认 Qz UILib preInit 已完成");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static final class OpenOffer {

        String sessionId;
        String pageId;
        String title;
        String resourcePolicy;
        String sha256;
        int htmlBytes;
        Map<String, String> metadata;

        static OpenOffer from(String sessionId, RemoteDocumentPage page, int htmlBytes, String sha256) {
            OpenOffer offer = new OpenOffer();
            offer.sessionId = sessionId;
            offer.pageId = page.getPageId();
            offer.title = page.getTitle();
            offer.resourcePolicy = page.getResourcePolicy().name();
            offer.sha256 = sha256;
            offer.htmlBytes = htmlBytes;
            offer.metadata = new LinkedHashMap<String, String>(page.getMetadata());
            return offer;
        }
    }

    static final class StreamRequest {
        String sessionId;
    }

    static final class SubmitPayload {
        String sessionId;
        String pageId;
        String action;
        String formId;
        Map<String, List<String>> values = Collections.emptyMap();
    }

    private static final class ServerSession {

        private final String sessionId;
        private final Object player;
        private final RemoteDocumentPage page;
        private final RemoteDocumentSubmitHandler handler;
        private final byte[] htmlBytes;
        private final String sha256;
        private final long expiresAtMillis;

        private ServerSession(String sessionId, Object player, RemoteDocumentPage page,
                RemoteDocumentSubmitHandler handler, byte[] htmlBytes, String sha256, long expiresAtMillis) {
            this.sessionId = sessionId;
            this.player = player;
            this.page = page;
            this.handler = handler;
            this.htmlBytes = htmlBytes.clone();
            this.sha256 = sha256;
            this.expiresAtMillis = expiresAtMillis;
        }

        private boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }

        private boolean matchesPlayer(Object candidate) {
            return player == candidate || player.equals(candidate);
        }
    }
}
