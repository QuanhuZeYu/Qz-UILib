package club.heiqi.qz_uilib.fontsystem.shader;

import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;

/**
 * 足够通用化的FBO 可自定义FBO的纹理大小
 * 支持缩放重载大小
 *
 * 注意：此实现基于LWJGL 2/OpenGL 3+的常见实践。
 * 依赖 org.lwjgl.opengl.GL30 (FBO核心API)
 */
public class FrameBufferObject {

    public final int target = GL30.GL_FRAMEBUFFER; // FBO绑定目标
    public int frameBufferID;
    public int colorTextureID;
    public int depthRenderBufferID;

    public int width;
    public int height;

    /**
     * 构造一个新的FrameBufferObject.
     * @param width 初始宽度
     * @param height 初始高度
     */
    public FrameBufferObject(int width, int height) {
        this.width = width;
        this.height = height;
        initialise();
    }

    /**
     * 初始化FBO、纹理和深度缓冲。
     */
    private void initialise() {
        // 1. 创建FBO
        frameBufferID = GL30.glGenFramebuffers();
        bind();

        colorTextureID = createColorTextureAttachment(width, height);

        // 3. 创建深度附件 (渲染缓冲)
        // 也可以使用纹理作为深度附件，但渲染缓冲对于大多数FBO应用来说更高效
        depthRenderBufferID = createDepthRenderBufferAttachment(width, height);

        // 4. 检查FBO完整性
        if (GL30.glCheckFramebufferStatus(target) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("ERROR: FBO is not complete!");
            // 抛出异常或进行更详细的错误处理
        }

        unbind();
    }

    /**
     * 绑定此FBO，后续的渲染命令将绘制到FBO的附件上。
     */
    public void bind() {
        GL30.glBindFramebuffer(target, frameBufferID);
        // 设置视口与FBO大小匹配
        GL11.glViewport(0, 0, width, height);
    }

    /**
     * 解除绑定FBO，将渲染目标切换回默认的帧缓冲 (屏幕)。
     */
    public void unbind() {
        GL30.glBindFramebuffer(target, 0);
        // 通常需要将视口重置为屏幕大小，这里留给调用者处理，
        // 或者在应用程序的渲染循环中统一管理。
        // 例如: GL11.glViewport(0, 0, screenWidth, screenHeight);
    }

    public void attachExistingColorTexture(int textureID, int width, int height) {
        // 1. 清理旧的颜色附件
        if (GL11.glIsTexture(this.colorTextureID)) {
            GL11.glDeleteTextures(this.colorTextureID);
        }

        this.colorTextureID = textureID;
        this.width = width;
        this.height = height;

        // 2. 绑定 FBO
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.frameBufferID);

        // 3. 附加外部纹理到 FBO
        GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                textureID,
                0
        );

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /**
     * 创建一个颜色纹理附件并附加到FBO。
     * @param w 纹理宽度
     * @param h 纹理高度
     * @return 颜色纹理的ID
     */
    private int createColorTextureAttachment(int w, int h) {
        int textureID = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);

        // 设置纹理参数
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        // 分配纹理存储空间 (null表示不上传数据，只分配空间)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        // 附加到FBO
        GL30.glFramebufferTexture2D(target, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textureID, 0);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return textureID;
    }

    /**
     * 创建一个深度渲染缓冲附件并附加到FBO。
     * @param w 宽度
     * @param h 高度
     * @return 深度渲染缓冲的ID
     */
    private int createDepthRenderBufferAttachment(int w, int h) {
        int renderBufferID = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderBufferID);

        // 分配深度存储空间
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, w, h);

        // 附加到FBO
        GL30.glFramebufferRenderbuffer(target, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, renderBufferID);

        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        return renderBufferID;
    }

    /**
     * 重载FBO的大小，释放旧资源并创建新资源。
     * @param newWidth 新的宽度
     * @param newHeight 新的高度
     */
    public void resize(int newWidth, int newHeight) {
        if (this.width == newWidth && this.height == newHeight) {
            return; // 大小未变，无需重载
        }

        // 1. 清理旧资源
        cleanUp();

        // 2. 更新大小
        this.width = newWidth;
        this.height = newHeight;

        // 3. 重新初始化FBO及其附件
        initialise();
    }

    /**
     * 清理所有GL资源。在销毁FBO或重载大小时调用。
     */
    public void cleanUp() {
        if (GL30.glIsFramebuffer(frameBufferID)) {
            GL30.glDeleteFramebuffers(frameBufferID);
        }
        if (GL11.glIsTexture(colorTextureID)) {
            GL11.glDeleteTextures(colorTextureID);
        }
        if (GL30.glIsRenderbuffer(depthRenderBufferID)) {
            GL30.glDeleteRenderbuffers(depthRenderBufferID);
        }
        // 重置ID
        frameBufferID = 0;
        colorTextureID = 0;
        depthRenderBufferID = 0;
    }

    /**
     * 获取FBO的颜色附件纹理ID。
     * @return 颜色纹理ID
     */
    public int getColorTextureID() {
        return colorTextureID;
    }

    /**
     * 获取FBO的宽度。
     * @return 宽度
     */
    public int getWidth() {
        return width;
    }

    /**
     * 获取FBO的高度。
     * @return 高度
     */
    public int getHeight() {
        return height;
    }
}
