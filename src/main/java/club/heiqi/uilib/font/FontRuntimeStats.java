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
    private final int lastFlushPageSubmitCount;
    private final int lastFlushDrawCallCount;
    private final int lastFlushTextureSwitchCount;
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
     * @param lastFlushPageSubmitCount 上次 flush 实际提交的字符页命令数量
     * @param lastFlushDrawCallCount 上次 flush 实际触发的 draw call 数量
     * @param lastFlushTextureSwitchCount 上次 flush 实际发生的纹理切换数量
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
            int lastFlushPageSubmitCount,
            int lastFlushDrawCallCount,
            int lastFlushTextureSwitchCount,
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
        this.lastFlushPageSubmitCount = lastFlushPageSubmitCount;
        this.lastFlushDrawCallCount = lastFlushDrawCallCount;
        this.lastFlushTextureSwitchCount = lastFlushTextureSwitchCount;
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

    public int getLastFlushPageSubmitCount() {
        return lastFlushPageSubmitCount;
    }

    public int getLastFlushDrawCallCount() {
        return lastFlushDrawCallCount;
    }

    public int getLastFlushTextureSwitchCount() {
        return lastFlushTextureSwitchCount;
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
                + ", lastFlushPageSubmits=" + lastFlushPageSubmitCount
                + ", lastFlushDrawCalls=" + lastFlushDrawCallCount
                + ", lastFlushTextureSwitches=" + lastFlushTextureSwitchCount
                + ", fontMatchCacheHits=" + fontMatchCacheHitCount
                + ", fontMatchCacheMisses=" + fontMatchCacheMissCount
                + ", widthCacheHits=" + widthCacheHitCount
                + ", widthCacheMisses=" + widthCacheMissCount;
    }
}
