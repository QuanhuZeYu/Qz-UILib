package club.heiqi.uilib.font.glyph;

import java.awt.image.BufferedImage;

import club.heiqi.uilib.font.FontType;

/**
 * 字符生成结果。
 */
public class GlyphGenerationResult {

    private final int codepoint;
    private final FontType fontType;
    private final BufferedImage image;
    private final GlyphInfo glyphInfo;

    /**
     * 创建字符生成结果。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @param image 字符图像
     * @param glyphInfo 字符度量信息
     */
    public GlyphGenerationResult(int codepoint, FontType fontType, BufferedImage image, GlyphInfo glyphInfo) {
        this.codepoint = codepoint;
        this.fontType = fontType;
        this.image = image;
        this.glyphInfo = glyphInfo;
    }

    public int getCodepoint() {
        return codepoint;
    }

    public FontType getFontType() {
        return fontType;
    }

    public BufferedImage getImage() {
        return image;
    }

    public GlyphInfo getGlyphInfo() {
        return glyphInfo;
    }
}
