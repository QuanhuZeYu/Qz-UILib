package club.heiqi.uilib.font.layout;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;

/**
 * {@link RichTextTagParser} 现代富文本标签解析器测试。
 */
public class RichTextTagParserTest {

    private static final int RED = 0xFFFF0000;

    @Test
    public void shouldParseColorHexAndName() {
        List<TextSegment> segments = RichTextTagParser.parse("<color=#FF0000>a</color>b", baseStyle());

        Assert.assertEquals(2, segments.size());
        Assert.assertEquals("a", segments.get(0).getText());
        Assert.assertEquals(RED, segments.get(0).getStyle().getColor());
        Assert.assertTrue(segments.get(0).getStyle().isColorExplicit());
        Assert.assertEquals("b", segments.get(1).getText());
        Assert.assertFalse(segments.get(1).getStyle().isColorExplicit());

        List<TextSegment> named = RichTextTagParser.parse("<color=red>x</color>", baseStyle());
        Assert.assertEquals(1, named.size());
        Assert.assertEquals(RED, named.get(0).getStyle().getColor());
    }

    @Test
    public void shouldParseArgbColor() {
        List<TextSegment> segments = RichTextTagParser.parse("<color=#80FF0000>x</color>", baseStyle());
        Assert.assertEquals(0x80FF0000, segments.get(0).getStyle().getColor());
    }

    @Test
    public void shouldParseNestedStylesAndRestoreOnClose() {
        List<TextSegment> segments = RichTextTagParser.parse("<color=red>a<b>b</b>c</color>d", baseStyle());

        Assert.assertEquals(4, segments.size());
        Assert.assertEquals("a", segments.get(0).getText());
        Assert.assertEquals(RED, segments.get(0).getStyle().getColor());
        Assert.assertEquals("b", segments.get(1).getText());
        Assert.assertEquals(RED, segments.get(1).getStyle().getColor());
        Assert.assertEquals(FontType.BOLD, segments.get(1).getStyle().getFontType());
        Assert.assertEquals("c", segments.get(2).getText());
        Assert.assertEquals(RED, segments.get(2).getStyle().getColor());
        Assert.assertEquals(FontType.NORMAL, segments.get(2).getStyle().getFontType());
        Assert.assertEquals("d", segments.get(3).getText());
        Assert.assertFalse(segments.get(3).getStyle().isColorExplicit());
    }

    @Test
    public void shouldSupportGenericCloseTag() {
        List<TextSegment> segments = RichTextTagParser.parse("<b><i>x</>y</b>", baseStyle());

        Assert.assertEquals(2, segments.size());
        Assert.assertEquals("x", segments.get(0).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(0).getStyle().getFontType());
        Assert.assertTrue(segments.get(0).getStyle().isItalic());
        Assert.assertEquals("y", segments.get(1).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(1).getStyle().getFontType());
        Assert.assertFalse(segments.get(1).getStyle().isItalic());
    }

    @Test
    public void shouldConvertLineBreakToNewline() {
        List<TextSegment> segments = RichTextTagParser.parse("<b>a<br>b</b>", baseStyle());

        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("a\nb", segments.get(0).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(0).getStyle().getFontType());
    }

    @Test
    public void shouldDecodeEntities() {
        List<TextSegment> segments = RichTextTagParser.parse("a&lt;b&gt;c&amp;d", baseStyle());

        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("a<b>c&d", segments.get(0).getText());
    }

    @Test
    public void shouldKeepUnknownTagsAsLiteralText() {
        List<TextSegment> segments = RichTextTagParser.parse("a<foo>x</foo>b", baseStyle());

        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("a<foo>x</foo>b", segments.get(0).getText());
    }

    @Test
    public void shouldAutoCloseUnclosedTags() {
        List<TextSegment> segments = RichTextTagParser.parse("<b>abc", baseStyle());

        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("abc", segments.get(0).getText());
        Assert.assertEquals(FontType.BOLD, segments.get(0).getStyle().getFontType());
    }

    @Test
    public void shouldIgnoreExtraClosingTags() {
        List<TextSegment> segments = RichTextTagParser.parse("abc</b>", baseStyle());

        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("abc", segments.get(0).getText());
        Assert.assertEquals(FontType.NORMAL, segments.get(0).getStyle().getFontType());
    }

