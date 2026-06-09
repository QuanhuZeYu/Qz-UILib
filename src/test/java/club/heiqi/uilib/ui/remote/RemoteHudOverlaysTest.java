package club.heiqi.uilib.ui.remote;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.api.NetStreamCall;
import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.transport.FrameHandler;
import club.heiqi.uilib.net.transport.ITransport;
import club.heiqi.uilib.net.transport.NetReceiveOrigin;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * 远程 HUD 网络入口测试。
 */
public class RemoteHudOverlaysTest {

    private NetService service;
    private RecordingTransport transport;

    @Before
    public void setUp() {
        resetNetService();
        RemoteHudOverlays.resetForTests();
        service = NetService.getInstance();
        transport = new RecordingTransport();
        service.bootstrap(transport);
        RemoteHudOverlays.register();
    }

    @After
    public void tearDown() {
        RemoteHudOverlays.resetForTests();
        resetNetService();
    }

    @Test
    public void shouldOpenStreamSubmitAndDismissHudOverlay() throws Exception {
        FakePlayer player = new FakePlayer("hudPlayer", 1);
        RemoteDocumentPage page = RemoteDocumentPage.builder("hud-page")
                .title("HUD 页面")
                .html("<html><body><form id=\"hud-form\" action=\"hud-submit\">"
                        + "<input type=\"text\" name=\"name\" value=\"alex\">"
                        + "<select name=\"phase\"><option value=\"wrong\">错误</option>"
                        + "<option value=\"hud-ok\" selected>HUD 已渲染</option></select>"
                        + "<button type=\"submit\" name=\"submitter\" value=\"提交 HUD\">提交 HUD</button>"
                        + "</form></body></html>")
                .build();
        final AtomicReference<RemoteHudSubmitEvent> captured = new AtomicReference<RemoteHudSubmitEvent>();
        String sessionId = RemoteHudOverlays.open(player, RemoteHudOverlay.dialog("hud-overlay", page).build(),
                new RemoteHudSubmitHandler() {
                    @Override
                    public void onSubmit(RemoteHudSubmitEvent event) {
                        captured.set(event);
                    }
                });

        Assert.assertEquals(1, transport.playerPayloads.size());
        NetEnvelope openEnvelope = NetEnvelope.decode(transport.playerPayloads.get(0).payload);
        Assert.assertEquals(NetEnvelope.Kind.CHANNEL, openEnvelope.getKind());
        Assert.assertEquals(MyMod.MODID + ":remote_hud_open", openEnvelope.getKey());
        RemoteHudOverlays.OpenOffer openOffer = RemoteHudOverlays.decodeOpenOffer(openEnvelope.toBody().asUtf8String());
        Assert.assertEquals("hud-overlay", openOffer.overlayId);
        Assert.assertEquals("hud-page", openOffer.pageId);
        Assert.assertEquals("HUD 页面", openOffer.title);
        Assert.assertEquals(RemoteHudOverlayMode.DIALOG.name(), openOffer.mode);
        Assert.assertTrue(openOffer.defaultCloseButtonVisible);

        NetStreamCall call = RemoteHudOverlays.callOverlayStream(sessionId);
        Assert.assertEquals(1, transport.clientToServerPayloads.size());
        transport.deliverToServer(transport.clientToServerPayloads.get(0), player);
        Assert.assertTrue(transport.playerPayloads.size() >= 2);
        NetEnvelope streamStart = NetEnvelope.decode(transport.playerPayloads.get(1).payload);
        Assert.assertEquals(NetEnvelope.Kind.STREAM_START, streamStart.getKind());
        Assert.assertEquals("hud-overlay", streamStart.getHeaders().get("x-qz-overlay-id"));
        Assert.assertEquals("hud-page", streamStart.getHeaders().get("x-qz-page-id"));
        Assert.assertEquals(MyMod.MODID + ":remote_hud_html", streamStart.getKey());

        RemoteHudOverlays.SubmitPayload submitPayload = new RemoteHudOverlays.SubmitPayload();
        submitPayload.sessionId = sessionId;
        submitPayload.overlayId = "hud-overlay";
        submitPayload.pageId = "hud-page";
        submitPayload.action = "hud-submit";
        submitPayload.formId = "hud-form";
        submitPayload.values = new LinkedHashMap<String, List<String>>();
        submitPayload.values.put("name", Arrays.asList("alex"));
        submitPayload.values.put("phase", Arrays.asList("hud-ok"));
        submitPayload.values.put("submitter", Arrays.asList("提交 HUD"));
        RemoteHudOverlays.submitFromClient(submitPayload);
        NetEnvelope submitEnvelope = NetEnvelope.decode(transport.clientToServerPayloads.get(1));
        Assert.assertEquals(NetEnvelope.Kind.CHANNEL, submitEnvelope.getKind());
        Assert.assertEquals(MyMod.MODID + ":remote_hud_submit", submitEnvelope.getKey());
        invokeHandleSubmit(submitEnvelope.toBody().asUtf8String(), player);

        Assert.assertNotNull(captured.get());
        Assert.assertEquals("hud-overlay", captured.get().getOverlayId());
        Assert.assertEquals("hud-ok", captured.get().getFirstValue("phase"));

        Assert.assertTrue(RemoteHudOverlays.dismiss(player, "hud-overlay"));
        NetEnvelope dismissEnvelope = NetEnvelope.decode(transport.playerPayloads.get(transport.playerPayloads.size() - 1)
                .payload);
        Assert.assertEquals(NetEnvelope.Kind.CHANNEL, dismissEnvelope.getKind());
        Assert.assertEquals(MyMod.MODID + ":remote_hud_dismiss", dismissEnvelope.getKey());
        RemoteHudOverlays.DismissPayload dismissPayload =
                RemoteJson.fromJson(dismissEnvelope.toBody().asUtf8String(), RemoteHudOverlays.DismissPayload.class);
        Assert.assertEquals("hud-overlay", dismissPayload.overlayId);
        Assert.assertEquals(sessionId, dismissPayload.sessionId);

        // 避免未完成的 stream future 在测试进程里留下悬挂引用。
        call.cancel();
    }

