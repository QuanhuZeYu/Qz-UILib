package club.heiqi.uilib.mixin.early;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.inventory.GuiContainer;

import club.heiqi.uilib.ui.container.experimental.minecraft.GuiContainerScenePhaseHook;

/** 为显式 attach 的 `GuiContainer` 提供 scene phase 与唯一输入 owner callback。 */
@Mixin(GuiContainer.class)
public abstract class MixinGuiContainerScenePhases {
    @Inject(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerBackgroundLayer(FII)V",
            shift = At.Shift.AFTER), require = 0)
    private void qzuilib$paintSceneMain(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiContainerScenePhaseHook.afterBackground((GuiContainer) (Object) this, mouseX, mouseY, partialTicks);
    }

    @Inject(method = "drawScreen", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/inventory/GuiContainer;draggedStack:Lnet/minecraft/item/ItemStack;",
            opcode = Opcodes.GETFIELD, ordinal = 0), require = 0)
    private void qzuilib$paintSceneOverlay(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiContainerScenePhaseHook.beforeCarried((GuiContainer) (Object) this);
    }

    @Inject(method = "drawScreen", at = @At("RETURN"), require = 0)
    private void qzuilib$finishSceneFrame(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiContainerScenePhaseHook.afterDraw((GuiContainer) (Object) this);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void qzuilib$claimPointerDown(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (GuiContainerScenePhaseHook.pointerDown((GuiContainer) (Object) this, mouseX, mouseY, button)) ci.cancel();
    }

    @Inject(method = "mouseClickMove", at = @At("HEAD"), cancellable = true, require = 0)
    private void qzuilib$dispatchClaimedMove(int mouseX, int mouseY, int button, long elapsed, CallbackInfo ci) {
        if (GuiContainerScenePhaseHook.pointerMove((GuiContainer) (Object) this, mouseX, mouseY)) ci.cancel();
    }

    @Inject(method = "mouseMovedOrUp", at = @At("HEAD"), cancellable = true, require = 0)
    private void qzuilib$dispatchClaimedUp(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button >= 0 && GuiContainerScenePhaseHook.pointerUp(
                (GuiContainer) (Object) this, mouseX, mouseY, button)) ci.cancel();
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true, require = 0)
    private void qzuilib$claimKey(char typedChar, int keyCode, CallbackInfo ci) {
        if (GuiContainerScenePhaseHook.keyTyped((GuiContainer) (Object) this, keyCode)) ci.cancel();
    }

    @Inject(method = "onGuiClosed", at = @At("HEAD"), require = 0)
    private void qzuilib$disposeSceneHost(CallbackInfo ci) {
        GuiContainerScenePhaseHook.close((GuiContainer) (Object) this);
    }
}
