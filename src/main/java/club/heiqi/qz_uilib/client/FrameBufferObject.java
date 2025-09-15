package club.heiqi.qz_uilib.client;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class FrameBufferObject {
    public int fbo;
    public int rbo;
    public int texture;
    public int textureWidth, textureHeight;
    public int depthTexture;
    public int previousFbo;
    public int previousX, previousY, previousWidth, previousHeight;
    // public FloatBuffer previousOrtho = BufferUtils.createFloatBuffer(16);
    // public boolean aspectRatioChange = false;

    public FrameBufferObject() {
        fbo = GL30.glGenFramebuffers();
        checkFboCreation();

        // 创建颜色附件纹理
        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

        textureWidth = Display.getWidth();
        textureHeight = Display.getHeight();
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA,
                textureWidth, textureHeight, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null
        );

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        // 创建深度纹理（带模板）
        depthTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_DEPTH24_STENCIL8,
                textureWidth, textureHeight, 0,
                GL30.GL_DEPTH_STENCIL,
                GL30.GL_UNSIGNED_INT_24_8,
                (ByteBuffer) null
        );

        // 添加深度纹理参数
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        // 绑定FBO并附加附件
        this.bind();

        // 正确附加颜色和深度纹理
        GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, texture, 0);
        GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, depthTexture, 0);

        checkFrameBufferStatus();
        this.unbind();
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (textureWidth == width && textureHeight == height) return;

        textureWidth = width;
        textureHeight = height;

        // 更新颜色附件纹理
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null
        );

        // 更新深度纹理
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH24_STENCIL8,
                width, height, 0,
                GL30.GL_DEPTH_STENCIL, GL30.GL_UNSIGNED_INT_24_8, (ByteBuffer) null
        );
    }



    public void bind() {
        previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

        IntBuffer buffer = BufferUtils.createIntBuffer(4);
        GL11.glGetInteger(GL11.GL_VIEWPORT, buffer);
        previousX = buffer.get(0);
        previousY = buffer.get(1);
        previousWidth = buffer.get(2);
        previousHeight = buffer.get(3);
        GL11.glViewport(0,0,Display.getWidth(),Display.getHeight());
    }

    public void unbind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
        GL11.glViewport(previousX,previousY,previousWidth,previousHeight);
        // if (aspectRatioChange) {
        //     GL11.glMatrixMode(GL11.GL_PROJECTION);
        //     GL11.glLoadMatrix(previousOrtho);
        //     aspectRatioChange = false;
        // }
    }

    public void render(int width, int height) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        // 启用纹理并绑定FBO纹理
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        // 绘制全屏四边形
        GL11.glBegin(GL11.GL_QUADS);
            // 左上角
            GL11.glTexCoord2f(0, 0);
            GL11.glVertex2f(0, height);

            // 右上角
            GL11.glTexCoord2f(1, 0);
            GL11.glVertex2f(width, height);

            // 右下角
            GL11.glTexCoord2f(1, 1);
            GL11.glVertex2f(width, 0);

            // 左下角
            GL11.glTexCoord2f(0, 1);
            GL11.glVertex2f(0, 0);
        GL11.glEnd();

        // 恢复状态
        GL11.glPopAttrib();
    }

    public void dispose() {
        GL30.glDeleteFramebuffers(fbo);
        GL11.glDeleteTextures(texture);
        GL11.glDeleteTextures(depthTexture); // ✅ 释放深度纹理
        if (rbo != 0) {
            GL30.glDeleteRenderbuffers(rbo); // ✅ 释放RBO（如果存在）
        }
    }


    public void checkFboCreation() {
        if (fbo == 0) {
            int error = GL11.glGetError();
            String errorMsg;
            switch (error) {
                case GL11.GL_INVALID_OPERATION:
                    errorMsg = "无效操作（可能未初始化上下文）";
                    break;
                case GL11.GL_OUT_OF_MEMORY:
                    errorMsg = "显存不足";
                    break;
                default:
                    errorMsg = "错误代码: 0x" + Integer.toHexString(error);
            }
            throw new IllegalStateException("无法创建帧缓冲: " + errorMsg);
        }
    }

    public void checkFrameBufferStatus() {
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            String error = switch (status) {
                case GL30.GL_FRAMEBUFFER_UNDEFINED -> "目标帧缓冲不存在";
                case GL30.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT -> "附件不完整";
                case GL30.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT -> "缺少颜色附件";
                case GL30.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER -> "绘制缓冲区不完整";
                case GL30.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER -> "读取缓冲区不完整";
                case GL30.GL_FRAMEBUFFER_UNSUPPORTED -> "格式不支持";
                case GL30.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE -> "多重采样不一致";
                case GL32.GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS -> "分层目标不完整";
                default -> "未知错误: 0x" + Integer.toHexString(status);
            };
            throw new RuntimeException("帧缓冲配置错误: " + error);
        }
    }
}
