package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.MathFontStyle;
import club.heiqi.uilib.font.latex.layout.MathMetrics;
import club.heiqi.uilib.font.latex.layout.MathFontSupport;
import club.heiqi.uilib.font.latex.layout.MathGlyphRef;
import club.heiqi.uilib.font.latex.layout.MathGlyphClip;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.latex.layout.GlyphElem;
import club.heiqi.uilib.font.glyph.MathGlyphRasterPlan;
import club.heiqi.uilib.font.page.MathGlyphSlot;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.render.GlyphRenderBatch;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.render.GlyphBatchCollector;
import club.heiqi.uilib.font.render.GlyphCollector;
import club.heiqi.uilib.ui.text.TextContentMode;

/** assembly 可见区域必须经过 prepared、阴影和生产 quad 裁片，不能用重复 alpha 遮盖连接。 */
public class LatexAssemblyClipRenderingTest {
    @Before
    public void enable() { LatexSoftwareRenderKit.enableMathFont(); }
    @After
    public void release() { LatexSoftwareRenderKit.resetShared(); }

    @Test
    public void assemblyClipSurvivesPreparedScaleShadowAndCacheAlternation() {
        String source = "\\left(\\begin{matrix}a\\\\b\\\\c\\\\d\\\\e\\\\f\\\\g\\\\h\\end{matrix}\\right)";
        for (float scale : new float[] {1, 1.25F, 4, 8}) {
            Recorder normal = collect(latex(source), 16, scale, 20.25F, 40.5F, false);
            Recorder shadow = collect(latex(source), 16, scale, 20.25F, 40.5F, true);
            Assert.assertFalse("Tall delimiter must exercise assembly clipping", normal.clips.isEmpty());
            Assert.assertEquals(normal.clips.size() * 2, shadow.clips.size());
            MathBox box = LatexSoftwareRenderKit.shared().service.getLatexBox(normal.segments.get(0), 16);
            List<MathGlyphClip> expected = new ArrayList<MathGlyphClip>();
            for (GlyphElem glyph : box.getGlyphs()) {
                if (glyph.getMathGlyphClip() != null) expected.add(glyph.getMathGlyphClip());
            }
            Assert.assertEquals("Native assembly parts fit one atlas tile", expected.size(), normal.clips.size());
            for (int i = 0; i < normal.clips.size(); i++) {
                float[] n = normal.clips.get(i);
                MathGlyphClip c = expected.get(i);
                Assert.assertEquals(c.getLeft() * scale, n[2] - n[0], 0.001F);
                Assert.assertEquals(c.getTop() * scale, n[3] - n[1], 0.001F);
                Assert.assertEquals(c.getRight() * scale, n[4] - n[0], 0.001F);
                Assert.assertEquals(c.getBottom() * scale, n[5] - n[1], 0.001F);
                Assert.assertArrayEquals(n, shadow.clips.get(i * 2 + 1), 0.001F);
                float[] s = shadow.clips.get(i * 2);
                for (int k = 0; k < n.length; k++) {
                    float offset = (float) (k % 2 == 0 ? FontConfig.shadowOffsetX : FontConfig.shadowOffsetY) * scale;
                    Assert.assertEquals(n[k] + offset, s[k], 0.001F);
                }
            }
            Assert.assertEquals(normal.end, shadow.end);
            Assert.assertTrue("Clipped assembly must emit actual quads", normal.clippedQuadCount > 0);
            Assert.assertEquals(normal.clippedQuadCount * 2, shadow.clippedQuadCount);
            Recorder plain = collect(latex("x"), 16, scale, 20.25F, 40.5F, false);
            Assert.assertTrue(plain.clips.isEmpty());
            Recorder again = collect(latex(source), 16, scale, 20.25F, 40.5F, false);
            Assert.assertEquals(normal.end, again.end);
            Assert.assertEquals(normal.clips.size(), again.clips.size());
            for (int i = 0; i < normal.clips.size(); i++) Assert.assertArrayEquals(normal.clips.get(i), again.clips.get(i), 0.0F);
        }
    }

    static String latex(String source) {
        return "<latex>" + source + "</latex>";
    }

