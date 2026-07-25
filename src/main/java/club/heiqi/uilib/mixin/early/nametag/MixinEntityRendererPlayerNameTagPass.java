package club.heiqi.uilib.mixin.early.nametag;

import club.heiqi.uilib.internal.font.PlayerNameTagRenderCoordinator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 为 {@code EntityRenderer.renderWorld} 的两个世界实体调用分别建立标签捕获域。 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererPlayerNameTagPass {

    /**
     * 原 host 返回时 TileEntity 循环已经完成，随后仍处于同一个 {@code renderWorld} caller。
     */
    @WrapOperation(
            method = "renderWorld(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities("
                            + "Lnet/minecraft/entity/EntityLivingBase;"
                            + "Lnet/minecraft/client/renderer/culling/ICamera;F)V"),
            require = 2,
            expect = 2,
            allow = 2)
    private void qzuilib$runPlayerNameTagPass(
            final RenderGlobal renderGlobal,
            final EntityLivingBase viewEntity,
            final ICamera camera,
            final float partialTicks,
            final Operation<Void> original) {
        PlayerNameTagRenderCoordinator.runHostPass(new Runnable() {
            @Override
            public void run() {
                original.call(renderGlobal, viewEntity, camera, partialTicks);
            }
        });
    }
}
