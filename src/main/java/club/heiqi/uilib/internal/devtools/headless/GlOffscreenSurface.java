package club.heiqi.uilib.internal.devtools.headless;

import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.PixelFormat;

/**
 * 离屏像素面：LWJGL2 上下文 + 自建 FBO，把「渲染目标尺寸」与「窗口/Xvfb 屏幕尺寸」彻底解耦。
 *
 * <p>为什么必须自建 FBO 而不是渲染默认 framebuffer：分辨率覆盖 360P~2K（甚至更高）时，窗口与屏幕无法承载；默认 framebuffer 尺寸只由平台决定。FBO 尺寸即请求尺寸，
 * 因此 CI 的 1366x768 Xvfb 也能产出 2560x1440 的图。</p>
 *
 * <p><b>线程约束</b>：GL 调用必须固定在创建上下文的那个线程上，本类不做任何跨线程保护——
 * 调用方（CLI 主线程或测试线程）负责在单线程内完成 open → capture → close。</p>
 *
 * <p>PixelFormat 必须带 stencil：生产 {@code ClipStack} 走 scissor + stencil mask，
 * 缺 stencil 会让圆角/裁剪语义静默失配。</p>
 */
public final class GlOffscreenSurface implements AutoCloseable {

    // 注意：不设置 DisplayMode。窗口尺寸与渲染目标无关（FBO 尺寸才是渲染尺寸），
    // 且编译类路径上的 org.lwjgl.opengl.DisplayMode 来自 lwjgl3ify shim（无 (int,int) 构造），
    // 显式设置窗口模式会在编译期就失败。默认模式足够。

    private final int width;
    private final int height;
    private final int framebufferId;
    private final int colorTextureId;
    private final int depthStencilBufferId;
    private final String glVersion;
    private final String glRenderer;
    private final int stencilBits;
    private final int maxTextureSize;
    private boolean closed;

    private GlOffscreenSurface(int width, int height, int framebufferId, int colorTextureId,
            int depthStencilBufferId, String glVersion, String glRenderer, int stencilBits, int maxTextureSize) {
        this.width = width;
        this.height = height;
        this.framebufferId = framebufferId;
        this.colorTextureId = colorTextureId;
        this.depthStencilBufferId = depthStencilBufferId;
        this.glVersion = glVersion;
        this.glRenderer = glRenderer;
        this.stencilBits = stencilBits;
        this.maxTextureSize = maxTextureSize;
    }

