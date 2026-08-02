package club.heiqi.uilib.font.page;

import java.util.concurrent.atomic.AtomicInteger;

import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/**
 * 待上传到主线程的字符结果。
 */
public final class PendingGlyphUpload {

    private final GlyphGenerationResult generationResult;
    private final AtomicInteger demandPriority;
    private final long bitmapBytes;
    private final long enqueueSequence;
    private final long enqueuedNanos;

    /**
     * 创建待上传记录。
     *
     * @param generationResult 字符生成结果
     */
    public PendingGlyphUpload(GlyphGenerationResult generationResult) {
        this(generationResult, new AtomicInteger(2), 0L, 0L, 0L);
    }

    PendingGlyphUpload(GlyphGenerationResult generationResult, AtomicInteger demandPriority, long bitmapBytes,
            long enqueueSequence, long enqueuedNanos) {
        if (generationResult == null) {
            throw new IllegalArgumentException("generationResult 不得为 null");
        }
        if (demandPriority == null || bitmapBytes < 0L) {
            throw new IllegalArgumentException("demandPriority 和 bitmapBytes 必须有效");
        }
        this.generationResult = generationResult;
        this.demandPriority = demandPriority;
        this.bitmapBytes = bitmapBytes;
        this.enqueueSequence = enqueueSequence;
        this.enqueuedNanos = enqueuedNanos;
    }

    public GlyphRequestToken getToken() {
        return generationResult.getToken();
    }

    public GlyphGenerationResult getGenerationResult() {
        return generationResult;
    }

    int getDemandPriority() {
        return demandPriority.get();
    }

    long getBitmapBytes() {
        return bitmapBytes;
    }

    long getEnqueueSequence() {
        return enqueueSequence;
    }

    int getEffectivePriority(long nowNanos, long agingStepNanos) {
        long elapsed = Math.max(0L, nowNanos - enqueuedNanos);
        long agingSteps = elapsed / agingStepNanos;
        return (int) Math.min(3L, (long) demandPriority.get() + agingSteps);
    }
}
