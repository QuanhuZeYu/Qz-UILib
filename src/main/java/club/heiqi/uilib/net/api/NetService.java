package club.heiqi.uilib.net.api;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.core.MainThreadDispatcher;
import club.heiqi.uilib.net.core.NetChunkAssembler;
import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.core.NetRequestRegistry;
import club.heiqi.uilib.net.transport.FrameHandler;
import club.heiqi.uilib.net.transport.ITransport;
import club.heiqi.uilib.net.transport.NetReceiveOrigin;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * Qz 网络层单例门面。
 *
 * <p>公共心智模型是 HTTP-like 内容语义：业务注册 route/channel，然后收发
 * JSON、文本或二进制 body。网络层不把每个请求建成一个 Java 消息类型。</p>
 */
public final class NetService {

    public static final String PHYSICAL_CHANNEL = "qz:0";
    private static final String META_KEY = "qz:meta";
    private static final String CHUNK_KEY = "qz:chunk";
    private static final String STREAM_TOTAL_BYTES_HEADER = "x-qz-stream-total-bytes";
    private static final String STREAM_CHUNK_COUNT_HEADER = "x-qz-stream-chunk-count";
    private static final String STREAM_SEQUENCE_HEADER = "x-qz-stream-sequence";
    private static final int STREAM_FRAME_MARGIN_BYTES = 2048;
    private static final int PREFERRED_STREAM_CHUNK_BYTES = 256 * 1024;
    private static final NetService INSTANCE = new NetService();

    private final Map<String, NetChannel> channels = new ConcurrentHashMap<String, NetChannel>();
    private final Map<String, NetFetchEndpoint> fetchEndpoints = new ConcurrentHashMap<String, NetFetchEndpoint>();
    private final Map<String, NetStreamEndpoint> streamEndpoints = new ConcurrentHashMap<String, NetStreamEndpoint>();
    private final Map<String, NetStore> stores = new ConcurrentHashMap<String, NetStore>();
    private final NetRequestRegistry requestRegistry = new NetRequestRegistry();
    private final NetChunkAssembler chunkAssembler = new NetChunkAssembler();
    private final AtomicLong nextChunkStreamId = new AtomicLong(1L);
    private final AtomicLong nextStreamRequestId = new AtomicLong(1L);
    private final Map<Long, StreamDownload> pendingStreams = new ConcurrentHashMap<Long, StreamDownload>();
    private final Set<Long> remoteCancelledStreams =
            Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private final FrameHandler frameHandler = new FrameHandler() {
        @Override
        public void handleFrame(String channelName, byte[] payload, NetReceiveOrigin origin) {
            handleInboundFrame(channelName, payload, origin);
        }
    };

    private volatile ITransport transport;
    private volatile boolean frozen;

    private NetService() {}

    /**
     * 返回网络层单例。
     *
     * @return 单例
     */
    public static NetService getInstance() {
        return INSTANCE;
    }

    /**
     * 返回客户端主线程 executor。
     *
     * @return executor
     */
    public static Executor mainThreadExecutor() {
        return MainThreadDispatcher.getInstance().asExecutor(NetSide.CLIENT);
    }

    /**
     * 返回指定侧主线程 executor。
     *
     * @param side 目标侧
     * @return executor
     */
    public static Executor mainThreadExecutor(NetSide side) {
        return MainThreadDispatcher.getInstance().asExecutor(side);
    }

    /**
     * 启动网络服务并绑定传输适配器。
     *
     * @param transport 传输适配器
     */
    public synchronized void bootstrap(ITransport transport) {
        Objects.requireNonNull(transport, "transport");
        if (this.transport != null) {
            if (this.transport.getClass() == transport.getClass()) {
                return;
            }
            throw new IllegalStateException("网络传输适配器已启动: " + this.transport.getName());
        }
        this.transport = transport;
        transport.bootstrap(frameHandler);
        MyMod.LOG.info("Qz 网络层已启动，适配器：{}", transport.getName());
    }

    /**
     * 冻结注册表，后续注册会失败。
     */
    public synchronized void freeze() {
        this.frozen = true;
        MyMod.LOG.info("Qz 网络层注册表已冻结：channels={} fetchEndpoints={} streamEndpoints={} stores={}",
                channels.size(), fetchEndpoints.size(), streamEndpoints.size(), stores.size());
    }

