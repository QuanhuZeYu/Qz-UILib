package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * {@link TextLayoutService} 控制字符统一口径测试（UnicodeTextClassifier 接入验证）。
 *
 * <p>基建同 {@link TextLayoutServiceTextContentModeTest}：Dialog 字体 + 宽度表预置固定码点宽 1.0，
 * wrap/trim/prefix 断言与绝对字号无关（缩放因子恒 1）。</p>
 */
public class TextLayoutServiceControlCharTest {

    @Test
    public void shouldMeasureTabAsFourSpacesInRawMode() {
        TextLayoutService service = createService();
        int tabWidth = service.getStringWidth("\t", TextContentMode.UILIB_RAW);
        int fourSpaces = service.getStringWidth("    ", TextContentMode.UILIB_RAW);
        Assert.assertTrue(tabWidth > 0);
        Assert.assertEquals(fourSpaces, tabWidth);
    }

    @Test
    public void shouldMeasureZeroWidthControlsAsZeroEvenWhenCached() {
        // 宽度表预置 1.0 后分类拦截仍返回 0（拦截优先于 cache）
        int[] zeroWidth = { 0x0000, 0x0007, 0x001F, 0x007F, 0x0080, 0x009F, 0x00AD, 0x061C,
                0x200B, 0x200C, 0x200D, 0x200E, 0x202A, 0x2060, 0x2066, 0x2069, 0xFEFF, 0xFE0F };
        TextLayoutService service = createService(zeroWidth);
        for (int codepoint : zeroWidth) {
            Assert.assertEquals("U+" + Integer.toHexString(codepoint) + " 应为 0 宽", 0,
                    service.getStringWidth(new String(Character.toChars(codepoint)),
                            TextContentMode.UILIB_RAW));
        }
    }

    @Test
    public void shouldWrapOnUnicodeNewlineFamilyInRawMode() {
        TextLayoutService service = createService('a', 'b');
        String[] separators = { "\u000B", "\f", "\u0085", "\u2028", "\u2029" };
        for (String separator : separators) {
            List<String> lines = service.listFormattedStringToWidth("a" + separator + "b", 1000,
                    TextContentMode.UILIB_RAW);
            Assert.assertEquals("分隔符 U+" + Integer.toHexString(separator.codePointAt(0)),
                    Arrays.asList("a", "b"), lines);
        }
    }

    @Test
    public void shouldFoldCrLfAsSingleBreakInRawMode() {
        TextLayoutService service = createService('a', 'b');
        List<String> lines = service.listFormattedStringToWidth("a\r\nb", 1000, TextContentMode.UILIB_RAW);
        Assert.assertEquals(Arrays.asList("a", "b"), lines);
    }

    @Test
    public void shouldBreakAtZwspInsteadOfHardBreakInRawMode() {
        TextLayoutService service = createService('a', 'b', 'c', 'd');
        List<String> zwsp = service.listFormattedStringToWidth("ab\u200Bcd", 3, TextContentMode.UILIB_RAW);
        List<String> hard = service.listFormattedStringToWidth("abcd", 3, TextContentMode.UILIB_RAW);
        Assert.assertEquals(Arrays.asList("ab", "cd"), zwsp);
        Assert.assertEquals(Arrays.asList("abc", "d"), hard);
    }

    @Test
    public void shouldAppendHyphenWhenBreakingAtSoftHyphenInRawMode() {
        TextLayoutService service = createService('a', 'b', 'c', 'd', '-');
        List<String> lines = service.listFormattedStringToWidth("ab\u00ADcd", 3, TextContentMode.UILIB_RAW);
        Assert.assertEquals(Arrays.asList("ab-", "cd"), lines);
    }

    @Test
    public void shouldBreakAtZwspAndKeepFormatCodesInMinecraftMode() {
        TextLayoutService service = createService('a', 'b', 'c', 'd');
        List<String> lines = service.listFormattedStringToWidth("§aab\u200Bcd", 3,
                TextContentMode.MINECRAFT_FORMATTED);
        Assert.assertEquals(Arrays.asList("§aab", "§acd"), lines);
    }

    @Test
    public void shouldWrapOnUnicodeNewlineFamilyInMinecraftMode() {
        TextLayoutService service = createService('a', 'b');
        List<String> lines = service.listFormattedStringToWidth("§aa\u2028b", 1000,
                TextContentMode.MINECRAFT_FORMATTED);
        Assert.assertEquals(Arrays.asList("§aa", "§ab"), lines);
    }

    @Test
    public void shouldBreakAtZwspInRichMode() {
        TextLayoutService service = createService('a', 'b', 'c', 'd');
        List<String> lines = service.listFormattedStringToWidth("ab\u200Bcd", 3, TextContentMode.RICH_TAGS);
        Assert.assertEquals(Arrays.asList("ab", "cd"), lines);
    }

