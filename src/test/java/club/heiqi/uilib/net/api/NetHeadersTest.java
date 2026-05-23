package club.heiqi.uilib.net.api;

import org.junit.Assert;
import org.junit.Test;

/**
 * `NetHeaders` 规范化测试。
 */
public class NetHeadersTest {

    @Test
    public void shouldNormalizeHeaderNamesAsCaseInsensitiveTokens() {
        NetMessage message = NetMessage.text("hello").withHeader("X-Qz-Trace", "abc");

        Assert.assertEquals("abc", message.getHeader("x-qz-trace"));
        Assert.assertEquals("abc", message.getHeader("X-QZ-TRACE"));
        Assert.assertTrue(message.getHeaders().containsKey("x-qz-trace"));
    }

    @Test
    public void shouldRejectInvalidHeaderName() {
        try {
            NetRequest.text("hello").withHeader("bad header", "value");
            Assert.fail("header 名不应允许空格");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("invalid char"));
        }
    }

    @Test
    public void shouldRejectHeaderValueWithNewLine() {
        try {
            NetResponse.text("hello").withHeader("x-qz", "line1\nline2");
            Assert.fail("header 值不应允许换行");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("CR/LF"));
        }
    }

    @Test
    public void shouldRejectTooManyHeaders() {
        NetMessage message = NetMessage.text("hello");
        try {
            for (int index = 0; index <= NetHeaders.MAX_HEADER_COUNT; index++) {
                message = message.withHeader("x-qz-" + index, "v");
            }
            Assert.fail("超过 header 数量上限时应拒绝");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("too many headers"));
        }
    }
}
