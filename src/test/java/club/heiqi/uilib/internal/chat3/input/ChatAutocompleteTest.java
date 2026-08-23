package club.heiqi.uilib.internal.chat3.input;

import org.junit.Assert;
import org.junit.Test;

/**
 * 补全公共前缀纯函数测试(commonPrefix)。
 */
public class ChatAutocompleteTest {

    @Test
    public void shouldFindCommonPrefix() {
        Assert.assertEquals("com", ChatInputSurface.commonPrefix(
                new String[] { "command", "compare", "compact" }));
        Assert.assertEquals("hel", ChatInputSurface.commonPrefix(
                new String[] { "hello", "help" }));
        Assert.assertEquals("abc", ChatInputSurface.commonPrefix(
                new String[] { "abc" }));
    }

    @Test
    public void shouldHandleEdgeCases() {
        Assert.assertNull(ChatInputSurface.commonPrefix(null));
        Assert.assertNull(ChatInputSurface.commonPrefix(new String[0]));
        Assert.assertEquals("", ChatInputSurface.commonPrefix(
                new String[] { "abc", "xyz" }));
    }
}
