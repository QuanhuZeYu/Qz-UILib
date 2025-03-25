package club.heiqi.qz_blockinfo.mixins.early;

import club.heiqi.qz_blockinfo.ClientProxy;
import club.heiqi.skija.GLCanvas;
import club.heiqi.skija.gui.BaseGUI;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Unique
    Logger LOG = LogManager.getLogger();

    @Inject(
        method = "startGame",
        at = @At("RETURN")
    )
    public void qz_blockinfo$startGame(CallbackInfo ci) {
        ClientProxy.canvas = new GLCanvas();
        LOG.info("初始化成功");
    }

    @Inject(
        method = "resize",
        at = @At("RETURN")
    )
    public void qz_blockinfo$resize(int width, int height, CallbackInfo ci) {
        if (ClientProxy.canvas == null) return;
        ClientProxy.canvas.dispose();
        ClientProxy.canvas = new GLCanvas();
    }

    @Inject(
        method = "runTick",
        at = @At("RETURN")
    )
    public void qz_blockinfo$runTick(CallbackInfo ci) {
        if (Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            Minecraft.getMinecraft().displayGuiScreen(new BaseGUI());
        }
    }
}
