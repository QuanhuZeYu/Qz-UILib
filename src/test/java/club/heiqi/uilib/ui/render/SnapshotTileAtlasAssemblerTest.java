package club.heiqi.uilib.ui.render;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/** 原始截图与滤镜结果分开复用：不能把兄弟玻璃重新采进同一批次背景。 */
public class SnapshotTileAtlasAssemblerTest {
    @Test
    public void shouldReuseRawTilesAcrossBlurAndDownsampleChanges() {
        FrameSnapshot source = snapshot();
        SampleRegion region = new SampleRegion(0, 0, 128, 128);
        for (int blur : new int[] { 0, 17, 18, 33, 34, 64 }) {
            int factor = UiMainLayerSnapshotGeometry.resolveDownsampleFactor(blur);
            List<TileAssemblyEntry> entries = SnapshotTileAtlasAssembler.resolveTileAssemblyEntries(
                    Collections.singletonList(source), 7, 3, region, 11, factor, blur);
            Assert.assertEquals(1, entries.size());
            Assert.assertSame("raw capture must survive filter parameter changes", source,
                    entries.get(0).sourceSnapshot);
            Assert.assertEquals(1, SnapshotTileAtlasAssembler.countReusableTileEntries(entries));
        }
    }

    @Test
    public void shouldRecaptureOnlyTilesOutsideTheRawCoverage() {
        FrameSnapshot source = snapshot();
        List<TileAssemblyEntry> entries = SnapshotTileAtlasAssembler.resolveTileAssemblyEntries(
                Collections.singletonList(source), 7, 3, new SampleRegion(0, 0, 256, 128), 11, 4, 34);
        Assert.assertEquals(2, entries.size());
        Assert.assertSame(source, entries.get(0).sourceSnapshot);
        Assert.assertNull(entries.get(1).sourceSnapshot);
    }

    @Test
    public void shouldNotReuseRawPixelsAcrossFramesTargetsOrRevisions() {
        FrameSnapshot source = snapshot();
        SampleRegion region = new SampleRegion(0, 0, 128, 128);
        Assert.assertNull(find(source, region, 8, 3, 11));
        Assert.assertNull(find(source, region, 7, 4, 11));
        Assert.assertNull(find(source, region, 7, 3, 12));
        Assert.assertNull(find(source, new SampleRegion(0, 0, 129, 128), 7, 3, 11));
        source.sourceTextureId = 0;
        Assert.assertNull(find(source, region, 7, 3, 11));
    }

    private static FrameSnapshot find(FrameSnapshot source, SampleRegion region, int frame, int fbo, int revision) {
        return SnapshotTileAtlasAssembler.findTileSourceSnapshot(Collections.singletonList(source),
                frame, fbo, region, revision, 4, 34);
    }

    private static FrameSnapshot snapshot() {
        FrameSnapshot source = new FrameSnapshot();
        source.sourceTextureId = 1;
        source.textureId = 2; // 已滤镜输出不能作为 raw atlas 的输入。
        source.capturedFrameId = 7;
        source.readFramebufferId = 3;
        source.contentRevision = 11;
        source.width = 128;
        source.height = 128;
        source.requestedDownsampleFactor = 1;
        source.blurRadius = 17;
        return source;
    }
}
