package club.heiqi.uilib.internal.devtools.headless;

import club.heiqi.uilib.internal.devtools.playground.TestPlaygroundHost;
import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.env.UiEnvironment;
import club.heiqi.uilib.ui.host.UiHostRenderSupport;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

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

    /** 采样会话的界面名：headless 一次出图即一个独立进程，无需与真机界面名区分。 */
    private static final String SAMPLE_SCREEN = "headless";
    private final HeadlessRequest request;
    private final GlOffscreenSurface surface;
    private final HeadlessCapabilities capabilities;
    private final UiEnvironment environment;
    private final UiSurface host;
    private final RecordingUiRenderContext renderContext;
    private final PaintContextCompositor paintContextCompositor;
    private final UiMainLayerSnapshotService mainLayerSnapshotService;
    private final HeadlessInputSource inputSource;
    private boolean closed;

    private HeadlessSession(HeadlessRequest request, GlOffscreenSurface surface,
            HeadlessCapabilities capabilities, UiEnvironment environment, UiSurface host,
            RecordingUiRenderContext renderContext,
            PaintContextCompositor paintContextCompositor, UiMainLayerSnapshotService mainLayerSnapshotService,
            HeadlessInputSource inputSource) {
        this.request = request;
        this.surface = surface;
        this.capabilities = capabilities;
        this.environment = environment;
        this.host = host;
        this.renderContext = renderContext;
        this.paintContextCompositor = paintContextCompositor;
        this.mainLayerSnapshotService = mainLayerSnapshotService;
        this.inputSource = inputSource;
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
        // 输入设备与会话同生命周期：脚本在装配期编译进设备，帧推进时由帧管线经 drainFrame 消费。
        HeadlessInputSource inputSource = new HeadlessInputSource(request.width(), request.height());
        HeadlessInputScript.apply(inputSource.device(), request.script());
        // 环境端口由请求声明（package-info 不变量 3：不读全局单例的隐藏状态）：诊断开关此前恒取生产
        // 配置字段，于是 headless 里 --debug 无从表达、采样器永远打不开。
        UiEnvironment environment = HeadlessEnvironment.of(request.diagnostics());
        HostBinding binding;
        try {
            binding = createHost(request, inputSource, environment);
        } catch (HeadlessFailure failure) {
            surface.close();
            throw failure;
        } catch (RuntimeException e) {
            surface.close();
            throw new HeadlessFailure(HeadlessFailure.Stage.ASSEMBLY,
                    "宿主装配失败（页面 " + request.pageId() + "）：" + e.getMessage(), e);
        }
        UiSurface host = binding.surface();
        // 会话持有合成器与主层快照服务：它们在每帧成对 begin/finish，是 backdrop/玻璃合成语义的载体，
        // 缺了它们不会报错，只会让玻璃层内容缺失（静默降级），因此与生产宿主保持同一装配。
        PaintContextCompositor paintContextCompositor = new PaintContextCompositor();
        UiMainLayerSnapshotService mainLayerSnapshotService = new UiMainLayerSnapshotService();
        // 用记录上下文（UiRenderContext 子类）承接像素路径：身份不变（instanceof 解析照旧生效），
        // 旁路记录命令面，供出图完整性交叉判据使用。
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext(request.width(), request.height(),
                paintContextCompositor, mainLayerSnapshotService);
        applyEnvironment(binding, request);
        return new HeadlessSession(request, surface, capabilities, environment, host, renderContext,
                paintContextCompositor, mainLayerSnapshotService, inputSource);
    }

    /**
     * 把请求声明的环境量投影到页面 runtime。
     *
     * <p>为什么在装配之后、首帧之前：字号倍率是 runtime 侧的运行期环境量（权威在
     * {@link SceneRuntime#setFontScale(int)}，自带字号代际失效通道），不是宿主字段 ——
     * 在此写入一次即由 runtime 自身的通道通知全部消费者，宿主与探针都不复制这个值。</p>
     *
     * <p>缺省倍率（不缩放）不写：写动作本身会推进字号代际，对「未声明缩放」的请求是纯属无谓的失效。</p>
     *
     * @param binding 装配结果
     * @param request 请求
     */
    private static void applyEnvironment(HostBinding binding, HeadlessRequest request) {
        if (request.fontScalePercent() == SceneRuntime.FONT_SCALE_NONE_PERCENT) {
            return;
        }
        binding.runtime().setFontScale(request.fontScalePercent());
    }

    /**
     * 页面来源：把页面标识映射为宿主。
     *
     * <p>当前提供 {@code playground}（测试场地首页）、{@code text-probe}（单行文本）、
     * {@code chat}（chat3 内容树）与 {@code hud}（HUD 宿主装配：外壳 + 锚定放置）；后续页面
     * 在此登记，不允许调用方自行 new 宿主绕过会话生命周期。</p>
     *
     * <p>返回类型是 {@link UiSurface} 而非 {@code AbstractSceneHostWidget}：页面宿主有两种形态——
     * 挂在场景帧管线上的「页面宿主」（Widget 派生、有输入源）与保留式「宿主窗口」
     * （{@link club.heiqi.uilib.ui.scene.host.SceneHostWindow}：无输入、内容空即隐）。
     * 会话只驱动渲染面，不假定宿主内部形态。</p>
     *
     * @param request 请求
     * @param inputSource 输入源（脚本已编译进设备）
     * @param environment 请求声明的环境端口；每个页面宿主都按构造依赖接收，不得回落生产单例
     * @return 装配结果（渲染面 + 该面的 runtime）
     */
    private static HostBinding createHost(HeadlessRequest request, HeadlessInputSource inputSource,
            UiEnvironment environment) {
        if ("playground".equals(request.pageId())) {
            TestPlaygroundHost playgroundHost = new TestPlaygroundHost(inputSource, environment);
            if (request.pageIndex() >= 0) {
                // 确定性切页：走宿主 signal 通道（与用户点击导航同源），不依赖命中坐标。
                playgroundHost.showPage(request.pageIndex());
            }
            return new HostBinding(playgroundHost, playgroundHost.runtime());
        }
        if (HeadlessRequest.TEXT_PROBE_PAGE.equals(request.pageId())) {
            TextProbeHost textProbe = new TextProbeHost(request.text(), request.width(), request.height(),
                    inputSource, environment);
            return new HostBinding(textProbe, textProbe.runtime());
        }
        if (HeadlessRequest.CHAT_PAGE.equals(request.pageId())) {
            ChatSceneProbeHost chatProbe = new ChatSceneProbeHost(request.width(), request.height(),
                    ChatSceneProbeHost.splitMessages(request.text()), inputSource, request.clockMillis(),
                    environment);
            return new HostBinding(chatProbe, chatProbe.runtime());
        }

        if (HeadlessRequest.HUD_PAGE.equals(request.pageId())) {
            HudSceneProbeHost hudProbe = new HudSceneProbeHost(request.width(), request.height(),
                    ChatSceneProbeHost.splitMessages(request.text()), request.pageIndex(),
                    request.clockMillis(), environment);
            return new HostBinding(hudProbe, hudProbe.runtime());
        }

        throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY,
                "未知页面：" + request.pageId()
                        + "（当前提供 playground / text-probe / chat / hud）");
    }

    /**
     * 页面装配结果：渲染面 + 该面的 runtime。
     *
     * <p>为什么把 runtime 一并交回：环境投影（字号倍率）只能落在 runtime 上，而 runtime 的创建点有
     * 两种形态 —— 页面宿主自建（{@code AbstractSceneHostWidget} 子类）与保留式宿主窗口自建
     * （{@code SceneHostWindow}）。在装配处显式取出，好过在投影处用 {@code instanceof} 反推：
     * 前者漏一处就有编译错误，后者漏一处只是静默不生效。</p>
     */
    private static final class HostBinding {

        private final UiSurface surface;
        private final SceneRuntime runtime;

        HostBinding(UiSurface surface, SceneRuntime runtime) {
            this.surface = surface;
            this.runtime = runtime;
        }

        UiSurface surface() {
            return surface;
        }

        SceneRuntime runtime() {
            return runtime;
        }
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
        // 采样会话：帧是采样的管辖单位（见 UiPerformanceMonitor javadoc）。headless 的帧循环是本进程的
        // 最外层帧入口——页面宿主内部还会各自 beginFrame，但重入深度保护使其只递增深度而不另建会话，
        // 于是「本次出图的统计」恰好等于这一张图的帧，而不是各宿主自成一段。诊断关闭时 beginFrame 立即
        // 返回、finishFrame 安全空转（无会话），代价是一次线程本地读。
        UiPerformanceMonitor monitor = UiPerformanceMonitor.getInstance();
        while (renderedFrames < request.maxFrames()) {
            monitor.beginFrame(SAMPLE_SCREEN, request.width(), request.height(), request.width(),
                    request.height(), environment.diagnostics());
            try {
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
            } finally {
                monitor.finishFrame();
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
        // 帧内事实只在请求声明采样时附上：关闭态读到的可能是同 JVM 早先会话的残留快照（批量出图同一进程）。
        // 该摘要是耗时事实，天然逐次不同；它不进 PNG，故不影响出图的逐像素可复现性。
        String performance = request.diagnostics() ? monitor.getRuntimeStats().toString() : null;
        return new HeadlessArtifact(request, capabilities, report, drawSummary, request.output(), bytes,
                elapsedMillis, renderedFrames, inputSource.device().describe(), performance);
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
