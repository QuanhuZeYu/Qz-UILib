package club.heiqi.qz_uilib.mixins.late;

import appeng.api.config.TerminalFontSize;
import appeng.client.render.StackSizeRenderer;
import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.fontsystem.impl.ReplaceFontRender;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = StackSizeRenderer.class, remap = false)
public abstract class MixinStackSizeRenderer {


    /**
     * @author 泉户泽雨
     * @reason 自定义显示大小
     */
    @Overwrite
    public static void drawStackSize(int offsetX, int offsetY, String customText, FontRenderer font,
                                     TerminalFontSize fontSize) {
        float scale = 1.0f;
        float shiftX = 0f;
        float shiftY = 0f;

        if (fontSize == TerminalFontSize.LARGE && customText.length() > 3) {
            fontSize = TerminalFontSize.DYNAMIC;
        }

        if (fontSize == TerminalFontSize.SMALL) {
            scale = 1;
            shiftX = 0;
            shiftY = 0;
        } else if (fontSize == TerminalFontSize.LARGE) {
            scale = 0.85f;
        } else if (fontSize == TerminalFontSize.DYNAMIC) {
            if (customText.length() == 3) {
                scale = 0.786f;
                shiftX = 0.5f;
            } else if (customText.length() == 4) {
                scale = 0.644f;
                shiftX = 1f;
                shiftY = 0.5f;
            } else if (customText.length() > 4) {
                scale = 0.5f;
                shiftX = 2;
                shiftY = 1;
            } else {
                scale = 0.85f;
            }
        }

        ReplaceFontRender replaceFontRender = ReplaceFontRender.getInstance();
        replaceFontRender.pushCharSize();
        replaceFontRender.setCharSize(Config.aeFontSize);
        if (scale == 1.0f) {
            replaceFontRender.drawStringWithShadow(
                    customText,
                    offsetX + 16 + 1 - replaceFontRender.getStringWidth(customText),
                    offsetY + 16 - (int)replaceFontRender.FONT_HEIGHT,
                    16777215);
        } else {
            final float inverseScaleFactor = 1.0f / scale;
            GL11.glScaled(scale, scale, scale);

            final int X = (int) (((float) offsetX - shiftX + 16.0f + 1.0f - replaceFontRender.getStringWidth(customText) * scale)
                    * inverseScaleFactor);
            final int Y = (int) (((float) offsetY + 16 - (int)replaceFontRender.FONT_HEIGHT * scale) * inverseScaleFactor);

            replaceFontRender.drawStringWithShadow(customText, X, Y, 16777215);

            GL11.glScaled(inverseScaleFactor, inverseScaleFactor, inverseScaleFactor);
        }
        replaceFontRender.popCharSize();
    }
}
