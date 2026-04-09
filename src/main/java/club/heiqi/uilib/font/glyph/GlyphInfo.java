package club.heiqi.uilib.font.glyph;

/**
 * 字符度量信息。
 */
public class GlyphInfo {

    private final int codepoint;
    private final int width;
    private final int height;
    private final float advance;
    private final float glyphWidth;
    private final float glyphHeight;

    /**
     * 创建字符度量信息。
     *
     * @param codepoint 字符码点
     * @param width 字符格宽度
     * @param height 字符格高度
     * @param advance 前进量
     * @param glyphWidth 实际字形宽度
     * @param glyphHeight 实际字形高度
     */
    public GlyphInfo(int codepoint, int width, int height, float advance, float glyphWidth, float glyphHeight) {
        this.codepoint = codepoint;
        this.width = width;
        this.height = height;
        this.advance = advance;
        this.glyphWidth = glyphWidth;
        this.glyphHeight = glyphHeight;
    }

    public int getCodepoint() {
        return codepoint;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public float getAdvance() {
        return advance;
    }

    public float getGlyphWidth() {
        return glyphWidth;
    }

    public float getGlyphHeight() {
        return glyphHeight;
    }
}
