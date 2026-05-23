package club.heiqi.uilib.net.transport;

import java.util.Collections;

/**
 * 网络传输适配器 SPI。
 */
public interface ITransport {

    /**
     * 返回适配器名称。
     *
     * @return 名称
     */
    String getName();

    /**
     * 启动传输适配器。
     *
     * @param frameHandler 入站帧处理器
     */
    void bootstrap(FrameHandler frameHandler);

    /**
     * 关闭传输适配器。
     */
    void shutdown();

    /**
     * 客户端发送到服务端。
     *
     * @param channelName 物理 channel
     * @param payload 数据
     */
    void sendToServer(String channelName, byte[] payload);

    /**
     * 服务端发送到玩家。
     *
     * @param player 玩家对象
     * @param channelName 物理 channel
     * @param payload 数据
     */
    void sendToPlayer(Object player, String channelName, byte[] payload);

    /**
     * 服务端广播到所有玩家。
     *
     * @param channelName 物理 channel
     * @param payload 数据
     */
    void sendToAll(String channelName, byte[] payload);

    /**
     * 服务端发送到指定维度。
     *
     * @param dimensionId 维度 id
     * @param channelName 物理 channel
     * @param payload 数据
     */
    void sendToDimension(int dimensionId, String channelName, byte[] payload);

    /**
     * 返回当前服务端在线玩家快照。
     *
     * <p>仅 Store accessControl 过滤和 per-player/dimension 定向同步需要枚举玩家。
     * 不支持枚举的适配器可返回空集合，此时带访问控制的 Store 不会退回盲目广播。</p>
     *
     * @return 在线玩家快照
     */
    default Iterable<?> getConnectedPlayers() {
        return Collections.emptyList();
    }

    /**
     * 返回玩家所在维度。
     *
     * @param player 玩家对象
     * @return 维度 id；无法识别时返回 null
     */
    default Integer getPlayerDimensionId(Object player) {
        return null;
    }

    /**
     * 返回当前方向物理帧上限。
     *
     * @param targetSide 接收侧
     * @return 单帧字节上限
     */
    int getPhysicalFrameLimit(NetSide targetSide);
}
