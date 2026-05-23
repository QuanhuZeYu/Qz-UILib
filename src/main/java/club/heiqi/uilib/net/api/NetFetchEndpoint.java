package club.heiqi.uilib.net.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * C2S Fetch endpoint。
 */
public final class NetFetchEndpoint {

    private final NetService service;
    private final NetEndpointId id;
    private final long timeoutMillis;
    private final NetFetchHandler handler;

    NetFetchEndpoint(NetService service, NetEndpointId id, long timeoutMillis, NetFetchHandler handler) {
        this.service = service;
        this.id = id;
        this.timeoutMillis = timeoutMillis;
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
            return service.registerFetchEndpoint(new NetFetchEndpoint(service, id, timeoutMillis, handler));
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
}
