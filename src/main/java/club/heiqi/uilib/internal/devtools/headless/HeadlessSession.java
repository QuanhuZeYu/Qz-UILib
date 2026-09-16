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
        for (int frame = 0; frame < request.frames(); frame++) {
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
                            "第 " + (frame + 1) + " 帧推进失败：" + e.getMessage(), e);
                } finally {
                    mainLayerSnapshotService.finishFrame();
                    paintContextCompositor.finishFrame();
                }
            }
            int frameError = surface.consumeGlError();
            if (frameError != 0 && glError == 0) {
                glError = frameError;
            }
        }
        int[] argb = surface.readPixels();
        int readError = surface.consumeGlError();
        if (readError != 0 && glError == 0) {
            glError = readError;
        }
        HeadlessDrawSummary drawSummary = renderContext.summary();
        HeadlessSelfCheck.Report report = HeadlessSelfCheck.inspect(argb, request.width(), request.height(), glError,
                drawSummary);
        long bytes = PngWriter.write(request.output(), argb, request.width(), request.height());
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        return new HeadlessArtifact(request, capabilities, report, drawSummary, request.output(), bytes,
                elapsedMillis);
    }

    /** @return 本次会话的能力快照 */
    public HeadlessCapabilities capabilities() {
        return capabilities;
    }

    /** @return 本次会话的请求 */
    public HeadlessRequest request() {
        return request;
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
