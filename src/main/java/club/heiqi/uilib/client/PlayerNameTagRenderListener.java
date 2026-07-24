package club.heiqi.uilib.client;

import club.heiqi.uilib.internal.font.PlayerNameTagReplayQueue;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

/**
 * 在世界渲染末尾回放普通玩家名称标签，并守住渲染帧队列边界。
 */
public final class PlayerNameTagRenderListener {

    /**
     * 在其他 world-last 监听完成后回放当前批次的玩家名称标签。
     *
     * @param event 世界渲染末尾事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        PlayerNameTagReplayQueue.drain();
    }

    /**
     * 在渲染 Tick 的两个边界丢弃未回放项，禁止残留跨帧。
     *
     * @param event 渲染 Tick 事件
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START || event.phase == TickEvent.Phase.END) {
            clearPendingReplays();
        }
    }

    /** 清空尚未回放的玩家名称标签。 */
    public void clearPendingReplays() {
        PlayerNameTagReplayQueue.clear();
    }
}
