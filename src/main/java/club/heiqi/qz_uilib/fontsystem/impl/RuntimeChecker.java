package club.heiqi.qz_uilib.fontsystem.impl;

import club.heiqi.qz_uilib.Config;
import com.gtnewhorizon.gtnhlib.client.event.RenderTooltipEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import java.util.Random;

/**
 * 用于实时检查需要替换的目标 将目标切换为替换的字体渲染引擎
 */
public class RuntimeChecker {
    /**全局检查器*/
    public static RuntimeChecker instance;
    public static RuntimeChecker getInstance() {
        if (instance == null) {
            instance = new RuntimeChecker();
        }
        return instance;
    }

    public FontRenderer cachedOriginal;
    public FontRenderer cacheStandardGalactic;
    @SubscribeEvent
    public void OriginalInspector_01(TickEvent.RenderTickEvent renderTick) {
        // 检查原版渲染器字段
        if (Minecraft.getMinecraft().fontRenderer instanceof ReplaceFontRender replaced) {
            if (!Config.replaceOrigin) {
                Minecraft.getMinecraft().fontRenderer = cachedOriginal;
            }
        }
        else {
            // 缓存原版的渲染器 或者是其他替换过的 非本模组实现的替换渲染器
            cachedOriginal = deepCopy(Minecraft.getMinecraft().fontRenderer);
            // 决定是否执行替换
            if (Config.replaceOrigin) {
                // 使用本模组的全局公共渲染器 以便外部调用
                Minecraft.getMinecraft().fontRenderer = ReplaceFontRender.getInstance();
            }
        }
        // 原版渲染器的另一个字段
        if (Minecraft.getMinecraft().standardGalacticFontRenderer instanceof ReplaceFontRender replaced) {
            if (!Config.replaceOrigin) {
                Minecraft.getMinecraft().standardGalacticFontRenderer = cacheStandardGalactic;
            }
        }
        else {
            cacheStandardGalactic = deepCopy(Minecraft.getMinecraft().standardGalacticFontRenderer);
            if (Config.replaceOrigin) {
                Minecraft.getMinecraft().standardGalacticFontRenderer = ReplaceFontRender.getInstance();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void replaceRenderToolTipFontRenderer(RenderTooltipEvent event) {
        if (Config.replaceOrigin) {
            event.font = ReplaceFontRender.getInstance();
        }
    }


    public FontRenderer deepCopy(FontRenderer target) {
        FontRenderer copied = new FontRenderer(Minecraft.getMinecraft().gameSettings, new ResourceLocation("textures/font/ascii.png"), target.renderEngine, target.unicodeFlag);
        return copied;
    }


    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }
}
