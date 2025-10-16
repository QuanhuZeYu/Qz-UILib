package club.heiqi.qz_uilib.mixins.late;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.MyMod;
import club.heiqi.qz_uilib.fontsystem.impl.ReplaceFontRender;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.NEIClientUtils;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = NEIClientUtils.class, remap = false)
public abstract class MixinNEIClientUtils {

    @Redirect(
            method = "drawNEIOverlayText(Ljava/lang/String;Lcodechicken/lib/vec/Rectangle4i;FIZLcodechicken/nei/NEIClientUtils$Alignment;)V",
            at = @At(
                    value = "INVOKE",
                    // 假设 drawString(String, int, int, int, boolean) -> (Ljava/lang/String;IIIZ)I
                    target = "Lcodechicken/nei/NEIClientUtils;gl2DRenderContext(Ljava/lang/Runnable;)V"
            ),
            remap = false
    )
    private static void redirectGl2DRenderContext(
            // 目标方法参数
            Runnable callback,

            // 宿主方法参数 (用于传递给新的 Runnable)
            String text, Rectangle4i rect, float scale, int color, boolean shadow, NEIClientUtils.Alignment alignment
    ) {
        callback = () -> {
            ReplaceFontRender replaceFontRender = ReplaceFontRender.getInstance();
            replaceFontRender.pushCharSize();
            replaceFontRender.setCharSize(Config.neiFontSize);
            final int width = replaceFontRender.getStringWidth(text);
            final double offsetX = rect.x + rect.w - width;
            final double offsetY = rect.y + rect.h - replaceFontRender.FONT_HEIGHT + 2;

            GL11.glTranslated(offsetX, offsetY, 0);

            replaceFontRender.drawString(text, 0, 0, color, shadow);
            replaceFontRender.popCharSize();

            GL11.glTranslated(-1 * offsetX, -1 * offsetY, 0);
        };
        NEIClientUtils.gl2DRenderContext(callback);
    }
}
