package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.diagnostic.FrameRateProbe;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.CursorBackendProvider;
import club.heiqi.uilib.ui.scene.input.KeyboardTextInputSource;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.PointerEventInputSource;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintResult;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

import java.util.IdentityHashMap;

/**
 * scene demo 宿主统一基类，集中维护输入、布局、路由、刷新、绘制与 overlay 回放管线。
 */
public abstract class AbstractSceneHostWidget extends Widget implements UiSurface {

    /** observer 反向触发布局时的单帧收敛上限；超限后保留下一帧继续，禁止无界自旋。 */
    private static final int MAX_LAYOUT_OBSERVER_SETTLE_PASSES = 3;

    /** 场景运行时，负责 signal 绑定、事件路由与 overlay 宿主。 */
    protected final SceneRuntime runtime;
    /** 主树布局引擎。 */
    protected final SceneLayoutEngine layoutEngine;
    /** 文本度量适配器，主树与 overlay 布局共用同源度量。 */
    protected final SceneTextMeasurer measurer;
    /** Display List 绘制计划生成器。 */
    protected final ScenePaintEngine paintEngine;
    /** Display List 回放器。 */
    protected final ScenePaintReplayer replayer;
    /** 平台输入源，可为 null 表示纯渲染退化模式。 */
    protected final PlatformInputSource inputSource;

    /**
     * 基类默认持有的帧率探针，render 内自动 {@link FrameRateProbe#tick()} 采集，
     * 子类继承即用，无需手动调用；通过 {@link #frameProbe} 的访问器读取 fps/帧耗时统计。
     */
    protected final FrameRateProbe frameProbe = new FrameRateProbe();

    /** overlay root → 专用布局引擎，按 root 身份隔离约束缓存。 */
    private final IdentityHashMap<SceneNode, SceneLayoutEngine> overlayLayoutEngines;

    /**
     * 最近一帧主树最终 layout 的结果（有效探针引用）。
     *
     * <p>render 至少执行 route 前与 flush 后两次 layout。若 flush 挂载了新树，host 会把第二次
     * layout 作为完整 presentation publication 再通知 observer，并在有新 layout 写入时做有界
     * settle；本字段保存当帧最终 LayoutResult。</p>
     */
    protected LayoutResult lastLayoutResult;

    /** overlay root → 最近一帧该 overlay 最终 layout 的结果（per-overlay 探针引用）。 */
    private final IdentityHashMap<SceneNode, LayoutResult> overlayLayoutResults;

    /**
     * 创建 scene demo 宿主基类。
     *
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    protected AbstractSceneHostWidget(PlatformInputSource inputSource) {
        this.inputSource = inputSource;
        this.measurer = new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
        this.runtime = new SceneRuntime(measurer);
        this.layoutEngine = new SceneLayoutEngine(measurer);
        this.paintEngine = new ScenePaintEngine(measurer);
        this.replayer = new ScenePaintReplayer();
        this.overlayLayoutEngines = new IdentityHashMap<SceneNode, SceneLayoutEngine>();
        this.overlayLayoutResults = new IdentityHashMap<SceneNode, LayoutResult>();
        if (inputSource instanceof CursorBackendProvider) {
            runtime.bindCursor(((CursorBackendProvider) inputSource).createCursorBackend());
        }
    }

    /**
     * 获取主树根节点。
     *
     * @return 主树根节点
     */
    protected abstract SceneNode getRoot();

    /**
     * 驱动完整 scene pipeline：主树帧循环 + overlay 布局、绘制和回放。
     *
     * @param w 宿主宽度
     * @param h 宿主高度
     * @param ctx 渲染出口
     * @param absX 宿主绝对 X 偏移
     * @param absY 宿主绝对 Y 偏移
     */
    @Override
    public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
        // host 每帧只采一次单调时间，帧率探针与 Motion 共用同一个 timestamp。
        // 子类若覆写 render 不调 super，则 tick 不执行——这是子类的责任，基类尽力默认采集。
        long frameTimeNanos = System.nanoTime();
        frameProbe.tick(frameTimeNanos);
        w = Math.max(0, w);
        h = Math.max(0, h);
        SceneNode root = getRoot();
        SceneInputFrame frame = inputSource != null ? inputSource.drainFrame() : SceneInputFrame.EMPTY;
        layoutEngine.layout(root, new Constraints(w, h));
        layoutOverlays(w, h);
        if (!frame.isEmpty()) {
            runtime.route(root, frame, absX, absY);
        }
        runtime.flush();
        // route 产生的 signal 已在上方 flush 物化；Motion 采样直接写 paint/composite 属性。
        // completion 创建的新单槽内容由 runtime 在同一帧内物化初始 effect。
        runtime.__sampleMotion(frameTimeNanos);
        this.lastLayoutResult = layoutEngine.layout(root, new Constraints(w, h));
        layoutOverlays(w, h);
        // B3/C4 零滞后路径：post-flush 主树与 overlay 都完成布局后再发布最终 epoch。
        // observer 因而不会读到第一轮旧树或尚未布局的 incoming presentation。
        settleLayoutObservers(root, new Constraints(w, h), w, h);
        // B8：滚动后 hover 重算（在 flush + layout 之后，scrollOffsetY 已生效）。
        // 空事件帧仍携带粘滞指针；平滑滚动跨帧推进 geometry 时也必须消费内部重算请求。
        runtime.reconcileHoverAfterScroll(root, frame.getPointerX(), frame.getPointerY(), absX, absY);
        dismissOverlaysWithInvisibleAnchor();

