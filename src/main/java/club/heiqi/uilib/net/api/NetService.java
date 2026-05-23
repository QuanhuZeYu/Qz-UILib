package club.heiqi.uilib.net.api;

import java.nio.charset.StandardCharsets;
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
    private static final NetService INSTANCE = new NetService();

    private final Map<String, NetChannel> channels = new ConcurrentHashMap<String, NetChannel>();
    private final Map<String, NetFetchEndpoint> fetchEndpoints = new ConcurrentHashMap<String, NetFetchEndpoint>();
    private final Map<String, NetStore> stores = new ConcurrentHashMap<String, NetStore>();
    private final NetRequestRegistry requestRegistry = new NetRequestRegistry();
    private final NetChunkAssembler chunkAssembler = new NetChunkAssembler();
    private final AtomicLong nextChunkStreamId = new AtomicLong(1L);
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
        MyMod.LOG.info("Qz 网络层注册表已冻结：channels={} fetchEndpoints={} stores={}", channels.size(),
                fetchEndpoints.size(), stores.size());
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
        sendEnvelope(NetTarget.server(), NetEnvelope.of(NetEnvelope.Kind.FETCH_REQUEST, NetSide.SERVER,
                endpoint.getId().asKey(), pending.getRequestId(), 0, request.getHeaders(), request.getBody()));
        return pending.getFuture();
    }

    void replyFetch(Object player, String endpointKey, long requestId, NetResponse response) {
        sendEnvelope(NetTarget.player(player), NetEnvelope.of(NetEnvelope.Kind.FETCH_RESPONSE, NetSide.CLIENT,
                endpointKey, requestId, response.getStatusCode(), response.getHeaders(), response.getBody()));
    }

    void failFetch(Object player, String endpointKey, long requestId, Throwable throwable) {
        String message = throwable == null ? "远端处理失败" : throwable.getClass().getSimpleName() + ": "
                + (throwable.getMessage() == null ? "" : throwable.getMessage());
        replyFetch(player, endpointKey, requestId, NetResponse.error(500, message));
    }

    void sendStoreSnapshot(NetStore store, NetTarget target, NetBody snapshot) {
        sendEnvelope(target, NetEnvelope.of(NetEnvelope.Kind.STORE_SNAPSHOT, targetSideOf(target),
                store.getId().asKey(), 0L, 0, Collections.<String, String>emptyMap(), snapshot));
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
        chunkAssembler.clear();
    }

    /**
     * 关闭网络层。
     */
    public synchronized void shutdown() {
        requestRegistry.failAllDisconnected();
        chunkAssembler.clear();
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
        stores.clear();
        requestRegistry.failAllDisconnected();
        chunkAssembler.clear();
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
            endpoint.receiveRequest(NetRequest.fromWire(envelope.getHeaders(), envelope.toBody()),
                    new NetFetchEndpoint.NetFetchRequestContext(this, envelope.getKey(), envelope.getRequestId(),
                            new NetReceiveContext(this, origin.getSide(), origin.getSender()), origin.getSender()));
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
        if (envelope.getKind() == NetEnvelope.Kind.STORE_SNAPSHOT || envelope.getKind() == NetEnvelope.Kind.STORE_DELTA) {
            NetStore store = stores.get(envelope.getKey());
            if (store == null) {
                MyMod.LOG.warn("收到未注册 Store 帧：{}", envelope.getKey());
                return;
            }
            store.receiveSnapshot(envelope.toBody());
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

    private NetSide targetSideOf(NetTarget target) {
        return target.getType() == NetTarget.Type.SERVER ? NetSide.SERVER : NetSide.CLIENT;
    }

    private void ensureRegistrable() {
        if (frozen) {
            throw new IllegalStateException("网络层注册表已冻结");
        }
    }
}
