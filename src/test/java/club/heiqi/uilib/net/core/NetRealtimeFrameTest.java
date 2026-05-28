package club.heiqi.uilib.net.core;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.net.api.NetRealtimeMessage;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * `NetRealtimeFrame` 编解码测试。
 */
public class NetRealtimeFrameTest {

    @Test
    public void shouldRoundTripRealtimeFrame() {
        byte[] payload = new byte[] { 1, 3, 5, 7 };
        NetRealtimeFrame encoded = NetRealtimeFrame.of(NetSide.CLIENT, "test:voice",
                NetRealtimeMessage.of(55L, 7, 123456789L, 3, payload));

        byte[] bytes = encoded.encode();
        Assert.assertTrue(NetRealtimeFrame.hasMagic(bytes));

        NetRealtimeFrame decoded = NetRealtimeFrame.decode(bytes);
        Assert.assertEquals(NetSide.CLIENT, decoded.getTargetSide());
        Assert.assertEquals("test:voice", decoded.getKey());
        Assert.assertEquals(55L, decoded.getStreamId());
        Assert.assertEquals(7, decoded.getSequence());
        Assert.assertEquals(123456789L, decoded.getTimestampMillis());
        Assert.assertEquals(3, decoded.getFlags());
        Assert.assertArrayEquals(payload, decoded.getPayload());
    }
}
