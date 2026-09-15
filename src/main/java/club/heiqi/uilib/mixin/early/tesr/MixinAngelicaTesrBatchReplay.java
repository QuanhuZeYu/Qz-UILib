package club.heiqi.uilib.mixin.early.tesr;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.heiqi.uilib.internal.font.tesr.TesrTextReplayCoordinator;

/**
 * Angelica TESR 批次提交点上的世界文字回放围栏（可选宿主 Mixin）。
 *
 * <p>宿主把 TESR 几何排队、在遍历结束后统一提交；本库字形却是在 {@code drawString} 调用点内的
 * 真实 draw 提交。两者落屏顺序不同源，木板晚于字形落屏即会在自身轮廓内覆盖字形。本围栏在宿主
 * <b>几何提交之后</b>回放本帧捕获的世界文字，把顺序还原为「几何先、文字后」。</p>
 *
 * <p>回放点覆盖宿主两条提交分支：{@code flush()}（非延迟管线：本方法内即提交完几何）与
 * {@code flushAfterDeferred()}（延迟管线：本方法提交延迟几何与延迟文字）。两处都在方法 RETURN 处
 * 校验「宿主已无未提交几何」后才回放，因此 Iris 延迟分支在 {@code flush()} 的早退返回上不会提前回放。</p>
 *
 * <p>兼容样式：目标类用字符串名指定，Angelica 缺席时本 Mixin 整体不应用，协调器保持停用，
 * 世界文字退回即时绘制。</p>
 */
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer", remap = false)
public abstract class MixinAngelicaTesrBatchReplay {

    /** 类初始化即完成握手，使首帧起即可捕获（无需等第一次提交）。 */
    @Inject(method = "<clinit>", at = @At("TAIL"), require = 0)
    private static void qzuilib$markTesrReplayHook(CallbackInfo callbackInfo) {
        TesrTextReplayCoordinator.markHostHookInstalled();
    }

    /** 非延迟管线：几何在本方法内提交完毕，RETURN 即安全回放点。 */
    @Inject(method = "flush", at = @At("RETURN"), require = 0)
    private void qzuilib$replayWorldTextAfterBatchFlush(CallbackInfo callbackInfo) {
        TesrTextReplayCoordinator.markHostHookInstalled();
        TesrTextReplayCoordinator.replayAfterHostCommit();
    }

    /** 延迟管线：延迟几何与延迟文字都在本方法内提交完毕。 */
    @Inject(method = "flushAfterDeferred", at = @At("RETURN"), require = 0)
    private void qzuilib$replayWorldTextAfterDeferredFlush(CallbackInfo callbackInfo) {
        TesrTextReplayCoordinator.markHostHookInstalled();
        TesrTextReplayCoordinator.replayAfterHostCommit();
    }
}
