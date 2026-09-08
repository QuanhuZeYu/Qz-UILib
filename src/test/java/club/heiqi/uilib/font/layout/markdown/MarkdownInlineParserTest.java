package club.heiqi.uilib.font.layout.markdown;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * MarkdownInlineParser 测试矩阵（聊天场景误伤防护为核心，与 LatexParserTest 同套路）。
 */
public class MarkdownInlineParserTest {

    private static TextStyle baseStyle() {
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        return style;
    }

    /** 拼接全部段的纯文本（latex 段以 <latex>source</latex> 占位）。 */
    private static String plainText(List<TextSegment> segments) {
        StringBuilder builder = new StringBuilder();
        for (TextSegment segment : segments) {
            builder.append(segment.isLatex() ? "<latex>" + segment.getLatexSource() + "</latex>"
                    : segment.getText());
        }
        return builder.toString();
    }

    private static boolean anyItalic(List<TextSegment> segments) {
        for (TextSegment segment : segments) {
            if (segment.getStyle().isItalic()) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void shouldParseBold() {
        List<TextSegment> segments = MarkdownInlineParser.parse("前 **bold** 后", baseStyle());
        Assert.assertEquals(3, segments.size());
        Assert.assertEquals("bold", segments.get(1).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(1).getStyle().getFontType());
    }

    @Test
    public void shouldParseUnderscoreBold() {
        List<TextSegment> segments = MarkdownInlineParser.parse("__bold__", baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("bold", segments.get(0).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(0).getStyle().getFontType());
    }

    @Test
    public void shouldParseItalic() {
        List<TextSegment> segments = MarkdownInlineParser.parse("前 *italic* 后", baseStyle());
        Assert.assertEquals(3, segments.size());
        Assert.assertEquals("italic", segments.get(1).getText());
        Assert.assertTrue(segments.get(1).getStyle().isItalic());
        Assert.assertEquals(FontType.NORMAL, segments.get(1).getStyle().getFontType());
    }

    @Test
    public void shouldParseBoldItalicCombo() {
        List<TextSegment> segments = MarkdownInlineParser.parse("***both***", baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("both", segments.get(0).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(0).getStyle().getFontType());
        Assert.assertTrue(segments.get(0).getStyle().isItalic());
    }

    @Test
    public void shouldNestItalicInsideBold() {
        List<TextSegment> segments = MarkdownInlineParser.parse("**bold *inner* bold**", baseStyle());
        Assert.assertEquals(3, segments.size());
        Assert.assertEquals("bold ", segments.get(0).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(0).getStyle().getFontType());
        Assert.assertEquals("inner", segments.get(1).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(1).getStyle().getFontType());
        Assert.assertTrue(segments.get(1).getStyle().isItalic());
        Assert.assertEquals(" bold", segments.get(2).getText());
    }

    @Test
    public void shouldParseStrikethrough() {
        List<TextSegment> segments = MarkdownInlineParser.parse("~~gone~~", baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertTrue(segments.get(0).getStyle().isStrikethrough());
    }

    // ==================== 误伤防护(聊天场景核心) ====================

    @Test
    public void shouldNotTriggerOnSnakeCase() {
        Assert.assertEquals("hello_world", plainText(MarkdownInlineParser.parse("hello_world", baseStyle())));
        Assert.assertEquals("a_b", plainText(MarkdownInlineParser.parse("a_b", baseStyle())));
        Assert.assertEquals("x_1", plainText(MarkdownInlineParser.parse("x_1", baseStyle())));
        Assert.assertFalse(anyItalic(MarkdownInlineParser.parse("hello_world", baseStyle())));
    }

    @Test
    public void shouldNotTriggerOnMultiplication() {
        Assert.assertEquals("a*b", plainText(MarkdownInlineParser.parse("a*b", baseStyle())));
        Assert.assertEquals("2*3=6", plainText(MarkdownInlineParser.parse("2*3=6", baseStyle())));
        Assert.assertEquals("x * y", plainText(MarkdownInlineParser.parse("x * y", baseStyle())));
        Assert.assertFalse(anyItalic(MarkdownInlineParser.parse("2*3=6", baseStyle())));
    }

    @Test
    public void shouldNotTriggerOnCjkSurroundedUnderscore() {
        Assert.assertEquals("中文_夹_中文",
                plainText(MarkdownInlineParser.parse("中文_夹_中文", baseStyle())));
        Assert.assertFalse(anyItalic(MarkdownInlineParser.parse("中文_夹_中文", baseStyle())));
    }

    @Test
    public void shouldAllowBoldAroundCjk() {
        List<TextSegment> segments = MarkdownInlineParser.parse("这是**重要**消息", baseStyle());
        Assert.assertEquals(3, segments.size());
        Assert.assertEquals("重要", segments.get(1).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(1).getStyle().getFontType());
    }

    @Test
    public void shouldNotTriggerTripleAsteriskAfterLetter() {
        // a***b*** : *** 前邻字母不构成开定界，整体字面
        Assert.assertEquals("a***b***", plainText(MarkdownInlineParser.parse("a***b***", baseStyle())));
    }

    // ==================== code / 链接 / 公式 / 转义 ====================

    @Test
    public void shouldKeepCodeSpanLiteral() {
        char tick = (char) 0x60;
        String input = "code: " + tick + "a **b** c" + tick + " 尾";
        List<TextSegment> segments = MarkdownInlineParser.parse(input, baseStyle());
        Assert.assertEquals(3, segments.size());
        Assert.assertEquals("code: ", segments.get(0).getText());
        // code span 内标记一律字面:** 不解析为粗体
        Assert.assertEquals("a **b** c", segments.get(1).getText());
        Assert.assertEquals(FontType.NORMAL, segments.get(1).getStyle().getFontType());
        Assert.assertEquals(" 尾", segments.get(2).getText());
    }

    @Test
    public void shouldParseLinkWithUrl() {
        List<TextSegment> segments = MarkdownInlineParser.parse("[点我](https://example.com/a)", baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("点我", segments.get(0).getText());
        Assert.assertEquals("https://example.com/a", segments.get(0).getStyle().getLink());
        Assert.assertTrue(segments.get(0).getStyle().isUnderline());
    }

    @Test
    public void shouldParseLinkWithNestedParens() {
        List<TextSegment> segments = MarkdownInlineParser.parse("[wiki](https://a.b/wiki/C_(lang))", baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("https://a.b/wiki/C_(lang)", segments.get(0).getStyle().getLink());
    }

    @Test
    public void shouldNestBoldInsideLinkLabel() {
        List<TextSegment> segments = MarkdownInlineParser.parse("[**粗**链接](https://a.b)", baseStyle());
        Assert.assertEquals(2, segments.size());
        Assert.assertEquals(FontType.BOLD, segments.get(0).getStyle().getFontType());
        Assert.assertEquals("https://a.b", segments.get(0).getStyle().getLink());
        Assert.assertEquals("链接", segments.get(1).getText());
        Assert.assertEquals("https://a.b", segments.get(1).getStyle().getLink());
    }

    @Test
    public void shouldParseInlineLatex() {
        List<TextSegment> segments = MarkdownInlineParser.parse("x=$y^2$+1", baseStyle());
        Assert.assertEquals(3, segments.size());
        Assert.assertTrue(segments.get(1).isLatex());
        Assert.assertEquals("y^2", segments.get(1).getLatexSource());
    }

    @Test
    public void shouldParseDoubleDollarLatex() {
        List<TextSegment> segments = MarkdownInlineParser.parse("$$\\frac{1}{2}$$", baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertTrue(segments.get(0).isLatex());
        Assert.assertEquals("\\frac{1}{2}", segments.get(0).getLatexSource());
    }

    @Test
    public void shouldPreserveTextAfterIncompleteDoubleDollarClose() {
        Assert.assertEquals("$$x$Z", plainText(MarkdownInlineParser.parse("$$x$Z", baseStyle())));
        Assert.assertEquals("$$x$", plainText(MarkdownInlineParser.parse("$$x$", baseStyle())));
        Assert.assertEquals("<latex>x$y</latex>Z",
                plainText(MarkdownInlineParser.parse("$$x$y$$Z", baseStyle())));
        Assert.assertEquals("<latex>x</latex>Z",
                plainText(MarkdownInlineParser.parse("$$x$$Z", baseStyle())));
    }

    @Test
    public void shouldNotTreatDollarAmountsAsLatex() {
        Assert.assertEquals("$5.99", plainText(MarkdownInlineParser.parse("$5.99", baseStyle())));
        Assert.assertEquals("$5 and $6", plainText(MarkdownInlineParser.parse("$5 and $6", baseStyle())));
        List<TextSegment> segments = MarkdownInlineParser.parse("$5 and $6", baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertFalse(segments.get(0).isLatex());
    }

    @Test
    public void shouldParseEscapes() {
        Assert.assertEquals("*x*", plainText(MarkdownInlineParser.parse("\\*x\\*", baseStyle())));
        Assert.assertEquals("_x_", plainText(MarkdownInlineParser.parse("\\_x\\_", baseStyle())));
        Assert.assertEquals("$5", plainText(MarkdownInlineParser.parse("\\$5", baseStyle())));
        Assert.assertEquals("\\", plainText(MarkdownInlineParser.parse("\\\\", baseStyle())));
    }

    // ==================== 宽容失败 ====================

    @Test
    public void shouldKeepUnclosedDelimitersLiteral() {
        Assert.assertEquals("**a", plainText(MarkdownInlineParser.parse("**a", baseStyle())));
        Assert.assertEquals("*a", plainText(MarkdownInlineParser.parse("*a", baseStyle())));
        Assert.assertEquals("~~a", plainText(MarkdownInlineParser.parse("~~a", baseStyle())));
        Assert.assertEquals("$a", plainText(MarkdownInlineParser.parse("$a", baseStyle())));
        Assert.assertEquals("[a](b", plainText(MarkdownInlineParser.parse("[a](b", baseStyle())));
    }

    @Test
    public void shouldKeepOrphanAsteriskLiteral() {
        Assert.assertEquals("a * b", plainText(MarkdownInlineParser.parse("a * b", baseStyle())));
        Assert.assertEquals("* ", plainText(MarkdownInlineParser.parse("* ", baseStyle())));
    }

    @Test
    public void shouldKeepNewlinesLiteral() {
        List<TextSegment> segments = MarkdownInlineParser.parse("第一行\n第二行", baseStyle());
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("第一行\n第二行", segments.get(0).getText());
    }

    // ==================== 样式叠加 ====================

    @Test
    public void shouldInheritBaseStyleColor() {
        TextStyle base = baseStyle();
        base.setColor(0xFF336699);
        List<TextSegment> segments = MarkdownInlineParser.parse("**bold**", base);
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals(0xFF336699, segments.get(0).getStyle().getColor());
        Assert.assertEquals(FontType.BOLD, segments.get(0).getStyle().getFontType());
    }

    @Test
    public void shouldNotMutateBaseStyle() {
        TextStyle base = baseStyle();
        MarkdownInlineParser.parse("**bold** *italic*", base);
        Assert.assertEquals(FontType.NORMAL, base.getFontType());
        Assert.assertFalse(base.isItalic());
        Assert.assertFalse(base.isUnderline());
    }

    @Test
    public void shouldParseSpanStream() {
        TextStyle boldBase = baseStyle();
        boldBase.setFontType(FontType.BOLD);
        List<MarkdownSpan> spans = java.util.Arrays.asList(
                new MarkdownSpan("前缀 ", baseStyle()),
                new MarkdownSpan("*斜体*", boldBase));
        List<TextSegment> segments = MarkdownInlineParser.parse(spans);
        Assert.assertEquals(2, segments.size());
        Assert.assertEquals("前缀 ", segments.get(0).getText());
        Assert.assertEquals("斜体", segments.get(1).getText());
        Assert.assertTrue(segments.get(1).getStyle().isItalic());
        Assert.assertEquals(FontType.BOLD, segments.get(1).getStyle().getFontType());
    }

    @Test
    public void shouldReturnEmptyForEmptyInput() {
        Assert.assertTrue(MarkdownInlineParser.parse("", baseStyle()).isEmpty());
        Assert.assertTrue(MarkdownInlineParser.parse(null, baseStyle()).isEmpty());
        Assert.assertTrue(MarkdownInlineParser.parse((String) null, null).isEmpty());
    }
}