        // ==================== 契约线：paint 子调用 ====================
        // paint 阶段产出自包含不可变 PaintPlan（Display List），命令坐标为绝对屏幕坐标，
        // 携带渲染所需的全部数据（背景/边框/文本/transform/opacity/clip 边界命令）。
        // PaintPlan 不持有任何上游可变状态引用（SceneNode/Transform/Signal），
        // 是数据层与渲染层之间唯一的合同交付物（守 NORTH_STAR 信条六/I6 并行强化）。
        // paint 过程中只读 signal 值与树结构，绝不写 signal（flush 已在上方完成）。
        PaintResult result = paintEngine.paint(root);
        PaintPlan plan = result.getPlan();

        // ==================== 契约线：replay 子调用 ====================
        // replay 阶段只消费 PaintPlan 与 UiRenderBackend，不得碰任何上游可变状态
        // （SceneNode/Signal/Transform/布局引擎内部状态）。PaintPlan 自包含使 replay 可延迟：
        // 同一 plan 可在任意时机 replay，结果一致（为阶段 2 跨线程并行 replay 铺路）。
        // GL 上下文绑定主线程，replay 永远在主线程执行（阶段 2 worker 生成 Display List，
        // 主线程 replay，绕开 GL 单线程硬墙）。
        replayer.replay(plan, ctx, absX, absY);

