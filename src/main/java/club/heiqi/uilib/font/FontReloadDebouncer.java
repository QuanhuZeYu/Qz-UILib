package club.heiqi.uilib.font;

import club.heiqi.uilib.font.event.FontReloadRequest;

/**
 * 字体重载请求合并器。
 */
class FontReloadDebouncer {

    private final long quietPeriodMs;
    private final long maxDelayMs;
    private long lastExecutedAt = Long.MIN_VALUE;
    private long firstPendingAt = 0L;
    private long lastPendingAt = 0L;
    private int pendingCount = 0;
    private FontReloadRequest pendingRequest;

    /**
     * 创建重载请求合并器。
     *
     * @param quietPeriodMs 安静窗口毫秒数
     * @param maxDelayMs 最大延迟毫秒数
     */
    FontReloadDebouncer(long quietPeriodMs, long maxDelayMs) {
        this.quietPeriodMs = Math.max(0L, quietPeriodMs);
        this.maxDelayMs = Math.max(this.quietPeriodMs, maxDelayMs);
    }

    /**
     * 接收一条重载请求。
     *
     * @param request 重载请求
     * @param now 当前时间戳
     * @return 需要立即执行的请求；若返回 null 表示已合并为 pending
     */
    FontReloadRequest request(FontReloadRequest request, long now) {
        if (pendingRequest == null && canExecuteImmediately(now)) {
            lastExecutedAt = now;
            return request;
        }
        queue(request, now);
        return null;
    }

    /**
     * 获取已到期的 pending 重载请求。
     *
     * @param now 当前时间戳
     * @return 到期请求；未到期返回 null
     */
    FontReloadRequest pollReady(long now) {
        if (pendingRequest == null) {
            return null;
        }
        boolean quietElapsed = now - lastPendingAt >= quietPeriodMs;
        boolean maxDelayElapsed = now - firstPendingAt >= maxDelayMs;
        if (!quietElapsed && !maxDelayElapsed) {
            return null;
        }
        FontReloadRequest readyRequest = buildPendingRequest();
        pendingRequest = null;
        pendingCount = 0;
        firstPendingAt = 0L;
        lastPendingAt = 0L;
        lastExecutedAt = now;
        return readyRequest;
    }

    /**
     * 获取待合并请求数量。
     *
     * @return 待合并请求数量
     */
    int getPendingCount() {
        return pendingCount;
    }

    private boolean canExecuteImmediately(long now) {
        return lastExecutedAt == Long.MIN_VALUE || now - lastExecutedAt >= quietPeriodMs;
    }

    private void queue(FontReloadRequest request, long now) {
        if (pendingRequest == null) {
            firstPendingAt = now;
            pendingCount = 0;
        }
        pendingRequest = request;
        pendingCount++;
        lastPendingAt = now;
    }

    private FontReloadRequest buildPendingRequest() {
        if (pendingCount <= 1) {
            return pendingRequest;
        }
        return new FontReloadRequest(pendingRequest.getReason() + ", coalesced=" + pendingCount);
    }
}
