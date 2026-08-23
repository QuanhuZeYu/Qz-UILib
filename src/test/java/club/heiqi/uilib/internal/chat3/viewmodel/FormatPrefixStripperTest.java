package club.heiqi.uilib.internal.chat3.viewmodel;

import org.junit.Assert;
import org.junit.Test;

/**
 * FormatPrefixStripper 契约测试:按有效字符剥离前缀、格式码对零宽跳过、防御边界。
 */
public class FormatPrefixStripperTest {

    @Test
    public void shouldStripPlainPrefix() {
        Assert.assertEquals("llo", FormatPrefixStripper.strip("hello", 2));
    }

    @Test
    public void shouldSkipFormatCodePairs() {
        // "\u00a7a<Steve> hi":有效字符 10 个,前缀 8 个(<Steve> )→ 剩 "hi",格式码已在前缀中
        Assert.assertEquals("hi", FormatPrefixStripper.strip("\u00a7a<Steve> hi", 8));
        // 格式码对在剥离点之后:保留
        Assert.assertEquals("\u00a7chello", FormatPrefixStripper.strip("xx\u00a7chello", 2));
    }

    @Test
    public void shouldHandleEdgeCases() {
        Assert.assertEquals("hello", FormatPrefixStripper.strip("hello", 0));
        Assert.assertEquals("hello", FormatPrefixStripper.strip("hello", -1));
        Assert.assertEquals("", FormatPrefixStripper.strip("hi", 10));
        Assert.assertEquals("", FormatPrefixStripper.strip(null, 1));
        Assert.assertEquals("", FormatPrefixStripper.strip("", 1));
    }
}
