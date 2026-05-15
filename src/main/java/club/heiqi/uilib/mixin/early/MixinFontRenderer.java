package club.heiqi.uilib.mixin.early;

import java.util.List;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.FontSplashReloadGuard;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.event.FontReloadRequest;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.IResourceManager;
import org.lwjgl.opengl.GL11;
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
        if (FontSplashReloadGuard.shouldSkipResourceReload()) {
            MyMod.LOG.info("SplashProgress 绘制阶段跳过 UILib 字体资源重载请求");
            return;
        }
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
                qzuilib$applyVanillaFontPostRenderState();
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
                int width = DefaultFontRendererAdapter.getInstance().drawString(text, x, y, color, false);
                qzuilib$applyVanillaFontPostRenderState();
                cir.setReturnValue(Integer.valueOf(width));
            } catch (RuntimeException exception) {
                qzuilib$logFontPipelineFailure(exception);
            }
        }
    }

    @Inject(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true)
    public void drawString(String text, int x, int y, int color, boolean dropShadow, CallbackInfoReturnable<Integer> cir) {
        if (FontConfig.replaceOrigin) {
            try {
                int width = DefaultFontRendererAdapter.getInstance().drawString(text, x, y, color, dropShadow);
                qzuilib$applyVanillaFontPostRenderState();
                cir.setReturnValue(Integer.valueOf(width));
            } catch (RuntimeException exception) {
                qzuilib$logFontPipelineFailure(exception);
            }
        }
    }

    @Inject(method = "drawStringWithShadow", at = @At("HEAD"), cancellable = true)
    public void drawStringWithShadow(String text, int x, int y, int color, CallbackInfoReturnable<Integer> cir) {
        if (FontConfig.replaceOrigin) {
            try {
                int width = DefaultFontRendererAdapter.getInstance().drawString(text, x, y, color, true);
                qzuilib$applyVanillaFontPostRenderState();
                cir.setReturnValue(Integer.valueOf(width));
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

    private static void qzuilib$applyVanillaFontPostRenderState() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
