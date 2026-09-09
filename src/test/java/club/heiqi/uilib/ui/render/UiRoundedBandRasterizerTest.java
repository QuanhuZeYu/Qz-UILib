package club.heiqi.uilib.ui.render;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;

public class UiRoundedBandRasterizerTest {
    private static final int[] OUTER = {0, 0, 24, 24, 8, 8, 8, 8};
    private static final int[] INNER = {1, 1, 23, 23, 7, 7, 7, 7};
    private static final int[] COLORS = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};

    @Test
    public void doubledCornerMatchesDensePhysicalCoverageReference() {
        RecordingRenderBackend backend = new RecordingRenderBackend();
        UiRenderBackends.__roundedBand(backend.scaled(2.0F), 0, 0, 24, 24,
                OUTER, INNER, null, COLORS);
        int[][] pixels = pixels(backend, 48, 48);
        int partial = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // 独立高精度 Y 积分，X 用圆方程；Python 同步验算误差界。
                double expected = 0;
                for (int sample = 0; sample < 4096; sample++) {
                    double py = y + (sample + 0.5) / 4096.0;
                    double outerLeft = 16 - Math.sqrt(256 - (16 - py) * (16 - py));
                    double innerLeft = py < 2 ? 48 : 16 - Math.sqrt(196 - (16 - py) * (16 - py));
                    expected += Math.max(0, Math.min(x + 1, innerLeft) - Math.max(x, outerLeft));
                }
                expected /= 4096;
                Assert.assertEquals("物理圆弧覆盖率 x=" + x + " y=" + y,
                        expected, (pixels[y][x] >>> 24) / 255.0, 0.015);
                int alpha = pixels[y][x] >>> 24;
                if (alpha > 0 && alpha < 255) partial++;
            }
        }
        Assert.assertTrue("200% 圆角不能退成逻辑像素台阶", partial > 40);
        Assert.assertNotEquals("相邻物理行应重新计算弧线", pixels[0][11], pixels[1][11]);
        Assert.assertEquals("中心保持透明", 0, pixels[24][24]);
    }

    @Test
    public void nestedFractionalScaleUsesOneContinuousConversion() {
        RecordingRenderBackend nested = new RecordingRenderBackend();
        RecordingRenderBackend single = new RecordingRenderBackend();
        UiRenderBackends.__roundedBand(nested.scaled(1.5F).scaled(1.5F), 3, 5, 27, 29,
                OUTER, INNER, null, COLORS);
        UiRenderBackends.__roundedBand(single.scaled(2.25F), 3, 5, 27, 29,
                OUTER, INNER, null, COLORS);
        Assert.assertEquals(single.getCallCount(), nested.getCallCount());
        for (int i = 0; i < single.getCallCount(); i++) {
            Assert.assertArrayEquals(single.getCall(i).args(), nested.getCall(i).args());
        }
    }

    @Test
    public void fractionalStraightEdgeIntegratesOnlyCoveredPartOfPixel() {
        RecordingRenderBackend backend = new RecordingRenderBackend();
        UiRenderBackends.__roundedBand(backend.scaled(1.3F), 0, 0, 10, 10,
                new int[] {0, 0, 10, 10, 0, 0, 0, 0},
                new int[] {1, 1, 9, 9, 0, 0, 0, 0}, null, COLORS);
        int[][] pixels = pixels(backend, 13, 13);
        // Python 验算：(1.3 - 1) * 255 四舍五入为77；float表示误差允许1 alpha。
        Assert.assertEquals(77, pixels[1][6] >>> 24, 1);
        Assert.assertEquals(77, pixels[6][1] >>> 24, 1);
        Assert.assertEquals(0, pixels[6][6]);
    }

    @Test
    public void straightEmptyCenterDoesNotIncreaseSpanCountWithArea() {
        RecordingRenderBackend small = new RecordingRenderBackend();
        RecordingRenderBackend large = new RecordingRenderBackend();
        UiRenderBackends.__roundedBand(small.scaled(2.0F), 0, 0, 24, 24, OUTER, INNER, null, COLORS);
        UiRenderBackends.__roundedBand(large.scaled(2.0F), 0, 0, 1200, 900,
                new int[] {0, 0, 1200, 900, 8, 8, 8, 8},
                new int[] {1, 1, 1199, 899, 7, 7, 7, 7}, null, COLORS);
        Assert.assertEquals("直边应合并，调用量不随空心面积增长", small.getCallCount(), large.getCallCount());
    }

    @Test
    public void resolvedAsymmetricCornerCanExceedHalfTheShortSide() {
        RecordingRenderBackend backend = new RecordingRenderBackend();
        // CSS 相邻半径和合法：左上12，其余0，32x16；不应再次裁成短边一半8。
        UiRenderBackends.__roundedBand(backend, 0, 0, 32, 16,
                new int[] {0, 0, 32, 16, 12, 0, 0, 0}, null, null, COLORS);
        int[][] pixels = pixels(backend, 32, 16);
        Assert.assertEquals("Python 高密度圆方程参考覆盖率为0", 0, pixels[0][6]);
        Assert.assertEquals("右上保留直角", 255, pixels[0][31] >>> 24);
        Assert.assertEquals("下边保留直角", 255, pixels[15][0] >>> 24);
    }

    @Test
    public void asymmetricClippedOutsideBandStaysInsideBoxAndOutsideFace() {
        RecordingRenderBackend backend = new RecordingRenderBackend();
        int[] outer = {0, 0, 24, 21, 8, 0, 6, 4};
        int[] face = {1, -2, 23, 18, 7, 0, 5, 3};
        int[] bounds = {0, -3, 24, 21, 8, 0, 6, 4};
        UiRenderBackends.__roundedBand(backend.scaled(2), 0, 3, 24, 24, outer, face, bounds, COLORS);
        int[][] pixels = pixels(backend, 48, 48);
        Assert.assertEquals(0, pixels[24][24]);
        Assert.assertTrue("厚底仍然可见", (pixels[45][24] >>> 24) > 0);
        Assert.assertEquals("圆角盒外不绘制", 0, pixels[47][47]);
    }

    private static int[][] pixels(RecordingRenderBackend backend, int width, int height) {
        int[][] pixels = new int[height][width];
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) {
            Assert.assertTrue(call.getInt(0) >= 0 && call.getInt(1) >= 0);
            Assert.assertTrue(call.getInt(2) <= width && call.getInt(3) <= height);
            for (int y = call.getInt(1); y < call.getInt(3); y++) {
                for (int x = call.getInt(0); x < call.getInt(2); x++) {
                    Assert.assertEquals("同一带不重复混合覆盖率", 0, pixels[y][x]);
                    pixels[y][x] = call.getInt(4);
                }
            }
        }
        return pixels;
    }
}
