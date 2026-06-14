package club.heiqi.uilib.ui.screen;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import club.heiqi.uilib.ui.render.BackdropBlurConfig;
import club.heiqi.uilib.ui.render.BackdropBlurPolicy;

/**
 * 宿主级背景模糊渲染器。
 *
 * <p>该运行时只关心“在 UI 主渲染开始前，把当前屏幕背景捕获并模糊后绘制到主 UI FBO”，
 * 不向 `Widget`、文档页或控制器暴露任何 effect/backdrop 语义。</p>
 */
final class UiHostBackgroundBlurRenderer {

    private static final float[][] BLUR_SAMPLES = new float[][] {
            { 0.0F, 0.0F, 2.0F },
            { -3.0F, 0.0F, 2.0F },
            { 3.0F, 0.0F, 2.0F },
            { 0.0F, -3.0F, 2.0F },
            { 0.0F, 3.0F, 2.0F },
            { -3.0F, -3.0F, 1.5F },
            { 3.0F, -3.0F, 1.5F },
            { -3.0F, 3.0F, 1.5F },
            { 3.0F, 3.0F, 1.5F }
    };

    private int capturedBackgroundTextureId;
    private int capturedWidth;
    private int capturedHeight;

    /**
     * 捕获当前已绘制到屏幕的背景。
     *
     * <p>仅在宿主级背景模糊实际启用时才执行全屏快照；策略禁用时直接跳过，
     * 避免每帧白白做一次全分辨率 {@code glCopyTexSubImage2D} 拷贝。</p>
     *
     * @param nativeWidth 原生宽度
     * @param nativeHeight 原生高度
     * @param backdropBlurPolicy 页面级背景模糊策略，为 null 时继承全局配置
     */
    void captureCurrentFramebuffer(int nativeWidth, int nativeHeight, BackdropBlurPolicy backdropBlurPolicy) {
        if (nativeWidth <= 0 || nativeHeight <= 0) {
            return;
        }
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        BackdropBlurPolicy policy = backdropBlurPolicy == null ? BackdropBlurPolicy.inheritGlobal()
                : backdropBlurPolicy;
        if (!policy.resolveHostBackgroundBlurEnabled(config)) {
            return;
        }
        ensureTexture(nativeWidth, nativeHeight);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, capturedBackgroundTextureId);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, nativeWidth, nativeHeight);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /**
     * 将捕获到的背景以宿主级模糊底图的形式绘制到当前已绑定的 FBO。
     *
     * @param nativeWidth 原生宽度
     * @param nativeHeight 原生高度
     */
    void drawBlurredBackground(int nativeWidth, int nativeHeight, BackdropBlurPolicy backdropBlurPolicy) {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        BackdropBlurPolicy policy = backdropBlurPolicy == null ? BackdropBlurPolicy.inheritGlobal()
                : backdropBlurPolicy;
        if (!policy.resolveHostBackgroundBlurEnabled(config) || capturedBackgroundTextureId == 0
                || nativeWidth <= 0 || nativeHeight <= 0) {
            return;
        }

        float totalWeight = 0.0F;
        for (float[] sample : BLUR_SAMPLES) {
            totalWeight += sample[2];
        }
        if (totalWeight <= 0.0F) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, capturedBackgroundTextureId);

            float strength = policy.resolveHostBackgroundBlurStrength(config);
            for (float[] sample : BLUR_SAMPLES) {
                float weight = sample[2] / totalWeight;
                GL11.glColor4f(weight, weight, weight, weight);
                float offsetX = sample[0] * strength / nativeWidth;
                float offsetY = sample[1] * strength / nativeHeight;
                drawFullscreenQuad(nativeWidth, nativeHeight, offsetX, offsetY);
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * 释放背景捕获纹理。
     */
    void close() {
        if (capturedBackgroundTextureId != 0) {
            GL11.glDeleteTextures(capturedBackgroundTextureId);
            capturedBackgroundTextureId = 0;
        }
        capturedWidth = 0;
        capturedHeight = 0;
    }

    private void ensureTexture(int nativeWidth, int nativeHeight) {
        if (capturedBackgroundTextureId == 0) {
            capturedBackgroundTextureId = GL11.glGenTextures();
        }
        if (capturedWidth == nativeWidth && capturedHeight == nativeHeight) {
            return;
        }

        capturedWidth = nativeWidth;
        capturedHeight = nativeHeight;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, capturedBackgroundTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, nativeWidth, nativeHeight, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private void drawFullscreenQuad(int nativeWidth, int nativeHeight, float offsetU, float offsetV) {
        float leftU = offsetU;
        float rightU = 1.0F + offsetU;
        float topV = 1.0F + offsetV;
        float bottomV = offsetV;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0.0D, nativeHeight, 0.0D, leftU, bottomV);
        tessellator.addVertexWithUV(nativeWidth, nativeHeight, 0.0D, rightU, bottomV);
        tessellator.addVertexWithUV(nativeWidth, 0.0D, 0.0D, rightU, topV);
        tessellator.addVertexWithUV(0.0D, 0.0D, 0.0D, leftU, topV);
        tessellator.draw();
    }
}
