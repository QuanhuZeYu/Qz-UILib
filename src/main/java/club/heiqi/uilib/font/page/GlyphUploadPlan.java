package club.heiqi.uilib.font.page;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/** Worker 与 render owner 之间的不可变 GL upload plan。 */
final class GlyphUploadPlan {

    private final GlyphRequestToken token;
    private final GlyphInfo glyphInfo;
    private final int imageWidth;
    private final int imageHeight;
    private final byte[] rgbaPixels;
    private final long bitmapBytes;

    private GlyphUploadPlan(GlyphRequestToken token, GlyphInfo glyphInfo, int imageWidth, int imageHeight,
            byte[] rgbaPixels, long bitmapBytes) {
        this.token = token;
        this.glyphInfo = glyphInfo;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.rgbaPixels = rgbaPixels;
        this.bitmapBytes = bitmapBytes;
    }

    static GlyphUploadPlan from(GlyphGenerationResult result) {
        if (result == null || result.getToken() == null) {
            throw new IllegalArgumentException("glyph generation result/token 不得为 null");
        }
        GlyphRequestToken token = result.getToken();
        GlyphInfo sourceInfo = result.getGlyphInfo();
        if (sourceInfo == null || sourceInfo.getCodepoint() != token.getCodepoint()) {
            throw new IllegalArgumentException("glyph result 的 token 与 glyphInfo 不一致");
        }
        GlyphInfo glyphInfo = copyGlyphInfo(sourceInfo);
        if (!glyphInfo.hasBitmap()) {
            return new GlyphUploadPlan(token, glyphInfo, 0, 0, null, 0L);
        }
        byte[] rgbaPixels = result.copyRgbaPixels();
        long requiredBytes = (long) glyphInfo.getSlotWidth() * (long) glyphInfo.getSlotHeight() * 4L;
        if (glyphInfo.getSlotWidth() <= 0 || glyphInfo.getSlotHeight() <= 0 || rgbaPixels == null
                || rgbaPixels.length != (int) requiredBytes) {
            throw new IllegalArgumentException("bitmap glyph 的像素与 slot 尺寸不一致");
        }
        if (requiredBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("bitmap glyph 像素数据超过单个 direct buffer 上限");
        }
        return new GlyphUploadPlan(token, glyphInfo, glyphInfo.getSlotWidth(), glyphInfo.getSlotHeight(),
                rgbaPixels, requiredBytes);
    }

    GlyphRequestToken getToken() {
        return token;
    }

    GlyphInfo getGlyphInfo() {
        return glyphInfo;
    }

    long getBitmapBytes() {
        return bitmapBytes;
    }

    ByteBuffer getRgbaPixels() {
        if (rgbaPixels == null) {
            return null;
        }
        return ByteBuffer.wrap(rgbaPixels).asReadOnlyBuffer();
    }

    BufferedImage createImage() {
        if (rgbaPixels == null || imageWidth <= 0 || imageHeight <= 0) {
            return null;
        }
        ByteBuffer pixels = getRgbaPixels();
        int[] argb = new int[imageWidth * imageHeight];
        for (int index = 0; index < argb.length; index++) {
            int red = pixels.get() & 0xFF;
            int green = pixels.get() & 0xFF;
            int blue = pixels.get() & 0xFF;
            int alpha = pixels.get() & 0xFF;
            argb[index] = alpha << 24 | red << 16 | green << 8 | blue;
        }
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, imageWidth, imageHeight, argb, 0, imageWidth);
        return image;
    }

    GlyphGenerationResult toGenerationResult() {
        return new GlyphGenerationResult(token, createImage(), glyphInfo);
    }

    private static GlyphInfo copyGlyphInfo(GlyphInfo source) {
        return new GlyphInfo(source.getCodepoint(), source.getWidth(), source.getHeight(), source.getAdvance(),
                source.getAscent(), source.getDescent(), source.getLeading(), source.getGlyphWidth(),
                source.getGlyphHeight(), source.getSlotWidth(), source.getSlotHeight(), source.getAtlasBaselineX(),
                source.getAtlasBaselineY(), source.getLineBaselineY(), source.getBearingX(), source.getBearingY(),
                source.hasBitmap(), source.isColoredGlyph());
    }
}
