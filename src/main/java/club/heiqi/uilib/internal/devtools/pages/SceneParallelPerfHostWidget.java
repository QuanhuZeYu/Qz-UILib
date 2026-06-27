package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.diagnostic.FrameRateProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.SceneParallelExecutor;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;

/**
 * Scene 布局/绘制并行性能真机实测宿主 Widget。
 *
 * <p>本页用于量化 scene layout/paint 并行路径相对串行路径的 fps 加速比，
 * 为 {@link SceneParallelExecutor#setParallelEnabled} 默认值决策与
 * fork 阈值校准提供真机数据依据。</p>
 *
 * <h3>三组对照实验（页面内引导文字提示用户怎么跑）</h3>
 * <ul>
 *   <li>实验1 小树负优化：节点数=50，切并行 ON/OFF 比 fps（预期 ON 持平或略慢）</li>
 *   <li>实验2 大树收益：节点数=1000，切并行 ON/OFF 算加速比 = fps(ON)/fps(OFF)</li>
 *   <li>实验3 阈值扫描：节点数=1000 并行 ON，forkThreshold 扫 32→64→128→256 找拐点</li>
 * </ul>
 *
 * <h3>树结构：深窄分支（核心，决定能否测出并行收益）</h3>
 * <p>内容区下挂 K 个「分支容器」，每个分支是一棵子树，节点数 ≈ 总节点数/K。
 * 让每个分支子树节点数 &gt; forkThreshold，这些分支才会各自被 fork 到不同 worker。
 * K 取 {@link SceneParallelExecutor#getPool()} 的并行度，对齐 worker 数量。
 * FboPerf 的「N 行 × 每行 10 叶」宽扁结构单行子树 ~11 节点永远 &lt; forkThreshold(64)，
 * 子树永不 fork，测不出并行——本页必须用深窄结构。</p>
 *
 * <h3>阈值联动</h3>
 * <p>{@link SceneParallelExecutor} 有 4 个独立阈值（layout/paint 各一对 fork+wholeTree）。
 * 本页 slider 设计为 layout/paint 联动同步设同值（2 个 slider），简化操作，
 * 校准目标是找统一拐点。</p>
 */
public class SceneParallelPerfHostWidget extends AbstractSceneHostWidget {

    /** 根背景色。 */
    private static final int ROOT_BG = 0xFF08111F;
    /** 标题文本色。 */
    private static final int TITLE_COLOR = 0xFFEAF1FF;
    /** 次要说明文本色。 */
    private static final int MUTED_COLOR = 0xFF8AA0C8;
    /** 监测条背景色。 */
    private static final int MONITOR_BG = 0xFF111C31;
    /** 内容区背景色。 */
    private static final int CONTENT_BG = 0xFF0D1728;
    /** 测试节点背景色（蓝）。 */
    private static final int NODE_BG = 0xFF2F6FB0;
    /** 标题条固定高度。 */
    private static final int TITLE_BAR_HEIGHT = 44;
    /** 监测条固定高度（拆两行，加高容纳两行文本）。 */
    private static final int MONITOR_BAR_HEIGHT = 56;
    /** 底部操作条固定高度（拆两行，加高容纳按钮排 + slider 排）。 */
    private static final int ACTION_BAR_HEIGHT = 80;
    /** 测试节点固定宽。 */
    private static final int NODE_W = 40;
    /** 测试节点固定高。 */
    private static final int NODE_H = 40;
    /** 每行排列的测试节点数。 */
    private static final int NODES_PER_ROW = 10;
    /** 行间距/列间距。 */
    private static final int CELL_GAP = 8;
    /** 引导文字字号（UI 像素）。 */
    private static final int GUIDE_FONT_SIZE = 9;
    /** 叶子文本字号（UI 像素），与引导文字同号即可，叶子文本会被裁但 measureWidth 仍执行。 */
    private static final int LEAF_FONT_SIZE = GUIDE_FONT_SIZE;

    /** 监测条文本刷新间隔（纳秒），200ms 约 5 次/秒，足够人眼读数且不污染测量帧。 */
    private static final long DISPLAY_INTERVAL_NANOS = 200_000_000L;

