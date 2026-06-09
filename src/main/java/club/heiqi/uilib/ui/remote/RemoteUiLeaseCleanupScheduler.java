package club.heiqi.uilib.ui.remote;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 远程 UI lease 清扫调度器。
 *
 * <p>该调度器只位于 ui.remote 内部，按固定 TTL 主动清理远程页面和 HUD session，
 * 不向 NetService 注入 keepalive、renew 或远程 UI 业务语义。</p>
 */
public final class RemoteUiLeaseCleanupScheduler {

    private static final RemoteUiLeaseCleanupScheduler INSTANCE = new RemoteUiLeaseCleanupScheduler();

    private static boolean registered;

    private RemoteUiLeaseCleanupScheduler() {}

    /**
     * 注册 Forge tick 调度。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        FMLCommonHandler.instance().bus().register(INSTANCE);
        registered = true;
    }

    /**
     * 服务端 tick 结束时主动清扫远程 UI lease。
     *
     * @param event 服务端 tick 事件
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickLeaseCleanup();
        }
    }

    /**
     * 推进远程 UI lease 清扫。
     */
    static void tickLeaseCleanup() {
        RemoteDocumentPages.tickLeaseCleanup();
        RemoteHudOverlays.tickLeaseCleanup();
    }
}
