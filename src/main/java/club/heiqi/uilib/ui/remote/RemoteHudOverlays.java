package club.heiqi.uilib.ui.remote;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * 服务端下发远程 HTML-like HUD 浮层的公开入口。
 *
 * <p>该入口与远程页面共用安全子集 HTML 解析、表单收集与外链策略；
 * 区别只在于客户端使用 HUD 宿主而不是整页 Screen。</p>
 */
public final class RemoteHudOverlays {

    public static final NetContentType REMOTE_HTML_CONTENT_TYPE =
            RemoteHtmlSessionGateway.REMOTE_HTML_CONTENT_TYPE;
    private static final String CLIENT_BRIDGE_CLASS =
            "club.heiqi.uilib.ui.remote.RemoteHudOverlayClientBridge";
    private static final long STREAM_MAX_BYTES = RemoteHtmlSessionGateway.DEFAULT_STREAM_MAX_BYTES;
    private static final RemoteHtmlSessionGateway<HudSession> SERVER_SESSIONS =
            new RemoteHtmlSessionGateway<HudSession>("远程 HUD");
    private static final Map<OverlayKey, String> ACTIVE_SESSION_IDS_BY_OVERLAY_KEY =
            new ConcurrentHashMap<OverlayKey, String>();

    private static volatile boolean registered;
    private static NetChannel openChannel;
    private static NetChannel dismissChannel;
    private static NetChannel submitChannel;
    private static NetStreamEndpoint streamEndpoint;

    private RemoteHudOverlays() {}

    /**
     * 注册远程 HUD 内置网络端点。
     *
     * <p>该方法由 Qz UILib 在 preInit 调用；业务方通常不需要手动调用。</p>
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        NetService service = NetService.getInstance();
        openChannel = service.channel(NetChannelId.of(MyMod.MODID, "remote_hud_open"))
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
        dismissChannel = service.channel(NetChannelId.of(MyMod.MODID, "remote_hud_dismiss"))
                .onReceive(new NetChannel.NetChannelHandler() {
                    @Override
                    public void onReceive(final NetMessage message, final NetReceiveContext context) {
                        if (context.getSide() == NetSide.CLIENT) {
                            context.runOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    dispatchClientDismiss(message.getBody().asUtf8String());
                                }
                            });
                            return;
                        }
                        context.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                handleClientDismiss(message.getBody().asUtf8String(), context.getSenderPlayer());
                            }
                        });
                    }
                })
                .register();
        submitChannel = service.channel(NetChannelId.of(MyMod.MODID, "remote_hud_submit"))
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
        streamEndpoint = service.stream(NetEndpointId.of(MyMod.MODID, "remote_hud_html"))
                .timeout(Duration.ofSeconds(60L))
                .maxBytes(STREAM_MAX_BYTES)
                .onRequest(new NetStreamEndpoint.NetStreamHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetStreamEndpoint.NetStreamRequestContext context) {
                        handleStreamRequest(request, context);
                    }
                })
                .register();
        registered = true;
    }

    /**
     * 向玩家打开远程 HUD 浮层。
     *
     * @param player 目标玩家
     * @param overlay 浮层内容
     * @param handler 表单提交处理器；可为 null
     * @return 创建的会话 id
     */
    public static String open(Object player, RemoteHudOverlay overlay, RemoteHudSubmitHandler handler) {
        ensureRegistered();
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        RemoteHudOverlay resolvedOverlay = requireOverlay(overlay);
        cleanupExpiredSessions();
        RemoteHtmlSessionGateway.RemoteHtmlSession<HudSession> session = SERVER_SESSIONS.createSession(player,
                new HudSession(resolvedOverlay, handler), resolvedOverlay.getPage().getHtml());
        OverlayKey overlayKey = OverlayKey.of(player, resolvedOverlay.getOverlayId());
        String previousSessionId = ACTIVE_SESSION_IDS_BY_OVERLAY_KEY.put(overlayKey, session.getSessionId());
        if (previousSessionId != null) {
            SERVER_SESSIONS.removeSession(previousSessionId);
        }
        openChannel.toPlayer(player).send(NetMessage.json(RemoteJson.toJson(OpenOffer.from(session.getSessionId(),
                resolvedOverlay, session.getHtmlByteCount(), session.getSha256()))));
        return session.getSessionId();
    }

