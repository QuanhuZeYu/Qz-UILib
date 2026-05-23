package club.heiqi.uilib.net.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.net.api.NetDisconnectedException;
import club.heiqi.uilib.net.api.NetTimeoutException;

/**
 * Fetch 请求生命周期表。
 */
public final class NetRequestRegistry {

    private final AtomicLong nextRequestId = new AtomicLong(1L);
    private final Map<Long, PendingRequest<?>> pendingRequests = new ConcurrentHashMap<Long, PendingRequest<?>>();

    /**
     * 注册新的 pending 请求。
     *
     * @param timeoutMillis 超时毫秒
     * @param <T> 响应类型
     * @return pending 请求
     */
    public <T> PendingRequest<T> register(long timeoutMillis) {
        long requestId = nextRequestId.getAndIncrement();
        PendingRequest<T> pending = new PendingRequest<T>(requestId, System.currentTimeMillis() + timeoutMillis);
        pendingRequests.put(Long.valueOf(requestId), pending);
        return pending;
    }

    /**
     * 完成请求。
     *
     * @param requestId 请求 id
     * @param value 响应
     * @param <T> 响应类型
     */
    @SuppressWarnings("unchecked")
    public <T> void complete(long requestId, T value) {
        PendingRequest<T> pending = (PendingRequest<T>) pendingRequests.remove(Long.valueOf(requestId));
        if (pending != null) {
            pending.future.complete(value);
        }
    }

    /**
     * 请求失败。
     *
     * @param requestId 请求 id
     * @param throwable 错误
     */
    public void fail(long requestId, Throwable throwable) {
        PendingRequest<?> pending = pendingRequests.remove(Long.valueOf(requestId));
        if (pending != null) {
            pending.future.completeExceptionally(throwable);
        }
    }

    /**
     * 处理超时。
     */
    public void expireTimedOut() {
        long now = System.currentTimeMillis();
        for (PendingRequest<?> pending : pendingRequests.values()) {
            if (pending.deadlineMillis <= now && pendingRequests.remove(Long.valueOf(pending.requestId), pending)) {
                pending.future.completeExceptionally(new NetTimeoutException("Fetch 请求超时: " + pending.requestId));
            }
        }
    }

    /**
     * 断连时失败所有 pending 请求。
     */
    public void failAllDisconnected() {
        for (PendingRequest<?> pending : pendingRequests.values()) {
            if (pendingRequests.remove(Long.valueOf(pending.requestId), pending)) {
                pending.future.completeExceptionally(new NetDisconnectedException("网络连接已断开"));
            }
        }
    }

    /**
     * pending 请求。
     *
     * @param <T> 响应类型
     */
    public static final class PendingRequest<T> {

        private final long requestId;
        private final long deadlineMillis;
        private final CompletableFuture<T> future = new CompletableFuture<T>();

        private PendingRequest(long requestId, long deadlineMillis) {
            this.requestId = requestId;
            this.deadlineMillis = deadlineMillis;
        }

        public long getRequestId() {
            return requestId;
        }

        public CompletableFuture<T> getFuture() {
            return future;
        }
    }
}
