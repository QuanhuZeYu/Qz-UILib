package club.heiqi.uilib.ui.render;

/**
 * 复用池中的快照槽。
 */
final class FrameSnapshot {

    int textureId;
    int sourceTextureId;
    int filteredTextureId;
    int intermediateTextureId;
    int filterFramebufferId;
    int sampleLeft;
    int sampleTop;
    int width;
    int height;
    int sourceWidth;
    int sourceHeight;
    int filteredWidth;
    int filteredHeight;
    int intermediateWidth;
    int intermediateHeight;
    int textureWidth;
    int textureHeight;
    int readFramebufferId = -1;
    int contentRevision;
    int blurRadius;
    int requestedDownsampleFactor = 1;
    int downsampleFactor = 1;
    int filterPassRadius;
    String filterDetail = "raw";
    String regionDetail = "exact";
    String tileDetail = "tiles=0 covered=0 missing=0 reused=0 copied=0";
    int capturedFrameId;
    int activeUseCount;
}
