package club.heiqi.uilib.host.neoforge263.mixin;

import club.heiqi.uilib.host.neoforge263.NeoForgeSceneScreen;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 失焦与 SDL key/text 使用同一客户端递交入口，不从绘制轮询跨越原生历史。 */
@Mixin(value = Window.class, remap = false)
abstract class WindowMixin {
    @Shadow public abstract long handle();

    @Inject(method = "onFocus", at = @At("TAIL"), remap = false)
    private void uilib$windowFocus(boolean focused, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getWindow().handle() == handle()
                && minecraft.gui.screen() instanceof NeoForgeSceneScreen screen) {
            minecraft.execute(() -> {
                if (minecraft.gui.screen() == screen) screen.nativeFocus(focused);
            });
        }
    }
}
