package club.heiqi.uilib.net.api;

import java.util.Objects;

/**
 * 双向内容语义 Channel。
 */
public final class NetChannel {

    private final NetService service;
    private final NetChannelId id;
    private final NetChannelHandler handler;

    NetChannel(NetService service, NetChannelId id, NetChannelHandler handler) {
        this.service = service;
        this.id = id;
        this.handler = handler;
    }

    public NetChannelId getId() {
        return id;
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

    void receive(NetMessage message, NetReceiveContext context) {
        if (handler != null) {
            handler.onReceive(message, context);
        }
    }

    private void send(NetTarget target, NetMessage message) {
        service.sendChannelMessage(this, target, message);
    }

    /**
     * Channel 注册构造器。
     */
    public static final class Builder {

        private final NetService service;
        private final NetChannelId id;
        private NetChannelHandler handler;

        Builder(NetService service, NetChannelId id) {
            this.service = service;
            this.id = id;
        }

        /**
         * 设置接收回调。
         *
         * @param handler 接收回调
         * @return 构造器
         */
        public Builder onReceive(NetChannelHandler handler) {
            this.handler = handler;
            return this;
        }

        /**
         * 注册 Channel。
         *
         * @return Channel
         */
        public NetChannel register() {
            return service.registerChannel(new NetChannel(service, id, handler));
        }
    }

    /**
     * 定向发送器。
     */
    public static final class Sender {

        private final NetChannel channel;
        private final NetTarget target;

        private Sender(NetChannel channel, NetTarget target) {
            this.channel = channel;
            this.target = target;
        }

        /**
         * 发送完整消息。
         *
         * @param message 消息
         */
        public void send(NetMessage message) {
            channel.send(target, Objects.requireNonNull(message, "message"));
        }

        /**
         * 发送 body。
         *
         * @param body body
         */
        public void send(NetBody body) {
            send(NetMessage.of(body));
        }

        /**
         * 发送 JSON 文本。
         *
         * @param json JSON 文本
         */
        public void sendJson(String json) {
            send(NetMessage.json(json));
        }

        /**
         * 发送二进制数据。
         *
         * @param bytes 字节
         */
        public void sendBinary(byte[] bytes) {
            send(NetMessage.binary(bytes));
        }
    }

    /**
     * Channel 接收回调。
     */
    public interface NetChannelHandler {

        /**
         * 处理消息。
         *
         * @param message 消息
         * @param context 上下文
         */
        void onReceive(NetMessage message, NetReceiveContext context);
    }
}
