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
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

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
 * 壳，交互测试不必经它；详见 docs/传感层/测试体系约定.md §7）。</p>
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
     * <p>委托给 {@link #create(SceneTextMeasurer)}，等价于 {@code create(new FixedTextMeasurer())}。</p>
     *
     * @return 新 harness 实例
     */
    public static SceneInteractionHarness create() {
        return create(new FixedTextMeasurer());
    }

    /**
     * 工厂：用外部传入的 {@link SceneTextMeasurer} 构造 {@link SceneRuntime} + {@link SceneLayoutEngine}。
     *
     * <p>用于控件构建期需要调 {@link SceneRuntime#measureTextWidth} 的场景——
     * {@link SceneRuntime#SceneRuntime()} 无参构造挂 null measurer，控件构建期调度量会抛异常，
     * 此重载让调用方注入真实/桩 measurer 避免该问题。</p>
     *
     * @param measurer 文本度量窄端口（不可为 null）
     * @return 新 harness 实例
     */
    public static SceneInteractionHarness create(SceneTextMeasurer measurer) {
        SceneRuntime runtime = new SceneRuntime(measurer);
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
     * 分帧按下（节点版）：在节点中心 BUTTON_DOWN 一帧 route + flush。
     *
     * <p>与 {@link #release(SceneNode)} 配对使用，跨 flush 由 Router 在同节点 DOWN+UP 时合成 CLICK。
     * 用于需要观察「按下后、释放前」中间态（如 pressed signal）的用例。</p>
     *
     * @param node 目标节点
     */
    public void press(SceneNode node) {
        int[] c = centerOf(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1]);
        flush();
    }

    /**
     * 分帧释放（节点版）：在节点中心 BUTTON_UP 一帧 route + flush。
     *
     * <p>与 {@link #press(SceneNode)} 配对，UP 帧触发 Router 合成 CLICK 派发到 on(node, CLICK)。</p>
     *
     * @param node 目标节点
     */
    public void release(SceneNode node) {
        int[] c = centerOf(node);
        routePointer(ScenePointerAction.BUTTON_UP, c[0], c[1]);
        flush();
    }

    /**
     * 跨帧按下-释放：DOWN 一帧 + 调用方回调（模拟生产每帧的 layout / overlay 重排）+ UP 一帧。
     *
     * <h3>用途</h3>
     * <p>守真因 D1 类回归——真机 DOWN 一帧 / UP 另一帧时，DOWN 后 flush 已触发 signal 写入与
     * overlay 重评（可能卸载 item），UP 帧到达时 hitTarget 是否仍存活、CLICK 是否仍合成。
     * 与 {@link #click(SceneNode)}（DOWN+UP 同帧 route，flush 之前 CLICK 已合成）互补，
     * 覆盖真机跨帧盲区。</p>
     *
     * <h3>坐标捕获时点（关键）</h3>
     * <p>DOWN 前用 {@link #centerOf(SceneNode)} 捕获一次，UP 复用同一坐标——
     * <b>不</b>在 betweenFrames 之后重取。理由：若 betweenFrames 卸载了 item 节点，
     * UP 时 {@link #centerOf} 会因 {@link SceneGeometry#absoluteBox} 返回零盒而抛
     * IllegalStateException（见 {@code centerOf} 内的零盒保护）；cross-frame 的语义本就是
     * 「UP 用 DOWN 那一帧的目标坐标」，与生产 UP 帧的指针位置不因节点卸载而漂移一致。</p>
     *
     * <h3>几何近似声明（守 YAGNI）</h3>
     * <p>harness <b>不</b>承担 overlay 锚点解析，{@link #centerOf} 取 {@link SceneGeometry#absoluteBox}
     * 沿 {@code __getParent()} 链累加到最顶祖先（overlay item 走到 overlay root 即停），得到
     * <b>相对 overlay root 的局部坐标</b>。这与 Router 在 {@code anchor=0}（默认 unset）时
     * {@code raw==local} 自洽（守 NORTH_STAR I12）的命中语义相符，故可用于：
     * <ul>
     *   <li>focus 跨帧不掐断时序回归</li>
     *   <li>CLICK 合成是否存活的守卫回归</li>
     * </ul>
     * <b>不可</b>用于：锚点定位精度测试（{@code anchor!=0} 或锚点探针场景需调用方自取几何）。
     * overlay 实际 layout 由 {@code betweenFrames} 回调在测试侧完成——harness 不复刻一份
     * 偏离生产 anchor 语义的第二 layout 实现（守 P1 漂移红线 + YAGNI，跨帧点击当前唯一消费者
     * 是 autocomplete，无需膨胀测试基建）。</p>
     *
     * <h3>betweenFrames 语义</h3>
     * <p>模拟生产 {@code AbstractSceneHostWidget.render} 的「route → flush → layout（含 overlay 重排）」
     * 时序：DOWN 帧 route+flush 后调用 {@code betweenFrames} 让测试侧 doLayout（含 overlay 重排、
     * 可能的卸载），再 UP 帧 route+flush。传 {@code null} 退化为紧邻两帧 DOWN/UP（无中间 layout），
     * 语义等价于 {@link #press(SceneNode)} + {@link #release(SceneNode)} 但用一个坐标缓存。</p>
     *
     * <p><b>不在本方法内写 signal、不命令式挂卸 overlay、不碰节点结构</b>（守 I1/I7/I11/I12、R11/R13）——
     * 纯 route+flush 注入路径，overlay 卸载由调用方在 {@code betweenFrames} 里触发生产语义（写 expanded
     * signal 等）。</p>
     *
     * @param node           目标节点（须已由调用方 layout，使 {@link #centerOf} 取到非零盒）
     * @param betweenFrames  DOWN flush 与 UP route 之间的回调；可为 null（紧邻两帧）
     */
    public void pressReleaseAcrossFrames(SceneNode node, Runnable betweenFrames) {
        int[] c = centerOf(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1]);
        flush();
        if (betweenFrames != null) {
            betweenFrames.run();
        }
        routePointer(ScenePointerAction.BUTTON_UP, c[0], c[1]);
        flush();
    }

    /**
     * 分帧按下（坐标版）：在 (x,y) BUTTON_DOWN 一帧 route + flush。
     *
     * @param x 逻辑 X
     * @param y 逻辑 Y
     */
    public void pressAt(int x, int y) {
        routePointer(ScenePointerAction.BUTTON_DOWN, x, y);
        flush();
    }

    /**
     * 分帧释放（坐标版）：在 (x,y) BUTTON_UP 一帧 route + flush。
     *
     * @param x 逻辑 X
     * @param y 逻辑 Y
     */
    public void releaseAt(int x, int y) {
        routePointer(ScenePointerAction.BUTTON_UP, x, y);
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
     * 坐标型移动：MOVE 到 (x,y) 一帧 route + flush。
     *
     * <p>命名采用 {@code At} 后缀与 {@link #pressAt} / {@link #releaseAt} / {@link #clickAt} 一致。
     * 常用于 hover-out 场景：先 {@link #moveTo(SceneNode)} 进入 hover，再 moveAt 到节点外坐标触发 hover 退出。</p>
     *
     * @param x 逻辑 X
     * @param y 逻辑 Y
     */
    public void moveAt(int x, int y) {
        routePointer(ScenePointerAction.MOVE, x, y);
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
     * 注入文本输入帧（ofText 一帧 + flush）到当前焦点节点。
     *
     * <p>调用方须先 {@link #click(SceneNode)} 选中输入框或
     * {@link SceneRuntime#requestFocus} 建立焦点，否则文本无目标节点接收。</p>
     *
     * @param text 待注入文本
     */
    public void typeText(String text) {
        ensureMounted();
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
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
