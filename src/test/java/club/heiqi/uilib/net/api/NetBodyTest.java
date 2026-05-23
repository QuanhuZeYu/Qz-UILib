package club.heiqi.uilib.net.api;

import org.junit.Assert;
import org.junit.Test;

/**
 * `NetBody` 与 `NetContentType` 内容语义测试。
 */
public class NetBodyTest {

    @Test
    public void shouldKeepJsonContentAsUtf8Bytes() {
        NetBody body = NetBody.json("{\"value\":1}");

        Assert.assertEquals(NetContentType.JSON, body.getContentType());
        Assert.assertTrue(body.getContentType().isJson());
        Assert.assertEquals("{\"value\":1}", body.asUtf8String());
    }

    @Test
    public void shouldCopyBinaryBytes() {
        byte[] bytes = new byte[] { 1, 2, 3 };
        NetBody body = NetBody.binary(bytes);
        bytes[0] = 9;
        byte[] exposed = body.getBytes();
        exposed[1] = 8;

        Assert.assertArrayEquals(new byte[] { 1, 2, 3 }, body.getBytes());
    }

    @Test
    public void shouldRecognizeMimeLikeJsonAndBinaryTypes() {
        NetContentType json = NetContentType.of("Application/Vnd.MyMod.State+Json; Charset=UTF-8");
        NetContentType binary = NetContentType.of("Application/X-MyMod-State");

        Assert.assertEquals("application/vnd.mymod.state+json; charset=utf-8", json.value());
        Assert.assertTrue(json.isJson());
        Assert.assertTrue(binary.isBinary());
    }
}