    @Test
    public void shouldAppendHyphenWhenBreakingAtSoftHyphenInRichMode() {
        TextLayoutService service = createService('a', 'b', 'c', 'd', '-');
        List<String> lines = service.listFormattedStringToWidth("ab\u00ADcd", 3, TextContentMode.RICH_TAGS);
        Assert.assertEquals(Arrays.asList("ab-", "cd"), lines);
    }

    @Test
    public void shouldFoldTrailingFoldableSpacesInRichMode() {
        TextLayoutService service = createService('a');
        List<String> lines = service.listFormattedStringToWidth("a\u00A0", 1000, TextContentMode.RICH_TAGS);
        Assert.assertEquals(Arrays.asList("a"), lines);
    }

    @Test
    public void shouldKeepVariationSelectorWithPrecedingCharInRichMode() {
        // VS 并入前字符 token：断行后 VS 不落行首
        TextLayoutService service = createService('a', 'b');
        List<String> lines = service.listFormattedStringToWidth("a\uFE0Fb", 1, TextContentMode.RICH_TAGS);
        Assert.assertEquals(Arrays.asList("a\uFE0F", "b"), lines);
    }

    @Test
    public void shouldWrapOnUnicodeNewlineFamilyInRichMode() {
        TextLayoutService service = createService('a', 'b');
        List<String> lines = service.listFormattedStringToWidth("a\u2028b", 1000, TextContentMode.RICH_TAGS);
        Assert.assertEquals(Arrays.asList("a", "b"), lines);
    }

    @Test
    public void shouldKeepZeroWidthCharsWhenTrimmingRaw() {
        TextLayoutService service = createService('a', 'b', 'c', 'd');
        String trimmed = service.trimStringToWidth("ab\u200Bcd", 2, TextContentMode.UILIB_RAW);
        Assert.assertEquals("ab\u200B", trimmed);
    }

    @Test
    public void shouldKeepZeroWidthPrefixesInRawPrefixWidths() {
        TextLayoutService service = createService('a', 'b');
        int[] widths = service.prefixWidthsRaw("a\u200Bb", UiFontWeight.NORMAL, UiFontStyle.NORMAL);
        Assert.assertArrayEquals(new int[] { 0, 1, 1, 2 }, widths);
    }

    // ==================== NFC 规范化（组合附加符挡 1） ====================

    @Test
    public void shouldNormalizeCombiningSequenceToPrecomposed() {
        TextLayoutService service = createService();
        // NFC：e + U+0301（组合尖音符）→ é（U+00E9 预组合）
        List<TextSegment> raw = service.parseSegments("e\u0301", 0xFFFFFFFF, TextContentMode.UILIB_RAW);
        Assert.assertEquals(1, raw.size());
        Assert.assertEquals("\u00E9", raw.get(0).getText());

        List<TextSegment> rich = service.parseSegments("e\u0301", 0xFFFFFFFF, TextContentMode.RICH_TAGS);
        Assert.assertEquals(1, rich.size());
        Assert.assertEquals("\u00E9", rich.get(0).getText());
    }

    @Test
    public void shouldNormalizeWrapAndTrimInputs() {
        TextLayoutService service = createService();
        List<String> lines = service.listFormattedStringToWidth("e\u0301", 1000, TextContentMode.UILIB_RAW);
        Assert.assertEquals(Arrays.asList("\u00E9"), lines);
    }

    @Test
    public void shouldKeepPrefixWidthsFaithfulWithoutNfc() {
        // prefixWidthsRaw 刻意不规范化：码点下标保真（caret 几何不受显示层 NFC 影响）；
        // 组合标记零宽（簇延续），前缀向量在 mark 边界不推进。
        TextLayoutService service = createService('e');
        int[] widths = service.prefixWidthsRaw("e\u0301", UiFontWeight.NORMAL, UiFontStyle.NORMAL);
        Assert.assertEquals(3, widths.length);
        Assert.assertEquals(widths[1], widths[2]);
    }

    @Test
    public void shouldResolveMarkPositionsForCombiningSequence() {
        // Dialog 字体链支持组合尖音符：GPOS 定位把 mark 放到基字上方（y < 0）
        TextLayoutService service = createService();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        float[] positions = service.resolveMarkPositions("e\u0301", style, 16);
        Assert.assertNotNull("resolveMarkPositions 返回 null（字体不可用）", positions);
        Assert.assertEquals("positions 长度应为 2×码点数: " + java.util.Arrays.toString(positions),
                4, positions.length);
        Assert.assertTrue("组合标记应定位在基线上方（y<0）: " + java.util.Arrays.toString(positions),
                positions[3] < 0.0F);
    }

