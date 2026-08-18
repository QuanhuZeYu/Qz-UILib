package club.heiqi.uilib.mixin.early;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import club.heiqi.uilib.font.FontService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.WorldSettings;

/**
 * 世界加载期字体上传泵注入。
 *
 * <p>{@code launchIntegratedServer} 在等待服务端进入 run loop 期间主线程空转（200ms sleep 循环），
 * 渲染帧完全停摆，帧驱动的字符页批上传（RenderTick START）随之静默；本 mixin 在该等待循环的每次
 * 迭代泵送一批待上传 glyph，并在 {@code loadWorld} 入口（chunk 渲染器同步构建前）再泵一次排空残余，
 * 使进入世界第一帧渲染时文字纹理已就绪。</p>
 */
@Mixin(value = Minecraft.class, priority = 900)
public abstract class MixinMinecraftWorldLoadPump {

    @Inject(method = "launchIntegratedServer(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V"), require = 1)
    private void qzuilib$pumpUploadsDuringServerStartup(String folderName, String worldName,
            WorldSettings worldSettings, CallbackInfo ci) {
        FontService.getInstance().pumpWorldLoadUploads();
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
            at = @At("HEAD"), require = 1)
    private void qzuilib$pumpUploadsBeforeRenderGlobalLoad(WorldClient worldClient, String loadingMessage,
            CallbackInfo ci) {
        FontService.getInstance().pumpWorldLoadUploads();
    }
}
