package club.heiqi.uilib.mixin.early;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.heiqi.uilib.ui.hud.UiHudDocumentHost;
import club.heiqi.uilib.ui.input.UiKeyboardCaptureState;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputService;
import club.heiqi.uilib.ui.screen.BaseScreen;
import net.minecraft.client.gui.GuiScreen;

/**
 * 当 UILib 已接管键盘时，阻断宿主原生界面继续处理同一批按键事件。
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreenKeyboardIsolation {

    /**
     * 在原生键盘处理开始前做一次统一拦截，避免同一按键同时命中 UILib 与宿主输入框。
     *
     * @param ci Mixin 回调
     */
    @Inject(method = "handleKeyboardInput", at = @At("HEAD"), cancellable = true)
    private void qzuilib$cancelNativeKeyboardWhenUiLibCaptures(CallbackInfo ci) {
        if (((Object) this) instanceof BaseScreen) {
            return;
        }
        UiInputFrame immediateFrame = UiInputService.getInstance().createImmediateKeyboardFrame();
        if (UiHudDocumentHost.getInstance().handleImmediateKeyboardInput((GuiScreen) (Object) this, immediateFrame)) {
            int keyCode = immediateFrame == null || immediateFrame.getKeyEvents().isEmpty() ? 0
                    : immediateFrame.getKeyEvents().get(0).getKeyCode();
            club.heiqi.uilib.ui.event.UiKeyEvent.Action action = immediateFrame == null || immediateFrame.getKeyEvents().isEmpty()
                    ? null
                    : immediateFrame.getKeyEvents().get(0).getAction();
            String collectedText = immediateFrame == null || immediateFrame.getTextEvents().isEmpty() ? null
                    : immediateFrame.getTextEvents().get(0).getText();
            UiInputService.getInstance().suppressNextCollectedKeyboardEvent(keyCode, action, collectedText);
            ci.cancel();
            return;
        }
        if (UiKeyboardCaptureState.getInstance().shouldCancelNativeKeyboardInput()) {
            ci.cancel();
        }
    }
}
