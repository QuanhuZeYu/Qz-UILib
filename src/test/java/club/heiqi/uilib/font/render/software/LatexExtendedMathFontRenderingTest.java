package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.MathFontStyle;
import club.heiqi.uilib.font.latex.layout.MathMetrics;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.render.GlyphRenderBatch;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.render.GlyphBatchCollector;
import club.heiqi.uilib.font.render.GlyphCollector;
import club.heiqi.uilib.ui.text.TextContentMode;

/** 局部数学字重必须同时决定度量、位图页和阴影，不用段尾宽度掩盖错误。 */
public class LatexExtendedMathFontRenderingTest {
    @AfterClass
    public static void release() { LatexSoftwareRenderKit.resetShared(); }

    @Test
    public void boldAlphabetUsesBoldPagesAndNestedSelectionRestoresHost() {
        for (boolean shadow : new boolean[] {false, true}) {
            assertWeights("<i>" + latex("\\mathbf{x1\\alpha+\\mathrm{y}\\mathit{z}\\mathnormal{q}}r") + "</i>",
                    "x1α+yzqr", shadow, "BBBNNNNN", "00000111");
            assertWeights("<b><i>" + latex("\\mathrm{x}\\mathnormal{1}\\mathit{2}") + "</i></b>",
                    "x12", shadow, "BBB", "001");
            assertWeights(latex("\\mathbf{x\\text{ab}\\sin}y"), "xabsiny",
                    shadow, "BNNNNNN", "0000001");
        }
    }

    @Test
    public void fullMetricsViewUsesSelectedWeightAndCanResetToOriginalHost() {
        collect(latex("\\mathbf{mx}\\mathrm{mx}"), 16, 1, 20.25F, 40.5F, false);
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        TextStyle normal = new TextStyle();
        TextStyle bold = new TextStyle();
        bold.setFontType(FontType.BOLD);
        MathMetrics root = shared.service.createMathMetrics(normal, 16);
        MathMetrics selected = root.forFontStyle(MathFontStyle.BOLD);
        MathMetrics expected = shared.service.createMathMetrics(bold, 16);
        for (int size : new int[] {12, 14, 16, 24}) {
            Assert.assertArrayEquals(values(expected, size), values(selected, size), 0.0F);
            Assert.assertArrayEquals(values(root, size), values(selected.forFontStyle(MathFontStyle.UPRIGHT), size), 0.0F);
            Assert.assertArrayEquals(values(root, size), values(selected.forFontStyle(MathFontStyle.MATH_NORMAL), size), 0.0F);
        }
    }

    @Test
    public void interiorAdvanceMatchesBoldMetricsAcrossSizeScaleCacheAndShadow() {
        for (int size : new int[] {12, 14, 16, 24}) for (float scale : new float[] {1, 1.25F, 4, 8}) {
            for (boolean shadow : new boolean[] {false, true}) {
                String source = latex("\\mathbf{mm}\\mathrm{m}");
                Recorder first = collect(source, size, scale, 20.25F, 40.5F, shadow);
                MathMetrics normal = LatexSoftwareRenderKit.shared().service.createMathMetrics(new TextStyle(), size);
                float advance = normal.forFontStyle(MathFontStyle.BOLD).advance("m", size) * scale;
                int copies = shadow ? 2 : 1;
                // 内部相邻字形距离验证，段尾补差无法让此断言伪通过。
                Assert.assertEquals(advance, first.glyphs.get(copies)[0] - first.glyphs.get(0)[0], 0.001F);
                Assert.assertEquals(advance, first.glyphs.get(copies * 2)[0] - first.glyphs.get(copies)[0], 0.001F);
                collect(latex("\\mathrm{mm}\\mathbf{m}"), size, scale, 20.25F, 40.5F, shadow);
                Recorder again = collect(source, size, scale, 20.25F, 40.5F, shadow);
                Assert.assertEquals(first.end, again.end);
                Assert.assertEquals(first.weights, again.weights);
                for (int i = 0; i < first.glyphs.size(); i++) {
                    Assert.assertArrayEquals(first.glyphs.get(i), again.glyphs.get(i), 0.0F);
                    Assert.assertArrayEquals(first.slots.get(i), again.slots.get(i));
                }
            }
        }
    }

