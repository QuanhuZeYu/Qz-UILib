package club.heiqi.uilib.host.neoforge263.mixin;

import club.heiqi.uilib.host.neoforge263.NeoForgeSceneScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.PreeditEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原版会拆成逐码点 CharacterEvent；只为自有页面保留 SDL 的一次完整提交。 */
@Mixin(value = KeyboardHandler.class, remap = false)
abstract class KeyboardHandlerMixin {
    @Shadow public abstract void textEditing(long window, PreeditEvent event);

    @Inject(method = "textInput", at = @At("HEAD"), cancellable = true, remap = false)
    private void uilib$committedText(long window, String text, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (window == minecraft.getWindow().handle() && minecraft.gui.overlay() == null
                && minecraft.gui.screen() instanceof NeoForgeSceneScreen screen) {
            // 同时保持 KeyboardHandler.lastPreeditEvent 的原版清空语义。
            textEditing(window, null);
            screen.committedText(text);
            callback.cancel();
        }
    }
}
