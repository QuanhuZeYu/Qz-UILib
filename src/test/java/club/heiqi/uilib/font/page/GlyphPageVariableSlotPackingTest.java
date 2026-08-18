package club.heiqi.uilib.font.page;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/**
 * 字符页可变 slot 装箱测试。
 */
public class GlyphPageVariableSlotPackingTest {

    /**
     * 不同尺寸的 slot 应按 shelf packing 记录真实位置与大小。
     */
    @Test
    public void shouldAllocateVariableSlotsWithRealBounds() {
        GlyphPage page = new GlyphPage(1, 0, 128, 64);

        GlyphPage.GlyphSlot first = page.allocateSlot(30, 40);
        GlyphPage.GlyphSlot second = page.allocateSlot(50, 20);
        GlyphPage.GlyphSlot third = page.allocateSlot(70, 30);

        Assert.assertEquals(0, first.getX());
        Assert.assertEquals(0, first.getY());
        Assert.assertEquals(30, first.getWidth());
        Assert.assertEquals(40, first.getHeight());
        Assert.assertEquals(31, second.getX());
        Assert.assertEquals(0, second.getY());
        Assert.assertEquals(0, third.getX());
        Assert.assertEquals(41, third.getY());
    }

    /**
     * 超出纹理页边界的 slot 不应被当前页接收。
     */
    @Test
    public void shouldRejectSlotLargerThanTexture() {
        GlyphPage page = new GlyphPage(1, 0, 64, 64);

        Assert.assertFalse(page.canAllocate(65, 10));
        Assert.assertFalse(page.canAllocate(10, 65));
        Assert.assertTrue(page.canAllocate(64, 64));
    }

    @Test
    public void rolledBackReservationDoesNotAdvanceShelfCursor() {
        GlyphPage page = new GlyphPage(1, 0, 64, 64);
        GlyphPage.SlotReservation reservation = page.reserveSlot(16, 12);

        reservation.commit();
        reservation.rollback();
        GlyphPage.GlyphSlot reused = page.allocateSlot(16, 12);

        Assert.assertEquals(0, reused.getSlotIndex());
        Assert.assertEquals(0, reused.getX());
        Assert.assertEquals(0, reused.getY());
        Assert.assertEquals(1, page.getCommittedSlotCount());
    }

    @Test
    public void textureInitializationErrorDoesNotPublishTextureId() {
        FakeGlApi gl = new FakeGlApi();
        gl.failTextureAllocation = true;
        GlyphPage page = new GlyphPage(1, 0, 64, 64, 3, gl);
        GlyphPage.GlyphSlot slot = page.allocateSlot(8, 8);
        GlyphRequestToken token = new GlyphRequestToken(1, 1L, 'A', FontType.NORMAL);

        try {
            page.upload(slot, token, new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB));
            Assert.fail("GL texture allocation error 必须中止 upload");
        } catch (GlyphPage.GlyphUploadException expected) {
            Assert.assertEquals("texture_allocate", expected.getPhase());
        }

        Assert.assertEquals(0, page.getTextureId());
        Assert.assertEquals(1, gl.deletedTextureCount);
        Assert.assertEquals(gl.pushAttribCount, gl.popAttribCount);
        Assert.assertEquals(gl.pushClientAttribCount, gl.popClientAttribCount);

