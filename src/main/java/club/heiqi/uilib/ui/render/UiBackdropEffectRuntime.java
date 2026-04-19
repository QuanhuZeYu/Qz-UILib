package club.heiqi.uilib.ui.render;

import java.util.List;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

/**
 * 宿主级 backdrop effect 运行时骨架。
 *
 * <p>当前版本只负责消费 effect 请求并执行占位型回放，
 * 为后续 shader / ping-pong blur 链路预留宿主级接缝。</p>
 */
public class UiBackdropEffectRuntime {

    /**
     * 在宿主提供的 effect FBO 中执行当前帧的 backdrop effect 请求。
     *
     * @param sourceTarget 主 UI 渲染目标
     * @param effectTarget effect 离屏目标
     * @param requests 当前帧 effect 请求
     * @param nativeWidth 原生宽度
     * @param nativeHeight 原生高度
     */
    public void execute(UiRenderTarget sourceTarget, UiRenderTarget effectTarget,
            List<UiRenderContext.BackdropEffectRequest> requests, int nativeWidth, int nativeHeight) {
        if (sourceTarget == null || effectTarget == null || requests == null || requests.isEmpty()) {
            return;
        }

        effectTarget.ensureSize(nativeWidth, nativeHeight);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        effectTarget.begin();
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            try {
                GL11.glLoadIdentity();
                GL11.glOrtho(0.0D, nativeWidth, nativeHeight, 0.0D, -1000.0D, 1000.0D);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                try {
                    GL11.glLoadIdentity();
                    for (UiRenderContext.BackdropEffectRequest request : requests) {
                        applyBackdropScissor(request, nativeHeight);
                        drawSourceRegion(sourceTarget, request, nativeWidth, nativeHeight);
                        drawTintOverlay(request);
                    }
                    UiRenderContext.clearClipState();
                } finally {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                }
            } finally {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(previousMatrixMode);
            }
        } finally {
            effectTarget.end();
            GL11.glMatrixMode(previousMatrixMode);
        }

        effectTarget.compositeToCurrentFramebuffer();
    }

    private void applyBackdropScissor(UiRenderContext.BackdropEffectRequest request, int nativeHeight) {
        int[] clipRect = request.getClipRect();
        int left = request.getLeft();
        int top = request.getTop();
        int right = request.getRight();
        int bottom = request.getBottom();
        if (clipRect != null) {
            left = Math.max(left, clipRect[0]);
            top = Math.max(top, clipRect[1]);
            right = Math.min(right, clipRect[2]);
            bottom = Math.min(bottom, clipRect[3]);
        }

        int width = Math.max(0, right - left);
        int height = Math.max(0, bottom - top);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glScissor(left, nativeHeight - bottom, width, height);
    }

    private void drawSourceRegion(UiRenderTarget sourceTarget, UiRenderContext.BackdropEffectRequest request, int nativeWidth,
            int nativeHeight) {
        if (sourceTarget.getColorTextureId() == 0 || nativeWidth <= 0 || nativeHeight <= 0) {
            return;
        }

        float u0 = request.getLeft() / (float) nativeWidth;
        float v0 = 1.0F - (request.getBottom() / (float) nativeHeight);
        float u1 = request.getRight() / (float) nativeWidth;
        float v1 = 1.0F - (request.getTop() / (float) nativeHeight);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTarget.getColorTextureId());

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(request.getLeft(), request.getBottom(), 0.0D, u0, v0);
        tessellator.addVertexWithUV(request.getRight(), request.getBottom(), 0.0D, u1, v0);
        tessellator.addVertexWithUV(request.getRight(), request.getTop(), 0.0D, u1, v1);
        tessellator.addVertexWithUV(request.getLeft(), request.getTop(), 0.0D, u0, v1);
        tessellator.draw();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private void drawTintOverlay(UiRenderContext.BackdropEffectRequest request) {
        int tintColor = request.getEffectSpec().tintColor;
        if (tintColor == 0) {
            return;
        }

        float alpha = ((tintColor >> 24) & 0xFF) / 255.0F;
        float red = ((tintColor >> 16) & 0xFF) / 255.0F;
        float green = ((tintColor >> 8) & 0xFF) / 255.0F;
        float blue = (tintColor & 0xFF) / 255.0F;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(red, green, blue, alpha);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(request.getLeft(), request.getBottom(), 0.0D);
        tessellator.addVertex(request.getRight(), request.getBottom(), 0.0D);
        tessellator.addVertex(request.getRight(), request.getTop(), 0.0D);
        tessellator.addVertex(request.getLeft(), request.getTop(), 0.0D);
        tessellator.draw();
    }
}
