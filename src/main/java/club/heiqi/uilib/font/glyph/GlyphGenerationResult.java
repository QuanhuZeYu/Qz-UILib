package club.heiqi.uilib.font.glyph;

import java.awt.image.BufferedImage;

import club.heiqi.uilib.font.FontType;

/**
 * 字符生成结果。
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
        this.image = image;
        this.glyphInfo = glyphInfo;
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
        return image;
    }

    public GlyphInfo getGlyphInfo() {
        return glyphInfo;
    }
}
