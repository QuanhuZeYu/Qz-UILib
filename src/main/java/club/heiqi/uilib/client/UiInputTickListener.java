package club.heiqi.uilib.client;

import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.hud.UiHudDocumentHost;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 在渲染帧中刷新 UI 输入与界面事件。
 */
public class UiInputTickListener {

    /**
     * 在渲染阶段结束时以帧频收集输入并分发给 UI。
     *
     * @param event 渲染 Tick 事件
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        UiInputService.getInstance().tick();
        UiInputFrame frame = UiInputService.getInstance().collectFrame();
        UiScreenManager.getInstance().tick(frame);
        UiHudDocumentHost.getInstance().handleInputFrame(frame);
    }
}
