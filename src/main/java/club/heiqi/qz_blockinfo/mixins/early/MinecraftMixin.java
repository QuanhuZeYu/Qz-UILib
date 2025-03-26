package club.heiqi.qz_blockinfo.mixins.early;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
        LOG.info("初始化成功");
    }

    @Inject(
        method = "resize",
        at = @At("RETURN")
    )
    public void qz_blockinfo$resize(int width, int height, CallbackInfo ci) {
        /*LOG.info("窗口正在缩放 {} {} | frame数量: {} glCanvas数量: {}", width, height,
            FrameBuffer.GLOBAL.size(), GLCanvas.GLOBALS.size());
        for (FrameBuffer frameBuffer : FrameBuffer.GLOBAL) {
            frameBuffer.resize(width, height);
        }
        for (GLCanvas glCanvas : GLCanvas.GLOBALS) {
            glCanvas.resize();
        }*/
    }

    @Inject(
        method = "runTick",
        at = @At("RETURN")
    )
    public void qz_blockinfo$runTick(CallbackInfo ci) {
        /*if (Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            Minecraft.getMinecraft().displayGuiScreen(new BaseGUI());
        }*/
    }

    @Inject(
        method = "func_147120_f",
        at = @At("HEAD")
    )
    public void qz_blockinfo$beforeSwapBuffer(CallbackInfo ci) {
        /*BeforeSwapBufferEvent.run();*/
    }
}
