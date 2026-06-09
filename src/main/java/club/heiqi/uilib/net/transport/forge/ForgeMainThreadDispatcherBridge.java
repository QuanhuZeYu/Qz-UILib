package club.heiqi.uilib.net.transport.forge;

import club.heiqi.uilib.net.api.NetService;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Forge tick 事件到网络主线程队列的桥。
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
