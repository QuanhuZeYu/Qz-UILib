package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * ChatUrlLinkifier 契约测试(T6a):URL 自动链接化 / hover 变体 / 命中扩展常量。
 *
 * <p>设计稿 §3.5/§5.2:链接默认色 0xFF7AB8F5 无下划线;hover 提亮 0xFF9CCBF8 + 下划线;
 * 命中区 = 文本包围盒上下 +2 / 左右 +1。</p>
 */
public class ChatUrlLinkifierTest {

    private static final int LINK = ChatMarkdownSettings.getLinkArgb();
    private static final int HOVER = ChatMarkdownSettings.getLinkHoverArgb();

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

    // ==================== findUrls(包级纯函数) ====================

    @Test
    public void findsHttpUrlsWithPlainPrefix() {
        List<ChatUrlLinkifier.Match> matches = ChatUrlLinkifier.findUrls("see http://a.com/b?p=1 ok");
        Assert.assertEquals(1, matches.size());
        Assert.assertEquals("http://a.com/b?p=1", matches.get(0).url);
        Assert.assertEquals(4, matches.get(0).start);
        Assert.assertEquals(4 + 18, matches.get(0).end);
    }

    @Test
    public void findsHttpsAndWwwAndUppercase() {
        Assert.assertEquals("https://x.y", ChatUrlLinkifier.findUrls("go https://x.y now").get(0).url);
        Assert.assertEquals("www.example.com/z", ChatUrlLinkifier.findUrls("see www.example.com/z").get(0).url);
        Assert.assertEquals("HTTP://X.Y/A", ChatUrlLinkifier.findUrls("UP HTTP://X.Y/A").get(0).url);
    }

    @Test
    public void stripsTrailingPunctuation() {
        Assert.assertEquals("https://a.b/c", ChatUrlLinkifier.findUrls("(https://a.b/c)").get(0).url);
        Assert.assertEquals("https://a.b/c", ChatUrlLinkifier.findUrls("x https://a.b/c。").get(0).url);
        Assert.assertEquals("http://a.b/c", ChatUrlLinkifier.findUrls("http://a.b/c。。。").get(0).url);
        Assert.assertEquals("http://a.b", ChatUrlLinkifier.findUrls("http://a.b,").get(0).url);
        Assert.assertEquals("http://a.b/c", ChatUrlLinkifier.findUrls("见 http://a.b/c，继续").get(0).url);
    }

    @Test
    public void findsMultipleUrlsPerSegment() {
        List<ChatUrlLinkifier.Match> matches = ChatUrlLinkifier.findUrls("http://a.com http://b.com");
        Assert.assertEquals(2, matches.size());
        Assert.assertEquals("http://a.com", matches.get(0).url);
        Assert.assertEquals("http://b.com", matches.get(1).url);
    }

    @Test
    public void findsNothingWithoutScheme() {
        Assert.assertTrue(ChatUrlLinkifier.findUrls("no url here").isEmpty());
        Assert.assertTrue("裸 www 无点不识别", ChatUrlLinkifier.findUrls("www").isEmpty());
        Assert.assertTrue(ChatUrlLinkifier.findUrls("").isEmpty());
        Assert.assertTrue(ChatUrlLinkifier.findUrls(null).isEmpty());
    }

    // ==================== linkify ====================

    @Test
    public void linkifySplitsUrlIntoSegmentWithDefaultColorWithoutUnderline() {
        List<TextSegment> out = ChatUrlLinkifier.linkify(single("see http://a.co x"), LINK);
        Assert.assertEquals(3, out.size());
        Assert.assertEquals("see ", out.get(0).getText());
        Assert.assertNull("非链接段不挂 link", out.get(0).getStyle().getLink());

        Assert.assertEquals("http://a.co", out.get(1).getText());
        Assert.assertEquals("http://a.co", out.get(1).getStyle().getLink());
        Assert.assertEquals(LINK, out.get(1).getStyle().getColor());
        Assert.assertFalse("链接默认无下划线(设计稿 §3.5)", out.get(1).getStyle().isUnderline());

        Assert.assertEquals(" x", out.get(2).getText());
    }

    @Test
    public void linkifyPreservesBaseStyleOnNonLinkParts() {
        TextStyle base = plainStyle();
        base.setItalic(true);
        TextSegment input = new TextSegment("go http://a.co now", base);
        List<TextSegment> out = ChatUrlLinkifier.linkify(
                Collections.singletonList(input), LINK);
        Assert.assertEquals(3, out.size());
        Assert.assertTrue("前缀段继承斜体", out.get(0).getStyle().isItalic());
        Assert.assertTrue("后缀段继承斜体", out.get(2).getStyle().isItalic());
    }

