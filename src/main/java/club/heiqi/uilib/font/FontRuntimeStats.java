package club.heiqi.uilib.font;

/**
 * 字体系统运行时统计快照。
 */
public class FontRuntimeStats {

    private final int pendingUploadCount;
    private final int readyGlyphCount;
    private final int normalPageCount;
    private final int boldPageCount;
    private final int directTableCodepointCount;
    private final int directTableSlotsPerPage;
    private final int queuedDrawStageUploadCount;
    private final int frameQuadCount;
    private final int lastFlushPageSubmitCount;
    private final int lastFlushDrawCallCount;
    private final int lastFlushTextureBindCount;
    private final long fontMatchCacheHitCount;
    private final long fontMatchCacheMissCount;
    private final long derivedFontCacheHitCount;
    private final long derivedFontCacheMissCount;
    private final long widthCacheHitCount;
    private final long widthCacheMissCount;
    private final int activeDemandCount;
    private final int maxDemandCount;
    private final int demandHighWaterMark;
    private final long rejectedDemandCount;
    private final long promotedDemandCount;
    private final long pendingBitmapBytes;
    private final long maxPendingBitmapBytes;
    private final int pendingUploadHighWaterMark;
    private final long pendingBitmapBytesHighWaterMark;
    private final int blockedGlyphPublisherCount;
    private final long mailboxBackpressureCount;
    private final long mailboxRejectedCount;

