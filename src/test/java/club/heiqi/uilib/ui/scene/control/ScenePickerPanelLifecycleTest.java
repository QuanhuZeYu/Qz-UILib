package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.testkit.SceneTestEnvironments;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.ui.diagnostic.UiPerfMarkers;
import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.GridProps;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.Props;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.Result;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;

/**
 * 面板生命周期守卫（ADR §4，A-15/A-16 的 build 级证据）。
 *
 * <p>契约：候选相关派生（memberIssues/filtered/gridItems/categoryRows/clampHighlight/VariantChooser）
 * 全部活在 {@code rt.portal(open, ...)} 的内容 Owner 内 —— 「关闭即停算」由 owner 作用域保证，
 * 而不是由 {@code if(!open) return} 逐帧门控。本类用<b>已接线</b>的计数埋点证明三件事：</p>
 * <ol>
 *   <li>关闭后（结果信号仍会变化）面板不再产出任何 {@code picker.*} 派生计数的增长；</li>
 *   <li>重开重新派生（内容 Owner 重建），关闭再次停止；</li>
 *   <li>20 次开合：effect 数每次关闭回到构建基线、打开态 {@code frame.nodes} 不漂移（无单调增长）。</li>
 * </ol>
 */
public class ScenePickerPanelLifecycleTest {

    private static final String SCREEN = "picker-lifecycle";
    private static final int W = 800;
    private static final int H = 600;

    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private SceneFramePipeline pipeline;
    private SceneNode sceneRoot;
    private MountHandle mountHandle;
    private long frameTimeNanos = 1_000_000L;
    private boolean originalUseDebug;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        originalUseDebug = Config.useDebug;
        Config.useDebug = true;
        UiPerformanceMonitor.getInstance().finishFrame();
        UiPerformanceMonitor.getInstance().resetHistory(SCREEN);
        SceneTextMeasurer measurer = new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
        // 诊断开关双源：帧管线采样判定走注入的环境端口（本用例断 frame.nodes，必须有采样），
        // UiPerformanceMonitor 的计数记录与统计读取仍是 Config.useDebug 静态直读（未接线），
        // 故 setUp 里那句静态赋值同样不可省 —— 两者同时打开才构成完整采样语义。
        rt = SceneTestEnvironments.runtime(measurer, SceneTestEnvironments.debugEnabled());
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        pipeline = new SceneFramePipeline(rt, layoutEngine, paintEngine, new ScenePaintReplayer(), measurer, null);
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        if (mountHandle != null) mountHandle.dispose();
        UiPerformanceMonitor.getInstance().finishFrame();
        UiPerformanceMonitor.getInstance().resetHistory(SCREEN);
        Config.useDebug = originalUseDebug;
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== A-15：关闭即停算 ====================

    /**
     * 关闭后结果信号变化不得触发任何面板候选派生（{@code picker.candidates} 计数缺席 = 机器可核证据）；
     * 重开后重新派生。
     *
     * <p>反证：把 Computed 建在 create 期（P4 前形态）时，{@code filtered/gridItems} 会在关闭状态下
     * 随 results 信号变化继续重算并写入 {@code picker.candidates} —— 本用例即该形态的变异检查点。</p>
     */
    @Test
    public void closedPanelStopsCandidateDerivationAndReopenResumes() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")));
        f.open.set(Boolean.TRUE);
        String openedCounters = countersOverTwoFrames();
        Assert.assertTrue("正锚：打开后必须产出候选派生计数",
                openedCounters.contains(UiPerfMarkers.COUNTER_PICKER_CANDIDATES));
        Assert.assertEquals("正锚：内容已挂载", 1, rt.getOverlayHost().size());

        f.open.set(Boolean.FALSE);
        runFrame();
        Assert.assertEquals("关闭后 overlay 必须清空", 0, rt.getOverlayHost().size());

