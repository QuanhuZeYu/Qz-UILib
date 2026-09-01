package club.heiqi.uilib.ui.scene.host;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintResult;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * 帧管线序列容器 —— scene 宿主一帧时序协议的显式载体（阶段 1：行为对拍）。
 *
 * <p>把原本内联在 {@link AbstractSceneHostWidget#render} 的一帧 16 步顺序协议搬入本类，
 * 拆成 11 个命名阶段（{@link FramePhase}）。阶段 1 只做「序列容器」：步骤顺序与行为
 * 与搬移前逐位一致（含 settle 局部循环、三处 flush、overlay anchor 两遍 layout、
 * {@code __sampleMotion} 内嵌 flush），不迁状态机、不收敛 flush、不动 epoch 桥接——这些
 * 属于阶段 2。</p>
 *
 * <h3>阶段 1 承诺</h3>
 * <ul>
 * <li>每帧入口 {@link #run(SceneNode, int, int, UiRenderBackend, int, int, long)} 与旧
 * {@code render} 方法的执行序列 1:1 对拍，靠 {@link #lastTrace()} 固化顺序契约；</li>
 * <li>每个阶段耗时经 {@link UiPerformanceMonitor#recordPhase} 以 {@code "frame.&lt;PHASE&gt;"}
 * 命名记录（无采样会话时零开销），供诊断页与真机对比；</li>
 * <li>overlay 簿记（per-root 布局引擎与结果缓存）随本类私有搬迁，外部行为不变。</li>
 * </ul>
 *
 * <p>本类不依赖 LWJGL / Minecraft 类型（渲染出口是平台无关的 {@link UiRenderBackend}），
 * 守 scene core 平台无关边界。</p>
 */
public final class SceneFramePipeline {

    /** 一帧内的命名阶段（阶段 1：顺序即契约，不可调换）。 */
    public enum FramePhase {
        /** drainFrame 一次性消费输入帧。 */
        INPUT_DRAIN,
        /** route 前主树布局 + overlay 布局（hit-test 用最新几何）。 */
        LAYOUT_PRE_ROUTE,
        /** 输入路由（空帧跳过）。 */
        ROUTE,
        /** 帧末批量 flush，物化 route 写入。 */
        FLUSH,
        /** Motion 采样（completion 物化由 SETTLE 第一轮 flush 兜底）。 */
        MOTION_SAMPLE,
        /** flush 后主树布局 + overlay 布局（新挂载树成型）。 */
        LAYOUT_POST_FLUSH,
        /** 布局 observer 有界收敛（bridgeEpoch → flush → 探脏 → relayout，最多 3 轮）。 */
        SETTLE,
        /** 滚动后 hover 重算（B8）。 */
        HOVER_RECONCILE,
        /** 锚点不可见 overlay 的受控关闭请求。 */
        DISMISS_INVISIBLE,
        /** paint：只读生成自包含不可变 PaintPlan。 */
        PAINT,
        /** replay：主树 + overlay bottom-first 回放。 */
        REPLAY
    }

    /** 单阶段推进结果（阶段 1 仅 PROCEED；SKIPPED 预留给阶段 2 单步驱动）。 */
    public enum PhaseOutcome {
        /** 阶段正常推进。 */
        PROCEED,
        /** 阶段进入但内部判定跳过（如空帧 route）。 */
        SKIPPED
    }

    /** observer 反向触发布局时的单帧收敛上限；超限后保留下一帧继续，禁止无界自旋。 */
    private static final int MAX_LAYOUT_OBSERVER_SETTLE_PASSES = 3;

    /**
     * 单帧 flush 次数预算上界：route(1) + motion completion 补 flush(1) + settle 每轮(≤3)
     * + dismiss 同帧(1) = 6。超过即证明存在未收拢的 flush 点（阶段 2-5 断言）。
     */
    private static final int MAX_FRAME_FLUSH_BUDGET = 6;

    /** 场景运行时，负责 signal 绑定、事件路由与 overlay 宿主。 */
    private final SceneRuntime runtime;
    /** 主树布局引擎。 */
    private final SceneLayoutEngine layoutEngine;
    /** 文本度量适配器，主树与 overlay 布局共用同源度量。 */
    private final SceneTextMeasurer measurer;
    /** Display List 绘制计划生成器。 */
    private final ScenePaintEngine paintEngine;
    /** Display List 回放器。 */
    private final ScenePaintReplayer replayer;
    /** 平台输入源，可为 null 表示纯渲染退化模式。 */
    private final PlatformInputSource inputSource;

    /** overlay root → 专用布局引擎，按 root 身份隔离约束缓存。 */
    private final IdentityHashMap<SceneNode, SceneLayoutEngine> overlayLayoutEngines =
            new IdentityHashMap<SceneNode, SceneLayoutEngine>();
    /** overlay root → 最近一帧该 overlay 最终 layout 的结果（per-overlay 探针引用）。 */
    private final IdentityHashMap<SceneNode, LayoutResult> overlayLayoutResults =
            new IdentityHashMap<SceneNode, LayoutResult>();

    /** 最近一帧主树最终 layout 的结果（有效探针引用）。 */
    private LayoutResult lastLayoutResult;

    /** 本帧共享状态（每帧 reset 复用，禁止每帧新建）。 */
    private final FrameState state = new FrameState();

    /** settle 跨帧状态：显式化「超限保留下一帧」（阶段 2-1，行为等价重构）。 */
    private final SettleState settleState = new SettleState();

    /** trace 开关（测试/诊断开启；默认关闭零开销）。 */
    private boolean traceEnabled;

    /** 阶段断言开关（测试开、生产默认关——真实超限帧允许 paint 旧布局，见阶段 3 策略化）。 */
    private boolean assertionsEnabled;

    /** 本帧 pipelineFlush 调用计数（flush 预算断言的探针）。 */
    private int frameFlushCount;
    /** 最近一帧实际进入的阶段序列（traceEnabled 时逐帧覆盖）。 */
    private final List<FramePhase> lastTrace = new ArrayList<FramePhase>(FramePhase.values().length);

    /**
     * 创建帧管线。
     *
     * @param runtime     场景运行时（与宿主共享同一实例）
     * @param layoutEngine 主树布局引擎
     * @param paintEngine 绘制计划生成器
     * @param replayer    绘制计划回放器
     * @param measurer    文本度量端口
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    public SceneFramePipeline(SceneRuntime runtime, SceneLayoutEngine layoutEngine,
                              ScenePaintEngine paintEngine, ScenePaintReplayer replayer,
                              SceneTextMeasurer measurer, PlatformInputSource inputSource) {
        if (runtime == null || layoutEngine == null || paintEngine == null
                || replayer == null || measurer == null) {
            throw new IllegalArgumentException("runtime/layoutEngine/paintEngine/replayer/measurer 均不可为 null");
        }
        this.runtime = runtime;
        this.layoutEngine = layoutEngine;
        this.paintEngine = paintEngine;
        this.replayer = replayer;
        this.measurer = measurer;
        this.inputSource = inputSource;
    }

    // ==================== 每帧入口 ====================

    /**
     * 驱动完整 scene pipeline：主树帧循环 + overlay 布局、绘制和回放。
     *
     * <p>执行序列与搬移前的 {@code AbstractSceneHostWidget.render} 逐位一致（阶段 1 对拍承诺）。</p>
     *
     * @param root           主树根节点
     * @param w              宿主宽度
     * @param h              宿主高度
     * @param ctx            渲染出口
     * @param absX           宿主绝对 X 偏移
     * @param absY           宿主绝对 Y 偏移
     * @param frameTimeNanos 本帧单调时间（帧率探针与 Motion 共用）
     * @return 本帧主树最终 LayoutResult（有效探针引用）
     */
    public LayoutResult run(SceneNode root, int w, int h, UiRenderBackend ctx,
                            int absX, int absY, long frameTimeNanos) {
        return run(root, w, h, ctx, absX, absY, frameTimeNanos, null);
    }

    /**
     * 驱动完整 scene pipeline，并可选地把整帧回放包进「窗口裁剪盒」（windowClip 为窗口局部坐标）。
     *
     * <p>屏幕级虚拟窗口（如 HUD）经锚定放置后，内容树布局盒可能宽于放置盒（内容超长时不收缩），
     * 由本参数在 REPLAY 前以放置盒硬裁剪，保证超界内容不画到窗口外。普通 UI 宿主传 null。</p>
     */
    public LayoutResult run(SceneNode root, int w, int h, UiRenderBackend ctx,
                            int absX, int absY, long frameTimeNanos, AnchorRect windowClip) {
        state.reset(root, w, h, ctx, absX, absY, frameTimeNanos, windowClip);
        frameFlushCount = 0;
        if (traceEnabled) {
            lastTrace.clear();
        }
        long t = System.nanoTime();
        phaseInputDrain();
        recordPhase(FramePhase.INPUT_DRAIN, System.nanoTime() - t);
        t = System.nanoTime();
        phaseLayoutPreRoute();
        recordPhase(FramePhase.LAYOUT_PRE_ROUTE, System.nanoTime() - t);
        t = System.nanoTime();
        phaseRoute();
        recordPhase(FramePhase.ROUTE, System.nanoTime() - t);
        t = System.nanoTime();
        phaseFlush();
        recordPhase(FramePhase.FLUSH, System.nanoTime() - t);
        t = System.nanoTime();
        phaseMotionSample();
        recordPhase(FramePhase.MOTION_SAMPLE, System.nanoTime() - t);
        t = System.nanoTime();
        phaseLayoutPostFlush();
        recordPhase(FramePhase.LAYOUT_POST_FLUSH, System.nanoTime() - t);
        t = System.nanoTime();
        phaseSettle();
        recordPhase(FramePhase.SETTLE, System.nanoTime() - t);
        t = System.nanoTime();
        phaseHoverReconcile();
        recordPhase(FramePhase.HOVER_RECONCILE, System.nanoTime() - t);
        t = System.nanoTime();
        phaseDismissInvisible();
        recordPhase(FramePhase.DISMISS_INVISIBLE, System.nanoTime() - t);
        t = System.nanoTime();
        phasePaint();
        recordPhase(FramePhase.PAINT, System.nanoTime() - t);
        t = System.nanoTime();
        phaseReplay();
        recordPhase(FramePhase.REPLAY, System.nanoTime() - t);
        if (assertionsEnabled && frameFlushCount > MAX_FRAME_FLUSH_BUDGET) {
            throw new IllegalStateException(
                    "契约违反：本帧 flush 次数 " + frameFlushCount + " 超过预算 " + MAX_FRAME_FLUSH_BUDGET
                            + "（flush 点未收拢）");
        }
        return lastLayoutResult;
    }

    // ==================== 阶段 1：16 步原样搬移 ====================

    /** INPUT_DRAIN：一次性消费输入帧，空帧返回 EMPTY 单例。 */
    private void phaseInputDrain() {
        trace(FramePhase.INPUT_DRAIN);
        state.frame = inputSource != null ? inputSource.drainFrame() : SceneInputFrame.EMPTY;
    }

    /** LAYOUT_PRE_ROUTE：route 前主树布局 + overlay 布局。 */
    private void phaseLayoutPreRoute() {
        trace(FramePhase.LAYOUT_PRE_ROUTE);
        layoutEngine.layout(state.root, state.constraints());
        layoutOverlays(state.w, state.h);
    }

    /** ROUTE：空帧跳过；否则 hit-test + 派发（handler 只写 signal）。 */
    private void phaseRoute() {
        trace(FramePhase.ROUTE);
        if (!state.frame.isEmpty()) {
            runtime.route(state.root, state.frame, state.absX, state.absY);
        }
    }

    /** FLUSH：物化 route 产生的 signal 写入（管线统一 flush 点之一）。 */
    private void phaseFlush() {
        trace(FramePhase.FLUSH);
        pipelineFlush("frame.route");
    }

    /** MOTION_SAMPLE：Motion 采样直接写 paint/composite 属性。 */
    private void phaseMotionSample() {
        trace(FramePhase.MOTION_SAMPLE);
        // 阶段 3：completion 的新 effect 由 SETTLE 第一轮 flush 兜底物化，本阶段不再收集
        // 补 flush 需求——LAYOUT_POST_FLUSH 恢复纯布局职责（flush 点收拢到 FLUSH/SETTLE/DISMISS）。
        runtime.__sampleMotion(state.frameTimeNanos);
    }

    /** LAYOUT_POST_FLUSH：flush 后主树布局 + overlay 布局（纯布局阶段，无 flush）。 */
    private void phaseLayoutPostFlush() {
        trace(FramePhase.LAYOUT_POST_FLUSH);
        this.lastLayoutResult = layoutEngine.layout(state.root, state.constraints());
        layoutOverlays(state.w, state.h);
    }

    /** SETTLE：布局 observer 有界收敛（见 {@link #settleLayoutObservers}）。 */
    private void phaseSettle() {
        trace(FramePhase.SETTLE);
        // 阶段 2-1：把「上帧超限 → 本帧强制进入」从脏标记隐式延续改为管线显式标志。
        // forced 只记录协议事实，不改变本帧执行路径（行为与搬移前逐位一致）。
        settleState.forced = settleState.deferredToNextFrame;
        settleState.deferredToNextFrame = false;
        settleState.passes = 0;
        settleLayoutObservers(state.root, state.constraints(), state.w, state.h);
    }

    /** HOVER_RECONCILE：滚动后 hover 重算（flush + layout 之后，scrollOffsetY 已生效）。 */
    private void phaseHoverReconcile() {
        trace(FramePhase.HOVER_RECONCILE);
        runtime.reconcileHoverAfterScroll(state.root, state.frame.getPointerX(),
                state.frame.getPointerY(), state.absX, state.absY);
    }

    /** DISMISS_INVISIBLE：只读锚点几何，请求关闭锚点不可见的 overlay；dismiss 同帧物化。 */
    private void phaseDismissInvisible() {
        trace(FramePhase.DISMISS_INVISIBLE);
        if (dismissOverlaysWithInvisibleAnchor()) {
            // 阶段 3：dismiss 请求同帧 flush——portal 的 visible effect 随即摘除 overlay，
            // 本帧 REPLAY 不再绘制它（消除旧实现滞后一帧的关闭）。
            pipelineFlush("frame.dismiss");
        }
    }

    /** PAINT：只读 signal 与树结构，生成自包含不可变 PaintPlan。 */
    private void phasePaint() {
        trace(FramePhase.PAINT);
        if (assertionsEnabled) {
            // 阶段 2-5：把「paint 只读 signal，绝不写」与「flush + settle 后布局收敛」两条注释契约变断言。
            if (ReactiveScheduler.get().__hasPendingWrites()) {
                throw new IllegalStateException(
                        "契约违反：PAINT 前仍有未 flush 的 pendingWrites（paint 阶段只读 signal，绝不写）");
            }
            if (hasPendingLayoutWork(state.root)) {
                throw new IllegalStateException(
                        "契约违反：PAINT 前主树/overlay 仍有未布局脏（settle 未收敛）");
            }
        }
        state.paintResult = paintEngine.paint(state.root);
    }

    /** REPLAY：主树 + overlay bottom-first 各自独立 replay。 */
    private void phaseReplay() {
        trace(FramePhase.REPLAY);
        replayer.replay(replayPlan(state.paintResult.getPlan()), state.ctx, state.absX, state.absY);
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            PaintResult overlayResult = paintEngine.paint(entry.getRoot());
            replayer.replay(overlayResult.getPlan(), state.ctx,
                    state.absX + entry.getAnchorX(), state.absY + entry.getAnchorY());
        }
    }

    // ==================== 搬移自 AbstractSceneHostWidget 的辅助逻辑 ====================

    /**
     * 把 flush 后新挂载树的完整 layout 发布给 observer，并收敛 observer 产生的有限布局写入。
     * 每帧至少发布一次最终布局；clean/composite-only 帧不进入额外 relayout。
     */
    private void settleLayoutObservers(SceneNode root, Constraints constraints,
                                       int width, int height) {
        for (int pass = 0; pass < MAX_LAYOUT_OBSERVER_SETTLE_PASSES; pass++) {
            settleState.passes = pass + 1;
            // 阶段 2-3：epoch → signal 桥接的唯一调用点收进管线（写入所有权归管线）。
            runtime.__setLayoutDoneEpoch(layoutEngine.layoutEpoch());
            pipelineFlush("frame.settle-pass-" + (pass + 1));
            if (!hasPendingLayoutWork(root)) {
                return;
            }
            this.lastLayoutResult = layoutEngine.layout(root, constraints);
            layoutOverlays(width, height);
        }
        // 循环超限退出：显式记录「下帧必跑」——现状靠脏标记存活隐式延续，
        // 本标志使协议可观测，为后续 SETTLE 短路/策略化（阶段 3）铺路。
        settleState.deferredToNextFrame = true;
    }

    /** 屏幕级虚拟窗口：整帧回放前以放置盒硬裁剪（窗口局部坐标），普通宿主原样返回。 */
    private PaintPlan replayPlan(PaintPlan plan) {
        if (state.windowClip == null) {
            return plan;
        }
        PaintPlan wrapped = new PaintPlan().addClipPush(state.windowClip.getX(), state.windowClip.getY(),
                state.windowClip.getX() + state.windowClip.getWidth(),
                state.windowClip.getY() + state.windowClip.getHeight(), 0);
        for (PaintCommand command : plan.getCommands()) wrapped.addCommand(command);
        wrapped.addClipPop();
        return wrapped;
    }

    /** 主树或 active overlay 是否仍有尚未布局的新节点/布局失效。 */
    private boolean hasPendingLayoutWork(SceneNode root) {
        if (hasPendingLayout(root)) {
            return true;
        }
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            if (hasPendingLayout(entry.getRoot())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPendingLayout(SceneNode node) {
        return node != null
                && (node.getCachedLayout() == null
                || node.__isSelfLayoutDirty()
                || node.__isDescendantLayoutDirty());
    }

    /**
     * 布局当前 active overlay roots，并清理已移除 overlay 的专用布局引擎。
     *
     * @param w 宿主宽度
     * @param h 宿主高度
     */
    private void layoutOverlays(int w, int h) {
        if (runtime.getOverlayHost().isEmpty()) {
            overlayLayoutEngines.clear();
            overlayLayoutResults.clear();
            return;
        }
        IdentityHashMap<SceneNode, Boolean> activeRoots = new IdentityHashMap<SceneNode, Boolean>();
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            SceneNode overlayRoot = entry.getRoot();
            Constraints constraints;
            SceneLayoutEngine engine = overlayLayoutEngines.get(overlayRoot);
            if (engine == null) {
                engine = new SceneLayoutEngine(measurer);
                overlayLayoutEngines.put(overlayRoot, engine);
            }
            if (entry.getAnchorProvider() != null) {
                AnchorRect triggerBox = entry.getAnchorProvider().get();
                int targetWidth = SceneAnchorResolver.resolveWidth(triggerBox, w, entry.getAnchoredLayout());
                engine.layout(overlayRoot, new Constraints(targetWidth, Constraints.UNCONSTRAINED));
                LayoutBox firstBox = (LayoutBox) overlayRoot.getCachedLayout();
                int contentHeight = firstBox != null ? firstBox.getHeight() : 0;
                SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveAuto(
                        triggerBox, w, h, contentHeight, entry.getAnchoredLayout());
                entry.setAnchorX(resolved.getX());
                entry.setAnchorY(resolved.getY());
                constraints = new Constraints(resolved.getWidth(), resolved.getMaxHeight());
            } else {
                entry.setAnchorX(0);
                entry.setAnchorY(0);
                constraints = new Constraints(w, h);
            }
            activeRoots.put(overlayRoot, Boolean.TRUE);
            overlayLayoutResults.put(overlayRoot, engine.layout(overlayRoot, constraints));
        }
        overlayLayoutEngines.entrySet().removeIf(entry -> !activeRoots.containsKey(entry.getKey()));
        overlayLayoutResults.entrySet().removeIf(entry -> !activeRoots.containsKey(entry.getKey()));
    }

    /**
     * 请求关闭锚点已不可见的 overlay：锚点被 scrollable 祖先完全裁掉，或锚点所在子树已被卸载
     * （虚拟化列表滚动卸载行后，锚点仍挂着旧 LayoutBox，absoluteBox 会返回陈旧盒，必须按离树判定）。
     *
     * <p>本步骤独立于 overlay 布局：只读锚点几何并通过 {@link SceneOverlayHost.Entry#requestDismiss()}
     * 走受控关闭信号，不在几何探针里写 signal。</p>
     *
     * @return 是否发出了 dismiss 请求（阶段 3：true 时由阶段入口同帧 flush 物化）
     */
    private boolean dismissOverlaysWithInvisibleAnchor() {
        if (runtime.getOverlayHost().isEmpty()) {
            return false;
        }
        boolean dismissed = false;
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            if (entry.getAnchorProvider() == null || entry.getAnchorProvider().getNode() == null) {
                continue;
            }
            if (!isAttachedToAnyMountedRoot(entry.getAnchorProvider().getNode())) {
                entry.requestDismiss();
                dismissed = true;
                continue;
            }
            AnchorRect visibleBox = SceneGeometry.visibleBoxWithinScrollableAncestors(
                    entry.getAnchorProvider().getNode(), 0, 0);
            if (visibleBox.getWidth() <= 0 || visibleBox.getHeight() <= 0) {
                entry.requestDismiss();
                dismissed = true;
            }
        }
        return dismissed;
    }

    /**
     * 判定节点是否仍挂在任一已挂载树的根上（本帧主树 root 或任一 overlay root）。
     *
     * <p>虚拟化列表滚动卸载行时，行内子节点的 parent 链在行节点处截断（行 parent==null），
     * 仅检查直接 parent 会漏判；沿链走到顶后必须确认顶端节点是某个已挂载根。</p>
     */
    private boolean isAttachedToAnyMountedRoot(SceneNode node) {
        SceneNode top = node;
        while (top.__getParent() != null) {
            top = top.__getParent();
        }
        if (top == state.root) {
            return true;
        }
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            if (entry.getRoot() == top) {
                return true;
            }
        }
        return false;
    }

    // ==================== 测试探针与诊断 ====================

    /**
     * 空窗 settle：flush + layout + 有界 observer 收敛，不 paint 不 replay。
     *
     * <p>屏幕级虚拟窗口宿主（HUD）对内容空尺寸的窗口调用本入口——signal 物化照常推进
     *（窗口一旦有内容下一帧即恢复绘制），仅跳过绘制半边。破「空窗跳帧 → effect 永不
     * 再物化 → 树恒空」的自锁，使投放方不再需要在宿主 render 栈外强制 flush。</p>
     *
     * @param root 窗口内容根节点
     * @param w    视口宽（空窗布局收敛用）
     * @param h    视口高
     */
    public void settleWithoutPaint(SceneNode root, int w, int h) {
        Constraints constraints = new Constraints(Math.max(0, w), Math.max(0, h));
        layoutEngine.layout(root, constraints);
        layoutOverlays(Math.max(0, w), Math.max(0, h));
        pipelineFlush("frame.empty-settle");
        this.lastLayoutResult = layoutEngine.layout(root, constraints);
        layoutOverlays(Math.max(0, w), Math.max(0, h));
        settleLayoutObservers(root, constraints, Math.max(0, w), Math.max(0, h));
    }

    /**
     * 测试探针：模拟 host render 的 layout publication + 有界 observer settle
     *（不含 route/motion sample/paint/replay）。
     *
     * <p>调用后订阅 layoutDoneSignal 的 observer 可读取 flush 内新挂载子树的完整 LayoutBox，
     * 无需额外等待下一帧。</p>
     *
     * @param root 主树根节点
     * @param w    画布宽
     * @param h    画布高
     */
    public void doFrameForTest(SceneNode root, int w, int h) {
        w = Math.max(0, w);
        h = Math.max(0, h);
        Constraints constraints = new Constraints(w, h);
        layoutEngine.layout(root, constraints);
        layoutOverlays(w, h);
        runtime.flush();
        this.lastLayoutResult = layoutEngine.layout(root, constraints);
        layoutOverlays(w, h);
        settleLayoutObservers(root, constraints, w, h);
    }

    /** @return 最近一帧主树最终 layout 的结果；若尚未 render 过返回 null */
    public LayoutResult getLastLayoutResult() {
        return lastLayoutResult;
    }

    /**
     * 获取指定 overlay root 最近一帧最终 layout 的结果（per-overlay 探针引用）。
     *
     * @param overlayRoot overlay 根节点
     * @return 对应 overlay 的最近 layout 结果；未缓存时返回 null
     */
    public LayoutResult getOverlayLayoutResult(SceneNode overlayRoot) {
        return overlayLayoutResults.get(overlayRoot);
    }

    /** @return 当前缓存的 overlay 专用布局引擎数量 */
    public int getOverlayLayoutEngineCount() {
        return overlayLayoutEngines.size();
    }

    /**
     * 获取指定 overlay root 的专用布局引擎。
     *
     * @param root overlay 根节点
     * @return 对应专用布局引擎，未缓存时返回 null
     */
    public SceneLayoutEngine getOverlayLayoutEngine(SceneNode root) {
        return overlayLayoutEngines.get(root);
    }

    /**
     * 开启/关闭阶段 trace（测试与诊断用；关闭时零记录零分配）。
     *
     * @param enabled 是否记录每帧进入的阶段序列
     */
    public void setTraceEnabled(boolean enabled) {
        this.traceEnabled = enabled;
        if (!enabled) {
            lastTrace.clear();
        }
    }

    /** @return trace 是否开启 */
    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    /**
     * @return 最近一帧实际进入的阶段序列（trace 开启时有效；逐帧覆盖）
     */
    public List<FramePhase> lastTrace() {
        return lastTrace;
    }

    /** @return 本帧 settle 是否由上一帧超限强制进入（协议探针，阶段 2-1） */
    public boolean __isSettleForced() {
        return settleState.forced;
    }

    /** @return 本帧 settle 已执行的收敛轮数（bridge+flush+探脏为一轮；协议探针） */
    public int __settlePasses() {
        return settleState.passes;
    }

    /** @return 本帧 settle 是否超限（下一帧将强制进入 SETTLE；协议探针） */
    public boolean __isSettleDeferred() {
        return settleState.deferredToNextFrame;
    }

    /**
     * 开启/关闭阶段断言（测试开、生产默认关）。
     *
     * <p>开启后 PAINT 前置断言（无 pendingWrites / 无布局脏）与帧末 flush 预算断言生效；
     * 生产默认关闭：真实超限帧允许 paint 旧布局（阶段 3 策略化前保持现状语义）。</p>
     *
     * @param enabled 是否校验阶段契约
     */
    public void __setAssertionsEnabled(boolean enabled) {
        this.assertionsEnabled = enabled;
    }

    /** @return 本帧 pipelineFlush 调用次数（flush 预算探针，阶段 2-5） */
    public int __frameFlushCount() {
        return frameFlushCount;
    }

    // ==================== 内部 ====================

    /**
     * 管线统一 flush 点（阶段 2-2）：所有帧内 flush 经此收口，并联动中央事务审计标签。
     *
     * <p>标签一次性：下一次 flush 提交的事务携带本标签（TransactionLog 审计「因何而改」）。
     * 注意 {@link ReactiveScheduler#flush()} 的可重入保护——嵌套调用会静默跳过，管线连续调用
     * 不得依赖其生效。</p>
     *
     * @param label 审计标签，如 {@code "frame.route"}
     */
    private void pipelineFlush(String label) {
        frameFlushCount++;
        ReactiveScheduler.get().labelNextTransaction(label);
        runtime.flush();
    }

    /** 记录阶段进入（trace 开启时）。 */
    private void trace(FramePhase phase) {
        if (traceEnabled) {
            lastTrace.add(phase);
        }
    }

    /** 记录阶段耗时到性能监控（无采样会话时零开销）。 */
    private void recordPhase(FramePhase phase, long nanos) {
        if (nanos > 0L) {
            UiPerformanceMonitor.getInstance().recordPhase("frame." + phase.name(), nanos);
        }
    }

    /** settle 跨帧协议状态（阶段 2-1：显式化，行为等价）。 */
    private static final class SettleState {

        /** 本帧 settle 是否由上一帧超限强制进入（只记录协议事实，不改变执行路径）。 */
        boolean forced;

        /** 本帧已执行的 settle 收敛轮数。 */
        int passes;

        /** 本帧 settle 超限：下一帧必须强制进入 SETTLE（即使脏标记已被清）。 */
        boolean deferredToNextFrame;
    }

    /** 本帧共享状态（每帧 reset 复用，禁止每帧新建）。 */
    private static final class FrameState {

        SceneNode root;
        int w;
        int h;
        int absX;
        int absY;
        UiRenderBackend ctx;
        long frameTimeNanos;
        SceneInputFrame frame = SceneInputFrame.EMPTY;
        PaintResult paintResult;
        AnchorRect windowClip;

        void reset(SceneNode root, int w, int h, UiRenderBackend ctx,
                   int absX, int absY, long frameTimeNanos, AnchorRect windowClip) {
            this.root = root;
            this.w = w;
            this.h = h;
            this.ctx = ctx;
            this.absX = absX;
            this.absY = absY;
            this.frameTimeNanos = frameTimeNanos;
            this.frame = SceneInputFrame.EMPTY;
            this.paintResult = null;
            this.windowClip = windowClip;
        }

        Constraints constraints() {
            return new Constraints(w, h);
        }
    }
}
