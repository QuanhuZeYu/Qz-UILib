package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.render.GlyphBatchCollector;
import club.heiqi.uilib.font.render.GlyphCollector;
import club.heiqi.uilib.ui.text.TextContentMode;

/** 显式数学样式必须经真实度量、生产 adapter 和生产批次保持字号与推进一致。 */
public class LatexExplicitStyleRenderingTest {
    static final int[] SIZES = {12, 14, 16, 24};
    static final float[] SCALES = {1, 1.25F, 4, 8};
    // 根字号的 B1 截断级别；工作站 b2a probe 入口用 Python 验证此表。
    private static final int[] SCRIPT = {8, 9, 11, 16};
    private static final int[] SCRIPTSCRIPT = {6, 7, 8, 12};

    @AfterClass
    public static void release() {
        LatexSoftwareRenderKit.resetShared();
    }

    @Test
    public void declarationsResetToRootSizeAndRespectGroupScopeThroughCollector() {
        for (int i = 0; i < SIZES.length; i++) {
            for (float scale : SCALES) {
                assertSizes("a\\scriptstyle b\\scriptscriptstyle c\\textstyle d\\displaystyle e",
                        SIZES[i], scale, SIZES[i], SCRIPT[i], SCRIPTSCRIPT[i], SIZES[i], SIZES[i]);
                assertSizes("{\\scriptstyle a}b", SIZES[i], scale, SCRIPT[i], SIZES[i]);
                assertSizes("x^{\\displaystyle y^z}", SIZES[i], scale, SIZES[i], SIZES[i], SCRIPT[i]);
                assertSizes("\\scriptstyle\\dfrac ab^2", SIZES[i], scale,
                        SIZES[i], SIZES[i], SCRIPTSCRIPT[i]);
            }
        }
    }

    @Test
    public void fractionOverridesReachGlyphsRulesAndAdvanceWithoutDoubleScaling() {
        for (int size : SIZES) {
            for (float scale : SCALES) {
                assertEquivalent("\\dfrac{a}{b}", "\\displaystyle\\frac{a}{b}", size, scale);
                assertEquivalent("\\tfrac{a}{b}", "\\textstyle\\frac{a}{b}", size, scale);
                Recorder display = collect(latex("\\dfrac{a}{b}"), size, scale, 20.25F, 40.5F);
                Recorder text = collect(latex("\\tfrac{a}{b}"), size, scale, 20.25F, 40.5F);
                Assert.assertEquals(1, display.rules.size());
                Assert.assertEquals(1, text.rules.size());
                Assert.assertTrue(display.glyphs.get(0)[2] > text.glyphs.get(0)[2]);
                Assert.assertTrue(display.batch.getDecorationBatch().getQuadCount() > 0);
                for (String source : new String[] {"\\dfrac{a}{b}", "\\tfrac{a}{b}",
                        "\\scriptstyle\\frac{a}{b}", "\\scriptscriptstyle\\sqrt{x}"}) {
                    MathBox box = LatexSoftwareRenderKit.layout(source, size);
                    Recorder r = collect(latex(source), size, scale, 20.25F, 40.5F);
                    Assert.assertEquals(source, (int) Math.ceil(20.25F + box.getWidth() * scale), r.end);
                    Assert.assertEquals(source, box.getRules().size(), r.rules.size());
                    for (int k = 0; k < r.rules.size(); k++) {
                        Assert.assertEquals(source, box.getRules().get(k).getWidth() * scale,
                                r.rules.get(k)[2], 0.001F);
                        Assert.assertEquals(source, Math.max(1, Math.round(box.getRules().get(k).getThickness() * scale)),
                                r.rules.get(k)[3], 0.0F);
                    }
                }
            }
        }
    }

