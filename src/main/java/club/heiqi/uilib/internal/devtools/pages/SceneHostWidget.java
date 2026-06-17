package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 新栈 ui.scene 最小宿主 Widget —— 粘合层：同时认识 SceneNode 和 UiRenderContext（合法职责）。
 *
 * <h3>端到端 pipeline（信条五/六/I6）</h3>
 * <pre>
 *  signal.set → runtime.flush() → layout(layoutEngine) → paint(paintEngine)
 *    → Display List(PaintPlan) → replay(replayer, ctx, absX, absY)
 * </pre>
 *
 * <h3>I6 接缝硬约束</h3>
 * <p>SceneNode 只在 flush→layout→paint 三步内流转；replay 只传 plan（PaintCommand 列表）
 * + 两个 int offset；<b>任何 ctx 调用的参数中绝不出现 SceneNode</b>。</p>
 */
public class SceneHostWidget extends Widget {

    private final SceneRuntime runtime;
    private final SceneLayoutEngine layoutEngine;
    private final ScenePaintEngine paintEngine;
    private final ScenePaintReplayer replayer;
    private final SceneNode root;

    /** 背景色 signal（PAINT 级），驱动根节点背景矩形 */
    private final Signal<Integer> bgColorSignal;
    /** 文本内容 signal（LAYOUT 级），驱动子节点 bindText */
    private final Signal<String> labelSignal;

    /** 根节点下的文本子节点引用，供交互逻辑读取 */
    private SceneNode textNode;

    /** I3 平台输入源（允许 null：null 时 pipeline 退化为原有行为） */
    private final PlatformInputSource inputSource;

    /**
     * 创建场景宿主 Widget（无输入源，渲染纯驱动模式）。
     */
    public SceneHostWidget() {
        this(null);
    }

    /**
     * 创建场景宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    public SceneHostWidget(PlatformInputSource inputSource) {
        this.inputSource = inputSource;
        this.runtime = new SceneRuntime();
        this.layoutEngine = new SceneLayoutEngine();
        this.paintEngine = new ScenePaintEngine();
        this.replayer = new ScenePaintReplayer();
        this.root = new SceneNode();
        // root 为容器节点，设置 fillParentHeight 使其背景矩形铺满 host 全高
        root.setFillParentHeight(true);

        // 初始化 signal（I1：signal 是唯一数据驱动力）
        this.bgColorSignal = Signal.create(0xFF333333);  // 深灰
        this.labelSignal = Signal.create("Scene Demo: Hello");

        // 手动构建场景树——不用 mount，因为 root 本身就是场景根（layout/paint 入口），不应作为子节点挂到任何 parent。
        // bind 不依赖 mount：当前无 mount childOwner 作用域时，effect 自动归属 runtime 的 rootOwner，由 dispose() 统一回收。
        runtime.bind(Invalidation.PAINT, bgColorSignal, root::setBackgroundColor);
        SceneNode child = new SceneNode();
        root.appendChild(child);
        runtime.bind(Invalidation.LAYOUT, labelSignal, child::setText);
        this.textNode = child;

        // 首次 flush，确保首帧有初始值
        runtime.flush();
    }

    /**
     * 设置根节点背景色。
     *
     * @param color ARGB 颜色值
     */
    public void setBgColor(int color) {
        bgColorSignal.set(color);
    }

    /**
     * 设置文本内容。
     *
     * @param text 文本
     */
    public void setLabel(String text) {
        labelSignal.set(text);
    }

    /**
     * 获取文本节点引用，供外部诊断。
     *
     * @return 文本子节点
     */
    public SceneNode getTextNode() {
        return textNode;
    }

    /**
     * 获取当前文本内容（从 sceneNode 属性槽读取）。
     *
     * @return 当前文本
     */
    public String getLabel() {
        return textNode.getText();
    }

    /**
     * 获取当前背景色。
     *
     * @return ARGB 颜色值
     */
    public int getBgColor() {
        return root.getBackgroundColor();
    }

    // ==================== Widget 生命周期 ====================

    /**
     * 每帧绘制 —— I3 输入层帧循环时序铁律。
     *
     * <pre>
     * 帧循环时序铁律（set→flush→layout 关系）
     *   Signal.set() 仅 queueWrite，flush 前 get() 返回旧值。
     *   时序：drainFrame → layout① → route(queueWrite) → flush(apply+effect)
     *         → layout②(吸收LAYOUT脏) → paint → replay
     * </pre>
     *
     * <ol>
     *   <li>{@code drainFrame} —— 取本帧输入事件</li>
     *   <li>{@code layout①} —— route hit-test 读当帧最新几何</li>
     *   <li>{@code route} —— 仅 queueWrite（不 flush），不破 7 脏探针</li>
     *   <li>{@code flush} —— 唯一让 queueWrite 生效 + 重跑脏 effect</li>
     *   <li>{@code layout②} —— 吸收 flush 产生的 LAYOUT 级变化</li>
     *   <li>{@code paint + replay} —— 绘制并回放到屏幕</li>
     * </ol>
     *
     * @param ctx 渲染上下文
     */
    @Override
    protected void drawSelf(UiRenderContext ctx) {
        int w = Math.max(0, getWidth());
        int h = Math.max(0, getHeight());

        // ① drainFrame：取本帧输入事件
        SceneInputFrame frame = (inputSource != null) ? inputSource.drainFrame() : SceneInputFrame.EMPTY;

        // ② layout①：route 的 hit-test 读当帧最新几何
        layoutEngine.layout(root, new Constraints(w, h));

        // ③ route：仅 queueWrite 写入 signal，不 flush
        if (!frame.isEmpty()) {
            runtime.route(root, frame, getAbsoluteX(), getAbsoluteY());
        }

        // ④ flush：唯一让 queueWrite 生效，重跑脏 effect、属性槽 setter 打分级脏标记
        runtime.flush();

        // ⑤ layout②：吸收 flush 产生的 LAYOUT 级变化；无 layout 脏时 I7 全跳过近零成本
        layoutEngine.layout(root, new Constraints(w, h));

        // ⑥ paint + replay
        PaintPlan plan = paintEngine.paint(root);
        replayer.replay(plan, ctx, getAbsoluteX(), getAbsoluteY());
    }

    /**
     * 获取 paint 引擎引用（供测试断言 regeneratedFragmentCount）。
     *
     * @return paint 引擎
     */
    public ScenePaintEngine getPaintEngine() {
        return paintEngine;
    }

    /**
     * 获取 layout 引擎引用（供测试断言 relayoutCount）。
     *
     * @return layout 引擎
     */
    public SceneLayoutEngine getLayoutEngine() {
        return layoutEngine;
    }

    /**
     * 回收资源：dispose runtime 以退订所有 effect。
     */
    public void dispose() {
        runtime.dispose();
    }
}
