package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.MathStyleOverride;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;

/** 根数学样式是片段元信息；富文本往返与裁剪不得将其改写成 TeX 命令。 */
public class RichTextLatexMathStyleTest {

    private static final MathStyleOverride[] STYLES = { MathStyleOverride.DISPLAY, MathStyleOverride.TEXT,
            MathStyleOverride.SCRIPT, MathStyleOverride.SCRIPTSCRIPT };

    @Test
    public void parsesAndSerializesEveryRootStyleWithoutChangingSource() {
        String source = " \\frac{a}{b} <>& </latex>\r\n\n x  ";
        for (MathStyleOverride mathStyle : STYLES) {
            TextStyle style = baseStyle();
            style.setFontSizePx(24);
            style.setFontType(FontType.BOLD);
            style.setItalic(true);
            style.setColor(0xFF123456);
            TextSegment original = TextSegment.forLatex(source, style, mathStyle);
            String serialized = RichTextTagParser.serialize(Collections.singletonList(original), null);
            Assert.assertTrue(serialized.contains("&lt;&gt;&amp;"));
            Assert.assertFalse(serialized.contains("\\displaystyle"));
            TextSegment copy = onlyLatex(serialized);
            assertFormula(original, copy);
            Assert.assertNotSame(style, copy.getStyle());
            copy.getStyle().setFontSizePx(10);
            Assert.assertEquals(24, original.getStyle().getFontSizePx());
        }
    }

    @Test
    public void acceptsExistingTagAttributeSpacingAndQuoting() {
        for (MathStyleOverride style : STYLES) {
            String value = style.name().toLowerCase(Locale.ROOT);
            for (String attribute : Arrays.asList("math-style=\"" + value + "\"",
                    "math-style = " + value, "MATH-STYLE = '" + value.toUpperCase(Locale.ROOT) + "'")) {
                Assert.assertEquals(style, onlyLatex("<latex " + attribute + ">x</latex>").getLatexMathStyle());
            }
        }
    }

    @Test
    public void defaultsMissingUnknownAndMalformedAttributesToText() {
        for (String attribute : Arrays.asList("", " math-style=\"unknown\"", " math-style=\"\"",
                " math-style=inherit", " math-style-display", " other=display", " math-style-extra=display")) {
            TextSegment segment = onlyLatex("<latex" + attribute + ">x</latex>");
            Assert.assertEquals(MathStyleOverride.TEXT, segment.getLatexMathStyle());
            Assert.assertEquals("<latex>x</latex>",
                    RichTextTagParser.serialize(Collections.singletonList(segment), null));
        }
    }

    @Test
    public void adjacentFormulaAndTextTagsKeepOriginalStyleCopies() {
        String input = "<color=red><b>a</b></color><b><latex math-style=display>x</latex>"
                + "</b><size=24><latex math-style=script>y</latex></size><i>b</i>";
        List<TextSegment> original = RichTextTagParser.parse(input, null);
        List<TextSegment> copy = RichTextTagParser.parse(RichTextTagParser.serialize(original, null), null);
        Assert.assertEquals(original.size(), copy.size());
        for (int index = 0; index < original.size(); index++) {
            Assert.assertEquals(original.get(index).getText(), copy.get(index).getText());
            Assert.assertEquals(original.get(index).isLatex(), copy.get(index).isLatex());
            assertFormula(original.get(index), copy.get(index));
            Assert.assertNotSame(original.get(index).getStyle(), copy.get(index).getStyle());
        }
    }

    @Test
    public void trimKeepsRootStyleAndFontSizeOrDropsWholeFormula() {
        TextLayoutService service = createService();
        RichTextContentStrategy strategy = new RichTextContentStrategy(service);
        for (MathStyleOverride style : STYLES) {
            String input = tagged(style, "x^2");
            TextSegment original = onlyLatex(input);
            int fits = (int) Math.ceil(service.getSegmentWidth(original, 16));
            assertFormula(original, onlyLatex(strategy.trim(input, fits, baseStyle(), 16)));
            Assert.assertEquals("", strategy.trim(input, 0, baseStyle(), 16));
        }
    }