    private static float[] values(MathMetrics m, int size) {
        return new float[] {m.advance("m", size), m.ascent(size), m.descent(size), m.xHeight(size),
                m.italicCorrection("m", size), m.inkWidth("m", size), m.inkLeftBearing("m", size),
                m.inkHeight("m", size), m.italicOverhang("m", size), m.inkCenterOffsetY("m", size)};
    }

    private static void assertWeights(String rich, String characters, boolean shadow, String weights, String italics) {
        Recorder r = collect(rich, 16, 1.25F, 20.25F, 40.5F, shadow);
        int copies = shadow ? 2 : 1;
        Assert.assertEquals(rich, weights.length() * copies, r.weights.size());
        Assert.assertEquals(characters.length(), weights.length());
        GlyphRuntimeTablesView tables = GlyphRuntimeTablesView.snapshot(LatexSoftwareRenderKit.shared().tables,
                LatexSoftwareRenderKit.shared().manager, 1);
        for (int i = 0; i < weights.length(); i++) for (int pass = 0; pass < copies; pass++) {
            FontType expected = weights.charAt(i) == 'B' ? FontType.BOLD : FontType.NORMAL;
            int n = i * copies + pass;
            Assert.assertEquals(rich + " glyph=" + i, expected, r.weights.get(n));
            Assert.assertEquals(rich, italics.charAt(i) == '1', r.italics.get(n));
            int cp = characters.charAt(i);
            Assert.assertEquals(tables.getSlotX(cp, expected), r.slots.get(n)[2]);
            Assert.assertEquals(tables.getSlotY(cp, expected), r.slots.get(n)[3]);
            Assert.assertEquals(tables.getPageTextureIdSnapshot(expected, r.slots.get(n)[0]), r.slots.get(n)[1]);
        }
        int quads = 0;
        for (int p = 0; p < r.batch.getActivePageCount(); p++) quads += r.batch.getActiveBatch(p).getQuadCount();
        Assert.assertEquals(r.weights.size(), quads);
    }

    static String latex(String source) {
        return "<latex>" + source + "</latex>";
    }

    static Recorder collect(String text, int size, float scale, float x, float y, boolean shadow) {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        Recorder result = new Recorder();
        result.segments = shared.service.parseSegments(text, 0xFFFFFFFF, TextContentMode.RICH_TAGS);
        LatexSoftwareRenderKit.assembleGlyphs(shared, result.segments);
        result.end = DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(result.segments,
                shared.settings, shared.service, GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, 1),
                x, y, shadow, scale, size, result);
        return result;
    }

    /** 仅旁录参数，所有几何继续委托生产批次，不替换字体或布局实现。 */
    static final class Recorder implements GlyphCollector {
        final GlyphBatchCollector batch = new GlyphBatchCollector();
        final List<float[]> glyphs = new ArrayList<float[]>();
        final List<FontType> weights = new ArrayList<FontType>();
        final List<int[]> slots = new ArrayList<int[]>();
        final List<Boolean> italics = new ArrayList<Boolean>();
        final List<float[]> rules = new ArrayList<float[]>();
        List<TextSegment> segments;
        int end;

        @Override
        public void collectBaselineAlignedGlyph(FontType fontType, int pageIndex, int textureId, int textureSize,
                int slotX, int slotY, int slotWidth, int slotHeight, int atlasBaselineX, int atlasBaselineY,
                int lineBaselineY, int defaultGlyphSize, int inkWidth, int inkHeight, int bearingX, int bearingY,
                float x, float y, float charSize, int color, boolean italic, byte glyphFlags, float baseCharSize) {
            glyphs.add(new float[] {x, y, charSize});
            italics.add(italic);
            weights.add(fontType);
            slots.add(new int[] {pageIndex, textureId, slotX, slotY});
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