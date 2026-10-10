package club.heiqi.uilib.ui.scene.text.font.nativefont;

/**
 * 固定请求em的线程所属face，创建/使用/关闭均在session所属线程；所有返回值脱离native生命周期。 shape读取完整text中的[start,end)，UTF16
 * cluster仍相对完整串；区间外上下文参与连接，BOT/EOT描述真实行端。 输出顺序及Y向上advance/offset沿整形结果保留，布局决定视觉位置，不在绑定里另建格式/候选引擎。
 * ink/raster使用同glyph与变体且禁止shape；墨迹Y向下，bitmap已premult且bearing/pixelScale为请求单位。
 */
public interface FontFace extends AutoCloseable {
    FontMetrics metrics();

    int glyphIndex(int codePoint);

    ShapeRun shape(
            String text,
            int start,
            int end,
            ShapeDirection direction,
            String language,
            String script,
            boolean beginningOfText,
            boolean endOfText);

    default ShapeRun shape(String text, ShapeDirection direction, String language) {
        return shape(text, 0, text.length(), direction, language, null, true, true);
    }

    GlyphBitmap rasterize(int glyphId, GlyphRasterStyle style);

    default GlyphBitmap rasterize(int glyphId) {
        return rasterize(glyphId, GlyphRasterStyle.NORMAL);
    }

    InkBounds glyphInkBounds(int glyphId, GlyphRasterStyle style);

    default InkBounds glyphInkBounds(int glyphId) {
        return glyphInkBounds(glyphId, GlyphRasterStyle.NORMAL);
    }

    /** 数学能力属于同一 face；不支持的后端保持显式边界。 */
    default boolean hasMathData() {
        return false;
    }

    /** OpenType MATH 常量编号；0、1、55 为百分比原值，其余为请求 em 的逻辑像素。 */
    default float mathConstant(int constant) {
        throw new UnsupportedOperationException("字体后端不支持 OpenType MATH");
    }

    /** 真正竖向变体的不可变副本；verticalAdvance 不能作为横向字宽。 */
    default java.util.List<MathData.Variant> mathVariants(int glyphId) {
        throw new UnsupportedOperationException("字体后端不支持 OpenType MATH variants");
    }

    /** 按指定轴返回完整变体；旧后端竖向入口继续有效。 */
    default java.util.List<MathData.Variant> mathVariants(
            int glyphId, club.heiqi.uilib.ui.scene.text.math.MathAxis axis) {
        java.util.Objects.requireNonNull(axis, "axis");
        if (axis == club.heiqi.uilib.ui.scene.text.math.MathAxis.VERTICAL)
            return mathVariants(glyphId);
        throw new UnsupportedOperationException("字体后端不支持横向 OpenType MATH variants");
    }

    /** 竖向部件按底到顶排列；无构建能力返回 null，快照不持有 native 地址。 */
    default MathData.Assembly mathAssembly(int glyphId) {
        return null;
    }

    /** 横向按左到右、竖向按底到顶返回部件；无构建能力返回 null。 */
    default MathData.Assembly mathAssembly(
            int glyphId, club.heiqi.uilib.ui.scene.text.math.MathAxis axis) {
        java.util.Objects.requireNonNull(axis, "axis");
        return axis == club.heiqi.uilib.ui.scene.text.math.MathAxis.VERTICAL
                ? mathAssembly(glyphId)
                : null;
    }

    /** 只有 MATH top-accent coverage 中实际存在的记录才返回数值。 */
    default float mathTopAccentAttachment(int glyphId) {
        return Float.NaN;
    }

    default float mathItalicCorrection(int glyphId) {
        throw new UnsupportedOperationException("字体后端不支持 OpenType MATH");
    }

    /** 实际 HB font 的横向 advance，供非 cmap variant 沿既有字形管线定位。 */
    default float glyphHorizontalAdvance(int glyphId) {
        throw new UnsupportedOperationException("字体后端不支持 glyph advance 查询");
    }

    @Override
    void close();
}