    @Test
    public void shouldIgnoreBadColorAttribute() {
        List<TextSegment> segments = RichTextTagParser.parse("<color=zzz>x</color>", baseStyle());

        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("x", segments.get(0).getText());
        Assert.assertFalse(segments.get(0).getStyle().isColorExplicit());
    }

    @Test
    public void shouldParseAndClampSize() {
        Assert.assertEquals(32, RichTextTagParser.parse("<size=32>x</size>", baseStyle())
                .get(0).getStyle().getFontSizePx());
        Assert.assertEquals(RichTextTagParser.MAX_FONT_SIZE_PX,
                RichTextTagParser.parse("<size=9999>x</size>", baseStyle()).get(0).getStyle().getFontSizePx());
        Assert.assertEquals(RichTextTagParser.MIN_FONT_SIZE_PX,
                RichTextTagParser.parse("<size=-5>x</size>", baseStyle()).get(0).getStyle().getFontSizePx());
        Assert.assertEquals(0, RichTextTagParser.parse("<size=abc>x</size>", baseStyle())
                .get(0).getStyle().getFontSizePx());
    }

    @Test
    public void shouldParseMarkWithDefaultColor() {
        List<TextSegment> segments = RichTextTagParser.parse("<mark>高亮</mark>尾", baseStyle());

        Assert.assertEquals(2, segments.size());
        Assert.assertEquals("高亮", segments.get(0).getText());
        Assert.assertEquals(RichTextTagParser.DEFAULT_MARK_COLOR, segments.get(0).getStyle().getMarkColor());
        Assert.assertEquals("尾", segments.get(1).getText());
        Assert.assertEquals(0, segments.get(1).getStyle().getMarkColor());
    }

    @Test
    public void shouldParseMarkWithCustomColorAndNesting() {
        List<TextSegment> segments = RichTextTagParser.parse("<mark=#FF0000>a<b>b</b></mark>c", baseStyle());

        Assert.assertEquals(3, segments.size());
        Assert.assertEquals(0xFFFF0000, segments.get(0).getStyle().getMarkColor());
        Assert.assertEquals(0xFFFF0000, segments.get(1).getStyle().getMarkColor());
        Assert.assertEquals(FontType.BOLD, segments.get(1).getStyle().getFontType());
        Assert.assertEquals(0, segments.get(2).getStyle().getMarkColor());
    }

    @Test
    public void shouldIgnoreBadMarkColorAttribute() {
        List<TextSegment> segments = RichTextTagParser.parse("<mark=zzz>x</mark>", baseStyle());

        Assert.assertEquals(1, segments.size());
        Assert.assertEquals(0, segments.get(0).getStyle().getMarkColor());
    }

    @Test
    public void shouldSerializeMarkRoundTrip() {
        String text = "前<mark>黄底</mark><mark=#8000FF00>绿底</mark><b>粗<mark>嵌套</mark></b>尾";
        List<TextSegment> parsed = RichTextTagParser.parse(text, baseStyle());
        String serialized = RichTextTagParser.serialize(parsed, baseStyle());
        List<TextSegment> reParsed = RichTextTagParser.parse(serialized, baseStyle());

        assertSegmentsEqual(parsed, reParsed);
        // 默认色序列化为无值 <mark>，自定义色带 8 位 ARGB 值
        Assert.assertTrue(serialized.contains("<mark>"));
        Assert.assertTrue(serialized.contains("<mark=#8000FF00>"));
    }

    @Test
    public void shouldParseSupAndSub() {
        List<TextSegment> segments = RichTextTagParser.parse("x<sup>2</sup>y<sub>n</sub>z", baseStyle());

        Assert.assertEquals(5, segments.size());
        Assert.assertEquals("x", segments.get(0).getText());
        Assert.assertFalse(segments.get(0).getStyle().isSuperscript());
        Assert.assertEquals("2", segments.get(1).getText());
        Assert.assertTrue(segments.get(1).getStyle().isSuperscript());
        Assert.assertFalse(segments.get(1).getStyle().isSubscript());
        Assert.assertEquals("y", segments.get(2).getText());
        Assert.assertFalse(segments.get(2).getStyle().isSuperscript());
        Assert.assertFalse(segments.get(2).getStyle().isSubscript());
        Assert.assertEquals("n", segments.get(3).getText());
        Assert.assertTrue(segments.get(3).getStyle().isSubscript());
        Assert.assertEquals("z", segments.get(4).getText());
        Assert.assertFalse(segments.get(4).getStyle().isSubscript());
    }

