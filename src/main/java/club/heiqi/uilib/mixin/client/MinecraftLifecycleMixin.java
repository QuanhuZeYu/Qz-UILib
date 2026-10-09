package club.heiqi.uilib.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import club.heiqi.uilib.host.client.font.MinecraftFontServices;

/** 在宿主GL上下文销毁之前，同一client线程释放字体owner；不能用shutdownhook代替。 */
@Mixin(Minecraft.class)
public abstract class MinecraftLifecycleMixin {
    // LWJGL3ify排除org.lwjgl类转换；此处仅转交present，不使用MC尺寸/绘制包装。
    @Inject(method = "func_147120_f", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V", remap = false))
    private void qz$presentFontResources(CallbackInfo callback) { club.heiqi.uilib.host.client.image.MinecraftImageServices.prepareResources(); MinecraftFontServices.beforePresent(); }
    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void qz$closeFontServices(CallbackInfo callback) { try { club.heiqi.uilib.host.client.image.MinecraftImageServices.closeShared(); } finally { MinecraftFontServices.closeShared(); } }
}
