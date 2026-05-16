package club.heiqi.uilib.font.page;

import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphInfo;

/**
 * 字符运行时版本隔离测试。
 */
public class GlyphRuntimeVersionIsolationTest {

    /**
     * 验证旧运行时生成结果不会进入新运行时的上传队列。
     */
    @Test
    public void shouldRejectStaleGenerationResultBeforeUploadQueue() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(2);

        manager.queueUpload(result(1, 'A'));

        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertEquals(GlyphState.NEW, manager.getState('A', FontType.NORMAL));
    }

    /**
     * 验证连续版本切换后，旧版本状态不会阻止新版本同码点重新生成。
     */
    @Test
    public void shouldAllowSameCodepointAfterRuntimeVersionChanges() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);

        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));
        Assert.assertEquals(GlyphState.GENERATING, manager.getState('A', FontType.NORMAL));

        manager.setRuntimeVersion(2);

        Assert.assertEquals(GlyphState.NEW, manager.getState('A', FontType.NORMAL));
        Assert.assertTrue(manager.tryMarkGenerating(2, 'A', FontType.NORMAL));
        Assert.assertEquals(GlyphState.GENERATING, manager.getState('A', FontType.NORMAL));
    }

    /**
     * 验证生成中字符会进入可恢复请求快照，供 reload 后重新提交。
     */
    @Test
    public void shouldSnapshotGeneratingGlyphAsRecoverableRequest() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);

        Assert.assertTrue(manager.tryMarkGenerating(1, '中', FontType.NORMAL));

        long[] requests = manager.snapshotRecoverableRequests();
        Assert.assertEquals(1, requests.length);
        Assert.assertEquals('中', GlyphPageManager.unpackRecoverableCodepoint(requests[0]));
        Assert.assertEquals(FontType.NORMAL, GlyphPageManager.unpackRecoverableFontType(requests[0]));
    }

    /**
     * 验证任务被取消时不会让字符永久卡在生成中，也不会留下可恢复请求对象。
     */
    @Test
    public void shouldReleaseGeneratingStateWhenGenerationCancelled() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);

        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));
        manager.markGenerationCancelled(1, 'A', FontType.NORMAL);

        Assert.assertEquals(GlyphState.NEW, manager.getState('A', FontType.NORMAL));
        Assert.assertEquals(0, manager.snapshotRecoverableRequests().length);
    }

    /**
     * 验证已取消的旧结果不能重新进入待上传队列。
     */
    @Test
    public void shouldRejectCancelledGenerationResultBeforeUploadQueue() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);

        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));
        manager.markGenerationCancelled(1, 'A', FontType.NORMAL);
        manager.queueUpload(result(1, 'A'));

        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertEquals(GlyphState.NEW, manager.getState('A', FontType.NORMAL));
    }

    /**
     * 验证结果必须匹配当前生成请求编号才允许进入上传队列。
     */
    @Test
    public void shouldRejectMismatchedGenerationIdBeforeUploadQueue() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);

        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));
        long generationId = manager.getGenerationId(1, 'A', FontType.NORMAL);
        manager.queueUpload(result(1, generationId + 1L, 'A'));

        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertEquals(GlyphState.GENERATING, manager.getState('A', FontType.NORMAL));
    }

    /**
     * 验证已取消的待上传记录刷新时不会写入字符页。
     */
    @Test
    public void shouldSkipCancelledPendingUploadWhenFlushing() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);

        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));
        long generationId = manager.getGenerationId(1, 'A', FontType.NORMAL);
        manager.queueUpload(result(1, generationId, 'A'));
        manager.markGenerationCancelled(1, 'A', FontType.NORMAL);
        manager.flushPendingUploads(1);

        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertEquals(0, manager.getReadyGlyphCount());
        Assert.assertEquals(GlyphState.NEW, manager.getState('A', FontType.NORMAL));
    }

    /**
     * 验证旧 pending 不能在同码点新请求进入待上传后被当作当前上传记录处理。
     */
    @Test
    public void shouldKeepOldPendingIsolatedAfterSameCodepointResubmitted() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);

        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));
        long oldGenerationId = manager.getGenerationId(1, 'A', FontType.NORMAL);
        manager.queueUpload(result(1, oldGenerationId, 'A'));
        manager.markGenerationCancelled(1, 'A', FontType.NORMAL);
        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));
        long newGenerationId = manager.getGenerationId(1, 'A', FontType.NORMAL);
        manager.queueUpload(result(1, newGenerationId, 'A'));

        Assert.assertEquals(2, manager.getPendingUploadCount());
        Assert.assertEquals(GlyphState.UPLOAD_PENDING, manager.getState('A', FontType.NORMAL));
        Assert.assertNotEquals(oldGenerationId, newGenerationId);
    }

    /**
     * 验证同一字符进入待上传后，重复生成结果不会制造重复页槽。
     */
    @Test
    public void shouldRejectDuplicateUploadResultWhilePending() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);

        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));
        long generationId = manager.getGenerationId(1, 'A', FontType.NORMAL);
        manager.queueUpload(result(1, generationId, 'A'));
        manager.queueUpload(result(1, generationId, 'A'));

        Assert.assertEquals(1, manager.getPendingUploadCount());
        Assert.assertEquals(GlyphState.UPLOAD_PENDING, manager.getState('A', FontType.NORMAL));
    }

    /**
     * 验证旧运行时未完成请求在 reset 后可按新版本重新提交。
     */
    @Test
    public void shouldAllowRecoverableRequestToBeResubmittedAfterReset() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);

        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));

        long oldRequest = manager.snapshotRecoverableRequests()[0];
        manager.setRuntimeVersion(2);
        manager.reset();

        Assert.assertEquals(GlyphState.NEW, manager.getState('A', FontType.NORMAL));
        Assert.assertTrue(manager.tryMarkGenerating(2, GlyphPageManager.unpackRecoverableCodepoint(oldRequest),
                GlyphPageManager.unpackRecoverableFontType(oldRequest)));
        Assert.assertEquals(GlyphState.GENERATING, manager.getState('A', FontType.NORMAL));
    }

    /**
     * 验证重载屏障可显式丢弃旧运行时等待上传的字形结果。
     */
    @Test
    public void shouldDiscardPendingUploadsDuringReloadBarrier() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);

        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));
        long generationId = manager.getGenerationId(1, 'A', FontType.NORMAL);
        manager.queueUpload(result(1, generationId, 'A'));

        Assert.assertEquals(1, manager.getPendingUploadCount());
        manager.discardPendingUploads();

        Assert.assertEquals(0, manager.getPendingUploadCount());
    }

    private static GlyphGenerationResult result(int runtimeVersion, int codepoint) {
        return result(runtimeVersion, 0L, codepoint);
    }

    private static GlyphGenerationResult result(int runtimeVersion, long generationId, int codepoint) {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        GlyphInfo glyphInfo = new GlyphInfo(codepoint, 8, 8, 6.0F, 6.0F, 8.0F, false);
        return new GlyphGenerationResult(runtimeVersion, generationId, codepoint, FontType.NORMAL, image, glyphInfo);
    }
}
