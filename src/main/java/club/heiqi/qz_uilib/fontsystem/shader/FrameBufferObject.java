package club.heiqi.qz_uilib.fontsystem.shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class FrameBufferObject implements Closeable {
    public int fbo;
    public int rbo;
    public int texture;
    public int textureWidth, textureHeight;
    public int depthTexture;
    public int previousFbo;
    public int previousX, previousY, previousWidth, previousHeight;

    public FrameBufferObject(int width, int height) {
        fbo = GL30.glGenFramebuffers();
        checkFboCreation();

        // 创建颜色附件纹理
        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        textureWidth = width;
        textureHeight = height;
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
        this.bind(width, height, false);

        // 正确附加颜色和深度纹理
        GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, texture, 0);
        GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, depthTexture, 0);

        checkFrameBufferStatus();
        this.unbind();
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (textureWidth == width && textureHeight == height) return;

        // MyMod.LOG.info("正在缩放FBO");

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


    public void bind(int width, int height, boolean copy) {
        resize(width, height);
        previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        // 更新视口
        IntBuffer buffer = BufferUtils.createIntBuffer(4);
        GL11.glGetInteger(GL11.GL_VIEWPORT, buffer);
        previousX = buffer.get(0);
        previousY = buffer.get(1);
        previousWidth = buffer.get(2);
        previousHeight = buffer.get(3);
        GL11.glViewport(0, 0, width, height);

        int curW = getCurrentFboWidth(previousFbo);
        int curH = getCurrentFboHeight(previousFbo);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);

        if (copy) {
            // 执行深度缓冲区的复制
            GL30.glBlitFramebuffer(
                    0, 0, curW, curH, // 源区域
                    0, 0, width, height, // 目标区域
                    GL11.GL_COLOR_BUFFER_BIT, // 复制的是颜色缓冲
                    GL11.GL_NEAREST // 对于深度缓存，滤波方式必须为 GL_NEAREST
            );
            // 执行深度缓冲区的复制
            GL30.glBlitFramebuffer(
                    0, 0, curW, curH, // 源区域
                    0, 0, width, height, // 目标区域
                    GL11.GL_DEPTH_BUFFER_BIT, // 复制的是深度数据
                    GL11.GL_NEAREST // 对于深度缓存，滤波方式必须为 GL_NEAREST
            );
        }

        // 绑定fbo
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
    }

    public void unbind() {
        // 恢复视口
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
        GL11.glViewport(previousX, previousY, previousWidth, previousHeight);
    }

    public void copyColorDepth() {
        int curFBO = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        int curW = getCurrentFboWidth(previousFbo);
        int curH = getCurrentFboHeight(previousFbo);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, curFBO);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);

        // 执行深度缓冲区的复制
        GL30.glBlitFramebuffer(
                0, 0, curW, curH, // 源区域
                0, 0, textureWidth, textureHeight, // 目标区域
                GL11.GL_COLOR_BUFFER_BIT, // 复制的是颜色缓冲
                GL11.GL_NEAREST // 对于深度缓存，滤波方式必须为 GL_NEAREST
        );
        // 执行深度缓冲区的复制
        GL30.glBlitFramebuffer(
                0, 0, curW, curH, // 源区域
                0, 0, textureWidth, textureHeight, // 目标区域
                GL11.GL_DEPTH_BUFFER_BIT, // 复制的是深度数据
                GL11.GL_NEAREST // 对于深度缓存，滤波方式必须为 GL_NEAREST
        );

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, curFBO);
    }

    /**
     * 自适应目标大小自动全屏绘制
     */
    public void render() {
        // 修改为正交投影矩阵 矩阵确保绘制坐标覆盖全屏 归一化正交矩阵
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 1, 0, -30000, 30000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        // 备份状态准备绘制
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        // 添加：显式禁用光照和雾，以避免颜色调制异常
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);

        // 启用纹理并绑定FBO纹理
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1,1,1,1f);

        // 绘制全屏四边形 - 统一为顺时针 (CW) 绕序
        GL11.glBegin(GL11.GL_TRIANGLES);

        // 第一个三角形 (顺时针: 左上 -> 右上 -> 右下)
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3f(0, 1, 0);  // 左上

        GL11.glTexCoord2f(1, 0);
        GL11.glVertex3f(1, 1, 0);  // 右上

        GL11.glTexCoord2f(1, 1);
        GL11.glVertex3f(1, 0, 0);  // 右下

        // 第二个三角形 (顺时针: 右下 -> 左下 -> 左上)
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex3f(1, 0, 0);  // 右下

        GL11.glTexCoord2f(0, 1);
        GL11.glVertex3f(0, 0, 0);  // 左下

        GL11.glTexCoord2f(0, 0);
        GL11.glVertex3f(0, 1, 0);  // 左上

        GL11.glEnd();

        // 恢复状态
        GL11.glPopAttrib();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
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

    @Override
    public void close() {
        dispose();
    }

    public void dispose() {
        GL30.glDeleteFramebuffers(fbo);
        GL11.glDeleteTextures(texture);
        GL11.glDeleteTextures(depthTexture); // ✅ 释放深度纹理
        if (rbo != 0) {
            GL30.glDeleteRenderbuffers(rbo); // ✅ 释放RBO（如果存在）
        }
    }


    public static int getCurrentFboWidth(int currentFbo) {
        if (currentFbo == 0) {
            return Display.getWidth();
        } else {
            // 检查附件类型
            int attachmentType = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            );
            int attachmentName = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            );

            if (attachmentType == GL11.GL_NONE) {
                throw new IllegalStateException("No color attachment found");
            }

            int width = 0;
            if (attachmentType == GL11.GL_TEXTURE) {
                // 记录当前绑定
                int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, attachmentName);
                width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                // 恢复绑定
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
            } else if (attachmentType == GL30.GL_RENDERBUFFER) {
                // 记录当前绑定
                int prevRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, attachmentName);
                width = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_WIDTH);
                // 恢复绑定
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, prevRenderbuffer);
            } else {
                throw new IllegalStateException("Unsupported attachment type: " + attachmentType);
            }
            return width;
        }
    }

    public static int getCurrentFboHeight(int currentFbo) {
        if (currentFbo == 0) {
            return Display.getHeight();
        } else {
            // 检查附件类型
            int attachmentType = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
            );
            int attachmentName = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            );

            if (attachmentType == GL11.GL_NONE) {
                throw new IllegalStateException("No color attachment found");
            }

            int height = 0;
            if (attachmentType == GL11.GL_TEXTURE) {
                // 记录当前绑定
                int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, attachmentName);
                height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                // 恢复绑定
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
            } else if (attachmentType == GL30.GL_RENDERBUFFER) {
                // 记录当前绑定
                int prevRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, attachmentName);
                height = GL30.glGetRenderbufferParameteri(GL30.GL_RENDERBUFFER, GL30.GL_RENDERBUFFER_HEIGHT);
                // 恢复绑定
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, prevRenderbuffer);
            } else {
                throw new IllegalStateException("Unsupported attachment type: " + attachmentType);
            }
            return height;
        }
    }
}
