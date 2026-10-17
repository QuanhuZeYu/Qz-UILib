package club.heiqi.uilib.host.neoforge263.mixin;

import club.heiqi.uilib.host.neoforge263.NeoForgeSceneScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 保留每个原生 MOVE 与按钮/键盘相对顺序，原版只在帧前递交合并后的移动。 */
@Mixin(value = MouseHandler.class, remap = false)
abstract class MouseHandlerMixin {
    @Inject(method = "onMove", at = @At("TAIL"), remap = false)
    private void uilib$nativeMove(long window, double x, double y, double deltaX, double deltaY, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (window == minecraft.getWindow().handle() && minecraft.gui.screen() instanceof NeoForgeSceneScreen screen)
            screen.nativeMouseMoved(x, y);
    }
}
