package club.heiqi.uilib.net.api;

import java.time.Duration;
import java.util.Objects;

/**
 * 实验性的低延迟实时 Channel。
 *
 * <p>该 Channel 面向高频小二进制帧，优先强调新鲜度而不是完整送达。
 * 它仍运行在 Minecraft 现有 TCP/custom payload 底座之上，不等价于 UDP 级实时媒体链路。</p>
 */
public final class NetRealtimeChannel {

    private static final int DEFAULT_MAX_FRAME_BYTES = 1200;
    private static final int DEFAULT_MAX_QUEUED_FRAMES = 3;
    private static final long DEFAULT_MAX_LATENCY_MILLIS = 250L;

    private final NetService service;
    private final NetRealtimeChannelId id;
    private final int maxFrameBytes;
    private final int maxQueuedFrames;
    private final long maxLatencyMillis;
    private final NetRealtimeDropPolicy dropPolicy;
    private final NetRealtimeHandler handler;

    NetRealtimeChannel(NetService service, NetRealtimeChannelId id, int maxFrameBytes, int maxQueuedFrames,
            long maxLatencyMillis, NetRealtimeDropPolicy dropPolicy, NetRealtimeHandler handler) {
        this.service = service;
        this.id = id;
        this.maxFrameBytes = requirePositive("maxFrameBytes", maxFrameBytes);
        this.maxQueuedFrames = requirePositive("maxQueuedFrames", maxQueuedFrames);
        this.maxLatencyMillis = requirePositive("maxLatencyMillis", maxLatencyMillis);
        this.dropPolicy = Objects.requireNonNull(dropPolicy, "dropPolicy");
        this.handler = handler;
    }

    public NetRealtimeChannelId getId() {
        return id;
    }

    int getMaxFrameBytes() {
        return maxFrameBytes;
    }

    int getMaxQueuedFrames() {
        return maxQueuedFrames;
    }

    long getMaxLatencyMillis() {
        return maxLatencyMillis;
    }

    NetRealtimeDropPolicy getDropPolicy() {
        return dropPolicy;
    }

    /**
     * 创建发往服务端的发送器。
     *
     * @return 发送器
     */
    public Sender toServer() {
        return new Sender(this, NetTarget.server());
    }

    /**
     * 创建发往单个玩家的发送器。
     *
     * @param player 玩家对象
     * @return 发送器
     */
    public Sender toPlayer(Object player) {
        return new Sender(this, NetTarget.player(player));
    }

    /**
     * 创建发往多个玩家的发送器。
     *
     * @param players 玩家集合
     * @return 发送器
     */
    public Sender toPlayers(Iterable<?> players) {
        return new Sender(this, NetTarget.players(players));
    }

    /**
     * 创建广播发送器。
     *
     * @return 发送器
     */
    public Sender toAll() {
        return new Sender(this, NetTarget.all());
    }

    /**
     * 创建发往维度的发送器。
     *
     * @param dimensionId 维度 id
     * @return 发送器
     */
    public Sender toDimension(int dimensionId) {
        return new Sender(this, NetTarget.dimension(dimensionId));
    }

    void receive(NetRealtimeMessage message, NetReceiveContext context) {
        if (handler != null) {
            handler.onReceive(message, context);
        }
    }

    private void send(NetTarget target, NetRealtimeMessage message) {
        service.sendRealtimeMessage(this, target, message);
    }

    /**
     * 实时 Channel 注册构造器。
     */
    public static final class Builder {

        private final NetService service;
        private final NetRealtimeChannelId id;
        private int maxFrameBytes = DEFAULT_MAX_FRAME_BYTES;
        private int maxQueuedFrames = DEFAULT_MAX_QUEUED_FRAMES;
        private long maxLatencyMillis = DEFAULT_MAX_LATENCY_MILLIS;
        private NetRealtimeDropPolicy dropPolicy = NetRealtimeDropPolicy.DROP_OLDEST;
        private NetRealtimeHandler handler;

        Builder(NetService service, NetRealtimeChannelId id) {
            this.service = service;
            this.id = id;
        }

        /**
         * 设置单帧业务负载大小上限。
         *
         * @param maxFrameBytes 最大负载字节数
         * @return 构造器
         */
        public Builder maxFrameBytes(int maxFrameBytes) {
            this.maxFrameBytes = requirePositive("maxFrameBytes", maxFrameBytes);
            return this;
        }

        /**
         * 设置同一逻辑 lane 允许排队的最大帧数。
         *
         * @param maxQueuedFrames 最大排队帧数
         * @return 构造器
         */
        public Builder maxQueuedFrames(int maxQueuedFrames) {
            this.maxQueuedFrames = requirePositive("maxQueuedFrames", maxQueuedFrames);
            return this;
        }

        /**
         * 设置帧的最大排队存活时间。
         *
         * @param maxLatency 最大排队时长
         * @return 构造器
         */
        public Builder maxLatency(Duration maxLatency) {
            this.maxLatencyMillis = requirePositive("maxLatencyMillis",
                    Objects.requireNonNull(maxLatency, "maxLatency").toMillis());
            return this;
        }

        /**
         * 设置队列满时的丢弃策略。
         *
         * @param dropPolicy 丢弃策略
         * @return 构造器
         */
        public Builder dropPolicy(NetRealtimeDropPolicy dropPolicy) {
            this.dropPolicy = Objects.requireNonNull(dropPolicy, "dropPolicy");
            return this;
        }

        /**
         * 设置接收回调。
         *
         * @param handler 接收回调
         * @return 构造器
         */
        public Builder onReceive(NetRealtimeHandler handler) {
            this.handler = handler;
            return this;
        }

        /**
         * 注册实时 Channel。
         *
         * @return Channel
         */
        public NetRealtimeChannel register() {
            return service.registerRealtimeChannel(new NetRealtimeChannel(service, id, maxFrameBytes,
                    maxQueuedFrames, maxLatencyMillis, dropPolicy, handler));
        }
    }

    /**
     * 实时帧发送器。
     */
    public static final class Sender {

        private final NetRealtimeChannel channel;
        private final NetTarget target;

        private Sender(NetRealtimeChannel channel, NetTarget target) {
            this.channel = channel;
            this.target = target;
        }

        /**
         * 发送完整实时帧。
         *
         * @param message 实时帧
         */
        public void send(NetRealtimeMessage message) {
            channel.send(target, Objects.requireNonNull(message, "message"));
        }

        /**
         * 发送实时二进制负载。
         *
         * @param streamId 业务流 id
         * @param sequence 帧序号
         * @param timestampMillis 业务时间戳
         * @param payload 负载
         */
        public void sendFrame(long streamId, int sequence, long timestampMillis, byte[] payload) {
            send(NetRealtimeMessage.of(streamId, sequence, timestampMillis, payload));
        }
    }

    /**
     * 实时帧接收回调。
     */
    public interface NetRealtimeHandler {

        /**
         * 处理实时帧。
         *
         * @param message 实时帧
         * @param context 上下文
         */
        void onReceive(NetRealtimeMessage message, NetReceiveContext context);
    }

    private static int requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static long requirePositive(String name, long value) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
