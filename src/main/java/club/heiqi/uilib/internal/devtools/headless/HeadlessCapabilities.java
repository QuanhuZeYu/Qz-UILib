package club.heiqi.uilib.internal.devtools.headless;

import java.awt.GraphicsEnvironment;

/**
 * 运行期能力声明：headless 设施启动即探测并如实声明「这台机器现在能做到什么」。
 *
 * <p>不变量：设施不假定 GL 可用、不假定系统字体可用、不假定尺寸上限。任何一项缺失都在
 * {@link HeadlessSession#open} 阶段被记录；渲染文本前先看 {@link #canRenderText()}，
 * 避免把「容器里没有字体」误判成「UI 代码画错了」。</p>
 */
public final class HeadlessCapabilities {

    private final int fontFamilyCount;
    private final String fontProbeDetail;
    private final boolean awtHeadless;
    private final String glVersion;
    private final String glRenderer;
    private final int stencilBits;
    private final int maxTextureSize;

    private HeadlessCapabilities(int fontFamilyCount, String fontProbeDetail, boolean awtHeadless,
            String glVersion, String glRenderer, int stencilBits, int maxTextureSize) {
        this.fontFamilyCount = fontFamilyCount;
        this.fontProbeDetail = fontProbeDetail;
        this.awtHeadless = awtHeadless;
        this.glVersion = glVersion;
        this.glRenderer = glRenderer;
        this.stencilBits = stencilBits;
        this.maxTextureSize = maxTextureSize;
    }

    /**
     * 探测字体环境（GL 侧尚未建立时调用）。
     *
     * <p>AWT 字体子系统是「全有或全无」：零 fontconfig 环境在 FontManagerFactory 初始化即抛异常，
     * 故这里必须捕获 Throwable 并如实降级为「不可渲染文本」，而不是让设施自身崩掉。</p>
     *
     * @return 仅含字体侧事实的能力对象
     */
    public static HeadlessCapabilities probeFonts() {
        boolean headless = Boolean.getBoolean("java.awt.headless");
        try {
            String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            return new HeadlessCapabilities(families.length, "ok", headless, null, null, -1, -1);
        } catch (Throwable t) {
            return new HeadlessCapabilities(-1, t.getClass().getName() + ": " + t.getMessage(), headless,
                    null, null, -1, -1);
        }
    }

    /**
     * 回填 GL 侧事实（上下文建立后调用）。
     *
     * @param version        GL_VERSION
     * @param renderer       GL_RENDERER
     * @param stencilBits    GL_STENCIL_BITS
     * @param maxTextureSize GL_MAX_TEXTURE_SIZE
     * @return 补全 GL 的新能力对象
     */
    public HeadlessCapabilities withGl(String version, String renderer, int stencilBits, int maxTextureSize) {
        return new HeadlessCapabilities(fontFamilyCount, fontProbeDetail, awtHeadless, version, renderer,
                stencilBits, maxTextureSize);
    }

    /** @return 系统可用字体族数量；-1 表示探测失败 */
    public int fontFamilyCount() {
        return fontFamilyCount;
    }

    /** @return 字体探测细节（成功时为 ok，失败时为异常摘要） */
    public String fontProbeDetail() {
        return fontProbeDetail;
    }

    /** @return 是否处于 AWT headless 模式 */
    public boolean awtHeadless() {
        return awtHeadless;
    }

    /** @return GL_VERSION，未建立上下文时为 null */
    public String glVersion() {
        return glVersion;
    }

    /** @return GL_RENDERER，未建立上下文时为 null */
    public String glRenderer() {
        return glRenderer;
    }

    /** @return GL_STENCIL_BITS；-1 表示未知 */
    public int stencilBits() {
        return stencilBits;
    }

    /** @return GL_MAX_TEXTURE_SIZE；-1 表示未知 */
    public int maxTextureSize() {
        return maxTextureSize;
    }

    /**
     * 文本是否可能渲染出内容。
     *
     * @return true 表示字体环境可用（不保证字形完整，缺字形由自检计数）
     */
    public boolean canRenderText() {
        return fontFamilyCount > 0;
    }

    /** @return 单行摘要，用于 CLI 与 artifact 输出 */
    public String summary() {
        return "gl=" + (glVersion == null ? "(none)" : glVersion)
                + " renderer=" + (glRenderer == null ? "(none)" : glRenderer)
                + " stencil=" + stencilBits
                + " maxTexture=" + maxTextureSize
                + " fonts=" + fontFamilyCount
                + " awtHeadless=" + awtHeadless;
    }
}
