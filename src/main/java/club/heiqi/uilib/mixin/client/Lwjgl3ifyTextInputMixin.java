package club.heiqi.uilib.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.heiqi.uilib.ui.host.minecraft.SceneNativeTextInput;

/** 复用 GTNH 3.0.31 唯一 batch consumer；不再消费 SDL 或 Keyboard 队列。 */
@Pseudo
@Mixin(targets = "org.lwjglx.Lwjgl3ifyEventLoop", remap = false)
public abstract class Lwjgl3ifyTextInputMixin {
    @Inject(method = "internalPumpEvents()V", at = @At("HEAD"), remap = false)
    private static void qz_uilib$markTextInputHook(CallbackInfo callback) {
        SceneNativeTextInput.markHookAvailable();
    }

    // 必需 hook 保留 defaultRequire=1；已识别的宿主签名变化必须显式暴露。
    @Inject(method = "internalPumpEvents()V", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/system/MemoryUtil;memCopy(JJJ)V", shift = At.Shift.AFTER, remap = false), remap = false)
    private static void qz_uilib$captureTextInput(CallbackInfo callback) {
        SceneNativeTextInput.capture();
    }
}