    @Test
    public void explicitStylesPreserveGlyphlessAdvanceAndFollowingRulePositions() {
        for (int size : SIZES) {
            for (float scale : SCALES) {
                for (String gap : new String[] {"\\scriptstyle\\quad", "\\scriptscriptstyle\\!"}) {
                    String tail = latex("\\dfrac{\\quad}{\\quad}") + "A";
                    Recorder plain = collect(tail, size, scale, 20.25F, 40.5F);
                    Recorder shifted = collect(latex(gap) + tail, size, scale, 20.25F, 40.5F);
                    float delta = LatexSoftwareRenderKit.layout(gap, size).getWidth() * scale;
                    Assert.assertEquals(1, shifted.glyphs.size());
                    Assert.assertEquals(1, shifted.rules.size());
                    Assert.assertEquals(plain.glyphs.get(0)[0] + delta, shifted.glyphs.get(0)[0], 0.001F);
                    Assert.assertEquals(plain.rules.get(0)[0] + delta, shifted.rules.get(0)[0], 0.001F);
                    Assert.assertEquals(plain.rules.get(0)[1], shifted.rules.get(0)[1], 0.0F);
                    Assert.assertEquals(plain.rules.get(0)[2], shifted.rules.get(0)[2], 0.0F);
                }
            }
        }
    }

    @Test
    public void cachedAlternatingStyleSourcesReturnToOriginalGlyphsRulesAndAdvance() {
        for (int size : SIZES) {
            for (float scale : SCALES) {
                String original = latex("\\scriptstyle\\frac{a}{b}");
                Recorder first = collect(original, size, scale, 20.25F, 40.5F);
                collect(latex("\\displaystyle\\frac{a}{b}"), size, scale, 20.25F, 40.5F);
                collect(latex("\\scriptscriptstyle\\frac{a}{b}"), size, scale, 20.25F, 40.5F);
                collect(latex("\\dfrac{a}{b}"), size, scale, 20.25F, 40.5F);
                Recorder again = collect(original, size, scale, 20.25F, 40.5F);
                assertSameRecording(first, again);
                Recorder explicitSize = collect("<size=" + size + ">" + original + "</size>",
                        10, scale, 20.25F, 40.5F);
                assertSameRecording(first, explicitSize);
            }
        }
    }

    private static void assertEquivalent(String a, String b, int size, float scale) {
        Recorder left = collect(latex(a), size, scale, 20.25F, 40.5F);
        Recorder right = collect(latex(b), size, scale, 20.25F, 40.5F);
        assertSameRecording(left, right);
    }

    private static void assertSameRecording(Recorder left, Recorder right) {
        Assert.assertEquals(left.end, right.end);
        Assert.assertEquals(left.glyphs.size(), right.glyphs.size());
        Assert.assertEquals(left.rules.size(), right.rules.size());
        for (int i = 0; i < left.glyphs.size(); i++) {
            Assert.assertArrayEquals(left.glyphs.get(i), right.glyphs.get(i), 0.001F);
        }
        for (int i = 0; i < left.rules.size(); i++) {
            Assert.assertArrayEquals(left.rules.get(i), right.rules.get(i), 0.001F);
        }
    }

    private static void assertSizes(String source, int size, float scale, int... expected) {
        Recorder result = collect(latex(source), size, scale, 20.25F, 40.5F);
        Assert.assertEquals(source, expected.length, result.glyphs.size());
        Assert.assertTrue(result.batch.getActivePageCount() > 0);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(source + " glyph=" + i, expected[i] * scale, result.glyphs.get(i)[2], 0.0F);
        }
    }

    static String latex(String source) {
        return "<latex>" + source + "</latex>";
    }

    static Recorder collect(String text, int size, float scale, float x, float y) {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        Recorder result = new Recorder();
        result.segments = shared.service.parseSegments(text, 0xFFFFFFFF, TextContentMode.RICH_TAGS);
        LatexSoftwareRenderKit.assembleGlyphs(shared, result.segments);
        result.end = DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(result.segments,
                shared.settings, shared.service, GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, 1),
                x, y, false, scale, size, result);
        return result;
    }

    /** 仅旁录参数，所有几何继续委托生产批次，不替换字体或布局实现。 */
    static final class Recorder implements GlyphCollector {
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