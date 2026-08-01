package club.heiqi.uilib.font;

import java.util.function.LongSupplier;

import club.heiqi.uilib.font.event.FontReloadRequest;

/**
 * 字体重载的 durable desired-state signal。
 *
 * <p>发布方只推进 desired sequence；唯一 reconcile owner 领取 ticket，成功后才推进 applied sequence。
 * 持续抖动没有强制 max delay，旧字体代际会继续服务，直到 signal 稳定后再收敛最新状态。</p>
 */
final class FontReloadSignal {

    private static final String UNSPECIFIED_REASON = "unspecified";

    private final long quietPeriodNanos;
    private final long retryBaseNanos;
    private final long retryMaxNanos;
    private final LongSupplier nanoClock;

    private long lifecycle = 1L;
    private long nextSequence;
    private long desiredSequence;
    private long appliedSequence;
    private long lastSignalAtNanos = Long.MIN_VALUE;
    private long lastFailureAtNanos = Long.MIN_VALUE;
    private int consecutiveFailures;
    private String latestReason = UNSPECIFIED_REASON;
    private boolean accepting = true;
    private Ticket inFlight;

    /** 创建具有稳定窗口和失败退避的 signal。 */
    FontReloadSignal(long quietPeriodNanos, long retryBaseNanos, long retryMaxNanos, LongSupplier nanoClock) {
        if (nanoClock == null) {
            throw new IllegalArgumentException("nanoClock 不得为 null");
        }
        this.quietPeriodNanos = Math.max(0L, quietPeriodNanos);
        this.retryBaseNanos = Math.max(0L, retryBaseNanos);
        this.retryMaxNanos = Math.max(this.retryBaseNanos, retryMaxNanos);
        this.nanoClock = nanoClock;
    }

    /**
     * 发布最新 desired state。
     *
     * @return 本次发布后的 desired sequence
     */
    synchronized long signal(FontReloadRequest request) {
        if (!accepting) {
            return -1L;
        }
        long nowNanos = nanoClock.getAsLong();
        nextSequence++;
        desiredSequence = nextSequence;
        latestReason = normalizeReason(request);
        lastSignalAtNanos = nowNanos;
        lastFailureAtNanos = Long.MIN_VALUE;
        consecutiveFailures = 0;
        return desiredSequence;
    }

    /**
     * 在 signal 稳定且不处于失败退避时领取唯一 reconcile ticket。
     *
     * @return 可执行 ticket；尚不可执行或已有 owner 时返回 null
     */
    synchronized Ticket pollReady() {
        if (inFlight != null || desiredSequence == appliedSequence) {
            return null;
        }
        long nowNanos = nanoClock.getAsLong();
        if (!elapsed(nowNanos, lastSignalAtNanos, quietPeriodNanos)
                || !elapsed(nowNanos, lastFailureAtNanos, retryDelayNanos())) {
            return null;
        }

        long signalCount = desiredSequence - appliedSequence;
        FontReloadRequest request = new FontReloadRequest(coalescedReason(latestReason, signalCount));
        inFlight = new Ticket(lifecycle, desiredSequence, signalCount, request);
        return inFlight;
    }

    /** 成功确认 ticket 捕获的 desired sequence。 */
    synchronized boolean completeSuccess(Ticket ticket) {
        if (!owns(ticket)) {
            return false;
        }
        appliedSequence = ticket.sequence;
        inFlight = null;
        lastFailureAtNanos = Long.MIN_VALUE;
        consecutiveFailures = 0;
        return true;
    }

    /**
     * 失败释放 owner，但不确认 desired state。
     *
     * <p>若 flight 中已有更新 signal，更新 signal 自带的新稳定窗口优先，不继承旧 ticket 的失败退避。</p>
     */
    synchronized boolean completeFailure(Ticket ticket) {
        if (!owns(ticket)) {
            return false;
        }
        long nowNanos = nanoClock.getAsLong();
        inFlight = null;
        if (desiredSequence == ticket.sequence) {
            consecutiveFailures++;
            lastFailureAtNanos = nowNanos;
        }
        return true;
    }

    /** 开启新 lifecycle，并让旧 ticket 和旧 pending signal 全部失效。 */
    synchronized void reset() {
        resetState();
    }

    /** 关闭当前 lifecycle，并与并发 signal 线性化。 */
    synchronized void closeLifecycle() {
        accepting = false;
        resetState();
    }

    /** 在 shutdown 后开启干净的新 lifecycle；首次初始化不会清除 pre-init signal。 */
    synchronized void openLifecycle() {
        if (accepting) {
            return;
        }
        accepting = true;
        resetState();
    }

    private void resetState() {
        lifecycle++;
        desiredSequence = nextSequence;
        appliedSequence = nextSequence;
        lastSignalAtNanos = Long.MIN_VALUE;
        lastFailureAtNanos = Long.MIN_VALUE;
        consecutiveFailures = 0;
        latestReason = UNSPECIFIED_REASON;
        inFlight = null;
    }

    synchronized int getPendingCount() {
        long pending = desiredSequence - appliedSequence;
        return pending > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pending;
    }

    synchronized boolean hasPending() {
        return desiredSequence != appliedSequence;
    }

    synchronized boolean isInFlight() {
        return inFlight != null;
    }

    synchronized long getDesiredSequence() {
        return desiredSequence;
    }

    synchronized long getAppliedSequence() {
        return appliedSequence;
    }

    synchronized int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    private boolean owns(Ticket ticket) {
        return ticket != null && ticket == inFlight && ticket.lifecycle == lifecycle;
    }

    private long retryDelayNanos() {
        if (lastFailureAtNanos == Long.MIN_VALUE || consecutiveFailures <= 0 || retryBaseNanos == 0L) {
            return 0L;
        }
        long delay = retryBaseNanos;
        for (int index = 1; index < consecutiveFailures && delay < retryMaxNanos; index++) {
            if (delay > retryMaxNanos / 2L) {
                return retryMaxNanos;
            }
            delay *= 2L;
        }
        return Math.min(delay, retryMaxNanos);
    }

    private boolean elapsed(long nowNanos, long sinceNanos, long durationNanos) {
        return sinceNanos == Long.MIN_VALUE || durationNanos == 0L || nowNanos - sinceNanos >= durationNanos;
    }

    private String normalizeReason(FontReloadRequest request) {
        if (request == null || request.getReason() == null || request.getReason().trim().isEmpty()) {
            return UNSPECIFIED_REASON;
        }
        return request.getReason();
    }

    private String coalescedReason(String reason, long signalCount) {
        return signalCount <= 1L ? reason : reason + ", coalesced=" + signalCount;
    }

    /** 单次 reconcile 对 desired state 的不可变捕获。 */
    static final class Ticket {

        private final long lifecycle;
        private final long sequence;
        private final long signalCount;
        private final FontReloadRequest request;

        private Ticket(long lifecycle, long sequence, long signalCount, FontReloadRequest request) {
            this.lifecycle = lifecycle;
            this.sequence = sequence;
            this.signalCount = signalCount;
            this.request = request;
        }

        long getSequence() {
            return sequence;
        }

        long getSignalCount() {
            return signalCount;
        }

        FontReloadRequest getRequest() {
            return request;
        }
    }
}
