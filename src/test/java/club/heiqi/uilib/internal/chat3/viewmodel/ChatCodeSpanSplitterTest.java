package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * ChatCodeSpanSplitter 契约测试(T6b):反引号对 → 衬底 code 段(设计稿 §3.5)。
 *
 * <p>轻量行级规则:反引号对 `code` 切 code 段;单反引号/未闭合/空配对按字面;
 * code 段内不嵌套解析;无 code 零分配返回原列表。</p>
 */
public class ChatCodeSpanSplitterTest {

    private static final int CODE_BG = ChatMarkdownSettings.getCodeBackgroundArgb();

    private static TextStyle plainStyle() {
        TextStyle style = new TextStyle();
        style.setColor(0xFFFFFFFF);
        return style;
    }

    private static TextSegment seg(String text) {
        return new TextSegment(text, plainStyle());
    }

    private static List<TextSegment> single(String text) {
        return Collections.singletonList(seg(text));
    }

    private static String str(List<TextSegment> segments) {
        StringBuilder builder = new StringBuilder();
        for (TextSegment segment : segments) {
            builder.append(segment.getText());
        }
        return builder.toString();
    }

    // ==================== 反引号对切分 ====================

    @Test
    public void splitsPairedBackticksIntoCodeSegments() {
        List<TextSegment> out = ChatCodeSpanSplitter.split(single("a `code` b"), CODE_BG);
        Assert.assertEquals("前缀 + code + 后缀三段", 3, out.size());
        Assert.assertEquals("a ", out.get(0).getText());
        Assert.assertEquals("code", out.get(1).getText());
        Assert.assertEquals(" b", out.get(2).getText());
        Assert.assertTrue("code 段标记", out.get(1).getStyle().isCodeSpan());
        Assert.assertEquals("code 衬底色注入", CODE_BG,
                out.get(1).getStyle().getCodeBackgroundColor());
        Assert.assertFalse("前缀段非 code", out.get(0).getStyle().isCodeSpan());
        Assert.assertFalse("后缀段非 code", out.get(2).getStyle().isCodeSpan());
        Assert.assertEquals("拼接后文本流不变(反引号为标记已剥除)", "a code b", str(out));
    }

    @Test
    public void splitsMultiplePairsInOneSegment() {
        List<TextSegment> out = ChatCodeSpanSplitter.split(single("`a` x `b`"), CODE_BG);
        Assert.assertEquals("code + 文本 + code 三段", 3, out.size());
        Assert.assertTrue(out.get(0).getStyle().isCodeSpan());
        Assert.assertEquals("a", out.get(0).getText());
        Assert.assertFalse("中间文本段非 code", out.get(1).getStyle().isCodeSpan());
        Assert.assertEquals(" x ", out.get(1).getText());
        Assert.assertTrue(out.get(2).getStyle().isCodeSpan());
        Assert.assertEquals("b", out.get(2).getText());
    }

    @Test
    public void codeSpanPreservesBaseStyleBits() {
        TextStyle bold = plainStyle();
        bold.setFontType(FontType.BOLD);
        bold.setItalic(true);
        List<TextSegment> out = ChatCodeSpanSplitter.split(
                Collections.singletonList(new TextSegment("`x`", bold)), CODE_BG);
        Assert.assertEquals(1, out.size());
        Assert.assertEquals("code 段继承粗体", FontType.BOLD,
                out.get(0).getStyle().getFontType());
        Assert.assertTrue("code 段继承斜体", out.get(0).getStyle().isItalic());
        Assert.assertEquals("code 段保留段色", 0xFFFFFFFF, out.get(0).getStyle().getColor());
    }

    // ==================== 单反引号/未闭合/空配对 ====================

    @Test
    public void singleBacktickStaysLiteral() {
        List<TextSegment> base = single("a ` b");
        List<TextSegment> out = ChatCodeSpanSplitter.split(base, CODE_BG);
        Assert.assertSame("无配对零分配返回原列表", base, out);
    }

    @Test
    public void unclosedPairStaysLiteral() {
        List<TextSegment> base = single("a `b c");
        List<TextSegment> out = ChatCodeSpanSplitter.split(base, CODE_BG);
        Assert.assertSame("未闭合零分配返回原列表", base, out);
    }

