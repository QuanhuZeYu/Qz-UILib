package club.heiqi.uilib.internal.devtools.headless;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.internal.devtools.playground.TestPlaygroundHost;
import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.env.UiEnvironment;
import club.heiqi.uilib.ui.host.UiHostRenderSupport;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;

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
    private final SceneRuntime runtime;
    private boolean closed;

    private HeadlessSession(HeadlessRequest request, GlOffscreenSurface surface,
            HeadlessCapabilities capabilities, UiEnvironment environment, UiSurface host,
            RecordingUiRenderContext renderContext,
            PaintContextCompositor paintContextCompositor, UiMainLayerSnapshotService mainLayerSnapshotService,
            HeadlessInputSource inputSource, SceneRuntime runtime) {
        this.request = request;
        this.surface = surface;
        this.capabilities = capabilities;
        this.environment = environment;
        this.host = host;
        this.renderContext = renderContext;
        this.paintContextCompositor = paintContextCompositor;
        this.mainLayerSnapshotService = mainLayerSnapshotService;
        this.inputSource = inputSource;
        this.runtime = runtime;
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
        // 出图确定性【实验验证中】：宽度缓存 miss 预算按 16ms 真实时间窗限流，超预算的码点按
        // 空格宽近似排版（TextLayoutService.tryAcquireWidthMissBudget）⇒ 布局宽度取决于「本窗口内
        // 已测量多少码点」，即取决于真实耗时。headless 是确定性工具，不受交互帧预算约束：解除预算，
        // 全部码点走精确测量。
        FontConfig.widthCacheMissBudgetPerWindow = 0;
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
                paintContextCompositor, mainLayerSnapshotService, inputSource, binding.runtime());
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
     * <p>外观档同属装配期环境量：它必须早于内容构建安装（控件的配方派生在构建期捕获主题信号对象），
     * 故在这里解析一次、逐个交给页面宿主的构造。{@code text-probe} 不接收外观档——它的前景色是写死的
     * 固定值，装主题对它没有任何影响，收了也是空参数。</p>
     *
     * @param request 请求
     * @param inputSource 输入源（脚本已编译进设备）
     * @param environment 请求声明的环境端口；每个页面宿主都按构造依赖接收，不得回落生产单例
     * @return 装配结果（渲染面 + 该面的 runtime）
     */
    private static HostBinding createHost(HeadlessRequest request, HeadlessInputSource inputSource,
            UiEnvironment environment) {
        SceneTheme theme = HeadlessThemes.resolve(request.theme());
        if ("playground".equals(request.pageId())) {
            TestPlaygroundHost playgroundHost = new TestPlaygroundHost(inputSource, environment, theme);
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
                    environment, theme);
            return new HostBinding(chatProbe, chatProbe.runtime());
        }

        if (HeadlessRequest.HUD_PAGE.equals(request.pageId())) {
            HudSceneProbeHost hudProbe = new HudSceneProbeHost(request.width(), request.height(),
                    ChatSceneProbeHost.splitMessages(request.text()), request.pageIndex(),
                    request.clockMillis(), environment, theme);
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
                advanceOneFrame(renderedFrames);
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
        // 诊断开关是**环境量**：判断它是否打开必须读会话环境端口（environment.diagnostics()），
        // 不得旁路读 request.diagnostics()。两者在 headless 里当前同值，但旁路读会让「--debug 是否
        // 真的接到采样器」在源码层不可验证 —— 把环境端口掐断（HeadlessEnvironment.of(false)）而
        // 此处照旧读请求时，perf 行仍会打印（内容全 0），出图验收单测因此失效（独立复核实测）。
        String performance = environment.diagnostics().debugEnabled()
                ? monitor.getRuntimeStats().toString() : null;
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

    /** @return 当前可寻址根数量（装配树根 + 活跃浮层根） */
    public int rootCount() {
        ensureOpen();
        return runtime.__addressableRoots().size();
    }

    /**
     * 驱动帧但不写 PNG：供寻址查询用（布局结果要等帧管线写回 cachedLayout）。
     *
     * <p><b>与 {@link #capture()} 共用同一帧驱动原语</b>（{@link #advanceOneFrame}），区别只在
     * 「读不读像素、写不写 PNG」—— 查询态与出图态必须看到同一棵树，帧前置/后置配平也不能有两套。</p>
     *
     * <p>踩过的坑（独立复核实测，6/6 稳定复现）：本方法最初只调 {@code host.render(...)}，
     * 缺了帧缓冲绑定与帧前置状态（正交投影 / viewport / 混合），字形上传事务因此被回滚 ——
     * 表现为每次查询必打 {@code glError=1282 UPLOAD_TRANSACTION_ROLLED_BACK} 且日志被污染。
     * 「少写几行也能跑」正是这类静默降级的温床，故改为共用原语。</p>
     *
     * @return 实际推进的帧数
     */
    public int advanceFramesForQuery() {
        ensureOpen();
        int rendered = 0;
        int glError = 0;
        // 至少推进 request.frames() 帧（保证布局落定），此后**只要脚本还有待办就继续推进**。
        // 不这么做的话「--actions="… click …" --nodes」会在脚本只跑了两帧时就去投影，
        // 查询到的是点击之前的树 —— 输出完全正常，是个静默空测（独立复核据此得出过错误结论）。
        // 上限沿用 maxFrames，避免不收敛的脚本把查询挂死。
        while (rendered < request.frames() || inputSource.device().hasPendingWork()) {
            if (rendered >= request.maxFrames()) {
                throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY,
                        "输入脚本在 " + request.maxFrames() + " 帧内未执行完（已推进 " + rendered
                                + " 帧）；请抬高 --max-frames 或检查脚本是否自续");
            }
            advanceOneFrame(rendered);
            rendered++;
            int frameError = surface.consumeGlError();
            if (frameError != 0 && glError == 0) {
                glError = frameError;
            }
        }
        // 查询路径此前不消费 GL 错误：帧坏了照样打印一份看似正常的事实表，
        // 变异测试证明这种静默会让「查询路径已修好」的门禁失效。故与出图同口径，坏帧即失败。
        if (glError != 0) {
            throw new HeadlessFailure(HeadlessFailure.Stage.FRAME,
                    "查询路径存在 GL 错误（glGetError=" + glError + "），事实表不可信");
        }
        return rendered;
    }

    /**
     * 推进一帧：帧缓冲绑定 → 帧前置（正交投影 / viewport / 混合）→ 生产宿主 render → 配对收尾。
     *
     * <p>{@link #capture()} 与 {@link #advanceFramesForQuery()} 的唯一帧驱动实现；两处若各写一份，
     * 帧前置漏一项就是上面那条 GL 错误。</p>
     *
     * @param renderedFrames 已推进帧数（仅用于失败信息定位）
     */
    private void advanceOneFrame(int renderedFrames) {
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
    }

    /**
     * 投影当前场景树：把「画布上有什么、在哪、能不能点」变成可寻址的事实表。
     *
     * <p>必须在至少推进过一帧之后调用：布局结果由帧管线写入节点的 cachedLayout，
     * 未布局的树投影出来坐标与尺寸全为 0（本方法如实报 0，不猜）。</p>
     *
     * @param interactiveOnly true = 只留可命中节点（其祖先链保留以给出路径）
     * @return 按根登记顺序、深度优先的事实行
     */
    public List<HeadlessTreeProjection.Row> projectTree(boolean interactiveOnly) {
        ensureOpen();
        List<HeadlessTreeProjection.Row> rows = new ArrayList<HeadlessTreeProjection.Row>();
        List<SceneNode> roots = runtime.__addressableRoots();
        // 命中入口只有一个：主树根。浮层由路由器自己按 top-first 检查 —— 若在此把每个浮层根都
        // 当成独立主树去命中，浮层的遮挡关系就被调用方拍平了，标注出的 [target] 会与实际派发不符。
        SceneNode mainRoot = runtime.__assemblyRoots().isEmpty() ? null : runtime.__assemblyRoots().get(0);
        HeadlessTreeProjection.HitProbe probe = mainRoot == null ? null
                : new HeadlessTreeProjection.HitProbe() {
                    @Override
                    public List<SceneNode> hitChainAt(SceneNode tree, int x, int y) {
                        // x,y 是 tree 自己的坐标空间；是不是画布空间、要不要叠加浮层锚点，由路由器
                        // 按同一处换算判定（__probeHitChain 的 tree 参数）。投影不猜这件事。
                        return runtime.getInputRouter().__probeHitChain(mainRoot, tree, x, y);
                    }

                    @Override
                    public int[] canvasBoxOf(SceneNode tree, int localX, int localY, int width, int height) {
                        return runtime.getInputRouter().__toCanvasBox(tree, localX, localY, width, height);
                    }

                    @Override
                    public String labelOf(SceneNode node) {
                        return addressOf(node);
                    }
                };
        for (int i = 0; i < roots.size(); i++) {
            rows.addAll(HeadlessTreeProjection.project(roots.get(i), i, interactiveOnly, probe));
        }
        return rows;
    }

    /**
     * 反查节点的<b>可执行地址</b>（形如 {@code r1/0/2 SceneNode}）；不在任何可寻址根下时返回 null。
     *
     * <p>与 {@link #centerOf(String)} 是同一套地址空间的<b>反方向</b>：那边由地址找节点，这边由节点
     * 找地址。两者共用 {@link #runtime}.__addressableRoots() 这一个事实源，因此报出的地址保证可被
     * {@code --center} 解回来 —— 诊断里给出的「被谁挡住了」如果指向一个解析不了的地址，
     * 那它比不给还糟。</p>
     *
     * @param node 目标节点；null 返回 null
     * @return {@code "<地址> <类型>"}；节点不在可寻址根下时 null
     */
    private String addressOf(SceneNode node) {
        if (node == null) {
            return null;
        }
        List<SceneNode> roots = runtime.__addressableRoots();
        for (int i = 0; i < roots.size(); i++) {
            List<Integer> indexes = childIndexesFrom(roots.get(i), node);
            if (indexes != null) {
                return HeadlessNodePath.of(i, indexes) + " " + node.getClass().getSimpleName();
            }
        }
        return null;
    }

    /**
     * 自下而上求节点在指定根下的子下标序列；不在该根下返回 null。
     *
     * <p>不缓存索引：投影与反查都发生在查询路径，节点规模在千级、调用次数与投影行数同阶，
     * 现算比维护一份「树变了要失效」的映射更不容易出错（静态快照必须有失效通道，此处干脆不留）。</p>
     */
    private static List<Integer> childIndexesFrom(SceneNode root, SceneNode node) {
        List<Integer> indexes = new ArrayList<Integer>();
        SceneNode current = node;
        while (current != root) {
            SceneNode parent = current.__getParent();
            if (parent == null) {
                return null;
            }
            int index = parent.__getChildren().indexOf(current);
            if (index < 0) {
                return null;
            }
            indexes.add(0, Integer.valueOf(index));
            current = parent;
        }
        return indexes;
    }

    /**
     * 按可见文本找可命中节点（大小写不敏感子串匹配）。
     *
     * <p>这是「按目标寻址」的查询面：agent 说出想点的文字（按钮文案、列表项），拿到地址与坐标，
     * 而不是自己数像素。命中多个时按投影顺序（= z-order 深度优先）返回，调用方自行取舍。</p>
     *
     * @param text 要匹配的文本片段（null / 空串返回空表）
     * @return 匹配的事实行
     */
    public List<HeadlessTreeProjection.Row> findByText(String text) {
        List<HeadlessTreeProjection.Row> matched = new ArrayList<HeadlessTreeProjection.Row>();
        if (text == null || text.isEmpty()) {
            return matched;
        }
        for (HeadlessTreeProjection.Row row : projectTree(true)) {
            if (HeadlessTreeProjection.matchesText(row, text)) {
                matched.add(row);
            }
        }
        return matched;
    }

    /**
     * 解析节点地址并返回该节点当前的中心点坐标。
     *
     * @param path 地址文本（r0/3/1）
     * @return 中心点（长度 2 的数组：x, y）
     * @throws HeadlessFailure 地址非法、越界，或该节点尚未布局
     */
    public int[] centerOf(String path) {
        HeadlessNodePath nodePath = HeadlessNodePath.parse(path);
        List<SceneNode> roots = runtime.__addressableRoots();
        if (nodePath.rootIndex() >= roots.size()) {
            throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY,
                    "根序号越界：" + path + "（当前树根数 " + roots.size() + "）");
        }
        SceneNode node = nodePath.resolve(roots.get(nodePath.rootIndex()));
        if (!(node.getCachedLayout() instanceof LayoutBox)) {
            throw new HeadlessFailure(HeadlessFailure.Stage.CAPABILITY,
                    "节点尚未布局，取不到坐标：" + path + "（先推进至少一帧再寻址）");
        }
        // 绝对盒走权威单点（含祖先滚动偏移注入），不自己累加局部坐标。
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return canvasCenter(node, box);
    }

    /**
     * 把「某棵树坐标空间下的盒」换算成<b>画布坐标</b>的中心点。
     *
     * <p><b>为什么必须有这一步</b>：浮层根被布局在自己的坐标空间里，它在画布上的位置由锚点
     * （{@code anchorX/anchorY}）与相对倍率（{@code relativeScale}）决定，局部 {@code (0,0)} 通常不在
     * 画布原点。{@link SceneGeometry#absoluteBox}(node, 0, 0) 给的是局部坐标 —— 对主树恰好等于画布
     * 坐标，对锚定浮层则不是。曾经的后果（实测）：锚定上下文菜单里每个菜单项都报「点不到」，
     * 且 {@code --center} 解出的坐标点下去落在主树空白处。</p>
     *
     * <p>换算口径与 {@code SceneFramePipeline.toHostLogicalBox} / {@code SceneInputRouter} 的浮层换算
     * 一致：画布 = 局部 × s + 锚点（{@code s == 1} 时退化为纯平移；锚定浮层的 s 恒为 1）。</p>
     *
     * @param node 该坐标所属的节点（用于判定它属于哪个浮层）
     * @param box  该节点在其所属树坐标空间下的绝对盒
     * @return 画布坐标下的中心点（长度 2 的数组：x, y）
     */
    private int[] canvasCenter(SceneNode node, AnchorRect box) {
        int localX = box.getX() + box.getWidth() / 2;
        int localY = box.getY() + box.getHeight() / 2;
        return runtime.getInputRouter().__toCanvasPoint(node, localX, localY);
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