    /**
     * 创建 Channel 构造器。
     *
     * @param id Channel id
     * @return 构造器
     */
    public NetChannel.Builder channel(NetChannelId id) {
        return new NetChannel.Builder(this, id);
    }

    /**
     * 创建 Fetch 构造器。
     *
     * @param id endpoint id
     * @return 构造器
     */
    public NetFetchEndpoint.Builder fetch(NetEndpointId id) {
        return new NetFetchEndpoint.Builder(this, id);
    }

    /**
     * 创建 Stream 构造器。
     *
     * @param id endpoint id
     * @return 构造器
     */
    public NetStreamEndpoint.Builder stream(NetEndpointId id) {
        return new NetStreamEndpoint.Builder(this, id);
    }

    /**
     * 创建 Store 构造器。
     *
     * @param id Store id
     * @return 构造器
     */
    public NetStore.Builder store(NetStoreId id) {
        return new NetStore.Builder(this, id);
    }

    synchronized NetChannel registerChannel(NetChannel channel) {
        ensureRegistrable();
        String key = channel.getId().asKey();
        if (channels.putIfAbsent(key, channel) != null) {
            throw new IllegalStateException("Channel 已注册: " + key);
        }
        return channel;
    }

    synchronized NetFetchEndpoint registerFetchEndpoint(NetFetchEndpoint endpoint) {
        ensureRegistrable();
        String key = endpoint.getId().asKey();
        if (fetchEndpoints.putIfAbsent(key, endpoint) != null) {
            throw new IllegalStateException("Fetch endpoint 已注册: " + key);
        }
        return endpoint;
    }

    synchronized NetStreamEndpoint registerStreamEndpoint(NetStreamEndpoint endpoint) {
        ensureRegistrable();
        String key = endpoint.getId().asKey();
        if (streamEndpoints.putIfAbsent(key, endpoint) != null) {
            throw new IllegalStateException("Stream endpoint 已注册: " + key);
        }
        return endpoint;
    }

    synchronized NetStore registerStore(NetStore store) {
        ensureRegistrable();
        String key = store.getId().asKey();
        if (stores.putIfAbsent(key, store) != null) {
            throw new IllegalStateException("Store 已注册: " + key);
        }
        return store;
    }

    void sendChannelMessage(NetChannel channel, NetTarget target, NetMessage message) {
        sendEnvelope(target, NetEnvelope.of(NetEnvelope.Kind.CHANNEL, targetSideOf(target), channel.getId().asKey(),
                0L, 0, message.getHeaders(), message.getBody()));
    }

    CompletableFuture<NetResponse> callFetchEndpoint(NetFetchEndpoint endpoint, NetRequest request) {
        NetRequestRegistry.PendingRequest<NetResponse> pending = requestRegistry.register(endpoint.getTimeoutMillis());
        try {
            sendEnvelope(NetTarget.server(), NetEnvelope.of(NetEnvelope.Kind.FETCH_REQUEST, NetSide.SERVER,
                    endpoint.getId().asKey(), pending.getRequestId(), 0, request.getHeaders(), request.getBody()));
        } catch (RuntimeException exception) {
            requestRegistry.fail(pending.getRequestId(), exception);
            throw exception;
        }
        return pending.getFuture();
    }

    NetStreamCall callStreamEndpoint(NetStreamEndpoint endpoint, NetRequest request) {
        long requestId = nextStreamRequestId.getAndIncrement();
        NetStreamCall call = new NetStreamCall(this, endpoint.getId().asKey(), requestId);
        StreamDownload download = new StreamDownload(call, System.currentTimeMillis() + endpoint.getTimeoutMillis(),
                endpoint.getMaxBytes());
        pendingStreams.put(Long.valueOf(requestId), download);
        try {
            sendEnvelope(NetTarget.server(), NetEnvelope.of(NetEnvelope.Kind.STREAM_REQUEST, NetSide.SERVER,
                    endpoint.getId().asKey(), requestId, 0, request.getHeaders(), request.getBody()));
        } catch (RuntimeException exception) {
            pendingStreams.remove(Long.valueOf(requestId), download);
            call.fail(exception);
            throw exception;
        }
        return call;
    }

