package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.render.GlyphRenderBatch;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.render.GlyphBatchCollector;
import club.heiqi.uilib.font.render.GlyphCollector;
import club.heiqi.uilib.ui.text.TextContentMode;

/** 显式数学字体经生产 prepared buffer、collector 和真实 quad 的回归。 */
public class LatexMathFontRenderingTest {
    static final int[] SIZES = {12, 14, 16, 24};
    static final float[] SCALES = {1, 1.25F, 4, 8};
    @AfterClass
    public static void release() { LatexSoftwareRenderKit.resetShared(); }

    @Test
    public void explicitFontsOverrideHostForNormalAndShadowQuads() {
        check("\\mathrm{x1\\alpha}+y", false, false, false, true, true);
        check("\\mathit{1\\alpha\\Gamma}", true, true, true);
        // 生成根号不携带 atom 字体元数据；其宿主继承仍是旧行为。
        check("\\sqrt{\\mathrm{x}}", true, false);
        check("\\mathit{+(=)\\sum\\sin}", false, false, false, false, false, false, false, false);
    }

    @Test
    public void nestedScopesRestoreFontAndTextKeepsHostRules() {
        check("\\mathrm{x\\mathit{1}y}z", false, true, false, true);
        for (boolean host : new boolean[] {false, true}) {
            assertFlags(wrap("\\mathit{\\text{ab}}", host), 16, 1, false, host, host);
            assertFlags(wrap("\\mathrm{\\text{ab}}", host), 16, 1, true, host, host);
            assertFlags(wrap("x1+\\alpha", host), 16, 1, false, true, host, host, host);
        }
        assertFlags("<i>A</i>B", 16, 1, false, true, false);
        assertFlags("<i>A" + latex("\\mathrm{x}\\mathit{1}") + "B</i>C",
                16, 1, true, true, false, true, true, false);
    }

    @Test
    public void sizeAndFontSourceAlternationCannotReuseStaleItalicState() {
        for (int size : SIZES) for (float scale : SCALES) for (boolean shadow : new boolean[] {false, true}) {
            String original = wrap("\\mathrm{x}\\mathit{1}", true);
            Recorder first = assertFlags(original, size, scale, shadow, false, true);
            assertFlags(wrap("\\mathit{x}\\mathrm{1}", true), size, scale, shadow, true, false);
            Recorder mixed = assertFlags(wrap("\\scriptstyle\\mathrm{\\displaystyle x}\\mathit{1}", true),
                    size, scale, shadow, false, true);
            Assert.assertEquals(size * scale, mixed.glyphs.get(0)[2], 0.0F);
            Assert.assertTrue(mixed.glyphs.get(shadow ? 2 : 1)[2] < mixed.glyphs.get(0)[2]);
            Recorder again = assertFlags(original, size, scale, shadow, false, true);
            Assert.assertEquals(first.end, again.end);
            for (int i = 0; i < first.glyphs.size(); i++) {
                Assert.assertArrayEquals(first.glyphs.get(i), again.glyphs.get(i), 0.0F);
            }
        }
    }

    private static void check(String source, boolean... expected) {
        for (int size : SIZES) for (float scale : SCALES) for (boolean shadow : new boolean[] {false, true}) {
            assertFlags(wrap(source, true), size, scale, shadow, expected);
        }
    }

    static String wrap(String source, boolean host) {
        return host ? "<i>" + latex(source) + "</i>" : latex(source);
    }

    private static Recorder assertFlags(String rich, int size, float scale, boolean shadow, boolean... expected) {
        Recorder r = collect(rich, size, scale, 20.25F, 40.5F, shadow);
        int copies = shadow ? 2 : 1;
        Assert.assertEquals(rich, expected.length * copies, r.italics.size());
        int expectedSlanted = 0;
        for (int i = 0; i < expected.length; i++) for (int pass = 0; pass < copies; pass++) {
            Assert.assertEquals(rich + " glyph=" + i + " pass=" + pass, expected[i], r.italics.get(i * copies + pass));
            if (expected[i]) expectedSlanted++;
        }
        int quads = 0;
        int slanted = 0;
        for (int p = 0; p < r.batch.getActivePageCount(); p++) {
            GlyphRenderBatch b = r.batch.getActiveBatch(p);
            float[] v = b.copyVertexData();
            int stride = GlyphRenderBatch.VERTEX_STRIDE_FLOATS;
            for (int q = 0; q < b.getQuadCount(); q++) {
                int o = q * GlyphRenderBatch.VERTICES_PER_QUAD * stride;
                float shift = v[o] - v[o + stride];
                Assert.assertTrue(rich, shift >= 0);
                if (shift > 0) slanted++;
                quads++;
            }
        }
        Assert.assertEquals(rich, r.italics.size(), quads);
        Assert.assertEquals(rich, expectedSlanted, slanted);
        return r;
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