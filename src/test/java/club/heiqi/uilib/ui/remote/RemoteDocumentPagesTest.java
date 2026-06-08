package club.heiqi.uilib.ui.remote;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.transport.FrameHandler;
import club.heiqi.uilib.net.transport.ITransport;
import club.heiqi.uilib.net.transport.NetReceiveOrigin;
import club.heiqi.uilib.net.transport.NetSide;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.screen.UiDocumentScreens;
import club.heiqi.uilib.ui.screen.UiScreenManager;

/**
 * 远程页面 session 与客户端桥回调顺序测试。
 */
public class RemoteDocumentPagesTest {

    private NetService service;
    private RecordingTransport transport;
    private RecordingDocumentScreenOpener screenOpener;

    @Before
    public void setUp() {
        resetNetService();
        RemoteDocumentPages.resetForTests();
        RemoteDocumentClientBridge.resetForTests();
        flushUiScreenTasks();
        service = NetService.getInstance();
        transport = new RecordingTransport();
        service.bootstrap(transport);
        RemoteDocumentPages.register();
        screenOpener = new RecordingDocumentScreenOpener();
        RemoteDocumentClientBridge.setDocumentScreenOpenerForTests(screenOpener);
    }

    @After
    public void tearDown() {
        RemoteDocumentClientBridge.resetForTests();
        RemoteDocumentPages.resetForTests();
        resetNetService();
        flushUiScreenTasks();
    }

    @Test
    public void shouldIgnoreOlderSuccessfulStreamAfterNewPageIsCurrent() {
        FakePlayer player = new FakePlayer("pagePlayer", 1);
        int firstOpenIndex = openPage(player, RemoteDocumentPage.of("page-s1", "旧页面", "<p>old-success</p>"));
        RemoteDocumentClientBridge.receiveOpenOffer(openJsonAt(firstOpenIndex));
        int firstRequestIndex = transport.clientToServerPayloads.size() - 1;

        int secondOpenIndex = openPage(player, RemoteDocumentPage.of("page-s2", "新页面", "<p>new-success</p>"));
        RemoteDocumentClientBridge.receiveOpenOffer(openJsonAt(secondOpenIndex));
        int secondRequestIndex = transport.clientToServerPayloads.size() - 1;

        deliverStreamRequestAndFlush(secondRequestIndex, player);
        Assert.assertTrue(screenOpener.lastText().contains("new-success"));

        deliverStreamRequestAndFlush(firstRequestIndex, player);

        Assert.assertTrue("旧 stream 成功回调不应覆盖当前新页面",
                screenOpener.lastText().contains("new-success"));
        Assert.assertFalse(screenOpener.lastText().contains("old-success"));
    }

    @Test
    public void shouldIgnoreOlderFailedStreamAfterNewPageIsCurrent() {
        FakePlayer player = new FakePlayer("pagePlayer", 2);
        RemoteDocumentPages.OpenOffer staleOffer = new RemoteDocumentPages.OpenOffer();
        staleOffer.sessionId = "missing-session";
        staleOffer.pageId = "missing-page";
        staleOffer.title = "旧错误页";
        staleOffer.resourcePolicy = RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY.name();
        RemoteDocumentClientBridge.receiveOpenOffer(RemoteJson.toJson(staleOffer));
        int staleRequestIndex = transport.clientToServerPayloads.size() - 1;

        int currentOpenIndex = openPage(player, RemoteDocumentPage.of("page-current", "当前页面", "<p>current-ok</p>"));
        RemoteDocumentClientBridge.receiveOpenOffer(openJsonAt(currentOpenIndex));
        int currentRequestIndex = transport.clientToServerPayloads.size() - 1;

        deliverStreamRequestAndFlush(currentRequestIndex, player);
        Assert.assertTrue(screenOpener.lastText().contains("current-ok"));

        deliverStreamRequestAndFlush(staleRequestIndex, player);

        Assert.assertTrue("旧 stream 失败回调不应打开错误页覆盖当前页面",
                screenOpener.lastText().contains("current-ok"));
        Assert.assertFalse(screenOpener.lastText().contains("远程页面校验失败"));
    }

    @Test
    public void shouldNotifyClientWhenPageSubmitFindsExpiredSession() {
        final AtomicLong nowMillis = new AtomicLong(2_000L);
        RemoteDocumentPages.setSessionClockForTests(new LongSupplier() {
            @Override
            public long getAsLong() {
                return nowMillis.get();
            }
        });
        FakePlayer player = new FakePlayer("pagePlayer", 3);
        final AtomicReference<RemoteDocumentSubmitEvent> captured =
                new AtomicReference<RemoteDocumentSubmitEvent>();
        String sessionId = RemoteDocumentPages.open(player,
                RemoteDocumentPage.of("page-expired", "过期页", "<form id=\"f\"></form>"),
                new RemoteDocumentSubmitHandler() {
                    @Override
                    public void onSubmit(RemoteDocumentSubmitEvent event) {
                        captured.set(event);
                    }
                });
        transport.playerPayloads.clear();

        nowMillis.addAndGet(RemoteHtmlSessionGateway.DEFAULT_SESSION_TTL_MILLIS + 1L);
        RemoteDocumentPages.SubmitPayload submitPayload = new RemoteDocumentPages.SubmitPayload();
        submitPayload.sessionId = sessionId;
        submitPayload.pageId = "page-expired";
        submitPayload.values = Collections.emptyMap();
        invokeHandleSubmit(RemoteJson.toJson(submitPayload), player);

        Assert.assertNull("过期提交不应再进入业务 handler", captured.get());
        Assert.assertEquals(1, transport.playerPayloads.size());
        NetEnvelope expiredEnvelope = NetEnvelope.decode(transport.playerPayloads.get(0).payload);
        Assert.assertEquals(MyMod.MODID + ":remote_page_expired", expiredEnvelope.getKey());
        RemoteDocumentPages.ExpiredPayload expiredPayload =
                RemoteDocumentPages.decodeExpiredPayload(expiredEnvelope.toBody().asUtf8String());
        Assert.assertEquals(sessionId, expiredPayload.sessionId);
        Assert.assertEquals("page-expired", expiredPayload.pageId);
        Assert.assertEquals("server-session-expired", expiredPayload.reason);
    }

