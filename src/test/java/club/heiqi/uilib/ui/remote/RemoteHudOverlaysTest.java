package club.heiqi.uilib.ui.remote;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
