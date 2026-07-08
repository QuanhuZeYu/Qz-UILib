package club.heiqi.uilib.ui.render;

/**
 * 单个 atlas tile 的数据来源。
 */
final class TileAssemblyEntry {

    final SampleRegion sampleRegion;
    final FrameSnapshot sourceSnapshot;

    TileAssemblyEntry(SampleRegion sampleRegion, FrameSnapshot sourceSnapshot) {
        this.sampleRegion = sampleRegion;
        this.sourceSnapshot = sourceSnapshot;
    }
}
