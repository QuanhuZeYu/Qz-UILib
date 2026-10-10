package club.heiqi.uilib.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import club.heiqi.uilib.host.client.font.compat.AngelicaBridge;

/** 仅plugin确认可选Angelica类存在时安装；require=1拒绝错版本，不依赖priority证明覆盖。
 * 主绘制仍UILib的共享plan/backend；这里仅旧segment保序及安全生命周期通知。 */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.client.font.BatchingFontRenderer", remap = false)
public abstract class AngelicaFontOrderMixin {
    @Unique
    public boolean uilibFontOrderHooksInstalled() {
        return true;
    }

    @Inject(method = "sealBatchSegment()V", at = @At("RETURN"), require = 1)
    private void uilibSealed(CallbackInfo ci) {
        AngelicaBridge.sealed(this);
    }

    // WrapOperation使用Injector.validateParams，同维引用数组可Coerce；CallbackInjector的数组分支不允许。
    // 保留实际owner，两个renderer可以都有[0,1)；原helper调用次数和boolean返回值不变。
    @WrapOperation(
            method = {"flushDeferredTextInner()V", "flushBatchInner()V"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer;drawCommands([Lcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer$FontDrawCmd;IILcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer;)V"),
            require = 3)
    private static void uilibBeforeDirect(
            @Coerce Object[] commands,
            int from,
            int to,
            @Coerce Object owner,
            Operation<Void> original) {
        AngelicaBridge.beforeOldReplay(owner, from, to);
        original.call(commands, from, to, owner);
    }

    @WrapOperation(
            method = {"flushDeferredThroughPipeline()V", "flushBatchThroughPipeline()V"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer;emitRangeThroughPipeline(Lcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer;[Lcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer$FontDrawCmd;IIIFFF)Z"),
            require = 3)
    private static boolean uilibBeforePipeline(
            @Coerce Object owner,
            @Coerce Object[] commands,
            int from,
            int to,
            int light,
            float nx,
            float ny,
            float nz,
            Operation<Boolean> original) {
        AngelicaBridge.beforeOldReplay(owner, from, to);
        return original.call(owner, commands, from, to, light, nx, ny, nz);
    }

    @Inject(
            method = {"flushDeferredText()V", "flushBatch()V"},
            at = @At("HEAD"),
            require = 1)
    private static void uilibBeginFlush(CallbackInfo ci) {
        AngelicaBridge.beginFlush();
    }

    @Inject(
            method = {"flushDeferredText()V", "flushBatch()V"},
            at = @At("RETURN"),
            require = 1)
    private static void uilibEndFlush(CallbackInfo ci) {
        AngelicaBridge.endFlush();
    }

    @Inject(method = "discardDeferredText()V", at = @At("RETURN"), require = 1)
    private static void uilibDiscard(CallbackInfo ci) {
        AngelicaBridge.discarded();
    }

    @Inject(method = "endFrame()V", at = @At("HEAD"), require = 1)
    private static void uilibAbortFrame(CallbackInfo ci) {
        AngelicaBridge.frameEnded();
    }
}
