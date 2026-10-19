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
    @org.spongepowered.asm.mixin.Shadow
    private java.util.List<net.minecraft.client.resources.IResourcePack> defaultResourcePacks;

    @Inject(method = "refreshResources", at = @At("HEAD"))
    private void qz$registerCoreAssets(CallbackInfo callback) {
        club.heiqi.uilib.host.client.font.CoreAssetResourcePack.install(defaultResourcePacks);
    }

    // LWJGL3ify排除org.lwjgl类转换；此处仅转交present，不使用MC尺寸/绘制包装。
    @Inject(
            method = "func_147120_f",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lorg/lwjgl/opengl/Display;update()V",
                            remap = false))
    private void qz$presentFontResources(CallbackInfo callback) {
        club.heiqi.uilib.host.client.image.MinecraftImageServices.prepareResources();
        MinecraftFontServices.beforePresent();
    }

    @Inject(method = "shutdownMinecraftApplet", at = @At("HEAD"))
    private void qz$closeFontServices(CallbackInfo callback) {
        // 页面先结束输入与所属资源，再结束其借用的应用服务；页面宿主规格第1节。
        Throwable failure = null;
        try {
            net.minecraft.client.gui.GuiScreen screen = Minecraft.getMinecraft().currentScreen;
            if (screen instanceof club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen)
                screen.onGuiClosed();
        } catch (RuntimeException | Error next) { failure = next; }
        try {
            club.heiqi.uilib.host.client.image.MinecraftImageServices.closeShared();
        } catch (RuntimeException | Error next) {
            if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next);
        }
        try {
            MinecraftFontServices.closeShared();
        } catch (RuntimeException | Error next) {
            if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next);
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }
}
