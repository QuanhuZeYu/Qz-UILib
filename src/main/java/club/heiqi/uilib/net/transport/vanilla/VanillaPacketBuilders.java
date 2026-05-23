package club.heiqi.uilib.net.transport.vanilla;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.server.MinecraftServer;

/**
 * vanilla custom payload 包构造与发送工具。
 */
public final class VanillaPacketBuilders {

    private static volatile NetworkManager clientNetworkManager;

    private VanillaPacketBuilders() {}

    /**
     * 记录客户端 NetHandler 构造期已可用的 NetworkManager。
     *
     * @param networkManager 网络管理器
     */
    static void rememberClientNetworkManager(NetworkManager networkManager) {
        clientNetworkManager = Objects.requireNonNull(networkManager, "networkManager");
    }

    /**
     * 清理已记录的客户端 NetworkManager。
     */
    static void clearClientNetworkManager() {
        clientNetworkManager = null;
    }

    /**
     * 客户端发送到服务端。
     *
     * @param channelName channel 名
     * @param payload 数据
     */
    public static void sendToServer(String channelName, byte[] payload) {
        NetworkManager networkManager = resolveClientNetworkManager();
        networkManager.scheduleOutboundPacket(new C17PacketCustomPayload(channelName, payload));
    }

    /**
     * 服务端发送到玩家。
     *
     * @param player 玩家对象
     * @param channelName channel 名
     * @param payload 数据
     */
    public static void sendToPlayer(Object player, String channelName, byte[] payload) {
        EntityPlayerMP entityPlayer = requirePlayer(player);
        entityPlayer.playerNetServerHandler.netManager
                .scheduleOutboundPacket(new S3FPacketCustomPayload(channelName, payload));
    }

    /**
     * 服务端广播。
     *
     * @param channelName channel 名
     * @param payload 数据
     */
    public static void sendToAll(String channelName, byte[] payload) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        List<?> players = server.getConfigurationManager().playerEntityList;
        for (Object player : players) {
            sendToPlayer(player, channelName, payload);
        }
    }

    /**
     * 服务端按维度发送。
     *
     * @param dimensionId 维度 id
     * @param channelName channel 名
     * @param payload 数据
     */
    public static void sendToDimension(int dimensionId, String channelName, byte[] payload) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        List<?> players = server.getConfigurationManager().playerEntityList;
        for (Object player : players) {
            EntityPlayerMP entityPlayer = requirePlayer(player);
            if (entityPlayer.dimension == dimensionId) {
                sendToPlayer(entityPlayer, channelName, payload);
            }
        }
    }

    private static EntityPlayerMP requirePlayer(Object player) {
        if (!(player instanceof EntityPlayerMP)) {
            throw new IllegalArgumentException("需要 EntityPlayerMP 作为发送目标: " + player);
        }
        return (EntityPlayerMP) player;
    }

    private static NetworkManager resolveClientNetworkManager() {
        NetworkManager rememberedNetworkManager = clientNetworkManager;
        if (rememberedNetworkManager != null) {
            return rememberedNetworkManager;
        }
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Method getMinecraft = minecraftClass.getMethod("getMinecraft");
            Object minecraft = getMinecraft.invoke(null);
            if (minecraft == null) {
                throw new IllegalStateException("Minecraft 客户端实例为空");
            }
            Method getNetHandler = minecraftClass.getMethod("getNetHandler");
            Object netHandler = getNetHandler.invoke(minecraft);
            if (netHandler == null) {
                throw new IllegalStateException("客户端 NetHandler 尚未建立");
            }
            Method getNetworkManager = netHandler.getClass().getMethod("getNetworkManager");
            Object networkManager = getNetworkManager.invoke(netHandler);
            if (!(networkManager instanceof NetworkManager)) {
                throw new IllegalStateException("客户端 NetworkManager 类型异常: " + networkManager);
            }
            return (NetworkManager) networkManager;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法解析客户端 NetworkManager", exception);
        }
    }
}
