package club.heiqi.uilib.net.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * C2S Fetch/RPC endpoint。
 *
 * @param <Req> 请求类型
 * @param <Resp> 响应类型
 */
public final class NetFetchEndpoint<Req, Resp> {

    private final NetService service;
    private final NetEndpointId id;
    private final Class<Req> requestType;
    private final Class<Resp> responseType;
    private final long timeoutMillis;
    private final NetFetchHandler<Req, Resp> handler;

    NetFetchEndpoint(NetService service, NetEndpointId id, Class<Req> requestType, Class<Resp> responseType,
            long timeoutMillis, NetFetchHandler<Req, Resp> handler) {
        this.service = service;
        this.id = id;
        this.requestType = requestType;
        this.responseType = responseType;
        this.timeoutMillis = timeoutMillis;
        this.handler = handler;
    }

    public NetEndpointId getId() {
        return id;
    }

    public Class<Req> getRequestType() {
        return requestType;
    }

    public Class<Resp> getResponseType() {
        return responseType;
    }

    /**
     * 客户端调用服务端 endpoint。
     *
     * @param request 请求
     * @return future
     */
    public CompletableFuture<Resp> call(Req request) {
        return service.callFetchEndpoint(this, Objects.requireNonNull(request, "request"));
    }

    long getTimeoutMillis() {
        return timeoutMillis;
    }

    void receiveRequest(Req request, NetFetchRequestContext<Resp> context) {
        if (handler == null) {
            context.fail(new NetRemoteException("Fetch endpoint 未注册处理器: " + id));
            return;
        }
        handler.onRequest(request, context);
    }

    /**
     * Fetch 注册构造器。
     *
     * @param <Req> 请求类型
     * @param <Resp> 响应类型
     */
    public static final class Builder<Req, Resp> {

        private final NetService service;
        private final NetEndpointId id;
        private final Class<Req> requestType;
        private final Class<Resp> responseType;
        private long timeoutMillis = 5_000L;
        private NetFetchHandler<Req, Resp> handler;

        Builder(NetService service, NetEndpointId id, Class<Req> requestType, Class<Resp> responseType) {
            this.service = service;
            this.id = id;
            this.requestType = requestType;
            this.responseType = responseType;
        }

        /**
         * 设置请求超时。
         *
         * @param timeout 超时时长
         * @return 构造器
         */
        public Builder<Req, Resp> timeout(Duration timeout) {
            this.timeoutMillis = Objects.requireNonNull(timeout, "timeout").toMillis();
            return this;
        }

        /**
         * 设置服务端请求处理器。
         *
         * @param handler 处理器
         * @return 构造器
         */
        public Builder<Req, Resp> onRequest(NetFetchHandler<Req, Resp> handler) {
            this.handler = handler;
            return this;
        }

        /**
         * 注册 endpoint。
         *
         * @return endpoint
         */
        public NetFetchEndpoint<Req, Resp> register() {
            return service.registerFetchEndpoint(new NetFetchEndpoint<Req, Resp>(service, id, requestType,
                    responseType, timeoutMillis, handler));
        }
    }

    /**
     * Fetch 处理器。
     *
     * @param <Req> 请求类型
     * @param <Resp> 响应类型
     */
    public interface NetFetchHandler<Req, Resp> {

        /**
         * 处理请求。
         *
         * @param request 请求
         * @param context 请求上下文
         */
        void onRequest(Req request, NetFetchRequestContext<Resp> context);
    }

    /**
     * Fetch 请求上下文。
     *
     * @param <Resp> 响应类型
     */
    public static final class NetFetchRequestContext<Resp> {

        private final NetService service;
        private final String endpointKey;
        private final long requestId;
        private final Class<Resp> responseType;
        private final NetReceiveContext receiveContext;
        private final Object replyTarget;

        NetFetchRequestContext(NetService service, String endpointKey, long requestId, Class<Resp> responseType,
                NetReceiveContext receiveContext, Object replyTarget) {
            this.service = service;
            this.endpointKey = endpointKey;
            this.requestId = requestId;
            this.responseType = responseType;
            this.receiveContext = receiveContext;
            this.replyTarget = replyTarget;
        }

        /**
         * 回复成功结果。
         *
         * @param response 响应
         */
        public void reply(Resp response) {
            service.replyFetch(replyTarget, endpointKey, requestId, responseType, response);
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
