package club.heiqi.uilib.font.page;

import java.util.ArrayList;
import java.util.List;

/**
 * 上传 mailbox 数据结构与配额：pending/inFlight 队列、epoch、预留计数、背压配额判定与
 * 优先级轮询原语。全部方法由调用方持 mailbox 锁访问（锁对象保留在 {@link GlyphPageManager}，
 * 锁顺序语义逐位不变）。
 */
final class GlyphMailbox {

    private final List<PendingGlyphUpload> pendingUploads = new ArrayList<PendingGlyphUpload>();
    private final List<PendingGlyphUpload> inFlightUploads = new ArrayList<PendingGlyphUpload>();
    private final GlyphStats stats;
    private final int maxPendingUploads;
    private final int visibleRecordReserve;
    private final long maxPendingBitmapBytes;
    private final long visibleBitmapReserve;
    private final long agingStepNanos;
    private volatile long epoch;
    private int reservedUploadCount;
    private long reservedBitmapBytes;
    private long sequence;

    GlyphMailbox(GlyphStats stats, int maxPendingUploads, int visibleRecordReserve, long maxPendingBitmapBytes,
            long visibleBitmapReserve, long agingStepNanos) {
        this.stats = stats;
        this.maxPendingUploads = maxPendingUploads;
        this.visibleRecordReserve = visibleRecordReserve;
        this.maxPendingBitmapBytes = maxPendingBitmapBytes;
        this.visibleBitmapReserve = visibleBitmapReserve;
        this.agingStepNanos = agingStepNanos;
    }

    long getEpoch() { return epoch; }
    void advanceEpoch() { epoch++; }
    int getMaxPendingUploads() { return maxPendingUploads; }
    long getMaxPendingBitmapBytes() { return maxPendingBitmapBytes; }
    long getVisibleBitmapReserve() { return visibleBitmapReserve; }
    long getAgingStepNanos() { return agingStepNanos; }
    int getReservedUploadCount() { return reservedUploadCount; }
    long getReservedBytes() { return reservedBitmapBytes; }

    List<PendingGlyphUpload> snapshotPending() { return new ArrayList<PendingGlyphUpload>(pendingUploads); }
    List<PendingGlyphUpload> snapshotInFlight() { return new ArrayList<PendingGlyphUpload>(inFlightUploads); }

    /** 清空队列并清零预留（discard 语义）。 */
    void clearAll() {
        pendingUploads.clear();
        inFlightUploads.clear();
        reservedUploadCount = 0;
        reservedBitmapBytes = 0L;
    }

    int pendingCount() { return pendingUploads.size(); }
    int inFlightCount() { return inFlightUploads.size(); }

    boolean hasCapacity(int demandPriority, long bitmapBytes) {
        int recordLimit = demandPriority == GlyphPageManager.PRIORITY_VISIBLE
                ? maxPendingUploads : maxPendingUploads - visibleRecordReserve;
        long byteLimit = demandPriority == GlyphPageManager.PRIORITY_VISIBLE
                ? maxPendingBitmapBytes : maxPendingBitmapBytes - visibleBitmapReserve;
        return reservedUploadCount < recordLimit && reservedBitmapBytes <= byteLimit - bitmapBytes;
    }

    /** 登记一次预留并刷新高水标。 */
    void reserve(long bitmapBytes) {
        reservedUploadCount++;
        reservedBitmapBytes += bitmapBytes;
        stats.recordHighWaterMarks(reservedUploadCount, reservedBitmapBytes);
    }

    /** 释放预留；epoch 不匹配时拒绝（返回 false），调用方不 notify。 */
    boolean releaseReservation(long reservedEpoch, long bitmapBytes) {
        if (reservedEpoch != epoch) {
            return false;
        }
        reservedUploadCount--;
        reservedBitmapBytes -= bitmapBytes;
        return true;
    }

    void enqueue(PendingGlyphUpload upload) { pendingUploads.add(upload); }

    /** 结算 in-flight 租约；epoch 不匹配或不在队列时拒绝（返回 false）。 */
    boolean completeLease(PendingGlyphUpload upload) {
        if (upload == null || upload.getMailboxEpoch() != epoch || !inFlightUploads.remove(upload)) {
            return false;
        }
        reservedUploadCount--;
        reservedBitmapBytes -= upload.getBitmapBytes();
        return true;
    }

    /** 调用方必须持有 mailbox 锁。 */
    boolean isLeaseCurrentLocked(PendingGlyphUpload upload) {
        return upload != null && upload.getMailboxEpoch() == epoch && inFlightUploads.contains(upload);
    }

    long nextSequence() { return ++sequence; }

    /** 按有效优先级挑选下一条上传（aging 提升 + 同优先级按入队序），可选允许首条超字节预算。 */
    Poll pollBest(long nowNanos, long attemptedBitmapBytes, long byteBudget, boolean allowOversizedFirst) {
        if (pendingUploads.isEmpty()) {
            return Poll.stop("EMPTY");
        }
        int bestIndex = 0;
        PendingGlyphUpload best = pendingUploads.get(0);
        int bestPriority = best.getEffectivePriority(nowNanos, agingStepNanos);
        for (int index = 1; index < pendingUploads.size(); index++) {
            PendingGlyphUpload candidate = pendingUploads.get(index);
            int candidatePriority = candidate.getEffectivePriority(nowNanos, agingStepNanos);
            if (candidatePriority > bestPriority
                    || candidatePriority == bestPriority
                            && candidate.getEnqueueSequence() < best.getEnqueueSequence()) {
                bestIndex = index;
                best = candidate;
                bestPriority = candidatePriority;
            }
        }
        if (!allowOversizedFirst && best.getBitmapBytes() > byteBudget - attemptedBitmapBytes) {
            return Poll.stop("BYTE_BUDGET");
        }
        pendingUploads.remove(bestIndex);
        inFlightUploads.add(best);
        return Poll.upload(best);
    }

    /** 单次轮询结果：要么一条上传，要么一个停止原因。 */
    static final class Poll {

        final PendingGlyphUpload upload;
        final String stopReason;

        private Poll(PendingGlyphUpload upload, String stopReason) {
            this.upload = upload;
            this.stopReason = stopReason;
        }

        static Poll upload(PendingGlyphUpload upload) { return new Poll(upload, null); }
        static Poll stop(String stopReason) { return new Poll(null, stopReason); }
    }
}
