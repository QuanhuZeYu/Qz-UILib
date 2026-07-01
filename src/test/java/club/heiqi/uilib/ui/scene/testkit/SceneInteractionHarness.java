package club.heiqi.uilib.ui.scene.testkit;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 场景交互注入 harness —— 输入侧「编程注入帧」入口的语义化封装。
 *
 * <p>把现有测试里反复出现的「取节点中心坐标 → new InputFrameBuilder → push RawInputEvent →
 * drainFrame → runtime.route → flush」范式收口为 {@code click(node)} / {@code moveTo(node)} /
 * {@code scroll(node, delta)} / {@code pressKey(key)} 等语义化方法，消除坐标硬编码与
 * 11 参 {@link RawInputEvent#ofPointer} 易错的传参样板。</p>
 *
 * <h3>定位（输入入口 A）</h3>
 * <p>本类属 testkit 跨包搭台设施，对应「输入侧入口 A 编程注入帧」——直接构造
 * {@link InputFrameBuilder} 帧推入 {@link SceneRuntime#route}，覆盖交互路由 + 状态机。
 * <b>不经 {@code MockPlatformInputSource}</b>（那是桥封板契约测试入口 B，≡ InputFrameBuilder
 * 壳，交互测试不必经它；详见 docs/记忆/长期事实/测试体系约定.md §7）。</p>
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>{@link #create()} 构造内置 {@link FixedTextMeasurer}（charWidth=8, lineHeight=16）的
 *       {@link SceneRuntime} + {@link SceneLayoutEngine}，与 scene 测试沙箱范式一致。</li>
 *   <li>{@link #mountRoot(SceneNode, int, int)} 记住路由根并对齐 layout（让
 *       {@link SceneGeometry#absoluteBox} 能取到非零盒，是后续 click/moveTo 取中心的必要前提）。</li>
 *   <li>语义方法内部完成 route + flush，调用方一行调用即可断言。</li>
 *   <li>用毕调 {@link #dispose()} 释放 runtime（测试 @After 调用）。</li>
 * </ul>
 *
 * <h3>click 合成语义</h3>
 * <p> {@link ScenePointerAction} 无 CLICK 原语——{@code click(node)} 照搬现有
 * {@code SceneButtonTest} 试金石 7 范式：{@code BUTTON_DOWN} 一帧 + {@code BUTTON_UP} 一帧
 * 分别 route，由 {@code SceneInputRouter} 在同节点完成 DOWN+UP 时自动合成 CLICK 事件派发。
 * 测试侧不构造 CLICK 事件本身。</p>
 *
 * @see SceneRuntime#route
 * @see InputFrameBuilder
 * @see RawInputEvent
 */
public final class SceneInteractionHarness {

    /** 路由根（mountRoot 后记住，供 route 使用） */
    private SceneNode root;
    /** 运行时（声明式 bind/on/route/flush 入口） */
    private final SceneRuntime runtime;
    /** 布局引擎（mountRoot 时 layout 让 box 就位） */
    private final SceneLayoutEngine layoutEngine;

    private SceneInteractionHarness(SceneRuntime runtime, SceneLayoutEngine layoutEngine) {
        this.runtime = runtime;
        this.layoutEngine = layoutEngine;
    }

    /**
     * 工厂：构造内置 {@link FixedTextMeasurer}（8,16）的 {@link SceneRuntime} + {@link SceneLayoutEngine}。
     *
     * @return 新 harness 实例
     */
    public static SceneInteractionHarness create() {
        SceneRuntime runtime = new SceneRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer();
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        return new SceneInteractionHarness(runtime, layoutEngine);
    }

    /** @return 内部运行时（供测试注册 on/bind/focusable 等） */
    public SceneRuntime getRuntime() {
        return runtime;
    }

    /**
     * 挂载路由根并对齐 layout。
     *
     * <p>记住 root 供后续 route 使用，并立即 layout 一帧让所有节点 cachedLayout 就位——
     * 这是 {@link #centerOf} 取非零盒、route hit-test 命中的必要前提。</p>
     *
     * @param root           场景树根节点
     * @param viewportWidth  视口宽（像素）
     * @param viewportHeight 视口高（像素）
     */
    public void mountRoot(SceneNode root, int viewportWidth, int viewportHeight) {
        this.root = root;
        layoutEngine.layout(root, new Constraints(viewportWidth, viewportHeight));
    }

    /**
     * 语义化点击：在节点中心 DOWN + UP 两帧 route，Router 自动合成 CLICK。route 后 flush。
     *
     * @param node 目标节点（须已 mountRoot 且 layout）
     */
    public void click(SceneNode node) {
        int[] c = centerOf(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1]);
        routePointer(ScenePointerAction.BUTTON_UP, c[0], c[1]);
        flush();
    }

    /**
     * 语义化移动：MOVE 到节点中心一帧 route + flush，触发 hover 进入。
     *
     * <p>断言 hovered 前，调用方须先 {@code runtime.interactionState(node).hovered()} 声明关心
     * （{@link club.heiqi.uilib.ui.scene.input.SceneInteractionState#hovered} 懒创建时序契约）。</p>
     *
     * @param node 目标节点
     */
    public void moveTo(SceneNode node) {
        int[] c = centerOf(node);
        routePointer(ScenePointerAction.MOVE, c[0], c[1]);
        flush();
    }

    /**
     * 语义化滚动：在节点中心 SCROLL 一帧 route + flush。
     *
     * <p>方向语义与 {@code SceneScrollViewportTest} 一致：{@code wheelDelta < 0} 向下滚
     * （内容上移 / scrollOffsetY 增大），{@code wheelDelta > 0} 向上滚。</p>
     *
     * @param node       目标节点（通常是 scrollable 视口）
     * @param wheelDelta 滚轮增量（负=向下滚，正=向上滚）
     */
    public void scroll(SceneNode node, int wheelDelta) {
        int[] c = centerOf(node);
        InputFrameBuilder fb = new InputFrameBuilder(c[0], c[1]);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, c[0], c[1],
                SceneMouseButton.NONE, wheelDelta, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(root, frame, 0, 0);
        flush();
    }

    /**
     * 按键（PRESSED）：KEY 一帧 route + flush。
     *
     * <p>键盘事件需要焦点目标，调用方须先 {@link SceneRuntime#requestFocus} 聚焦目标节点。</p>
     *
     * @param key 平台无关按键
     */
    public void pressKey(SceneKey key) {
        ensureMounted();
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, 0, 0, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(root, frame, 0, 0);
        flush();
    }

    /**
     * 坐标型点击（少数边界场景用，绕过节点中心计算）。
     *
     * @param x 逻辑 X
     * @param y 逻辑 Y
     */
    public void clickAt(int x, int y) {
        routePointer(ScenePointerAction.BUTTON_DOWN, x, y);
        routePointer(ScenePointerAction.BUTTON_UP, x, y);
        flush();
    }

    /** flush 一帧让本批次 route 产生的 signal 写入 / effect 落盘生效。 */
    public void flush() {
        runtime.flush();
    }

    /** 释放运行时（测试 @After 调用）。 */
    public void dispose() {
        runtime.dispose();
    }

    // ==================== 内部 ====================

    /**
     * 取节点 {@link SceneGeometry#absoluteBox} 中心（rootAbs=0,0）。
     *
     * @param node 目标节点
     * @return {@code int[]{centerX, centerY}}
     */
    private int[] centerOf(SceneNode node) {
        ensureMounted();
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            throw new IllegalStateException(
                    "节点未 layout 或零尺寸，无法取中心: " + box + "（节点=" + node + "）");
        }
        return new int[]{box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2};
    }

    /** 构造单指针事件帧并 route 到 root（LEFT 按钮，无修饰键，button 命中范式）。 */
    private void routePointer(ScenePointerAction action, int x, int y) {
        ensureMounted();
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(root, frame, 0, 0);
    }

    /** 确保已 mountRoot，否则 route 无目标根。 */
    private void ensureMounted() {
        if (root == null) {
            throw new IllegalStateException("未 mountRoot，请先调用 mountRoot(root, w, h)");
        }
    }
}
