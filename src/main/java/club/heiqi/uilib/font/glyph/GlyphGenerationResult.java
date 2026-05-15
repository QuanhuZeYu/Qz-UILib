package club.heiqi.uilib.font.glyph;

import java.awt.image.BufferedImage;

import club.heiqi.uilib.font.FontType;

/**
 * 字符生成结果。
 */
public class GlyphGenerationResult {

    private final int runtimeVersion;
    private final long generationId;
    private final int codepoint;
    private final FontType fontType;
    private final BufferedImage image;
    private final GlyphInfo glyphInfo;

    /**
     * 创建字符生成结果。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @param image 字符图像
     * @param glyphInfo 字符度量信息
     */
    public GlyphGenerationResult(int runtimeVersion, int codepoint, FontType fontType, BufferedImage image,
            GlyphInfo glyphInfo) {
        this(runtimeVersion, 0L, codepoint, fontType, image, glyphInfo);
    }

    /**
     * 创建带生成请求编号的字符生成结果。
     *
     * @param runtimeVersion 运行时版本
     * @param generationId 生成请求编号
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @param image 字符图像
     * @param glyphInfo 字符度量信息
     */
    public GlyphGenerationResult(int runtimeVersion, long generationId, int codepoint, FontType fontType,
            BufferedImage image, GlyphInfo glyphInfo) {
        this.runtimeVersion = runtimeVersion;
        this.generationId = generationId;
        this.codepoint = codepoint;
        this.fontType = fontType;
        this.image = image;
        this.glyphInfo = glyphInfo;
    }

    public int getRuntimeVersion() {
        return runtimeVersion;
    }

    public long getGenerationId() {
        return generationId;
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
