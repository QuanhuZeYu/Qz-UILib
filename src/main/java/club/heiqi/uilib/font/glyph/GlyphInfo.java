package club.heiqi.uilib.font.glyph;

import club.heiqi.uilib.font.latex.layout.MathGlyphRef;

/**
 * 字符度量信息。
 */
public class GlyphInfo {

    private final Integer codepoint;
    private final MathGlyphRef mathGlyphRef;
    private final int width;
    private final int height;
    private final float advance;
    private final float ascent;
    private final float descent;
    private final float leading;
    private final float glyphWidth;
    private final float glyphHeight;
    private final int slotWidth;
    private final int slotHeight;
    private final int atlasBaselineX;
    private final int atlasBaselineY;
    private final int lineBaselineY;
    private final int bearingX;
    private final int bearingY;
    private final boolean hasBitmap;
    private final boolean coloredGlyph;

    /**
     * 创建字符度量信息。
     *
     * @param codepoint    字符码点
     * @param width        字符格宽度
     * @param height       字符格高度
     * @param advance      前进量
     * @param ascent       字体上升量，量纲=atlas 像素（awtCharSize 坐标系）
     * @param descent      字体下降量，量纲=atlas 像素（awtCharSize 坐标系）
     * @param glyphWidth   实际字形宽度
     * @param glyphHeight  实际字形高度
     * @param coloredGlyph 是否为保留原始颜色的彩色字形
     */
    public GlyphInfo(int codepoint, int width, int height, float advance, float ascent, float descent,
                     float glyphWidth, float glyphHeight, boolean coloredGlyph) {
        this(codepoint, width, height, advance, ascent, descent, 0.0F, glyphWidth, glyphHeight, width, height, 0, 0,
                height, 0, 0, true, coloredGlyph);
    }

    /**
     * 创建字符度量信息。
     *
     * @param codepoint    字符码点
     * @param width        字符格宽度
     * @param height       字符格高度
     * @param advance      前进量
     * @param glyphWidth   实际字形宽度
     * @param glyphHeight  实际字形高度
     * @param coloredGlyph 是否为保留原始颜色的彩色字形
     */
    public GlyphInfo(int codepoint, int width, int height, float advance, float glyphWidth, float glyphHeight,
                     boolean coloredGlyph) {
        this(codepoint, width, height, advance, 0.0F, 0.0F, 0.0F, glyphWidth, glyphHeight, coloredGlyph);
    }

    /**
     * 创建字符度量信息。
     *
     * @param codepoint    字符码点
     * @param width        字符格宽度
     * @param height       字符格高度
     * @param advance      前进量
     * @param ascent       字体上升量，量纲=atlas 像素（awtCharSize 坐标系）
     * @param descent      字体下降量，量纲=atlas 像素（awtCharSize 坐标系）
     * @param leading      字体行间隙，量纲=atlas 像素（awtCharSize 坐标系）
     * @param glyphWidth   实际字形宽度
     * @param glyphHeight  实际字形高度
     * @param coloredGlyph 是否为保留原始颜色的彩色字形
     */
    public GlyphInfo(int codepoint, int width, int height, float advance, float ascent, float descent, float leading,
                     float glyphWidth, float glyphHeight, boolean coloredGlyph) {
        this(codepoint, width, height, advance, ascent, descent, leading, glyphWidth, glyphHeight, width, height, 0, 0,
                height, 0, 0, true, coloredGlyph);
    }

