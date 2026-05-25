package club.heiqi.uilib.ui.remote;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.MyMod;
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
            RemoteHtmlSessionGateway.REMOTE_HTML_CONTENT_TYPE;
    private static final String CLIENT_BRIDGE_CLASS =
            "club.heiqi.uilib.ui.remote.RemoteDocumentClientBridge";
    private static final long STREAM_MAX_BYTES = RemoteHtmlSessionGateway.DEFAULT_STREAM_MAX_BYTES;
    private static final RemoteHtmlSessionGateway<PageSession> SERVER_SESSIONS =
            new RemoteHtmlSessionGateway<PageSession>("远程页面");

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
        RemoteHtmlSessionGateway.RemoteHtmlSession<PageSession> session = SERVER_SESSIONS.createSession(player,
                new PageSession(resolvedPage, handler), resolvedPage.getHtml());
        openChannel.toPlayer(player).send(NetMessage.json(RemoteJson.toJson(OpenOffer.from(session.getSessionId(),
                resolvedPage, session.getHtmlByteCount(), session.getSha256()))));
        return session.getSessionId();
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
        return SERVER_SESSIONS.callStream(streamEndpoint, sessionId);
    }

    static void submitFromClient(SubmitPayload payload) {
        ensureRegistered();
        submitChannel.toServer().send(NetMessage.json(RemoteJson.toJson(payload)));
    }

    static OpenOffer decodeOpenOffer(String json) {
        OpenOffer offer = RemoteJson.fromJson(json, OpenOffer.class);
        if (offer == null || RemoteHtmlSessionGateway.isBlank(offer.sessionId)) {
            throw new IllegalArgumentException("远程页面 open offer 缺少 sessionId");
        }
        return offer;
    }

    static SubmitPayload decodeSubmitPayload(String json) {
        SubmitPayload payload = RemoteJson.fromJson(json, SubmitPayload.class);
        if (payload == null || RemoteHtmlSessionGateway.isBlank(payload.sessionId)) {
            throw new IllegalArgumentException("远程页面提交缺少 sessionId");
        }
        if (payload.values == null) {
            payload.values = Collections.emptyMap();
        }
        return payload;
    }

    static String sha256Hex(byte[] bytes) {
        return RemoteHtmlSessionGateway.sha256Hex(bytes);
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
        SERVER_SESSIONS.handleStreamRequest(request, context,
                new RemoteHtmlSessionGateway.HeaderContributor<PageSession>() {
                    @Override
                    public NetResponse addHeaders(NetResponse response,
                            RemoteHtmlSessionGateway.RemoteHtmlSession<PageSession> session) {
                        RemoteDocumentPage page = session.getPayload().page;
                        return response
                                .withHeader("x-qz-page-id", page.getPageId())
                                .withHeader("x-qz-resource-policy", page.getResourcePolicy().name());
                    }
                });
    }

    private static void handleSubmit(String json, Object senderPlayer) {
        SubmitPayload payload;
        try {
            payload = decodeSubmitPayload(json);
        } catch (IllegalArgumentException exception) {
            MyMod.LOG.warn("远程页面提交协议无效", exception);
            return;
        }
        SERVER_SESSIONS.cleanupExpiredSessions(null);
        RemoteHtmlSessionGateway.RemoteHtmlSession<PageSession> session =
                SERVER_SESSIONS.getSession(payload.sessionId);
        if (session == null) {
            MyMod.LOG.warn("远程页面提交 session 不存在：{}", payload.sessionId);
            return;
        }
        if (!session.matchesPlayer(senderPlayer)) {
            MyMod.LOG.warn("远程页面提交玩家不匹配：session={} sender={}", payload.sessionId,
                    String.valueOf(senderPlayer));
            return;
        }
        PageSession pageSession = session.getPayload();
        if (!pageSession.page.getPageId().equals(payload.pageId)) {
            MyMod.LOG.warn("远程页面提交 pageId 不匹配：session={} expected={} actual={}",
                    payload.sessionId, pageSession.page.getPageId(), payload.pageId);
            return;
        }
        if (pageSession.handler == null) {
            MyMod.LOG.debug("远程页面提交无 handler：session={} page={}", payload.sessionId, payload.pageId);
            return;
        }
        pageSession.handler.onSubmit(new RemoteDocumentSubmitEvent(senderPlayer, payload.sessionId, payload.pageId,
                payload.action, payload.formId, payload.values, pageSession.handler));
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

    static final class SubmitPayload {
        String sessionId;
        String pageId;
        String action;
        String formId;
        Map<String, List<String>> values = Collections.emptyMap();
    }

    private static final class PageSession {

        private final RemoteDocumentPage page;
        private final RemoteDocumentSubmitHandler handler;

        private PageSession(RemoteDocumentPage page, RemoteDocumentSubmitHandler handler) {
            this.page = page;
            this.handler = handler;
        }
    }
}
