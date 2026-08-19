package club.heiqi.uilib.font.page;

/**
 * {@link GlyphPageManager} 的纯统计指标（计数器与高水标），与 mailbox/atlas 状态解耦，
 * 全部无锁自增（单写者 + 诊断读容忍弱一致）。
 */
final class GlyphStats {

    private long pendingUploadHighWaterMark;
    private long pendingBitmapBytesHighWaterMark;
    private long mailboxBackpressureCount;
    private long mailboxRejectedCount;
    private long atlasPressureCount;
    private long uploadRollbackCount;
    private long uploadAttemptBudgetExhaustedCount;
    private long uploadByteBudgetExhaustedCount;
    private long uploadTimeBudgetExhaustedCount;

    void recordMailboxRejection() { mailboxRejectedCount++; }
    void recordMailboxBackpressure() { mailboxBackpressureCount++; }
    void recordAtlasPressure() { atlasPressureCount++; }
    void recordUploadRollback() { uploadRollbackCount++; }
    void recordUploadAttemptBudgetExhausted() { uploadAttemptBudgetExhaustedCount++; }
    void recordUploadByteBudgetExhausted() { uploadByteBudgetExhaustedCount++; }
    void recordUploadTimeBudgetExhausted() { uploadTimeBudgetExhaustedCount++; }

    void recordHighWaterMarks(int uploadCount, long bitmapBytes) {
        pendingUploadHighWaterMark = Math.max(pendingUploadHighWaterMark, uploadCount);
        pendingBitmapBytesHighWaterMark = Math.max(pendingBitmapBytesHighWaterMark, bitmapBytes);
    }

    long getPendingUploadHighWaterMark() { return pendingUploadHighWaterMark; }
    long getPendingBitmapBytesHighWaterMark() { return pendingBitmapBytesHighWaterMark; }
    long getMailboxBackpressureCount() { return mailboxBackpressureCount; }
    long getMailboxRejectedCount() { return mailboxRejectedCount; }
    long getAtlasPressureCount() { return atlasPressureCount; }
    long getUploadRollbackCount() { return uploadRollbackCount; }
    long getUploadAttemptBudgetExhaustedCount() { return uploadAttemptBudgetExhaustedCount; }
    long getUploadByteBudgetExhaustedCount() { return uploadByteBudgetExhaustedCount; }
    long getUploadTimeBudgetExhaustedCount() { return uploadTimeBudgetExhaustedCount; }
}
