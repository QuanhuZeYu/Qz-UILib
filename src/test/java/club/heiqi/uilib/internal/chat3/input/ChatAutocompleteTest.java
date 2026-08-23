package club.heiqi.uilib.internal.chat3.input;

import org.junit.Assert;
import org.junit.Test;

/**
 * 补全公共前缀纯函数测试(commonPrefix)。
 */
public class ChatAutocompleteTest {

    @Test
    public void shouldFindCommonPrefix() {
        Assert.assertEquals("com", ChatInputBar.commonPrefix(
                new String[] { "command", "compare", "compact" }));
        Assert.assertEquals("hel", ChatInputBar.commonPrefix(
                new String[] { "hello", "help" }));
        Assert.assertEquals("abc", ChatInputBar.commonPrefix(
                new String[] { "abc" }));
    }

    @Test
    public void shouldHandleEdgeCases() {
        Assert.assertNull(ChatInputBar.commonPrefix(null));
        Assert.assertNull(ChatInputBar.commonPrefix(new String[0]));
        Assert.assertEquals("", ChatInputBar.commonPrefix(
                new String[] { "abc", "xyz" }));
    }
}