    /**
     * 向玩家打开不需要表单回调的远程 HUD 浮层。
     *
     * @param player 目标玩家
     * @param overlay 浮层内容
     * @return 创建的会话 id
     */
    public static String open(Object player, RemoteHudOverlay overlay) {
        return open(player, overlay, null);
    }

    /**
     * 以对话框模式打开远程 HUD。
     *
     * @param player 目标玩家
     * @param page 远程页面
     * @param handler 提交处理器；可为 null
     * @return 会话 id
     */
    public static String showDialog(Object player, RemoteDocumentPage page, RemoteHudSubmitHandler handler) {
        return open(player, RemoteHudOverlay.dialog(page.getPageId(), page).build(), handler);
    }

    /**
     * 以对话框模式打开指定业务 id 的远程 HUD。
     *
     * @param player 目标玩家
     * @param overlayId 浮层业务标识
     * @param page 远程页面
     * @param handler 提交处理器；可为 null
     * @return 会话 id
     */
    public static String showDialog(Object player, String overlayId, RemoteDocumentPage page,
            RemoteHudSubmitHandler handler) {
        return open(player, RemoteHudOverlay.dialog(overlayId, page).build(), handler);
    }

    /**
     * 以自动消失提示模式打开远程 HUD。
     *
     * @param player 目标玩家
     * @param page 远程页面
     * @return 会话 id
     */
    public static String showToast(Object player, RemoteDocumentPage page) {
        return open(player, RemoteHudOverlay.toast(page.getPageId(), page).build(), null);
    }

    /**
     * 以自动消失提示模式打开指定业务 id 的远程 HUD。
     *
     * @param player 目标玩家
     * @param overlayId 浮层业务标识
     * @param page 远程页面
     * @param durationMillis 显示毫秒数
     * @return 会话 id
     */
    public static String showToast(Object player, String overlayId, RemoteDocumentPage page, long durationMillis) {
        return open(player, RemoteHudOverlay.toast(overlayId, page).durationMillis(durationMillis).build(), null);
    }

    /**
     * 以弹幕模式打开远程 HUD。
     *
     * @param player 目标玩家
     * @param page 远程页面
     * @return 会话 id
     */
    public static String showDanmaku(Object player, RemoteDocumentPage page) {
        return open(player, RemoteHudOverlay.danmaku(page.getPageId(), page).build(), null);
    }

    /**
     * 以弹幕模式打开指定业务 id 的远程 HUD。
     *
     * @param player 目标玩家
     * @param overlayId 浮层业务标识
     * @param page 远程页面
     * @return 会话 id
     */
    public static String showDanmaku(Object player, String overlayId, RemoteDocumentPage page) {
        return open(player, RemoteHudOverlay.danmaku(overlayId, page).build(), null);
    }

    /**
     * 关闭指定玩家的指定浮层。
     *
     * @param player 目标玩家
     * @param overlayId 浮层业务标识
     * @return 是否找到并关闭了活动会话
     */
    public static boolean dismiss(Object player, String overlayId) {
        ensureRegistered();
        if (player == null || RemoteHtmlSessionGateway.isBlank(overlayId)) {
            return false;
        }
        OverlayKey key = OverlayKey.of(player, overlayId);
        String sessionId = ACTIVE_SESSION_IDS_BY_OVERLAY_KEY.remove(key);
        DismissPayload payload = new DismissPayload();
        payload.sessionId = sessionId == null ? "" : sessionId;
        payload.overlayId = overlayId;
        payload.reason = "server-dismiss";
        if (sessionId != null) {
            RemoteHtmlSessionGateway.RemoteHtmlSession<HudSession> session =
                    SERVER_SESSIONS.removeSession(sessionId);
            if (session != null) {
                contextSendDismissToClient(player, payload);
                return true;
            }
        }
        contextSendDismissToClient(player, payload);
        return false;
    }

    static NetStreamCall callOverlayStream(String sessionId) {
        ensureRegistered();
        return SERVER_SESSIONS.callStream(streamEndpoint, sessionId);
    }

