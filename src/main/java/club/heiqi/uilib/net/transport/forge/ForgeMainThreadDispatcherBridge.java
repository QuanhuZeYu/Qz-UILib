package club.heiqi.uilib.net.transport.forge;

import club.heiqi.uilib.config.modern.ModernConfigApplyCoordinator;
import club.heiqi.uilib.net.api.NetService;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Forge tick 事件到网络主线程队列的桥。
 *
 * <p>CLIENT END 顺序：</p>
 * <ol>
 *   <li>{@link ModernConfigApplyCoordinator#retryPendingOnce()}——仅当 owner false 且有有效 pending
 *       时 enqueue 一次；owner true 时 no-op，避免与已有 queued coordinator 重复</li>
 *   <li>timeouts + {@link NetService#drainClientMainThreadTasks()}——dispatcher 入口快照预算；
 *       drain 期间 enqueue 留 next-drain</li>
 * </ol>
 *
 * <p>每 tick 最多一个 coordinator apply（no-spin）。</p>
 */
public final class ForgeMainThreadDispatcherBridge {

    private static final ForgeMainThreadDispatcherBridge INSTANCE = new ForgeMainThreadDispatcherBridge();

    private ForgeMainThreadDispatcherBridge() {}

    /**
     * 返回单例。
     *
     * @return 桥
     */
    public static ForgeMainThreadDispatcherBridge getInstance() {
        return INSTANCE;
    }

    /**
     * 客户端 tick 排空客户端主线程任务。
     *
     * @param event tick 事件
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // 配置回灌失败后的 tick 重试（owner false 才排，禁止与已有 queued 重复）
            ModernConfigApplyCoordinator.getInstance().retryPendingOnce();
            NetService.getInstance().tickTimeouts();
            NetService.getInstance().drainClientMainThreadTasks();
        }
    }

    /**
     * 服务端 tick 排空服务端主线程任务。
     *
     * @param event tick 事件
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            NetService.getInstance().tickTimeouts();
            NetService.getInstance().drainServerMainThreadTasks();
        }
    }
}
