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
    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void qz$closeFontServices(CallbackInfo callback) { MinecraftFontServices.closeShared(); }
}
