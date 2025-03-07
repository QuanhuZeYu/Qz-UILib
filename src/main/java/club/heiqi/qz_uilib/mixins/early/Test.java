package club.heiqi.qz_uilib.mixins.early;

import club.heiqi.qz_uilib.MyMod;
import club.heiqi.qz_uilib.fontsystem.FontManager;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    value = FontRenderer.class,
    priority = 5000
)
public abstract class Test {
    @Shadow
    public float posX;
    @Shadow
    public float posY;

    @Inject(
        method = "renderCharAtPos",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    public void qzuilib$renderCharAtPos(int index, char c, boolean b, CallbackInfoReturnable<Float> ci) {
        String ch = Character.toString(c);
        FontManager fontManager = MyMod.fontManager;
        if (fontManager == null || !fontManager.canRender) {
            return;
        }
        if (fontManager.canRender){
            float f = fontManager.renderCharAt(ch, this.posX, this.posY, 8f);
            ci.setReturnValue(f);
            ci.cancel();
        }
    }
}
