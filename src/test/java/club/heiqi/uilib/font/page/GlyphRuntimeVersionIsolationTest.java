package club.heiqi.uilib.font.page;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontCharacterRuleSet;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphGenerationDispatcher;
import club.heiqi.uilib.font.glyph.GlyphGenerationPriority;
import club.heiqi.uilib.font.glyph.GlyphGenerationTask;
import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;

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
    public void generationResultDefensivelyFreezesWorkerPixels() {
        GlyphRequestToken token = new GlyphRequestToken(1, 1L, 'A', FontType.NORMAL);
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFF123456);
        GlyphInfo glyphInfo = new GlyphInfo('A', 1, 1, 1.0F, 1.0F, 1.0F, false);

        GlyphGenerationResult result = new GlyphGenerationResult(token, source, glyphInfo);
        source.setRGB(0, 0, 0x00000000);
        BufferedImage firstRead = result.getImage();
        firstRead.setRGB(0, 0, 0xFFFFFFFF);

        Assert.assertEquals(0xFF123456, result.getImage().getRGB(0, 0));
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
    public void failedUploadRollsBackSlotForNextGlyph() {
        GlyphPageManager manager = initializedManager(1);
        RetryUploadGlyphPage uploadPage = new RetryUploadGlyphPage();
        manager.getRuntimeTables().normalPages[0] = uploadPage;
        GlyphRequestToken failed = queue(manager, 'A');

        try {
            manager.flushPendingUploads(1);
            Assert.fail("首次 upload 应按测试注入失败");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("upload failure", expected.getMessage());
        }
        GlyphRequestToken retried = queue(manager, 'B');
        manager.flushPendingUploads(1);

        Assert.assertEquals(GlyphState.FAILED, manager.getTokenState(failed));
        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(retried));
        Assert.assertEquals(2, uploadPage.slotIndexes.size());
        Assert.assertEquals(uploadPage.slotIndexes.get(0), uploadPage.slotIndexes.get(1));
        Assert.assertEquals(Integer.valueOf(0), uploadPage.slotIndexes.get(1));
    }

    @Test
    public void glErrorRollsBackManagerResidencyAndReusesClearedSlot() {
        GlyphPageManager manager = budgetedManager(new AtomicLong(0L), 8, 1000L, 1024L);
        GlyphPageVariableSlotPackingTest.FakeGlApi gl = new GlyphPageVariableSlotPackingTest.FakeGlApi();
        GlyphPage uploadPage = new GlyphPage(1, 0, 64, 64, 3, gl);
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        tables.normalPages[0] = uploadPage;
        GlyphRequestToken failed = queue(manager, 'A');
        gl.failNextMipmap();

        // 批处理下 mipmap 在批次结算时重建：失败触发整页 quarantine，flush 不向外抛出。
        manager.flushPendingUploads(1);

        Assert.assertEquals("批结算失败后页被隔离，已发布 glyph 回退为 ABSENT", GlyphState.ABSENT,
                manager.getTokenState(failed));
        Assert.assertEquals(0, uploadPage.getCommittedSlotCount());
        Assert.assertEquals(1, gl.getTexSubImageCount());
        Assert.assertEquals(0, uploadPage.getTextureId());
        assertNoPublishedMetadata(tables, 'A');

        GlyphRequestToken next = queue(manager, 'B');
        manager.flushPendingUploads(1);

        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(next));
        Assert.assertEquals(0, GlyphRuntimeTables.unpackSlotIndex(tables.locationNormal['B']));
        Assert.assertEquals("第一批结算失败一次、第二批结算成功一次", 2, gl.getGenerateMipmapCount());
    }

    @Test
    public void failedRollbackCloseStillCountsOwnedTextureAgainstAtlasCap() {
        GlyphPageManager manager = budgetedManager(new AtomicLong(0L), 1, 1000L, 1024L);
        manager.getRuntimeTables().normalPages[0] = new OwnedCloseFailGlyphPage();
        GlyphRequestToken token = queue(manager, 'A');

        try {
            manager.flushPendingUploads(1);
            Assert.fail("upload failure 必须传播");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("upload failure", expected.getMessage());
            Assert.assertEquals(1, expected.getSuppressed().length);
        }

        Assert.assertEquals(GlyphState.FAILED, manager.getTokenState(token));
        Assert.assertEquals(1, manager.getResidentAtlasPageCount());
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
        CancellingUploadGlyphPage uploadPage = new CancellingUploadGlyphPage(manager);
        tables.normalPages[0] = uploadPage;

        manager.flushPendingUploads(1);

        Assert.assertEquals(GlyphState.CANCELLED_STALE, manager.getTokenState(token));
        Assert.assertEquals(GlyphRuntimeTables.LOCATION_NOT_READY, tables.locationNormal['A']);
        Assert.assertEquals(0, manager.getReadyGlyphCount());
        assertNoPublishedMetadata(tables, 'A');

        GlyphRequestToken next = queue(manager, 'B');
        manager.flushPendingUploads(1);
        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(next));
        Assert.assertEquals(2, uploadPage.slotIndexes.size());
        Assert.assertEquals(uploadPage.slotIndexes.get(0), uploadPage.slotIndexes.get(1));
        Assert.assertEquals(1, uploadPage.rollbackCount);
    }

    @Test
    public void failedStalePixelClearQuarantinesExistingAtlasPage() {
        GlyphPageManager manager = budgetedManager(new AtomicLong(0L), 1, 1000L, 1024L * 1024L);
        GlyphPageVariableSlotPackingTest.FakeGlApi gl = new GlyphPageVariableSlotPackingTest.FakeGlApi();
        QuarantiningGlyphPage uploadPage = new QuarantiningGlyphPage(manager, gl);
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        tables.normalPages[0] = uploadPage;
        GlyphRequestToken resident = queue(manager, 'A');
        manager.flushPendingUploads(1);
        GlyphRequestToken pressured = manager.claimRequest(1, 'P', FontType.BOLD);
        Assert.assertTrue(manager.markRasterizing(pressured));
        Assert.assertTrue(manager.queueUpload(result(pressured)));
        manager.flushPendingUploads(1);
        Assert.assertNull(manager.claimRequest(1, 'P', FontType.BOLD));
        uploadPage.failNextStaleRollback = true;
        GlyphRequestToken stale = queue(manager, 'B');

        try {
            manager.flushPendingUploads(1);
            Assert.fail("stale pixel clear 失败必须隔离整页");
        } catch (GlyphPage.GlyphUploadException expected) {
            Assert.assertEquals("batch_rollback_pixels", expected.getPhase());
        }

        Assert.assertEquals(GlyphState.ABSENT, manager.getTokenState(resident));
        Assert.assertEquals(GlyphState.CANCELLED_STALE, manager.getTokenState(stale));
        Assert.assertEquals(0, manager.getReadyGlyphCount());
        Assert.assertEquals(0, manager.getResidentAtlasPageCount());
        Assert.assertEquals(0, uploadPage.getTextureId());
        Assert.assertEquals(0, uploadPage.getCommittedSlotCount());
        Assert.assertNotNull(manager.claimRequest(1, 'P', FontType.BOLD));
        assertNoPublishedMetadata(tables, 'A');
        assertNoPublishedMetadata(tables, 'B');
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

    @Test
    public void mailboxRecordBoundBlocksVisiblePublisherUntilDrain() throws Exception {
        GlyphPageManager manager = manager(1, 2, 1024L, 0, 0L);
        GlyphRequestToken first = rasterizing(manager, 'A', 2);
        GlyphRequestToken second = rasterizing(manager, 'B', 2);
        GlyphRequestToken third = rasterizing(manager, 'C', 3);
        Assert.assertTrue(manager.queueUpload(noBitmapResult(first)));
        Assert.assertTrue(manager.queueUpload(noBitmapResult(second)));
        AtomicBoolean thirdAccepted = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread publisher = publisherThread(manager, noBitmapResult(third), thirdAccepted, failure);
        publisher.start();
        awaitBlockedPublishers(manager, 1);
        Assert.assertEquals(2, manager.getPendingUploadCount());
        Assert.assertEquals(0L, manager.getPendingBitmapBytes());

        manager.flushPendingUploads(1);
        publisher.join(TimeUnit.SECONDS.toMillis(5L));

        Assert.assertFalse(publisher.isAlive());
        Assert.assertNull(failure.get());
        Assert.assertTrue(thirdAccepted.get());
        Assert.assertEquals(2, manager.getPendingUploadCount());
        Assert.assertEquals(0, manager.getBlockedPublisherCount());
    }

    @Test
    public void mailboxBitmapBytesAreStrictlyBounded() throws Exception {
        GlyphPageManager manager = initializedManager(1, 3, 256L, 0, 0L);
        manager.getRuntimeTables().normalPages[0] = new NoGlGlyphPage(64);
        GlyphRequestToken first = rasterizing(manager, 'A', 2);
        GlyphRequestToken second = rasterizing(manager, 'B', 3);
        Assert.assertTrue(manager.queueUpload(result(first, 8, 8)));
        AtomicBoolean secondAccepted = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread publisher = publisherThread(manager, result(second, 4, 4), secondAccepted, failure);
        publisher.start();
        awaitBlockedPublishers(manager, 1);
        Assert.assertEquals(256L, manager.getPendingBitmapBytes());

        manager.flushPendingUploads(1);
        publisher.join(TimeUnit.SECONDS.toMillis(5L));

        Assert.assertFalse(publisher.isAlive());
        Assert.assertNull(failure.get());
        Assert.assertTrue(secondAccepted.get());
        Assert.assertEquals(64L, manager.getPendingBitmapBytes());
        Assert.assertEquals(256L, manager.getPendingBitmapBytesHighWaterMark());
        manager.flushPendingUploads(1);
        Assert.assertEquals(0L, manager.getPendingBitmapBytes());
    }

    @Test
    public void mailboxReservationIncludesRenderThreadInFlightUpload() {
        GlyphPageManager manager = initializedManager(1, 2, 1024L, 0, 0L);
        InspectingUploadGlyphPage uploadPage = new InspectingUploadGlyphPage(manager);
        manager.getRuntimeTables().normalPages[0] = uploadPage;
        GlyphRequestToken token = queue(manager, 'A');

        manager.flushPendingUploads(1);

        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(token));
        Assert.assertEquals(256L, uploadPage.bitmapBytesDuringUpload);
        Assert.assertEquals(1, uploadPage.inFlightDuringUpload);
        Assert.assertEquals(0L, manager.getPendingBitmapBytes());
        Assert.assertEquals(0, manager.getInFlightUploadCount());
    }

    @Test
    public void discardDuringGlUploadPreventsPostEpochResidencyCommit() throws Exception {
        GlyphPageManager manager = initializedManager(1, 2, 1024L, 0, 0L);
        BlockingUploadGlyphPage uploadPage = new BlockingUploadGlyphPage();
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        tables.normalPages[0] = uploadPage;
        GlyphRequestToken token = queue(manager, 'A');
        AtomicReference<Throwable> flushFailure = new AtomicReference<Throwable>();
        Thread flush = new Thread(() -> {
            try {
                manager.flushPendingUploads(1);
            } catch (Throwable throwable) {
                flushFailure.set(throwable);
            }
        }, "glyph-upload-epoch-flush");
        flush.start();
        Assert.assertTrue(uploadPage.entered.await(5L, TimeUnit.SECONDS));

        Thread discard = new Thread(manager::discardPendingUploads, "glyph-upload-epoch-discard");
        discard.start();
        awaitInFlightUploads(manager, 0);
        uploadPage.release.countDown();
        flush.join(TimeUnit.SECONDS.toMillis(5L));
        discard.join(TimeUnit.SECONDS.toMillis(5L));

        Assert.assertFalse(flush.isAlive());
        Assert.assertFalse(discard.isAlive());
        Assert.assertNull(flushFailure.get());
        Assert.assertEquals(GlyphState.CANCELLED_STALE, manager.getTokenState(token));
        Assert.assertEquals(0, manager.getReadyGlyphCount());
        Assert.assertEquals(0L, manager.getPendingBitmapBytes());
        assertNoPublishedMetadata(tables, 'A');
    }

    @Test
    public void bitmapDrainStopsAtByteBudgetWithoutLosingQueuedRecord() {
        GlyphPageManager manager = budgetedManager(new AtomicLong(0L), 8, 1000L, 256L);
        manager.getRuntimeTables().normalPages[0] = new NoGlGlyphPage(64);
        GlyphRequestToken first = queue(manager, 'A');
        GlyphRequestToken second = queue(manager, 'B');

        manager.flushPendingUploads(10);

        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(first));
        Assert.assertEquals(GlyphState.UPLOAD_QUEUED, manager.getTokenState(second));
        Assert.assertEquals(1, manager.getPendingUploadCount());
        Assert.assertEquals(256L, manager.getPendingBitmapBytes());
    }

    @Test
    public void bitmapDrainStopsAtMonotonicTimeBudget() {
        AtomicLong now = new AtomicLong(0L);
        GlyphPageManager manager = budgetedManager(now, 8, 100L, 1024L);
        manager.getRuntimeTables().normalPages[0] = new ClockAdvancingUploadGlyphPage(now, 100L);
        GlyphRequestToken first = queue(manager, 'A');
        GlyphRequestToken second = queue(manager, 'B');

        manager.flushPendingUploads(10);

        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(first));
        Assert.assertEquals(GlyphState.UPLOAD_QUEUED, manager.getTokenState(second));
        Assert.assertEquals(1, manager.getPendingUploadCount());
    }

    @Test
    public void atlasPressureSettlesOutsideMailboxAndRemainsRecoverable() {
        AtomicLong now = new AtomicLong(0L);
        GlyphPageManager manager = budgetedManager(now, 1, 1000L, 1024L * 1024L);
        manager.getRuntimeTables().normalPages[0] = new NoGlGlyphPage(64);
        GlyphRequestToken resident = manager.claimRequest(1, 'A', FontType.NORMAL);
        Assert.assertTrue(manager.markRasterizing(resident));
        Assert.assertTrue(manager.queueUpload(result(resident, 64, 64)));
        manager.flushPendingUploads(1);
        GlyphRequestToken pressured = queue(manager, 'B');

        manager.flushPendingUploads(1);

        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(resident));
        Assert.assertEquals(GlyphState.ABSENT, manager.getTokenState(pressured));
        Assert.assertTrue(manager.isAtlasPressure(FontType.NORMAL));
        Assert.assertEquals(1, manager.getResidentAtlasPageCount());
        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertEquals(0L, manager.getPendingBitmapBytes());
        Assert.assertNull(manager.claimRequest(1, 'B', FontType.NORMAL));
        long[] recoverable = manager.snapshotRecoverableRequests();
        Assert.assertEquals(1, recoverable.length);
        Assert.assertEquals('B', GlyphPageManager.unpackRecoverableCodepoint(recoverable[0]));
        assertNoPublishedMetadata(manager.getRuntimeTables(), 'B');
    }

    @Test
    public void atlasPageByteEstimateIncludesFullMipChain() {
        Assert.assertEquals(84L, GlyphPageManager.atlasPageBytes(4));
        Assert.assertEquals(21844L, GlyphPageManager.atlasPageBytes(64));
    }

    @Test
    public void atlasByteCapIncludesMipChainBeforeActivatingSecondPage() {
        AtomicLong now = new AtomicLong(0L);
        GlyphPageManager manager = budgetedManager(now, 8, 32768L, 1000L, 1024L * 1024L);
        manager.getRuntimeTables().normalPages[0] = new NoGlGlyphPage(64);
        GlyphRequestToken resident = manager.claimRequest(1, 'A', FontType.NORMAL);
        Assert.assertTrue(manager.markRasterizing(resident));
        Assert.assertTrue(manager.queueUpload(result(resident, 64, 64)));
        manager.flushPendingUploads(1);
        GlyphRequestToken pressured = manager.claimRequest(1, 'B', FontType.NORMAL);
        Assert.assertTrue(manager.markRasterizing(pressured));
        Assert.assertTrue(manager.queueUpload(result(pressured, 64, 64)));

        manager.flushPendingUploads(1);

        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(resident));
        Assert.assertEquals(GlyphState.ABSENT, manager.getTokenState(pressured));
        Assert.assertTrue(manager.isAtlasPressure(FontType.NORMAL));
        Assert.assertEquals(1, manager.getResidentAtlasPageCount());
    }

    @Test
    public void visibleMailboxReserveRejectsForegroundWithoutBlockingPublisher() {
        GlyphPageManager manager = manager(1, 3, 1024L, 1, 128L);
        GlyphRequestToken first = rasterizing(manager, 'A', 2);
        GlyphRequestToken second = rasterizing(manager, 'B', 2);
        GlyphRequestToken rejected = rasterizing(manager, 'C', 2);
        GlyphRequestToken visible = rasterizing(manager, 'V', 3);
        Assert.assertTrue(manager.queueUpload(noBitmapResult(first)));
        Assert.assertTrue(manager.queueUpload(noBitmapResult(second)));

        Assert.assertFalse(manager.queueUpload(noBitmapResult(rejected)));
        Assert.assertTrue(manager.queueUpload(noBitmapResult(visible)));

        Assert.assertEquals(3, manager.getPendingUploadCount());
        Assert.assertEquals(GlyphState.FAILED, manager.getTokenState(rejected));
        Assert.assertEquals(0, manager.getBlockedPublisherCount());
        Assert.assertEquals(1L, manager.getMailboxRejectedCount());
    }

    @Test
    public void nonVisibleMailboxPressureDoesNotBlockVisibleDemandBehindSingleWorker() throws Exception {
        GlyphPageManager manager = manager(1, 2, 1024L * 1024L, 1, 0L);
        queueNoBitmap(manager, 'A', 2);
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        FirstBlockingMatcher matcher = new FirstBlockingMatcher(catalog, cache);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(matcher, manager, cache, manager::queueUpload);

        dispatcher.submit(new GlyphGenerationTask(1, 'B', FontType.NORMAL, 32, GlyphGenerationPriority.LOW));
        Assert.assertTrue(matcher.firstEntered.await(5L, TimeUnit.SECONDS));
        dispatcher.submit(new GlyphGenerationTask(1, 'V', FontType.NORMAL, 32, GlyphGenerationPriority.HIGH));
        matcher.releaseFirst.countDown();

        awaitState(manager, 'B', GlyphState.FAILED);
        awaitState(manager, 'V', GlyphState.UPLOAD_QUEUED);
        Assert.assertEquals(2, manager.getPendingUploadCount());
        Assert.assertEquals(0, manager.getBlockedPublisherCount());
        dispatcher.reset();
        manager.discardPendingUploads();
    }

    @Test
    public void terminalSettlementWakesMailboxPublisherWithoutDrain() throws Exception {
        GlyphPageManager manager = manager(1, 1, 1024L, 0, 0L);
        queueNoBitmap(manager, 'A', 2);
        GlyphRequestToken waiting = rasterizing(manager, 'B', 3);
        AtomicBoolean accepted = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread publisher = publisherThread(manager, noBitmapResult(waiting), accepted, failure);
        publisher.start();
        awaitBlockedPublishers(manager, 1);

        Assert.assertTrue(manager.markCancelled(waiting, GlyphState.RASTERIZING));
        publisher.join(TimeUnit.SECONDS.toMillis(5L));

        Assert.assertFalse(publisher.isAlive());
        Assert.assertNull(failure.get());
        Assert.assertFalse(accepted.get());
        Assert.assertEquals(0, manager.getBlockedPublisherCount());
    }

    @Test
    public void mailboxDrainsByPriorityThenSequence() {
        GlyphPageManager manager = manager(1, 5, 1024L, 0, 0L);
        GlyphRequestToken warmup = queueNoBitmap(manager, 'W', 0);
        GlyphRequestToken prefetch = queueNoBitmap(manager, 'P', 1);
        GlyphRequestToken foreground = queueNoBitmap(manager, 'F', 2);
        GlyphRequestToken visibleFirst = queueNoBitmap(manager, 'A', 3);
        GlyphRequestToken visibleSecond = queueNoBitmap(manager, 'B', 3);

        assertNextUpload(manager, visibleFirst);
        assertNextUpload(manager, visibleSecond);
        assertNextUpload(manager, foreground);
        assertNextUpload(manager, prefetch);
        assertNextUpload(manager, warmup);
        Assert.assertEquals(0, manager.getPendingUploadCount());
    }

    @Test
    public void mailboxAgingReordersOnlyAtExactManualClockBoundary() {
        Assert.assertEquals('V', firstMailboxUploadAt(299L));
        Assert.assertEquals('W', firstMailboxUploadAt(300L));
    }

    @Test
    public void uploadQueuedDemandPromotionReordersMailboxWithoutNewToken() {
        GlyphPageManager manager = manager(1, 3, 1024L, 0, 0L);
        GlyphRequestToken warmup = queueNoBitmap(manager, 'W', 0);
        GlyphRequestToken foreground = queueNoBitmap(manager, 'F', 2);

        Assert.assertSame(warmup, manager.promoteDemand(1, 'W', FontType.NORMAL, 3));
        Assert.assertNull(manager.claimRequest(1, 'W', FontType.NORMAL));
        assertNextUpload(manager, warmup);
        Assert.assertEquals(GlyphState.UPLOAD_QUEUED, manager.getTokenState(foreground));
    }

    @Test
    public void oversizedBitmapIsRejectedWithoutConsumingMailboxCapacity() {
        GlyphPageManager manager = manager(1, 2, 256L, 0, 0L);
        GlyphRequestToken token = rasterizing(manager, 'A', 3);

        Assert.assertFalse(manager.queueUpload(result(token, 9, 8)));

        Assert.assertEquals(GlyphState.FAILED, manager.getTokenState(token));
        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertEquals(0L, manager.getPendingBitmapBytes());
        Assert.assertEquals(1L, manager.getMailboxRejectedCount());
    }

    @Test
    public void nonVisibleBitmapLargerThanItsPartitionIsRejectedInsteadOfWaitingForever() {
        GlyphPageManager manager = manager(1, 2, 512L, 0, 256L);
        GlyphRequestToken foreground = rasterizing(manager, 'F', 2);
        GlyphRequestToken promoted = rasterizing(manager, 'V', 2);

        Assert.assertFalse(manager.queueUpload(result(foreground, 9, 8)));
        Assert.assertSame(promoted, manager.promoteDemand(1, 'V', FontType.NORMAL, 3));
        Assert.assertTrue(manager.queueUpload(result(promoted, 9, 8)));

        Assert.assertEquals(GlyphState.FAILED, manager.getTokenState(foreground));
        Assert.assertEquals(GlyphState.UPLOAD_QUEUED, manager.getTokenState(promoted));
        Assert.assertEquals(288L, manager.getPendingBitmapBytes());
        Assert.assertEquals(1L, manager.getMailboxRejectedCount());
    }

    @Test
    public void mailboxClockFailureOccursBeforeCapacityReservation() {
        GlyphPageManager manager = new GlyphPageManager(2, 1024L, 0, 0L, 100L, () -> {
            throw new IllegalStateException("clock failure");
        });
        manager.setRuntimeVersion(1);
        GlyphRequestToken token = rasterizing(manager, 'T', 3);

        try {
            manager.queueUpload(noBitmapResult(token));
            Assert.fail("clock 异常必须传播");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("clock failure", expected.getMessage());
        }

        Assert.assertEquals(GlyphState.RASTERIZING, manager.getTokenState(token));
        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertEquals(0L, manager.getPendingBitmapBytes());
        Assert.assertTrue(manager.markCancelled(token, GlyphState.RASTERIZING));
    }

    @Test
    public void lazyLifecycleResetGatesStaleGeometryAndInvalidatesOldTokens() {
        GlyphPageManager manager = initializedManager(1);
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        tables.normalPages[0] = new NoGlGlyphPage();
        tables.widthNormal['A'] = 7.25F;
        tables.matchedFontNormal['A'] = 0;
        GlyphRequestToken resident = queue(manager, 'A');
        manager.flushPendingUploads(1);
        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(resident));
        Assert.assertEquals(8, tables.slotWidthNormal['A']);

        // reload 换 generation：惰性清理只重置门控数组
        manager.setGeneration(2, new FontRuntimeSettings(3, 64.0D, 10.0D, 4.0D, 1.0D, false,
                new String[0], FontCharacterRuleSet.empty()));

        Assert.assertEquals(GlyphState.ABSENT, manager.getState('A', FontType.NORMAL));
        Assert.assertEquals(GlyphRuntimeTables.LOCATION_NOT_READY, manager.getPackedLocation('A', FontType.NORMAL));
        Assert.assertTrue("宽度缓存跨 generation 失效", Float.isNaN(tables.widthNormal['A']));
        Assert.assertEquals(GlyphRuntimeTables.FONT_INDEX_UNRESOLVED, tables.matchedFontNormal['A']);
        Assert.assertNull("旧 generation token 必须失效", manager.getTokenState(resident));
        // 几何数组保留旧值，但 location 门控使渲染侧不可见
        Assert.assertEquals(8, tables.slotWidthNormal['A']);

        // 新 generation 重新 claim 时按需重置几何，再上传得到新几何
        tables.normalPages[0] = new NoGlGlyphPage(2, 4096);
        GlyphRequestToken retried = manager.claimRequest(2, 'A', FontType.NORMAL);
        Assert.assertNotNull(retried);
        Assert.assertEquals(0, tables.slotWidthNormal['A']);
        Assert.assertTrue(manager.markRasterizing(retried));
        Assert.assertTrue(manager.queueUpload(result(retried)));
        manager.flushPendingUploads(1);
        Assert.assertEquals(GlyphState.RESIDENT, manager.getTokenState(retried));
        Assert.assertEquals(8, tables.slotWidthNormal['A']);
        Assert.assertEquals(0, GlyphRuntimeTables.unpackSlotIndex(tables.locationNormal['A']));
    }

    @Test
    public void dispatcherResetInterruptsBackpressuredPublisherAndSettlesToken() throws Exception {
        GlyphPageManager manager = manager(1, 1, 1024L * 1024L, 0, 0L);
        GlyphRequestToken filling = queueNoBitmap(manager, 'A', 2);
        FontCatalog catalog = new FontCatalog();
        DerivedFontCache cache = new DerivedFontCache(catalog);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(new AlwaysMatcher(catalog, cache), manager, cache, manager::queueUpload);

        dispatcher.submit(new GlyphGenerationTask(1, 'B', FontType.NORMAL, 32, GlyphGenerationPriority.HIGH));
        awaitBlockedPublishers(manager, 1);
        dispatcher.reset();

        Assert.assertEquals(GlyphState.CANCELLED_STALE, manager.getState('B', FontType.NORMAL));
        Assert.assertEquals(0, manager.getBlockedPublisherCount());
        Assert.assertEquals(0, dispatcher.getActiveDemandCount());
        manager.discardPendingUploads();
        Assert.assertEquals(GlyphState.CANCELLED_STALE, manager.getTokenState(filling));
        Assert.assertEquals(0L, manager.getPendingBitmapBytes());
    }

    @Test
    public void interruptedMailboxWaitPreservesPublisherInterruptStatus() throws Exception {
        GlyphPageManager manager = manager(1, 1, 1024L, 0, 0L);
        queueNoBitmap(manager, 'A', 2);
        GlyphRequestToken waiting = rasterizing(manager, 'B', 3);
        AtomicBoolean accepted = new AtomicBoolean(false);
        AtomicBoolean interruptPreserved = new AtomicBoolean(false);
        Thread publisher = new Thread(() -> {
            accepted.set(manager.queueUpload(noBitmapResult(waiting)));
            interruptPreserved.set(Thread.currentThread().isInterrupted());
        }, "glyph-mailbox-interrupt-test");
        publisher.start();
        awaitBlockedPublishers(manager, 1);

        publisher.interrupt();
        publisher.join(TimeUnit.SECONDS.toMillis(5L));

        Assert.assertFalse(publisher.isAlive());
        Assert.assertFalse(accepted.get());
        Assert.assertTrue(interruptPreserved.get());
        Assert.assertTrue(manager.markCancelled(waiting, GlyphState.RASTERIZING));
    }

    private static GlyphPageManager manager(int generation) {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(generation);
        return manager;
    }

    private static GlyphPageManager manager(int generation, int maxRecords, long maxBytes, int visibleRecordReserve,
            long visibleBytesReserve) {
        GlyphPageManager manager = new GlyphPageManager(maxRecords, maxBytes, visibleRecordReserve,
                visibleBytesReserve, TimeUnit.MILLISECONDS.toNanos(500L), System::nanoTime);
        manager.setRuntimeVersion(generation);
        return manager;
    }

    private static int firstMailboxUploadAt(long releaseNanos) {
        AtomicLong now = new AtomicLong(0L);
        GlyphPageManager manager = new GlyphPageManager(3, 1024L, 0, 0L, 100L, now::get);
        manager.setRuntimeVersion(1);
        GlyphRequestToken warmup = queueNoBitmap(manager, 'W', 0);
        now.set(releaseNanos);
        GlyphRequestToken visible = queueNoBitmap(manager, 'V', 3);

        manager.flushPendingUploads(1);

        return manager.getTokenState(warmup) == GlyphState.NO_BITMAP
                ? warmup.getCodepoint() : visible.getCodepoint();
    }

    private static GlyphPageManager initializedManager(int generation) {
        GlyphPageManager manager = manager(generation);
        FontRuntimeSettings settings = new FontRuntimeSettings(3, 64.0D, 10.0D, 4.0D, 1.0D, false,
                new String[0], FontCharacterRuleSet.empty());
        manager.setGeneration(generation, settings);
        manager.initialize();
        return manager;
    }

    private static GlyphPageManager initializedManager(int generation, int maxRecords, long maxBytes,
            int visibleRecordReserve, long visibleBytesReserve) {
        GlyphPageManager manager = manager(generation, maxRecords, maxBytes, visibleRecordReserve,
                visibleBytesReserve);
        FontRuntimeSettings settings = new FontRuntimeSettings(3, 64.0D, 10.0D, 4.0D, 1.0D, false,
                new String[0], FontCharacterRuleSet.empty());
        manager.setGeneration(generation, settings);
        manager.initialize();
        return manager;
    }

    private static GlyphPageManager budgetedManager(AtomicLong now, int maxResidentPages, long maxDrainNanos,
            long maxDrainBytes) {
        GlyphPageManager manager = new GlyphPageManager(4, 1024L * 1024L, 0, 0L, 100L,
                maxResidentPages, maxDrainNanos, maxDrainBytes, now::get);
        manager.setRuntimeVersion(1);
        FontRuntimeSettings settings = new FontRuntimeSettings(3, 1.0D, 10.0D, 4.0D, 1.0D, false,
                new String[0], FontCharacterRuleSet.empty());
        manager.setGeneration(1, settings);
        manager.initialize();
        return manager;
    }

    private static GlyphPageManager budgetedManager(AtomicLong now, int maxResidentPages, long maxResidentBytes,
            long maxDrainNanos, long maxDrainBytes) {
        GlyphPageManager manager = new GlyphPageManager(4, 1024L * 1024L, 0, 0L, 100L,
                maxResidentPages, maxResidentBytes, maxDrainNanos, maxDrainBytes, now::get);
        manager.setRuntimeVersion(1);
        FontRuntimeSettings settings = new FontRuntimeSettings(3, 1.0D, 10.0D, 4.0D, 1.0D, false,
                new String[0], FontCharacterRuleSet.empty());
        manager.setGeneration(1, settings);
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
        return result(token, 8, 8);
    }

    private static GlyphGenerationResult result(GlyphRequestToken token, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        GlyphInfo glyphInfo = new GlyphInfo(token.getCodepoint(), width, height, 6.0F,
                (float) width, (float) height, false);
        return new GlyphGenerationResult(token, image, glyphInfo);
    }

    private static GlyphGenerationResult noBitmapResult(GlyphRequestToken token) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        GlyphInfo glyphInfo = new GlyphInfo(token.getCodepoint(), 8, 8, 6.0F, 6.0F, 2.0F, 0.0F,
                0.0F, 0.0F, 0, 0, 0, 0, 6, 0, 0, false, false);
        return new GlyphGenerationResult(token, image, glyphInfo);
    }

    private static GlyphRequestToken rasterizing(GlyphPageManager manager, int codepoint, int priority) {
        GlyphRequestToken token = manager.claimRequest(1, codepoint, FontType.NORMAL, priority);
        Assert.assertNotNull(token);
        Assert.assertTrue(manager.markRasterizing(token));
        return token;
    }

    private static GlyphRequestToken queueNoBitmap(GlyphPageManager manager, int codepoint, int priority) {
        GlyphRequestToken token = rasterizing(manager, codepoint, priority);
        Assert.assertTrue(manager.queueUpload(noBitmapResult(token)));
        return token;
    }

    private static Thread publisherThread(GlyphPageManager manager, GlyphGenerationResult result,
            AtomicBoolean accepted, AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                accepted.set(manager.queueUpload(result));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "glyph-mailbox-publisher-test");
    }

    private static void awaitBlockedPublishers(GlyphPageManager manager, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (System.nanoTime() < deadline) {
            if (manager.getBlockedPublisherCount() == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        Assert.fail("等待 mailbox publisher 阻塞超时，expected=" + expected + " actual="
                + manager.getBlockedPublisherCount());
    }

    private static void awaitInFlightUploads(GlyphPageManager manager, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (System.nanoTime() < deadline) {
            if (manager.getInFlightUploadCount() == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        Assert.fail("等待 in-flight upload 数量超时，expected=" + expected + " actual="
                + manager.getInFlightUploadCount());
    }

    private static void awaitState(GlyphPageManager manager, int codepoint, GlyphState expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        while (System.nanoTime() < deadline) {
            if (manager.getState(codepoint, FontType.NORMAL) == expected) {
                return;
            }
            Thread.sleep(10L);
        }
        Assert.fail("等待 glyph 状态超时，expected=" + expected + " actual="
                + manager.getState(codepoint, FontType.NORMAL));
    }

    private static void assertNextUpload(GlyphPageManager manager, GlyphRequestToken expected) {
        manager.flushPendingUploads(1);
        Assert.assertEquals(GlyphState.NO_BITMAP, manager.getTokenState(expected));
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
            this(1, 4096);
        }

        private NoGlGlyphPage(int textureSize) {
            this(1, textureSize);
        }

        private NoGlGlyphPage(int runtimeVersion, int textureSize) {
            super(runtimeVersion, 0, textureSize, 64, 3);
        }

        @Override
        void upload(GlyphSlot slot, GlyphUploadPlan plan) {
            uploaded = true;
        }

        @Override
        void rollbackUploadedRegion(GlyphSlot slot) {
        }

        @Override
        void beginBatchUpload() {
        }

        @Override
        void endBatchUpload() {
        }
    }

    private static final class RetryUploadGlyphPage extends NoGlGlyphPage {

        private final List<Integer> slotIndexes = new ArrayList<Integer>();
        private boolean failNext = true;

        @Override
        void upload(GlyphSlot slot, GlyphUploadPlan plan) {
            slotIndexes.add(Integer.valueOf(slot.getSlotIndex()));
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("upload failure");
            }
        }
    }

    private static final class InspectingUploadGlyphPage extends NoGlGlyphPage {

        private final GlyphPageManager manager;
        private long bitmapBytesDuringUpload;
        private int inFlightDuringUpload;

        private InspectingUploadGlyphPage(GlyphPageManager manager) {
            this.manager = manager;
        }

        @Override
        void upload(GlyphSlot slot, GlyphUploadPlan plan) {
            bitmapBytesDuringUpload = manager.getPendingBitmapBytes();
            inFlightDuringUpload = manager.getInFlightUploadCount();
        }
    }

    private static final class ClockAdvancingUploadGlyphPage extends NoGlGlyphPage {

        private final AtomicLong now;
        private final long advanceTo;

        private ClockAdvancingUploadGlyphPage(AtomicLong now, long advanceTo) {
            super(64);
            this.now = now;
            this.advanceTo = advanceTo;
        }

        @Override
        void upload(GlyphSlot slot, GlyphUploadPlan plan) {
            now.set(advanceTo);
        }
    }

    private static final class BlockingUploadGlyphPage extends NoGlGlyphPage {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        void upload(GlyphSlot slot, GlyphUploadPlan plan) {
            entered.countDown();
            try {
                if (!release.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("等待 discard epoch 释放超时");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待 discard epoch 被中断", exception);
            }
        }
    }

    private static final class FailingUploadGlyphPage extends NoGlGlyphPage {

        @Override
        void upload(GlyphSlot slot, GlyphUploadPlan plan) {
            throw new IllegalStateException("upload failure");
        }
    }

    private static final class OwnedCloseFailGlyphPage extends NoGlGlyphPage {

        private boolean ownsTexture;

        private OwnedCloseFailGlyphPage() {
            super(64);
        }

        @Override
        void upload(GlyphSlot slot, GlyphUploadPlan plan) {
            ownsTexture = true;
            throw new IllegalStateException("upload failure");
        }

        @Override
        public void close() {
            throw new IllegalStateException("close failure");
        }

        @Override
        boolean hasTextureOwnership() {
            return ownsTexture;
        }
    }

    private static final class ErrorUploadGlyphPage extends NoGlGlyphPage {

        @Override
        void upload(GlyphSlot slot, GlyphUploadPlan plan) {
            throw new AssertionError("upload error");
        }
    }

    private static final class QuarantiningGlyphPage extends GlyphPage {

        private final GlyphPageManager manager;
        private final GlyphPageVariableSlotPackingTest.FakeGlApi gl;
        private boolean failNextStaleRollback;

        private QuarantiningGlyphPage(GlyphPageManager manager,
                GlyphPageVariableSlotPackingTest.FakeGlApi gl) {
            super(1, 0, 64, 64, 3, gl);
            this.manager = manager;
            this.gl = gl;
        }

        @Override
        void upload(GlyphSlot slot, GlyphUploadPlan plan) {
            super.upload(slot, plan);
            if (failNextStaleRollback) {
                failNextStaleRollback = false;
                Assert.assertTrue(manager.markCancelled(plan.getToken(), GlyphState.UPLOADING));
                gl.failNextSubImage();
            }
        }
    }

    private static final class CancellingUploadGlyphPage extends NoGlGlyphPage {

        private final GlyphPageManager manager;
        private final List<Integer> slotIndexes = new ArrayList<Integer>();
        private boolean cancelNext = true;
        private int rollbackCount;

        private CancellingUploadGlyphPage(GlyphPageManager manager) {
            this.manager = manager;
        }

        @Override
        void upload(GlyphSlot slot, GlyphUploadPlan plan) {
            slotIndexes.add(Integer.valueOf(slot.getSlotIndex()));
            if (cancelNext) {
                cancelNext = false;
                Assert.assertTrue(manager.markCancelled(plan.getToken(), GlyphState.UPLOADING));
            }
        }

        @Override
        void rollbackUploadedRegion(GlyphSlot slot) {
            rollbackCount++;
        }
    }

    private static final class AlwaysMatcher extends FontMatcher {

        private AlwaysMatcher(FontCatalog catalog, DerivedFontCache cache) {
            super(catalog, cache);
        }

        @Override
        public int matchFontIndex(int runtimeVersion, int codepoint, FontType fontType) {
            return 0;
        }

        @Override
        public Font getDerivedFont(int runtimeVersion, int fontIndex, FontType fontType, int glyphSize) {
            return new Font("Dialog", Font.PLAIN, glyphSize);
        }
    }

    private static final class FirstBlockingMatcher extends FontMatcher {

        private final AtomicInteger invocationCount = new AtomicInteger(0);
        private final CountDownLatch firstEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);

        private FirstBlockingMatcher(FontCatalog catalog, DerivedFontCache cache) {
            super(catalog, cache);
        }

        @Override
        public int matchFontIndex(int runtimeVersion, int codepoint, FontType fontType) {
            if (invocationCount.incrementAndGet() == 1) {
                firstEntered.countDown();
                try {
                    if (!releaseFirst.await(5L, TimeUnit.SECONDS)) {
                        throw new AssertionError("等待首个 mailbox worker 释放超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("等待首个 mailbox worker 释放被中断", exception);
                }
            }
            return 0;
        }

        @Override
        public Font getDerivedFont(int runtimeVersion, int fontIndex, FontType fontType, int glyphSize) {
            return new Font("Dialog", Font.PLAIN, glyphSize);
        }
    }
}
