package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.internal.LatexFontSize;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.latex.layout.MathMetrics;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.render.GlyphBatchCollector;
import club.heiqi.uilib.font.render.GlyphCollector;
import club.heiqi.uilib.ui.text.TextContentMode;

/** 复用真字形装配及生产 collector 管线，锁住字号和无字形公式推进。 */
public class LatexSizeAndAdvanceRegressionTest {

    @AfterClass
    public static void release() {
        LatexSoftwareRenderKit.resetShared();
    }

    @Test
    public void scriptAndNestedScriptUseMeasuredTruncatedSize() {
        assertSizes("<latex>x^{23}</latex>", 14, 1.0F, 14, 9, 9);
        assertSizes("<latex>x^{y^2}</latex>", 10, 1.0F, 10, 7, 4);
        assertSizes("<latex>x^{y^{z^2}}</latex>", 14, 1.25F, 14, 9, 6, 4);
        assertSizes("<latex>x</latex>", 14, 1.25F, 14); // uniform 快路径
        assertSizes("<latex>x^{y^{z^{a^{b^2}}}}</latex>", 1, 1.0F, 1, 1, 1, 1, 1, 1);
    }

    @Test
    public void integerRoundTripsAndAllMathMetricsShareSizePolicy() {
        float recovered = 7.0F * (31.0F / 7.0F);
        Assert.assertTrue("确实触发 float 往返下溢整数", recovered < 31.0F);
        Assert.assertEquals(31, LatexFontSize.effective(recovered));
        Assert.assertEquals(9, LatexFontSize.effective(9.8F));
        Assert.assertEquals(4, LatexFontSize.effective(4.9F));
        Assert.assertEquals(30, LatexFontSize.effective(30.999F));
        LatexSoftwareRenderKit.render("x", 14);
        TextStyle style = new TextStyle();
        style.resetAll(0xFFFFFFFF);
        MathMetrics metrics = LatexSoftwareRenderKit.currentService().createMathMetrics(style, 7);
        Assert.assertEquals(metrics.advance("x", 31), metrics.advance("x", recovered), 0.0F);
        Assert.assertEquals(metrics.ascent(31), metrics.ascent(recovered), 0.0F);
        Assert.assertEquals(metrics.descent(31), metrics.descent(recovered), 0.0F);
        Assert.assertEquals(metrics.xHeight(31), metrics.xHeight(recovered), 0.0F);
        Assert.assertEquals(metrics.inkWidth("x", 31), metrics.inkWidth("x", recovered), 0.0F);
        Assert.assertEquals(metrics.inkHeight("x", 31), metrics.inkHeight("x", recovered), 0.0F);
        Assert.assertEquals(metrics.inkLeftBearing("x", 31), metrics.inkLeftBearing("x", recovered), 0.0F);
        Assert.assertEquals(metrics.inkCenterOffsetY("x", 31), metrics.inkCenterOffsetY("x", recovered), 0.0F);
        Assert.assertEquals(metrics.italicCorrection("x", 31), metrics.italicCorrection("x", recovered), 0.0F);
        Assert.assertEquals(metrics.italicOverhang("x", 31), metrics.italicOverhang("x", recovered), 0.0F);
    }

    @Test
    public void explicitSegmentSizeDoesNotOverrideScriptAdvanceAgain() {
        Recorder explicit = render("<size=14><latex>x^{23}</latex></size>", 10, 1.25F);
        Recorder implicit = render("<latex>x^{23}</latex>", 14, 1.25F);
        Assert.assertEquals(implicit.end, explicit.end);
        Assert.assertEquals(implicit.glyphs.size(), explicit.glyphs.size());
        for (int i = 0; i < implicit.glyphs.size(); i++) {
            Assert.assertArrayEquals(implicit.glyphs.get(i), explicit.glyphs.get(i), 0.0001F);
        }
    }

    @Test
    public void glyphlessSpacingSurvivesEverySegmentBoundaryAndScale() {
        for (String source : new String[] {"\\quad", "\\!"}) {
            String gap = latex(source);
            for (float scale : new float[] {1.0F, 1.25F, 0.75F}) {
                for (String text : new String[] {gap + "A", "A" + gap + "B", "AB" + gap,
                        gap, gap + gap, gap + gap + "A", "A" + gap + gap + "B"}) {
                    Recorder result = render(text, 14, scale);
                    float expectedX = 4.0F;
                    int glyph = 0;
                    for (TextSegment segment : result.segments) {
                        float width = (float) LatexSoftwareRenderKit.currentService().getSegmentWidth(segment, 14);
                        if (!segment.isLatex()) {
                            for (int cp : segment.getText().codePoints().toArray()) {
                                Assert.assertEquals(text, expectedX, result.glyphs.get(glyph++)[0], 0.0001F);
                                expectedX += (float) LatexSoftwareRenderKit.currentService()
                                        .resolveAdvance(cp, segment.getStyle(), 14) * scale;
                            }
                        } else {
                            expectedX += width * scale;
                        }
                    }
                    Assert.assertEquals("不能制造占位字形：" + text, glyph, result.glyphs.size());
                    Assert.assertEquals("必须返回包括尾段的终点：" + text,
                            (int) Math.ceil(expectedX), result.end);
                }
            }
        }
    }

