package club.heiqi.uilib.font.render;

import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;

/** 分片的几何不重叠，采样 clip 仍包含邻域 padding。 */
public class MathGlyphTileQuadTest {
    @Test
    public void adjacentTileCoresMeetWithoutBleedAtEveryScale() {
        for (float scale : new float[] { 1.0F, 1.25F, 4.0F, 8.0F }) {
            FontBatchRenderer.GlyphQuadMetrics first = tile(0, 2, scale);
            FontBatchRenderer.GlyphQuadMetrics second = tile(60, -58, scale);
            Assert.assertEquals(first.quadX + first.renderWidth, second.quadX, 0.0001F);
            Assert.assertEquals(first.quadY, second.quadY, 0.0001F);
            Assert.assertEquals(0.25F, first.quadX, 0.0001F);
            Assert.assertTrue(first.clipU0 < first.u0);
            Assert.assertTrue(first.clipU1 > first.u1);
            Assert.assertEquals(first.u0, second.u0, 0.0001F);
            Assert.assertEquals(first.u1, second.u1, 0.0001F);
        }
    }

    private static FontBatchRenderer.GlyphQuadMetrics tile(int bearingX, int atlasBaselineX, float scale) {
        return FontBatchRenderer.resolveGlyphQuadMetrics(128, 8, 8, 64, 16, atlasBaselineX, 10,
                0, 16, 60, 12, bearingX, -8, 0.25F, 0.5F, 16 * scale, 16 * scale,
                GlyphRuntimeTables.GLYPH_FLAG_MATH_CORE);
    }
}
