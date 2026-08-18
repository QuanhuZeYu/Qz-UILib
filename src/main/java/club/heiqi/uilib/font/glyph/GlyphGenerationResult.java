package club.heiqi.uilib.font.glyph;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import club.heiqi.uilib.font.FontType;

/**
 * 字符生成结果。
 *
 * <p>构造时把 worker 产出的像素冻结为私有 RGBA byte 快照，getter 不暴露可变引用；
 * 后续 upload plan 直接复用该快照，避免重复经过 {@link BufferedImage} 的像素往返。</p>
 */
public class GlyphGenerationResult {

    private final GlyphRequestToken token;
    private final byte[] rgbaPixels;
    private final int imageWidth;
    private final int imageHeight;
    private final GlyphInfo glyphInfo;

    /**
     * 创建字符生成结果。
     *
     * @param token 与 worker 任务相同的请求 token
     * @param image 字符图像
     * @param glyphInfo 字符度量信息
     */
    public GlyphGenerationResult(GlyphRequestToken token, BufferedImage image, GlyphInfo glyphInfo) {
        if (token == null) {
            throw new IllegalArgumentException("token 不得为 null");
        }
        this.token = token;
        if (image == null) {
            this.rgbaPixels = null;
            this.imageWidth = 0;
            this.imageHeight = 0;
        } else {
            this.rgbaPixels = copyRgbaPixels(image);
            this.imageWidth = image.getWidth();
            this.imageHeight = image.getHeight();
        }
        this.glyphInfo = copyGlyphInfo(glyphInfo);
    }

    public GlyphRequestToken getToken() {
        return token;
    }

    public int getRuntimeVersion() {
        return token.getGeneration();
    }

    public int getCodepoint() {
        return token.getCodepoint();
    }

    public FontType getFontType() {
        return token.getFontType();
    }

    public BufferedImage getImage() {
        return createImage();
    }

    /**
     * 获取像素快照的独立副本（RGBA 顺序，每像素 4 字节）。
     *
     * @return 独立字节数组副本；无位图时返回 null
     */
    public byte[] copyRgbaPixels() {
        return rgbaPixels == null ? null : Arrays.copyOf(rgbaPixels, rgbaPixels.length);
    }

    public GlyphInfo getGlyphInfo() {
        return glyphInfo;
    }

    private BufferedImage createImage() {
        if (rgbaPixels == null || imageWidth <= 0 || imageHeight <= 0) {
            return null;
        }
        int[] argb = new int[imageWidth * imageHeight];
        int offset = 0;
        for (int index = 0; index < argb.length; index++) {
            int red = rgbaPixels[offset++] & 0xFF;
            int green = rgbaPixels[offset++] & 0xFF;
            int blue = rgbaPixels[offset++] & 0xFF;
            int alpha = rgbaPixels[offset++] & 0xFF;
            argb[index] = alpha << 24 | red << 16 | green << 8 | blue;
        }
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, imageWidth, imageHeight, argb, 0, imageWidth);
        return image;
    }

    private static byte[] copyRgbaPixels(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        long requiredBytes = (long) width * (long) height * 4L;
        if (requiredBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("bitmap glyph 像素数据超过单个 buffer 上限");
        }
        byte[] pixels = new byte[(int) requiredBytes];
        int[] argb = source.getRGB(0, 0, width, height, null, 0, width);
        int offset = 0;
        for (int pixel : argb) {
            pixels[offset++] = (byte) ((pixel >> 16) & 0xFF);
            pixels[offset++] = (byte) ((pixel >> 8) & 0xFF);
            pixels[offset++] = (byte) (pixel & 0xFF);
            pixels[offset++] = (byte) ((pixel >> 24) & 0xFF);
        }
        return pixels;
    }

    private static GlyphInfo copyGlyphInfo(GlyphInfo source) {
        if (source == null) {
            return null;
        }
        return new GlyphInfo(source.getCodepoint(), source.getWidth(), source.getHeight(), source.getAdvance(),
                source.getAscent(), source.getDescent(), source.getLeading(), source.getGlyphWidth(),
                source.getGlyphHeight(), source.getSlotWidth(), source.getSlotHeight(), source.getAtlasBaselineX(),
                source.getAtlasBaselineY(), source.getLineBaselineY(), source.getBearingX(), source.getBearingY(),
                source.hasBitmap(), source.isColoredGlyph());
    }
}
