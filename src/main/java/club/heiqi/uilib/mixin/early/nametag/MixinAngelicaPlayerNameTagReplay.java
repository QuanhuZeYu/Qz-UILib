package club.heiqi.uilib.mixin.early.nametag;

import club.heiqi.uilib.internal.font.PlayerNameTagRenderCoordinator;
import club.heiqi.uilib.internal.font.angelica.AngelicaNameTagReplayGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 为 coordinator 的唯一批次调用点安装 Angelica 2.1.50 回放围栏。 */
@Mixin(value = PlayerNameTagRenderCoordinator.class, remap = false)
public abstract class MixinAngelicaPlayerNameTagReplay {

    @Shadow
    private static boolean angelicaReplayGuardInstalled;

    /** 在目标类初始化完成后提供可选围栏已安装握手。 */
    @Inject(method = "<clinit>", at = @At("TAIL"), require = 1)
    private static void qzuilib$installAngelicaReplayGuard(CallbackInfo callbackInfo) {
        angelicaReplayGuardInstalled = true;
    }

    /** 只包装 coordinator 自有的单个 {@link Runnable#run()} 批次调用。 */
    @WrapOperation(
            method = "runReplayBatch(Ljava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V"),
            require = 1,
            expect = 1,
            allow = 1)
    private static void qzuilib$guardAngelicaReplay(
            final Runnable batch,
            final Operation<Void> original) {
        AngelicaNameTagReplayGuard.runGuarded(new Runnable() {
            @Override
            public void run() {
                original.call(batch);
            }
        });
    }
}
