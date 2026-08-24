package club.heiqi.uilib.internal.chat3.input;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tab 补全纯函数核测试(T1):词边界、大小写折叠公共前缀、折叠匹配、循环正反、替换拼回。
 */
public class ChatAutocompleteTest {

    @Test
    public void shouldFindCommonPrefix() {
        Assert.assertEquals("com", ChatCompletionState.commonPrefix(
                new String[] { "command", "compare", "compact" }));
        Assert.assertEquals("hel", ChatCompletionState.commonPrefix(
                new String[] { "hello", "help" }));
        Assert.assertEquals("abc", ChatCompletionState.commonPrefix(
                new String[] { "abc" }));
    }

    @Test
    public void shouldHandleEdgeCases() {
        Assert.assertNull(ChatCompletionState.commonPrefix((String[]) null));
        Assert.assertNull(ChatCompletionState.commonPrefix(new String[0]));
        Assert.assertEquals("", ChatCompletionState.commonPrefix(
                new String[] { "abc", "xyz" }));
    }

    @Test
    public void shouldFoldCaseForCommonPrefix() {
        // 大小写不敏感比较,返回首候选原始 case 片段
        Assert.assertEquals("Hel", ChatCompletionState.commonPrefix(
                new String[] { "Hello", "help", "HELIUM" }));
        Assert.assertEquals("he", ChatCompletionState.commonPrefix(
                new String[] { "heLLo", "Heimdal" }));
    }

    @Test
    public void shouldLocateWordBoundary() {
        Assert.assertEquals("整词无空格", 0, ChatCompletionState.wordStart("/tp"));
        Assert.assertEquals("空格后玩家词 ste", 4, ChatCompletionState.wordStart("/tp ste"));
        Assert.assertEquals("空格后玩家词 steve", 4, ChatCompletionState.wordStart("/tp steve"));
        Assert.assertEquals("无前缀整词", 0, ChatCompletionState.wordStart("hello"));
        Assert.assertEquals("单字符词", 6, ChatCompletionState.wordStart("hello w"));
        Assert.assertEquals("尾随空格词为空", 8, ChatCompletionState.wordStart("hello w "));
        Assert.assertEquals("空文本", 0, ChatCompletionState.wordStart(""));
        Assert.assertEquals("null 防御", 0, ChatCompletionState.wordStart(null));
        // caret 参数版(二期光标处补全用):光标在词中
        Assert.assertEquals("caret 在词中仍取词首", 0, ChatCompletionState.wordStart("/tp steve", 3));
        Assert.assertEquals("caret 在玩家词中", 4, ChatCompletionState.wordStart("/tp steve", 5));
        Assert.assertEquals("caret 越界 clamp 到末尾", 4, ChatCompletionState.wordStart("/tp steve", 99));
    }

    @Test
    public void shouldCycleIndexForwardAndBackward() {
        Assert.assertEquals(1, ChatCompletionState.cycleIndex(0, 1, 3));
        Assert.assertEquals(2, ChatCompletionState.cycleIndex(1, 1, 3));
        Assert.assertEquals("正向到尾回卷 0", 0, ChatCompletionState.cycleIndex(2, 1, 3));
        Assert.assertEquals("反向到首回卷末尾", 2, ChatCompletionState.cycleIndex(0, -1, 3));
        Assert.assertEquals(0, ChatCompletionState.cycleIndex(2, -2, 3));
        Assert.assertEquals("空集防御", 0, ChatCompletionState.cycleIndex(5, 1, 0));
        Assert.assertEquals("单候选恒 0", 0, ChatCompletionState.cycleIndex(0, 1, 1));
    }

    @Test
    public void shouldMatchCaseInsensitive() {
        Assert.assertEquals(Arrays.asList("Steve", "steve2"),
                ChatCompletionState.matchCaseInsensitive(Arrays.asList("Steve", "steve2", "Alex"), "ste"));
        Assert.assertEquals(Arrays.asList("Alex"),
                ChatCompletionState.matchCaseInsensitive(Arrays.asList("Steve", "Alex"), "A"));
        Assert.assertTrue("无匹配空列表",
                ChatCompletionState.matchCaseInsensitive(Arrays.asList("Steve", "Alex"), "zz").isEmpty());
        Assert.assertTrue("null 候选集防御",
                ChatCompletionState.matchCaseInsensitive(null, "a").isEmpty());
    }

    @Test
    public void shouldReplaceWordKeepingPrefix() {
        // 补全丢 "/" bug 回归:替换当前词保留前缀
        Assert.assertEquals("/tp Steve", ChatCompletionState.replaceWord("/tp ste", "Steve"));
        Assert.assertEquals("/tp steve2", ChatCompletionState.replaceWord("/tp steve", "steve2"));
        Assert.assertEquals("Steve", ChatCompletionState.replaceWord("ste", "Steve"));
        Assert.assertEquals("hello Steve", ChatCompletionState.replaceWord("hello s", "Steve"));
        Assert.assertEquals("替换为空候选", "/tp ", ChatCompletionState.replaceWord("/tp ste", ""));
        Assert.assertEquals("null 全文防御", "X", ChatCompletionState.replaceWord(null, "X"));
    }

    @Test
    public void shouldDedupePreservingOrder() {
        Assert.assertEquals(Arrays.asList("a", "b"),
                ChatCompletionState.dedupe(new String[] { "a", "b", "a", null, "b" }));
        Assert.assertTrue(ChatCompletionState.dedupe(null).isEmpty());
    }
}