    @Test
    public void shouldExposeToastAndDanmakuOpenHelpers() {
        FakePlayer player = new FakePlayer("hudPlayer", 2);
        RemoteDocumentPage page = RemoteDocumentPage.of("toast-page", "Toast", "<p>ok</p>");

        RemoteHudOverlays.showToast(player, "toast-overlay", page, 1234L);
        RemoteHudOverlays.showDanmaku(player, "danmaku-overlay", page);

        Assert.assertEquals(2, transport.playerPayloads.size());
        RemoteHudOverlays.OpenOffer toastOffer = RemoteHudOverlays.decodeOpenOffer(
                NetEnvelope.decode(transport.playerPayloads.get(0).payload).toBody().asUtf8String());
        RemoteHudOverlays.OpenOffer danmakuOffer = RemoteHudOverlays.decodeOpenOffer(
                NetEnvelope.decode(transport.playerPayloads.get(1).payload).toBody().asUtf8String());
        Assert.assertEquals(RemoteHudOverlayMode.TOAST.name(), toastOffer.mode);
        Assert.assertEquals(1234L, toastOffer.durationMillis);
        Assert.assertEquals(RemoteHudOverlayMode.DANMAKU.name(), danmakuOffer.mode);
    }

    @Test
    public void shouldKeepNewSessionWhenOldClientDismissArrivesForSameOverlayId() throws Exception {
        FakePlayer player = new FakePlayer("hudPlayer", 3);
        RemoteDocumentPage firstPage = RemoteDocumentPage.of("hud-page-1", "HUD 1", "<p>one</p>");
        RemoteDocumentPage secondPage = RemoteDocumentPage.of("hud-page-2", "HUD 2", "<p>two</p>");

        String firstSessionId = RemoteHudOverlays.open(player,
                RemoteHudOverlay.dialog("same-overlay", firstPage).build(), null);
        String secondSessionId = RemoteHudOverlays.open(player,
                RemoteHudOverlay.dialog("same-overlay", secondPage).build(), null);

        RemoteHudOverlays.DismissPayload oldDismiss = new RemoteHudOverlays.DismissPayload();
        oldDismiss.sessionId = firstSessionId;
        oldDismiss.surfaceType = RemoteUiProtocol.SurfaceType.HUD.name();
        oldDismiss.surfaceId = "same-overlay";
        oldDismiss.contentRevision = 1L;
        oldDismiss.closeScope = RemoteUiProtocol.CloseScope.SESSION.name();
        oldDismiss.overlayId = "same-overlay";
        oldDismiss.reason = "client-close";
        invokeHandleClientDismiss(RemoteJson.toJson(oldDismiss), player);

        RemoteHudOverlays.SubmitPayload newSubmit = new RemoteHudOverlays.SubmitPayload();
        newSubmit.sessionId = secondSessionId;
        newSubmit.overlayId = "same-overlay";
        newSubmit.pageId = "hud-page-2";
        newSubmit.values = java.util.Collections.emptyMap();
        invokeHandleSubmit(RemoteJson.toJson(newSubmit), player);

        Assert.assertTrue("旧 session dismiss 不应移除同 overlayId 的新 session",
                RemoteHudOverlays.dismiss(player, "same-overlay"));
        NetEnvelope dismissEnvelope = NetEnvelope.decode(transport.playerPayloads.get(transport.playerPayloads.size() - 1)
                .payload);
        RemoteHudOverlays.DismissPayload dismissPayload =
                RemoteJson.fromJson(dismissEnvelope.toBody().asUtf8String(), RemoteHudOverlays.DismissPayload.class);
        Assert.assertEquals(secondSessionId, dismissPayload.sessionId);
    }

