package club.heiqi.uilib.font.latex.layout;

import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.MathStyleOverride;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/** 同一源码的根数学样式隔离，同时旧入口和 INHERIT 共享 TEXT。 */
public class LatexRootStyleCacheTest {
    private static final MathMetrics METRICS = new MathMetrics() {
        public float advance(String text, float size) { return text.length() * size / 2; }
        public float ascent(float size) { return size; }
        public float descent(float size) { return size / 4; }
        public float xHeight(float size) { return size / 2; }
    };

    @Test
    public void rootStylesHaveSeparateGeometryAndStableCacheHits() {
        LatexCache cache = LatexCache.getInstance();
        MathLayoutService layout = new MathLayoutService();
        String source = "\\frac{a}{b}\\operatorname{rootcache}";
        MathBox text = cache.getOrLayout(source, 16, 876543, FontType.NORMAL, layout, METRICS, 0);
        MathBox display = cache.getOrLayout(source, 16, 876543, FontType.NORMAL, layout, METRICS, 0,
                MathStyleOverride.DISPLAY);
        Assert.assertNotSame(text, display);
        Assert.assertTrue(display.getHeight() + display.getDepth() > text.getHeight() + text.getDepth());
        Assert.assertSame(text, cache.getOrLayout(source, 16, 876543, FontType.NORMAL, layout, METRICS, 0,
                MathStyleOverride.INHERIT));
        Assert.assertSame(display, cache.getOrLayout(source, 16, 876543, FontType.NORMAL, layout, METRICS, 0,
                MathStyleOverride.DISPLAY));
        Assert.assertNotSame(display, cache.getOrLayout(source, 16, 876543, FontType.NORMAL, layout, METRICS, 1,
                MathStyleOverride.DISPLAY));
    }

    @Test
    public void styleCopiesPreserveSourceAndRootSelection() {
        TextStyle initial = new TextStyle();
        TextSegment segment = TextSegment.forLatex(" x \n+y ", initial, MathStyleOverride.DISPLAY);
        TextStyle replacement = new TextStyle();
        TextSegment copied = segment.withStyle(replacement);
        Assert.assertEquals(segment.getLatexSource(), copied.getLatexSource());
        Assert.assertEquals(MathStyleOverride.DISPLAY, copied.getLatexMathStyle());
        Assert.assertSame(replacement, copied.getStyle());
        Assert.assertSame(initial, segment.getStyle());
        Assert.assertEquals(MathStyleOverride.TEXT, TextSegment.forLatex("x", initial).getLatexMathStyle());
        Assert.assertEquals(MathStyleOverride.INHERIT, new TextSegment("x", initial).getLatexMathStyle());
    }
}