    /** 根节点。 */
    private final SceneNode root;
    /** 内容区容器（rebuild 时清空并重建引导块 + 测试分支）。 */
    private final SceneNode content;
    /** fps 文本节点（每帧直接 setText，不走 bind）。 */
    private final SceneNode fpsText;
    /** 统计文本节点（每帧直接 setText，不走 bind）。 */
    private final SceneNode statsText;
    /** 并行状态文本节点（每帧直接 setText，不走 bind）。 */
    private final SceneNode parallelText;

    /** 测试节点数量 signal（初始 1000，范围 0-2000，step 50；>256 才能触发并行路径）。 */
    private final Signal<Integer> nodeCountSignal;
    /** 并行开关按钮 label（随状态翻转）。 */
    private final Signal<String> parallelLabel;

    /** 节点数 slider 标签（实时显示「节点数: 当前值」，拖拽预览期也更新）。 */
    private SceneNode nodeCountLabel;
    /** fork阈值 slider 标签（实时显示「fork阈值: 当前值」，拖拽预览期也更新）。 */
    private SceneNode forkThresholdLabel;
    /** 整树阈值 slider 标签（实时显示「整树阈值: 当前值」，拖拽预览期也更新）。 */
    private SceneNode wholeThresholdLabel;

    /** 上次刷新监测条文本的 nanoTime 时间戳，用于 setText 节流。 */
    private long lastDisplayNanos;
    /** 预热是否已完成。 */
    private boolean warmedUp;

    /** 叶子编号计数器：rebuild 时重置为 0，每个叶子文本 "节点 #N" 用递增 N 避免全命中 widthCache。 */
    private int leafCounter;

    /**
     * 创建 Scene 并行性能真机实测宿主 Widget。
     *
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    public SceneParallelPerfHostWidget(PlatformInputSource inputSource) {
        super(inputSource);
        this.nodeCountSignal = Signal.create(Integer.valueOf(1000));
        this.parallelLabel = Signal.create(
                SceneParallelExecutor.isParallelEnabled() ? "并行: ON" : "并行: OFF");

        this.root = createRoot();
        root.appendChild(createTitleBar());

        SceneNode monitorBar = createMonitorBar();
        root.appendChild(monitorBar);
        // monitorBar(COLUMN) → row1(ROW)[fpsText, statsText], row2(ROW)[parallelText]
        SceneNode monitorRow1 = (SceneNode) monitorBar.__getChildren().get(0);
        this.fpsText = (SceneNode) monitorRow1.__getChildren().get(0);
        this.statsText = (SceneNode) monitorRow1.__getChildren().get(1);
        SceneNode monitorRow2 = (SceneNode) monitorBar.__getChildren().get(1);
        this.parallelText = (SceneNode) monitorRow2.__getChildren().get(0);

        this.content = createContent();
        root.appendChild(content);

        root.appendChild(createActionBar());

        // 构造末尾：rebuild（内含建池读并行度）→ warmUp → flush
        rebuild();
        warmUp();
        runtime.flush();
    }

    /**
     * 每帧渲染后从基类 {@link #frameProbe} 读取 fps 与帧耗时，节流后 setText 到监测条文本节点。
     *
     * <p>帧采样已由基类 {@link AbstractSceneHostWidget#render} 内 {@link FrameRateProbe#tick()}
     * 自动完成，本页只负责读取统计 + 显示节流。不走 bind/boundText，避免 invalidation 刷新时序问题。</p>
     *
     * @param w 宿主宽度
     * @param h 宿主高度
     * @param ctx 渲染出口
     * @param absX 宿主绝对 X 偏移
     * @param absY 宿主绝对 Y 偏移
     */
    @Override
    public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
        super.render(w, h, ctx, absX, absY);

        // setText 节流：每 200ms 才刷新一次显示，避免每帧 setText 干扰测量。
        long now = System.nanoTime();
        if (now - lastDisplayNanos < DISPLAY_INTERVAL_NANOS) {
            return;
        }
        lastDisplayNanos = now;