    void replyFetch(Object player, String endpointKey, long requestId, NetResponse response) {
        if (player == null) {
            MyMod.LOG.warn("Qz Fetch 回复缺少发送者，无法发送：endpoint={} requestId={}", endpointKey,
                    Long.valueOf(requestId));
            return;
        }
        sendEnvelope(NetTarget.player(player), NetEnvelope.of(NetEnvelope.Kind.FETCH_RESPONSE, NetSide.CLIENT,
                endpointKey, requestId, response.getStatusCode(), response.getHeaders(), response.getBody()));
    }

    void failFetch(Object player, String endpointKey, long requestId, Throwable throwable) {
        String message = throwable == null ? "远端处理失败" : throwable.getClass().getSimpleName() + ": "
                + (throwable.getMessage() == null ? "" : throwable.getMessage());
        replyFetch(player, endpointKey, requestId, NetResponse.error(500, message));
    }

    void replyStream(Object player, String endpointKey, long requestId, NetResponse response) {
        Objects.requireNonNull(response, "response");
        if (isStreamCancelled(requestId)) {
            remoteCancelledStreams.remove(Long.valueOf(requestId));
            MyMod.LOG.info("Qz Stream 在开始前已被远端取消，跳过发送：endpoint={} requestId={}", endpointKey,
                    Long.valueOf(requestId));
            return;
        }
        NetStreamEndpoint endpoint = streamEndpoints.get(endpointKey);
        long maxBytes = endpoint == null ? NetPayloadLimits.DEFAULT_STREAM_CONTENT_LIMIT : endpoint.getMaxBytes();
        if (player == null) {
            MyMod.LOG.warn("Qz Stream 回复缺少发送者，无法发送：endpoint={} requestId={}", endpointKey,
                    Long.valueOf(requestId));
            return;
        }
        NetBody body = response.getBody();
        NetPayloadLimits.requireStreamContentSize(body.size(), maxBytes);
        NetTarget target = NetTarget.player(player);
        byte[] bytes = body.getBytes();
        int physicalLimit = requireTransport().getPhysicalFrameLimit(NetSide.CLIENT);
        int chunkSize = streamChunkSize(physicalLimit);
        int chunkCount = bytes.length == 0 ? 0 : (bytes.length + chunkSize - 1) / chunkSize;

        Map<String, String> startHeaders = new LinkedHashMap<String, String>(response.getHeaders());
        startHeaders.put(STREAM_TOTAL_BYTES_HEADER, Long.toString(bytes.length));
        startHeaders.put(STREAM_CHUNK_COUNT_HEADER, Integer.toString(chunkCount));
        sendStreamEnvelope(target, NetEnvelope.of(NetEnvelope.Kind.STREAM_START, NetSide.CLIENT, endpointKey,
                requestId, response.getStatusCode(), startHeaders, NetBody.of(body.getContentType(), new byte[0])));

        for (int sequence = 0; sequence < chunkCount; sequence++) {
            if (isStreamCancelled(requestId)) {
                MyMod.LOG.info("Qz Stream 已被远端取消，停止发送：endpoint={} requestId={}", endpointKey,
                        Long.valueOf(requestId));
                break;
            }
            int offset = sequence * chunkSize;
            int length = Math.min(chunkSize, bytes.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(bytes, offset, chunk, 0, length);
            Map<String, String> chunkHeaders = new LinkedHashMap<String, String>();
            chunkHeaders.put(STREAM_SEQUENCE_HEADER, Integer.toString(sequence));
            sendStreamEnvelope(target, NetEnvelope.of(NetEnvelope.Kind.STREAM_CHUNK, NetSide.CLIENT, endpointKey,
                    requestId, 0, chunkHeaders, NetBody.binary(chunk)));
        }
        remoteCancelledStreams.remove(Long.valueOf(requestId));
    }

    void failStream(Object player, String endpointKey, long requestId, Throwable throwable) {
        String message = throwable == null ? "远端 Stream 处理失败" : throwable.getClass().getSimpleName() + ": "
                + (throwable.getMessage() == null ? "" : throwable.getMessage());
        if (player == null) {
            MyMod.LOG.warn("Qz Stream 错误帧缺少发送者，无法回复：endpoint={} requestId={}", endpointKey,
                    Long.valueOf(requestId));
            return;
        }
        sendStreamEnvelope(NetTarget.player(player), NetEnvelope.of(NetEnvelope.Kind.STREAM_ERROR, NetSide.CLIENT,
                endpointKey, requestId, 500, Collections.<String, String>emptyMap(), NetBody.text(message)));
    }

    boolean isStreamCancelled(long requestId) {
        return remoteCancelledStreams.contains(Long.valueOf(requestId));
    }

    void cancelStreamCall(String endpointKey, long requestId) {
        pendingStreams.remove(Long.valueOf(requestId));
        try {
            sendEnvelope(NetTarget.server(), NetEnvelope.binary(NetEnvelope.Kind.STREAM_CANCEL, NetSide.SERVER,
                    endpointKey, requestId, new byte[0]));
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("发送 Qz Stream 取消帧失败：endpoint={} requestId={}", endpointKey,
                    Long.valueOf(requestId), exception);
        }
    }

    void sendStoreSnapshot(NetStore store, NetTarget target, NetBody snapshot) {
        sendStoreEnvelope(store, target, NetEnvelope.of(NetEnvelope.Kind.STORE_SNAPSHOT, targetSideOf(target),
                store.getId().asKey(), 0L, 0, Collections.<String, String>emptyMap(), snapshot));
    }

    void sendStoreDelta(NetStore store, NetTarget target, NetBody delta) {
        sendStoreEnvelope(store, target, NetEnvelope.of(NetEnvelope.Kind.STORE_DELTA, targetSideOf(target),
                store.getId().asKey(), 0L, 0, Collections.<String, String>emptyMap(), delta));
    }

    /**
     * 客户端连接就绪时发送协议能力握手。
     */
    public void sendCapabilityHandshakeToServer() {
        sendMeta(NetTarget.server(), NetSide.SERVER);
    }

    /**
     * 服务端玩家加入时发送协议能力握手。
     *
     * @param player 玩家对象
     */
    public void sendCapabilityHandshakeToPlayer(Object player) {
        sendMeta(NetTarget.player(player), NetSide.CLIENT);
    }

    /**
     * 客户端断连时清理 pending 请求。
     */
    public void onClientDisconnected() {
        requestRegistry.failAllDisconnected();
        failAllPendingStreams(new NetDisconnectedException("网络连接已断开"));
        chunkAssembler.clear();
        remoteCancelledStreams.clear();
    }

    /**
     * 关闭网络层。
     */
    public synchronized void shutdown() {
        requestRegistry.failAllDisconnected();
        failAllPendingStreams(new NetDisconnectedException("网络连接已断开"));
        chunkAssembler.clear();
        remoteCancelledStreams.clear();
        ITransport activeTransport = transport;
        if (activeTransport != null) {
            activeTransport.shutdown();
        }
        transport = null;
    }

    /**
     * 入队到主线程。
     *
     * @param side 目标侧
     * @param runnable 任务
     */
    public void runOnMainThread(NetSide side, Runnable runnable) {
        MainThreadDispatcher.getInstance().enqueue(side, runnable);
    }

    /**
     * 排空客户端主线程任务。
     */
    public void drainClientMainThreadTasks() {
        MainThreadDispatcher.getInstance().drainClient();
    }

    /**
     * 排空服务端主线程任务。
     */
    public void drainServerMainThreadTasks() {
        MainThreadDispatcher.getInstance().drainServer();
    }

    void resetForTests() {
        channels.clear();
        fetchEndpoints.clear();
        streamEndpoints.clear();
        stores.clear();
        requestRegistry.failAllDisconnected();
        failAllPendingStreams(new NetDisconnectedException("网络连接已断开"));
        chunkAssembler.clear();
        remoteCancelledStreams.clear();
        frozen = false;
        transport = null;
    }

    private void sendMeta(NetTarget target, NetSide targetSide) {
        String json = "{\"protocol\":2,\"contentTypes\":[\"application/json\",\"application/octet-stream\","
                + "\"text/plain; charset=utf-8\"],\"ordinaryLogicalLimit\":"
                + NetPayloadLimits.DEFAULT_LOGICAL_MESSAGE_LIMIT + "}";
        sendEnvelope(target, NetEnvelope.of(NetEnvelope.Kind.META, targetSide, META_KEY, 0L, 0,
                Collections.<String, String>emptyMap(), NetBody.json(json)));
    }

    private void handleInboundFrame(String channelName, byte[] payload, NetReceiveOrigin origin) {
        try {
            NetEnvelope envelope = NetEnvelope.decode(payload);
            if (envelope.getKind() == NetEnvelope.Kind.CHUNK) {
                byte[] completed = chunkAssembler.accept(envelope.getPayload());
                if (completed == null) {
                    return;
                }
                envelope = NetEnvelope.decode(completed);
            }
            if (envelope.getTargetSide() != origin.getSide()) {
                MyMod.LOG.warn("丢弃方向不匹配的 Qz 网络帧：channel={} kind={} expectedSide={} actualSide={}",
                        channelName, envelope.getKind(), envelope.getTargetSide(), origin.getSide());
                return;
            }
            dispatchEnvelope(envelope, origin);
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("Qz 网络帧处理失败，channel={}", channelName, exception);
        }
    }

    private void dispatchEnvelope(NetEnvelope envelope, NetReceiveOrigin origin) {
        requestRegistry.expireTimedOut();
        expireTimedOutStreams();
        if (envelope.getKind() == NetEnvelope.Kind.META) {
            MyMod.LOG.debug("收到 Qz 网络能力握手：side={} body={}", origin.getSide(),
                    new String(envelope.getPayload(), StandardCharsets.UTF_8));
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.CHANNEL) {
            NetChannel channel = channels.get(envelope.getKey());
            if (channel == null) {
                MyMod.LOG.warn("收到未注册 Channel 帧：{}", envelope.getKey());
                return;
            }
            channel.receive(NetMessage.fromWire(envelope.getHeaders(), envelope.toBody()),
                    new NetReceiveContext(this, origin.getSide(), origin.getSender()));
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.FETCH_REQUEST) {
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
                        new NetFetchEndpoint.NetFetchRequestContext(this, envelope.getKey(), envelope.getRequestId(),
                                new NetReceiveContext(this, origin.getSide(), origin.getSender()),
                                origin.getSender()));
            } catch (RuntimeException exception) {
                failFetch(origin.getSender(), envelope.getKey(), envelope.getRequestId(), exception);
            }
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STREAM_REQUEST) {
            NetStreamEndpoint endpoint = streamEndpoints.get(envelope.getKey());
            if (endpoint == null) {
                MyMod.LOG.warn("收到未注册 Stream 请求：{}", envelope.getKey());
                failStream(origin.getSender(), envelope.getKey(), envelope.getRequestId(),
                        new IllegalStateException("Stream endpoint 未注册: " + envelope.getKey()));
                return;
            }
            try {
                endpoint.receiveRequest(NetRequest.fromWire(envelope.getHeaders(), envelope.toBody()),
                        new NetStreamEndpoint.NetStreamRequestContext(this, envelope.getKey(), envelope.getRequestId(),
                                new NetReceiveContext(this, origin.getSide(), origin.getSender()),
                                origin.getSender()));
            } catch (RuntimeException exception) {
                failStream(origin.getSender(), envelope.getKey(), envelope.getRequestId(), exception);
            }
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STREAM_START) {
            handleStreamStart(envelope);
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STREAM_CHUNK) {
            handleStreamChunk(envelope);
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STREAM_ERROR) {
            handleStreamError(envelope);
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STREAM_CANCEL) {
            remoteCancelledStreams.add(Long.valueOf(envelope.getRequestId()));
            MyMod.LOG.info("收到 Qz Stream 取消帧：endpoint={} requestId={} sender={}", envelope.getKey(),
                    Long.valueOf(envelope.getRequestId()), String.valueOf(origin.getSender()));
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
            NetStore store = stores.get(envelope.getKey());
            if (store == null) {
                MyMod.LOG.warn("收到未注册 Store 帧：{}", envelope.getKey());
                return;
            }
            store.receiveSnapshot(envelope.toBody());
            return;
        }
        if (envelope.getKind() == NetEnvelope.Kind.STORE_DELTA) {
            NetStore store = stores.get(envelope.getKey());
            if (store == null) {
                MyMod.LOG.warn("收到未注册 Store 增量帧：{}", envelope.getKey());
                return;
            }
            store.receiveDelta(envelope.toBody());
        }
    }

    private void sendEnvelope(NetTarget target, NetEnvelope envelope) {
        ITransport activeTransport = transport;
        if (activeTransport == null) {
            throw new IllegalStateException("网络层尚未 bootstrap");
        }
        byte[] encoded = envelope.encode();
        NetPayloadLimits.requireLogicalMessageSize(encoded.length);
        if (encoded.length >= NetPayloadLimits.LARGE_MESSAGE_WARN_THRESHOLD) {
            MyMod.LOG.warn("Qz 普通网络消息已达到大消息阈值：{} bytes，key={}", encoded.length, envelope.getKey());
        }
        int physicalLimit = activeTransport.getPhysicalFrameLimit(envelope.getTargetSide());
        if (encoded.length <= physicalLimit) {
            sendPhysical(target, encoded);
            return;
        }
        sendChunked(target, envelope.getTargetSide(), encoded, physicalLimit);
    }

    private void sendStoreEnvelope(NetStore store, NetTarget target, NetEnvelope envelope) {
        if (!store.hasAccessControl()) {
            sendEnvelope(target, envelope);
            return;
        }
        switch (target.getType()) {
            case PLAYER:
                sendStoreEnvelopeToPlayerIfAllowed(store, target.getPlayer(), envelope);
                return;
            case PLAYERS:
                for (Object player : target.getPlayers()) {
                    sendStoreEnvelopeToPlayerIfAllowed(store, player, envelope);
                }
                return;
            case ALL:
                sendStoreEnvelopeToAccessiblePlayers(store, null, envelope);
                return;
            case DIMENSION:
                sendStoreEnvelopeToAccessiblePlayers(store, Integer.valueOf(target.getDimensionId()), envelope);
                return;
            default:
                throw new IllegalStateException("Store 同步帧只能发送到客户端目标：" + target.getType());
        }
    }

    private void sendStoreEnvelopeToAccessiblePlayers(NetStore store, Integer dimensionId, NetEnvelope envelope) {
        ITransport activeTransport = transport;
        if (activeTransport == null) {
            throw new IllegalStateException("网络层尚未 bootstrap");
        }
        for (Object player : activeTransport.getConnectedPlayers()) {
            if (dimensionId != null && !dimensionId.equals(activeTransport.getPlayerDimensionId(player))) {
                continue;
            }
            sendStoreEnvelopeToPlayerIfAllowed(store, player, envelope);
        }
    }

    private void sendStoreEnvelopeToPlayerIfAllowed(NetStore store, Object player, NetEnvelope envelope) {
        if (player != null && store.canAccess(player)) {
            sendEnvelope(NetTarget.player(player), envelope);
        }
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
        replyFetch(sender, endpointKey, requestId, response);
    }

    private void handleStreamStart(NetEnvelope envelope) {
        StreamDownload download = pendingStreams.get(Long.valueOf(envelope.getRequestId()));
        if (download == null) {
            MyMod.LOG.warn("收到未知 Stream start：endpoint={} requestId={}", envelope.getKey(),
                    Long.valueOf(envelope.getRequestId()));
            return;
        }
        boolean started;
        try {
            started = download.markStarted(envelope);
        } catch (RuntimeException exception) {
            pendingStreams.remove(Long.valueOf(envelope.getRequestId()), download);
            download.getCall().fail(exception);
            return;
        }
        if (!started) {
            MyMod.LOG.warn("收到重复 Stream start：endpoint={} requestId={}", envelope.getKey(),
                    Long.valueOf(envelope.getRequestId()));
            return;
        }
        download.getCall().emitProgress(new NetStreamProgress(envelope.getRequestId(), 0L,
                download.getTotalBytes()));
        if (download.getTotalBytes() == 0L) {
            completeStreamDownload(envelope.getRequestId(), download);
        }
    }

    private void handleStreamChunk(NetEnvelope envelope) {
        StreamDownload download = pendingStreams.get(Long.valueOf(envelope.getRequestId()));
        if (download == null) {
            MyMod.LOG.warn("收到未知 Stream chunk：endpoint={} requestId={}", envelope.getKey(),
                    Long.valueOf(envelope.getRequestId()));
            return;
        }
        boolean accepted;
        try {
            accepted = download.acceptChunk(envelope);
        } catch (RuntimeException exception) {
            pendingStreams.remove(Long.valueOf(envelope.getRequestId()), download);
            download.getCall().fail(exception);
            return;
        }
        if (!accepted) {
            pendingStreams.remove(Long.valueOf(envelope.getRequestId()), download);
            download.getCall().fail(new IllegalStateException("Stream 分片序号或长度不一致: " + envelope.getKey()));
            return;
        }
        download.getCall().emitProgress(new NetStreamProgress(envelope.getRequestId(), download.getReceivedBytes(),
                download.getTotalBytes()));
        if (download.isComplete()) {
            completeStreamDownload(envelope.getRequestId(), download);
        }
    }

    private void handleStreamError(NetEnvelope envelope) {
        StreamDownload download = pendingStreams.remove(Long.valueOf(envelope.getRequestId()));
        if (download == null) {
            MyMod.LOG.warn("收到未知 Stream error：endpoint={} requestId={}", envelope.getKey(),
                    Long.valueOf(envelope.getRequestId()));
            return;
        }
        download.getCall().fail(new NetRemoteException(new String(envelope.getPayload(), StandardCharsets.UTF_8)));
    }

    private void completeStreamDownload(long requestId, StreamDownload download) {
        pendingStreams.remove(Long.valueOf(requestId), download);
        try {
            download.getCall().complete(NetResponse.fromWire(download.getStatusCode(), download.getHeaders(),
                    NetBody.of(download.getContentType(), download.getBytes())));
        } catch (RuntimeException exception) {
            download.getCall().fail(exception);
        }
    }

    private void expireTimedOutStreams() {
        long now = System.currentTimeMillis();
        for (StreamDownload download : pendingStreams.values()) {
            if (download.isExpired(now) && pendingStreams.remove(Long.valueOf(download.getRequestId()), download)) {
                download.getCall().fail(new NetTimeoutException("Stream 请求超时: " + download.getRequestId()));
            }
        }
    }

    private void failAllPendingStreams(Throwable throwable) {
        for (StreamDownload download : pendingStreams.values()) {
            if (pendingStreams.remove(Long.valueOf(download.getRequestId()), download)) {
                download.getCall().fail(throwable);
            }
        }
    }

    private void sendStreamEnvelope(NetTarget target, NetEnvelope envelope) {
        ITransport activeTransport = requireTransport();
        byte[] encoded = envelope.encode();
        int physicalLimit = activeTransport.getPhysicalFrameLimit(envelope.getTargetSide());
        if (encoded.length > physicalLimit) {
            throw new IllegalStateException("Stream 帧超过物理上限：" + encoded.length + " > " + physicalLimit);
        }
        sendPhysical(target, encoded);
    }

    private int streamChunkSize(int physicalLimit) {
        int normalizedLimit = NetPayloadLimits.clampPhysicalLimit(physicalLimit);
        int preferred = Math.min(PREFERRED_STREAM_CHUNK_BYTES, Math.max(1, normalizedLimit - STREAM_FRAME_MARGIN_BYTES));
        return Math.max(1, preferred);
    }

    private ITransport requireTransport() {
        ITransport activeTransport = transport;
        if (activeTransport == null) {
            throw new IllegalStateException("网络层尚未 bootstrap");
        }
        return activeTransport;
    }

    private void sendChunked(NetTarget target, NetSide targetSide, byte[] encoded, int physicalLimit) {
        int chunkSize = Math.max(1, physicalLimit - 192);
        long streamId = nextChunkStreamId.getAndIncrement();
        int total = (encoded.length + chunkSize - 1) / chunkSize;
        for (int sequence = 0; sequence < total; sequence++) {
            int offset = sequence * chunkSize;
            int length = Math.min(chunkSize, encoded.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(encoded, offset, chunk, 0, length);
            NetEnvelope chunkEnvelope = NetEnvelope.binary(NetEnvelope.Kind.CHUNK, targetSide, CHUNK_KEY, 0L,
                    NetChunkAssembler.encodeChunk(streamId, sequence, total, encoded.length, chunk));
            byte[] chunkBytes = chunkEnvelope.encode();
            if (chunkBytes.length > physicalLimit) {
                throw new IllegalStateException("分片帧仍超过物理上限：" + chunkBytes.length + " > " + physicalLimit);
            }
            sendPhysical(target, chunkBytes);
        }
    }

    private void sendPhysical(NetTarget target, byte[] payload) {
        ITransport activeTransport = transport;
        switch (target.getType()) {
            case SERVER:
                activeTransport.sendToServer(PHYSICAL_CHANNEL, payload);
                return;
            case PLAYER:
                activeTransport.sendToPlayer(target.getPlayer(), PHYSICAL_CHANNEL, payload);
                return;
            case PLAYERS:
                for (Object player : target.getPlayers()) {
                    activeTransport.sendToPlayer(player, PHYSICAL_CHANNEL, payload);
                }
                return;
            case ALL:
                activeTransport.sendToAll(PHYSICAL_CHANNEL, payload);
                return;
            case DIMENSION:
                activeTransport.sendToDimension(target.getDimensionId(), PHYSICAL_CHANNEL, payload);
                return;
            default:
                throw new IllegalStateException("未知网络目标：" + target.getType());
        }
    }

    private static final class StreamDownload {

        private final NetStreamCall call;
        private final long deadlineMillis;
        private final long maxBytes;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Map<String, String> headers = Collections.emptyMap();
        private NetContentType contentType = NetContentType.BINARY;
        private int statusCode;
        private long totalBytes = -1L;
        private int expectedSequence;
        private boolean started;

        StreamDownload(NetStreamCall call, long deadlineMillis, long maxBytes) {
            this.call = call;
            this.deadlineMillis = deadlineMillis;
            this.maxBytes = maxBytes;
        }

        long getRequestId() {
            return call.getRequestId();
        }

        NetStreamCall getCall() {
            return call;
        }

        long getTotalBytes() {
            return totalBytes;
        }

        long getReceivedBytes() {
            return bytes.size();
        }

        boolean isExpired(long nowMillis) {
            return nowMillis >= deadlineMillis;
        }

        boolean markStarted(NetEnvelope envelope) {
            if (started) {
                return false;
            }
            started = true;
            statusCode = envelope.getStatusCode();
            contentType = envelope.getContentType();
            headers = filterStreamHeaders(envelope.getHeaders());
            totalBytes = parseLongHeader(envelope.getHeaders(), STREAM_TOTAL_BYTES_HEADER, 0L);
            NetPayloadLimits.requireStreamContentSize(totalBytes, maxBytes);
            return true;
        }

        boolean acceptChunk(NetEnvelope envelope) {
            if (!started) {
                return false;
            }
            int sequence = (int) parseLongHeader(envelope.getHeaders(), STREAM_SEQUENCE_HEADER, -1L);
            if (sequence != expectedSequence) {
                return false;
            }
            byte[] payload = envelope.getPayload();
            if (((long) bytes.size()) + payload.length > totalBytes) {
                return false;
            }
            bytes.write(payload, 0, payload.length);
            expectedSequence++;
            return true;
        }

        boolean isComplete() {
            return started && totalBytes >= 0L && ((long) bytes.size()) >= totalBytes;
        }

        Map<String, String> getHeaders() {
            return headers;
        }

        NetContentType getContentType() {
            return contentType;
        }

        int getStatusCode() {
            return statusCode;
        }

        byte[] getBytes() {
            return bytes.toByteArray();
        }

        private Map<String, String> filterStreamHeaders(Map<String, String> source) {
            Map<String, String> filtered = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> entry : source.entrySet()) {
                String key = entry.getKey();
                if (STREAM_TOTAL_BYTES_HEADER.equals(key) || STREAM_CHUNK_COUNT_HEADER.equals(key)
                        || STREAM_SEQUENCE_HEADER.equals(key)) {
                    continue;
                }
                filtered.put(key, entry.getValue());
            }
            return filtered;
        }
    }

    private NetSide targetSideOf(NetTarget target) {
        return target.getType() == NetTarget.Type.SERVER ? NetSide.SERVER : NetSide.CLIENT;
    }

    private static long parseLongHeader(Map<String, String> headers, String name, long defaultValue) {
        String value = headers.get(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    private void ensureRegistrable() {
        if (frozen) {
            throw new IllegalStateException("网络层注册表已冻结");
        }
    }
}
