package club.heiqi.uilib.font.page;

import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/**
 * 字符页批上传测试：attrib push/pop 与 mipmap 重建按批次结算而非逐 glyph 结算。
 */
public class GlyphPageBatchUploadTest {

    @Test
    public void batchUploadSettlesAttribAndMipmapOnce() {
        GlyphPageVariableSlotPackingTest.FakeGlApi gl = new GlyphPageVariableSlotPackingTest.FakeGlApi();
        GlyphPage page = new GlyphPage(1, 0, 64, 64, 3, gl);
        GlyphPage.GlyphSlot first = page.allocateSlot(8, 8);
        GlyphPage.GlyphSlot second = page.allocateSlot(8, 8);

        page.beginBatchUpload();
        page.uploadInBatch(first, plan('A', 8, 8));
        page.uploadInBatch(second, plan('B', 8, 8));
        page.endBatchUpload();

        Assert.assertEquals(2, gl.getTexSubImageCount());
        Assert.assertEquals(1, gl.getPushAttribCount());
        Assert.assertEquals(1, gl.getPopAttribCount());
        Assert.assertEquals(1, gl.getPushClientAttribCount());
        Assert.assertEquals(1, gl.getPopClientAttribCount());
        Assert.assertEquals(1, gl.getGenerateMipmapCount());
        Assert.assertTrue(page.getTextureId() > 0);
        Assert.assertFalse(page.isBatchActive());
    }

    @Test
    public void batchSubImageFailureClearsRegionAndClosesAllocation() {
        GlyphPageVariableSlotPackingTest.FakeGlApi gl = new GlyphPageVariableSlotPackingTest.FakeGlApi();
        GlyphPage page = new GlyphPage(1, 0, 64, 64, 3, gl);
        GlyphPage.GlyphSlot slot = page.allocateSlot(8, 8);

        page.beginBatchUpload();
        gl.failNextSubImage();
        try {
            page.uploadInBatch(slot, plan('A', 8, 8));
            Assert.fail("批内 subImage 失败必须中止 upload");
        } catch (GlyphPage.GlyphUploadException expected) {
            Assert.assertEquals("batch_upload_pixels", expected.getPhase());
        }
        page.endBatchUpload();

        Assert.assertEquals("一次写入和一次透明清区域写入", 2, gl.getTexSubImageCount());
        Assert.assertTrue(gl.sawNonTransparentUpload());
        Assert.assertTrue(gl.lastSubImageAllZero());
        Assert.assertFalse("清区域成功后页仍可继续分配", page.isAllocationClosed());
        Assert.assertFalse(page.isBatchActive());
        Assert.assertEquals(gl.getPushAttribCount(), gl.getPopAttribCount());
        Assert.assertEquals(gl.getPushClientAttribCount(), gl.getPopClientAttribCount());

        // 清区域成功后页保持可用：下一批上传仍正常。
        GlyphPage.GlyphSlot next = page.allocateSlot(8, 8);
        page.beginBatchUpload();
        page.uploadInBatch(next, plan('B', 8, 8));
        page.endBatchUpload();
        Assert.assertEquals(3, gl.getTexSubImageCount());
        Assert.assertEquals("两次批结算各重建一次 mipmap", 2, gl.getGenerateMipmapCount());
    }

    @Test
    public void batchEndMipmapFailureRestoresStateAndLeavesBatch() {
        GlyphPageVariableSlotPackingTest.FakeGlApi gl = new GlyphPageVariableSlotPackingTest.FakeGlApi();
        GlyphPage page = new GlyphPage(1, 0, 64, 64, 3, gl);
        GlyphPage.GlyphSlot slot = page.allocateSlot(8, 8);

        page.beginBatchUpload();
        page.uploadInBatch(slot, plan('A', 8, 8));
        gl.failNextMipmap();
        try {
            page.endBatchUpload();
            Assert.fail("批 mipmap 失败必须抛出");
        } catch (GlyphPage.GlyphUploadException expected) {
            Assert.assertEquals("batch_mipmap", expected.getPhase());
        }

        Assert.assertFalse(page.isBatchActive());
        Assert.assertEquals(gl.getPushAttribCount(), gl.getPopAttribCount());
        Assert.assertEquals(gl.getPushClientAttribCount(), gl.getPopClientAttribCount());
        try {
            page.uploadInBatch(slot, plan('B', 8, 8));
            Assert.fail("批结束后不能继续批内上传");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("未处于批上传"));
        }
    }

    @Test
    public void beginTextureAllocationFailureRestoresPushedAttributes() {
        GlyphPageVariableSlotPackingTest.FakeGlApi gl = new GlyphPageVariableSlotPackingTest.FakeGlApi();
        gl.failTextureAllocation();
        GlyphPage page = new GlyphPage(1, 0, 64, 64, 3, gl);
        page.allocateSlot(8, 8);

        try {
            page.beginBatchUpload();
            Assert.fail("texture allocation 失败必须中止 begin");
        } catch (GlyphPage.GlyphUploadException expected) {
            Assert.assertEquals("texture_allocate", expected.getPhase());
        }

        Assert.assertFalse(page.isBatchActive());
        Assert.assertEquals(gl.getPushAttribCount(), gl.getPopAttribCount());
        Assert.assertEquals(gl.getPushClientAttribCount(), gl.getPopClientAttribCount());
    }

    @Test
    public void endAfterInBatchCloseOnlyRestoresState() {
        GlyphPageVariableSlotPackingTest.FakeGlApi gl = new GlyphPageVariableSlotPackingTest.FakeGlApi();
        GlyphPage page = new GlyphPage(1, 0, 64, 64, 3, gl);
        GlyphPage.GlyphSlot slot = page.allocateSlot(8, 8);

        page.beginBatchUpload();
        page.uploadInBatch(slot, plan('A', 8, 8));
        page.close();
        page.endBatchUpload();

        Assert.assertFalse(page.isBatchActive());
        Assert.assertEquals(0, gl.getGenerateMipmapCount());
        Assert.assertEquals(gl.getPushAttribCount(), gl.getPopAttribCount());
        Assert.assertEquals(gl.getPushClientAttribCount(), gl.getPopClientAttribCount());
    }

    private static GlyphUploadPlan plan(char codepoint, int slotWidth, int slotHeight) {
        GlyphRequestToken token = new GlyphRequestToken(1, codepoint, codepoint, FontType.NORMAL);
        GlyphInfo glyphInfo = new GlyphInfo(codepoint, 8, 8, 8.0F, 0.0F, 0.0F, 0.0F, 8.0F, 8.0F, slotWidth, slotHeight,
                1, 1, slotHeight, 0, 0, true, false);
        BufferedImage image = new BufferedImage(slotWidth, slotHeight, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(slotWidth / 2, slotHeight / 2, 0xFF123456);
        return GlyphUploadPlan.from(new GlyphGenerationResult(token, image, glyphInfo));
    }
}
