package club.heiqi.qz_uilib.mixins.early;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.fontsystem.impl.ReplaceFontRender;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher {

    @Inject(
            method = "renderTileEntity",
            at = @At(value = "HEAD")
    )
    public void startRenderTileEntity(TileEntity p_147544_1_, float p_147544_2_, CallbackInfo ci) {
        if (Config.replaceOrigin) {
            ReplaceFontRender.getInstance().batchRenderer.startBatch();
        }
    }

    @Inject(
            method = "renderTileEntity",
            at = @At(value = "TAIL")
    )
    public void endRenderTileEntity(TileEntity p_147544_1_, float p_147544_2_, CallbackInfo ci) {
        if (Config.replaceOrigin) {
            ReplaceFontRender.getInstance().batchRenderer.endBatch();
        }
    }
}
