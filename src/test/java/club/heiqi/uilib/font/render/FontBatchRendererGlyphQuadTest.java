package club.heiqi.uilib.font.render;

import org.junit.Assert;
import org.junit.Test;

/**
 * 字体批渲染几何契约测试。
 */
public class FontBatchRendererGlyphQuadTest {

    /**
     * 可变 slot 应按 ink 边界落位，并只采样 ink UV。
     *
     * <p>INK_BLEED=1.0 外扩后：texCoord（u0/u1/v0/v1）各向外扩 1/textureSize 像素，几何向外扩 INK_BLEED*glyphScale 像素。
     * 此例 glyphScale=50/100=0.5，故 texCoord ±1/128，几何 ±0.5、宽高 +1。</p>
     *
     * <p>uvBounds（clipU0/clipU1/clipV0/clipV1）使用整个 slot 范围放行 shader 采样，
     * clipU0=slotX/texSize、clipU1=(slotX+slotWidth)/texSize，v 轴同理。</p>
     */
    @Test
    public void shouldMapInkAreaByBearing() {
        FontBatchRenderer.GlyphQuadMetrics metrics = FontBatchRenderer.resolveGlyphQuadMetrics(
                128, 10, 20, 30, 40, 6, 18, 50, 100, 24, 32, 2, -10, 200.0F, 300.0F, 50.0F);

        // 顶点 texCoord：ink ± INK_BLEED
        Assert.assertEquals(17.0F / 128.0F, metrics.u0, 0.0001F);
        Assert.assertEquals(43.0F / 128.0F, metrics.u1, 0.0001F);
        Assert.assertEquals(27.0F / 128.0F, metrics.v0, 0.0001F);
        Assert.assertEquals(61.0F / 128.0F, metrics.v1, 0.0001F);
        // uvBounds（clip 边界）：整个 slot 范围
        Assert.assertEquals(10.0F / 128.0F, metrics.clipU0, 0.0001F);
        Assert.assertEquals((10.0F + 30.0F) / 128.0F, metrics.clipU1, 0.0001F);
        Assert.assertEquals(20.0F / 128.0F, metrics.clipV0, 0.0001F);
        Assert.assertEquals((20.0F + 40.0F) / 128.0F, metrics.clipV1, 0.0001F);
        Assert.assertEquals(200.5F, metrics.quadX, 0.0001F);
        Assert.assertEquals(319.5F, metrics.quadY, 0.0001F);
        Assert.assertEquals(13.0F, metrics.renderWidth, 0.0001F);
        Assert.assertEquals(17.0F, metrics.renderHeight, 0.0001F);
    }

    /**
     * 混排字号共享基线：不同 glyph 字号（charSize）但同一段基准（baseCharSize）时，
     * 两 glyph 的基线必须落在同一高度；glyph 自身几何仍按各自字号缩放。
     */
    @Test
    public void shouldShareBaselineAcrossMixedCharSizes() {
        // lineBaselineY=50、defaultGlyphSize=64、baseCharSize=32 → 共享基线 y=300+50×0.5=325
        FontBatchRenderer.GlyphQuadMetrics small = FontBatchRenderer.resolveGlyphQuadMetrics(
                128, 10, 20, 30, 40, 6, 18, 50, 64, 24, 32, 2, -10, 200.0F, 300.0F, 32.0F, 32.0F);
        FontBatchRenderer.GlyphQuadMetrics large = FontBatchRenderer.resolveGlyphQuadMetrics(
                128, 10, 20, 30, 40, 6, 18, 50, 64, 24, 32, 2, -10, 200.0F, 300.0F, 64.0F, 32.0F);

        // quadY = 基线 + bearingY×glyphScale - bleed；反解基线验证共享
        float smallBaseline = small.quadY + 10.0F * 0.5F + 0.5F;
        float largeBaseline = large.quadY + 10.0F * 1.0F + 1.0F;

        Assert.assertEquals(325.0F, smallBaseline, 0.0001F);
        Assert.assertEquals(smallBaseline, largeBaseline, 0.0001F);
        // 大字 quad 更高（bearing 按自身字号放大），但顶部不与小字对齐
        Assert.assertTrue(large.quadY < small.quadY);
    }

