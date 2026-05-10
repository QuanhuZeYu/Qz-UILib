package club.heiqi.uilib.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import club.heiqi.uilib.ui.hud.UiHudDocumentHost;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * HUD 文档渲染监听器。
 */
public class UiHudRenderListener {

    /**
     * 在游戏 HUD 阶段绘制 HUD 文档层。
     *
     * @param event HUD 渲染事件
     */
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event == null || event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        if (Minecraft.getMinecraft() == null || Minecraft.getMinecraft().currentScreen != null) {
            return;
        }
        UiHudDocumentHost.getInstance().renderHud(event.partialTicks);
    }

    /**
     * 在普通 GuiScreen 之后补绘交互 HUD 层。
     *
     * @param event 屏幕后置绘制事件
     */
    @SubscribeEvent
    public void onGuiScreenDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event == null) {
            return;
        }
        if (!UiHudDocumentHost.getInstance().hasVisibleLayer(event.gui)) {
            return;
        }
        UiHudDocumentHost.getInstance().renderOnScreen(event.renderPartialTicks);
    }
}
