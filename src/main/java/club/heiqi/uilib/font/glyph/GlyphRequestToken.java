package club.heiqi.uilib.font.glyph;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.layout.MathGlyphRef;

/**
 * 一次字形请求在线程与上传阶段之间共享的不可变身份。
 */
public final class GlyphRequestToken {

    public enum Kind { CODEPOINT, MATH_GLYPH }

    private final MathGlyphRef mathGlyphRef;
    private final int rasterSize;
    private final int tileIndex;
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
        this.mathGlyphRef = null;
        this.rasterSize = 0;
        this.tileIndex = 0;
    }

    private GlyphRequestToken(int generation, long requestId, MathGlyphRef glyphRef, int rasterSize, int tileIndex) {
        if (requestId == 0L || glyphRef == null || rasterSize <= 0 || tileIndex < 0) {
            throw new IllegalArgumentException("数学请求要求非零 requestId、非空 glyphRef、正 rasterSize 和非负 tileIndex");
        }
        this.generation = generation;
        this.requestId = requestId;
        this.mathGlyphRef = glyphRef;
        this.rasterSize = rasterSize;
        this.tileIndex = tileIndex;
        this.codepoint = 0;
        this.fontType = null;
    }

    public static GlyphRequestToken forMathGlyph(int generation, long requestId, MathGlyphRef glyphRef,
            int rasterSize, int tileIndex) {
        return new GlyphRequestToken(generation, requestId, glyphRef, rasterSize, tileIndex);
    }

    public Kind getKind() { return mathGlyphRef == null ? Kind.CODEPOINT : Kind.MATH_GLYPH; }
    public MathGlyphRef getMathGlyphRef() { requireKind(Kind.MATH_GLYPH); return mathGlyphRef; }
    public int getRasterSize() { requireKind(Kind.MATH_GLYPH); return rasterSize; }
    public int getTileIndex() { requireKind(Kind.MATH_GLYPH); return tileIndex; }

    private void requireKind(Kind expected) {
        if (getKind() != expected) { throw new IllegalStateException("请求类型不匹配: " + getKind()); }
    }

    public int getGeneration() {
        return generation;
    }

    public long getRequestId() {
        return requestId;
    }

    public int getCodepoint() {
        requireKind(Kind.CODEPOINT);
        return codepoint;
    }

    public FontType getFontType() {
        requireKind(Kind.CODEPOINT);
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
                && getKind() == token.getKind()
                && (getKind() == Kind.CODEPOINT ? codepoint == token.codepoint && fontType == token.fontType
                        : rasterSize == token.rasterSize && tileIndex == token.tileIndex
                                && mathGlyphRef.equals(token.mathGlyphRef));
    }

    @Override
    public int hashCode() {
        int result = generation;
        result = 31 * result + (int) (requestId ^ requestId >>> 32);
        if (getKind() == Kind.MATH_GLYPH) {
            result = 31 * result + mathGlyphRef.hashCode();
            result = 31 * result + rasterSize;
            return 31 * result + tileIndex;
        }
        result = 31 * result + codepoint;
        result = 31 * result + fontType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "GlyphRequestToken{generation=" + generation
                + ", requestId=" + requestId
                + ", kind=" + getKind()
                + (getKind() == Kind.CODEPOINT ? ", codepoint=" + codepoint + ", fontType=" + fontType
                        : ", mathGlyphRef=" + mathGlyphRef + ", rasterSize=" + rasterSize + ", tileIndex=" + tileIndex)
                + '}';
    }
}
