package club.heiqi.uilib.font.page;

import java.awt.image.BufferedImage;
import club.heiqi.uilib.font.util.BundledMathFont;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.font.glyph.GlyphGenerationTask;
import club.heiqi.uilib.font.glyph.GlyphGenerationPriority;
import club.heiqi.uilib.font.glyph.GlyphGenerator;
import club.heiqi.uilib.font.latex.MathFontStyle;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;
import club.heiqi.uilib.font.latex.layout.MathGlyphRef;

/** Actual shared mailbox, packing and software-GL upload lifecycle, without a second math atlas. */
public class MathGlyphPageLifecycleTest {
    private static final MathGlyphRef FIRST = MathGlyphRef.forFontGlyph("face-a", 7);
    private static final MathGlyphRef SECOND = MathGlyphRef.forFontGlyph("face-b", 7);

    private FontRuntimeSettings settings() {
        return new FontRuntimeSettings(3, 1, 16, 0, 0, false, null, null);
    }

    private GlyphPageManager manager(GlyphPageVariableSlotPackingTest.FakeGlApi gl, int pages) {
        GlyphPageManager manager = new GlyphPageManager(null, 16, 1048576L, 1, 1024L,
                1000L, pages, 1048576L, Long.MAX_VALUE, 1048576L, () -> 0L, gl);
        manager.setGeneration(1, settings());
        manager.initialize();
        return manager;
    }

