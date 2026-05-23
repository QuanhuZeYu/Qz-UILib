package club.heiqi.uilib.net.api;

import java.util.Objects;

/**
 * 双向消息 Channel。
 *
 * @param <T> 消息类型
 */
public final class NetChannel<T> {

    private final NetService service;
    private final NetChannelId id;
    private final Class<T> messageType;
    private final NetChannelHandler<T> handler;

    NetChannel(NetService service, NetChannelId id, Class<T> messageType, NetChannelHandler<T> handler) {
        this.service = service;
        this.id = id;
        this.messageType = messageType;
        this.handler = handler;
    }

    public NetChannelId getId() {
        return id;
    }

    public Class<T> getMessageType() {
        return messageType;
    }

    /**
     * 创建发往服务端的发送器。
     *
     * @return 发送器
     */
    public Sender<T> toServer() {
        return new Sender<T>(this, NetTarget.server());
    }

    /**
     * 创建发往单个玩家的发送器。
     *
     * @param player 玩家对象
     * @return 发送器
     */
    public Sender<T> toPlayer(Object player) {
        return new Sender<T>(this, NetTarget.player(player));
    }

    /**
     * 创建发往多个玩家的发送器。
     *
     * @param players 玩家集合
     * @return 发送器
     */
    public Sender<T> toPlayers(Iterable<?> players) {
        return new Sender<T>(this, NetTarget.players(players));
    }

    /**
     * 创建广播发送器。
     *
     * @return 发送器
     */
    public Sender<T> toAll() {
        return new Sender<T>(this, NetTarget.all());
    }

    /**
     * 创建发往维度的发送器。
     *
     * @param dimensionId 维度 id
     * @return 发送器
     */
    public Sender<T> toDimension(int dimensionId) {
        return new Sender<T>(this, NetTarget.dimension(dimensionId));
    }

    void receive(T message, NetReceiveContext context) {
        if (handler != null) {
            handler.onReceive(message, context);
        }
    }

    private void send(NetTarget target, T message) {
        service.sendChannelMessage(this, target, message);
    }

    /**
     * Channel 注册构造器。
     *
     * @param <T> 消息类型
     */
    public static final class Builder<T> {

        private final NetService service;
        private final NetChannelId id;
        private final Class<T> messageType;
        private NetChannelHandler<T> handler;

        Builder(NetService service, NetChannelId id, Class<T> messageType) {
            this.service = service;
            this.id = id;
            this.messageType = messageType;
        }

        /**
         * 设置接收回调。
         *
         * @param handler 接收回调
         * @return 构造器
         */
        public Builder<T> onReceive(NetChannelHandler<T> handler) {
            this.handler = handler;
            return this;
        }

        /**
         * 注册 Channel。
         *
         * @return Channel
         */
        public NetChannel<T> register() {
            return service.registerChannel(new NetChannel<T>(service, id, messageType, handler));
        }
    }

    /**
     * 定向发送器。
     *
     * @param <T> 消息类型
     */
    public static final class Sender<T> {

        private final NetChannel<T> channel;
        private final NetTarget target;

        private Sender(NetChannel<T> channel, NetTarget target) {
            this.channel = channel;
            this.target = target;
        }

        /**
         * 发送消息。
         *
         * @param message 消息
         */
        public void send(T message) {
            channel.send(target, Objects.requireNonNull(message, "message"));
        }
    }

    /**
     * Channel 接收回调。
     *
     * @param <T> 消息类型
     */
    public interface NetChannelHandler<T> {

        /**
         * 处理消息。
         *
         * @param message 消息
         * @param context 上下文
         */
        void onReceive(T message, NetReceiveContext context);
    }
}
