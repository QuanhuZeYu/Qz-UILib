package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * {@link TextLayoutService} 现代富文本标签（{@link TextContentMode#RICH_TAGS}）模式测量/裁剪/换行测试。
 */
public class TextLayoutServiceRichModeTest {

    private static final int RED = 0xFFFF0000;

    @Test
    public void shouldMeasureRichTextWithoutTagWidth() {
        TextLayoutService service = createService('A');

        int richWidth = service.getStringWidth("<b>AA</b>", TextContentMode.RICH_TAGS);
        int rawWidth = service.getStringWidth("AA", TextContentMode.UILIB_RAW);

        Assert.assertEquals(rawWidth, richWidth);
    }

    @Test
    public void shouldScaleWidthByExplicitSize() {
        TextLayoutService service = createService('A');
        int baseSize = (int) FontRuntimeSettings.capture().getCharSize();
        String text = "A<size=" + (baseSize * 2) + ">A";

        int richWidth = service.getStringWidth(text, TextContentMode.RICH_TAGS);
        int singleWidth = service.getStringWidth("A", TextContentMode.UILIB_RAW);

        Assert.assertEquals(singleWidth * 3, richWidth);
    }

    @Test
    public void shouldWrapRichTextWithStyleContinuation() {
        TextLayoutService service = createService('A', 'B', 'C', 'D');

        List<String> lines = service.listFormattedStringToWidth("<b>ABCD</b>", 2, TextContentMode.RICH_TAGS);

        Assert.assertEquals(Arrays.asList("<b>AB</b>", "<b>CD</b>"), lines);
        assertLineBold(lines.get(0));
        assertLineBold(lines.get(1));
    }

    @Test
    public void shouldWrapAcrossColorSpanBoundary() {
        TextLayoutService service = createService('A', 'B', 'C', 'D');

        List<String> lines = service.listFormattedStringToWidth("<color=#FF0000>ABCD</color>", 2,
                TextContentMode.RICH_TAGS);

        Assert.assertEquals(2, lines.size());
        for (String line : lines) {
            List<TextSegment> segments = service.parseSegments(line, 0xFFFFFFFF, TextContentMode.RICH_TAGS);
            Assert.assertEquals(1, segments.size());
            Assert.assertEquals(RED, segments.get(0).getStyle().getColor());
        }
    }

    @Test
    public void shouldHonorHardLineBreakWithStyleContinuation() {
        TextLayoutService service = createService('A');

        List<String> lines = service.listFormattedStringToWidth("<b>A<br>B</b>", 100, TextContentMode.RICH_TAGS);

        Assert.assertEquals(Arrays.asList("<b>A</b>", "<b>B</b>"), lines);
    }

    @Test
    public void shouldTrimRichTextForward() {
        TextLayoutService service = createService('A', 'B', 'C', 'D');

        String trimmed = service.trimStringToWidth("<b>ABCD</b>", 2, TextContentMode.RICH_TAGS);

        Assert.assertEquals("<b>AB</b>", trimmed);
    }

    @Test
    public void shouldTrimRichTextFromTail() {
        TextLayoutService service = createService('A', 'B', 'C', 'D');

        String trimmed = service.trimStringToWidth("<b>ABCD</b>", 2, true, TextContentMode.RICH_TAGS);

        Assert.assertEquals("<b>CD</b>", trimmed);
    }

    @Test
    public void shouldTreatNewlineAsZeroWidth() {
        TextLayoutService service = createService('A');

        int newlineWidth = service.getStringWidth("A\nA", TextContentMode.UILIB_RAW);
        int plainWidth = service.getStringWidth("AA", TextContentMode.UILIB_RAW);

        Assert.assertEquals(plainWidth, newlineWidth);
    }

    @Test
    public void shouldMeasureSegmentWidthWithExplicitFontSize() {
        TextLayoutService service = createService('A');
        int baseSize = (int) FontRuntimeSettings.capture().getCharSize();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        style.setFontSizePx(baseSize * 2);
        TextSegment segment = new TextSegment("A", style);

        double width = service.getSegmentWidth(segment);
        double singleWidth = service.getSegmentWidth(new TextSegment("A", plainStyle()));

        Assert.assertEquals(Math.ceil(singleWidth * 2.0D), width, 0.001D);
    }

    @Test
    public void shouldComputeMaxFontSizeLineHeightForRichText() {
        TextLayoutService service = createService('A');
        int baseSize = (int) FontRuntimeSettings.capture().getCharSize();
        TextMeasureStyle richStyle = TextMeasureStyle.fontSizePx(baseSize)
                .withTextContentMode(TextContentMode.RICH_TAGS);

        int mixedLineHeight = service.getLineHeight("A<size=" + (baseSize * 2) + ">B</size>", richStyle);
        int bigLineHeight = service.getLineHeight(new TextMeasureStyle(baseSize * 2,
                TextContentMode.RICH_TAGS, UiFontWeight.NORMAL, UiFontStyle.NORMAL));

        Assert.assertEquals(bigLineHeight, mixedLineHeight);
    }

    @Test
    public void shouldFallBackToPlainLineHeightOutsideRichMode() {
        TextLayoutService service = createService('A');
        int baseSize = (int) FontRuntimeSettings.capture().getCharSize();
        TextMeasureStyle rawStyle = TextMeasureStyle.fontSizePx(baseSize);

        Assert.assertEquals(service.getLineHeight(rawStyle),
                service.getLineHeight("A<size=" + (baseSize * 2) + ">B</size>", rawStyle));
    }

    @Test
    public void shouldWrapMixedSizeLineWithSegmentSizes() {
        TextLayoutService service = createService('A', 'B', 'C');
        int baseSize = (int) FontRuntimeSettings.capture().getCharSize();
        String text = "A<size=" + (baseSize * 2) + ">B</size>C";

        List<String> lines = service.listFormattedStringToWidth(text, 2, TextContentMode.RICH_TAGS);

        // 宽 1 + 2 超 2 → 首行仅 A；B(2) 超宽但行空直接放、独占一行；C(1) 累计 3 超 2 → 断行
        Assert.assertEquals(3, lines.size());
        Assert.assertEquals("A", lines.get(0));
        Assert.assertEquals("<size=" + (baseSize * 2) + ">B</size>", lines.get(1));
        Assert.assertEquals("C", lines.get(2));
    }

    @Test
    public void shouldWrapEnglishWordsAtWordBoundary() {
        TextLayoutService service = createService('h', 'e', 'l', 'o', 'w', 'r', 'd', 'f', ' ');

        List<String> lines = service.listFormattedStringToWidth("hello world foo", 8, TextContentMode.RICH_TAGS);

        Assert.assertEquals(Arrays.asList("hello", "world", "foo"), lines);
    }

    @Test
    public void shouldNotSplitEnglishWordWhenItFits() {
        TextLayoutService service = createService('a', 'b', ' ');

        List<String> lines = service.listFormattedStringToWidth("ab ab", 4, TextContentMode.RICH_TAGS);

        Assert.assertEquals(Arrays.asList("ab", "ab"), lines);
    }

    @Test
    public void shouldHardBreakOverlongEnglishWord() {
        TextLayoutService service = createService('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h');

        List<String> lines = service.listFormattedStringToWidth("abcdefgh", 4, TextContentMode.RICH_TAGS);

        Assert.assertEquals(Arrays.asList("abcd", "efgh"), lines);
    }

    @Test
    public void shouldFoldTrailingAndLeadingSpaces() {
        TextLayoutService service = createService('a', 'b', ' ');

        List<String> lines = service.listFormattedStringToWidth("a  b", 2, TextContentMode.RICH_TAGS);

        Assert.assertEquals(Arrays.asList("a", "b"), lines);
    }

    @Test
    public void shouldWrapCjkPerCharacter() {
        TextLayoutService service = createService('中', '文', 'a', 'b');

        List<String> lines = service.listFormattedStringToWidth("中文ab中文", 3, TextContentMode.RICH_TAGS);

        Assert.assertEquals(Arrays.asList("中文", "ab中", "文"), lines);
    }

    @Test
    public void shouldWordWrapAcrossStyleSpans() {
        TextLayoutService service = createService('h', 'e', 'l', 'o', 'w', 'r', 'd', ' ');

        List<String> lines = service.listFormattedStringToWidth("<b>hello</b> world", 8, TextContentMode.RICH_TAGS);

        Assert.assertEquals(Arrays.asList("<b>hello</b>", "world"), lines);
    }

    @Test
    public void shouldKeepExplicitEmptyLines() {
        TextLayoutService service = createService('A');

        List<String> lines = service.listFormattedStringToWidth("<b>A<br><br>B</b>", 100, TextContentMode.RICH_TAGS);

        Assert.assertEquals(Arrays.asList("<b>A</b>", "", "<b>B</b>"), lines);
    }

    private static TextStyle plainStyle() {
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        return style;
    }

    private static void assertLineBold(String line) {
        Assert.assertTrue(line.startsWith("<b>"));
        Assert.assertTrue(line.endsWith("</b>"));
    }

    private static TextLayoutService createService(int... fixedWidthCodepoints) {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        GlyphPageManager glyphPageManager = new GlyphPageManager();
        float[] normalWidths = glyphPageManager.getRuntimeTables().widthArray(FontType.NORMAL);
        float[] boldWidths = glyphPageManager.getRuntimeTables().widthArray(FontType.BOLD);
        for (int codepoint : fixedWidthCodepoints) {
            normalWidths[codepoint] = 1.0F;
            boldWidths[codepoint] = 1.0F;
        }
        TextLayoutService service = new TextLayoutService(new FontMatcher(fontCatalog, derivedFontCache),
                glyphPageManager, derivedFontCache);
        service.setRuntimeVersion(1);
        return service;
    }
}