    @Test
    public void shouldKeepSupSubMutuallyExclusive() {
        List<TextSegment> segments = RichTextTagParser.parse("<sup>a<sub>b</sub>c</sup>", baseStyle());

        Assert.assertEquals(3, segments.size());
        Assert.assertTrue(segments.get(0).getStyle().isSuperscript());
        Assert.assertTrue(segments.get(1).getStyle().isSubscript());
        Assert.assertFalse(segments.get(1).getStyle().isSuperscript());
        Assert.assertTrue(segments.get(2).getStyle().isSuperscript());
        Assert.assertFalse(segments.get(2).getStyle().isSubscript());
    }

    @Test
    public void shouldSerializeSupSubRoundTrip() {
        String text = "x<sup>2</sup>y<sub>n</sub><sup><b>嵌套</b></sup>尾";
        List<TextSegment> parsed = RichTextTagParser.parse(text, baseStyle());
        String serialized = RichTextTagParser.serialize(parsed, baseStyle());
        List<TextSegment> reParsed = RichTextTagParser.parse(serialized, baseStyle());

        assertSegmentsEqual(parsed, reParsed);
    }

    @Test
    public void shouldParseLetterSpacing() {
        List<TextSegment> segments = RichTextTagParser.parse("<spacing=2.5>ab</spacing>尾", baseStyle());

        Assert.assertEquals(2, segments.size());
        Assert.assertEquals(2.5F, segments.get(0).getStyle().getLetterSpacing(), 0.001F);
        Assert.assertEquals(0.0F, segments.get(1).getStyle().getLetterSpacing(), 0.001F);
    }

    @Test
    public void shouldIgnoreBadSpacingAttribute() {
        List<TextSegment> segments = RichTextTagParser.parse("<spacing=abc>x</spacing>", baseStyle());

        Assert.assertEquals(1, segments.size());
        Assert.assertEquals(0.0F, segments.get(0).getStyle().getLetterSpacing(), 0.001F);
    }

    @Test
    public void shouldSerializeSpacingRoundTrip() {
        String text = "前<spacing=2>宽字距</spacing><spacing=-1>紧凑</spacing>尾";
        List<TextSegment> parsed = RichTextTagParser.parse(text, baseStyle());
        String serialized = RichTextTagParser.serialize(parsed, baseStyle());
        List<TextSegment> reParsed = RichTextTagParser.parse(serialized, baseStyle());

        assertSegmentsEqual(parsed, reParsed);
    }

    @Test
    public void shouldParseLinkAndAutoUnderline() {
        List<TextSegment> segments = RichTextTagParser.parse(
                "看<a=https://example.com>链接</a>尾", baseStyle());

        Assert.assertEquals(3, segments.size());
        Assert.assertEquals("看", segments.get(0).getText());
        Assert.assertNull(segments.get(0).getStyle().getLink());
        Assert.assertEquals("链接", segments.get(1).getText());
        Assert.assertEquals("https://example.com", segments.get(1).getStyle().getLink());
        Assert.assertTrue(segments.get(1).getStyle().isUnderline());
        Assert.assertEquals("尾", segments.get(2).getText());
        Assert.assertNull(segments.get(2).getStyle().getLink());
    }

    @Test
    public void shouldParseHtmlStyleHrefForms() {
        Assert.assertEquals("https://a.b/c",
                RichTextTagParser.parse("<a href=https://a.b/c>x</a>", baseStyle())
                        .get(0).getStyle().getLink());
        Assert.assertEquals("https://a.b/c",
                RichTextTagParser.parse("<a href=\"https://a.b/c\">x</a>", baseStyle())
                        .get(0).getStyle().getLink());
    }

