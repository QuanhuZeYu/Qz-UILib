package club.heiqi.uilib.net.api;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import club.heiqi.uilib.config.ConfigTemplateSyncManager;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.core.NetRequestRegistry;
import club.heiqi.uilib.net.transport.NetReceiveOrigin;

/**
 * 网络信封入站路由分发器。
 */
final class NetEnvelopeDispatcher {

    private final NetService service;
    private final Map<String, NetChannel> channels;
    private final Map<String, NetFetchEndpoint> fetchEndpoints;
    private final Map<String, NetStreamEndpoint> streamEndpoints;
    private final Map<String, NetStore> stores;
    private final NetRequestRegistry requestRegistry;
    private final NetStreamDownloadRegistry streamDownloads;

    NetEnvelopeDispatcher(NetService service, Map<String, NetChannel> channels,
            Map<String, NetFetchEndpoint> fetchEndpoints, Map<String, NetStreamEndpoint> streamEndpoints,
            Map<String, NetStore> stores, NetRequestRegistry requestRegistry,
            NetStreamDownloadRegistry streamDownloads) {
        this.service = service;
        this.channels = channels;
        this.fetchEndpoints = fetchEndpoints;
        this.streamEndpoints = streamEndpoints;
        this.stores = stores;
        this.requestRegistry = requestRegistry;
        this.streamDownloads = streamDownloads;
    }

    /**
     * 分发已解码且方向校验通过的信封。
     *
     * @param envelope 信封
     * @param origin 接收来源
     */
    void dispatch(NetEnvelope envelope, NetReceiveOrigin origin) {
        service.tickTimeouts();
        if (envelope.getKind() == NetEnvelope.Kind.META) {
            MyMod.LOG.debug("收到 Qz 网络能力握手：side={} body={}", origin.getSide(),
                    new String(envelope.getPayload(), StandardCharsets.UTF_8));
            if (origin.getSide() == club.heiqi.uilib.net.transport.NetSide.CLIENT) {
                ConfigTemplateSyncManager.getInstance().setClientRemoteAvailable(true);
            }
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.CHANNEL) {
            dispatchChannel(envelope, origin);
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.FETCH_REQUEST) {
            dispatchFetchRequest(envelope, origin);
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STREAM_REQUEST) {
            dispatchStreamRequest(envelope, origin);
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STREAM_START) {
            streamDownloads.handleStart(envelope);
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STREAM_CHUNK) {
            streamDownloads.handleChunk(envelope);
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STREAM_ERROR) {
            streamDownloads.handleError(envelope);
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STREAM_CANCEL) {
            streamDownloads.markRemoteCancelled(envelope, origin.getSender());
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.FETCH_RESPONSE) {
            requestRegistry.complete(envelope.getRequestId(), NetResponse.fromWire(envelope.getStatusCode(),
                    envelope.getHeaders(), envelope.toBody()));
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.FETCH_ERROR) {
            String message = new String(envelope.getPayload(), StandardCharsets.UTF_8);
            requestRegistry.fail(envelope.getRequestId(), new NetRemoteException(message));
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STORE_SNAPSHOT) {
            dispatchStoreSnapshot(envelope);
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STORE_DELTA) {
            dispatchStoreDelta(envelope);
        }
    }

    private void dispatchChannel(NetEnvelope envelope, NetReceiveOrigin origin) {
        NetChannel channel = channels.get(envelope.getKey());
        if (channel == null) {
            MyMod.LOG.warn("收到未注册 Channel 帧：{}", envelope.getKey());
            return;
        }
        channel.receive(NetMessage.fromWire(envelope.getHeaders(), envelope.toBody()),
                new NetReceiveContext(service, origin.getSide(), origin.getSender()));
    }

    private void dispatchFetchRequest(NetEnvelope envelope, NetReceiveOrigin origin) {
        NetFetchEndpoint endpoint = fetchEndpoints.get(envelope.getKey());
        if (endpoint == null) {
            MyMod.LOG.warn("收到未注册 Fetch 请求：{}", envelope.getKey());
            return;
        }
        NetFetchEndpoint.RateLimitDecision decision = endpoint.checkRateLimit(origin.getSender());
        if (!decision.isAllowed()) {
            MyMod.LOG.warn("Qz Fetch 请求被限流：endpoint={} sender={} retryAfterMs={}", envelope.getKey(),
                    String.valueOf(origin.getSender()), Long.valueOf(decision.getRetryAfterMillis()));
            endpointRateLimited(origin.getSender(), envelope.getKey(), envelope.getRequestId(), decision);
            return;
        }
        try {
            endpoint.receiveRequest(NetRequest.fromWire(envelope.getHeaders(), envelope.toBody()),
                    new NetFetchEndpoint.NetFetchRequestContext(service, envelope.getKey(), envelope.getRequestId(),
                            new NetReceiveContext(service, origin.getSide(), origin.getSender()),
                            origin.getSender()));
        } catch (RuntimeException exception) {
            service.failFetch(origin.getSender(), envelope.getKey(), envelope.getRequestId(), exception);
        }
    }

    private void dispatchStreamRequest(NetEnvelope envelope, NetReceiveOrigin origin) {
        NetStreamEndpoint endpoint = streamEndpoints.get(envelope.getKey());
        if (endpoint == null) {
            MyMod.LOG.warn("收到未注册 Stream 请求：{}", envelope.getKey());
            service.failStream(origin.getSender(), envelope.getKey(), envelope.getRequestId(),
                    new IllegalStateException("Stream endpoint 未注册: " + envelope.getKey()));
            return;
        }
        try {
            endpoint.receiveRequest(NetRequest.fromWire(envelope.getHeaders(), envelope.toBody()),
                    new NetStreamEndpoint.NetStreamRequestContext(service, envelope.getKey(), envelope.getRequestId(),
                            new NetReceiveContext(service, origin.getSide(), origin.getSender()),
                            origin.getSender()));
        } catch (RuntimeException exception) {
            service.failStream(origin.getSender(), envelope.getKey(), envelope.getRequestId(), exception);
        }
    }

    private void dispatchStoreSnapshot(NetEnvelope envelope) {
        NetStore store = stores.get(envelope.getKey());
        if (store == null) {
            MyMod.LOG.warn("收到未注册 Store 帧：{}", envelope.getKey());
            return;
        }
        store.receiveSnapshot(envelope.toBody());
    }

    private void dispatchStoreDelta(NetEnvelope envelope) {
        NetStore store = stores.get(envelope.getKey());
        if (store == null) {
            MyMod.LOG.warn("收到未注册 Store 增量帧：{}", envelope.getKey());
            return;
        }
        store.receiveDelta(envelope.toBody());
    }

    private void endpointRateLimited(Object sender, String endpointKey, long requestId,
            NetFetchEndpoint.RateLimitDecision decision) {
        if (sender == null) {
            MyMod.LOG.warn("Qz Fetch 限流响应缺少发送者，无法回复：endpoint={} requestId={}", endpointKey,
                    Long.valueOf(requestId));
            return;
        }
        NetResponse response = NetResponse.error(429, "Fetch 请求过于频繁")
                .withHeader("retry-after-ms", Long.toString(decision.getRetryAfterMillis()));
        service.replyFetch(sender, endpointKey, requestId, response);
    }
}
