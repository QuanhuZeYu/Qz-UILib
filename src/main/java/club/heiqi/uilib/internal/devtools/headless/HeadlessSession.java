package club.heiqi.uilib.internal.devtools.headless;

import club.heiqi.uilib.internal.devtools.playground.TestPlaygroundHost;
import club.heiqi.uilib.ui.host.UiHostRenderSupport;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;

/**
 * headless 会话：一次装配 → 多次推进 → 多次出图，并独占 GL 资源生命周期。
 *
 * <p>会话是设施对外的唯一状态载体：能力在 {@link #open} 时探测并冻结，页面在 open 时装配，
 * 帧推进与像素读回只发生在 {@link #capture()} 内。所有渲染都经生产后端
 * {@link UiRenderContext}、生产帧管线与生产装配点，本类不复制它们的语义。</p>
 *
 * <p><b>线程约束</b>：会话必须在创建它的线程内使用（GL 上下文绑定线程）。</p>
 */
public final class HeadlessSession implements AutoCloseable {

    private final HeadlessRequest request;
    private final GlOffscreenSurface surface;
    private final HeadlessCapabilities capabilities;
    private final AbstractSceneHostWidget host;
    private final RecordingUiRenderContext renderContext;
    private final PaintContextCompositor paintContextCompositor;
    private final UiMainLayerSnapshotService mainLayerSnapshotService;
    private boolean closed;

    private HeadlessSession(HeadlessRequest request, GlOffscreenSurface surface,
            HeadlessCapabilities capabilities, AbstractSceneHostWidget host, RecordingUiRenderContext renderContext,
            PaintContextCompositor paintContextCompositor, UiMainLayerSnapshotService mainLayerSnapshotService) {
        this.request = request;
        this.surface = surface;
        this.capabilities = capabilities;
        this.host = host;
        this.renderContext = renderContext;
        this.paintContextCompositor = paintContextCompositor;
        this.mainLayerSnapshotService = mainLayerSnapshotService;
    }

    /**
     * 打开会话：探测能力 → 建立离屏上下文 → 装配宿主与渲染上下文。
     *
     * <p>失败即释放已获取的资源（无半开状态），并以 {@link HeadlessFailure} 报出阶段。</p>
     *
     * @param request 出图请求
     * @return 已就绪会话
     */
    public static HeadlessSession open(HeadlessRequest request) {
        HeadlessCapabilities capabilities = HeadlessCapabilities.probeFonts();
        GlOffscreenSurface surface = GlOffscreenSurface.create(request.width(), request.height());
        capabilities = capabilities.withGl(surface.glVersion(), surface.glRenderer(), surface.stencilBits(),
                surface.maxTextureSize());
        AbstractSceneHostWidget host;
        try {
            host = createHost(request);
        } catch (HeadlessFailure failure) {
            surface.close();
            throw failure;
        } catch (RuntimeException e) {
            surface.close();
            throw new HeadlessFailure(HeadlessFailure.Stage.ASSEMBLY,
                    "宿主装配失败（页面 " + request.pageId() + "）：" + e.getMessage(), e);
        }
        // 会话持有合成器与主层快照服务：它们在每帧成对 begin/finish，是 backdrop/玻璃合成语义的载体，
        // 缺了它们不会报错，只会让玻璃层内容缺失（静默降级），因此与生产宿主保持同一装配。
        PaintContextCompositor paintContextCompositor = new PaintContextCompositor();
        UiMainLayerSnapshotService mainLayerSnapshotService = new UiMainLayerSnapshotService();
        // 用记录上下文（UiRenderContext 子类）承接像素路径：身份不变（instanceof 解析照旧生效），
        // 旁路记录命令面，供出图完整性交叉判据使用。
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext(request.width(), request.height(),
                paintContextCompositor, mainLayerSnapshotService);
        return new HeadlessSession(request, surface, capabilities, host, renderContext, paintContextCompositor,
                mainLayerSnapshotService);
    }

