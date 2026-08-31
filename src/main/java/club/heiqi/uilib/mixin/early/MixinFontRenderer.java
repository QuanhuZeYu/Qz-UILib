package club.heiqi.uilib.mixin.early;

import java.util.List;

import club.heiqi.uilib.font.FontRendererFallbackInvoker;
import club.heiqi.uilib.font.FontRendererFallbackInvoker.InvocationResult;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.event.FontReloadRequest;
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
            // 接管即替换：handled 分支内显式 cancel，原版 renderString 不再执行；
            // 原版尾状态由 FontRendererFallbackInvoker.applyVanillaDrawStringTailState 幂等补齐。
            cir.cancel();
        }
    }

    @Inject(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true)
    public void drawString(String text, int x, int y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        InvocationResult<Integer> result = qzuilib$fontInvoker.drawString(text, x, y, color, dropShadow);
        if (result.isHandled()) {
            cir.setReturnValue(result.getValue());
            // 阴影变体同理：handled 分支内显式 cancel，避免原版 renderString 再执行一遍。
            cir.cancel();
        }
    }

    @Inject(method = "drawStringWithShadow", at = @At("HEAD"), cancellable = true)
    public void drawStringWithShadow(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        InvocationResult<Integer> result = qzuilib$fontInvoker.drawString(text, x, y, color, true);
        if (result.isHandled()) {
            cir.setReturnValue(result.getValue());
            // drawStringWithShadow 原版嵌套 drawString(..., true)：外层不 cancel 则阴影+正文整条
            // 原版链路会继续执行，与 UILib 已有绘制叠加；handled 分支内显式 cancel 一刀切断。
            cir.cancel();
        }
    }

    // 度量注入点逐点结论：getStringWidth / listFormattedStringToWidth / splitStringWidth /
    // trimStringToWidth×2 均纯度量、无 GL 副作用，即使 handled 也只 setReturnValue、有意不 cancel——
    // 原版方法体执行无任何绘制输出，cancel 只会额外缩小与依赖原版体副作用的其他注入共存面。
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
