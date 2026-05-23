package club.heiqi.uilib.net.api;

/**
 * Stream 下载进度快照。
 */
public final class NetStreamProgress {

    private final long requestId;
    private final long receivedBytes;
    private final long totalBytes;

    NetStreamProgress(long requestId, long receivedBytes, long totalBytes) {
        this.requestId = requestId;
        this.receivedBytes = receivedBytes;
        this.totalBytes = totalBytes;
    }

    public long getRequestId() {
        return requestId;
    }

    public long getReceivedBytes() {
        return receivedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public boolean isComplete() {
        return totalBytes >= 0L && receivedBytes >= totalBytes;
    }

    /**
     * 返回 0..1 的进度比例；未知总长度时返回 0。
     *
     * @return 进度比例
     */
    public double getRatio() {
        if (totalBytes <= 0L) {
            return isComplete() ? 1.0D : 0.0D;
        }
        return Math.min(1.0D, (double) receivedBytes / (double) totalBytes);
    }
}
