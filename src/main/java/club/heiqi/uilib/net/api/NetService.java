package club.heiqi.uilib.net.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.core.MainThreadDispatcher;
import club.heiqi.uilib.net.core.NetChunkAssembler;
import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.core.NetOutboundScheduler;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.core.NetRealtimeFrame;
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
    private static final NetService INSTANCE = new NetService();

    private final Map<String, NetChannel> channels = new ConcurrentHashMap<String, NetChannel>();
    private final Map<String, NetRealtimeChannel> realtimeChannels = new ConcurrentHashMap<String, NetRealtimeChannel>();
    private final Map<String, NetFetchEndpoint> fetchEndpoints = new ConcurrentHashMap<String, NetFetchEndpoint>();
    private final Map<String, NetStreamEndpoint> streamEndpoints = new ConcurrentHashMap<String, NetStreamEndpoint>();
    private final Map<String, NetStore> stores = new ConcurrentHashMap<String, NetStore>();
    private final NetRequestRegistry requestRegistry = new NetRequestRegistry();
    private final NetChunkAssembler chunkAssembler = new NetChunkAssembler();
    private final AtomicLong nextChunkStreamId = new AtomicLong(1L);
    private final NetStreamDownloadRegistry streamDownloads = new NetStreamDownloadRegistry(this, streamEndpoints);
    private final NetStoreSender storeSender = new NetStoreSender(this);
    private final NetEnvelopeDispatcher envelopeDispatcher = new NetEnvelopeDispatcher(this, channels, fetchEndpoints,
            streamEndpoints, stores, requestRegistry, streamDownloads);
    private final NetRealtimeDispatcher realtimeDispatcher = new NetRealtimeDispatcher(this, realtimeChannels);
    private final NetOutboundScheduler outboundScheduler = new NetOutboundScheduler();
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
        MyMod.LOG.info("Qz 网络层注册表已冻结：channels={} realtimeChannels={} fetchEndpoints={} streamEndpoints={} stores={}",
                channels.size(), realtimeChannels.size(), fetchEndpoints.size(), streamEndpoints.size(), stores.size());
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
     * 创建实时 Channel 构造器。
     *
     * @param id Channel id
     * @return 构造器
     */
    public NetRealtimeChannel.Builder realtime(NetRealtimeChannelId id) {
        return new NetRealtimeChannel.Builder(this, id);
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

    synchronized NetRealtimeChannel registerRealtimeChannel(NetRealtimeChannel channel) {
        ensureRegistrable();
        String key = channel.getId().asKey();
        if (realtimeChannels.putIfAbsent(key, channel) != null) {
            throw new IllegalStateException("Realtime Channel 已注册: " + key);
        }
        return channel;
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

    void sendRealtimeMessage(NetRealtimeChannel channel, NetTarget target, NetRealtimeMessage message) {
        if (message.size() > channel.getMaxFrameBytes()) {
            throw new IllegalArgumentException("Realtime 帧超过负载上限: " + message.size() + " > "
                    + channel.getMaxFrameBytes());
        }
        NetRealtimeFrame frame = NetRealtimeFrame.of(targetSideOf(target), channel.getId().asKey(), message);
        byte[] encoded = frame.encode();
        int physicalLimit = requireTransport().getPhysicalFrameLimit(frame.getTargetSide());
        if (encoded.length > physicalLimit) {
            throw new IllegalStateException("Realtime 帧超过物理上限：" + encoded.length + " > " + physicalLimit);
        }
        long expireAtMillis = System.currentTimeMillis() + channel.getMaxLatencyMillis();
        enqueueSend(target, encoded, NetOutboundScheduler.Priority.REALTIME, realtimeQueueKey(target, channel),
                channel.getMaxQueuedFrames(), channel.getDropPolicy(), expireAtMillis);
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
        return streamDownloads.callEndpoint(endpoint, request);
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
        streamDownloads.reply(player, endpointKey, requestId, response);
    }

    void failStream(Object player, String endpointKey, long requestId, Throwable throwable) {
        streamDownloads.fail(player, endpointKey, requestId, throwable);
    }

    boolean isStreamCancelled(long requestId) {
        return streamDownloads.isCancelled(requestId);
    }

    void cancelStreamCall(String endpointKey, long requestId) {
        streamDownloads.cancelCall(endpointKey, requestId);
    }

    void sendStoreSnapshot(NetStore store, NetTarget target, NetBody snapshot) {
        storeSender.send(store, target, NetEnvelope.of(NetEnvelope.Kind.STORE_SNAPSHOT, targetSideOf(target),
                store.getId().asKey(), 0L, 0, Collections.<String, String>emptyMap(), snapshot));
    }

    void sendStoreDelta(NetStore store, NetTarget target, NetBody delta) {
        storeSender.send(store, target, NetEnvelope.of(NetEnvelope.Kind.STORE_DELTA, targetSideOf(target),
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
        streamDownloads.failAll(new NetDisconnectedException("网络连接已断开"));
        chunkAssembler.clear();
        streamDownloads.clearRemoteCancelled();
    }

    /**
     * 推进通用网络请求超时。
     *
     * <p>该 tick 只处理 Fetch 与 Stream 的 deadline，不处理任何业务层生命周期。</p>
     */
    public void tickTimeouts() {
        requestRegistry.expireTimedOut();
        streamDownloads.expireTimedOut();
    }

    /**
     * 关闭网络层。
     */
    public synchronized void shutdown() {
        requestRegistry.failAllDisconnected();
        streamDownloads.failAll(new NetDisconnectedException("网络连接已断开"));
        chunkAssembler.clear();
        streamDownloads.clearRemoteCancelled();
        outboundScheduler.clear();
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
        realtimeChannels.clear();
        fetchEndpoints.clear();
        streamEndpoints.clear();
        stores.clear();
        requestRegistry.failAllDisconnected();
        streamDownloads.failAll(new NetDisconnectedException("网络连接已断开"));
        chunkAssembler.clear();
        streamDownloads.clearRemoteCancelled();
        outboundScheduler.clear();
        frozen = false;
        transport = null;
    }

    private void sendMeta(NetTarget target, NetSide targetSide) {
        String json = "{\"protocol\":2,\"contentTypes\":[\"application/json\",\"application/octet-stream\","
                + "\"text/plain; charset=utf-8\"],\"ordinaryLogicalLimit\":"
                + NetPayloadLimits.DEFAULT_LOGICAL_MESSAGE_LIMIT
                + ",\"features\":[\"realtime-channel-v1\"]";
        json += "}";
        sendEnvelope(target, NetEnvelope.of(NetEnvelope.Kind.META, targetSide, META_KEY, 0L, 0,
                Collections.<String, String>emptyMap(), NetBody.json(json)));
    }

    private void handleInboundFrame(String channelName, byte[] payload, NetReceiveOrigin origin) {
        try {
            if (NetRealtimeFrame.hasMagic(payload)) {
                NetRealtimeFrame realtimeFrame = NetRealtimeFrame.decode(payload);
                if (realtimeFrame.getTargetSide() != origin.getSide()) {
                    MyMod.LOG.warn("丢弃方向不匹配的 Qz 实时帧：channel={} key={} expectedSide={} actualSide={}",
                            channelName, realtimeFrame.getKey(), realtimeFrame.getTargetSide(), origin.getSide());
                    return;
                }
                realtimeDispatcher.dispatch(realtimeFrame, origin);
                return;
            }
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
            envelopeDispatcher.dispatch(envelope, origin);
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("Qz 网络帧处理失败，channel={}", channelName, exception);
        }
    }

    void sendEnvelope(NetTarget target, NetEnvelope envelope) {
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
            enqueueSend(target, encoded, NetOutboundScheduler.Priority.CONTROL, null, 0,
                    NetRealtimeDropPolicy.DROP_NEWEST, 0L);
            return;
        }
        sendChunked(target, envelope.getTargetSide(), encoded, physicalLimit);
    }

    void sendStreamEnvelope(NetTarget target, NetEnvelope envelope) {
        ITransport activeTransport = requireTransport();
        byte[] encoded = envelope.encode();
        int physicalLimit = activeTransport.getPhysicalFrameLimit(envelope.getTargetSide());
        if (encoded.length > physicalLimit) {
            throw new IllegalStateException("Stream 帧超过物理上限：" + encoded.length + " > " + physicalLimit);
        }
        enqueueSend(target, encoded, NetOutboundScheduler.Priority.BULK, null, 0,
                NetRealtimeDropPolicy.DROP_NEWEST, 0L);
    }

    ITransport requireTransport() {
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
            enqueueSend(target, chunkBytes, NetOutboundScheduler.Priority.BULK, null, 0,
                    NetRealtimeDropPolicy.DROP_NEWEST, 0L);
        }
    }

    private void enqueueSend(final NetTarget target, final byte[] payload, NetOutboundScheduler.Priority priority,
            String realtimeQueueKey, int maxQueuedFrames, NetRealtimeDropPolicy dropPolicy, long expireAtMillis) {
        Runnable dispatch = new Runnable() {
            @Override
            public void run() {
                sendPhysicalNow(target, payload);
            }
        };
        if (priority == NetOutboundScheduler.Priority.REALTIME) {
            outboundScheduler.enqueueRealtime(realtimeQueueKey, maxQueuedFrames, dropPolicy, expireAtMillis, dispatch);
            return;
        }
        outboundScheduler.enqueue(priority, dispatch);
    }

    private String realtimeQueueKey(NetTarget target, NetRealtimeChannel channel) {
        StringBuilder builder = new StringBuilder(channel.getId().asKey());
        builder.append('|').append(target.getType().name());
        switch (target.getType()) {
            case SERVER:
                return builder.toString();
            case PLAYER:
                return builder.append('|').append(System.identityHashCode(target.getPlayer())).toString();
            case PLAYERS:
                return builder.append('|').append(System.identityHashCode(target.getPlayers())).toString();
            case ALL:
                return builder.toString();
            case DIMENSION:
                return builder.append('|').append(target.getDimensionId()).toString();
            default:
                return builder.toString();
        }
    }

    private void sendPhysicalNow(NetTarget target, byte[] payload) {
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

    private NetSide targetSideOf(NetTarget target) {
        return target.getType() == NetTarget.Type.SERVER ? NetSide.SERVER : NetSide.CLIENT;
    }

    private void ensureRegistrable() {
        if (frozen) {
            throw new IllegalStateException("网络层注册表已冻结");
        }
    }
}