        // 第一行：fps + 帧统计
        double avgFps = frameProbe.getAverageFps();
        double avgMs = frameProbe.getAverageFrameTimeMs();
        double maxMs = frameProbe.getMaxFrameTimeMs();
        int slowCount = frameProbe.getSlowFrameCount();
        int sampledCount = frameProbe.getSampledFrameCount();

        fpsText.setText(String.format("fps=%.1f", Double.valueOf(avgFps)));
        statsText.setText(String.format("frame=%.2fms  max=%.2fms  slow=%d/%d",
                Double.valueOf(avgMs),
                Double.valueOf(maxMs),
                Integer.valueOf(slowCount),
                Integer.valueOf(sampledCount)));

        // 第二行：并行状态 + 节点数 + 阈值 + 是否触发
        boolean enabled = SceneParallelExecutor.isParallelEnabled();
        int nodeCount = root.__getCachedSubtreeNodeCount();
        int wholeTh = SceneParallelExecutor.getPaintWholeTreeThreshold();
        int forkTh = SceneParallelExecutor.getPaintForkThreshold();
        boolean wholeHit = nodeCount >= wholeTh;
        parallelText.setText(String.format("并行=%s  节点=%d  整树阈=%d(%s)  fork阈=%d",
                enabled ? "ON" : "OFF",
                Integer.valueOf(nodeCount),
                Integer.valueOf(wholeTh),
                (enabled && wholeHit) ? "已触发" : "未触发",
                Integer.valueOf(forkTh)));
    }

    /**
     * 创建根容器。
     *
     * @return 根节点
     */
    private SceneNode createRoot() {
        SceneNode node = new SceneNode();
        node.setFillParentHeight(true);
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setPadding(20);
        node.setGap(12);
        node.setBackgroundColor(ROOT_BG);
        return node;
    }

    /**
     * 创建标题条。
     *
     * @return 标题条节点
     */
    private SceneNode createTitleBar() {
        SceneNode titleBar = new SceneNode();
        titleBar.setFlexDirection(FlexDirection.COLUMN);
        titleBar.setPreferredHeight(TITLE_BAR_HEIGHT);
        titleBar.setGap(4);
        titleBar.setHitTestable(false);
        titleBar.appendChild(text("Scene 布局/绘制并行性能实测", TITLE_COLOR));
        titleBar.appendChild(text("阶段2第三批 · fork阈值校准 + PARALLEL_ENABLED 决策", MUTED_COLOR));
        return titleBar;
    }

    /**
     * 创建性能监测条（拆两行：第一行 fps+stats，第二行 parallel 状态）。
     *
     * <p>文本节点引用保存为字段供每帧 setText。结构：
     * <pre>
     * monitorBar (COLUMN)
     *   ├─ row1 (ROW): fpsText, statsText
     *   └─ row2 (ROW): parallelText
     * </pre>
     * 构造期按此结构取 children 引用。</p>
     *
     * @return 监测条节点
     */
    private SceneNode createMonitorBar() {
        SceneNode column = new SceneNode();
        column.setFlexDirection(FlexDirection.COLUMN);
        column.setPreferredHeight(MONITOR_BAR_HEIGHT);
        column.setPadding(8);
        column.setGap(4);
        column.setBackgroundColor(MONITOR_BG);
        column.setCornerRadius(8);

        SceneNode row1 = new SceneNode();
        row1.setFlexDirection(FlexDirection.ROW);
        row1.setGap(24);
        row1.setHitTestable(false);
        row1.appendChild(text("fps=--", TITLE_COLOR));
        row1.appendChild(text("frame=--  max=--  slow=--/--", TITLE_COLOR));
        column.appendChild(row1);

        SceneNode row2 = new SceneNode();
        row2.setFlexDirection(FlexDirection.ROW);
        row2.setHitTestable(false);
        row2.appendChild(text("并行=--  节点=--  整树阈=--  fork阈=--", TITLE_COLOR));
        column.appendChild(row2);

        return column;
    }

    /**
     * 创建内容区容器（可滚动 + 裁剪，承载引导块 + 测试分支）。
     *
     * @return 内容区节点
     */
    private SceneNode createContent() {
        SceneNode node = new SceneNode();
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setFillParentHeight(true);
        node.setScrollable(true);
        node.setClipChildren(true);
        node.setPadding(10);
        node.setGap(CELL_GAP);
        node.setBackgroundColor(CONTENT_BG);
        node.setCornerRadius(10);
        return node;
    }

    /**
     * 创建底部调参操作条（拆两行：按钮排 + slider 排）。
     *
     * <p>结构：
     * <pre>
     * actionBar (COLUMN)
     *   ├─ buttonRow (ROW): 并行开关 / 预热 / 重置
     *   └─ sliderRow (ROW): [标签+节点数slider] [标签+fork阈slider] [标签+整树阈slider]
     * </pre>
     * slider 自身无内置 label，需在前面放裸 SceneNode 文字标签。</p>
     *
     * @return 操作条节点
     */
    private SceneNode createActionBar() {
        SceneNode column = new SceneNode();
        column.setFlexDirection(FlexDirection.COLUMN);
        column.setPreferredHeight(ACTION_BAR_HEIGHT);
        column.setGap(6);

        SceneNode buttonRow = new SceneNode();
        buttonRow.setFlexDirection(FlexDirection.ROW);
        buttonRow.setGap(10);
        mountParallelToggle(buttonRow);
        mountButton(buttonRow, "预热", this::warmUp);
        mountButton(buttonRow, "重置采样", this::resetSampling);
        column.appendChild(buttonRow);

        SceneNode sliderRow = new SceneNode();
        sliderRow.setFlexDirection(FlexDirection.ROW);
        sliderRow.setGap(16);
        nodeCountLabel = text("节点数: " + nodeCountSignal.get(), MUTED_COLOR);
        sliderRow.appendChild(nodeCountLabel);
        mountNodeCountSlider(sliderRow);
        forkThresholdLabel = text(
                "fork阈值: " + SceneParallelExecutor.getPaintForkThreshold(), MUTED_COLOR);
        sliderRow.appendChild(forkThresholdLabel);
        mountForkThresholdSlider(sliderRow);
        wholeThresholdLabel = text(
                "整树阈值: " + SceneParallelExecutor.getPaintWholeTreeThreshold(), MUTED_COLOR);
        sliderRow.appendChild(wholeThresholdLabel);
        mountWholeThresholdSlider(sliderRow);
        column.appendChild(sliderRow);

        return column;
    }

    /**
     * 挂载并行开关按钮（label 随状态翻转，点击不 rebuild 只 resetSampling）。
     *
     * @param parent 父节点
     */
    private void mountParallelToggle(SceneNode parent) {
        SceneButton.Props props = new SceneButton.Props(
                parallelLabel,
                Signal.create(Boolean.TRUE),
                () -> {
                    boolean next = !SceneParallelExecutor.isParallelEnabled();
                    SceneParallelExecutor.setParallelEnabled(next);
                    parallelLabel.set(next ? "并行: ON" : "并行: OFF");
                    resetSampling();   // 切开关清窗口，不 rebuild
                });
        SceneNode button = runtime.mount(parent, SceneButton.create(runtime, props)).getRoot();
        button.setPreferredWidth(110);
        button.setPreferredHeight(36);
    }

    /**
     * 挂载节点数 slider（0-2000，step 50，committing 时 rebuild）。
     *
     * @param parent 父节点
     */
    private void mountNodeCountSlider(SceneNode parent) {
        Signal<Double> value = Signal.create(Double.valueOf(nodeCountSignal.get()));
        SceneSlider.Props props = SceneSlider.Props.builder(value)
                .min(0)
                .max(2000)
                .step(50)
                .onChange((v, committing) -> {
                    value.set(Double.valueOf(v));
                    nodeCountLabel.setText("节点数: " + (int) Math.round(v));
                    if (committing) {
                        nodeCountSignal.set(Integer.valueOf((int) Math.round(v)));
                        rebuild();   // rebuild 内已含 resetSampling
                    }
                })
                .build();
        runtime.mount(parent, SceneSlider.create(runtime, props));
    }

    /**
     * 挂载 forkThreshold slider（16-512，step 16，联动 layout+paint，committing 时 resetSampling）。
     *
     * <p>阈值已运行时接线（见设计方案 §0.2），slider onChange 调 setter 后下一帧 layout/paint
     * 即生效，无需 rebuild 树，只需 resetSampling 清窗口。</p>
     *
     * @param parent 父节点
     */
    private void mountForkThresholdSlider(SceneNode parent) {
        Signal<Double> value = Signal.create(
                Double.valueOf(SceneParallelExecutor.getPaintForkThreshold()));
        SceneSlider.Props props = SceneSlider.Props.builder(value)
                .min(16)
                .max(512)
                .step(16)
                .onChange((v, committing) -> {
                    value.set(Double.valueOf(v));
                    forkThresholdLabel.setText("fork阈值: " + (int) Math.round(v));
                    if (committing) {
                        int th = (int) Math.round(v);
                        SceneParallelExecutor.setLayoutForkThreshold(th);
                        SceneParallelExecutor.setPaintForkThreshold(th);
                        resetSampling();   // 阈值已运行时接线，无需 rebuild
                    }
                })
                .build();
        runtime.mount(parent, SceneSlider.create(runtime, props));
    }

    /**
     * 挂载 wholeTreeThreshold slider（64-1024，step 64，联动 layout+paint，committing 时 resetSampling）。
     *
     * @param parent 父节点
     */
    private void mountWholeThresholdSlider(SceneNode parent) {
        Signal<Double> value = Signal.create(
                Double.valueOf(SceneParallelExecutor.getPaintWholeTreeThreshold()));
        SceneSlider.Props props = SceneSlider.Props.builder(value)
                .min(64)
                .max(1024)
                .step(64)
                .onChange((v, committing) -> {
                    value.set(Double.valueOf(v));
                    wholeThresholdLabel.setText("整树阈值: " + (int) Math.round(v));
                    if (committing) {
                        int th = (int) Math.round(v);
                        SceneParallelExecutor.setLayoutWholeTreeThreshold(th);
                        SceneParallelExecutor.setPaintWholeTreeThreshold(th);
                        resetSampling();
                    }
                })
                .build();
        runtime.mount(parent, SceneSlider.create(runtime, props));
    }

    /**
     * 挂载普通操作按钮。
     *
     * @param parent 父节点
     * @param label 按钮文案
     * @param onClick 点击回调
     */
    private void mountButton(SceneNode parent, String label, Runnable onClick) {
        SceneButton.Props props = new SceneButton.Props(Signal.create(label), Signal.create(Boolean.TRUE), onClick);
        SceneNode button = runtime.mount(parent, SceneButton.create(runtime, props)).getRoot();
        button.setPreferredWidth(100);
        button.setPreferredHeight(36);
    }

    /**
     * 重置 fps 采样历史：基类 probe 计数器归零 + 本页显示节流时间戳归零。
     */
    private void resetSampling() {
        resetFrameStats();
        lastDisplayNanos = 0L;
    }

    /**
     * 预热：强制建 ForkJoinPool + 预热文本度量缓存，避免首帧建池/冷测量污染 fps 窗口。
     *
     * <p>已核实 {@link club.heiqi.uilib.ui.scene.text.SceneTextMeasurer#measureWidth(String, int)}
     * 签名为 {@code measureWidth(String text, int fontSizePx)}，返回 int UI 像素宽度。
     * 本页 measurer 由基类 {@link AbstractSceneHostWidget} 构造期初始化为
     * {@code TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance())}，
     * 可直接调用预热本页所有静态文本的 widthCache。</p>
     */
    private void warmUp() {
        // 1. 预热 ForkJoinPool：强制建池，避免首次并行帧建池尖峰
        //    同时 rebuild() 内已调过 getPool() 读并行度，此处幂等返回已建池。
        SceneParallelExecutor.getPool();

        // 2. 预热文本度量缓存：循环度量本页所有静态文本，填 widthCache 避免冷启动首帧偏慢
        String[] staticTexts = new String[] {
                "Scene 布局/绘制并行性能实测",
                "阶段2第三批 · fork阈值校准 + PARALLEL_ENABLED 决策",
                "fps=--",
                "frame=--  max=--  slow=--/--",
                "并行=--  节点=--  整树阈=--  fork阈=--",
                "并行: ON", "并行: OFF",
                "预热", "重置采样",
                "节点数: 1000", "fork阈值: 64", "整树阈值: 256",
                "实验1 小树负优化：节点数=50 → 切并行 ON/OFF 各稳定3秒 → 比 fps",
                "实验2 大树收益  ：节点数=1000 → 切并行 ON/OFF 各稳定3秒 → 加速比",
                "实验3 阈值扫描  ：节点数=1000 并行 ON → forkThreshold 扫 32→64→128→256 → 找拐点",
                "每次改参后点[重置采样]，等慢帧数稳定后再读数"
        };
        for (String s : staticTexts) {
            measurer.measureWidth(s, GUIDE_FONT_SIZE);
        }

        // 3. 预热叶子文本字符集：叶子文本动态生成 "节点 #0".."节点 #1999"，无法全部预热。
        //    预热数字 0-9 + "节点 #" 前缀，让 widthCache 对这些字符命中，
        //    避免并行 worker 冷启动首次遇这些字符撞 DerivedFontCache synchronized miss 锁。
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(i);
        }
        sb.append("节点 #");
        measurer.measureWidth(sb.toString(), LEAF_FONT_SIZE);

        warmedUp = true;
    }

    /**
     * 按当前 nodeCount 清空内容区并重建引导块 + 深窄分支测试树。
     *
     * <p>结构：
     * <pre>
     * content (COLUMN, scrollable, clipChildren)
     *   ├─ 引导块（4 行静态文本，MUTED_COLOR）
     *   ├─ branch[0] (COLUMN, 子树含 M 个叶)
     *   ├─ branch[1]
     *   ├─ ...
     *   └─ branch[K-1]
     * 其中 K = getPool().getParallelism()（对齐 worker 数），M = ceil(nodeCount / K)
     * </pre>
     * 每个 branch 内部 M 个叶按 NODES_PER_ROW(10) 再分行排（行节点 hitTestable=false）。
     * branch 子树节点数 ≈ M = nodeCount/K。当 nodeCount=1000、K=8 时 M≈125 &gt; 64 →
     * 每个 branch 都会 fork，K 路并行。当 nodeCount=50 时 M≈7 &lt; 64 → 不 fork（测实验1小树负优化）。</p>
     */
    private void rebuild() {
        // 参数切换时重置本地帧计时，避免 120 帧滚动窗口内新旧数据混合影响测量准确性。
        resetSampling();

        // 叶子编号从 0 重新开始，确保每次 rebuild 后叶子文本 "节点 #0".."节点 #N" 一致
        leafCounter = 0;

        // 清空旧内容（复制 children 列表避免并发修改）
        List<SceneNode> old = new ArrayList<SceneNode>(content.__getChildren());
        for (SceneNode child : old) {
            content.removeChild(child);
        }

        // 1. 引导块（content 顶部，4 行实验说明）
        mountGuideBlock();

        // 2. 深窄分支测试树
        int count = nodeCountSignal.get();
        if (count > 0) {
            // K 对齐 pool 并行度；getPool() 首次调用会建池（预热副作用）
            int k = Math.max(1, SceneParallelExecutor.getPool().getParallelism());
            int m = (count + k - 1) / k;   // ceil(nodeCount / K)
            int remaining = count;
            for (int b = 0; b < k && remaining > 0; b++) {
                int branchLeaves = Math.min(m, remaining);
                remaining -= branchLeaves;
                SceneNode branch = buildBranch(branchLeaves);
                content.appendChild(branch);
            }
        }

        runtime.flush();
    }

    /**
     * 在 content 顶部挂载实验引导块（4 行静态文本，MUTED_COLOR）。
     */
    private void mountGuideBlock() {
        content.appendChild(text(
                "实验1 小树负优化：节点数=50 → 切并行 ON/OFF 各稳定3秒 → 比 fps", MUTED_COLOR));
        content.appendChild(text(
                "实验2 大树收益  ：节点数=1000 → 切并行 ON/OFF 各稳定3秒 → 加速比", MUTED_COLOR));
        content.appendChild(text(
                "实验3 阈值扫描  ：节点数=1000 并行 ON → forkThreshold 扫 32→64→128→256 → 找拐点", MUTED_COLOR));
        content.appendChild(text(
                "每次改参后点[重置采样]，等慢帧数稳定后再读数", MUTED_COLOR));
    }

    /**
     * 构造单个分支容器（一棵子树，含 leafCount 个叶节点按行排列）。
     *
     * <p>branch 子树节点数 = 1(自身) + 行数 + leafCount。当 leafCount &gt; forkThreshold 时，
     * 该 branch 子树会被 fork 到独立 worker。</p>
     *
     * @param leafCount 该分支的叶节点数
     * @return 分支容器节点
     */
    private SceneNode buildBranch(int leafCount) {
        SceneNode branch = new SceneNode();
        branch.setFlexDirection(FlexDirection.COLUMN);
        branch.setGap(CELL_GAP);
        branch.setHitTestable(false);

        SceneNode currentRow = null;
        for (int i = 0; i < leafCount; i++) {
            if (i % NODES_PER_ROW == 0) {
                currentRow = new SceneNode();
                currentRow.setFlexDirection(FlexDirection.ROW);
                currentRow.setGap(CELL_GAP);
                currentRow.setHitTestable(false);
                branch.appendChild(currentRow);
            }
            currentRow.appendChild(buildLeaf());
        }
        return branch;
    }

    /**
     * 构造单个叶节点（带文本，给 layout/paint 制造真实文本度量负载）。
     *
     * <p>叶子高度固定 40，宽度不设 preferredWidth——让 computeWidth 走文本 shrink-to-fit 路径，
     * 调 measurer.measureWidth 量文本宽度（真实 AWT 重负载）。文本水平居中对齐，使 paint 阶段
     * calculateTextLeft 走 CENTER 分支同样调 measureWidth 算居中偏移，layout 与 paint 双路径
     * 均产生测量负载。不同叶子文本字符组合不同，避免 widthCache 全命中（不同字符走 miss 路径
     * 有真实 AWT 测量开销），让并行收益可测、不被 fork/join 调度开销淹没。</p>
     *
     * @return 叶节点
     */
    private SceneNode buildLeaf() {
        SceneNode node = new SceneNode();
        // ★ 不设 preferredWidth：让 computeWidth 走文本 shrink-to-fit 路径，
        // 调 measurer.measureWidth 量文本宽度（真实 AWT 重负载）
        node.setPreferredHeight(NODE_H);
        node.setBackgroundColor(NODE_BG);
        // 加文本：每个叶子不同文本，避免 widthCache 全命中（不同字符走 miss 路径有真实 AWT 测量开销）
        node.setText("节点 #" + (leafCounter++));
        node.setTextColor(TITLE_COLOR);
        // 文本水平居中：让 paint 阶段 calculateTextLeft 走 CENTER 分支，
        // 同样调 measurer.measureWidth 算居中偏移（paint 路径也产生测量负载）
        node.setTextHorizontalAlign(TextHorizontalAlign.CENTER);
        node.setHitTestable(false);
        return node;
    }

    /**
     * 创建静态文字节点。
     *
     * @param value 文本
     * @param color 文本颜色
     * @return 文字节点
     */
    private SceneNode text(String value, int color) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setHitTestable(false);
        return node;
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** @return 节点数 signal */
    Signal<Integer> __getNodeCountSignal() {
        return nodeCountSignal;
    }

    /** @return 预热是否已完成 */
    boolean __isWarmedUp() {
        return warmedUp;
    }
}