    @Test
    public void shouldStackMultipleMarksUpward() {
        // 三层组合标记：逐层上摞（y 越来越负，金字塔形态）
        TextLayoutService service = createService();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        float[] positions = service.resolveMarkPositions("a\u0301\u0300\u0308", style, 16);
        Assert.assertNotNull(positions);
        Assert.assertEquals(8, positions.length);
        Assert.assertTrue("第一层 mark 应在基线上方: " + positions[3], positions[3] < 0.0F);
        Assert.assertTrue("第二层应高于第一层: " + positions[3] + " vs " + positions[5],
                positions[5] < positions[3]);
        Assert.assertTrue("第三层应高于第二层: " + positions[5] + " vs " + positions[7],
                positions[7] < positions[5]);
    }

    @Test
    public void shouldStackAboveAndBelowMarksInOppositeDirections() {
        // U+06D6 阿拉伯高位标记（CCC 230）向上；U+06E3 阿拉伯低位标记（CCC 220）向下
        TextLayoutService service = createService();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        float[] positions = service.resolveMarkPositions("a\u06D6\u06E3", style, 16);
        Assert.assertNotNull(positions);
        Assert.assertEquals(6, positions.length);
        Assert.assertTrue("高位标记应向上（y<0）: " + positions[3], positions[3] < 0.0F);
        Assert.assertTrue("低位标记应向下（y>0）: " + positions[5], positions[5] > 0.0F);
    }

    @Test
    public void shouldResolvePositionsForAllMarksLine() {
        // 全标记行（无常规字符锚）：以空格为字体锚，堆叠仍可用
        TextLayoutService service = createService();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        float[] positions = service.resolveMarkPositions("\u06D6\u06E3\u0E34", style, 16);
        Assert.assertNotNull(positions);
        Assert.assertEquals(6, positions.length);
    }

    @Test
    public void shouldPreserveWaterFloodTextThroughWrap() {
        // 贴吧灌水文本（泰语/阿拉伯组合标记堆叠在空格上）：wrap 不丢码点、行结构正确
        String water = "\u0E34\u06D6\u0E34\u06E3 \u06E3\u06E3\u06D6\u06D6\u06D6\u06D6\u0E34\u06D6\u0E34\u0E34\u06E3\u06E3\u06D6\u06D6\u0E34 "
                + "\u06D6\u0E34\u0E34\u06E3\u06E3\u06D6\u06D6\u0E34\u06E3 \u06E3\u06E3\u06D6\u06D6\u06D6\u0E34\u06D6\u0E34\u0E34 "
                + "\u06E3\u06E3\u06D6\u06D6 \u06D6 \u06E3\u06E3\u06D6\u06D6\u0E34 \u06D6\u0E34\u0E34\u06E3\u06E3\u06D6\u06D6\u0E34\u06E3 \u06D6\n"
                + "\u06E3\u06E3\u06D6\u06D6\u06D6\u0E34\u06D6\u0E34\u0E34 \u06E3\u06E3\u06D6\u06D6 \u06D6 \u06E3\u06E3\u06D6\u06D6\u0E34 "
                + "\u6C34\u5427\u3002";
        TextLayoutService service = createService();
        List<String> lines = service.listFormattedStringToWidth(water, 1000, TextContentMode.UILIB_RAW);
        Assert.assertEquals(2, lines.size());
        int total = 0;
        for (String line : lines) {
            total += line.codePointCount(0, line.length());
        }
        // 1 个换行码点被折叠（\n 不占行内容），其余码点全保留
        Assert.assertEquals(water.codePointCount(0, water.length()) - 1, total);
    }

    @Test
    public void shouldResolveNullForTextWithoutClusterChars() {
        TextLayoutService service = createService();
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        Assert.assertNull(service.resolveMarkPositions("abc", style, 16));
        Assert.assertNull(service.resolveMarkPositions("", style, 16));
    }

    @Test
    public void shouldNotNormalizeAlreadyComposedText() {
        TextLayoutService service = createService();
        List<TextSegment> segments = service.parseSegments("\u00E9", 0xFFFFFFFF, TextContentMode.UILIB_RAW);
        Assert.assertEquals("\u00E9", segments.get(0).getText());
    }

    private static TextLayoutService createService(int... fixedWidthCodepoints) {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        GlyphPageManager glyphPageManager = new GlyphPageManager();
        float[] normalWidths = glyphPageManager.getRuntimeTables().widthArray(FontType.NORMAL);
        for (int codepoint : fixedWidthCodepoints) {
            normalWidths[codepoint] = 1.0F;
        }
        FontMatcher fontMatcher = new FontMatcher(fontCatalog, derivedFontCache);
        // 绑定 runtime version 1 与 runtimeTables，使 matchFontIndex/getDerivedFont 可用
        // （resolveMarkPositions 的 AWT mark 定位路径依赖真实字体匹配）。
        fontMatcher.setRuntimeTables(1, glyphPageManager.getRuntimeTables());
        TextLayoutService service = new TextLayoutService(fontMatcher, glyphPageManager, derivedFontCache);
        service.setRuntimeVersion(1);
        return service;
    }
}
