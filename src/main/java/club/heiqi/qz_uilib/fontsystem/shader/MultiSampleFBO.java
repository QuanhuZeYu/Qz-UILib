package club.heiqi.qz_uilib.fontsystem.shader;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.client.ErrorCleaner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.concurrent.atomic.AtomicBoolean;

public class MultiSampleFBO {
    public static Logger LOG = LogManager.getLogger();
    public static int MAX_SAMPLE = 0;
    public int fboID, colorRenderBufferID, depthRenderBufferID;
    public int width, height;
    /**标记是否手动清理了*/
    public final AtomicBoolean isClosedManually = new AtomicBoolean(false);

    public MultiSampleFBO(int width, int height) {
        this.width = width;
        this.height = height;

        // 1. 生成FBO
        this.fboID = GL30.glGenFramebuffers();
    }

    public MultiSampleFBO initByDefaultColorAndDepth() {
        bind();
        // 附加默认颜色纹理
        genMultiSample2DAndAttachColorTexture();

        // 附加默认深度和模板
        genMultiRenderBufferAndAttachDepthRenderBuffer();

        unbind();

        check("Init FBO");
        return this;
    }

    public void resolve(int frameID, int destW, int destH) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fboID);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, frameID);

        GL30.glBlitFramebuffer(
                0,0,width,height,
                0,0,destW,destH,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
        );

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
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

    public void genMultiSample2DAndAttachColorTexture() {
        if (MAX_SAMPLE == 0) {
            MAX_SAMPLE = GL11.glGetInteger(GL30.GL_MAX_SAMPLES);
        }
        int sampleCount = Math.min(MAX_SAMPLE, Config.msaa);

        colorRenderBufferID = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, colorRenderBufferID);
        GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, sampleCount, GL11.GL_RGBA, width, height);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, colorRenderBufferID);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        check();
    }

    public void genMultiRenderBufferAndAttachDepthRenderBuffer() {
        if (MAX_SAMPLE == 0) {
            MAX_SAMPLE = GL11.glGetInteger(GL30.GL_MAX_SAMPLES);
        }
        int sampleCount = Math.min(MAX_SAMPLE, Config.msaa);
        depthRenderBufferID = GL30.glGenRenderbuffers();

        // 【修正点】: 绑定Renderbuffer时 target 应该是 GL30.GL_RENDERBUFFER
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderBufferID);

        GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, sampleCount, GL30.GL_DEPTH24_STENCIL8, width, height);

        // 将 Renderbuffer 附加到 FBO
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRenderBufferID);

        // 【建议点】：操作完成后解绑 Renderbuffer
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

        // 【建议点】：添加检查
        check("Attach Depth/Stencil Renderbuffer");
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
        bind();

        this.width = newWidth;
        this.height = newHeight;

        // 计算采样数，与初始化时保持一致
        if (MAX_SAMPLE == 0) {
            MAX_SAMPLE = GL11.glGetInteger(GL30.GL_MAX_SAMPLES);
        }
        int sampleCount = Math.min(MAX_SAMPLE, Config.msaa);

        // 1. 重新分配颜色纹理存储
        if (colorRenderBufferID != 0) {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, colorRenderBufferID);
            // 重新分配颜色存储
            GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, sampleCount, GL11.GL_RGBA, width, height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, colorRenderBufferID);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0); // 解绑 Renderbuffer
            check("Resize Color RenderBuffer");
        }

        // 2. 重新分配深度/模板渲染缓冲存储
        if (depthRenderBufferID != 0) {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderBufferID);
            // 重新分配深度/模板存储 (使用 DEPTH24_STENCIL8 格式)
            GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, sampleCount, GL30.GL_DEPTH24_STENCIL8, width, height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRenderBufferID);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0); // 解绑 Renderbuffer
            check("Resize Depth RenderBuffer");
        }

        // 检查 FBO 状态是否仍然完整
        int fboStatus = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (fboStatus != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LOG.error("FBO resize failed, status: {}", fboStatus);
        }

        unbind(); // 解绑 FBO
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


    public void close() {
        if (colorRenderBufferID != 0)
            GL30.glDeleteRenderbuffers(colorRenderBufferID);
        if (depthRenderBufferID != 0)
            GL30.glDeleteRenderbuffers(depthRenderBufferID);
        if (fboID != 0)
            GL30.glDeleteFramebuffers(fboID);

        // 防止重复清理
        isClosedManually.set(true);
    }

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
