package club.heiqi.qz_blockinfo.mixins.early;

import club.heiqi.qz_blockinfo.ClientProxy;
import club.heiqi.skija.GLCanvas;
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
        ClientProxy.glCanvas = new GLCanvas();
        System.out.println("初始化成功");
    }
}
