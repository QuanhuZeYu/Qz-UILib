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

    // ==================== F5:系统消息链接化保留原色(用户拍板) ====================

    @Test
    public void linkifyPreserveColorKeepsOriginalSegmentColor() {
        TextStyle sys = plainStyle();
        sys.setColor(ChatMarkdownSettings.getSystemTextArgb());
        List<TextSegment> out = ChatUrlLinkifier.linkifyPreserveColor(
                Collections.singletonList(new TextSegment("see http://a.co x", sys)));
        Assert.assertEquals(3, out.size());
        Assert.assertEquals("http://a.co", out.get(1).getStyle().getLink());
        Assert.assertEquals("URL 切片保留系统消息 § 原色(不强制 text-link)",
                ChatMarkdownSettings.getSystemTextArgb(), out.get(1).getStyle().getColor());
        Assert.assertNotEquals("与统一链接色不同", ChatMarkdownSettings.getLinkArgb(),
                out.get(1).getStyle().getColor());
        Assert.assertFalse("链接默认无下划线", out.get(1).getStyle().isUnderline());
    }

    @Test
    public void linkifyPreserveColorKeepsEachColorAcrossAdjacentSections() {
        // URL 被 § 彩色段拆开:各切片保留各自 § 色(不合并为统一 link 色)
        TextStyle green = plainStyle();
        green.setColor(0xFF55FF55);
        TextStyle blue = plainStyle();
        blue.setColor(0xFF5555FF);
        List<TextSegment> base = Arrays.asList(
                new TextSegment("go https://a.b", green),
                new TextSegment("/c x", blue));
        List<TextSegment> out = ChatUrlLinkifier.linkifyPreserveColor(base);
        Assert.assertEquals(4, out.size());
        Assert.assertEquals("https://a.b/c", out.get(1).getStyle().getLink());
        Assert.assertEquals("前切片保留段 1 绿色", 0xFF55FF55, out.get(1).getStyle().getColor());
        Assert.assertEquals("后切片保留段 2 蓝色", 0xFF5555FF, out.get(2).getStyle().getColor());
        Assert.assertEquals("https://a.b/c", out.get(2).getStyle().getLink());
    }

    @Test
    public void linkifyPreserveColorReturnsSameReferenceWithoutUrls() {
        List<TextSegment> base = single("plain text");
        Assert.assertSame("无 URL 段流原引用返回(零分配)", base,
                ChatUrlLinkifier.linkifyPreserveColor(base));
    }

    // ==================== P8:URL 跨 code/LaTeX 边界(分段窗口扫描) ====================

    @Test
    public void linkifyTruncatesUrlAcrossCodeBoundary() {
        // P8:段1 "http://x." 后接 code 边界段——旧实现把段3 "z" 拼进幻影 URL
        // "http://x.z" 且两端同时挂链;窗口扫描下 URL 体 = 分隔符前连续文本,
        // 尾随 '.' 剥离后产独立 URL "http://x",code 段透传,段3 不误链。
        TextStyle codeStyle = plainStyle();
        codeStyle.setCodeSpan(true);
        List<TextSegment> base = Arrays.asList(
                seg("http://x."),
                new TextSegment("y", codeStyle),
                seg("z"));
        List<TextSegment> out = ChatUrlLinkifier.linkify(base, LINK);
        Assert.assertEquals("段1 切出 link+尾点,code 透传,段3 原样 → 4 段", 4, out.size());
        Assert.assertEquals("http://x", out.get(0).getText());
        Assert.assertEquals("剥离尾点后挂独立 URL", "http://x", out.get(0).getStyle().getLink());
        Assert.assertEquals(LINK, out.get(0).getStyle().getColor());
        Assert.assertEquals("尾随 '.' 剥离为独立非链接段", ".", out.get(1).getText());
        Assert.assertNull(out.get(1).getStyle().getLink());
        Assert.assertTrue("code 段透传", out.get(2).getStyle().isCodeSpan());
        Assert.assertNull("code 段无 link", out.get(2).getStyle().getLink());
        Assert.assertEquals("z", out.get(3).getText());
        Assert.assertNull("段3 无 link(不被跨边界 URL 吞并)", out.get(3).getStyle().getLink());
    }

    @Test
    public void linkifyKeepsCompleteUrlBeforeCodeBoundary() {
        // 完整 URL 全部落在边界段前的窗口内 → 全量 link,不因边界截断/吞并。
        TextStyle codeStyle = plainStyle();
        codeStyle.setCodeSpan(true);
        List<TextSegment> base = Arrays.asList(
                seg("http://x.co"),
                new TextSegment("y", codeStyle),
                seg("z"));
        List<TextSegment> out = ChatUrlLinkifier.linkify(base, LINK);
        Assert.assertEquals(3, out.size());
        Assert.assertEquals("http://x.co", out.get(0).getText());
        Assert.assertEquals("http://x.co", out.get(0).getStyle().getLink());
        Assert.assertEquals(LINK, out.get(0).getStyle().getColor());
        Assert.assertTrue("code 段透传", out.get(1).getStyle().isCodeSpan());
        Assert.assertNull(out.get(1).getStyle().getLink());
        Assert.assertEquals("z", out.get(2).getText());
        Assert.assertNull(out.get(2).getStyle().getLink());
    }

    @Test
    public void linkifyTruncatesUrlAcrossLatexBoundary() {
        // LaTeX 段同为硬边界:窗口在 latex 边界处断开,规则与 code 一致。
        TextStyle style = plainStyle();
        List<TextSegment> base = Arrays.asList(
                seg("http://x."),
                TextSegment.forLatex("y", style),
                seg("z"));
        List<TextSegment> out = ChatUrlLinkifier.linkify(base, LINK);
        Assert.assertEquals(4, out.size());
        Assert.assertEquals("http://x", out.get(0).getText());
        Assert.assertEquals("http://x", out.get(0).getStyle().getLink());
        Assert.assertEquals(".", out.get(1).getText());
        Assert.assertNull(out.get(1).getStyle().getLink());
        Assert.assertTrue("LaTeX 段透传", out.get(2).isLatex());
        Assert.assertSame("LaTeX 段原引用透传", base.get(1), out.get(2));
        Assert.assertNull(out.get(2).getStyle().getLink());
        Assert.assertEquals("z", out.get(3).getText());
        Assert.assertNull(out.get(3).getStyle().getLink());
    }

    @Test
    public void linkifyCrossBoundaryNoPhantomUrl() {
        // URL 全在 code 段内:两侧普通窗口("pre "/" post")无 URL → 零分配原引用返回。
        TextStyle codeStyle = plainStyle();
        codeStyle.setCodeSpan(true);
        List<TextSegment> base = Arrays.asList(
                seg("pre "),
                new TextSegment("http://a.co", codeStyle),
                seg(" post"));
        List<TextSegment> out = ChatUrlLinkifier.linkify(base, LINK);
        Assert.assertSame("全部窗口无 URL → 原引用返回(零分配)", base, out);
        Assert.assertNull(out.get(0).getStyle().getLink());
        Assert.assertNull(out.get(1).getStyle().getLink());
        Assert.assertNull(out.get(2).getStyle().getLink());
    }

    @Test
    public void linkifyMultipleWindowsEachScanned() {
        // 两普通窗口(A 边界段隔开)各自独立扫描:各自识别 URL,不得跨窗口合并成
        // 幻影 "http://a.cohttp://b.co"。
        TextStyle codeStyle = plainStyle();
        codeStyle.setCodeSpan(true);
        List<TextSegment> base = Arrays.asList(
                seg("see http://a.co"),
                new TextSegment("c", codeStyle),
                seg("http://b.co end"));
        List<TextSegment> out = ChatUrlLinkifier.linkify(base, LINK);
        Assert.assertEquals("段1(普通+link)→2,code→1,段3(link+普通)→2 → 5 段", 5, out.size());
        Assert.assertEquals("see ", out.get(0).getText());
        Assert.assertNull(out.get(0).getStyle().getLink());
        Assert.assertEquals("http://a.co", out.get(1).getText());
        Assert.assertEquals("窗口1 URL 独立", "http://a.co", out.get(1).getStyle().getLink());
        Assert.assertTrue("边界 code 段透传", out.get(2).getStyle().isCodeSpan());
        Assert.assertNull(out.get(2).getStyle().getLink());
        Assert.assertEquals("http://b.co", out.get(3).getText());
        Assert.assertEquals("窗口2 URL 独立", "http://b.co", out.get(3).getStyle().getLink());
        Assert.assertEquals(" end", out.get(4).getText());
        Assert.assertNull(out.get(4).getStyle().getLink());
    }

    @Test
    public void findUrlsUnchangedDirectUnit() {
        // 回归护栏:findUrls 扫描器字符类/匹配语义不变(窗口扫描只改调用方式、
        // 不碰扫描器本身)。带尾点输入在 findUrls 层即剥尾标点("http://x." → "http://x")。
        List<ChatUrlLinkifier.Match> matches = ChatUrlLinkifier.findUrls("x http://a.co y http://b.co z");
        Assert.assertEquals(2, matches.size());
        Assert.assertEquals("http://a.co", matches.get(0).url);
        Assert.assertEquals(2, matches.get(0).start);
        Assert.assertEquals(2 + 11, matches.get(0).end);
        Assert.assertEquals("http://b.co", matches.get(1).url);
        List<ChatUrlLinkifier.Match> trailingDot = ChatUrlLinkifier.findUrls("http://x.");
        Assert.assertEquals(1, trailingDot.size());
        Assert.assertEquals("http://x", trailingDot.get(0).url);
        Assert.assertEquals(0, trailingDot.get(0).start);
        Assert.assertEquals(8, trailingDot.get(0).end);
    }

    // ==================== 命中扩展(设计稿 §5.2) ====================

    @Test
    public void hitPaddingMatchesDesignSpec() {
        Assert.assertEquals("命中区上下扩 2px", 2, ChatUrlLinkifier.HIT_PAD_Y);
        Assert.assertEquals("命中区左右扩 1px", 1, ChatUrlLinkifier.HIT_PAD_X);
    }
}