        // overlay 子树各自独立 paint + replay，与主树契约同构（per-tree 隔离）
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            PaintResult overlayResult = paintEngine.paint(entry.getRoot());
            replayer.replay(overlayResult.getPlan(), ctx, absX + entry.getAnchorX(), absY + entry.getAnchorY());
        }
    }

    /**
     * 把 flush 后新挂载树的完整 layout 发布给 observer，并收敛 observer 产生的有限布局写入。
     * 每帧至少发布一次最终布局；clean/composite-only 帧不进入额外 relayout。
     */
    private void settleLayoutObservers(SceneNode root, Constraints constraints,
                                       int width, int height) {
        for (int pass = 0; pass < MAX_LAYOUT_OBSERVER_SETTLE_PASSES; pass++) {
            runtime.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
            runtime.flush();
            if (!hasPendingLayoutWork(root)) {
                return;
            }
            this.lastLayoutResult = layoutEngine.layout(root, constraints);
            layoutOverlays(width, height);
        }
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
     */
    private void dismissOverlaysWithInvisibleAnchor() {
        if (runtime.getOverlayHost().isEmpty()) {
            return;
        }
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            if (entry.getAnchorProvider() == null || entry.getAnchorProvider().getNode() == null) {
                continue;
            }
            if (!isAttachedToAnyMountedRoot(entry.getAnchorProvider().getNode())) {
                entry.requestDismiss();
                continue;
            }
            AnchorRect visibleBox = SceneGeometry.visibleBoxWithinScrollableAncestors(
                    entry.getAnchorProvider().getNode(), 0, 0);
            if (visibleBox.getWidth() <= 0 || visibleBox.getHeight() <= 0) {
                entry.requestDismiss();
            }
        }
    }

    /**
     * 判定节点是否仍挂在任一已挂载树的根上（主树 root 或任一 overlay root）。
     *
     * <p>虚拟化列表滚动卸载行时，行内子节点的 parent 链在行节点处截断（行 parent==null），
     * 仅检查直接 parent 会漏判；沿链走到顶后必须确认顶端节点是某个已挂载根。</p>
     */
    private boolean isAttachedToAnyMountedRoot(SceneNode node) {
        SceneNode top = node;
        while (top.__getParent() != null) {
            top = top.__getParent();
        }
        if (top == getRoot()) {
            return true;
        }
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            if (entry.getRoot() == top) {
                return true;
            }
        }
        return false;
    }

    /**
     * 每帧绘制入口，转发到统一 scene render pipeline。
     *
     * @param ctx 渲染上下文
     */
    @Override
    protected void drawSelf(UiRenderContext ctx) {
        render(getWidth(), getHeight(), ctx, getAbsoluteX(), getAbsoluteY());
    }

    /**
     * 宿主键盘事件转发入口。
     *
     * @param typedChar 输入字符
     * @param keyCode 原生键码
     */
    @Override
    public void onKeyTyped(char typedChar, int keyCode) {
        if (inputSource instanceof KeyboardTextInputSource) {
            ((KeyboardTextInputSource) inputSource).pushKeyTyped(typedChar, keyCode, System.nanoTime());
        }
    }

    /**
     * 外部文本旁路转发入口。
     *
     * @param text 完整文本内容
     */
    @Override
    public void pushText(String text) {
        if (inputSource instanceof KeyboardTextInputSource) {
            ((KeyboardTextInputSource) inputSource).pushText(text, System.nanoTime());
        }
    }

    /**
     * 切换外部文本模式。
     *
     * @param external true 表示外部文本事件接管输入
     */
    @Override
    public void setExternalTextMode(boolean external) {
        if (inputSource instanceof KeyboardTextInputSource) {
            ((KeyboardTextInputSource) inputSource).setExternalTextMode(external);
        }
    }

    /**
     * 宿主指针按钮事件转发入口（Bug3 修复）。
     *
     * <p>由 {@code McScreenBridge.mouseClicked/mouseMovedOrUp} 调用，
     * 转发到 {@link PointerEventInputSource}（如 {@code LwjglInputSource}）。
     * 非 {@link PointerEventInputSource} 实现的输入源静默丢弃（与原 {@code instanceof} false 分支等价）。</p>
     *
     * @param action    BUTTON_DOWN 或 BUTTON_UP
     * @param callbackX 宿主回调 X（非权威坐标）
     * @param callbackY 宿主回调 Y（非权威坐标）
     * @param button    鼠标按钮
     * @param timeNanos 事件时间戳（纳秒）
     */
    @Override
    public void onPointerButton(ScenePointerAction action, int callbackX, int callbackY,
                                SceneMouseButton button, long timeNanos) {
        if (inputSource instanceof PointerEventInputSource) {
            ((PointerEventInputSource) inputSource).pushPointerButton(action, callbackX, callbackY,
                    button, timeNanos);
        }
    }

    /**
     * 切换外部指针模式（按钮事件由宿主回调接管，poll 停产 button 边沿）。
     *
     * @param external true 表示按钮事件走宿主回调旁路
     */
    @Override
    public void setExternalPointerMode(boolean external) {
        if (inputSource instanceof PointerEventInputSource) {
            ((PointerEventInputSource) inputSource).setExternalPointerMode(external);
        }
    }

    /**
     * 重置基类帧率探针的采样历史（环形缓冲 + 计数器 + lastFrameNanos）。
     *
     * <p>供子类在切换模式、重置场景或重新进入诊断页时调用，避免 120 帧滚动窗口内
     * 新旧数据混合影响测量准确性。</p>
     */
    protected void resetFrameStats() {
        frameProbe.reset();
    }

    /** 释放 runtime 资源。 */
    @Override
    public void dispose() {
        runtime.dispose();
    }

    /** @return paint 引擎 */
    public ScenePaintEngine getPaintEngine() {
        return paintEngine;
    }

    /** @return layout 引擎 */
    public SceneLayoutEngine getLayoutEngine() {
        return layoutEngine;
    }

    /**
     * @return 主树 layoutDoneSignal（只读），委托 {@link SceneRuntime#layoutDoneSignal()}。
     *         每帧 post-flush 主树与 overlay 布局完成后由 host 桥接最终主树 epoch；
     *         observer 写入最多再收敛三轮（见 {@link SceneRuntime#__bridgeLayoutEpoch}）。
     *         订阅方据此在同帧 flush 内重跑 effect 读最新 LayoutBox（B3/C4 零滞后路径）。
     */
    public ReadableSignal<Integer> layoutDoneSignal() {
        return runtime.layoutDoneSignal();
    }

    /**
     * 获取最近一帧主树最终 layout 的结果（per-call 探针引用）。
     *
     * @return 最近一帧主树 layout 结果；若尚未 render 过返回 null
     */
    public LayoutResult getLastLayoutResult() {
        return lastLayoutResult;
    }

    /**
     * 测试探针：模拟 host render 的 layout publication + 有界 observer settle
     *（不含 route/motion sample/paint/replay）。
     *
     * <p>调用后订阅 layoutDoneSignal 的 observer 可读取 flush 内新挂载子树的完整 LayoutBox，
     * 无需额外等待下一帧。</p>
     *
     * @param w 画布宽
     * @param h 画布高
     */
    public void __doFrameForTest(int w, int h) {
        SceneNode root = getRoot();
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

    /** @return 当前缓存的 overlay 专用布局引擎数量 */
    int __getOverlayLayoutEngineCount() {
        return getOverlayLayoutEngineCount();
    }

    /**
     * 获取指定 overlay root 的专用布局引擎。
     *
     * @param overlayRoot overlay 根节点
     * @return 对应专用布局引擎，未缓存时返回 null
     */
    SceneLayoutEngine __getOverlayLayoutEngine(SceneNode overlayRoot) {
        return getOverlayLayoutEngine(overlayRoot);
    }
}
