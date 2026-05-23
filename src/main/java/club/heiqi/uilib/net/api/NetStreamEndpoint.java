package club.heiqi.uilib.net.api;

import java.time.Duration;
import java.util.Objects;

import club.heiqi.uilib.net.core.NetPayloadLimits;

/**
 * C2S 请求、S2C 大内容响应的 Stream endpoint。
 *
 * <p>Stream 面向超过普通 16 MiB 逻辑消息上限的资源、文件或大快照。
 * 请求本身仍是轻量 `NetRequest`，响应 body 走独立 chunk 生命周期。</p>
 */
public final class NetStreamEndpoint {

    private final NetService service;
    private final NetEndpointId id;
    private final long timeoutMillis;
    private final long maxBytes;
    private final NetStreamHandler handler;

    NetStreamEndpoint(NetService service, NetEndpointId id, long timeoutMillis, long maxBytes,
            NetStreamHandler handler) {
        this.service = service;
        this.id = id;
        this.timeoutMillis = timeoutMillis;
        this.maxBytes = NetPayloadLimits.clampStreamContentLimit(maxBytes);
        this.handler = handler;
    }

    public NetEndpointId getId() {
        return id;
    }

    long getTimeoutMillis() {
        return timeoutMillis;
    }

    long getMaxBytes() {
        return maxBytes;
    }

    /**
     * 客户端发起 Stream 请求。
     *
     * @param request 请求
     * @return Stream 调用句柄
     */
    public NetStreamCall call(NetRequest request) {
        return service.callStreamEndpoint(this, Objects.requireNonNull(request, "request"));
    }

    /**
     * 客户端以 JSON body 发起 Stream 请求。
     *
     * @param json JSON 文本
     * @return Stream 调用句柄
     */
    public NetStreamCall callJson(String json) {
        return call(NetRequest.json(json));
    }

    void receiveRequest(NetRequest request, NetStreamRequestContext context) {
        if (handler == null) {
            context.reply(NetResponse.error(404, "Stream endpoint 未注册处理器: " + id));
            return;
        }
        handler.onRequest(request, context);
    }

    /**
     * Stream 注册构造器。
     */
    public static final class Builder {

        private final NetService service;
        private final NetEndpointId id;
        private long timeoutMillis = 60_000L;
        private long maxBytes = NetPayloadLimits.DEFAULT_STREAM_CONTENT_LIMIT;
        private NetStreamHandler handler;

        Builder(NetService service, NetEndpointId id) {
            this.service = service;
            this.id = id;
        }

        /**
         * 设置 Stream 完成超时。
         *
         * @param timeout 超时时长
         * @return 构造器
         */
        public Builder timeout(Duration timeout) {
            this.timeoutMillis = Objects.requireNonNull(timeout, "timeout").toMillis();
            return this;
        }

        /**
         * 设置单次 Stream 响应最大字节数，硬上限为 1 GiB。
         *
         * @param maxBytes 最大字节数
         * @return 构造器
         */
        public Builder maxBytes(long maxBytes) {
            this.maxBytes = NetPayloadLimits.clampStreamContentLimit(maxBytes);
            return this;
        }

        /**
         * 设置服务端请求处理器。
         *
         * @param handler 处理器
         * @return 构造器
         */
        public Builder onRequest(NetStreamHandler handler) {
            this.handler = handler;
            return this;
        }

        /**
         * 注册 endpoint。
         *
         * @return endpoint
         */
        public NetStreamEndpoint register() {
            return service.registerStreamEndpoint(new NetStreamEndpoint(service, id, timeoutMillis, maxBytes,
                    handler));
        }
    }

    /**
     * Stream 处理器。
     */
    public interface NetStreamHandler {

        /**
         * 处理 Stream 请求。
         *
         * @param request 请求
         * @param context 请求上下文
         */
        void onRequest(NetRequest request, NetStreamRequestContext context);
    }

    /**
     * Stream 请求上下文。
     */
    public static final class NetStreamRequestContext {

        private final NetService service;
        private final String endpointKey;
        private final long requestId;
        private final NetReceiveContext receiveContext;
        private final Object replyTarget;

        NetStreamRequestContext(NetService service, String endpointKey, long requestId,
                NetReceiveContext receiveContext, Object replyTarget) {
            this.service = service;
            this.endpointKey = endpointKey;
            this.requestId = requestId;
            this.receiveContext = receiveContext;
            this.replyTarget = replyTarget;
        }

        /**
         * 回复完整 Stream 结果。
         *
         * @param response 响应
         */
        public void reply(NetResponse response) {
            service.replyStream(replyTarget, endpointKey, requestId, Objects.requireNonNull(response, "response"));
        }

        /**
         * 回复成功 body。
         *
         * @param body body
         */
        public void reply(NetBody body) {
            reply(NetResponse.ok(body));
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
            service.failStream(replyTarget, endpointKey, requestId, throwable);
        }

        /**
         * 返回对端是否已取消本次 Stream。
         *
         * @return true 表示已取消
         */
        public boolean isCancelled() {
            return service.isStreamCancelled(requestId);
        }

        public NetReceiveContext getReceiveContext() {
            return receiveContext;
        }
    }
}
