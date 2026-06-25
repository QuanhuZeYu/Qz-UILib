package club.heiqi.uilib.font.render;

import org.junit.Assert;
import org.junit.Test;

/**
 * 字体批渲染几何契约测试。
 */
public class FontBatchRendererGlyphQuadTest {

    /**
     * 可变 slot 应按 ink 边界落位，并只采样 ink UV。
     */
    @Test
    public void shouldMapInkAreaByBearing() {
        FontBatchRenderer.GlyphQuadMetrics metrics = FontBatchRenderer.resolveGlyphQuadMetrics(
                128, 10, 20, 30, 40, 6, 18, 50, 100, 24, 32, 2, -10, 200.0F, 300.0F, 50.0F);

        Assert.assertEquals(18.0F / 128.0F, metrics.u0, 0.0001F);
        Assert.assertEquals(42.0F / 128.0F, metrics.u1, 0.0001F);
        Assert.assertEquals(28.0F / 128.0F, metrics.v0, 0.0001F);
        Assert.assertEquals(60.0F / 128.0F, metrics.v1, 0.0001F);
        Assert.assertEquals(201.0F, metrics.quadX, 0.0001F);
        Assert.assertEquals(320.0F, metrics.quadY, 0.0001F);
        Assert.assertEquals(12.0F, metrics.renderWidth, 0.0001F);
        Assert.assertEquals(16.0F, metrics.renderHeight, 0.0001F);
    }

    /**
     * 极小 slot 的 UV 范围也必须保持正向，避免旧内缩逻辑反转坐标。
     */
    @Test
    public void shouldKeepSinglePixelSlotUvRangeForward() {
        FontBatchRenderer.GlyphQuadMetrics metrics = FontBatchRenderer.resolveGlyphQuadMetrics(
                64, 5, 7, 1, 1, 0, 0, 8, 8, 1, 1, 0, 0, 0.0F, 0.0F, 8.0F);

        Assert.assertEquals(5.0F / 64.0F, metrics.u0, 0.0001F);
        Assert.assertEquals(6.0F / 64.0F, metrics.u1, 0.0001F);
        Assert.assertEquals(7.0F / 64.0F, metrics.v0, 0.0001F);
        Assert.assertEquals(8.0F / 64.0F, metrics.v1, 0.0001F);
        Assert.assertTrue("UV 宽度应为正", metrics.u1 > metrics.u0);
        Assert.assertTrue("UV 高度应为正", metrics.v1 > metrics.v0);
    }
}
