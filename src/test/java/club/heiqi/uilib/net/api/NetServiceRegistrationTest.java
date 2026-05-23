package club.heiqi.uilib.net.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.transport.FrameHandler;
import club.heiqi.uilib.net.transport.ITransport;
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

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public void bootstrap(FrameHandler frameHandler) {
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
