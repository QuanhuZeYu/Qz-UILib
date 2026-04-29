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

    /**
     * 验证采样区域会扩展到固定 block 边界，便于相近 glass 元素复用同一 snapshot。
     */
    @Test
    public void shouldAlignSampleRegionToSnapshotBlocks() {
        UiMainLayerSnapshotService.SampleRegion sampleRegion = UiMainLayerSnapshotService.resolveSampleRegion(512,
                360, 100, 100, 140, 140, 18);
        UiMainLayerSnapshotService.SampleRegion blockRegion = UiMainLayerSnapshotService.resolveBlockAlignedSampleRegion(
                512, 360, sampleRegion);

        Assert.assertEquals(0, blockRegion.getLeft());
        Assert.assertEquals(0, blockRegion.getTop());
        Assert.assertEquals(256, blockRegion.getRight());
        Assert.assertEquals(256, blockRegion.getBottom());
    }

    /**
     * 验证相近但不完全相同的采样区域可归并到同一个 block snapshot 区域。
     */
    @Test
    public void shouldBucketNearbySampleRegionsIntoSameSnapshotBlock() {
        UiMainLayerSnapshotService.SampleRegion firstRegion = UiMainLayerSnapshotService.resolveSampleRegion(512,
                360, 100, 100, 140, 140, 18);
        UiMainLayerSnapshotService.SampleRegion secondRegion = UiMainLayerSnapshotService.resolveSampleRegion(512,
                360, 110, 108, 150, 148, 18);
        UiMainLayerSnapshotService.SampleRegion firstBlock = UiMainLayerSnapshotService.resolveBlockAlignedSampleRegion(
                512, 360, firstRegion);
        UiMainLayerSnapshotService.SampleRegion secondBlock = UiMainLayerSnapshotService.resolveBlockAlignedSampleRegion(
                512, 360, secondRegion);

        Assert.assertEquals(firstBlock.getLeft(), secondBlock.getLeft());
        Assert.assertEquals(firstBlock.getTop(), secondBlock.getTop());
        Assert.assertEquals(firstBlock.getRight(), secondBlock.getRight());
        Assert.assertEquals(firstBlock.getBottom(), secondBlock.getBottom());
    }

    /**
     * 验证采样区域会映射到稳定的 128px tile 网格范围。
     */
    @Test
    public void shouldResolveSnapshotTileRegion() {
        UiMainLayerSnapshotService.SampleRegion sampleRegion = UiMainLayerSnapshotService.resolveSampleRegion(768,
                512, 180, 130, 250, 260, 18);
        UiMainLayerSnapshotService.SampleRegion blockRegion = UiMainLayerSnapshotService.resolveBlockAlignedSampleRegion(
                768, 512, sampleRegion);

        UiMainLayerSnapshotService.TileRegion tileRegion = UiMainLayerSnapshotService.resolveTileRegion(blockRegion);

        Assert.assertEquals(1, tileRegion.getTileLeft());
        Assert.assertEquals(0, tileRegion.getTileTop());
        Assert.assertEquals(3, tileRegion.getTileRight());
        Assert.assertEquals(3, tileRegion.getTileBottom());
        Assert.assertEquals(2, tileRegion.getTileWidth());
        Assert.assertEquals(3, tileRegion.getTileHeight());
        Assert.assertEquals(6, tileRegion.getTileCount());
    }

    /**
     * 验证 tile 数量使用向上取整，覆盖非对齐采样区域的边缘 tile。
     */
    @Test
    public void shouldCountTilesCoveredBySampleRegion() {
        UiMainLayerSnapshotService.SampleRegion blockRegion = UiMainLayerSnapshotService.resolveBlockAlignedSampleRegion(
                512, 512, UiMainLayerSnapshotService.resolveSampleRegion(512, 512, 100, 100, 140, 140, 18));
        UiMainLayerSnapshotService.SampleRegion unevenRegion = UiMainLayerSnapshotService.resolveSampleRegion(512,
                512, 128, 128, 129, 129, 0);

        Assert.assertEquals(4, UiMainLayerSnapshotService.resolveTileCount(blockRegion));
        Assert.assertEquals(4, UiMainLayerSnapshotService.resolveTileCount(unevenRegion));
    }

    /**
     * 验证较大的 block 区域可作为同帧临时 atlas 覆盖后续较小采样区域。
     */
    @Test
    public void shouldReuseLargerBlockRegionAsSnapshotAtlas() {
        UiMainLayerSnapshotService.SampleRegion largerRegion = UiMainLayerSnapshotService.resolveSampleRegion(768,
                512, 90, 90, 330, 230, 18);
        UiMainLayerSnapshotService.SampleRegion smallerRegion = UiMainLayerSnapshotService.resolveSampleRegion(768,
                512, 140, 120, 190, 160, 18);
        UiMainLayerSnapshotService.SampleRegion unrelatedRegion = UiMainLayerSnapshotService.resolveSampleRegion(768,
                512, 430, 120, 480, 160, 18);

        UiMainLayerSnapshotService.SampleRegion largerBlock = UiMainLayerSnapshotService.resolveBlockAlignedSampleRegion(
                768, 512, largerRegion);
        UiMainLayerSnapshotService.SampleRegion smallerBlock = UiMainLayerSnapshotService.resolveBlockAlignedSampleRegion(
                768, 512, smallerRegion);
        UiMainLayerSnapshotService.SampleRegion unrelatedBlock = UiMainLayerSnapshotService.resolveBlockAlignedSampleRegion(
                768, 512, unrelatedRegion);

        Assert.assertTrue(UiMainLayerSnapshotService.containsSampleRegion(largerBlock, smallerBlock));
        Assert.assertFalse(UiMainLayerSnapshotService.containsSampleRegion(smallerBlock, largerBlock));
        Assert.assertFalse(UiMainLayerSnapshotService.containsSampleRegion(largerBlock, unrelatedBlock));
    }

    /**
     * 验证大半径 blur 会进入降采样滤镜路径。
     */
    @Test
    public void shouldResolveDownsampleFactorFromBlurRadius() {
        Assert.assertEquals(1, UiMainLayerSnapshotService.resolveDownsampleFactor(0));
        Assert.assertEquals(1, UiMainLayerSnapshotService.resolveDownsampleFactor(17));
        Assert.assertEquals(2, UiMainLayerSnapshotService.resolveDownsampleFactor(18));
        Assert.assertEquals(2, UiMainLayerSnapshotService.resolveDownsampleFactor(33));
        Assert.assertEquals(4, UiMainLayerSnapshotService.resolveDownsampleFactor(34));
    }

    /**
     * 验证降采样尺寸使用向上取整，避免奇数尺寸丢失边缘像素。
     */
    @Test
    public void shouldResolveDownsampledTextureSize() {
        Assert.assertEquals(50, UiMainLayerSnapshotService.resolveDownsampledSize(100, 2));
        Assert.assertEquals(51, UiMainLayerSnapshotService.resolveDownsampledSize(101, 2));
        Assert.assertEquals(26, UiMainLayerSnapshotService.resolveDownsampledSize(101, 4));
        Assert.assertEquals(1, UiMainLayerSnapshotService.resolveDownsampledSize(0, 4));
    }

    /**
     * 验证降采样后的滤镜 pass 会为大半径 blur 分配独立 separable blur 半径。
     */
    @Test
    public void shouldResolveFilterPassBlurRadius() {
        Assert.assertEquals(0, UiMainLayerSnapshotService.resolveFilterPassRadius(17, 1));
        Assert.assertEquals(2, UiMainLayerSnapshotService.resolveFilterPassRadius(18, 2));
        Assert.assertEquals(3, UiMainLayerSnapshotService.resolveFilterPassRadius(33, 2));
        Assert.assertEquals(2, UiMainLayerSnapshotService.resolveFilterPassRadius(36, 4));
        Assert.assertEquals(8, UiMainLayerSnapshotService.resolveFilterPassRadius(200, 4));
    }
}
