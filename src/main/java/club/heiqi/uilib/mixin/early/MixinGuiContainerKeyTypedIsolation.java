package club.heiqi.uilib.mixin.early;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.heiqi.uilib.ui.input.UiKeyboardCaptureState;
import net.minecraft.client.gui.inventory.GuiContainer;

/**
 * 当 HUD 已接管键盘时，阻断容器页继续执行 `keyTyped(...)` 内的快捷栏和热键逻辑。
 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainerKeyTypedIsolation {

    /**
     * 在容器页按键处理开头直接拦截，避免数字键等热键继续落到快捷栏切换逻辑。
     *
     * @param typedChar 输入字符
     * @param keyCode 键码
     * @param ci Mixin 回调
     */
    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void qzuilib$cancelContainerKeyTypedWhenHudCaptures(char typedChar, int keyCode, CallbackInfo ci) {
        if (UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured()) {
            ci.cancel();
        }
    }
}
