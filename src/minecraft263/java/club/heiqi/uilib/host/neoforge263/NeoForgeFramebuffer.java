package club.heiqi.uilib.host.neoforge263;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL20C.GL_MAX_DRAW_BUFFERS;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlTextureView;

/** Post 位于主贴图 blit 之前；宿主 render pass 已解绑，不能向默认 FBO 回放。 */
final class NeoForgeFramebuffer implements AutoCloseable {
    private int framebuffer;
    private GlTextureView attachedColor;

    void render(RenderTarget target, Runnable replay) {
        if (!(target.getColorTextureView() instanceof GlTextureView color))
            throw new IllegalStateException("UILib 26.3 需要当前实例的 OpenGL 图形后端");
        int read = GlStateManager.getFrameBuffer(GL_READ_FRAMEBUFFER);
        int draw = GlStateManager.getFrameBuffer(GL_DRAW_FRAMEBUFFER);
        int[] viewport = new int[4], scissor = new int[4], mask = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissor);
        glGetIntegerv(GL_COLOR_WRITEMASK, mask);
        boolean scissorEnabled = glIsEnabled(GL_SCISSOR_TEST), stencil = glIsEnabled(GL_STENCIL_TEST), depth = glIsEnabled(GL_DEPTH_TEST);
        boolean[] blendTargets = new boolean[glGetInteger(GL_MAX_DRAW_BUFFERS)];
        for (int i = 0; i < blendTargets.length; i++) blendTargets[i] = glIsEnabledi(GL_BLEND, i);
        try {
            if (framebuffer == 0) framebuffer = glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            // resize 会关闭旧纹理并新建 view；按资源身份更新，GL 数字 id 可被复用。
            if (attachedColor != color) {
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color.glId(), color.fboMipLevel());
                if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("UILib 主目标颜色附件不完整");
                attachedColor = color;
            }
            GlStateManager._viewport(0, 0, target.width, target.height);
            GlStateManager._disableScissorTest();
            GlStateManager._disableStencilTest();
            GlStateManager._disableDepthTest();
            GlStateManager._colorMask(0, 15);
            replay.run();
        } finally {
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            // 共同二维 backend 使用全局 blend enable；恢复各目标，保持宿主 indexed 缓存有效。
            for (int i = 0; i < blendTargets.length; i++) {
                if (blendTargets[i]) glEnablei(GL_BLEND, i); else glDisablei(GL_BLEND, i);
            }
            GlStateManager._scissorBox(scissor[0], scissor[1], scissor[2], scissor[3]);
            if (scissorEnabled) GlStateManager._enableScissorTest(); else GlStateManager._disableScissorTest();
            if (stencil) GlStateManager._enableStencilTest(); else GlStateManager._disableStencilTest();
            if (depth) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
            GlStateManager._colorMask(0, (mask[0] != 0 ? 1 : 0) | (mask[1] != 0 ? 2 : 0) | (mask[2] != 0 ? 4 : 0) | (mask[3] != 0 ? 8 : 0));
        }
    }

    @Override public void close() {
        if (framebuffer != 0) { GlStateManager._glDeleteFramebuffers(framebuffer); framebuffer = 0; }
        attachedColor = null;
    }
}