    /**
     * 建立上下文与指定尺寸的离屏帧缓冲。
     *
     * @param width  渲染目标宽（像素）
     * @param height 渲染目标高（像素）
     * @return 已就绪的离屏像素面
     * @throws HeadlessFailure 上下文创建失败或 FBO 不完整
     */
    public static GlOffscreenSurface create(int width, int height) {
        try {
            ensureContext();
        } catch (Throwable e) {
            // 编译期解析到 lwjgl3ify 的 org.lwjgl shim（其 create 不声明 checked 异常），
            // 运行期用真 LWJGL2（其 create 抛 checked LWJGLException）——签名不一致，故统一按 Throwable 收口，
            // 两种 classpath 组合下都能给出可归因的失败。
            String hint = e instanceof UnsatisfiedLinkError
                    ? "加载 LWJGL2 natives 失败：请确认 natives 已解压且 -Djava.library.path 指向该目录"
                            + "（exportHeadlessClasspath 生成的 qz-shot.bat 已自带该参数）"
                    : "创建 GL 上下文失败（Linux 无桌面环境需 Xvfb）";
            throw new HeadlessFailure(HeadlessFailure.Stage.CONTEXT, hint + "：" + HeadlessFailure.brief(e), e);
        }
        String version = safeGlString(GL11.GL_VERSION);
        String renderer = safeGlString(GL11.GL_RENDERER);
        int stencil = GL11.glGetInteger(GL11.GL_STENCIL_BITS);
        int maxTexture = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (maxTexture > 0 && (width > maxTexture || height > maxTexture)) {
            Display.destroy();
            throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY,
                    "请求尺寸超过 GL_MAX_TEXTURE_SIZE：" + width + "x" + height + " vs " + maxTexture);
        }
        int framebufferId = 0;
        int colorTextureId = 0;
        int depthStencilBufferId = 0;
        try {
            framebufferId = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
            colorTextureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureId);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                    colorTextureId, 0);
            depthStencilBufferId = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencilBufferId);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, depthStencilBufferId);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new HeadlessFailure(HeadlessFailure.Stage.CONTEXT,
                        "FBO 不完整：status=" + status + "（" + width + "x" + height + "）");
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        } catch (HeadlessFailure failure) {
            Display.destroy();
            throw failure;
        }
        return new GlOffscreenSurface(width, height, framebufferId, colorTextureId, depthStencilBufferId,
                version, renderer, stencil, maxTexture);
    }

    /**
     * 确保进程级 GL 上下文存在（已存在则复用）。
     *
     * <p>上下文是<b>进程级资源</b>，不是会话级资源：字体 atlas 等全局 GL 对象挂在它上面，
     * 每次会话销毁并重建上下文会让后续会话用到失效纹理——实测多档矩阵从第 2 档起
     * {@code glError=1281}、颜色数从 1290 掉到 217（文字大面积丢失）。</p>
     */
    private static void ensureContext() throws LWJGLException {
        if (Display.isCreated()) {
            return;
        }
        Display.setTitle("Qz-UILib headless");
        Display.create(new PixelFormat().withDepthBits(24).withStencilBits(8));
    }

    /** 进程退出前释放上下文（可选：JVM 退出也会释放）。多测试共享 JVM 时不应调用。 */
    public static void shutdownContext() {
        if (Display.isCreated()) {
            Display.destroy();
        }
    }


    private static String safeGlString(int name) {
        String value = GL11.glGetString(name);
        return value == null ? "(unknown)" : value;
    }

    /**
     * 绑定本 FBO，并以请求的宿主背景色清屏，作为一帧的起点。
     *
     * <p>背景不是装饰：UI 面板大量使用半透明玻璃配方，真机上叠在游戏世界之上才成立。
     * 若 headless 从全透明开始，面板 alpha 会停在极低值（实测 meanAlpha≈18/255），
     * 导出 PNG 后看似「白底淡字」——那是缺宿主背景，不是 UI 画错。</p>
     *
     * @param backgroundArgb 宿主背景色（ARGB）；0x00000000 表示透明背景
     */
    public void beginFrame(int backgroundArgb) {
        ensureOpen();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
        GL11.glViewport(0, 0, width, height);
        GL11.glClearColor(((backgroundArgb >> 16) & 0xFF) / 255f, ((backgroundArgb >> 8) & 0xFF) / 255f,
                (backgroundArgb & 0xFF) / 255f, ((backgroundArgb >>> 24) & 0xFF) / 255f);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
    }

    /**
     * 读回当前帧像素（行主序 ARGB，原点左上）。
     *
     * @return 长度 width*height 的 ARGB 像素
     */
    public int[] readPixels() {
        ensureOpen();
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            int srcRow = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int i = (srcRow * width + x) * 4;
                int r = buffer.get(i) & 0xFF;
                int g = buffer.get(i + 1) & 0xFF;
                int b = buffer.get(i + 2) & 0xFF;
                int a = buffer.get(i + 3) & 0xFF;
                argb[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return argb;
    }

    /**
     * 取走并清空 GL 错误队列。
     *
     * @return 首个非零错误码；无错误返回 0
     */
    public int consumeGlError() {
        ensureOpen();
        int first = 0;
        for (int i = 0; i < 16; i++) {
            int error = GL11.glGetError();
            if (error == 0) {
                break;
            }
            if (first == 0) {
                first = error;
            }
        }
        return first;
    }

    /** @return 渲染目标宽 */
    public int width() {
        return width;
    }

    /** @return 渲染目标高 */
    public int height() {
        return height;
    }

    /** @return GL_VERSION */
    public String glVersion() {
        return glVersion;
    }

    /** @return GL_RENDERER */
    public String glRenderer() {
        return glRenderer;
    }

    /** @return GL_STENCIL_BITS */
    public int stencilBits() {
        return stencilBits;
    }

    /** @return GL_MAX_TEXTURE_SIZE */
    public int maxTextureSize() {
        return maxTextureSize;
    }

    private void ensureOpen() {
        if (closed) {
            throw new HeadlessFailure(HeadlessFailure.Stage.CONTEXT, "像素面已关闭");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (Display.isCreated()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            if (colorTextureId != 0) {
                GL11.glDeleteTextures(colorTextureId);
            }
            if (depthStencilBufferId != 0) {
                GL30.glDeleteRenderbuffers(depthStencilBufferId);
            }
            if (framebufferId != 0) {
                GL30.glDeleteFramebuffers(framebufferId);
            }
        }
        // 不销毁 Display：上下文由进程共享（见 ensureContext 的说明）。
    }
}
