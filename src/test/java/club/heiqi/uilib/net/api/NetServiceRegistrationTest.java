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

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static final class RecordingTransport implements ITransport {

        final List<byte[]> clientToServerPayloads = new ArrayList<byte[]>();

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
        }

        @Override
        public void sendToAll(String channelName, byte[] payload) {
        }

        @Override
        public void sendToDimension(int dimensionId, String channelName, byte[] payload) {
        }

        @Override
        public int getPhysicalFrameLimit(NetSide targetSide) {
            return targetSide == NetSide.SERVER ? 256 : NetPayloadLimits.GTNH_DEFAULT_PHYSICAL_LIMIT;
        }
    }
}
