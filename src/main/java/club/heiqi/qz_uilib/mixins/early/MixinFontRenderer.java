package club.heiqi.qz_uilib.mixins.early;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.fontsystem.impl.ReplaceFontRender;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = FontRenderer.class, priority = 999)
public abstract class MixinFontRenderer {

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    public void onResourceManagerReload(IResourceManager p_110549_1_, CallbackInfo ci) {
        ReplaceFontRender.getInstance().onResourceManagerReload(p_110549_1_);
    }

    @Inject(method = "drawSplitString", at = @At("HEAD"), cancellable = true)
    public void drawSplitString(String str, int x, int y, int wrapWidth, int textColor, CallbackInfo ci) {
        if (Config.replaceOrigin) {
            ReplaceFontRender.getInstance().drawSplitString(str, x, y, wrapWidth, textColor);
            ci.cancel();
        }
    }

    @Inject(method = "drawString", at = @At("HEAD"), cancellable = true)
    public void drawString(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().drawString(text, x, y, color));
        }
    }

    @Inject(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true)
    public void drawString(String text, int x, int y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().drawString(text, x, y, color, dropShadow));
        }
    }

    @Inject(method = "drawStringWithShadow", at = @At("HEAD"), cancellable = true)
    public void drawStringWithShadow(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().drawStringWithShadow(text, x, y, color));
        }
    }

    @Inject(method = "getCharWidth", at = @At("HEAD"), cancellable = true)
    public void getCharWidth(char p_78263_1_, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().getCharWidth(p_78263_1_));
        }
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    public void getStringWidth(String p_78256_1_, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().getStringWidth(p_78256_1_));
        }
    }

    @Inject(method = "listFormattedStringToWidth", at = @At("HEAD"), cancellable = true)
    public void listFormattedStringToWidth(String str, int wrapWidth, CallbackInfoReturnable<List<String>> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().listFormattedStringToWidth(str, wrapWidth));
        }
    }

    @Inject(method = "splitStringWidth", at = @At("HEAD"), cancellable = true)
    public void splitStringWidth(String p_78267_1_, int p_78267_2_, CallbackInfoReturnable<Integer> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().splitStringWidth(p_78267_1_, p_78267_2_));
        }
    }

    @Inject(method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    public void trimStringToWidth(String p_78262_1_, int p_78262_2_, boolean p_78262_3_, CallbackInfoReturnable<String> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().trimStringToWidth(p_78262_1_, p_78262_2_, p_78262_3_));
        }
    }

    @Inject(method = "trimStringToWidth(Ljava/lang/String;I)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    public void trimStringToWidth(String p_78269_1_, int p_78269_2_, CallbackInfoReturnable<String> cir) {
        if (Config.replaceOrigin) {
            cir.setReturnValue(ReplaceFontRender.getInstance().trimStringToWidth(p_78269_1_, p_78269_2_));
        }
    }
}
