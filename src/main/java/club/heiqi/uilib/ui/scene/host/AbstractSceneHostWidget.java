package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.diagnostic.FrameRateProbe;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.UiSurface;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.ClipboardBackendProvider;
import club.heiqi.uilib.ui.scene.input.CursorBackendProvider;
import club.heiqi.uilib.ui.scene.input.KeyboardTextInputSource;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.PointerEventInputSource;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

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

    /** 帧管线序列容器：一帧时序协议的显式载体（阶段 1 序列容器，行为与旧 render 1:1 对拍）。 */
    private final SceneFramePipeline pipeline;

    /**
     * 最近一帧主树最终 layout 的结果（有效探针引用）。
     *
     * <p>render 至少执行 route 前与 flush 后两次 layout。若 flush 挂载了新树，host 会把第二次
     * layout 作为完整 presentation publication 再通知 observer，并在有新 layout 写入时做有界
     * settle；本字段保存当帧最终 LayoutResult。</p>
     */
    protected LayoutResult lastLayoutResult;

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
        this.pipeline = new SceneFramePipeline(runtime, layoutEngine, paintEngine, replayer,
                measurer, inputSource);
        if (inputSource instanceof CursorBackendProvider) {
            runtime.bindCursor(((CursorBackendProvider) inputSource).createCursorBackend());
        }
        if (inputSource instanceof ClipboardBackendProvider) {
            runtime.bindClipboard(((ClipboardBackendProvider) inputSource).createClipboardBackend());
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
        // host 每帧只采一次单调时间，帧率探针与 Motion 共用同一个 timestamp；
        // tick 保留在宿主（子类覆写 render 不调 super 则 tick 不执行——子类责任，基类尽力默认采集）。
        long frameTimeNanos = System.nanoTime();
        frameProbe.tick(frameTimeNanos);
        runtime.__tickFrame(frameTimeNanos);
        w = Math.max(0, w);
        h = Math.max(0, h);
        SceneNode root = getRoot();
        // 一帧 16 步时序协议全部委托帧管线（阶段 1 序列容器，行为与旧 render 1:1 对拍）。
        this.lastLayoutResult = pipeline.run(root, w, h, ctx, absX, absY, frameTimeNanos);
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
        pipeline.doFrameForTest(getRoot(), w, h);
        this.lastLayoutResult = pipeline.getLastLayoutResult();
    }

    /**
     * 获取指定 overlay root 最近一帧最终 layout 的结果（per-overlay 探针引用）。
     *
     * @param overlayRoot overlay 根节点
     * @return 对应 overlay 的最近 layout 结果；未缓存时返回 null
     */
    public LayoutResult getOverlayLayoutResult(SceneNode overlayRoot) {
        return pipeline.getOverlayLayoutResult(overlayRoot);
    }

    /** @return 当前缓存的 overlay 专用布局引擎数量 */
    public int getOverlayLayoutEngineCount() {
        return pipeline.getOverlayLayoutEngineCount();
    }

    /**
     * 获取指定 overlay root 的专用布局引擎。
     *
     * @param root overlay 根节点
     * @return 对应专用布局引擎，未缓存时返回 null
     */
    public SceneLayoutEngine getOverlayLayoutEngine(SceneNode root) {
        return pipeline.getOverlayLayoutEngine(root);
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
