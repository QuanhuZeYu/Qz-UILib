package club.heiqi.uilib.client;

import club.heiqi.uilib.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

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
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.currentScreen != null) {
            return;
        }
        renderUiDebugOverlay(minecraft, null);
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
        renderUiDebugOverlay(Minecraft.getMinecraft(), event.gui);
    }

    /**
     * 在屏幕右上角绘制当前页面类名调试信息。
     *
     * @param minecraft 客户端实例
     * @param screen 当前屏幕
     */
    private void renderUiDebugOverlay(Minecraft minecraft, GuiScreen screen) {
        if (!Config.uiDebug || minecraft == null || minecraft.fontRenderer == null) {
            return;
        }
        int nativeWidth = Math.max(1, minecraft.displayWidth);
        int nativeHeight = Math.max(1, minecraft.displayHeight);
        ScaledResolution scaledResolution = new ScaledResolution(minecraft, nativeWidth, nativeHeight);
        FontRenderer fontRenderer = minecraft.fontRenderer;
        int maxTextWidth = Math.max(32, scaledResolution.getScaledWidth() - 8);
        String rawScreenClassName = resolveScreenClassName(screen);
        String debugText = fitDebugTextToWidth(rawScreenClassName, fontRenderer, maxTextWidth);
        int textWidth = Math.max(0, fontRenderer.getStringWidth(debugText));
        int x = Math.max(2, scaledResolution.getScaledWidth() - textWidth - 4);
        int y = 4;
        Gui.drawRect(Math.max(0, x - 2), Math.max(0, y - 2), Math.min(scaledResolution.getScaledWidth(), x + textWidth + 2),
                Math.min(scaledResolution.getScaledHeight(), y + fontRenderer.FONT_HEIGHT + 2), 0xA0000000);
        fontRenderer.drawStringWithShadow(debugText, x, y, 0xFFFFFFFF);
    }

    /**
     * 返回当前屏幕类名文本。
     *
     * @param screen 当前屏幕
     * @return 调试显示文本
     */
    private String resolveScreenClassName(GuiScreen screen) {
        return screen == null ? "null" : screen.getClass().getName();
    }

    /**
     * 将调试文本裁剪到可用宽度内，优先保留尾部类名。
     *
     * @param text 原始文本
     * @param fontRenderer 字体渲染器
     * @param maxWidth 最大宽度
     * @return 适配后的文本
     */
    private String fitDebugTextToWidth(String text, FontRenderer fontRenderer, int maxWidth) {
        if (text == null || text.isEmpty() || fontRenderer == null || maxWidth <= 0) {
            return "";
        }
        if (fontRenderer.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        if (fontRenderer.getStringWidth(ellipsis) >= maxWidth) {
            return ellipsis;
        }
        String trimmed = text;
        while (!trimmed.isEmpty() && fontRenderer.getStringWidth(ellipsis + trimmed) > maxWidth) {
            trimmed = trimmed.substring(1);
        }
        return ellipsis + trimmed;
    }
}