    /**
     * 创建空运行时统计快照。
     *
     * @return 空统计
     */
    public static FontRuntimeStats empty() {
        return new FontRuntimeStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, 0L,
                0L);
    }

    /**
     * 创建运行时统计快照。
     *
     * @param pendingUploadCount 待上传数量
     * @param readyGlyphCount 已就绪字符数量
     * @param normalPageCount 普通字符页数量
     * @param boldPageCount 粗体字符页数量
     * @param directTableCodepointCount direct-index 码点容量
     * @param directTableSlotsPerPage 每页预计算槽位数量
     * @param queuedDrawStageUploadCount 最近一秒 draw-stage 上传次数
     * @param frameQuadCount 当前帧四边形数量
     * @param lastFlushPageSubmitCount 上次 flush 实际提交的字符页批次数量
     * @param lastFlushDrawCallCount 上次 flush 实际触发的 draw call 数量
     * @param lastFlushTextureBindCount 上次 flush 由字体批渲染器执行的纹理绑定数量
     * @param fontMatchCacheHitCount 字体匹配缓存命中次数
     * @param fontMatchCacheMissCount 字体匹配缓存未命中次数
     * @param derivedFontCacheHitCount 派生字体缓存命中次数
     * @param derivedFontCacheMissCount 派生字体缓存未命中次数
     * @param widthCacheHitCount 宽度缓存命中次数
     * @param widthCacheMissCount 宽度缓存未命中次数
     */
    public FontRuntimeStats(
            int pendingUploadCount,
            int readyGlyphCount,
            int normalPageCount,
            int boldPageCount,
            int directTableCodepointCount,
            int directTableSlotsPerPage,
            int queuedDrawStageUploadCount,
            int frameQuadCount,
            int lastFlushPageSubmitCount,
            int lastFlushDrawCallCount,
            int lastFlushTextureBindCount,
            long fontMatchCacheHitCount,
            long fontMatchCacheMissCount,
            long derivedFontCacheHitCount,
            long derivedFontCacheMissCount,
            long widthCacheHitCount,
            long widthCacheMissCount) {
        this(pendingUploadCount, readyGlyphCount, normalPageCount, boldPageCount, directTableCodepointCount,
                directTableSlotsPerPage, queuedDrawStageUploadCount, frameQuadCount, lastFlushPageSubmitCount,
                lastFlushDrawCallCount, lastFlushTextureBindCount, fontMatchCacheHitCount, fontMatchCacheMissCount,
                derivedFontCacheHitCount, derivedFontCacheMissCount, widthCacheHitCount, widthCacheMissCount,
                0, 0, 0, 0L, 0L, 0L, 0L, 0, 0L, 0, 0L, 0L);
    }

    FontRuntimeStats(
            int pendingUploadCount,
            int readyGlyphCount,
            int normalPageCount,
            int boldPageCount,
            int directTableCodepointCount,
            int directTableSlotsPerPage,
            int queuedDrawStageUploadCount,
            int frameQuadCount,
            int lastFlushPageSubmitCount,
            int lastFlushDrawCallCount,
            int lastFlushTextureBindCount,
            long fontMatchCacheHitCount,
            long fontMatchCacheMissCount,
            long derivedFontCacheHitCount,
            long derivedFontCacheMissCount,
            long widthCacheHitCount,
            long widthCacheMissCount,
            int activeDemandCount,
            int maxDemandCount,
            int demandHighWaterMark,
            long rejectedDemandCount,
            long promotedDemandCount,
            long pendingBitmapBytes,
            long maxPendingBitmapBytes,
            int pendingUploadHighWaterMark,
            long pendingBitmapBytesHighWaterMark,
            int blockedGlyphPublisherCount,
            long mailboxBackpressureCount,
            long mailboxRejectedCount) {
        this.pendingUploadCount = pendingUploadCount;
        this.readyGlyphCount = readyGlyphCount;
        this.normalPageCount = normalPageCount;
        this.boldPageCount = boldPageCount;
        this.directTableCodepointCount = directTableCodepointCount;
        this.directTableSlotsPerPage = directTableSlotsPerPage;
        this.queuedDrawStageUploadCount = queuedDrawStageUploadCount;
        this.frameQuadCount = frameQuadCount;
        this.lastFlushPageSubmitCount = lastFlushPageSubmitCount;
        this.lastFlushDrawCallCount = lastFlushDrawCallCount;
        this.lastFlushTextureBindCount = lastFlushTextureBindCount;
        this.fontMatchCacheHitCount = fontMatchCacheHitCount;
        this.fontMatchCacheMissCount = fontMatchCacheMissCount;
        this.derivedFontCacheHitCount = derivedFontCacheHitCount;
        this.derivedFontCacheMissCount = derivedFontCacheMissCount;
        this.widthCacheHitCount = widthCacheHitCount;
        this.widthCacheMissCount = widthCacheMissCount;
        this.activeDemandCount = activeDemandCount;
        this.maxDemandCount = maxDemandCount;
        this.demandHighWaterMark = demandHighWaterMark;
        this.rejectedDemandCount = rejectedDemandCount;
        this.promotedDemandCount = promotedDemandCount;
        this.pendingBitmapBytes = pendingBitmapBytes;
        this.maxPendingBitmapBytes = maxPendingBitmapBytes;
        this.pendingUploadHighWaterMark = pendingUploadHighWaterMark;
        this.pendingBitmapBytesHighWaterMark = pendingBitmapBytesHighWaterMark;
        this.blockedGlyphPublisherCount = blockedGlyphPublisherCount;
        this.mailboxBackpressureCount = mailboxBackpressureCount;
        this.mailboxRejectedCount = mailboxRejectedCount;
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

    public int getDirectTableCodepointCount() {
        return directTableCodepointCount;
    }

    public int getDirectTableSlotsPerPage() {
        return directTableSlotsPerPage;
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

    public int getLastFlushTextureBindCount() {
        return lastFlushTextureBindCount;
    }

    public long getFontMatchCacheHitCount() {
        return fontMatchCacheHitCount;
    }

    public long getFontMatchCacheMissCount() {
        return fontMatchCacheMissCount;
    }

    public long getDerivedFontCacheHitCount() {
        return derivedFontCacheHitCount;
    }

    public long getDerivedFontCacheMissCount() {
        return derivedFontCacheMissCount;
    }

    public long getWidthCacheHitCount() {
        return widthCacheHitCount;
    }

    public long getWidthCacheMissCount() {
        return widthCacheMissCount;
    }

    public int getActiveDemandCount() {
        return activeDemandCount;
    }

    public int getMaxDemandCount() {
        return maxDemandCount;
    }

    public int getDemandHighWaterMark() {
        return demandHighWaterMark;
    }

    public long getRejectedDemandCount() {
        return rejectedDemandCount;
    }

    public long getPromotedDemandCount() {
        return promotedDemandCount;
    }

    public long getPendingBitmapBytes() {
        return pendingBitmapBytes;
    }

    public long getMaxPendingBitmapBytes() {
        return maxPendingBitmapBytes;
    }

    public int getPendingUploadHighWaterMark() {
        return pendingUploadHighWaterMark;
    }

    public long getPendingBitmapBytesHighWaterMark() {
        return pendingBitmapBytesHighWaterMark;
    }

    public int getBlockedGlyphPublisherCount() {
        return blockedGlyphPublisherCount;
    }

    public long getMailboxBackpressureCount() {
        return mailboxBackpressureCount;
    }

    public long getMailboxRejectedCount() {
        return mailboxRejectedCount;
    }

    @Override
    public String toString() {
        return "pendingUploads=" + pendingUploadCount
                + ", readyGlyphs=" + readyGlyphCount
                + ", normalPages=" + normalPageCount
                + ", boldPages=" + boldPageCount
                + ", directTableCodepoints=" + directTableCodepointCount
                + ", directTableSlotsPerPage=" + directTableSlotsPerPage
                + ", drawStageUploadsLastSecond=" + queuedDrawStageUploadCount
                + ", frameQuads=" + frameQuadCount
                + ", lastFlushPageBatches=" + lastFlushPageSubmitCount
                + ", lastFlushDrawCalls=" + lastFlushDrawCallCount
                + ", lastFlushTextureBinds=" + lastFlushTextureBindCount
                + ", fontMatchCacheHits=" + fontMatchCacheHitCount
                + ", fontMatchCacheMisses=" + fontMatchCacheMissCount
                + ", derivedFontCacheHits=" + derivedFontCacheHitCount
                + ", derivedFontCacheMisses=" + derivedFontCacheMissCount
                + ", widthCacheHits=" + widthCacheHitCount
                + ", widthCacheMisses=" + widthCacheMissCount
                + ", activeDemands=" + activeDemandCount + '/' + maxDemandCount
                + ", demandHighWater=" + demandHighWaterMark
                + ", demandRejected=" + rejectedDemandCount
                + ", demandPromoted=" + promotedDemandCount
                + ", pendingBitmapBytes=" + pendingBitmapBytes + '/' + maxPendingBitmapBytes
                + ", uploadHighWater=" + pendingUploadHighWaterMark
                + ", bitmapBytesHighWater=" + pendingBitmapBytesHighWaterMark
                + ", blockedGlyphPublishers=" + blockedGlyphPublisherCount
                + ", mailboxBackpressure=" + mailboxBackpressureCount
                + ", mailboxRejected=" + mailboxRejectedCount;
    }
}
