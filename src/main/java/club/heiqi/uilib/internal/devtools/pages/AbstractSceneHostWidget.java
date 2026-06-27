package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.diagnostic.FrameRateProbe;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
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
        if (inputSource instanceof LwjglInputSource) {
            runtime.bindCursor(new LwjglCursorBackend());
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
        // 基类默认采集帧率：放在 render 入口最外层，确保每帧都采样到。
        // 子类若覆写 render 不调 super，则 tick 不执行——这是子类的责任，基类尽力默认采集。
        frameProbe.tick();
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
        layoutEngine.layout(root, new Constraints(w, h));
        layoutOverlays(w, h);
        // B8：滚动后 hover 重算（在 flush + layout 之后，scrollOffsetY 已生效）。
        // 用帧末粘滞指针坐标重做 hit-test + hover 切换；hover signal 写入走 queueWrite，下一帧 flush 生效。
        if (!frame.isEmpty()) {
            runtime.reconcileHoverAfterScroll(root, frame.getPointerX(), frame.getPointerY(), absX, absY);
        }
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
     * 布局当前 active overlay roots，并清理已移除 overlay 的专用布局引擎。
     *
     * @param w 宿主宽度
     * @param h 宿主高度
     */
    private void layoutOverlays(int w, int h) {
        if (runtime.getOverlayHost().isEmpty()) {
            overlayLayoutEngines.clear();
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
                engine.layout(overlayRoot, new Constraints(triggerBox.getWidth(), Constraints.UNCONSTRAINED));
                LayoutBox firstBox = (LayoutBox) overlayRoot.getCachedLayout();
                int contentHeight = firstBox != null ? firstBox.getHeight() : 0;
                SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveAuto(
                        triggerBox, w, h, contentHeight);
                entry.setAnchorX(resolved.getX());
                entry.setAnchorY(resolved.getY());
                constraints = new Constraints(resolved.getWidth(), resolved.getMaxHeight());
            } else {
                entry.setAnchorX(0);
                entry.setAnchorY(0);
                constraints = new Constraints(w, h);
            }
            activeRoots.put(overlayRoot, Boolean.TRUE);
            engine.layout(overlayRoot, constraints);
        }
        overlayLayoutEngines.entrySet().removeIf(entry -> !activeRoots.containsKey(entry.getKey()));
    }

    /**
     * 请求关闭锚点已被 scrollable 祖先完全裁掉的 overlay。
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
            AnchorRect visibleBox = SceneGeometry.visibleBoxWithinScrollableAncestors(
                    entry.getAnchorProvider().getNode(), 0, 0);
            if (visibleBox.getWidth() <= 0 || visibleBox.getHeight() <= 0) {
                entry.requestDismiss();
            }
        }
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
        if (inputSource instanceof LwjglInputSource) {
            ((LwjglInputSource) inputSource).pushKeyTyped(typedChar, keyCode, System.nanoTime());
        }
    }

    /**
     * 外部文本旁路转发入口。
     *
     * @param text 完整文本内容
     */
    @Override
    public void pushText(String text) {
        if (inputSource instanceof LwjglInputSource) {
            ((LwjglInputSource) inputSource).pushText(text, System.nanoTime());
        }
    }

    /**
     * 切换外部文本模式。
     *
     * @param external true 表示外部文本事件接管输入
     */
    @Override
    public void setExternalTextMode(boolean external) {
        if (inputSource instanceof LwjglInputSource) {
            ((LwjglInputSource) inputSource).setExternalTextMode(external);
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