    @Test
    public void shouldKeepNewSessionWhenOldSubmitEventDismissesSameOverlayId() throws Exception {
        FakePlayer player = new FakePlayer("hudPlayer", 4);
        RemoteDocumentPage firstPage = RemoteDocumentPage.of("hud-page-1", "HUD 1", "<form id=\"f\"></form>");
        RemoteDocumentPage secondPage = RemoteDocumentPage.of("hud-page-2", "HUD 2", "<p>two</p>");
        final AtomicReference<RemoteHudSubmitEvent> captured = new AtomicReference<RemoteHudSubmitEvent>();

        String firstSessionId = RemoteHudOverlays.open(player,
                RemoteHudOverlay.dialog("same-overlay", firstPage).build(), new RemoteHudSubmitHandler() {
                    @Override
                    public void onSubmit(RemoteHudSubmitEvent event) {
                        captured.set(event);
                    }
                });
        RemoteHudOverlays.SubmitPayload firstSubmit = new RemoteHudOverlays.SubmitPayload();
        firstSubmit.sessionId = firstSessionId;
        firstSubmit.overlayId = "same-overlay";
        firstSubmit.pageId = "hud-page-1";
        firstSubmit.values = java.util.Collections.emptyMap();
        invokeHandleSubmit(RemoteJson.toJson(firstSubmit), player);
        Assert.assertNotNull(captured.get());

        String secondSessionId = RemoteHudOverlays.open(player,
                RemoteHudOverlay.dialog("same-overlay", secondPage).build(), null);
        transport.playerPayloads.clear();
        captured.get().dismiss();

        Assert.assertTrue("旧 submit event dismiss 不应关闭同 overlayId 的新 session",
                RemoteHudOverlays.dismiss(player, "same-overlay"));
        RemoteHudOverlays.DismissPayload dismissPayload = findDismissPayload();
        Assert.assertEquals(secondSessionId, dismissPayload.sessionId);
    }

    @Test
    public void shouldNotifyClientWhenHudSubmitFindsExpiredSession() throws Exception {
        final AtomicLong nowMillis = new AtomicLong(1_000L);
        RemoteHudOverlays.setSessionClockForTests(new LongSupplier() {
            @Override
            public long getAsLong() {
                return nowMillis.get();
            }
        });
        FakePlayer player = new FakePlayer("hudPlayer", 4);
        RemoteDocumentPage page = RemoteDocumentPage.of("hud-page", "HUD", "<form id=\"f\"></form>");
        String sessionId = RemoteHudOverlays.open(player, RemoteHudOverlay.dialog("hud-overlay", page).build(), null);
        transport.playerPayloads.clear();

        nowMillis.addAndGet(RemoteHtmlSessionGateway.DEFAULT_SESSION_TTL_MILLIS + 1L);
        RemoteHudOverlays.SubmitPayload submitPayload = new RemoteHudOverlays.SubmitPayload();
        submitPayload.sessionId = sessionId;
        submitPayload.overlayId = "hud-overlay";
        submitPayload.pageId = "hud-page";
        submitPayload.values = java.util.Collections.emptyMap();
        invokeHandleSubmit(RemoteJson.toJson(submitPayload), player);

        Assert.assertEquals(1, transport.playerPayloads.size());
        NetEnvelope dismissEnvelope = NetEnvelope.decode(transport.playerPayloads.get(0).payload);
        Assert.assertEquals(MyMod.MODID + ":remote_hud_dismiss", dismissEnvelope.getKey());
        RemoteHudOverlays.DismissPayload dismissPayload =
                RemoteJson.fromJson(dismissEnvelope.toBody().asUtf8String(), RemoteHudOverlays.DismissPayload.class);
        Assert.assertEquals(sessionId, dismissPayload.sessionId);
        Assert.assertEquals("hud-overlay", dismissPayload.overlayId);
        Assert.assertEquals("server-session-expired", dismissPayload.reason);
    }

