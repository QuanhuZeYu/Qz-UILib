package club.heiqi.qz_uilib.mixins.early;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.fontsystem.impl.ReplaceFontRender;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiIngameForge.class, priority = 999)
public class MixinGuiIngameForge {

    @Inject(
            method = "renderHUDText",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraftforge/client/event/RenderGameOverlayEvent$Text.<init>(Lnet/minecraftforge/client/event/RenderGameOverlayEvent;Ljava/util/ArrayList;Ljava/util/ArrayList;)V"
            ),
            remap = false
    )
    public void startF3TextBatching(int width, int height, CallbackInfo ci) {
        if (Config.replaceOrigin) {
            ReplaceFontRender.getInstance().batchRenderer.startBatch();
        }
    }

    @Inject(
            method = "renderHUDText",
            at = @At(value = "TAIL"),
            remap = false
    )
    public void endF3TextBatching(int width, int height, CallbackInfo ci) {
        if (Config.replaceOrigin) {
            ReplaceFontRender.getInstance().batchRenderer.endBatch();
        }
    }
}
