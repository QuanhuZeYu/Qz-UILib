package club.heiqi.uilib.font.page;

import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontCharacterRuleSet;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/** 字形 token、状态与 runtime generation 隔离测试。 */
public class GlyphRuntimeVersionIsolationTest {

    @Test
    public void claimReturnsCompleteTokenAtomically() {
        GlyphPageManager manager = manager(3);

        GlyphRequestToken token = manager.claimRequest(3, 'A', FontType.BOLD);

        Assert.assertNotNull(token);
        Assert.assertEquals(3, token.getGeneration());
        Assert.assertTrue(token.getRequestId() != 0L);
        Assert.assertEquals('A', token.getCodepoint());
        Assert.assertEquals(FontType.BOLD, token.getFontType());
        Assert.assertEquals(GlyphState.QUEUED, manager.getTokenState(token));
        Assert.assertNull(manager.claimRequest(3, 'A', FontType.BOLD));
    }

    @Test
    public void staleTokenCannotSettleNewRequestInSameGeneration() {
        GlyphPageManager manager = manager(1);
        GlyphRequestToken oldToken = manager.claimRequest(1, 'A', FontType.NORMAL);
        Assert.assertTrue(manager.markRasterizing(oldToken));
        Assert.assertTrue(manager.markCancelled(oldToken, GlyphState.RASTERIZING));
        GlyphRequestToken newToken = manager.claimRequest(1, 'A', FontType.NORMAL);

        Assert.assertNotNull(newToken);
        Assert.assertNotEquals(oldToken.getRequestId(), newToken.getRequestId());
        Assert.assertFalse(manager.markFailed(oldToken, GlyphState.QUEUED));
        Assert.assertFalse(manager.markCancelled(oldToken, GlyphState.QUEUED));
        Assert.assertFalse(manager.queueUpload(result(oldToken)));
        Assert.assertEquals(GlyphState.QUEUED, manager.getTokenState(newToken));
    }

    @Test
    public void oldGenerationTokenCannotMutateTransferredStorage() {
        GlyphPageManager manager = manager(1);
        GlyphRequestToken oldToken = manager.claimRequest(1, 'A', FontType.NORMAL);
        Assert.assertTrue(manager.markRasterizing(oldToken));

        manager.setRuntimeVersion(2);
        GlyphRequestToken newToken = manager.claimRequest(2, 'A', FontType.NORMAL);

        Assert.assertNull(manager.getTokenState(oldToken));
        Assert.assertFalse(manager.markFailed(oldToken, GlyphState.RASTERIZING));
        Assert.assertFalse(manager.markCancelled(oldToken, GlyphState.RASTERIZING));
        Assert.assertFalse(manager.queueUpload(result(oldToken)));
        Assert.assertEquals(GlyphState.QUEUED, manager.getTokenState(newToken));
    }

    @Test
    public void resultAndPendingUploadPreserveSameTokenIdentity() {
        GlyphPageManager manager = manager(1);
        GlyphRequestToken token = manager.claimRequest(1, 'A', FontType.NORMAL);
        Assert.assertTrue(manager.markRasterizing(token));
        GlyphGenerationResult result = result(token);
        PendingGlyphUpload upload = new PendingGlyphUpload(result);

        Assert.assertSame(token, result.getToken());
        Assert.assertSame(token, upload.getToken());
        Assert.assertTrue(manager.queueUpload(result));
        Assert.assertFalse(manager.queueUpload(result));
        Assert.assertEquals(GlyphState.UPLOAD_QUEUED, manager.getTokenState(token));
        Assert.assertEquals(1, manager.getPendingUploadCount());
    }

    @Test
    public void staleUploadConsumesFlushBudget() {
        GlyphPageManager manager = manager(1);
        GlyphRequestToken staleToken = queue(manager, 'A');
        Assert.assertTrue(manager.markCancelled(staleToken, GlyphState.UPLOAD_QUEUED));
        GlyphRequestToken currentToken = queue(manager, 'B');

        manager.flushPendingUploads(1);

        Assert.assertEquals(1, manager.getPendingUploadCount());
        Assert.assertEquals(0, manager.getReadyGlyphCount());
        Assert.assertEquals(GlyphState.UPLOAD_QUEUED, manager.getTokenState(currentToken));
    }

    @Test
    public void discardSettlesQueuedUploadAsCancelled() {
        GlyphPageManager manager = manager(1);
        GlyphRequestToken token = queue(manager, 'A');

        manager.discardPendingUploads();

        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertEquals(GlyphState.CANCELLED_STALE, manager.getTokenState(token));
        Assert.assertEquals(0, manager.snapshotRecoverableRequests().length);
    }

