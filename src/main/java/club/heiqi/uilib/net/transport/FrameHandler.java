package club.heiqi.uilib.net.transport;

/**
 * 传输层入站帧处理器。
 */
public interface FrameHandler {

    /**
     * 处理传输层收到的原始帧。
     *
     * @param channelName vanilla/FML channel 名
     * @param payload 帧内容
     * @param origin 接收来源
     */
    void handleFrame(String channelName, byte[] payload, NetReceiveOrigin origin);
}
