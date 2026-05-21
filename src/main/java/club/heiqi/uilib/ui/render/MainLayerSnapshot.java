package club.heiqi.uilib.ui.render;

/**
 * 单帧主层快照。
 */
final class MainLayerSnapshot {

    private final int textureId;
    private final int sampleLeft;
    private final int sampleTop;
    private final int width;
    private final int height;
    private final int textureWidth;
    private final int textureHeight;
    private final int readFramebufferId;
    private final int contentRevision;
    private final int downsampleFactor;
    private final String filterDetail;
    private final String regionDetail;
    private final String tileDetail;
    private final boolean reused;

    private MainLayerSnapshot(int textureId, int sampleLeft, int sampleTop, int width, int height,
            int readFramebufferId, int contentRevision, int textureWidth, int textureHeight,
            int downsampleFactor, String filterDetail, String regionDetail, String tileDetail, boolean reused) {
        this.textureId = textureId;
        this.sampleLeft = sampleLeft;
        this.sampleTop = sampleTop;
        this.width = width;
        this.height = height;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.readFramebufferId = readFramebufferId;
        this.contentRevision = contentRevision;
        this.downsampleFactor = downsampleFactor;
        this.filterDetail = filterDetail == null ? "raw" : filterDetail;
        this.regionDetail = regionDetail == null ? "exact" : regionDetail;
        this.tileDetail = tileDetail == null ? "tiles=0 covered=0 missing=0 reused=0 copied=0" : tileDetail;
        this.reused = reused;
    }

    static MainLayerSnapshot captured(int textureId, SampleRegion sampleRegion, int readFramebufferId,
            int contentRevision, int textureWidth, int textureHeight, int downsampleFactor, String filterDetail,
            String regionDetail, String tileDetail) {
        return new MainLayerSnapshot(textureId, sampleRegion.getLeft(), sampleRegion.getTop(), sampleRegion.getWidth(),
                sampleRegion.getHeight(), readFramebufferId, contentRevision, textureWidth, textureHeight,
                downsampleFactor, filterDetail, regionDetail, tileDetail, false);
    }

    static MainLayerSnapshot reused(int textureId, SampleRegion sampleRegion, int readFramebufferId,
            int contentRevision, int textureWidth, int textureHeight, int downsampleFactor, String filterDetail,
            String regionDetail, String tileDetail) {
        return new MainLayerSnapshot(textureId, sampleRegion.getLeft(), sampleRegion.getTop(), sampleRegion.getWidth(),
                sampleRegion.getHeight(), readFramebufferId, contentRevision, textureWidth, textureHeight,
                downsampleFactor, filterDetail, regionDetail, tileDetail, true);
    }

    int getTextureId() {
        return textureId;
    }

    int getSampleLeft() {
        return sampleLeft;
    }

    int getSampleTop() {
        return sampleTop;
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    int getTextureWidth() {
        return textureWidth;
    }

    int getTextureHeight() {
        return textureHeight;
    }

    int getReadFramebufferId() {
        return readFramebufferId;
    }

    int getContentRevision() {
        return contentRevision;
    }

    int getDownsampleFactor() {
        return downsampleFactor;
    }

    String getFilterDetail() {
        return filterDetail;
    }

    String getRegionDetail() {
        return regionDetail;
    }

    String getTileDetail() {
        return tileDetail;
    }

    boolean isReused() {
        return reused;
    }
}