    @Test
    public void activeTokenIsIncludedInRecoverableDemandSnapshot() {
        GlyphPageManager manager = manager(1);
        GlyphRequestToken token = manager.claimRequest(1, '中', FontType.BOLD);
        Assert.assertTrue(manager.markRasterizing(token));

        long[] requests = manager.snapshotRecoverableRequests();

        Assert.assertEquals(1, requests.length);
        Assert.assertEquals('中', GlyphPageManager.unpackRecoverableCodepoint(requests[0]));
        Assert.assertEquals(FontType.BOLD, GlyphPageManager.unpackRecoverableFontType(requests[0]));
    }

    @Test
    public void noBitmapResultCommitsExplicitTerminalState() {
        GlyphPageManager manager = manager(1);
        GlyphRequestToken token = manager.claimRequest(1, ' ', FontType.NORMAL);
        Assert.assertTrue(manager.markRasterizing(token));
        Assert.assertTrue(manager.queueUpload(noBitmapResult(token)));

        manager.flushPendingUploads(1);

        Assert.assertEquals(GlyphState.NO_BITMAP, manager.getTokenState(token));
        Assert.assertEquals(GlyphRuntimeTables.LOCATION_NO_BITMAP,
                manager.getRuntimeTables().locationNormal[' ']);
        Assert.assertTrue(manager.isReady(' ', FontType.NORMAL));
        Assert.assertFalse(manager.markFailed(token, GlyphState.NO_BITMAP));
        Assert.assertFalse(manager.markCancelled(token, GlyphState.NO_BITMAP));
        Assert.assertEquals(GlyphState.NO_BITMAP, manager.getTokenState(token));
    }

    @Test
    public void uploadExceptionSettlesTokenAsFailed() {
        GlyphPageManager manager = initializedManager(1);
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        tables.normalPages[0] = new FailingUploadGlyphPage();
        GlyphRequestToken token = queue(manager, 'A');

        try {
            manager.flushPendingUploads(1);
            Assert.fail("upload 异常应继续传播给 render owner");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("upload failure", expected.getMessage());
        }

        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertEquals(GlyphState.FAILED, manager.getTokenState(token));
        Assert.assertEquals(GlyphRuntimeTables.LOCATION_NOT_READY, tables.locationNormal['A']);
        assertNoPublishedMetadata(tables, 'A');
    }

    @Test
    public void uploadErrorSettlesTokenBeforePropagating() {
        GlyphPageManager manager = initializedManager(1);
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        tables.normalPages[0] = new ErrorUploadGlyphPage();
        GlyphRequestToken token = queue(manager, 'A');

        try {
            manager.flushPendingUploads(1);
            Assert.fail("upload Error 应继续传播给 render owner");
        } catch (AssertionError expected) {
            Assert.assertEquals("upload error", expected.getMessage());
        }

        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertEquals(GlyphState.FAILED, manager.getTokenState(token));
        Assert.assertEquals(GlyphRuntimeTables.LOCATION_NOT_READY, tables.locationNormal['A']);
        assertNoPublishedMetadata(tables, 'A');
    }

    @Test
    public void staleCommitCannotPublishResidency() {
        GlyphPageManager manager = initializedManager(1);
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        GlyphRequestToken token = queue(manager, 'A');
        tables.normalPages[0] = new CancellingUploadGlyphPage(manager);

        manager.flushPendingUploads(1);

        Assert.assertEquals(GlyphState.CANCELLED_STALE, manager.getTokenState(token));
        Assert.assertEquals(GlyphRuntimeTables.LOCATION_NOT_READY, tables.locationNormal['A']);
        Assert.assertEquals(0, manager.getReadyGlyphCount());
        assertNoPublishedMetadata(tables, 'A');
    }