        // 关闭状态下更换结果：内容 Owner 已 dispose，任何面板候选派生都必须缺席。
        int readsAfterClose = f.results.reads;
        f.results.set(new SearchPickerData.SearchResult(Arrays.asList(candidate("c"), candidate("d"))));
        UiRuntimeStats closed = runFrame();
        runFrame();
        Assert.assertEquals("关闭后结果信号不得被读取（面板候选派生已随内容 Owner 停止）",
                readsAfterClose, f.results.reads);
        Assert.assertFalse("关闭后结果变化不得触发候选派生（picker.candidates 必须缺席）",
                closed.getCounterSummary().contains(UiPerfMarkers.COUNTER_PICKER_CANDIDATES));
        Assert.assertFalse("关闭后结果变化不得触发网格派生（picker.results 必须缺席）",
                closed.getCounterSummary().contains(UiPerfMarkers.COUNTER_PICKER_RESULTS));

        f.open.set(Boolean.TRUE);
        String reopenedCounters = countersOverTwoFrames();
        Assert.assertTrue("重开必须重新派生（内容 Owner 重建）",
                reopenedCounters.contains(UiPerfMarkers.COUNTER_PICKER_CANDIDATES));
        Assert.assertNotNull("重开后列表视口可用", f.result.grid().get());
    }

    /** 变体浮层订阅随内容 Owner 回收：主面板直接关闭时浮层不得残留。 */
    @Test
    public void variantOverlaySubscriptionsStopWhenPanelCloses() {
        Fixture f = new Fixture(Collections.singletonList(candidateWithVariants("a", "v1", "v2")));
        int created = ReactiveTestProbe.registeredEffectCount();
        f.open.set(Boolean.TRUE);
        runFrame();
        runFrame();
        SceneNode cell = gridCell(f.result.grid().get(), 0);
        click(cell);
        runFrame();
        Assert.assertTrue("前置：点击带变体候选应打开变体浮层", f.result.variantsOpen().get().booleanValue());
        Assert.assertEquals("前置：主面板 + 变体浮层两个 overlay", 2, rt.getOverlayHost().size());

        f.open.set(Boolean.FALSE);
        runFrame();
        Assert.assertEquals("关闭主面板必须摘除变体浮层", 0, rt.getOverlayHost().size());
        Assert.assertEquals("关闭后 effect 必须回到面板构建基线",
                created, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== A-16：20 次开合无增长倾向 ====================

    /**
     * 同屏 20 次开合：每次关闭 effect 回到构建基线、{@code frame.nodes} 回到关闭基线；
     * 打开态的 {@code frame.nodes} 全程同一值（无单调增长、无残留子树）。
     */
    @Test
    public void twentyOpenCloseCyclesKeepEffectsAndFrameNodesFlat() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b"), candidate("c")));
        long closedNodes = frameNodes(runFrame());
        int created = ReactiveTestProbe.registeredEffectCount();
        Set<Long> openNodeCounts = new HashSet<Long>();
        for (int cycle = 0; cycle < 20; cycle++) {
            f.open.set(Boolean.TRUE);
            openNodeCounts.add(Long.valueOf(frameNodes(runFrame())));
            openNodeCounts.add(Long.valueOf(frameNodes(runFrame())));
            Assert.assertEquals("第 " + cycle + " 次打开：panel overlay 在场", 1, rt.getOverlayHost().size());

            f.open.set(Boolean.FALSE);
            UiRuntimeStats closed = runFrame();
            Assert.assertEquals("第 " + cycle + " 次关闭：overlay 清空", 0, rt.getOverlayHost().size());
            Assert.assertEquals("第 " + cycle + " 次关闭：effect 回到构建基线",
                    created, ReactiveTestProbe.registeredEffectCount());
            Assert.assertEquals("第 " + cycle + " 次关闭：帧节点数回到关闭基线",
                    closedNodes, frameNodes(closed));
        }
        Assert.assertEquals("20 次开合的打开态 frame.nodes 必须只有同一值（无增长倾向）: " + openNodeCounts,
                1, openNodeCounts.size());
        Assert.assertTrue("打开态节点数必须大于关闭态（隐藏面板真实挂载）",
                openNodeCounts.iterator().next().longValue() > closedNodes);
    }

    // ==================== A-15 源码守卫 ====================

    /**
     * 源码守卫（ADR §7 A-15）：面板**不得**以 open 作为求值门控 —— 「关闭即停算」只能来自
     * owner 作用域（{@code rt.portal} 内容 Owner）与订阅边界。
     *
     * <p>先剥注释再扫（类注释里就写着「禁止 if(!open) 逐帧门控」，不剥会把规矩本身当成违规）；
     * 再用正锚钉住「open 确实交给了 portal/状态机」，避免否定断言在改错文件时真空真。</p>
     */
    @Test
    public void panelSourceHasNoOpenGatedEvaluation() throws Exception {
        String code = codeWithoutComments(new String(Files.readAllBytes(Paths.get(
                "src/main/java/club/heiqi/uilib/ui/scene/control/ScenePickerPanel.java")),
                StandardCharsets.UTF_8));
        Assert.assertTrue("正锚：open 必须交给 portal 与状态机",
                code.contains("rt.portal(open") && code.contains("rt.bind(open"));
        Assert.assertFalse("面板不得用 open 做逐帧/逐次求值门控（ADR §4.3）",
                code.contains("open.get()") || code.contains("!open")
                        || code.contains("Boolean.TRUE.equals(open)"));
    }

    /** 剥离注释（源码守卫只对真实代码生效；与 PickerSourceCallSiteGuardTest 同法）。 */
    private static String codeWithoutComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        int length = source.length();
        while (index < length) {
            char current = source.charAt(index);
            if (current == '/' && index + 1 < length && source.charAt(index + 1) == '/') {
                while (index < length && source.charAt(index) != '\n') index++;
            } else if (current == '/' && index + 1 < length && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < length && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    index++;
                }
                index += 2;
            } else if (current == '"') {
                out.append(current);
                index++;
                while (index < length && source.charAt(index) != '"') {
                    if (source.charAt(index) == '\\') {
                        out.append(source.charAt(index));
                        index++;
                    }
                    if (index < length) {
                        out.append(source.charAt(index));
                        index++;
                    }
                }
                if (index < length) {
                    out.append(source.charAt(index));
                    index++;
                }
            } else {
                out.append(current);
                index++;
            }
        }
        return out.toString();
    }

    // ==================== 宿主帧驱动 ====================

    /** 跑一帧完整 pipeline（布局 + flush + 绘制 + 回放），返回该帧采样统计。 */
    private UiRuntimeStats runFrame() {
        UiPerformanceMonitor monitor = UiPerformanceMonitor.getInstance();
        frameTimeNanos += 16_000_000L;
        monitor.beginFrame(SCREEN, W, H, W, H);
        try {
            pipeline.run(sceneRoot, W, H, new RecordingRenderBackend(), 0, 0, frameTimeNanos);
        } finally {
            monitor.finishFrame();
        }
        return monitor.getRuntimeStats();
    }

    /**
     * 连续两帧的计数摘要合并：挂载帧与新注册 Computed 的首次求值可能落在相邻两帧
     * （内容树的 Computed 在本帧 flush 内注册、求值时机由调度器决定），断言只看「是否发生」。
     */
    private String countersOverTwoFrames() {
        String first = runFrame().getCounterSummary();
        String second = runFrame().getCounterSummary();
        return first + "；" + second;
    }

    private static long frameNodes(UiRuntimeStats stats) {
        return counterTotal(stats.getCounterSummary(), UiPerfMarkers.COUNTER_FRAME_NODES);
    }

    /** 解析计数摘要 {@code name=total/max×samples}（{@code ；} 分隔）；缺席返回 0。 */
    private static long counterTotal(String summary, String name) {
        if (summary == null || summary.isEmpty()) {
            return 0L;
        }
        for (String entry : summary.split("；")) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || !entry.substring(0, eq).equals(name)) {
                continue;
            }
            int slash = entry.indexOf('/', eq);
            String total = slash < 0 ? entry.substring(eq + 1) : entry.substring(eq + 1, slash);
            return Long.parseLong(total.trim());
        }
        return 0L;
    }

    private void click(SceneNode node) {
        int[] center = centerOf(node);
        InputFrameBuilder fb = new InputFrameBuilder(center[0], center[1]);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, center[0], center[1],
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, center[0], center[1],
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1001L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
    }

    private static int[] centerOf(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            throw new IllegalStateException("节点未布局或零尺寸，无法取中心: " + box);
        }
        return new int[] { box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2 };
    }

    /** 窗口内列表单元（按行序平铺）。 */
    private static SceneNode gridCell(SceneNode viewport, int index) {
        SceneNode rowsContainer = viewport.__getChildren().get(0).__getChildren().get(1);
        for (SceneNode row : rowsContainer.__getChildren()) {
            if (index < row.__getChildren().size()) {
                return row.__getChildren().get(index);
            }
            index -= row.__getChildren().size();
        }
        throw new IllegalStateException("cell index out of mounted window: " + index);
    }

    // ==================== 夹具 ====================

    /** 受控开合夹具：results 直供（旧全量路径），验证 owner 作用域的停止语义。 */
    private final class Fixture {
        final Signal<String> query = Signal.create("");
        /** 计数读入口：候选数据在关闭状态下被读取的次数（「关闭即停算」的直接探针）。 */
        final CountingResults results;
        final Signal<Boolean> open = Signal.create(Boolean.FALSE);
        final Result result;

        Fixture(List<SearchPickerData.Candidate> initialCandidates) {
            results = new CountingResults(new SearchPickerData.SearchResult(initialCandidates));
            final Result[] holder = new Result[1];
            mountHandle = rt.mount(sceneRoot, () -> {
                holder[0] = ScenePickerPanel.create(rt, Props.builder(query, results,
                        Signal.create(Boolean.TRUE), query::set, ignored -> { }, visualAdapter())
                        .open(open)
                        .onCloseRequest(() -> open.set(Boolean.FALSE))
                        .grid(GridProps.of(3, 64, 64, 8, 8, 3))
                        .build());
                return holder[0].root();
            });
            result = holder[0];
        }
    }

    private static VisualAdapter visualAdapter() {
        return new VisualAdapter() {
            @Override
            public String candidateLabel(SearchPickerData.Candidate candidate) {
                return candidate.label();
            }

            @Override
            public String variantLabel(SearchPickerData.Variant variant) {
                return variant.label();
            }
        };
    }

    /** 只读计数包装：统计候选数据信号在关闭状态下的读取次数（0 = 确实没有派生在跑）。 */
    private static final class CountingResults
            implements club.heiqi.uilib.ui.reactive.ReadableSignal<SearchPickerData.SearchResult> {
        private final Signal<SearchPickerData.SearchResult> delegate;
        private int reads;

        CountingResults(SearchPickerData.SearchResult initial) {
            delegate = Signal.create(initial);
        }

        @Override
        public SearchPickerData.SearchResult get() {
            reads++;
            return delegate.get();
        }

        void set(SearchPickerData.SearchResult next) {
            delegate.set(next);
        }
    }

    private static SearchPickerData.Candidate candidate(String key) {
        return new SearchPickerData.Candidate(key, key + ":label",
                Collections.<SearchPickerData.Variant>emptyList());
    }

    private static SearchPickerData.Candidate candidateWithVariants(String key, String... variantKeys) {
        ArrayList<SearchPickerData.Variant> variants = new ArrayList<SearchPickerData.Variant>();
        for (String variantKey : variantKeys) {
            variants.add(new SearchPickerData.Variant(variantKey, variantKey + ":label"));
        }
        return new SearchPickerData.Candidate(key, key + ":label", variants);
    }
}
