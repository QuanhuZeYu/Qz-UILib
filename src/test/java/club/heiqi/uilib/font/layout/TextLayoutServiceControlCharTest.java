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

    private static TextLayoutService createService(int... fixedWidthCodepoints) {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        GlyphPageManager glyphPageManager = new GlyphPageManager();
        float[] normalWidths = glyphPageManager.getRuntimeTables().widthArray(FontType.NORMAL);
        for (int codepoint : fixedWidthCodepoints) {
            normalWidths[codepoint] = 1.0F;
        }
        TextLayoutService service = new TextLayoutService(new FontMatcher(fontCatalog, derivedFontCache),
                glyphPageManager, derivedFontCache);
        service.setRuntimeVersion(1);
        return service;
    }
}
