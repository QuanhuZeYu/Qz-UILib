package club.heiqi.uilib.font.page;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import club.heiqi.uilib.font.FontRuntimeDiagnostics;
import club.heiqi.uilib.font.config.FontConfig;

/**
 * 字符页。
 */
public class GlyphPage {

    /**
     * 主线程专用零数据纹理缓冲。
     *
     * <p>{@link #ensureTexture()} 只在渲染主线程调用，因此无并发风险。
     * 按需扩容，避免每次创建新页都分配 + 填零一块 direct buffer。</p>
     */
    private static ByteBuffer renderThreadEmptyBuffer;

    private final int runtimeVersion;
    private final int pageIndex;
    private final int textureSize;
    private final int glyphSize;
    private final int slotGap;
    private ByteBuffer uploadBuffer;
    private int textureId;
    private int nextSlotIndex = 0;
    private int cursorX;
    private int cursorY;
    private int shelfHeight;

    /**
     * 创建字符页。
     *
     * @param pageIndex 页索引
     * @param textureSize 纹理边长
     * @param glyphSize 字符格大小
     */
    public GlyphPage(int runtimeVersion, int pageIndex, int textureSize, int glyphSize) {
        this.runtimeVersion = runtimeVersion;
        this.pageIndex = pageIndex;
        this.textureSize = textureSize;
        this.glyphSize = glyphSize;
        this.slotGap = 1;

        uploadBuffer = null;
        textureId = 0;
    }

    /**
     * 判断当前页是否还能分配新槽位。
     *
     * @return 是否还能分配
     */
    public boolean canAllocate() {
        return canAllocate(1, 1);
    }

    /**
     * 判断当前页是否能容纳指定大小的槽位。
     *
     * @param slotWidth 槽位宽度
     * @param slotHeight 槽位高度
     * @return 是否能容纳
     */
    public boolean canAllocate(int slotWidth, int slotHeight) {
        return probeSlot(slotWidth, slotHeight).fits;
    }

    /**
     * 分配下一个可用槽位。
     *
     * @return 槽位信息
     */
    public GlyphSlot allocateSlot(int slotWidth, int slotHeight) {
        SlotProbe probe = probeSlot(slotWidth, slotHeight);
        if (!probe.fits) {
            throw new IllegalStateException("字符页容量不足");
        }
        GlyphSlot slot = new GlyphSlot(nextSlotIndex++, probe.x, probe.y, probe.width, probe.height);
        cursorX = probe.x + probe.width + slotGap;
        cursorY = probe.y;
        shelfHeight = Math.max(probe.shelfHeight, probe.height);
        return slot;
    }

    /**
     * 将字符图像上传到纹理页。
     *
     * @param slotIndex 槽位索引
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @param image 字符图像
     */
    public void upload(GlyphSlot slot, int codepoint, club.heiqi.uilib.font.FontType fontType, BufferedImage image) {
        if (slot == null || slot.getSlotIndex() < 0) {
            throw new IllegalStateException("字符未分配页槽位");
        }
        if (image == null || image.getWidth() != slot.getWidth() || image.getHeight() != slot.getHeight()) {
            throw new IllegalArgumentException("字符图像尺寸与页槽位不一致");
        }
        boolean logUploadDiagnostics = FontRuntimeDiagnostics.shouldLogGlyphUpload();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        try {
            ensureTexture();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            prepareUnpackState();
            GL11.glTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    slot.getX(),
                    slot.getY(),
                    slot.getWidth(),
                    slot.getHeight(),
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    toByteBuffer(image));
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            if (logUploadDiagnostics) {
                FontRuntimeDiagnostics.logGlyphUpload(runtimeVersion, codepoint, fontType, textureId,
                        GL11.glIsTexture(textureId), GL11.glGetError(), image);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
        }
    }

    /**
     * 释放纹理页资源。
     */
    public void close() {
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
            textureId = 0;
        }
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public int getRuntimeVersion() {
        return runtimeVersion;
    }

    public int getTextureSize() {
        return textureSize;
    }

    public int getGlyphSize() {
        return glyphSize;
    }