    static void submitFromClient(SubmitPayload payload) {
        ensureRegistered();
        submitChannel.toServer().send(NetMessage.json(RemoteJson.toJson(payload)));
    }

    static void dismissFromClient(DismissPayload payload) {
        ensureRegistered();
        dismissChannel.toServer().send(NetMessage.json(RemoteJson.toJson(payload)));
    }

    static OpenOffer decodeOpenOffer(String json) {
        OpenOffer offer = RemoteJson.fromJson(json, OpenOffer.class);
        if (offer == null || RemoteHtmlSessionGateway.isBlank(offer.sessionId)
                || RemoteHtmlSessionGateway.isBlank(offer.overlayId)) {
            throw new IllegalArgumentException("远程 HUD open offer 缺少 sessionId 或 overlayId");
        }
        return offer;
    }

    static SubmitPayload decodeSubmitPayload(String json) {
        SubmitPayload payload = RemoteJson.fromJson(json, SubmitPayload.class);
        if (payload == null || RemoteHtmlSessionGateway.isBlank(payload.sessionId)
                || RemoteHtmlSessionGateway.isBlank(payload.overlayId)) {
            throw new IllegalArgumentException("远程 HUD 提交缺少 sessionId 或 overlayId");
        }
        if (payload.values == null) {
            payload.values = Collections.emptyMap();
        }
        return payload;
    }

    static DismissPayload decodeDismissPayload(String json) {
        DismissPayload payload = RemoteJson.fromJson(json, DismissPayload.class);
        if (payload == null || RemoteHtmlSessionGateway.isBlank(payload.overlayId)) {
            throw new IllegalArgumentException("远程 HUD dismiss 缺少 overlayId");
        }
        payload.sessionId = payload.sessionId == null ? "" : payload.sessionId;
        payload.reason = payload.reason == null ? "" : payload.reason;
        return payload;
    }

    static String sha256Hex(byte[] bytes) {
        return RemoteHtmlSessionGateway.sha256Hex(bytes);
    }

    static void resetForTests() {
        SERVER_SESSIONS.clear();
        ACTIVE_SESSION_IDS_BY_OVERLAY_KEY.clear();
        registered = false;
        openChannel = null;
        dismissChannel = null;
        submitChannel = null;
        streamEndpoint = null;
    }

    private static void handleStreamRequest(NetRequest request,
            NetStreamEndpoint.NetStreamRequestContext context) {
        SERVER_SESSIONS.handleStreamRequest(request, context,
                new RemoteHtmlSessionGateway.HeaderContributor<HudSession>() {
                    @Override
                    public NetResponse addHeaders(NetResponse response,
                            RemoteHtmlSessionGateway.RemoteHtmlSession<HudSession> session) {
                        RemoteHudOverlay overlay = session.getPayload().overlay;
                        return response
                                .withHeader("x-qz-overlay-id", overlay.getOverlayId())
                                .withHeader("x-qz-page-id", overlay.getPage().getPageId())
                                .withHeader("x-qz-resource-policy", overlay.getPage().getResourcePolicy().name())
                                .withHeader("x-qz-overlay-mode", overlay.getMode().name())
                                .withHeader("x-qz-overlay-duration", Long.toString(overlay.getDurationMillis()))
                                .withHeader("x-qz-overlay-close-button",
                                        Boolean.toString(overlay.isDefaultCloseButtonVisible()))
                                .withHeader("x-qz-overlay-close-label", overlay.getCloseButtonLabel());
                    }
                });
    }

