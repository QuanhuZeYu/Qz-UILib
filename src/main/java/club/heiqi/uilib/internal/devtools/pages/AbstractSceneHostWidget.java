package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
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
        this.paintEngine = new ScenePaintEngine();
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
        PaintPlan plan = paintEngine.paint(root);
        replayer.replay(plan, ctx, absX, absY);
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            PaintPlan overlayPlan = paintEngine.paint(entry.getRoot());
            replayer.replay(overlayPlan, ctx, absX + entry.getAnchorX(), absY + entry.getAnchorY());
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
            if (entry.getAnchorProvider() != null) {
                SceneAnchorResolver.AnchorRect triggerBox = entry.getAnchorProvider().get();
                SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveDown(triggerBox, w, h);
                entry.setAnchorX(resolved.getX());
                entry.setAnchorY(resolved.getY());
                constraints = new Constraints(resolved.getWidth(), resolved.getMaxHeight());
            } else {
                entry.setAnchorX(0);
                entry.setAnchorY(0);
                constraints = new Constraints(w, h);
            }
            activeRoots.put(overlayRoot, Boolean.TRUE);
            SceneLayoutEngine engine = overlayLayoutEngines.get(overlayRoot);
            if (engine == null) {
                engine = new SceneLayoutEngine(measurer);
                overlayLayoutEngines.put(overlayRoot, engine);
            }
            engine.layout(overlayRoot, constraints);
        }
        overlayLayoutEngines.entrySet().removeIf(entry -> !activeRoots.containsKey(entry.getKey()));
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
