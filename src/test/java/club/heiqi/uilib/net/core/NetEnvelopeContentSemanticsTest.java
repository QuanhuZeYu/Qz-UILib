package club.heiqi.uilib.net.core;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetContentType;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * `NetEnvelope` 内容语义格式测试。
 */
public class NetEnvelopeContentSemanticsTest {

    @Test
    public void shouldRoundTripContentTypeHeadersStatusAndBody() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("x-qz-event", "sync");
        NetBody body = NetBody.of(NetContentType.of("application/vnd.mymod.state+json; charset=utf-8"),
                "{\"value\":42}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        NetEnvelope decoded = NetEnvelope.decode(NetEnvelope.of(NetEnvelope.Kind.FETCH_RESPONSE, NetSide.CLIENT,
                "mymod:getState", 77L, 202, headers, body).encode());

        Assert.assertEquals(NetEnvelope.Kind.FETCH_RESPONSE, decoded.getKind());
        Assert.assertEquals(NetSide.CLIENT, decoded.getTargetSide());
        Assert.assertEquals("mymod:getState", decoded.getKey());
        Assert.assertEquals(77L, decoded.getRequestId());
        Assert.assertEquals(202, decoded.getStatusCode());
        Assert.assertEquals("sync", decoded.getHeaders().get("x-qz-event"));
        Assert.assertEquals("application/vnd.mymod.state+json; charset=utf-8", decoded.getContentType().value());
        Assert.assertTrue(decoded.getContentType().isJson());
        Assert.assertEquals("{\"value\":42}", decoded.toBody().asUtf8String());
    }

    @Test
    public void shouldProtectPayloadFromExternalMutation() {
        byte[] payload = new byte[] { 4, 5, 6 };
        NetEnvelope envelope = NetEnvelope.binary(NetEnvelope.Kind.CHANNEL, NetSide.SERVER, "mymod:bin", 0L,
                payload);

        payload[0] = 9;
        byte[] exposed = envelope.getPayload();
        exposed[1] = 8;

        Assert.assertArrayEquals(new byte[] { 4, 5, 6 }, envelope.getPayload());
    }
}