    @Test
    public void wrapPreservesAtomicFormulaNewlinesAndAdjacentPlainText() {
        RichTextContentStrategy strategy = new RichTextContentStrategy(createService());
        for (MathStyleOverride style : STYLES) {
            String formula = tagged(style, " x^2\r\n x  ");
            TextSegment original = onlyLatex(formula);
            String wide = strategy.wrap("a" + formula + "b", Integer.MAX_VALUE, baseStyle(), 16);
            List<TextSegment> wideSegments = RichTextTagParser.parse(wide, null);
            Assert.assertEquals(3, wideSegments.size());
            Assert.assertEquals("a", wideSegments.get(0).getText());
            assertFormula(original, wideSegments.get(1));
            Assert.assertEquals("b", wideSegments.get(2).getText());
            String narrow = strategy.wrap("a\n" + formula + "b", 1, baseStyle(), 16);
            assertFormula(original, onlyLatex(narrow));
            Assert.assertTrue(narrow.contains("\n"));
        }
        // 同样式尾随正文也不可合并进公式段。
        String sameStyle = strategy.wrap("<latex math-style=display>x</latex>b",
                Integer.MAX_VALUE, baseStyle(), 16);
        Assert.assertEquals(2, RichTextTagParser.parse(sameStyle, null).size());
        Assert.assertEquals("x", onlyLatex(sameStyle).getLatexSource());
    }

    @Test
    public void plainTextRetainsItsExistingNewlineAndSerializationSemantics() {
        List<TextSegment> plain = RichTextTagParser.parse("<b>a\r\nb&lt;&amp;</b>", null);
        Assert.assertEquals("a\nb<&", plain.get(0).getText());
        Assert.assertEquals(MathStyleOverride.INHERIT, plain.get(0).getLatexMathStyle());
        Assert.assertEquals("<b>a\nb&lt;&amp;</b>", RichTextTagParser.serialize(plain, null));
    }

    private static String tagged(MathStyleOverride style, String source) {
        return "<size=24><latex math-style=\"" + style.name().toLowerCase(Locale.ROOT)
                + "\">" + source + "</latex></size>";
    }

    private static TextSegment onlyLatex(String text) {
        TextSegment result = null;
        for (TextSegment segment : RichTextTagParser.parse(text, null)) {
            if (segment.isLatex()) {
                Assert.assertNull("应只有一个完整公式", result);
                result = segment;
            }
        }
        Assert.assertNotNull("公式不得消失", result);
        return result;
    }

    private static void assertFormula(TextSegment original, TextSegment copy) {
        Assert.assertEquals(original.getLatexSource(), copy.getLatexSource());
        Assert.assertEquals(original.getLatexMathStyle(), copy.getLatexMathStyle());
        Assert.assertEquals(original.getStyle().getFontSizePx(), copy.getStyle().getFontSizePx());
        Assert.assertEquals(original.getStyle().getFontType(), copy.getStyle().getFontType());
        Assert.assertEquals(original.getStyle().getColor(), copy.getStyle().getColor());
        Assert.assertEquals(original.getStyle().isColorExplicit(), copy.getStyle().isColorExplicit());
        Assert.assertEquals(original.getStyle().isItalic(), copy.getStyle().isItalic());
    }

    private static TextStyle baseStyle() {
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        return style;
    }

    private static TextLayoutService createService() {
        FontCatalog catalog = new FontCatalog();
        catalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derived = new DerivedFontCache(catalog);
        GlyphPageManager pages = new GlyphPageManager();
        float[] widths = pages.getRuntimeTables().widthArray(FontType.NORMAL);
        for (char ch : "abx2".toCharArray()) {
            widths[ch] = 8.0F;
        }
        FontMatcher matcher = new FontMatcher(catalog, derived);
        matcher.setRuntimeTables(1, pages.getRuntimeTables());
        TextLayoutService service = new TextLayoutService(matcher, pages, derived);
        service.setRuntimeVersion(1);
        return service;
    }
}
