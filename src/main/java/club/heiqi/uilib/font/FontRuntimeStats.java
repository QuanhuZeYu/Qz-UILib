package club.heiqi.uilib.font;

/**
 * 字体系统运行时统计快照。
 */
public class FontRuntimeStats {

    private final int pendingUploadCount;
    private final int readyGlyphCount;
    private final int normalPageCount;
    private final int boldPageCount;
    private final int queuedDrawStageUploadCount;
    private final int frameQuadCount;
    private final long fontMatchCacheHitCount;
    private final long fontMatchCacheMissCount;
    private final long widthCacheHitCount;
    private final long widthCacheMissCount;

    /**
     * 创建运行时统计快照。
     *
     * @param pendingUploadCount 待上传数量
     * @param readyGlyphCount 已就绪字符数量
     * @param normalPageCount 普通字符页数量
     * @param boldPageCount 粗体字符页数量
     * @param queuedDrawStageUploadCount 最近一秒 draw-stage 上传次数
     * @param frameQuadCount 当前帧四边形数量
     * @param fontMatchCacheHitCount 字体匹配缓存命中次数
     * @param fontMatchCacheMissCount 字体匹配缓存未命中次数
     * @param widthCacheHitCount 宽度缓存命中次数
     * @param widthCacheMissCount 宽度缓存未命中次数
     */
    public FontRuntimeStats(
            int pendingUploadCount,
            int readyGlyphCount,
            int normalPageCount,
            int boldPageCount,
            int queuedDrawStageUploadCount,
            int frameQuadCount,
            long fontMatchCacheHitCount,
            long fontMatchCacheMissCount,
            long widthCacheHitCount,
            long widthCacheMissCount) {
        this.pendingUploadCount = pendingUploadCount;
        this.readyGlyphCount = readyGlyphCount;
        this.normalPageCount = normalPageCount;
        this.boldPageCount = boldPageCount;
        this.queuedDrawStageUploadCount = queuedDrawStageUploadCount;
        this.frameQuadCount = frameQuadCount;
        this.fontMatchCacheHitCount = fontMatchCacheHitCount;
        this.fontMatchCacheMissCount = fontMatchCacheMissCount;
        this.widthCacheHitCount = widthCacheHitCount;
        this.widthCacheMissCount = widthCacheMissCount;
    }

    public int getPendingUploadCount() {
        return pendingUploadCount;
    }

    public int getReadyGlyphCount() {
        return readyGlyphCount;
    }

    public int getNormalPageCount() {
        return normalPageCount;
    }

    public int getBoldPageCount() {
        return boldPageCount;
    }

    public int getQueuedDrawStageUploadCount() {
        return queuedDrawStageUploadCount;
    }

    public int getFrameQuadCount() {
        return frameQuadCount;
    }

    public long getFontMatchCacheHitCount() {
        return fontMatchCacheHitCount;
    }

    public long getFontMatchCacheMissCount() {
        return fontMatchCacheMissCount;
    }

    public long getWidthCacheHitCount() {
        return widthCacheHitCount;
    }

    public long getWidthCacheMissCount() {
        return widthCacheMissCount;
    }

    @Override
    public String toString() {
        return "pendingUploads=" + pendingUploadCount
                + ", readyGlyphs=" + readyGlyphCount
                + ", normalPages=" + normalPageCount
                + ", boldPages=" + boldPageCount
                + ", drawStageUploadsLastSecond=" + queuedDrawStageUploadCount
                + ", frameQuads=" + frameQuadCount
                + ", fontMatchCacheHits=" + fontMatchCacheHitCount
                + ", fontMatchCacheMisses=" + fontMatchCacheMissCount
                + ", widthCacheHits=" + widthCacheHitCount
                + ", widthCacheMisses=" + widthCacheMissCount;
    }
}
