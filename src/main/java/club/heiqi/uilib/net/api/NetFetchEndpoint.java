package club.heiqi.uilib.net.api;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C2S Fetch endpoint。
 */
public final class NetFetchEndpoint {

    private final NetService service;
    private final NetEndpointId id;
    private final long timeoutMillis;
    private final SlidingWindowRateLimiter rateLimiter;
    private final NetFetchHandler handler;

    NetFetchEndpoint(NetService service, NetEndpointId id, long timeoutMillis,
            NetFetchRateLimit rateLimit, NetFetchHandler handler) {
        this.service = service;
        this.id = id;
        this.timeoutMillis = timeoutMillis;
        this.rateLimiter = rateLimit == null ? null : new SlidingWindowRateLimiter(rateLimit);
        this.handler = handler;
    }

    public NetEndpointId getId() {
        return id;
    }

    /**
     * 客户端调用服务端 endpoint。
     *
     * @param request 请求
     * @return response future
     */
    public CompletableFuture<NetResponse> call(NetRequest request) {
        return service.callFetchEndpoint(this, Objects.requireNonNull(request, "request"));
    }

    /**
     * 客户端以 JSON body 调用。
     *
     * @param json JSON 文本
     * @return response future
     */
    public CompletableFuture<NetResponse> callJson(String json) {
        return call(NetRequest.json(json));
    }

    long getTimeoutMillis() {
        return timeoutMillis;
    }

    RateLimitDecision checkRateLimit(Object sender) {
        if (rateLimiter == null) {
            return RateLimitDecision.allowed();
        }
        return rateLimiter.check(sender);
    }

    void receiveRequest(NetRequest request, NetFetchRequestContext context) {
        if (handler == null) {
            context.reply(NetResponse.error(404, "Fetch endpoint 未注册处理器: " + id));
            return;
        }
        handler.onRequest(request, context);
    }

    /**
     * Fetch 注册构造器。
     */
    public static final class Builder {

        private final NetService service;
        private final NetEndpointId id;
        private long timeoutMillis = 5_000L;
        private NetFetchRateLimit rateLimit;
        private NetFetchHandler handler;

        Builder(NetService service, NetEndpointId id) {
            this.service = service;
            this.id = id;
        }

        /**
         * 设置请求超时。
         *
         * @param timeout 超时时长
         * @return 构造器
         */
        public Builder timeout(Duration timeout) {
            this.timeoutMillis = Objects.requireNonNull(timeout, "timeout").toMillis();
            return this;
        }

        /**
         * 设置每个发送者的滑动窗口限流。
         *
         * @param maxRequests 窗口内允许的请求数
         * @param window 窗口时长
         * @return 构造器
         */
        public Builder rateLimit(int maxRequests, Duration window) {
            return rateLimit(NetFetchRateLimit.of(maxRequests, window));
        }

        /**
         * 设置滑动窗口限流配置。
         *
         * @param rateLimit 限流配置
         * @return 构造器
         */
        public Builder rateLimit(NetFetchRateLimit rateLimit) {
            this.rateLimit = Objects.requireNonNull(rateLimit, "rateLimit");
            return this;
        }

        /**
         * 设置服务端请求处理器。
         *
         * @param handler 处理器
         * @return 构造器
         */
        public Builder onRequest(NetFetchHandler handler) {
            this.handler = handler;
            return this;
        }

        /**
         * 注册 endpoint。
         *
         * @return endpoint
         */
        public NetFetchEndpoint register() {
            return service.registerFetchEndpoint(new NetFetchEndpoint(service, id, timeoutMillis, rateLimit,
                    handler));
        }
    }

    /**
     * Fetch 处理器。
     */
    public interface NetFetchHandler {

        /**
         * 处理请求。
         *
         * @param request 请求
         * @param context 请求上下文
         */
        void onRequest(NetRequest request, NetFetchRequestContext context);
    }

    /**
     * Fetch 请求上下文。
     */
    public static final class NetFetchRequestContext {

        private final NetService service;
        private final String endpointKey;
        private final long requestId;
        private final NetReceiveContext receiveContext;
        private final Object replyTarget;

        NetFetchRequestContext(NetService service, String endpointKey, long requestId,
                NetReceiveContext receiveContext, Object replyTarget) {
            this.service = service;
            this.endpointKey = endpointKey;
            this.requestId = requestId;
            this.receiveContext = receiveContext;
            this.replyTarget = replyTarget;
        }

        /**
         * 回复成功结果。
         *
         * @param response 响应
         */
        public void reply(NetResponse response) {
            service.replyFetch(replyTarget, endpointKey, requestId, Objects.requireNonNull(response, "response"));
        }

        /**
         * 回复 JSON 成功结果。
         *
         * @param json JSON 文本
         */
        public void replyJson(String json) {
            reply(NetResponse.json(json));
        }

        /**
         * 回复失败。
         *
         * @param throwable 错误
         */
        public void fail(Throwable throwable) {
            service.failFetch(replyTarget, endpointKey, requestId, throwable);
        }

        public NetReceiveContext getReceiveContext() {
            return receiveContext;
        }
    }

    /**
     * Fetch 限流决策。
     */
    static final class RateLimitDecision {

        private static final RateLimitDecision ALLOWED = new RateLimitDecision(true, 0L);

        private final boolean allowed;
        private final long retryAfterMillis;

        private RateLimitDecision(boolean allowed, long retryAfterMillis) {
            this.allowed = allowed;
            this.retryAfterMillis = retryAfterMillis;
        }

        static RateLimitDecision allowed() {
            return ALLOWED;
        }

        static RateLimitDecision rejected(long retryAfterMillis) {
            return new RateLimitDecision(false, retryAfterMillis);
        }

        boolean isAllowed() {
            return allowed;
        }

        long getRetryAfterMillis() {
            return retryAfterMillis;
        }
    }

    private static final class SlidingWindowRateLimiter {

        private static final Object ANONYMOUS_SENDER = new Object();

        private final NetFetchRateLimit rateLimit;
        private final ConcurrentHashMap<Object, Window> windows = new ConcurrentHashMap<Object, Window>();

        SlidingWindowRateLimiter(NetFetchRateLimit rateLimit) {
            this.rateLimit = rateLimit;
        }

        RateLimitDecision check(Object sender) {
            Object key = sender == null ? ANONYMOUS_SENDER : sender;
            Window window = windows.get(key);
            if (window == null) {
                Window created = new Window();
                Window existing = windows.putIfAbsent(key, created);
                window = existing == null ? created : existing;
            }
            return window.check(rateLimit, System.currentTimeMillis());
        }
    }

    private static final class Window {

        private final ArrayDeque<Long> timestamps = new ArrayDeque<Long>();

        synchronized RateLimitDecision check(NetFetchRateLimit rateLimit, long nowMillis) {
            long cutoff = nowMillis - rateLimit.getWindowMillis();
            while (!timestamps.isEmpty() && timestamps.peekFirst().longValue() <= cutoff) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= rateLimit.getMaxRequests()) {
                long first = timestamps.peekFirst().longValue();
                long retryAfter = Math.max(1L, rateLimit.getWindowMillis() - (nowMillis - first));
                return RateLimitDecision.rejected(retryAfter);
            }
            timestamps.addLast(Long.valueOf(nowMillis));
            return RateLimitDecision.allowed();
        }
    }
}