    private GlyphGenerationResult result(GlyphRequestToken token, int size) {
        GlyphInfo info = new GlyphInfo(token.getMathGlyphRef(), 16, 16, 12, 12, 4, 0,
                size, size, size, size, -2, -3, 12, 2, 3, true, false);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff123456);
        return GlyphGenerationResult.forMathGlyph(token, image, info);
    }

    private GlyphRequestToken upload(GlyphPageManager manager, MathGlyphRef ref, int bitmapSize) {
        GlyphRequestToken token = manager.claimMathRequest(1, ref, 16, 0, 3);
        Assert.assertNotNull(token);
        Assert.assertTrue(manager.markRasterizing(token));
        Assert.assertTrue(manager.queueUpload(result(token, bitmapSize)));
        manager.flushPendingUploads(16);
        return token;
    }

    @Test
    public void bundledPhysicalGlyphRendersVisiblePixelsThroughTheExistingUploadPipeline() {
        GlyphPageVariableSlotPackingTest.FakeGlApi gl = new GlyphPageVariableSlotPackingTest.FakeGlApi();
        GlyphPageManager manager = manager(gl, 2);
        BundledMathFont math = BundledMathFont.shared();
        FontCatalog catalog = new FontCatalog();
        catalog.publish(catalog.prepareSnapshotWithMath(Collections.emptyList(), math));
        DerivedFontCache cache = new DerivedFontCache(catalog);
        FontMatcher matcher = new FontMatcher(catalog, cache) {
            @Override public FontRuntimeSettings getRuntimeSettings(int generation) {
                return generation == 1 ? settings() : null;
            }
        };
        matcher.setRuntimeTables(1, manager.getRuntimeTables());
        MathGlyphRef ref = math.resolve('x', MathFontStyle.MATH_NORMAL, FontType.NORMAL);
        GlyphRequestToken token = manager.claimMathRequest(1, ref, 16, 0, 3);
        Assert.assertTrue(manager.markRasterizing(token));
        GlyphGenerationTask task = new GlyphGenerationTask(
                token, 16, GlyphGenerationPriority.HIGH);
        GlyphGenerationResult generated = new GlyphGenerator(matcher, cache).generate(task);
        Assert.assertNotNull(generated);
        Assert.assertTrue(generated.getMathGlyphInfo().hasBitmap());
        Assert.assertTrue(manager.queueUpload(generated));
        manager.flushPendingUploads(16);
        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(token));
        Assert.assertTrue(gl.sawNonTransparentUpload());
        Assert.assertNotNull(manager.getMathGlyphSlot(1, ref, 16, 0));
        manager.reset();
    }

    @Test
    public void sameGidDifferentFaceUploadsIntoDistinctSlotsOnSharedOrdinaryPage() {
        GlyphPageVariableSlotPackingTest.FakeGlApi gl = new GlyphPageVariableSlotPackingTest.FakeGlApi();
        GlyphPageManager manager = manager(gl, 2);
        int epoch = manager.getRuntimeTables().getInkEpoch();
        GlyphRequestToken a = upload(manager, FIRST, 8);
        GlyphRequestToken b = upload(manager, SECOND, 8);
        MathGlyphSlot first = manager.getMathGlyphSlot(1, FIRST, 16, 0);
        MathGlyphSlot second = manager.getMathGlyphSlot(1, SECOND, 16, 0);
        Assert.assertNotNull(first);
        Assert.assertNotNull(second);
        Assert.assertEquals(first.getTextureId(), second.getTextureId());
        Assert.assertTrue(first.getSlotX() != second.getSlotX() || first.getSlotY() != second.getSlotY());
        Assert.assertEquals(FIRST, first.getGlyphInfo().getMathGlyphRef());
        Assert.assertTrue((first.getFlags() & GlyphRuntimeTables.GLYPH_FLAG_MATH_CORE) != 0);
        Assert.assertEquals(first.getPageIndex(), second.getPageIndex());
        Assert.assertEquals(-2, first.getGlyphInfo().getAtlasBaselineX());
        Assert.assertEquals(-3, first.getGlyphInfo().getAtlasBaselineY());
        Assert.assertTrue(manager.getRuntimeTables().getInkEpoch() > epoch);
        Assert.assertEquals(2, gl.getTexSubImageCount());
        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(a));
        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(b));
        GlyphRequestToken ordinary = manager.claimRequest(1, 'A', FontType.NORMAL, 3);
        Assert.assertTrue(manager.markRasterizing(ordinary));
        GlyphInfo info = new GlyphInfo('A', 8, 8, 8, 8, 8, false);
        BufferedImage bitmap = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Assert.assertTrue(manager.queueUpload(new GlyphGenerationResult(ordinary, bitmap, info)));
        manager.flushPendingUploads(16);
        Assert.assertTrue(manager.isReady('A', FontType.NORMAL));
        Assert.assertEquals(first.getTextureId(), manager.getPageByLocation(manager.getPackedLocation('A', FontType.NORMAL),
                FontType.NORMAL).getTextureId());
        Assert.assertTrue(manager.evictMathGlyphPage(a));
        Assert.assertFalse(manager.isReady('A', FontType.NORMAL));
        Assert.assertNull(manager.getMathGlyphSlot(1, SECOND, 16, 0));
        manager.reset();
    }

    @Test
    public void cancellationReclaimAndReloadRejectOldTokensAndPendingPlans() {
        GlyphPageManager manager = manager(new GlyphPageVariableSlotPackingTest.FakeGlApi(), 2);
        GlyphRequestToken old = manager.claimMathRequest(1, FIRST, 16, 0, 1);
        Assert.assertSame(old, manager.promoteMathDemand(1, FIRST, 16, 0, 3));
        Assert.assertNull(manager.claimMathRequest(1, FIRST, 16, 0, 3));
        Assert.assertTrue(manager.markRasterizing(old));
        GlyphGenerationResult stale = result(old, 8);
        Assert.assertTrue(manager.markCancelled(old, GlyphState.RASTERIZING));
        GlyphRequestToken next = manager.claimMathRequest(1, FIRST, 16, 0, 3);
        Assert.assertNotEquals(old, next);
        Assert.assertNull(manager.getTokenState(old));
        Assert.assertFalse(manager.queueUpload(stale));
        Assert.assertTrue(manager.markRasterizing(next));
        Assert.assertTrue(manager.queueUpload(result(next, 8)));
        manager.setGeneration(2, settings());
        manager.flushPendingUploads(16);
        Assert.assertNull(manager.getMathGlyphSlot(1, FIRST, 16, 0));
        Assert.assertNull(manager.getMathGlyphSlot(2, FIRST, 16, 0));
        Assert.assertNull(manager.getTokenState(next));
        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertTrue(manager.getRuntimeTables().mathGlyphs.isEmpty());
        manager.reset();
    }

    @Test
    public void noBitmapMathPublishesMetricsAndInkEpochWithoutAllocatingTexture() {
        GlyphPageVariableSlotPackingTest.FakeGlApi gl = new GlyphPageVariableSlotPackingTest.FakeGlApi();
        GlyphPageManager manager = manager(gl, 1);
        GlyphRequestToken token = manager.claimMathRequest(1, FIRST, 16, 0, 3);
        int epoch = manager.getRuntimeTables().getInkEpoch();
        Assert.assertTrue(manager.markRasterizing(token));
        GlyphInfo info = new GlyphInfo(FIRST, 16, 16, 12, 12, 4, 0, 0, 0, 0, 0, 0, 0, 12, 0, 0, false, false);
        Assert.assertTrue(manager.queueUpload(GlyphGenerationResult.forMathGlyph(token, null, info)));
        manager.flushPendingUploads(16);
        MathGlyphSlot slot = manager.getMathGlyphSlot(1, FIRST, 16, 0);
        Assert.assertNotNull(slot);
        Assert.assertEquals(GlyphState.NO_BITMAP, manager.getTokenState(token));
        Assert.assertEquals(12, slot.getGlyphInfo().getAdvance(), 0);
        Assert.assertEquals(-1, slot.getPageIndex());
        Assert.assertEquals(0, slot.getTextureId());
        Assert.assertEquals(0, manager.getResidentAtlasPageCount());
        Assert.assertEquals(0, gl.getTexSubImageCount());
        Assert.assertTrue(manager.getRuntimeTables().getInkEpoch() > epoch);
        manager.reset();
    }

    @Test
    public void atlasBudgetPressureEvictionAndRegenerationShareOneBudget() {
        GlyphPageManager manager = manager(new GlyphPageVariableSlotPackingTest.FakeGlApi(), 1);
        GlyphRequestToken first = upload(manager, FIRST, settings().getTextureSize());
        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(first));
        GlyphRequestToken blocked = upload(manager, SECOND, 8);
        Assert.assertEquals(GlyphState.ABSENT, manager.getTokenState(blocked));
        Assert.assertEquals(1, manager.getResidentAtlasPageCount());
        Assert.assertNull(manager.claimMathRequest(1, SECOND, 16, 0, 3));
        Assert.assertTrue(manager.evictMathGlyphPage(first));
        Assert.assertNull(manager.getMathGlyphSlot(1, FIRST, 16, 0));
        Assert.assertEquals(0, manager.getResidentAtlasPageCount());
        GlyphRequestToken regenerated = upload(manager, SECOND, 8);
        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(regenerated));
        Assert.assertFalse(manager.queueUpload(result(blocked, 8)));
        Assert.assertFalse(manager.evictMathGlyphPage(first));
        Assert.assertEquals(1, manager.getResidentAtlasPageCount());
        manager.reset();
    }

    @Test
    public void failedBatchInvalidatesMathResidency() {
        GlyphPageVariableSlotPackingTest.FakeGlApi gl = new GlyphPageVariableSlotPackingTest.FakeGlApi();
        GlyphPageManager manager = manager(gl, 1);
        GlyphRequestToken token = manager.claimMathRequest(1, FIRST, 16, 0, 3);
        Assert.assertTrue(manager.markRasterizing(token));
        Assert.assertTrue(manager.queueUpload(result(token, 8)));
        gl.failNextMipmap();
        manager.flushPendingUploads(16);
        Assert.assertNull(manager.getMathGlyphSlot(1, FIRST, 16, 0));
        Assert.assertEquals(GlyphState.ABSENT, manager.getTokenState(token));
        Assert.assertNotNull(manager.claimMathRequest(1, FIRST, 16, 0, 3));
        manager.reset();
    }
}