    /**
     * 极小 slot 的 UV 范围也必须保持正向，避免旧内缩逻辑反转坐标。
     *
     * <p>INK_BLEED=1.0 外扩后：texCoord 各向外扩 1/64 像素，仍保持正向。
     * uvBounds 用 slot 范围：clipU0=5/64、clipU1=6/64。</p>
     */
    @Test
    public void shouldKeepSinglePixelSlotUvRangeForward() {
        FontBatchRenderer.GlyphQuadMetrics metrics = FontBatchRenderer.resolveGlyphQuadMetrics(
                64, 5, 7, 1, 1, 0, 0, 8, 8, 1, 1, 0, 0, 0.0F, 0.0F, 8.0F);

        Assert.assertEquals(4.0F / 64.0F, metrics.u0, 0.0001F);
        Assert.assertEquals(7.0F / 64.0F, metrics.u1, 0.0001F);
        Assert.assertEquals(6.0F / 64.0F, metrics.v0, 0.0001F);
        Assert.assertEquals(9.0F / 64.0F, metrics.v1, 0.0001F);
        // texCoord（ink±bleed）允许越过 slot 边界，这是 mipmap 羽化的正常行为
        Assert.assertTrue("texCoord 宽度应为正", metrics.u1 > metrics.u0);
        Assert.assertTrue("texCoord 高度应为正", metrics.v1 > metrics.v0);
        // uvBounds（slot 范围）同样保持正向
        Assert.assertEquals(5.0F / 64.0F, metrics.clipU0, 0.0001F);
        Assert.assertEquals(6.0F / 64.0F, metrics.clipU1, 0.0001F);
        Assert.assertEquals(7.0F / 64.0F, metrics.clipV0, 0.0001F);
        Assert.assertEquals(8.0F / 64.0F, metrics.clipV1, 0.0001F);
        Assert.assertTrue("uvBounds 宽度应为正", metrics.clipU1 > metrics.clipU0);
        Assert.assertTrue("uvBounds 高度应为正", metrics.clipV1 > metrics.clipV0);
    }

    /**
     * uvBounds（clip 边界）必须恰好等于整个 slot 范围，放行完整 padding 区的 mipmap 羽化采样；
     * 而 texCoord（ink±bleed）在真实 ink padding 契约下仍落在 slot 内。
     *
     * <p>P1 拆分后：uvBounds 用 slot 范围（clipU0=slotX/texSize 等），texCoord 用 ink±bleed。
     * 此例 textureSize=128，slot 26×26，ink 10×10，ink padding=8，bleed=1。</p>
     */
    @Test
    public void shouldKeepUvInsideSlotUnderRealInkPaddingContract() {
        int textureSize = 128;
        int slotX = 10;
        int slotY = 20;
        int slotWidth = 26;
        int slotHeight = 26;
        int atlasBaselineX = 8;
        int atlasBaselineY = 8;
        int inkWidth = 10;
        int inkHeight = 10;
        FontBatchRenderer.GlyphQuadMetrics metrics = FontBatchRenderer.resolveGlyphQuadMetrics(
                textureSize, slotX, slotY, slotWidth, slotHeight, atlasBaselineX, atlasBaselineY,
                50, 100, inkWidth, inkHeight, 0, 0, 200.0F, 300.0F, 50.0F);

        float slotU0 = (float) slotX / (float) textureSize;
        float slotU1 = (float) (slotX + slotWidth) / (float) textureSize;
        float slotV0 = (float) slotY / (float) textureSize;
        float slotV1 = (float) (slotY + slotHeight) / (float) textureSize;
        // uvBounds 必须精确等于 slot 范围，放行完整 padding 羽化
        Assert.assertEquals("clipU0 应等于 slot 左边界", slotU0, metrics.clipU0, 0.0001F);
        Assert.assertEquals("clipU1 应等于 slot 右边界", slotU1, metrics.clipU1, 0.0001F);
        Assert.assertEquals("clipV0 应等于 slot 上边界", slotV0, metrics.clipV0, 0.0001F);
        Assert.assertEquals("clipV1 应等于 slot 下边界", slotV1, metrics.clipV1, 0.0001F);
        // texCoord（ink±bleed）在真实 ink padding 契约下仍落在 slot 内
        Assert.assertTrue("texCoord u0 不应越过 slot 左边界", metrics.u0 >= slotU0);
        Assert.assertTrue("texCoord u1 不应越过 slot 右边界", metrics.u1 <= slotU1);
        Assert.assertTrue("texCoord v0 不应越过 slot 上边界", metrics.v0 >= slotV0);
        Assert.assertTrue("texCoord v1 不应越过 slot 下边界", metrics.v1 <= slotV1);
    }
}
