package club.heiqi.uilib.font.page;

import club.heiqi.uilib.font.glyph.GlyphInfo;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/** Immutable atlas snapshot. Its token is valid only until the corresponding page or generation retires. */
public final class MathGlyphSlot {
    private final GlyphRequestToken token;
    private final int pageIndex;
    private final int textureId;
    private final int textureSize;
    private final int slotX;
    private final int slotY;
    private final GlyphInfo glyphInfo;

    MathGlyphSlot(GlyphRequestToken token, int pageIndex, int textureId, int textureSize, int slotX, int slotY, GlyphInfo info) {
        this.token = token;
        this.pageIndex = pageIndex;
        this.textureId = textureId;
        this.textureSize = textureSize;
        this.slotX = slotX;
        this.slotY = slotY;
        this.glyphInfo = GlyphInfo.copyOf(info);
    }

    public GlyphRequestToken getToken() { return token; }
    public int getPageIndex() { return pageIndex; }
    public int getTextureId() { return textureId; }
    public int getTextureSize() { return textureSize; }
    public int getSlotX() { return slotX; }
    public int getSlotY() { return slotY; }
    public int getSlotWidth() { return glyphInfo.getSlotWidth(); }
    public int getSlotHeight() { return glyphInfo.getSlotHeight(); }
    public GlyphInfo getGlyphInfo() { return glyphInfo; }
    public byte getFlags() {
        return (byte) (GlyphRuntimeTables.GLYPH_FLAG_MATH_CORE
                | (glyphInfo.hasBitmap() ? GlyphRuntimeTables.GLYPH_FLAG_HAS_BITMAP : 0)
                | (glyphInfo.isColoredGlyph() ? GlyphRuntimeTables.GLYPH_FLAG_COLORED : 0));
    }
}
