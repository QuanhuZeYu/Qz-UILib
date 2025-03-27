package club.heiqi.qz_uilib.mixins.early;

import club.heiqi.qz_uilib.hook.BeforeSwapBufferEvent;
import club.heiqi.qz_uilib.shader.ShaderManager;
import club.heiqi.qz_uilib.shader.ShaderName;
import club.heiqi.qz_uilib.skija.FrameBuffer;
import club.heiqi.qz_uilib.skija.GLCanvas;
import club.heiqi.qz_uilib.skija.gui.TestGUI;
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
        /*new ShaderManager(ShaderName.VOID_SHADER.name(),"shaders/void.vert","shaders/void.frag");
        LOG.info("初始化成功");*/
    }

    @Inject(
        method = "resize",
        at = @At("RETURN")
    )
    public void qz_blockinfo$resize(int width, int height, CallbackInfo ci) {
        LOG.info("窗口正在缩放 {} {} | frame数量: {} glCanvas数量: {}", width, height,
            FrameBuffer.GLOBAL.size(), GLCanvas.GLOBALS.size());
        for (FrameBuffer frameBuffer : FrameBuffer.GLOBAL) {
            frameBuffer.resize(width, height);
        }
        for (GLCanvas glCanvas : GLCanvas.GLOBALS) {
            glCanvas.resize();
        }
    }

    @Inject(
        method = "runTick",
        at = @At("RETURN")
    )
    public void qz_blockinfo$runTick(CallbackInfo ci) {
        if (Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            Minecraft.getMinecraft().displayGuiScreen(new TestGUI());
        }
    }

    @Inject(
        method = "func_147120_f",
        at = @At("HEAD")
    )
    public void qz_blockinfo$beforeSwapBuffer(CallbackInfo ci) {
        BeforeSwapBufferEvent.run();
    }
}
