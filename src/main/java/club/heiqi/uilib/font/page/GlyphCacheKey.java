package club.heiqi.uilib.font.page;

import club.heiqi.uilib.font.FontType;

/**
 * 字符缓存键。
 */
public class GlyphCacheKey {

    private final int runtimeVersion;
    private final int codepoint;
    private final FontType fontType;

    /**
     * 创建字符缓存键。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     */
    public GlyphCacheKey(int runtimeVersion, int codepoint, FontType fontType) {
        this.runtimeVersion = runtimeVersion;
        this.codepoint = codepoint;
        this.fontType = fontType;
    }

    public int getRuntimeVersion() {
        return runtimeVersion;
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
        return runtimeVersion == other.runtimeVersion
                && codepoint == other.codepoint
                && fontType == other.fontType;
    }

    @Override
    public int hashCode() {
        int result = Integer.valueOf(runtimeVersion).hashCode();
        result = 31 * result + Integer.valueOf(codepoint).hashCode();
        result = 31 * result + fontType.hashCode();
        return result;
    }
}
