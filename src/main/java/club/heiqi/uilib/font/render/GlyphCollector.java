package club.heiqi.uilib.font.render;

import club.heiqi.uilib.font.FontType;

/**
 * 字形绘制收集接口：文本渲染层的唯一收集出口。
 *
 * <p>真机实现为 {@link FontBatchRenderer}（GL 批渲染）；headless 实现为
 * {@link GlyphBatchCollector}（纯 JVM，无 LWJGL 依赖）。两者共享同一收集语义与
 * 同一 {@code resolveGlyphQuadMetrics} 几何口径，使 headless 软件渲染验收与
 * 真机渲染在「收集侧指令流」上完全同源。</p>
 */
public interface GlyphCollector {

    /**
     * 按 atlas 基线契约收集一个字符四边形。
     *
     * <p>语义与 {@code FontBatchRenderer#collectBaselineAlignedGlyph} 的
     * generation 基准字号版本一致。</p>
     *
     * @param fontType        字重类型
     * @param pageIndex       字符页索引
     * @param textureId       字符页纹理 ID
     * @param textureSize     字符页纹理边长
     * @param slotX           槽位 X
     * @param slotY           槽位 Y
     * @param slotWidth       槽位宽度
     * @param slotHeight      槽位高度
     * @param atlasBaselineX  槽位内基线 X
     * @param atlasBaselineY  槽位内基线 Y
     * @param lineBaselineY   默认字符格内文本基线 Y（atlas 像素）
     * @param defaultGlyphSize 默认字符格大小
     * @param inkWidth        ink 区域宽度
     * @param inkHeight       ink 区域高度
     * @param bearingX        ink 左边缘相对基线 X 的偏移
     * @param bearingY        ink 上边缘相对基线 Y 的偏移
     * @param x               绘制起点 X
     * @param y               绘制起点 Y
     * @param charSize        字体显示尺寸
     * @param color           文本颜色
     * @param italic          是否斜体
     * @param glyphFlags      字形标记
     * @param baseCharSize    整段基准渲染尺寸（决定共享基线位置）
     */
    void collectBaselineAlignedGlyph(FontType fontType, int pageIndex, int textureId, int textureSize,
            int slotX, int slotY, int slotWidth, int slotHeight, int atlasBaselineX, int atlasBaselineY,
            int lineBaselineY, int defaultGlyphSize, int inkWidth, int inkHeight, int bearingX, int bearingY,
            float x, float y, float charSize, int color, boolean italic, byte glyphFlags, float baseCharSize);

    /**
     * 收集一个纯色文本装饰线矩形（下划线/删除线/LaTeX 规则线）。
     *
     * @param x      起始 X
     * @param y      起始 Y
     * @param width  线条宽度
     * @param height 线条高度
     * @param color  文本颜色
     */
    void collectDecoration(float x, float y, float width, float height, int color);

    /**
     * 收集行内高亮背景矩形（{@code <mark>}），绘制顺序先于字形。
     *
     * @param x      起始 X
     * @param y      起始 Y
     * @param width  矩形宽度
     * @param height 矩形高度
     * @param color  高亮背景色（ARGB）
     */
    void collectMarkBackground(float x, float y, float width, float height, int color);
}