    @Test
    public void linkifyReturnsSameReferenceWithoutUrls() {
        List<TextSegment> base = single("plain text");
        Assert.assertSame("无 URL 段流原引用返回(零分配)", base, ChatUrlLinkifier.linkify(base, LINK));
    }

    @Test
    public void linkifyRecognizesUrlAcrossAdjacentSections() {
        // 模拟 § 彩色段把 URL 拆开(B12 验收偏差 5):段1 = "go https://a.b"(绿),
        // 段2 = "/c x"(蓝紫)——URL 字符跨两段连续,拼接后统一识别。
        TextStyle green = plainStyle();
        green.setColor(0xFF55FF55);
        TextStyle blue = plainStyle();
        blue.setColor(0xFF5555FF);
        List<TextSegment> base = Arrays.asList(
                new TextSegment("go https://a.b", green),
                new TextSegment("/c x", blue));
        List<TextSegment> out = ChatUrlLinkifier.linkify(base, LINK);
        Assert.assertEquals("2 段 + 1 个跨段 URL → 4 段", 4, out.size());
        Assert.assertEquals("go ", out.get(0).getText());
        Assert.assertEquals("URL 前切片(段1 内)强制 link 色", LINK, out.get(1).getStyle().getColor());
        Assert.assertEquals("https://a.b/c", out.get(1).getStyle().getLink());
        Assert.assertEquals("https://a.b", out.get(1).getText());
        Assert.assertEquals("URL 后切片(段2 内)强制 link 色", LINK, out.get(2).getStyle().getColor());
        Assert.assertEquals("https://a.b/c", out.get(2).getStyle().getLink());
        Assert.assertEquals("/c", out.get(2).getText());
        Assert.assertEquals("URL 之外的 § 颜色保留", 0xFF5555FF, out.get(3).getStyle().getColor());
    }

    @Test
    public void linkifyKeepsCodeSegmentAsHardBoundary() {
        // code 段是硬边界:B12 跨段只作用于普通段,code 段仍原样透传、不链接化。
        TextStyle codeStyle = plainStyle();
        codeStyle.setCodeSpan(true);
        List<TextSegment> base = Arrays.asList(seg("x "), new TextSegment("http://a.co", codeStyle));
        List<TextSegment> out = ChatUrlLinkifier.linkify(base, LINK);
        Assert.assertEquals(2, out.size());
        Assert.assertNull("code 段不挂 link", out.get(1).getStyle().getLink());
        Assert.assertSame("code 段原引用透传", base.get(1), out.get(1));
    }

    @Test
    public void linkifyPassesLatexSegmentsThrough() {
        TextStyle style = plainStyle();
        TextSegment latex = TextSegment.forLatex("x^2", style);
        List<TextSegment> out = ChatUrlLinkifier.linkify(
                Collections.singletonList(latex), LINK);
        Assert.assertSame("LaTeX 段不链接化", latex, out.get(0));
    }

    // ==================== hoverLinkify ====================

    @Test
    public void hoverLinkifyBrightensLinkAndAddsUnderline() {
        List<TextSegment> base = ChatUrlLinkifier.linkify(single("see http://a.co x"), LINK);
        List<TextSegment> hover = ChatUrlLinkifier.hoverLinkify(base, HOVER);
        Assert.assertEquals(3, hover.size());
        Assert.assertEquals(HOVER, hover.get(1).getStyle().getColor());
        Assert.assertTrue("hover 加下划线", hover.get(1).getStyle().isUnderline());
        Assert.assertEquals("link 字段保留", "http://a.co", hover.get(1).getStyle().getLink());
        Assert.assertSame("非链接段原引用透传", base.get(0), hover.get(0));
        Assert.assertNotSame("链接段被替换(新对象)", base.get(1), hover.get(1));
    }

    @Test
    public void hoverLinkifyReturnsSameReferenceWithoutLinks() {
        List<TextSegment> base = single("no link");
        Assert.assertSame("无链接段流原引用返回", base, ChatUrlLinkifier.hoverLinkify(base, HOVER));
    }

    // ==================== 命中扩展(设计稿 §5.2) ====================

    @Test
    public void hitPaddingMatchesDesignSpec() {
        Assert.assertEquals("命中区上下扩 2px", 2, ChatUrlLinkifier.HIT_PAD_Y);
        Assert.assertEquals("命中区左右扩 1px", 1, ChatUrlLinkifier.HIT_PAD_X);
    }
}
