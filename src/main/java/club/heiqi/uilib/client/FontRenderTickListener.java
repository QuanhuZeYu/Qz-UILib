package club.heiqi.uilib.client;

import club.heiqi.uilib.font.FontService;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 在渲染帧末尾统一处理字体纹理页上传。
 */
public class FontRenderTickListener {

    /**
     * 在渲染阶段结束时刷新待上传字符。
     *
     * @param event 渲染 Tick 事件
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        FontService.getInstance().tickMainThread(64);
    }
}
