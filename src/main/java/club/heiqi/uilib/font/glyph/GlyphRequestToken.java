package club.heiqi.uilib.font.glyph;

import club.heiqi.uilib.font.FontType;

/**
 * 一次字形请求在线程与上传阶段之间共享的不可变身份。
 */
public final class GlyphRequestToken {

    private final int generation;
    private final long requestId;
    private final int codepoint;
    private final FontType fontType;

    /**
     * 创建字形请求 token。
     *
     * @param generation 字体运行时代际
     * @param requestId 单调请求编号
     * @param codepoint Unicode 码点
     * @param fontType 字重类型
     */
    public GlyphRequestToken(int generation, long requestId, int codepoint, FontType fontType) {
        if (requestId == 0L) {
            throw new IllegalArgumentException("requestId 不得为 0");
        }
        if (!Character.isValidCodePoint(codepoint)) {
            throw new IllegalArgumentException("codepoint 超出 Unicode 范围");
        }
        if (fontType == null) {
            throw new IllegalArgumentException("fontType 不得为 null");
        }
        this.generation = generation;
        this.requestId = requestId;
        this.codepoint = codepoint;
        this.fontType = fontType;
    }

    public int getGeneration() {
        return generation;
    }

    public long getRequestId() {
        return requestId;
    }

    public int getCodepoint() {
        return codepoint;
    }

    public FontType getFontType() {
        return fontType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GlyphRequestToken)) {
            return false;
        }
        GlyphRequestToken token = (GlyphRequestToken) other;
        return generation == token.generation
                && requestId == token.requestId
                && codepoint == token.codepoint
                && fontType == token.fontType;
    }

    @Override
    public int hashCode() {
        int result = generation;
        result = 31 * result + (int) (requestId ^ requestId >>> 32);
        result = 31 * result + codepoint;
        result = 31 * result + fontType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "GlyphRequestToken{generation=" + generation
                + ", requestId=" + requestId
                + ", codepoint=" + codepoint
                + ", fontType=" + fontType + '}';
    }
}
