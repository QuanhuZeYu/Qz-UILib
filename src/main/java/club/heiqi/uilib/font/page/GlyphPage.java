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

    private static ByteBuffer emptyTextureBuffer = createEmptyTextureBuffer(64 * 64 * 4);

    private final int runtimeVersion;
    private final int pageIndex;
    private final int textureSize;
    private final int glyphSize;
    private final int columnCount;
    private final int rowCount;
    private final short[] slotXByIndex;
    private final short[] slotYByIndex;
    private final int slotCount;
    private ByteBuffer uploadBuffer;
    private int textureId;
    private int nextSlotIndex = 0;

    /**
     * 创建字符页。
     *
     * @param pageIndex 页索引
     * @param textureSize 纹理边长
     * @param glyphSize 字符格大小
     */
    public GlyphPage(int runtimeVersion, int pageIndex, int textureSize, int glyphSize,
            short[] slotXByIndex, short[] slotYByIndex) {
        this.runtimeVersion = runtimeVersion;
        this.pageIndex = pageIndex;
        this.textureSize = textureSize;
        this.glyphSize = glyphSize;
        this.columnCount = Math.max(1, textureSize / glyphSize);
        this.rowCount = Math.max(1, textureSize / glyphSize);
        this.slotXByIndex = slotXByIndex;
        this.slotYByIndex = slotYByIndex;
        this.slotCount = Math.min(columnCount * rowCount, Math.min(slotXByIndex.length, slotYByIndex.length));

        uploadBuffer = BufferUtils.createByteBuffer(glyphSize * glyphSize * 4);
        textureId = 0;
    }

    /**
     * 判断当前页是否还能分配新槽位。
     *
     * @return 是否还能分配
     */
    public boolean canAllocate() {
        return nextSlotIndex < slotCount;
    }

    /**
     * 分配下一个可用槽位。
     *
     * @return 槽位索引
     */
    public int allocateSlot() {
        if (!canAllocate()) {
            throw new IllegalStateException("字符页容量不足");
        }
        return nextSlotIndex++;
    }

    /**
     * 将字符图像上传到纹理页。
     *
     * @param slotIndex 槽位索引
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @param image 字符图像
     */
    public void upload(int slotIndex, int codepoint, club.heiqi.uilib.font.FontType fontType, BufferedImage image) {
        if (slotIndex < 0 || slotIndex >= slotCount) {
            throw new IllegalStateException("字符未分配页槽位");
        }
        int slotX = slotXByIndex[slotIndex] & 0xFFFF;
        int slotY = slotYByIndex[slotIndex] & 0xFFFF;
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
                    slotX,
                    slotY,
                    glyphSize,
                    glyphSize,
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
        ByteBuffer emptyTexture = obtainEmptyTextureBuffer(textureSize * textureSize * 4).duplicate();
        emptyTexture.clear();
        emptyTexture.limit(textureSize * textureSize * 4);
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

    private static synchronized ByteBuffer obtainEmptyTextureBuffer(int requiredCapacity) {
        if (emptyTextureBuffer.capacity() >= requiredCapacity) {
            return emptyTextureBuffer;
        }
        emptyTextureBuffer = createEmptyTextureBuffer(requiredCapacity);
        return emptyTextureBuffer;
    }

    private static ByteBuffer createEmptyTextureBuffer(int capacity) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(capacity);
        for (int index = 0; index < buffer.capacity(); index++) {
            buffer.put((byte) 0);
        }
        buffer.flip();
        return buffer;
    }
}
