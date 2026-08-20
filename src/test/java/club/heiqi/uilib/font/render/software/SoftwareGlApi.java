package club.heiqi.uilib.font.render.software;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import club.heiqi.uilib.font.page.GlApi;

/**
 * 软件纹理 {@link GlApi}：字符页上传像素保留在 CPU 侧（headless 验收场地）。
 *
 * <p>真机 {@code LwjglGlApi} 把同一上传像素交给 GL；本实现按 textureId 留存
 * ARGB 页纹理并实现 {@link GlyphTextureSource}，使软件光栅化器按 UV 采样到
 * 与真机 shader 同源的字形 ink。attrib/pixelStore/texParameter/mipmap 为 no-op，
 * {@code getError} 恒 0（无 GL 错误注入需求）。</p>
 */
public final class SoftwareGlApi implements GlApi, GlyphTextureSource {

    private final Map<Integer, int[]> textures = new HashMap<Integer, int[]>();
    private final Map<Integer, Integer> sizes = new HashMap<Integer, Integer>();
    private int nextTextureId = 1;
    private int boundTexture;

    @Override
    public void pushAttrib(int mask) {
        // 无状态可存
    }

    @Override
    public void pushClientAttrib(int mask) {
        // 无状态可存
    }

    @Override
    public void popClientAttrib() {
        // 无状态可存
    }

    @Override
    public void popAttrib() {
        // 无状态可存
    }

    @Override
    public int genTexture() {
        int textureId = nextTextureId++;
        textures.put(Integer.valueOf(textureId), new int[0]);
        sizes.put(Integer.valueOf(textureId), Integer.valueOf(0));
        return textureId;
    }

    @Override
    public void bindTexture(int target, int texture) {
        boundTexture = texture;
    }

    @Override
    public void pixelStore(int parameter, int value) {
        // 无状态可存
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border,
            int format, int type, ByteBuffer pixels) {
        int[] argb = new int[width * height];
        if (pixels != null && pixels.remaining() >= width * height * 4) {
            writeRegion(argb, width, 0, 0, width, height, pixels);
        }
        textures.put(Integer.valueOf(boundTexture), argb);
        sizes.put(Integer.valueOf(boundTexture), Integer.valueOf(width));
    }

    @Override
    public void texParameter(int target, int parameter, int value) {
        // 无状态可存
    }

    @Override
    public void texSubImage2D(int target, int level, int x, int y, int width, int height, int format, int type,
            ByteBuffer pixels) {
        int[] argb = textures.get(Integer.valueOf(boundTexture));
        if (argb == null) {
            return;
        }
        int size = sizes.get(Integer.valueOf(boundTexture)).intValue();
        writeRegion(argb, size, x, y, width, height, pixels);
    }

    @Override
    public void generateMipmap(int target) {
        // 软件光栅化不做 mipmap
    }

    @Override
    public boolean isTexture(int texture) {
        return textures.containsKey(Integer.valueOf(texture));
    }

    @Override
    public void deleteTexture(int texture) {
        textures.remove(Integer.valueOf(texture));
        sizes.remove(Integer.valueOf(texture));
    }

    @Override
    public int getError() {
        return 0;
    }

    @Override
    public SoftwarePageTexture resolve(int textureId) {
        Integer size = sizes.get(Integer.valueOf(textureId));
        int[] argb = textures.get(Integer.valueOf(textureId));
        if (size == null || argb == null || argb.length != size.intValue() * size.intValue()) {
            return null;
        }
        int[] copy = new int[argb.length];
        System.arraycopy(argb, 0, copy, 0, argb.length);
        return new SoftwarePageTexture(size.intValue(), copy);
    }

    /** 已分配纹理数量。 */
    public int getTextureCount() {
        return textures.size();
    }

    private static void writeRegion(int[] target, int targetStride, int x, int y, int width, int height,
            ByteBuffer pixels) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int red = pixels.get() & 0xFF;
                int green = pixels.get() & 0xFF;
                int blue = pixels.get() & 0xFF;
                int alpha = pixels.get() & 0xFF;
                int targetIndex = (y + row) * targetStride + x + col;
                if (targetIndex >= 0 && targetIndex < target.length) {
                    target[targetIndex] = alpha << 24 | red << 16 | green << 8 | blue;
                }
            }
        }
    }
}
