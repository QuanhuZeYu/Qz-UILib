package club.heiqi.uilib.ui.render;

import org.junit.Assert;
import org.junit.Test;

/**
 * `UiMainLayerSnapshotService` 的纯几何契约测试。
 */
public class UiMainLayerSnapshotServiceTest {

    /**
     * 验证 backdrop 采样区域会按 blur 半径扩张。
     */
    @Test
    public void shouldExpandBackdropSampleRegionByBlurRadius() {
        UiMainLayerSnapshotService.SampleRegion sampleRegion = UiMainLayerSnapshotService.resolveSampleRegion(320,
                240, 40, 50, 100, 120, 14);

        Assert.assertNotNull(sampleRegion);
        Assert.assertEquals(20, sampleRegion.getLeft());
        Assert.assertEquals(30, sampleRegion.getTop());
        Assert.assertEquals(120, sampleRegion.getRight());
        Assert.assertEquals(140, sampleRegion.getBottom());
        Assert.assertEquals(100, sampleRegion.getWidth());
        Assert.assertEquals(110, sampleRegion.getHeight());
    }

    /**
     * 验证采样区域会被限制在屏幕内。
     */
    @Test
    public void shouldClampBackdropSampleRegionToScreenBounds() {
        UiMainLayerSnapshotService.SampleRegion sampleRegion = UiMainLayerSnapshotService.resolveSampleRegion(100,
                80, -10, 4, 96, 90, 36);

        Assert.assertNotNull(sampleRegion);
        Assert.assertEquals(0, sampleRegion.getLeft());
        Assert.assertEquals(0, sampleRegion.getTop());
        Assert.assertEquals(100, sampleRegion.getRight());
        Assert.assertEquals(80, sampleRegion.getBottom());
    }

    /**
     * 验证无效区域不会请求快照。
     */
    @Test
    public void shouldRejectEmptyBackdropSampleRegion() {
        Assert.assertNull(UiMainLayerSnapshotService.resolveSampleRegion(100, 80, 160, 20, 180, 40, 12));
    }

    /**
     * 验证主层快照尺寸有保护上限。
     */
    @Test
    public void shouldLimitMainLayerSnapshotSize() {
        Assert.assertTrue(UiMainLayerSnapshotService.isSnapshotSizeAllowed(320, 240));
        Assert.assertTrue(UiMainLayerSnapshotService.isSnapshotSizeAllowed(4096, 4096));
        Assert.assertFalse(UiMainLayerSnapshotService.isSnapshotSizeAllowed(4097, 240));
        Assert.assertFalse(UiMainLayerSnapshotService.isSnapshotSizeAllowed(320, 4097));
        Assert.assertFalse(UiMainLayerSnapshotService.isSnapshotSizeAllowed(0, 240));
    }

    /**
     * 验证局部采样区域使用自身尺寸参与快照保护，而不是整屏尺寸。
     */
    @Test
    public void shouldLimitLocalSnapshotBySampleRegionSize() {
        UiMainLayerSnapshotService.SampleRegion smallRegion = UiMainLayerSnapshotService.resolveSampleRegion(8000,
                8000, 100, 120, 220, 260, 14);
        UiMainLayerSnapshotService.SampleRegion oversizedRegion = UiMainLayerSnapshotService.resolveSampleRegion(5000,
                5000, 0, 0, 5000, 5000, 0);

        Assert.assertTrue(UiMainLayerSnapshotService.isSnapshotSizeAllowed(smallRegion));
        Assert.assertFalse(UiMainLayerSnapshotService.isSnapshotSizeAllowed(oversizedRegion));
    }

    /**
     * 验证 top-left UI 采样区域会转换为 OpenGL copy 所需的 bottom-left 源坐标。
     */
    @Test
    public void shouldResolveOpenGlCopySourceYFromSampleRegion() {
        UiMainLayerSnapshotService.SampleRegion sampleRegion = UiMainLayerSnapshotService.resolveSampleRegion(320,
                240, 40, 50, 100, 120, 14);

        Assert.assertEquals(100, UiMainLayerSnapshotService.resolveCopySourceY(240, sampleRegion));
    }
}