    /**
     * 页面来源：把页面标识映射为宿主。
     *
     * <p>当前只提供 {@code playground}（测试场地首页）；后续页面（核心控件冒烟、配置页、chat3、HUD）
     * 在此登记，不允许调用方自行 new 宿主绕过会话生命周期。</p>
     *
     * @param request 请求
     * @return 已装配页面宿主
     */
    private static AbstractSceneHostWidget createHost(HeadlessRequest request) {
        if ("playground".equals(request.pageId())) {
            return new TestPlaygroundHost(new HeadlessInputSource(request.width(), request.height()));
        }
        if (HeadlessRequest.TEXT_PROBE_PAGE.equals(request.pageId())) {
            return new TextProbeHost(request.text(), request.width(), request.height());
        }
        throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY,
                "未知页面：" + request.pageId() + "（当前仅提供 playground）");
    }

    /**
     * 推进请求指定的帧数并出图。
     *
     * <p>每帧流程：绑定离屏帧缓冲 → 清屏 → 生产宿主 {@code render}（内含布局 / 路由 / 绘制 / 回放）
     * → 收集 GL 错误；全部帧结束后读回像素、自检、编码 PNG。</p>
     *
     * @return 产物（像素路径 + 自检 + 能力快照 + 耗时）
     */
    public HeadlessArtifact capture() {
        ensureOpen();
        long startedNanos = System.nanoTime();
        int glError = 0;
        int[] argb = null;
        int lastFingerprint = 0;
        int stableFrames = 0;
        int renderedFrames = 0;
        // 帧循环 = 「最少帧数」+「稳定判据」：字形是异步生成的，固定帧数出图会在字形未就绪时产出残缺内容
        // （实测同一次文本探针：1 帧全空、2 帧只剩首个字形、≥10 帧收敛）。稳定判据用像素指纹，
        // 连续 settleFrames 帧指纹一致即停；maxFrames 是硬上限，避免不收敛时死循环。
        while (renderedFrames < request.maxFrames()) {
            renderContext.resetFrame();
            surface.beginFrame(request.background());
            // 帧前置语义（正交投影 / viewport / 混合状态）与生产 MC 宿主共用 UiHostRenderSupport.beginMainUiFrame：
            // headless 自建这一段的后果是顶点落在单位矩阵下被整体裁掉——表现为「绘制无像素」而不是报错。
            try (UiHostRenderSupport.MainFrameScope frameScope =
                    UiHostRenderSupport.beginMainUiFrame(request.width(), request.height())) {
                paintContextCompositor.beginFrame();
                mainLayerSnapshotService.beginFrame();
                try {
                    host.render(request.width(), request.height(), renderContext, 0, 0);
                } catch (HeadlessFailure failure) {
                    throw failure;
                } catch (RuntimeException e) {
                    throw new HeadlessFailure(HeadlessFailure.Stage.FRAME,
                            "第 " + (renderedFrames + 1) + " 帧推进失败：" + e.getMessage(), e);
                } finally {
                    mainLayerSnapshotService.finishFrame();
                    paintContextCompositor.finishFrame();
                }
            }
            renderedFrames++;
            int frameError = surface.consumeGlError();
            if (frameError != 0 && glError == 0) {
                glError = frameError;
            }
            if (renderedFrames < request.frames()) {
                continue;
            }
            argb = surface.readPixels();
            int fingerprint = fingerprintOf(argb);
            if (fingerprint == lastFingerprint) {
                stableFrames++;
                if (stableFrames >= request.settleFrames()) {
                    break;
                }
            } else {
                stableFrames = 0;
                lastFingerprint = fingerprint;
            }
        }
        if (argb == null) {
            throw new HeadlessFailure(HeadlessFailure.Stage.FRAME,
                    "未产出任何帧（frames=" + request.frames() + "，maxFrames=" + request.maxFrames() + "）");
        }
        HeadlessDrawSummary drawSummary = renderContext.summary();
        HeadlessSelfCheck.Report report = HeadlessSelfCheck.inspect(argb, request.width(), request.height(), glError,
                drawSummary);
        long bytes = PngWriter.write(request.output(), argb, request.width(), request.height());
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        return new HeadlessArtifact(request, capabilities, report, drawSummary, request.output(), bytes,
                elapsedMillis, renderedFrames);
    }

    /** @return 本次会话的能力快照 */
    public HeadlessCapabilities capabilities() {
        return capabilities;
    }

    /** @return 本次会话的请求 */
    public HeadlessRequest request() {
        return request;
    }

    /**
     * 像素指纹：按步长采样做 FNV 哈希，用于判断「这一帧与上一帧是否已经一致」。
     *
     * @param argb 行主序 ARGB 像素
     * @return 指纹值
     */
    private static int fingerprintOf(int[] argb) {
        int hash = 0x811C9DC5;
        int step = Math.max(1, argb.length / 4096);
        for (int i = 0; i < argb.length; i += step) {
            hash = (hash ^ argb[i]) * 0x01000193;
        }
        return hash;
    }

    private void ensureOpen() {
        if (closed) {
            throw new HeadlessFailure(HeadlessFailure.Stage.CONTEXT, "会话已关闭");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            host.dispose();
        } catch (RuntimeException ignored) {
            // 资源释放路径不掩盖主流程结果：宿主 runtime 回收失败不应让已产出的图作废。
        }
        surface.close();
    }
}
