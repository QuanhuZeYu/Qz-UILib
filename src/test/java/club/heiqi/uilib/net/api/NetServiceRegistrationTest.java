package club.heiqi.uilib.net.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.core.NetRealtimeFrame;
import club.heiqi.uilib.net.transport.FrameHandler;
import club.heiqi.uilib.net.transport.ITransport;
import club.heiqi.uilib.net.transport.NetReceiveOrigin;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * `NetService` 注册与出站策略测试。
 */
public class NetServiceRegistrationTest {

    private NetService service;
    private RecordingTransport transport;

    @Before
    public void setUp() {
        service = NetService.getInstance();
        service.resetForTests();
        transport = new RecordingTransport();
        service.bootstrap(transport);
    }

    @After
    public void tearDown() {
        service.resetForTests();
    }

    @Test
    public void shouldFreezeRegistrationsAfterPostInitBoundary() {
        service.channel(NetChannelId.of("test", "ping")).register();
        service.freeze();

        try {
            service.channel(NetChannelId.of("test", "late")).register();
            Assert.fail("冻结后不应允许注册 Channel");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("冻结"));
        }
    }

    @Test
    public void shouldSendChannelMessageAsContentEnvelope() {
        NetChannel channel = service.channel(NetChannelId.of("test", "chat"))
                .register();
        service.freeze();

        channel.toServer().send(NetMessage.json("{\"text\":\"hello\"}").withHeader("x-message-kind", "chat"));

        Assert.assertEquals(1, transport.clientToServerPayloads.size());
        NetEnvelope envelope = NetEnvelope.decode(transport.clientToServerPayloads.get(0));
        Assert.assertEquals(NetEnvelope.Kind.CHANNEL, envelope.getKind());
        Assert.assertEquals("test:chat", envelope.getKey());
        Assert.assertEquals(NetContentType.JSON, envelope.getContentType());
        Assert.assertEquals("chat", envelope.getHeaders().get("x-message-kind"));
        Assert.assertEquals("{\"text\":\"hello\"}", new String(envelope.getPayload(), StandardCharsets.UTF_8));
    }

    @Test
    public void shouldChunkMessagesAbovePhysicalLimit() {
        NetChannel channel = service.channel(NetChannelId.of("test", "big"))
                .register();
        service.freeze();

        channel.toServer().send(NetMessage.text(repeat('a', 2_000)));

        Assert.assertTrue(transport.clientToServerPayloads.size() > 1);
        for (byte[] payload : transport.clientToServerPayloads) {
            Assert.assertTrue(payload.length <= transport.getPhysicalFrameLimit(NetSide.SERVER));
        }
    }

    @Test
    public void shouldSendRealtimeFrameThroughDedicatedFastPath() {
        final List<NetRealtimeMessage> received = new ArrayList<NetRealtimeMessage>();
        NetRealtimeChannel channel = service.realtime(NetRealtimeChannelId.of("test", "voice"))
                .maxFrameBytes(64)
                .onReceive(new NetRealtimeChannel.NetRealtimeHandler() {
                    @Override
                    public void onReceive(NetRealtimeMessage message, NetReceiveContext context) {
                        received.add(message);
                    }
                })
                .register();
        service.freeze();

        byte[] payload = new byte[] { 9, 8, 7, 6 };
        channel.toServer().sendFrame(12L, 4, 999L, payload);

        Assert.assertEquals(1, transport.clientToServerPayloads.size());
        Assert.assertTrue(NetRealtimeFrame.hasMagic(transport.clientToServerPayloads.get(0)));
        NetRealtimeFrame frame = NetRealtimeFrame.decode(transport.clientToServerPayloads.get(0));
        Assert.assertEquals("test:voice", frame.getKey());
        Assert.assertEquals(12L, frame.getStreamId());
        Assert.assertEquals(4, frame.getSequence());
        Assert.assertArrayEquals(payload, frame.getPayload());

        transport.deliverToServer(transport.clientToServerPayloads.get(0), new FakePlayer("voice", 0));
        Assert.assertEquals(1, received.size());
        Assert.assertEquals(12L, received.get(0).getStreamId());
        Assert.assertEquals(4, received.get(0).getSequence());
        Assert.assertArrayEquals(payload, received.get(0).getPayload());
    }

    @Test
    public void shouldPrioritizeRealtimeFramesAheadOfRemainingBulkQueue() {
        NetChannel bulkChannel = service.channel(NetChannelId.of("test", "bulkPriority"))
                .register();
        NetRealtimeChannel realtime = service.realtime(NetRealtimeChannelId.of("test", "priorityVoice"))
                .maxFrameBytes(64)
                .register();
        service.freeze();

        transport.setClientToServerHook(new Runnable() {
            @Override
            public void run() {
                realtime.toServer().sendFrame(1L, 1, 111L, new byte[] { 3, 4, 5 });
            }
        });
        bulkChannel.toServer().send(NetMessage.text(repeat('b', 2000)));

        Assert.assertTrue(transport.clientToServerPayloads.size() > 2);
        NetEnvelope first = NetEnvelope.decode(transport.clientToServerPayloads.get(0));
        Assert.assertEquals(NetEnvelope.Kind.CHUNK, first.getKind());
        Assert.assertTrue(NetRealtimeFrame.hasMagic(transport.clientToServerPayloads.get(1)));
        NetEnvelope third = NetEnvelope.decode(transport.clientToServerPayloads.get(2));
        Assert.assertEquals(NetEnvelope.Kind.CHUNK, third.getKind());
    }

    @Test
    public void shouldStreamLargeResponsesThroughIndependentLifecycle() throws Exception {
        FakePlayer player = new FakePlayer("streamPlayer", 0);
        final byte[] largeBody = new byte[NetPayloadLimits.DEFAULT_LOGICAL_MESSAGE_LIMIT + 1024];
        for (int index = 0; index < largeBody.length; index++) {
            largeBody[index] = (byte) (index & 0xFF);
        }
        NetStreamEndpoint endpoint = service.stream(NetEndpointId.of("test", "download"))
                .onRequest(new NetStreamEndpoint.NetStreamHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetStreamEndpoint.NetStreamRequestContext context) {
                        context.reply(NetResponse.ok(NetBody.binary(largeBody))
                                .withHeader("x-stream-kind", "large"));
                    }
                })
                .register();
        service.freeze();

        final List<NetStreamProgress> progress = new ArrayList<NetStreamProgress>();
        NetStreamCall call = endpoint.call(NetRequest.json("{\"download\":true}"))
                .onProgress(new NetStreamProgressListener() {
                    @Override
                    public void onProgress(NetStreamProgress progressSnapshot) {
                        progress.add(progressSnapshot);
                    }
                });

        Assert.assertEquals(1, transport.clientToServerPayloads.size());
        NetEnvelope requestEnvelope = NetEnvelope.decode(transport.clientToServerPayloads.get(0));
        Assert.assertEquals(NetEnvelope.Kind.STREAM_REQUEST, requestEnvelope.getKind());

        transport.deliverToServer(transport.clientToServerPayloads.get(0), player);

        Assert.assertTrue(transport.playerPayloads.size() > 1);
        List<PlayerPayload> frames = new ArrayList<PlayerPayload>(transport.playerPayloads);
        for (PlayerPayload frame : frames) {
            transport.deliverToClient(frame.payload);
        }

        NetResponse response = call.getFuture().get(2, TimeUnit.SECONDS);
        Assert.assertTrue(response.isOk());
        Assert.assertEquals("large", response.getHeader("x-stream-kind"));
        Assert.assertArrayEquals(largeBody, response.getBody().getBytes());
        Assert.assertFalse(progress.isEmpty());
        Assert.assertTrue(progress.get(progress.size() - 1).isComplete());
    }

    @Test
    public void shouldCancelStreamCallAndEmitCancelFrame() {
        NetStreamEndpoint endpoint = service.stream(NetEndpointId.of("test", "cancelStream"))
                .register();
        service.freeze();

        NetStreamCall call = endpoint.call(NetRequest.json("{\"download\":true}"));
        Assert.assertTrue(call.cancel());
        Assert.assertTrue(call.getFuture().isCancelled());

        Assert.assertEquals(2, transport.clientToServerPayloads.size());
        NetEnvelope cancelEnvelope = NetEnvelope.decode(transport.clientToServerPayloads.get(1));
        Assert.assertEquals(NetEnvelope.Kind.STREAM_CANCEL, cancelEnvelope.getKind());

        transport.deliverToServer(transport.clientToServerPayloads.get(1), new FakePlayer("streamPlayer", 0));
        Assert.assertTrue(service.isStreamCancelled(cancelEnvelope.getRequestId()));
    }

    @Test
    public void shouldRateLimitFetchRequestsPerSender() {
        final int[] handled = new int[1];
        NetFetchEndpoint endpoint = service.fetch(NetEndpointId.of("test", "limitedFetch"))
                .rateLimit(1, java.time.Duration.ofSeconds(30))
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        handled[0]++;
                        context.reply(NetResponse.ok(NetBody.text("ok")).withHeader("x-fetch-kind", "limited"));
                    }
                })
                .register();
        service.freeze();

        FakePlayer player = new FakePlayer("fetchPlayer", 1);
        transport.deliverToServer(NetEnvelope.of(NetEnvelope.Kind.FETCH_REQUEST, NetSide.SERVER,
                endpoint.getId().asKey(), 101L, 0, java.util.Collections.<String, String>emptyMap(),
                NetRequest.json("{\"n\":1}").getBody()).encode(), player);
        transport.deliverToServer(NetEnvelope.of(NetEnvelope.Kind.FETCH_REQUEST, NetSide.SERVER,
                endpoint.getId().asKey(), 102L, 0, java.util.Collections.<String, String>emptyMap(),
                NetRequest.json("{\"n\":2}").getBody()).encode(), player);

        Assert.assertEquals(1, handled[0]);
        Assert.assertEquals(2, transport.playerPayloads.size());
        NetEnvelope firstResponse = NetEnvelope.decode(transport.playerPayloads.get(0).payload);
        NetEnvelope secondResponse = NetEnvelope.decode(transport.playerPayloads.get(1).payload);
        Assert.assertEquals(200, firstResponse.getStatusCode());
        Assert.assertEquals("limited", firstResponse.getHeaders().get("x-fetch-kind"));
        Assert.assertEquals(429, secondResponse.getStatusCode());
        Assert.assertNotNull(secondResponse.getHeaders().get("retry-after-ms"));
    }

    @Test
    public void shouldApplyStoreDeltaThroughCustomApplier() {
        NetStore store = service.store(NetStoreId.of("test", "delta"))
                .initial(NetBody.text("hello"))
                .deltaApplier(new NetStore.StoreDeltaApplier() {
                    @Override
                    public NetBody apply(NetBody current, NetBody delta) {
                        return NetBody.text(current.asUtf8String() + delta.asUtf8String());
                    }
                })
                .register();
        service.freeze();

        store.applyDelta(NetBody.text(" world"));

        Assert.assertEquals("hello world", store.get().asUtf8String());
        Assert.assertEquals(1, transport.allPayloads.size());
        NetEnvelope envelope = NetEnvelope.decode(transport.allPayloads.get(0));
        Assert.assertEquals(NetEnvelope.Kind.STORE_DELTA, envelope.getKind());
        Assert.assertEquals(" world", envelope.toBody().asUtf8String());

        store.receiveDelta(envelope.toBody());
        Assert.assertEquals("hello world", store.get().asUtf8String());
        Assert.assertEquals("hello world", store.view().getSnapshot().asUtf8String());

        store.receiveSnapshot(NetBody.text("start"));
        store.receiveDelta(NetBody.text("!"));
        Assert.assertEquals("start!", store.get().asUtf8String());
        Assert.assertEquals("start!", store.view().getSnapshot().asUtf8String());
    }

    @Test
    public void shouldFilterGlobalStoreSnapshotsWithAccessControl() {
        final FakePlayer allowed = new FakePlayer("allowed", 0);
        final FakePlayer denied = new FakePlayer("denied", 0);
        transport.connectedPlayers.add(allowed);
        transport.connectedPlayers.add(denied);
        NetStore store = service.store(NetStoreId.of("test", "secure"))
                .accessControl(new NetStore.AccessControl() {
                    @Override
                    public boolean canAccess(Object player, NetStore store) {
                        return player == allowed;
                    }
                })
                .register();
        service.freeze();

        store.set(NetBody.json("{\"secure\":true}"));

        Assert.assertEquals(0, transport.allPayloads.size());
        Assert.assertEquals(1, transport.playerPayloads.size());
        Assert.assertSame(allowed, transport.playerPayloads.get(0).player);
        NetEnvelope envelope = NetEnvelope.decode(transport.playerPayloads.get(0).payload);
        Assert.assertEquals(NetEnvelope.Kind.STORE_SNAPSHOT, envelope.getKind());
        Assert.assertEquals("test:secure", envelope.getKey());
    }

    @Test
    public void shouldSendPerPlayerStoreSnapshotsToSinglePlayer() {
        FakePlayer player = new FakePlayer("alex", 3);
        NetStore store = service.store(NetStoreId.of("test", "playerState"))
                .scope(NetStoreScope.PER_PLAYER)
                .initialJson("{\"value\":0}")
                .register();
        service.freeze();

        store.setForPlayer(player, NetBody.json("{\"value\":7}"));

        Assert.assertEquals("{\"value\":7}", store.getForPlayer(player).asUtf8String());
        Assert.assertEquals(1, transport.playerPayloads.size());
        Assert.assertSame(player, transport.playerPayloads.get(0).player);
        Assert.assertEquals(0, transport.allPayloads.size());
    }

    @Test
    public void shouldFilterDimensionStoreSnapshotsWithAccessControl() {
        final FakePlayer allowed = new FakePlayer("allowed", 7);
        final FakePlayer denied = new FakePlayer("denied", 7);
        final FakePlayer otherDimension = new FakePlayer("other", 8);
        transport.connectedPlayers.add(allowed);
        transport.connectedPlayers.add(denied);
        transport.connectedPlayers.add(otherDimension);
        NetStore store = service.store(NetStoreId.of("test", "dimensionState"))
                .scope(NetStoreScope.DIMENSION)
                .accessControl(new NetStore.AccessControl() {
                    @Override
                    public boolean canAccess(Object player, NetStore store) {
                        return player == allowed || player == otherDimension;
                    }
                })
                .register();
        service.freeze();

        store.setForDimension(7, NetBody.json("{\"dimension\":7}"));

        Assert.assertEquals("{\"dimension\":7}", store.getForDimension(7).asUtf8String());
        Assert.assertEquals(1, transport.playerPayloads.size());
        Assert.assertSame(allowed, transport.playerPayloads.get(0).player);
        Assert.assertEquals(0, transport.dimensionPayloads.size());
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static final class RecordingTransport implements ITransport {

        final List<byte[]> clientToServerPayloads = new ArrayList<byte[]>();
        final List<byte[]> allPayloads = new ArrayList<byte[]>();
        final List<DimensionPayload> dimensionPayloads = new ArrayList<DimensionPayload>();
        final List<PlayerPayload> playerPayloads = new ArrayList<PlayerPayload>();
        final List<FakePlayer> connectedPlayers = new ArrayList<FakePlayer>();
        FrameHandler frameHandler;
        Runnable clientToServerHook;

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
            Runnable hook = clientToServerHook;
            if (hook != null) {
                clientToServerHook = null;
                hook.run();
            }
        }

        @Override
        public void sendToPlayer(Object player, String channelName, byte[] payload) {
            playerPayloads.add(new PlayerPayload(player, payload));
        }

        @Override
        public void sendToAll(String channelName, byte[] payload) {
            allPayloads.add(payload);
        }

        @Override
        public void sendToDimension(int dimensionId, String channelName, byte[] payload) {
            dimensionPayloads.add(new DimensionPayload(dimensionId, payload));
        }

        @Override
        public Iterable<?> getConnectedPlayers() {
            return connectedPlayers;
        }

        @Override
        public Integer getPlayerDimensionId(Object player) {
            if (!(player instanceof FakePlayer)) {
                return null;
            }
            return Integer.valueOf(((FakePlayer) player).dimensionId);
        }

        @Override
        public int getPhysicalFrameLimit(NetSide targetSide) {
            return targetSide == NetSide.SERVER ? 256 : NetPayloadLimits.GTNH_DEFAULT_PHYSICAL_LIMIT;
        }

        void deliverToServer(byte[] payload, FakePlayer sender) {
            frameHandler.handleFrame(NetService.PHYSICAL_CHANNEL, payload, NetReceiveOrigin.server(sender));
        }

        void deliverToClient(byte[] payload) {
            frameHandler.handleFrame(NetService.PHYSICAL_CHANNEL, payload, NetReceiveOrigin.client());
        }

        void setClientToServerHook(Runnable clientToServerHook) {
            this.clientToServerHook = clientToServerHook;
        }
    }

    private static final class FakePlayer {

        final String name;
        final int dimensionId;

        FakePlayer(String name, int dimensionId) {
            this.name = name;
            this.dimensionId = dimensionId;
        }
    }

    private static final class PlayerPayload {

        final Object player;
        final byte[] payload;

        PlayerPayload(Object player, byte[] payload) {
            this.player = player;
            this.payload = payload;
        }
    }

    private static final class DimensionPayload {

        final int dimensionId;
        final byte[] payload;

        DimensionPayload(int dimensionId, byte[] payload) {
            this.dimensionId = dimensionId;
            this.payload = payload;
        }
    }
}
