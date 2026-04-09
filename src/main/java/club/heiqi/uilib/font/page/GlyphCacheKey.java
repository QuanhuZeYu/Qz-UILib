package club.heiqi.uilib.font.page;

import club.heiqi.uilib.font.FontType;

/**
 * 字符缓存键。
 */
public class GlyphCacheKey {

    private final int codepoint;
    private final FontType fontType;

    /**
     * 创建字符缓存键。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     */
    public GlyphCacheKey(int codepoint, FontType fontType) {
        this.codepoint = codepoint;
        this.fontType = fontType;
    }

    public int getCodepoint() {
        return codepoint;
    }

    public FontType getFontType() {
        return fontType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GlyphCacheKey)) {
            return false;
        }
        GlyphCacheKey other = (GlyphCacheKey) obj;
        return codepoint == other.codepoint && fontType == other.fontType;
    }

    @Override
    public int hashCode() {
        int result = Integer.valueOf(codepoint).hashCode();
        result = 31 * result + fontType.hashCode();
        return result;
    }
}
