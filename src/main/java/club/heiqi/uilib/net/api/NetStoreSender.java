package club.heiqi.uilib.net.api;

import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.transport.ITransport;

/**
 * Store 同步帧发送器。
 */
final class NetStoreSender {

    private final NetService service;

    NetStoreSender(NetService service) {
        this.service = service;
    }

    /**
     * 发送 Store 同步帧，并在需要时应用访问控制。
     *
     * @param store Store 定义
     * @param target 目标
     * @param envelope 信封
     */
    void send(NetStore store, NetTarget target, NetEnvelope envelope) {
        if (!store.hasAccessControl()) {
            service.sendEnvelope(target, envelope);
            return;
        }
        switch (target.getType()) {
            case PLAYER:
                sendToPlayerIfAllowed(store, target.getPlayer(), envelope);
                return;
            case PLAYERS:
                for (Object player : target.getPlayers()) {
                    sendToPlayerIfAllowed(store, player, envelope);
                }
                return;
            case ALL:
                sendToAccessiblePlayers(store, null, envelope);
                return;
            case DIMENSION:
                sendToAccessiblePlayers(store, Integer.valueOf(target.getDimensionId()), envelope);
                return;
            default:
                throw new IllegalStateException("Store 同步帧只能发送到客户端目标：" + target.getType());
        }
    }

    private void sendToAccessiblePlayers(NetStore store, Integer dimensionId, NetEnvelope envelope) {
        ITransport activeTransport = service.requireTransport();
        for (Object player : activeTransport.getConnectedPlayers()) {
            if (dimensionId != null && !dimensionId.equals(activeTransport.getPlayerDimensionId(player))) {
                continue;
            }
            sendToPlayerIfAllowed(store, player, envelope);
        }
    }

    private void sendToPlayerIfAllowed(NetStore store, Object player, NetEnvelope envelope) {
        if (player != null && store.canAccess(player)) {
            service.sendEnvelope(NetTarget.player(player), envelope);
        }
    }
}