    @Test
    public void spacingMovesFollowingFormulaGlyphsAndRulesTogether() {
        for (String source : new String[] {"\\quad", "\\!"}) {
            for (float scale : new float[] {1.0F, 1.25F}) {
                Recorder plain = render("A" + latex("\\frac{1}{2}"), 14, scale);
                Recorder spaced = render("A" + latex(source) + latex(source) + latex("\\frac{1}{2}"), 14, scale);
                float delta = LatexSoftwareRenderKit.layout(source, 14).getWidth() * scale * 2;
                Assert.assertFalse(plain.rules.isEmpty());
                for (int i = 1; i < plain.glyphs.size(); i++) {
                    Assert.assertEquals(plain.glyphs.get(i)[0] + delta, spaced.glyphs.get(i)[0], 0.0001F);
                }
                for (int i = 0; i < plain.rules.size(); i++) {
                    Assert.assertEquals(plain.rules.get(i)[0] + delta, spaced.rules.get(i)[0], 0.0001F);
                    Assert.assertEquals(plain.rules.get(i)[1], spaced.rules.get(i)[1], 0.0F);
                }
            }
        }
    }

    @Test
    public void ruleOnlyFormulaKeepsWidthAndUsesItsOwnAscent() {
        String source = "\\frac{\\quad}{\\quad}";
        MathBox box = LatexSoftwareRenderKit.layout(source, 14);
        Assert.assertTrue(box.getGlyphs().isEmpty());
        Recorder result = render(latex(source), 14, 1.25F);
        Assert.assertTrue(result.glyphs.isEmpty());
        Assert.assertFalse(result.batch.getDecorationBatch().isEmpty());
        Assert.assertEquals((int) Math.ceil(4 + box.getWidth() * 1.25F), result.end);
        // 前置无字形间距既不改变行居中，也不能让规则线丢失。
        Recorder shifted = render(latex("\\quad") + latex(source), 14, 1.25F);
        Assert.assertEquals(result.rules.get(0)[0] + 14 * 1.25F, shifted.rules.get(0)[0], 0.0001F);
        Assert.assertEquals(result.rules.get(0)[1], shifted.rules.get(0)[1], 0.0F);
        Recorder mixed = render("A" + latex(source), 14, 1.25F);
        float center = 4 + LatexSoftwareRenderKit.currentService().getAscent(14) * 1.25F
                + box.getRules().get(0).getY() * 1.25F;
        float thickness = Math.max(1, Math.round(box.getRules().get(0).getThickness() * 1.25F));
        Assert.assertEquals(Math.round(center - thickness / 2), mixed.rules.get(0)[1], 0.0F);
    }

    @Test
    public void emptyTextFormulaMeasuresAndRendersWithoutGlyphOrAdvance() {
        LatexSoftwareRenderKit.RenderResult result = LatexSoftwareRenderKit.render(latex("\\text{}"), 14);
        Assert.assertEquals("testkit 起点为 4，advance 为零", 4, result.advanceWidth);
        Assert.assertEquals(0, result.collector.getActivePageCount());
        Assert.assertTrue(result.collector.getDecorationBatch().isEmpty());
        Assert.assertEquals(0.0F, LatexSoftwareRenderKit.layout("\\text{}", 14).getWidth(), 0.0F);
    }

    private static void assertSizes(String text, int baseSize, float scale, int... expected) {
        Recorder result = render(text, baseSize, scale);
        Assert.assertEquals(text, expected.length, result.glyphs.size());
        Assert.assertTrue("真字形必须进入既有批次", result.batch.getActivePageCount() > 0);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(text + " glyph=" + i, expected[i] * scale, result.glyphs.get(i)[2], 0.0F);
        }
    }

    private static String latex(String source) {
        return "<latex>" + source + "</latex>";
    }

    private static Recorder render(String text, int size, float scale) {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        Recorder result = new Recorder();
        result.segments = shared.service.parseSegments(text, 0xFFFFFFFF, TextContentMode.RICH_TAGS);
        LatexSoftwareRenderKit.assembleGlyphs(shared, result.segments);
        result.end = DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(result.segments,
                shared.settings, shared.service, GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, 1),
                4, 4, false, scale, size, result);
        return result;
    }

    /** 仅旁录参数，所有几何继续委托生产批次，不替换字体或布局实现。 */
    private static final class Recorder implements GlyphCollector {
        final GlyphBatchCollector batch = new GlyphBatchCollector();
        final List<float[]> glyphs = new ArrayList<float[]>();
        final List<float[]> rules = new ArrayList<float[]>();
        List<TextSegment> segments;
        int end;

        @Override
        public void collectBaselineAlignedGlyph(FontType fontType, int pageIndex, int textureId, int textureSize,
                int slotX, int slotY, int slotWidth, int slotHeight, int atlasBaselineX, int atlasBaselineY,
                int lineBaselineY, int defaultGlyphSize, int inkWidth, int inkHeight, int bearingX, int bearingY,
                float x, float y, float charSize, int color, boolean italic, byte glyphFlags, float baseCharSize) {
            glyphs.add(new float[] {x, y, charSize});
            batch.collectBaselineAlignedGlyph(fontType, pageIndex, textureId, textureSize, slotX, slotY,
                    slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, defaultGlyphSize,
                    inkWidth, inkHeight, bearingX, bearingY, x, y, charSize, color, italic, glyphFlags, baseCharSize);
        }

        @Override
        public void collectDecoration(float x, float y, float width, float height, int color) {
            rules.add(new float[] {x, y, width, height});
            batch.collectDecoration(x, y, width, height, color);
        }

        @Override
        public void collectMarkBackground(float x, float y, float width, float height, int color) {
            batch.collectMarkBackground(x, y, width, height, color);
        }
    }
}
