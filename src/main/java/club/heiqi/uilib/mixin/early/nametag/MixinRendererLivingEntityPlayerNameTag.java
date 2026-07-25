package club.heiqi.uilib.mixin.early.nametag;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.internal.font.PlayerNameTagRenderCoordinator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 仅捕获普通玩家名称最终进入 {@code func_147906_a} 的调用点。 */
@Mixin(value = RendererLivingEntity.class, priority = 900)
public abstract class MixinRendererLivingEntityPlayerNameTag {

    /** 保留睡眠与非睡眠分支传入的完整参数和既有 wrapper 链。 */
    @WrapOperation(
            method = "func_96449_a(Lnet/minecraft/entity/EntityLivingBase;DDDLjava/lang/String;FD)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;func_147906_a("
                            + "Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V"),
            require = 2,
            expect = 2,
            allow = 2)
    private void qzuilib$deferPlayerNameTag(
            final RendererLivingEntity renderer,
            final Entity entity,
            final String text,
            final double x,
            final double y,
            final double z,
            final int maxDistance,
            final Operation<Void> original) {
        if (!FontConfig.replaceOrigin || !(entity instanceof AbstractClientPlayer)) {
            original.call(renderer, entity, text, x, y, z, maxDistance);
            return;
        }
        final float capturedLightmapX = OpenGlHelper.lastBrightnessX;
        final float capturedLightmapY = OpenGlHelper.lastBrightnessY;
        PlayerNameTagRenderCoordinator.captureOrRun(new Runnable() {
            @Override
            public void run() {
                OpenGlHelper.setLightmapTextureCoords(
                        OpenGlHelper.lightmapTexUnit,
                        capturedLightmapX,
                        capturedLightmapY);
                original.call(renderer, entity, text, x, y, z, maxDistance);
            }
        });
    }
}
