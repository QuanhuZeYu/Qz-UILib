package club.heiqi.uilib.internal.chat3.viewmodel;

import org.junit.Assert;
import org.junit.Test;

/**
 * ChatMarkdownLineRule 契约测试(C 拍板 §3.5/§10.1):无序列表标记/层级缩进映射、
 * 有序列表保留序号、块级公式($$ / $...$ 独占行)识别、普通行不受影响。
 */
public class ChatMarkdownLineRuleTest {

    @Test
    public void unorderedListMarksAreClassified() {
        for (String marker : new String[] { "- ", "* ", "+ " }) {
            ChatMarkdownLineRule.Match match = ChatMarkdownLineRule.classify(marker + "item");
            Assert.assertEquals(marker + " 是无序列表", ChatMarkdownLineRule.Kind.UNORDERED_LIST,
                    match.getKind());
            Assert.assertEquals("内容去标记", "item", match.getContent());
            Assert.assertEquals("无前导空格层级 0", 0, match.getLevel());
        }
    }

    @Test
    public void leadingSpacesMapToLevelPerTwoSpaces() {
        Assert.assertEquals("无缩进 → 0 级", 0,
                ChatMarkdownLineRule.classify("- a").getLevel());
        Assert.assertEquals("1 空格 → 0 级", 0,
                ChatMarkdownLineRule.classify(" - a").getLevel());
        Assert.assertEquals("2 空格 → 1 级", 1,
                ChatMarkdownLineRule.classify("  - a").getLevel());
        Assert.assertEquals("3 空格 → 1 级", 1,
                ChatMarkdownLineRule.classify("   - a").getLevel());
        Assert.assertEquals("4 空格 → 2 级", 2,
                ChatMarkdownLineRule.classify("    - a").getLevel());
    }

    @Test
    public void orderedListKeepsNumberAsOrderedKind() {
        ChatMarkdownLineRule.Match match = ChatMarkdownLineRule.classify("12. keep me");
        Assert.assertEquals("N. 识别为有序列表(保留序号)", ChatMarkdownLineRule.Kind.ORDERED_LIST,
                match.getKind());
        Assert.assertEquals("原文保留", "12. keep me", match.getContent());
    }

    @Test
    public void blockMathDoubleDollarIsClassified() {
        ChatMarkdownLineRule.Match match = ChatMarkdownLineRule.classify("$$x^2$$");
        Assert.assertEquals(ChatMarkdownLineRule.Kind.BLOCK_MATH, match.getKind());
        Assert.assertEquals("x^2", match.getLatexSource());

        // 只有开头 $$(无结尾)→ 整行其余为 TeX 源
        ChatMarkdownLineRule.Match open = ChatMarkdownLineRule.classify("$$x^2");
        Assert.assertEquals(ChatMarkdownLineRule.Kind.BLOCK_MATH, open.getKind());
        Assert.assertEquals("x^2", open.getLatexSource());
    }

    @Test
    public void singleDollarWholeLineIsBlockMath() {
        ChatMarkdownLineRule.Match match = ChatMarkdownLineRule.classify("$x^2$");
        Assert.assertEquals(ChatMarkdownLineRule.Kind.BLOCK_MATH, match.getKind());
        Assert.assertEquals("x^2", match.getLatexSource());
    }

    @Test
    public void inlineMathWithOtherContentIsNotBlockMath() {
        // 行内混排($ 不独占 / 多个 $ 对)→ 普通行
        Assert.assertEquals(ChatMarkdownLineRule.Kind.NONE,
                ChatMarkdownLineRule.classify("foo $x$ bar").getKind());
        Assert.assertEquals(ChatMarkdownLineRule.Kind.NONE,
                ChatMarkdownLineRule.classify("$a$ $b$").getKind());
    }

    @Test
    public void plainLinesAreUnaffected() {
        Assert.assertEquals(ChatMarkdownLineRule.Kind.NONE,
                ChatMarkdownLineRule.classify("-not-list").getKind());
        Assert.assertEquals(ChatMarkdownLineRule.Kind.NONE,
                ChatMarkdownLineRule.classify("a - b").getKind());
        Assert.assertEquals(ChatMarkdownLineRule.Kind.NONE,
                ChatMarkdownLineRule.classify("").getKind());
        Assert.assertEquals(ChatMarkdownLineRule.Kind.NONE,
                ChatMarkdownLineRule.classify("   ").getKind());
        Assert.assertEquals(ChatMarkdownLineRule.Kind.NONE,
                ChatMarkdownLineRule.classify("1.5 不是列表").getKind());
    }
}
