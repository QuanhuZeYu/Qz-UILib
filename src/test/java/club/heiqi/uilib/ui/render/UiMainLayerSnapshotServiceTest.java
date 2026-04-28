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
}
