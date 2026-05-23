package club.heiqi.uilib.net.transport.vanilla;

import club.heiqi.uilib.net.transport.FrameHandler;
import club.heiqi.uilib.net.transport.NetReceiveOrigin;

/**
 * early mixin 与传输适配器之间的入站分发器。
 */
public final class VanillaInboundDispatcher {

    private static volatile FrameHandler frameHandler;

    private VanillaInboundDispatcher() {}

    /**
     * 设置入站处理器。
     *
     * @param handler 处理器
     */
    public static void setFrameHandler(FrameHandler handler) {
        frameHandler = handler;
    }

    /**
     * 判断是否为 Qz 物理 channel。
     *
     * @param channelName channel 名
     * @return true 表示 Qz channel
     */
    public static boolean isQzChannel(String channelName) {
        return channelName != null && channelName.startsWith("qz:");
    }

    /**
     * 分发客户端收到的 payload。
     *
     * @param channelName channel 名
     * @param payload payload
     * @return true 表示已处理
     */
    public static boolean dispatchClient(String channelName, byte[] payload) {
        if (!isQzChannel(channelName)) {
            return false;
        }
        FrameHandler handler = frameHandler;
        if (handler != null) {
            handler.handleFrame(channelName, payload, NetReceiveOrigin.client());
        }
        return true;
    }

    /**
     * 分发服务端收到的 payload。
     *
     * @param sender 发送玩家
     * @param channelName channel 名
     * @param payload payload
     * @return true 表示已处理
     */
    public static boolean dispatchServer(Object sender, String channelName, byte[] payload) {
        if (!isQzChannel(channelName)) {
            return false;
        }
        FrameHandler handler = frameHandler;
        if (handler != null) {
            handler.handleFrame(channelName, payload, NetReceiveOrigin.server(sender));
        }
        return true;
    }
}
