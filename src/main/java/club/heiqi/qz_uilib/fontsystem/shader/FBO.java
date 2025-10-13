package club.heiqi.qz_uilib.fontsystem.shader;

import club.heiqi.qz_uilib.client.ErrorCleaner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class FBO {
    public static Logger LOG = LogManager.getLogger();
    public int fboID, colorTextureID, depthRenderBufferID;
    public int width, height;
    /**标记是否是外部的颜色纹理*/
    public boolean isOutSideTexture = false;
    /**标记是否手动清理了*/
    public final AtomicBoolean isClosedManually = new AtomicBoolean(false);

    public FBO(int width, int height) {
        this.width = width;
        this.height = height;

        // 1. 生成FBO
        this.fboID = GL30.glGenFramebuffers();
    }

    public FBO initByDefaultColorAndDepth() {
        bind();
        // 附加默认颜色纹理
        genTexture2DAndAttachColorTexture();

        // 附加默认深度和模板
        genRenderBufferAndAttachDepthRenderBuffer();

        unbind();

        return this;
    }

    public FBO initByOutColorAndGenDepth(int textureID) {
        bind();

        attachColorTexture(textureID);
        genRenderBufferAndAttachDepthRenderBuffer();

        unbind();
        return this;
    }

    public void bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboID);
    }
    public int previousFBOID = 0;
    public void bindAndRecordPreviousFBO() {
        previousFBOID = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboID);
    }

    public void unbind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }
    public void unbindAndRestorePreviousFBO() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFBOID);
        previousFBOID = 0;
    }

    public void genTexture2DAndAttachColorTexture() {
        colorTextureID = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureID);

        // 设置纹理参数
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        // 分配内存
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA,
                width,
                height,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null
        );

        // 附加到FBO
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTextureID, 0);

        // 解绑
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public void attachColorTexture(int textureID) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);

        // 附加到FBO
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textureID, 0);

        // 解绑
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        isOutSideTexture = true;
    }

    public void genRenderBufferAndAttachDepthRenderBuffer() {
        depthRenderBufferID = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderBufferID);

        // 为深度和模板缓冲分配存储空间
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);

        // 将RBO附加到FBO
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRenderBufferID);

        // 解绑
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
    }

    /**
     * 调整FBO及其附件的大小。
     * @param newWidth 新的宽度
     * @param newHeight 新的高度
     */
    public void resize(int newWidth, int newHeight) {
        if (this.width == newWidth && this.height == newHeight) {
            return; // 尺寸未改变，无需操作
        }

        if (isOutSideTexture) {
            LOG.warn("FBO颜色纹理为外部纹理，无法在此方法中调整大小。");
            throw new UnsupportedOperationException("FBO 颜色纹理为外部纹理，无法调用 resize 方法。");
        }

        // 记录并绑定FBO
        bind();

        this.width = newWidth;
        this.height = newHeight;

        // 1. 重新分配颜色纹理存储
        if (colorTextureID != 0) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureID);
            // 重新分配内存，保持与 genTexture2DAndAttachColorTexture 一致的格式
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL11.GL_RGBA,
                    width,
                    height,
                    0,
                    GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE,
                    (ByteBuffer) null
            );
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }

        // 2. 重新分配深度/模板渲染缓冲存储
        if (depthRenderBufferID != 0) {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderBufferID);
            // 重新分配存储空间，保持与 genRenderBufferAndAttachDepthRenderBuffer 一致的格式
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        }

        // 检查FBO完成状态（可选，但推荐）
        checkCompletion();

        // 恢复之前绑定的FBO
        unbind();

        LOG.debug("FBO resized to: {}x{}", width, height);
    }

    public void checkCompletion() {
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            String errorMsg;
            switch (status) {
                case GL30.GL_FRAMEBUFFER_UNDEFINED:
                    errorMsg = "GL_FRAMEBUFFER_UNDEFINED";
                    break;
                case GL30.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT:
                    errorMsg = "GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT";
                    break;
                case GL30.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT:
                    errorMsg = "GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT";
                    break;
                case GL30.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER:
                    errorMsg = "GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER";
                    break;
                case GL30.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER:
                    errorMsg = "GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER";
                    break;
                case GL30.GL_FRAMEBUFFER_UNSUPPORTED:
                    errorMsg = "GL_FRAMEBUFFER_UNSUPPORTED";
                    break;
                case GL30.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE:
                    errorMsg = "GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE";
                    break;
                case GL32.GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS:
                    errorMsg = "GL_FRAMEBUFFER_INCOMPLETE_LAYER_TARGETS";
                    break;
                default:
                    errorMsg = "Unknown error: " + status;
                    break;
            }
            throw new RuntimeException("FrameBufferObject creation failed: " + errorMsg);
        }
    }

    public void close() {
        if (!isOutSideTexture && colorTextureID != 0)
            GL11.glDeleteTextures(colorTextureID);
        if (depthRenderBufferID != 0)
            GL30.glDeleteRenderbuffers(depthRenderBufferID);
        if (fboID != 0)
            GL30.glDeleteFramebuffers(fboID);

        // 防止重复清理
        isClosedManually.set(true);
    }


    // region 检查函数
    public static void check(String label) {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            String errorString = getGLErrorString(error);
            LOG.error("--- OpenGL Error Check ---");
            LOG.error("STEP: {}", label);
            LOG.error("ERROR ID: {}", error);
            LOG.error("ERROR DESC: {}", errorString);
            LOG.error("--------------------------");

            // 建议在这里抛出异常或中断，以便立即定位问题
            // throw new RuntimeException("OpenGL Error (" + errorString + ") after step: " + label);
        }
        else {
            LOG.info("无异常");
        }
    }
    public static void check() {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            String errorString = getGLErrorString(error);
            LOG.error("--- OpenGL Error Check ---");
            LOG.error("ERROR ID: {}", error);
            LOG.error("ERROR DESC: {}", errorString);
            LOG.error("--------------------------");
        }
        else {
            LOG.info("无异常");
        }
    }

    // 辅助方法：将错误代码转换为可读字符串
    private static String getGLErrorString(int error) {
        switch (error) {
            case GL11.GL_NO_ERROR:
                return "GL_NO_ERROR";
            case GL11.GL_INVALID_ENUM:
                return "GL_INVALID_ENUM (An unacceptable value is specified for an enumerated argument)";
            case GL11.GL_INVALID_VALUE:
                return "GL_INVALID_VALUE (A numeric argument is out of range)";
            case GL11.GL_INVALID_OPERATION:
                return "GL_INVALID_OPERATION (The specified operation is not allowed in the current state)";
            case GL11.GL_STACK_OVERFLOW:
                return "GL_STACK_OVERFLOW (A push operation was attempted when the stack was full)";
            case GL11.GL_STACK_UNDERFLOW:
                return "GL_STACK_UNDERFLOW (A pop operation was attempted when the stack was empty)";
            case GL11.GL_OUT_OF_MEMORY:
                return "GL_OUT_OF_MEMORY (There is not enough memory left to execute the command)";
            case GL30.GL_INVALID_FRAMEBUFFER_OPERATION:
                return "GL_INVALID_FRAMEBUFFER_OPERATION";
            default:
                return "UNKNOWN_ERROR";
        }
    }
    // endregion 检查函数


    /**
     * 最后的兜底方案，请一定一定要手动调用close方法
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (!isClosedManually.get()) {
            LOG.error("有对象未被手动释放资源产生了内存泄漏！");
            // 添加到主线程清理
            ErrorCleaner.errorCleaners.add(this::close);
        }
    }
}