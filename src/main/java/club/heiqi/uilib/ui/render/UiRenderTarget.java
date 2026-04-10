package club.heiqi.uilib.ui.render;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * UI 原生分辨率离屏渲染目标。
 */
public class UiRenderTarget {

    private final IntBuffer previousViewport = BufferUtils.createIntBuffer(16);

    private int framebufferId;
    private int colorTextureId;
    private int depthStencilRenderbufferId;
    private int width;
    private int height;
    private int previousFramebufferId;

    /**
     * 确保离屏目标尺寸与当前窗口一致。
     *
     * @param width 原生窗口宽度
     * @param height 原生窗口高度
     */
    public void ensureSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (framebufferId == 0) {
            initialize(width, height);
            return;
        }
        if (this.width != width || this.height != height) {
            resize(width, height);
        }
    }

    /**
     * 绑定离屏目标并记录先前状态。
     */
    public void begin() {
        previousViewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);
        previousFramebufferId = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
        GL11.glViewport(0, 0, width, height);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
    }

    /**
     * 恢复先前 FBO 与视口。
     */
    public void end() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebufferId);
        GL11.glViewport(previousViewport.get(0), previousViewport.get(1), previousViewport.get(2), previousViewport.get(3));
        previousFramebufferId = 0;
    }

    /**
     * 将离屏结果按 MC 当前 GUI 视口绘制到屏幕。
     *
     * @param guiWidth GUI 逻辑宽度
     * @param guiHeight GUI 逻辑高度
     */
    public void drawToScreen(int guiWidth, int guiHeight) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureId);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0.0D, guiHeight, 0.0D, 0.0D, 0.0D);
        tessellator.addVertexWithUV(guiWidth, guiHeight, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(guiWidth, 0.0D, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(0.0D, 0.0D, 0.0D, 0.0D, 1.0D);
        tessellator.draw();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /**
     * 释放离屏目标资源。
     */
    public void close() {
        if (colorTextureId != 0) {
            GL11.glDeleteTextures(colorTextureId);
            colorTextureId = 0;
        }
        if (depthStencilRenderbufferId != 0) {
            GL30.glDeleteRenderbuffers(depthStencilRenderbufferId);
            depthStencilRenderbufferId = 0;
        }
        if (framebufferId != 0) {
            GL30.glDeleteFramebuffers(framebufferId);
            framebufferId = 0;
        }
        width = 0;
        height = 0;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private void initialize(int width, int height) {
        framebufferId = GL30.glGenFramebuffers();
        colorTextureId = GL11.glGenTextures();
        depthStencilRenderbufferId = GL30.glGenRenderbuffers();
        this.width = width;
        this.height = height;
        allocateAttachments();
    }

    private void resize(int width, int height) {
        this.width = width;
        this.height = height;
        allocateAttachments();
    }

    private void allocateAttachments() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                colorTextureId, 0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencilRenderbufferId);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER, depthStencilRenderbufferId);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            throw new IllegalStateException("UI 离屏渲染目标创建失败，状态码=" + status);
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }
}