    @Test
    public void shouldIgnoreLinkWithoutUrl() {
        List<TextSegment> segments = RichTextTagParser.parse("<a>x</a>", baseStyle());

        Assert.assertEquals(1, segments.size());
        Assert.assertNull(segments.get(0).getStyle().getLink());
        Assert.assertFalse(segments.get(0).getStyle().isUnderline());
    }

    @Test
    public void shouldSerializeLinkRoundTrip() {
        String text = "看<a=https://example.com><b>链接</b></a>尾";
        List<TextSegment> parsed = RichTextTagParser.parse(text, baseStyle());
        String serialized = RichTextTagParser.serialize(parsed, baseStyle());
        List<TextSegment> reParsed = RichTextTagParser.parse(serialized, baseStyle());

        assertSegmentsEqual(parsed, reParsed);
    }

    @Test
    public void shouldSupportSpaceSeparatedValue() {
        List<TextSegment> segments = RichTextTagParser.parse("<color red>x</color>", baseStyle());

        Assert.assertEquals(1, segments.size());
        Assert.assertEquals(RED, segments.get(0).getStyle().getColor());
    }

    @Test
    public void shouldReturnEmptyListForEmptyText() {
        Assert.assertTrue(RichTextTagParser.parse("", baseStyle()).isEmpty());
        Assert.assertTrue(RichTextTagParser.parse(null, baseStyle()).isEmpty());
        Assert.assertEquals("", RichTextTagParser.serialize(null, baseStyle()));
    }

    @Test
    public void shouldSerializeRoundTrip() {
        String text = "前<color=#FF5533>红<b>粗<i>粗斜</i></b>红</color><size=24>大字</size><unknown>尾";
        List<TextSegment> parsed = RichTextTagParser.parse(text, baseStyle());
        String serialized = RichTextTagParser.serialize(parsed, baseStyle());
        List<TextSegment> reParsed = RichTextTagParser.parse(serialized, baseStyle());

        assertSegmentsEqual(parsed, reParsed);
    }

    @Test
    public void shouldCloseAllStylesAtSerializeEnd() {
        TextStyle style = baseStyle();
        style.setColor(RED);
        style.setFontType(FontType.BOLD);
        List<TextSegment> segments = java.util.Collections.singletonList(new TextSegment("x", style));

        String serialized = RichTextTagParser.serialize(segments, baseStyle());

        Assert.assertTrue(serialized.endsWith("</b></color>"));
    }

    @Test
    public void shouldEscapeAngleBracketsInSerialize() {
        List<TextSegment> segments = java.util.Collections.singletonList(
                new TextSegment("a<b", baseStyle()));

        String serialized = RichTextTagParser.serialize(segments, baseStyle());

        Assert.assertEquals("a&lt;b", serialized);
    }

    private static TextStyle baseStyle() {
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        return style;
    }

    private static void assertSegmentsEqual(List<TextSegment> expected, List<TextSegment> actual) {
        Assert.assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            TextSegment expectedSegment = expected.get(index);
            TextSegment actualSegment = actual.get(index);
            Assert.assertEquals(expectedSegment.getText(), actualSegment.getText());
            assertStyleEqual(expectedSegment.getStyle(), actualSegment.getStyle());
        }
    }

    private static void assertStyleEqual(TextStyle expected, TextStyle actual) {
        Assert.assertEquals(expected.getColor(), actual.getColor());
        Assert.assertEquals(expected.isColorExplicit(), actual.isColorExplicit());
        Assert.assertEquals(expected.getFontType(), actual.getFontType());
        Assert.assertEquals(expected.isItalic(), actual.isItalic());
        Assert.assertEquals(expected.isUnderline(), actual.isUnderline());
        Assert.assertEquals(expected.isStrikethrough(), actual.isStrikethrough());
        Assert.assertEquals(expected.getFontSizePx(), actual.getFontSizePx());
        Assert.assertEquals(expected.getMarkColor(), actual.getMarkColor());
        Assert.assertEquals(expected.isSuperscript(), actual.isSuperscript());
        Assert.assertEquals(expected.isSubscript(), actual.isSubscript());
        Assert.assertEquals(expected.getLetterSpacing(), actual.getLetterSpacing(), 0.001F);
        Assert.assertEquals(expected.getLink(), actual.getLink());
    }
}
