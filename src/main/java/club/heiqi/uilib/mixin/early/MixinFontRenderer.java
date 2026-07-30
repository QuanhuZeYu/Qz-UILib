package club.heiqi.uilib.mixin.early;

import java.util.List;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontRendererFallbackInvoker;
import club.heiqi.uilib.font.FontRendererFallbackInvoker.InvocationResult;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.FontSplashReloadGuard;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.event.FontReloadRequest;
import club.heiqi.uilib.internal.image.HostImageResourceEpoch;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版字体渲染替换注入。
 */
@Mixin(value = FontRenderer.class, priority = 999)
public abstract class MixinFontRenderer {

    private static final FontRendererFallbackInvoker qzuilib$fontInvoker =
            FontRendererFallbackInvoker.getInstance();

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    public void onResourceManagerReload(IResourceManager resourceManager, CallbackInfo ci) {
        HostImageResourceEpoch.advance();
        if (FontSplashReloadGuard.shouldSkipResourceReload()) {
            MyMod.LOG.info("SplashProgress 绘制阶段跳过 UILib 字体资源重载请求");
            return;
        }
        FontService.getInstance().reload(new FontReloadRequest("resource_manager_reload"));
        if (FontConfig.replaceOrigin) {
            qzuilib$fontInvoker.warmUpAdapterIfNeeded();
        }
    }

    @Inject(method = "drawSplitString", at = @At("HEAD"), cancellable = true)
    public void drawSplitString(String text, int x, int y, int wrapWidth, int textColor, CallbackInfo ci) {
        if (qzuilib$fontInvoker.drawSplitString(text, x, y, wrapWidth, textColor)) {
            ci.cancel();
        }
    }

    @Inject(method = "drawString(Ljava/lang/String;III)I", at = @At("HEAD"), cancellable = true)
    public void drawString(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        InvocationResult<Integer> result = qzuilib$fontInvoker.drawString(text, x, y, color, false);
        if (result.isHandled()) {
            cir.setReturnValue(result.getValue());
        }
    }

    @Inject(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true)
    public void drawString(String text, int x, int y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        InvocationResult<Integer> result = qzuilib$fontInvoker.drawString(text, x, y, color, dropShadow);
        if (result.isHandled()) {
            cir.setReturnValue(result.getValue());
        }
    }

    @Inject(method = "drawStringWithShadow", at = @At("HEAD"), cancellable = true)
    public void drawStringWithShadow(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        InvocationResult<Integer> result = qzuilib$fontInvoker.drawString(text, x, y, color, true);
        if (result.isHandled()) {
            cir.setReturnValue(result.getValue());
        }
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    public void getStringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        InvocationResult<Integer> result = qzuilib$fontInvoker.getStringWidth(text);
        if (result.isHandled()) {
            cir.setReturnValue(result.getValue());
        }
    }

    @Inject(method = "listFormattedStringToWidth", at = @At("HEAD"), cancellable = true)
    public void listFormattedStringToWidth(String text, int wrapWidth, CallbackInfoReturnable<List<String>> cir) {
        InvocationResult<List<String>> result = qzuilib$fontInvoker.listFormattedStringToWidth(text, wrapWidth);
        if (result.isHandled()) {
            cir.setReturnValue(result.getValue());
        }
    }

    @Inject(method = "splitStringWidth", at = @At("HEAD"), cancellable = true)
    public void splitStringWidth(String text, int wrapWidth, CallbackInfoReturnable<Integer> cir) {
        InvocationResult<Integer> result = qzuilib$fontInvoker.splitStringWidth(text, wrapWidth);
        if (result.isHandled()) {
            cir.setReturnValue(result.getValue());
        }
    }

    @Inject(method = "trimStringToWidth(Ljava/lang/String;I)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    public void trimStringToWidth(String text, int width, CallbackInfoReturnable<String> cir) {
        InvocationResult<String> result = qzuilib$fontInvoker.trimStringToWidth(text, width);
        if (result.isHandled()) {
            cir.setReturnValue(result.getValue());
        }
    }

    @Inject(method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    public void trimStringToWidth(String text, int width, boolean reverse, CallbackInfoReturnable<String> cir) {
        InvocationResult<String> result = qzuilib$fontInvoker.trimStringToWidth(text, width, reverse);
        if (result.isHandled()) {
            cir.setReturnValue(result.getValue());
        }
    }
}
