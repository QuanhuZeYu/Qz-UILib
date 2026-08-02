package club.heiqi.uilib.font.page;

import java.util.concurrent.atomic.AtomicInteger;

import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/**
 * 待上传到主线程的字符结果。
 */
public final class PendingGlyphUpload {

    private final GlyphUploadPlan uploadPlan;
    private final AtomicInteger demandPriority;
    private final long bitmapBytes;
    private final long enqueueSequence;
    private final long enqueuedNanos;
    private final long mailboxEpoch;

    /**
     * 创建待上传记录。
     *
     * @param generationResult 字符生成结果
     */
    public PendingGlyphUpload(GlyphGenerationResult generationResult) {
        this(GlyphUploadPlan.from(generationResult), new AtomicInteger(2), 0L, 0L, 0L);
    }

    PendingGlyphUpload(GlyphUploadPlan uploadPlan, AtomicInteger demandPriority, long enqueueSequence,
            long enqueuedNanos, long mailboxEpoch) {
        if (uploadPlan == null) {
            throw new IllegalArgumentException("uploadPlan 不得为 null");
        }
        if (demandPriority == null) {
            throw new IllegalArgumentException("demandPriority 不得为 null");
        }
        this.uploadPlan = uploadPlan;
        this.demandPriority = demandPriority;
        this.bitmapBytes = uploadPlan.getBitmapBytes();
        this.enqueueSequence = enqueueSequence;
        this.enqueuedNanos = enqueuedNanos;
        this.mailboxEpoch = mailboxEpoch;
    }

    public GlyphRequestToken getToken() {
        return uploadPlan.getToken();
    }

    public GlyphGenerationResult getGenerationResult() {
        return uploadPlan.toGenerationResult();
    }

    GlyphUploadPlan getUploadPlan() {
        return uploadPlan;
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

    long getMailboxEpoch() {
        return mailboxEpoch;
    }

    int getEffectivePriority(long nowNanos, long agingStepNanos) {
        long elapsed = Math.max(0L, nowNanos - enqueuedNanos);
        long agingSteps = elapsed / agingStepNanos;
        return (int) Math.min(3L, (long) demandPriority.get() + agingSteps);
    }
}
