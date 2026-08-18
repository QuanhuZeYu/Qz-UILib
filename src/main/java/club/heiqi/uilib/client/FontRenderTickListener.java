package club.heiqi.uilib.client;

import club.heiqi.uilib.font.FontService;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 在渲染帧开始（渲染管线尚未触达 GL 的稳定阶段）统一协调字体 reload signal 与纹理页批上传。
 */
public class FontRenderTickListener {

    /**
     * 在渲染阶段开始时收敛稳定 reload signal，再批量上传待上传字符。
     *
     * <p>上传固定在本阶段而非 drawString 期间：批上传会成批持有 GL 状态（attrib/unpack/mipmap），
     * 只有稳定管线阶段才能保证批次结算不与任意渲染命令交错。</p>
     *
     * @param event 渲染 Tick 事件
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        FontService.getInstance().tickMainThread(64);
    }
}
