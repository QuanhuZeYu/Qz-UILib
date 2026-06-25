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
     * <p>INK_BLEED=1.0 外扩后：UV 各向外扩 1/textureSize 像素，几何向外扩 INK_BLEED*glyphScale 像素。
     * 此例 glyphScale=50/100=0.5，故 UV ±1/128，几何 ±0.5、宽高 +1。</p>
     */
    @Test
    public void shouldMapInkAreaByBearing() {
        FontBatchRenderer.GlyphQuadMetrics metrics = FontBatchRenderer.resolveGlyphQuadMetrics(
                128, 10, 20, 30, 40, 6, 18, 50, 100, 24, 32, 2, -10, 200.0F, 300.0F, 50.0F);

        Assert.assertEquals(17.0F / 128.0F, metrics.u0, 0.0001F);
        Assert.assertEquals(43.0F / 128.0F, metrics.u1, 0.0001F);
        Assert.assertEquals(27.0F / 128.0F, metrics.v0, 0.0001F);
        Assert.assertEquals(61.0F / 128.0F, metrics.v1, 0.0001F);
        Assert.assertEquals(200.5F, metrics.quadX, 0.0001F);
        Assert.assertEquals(319.5F, metrics.quadY, 0.0001F);
        Assert.assertEquals(13.0F, metrics.renderWidth, 0.0001F);
        Assert.assertEquals(17.0F, metrics.renderHeight, 0.0001F);
    }

    /**
     * 极小 slot 的 UV 范围也必须保持正向，避免旧内缩逻辑反转坐标。
     *
     * <p>INK_BLEED=1.0 外扩后：UV 各向外扩 1/64 像素，仍保持正向。</p>
     */
    @Test
    public void shouldKeepSinglePixelSlotUvRangeForward() {
        FontBatchRenderer.GlyphQuadMetrics metrics = FontBatchRenderer.resolveGlyphQuadMetrics(
                64, 5, 7, 1, 1, 0, 0, 8, 8, 1, 1, 0, 0, 0.0F, 0.0F, 8.0F);

        Assert.assertEquals(4.0F / 64.0F, metrics.u0, 0.0001F);
        Assert.assertEquals(7.0F / 64.0F, metrics.u1, 0.0001F);
        Assert.assertEquals(6.0F / 64.0F, metrics.v0, 0.0001F);
        Assert.assertEquals(9.0F / 64.0F, metrics.v1, 0.0001F);
        Assert.assertTrue("UV 宽度应为正", metrics.u1 > metrics.u0);
        Assert.assertTrue("UV 高度应为正", metrics.v1 > metrics.v0);
    }

    /**
     * 真实生成契约下 inkLeftInSlot≥INK_PADDING，UV 外扩后仍应落在 slot 内，
     * 不越过 slot 边界采到相邻 slot 或 gap。
     *
     * <p>INK_BLEED=1.0 外扩 1 像素；inkLeftInSlot=8（=INK_PADDING），故 u0≥slotX/texSize、
     * u1≤(slotX+slotWidth)/texSize。此例 textureSize=128，slot 26×26，ink 10×10。</p>
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
        Assert.assertTrue("u0 不应越过 slot 左边界", metrics.u0 >= slotU0);
        Assert.assertTrue("u1 不应越过 slot 右边界", metrics.u1 <= slotU1);
        Assert.assertTrue("v0 不应越过 slot 上边界", metrics.v0 >= slotV0);
        Assert.assertTrue("v1 不应越过 slot 下边界", metrics.v1 <= slotV1);
    }
}