    public int getTextureId() {
        return textureId;
    }

    /**
     * 获取或创建字符页纹理。
     *
     * @return 字符页纹理 ID
     */
    public int getOrCreateTextureId() {
        ensureTexture();
        return textureId;
    }

    private void ensureTexture() {
        if (textureId != 0) {
            return;
        }

        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        ByteBuffer emptyTexture = obtainRenderThreadEmptyBuffer(textureSize * textureSize * 4);
        prepareUnpackState();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, textureSize, textureSize, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, emptyTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, getLerpMode());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private void prepareUnpackState() {
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST, 0);
    }

    private int getLerpMode() {
        switch (FontConfig.lerpMode) {
            case 0:
                return GL11.GL_NEAREST_MIPMAP_NEAREST;
            case 1:
                return GL11.GL_LINEAR_MIPMAP_NEAREST;
            case 2:
                return GL11.GL_NEAREST_MIPMAP_LINEAR;
            default:
                return GL11.GL_LINEAR_MIPMAP_LINEAR;
        }
    }

    private SlotProbe probeSlot(int slotWidth, int slotHeight) {
        int safeWidth = Math.max(1, slotWidth);
        int safeHeight = Math.max(1, slotHeight);
        if (safeWidth > textureSize || safeHeight > textureSize) {
            return new SlotProbe(false, 0, 0, safeWidth, safeHeight, shelfHeight);
        }

        int nextX = cursorX;
        int nextY = cursorY;
        int nextShelfHeight = shelfHeight;
        if (nextX > 0 && nextX + safeWidth > textureSize) {
            nextX = 0;
            nextY += nextShelfHeight + slotGap;
            nextShelfHeight = 0;
        }
        boolean fits = nextY + safeHeight <= textureSize;
        return new SlotProbe(fits, nextX, nextY, safeWidth, safeHeight, nextShelfHeight);
    }

    private ByteBuffer toByteBuffer(BufferedImage image) {
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            converted.getGraphics().drawImage(image, 0, 0, null);
            image = converted;
        }

        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
        int requiredCapacity = image.getWidth() * image.getHeight() * 4;
        if (uploadBuffer == null || uploadBuffer.capacity() < requiredCapacity) {
            uploadBuffer = BufferUtils.createByteBuffer(requiredCapacity);
        }
        ByteBuffer buffer = uploadBuffer;
        buffer.clear();

        for (int pixel : pixels) {
            buffer.put((byte) ((pixel >> 16) & 0xFF));
            buffer.put((byte) ((pixel >> 8) & 0xFF));
            buffer.put((byte) (pixel & 0xFF));
            buffer.put((byte) ((pixel >> 24) & 0xFF));
        }
        buffer.flip();
        return buffer;
    }

    private static ByteBuffer obtainRenderThreadEmptyBuffer(int requiredCapacity) {
        if (renderThreadEmptyBuffer != null && renderThreadEmptyBuffer.capacity() >= requiredCapacity) {
            renderThreadEmptyBuffer.clear();
            renderThreadEmptyBuffer.limit(requiredCapacity);
            return renderThreadEmptyBuffer;
        }
        renderThreadEmptyBuffer = createEmptyTextureBuffer(requiredCapacity);
        return renderThreadEmptyBuffer;
    }

    private static ByteBuffer createEmptyTextureBuffer(int capacity) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(capacity);
        for (int index = 0; index < buffer.capacity(); index++) {
            buffer.put((byte) 0);
        }
        buffer.flip();
        return buffer;
    }

    /**
     * 字符页内已分配槽位。
     */
    public static final class GlyphSlot {

        private final int slotIndex;
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private GlyphSlot(int slotIndex, int x, int y, int width, int height) {
            this.slotIndex = slotIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int getSlotIndex() {
            return slotIndex;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    private static final class SlotProbe {

        private final boolean fits;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int shelfHeight;

        private SlotProbe(boolean fits, int x, int y, int width, int height, int shelfHeight) {
            this.fits = fits;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.shelfHeight = shelfHeight;
        }
    }
}
