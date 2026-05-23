package club.heiqi.uilib.net.transport.vanilla;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.transport.FrameHandler;
import club.heiqi.uilib.net.transport.ITransport;
import club.heiqi.uilib.net.transport.NetSide;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.IChatComponent;

/**
 * 默认 vanilla custom payload + early mixin 传输适配器。
 */
public final class VanillaMixinTransport implements ITransport {

    @Override
    public String getName() {
        return "vanilla-mixin";
    }

    @Override
    public void bootstrap(FrameHandler frameHandler) {
        VanillaInboundDispatcher.setFrameHandler(frameHandler);
    }

    @Override
    public void shutdown() {
        VanillaInboundDispatcher.setFrameHandler(null);
    }

    @Override
    public void sendToServer(String channelName, byte[] payload) {
        VanillaPacketBuilders.sendToServer(channelName, payload);
    }

    @Override
    public void sendToPlayer(Object player, String channelName, byte[] payload) {
        VanillaPacketBuilders.sendToPlayer(player, channelName, payload);
    }

    @Override
    public void sendToAll(String channelName, byte[] payload) {
        VanillaPacketBuilders.sendToAll(channelName, payload);
    }

    @Override
    public void sendToDimension(int dimensionId, String channelName, byte[] payload) {
        VanillaPacketBuilders.sendToDimension(dimensionId, channelName, payload);
    }

    @Override
    public int getPhysicalFrameLimit(NetSide targetSide) {
        if (targetSide == NetSide.SERVER) {
            return NetPayloadLimits.COMPAT_PHYSICAL_FRAME_LIMIT;
        }
        return NetPayloadLimits.GTNH_DEFAULT_PHYSICAL_LIMIT;
    }

    /**
     * 客户端 NetHandler 构造完成。
     *
     * @param networkManager 网络管理器
     */
    public static void onClientHandshakeReady(NetworkManager networkManager) {
        MyMod.LOG.debug("Qz vanilla transport client handshake ready: {}", networkManager);
        NetService.getInstance().sendSchemaHandshakeToServer();
    }

    /**
     * 客户端断连。
     *
     * @param reason 断连原因
     */
    public static void onClientDisconnected(IChatComponent reason) {
        MyMod.LOG.debug("Qz vanilla transport client disconnected: {}", reason);
        NetService.getInstance().onClientDisconnected();
    }

    /**
     * 服务端玩家连接就绪。
     *
     * @param player 玩家
     */
    public static void onServerPlayerJoined(EntityPlayerMP player) {
        MyMod.LOG.debug("Qz vanilla transport server player joined: {}", player);
        NetService.getInstance().sendSchemaHandshakeToPlayer(player);
    }

    /**
     * 服务端玩家离开。
     *
     * @param player 玩家
     */
    public static void onServerPlayerLeft(EntityPlayerMP player) {
        MyMod.LOG.debug("Qz vanilla transport server player left: {}", player);
    }

    /**
     * 客户端收到 custom payload。
     *
     * @param channelName channel 名
     * @param payload payload
     * @return true 表示已处理
     */
    public static boolean onClientCustomPayload(String channelName, byte[] payload) {
        return VanillaInboundDispatcher.dispatchClient(channelName, payload);
    }

    /**
     * 服务端收到 custom payload。
     *
     * @param player 玩家
     * @param channelName channel 名
     * @param payload payload
     * @return true 表示已处理
     */
    public static boolean onServerCustomPayload(EntityPlayerMP player, String channelName, byte[] payload) {
        return VanillaInboundDispatcher.dispatchServer(player, channelName, payload);
    }
}