    @Test
    public void bitmapUploadOnlyPublishesResidencyAndGeometry() {
        GlyphPageManager manager = initializedManager(1);
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        NoGlGlyphPage uploadPage = new NoGlGlyphPage();
        tables.normalPages[0] = uploadPage;
        tables.widthNormal['A'] = 7.25F;
        float ascent = tables.ascentNormal;
        float descent = tables.descentNormal;
        float leading = tables.leadingNormal;
        GlyphRequestToken token = manager.claimRequest(1, 'A', FontType.NORMAL);
        Assert.assertTrue(manager.markRasterizing(token));

        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        GlyphInfo glyphInfo = new GlyphInfo('A', 64, 64, 12.0F, 40.0F, 15.0F, 9.0F, 5.0F, 6.0F,
                8, 8, 2, 7, 48, -1, 3, true, false);
        Assert.assertTrue(manager.queueUpload(new GlyphGenerationResult(token, image, glyphInfo)));
        manager.flushPendingUploads(1);

        Assert.assertTrue(uploadPage.uploaded);
        Assert.assertEquals(7.25F, tables.widthNormal['A'], 0.0F);
        Assert.assertEquals(ascent, tables.ascentNormal, 0.0F);
        Assert.assertEquals(descent, tables.descentNormal, 0.0F);
        Assert.assertEquals(leading, tables.leadingNormal, 0.0F);
        Assert.assertEquals(8, tables.slotWidthNormal['A']);
        Assert.assertEquals(8, tables.slotHeightNormal['A']);
        Assert.assertEquals(5, tables.inkWidthNormal['A']);
        Assert.assertEquals(6, tables.inkHeightNormal['A']);
        Assert.assertTrue((tables.flagsNormal['A'] & GlyphRuntimeTables.GLYPH_FLAG_HAS_BITMAP) != 0);
        Assert.assertNotEquals(GlyphRuntimeTables.LOCATION_NO_BITMAP, tables.locationNormal['A']);
        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(token));
    }

    private static GlyphPageManager manager(int generation) {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(generation);
        return manager;
    }

    private static GlyphPageManager initializedManager(int generation) {
        GlyphPageManager manager = manager(generation);
        FontRuntimeSettings settings = new FontRuntimeSettings(3, 64.0D, 10.0D, 4.0D, 1.0D, false,
                new String[0], FontCharacterRuleSet.empty());
        manager.setGeneration(generation, settings);
        manager.initialize();
        return manager;
    }

    private static GlyphRequestToken queue(GlyphPageManager manager, int codepoint) {
        GlyphRequestToken token = manager.claimRequest(1, codepoint, FontType.NORMAL);
        Assert.assertNotNull(token);
        Assert.assertTrue(manager.markRasterizing(token));
        Assert.assertTrue(manager.queueUpload(result(token)));
        return token;
    }

    private static GlyphGenerationResult result(GlyphRequestToken token) {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        GlyphInfo glyphInfo = new GlyphInfo(token.getCodepoint(), 8, 8, 6.0F, 6.0F, 8.0F, false);
        return new GlyphGenerationResult(token, image, glyphInfo);
    }

    private static GlyphGenerationResult noBitmapResult(GlyphRequestToken token) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        GlyphInfo glyphInfo = new GlyphInfo(token.getCodepoint(), 8, 8, 6.0F, 6.0F, 2.0F, 0.0F,
                0.0F, 0.0F, 0, 0, 0, 0, 6, 0, 0, false, false);
        return new GlyphGenerationResult(token, image, glyphInfo);
    }

    private static void assertNoPublishedMetadata(GlyphRuntimeTables tables, int codepoint) {
        Assert.assertEquals(0, tables.flagsNormal[codepoint]);
        Assert.assertEquals(0, tables.slotXNormal[codepoint]);
        Assert.assertEquals(0, tables.slotYNormal[codepoint]);
        Assert.assertEquals(0, tables.slotWidthNormal[codepoint]);
        Assert.assertEquals(0, tables.slotHeightNormal[codepoint]);
        Assert.assertEquals(0, tables.atlasBaselineXNormal[codepoint]);
        Assert.assertEquals(0, tables.atlasBaselineYNormal[codepoint]);
        Assert.assertEquals(0, tables.lineBaselineYNormal[codepoint]);
        Assert.assertEquals(0, tables.inkWidthNormal[codepoint]);
        Assert.assertEquals(0, tables.inkHeightNormal[codepoint]);
        Assert.assertEquals(0, tables.bearingXNormal[codepoint]);
        Assert.assertEquals(0, tables.bearingYNormal[codepoint]);
    }

    private static class NoGlGlyphPage extends GlyphPage {

        private boolean uploaded;

        private NoGlGlyphPage() {
            super(1, 0, 4096, 64, 3);
        }

        @Override
        public void upload(GlyphSlot slot, GlyphRequestToken token, BufferedImage image) {
            uploaded = true;
        }
    }

    private static final class FailingUploadGlyphPage extends NoGlGlyphPage {

        @Override
        public void upload(GlyphSlot slot, GlyphRequestToken token, BufferedImage image) {
            throw new IllegalStateException("upload failure");
        }
    }

    private static final class ErrorUploadGlyphPage extends NoGlGlyphPage {

        @Override
        public void upload(GlyphSlot slot, GlyphRequestToken token, BufferedImage image) {
            throw new AssertionError("upload error");
        }
    }

    private static final class CancellingUploadGlyphPage extends NoGlGlyphPage {

        private final GlyphPageManager manager;

        private CancellingUploadGlyphPage(GlyphPageManager manager) {
            this.manager = manager;
        }

        @Override
        public void upload(GlyphSlot slot, GlyphRequestToken token, BufferedImage image) {
            Assert.assertTrue(manager.markCancelled(token, GlyphState.UPLOADING));
        }
    }
}
