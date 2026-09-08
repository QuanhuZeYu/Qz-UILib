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

/** 固定数学 face 经真实 request/upload/slot/collector 的生产整合回归。 */
public class LatexPhysicalMathRenderingTest {
    @Before
    public void enable() { LatexSoftwareRenderKit.enableMathFont(); }
    @After
    public void release() { LatexSoftwareRenderKit.resetShared(); }

    @Test
    public void resolvedGlyphsUsePhysicalSlotsAndNeverAddHostShear() {
        for (boolean shadow : new boolean[] {false, true}) {
            Recorder r = collect("<i>" + latex("x+\\mathbf{m}\\mathit{1\\alpha}") + "</i>", 16, 1.25F, 20.25F, 40.5F, shadow);
            LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
            MathBox box = shared.service.getLatexBox(r.segments.get(0), 16);
            MathFontSupport support = shared.service.createMathMetrics(r.segments.get(0).getStyle(), 16).mathFontSupport();
            Assert.assertNotNull(support);
            GlyphRuntimeTablesView view = GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, shared.runtimeVersion);
            int index = 0;
            for (GlyphElem g : box.getGlyphs()) {
                Assert.assertNotNull(g.getMathGlyphRef());
                Assert.assertFalse(g.isItalic());
                Assert.assertFalse(g.isInheritTextItalic());
                int size = shared.settings.getPageGlyphSize();
                MathGlyphRasterPlan plan = new MathGlyphRasterPlan(support.measure(g.getMathGlyphRef(), size),
                        shared.settings.getTextureSize(), shared.settings.getGlyphInkPadding());
                for (int tile = 0; tile < plan.getTileCount(); tile++) {
                    MathGlyphSlot slot = view.getMathGlyphSlot(g.getMathGlyphRef(), size, tile);
                    Assert.assertNotNull(slot);
                    if (!slot.getGlyphInfo().hasBitmap()) continue;
                    for (int pass = 0; pass < (shadow ? 2 : 1); pass++) {
                        Assert.assertEquals(FontType.NORMAL, r.weights.get(index));
                        Assert.assertFalse(r.italics.get(index));
                        Assert.assertTrue((r.flags.get(index) & GlyphRuntimeTables.GLYPH_FLAG_MATH_CORE) != 0);
                        Assert.assertArrayEquals(new int[] {slot.getPageIndex(), slot.getTextureId(), slot.getSlotX(), slot.getSlotY()}, r.slots.get(index));
                        index++;
                    }
                }
            }
            Assert.assertEquals(index, r.glyphs.size());
            for (int page = 0; page < r.batch.getActivePageCount(); page++) {
                GlyphRenderBatch batch = r.batch.getActiveBatch(page);
                float[] vertices = batch.copyVertexData();
                int stride = GlyphRenderBatch.VERTEX_STRIDE_FLOATS;
                for (int q = 0; q < batch.getQuadCount(); q++) {
                    int o = q * GlyphRenderBatch.VERTICES_PER_QUAD * stride;
                    Assert.assertEquals(vertices[o], vertices[o + stride], 0.0F);
                }
            }
        }
        Recorder mixed = collect("<i>A" + latex("x") + "B</i>", 16, 1, 20.25F, 40.5F, false);
        Assert.assertEquals(3, mixed.italics.size());
        Assert.assertTrue(mixed.italics.get(0));
        Assert.assertFalse(mixed.italics.get(1));
        Assert.assertTrue(mixed.italics.get(2));
    }

    @Test
    public void hostWeightBindingAndInteriorAdvanceMatchPhysicalMetrics() {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        MathFontSupport provider = shared.fontMatcher.getCatalogSnapshot(shared.runtimeVersion).getMathFontSupport();
        TextStyle bold = new TextStyle();
        bold.setFontType(FontType.BOLD);
        MathMetrics boldMetrics = shared.service.createMathMetrics(bold, 16);
        for (MathFontStyle style : new MathFontStyle[] {MathFontStyle.UPRIGHT, MathFontStyle.ITALIC, MathFontStyle.MATH_NORMAL}) {
            Assert.assertEquals(provider.resolve('x', style, FontType.BOLD),
                    boldMetrics.mathFontSupport().resolve('x', style, FontType.NORMAL));
            Assert.assertEquals(provider.resolve('x', style, FontType.BOLD),
                    boldMetrics.forFontStyle(MathFontStyle.UPRIGHT).mathFontSupport().resolve('x', style, FontType.NORMAL));
        }
        for (int size : new int[] {12, 14, 16, 24}) for (float scale : new float[] {1, 1.25F, 4, 8}) {
            Recorder first = collect(latex("\\mathbf{mm}"), size, scale, 20.25F, 40.5F, false);
            MathGlyphRef ref = provider.resolve('m', MathFontStyle.BOLD, FontType.NORMAL);
            Assert.assertEquals(provider.measure(ref, size).getAdvance() * scale,
                    first.glyphs.get(1)[0] - first.glyphs.get(0)[0], 0.001F);
            collect(latex("\\mathrm{mm}"), size, scale, 20.25F, 40.5F, false);
            Recorder again = collect(latex("\\mathbf{mm}"), size, scale, 20.25F, 40.5F, false);
            Assert.assertEquals(first.end, again.end);
            Assert.assertArrayEquals(first.glyphs.get(1), again.glyphs.get(1), 0.0F);
        }
    }

    @Test
    public void oldSnapshotRejectsNewOrEvictedMathPages() {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        GlyphRuntimeTablesView before = GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, shared.runtimeVersion);
        Recorder r = collect(latex("\\mathbf{x}"), 16, 1, 20.25F, 40.5F, false);
        MathGlyphRef ref = shared.service.getLatexBox(r.segments.get(0), 16).getGlyphs().get(0).getMathGlyphRef();
        GlyphRuntimeTablesView after = GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, shared.runtimeVersion);
        MathGlyphSlot slot = after.getMathGlyphSlot(ref, shared.settings.getPageGlyphSize(), 0);
        Assert.assertNotNull(slot);
        // If upload added a new physical page, the old snapshot cannot discover its texture.
        if (before.getPageTextureIdSnapshot(FontType.NORMAL, slot.getPageIndex()) == 0) {
            Assert.assertNull(before.getMathGlyphSlot(ref, shared.settings.getPageGlyphSize(), 0));
        }
        Assert.assertTrue(shared.manager.evictMathGlyphPage(slot.getToken()));
        Assert.assertNull(after.getMathGlyphSlot(ref, shared.settings.getPageGlyphSize(), 0));
        Assert.assertNull(GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, shared.runtimeVersion + 1).getMathGlyphSlot(ref, shared.settings.getPageGlyphSize(), 0));
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
        final List<float[]> glyphs = new ArrayList<float[]>();
        final List<FontType> weights = new ArrayList<FontType>();
        final List<int[]> slots = new ArrayList<int[]>();
        final List<Byte> flags = new ArrayList<Byte>();
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
            flags.add(glyphFlags);
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