    @Test
    public void shouldNotifyClientWhenPageStreamFindsExpiredSession() {
        final AtomicLong nowMillis = new AtomicLong(3_000L);
        RemoteDocumentPages.setSessionClockForTests(new LongSupplier() {
            @Override
            public long getAsLong() {
                return nowMillis.get();
            }
        });
        FakePlayer player = new FakePlayer("pagePlayer", 4);
        int openIndex = openPage(player, RemoteDocumentPage.of("page-stream-expired", "过期页", "<p>late</p>"));
        RemoteDocumentClientBridge.receiveOpenOffer(openJsonAt(openIndex));
        int requestIndex = transport.clientToServerPayloads.size() - 1;
        transport.playerPayloads.clear();

        nowMillis.addAndGet(RemoteHtmlSessionGateway.DEFAULT_SESSION_TTL_MILLIS + 1L);
        deliverStreamRequestAndFlush(requestIndex, player);

        Assert.assertTrue("客户端应打开可见错误页，而不是停留在 loading",
                screenOpener.lastText().contains("远程页面校验失败"));
        Assert.assertTrue(screenOpener.lastText().contains("404"));
        Assert.assertTrue("stream 过期移除也应给客户端发送失效通知",
                containsEnvelopeKey(MyMod.MODID + ":remote_page_expired"));
    }

    private int openPage(FakePlayer player, RemoteDocumentPage page) {
        int index = transport.playerPayloads.size();
        RemoteDocumentPages.open(player, page, null);
        Assert.assertEquals(index + 1, transport.playerPayloads.size());
        return index;
    }

    private String openJsonAt(int index) {
        NetEnvelope envelope = NetEnvelope.decode(transport.playerPayloads.get(index).payload);
        Assert.assertEquals(MyMod.MODID + ":remote_page_open", envelope.getKey());
        return envelope.toBody().asUtf8String();
    }

    private void deliverStreamRequestAndFlush(int requestIndex, FakePlayer player) {
        int responseStart = transport.playerPayloads.size();
        transport.deliverToServer(transport.clientToServerPayloads.get(requestIndex), player);
        int responseEnd = transport.playerPayloads.size();
        for (int index = responseStart; index < responseEnd; index++) {
            transport.deliverToClient(transport.playerPayloads.get(index).payload);
        }
        flushUiScreenTasks();
    }

    private boolean containsEnvelopeKey(String key) {
        for (PlayerPayload payload : transport.playerPayloads) {
            if (key.equals(NetEnvelope.decode(payload.payload).getKey())) {
                return true;
            }
        }
        return false;
    }

    private static void invokeHandleSubmit(String json, Object senderPlayer) {
        try {
            Method method = RemoteDocumentPages.class.getDeclaredMethod("handleSubmit", String.class, Object.class);
            method.setAccessible(true);
            method.invoke(null, json, senderPlayer);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法触发远程页面提交处理", exception);
        }
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

    private static void flushUiScreenTasks() {
        try {
            Method method = UiScreenManager.class.getDeclaredMethod("flushPendingTasks");
            method.setAccessible(true);
            method.invoke(UiScreenManager.getInstance());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法刷新 UI Screen 延后任务", exception);
        }
    }

    private static String collectText(DocumentNode node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).getText();
        }
        StringBuilder builder = new StringBuilder();
        for (DocumentNode child : node.getChildren()) {
            builder.append(collectText(child));
        }
        return builder.toString();
    }

    private static final class RecordingDocumentScreenOpener implements RemoteDocumentClientBridge.DocumentScreenOpener {

        private final List<String> openedTexts = new ArrayList<String>();

        @Override
        public void open(UiDocumentScreens.DocumentScreenContentBuilder builder) {
            UiDocument document = UiDocument.create();
            builder.build(document);
            openedTexts.add(collectText(document.getRootElement()));
        }

        private String lastText() {
            return openedTexts.isEmpty() ? "" : openedTexts.get(openedTexts.size() - 1);
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
            return Collections.emptyList();
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

        void deliverToClient(byte[] payload) {
            frameHandler.handleFrame(NetService.PHYSICAL_CHANNEL, payload, NetReceiveOrigin.client());
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
