package club.heiqi.uilib.net.api;

import club.heiqi.uilib.net.transport.NetSide;

/**
 * 入站消息上下文。
 */
public final class NetReceiveContext {

    private final NetService service;
    private final NetSide side;
    private final Object sender;

    NetReceiveContext(NetService service, NetSide side, Object sender) {
        this.service = service;
        this.side = side;
        this.sender = sender;
    }

    /**
     * 返回当前接收侧。
     *
     * @return 接收侧
     */
    public NetSide getSide() {
        return side;
    }

    /**
     * 返回发送玩家对象。服务端接收 C2S 时通常是 EntityPlayerMP。
     *
     * @return 发送玩家
     */
    public Object getSenderPlayer() {
        return sender;
    }

    /**
     * 返回发送玩家并按调用方需要转换类型。
     *
     * @param <T> 玩家类型
     * @return 发送玩家
     */
    @SuppressWarnings("unchecked")
    public <T> T getSenderPlayerAs() {
        return (T) sender;
    }

    /**
     * 将任务切回当前侧主线程队列。
     *
     * @param runnable 任务
     */
    public void runOnMainThread(Runnable runnable) {
        service.runOnMainThread(side, runnable);
    }
}