    private static void handleSubmit(String json, Object senderPlayer) {
        SubmitPayload payload;
        try {
            payload = decodeSubmitPayload(json);
        } catch (IllegalArgumentException exception) {
            MyMod.LOG.warn("远程 HUD 提交协议无效", exception);
            return;
        }
        cleanupExpiredSessions();
        RemoteHtmlSessionGateway.RemoteHtmlSession<HudSession> session =
                SERVER_SESSIONS.getSession(payload.sessionId);
        if (session == null) {
            MyMod.LOG.warn("远程 HUD 提交 session 不存在：{}", payload.sessionId);
            return;
        }
        if (!session.matchesPlayer(senderPlayer)) {
            MyMod.LOG.warn("远程 HUD 提交玩家不匹配：session={} sender={}", payload.sessionId,
                    String.valueOf(senderPlayer));
            return;
        }
        HudSession hudSession = session.getPayload();
        if (!hudSession.overlay.getOverlayId().equals(payload.overlayId)) {
            MyMod.LOG.warn("远程 HUD 提交 overlayId 不匹配：session={} expected={} actual={}",
                    payload.sessionId, hudSession.overlay.getOverlayId(), payload.overlayId);
            return;
        }
        if (!hudSession.overlay.getPage().getPageId().equals(payload.pageId)) {
            MyMod.LOG.warn("远程 HUD 提交 pageId 不匹配：session={} expected={} actual={}",
                    payload.sessionId, hudSession.overlay.getPage().getPageId(), payload.pageId);
            return;
        }
        if (hudSession.handler == null) {
            MyMod.LOG.debug("远程 HUD 提交无 handler：session={} overlay={}", payload.sessionId, payload.overlayId);
            return;
        }
        hudSession.handler.onSubmit(new RemoteHudSubmitEvent(senderPlayer, payload.sessionId, payload.overlayId,
                payload.pageId, payload.action, payload.formId, payload.values, hudSession.handler));
    }

    private static void handleClientDismiss(String json, Object senderPlayer) {
        DismissPayload payload;
        try {
            payload = decodeDismissPayload(json);
        } catch (IllegalArgumentException exception) {
            MyMod.LOG.warn("远程 HUD dismiss 协议无效", exception);
            return;
        }
        String sessionId = payload.sessionId;
        if (!RemoteHtmlSessionGateway.isBlank(sessionId)) {
            RemoteHtmlSessionGateway.RemoteHtmlSession<HudSession> session =
                    SERVER_SESSIONS.getSession(sessionId);
            if (session != null && session.matchesPlayer(senderPlayer)) {
                removeSession(session.getSessionId());
                return;
            }
        }
        OverlayKey overlayKey = OverlayKey.of(senderPlayer, payload.overlayId);
        String mappedSessionId = ACTIVE_SESSION_IDS_BY_OVERLAY_KEY.remove(overlayKey);
        if (mappedSessionId != null) {
            removeSession(mappedSessionId);
        }
    }

    private static void removeSession(String sessionId) {
        if (RemoteHtmlSessionGateway.isBlank(sessionId)) {
            return;
        }
        RemoteHtmlSessionGateway.RemoteHtmlSession<HudSession> session =
                SERVER_SESSIONS.removeSession(sessionId);
        if (session == null) {
            return;
        }
        HudSession hudSession = session.getPayload();
        ACTIVE_SESSION_IDS_BY_OVERLAY_KEY.remove(OverlayKey.of(session.getPlayer(), hudSession.overlay.getOverlayId()),
                sessionId);
    }

    private static void dispatchClientOpenOffer(String json) {
        try {
            Class<?> bridgeClass = Class.forName(CLIENT_BRIDGE_CLASS);
            Method method = bridgeClass.getDeclaredMethod("receiveOpenOffer", String.class);
            method.setAccessible(true);
            method.invoke(null, json);
        } catch (ClassNotFoundException exception) {
            MyMod.LOG.warn("远程 HUD 客户端桥缺失", exception);
        } catch (NoSuchMethodException exception) {
            MyMod.LOG.warn("远程 HUD 客户端桥入口缺失", exception);
        } catch (IllegalAccessException exception) {
            MyMod.LOG.warn("远程 HUD 客户端桥不可访问", exception);
        } catch (InvocationTargetException exception) {
            MyMod.LOG.warn("远程 HUD 客户端桥执行失败", exception.getCause());
        }
    }

    private static void dispatchClientDismiss(String json) {
        try {
            Class<?> bridgeClass = Class.forName(CLIENT_BRIDGE_CLASS);
            Method method = bridgeClass.getDeclaredMethod("receiveDismiss", String.class);
            method.setAccessible(true);
            method.invoke(null, json);
        } catch (ClassNotFoundException exception) {
            MyMod.LOG.warn("远程 HUD 客户端桥缺失", exception);
        } catch (NoSuchMethodException exception) {
            MyMod.LOG.warn("远程 HUD 客户端桥 dismiss 入口缺失", exception);
        } catch (IllegalAccessException exception) {
            MyMod.LOG.warn("远程 HUD 客户端桥 dismiss 不可访问", exception);
        } catch (InvocationTargetException exception) {
            MyMod.LOG.warn("远程 HUD 客户端桥 dismiss 执行失败", exception.getCause());
        }
    }

