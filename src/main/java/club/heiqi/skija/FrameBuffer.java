package club.heiqi.skija;

import club.heiqi.qz_blockinfo.hook.BeforeSwapBufferEvent;
import club.heiqi.skija.state.GLStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.*;

public class FrameBuffer {
    public static Logger LOG = LogManager.getLogger();
    public static final List<FrameBuffer> GLOBAL = new ArrayList<>();
    public int fboID;
    public int colorTextureID;
    public int depthStencilBufferID;
    public int width, height;

    public int oldFrame;


    public FrameBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        initFBO();
        GLOBAL.add(this);
    }

    public void initFBO() {
        // 生成FBO
        fboID = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboID);

        // 创建颜色纹理附件
        colorTextureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTextureID);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTextureID, 0);

        // 创建深度+模板缓冲（渲染缓冲对象）
        depthStencilBufferID = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, depthStencilBufferID);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthStencilBufferID);

        // 检查完整性
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer is not complete!");
        }

        LOG.info("创建的fbo {}", fboID);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void bind(int width, int height) {
        oldFrame = glGetInteger(GL_FRAMEBUFFER_BINDING);
        glBindFramebuffer(GL_FRAMEBUFFER, fboID);
        glViewport(0, 0, width, height);
    }

    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, oldFrame);
    }

    public void renderToScreen() {
        // 备份状态
        glPushMatrix();
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glPushClientAttrib(GL_ALL_CLIENT_ATTRIB_BITS);
        // 获取当前绑定FBO

        glViewport(0, 0, width, height);

        // 叠加到当前frame上
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // 标准Alpha混合
        glDisable(GL_DEPTH_TEST);


        // 立即模式渲染全屏四边形
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, colorTextureID);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1000, 1000);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glColorMask(true, true, true, false);
        glColor4f(1.0f, 1.0f, 1.0f, 1f);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex3f(0,height,999); // 左下
        glTexCoord2f(1, 0); glVertex3f(width,height,999); // 右下
        glTexCoord2f(1, 1); glVertex3f(width,0,999); // 右上
        glTexCoord2f(0, 1); glVertex3f(0,0,999); // 左上
        glEnd();

        glPopClientAttrib();
        glPopAttrib();
        glPopMatrix();
    }

    public void renderAtBeforeSwap() {
        BeforeSwapBufferEvent.runnable.add(this::renderToScreen);
    }

    // 资源清理
    public void dispose() {
        glDeleteFramebuffers(fboID);
        glDeleteTextures(colorTextureID);
        glDeleteRenderbuffers(depthStencilBufferID);
        GLOBAL.remove(this);
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) return;

        int oldFbo = fboID;
        int oldColorTexture = colorTextureID;
        int oldDepthStencilBuffer = depthStencilBufferID;

        // 生成新资源
        width = newWidth;
        height = newHeight;
        initFBO();

        glDeleteFramebuffers(oldFbo);
        glDeleteTextures(oldColorTexture);
        glDeleteRenderbuffers(oldDepthStencilBuffer);
    }
}
