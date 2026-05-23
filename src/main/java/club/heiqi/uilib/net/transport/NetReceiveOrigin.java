package club.heiqi.uilib.net.transport;

/**
 * 传输层接收来源。
 */
public final class NetReceiveOrigin {

    private final NetSide side;
    private final Object sender;

    private NetReceiveOrigin(NetSide side, Object sender) {
        this.side = side;
        this.sender = sender;
    }

    /**
     * 创建客户端接收来源。
     *
     * @return 来源
     */
    public static NetReceiveOrigin client() {
        return new NetReceiveOrigin(NetSide.CLIENT, null);
    }

    /**
     * 创建服务端接收来源。
     *
     * @param sender 发送玩家
     * @return 来源
     */
    public static NetReceiveOrigin server(Object sender) {
        return new NetReceiveOrigin(NetSide.SERVER, sender);
    }

    /**
     * 返回当前接收侧。
     *
     * @return 网络侧
     */
    public NetSide getSide() {
        return side;
    }

    /**
     * 返回发送玩家。客户端接收服务端包时为 null。
     *
     * @return 发送者
     */
    public Object getSender() {
        return sender;
    }
}