        gl.failTextureAllocation = false;
        page.upload(slot, token, new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB));
        Assert.assertTrue(page.getTextureId() > 0);
    }

    /**
     * 上传路径只保存纹理服务器状态与 client unpack state，而非 GL_ALL_ATTRIB_BITS 全量状态。
     */
    @Test
    public void uploadSavesOnlyTextureAndClientPixelStoreState() {
        FakeGlApi gl = new FakeGlApi();
        GlyphPage page = new GlyphPage(1, 0, 64, 64, 3, gl);
        GlyphPage.GlyphSlot slot = page.allocateSlot(8, 8);
        GlyphRequestToken token = new GlyphRequestToken(1, 1L, 'A', FontType.NORMAL);

        page.upload(slot, token, opaqueImage(8, 8));

        Assert.assertEquals(GL11.GL_TEXTURE_BIT, gl.getLastPushAttribMask());
        Assert.assertEquals(GL11.GL_CLIENT_PIXEL_STORE_BIT, gl.getLastPushClientAttribMask());
    }

    @Test
    public void postWriteErrorClearsPixelsBeforeSlotRollback() {
        FakeGlApi gl = new FakeGlApi();
        gl.failNextMipmap = true;
        GlyphPage page = new GlyphPage(1, 0, 64, 64, 3, gl);
        page.allocateSlot(8, 8);
        GlyphPage.SlotReservation reservation = page.reserveSlot(8, 8);
        GlyphRequestToken token = new GlyphRequestToken(1, 1L, 'A', FontType.NORMAL);
        BufferedImage image = opaqueImage(8, 8);

        try {
            page.upload(reservation.getSlot(), token, image);
            Assert.fail("mipmap error 必须中止 upload");
        } catch (GlyphPage.GlyphUploadException expected) {
            Assert.assertEquals("upload_mipmap", expected.getPhase());
        }
        reservation.rollback();
        GlyphPage.GlyphSlot reused = page.allocateSlot(8, 8);

        Assert.assertEquals("一次原写入和一次透明 rollback 写入", 2, gl.texSubImageCount);
        Assert.assertEquals(1, reused.getSlotIndex());
        Assert.assertEquals(9, reused.getX());
        Assert.assertEquals(0, reused.getY());
        Assert.assertTrue(gl.sawNonTransparentUpload);
        Assert.assertTrue(gl.lastSubImageAllZero());
        Assert.assertEquals(2, gl.generateMipmapCount);
        Assert.assertEquals(gl.firstSubImageX, gl.lastSubImageX);
        Assert.assertEquals(gl.firstSubImageY, gl.lastSubImageY);
        Assert.assertEquals(gl.firstSubImageWidth, gl.lastSubImageWidth);
        Assert.assertEquals(gl.firstSubImageHeight, gl.lastSubImageHeight);
    }

    @Test
    public void successfulWriteCanBeClearedBeforePostGlSlotRollback() {
        FakeGlApi gl = new FakeGlApi();
        GlyphPage page = new GlyphPage(1, 0, 64, 64, 3, gl);
        page.allocateSlot(8, 8);
        GlyphPage.SlotReservation reservation = page.reserveSlot(8, 8);
        GlyphRequestToken token = new GlyphRequestToken(1, 1L, 'A', FontType.NORMAL);

        page.upload(reservation.getSlot(), token, opaqueImage(8, 8));
        page.rollbackUploadedRegion(reservation.getSlot());
        reservation.rollback();
        GlyphPage.GlyphSlot reused = page.allocateSlot(8, 8);

        Assert.assertEquals("一次成功写入和一次透明 rollback 写入", 2, gl.texSubImageCount);
        Assert.assertEquals(1, reused.getSlotIndex());
        Assert.assertEquals(9, reused.getX());
        Assert.assertEquals(gl.pushAttribCount, gl.popAttribCount);
        Assert.assertEquals(gl.pushClientAttribCount, gl.popClientAttribCount);
        Assert.assertTrue(gl.sawNonTransparentUpload);
        Assert.assertTrue(gl.lastSubImageAllZero());
        Assert.assertEquals(2, gl.generateMipmapCount);
        Assert.assertEquals(gl.firstSubImageX, gl.lastSubImageX);
        Assert.assertEquals(gl.firstSubImageY, gl.lastSubImageY);
        Assert.assertEquals(gl.firstSubImageWidth, gl.lastSubImageWidth);
        Assert.assertEquals(gl.firstSubImageHeight, gl.lastSubImageHeight);
    }

    @Test
    public void stateRestoreErrorClearsPixelsBeforeSlotRollback() {
        FakeGlApi gl = new FakeGlApi();
        gl.failNextPopAttrib = true;
        GlyphPage page = new GlyphPage(1, 0, 64, 64, 3, gl);
        page.allocateSlot(8, 8);
        GlyphPage.SlotReservation reservation = page.reserveSlot(8, 8);
        GlyphRequestToken token = new GlyphRequestToken(1, 1L, 'A', FontType.NORMAL);

        try {
            page.upload(reservation.getSlot(), token, opaqueImage(8, 8));
            Assert.fail("state restore error 必须中止 upload");
        } catch (GlyphPage.GlyphUploadException expected) {
            Assert.assertEquals("upload_state_restore", expected.getPhase());
        }
        reservation.rollback();
        GlyphPage.GlyphSlot reused = page.allocateSlot(8, 8);

        Assert.assertEquals(2, gl.texSubImageCount);
        Assert.assertTrue(gl.sawNonTransparentUpload);
        Assert.assertTrue(gl.lastSubImageAllZero());
        Assert.assertEquals(2, gl.generateMipmapCount);
        Assert.assertEquals(gl.firstSubImageX, gl.lastSubImageX);
        Assert.assertEquals(gl.firstSubImageY, gl.lastSubImageY);
        Assert.assertEquals(gl.firstSubImageWidth, gl.lastSubImageWidth);
        Assert.assertEquals(gl.firstSubImageHeight, gl.lastSubImageHeight);
        Assert.assertEquals(1, reused.getSlotIndex());
        Assert.assertEquals(9, reused.getX());
        Assert.assertTrue(page.getTextureId() > 0);
    }

    @Test
    public void failedAttribPushDoesNotPopUnpushedHostStack() {
        FakeGlApi gl = new FakeGlApi();
        gl.failNextAttribPush = true;
        GlyphPage page = new GlyphPage(1, 0, 64, 64, 3, gl);
        GlyphPage.GlyphSlot slot = page.allocateSlot(8, 8);
        GlyphRequestToken token = new GlyphRequestToken(1, 1L, 'A', FontType.NORMAL);

        try {
            page.upload(slot, token, new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB));
            Assert.fail("attrib push error 必须中止 upload");
        } catch (GlyphPage.GlyphUploadException expected) {
            Assert.assertEquals("upload_attrib_push", expected.getPhase());
        }

        Assert.assertEquals(1, gl.pushAttribCount);
        Assert.assertEquals(0, gl.popAttribCount);
        Assert.assertEquals(0, gl.pushClientAttribCount);
        Assert.assertEquals(0, gl.popClientAttribCount);
    }

    private static BufferedImage opaqueImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(width / 2, height / 2, 0xFF123456);
        return image;
    }

    static final class FakeGlApi implements GlyphPage.GlApi {

        private int nextTextureId = 1;
        private int pendingError;
        private int deletedTextureCount;
        private int pushAttribCount;
        private int popAttribCount;
        private int pushClientAttribCount;
        private int popClientAttribCount;
        private int lastPushAttribMask;
        private int lastPushClientAttribMask;
        private boolean failTextureAllocation;
        private boolean failNextMipmap;
        private boolean failNextAttribPush;
        private boolean failNextPopAttrib;
        private boolean failNextSubImage;
        private int texSubImageCount;
        private int generateMipmapCount;
        private byte[] lastSubImagePixels = new byte[0];
        private boolean sawNonTransparentUpload;
        private int firstSubImageX;
        private int firstSubImageY;
        private int firstSubImageWidth;
        private int firstSubImageHeight;
        private int lastSubImageX;
        private int lastSubImageY;
        private int lastSubImageWidth;
        private int lastSubImageHeight;

        void failNextMipmap() {
            failNextMipmap = true;
        }

        void failTextureAllocation() {
            failTextureAllocation = true;
        }

        boolean sawNonTransparentUpload() {
            return sawNonTransparentUpload;
        }

        int getTexSubImageCount() {
            return texSubImageCount;
        }

        int getPushAttribCount() {
            return pushAttribCount;
        }

        int getPopAttribCount() {
            return popAttribCount;
        }

        int getPushClientAttribCount() {
            return pushClientAttribCount;
        }

        int getLastPushAttribMask() {
            return lastPushAttribMask;
        }

        int getLastPushClientAttribMask() {
            return lastPushClientAttribMask;
        }

        int getPopClientAttribCount() {
            return popClientAttribCount;
        }

        int getGenerateMipmapCount() {
            return generateMipmapCount;
        }

        int getDeletedTextureCount() {
            return deletedTextureCount;
        }

        void failNextSubImage() {
            failNextSubImage = true;
        }

        boolean lastSubImageAllZero() {
            for (byte value : lastSubImagePixels) {
                if (value != 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void pushAttrib(int mask) {
            pushAttribCount++;
            lastPushAttribMask = mask;
            if (failNextAttribPush) {
                failNextAttribPush = false;
                pendingError = 1282;
            }
        }

        @Override
        public void pushClientAttrib(int mask) {
            pushClientAttribCount++;
            lastPushClientAttribMask = mask;
        }

        @Override
        public void popClientAttrib() {
            popClientAttribCount++;
        }

        @Override
        public void popAttrib() {
            popAttribCount++;
            if (failNextPopAttrib) {
                failNextPopAttrib = false;
                pendingError = 1282;
            }
        }

        @Override
        public int genTexture() {
            return nextTextureId++;
        }

        @Override
        public void bindTexture(int target, int texture) {
        }

        @Override
        public void pixelStore(int parameter, int value) {
        }

        @Override
        public void texImage2D(int target, int level, int internalFormat, int width, int height, int border,
                int format, int type, ByteBuffer pixels) {
            if (failTextureAllocation) {
                pendingError = 1282;
            }
        }

        @Override
        public void texParameter(int target, int parameter, int value) {
        }

        @Override
        public void texSubImage2D(int target, int level, int x, int y, int width, int height, int format, int type,
                ByteBuffer pixels) {
            texSubImageCount++;
            if (texSubImageCount == 1) {
                firstSubImageX = x;
                firstSubImageY = y;
                firstSubImageWidth = width;
                firstSubImageHeight = height;
            }
            lastSubImageX = x;
            lastSubImageY = y;
            lastSubImageWidth = width;
            lastSubImageHeight = height;
            ByteBuffer snapshot = pixels.duplicate();
            lastSubImagePixels = new byte[snapshot.remaining()];
            snapshot.get(lastSubImagePixels);
            for (byte value : lastSubImagePixels) {
                if (value != 0) {
                    sawNonTransparentUpload = true;
                    break;
                }
            }
            if (failNextSubImage) {
                failNextSubImage = false;
                pendingError = 1282;
            }
        }

        @Override
        public void generateMipmap(int target) {
            generateMipmapCount++;
            if (failNextMipmap) {
                failNextMipmap = false;
                pendingError = 1282;
            }
        }

        @Override
        public boolean isTexture(int texture) {
            return texture > 0;
        }

        @Override
        public void deleteTexture(int texture) {
            deletedTextureCount++;
        }

        @Override
        public int getError() {
            int error = pendingError;
            pendingError = 0;
            return error;
        }
    }
}