    static Recorder collect(String text, int size, float scale, float x, float y, boolean shadow) {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        Recorder result = new Recorder();
        result.segments = shared.service.parseSegments(text, 0xFFFFFFFF, TextContentMode.RICH_TAGS);
        LatexSoftwareRenderKit.assembleGlyphs(shared, result.segments, size);
        result.end = DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(result.segments,
                shared.settings, shared.service, GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, shared.runtimeVersion),
                x, y, shadow, scale, size, result);
        return result;
    }

    /** 仅旁录参数，所有几何继续委托生产批次，不替换字体或布局实现。 */
    static final class Recorder implements GlyphCollector {
        final GlyphBatchCollector batch = new GlyphBatchCollector();
        final List<float[]> clips = new ArrayList<float[]>();
        final List<float[]> glyphs = new ArrayList<float[]>();
        final List<FontType> weights = new ArrayList<FontType>();
        final List<int[]> slots = new ArrayList<int[]>();
        final List<Byte> flags = new ArrayList<Byte>();
        final List<Boolean> italics = new ArrayList<Boolean>();
        final List<float[]> rules = new ArrayList<float[]>();
        List<TextSegment> segments;
        int end;
        int clippedQuadCount;

        @Override
        public void collectBaselineAlignedGlyph(FontType fontType, int pageIndex, int textureId, int textureSize,
                int slotX, int slotY, int slotWidth, int slotHeight, int atlasBaselineX, int atlasBaselineY,
                int lineBaselineY, int defaultGlyphSize, int inkWidth, int inkHeight, int bearingX, int bearingY,
                float x, float y, float charSize, int color, boolean italic, byte glyphFlags, float baseCharSize) {
            glyphs.add(new float[] {x, y, charSize});
            italics.add(italic);
            flags.add(glyphFlags);
            weights.add(fontType);
            slots.add(new int[] {pageIndex, textureId, slotX, slotY});
            batch.collectBaselineAlignedGlyph(fontType, pageIndex, textureId, textureSize, slotX, slotY,
                    slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, defaultGlyphSize,
                    inkWidth, inkHeight, bearingX, bearingY, x, y, charSize, color, italic, glyphFlags, baseCharSize);
        }

        @Override
        public void collectBaselineAlignedGlyphClipped(FontType fontType, int pageIndex, int textureId, int textureSize,
                int slotX, int slotY, int slotWidth, int slotHeight, int atlasBaselineX, int atlasBaselineY,
                int lineBaselineY, int defaultGlyphSize, int inkWidth, int inkHeight, int bearingX, int bearingY,
                float x, float y, float charSize, int color, boolean italic, byte glyphFlags, float baseCharSize,
                float left, float top, float right, float bottom) {
            clips.add(new float[] {x, y, left, top, right, bottom});
            GlyphBatchCollector probe = new GlyphBatchCollector();
            probe.collectBaselineAlignedGlyphClipped(fontType, pageIndex, textureId, textureSize, slotX, slotY,
                    slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, defaultGlyphSize,
                    inkWidth, inkHeight, bearingX, bearingY, x, y, charSize, color, italic, glyphFlags, baseCharSize,
                    left, top, right, bottom);
            for (int p = 0; p < probe.getActivePageCount(); p++) {
                clippedQuadCount += probe.getActiveBatch(p).getQuadCount();
                float[] v = probe.getActiveBatch(p).copyVertexData();
                for (int i = 0; i < v.length; i += GlyphRenderBatch.VERTEX_STRIDE_FLOATS) {
                    Assert.assertTrue(v[i] >= left - 0.001F && v[i] <= right + 0.001F);
                    Assert.assertTrue(v[i + 1] >= top - 0.001F && v[i + 1] <= bottom + 0.001F);
                }
            }
            batch.collectBaselineAlignedGlyphClipped(fontType, pageIndex, textureId, textureSize, slotX, slotY,
                    slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, defaultGlyphSize,
                    inkWidth, inkHeight, bearingX, bearingY, x, y, charSize, color, italic, glyphFlags, baseCharSize,
                    left, top, right, bottom);
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