package club.heiqi.qz_uilib.mixins;

import club.heiqi.qz_uilib.MyMod;
import club.heiqi.qz_uilib.fontsystem.FontManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static club.heiqi.qz_uilib.MOD_INFO.LOG;

@Mixin(value = FontRenderer.class)
public abstract class Test {
    @Shadow
    public float posX;
    @Shadow
    public float posY;

//    @Inject(
//        method = "<init>(Lnet/minecraft/client/settings/GameSettings;Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/renderer/texture/TextureManager;Z)V",
//        at = @At("HEAD"),
//        remap = true
//    )
//    private static void qz$init(GameSettings gameSettings, ResourceLocation fontLocation, TextureManager textureManager, boolean useUnicode, CallbackInfo ci) {
////        fontManager = new FontManager();
//    }

//    @Inject(
//        method = "drawString(Ljava/lang/String;IIIZ)I",
//        at = @At("HEAD"),
//        cancellable = true,
//        remap = true
//    )
//    public void qz$drawString(String text, int x, int y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> ci) {
//        ci.cancel();
//    }

    @Inject(
        method = "renderUnicodeChar",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    public void qzuilib$renderUnicodeChar(char c, boolean bold, CallbackInfoReturnable<Float> ci) {
        FontManager fontManager = MyMod.fontManager;
        if (fontManager == null) return;
        float f = fontManager.renderCharAt(c, this.posX, this.posY);
        ci.setReturnValue(f);
    }
}