    /**
     * 创建基线与实际 ink bounds 驱动的字符度量信息。
     *
     * @param codepoint      字符码点
     * @param width          默认字符格宽度
     * @param height         默认字符格高度
     * @param advance        前进量
     * @param ascent         字体上升量，量纲=atlas 像素（awtCharSize 坐标系）
     * @param descent        字体下降量，量纲=atlas 像素（awtCharSize 坐标系）
     * @param leading        字体行间隙，量纲=atlas 像素（awtCharSize 坐标系）
     * @param glyphWidth     实际字形宽度
     * @param glyphHeight    实际字形高度
     * @param slotWidth      atlas 槽位宽度
     * @param slotHeight     atlas 槽位高度
     * @param atlasBaselineX 槽位内基线 X
     * @param atlasBaselineY 槽位内基线 Y
     * @param lineBaselineY  默认字符格内文本基线 Y，量纲=atlas 像素（awtCharSize 坐标系）
     * @param bearingX       ink bounds 相对基线的 X 偏移
     * @param bearingY       ink bounds 相对基线的 Y 偏移
     * @param hasBitmap      是否存在实际像素
     * @param coloredGlyph   是否为保留原始颜色的彩色字形
     */
    public GlyphInfo(int codepoint, int width, int height, float advance, float ascent, float descent, float leading,
                     float glyphWidth, float glyphHeight, int slotWidth, int slotHeight, int atlasBaselineX,
                     int atlasBaselineY, int lineBaselineY, int bearingX, int bearingY, boolean hasBitmap,
                     boolean coloredGlyph) {
        this(Integer.valueOf(codepoint), null, width, height, advance, ascent, descent, leading, glyphWidth,
                glyphHeight, slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY,
                bearingX, bearingY, hasBitmap, coloredGlyph);
    }

    /** 数学元数据复用相同的 atlas 几何，身份不经过 Unicode 码点。 */
    public GlyphInfo(MathGlyphRef glyphRef, int width, int height, float advance, float ascent, float descent,
            float leading, float glyphWidth, float glyphHeight, int slotWidth, int slotHeight, int atlasBaselineX,
            int atlasBaselineY, int lineBaselineY, int bearingX, int bearingY, boolean hasBitmap, boolean coloredGlyph) {
        this(null, requireMathGlyphRef(glyphRef), width, height, advance, ascent, descent, leading, glyphWidth,
                glyphHeight, slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY,
                bearingX, bearingY, hasBitmap, coloredGlyph);
    }

    private GlyphInfo(Integer codepoint, MathGlyphRef glyphRef, int width, int height, float advance, float ascent,
            float descent, float leading, float glyphWidth, float glyphHeight, int slotWidth, int slotHeight,
            int atlasBaselineX, int atlasBaselineY, int lineBaselineY, int bearingX, int bearingY,
            boolean hasBitmap, boolean coloredGlyph) {
        this.codepoint = codepoint;
        this.mathGlyphRef = glyphRef;
        this.width = width;
        this.height = height;
        this.advance = advance;
        this.ascent = Math.max(0.0F, ascent);
        this.descent = Math.max(0.0F, descent);
        this.leading = Math.max(0.0F, leading);
        this.glyphWidth = glyphWidth;
        this.glyphHeight = glyphHeight;
        this.slotWidth = Math.max(0, slotWidth);
        this.slotHeight = Math.max(0, slotHeight);
        // 数学 tile 的原点可位于基线另一侧；负槽内基线不能按普通字符格钳制。
        this.atlasBaselineX = glyphRef == null ? Math.max(0, atlasBaselineX) : atlasBaselineX;
        this.atlasBaselineY = glyphRef == null ? Math.max(0, atlasBaselineY) : atlasBaselineY;
        this.lineBaselineY = Math.max(0, lineBaselineY);
        this.bearingX = bearingX;
        this.bearingY = bearingY;
        this.hasBitmap = hasBitmap;
        this.coloredGlyph = coloredGlyph;
    }