    @Test
    public void emptyPairStaysLiteral() {
        List<TextSegment> base = single("a ``b");
        List<TextSegment> out = ChatCodeSpanSplitter.split(base, CODE_BG);
        Assert.assertSame("空配对按字面(零分配)", base, out);
    }

    // ==================== 零分配 ====================

    @Test
    public void noCodeReturnsSameListReference() {
        List<TextSegment> base = single("hello world");
        Assert.assertSame("无 code 原列表引用返回", base, ChatCodeSpanSplitter.split(base, CODE_BG));
        List<TextSegment> empty = Collections.emptyList();
        Assert.assertSame("空列表原引用返回", empty, ChatCodeSpanSplitter.split(empty, CODE_BG));
        Assert.assertNull("null 原样返回", ChatCodeSpanSplitter.split(null, CODE_BG));
    }

    @Test
    public void multiSegmentNoCodeReturnsSameListReference() {
        List<TextSegment> base = new java.util.ArrayList<TextSegment>();
        base.add(seg("one"));
        base.add(seg("two"));
        Assert.assertSame("多段无 code 原列表引用返回", base, ChatCodeSpanSplitter.split(base, CODE_BG));
    }

    // ==================== code 段内不嵌套解析 ====================

    @Test
    public void danglingTrailingBacktickStaysLiteral() {
        // "x `y ` z` w"(奇数反引号):第 1/2 个反引号配对成 code "y ",第 3 个反引号
        // 无闭合配对 → 剩余 " z` w" 全部按字面(不参与二次切分,即 code 后不嵌套解析)
        List<TextSegment> out = ChatCodeSpanSplitter.split(single("x `y ` z` w"), CODE_BG);
        Assert.assertEquals(3, out.size());
        Assert.assertEquals("x ", out.get(0).getText());
        Assert.assertTrue("第 1/2 个反引号配对成 code 段", out.get(1).getStyle().isCodeSpan());
        Assert.assertEquals("code 内容", "y ", out.get(1).getText());
        Assert.assertFalse("游离反引号按字面透传", out.get(2).getStyle().isCodeSpan());
        Assert.assertEquals(" z` w", out.get(2).getText());
        Assert.assertEquals("拼接后文本流不变", "x y  z` w", str(out));
    }

    @Test
    public void latexSegmentPassesThroughUnchanged() {
        TextStyle style = plainStyle();
        List<TextSegment> base = new java.util.ArrayList<TextSegment>();
        base.add(TextSegment.forLatex("x^2", style));
        base.add(seg("a `b`"));
        List<TextSegment> out = ChatCodeSpanSplitter.split(base, CODE_BG);
        Assert.assertEquals(3, out.size());
        Assert.assertTrue("LaTeX 段原样透传", out.get(0).isLatex());
        Assert.assertEquals("文本段前缀", "a ", out.get(1).getText());
        Assert.assertTrue("code 切分照常作用于文本段", out.get(2).getStyle().isCodeSpan());
    }

    // ==================== 与 linkify 协作(解析顺序:先 code 切分后链接化) ====================

    @Test
    public void codeSegmentClearsLinkSemantics() {
        // 解析序 = 先 code 切分后 linkify(linkify 对 code 段跳过):code 内 URL 不残留链接语义。
        List<TextSegment> out = ChatCodeSpanSplitter.split(single("`http://a.co` tail"), CODE_BG);
        List<TextSegment> linked = ChatUrlLinkifier.linkify(out, ChatMarkdownSettings.getLinkArgb());
        Assert.assertEquals(2, linked.size());
        Assert.assertTrue(linked.get(0).getStyle().isCodeSpan());
        Assert.assertNull("code 段内 URL 不残留链接语义", linked.get(0).getStyle().getLink());
        Assert.assertNull("普通后缀段无链接", linked.get(1).getStyle().getLink());
    }

    @Test
    public void linkBesidesCodeStaysLink() {
        List<TextSegment> out = ChatCodeSpanSplitter.split(single("`c` http://a.co"), CODE_BG);
        List<TextSegment> linked = ChatUrlLinkifier.linkify(out, ChatMarkdownSettings.getLinkArgb());
        Assert.assertEquals(3, linked.size());
        Assert.assertTrue("code 段标记", linked.get(0).getStyle().isCodeSpan());
        Assert.assertNull("code 段内无链接", linked.get(0).getStyle().getLink());
        Assert.assertEquals("code 外 URL 保持链接", "http://a.co", linked.get(2).getStyle().getLink());
    }
}