    @Test
    public void shouldNotifyClientWhenHudStreamFindsExpiredSession() {
        final AtomicLong nowMillis = new AtomicLong(2_000L);
        RemoteHudOverlays.setSessionClockForTests(new LongSupplier() {
            @Override
            public long getAsLong() {
                return nowMillis.get();
            }
        });
        FakePlayer player = new FakePlayer("hudPlayer", 5);
        RemoteDocumentPage page = RemoteDocumentPage.of("hud-page", "HUD", "<p>late</p>");
        String sessionId = RemoteHudOverlays.open(player, RemoteHudOverlay.dialog("hud-overlay", page).build(), null);
        RemoteHudOverlays.callOverlayStream(sessionId);
        int requestIndex = transport.clientToServerPayloads.size() - 1;
        transport.playerPayloads.clear();

        nowMillis.addAndGet(RemoteHtmlSessionGateway.DEFAULT_SESSION_TTL_MILLIS + 1L);
        transport.deliverToServer(transport.clientToServerPayloads.get(requestIndex), player);

        Assert.assertTrue("HUD stream 过期应给客户端发送 dismiss，避免 sticky dialog 停留",
                containsEnvelopeKey(MyMod.MODID + ":remote_hud_dismiss"));
        RemoteHudOverlays.DismissPayload dismissPayload = findDismissPayload();
        Assert.assertEquals(sessionId, dismissPayload.sessionId);
        Assert.assertEquals("hud-overlay", dismissPayload.overlayId);
        Assert.assertEquals("server-session-expired", dismissPayload.reason);
    }

    private static void resetNetService() {
        try {
            Method method = NetService.class.getDeclaredMethod("resetForTests");
            method.setAccessible(true);
            method.invoke(NetService.getInstance());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法重置 NetService", exception);
        }
    }

    private static void invokeHandleSubmit(String json, Object senderPlayer) {
        try {
            Method method = RemoteHudOverlays.class.getDeclaredMethod("handleSubmit", String.class, Object.class);
            method.setAccessible(true);
            method.invoke(null, json, senderPlayer);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法触发远程 HUD 提交处理", exception);
        }
    }

    private static void invokeHandleClientDismiss(String json, Object senderPlayer) {
        try {
            Method method = RemoteHudOverlays.class.getDeclaredMethod("handleClientDismiss", String.class, Object.class);
            method.setAccessible(true);
            method.invoke(null, json, senderPlayer);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法触发远程 HUD 客户端关闭处理", exception);
        }
    }

    private boolean containsEnvelopeKey(String key) {
        for (PlayerPayload payload : transport.playerPayloads) {
            if (key.equals(NetEnvelope.decode(payload.payload).getKey())) {
                return true;
            }
        }
        return false;
    }

    private RemoteHudOverlays.DismissPayload findDismissPayload() {
        for (PlayerPayload payload : transport.playerPayloads) {
            NetEnvelope envelope = NetEnvelope.decode(payload.payload);
            if ((MyMod.MODID + ":remote_hud_dismiss").equals(envelope.getKey())) {
                return RemoteJson.fromJson(envelope.toBody().asUtf8String(),
                        RemoteHudOverlays.DismissPayload.class);
            }
        }
        throw new AssertionError("未找到 HUD dismiss payload");
    }

    private static final class RecordingTransport implements ITransport {

        final List<byte[]> clientToServerPayloads = new ArrayList<byte[]>();
        final List<PlayerPayload> playerPayloads = new ArrayList<PlayerPayload>();
        FrameHandler frameHandler;

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public void bootstrap(FrameHandler frameHandler) {
            this.frameHandler = frameHandler;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void sendToServer(String channelName, byte[] payload) {
            clientToServerPayloads.add(payload);
        }

        @Override
        public void sendToPlayer(Object player, String channelName, byte[] payload) {
            playerPayloads.add(new PlayerPayload(player, payload));
        }

        @Override
        public void sendToAll(String channelName, byte[] payload) {
        }

        @Override
        public void sendToDimension(int dimensionId, String channelName, byte[] payload) {
        }

        @Override
        public Iterable<?> getConnectedPlayers() {
            return java.util.Collections.emptyList();
        }

        @Override
        public Integer getPlayerDimensionId(Object player) {
            return Integer.valueOf(player instanceof FakePlayer ? ((FakePlayer) player).dimensionId : 0);
        }

        @Override
        public int getPhysicalFrameLimit(NetSide targetSide) {
            return NetPayloadLimits.GTNH_DEFAULT_PHYSICAL_LIMIT;
        }

        void deliverToServer(byte[] payload, FakePlayer sender) {
            frameHandler.handleFrame(NetService.PHYSICAL_CHANNEL, payload, NetReceiveOrigin.server(sender));
        }
    }

    private static final class PlayerPayload {

        final Object player;
        final byte[] payload;

        private PlayerPayload(Object player, byte[] payload) {
            this.player = player;
            this.payload = payload;
        }
    }

    private static final class FakePlayer {

        final String name;
        final int dimensionId;

        private FakePlayer(String name, int dimensionId) {
            this.name = name;
            this.dimensionId = dimensionId;
        }
    }
}
