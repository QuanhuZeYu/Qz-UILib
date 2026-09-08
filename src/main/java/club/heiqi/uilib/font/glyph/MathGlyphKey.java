package club.heiqi.uilib.font.glyph;

import club.heiqi.uilib.font.latex.layout.MathGlyphRef;

/** 数学字形在一个运行时代际中的 demand/page 身份，不包含单次请求编号。 */
public final class MathGlyphKey {

    private final int generation;
    private final MathGlyphRef mathGlyphRef;
    private final int rasterSize;
    private final int tileIndex;

    public MathGlyphKey(int generation, MathGlyphRef mathGlyphRef, int rasterSize, int tileIndex) {
        if (mathGlyphRef == null || rasterSize <= 0 || tileIndex < 0) {
            throw new IllegalArgumentException("mathGlyphRef、rasterSize 和 tileIndex 必须有效");
        }
        this.generation = generation;
        this.mathGlyphRef = mathGlyphRef;
        this.rasterSize = rasterSize;
        this.tileIndex = tileIndex;
    }

    public int getGeneration() { return generation; }

    public MathGlyphRef getMathGlyphRef() { return mathGlyphRef; }

    public int getRasterSize() { return rasterSize; }

    public int getTileIndex() { return tileIndex; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof MathGlyphKey)) { return false; }
        MathGlyphKey key = (MathGlyphKey) other;
        return generation == key.generation && rasterSize == key.rasterSize
                && tileIndex == key.tileIndex && mathGlyphRef.equals(key.mathGlyphRef);
    }

    @Override
    public int hashCode() {
        int result = generation;
        result = 31 * result + mathGlyphRef.hashCode();
        result = 31 * result + rasterSize;
        return 31 * result + tileIndex;
    }
}
