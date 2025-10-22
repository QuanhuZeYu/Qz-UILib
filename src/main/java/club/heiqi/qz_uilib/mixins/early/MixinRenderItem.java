package club.heiqi.qz_uilib.mixins.early;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.fontsystem.impl.ReplaceFontRender;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(RenderItem.class)
public abstract class MixinRenderItem {

    @Redirect(
            method = "renderItemOverlayIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;III)I"
            ),
            remap = false
    )
    private int redirectDrawStringWithShadow(FontRenderer fontRenderer, String text, int x, int y, int color,
                                             // 添加额外的参数来获取 renderItemOverlayIntoGUI 的其他参数
                                             // Mixin 会自动将调用点之前堆栈中的参数传递给 @Redirect 方法
                                             FontRenderer unusedFontRenderer, TextureManager manager, ItemStack itemStack, int itemX, int itemY, String originalText) {
        if (Config.customInvCountFont) {
            ReplaceFontRender replaceFontRender = ReplaceFontRender.getInstance();
            replaceFontRender.pushCharSize();
            replaceFontRender.setCharSize(Config.stackFontSize);
            int stringWidth = fontRenderer.getStringWidth(text);

            int newX = itemX + 16 - stringWidth;
            int newY = itemY + 15 - (int) replaceFontRender.FONT_HEIGHT + /*向下移动一个基线距离*/2;
            if (Config.centered) {
                newX = itemX + 8 - (stringWidth / 2);
            }
            int stringWithShadow = fontRenderer.drawStringWithShadow(text, newX, newY, 16777215);

            replaceFontRender.popCharSize();

            return stringWithShadow;
        }
        else {
            return fontRenderer.drawString(text, x, y, color);
        }
    }
}
