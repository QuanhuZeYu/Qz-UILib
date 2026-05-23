package club.heiqi.uilib.net.transport.vanilla;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.transport.NetSide;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;

/**
 * vanilla transport 的 FML 连接生命周期桥。
 */
public final class VanillaConnectionLifecycle {

    private static final VanillaConnectionLifecycle INSTANCE = new VanillaConnectionLifecycle();

    private final Set<NetworkManager> clientHandshakeManagers =
            Collections.newSetFromMap(new ConcurrentHashMap<NetworkManager, Boolean>());
    private final Set<NetworkManager> serverHandshakeManagers =
            Collections.newSetFromMap(new ConcurrentHashMap<NetworkManager, Boolean>());
    private volatile boolean registered;

    private VanillaConnectionLifecycle() {}

    /**
     * 返回单例生命周期桥。
     *
     * @return 单例
     */
    public static VanillaConnectionLifecycle getInstance() {
        return INSTANCE;
    }

    /**
     * 注册到 FML 事件总线。
     */
    public synchronized void register() {
        if (registered) {
            return;
        }
        FMLCommonHandler.instance().bus().register(this);
        registered = true;
    }

    /**
     * 从 FML 事件总线注销并清理连接状态。
     */
    public synchronized void unregister() {
        if (registered) {
            FMLCommonHandler.instance().bus().unregister(this);
            registered = false;
        }
        clear();
    }

    /**
     * 客户端 FML 握手完成后发送 Qz 能力握手。
     *
     * @param event FML 客户端连接事件
     */
    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        onClientConnectionEstablished(event.manager);
    }

    /**
     * 服务端 FML 握手完成后安排 Qz 能力握手。
     *
     * @param event FML 服务端连接事件
     */
    @SubscribeEvent
    public void onServerConnected(FMLNetworkEvent.ServerConnectionFromClientEvent event) {
        EntityPlayerMP player = resolveServerPlayer(event.handler);
        if (player == null) {
            MyMod.LOG.warn("Qz vanilla transport 无法解析服务端连接玩家，跳过能力握手：handler={}",
                    String.valueOf(event.handler));
            return;
        }
        onServerConnectionEstablished(event.manager, player);
    }

    /**
     * 客户端断连后清理连接一次性标记。
     *
     * @param event FML 客户端断连事件
     */
    @SubscribeEvent
    public void onClientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        onClientDisconnected();
    }

    /**
     * 服务端断连后清理连接一次性标记。
     *
     * @param event FML 服务端断连事件
     */
    @SubscribeEvent
    public void onServerDisconnected(FMLNetworkEvent.ServerDisconnectionFromClientEvent event) {
        if (event.manager != null) {
            serverHandshakeManagers.remove(event.manager);
        }
    }

    /**
     * 客户端连接建立后发送一次 Qz 能力握手。
     *
     * @param networkManager 当前连接的 NetworkManager
     */
    void onClientConnectionEstablished(NetworkManager networkManager) {
        if (networkManager == null || !clientHandshakeManagers.add(networkManager)) {
            return;
        }
        VanillaPacketBuilders.rememberClientNetworkManager(networkManager);
        try {
            MyMod.LOG.debug("Qz vanilla transport client FML connection established: {}", networkManager);
            NetService.getInstance().sendCapabilityHandshakeToServer();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("Qz 客户端能力握手发送失败", exception);
        }
    }

    /**
     * 服务端连接建立后在下一次服务端主线程排空时发送一次 Qz 能力握手。
     *
     * @param networkManager 当前连接的 NetworkManager
     * @param player 玩家对象
     */
    void onServerConnectionEstablished(final NetworkManager networkManager, final Object player) {
        if (networkManager == null || player == null || !serverHandshakeManagers.add(networkManager)) {
            return;
        }
        NetService.getInstance().runOnMainThread(NetSide.SERVER, new Runnable() {
            @Override
            public void run() {
                try {
                    MyMod.LOG.debug("Qz vanilla transport server FML connection established: {}", player);
                    NetService.getInstance().sendCapabilityHandshakeToPlayer(player);
                } catch (RuntimeException exception) {
                    MyMod.LOG.warn("Qz 服务端能力握手发送失败：player={}", String.valueOf(player), exception);
                }
            }
        });
    }

    /**
     * 服务端玩家离开时清理连接一次性标记。
     *
     * @param player 玩家
     */
    void onServerPlayerLeft(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) {
            return;
        }
        serverHandshakeManagers.remove(player.playerNetServerHandler.netManager);
    }

    /**
     * 客户端断连时清理连接一次性标记。
     */
    void onClientDisconnected() {
        clientHandshakeManagers.clear();
    }

    /**
     * 测试用状态重置。
     */
    void resetForTests() {
        clear();
    }

    /**
     * 从服务端 play handler 解析玩家。
     *
     * @param handler FML 事件中的 play handler
     * @return 玩家，无法解析时返回 null
     */
    static EntityPlayerMP resolveServerPlayer(Object handler) {
        if (!(handler instanceof NetHandlerPlayServer)) {
            return null;
        }
        return ((NetHandlerPlayServer) handler).playerEntity;
    }

    private void clear() {
        clientHandshakeManagers.clear();
        serverHandshakeManagers.clear();
    }
}
