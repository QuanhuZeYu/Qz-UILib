package club.heiqi.uilib.net.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Fetch endpoint 的滑动窗口限流配置。
 */
public final class NetFetchRateLimit {

    private final int maxRequests;
    private final long windowMillis;

    private NetFetchRateLimit(int maxRequests, long windowMillis) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        if (windowMillis <= 0L) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    /**
     * 创建滑动窗口限流配置。
     *
     * @param maxRequests 窗口内允许的请求数
     * @param window 窗口时长
     * @return 限流配置
     */
    public static NetFetchRateLimit of(int maxRequests, Duration window) {
        return new NetFetchRateLimit(maxRequests, Objects.requireNonNull(window, "window").toMillis());
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public long getWindowMillis() {
        return windowMillis;
    }
}
