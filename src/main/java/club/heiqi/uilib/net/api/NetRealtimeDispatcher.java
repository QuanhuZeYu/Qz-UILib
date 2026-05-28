package club.heiqi.uilib.net.api;

import java.util.Map;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.core.NetRealtimeFrame;
import club.heiqi.uilib.net.transport.NetReceiveOrigin;

/**
 * 实时小帧入站分发器。
 */
final class NetRealtimeDispatcher {

    private final NetService service;
    private final Map<String, NetRealtimeChannel> realtimeChannels;

    NetRealtimeDispatcher(NetService service, Map<String, NetRealtimeChannel> realtimeChannels) {
        this.service = service;
        this.realtimeChannels = realtimeChannels;
    }

    /**
     * 分发已解码且方向校验通过的实时帧。
     *
     * @param frame 实时帧
     * @param origin 接收来源
     */
    void dispatch(NetRealtimeFrame frame, NetReceiveOrigin origin) {
        NetRealtimeChannel channel = realtimeChannels.get(frame.getKey());
        if (channel == null) {
            MyMod.LOG.warn("收到未注册 Realtime Channel 帧：{}", frame.getKey());
            return;
        }
        if (frame.getPayload().length > channel.getMaxFrameBytes()) {
            MyMod.LOG.warn("收到超出 Realtime Channel 负载上限的帧：key={} bytes={} limit={}", frame.getKey(),
                    Integer.valueOf(frame.getPayload().length), Integer.valueOf(channel.getMaxFrameBytes()));
            return;
        }
        channel.receive(frame.toMessage(), new NetReceiveContext(service, origin.getSide(), origin.getSender()));
    }
}
