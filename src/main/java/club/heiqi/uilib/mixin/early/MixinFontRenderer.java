package club.heiqi.uilib.mixin.early;

import java.util.List;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
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

    private static boolean qzuilib$fontPipelineFailureLogged;

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    public void onResourceManagerReload(IResourceManager resourceManager, CallbackInfo ci) {
        FontService.getInstance().reload(new FontReloadRequest("resource_manager_reload"));
        if (FontConfig.replaceOrigin) {
            DefaultFontRendererAdapter.getInstance();
        }
    }

    @Inject(method = "drawSplitString", at = @At("HEAD"), cancellable = true)
    public void drawSplitString(String text, int x, int y, int wrapWidth, int textColor, CallbackInfo ci) {
        if (FontConfig.replaceOrigin) {
            try {
                DefaultFontRendererAdapter.getInstance().drawSplitString(text, x, y, wrapWidth, textColor);
                ci.cancel();
            } catch (RuntimeException exception) {
                qzuilib$logFontPipelineFailure(exception);
            }
        }
    }

    @Inject(method = "drawString(Ljava/lang/String;III)I", at = @At("HEAD"), cancellable = true)
    public void drawString(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (FontConfig.replaceOrigin) {
            try {
                cir.setReturnValue(DefaultFontRendererAdapter.getInstance().drawString(text, x, y, color, false));
            } catch (RuntimeException exception) {
                qzuilib$logFontPipelineFailure(exception);
            }
        }
    }

    @Inject(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true)
    public void drawString(String text, int x, int y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (FontConfig.replaceOrigin) {
            try {
                cir.setReturnValue(DefaultFontRendererAdapter.getInstance().drawString(text, x, y, color, dropShadow));
            } catch (RuntimeException exception) {
                qzuilib$logFontPipelineFailure(exception);
            }
        }
    }

    @Inject(method = "drawStringWithShadow", at = @At("HEAD"), cancellable = true)
    public void drawStringWithShadow(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (FontConfig.replaceOrigin) {
            try {
                cir.setReturnValue(DefaultFontRendererAdapter.getInstance().drawString(text, x, y, color, true));
            } catch (RuntimeException exception) {
                qzuilib$logFontPipelineFailure(exception);
            }
        }
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    public void getStringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (FontConfig.replaceOrigin) {
            try {
                cir.setReturnValue(DefaultFontRendererAdapter.getInstance().getStringWidth(text));
            } catch (RuntimeException exception) {
                qzuilib$logFontPipelineFailure(exception);
            }
        }
    }

    @Inject(method = "listFormattedStringToWidth", at = @At("HEAD"), cancellable = true)
    public void listFormattedStringToWidth(String text, int wrapWidth, CallbackInfoReturnable<List<String>> cir) {
        if (FontConfig.replaceOrigin) {
            try {
                cir.setReturnValue(DefaultFontRendererAdapter.getInstance().listFormattedStringToWidth(text, wrapWidth));
            } catch (RuntimeException exception) {
                qzuilib$logFontPipelineFailure(exception);
            }
        }
    }

    @Inject(method = "splitStringWidth", at = @At("HEAD"), cancellable = true)
    public void splitStringWidth(String text, int wrapWidth, CallbackInfoReturnable<Integer> cir) {
        if (FontConfig.replaceOrigin) {
            try {
                cir.setReturnValue(DefaultFontRendererAdapter.getInstance().splitStringWidth(text, wrapWidth));
            } catch (RuntimeException exception) {
                qzuilib$logFontPipelineFailure(exception);
            }
        }
    }

    @Inject(method = "trimStringToWidth(Ljava/lang/String;I)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    public void trimStringToWidth(String text, int width, CallbackInfoReturnable<String> cir) {
        if (FontConfig.replaceOrigin) {
            try {
                cir.setReturnValue(DefaultFontRendererAdapter.getInstance().trimStringToWidth(text, width));
            } catch (RuntimeException exception) {
                qzuilib$logFontPipelineFailure(exception);
            }
        }
    }

    @Inject(method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    public void trimStringToWidth(String text, int width, boolean reverse, CallbackInfoReturnable<String> cir) {
        if (FontConfig.replaceOrigin) {
            try {
                cir.setReturnValue(DefaultFontRendererAdapter.getInstance().trimStringToWidth(text, width, reverse));
            } catch (RuntimeException exception) {
                qzuilib$logFontPipelineFailure(exception);
            }
        }
    }

    private static void qzuilib$logFontPipelineFailure(RuntimeException exception) {
        if (qzuilib$fontPipelineFailureLogged) {
            return;
        }
        qzuilib$fontPipelineFailureLogged = true;
        MyMod.LOG.error("UILib 字体管线接管失败，本次调用回落原版 FontRenderer。", exception);
    }
}
