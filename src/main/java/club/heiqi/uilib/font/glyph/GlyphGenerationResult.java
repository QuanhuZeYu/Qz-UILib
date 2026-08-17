package club.heiqi.uilib.font.glyph;

import java.awt.image.BufferedImage;

import club.heiqi.uilib.font.FontType;

/**
 * 字符生成结果。
 *
 * <p>构造时冻结 worker 产出的像素与几何，getter 不暴露可变的 {@link BufferedImage} 引用。
 * render mailbox 会再把该快照转换为只读的 GL upload plan。</p>
 */
public class GlyphGenerationResult {

    private final GlyphRequestToken token;
    private final BufferedImage image;
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
        this.image = copyImage(image);
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
        return copyImage(image);
    }

    public GlyphInfo getGlyphInfo() {
        return glyphInfo;
    }

    private static BufferedImage copyImage(BufferedImage source) {
        if (source == null) {
            return null;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        copy.setRGB(0, 0, width, height, pixels, 0, width);
        return copy;
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
