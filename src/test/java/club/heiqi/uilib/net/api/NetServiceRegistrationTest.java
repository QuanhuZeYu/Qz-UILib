package club.heiqi.uilib.net.api;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
        service.channel(NetChannelId.of("test", "ping"), ProbeMessage.class).register();
        service.freeze();

        try {
            service.channel(NetChannelId.of("test", "late"), ProbeMessage.class).register();
            Assert.fail("冻结后不应允许注册 Channel");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("冻结"));
        }
    }

    @Test
    public void shouldChunkMessagesAbovePhysicalLimit() {
        NetChannel<ProbeMessage> channel = service.channel(NetChannelId.of("test", "big"), ProbeMessage.class)
                .register();
        service.freeze();

        ProbeMessage message = new ProbeMessage();
        message.text = repeat('a', 2_000);
        channel.toServer().send(message);

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

    public static final class ProbeMessage {

        public String text;
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
