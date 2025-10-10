package club.heiqi.qz_uilib.mixins.late;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.fontsystem.impl.ReplaceFontRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumcraft.client.lib.TCFontRenderer;

@Mixin(TCFontRenderer.class)
public class MixinTCFontRenderer {

    @Inject(method = "drawString(Ljava/lang/String;III)I", at = @At("HEAD"), cancellable = true, remap = false)
    public void drawString(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().drawString(text, x, y, color));
        }
    }

    @Inject(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true, remap = false)
    public void drawString(String text, int x, int y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().drawString(text, x, y, color, dropShadow));
        }
    }

    @Inject(method = "drawStringWithShadow", at = @At("HEAD"), cancellable = true, remap = false)
    public void drawStringWithShadow(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().drawStringWithShadow(text, x, y, color));
        }
    }

    @Inject(method = "getCharWidth", at = @At("HEAD"), cancellable = true, remap = false)
    public void getCharWidth(char p_78263_1_, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().getCharWidth(p_78263_1_));
        }
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true, remap = false)
    public void getStringWidth(String p_78256_1_, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().getStringWidth(p_78256_1_));
        }
    }

    @Inject(method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, remap = false)
    public void trimStringToWidth(String p_78262_1_, int p_78262_2_, boolean p_78262_3_, CallbackInfoReturnable<String> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().trimStringToWidth(p_78262_1_, p_78262_2_, p_78262_3_));
        }
    }

    @Inject(method = "trimStringToWidth(Ljava/lang/String;I)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, remap = false)
    public void trimStringToWidth(String p_78269_1_, int p_78269_2_, CallbackInfoReturnable<String> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().trimStringToWidth(p_78269_1_, p_78269_2_));
        }
    }

    @Inject(method = "renderString", at = @At("HEAD"), cancellable = true, remap = false)
    public void renderString(String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().renderString(text, x, y, color, shadow));
        }
    }
}
