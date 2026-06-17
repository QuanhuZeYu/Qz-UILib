package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
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

    /**
     * 创建场景宿主 Widget，构造期完成响应式场景树的挂载。
     */
    public SceneHostWidget() {
        this.runtime = new SceneRuntime();
        this.layoutEngine = new SceneLayoutEngine();
        this.paintEngine = new ScenePaintEngine();
        this.replayer = new ScenePaintReplayer();
        this.root = new SceneNode();

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
     * 每帧绘制 —— 固化 layout→paint 同帧成对契约的兑现点（T5c 遗留⑤）。
     *
     * <ol>
     *   <li>{@code runtime.flush()} —— 响应式帧末批处理（I2/I9）</li>
     *   <li>{@code layoutEngine.layout(root, new Constraints(w))} —— 增量布局</li>
     *   <li>{@code paintEngine.paint(root)} —— 增量绘制产 Display List</li>
     *   <li>{@code replayer.replay(plan, ctx, absX, absY)} —— 回放叠加屏幕 offset</li>
     * </ol>
     *
     * @param ctx 渲染上下文
     */
    @Override
    protected void drawSelf(UiRenderContext ctx) {
        // ① 帧末批处理：应用 signal 写入并重跑脏 effect
        runtime.flush();

        // ② 增量布局
        int w = Math.max(0, getWidth());
        layoutEngine.layout(root, new Constraints(w));

        // ③ 增量绘制
        PaintPlan plan = paintEngine.paint(root);

        // ④ 回放到屏幕（叠加 Widget 的绝对坐标）
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