    private static void contextSendDismissToClient(Object player, DismissPayload payload) {
        if (player == null) {
            return;
        }
        dismissChannel.toPlayer(player).send(NetMessage.json(RemoteJson.toJson(payload)));
    }

    private static void cleanupExpiredSessions() {
        SERVER_SESSIONS.cleanupExpiredSessions(new RemoteHtmlSessionGateway.SessionRemovalListener<HudSession>() {
            @Override
            public void onSessionRemoved(RemoteHtmlSessionGateway.RemoteHtmlSession<HudSession> session) {
                HudSession hudSession = session.getPayload();
                ACTIVE_SESSION_IDS_BY_OVERLAY_KEY.remove(OverlayKey.of(session.getPlayer(),
                        hudSession.overlay.getOverlayId()), session.getSessionId());
            }
        });
    }

    private static RemoteHudOverlay requireOverlay(RemoteHudOverlay overlay) {
        if (overlay == null) {
            throw new IllegalArgumentException("overlay must not be null");
        }
        return overlay;
    }

    private static void ensureRegistered() {
        if (!registered || openChannel == null || dismissChannel == null || submitChannel == null
                || streamEndpoint == null) {
            throw new IllegalStateException("远程 HUD 网络端点尚未注册，请确认 Qz UILib preInit 已完成");
        }
    }

    static final class OpenOffer {

        String sessionId;
        String overlayId;
        String pageId;
        String title;
        String mode;
        String resourcePolicy;
        String sha256;
        int htmlBytes;
        long durationMillis;
        boolean defaultCloseButtonVisible;
        String closeButtonLabel;
        Map<String, String> metadata;
        Map<String, String> pageMetadata;

        static OpenOffer from(String sessionId, RemoteHudOverlay overlay, int htmlBytes, String sha256) {
            OpenOffer offer = new OpenOffer();
            offer.sessionId = sessionId;
            offer.overlayId = overlay.getOverlayId();
            offer.pageId = overlay.getPage().getPageId();
            offer.title = overlay.getPage().getTitle();
            offer.mode = overlay.getMode().name();
            offer.resourcePolicy = overlay.getPage().getResourcePolicy().name();
            offer.sha256 = sha256;
            offer.htmlBytes = htmlBytes;
            offer.durationMillis = overlay.getDurationMillis();
            offer.defaultCloseButtonVisible = overlay.isDefaultCloseButtonVisible();
            offer.closeButtonLabel = overlay.getCloseButtonLabel();
            offer.metadata = new LinkedHashMap<String, String>(overlay.getMetadata());
            offer.pageMetadata = new LinkedHashMap<String, String>(overlay.getPage().getMetadata());
            return offer;
        }
    }

    static final class SubmitPayload {
        String sessionId;
        String overlayId;
        String pageId;
        String action;
        String formId;
        Map<String, List<String>> values = Collections.emptyMap();
    }

    static final class DismissPayload {
        String sessionId;
        String overlayId;
        String reason;
    }

    private static final class HudSession {

        private final RemoteHudOverlay overlay;
        private final RemoteHudSubmitHandler handler;

        private HudSession(RemoteHudOverlay overlay, RemoteHudSubmitHandler handler) {
            this.overlay = overlay;
            this.handler = handler;
        }
    }

    private static final class OverlayKey {

        private final Object player;
        private final String overlayId;

        private OverlayKey(Object player, String overlayId) {
            this.player = player;
            this.overlayId = overlayId == null ? "" : overlayId;
        }

        static OverlayKey of(Object player, String overlayId) {
            return new OverlayKey(player, overlayId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof OverlayKey)) {
                return false;
            }
            OverlayKey other = (OverlayKey) obj;
            return java.util.Objects.equals(player, other.player)
                    && overlayId.equals(other.overlayId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(player, overlayId);
        }
    }
}