    /**
     * 创建基线与实际 ink bounds 驱动的字符度量信息。
     *
     * @param codepoint      字符码点
     * @param width          默认字符格宽度
     * @param height         默认字符格高度
     * @param advance        前进量
     * @param glyphWidth     实际字形宽度
     * @param glyphHeight    实际字形高度
     * @param slotWidth      atlas 槽位宽度
     * @param slotHeight     atlas 槽位高度
     * @param atlasBaselineX 槽位内基线 X
     * @param atlasBaselineY 槽位内基线 Y
     * @param lineBaselineY  默认字符格内文本基线 Y，量纲=atlas 像素（awtCharSize 坐标系）
     * @param bearingX       ink bounds 相对基线的 X 偏移
     * @param bearingY       ink bounds 相对基线的 Y 偏移
     * @param hasBitmap      是否存在实际像素
     * @param coloredGlyph   是否为保留原始颜色的彩色字形
     */
    public GlyphInfo(int codepoint, int width, int height, float advance, float glyphWidth, float glyphHeight,
                     int slotWidth, int slotHeight, int atlasBaselineX, int atlasBaselineY, int lineBaselineY, int bearingX,
                     int bearingY, boolean hasBitmap, boolean coloredGlyph) {
        this(codepoint, width, height, advance, 0.0F, 0.0F, 0.0F, glyphWidth, glyphHeight, slotWidth, slotHeight,
                atlasBaselineX, atlasBaselineY, lineBaselineY, bearingX, bearingY, hasBitmap, coloredGlyph);
    }

    public GlyphRequestToken.Kind getKind() {
        return mathGlyphRef == null ? GlyphRequestToken.Kind.CODEPOINT : GlyphRequestToken.Kind.MATH_GLYPH;
    }

    public MathGlyphRef getMathGlyphRef() {
        if (mathGlyphRef == null) { throw new IllegalStateException("码点元数据没有数学身份"); }
        return mathGlyphRef;
    }

    private static MathGlyphRef requireMathGlyphRef(MathGlyphRef glyphRef) {
        if (glyphRef == null) { throw new IllegalArgumentException("glyphRef 不得为 null"); }
        return glyphRef;
    }

    /** 冻结包括潜在可变子类在内的全部元数据；数学身份保留明确分支。 */
    public static GlyphInfo copyOf(GlyphInfo source) {
        if (source == null) { return null; }
        if (source.getKind() == GlyphRequestToken.Kind.MATH_GLYPH) {
            return new GlyphInfo(source.getMathGlyphRef(), source.getWidth(), source.getHeight(), source.getAdvance(),
                    source.getAscent(), source.getDescent(), source.getLeading(), source.getGlyphWidth(),
                    source.getGlyphHeight(), source.getSlotWidth(), source.getSlotHeight(), source.getAtlasBaselineX(),
                    source.getAtlasBaselineY(), source.getLineBaselineY(), source.getBearingX(), source.getBearingY(),
                    source.hasBitmap(), source.isColoredGlyph());
        }
        return new GlyphInfo(source.getCodepoint(), source.getWidth(), source.getHeight(), source.getAdvance(),
                source.getAscent(), source.getDescent(), source.getLeading(), source.getGlyphWidth(),
                source.getGlyphHeight(), source.getSlotWidth(), source.getSlotHeight(), source.getAtlasBaselineX(),
                source.getAtlasBaselineY(), source.getLineBaselineY(), source.getBearingX(), source.getBearingY(),
                source.hasBitmap(), source.isColoredGlyph());
    }

    public int getCodepoint() {
        if (mathGlyphRef != null) { throw new IllegalStateException("数学元数据没有 Unicode 码点"); }
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

    public float getAscent() {
        return ascent;
    }

    public float getDescent() {
        return descent;
    }

    public float getLeading() {
        return leading;
    }

    public float getGlyphWidth() {
        return glyphWidth;
    }

    public float getGlyphHeight() {
        return glyphHeight;
    }

    public int getSlotWidth() {
        return slotWidth;
    }

    public int getSlotHeight() {
        return slotHeight;
    }

    public int getAtlasBaselineX() {
        return atlasBaselineX;
    }

    public int getAtlasBaselineY() {
        return atlasBaselineY;
    }

    public int getLineBaselineY() {
        return lineBaselineY;
    }

    public int getBearingX() {
        return bearingX;
    }

    public int getBearingY() {
        return bearingY;
    }

    public boolean hasBitmap() {
        return hasBitmap;
    }

    public boolean isColoredGlyph() {
        return coloredGlyph;
    }
}
