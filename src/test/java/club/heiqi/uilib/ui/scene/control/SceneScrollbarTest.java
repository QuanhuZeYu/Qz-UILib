package club.heiqi.uilib.ui.scene.control;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * SceneScrollbar 单元测试 —— 验证派生几何算法、失效级别（I4 双轨核对 / COMPOSITE 级零重排）、
 * B1 无溢出隐藏、B2 拖动 + track page、B3 resize 更新、C5 首帧零高、中性灰三态颜色。
 *
 * <p>测试用 {@code runtime.__bridgeLayoutEpoch(layoutEngine.layoutEpoch()) + runtime.flush()} 模拟
 * host 的 layoutDoneSignal 桥接：doFrame 顺序：layout（产出 LayoutBox）→ 桥接 epoch → flush
 * （驱动 effect 读最新 LayoutBox）→ layout（清掉 effect 写入的 selfLayoutDirty）。</p>
 */
public class SceneScrollbarTest {

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int BAR_WIDTH = 4;
    private static final int MIN_THUMB = 20;

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer());
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer());
        sceneRoot = new SceneNode();
        sceneRoot.setFillParentHeight(true); // 根填满 canvas 高，使 column 有 free space grow
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 滚动树 + scrollbar 构建产物。 */
    private static final class ScrollSetup {
        final SceneNode viewport;
        final Signal<Integer> scrollSignal;
        final SceneScrollbar.Result scrollbar;

        ScrollSetup(SceneNode viewport, Signal<Integer> scrollSignal, SceneScrollbar.Result scrollbar) {
            this.viewport = viewport;
            this.scrollSignal = scrollSignal;
            this.scrollbar = scrollbar;
        }
    }

    /**
     * 构建指定视口高和内容高的滚动树 + scrollbar，返回含 scrollSignal 引用的产物。
     *
     * @param viewportHeight 视口高度
     * @param contentHeight 内容高度
     * @return 滚动树构建产物
     */
    private ScrollSetup build(int viewportHeight, int contentHeight) {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(viewportHeight);
        sceneRoot.appendChild(viewport);

        SceneNode content = new SceneNode();
        content.setPreferredHeight(contentHeight);
        viewport.appendChild(content);

        Signal<Integer> scrollSignal = SceneScrolls.attach(runtime, viewport);
        SceneScrollbar.Props props = new SceneScrollbar.Props(
                viewport, scrollSignal, scrollSignal::set,
                SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                BAR_WIDTH, MIN_THUMB);
        SceneScrollbar.Result sb = SceneScrollbar.create(runtime, props);
        sceneRoot.appendChild(sb.column());
        return new ScrollSetup(viewport, scrollSignal, sb);
    }

    /**
     * 执行 layout + 桥接 layoutEpoch + flush + layout（模拟宿主帧循环 +
     * layoutDoneSignal 桥接：layout 产出 LayoutBox → 桥接 epoch 驱动 scrollbar effect 重跑
     * 读最新 LayoutBox → layout 清掉 effect 写入的脏标记）。
     */
    private void doFrame() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        // 桥接 layoutDoneSignal：bump epoch 驱动 scrollbar effect 重跑读最新 LayoutBox
        runtime.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        runtime.flush();
        // 末尾 layout：清掉 effect 写入的 selfLayoutDirty，使树回到干净态
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * 用指定约束执行 doFrame（用于 B3 resize 测试）。
     */
    private void doFrame(int w, int h) {
        layoutEngine.layout(sceneRoot, new Constraints(w, h));
        runtime.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(w, h));
    }

    /**
     * 在指定绝对坐标投递单指针事件并 route 到 sceneRoot（rootAbs=0,0）。
     */
    private void routePointer(ScenePointerAction action, int x, int y) {
        routePointer(action, x, y, 0, 0);
    }

    /**
     * 在指定绝对坐标投递单指针事件并 route 到 sceneRoot，可指定 rootAbsX/Y（模拟 GUI 居中 margin）。
     *
     * @param action   指针动作
     * @param x        指针画布逻辑 X
     * @param y        指针画布逻辑 Y
     * @param rootAbsX 根节点屏幕绝对 X 偏移
     * @param rootAbsY 根节点屏幕绝对 Y 偏移
     */
    private void routePointer(ScenePointerAction action, int x, int y, int rootAbsX, int rootAbsY) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, rootAbsX, rootAbsY);
    }

    /**
     * 获取节点绝对盒中心 X。
     */
    private int centerX(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getX() + box.getWidth() / 2;
    }

    /**
     * 获取节点绝对盒中心 Y。
     */
    private int centerY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY() + box.getHeight() / 2;
    }

    /**
     * 获取 thumb 视觉中心 Y（布局中心 + transform 平移）。
     * hit-test 用布局位置（不含 transform），故 scroll > 0 时 thumb 视觉中心 ≠ 布局中心，
     * 点击视觉中心命中 column 而非 thumb（BUG1 根因）。
     */
    private float thumbVisualCenterY(SceneNode thumb) {
        AnchorRect box = SceneGeometry.absoluteBox(thumb, 0, 0);
        float transformY = thumb.getTransform().translateY;
        return box.getY() + transformY + box.getHeight() / 2f;
    }

    // ==================== 验收 1：结构 ====================

    @Test
    public void createShouldReturnColumnAndThumb() {
        ScrollSetup setup = build(200, 600);
        SceneScrollbar.Result sb = setup.scrollbar;
        Assert.assertNotNull("column 非空", sb.column());
        Assert.assertNotNull("thumb 非空", sb.thumb());
        Assert.assertEquals("column 含 thumb", 1, sb.column().__getChildren().size());
        Assert.assertSame("column 第一个子是 thumb", sb.thumb(), sb.column().__getChildren().get(0));
    }

    @Test
    public void columnShouldHaveBarWidthAndFillHeightAndClip() {
        ScrollSetup setup = build(200, 600);
        SceneScrollbar.Result sb = setup.scrollbar;
        // 初始 preferredWidth=barWidth（create 时设），doFrame 后有溢出仍为 barWidth
        Assert.assertEquals("column 初始宽 = barWidth", BAR_WIDTH, sb.column().getPreferredWidth());
        Assert.assertTrue("column fillParentHeight", sb.column().isFillParentHeight());
        Assert.assertTrue("column clipChildren", sb.column().isClipChildren());
    }

    // ==================== M2：column 可命中 + 滚轮转发 ====================

    @Test
    public void columnShouldBeHitTestableForScrollForwarding() {
        ScrollSetup setup = build(200, 600);
        Assert.assertTrue("column hitTestable=true（M2 滚轮转发）", setup.scrollbar.column().isHitTestable());
    }

    @Test
    public void defaultBarWidthShouldBeWidened() {
        Assert.assertEquals("DEFAULT_BAR_WIDTH=8", 8, SceneScrollbar.DEFAULT_BAR_WIDTH);
    }

    @Test
    public void thumbShouldHaveBarWidthAndThumbColor() {
        ScrollSetup setup = build(200, 600);
        SceneScrollbar.Result sb = setup.scrollbar;
        Assert.assertEquals("thumb 宽 = barWidth", BAR_WIDTH, sb.thumb().getPreferredWidth());
        // B1：默认色改为中性灰 idle（Slate-400 @ 40%），不再是 ACCENT
        Assert.assertEquals("thumb 默认色 = SCROLLBAR_THUMB_IDLE",
                SceneChromeTokens.SCROLLBAR_THUMB_IDLE, sb.thumb().getBackgroundColor());
    }

    // ==================== 验收 2：派生几何（有溢出） ====================

    /**
     * viewport=200, content=600 → maxScroll=400, thumbH=200²/600≈66, trackRange=134.
     * scroll=0 → thumbTop=0; scroll=200 → thumbTop=67; scroll=400 → thumbTop=134.
     */
    @Test
    public void thumbGeometryShouldReflectScrollPosition() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(setup.viewport);
        Assert.assertEquals("maxScroll=400", 400, maxScroll);

        int expectedThumb = 200 * 200 / (200 + maxScroll);
        Assert.assertEquals("thumb 高 = 200²/600 = 66", expectedThumb, setup.scrollbar.thumb().getPreferredHeight());

        // scroll=0 → thumbTop=0
        Assert.assertEquals("scroll=0 thumbTop=0", 0f, setup.scrollbar.thumb().getTransform().translateY, 0.5f);

        // 滚动到 200 → thumbTop=134*200/400=67
        setup.scrollSignal.set(Integer.valueOf(200));
        doFrame();
        int trackRange = 200 - expectedThumb;
        Assert.assertEquals("scroll=200 thumbTop=67",
                trackRange * 200 / (float) maxScroll,
                setup.scrollbar.thumb().getTransform().translateY, 0.5f);

        // 滚动到 maxScroll → thumbTop=trackRange=134
        setup.scrollSignal.set(Integer.valueOf(maxScroll));
        doFrame();
        Assert.assertEquals("scroll=max thumbTop=trackRange",
                (float) trackRange, setup.scrollbar.thumb().getTransform().translateY, 0.5f);
    }

    // ==================== 验收 3：无溢出时 thumb 高=0（B1）+ column 宽=0 ====================

    @Test
    public void noOverflowThumbShouldFillTrack() {
        ScrollSetup setup = build(200, 100);
        doFrame();
        // B1：无溢出 thumb 高=0（不可见），不再是占满 track
        Assert.assertEquals("无溢出 thumb 高=0", 0, setup.scrollbar.thumb().getPreferredHeight());
        Assert.assertEquals("无溢出 thumbTop=0", 0f,
                setup.scrollbar.thumb().getTransform().translateY, 0.5f);
    }

    @Test
    public void noOverflowColumnWidthShouldBeZero() {
        ScrollSetup setup = build(200, 100);
        doFrame();
        // B1：无溢出 column 宽派生为 0（整条滚动条不占布局宽）
        Assert.assertEquals("无溢出 column 宽=0", 0, setup.scrollbar.column().getPreferredWidth());
    }

    @Test
    public void noOverflowThumbColorShouldBeTransparent() {
        ScrollSetup setup = build(200, 100);
        doFrame();
        // B1：无溢出 thumb 颜色透明
        Assert.assertEquals("无溢出 thumb 颜色透明", 0x00000000,
                setup.scrollbar.thumb().getBackgroundColor());
    }

    // ==================== 验收 4：minThumbHeight 下限 ====================

    /**
     * viewport=200, content=10000 → thumbH=200²/10200≈3 < minThumb=20 → thumb=20.
     */
    @Test
    public void thumbShouldClampToMinThumbHeight() {
        ScrollSetup setup = build(200, 10000);
        doFrame();
        Assert.assertEquals("thumb 高 clamp 到 minThumb=20", MIN_THUMB,
                setup.scrollbar.thumb().getPreferredHeight());
    }

    // ==================== 验收 5：滚动零重排（COMPOSITE 级）+ I4 双轨探针断言 ====================

    @Test
    public void scrollShouldOnlyChangeTransformNotHeight() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int thumbHBefore = setup.scrollbar.thumb().getPreferredHeight();

        // 滚动：只写 scrollSignal，不桥接 layoutEpoch
        setup.scrollSignal.set(Integer.valueOf(200));
        runtime.flush(); // 物化 COMPOSITE bind（不跑 layout，保留脏标记供探针断言）

        Assert.assertTrue("滚动后 thumb __isCompositeDirty()=true（transform 标 COMPOSITE 脏）",
                setup.scrollbar.thumb().__isCompositeDirty());
        Assert.assertFalse("滚动后 thumb __isSelfLayoutDirty()=false（LAYOUT bind 未跑，height 未变不标 LAYOUT 脏）",
                setup.scrollbar.thumb().__isSelfLayoutDirty());

        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("滚动后 thumb 高不变", thumbHBefore,
                setup.scrollbar.thumb().getPreferredHeight());
        Assert.assertTrue("滚动后 thumb transform translateY > 0",
                setup.scrollbar.thumb().getTransform().translateY > 0f);
    }

    // ==================== C5：首帧 thumb 高=0（不闪烁） ====================

    @Test
    public void thumbInitialHeightShouldBeZero() {
        ScrollSetup setup = build(200, 600);
        // create 后、flush 前，thumb 初始高=0（C5：避免首帧闪烁）
        Assert.assertEquals("首帧 thumb 初始高=0", 0, setup.scrollbar.thumb().getPreferredHeight());
    }

    // ==================== B1：有溢出 thumb 颜色 = 中性灰 idle ====================

    @Test
    public void overflowThumbColorShouldBeNeutralGray() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        // 有溢出 + idle 态 → SCROLLBAR_THUMB_IDLE（中性灰）
        Assert.assertEquals("有溢出 idle thumb 色 = SCROLLBAR_THUMB_IDLE",
                SceneChromeTokens.SCROLLBAR_THUMB_IDLE,
                setup.scrollbar.thumb().getBackgroundColor());
    }

    // ==================== B1：thumb 颜色三态（hover/pressed） ====================

    @Test
    public void thumbColorShouldReflectHoverState() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        // MOVE 到 thumb 中心 → hover=true
        int x = centerX(setup.scrollbar.thumb());
        int y = centerY(setup.scrollbar.thumb());
        routePointer(ScenePointerAction.MOVE, x, y);
        runtime.flush();
        Assert.assertEquals("hover thumb 色 = SCROLLBAR_THUMB_HOVER",
                SceneChromeTokens.SCROLLBAR_THUMB_HOVER,
                setup.scrollbar.thumb().getBackgroundColor());
    }

    @Test
    public void thumbColorShouldReflectPressedState() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        // DOWN 到 thumb 中心 → pressed=true → DRAG 色（pressed 优先于 hover）
        int x = centerX(setup.scrollbar.thumb());
        int y = centerY(setup.scrollbar.thumb());
        routePointer(ScenePointerAction.BUTTON_DOWN, x, y);
        runtime.flush();
        Assert.assertEquals("pressed thumb 色 = SCROLLBAR_THUMB_DRAG",
                SceneChromeTokens.SCROLLBAR_THUMB_DRAG,
                setup.scrollbar.thumb().getBackgroundColor());
    }

    // ==================== B2：thumb 可命中 + 拖动 ====================

    @Test
    public void thumbShouldBeHitTestableForDrag() {
        ScrollSetup setup = build(200, 600);
        Assert.assertTrue("thumb hitTestable=true（B2 拖动）", setup.scrollbar.thumb().isHitTestable());
    }

    @Test
    public void dragThumbShouldScrollContent() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(setup.viewport);
        int thumbH = setup.scrollbar.thumb().getPreferredHeight();
        int trackRange = 200 - thumbH;

        int x = centerX(setup.scrollbar.thumb());
        int y = centerY(setup.scrollbar.thumb());
        // DOWN 到 thumb 中心 → 开始拖动 + capture
        routePointer(ScenePointerAction.BUTTON_DOWN, x, y);
        runtime.flush();
        // MOVE 下移 50px → scrollDelta = 50 * maxScroll / trackRange
        routePointer(ScenePointerAction.MOVE, x, y + 50);
        runtime.flush();

        long expectedScroll = (long) 50 * maxScroll / trackRange;
        Assert.assertEquals("拖动 thumb 后 scrollSignal = pointerDelta * maxScroll / trackRange",
                (int) Math.min(maxScroll, Math.max(0, expectedScroll)),
                setup.scrollSignal.get().intValue());
    }

    @Test
    public void dragThumbShouldClampToMaxScroll() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(setup.viewport);

        int x = centerX(setup.scrollbar.thumb());
        int y = centerY(setup.scrollbar.thumb());
        routePointer(ScenePointerAction.BUTTON_DOWN, x, y);
        runtime.flush();
        // MOVE 下移超大距离 → clamp 到 maxScroll
        routePointer(ScenePointerAction.MOVE, x, y + 10000);
        runtime.flush();

        Assert.assertEquals("拖动超限 clamp 到 maxScroll", maxScroll,
                setup.scrollSignal.get().intValue());
    }

    @Test
    public void dragThumbShouldClearDraggingOnPointerUp() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(setup.viewport);
        int thumbH = setup.scrollbar.thumb().getPreferredHeight();
        int trackRange = 200 - thumbH;

        int x = centerX(setup.scrollbar.thumb());
        int y = centerY(setup.scrollbar.thumb());
        // DOWN 到 thumb 中心 → 开始拖动 + capture
        routePointer(ScenePointerAction.BUTTON_DOWN, x, y);
        runtime.flush();
        // MOVE 下移 50px → scroll 变化
        routePointer(ScenePointerAction.MOVE, x, y + 50);
        runtime.flush();
        int scrollAfterMove = setup.scrollSignal.get().intValue();
        long expected = (long) 50 * maxScroll / trackRange;
        Assert.assertEquals("拖动中 scroll 已变化",
                (int) Math.min(maxScroll, Math.max(0, expected)), scrollAfterMove);

        // UP 释放 → dragging 清除 + capture 释放
        routePointer(ScenePointerAction.BUTTON_UP, x, y + 50);
        runtime.flush();

        // UP 后再 MOVE → dragging 已清除，不应触发滚动
        int scrollBeforeUpMove = setup.scrollSignal.get().intValue();
        routePointer(ScenePointerAction.MOVE, x, y + 100);
        runtime.flush();
        Assert.assertEquals("UP 后再 MOVE 不触发滚动（dragging 已清除）",
                scrollBeforeUpMove, setup.scrollSignal.get().intValue());
    }

    @Test
    public void dragThumbShouldClearDraggingOnPointerCancel() {
        ScrollSetup setup = build(200, 600);
        doFrame();

        int x = centerX(setup.scrollbar.thumb());
        int y = centerY(setup.scrollbar.thumb());
        // DOWN 到 thumb 中心 → 开始拖动 + capture
        routePointer(ScenePointerAction.BUTTON_DOWN, x, y);
        runtime.flush();
        // MOVE 下移 50px → scroll 变化（确认拖动已激活）
        routePointer(ScenePointerAction.MOVE, x, y + 50);
        runtime.flush();
        int scrollAfterMove = setup.scrollSignal.get().intValue();
        Assert.assertTrue("CANCEL 前 scroll 已变化（dragging 已激活）",
                scrollAfterMove > 0);

        // CANCEL → dragging 清除 + capture 释放
        routePointer(ScenePointerAction.CANCEL, x, y + 50);
        runtime.flush();

        // CANCEL 后再 MOVE → dragging 已清除，不应触发滚动
        int scrollBeforeCancelMove = setup.scrollSignal.get().intValue();
        routePointer(ScenePointerAction.MOVE, x, y + 100);
        runtime.flush();
        Assert.assertEquals("CANCEL 后再 MOVE 不触发滚动（dragging 已清除）",
                scrollBeforeCancelMove, setup.scrollSignal.get().intValue());
    }

    @Test
    public void trackClickAboveThumbShouldPageUp() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        // 滚动到 300，使 thumb 视觉下移（视觉 top ≈ 300.5），留出上方 track 空间
        setup.scrollSignal.set(Integer.valueOf(300));
        doFrame();

        // thumb 布局区始终在 column 顶部 [200, 266)，hit tester 据布局命中。
        // 点击 Y=280：在 column 内 [200,300]、在布局 thumb 之外 [266,300) → column handler，
        // 且 280 < thumb 视觉 top(≈300.5) → page up。
        AnchorRect thumbBox = SceneGeometry.absoluteBox(setup.scrollbar.thumb(), 0, 0);
        int x = thumbBox.getX() + thumbBox.getWidth() / 2;
        int clickY = 280;
        Assert.assertTrue("点击点在 column 内", clickY >= 200 && clickY < 300);
        Assert.assertTrue("点击点在布局 thumb 之外（hit tester 路由到 column）", clickY >= 266);
        routePointer(ScenePointerAction.BUTTON_DOWN, x, clickY);
        runtime.flush();

        // page up: scrollSignal = max(0, 300 - viewportHeight=200) = 100
        Assert.assertEquals("track 上方点击 page up", 100, setup.scrollSignal.get().intValue());
    }

    @Test
    public void trackClickBelowThumbShouldPageDown() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        // scroll=0：thumb 视觉 = 布局 = [200, 266)。点击 Y=280 在下方 track → page down
        AnchorRect thumbBox = SceneGeometry.absoluteBox(setup.scrollbar.thumb(), 0, 0);
        int x = thumbBox.getX() + thumbBox.getWidth() / 2;
        int clickY = 280;
        Assert.assertTrue("点击点在布局 thumb 之外", clickY >= 266);
        routePointer(ScenePointerAction.BUTTON_DOWN, x, clickY);
        runtime.flush();

        // page down: scrollSignal = min(400, 0 + 200) = 200
        Assert.assertEquals("track 下方点击 page down", 200, setup.scrollSignal.get().intValue());
    }

    @Test
    public void trackClickOnThumbShouldNotPage() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int scrollBefore = setup.scrollSignal.get().intValue();

        // 点击 thumb 中心 → thumb DOWN handler stopPropagation，column 不 page
        int x = centerX(setup.scrollbar.thumb());
        int y = centerY(setup.scrollbar.thumb());
        routePointer(ScenePointerAction.BUTTON_DOWN, x, y);
        runtime.flush();

        Assert.assertEquals("点击 thumb 不触发 track page", scrollBefore,
                setup.scrollSignal.get().intValue());
    }

    // ==================== B3：resize 后 thumb 几何更新 ====================

    @Test
    public void resizeShouldUpdateThumbGeometry() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int thumbHBefore = setup.scrollbar.thumb().getPreferredHeight();
        Assert.assertEquals("初始 thumb 高=66", 66, thumbHBefore);

        // resize：canvas 高度从 300 改到 250 → viewport 高度仍 200（preferredHeight），
        // 但 canvas 宽度变化不影响。改 viewport 高度来测试 resize。
        // 直接改 viewport preferredHeight 模拟 resize
        setup.viewport.setPreferredHeight(150);
        doFrame(CANVAS_WIDTH, CANVAS_HEIGHT);

        int maxScrollAfter = SceneGeometry.maxScrollY(setup.viewport);
        int vpHeightAfter = ((LayoutBox) setup.viewport.getCachedLayout()).getHeight();
        int expectedThumbAfter = vpHeightAfter * vpHeightAfter / (vpHeightAfter + maxScrollAfter);
        Assert.assertEquals("resize 后 thumb 高更新", expectedThumbAfter,
                setup.scrollbar.thumb().getPreferredHeight());
        Assert.assertNotEquals("resize 后 thumb 高变化", thumbHBefore,
                setup.scrollbar.thumb().getPreferredHeight());
    }

    // ==================== BUG1：scroll > 0 时点击 thumb 视觉位置拖动（column 转发拖动） ====================

    /**
     * BUG1 根因：thumb 因 transform 平移，hit-test 用布局位置（不含 transform），scroll > 0 时
     * 用户点击 thumb 视觉位置会命中 column（thumb 布局在顶部，视觉在中间），thumb DOWN handler
     * 不触发，原 column DOWN handler 只 page 不启动拖动 → 拖动失效。修复：column DOWN handler
     * 检测点击在 thumb 视觉区内时启动拖动 + column 注册 MOVE/UP/CANCEL handler（capture target=column
     * 时 MOVE/UP 投 column），共享 dragStart/dragging 闭包。
     *
     * <p>以下测试在 scroll > 0 时点击 thumb 视觉中心（hit-test 命中 column），验证 column 转发拖动。</p>
     */

    /**
     * scroll=100 时点击 thumb 视觉中心 → column DOWN 启动拖动，DOWN 后 scroll 无跳跃，
     * MOVE 50px 后 scroll = 100 + 50*maxScroll/trackRange。
     *
     * <p>注：column 高 = canvas - viewport = 300 - 200 = 100（sceneRoot COLUMN 上下排列 viewport/column），
     * scroll 过大时 thumb 视觉中心超出 column 范围无法命中。scroll=100 时 thumb 视觉中心 ≈ 266，
     * 在 column [200, 300) 内，可命中 column 触发 BUG1 转发拖动。</p>
     */
    @Test
    public void dragFromThumbVisualPositionShouldNotJump() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(setup.viewport);
        int thumbH = setup.scrollbar.thumb().getPreferredHeight();
        int trackRange = 200 - thumbH;

        // scroll=100 → thumb 视觉下移（transformY≈33）
        setup.scrollSignal.set(Integer.valueOf(100));
        doFrame();

        int x = centerX(setup.scrollbar.thumb());
        int clickY = (int) thumbVisualCenterY(setup.scrollbar.thumb());

        // 确认点击点在 thumb 布局区外（hit-test 命中 column，非 thumb）
        AnchorRect thumbBox = SceneGeometry.absoluteBox(setup.scrollbar.thumb(), 0, 0);
        Assert.assertTrue("点击点在 thumb 布局区外（hit-test 命中 column，BUG1 场景）",
                clickY >= thumbBox.getY() + thumbBox.getHeight());

        // DOWN 到 thumb 视觉中心 → column DOWN handler 启动拖动（BUG1 修复）
        routePointer(ScenePointerAction.BUTTON_DOWN, x, clickY);
        runtime.flush();
        // 无跳跃：DOWN 后 scroll 仍 100（dragStart 只设状态，不改 scroll）
        Assert.assertEquals("DOWN 后 scroll 无跳跃（仍 100）",
                100, setup.scrollSignal.get().intValue());

        // MOVE 下移 50px → pointerDelta=50, scrollDelta=50*maxScroll/trackRange
        routePointer(ScenePointerAction.MOVE, x, clickY + 50);
        runtime.flush();
        long expectedScroll = (long) 100 + (long) 50 * maxScroll / trackRange;
        Assert.assertEquals("MOVE 50px 后 scroll = 100 + 50*maxScroll/trackRange",
                (int) Math.min(maxScroll, Math.max(0, expectedScroll)),
                setup.scrollSignal.get().intValue());
    }

    /**
     * scroll=100 时点击 thumb 视觉位置 → column DOWN 启动拖动，MOVE +10000 → clamp 到 maxScroll。
     */
    @Test
    public void dragFromThumbVisualPositionShouldClamp() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(setup.viewport);

        setup.scrollSignal.set(Integer.valueOf(100));
        doFrame();

        int x = centerX(setup.scrollbar.thumb());
        int clickY = (int) thumbVisualCenterY(setup.scrollbar.thumb());
        routePointer(ScenePointerAction.BUTTON_DOWN, x, clickY);
        runtime.flush();
        // MOVE 下移超大距离 → clamp 到 maxScroll
        routePointer(ScenePointerAction.MOVE, x, clickY + 10000);
        runtime.flush();
        Assert.assertEquals("column 转发拖动超限 clamp 到 maxScroll",
                maxScroll, setup.scrollSignal.get().intValue());
    }

    /**
     * column DOWN 启动拖动后 UP 清 dragging，UP 后再 MOVE 不触发滚动。
     */
    @Test
    public void dragFromThumbVisualPositionShouldClearOnUp() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        setup.scrollSignal.set(Integer.valueOf(100));
        doFrame();

        int x = centerX(setup.scrollbar.thumb());
        int clickY = (int) thumbVisualCenterY(setup.scrollbar.thumb());
        // column DOWN 启动拖动
        routePointer(ScenePointerAction.BUTTON_DOWN, x, clickY);
        runtime.flush();
        // MOVE 已生效（column dragMoveHandler 跑）
        routePointer(ScenePointerAction.MOVE, x, clickY + 50);
        runtime.flush();
        int scrollAfterMove = setup.scrollSignal.get().intValue();
        Assert.assertTrue("column DOWN 启动拖动后 MOVE 已生效（scroll > 100）",
                scrollAfterMove > 100);

        // UP 释放 → dragging 清除 + capture 释放
        routePointer(ScenePointerAction.BUTTON_UP, x, clickY + 50);
        runtime.flush();

        // UP 后再 MOVE → dragging 已清除，不触发滚动
        int scrollBeforeUpMove = setup.scrollSignal.get().intValue();
        routePointer(ScenePointerAction.MOVE, x, clickY + 100);
        runtime.flush();
        Assert.assertEquals("UP 后再 MOVE 不触发滚动（column dragging 已清除）",
                scrollBeforeUpMove, setup.scrollSignal.get().intValue());
    }

    // ==================== 坐标系对齐：rootAbsY≠0 场景（本次修复核心） ====================

    /**
     * 根因回归：rootAbsY≠0（GUI 居中 margin）时，原 column DOWN handler 用
     * {@code ev.getPointerY()}（画布逻辑，含 rootAbsY）与 {@code absoluteBox}（host 局部，不含 rootAbsY）
     * 比对，错位 rootAbsY → 点击 thumb 视觉区被误判为 track → 走 page 分支不启动拖动。
     * 修复后用 {@code ev.getHostPointerY()}（host 局部）与 absoluteBox 同系，正确判为 thumb 视觉区 → 启动拖动。
     */
    @Test
    public void dragFromThumbVisualPositionWithRootAbsYShouldStartDragNotPage() {
        final int rootAbsX = 50;
        final int rootAbsY = 30;
        ScrollSetup setup = build(200, 600);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(setup.viewport);
        int thumbH = setup.scrollbar.thumb().getPreferredHeight();
        int trackRange = 200 - thumbH;

        // scroll=100 → thumb 视觉下移（transformY≈33.5）
        setup.scrollSignal.set(Integer.valueOf(100));
        doFrame();

        // thumb 视觉区（host 局部）：[thumbVisualTop, thumbVisualBottom)
        AnchorRect thumbBox = SceneGeometry.absoluteBox(setup.scrollbar.thumb(), 0, 0);
        float transformY = setup.scrollbar.thumb().getTransform().translateY;
        float thumbVisualTop = thumbBox.getY() + transformY;       // ≈233.5
        float thumbVisualBottom = thumbVisualTop + thumbBox.getHeight(); // ≈299.5

        // 选 host 局部 clickY=280：在 thumb 视觉区内 [233.5,299.5)，且在 thumb 布局区外 [200,266) → hit-test 命中 column（BUG1 场景）
        int clickYHost = 280;
        Assert.assertTrue("点击点在 thumb 视觉区内（host 局部）",
                clickYHost >= thumbVisualTop && clickYHost < thumbVisualBottom);
        Assert.assertTrue("点击点在 thumb 布局区外（hit-test 命中 column）",
                clickYHost >= thumbBox.getY() + thumbBox.getHeight());

        // 画布逻辑坐标 = host 局部 + rootAbsY（模拟屏幕绝对坐标）
        int clickYCanvas = clickYHost + rootAbsY;
        // X 也需加 rootAbsX（hit-test 要求 pointerX/Y 含 rootAbs，与节点 absX/Y 同系）
        int xCanvas = thumbBox.getX() + thumbBox.getWidth() / 2 + rootAbsX;

        // 修复前：ev.getPointerY()=clickYCanvas=310 与 thumbVisualTop=233.5 比对 → 310 >= 299.5 → 误判 page down
        // 修复后：ev.getHostPointerY()=clickYHost=280 与 thumbVisualTop=233.5 比对 → 280 < 299.5 → 判为 thumb 视觉区 → 启动拖动
        routePointer(ScenePointerAction.BUTTON_DOWN, xCanvas, clickYCanvas, rootAbsX, rootAbsY);
        runtime.flush();
        // 启动拖动：DOWN 后 scroll 无跳跃（仍 100），证明走拖动分支而非 page 分支
        // （若走 page 分支，scroll 会变成 clamp(100+200, 0, 400)=300）
        Assert.assertEquals("rootAbsY≠0 时点击 thumb 视觉区启动拖动（非 page），DOWN 后 scroll 无跳跃",
                100, setup.scrollSignal.get().intValue());

        // MOVE 下移 50px（画布坐标）→ hostPointerY delta=50, scrollDelta=50*maxScroll/trackRange
        routePointer(ScenePointerAction.MOVE, xCanvas, clickYCanvas + 50, rootAbsX, rootAbsY);
        runtime.flush();
        long expectedScroll = (long) 100 + (long) 50 * maxScroll / trackRange;
        Assert.assertEquals("rootAbsY≠0 时拖动 MOVE 50px 后 scroll = 100 + 50*maxScroll/trackRange",
                (int) Math.min(maxScroll, Math.max(0, expectedScroll)),
                setup.scrollSignal.get().intValue());
    }

    /**
     * rootAbsY≠0 时点击 track 空白区（thumb 视觉上方/下方）仍正确触发 page up/down。
     * 验证 page 逻辑在坐标系对齐后不受影响。
     */
    @Test
    public void trackClickWithRootAbsYShouldStillPage() {
        final int rootAbsX = 50;
        final int rootAbsY = 30;
        ScrollSetup setup = build(200, 600);
        doFrame();
        // scroll=0：thumb 视觉 = 布局 = [200, 266)。点击 Y=280 在下方 track → page down
        AnchorRect thumbBox = SceneGeometry.absoluteBox(setup.scrollbar.thumb(), 0, 0);
        int xCanvas = thumbBox.getX() + thumbBox.getWidth() / 2 + rootAbsX;
        // 画布坐标 = host 局部 280 + rootAbsY 30 = 310
        int clickYCanvas = 280 + rootAbsY;
        routePointer(ScenePointerAction.BUTTON_DOWN, xCanvas, clickYCanvas, rootAbsX, rootAbsY);
        runtime.flush();
        // page down: scrollSignal = min(400, 0 + 200) = 200
        Assert.assertEquals("rootAbsY≠0 时 track 下方点击仍 page down", 200,
                setup.scrollSignal.get().intValue());
    }

    /**
     * delta 范式首帧不跳跃：DOWN 后首帧 MOVE 到同一点（delta=0）→ scroll 不变。
     * 验证 dragStart[1]=点击点（非视觉中心校准）后，首帧 delta=0 不触发滚动。
     */
    @Test
    public void dragFirstMoveZeroDeltaShouldNotScroll() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int scrollBefore = 100;
        setup.scrollSignal.set(Integer.valueOf(scrollBefore));
        doFrame();

        int x = centerX(setup.scrollbar.thumb());
        int clickY = (int) thumbVisualCenterY(setup.scrollbar.thumb());
        // DOWN 到 thumb 视觉中心
        routePointer(ScenePointerAction.BUTTON_DOWN, x, clickY);
        runtime.flush();
        // 首帧 MOVE 到同一点（delta=0）→ scroll 不变
        routePointer(ScenePointerAction.MOVE, x, clickY);
        runtime.flush();
        Assert.assertEquals("首帧 MOVE delta=0 时 scroll 不变（delta 范式无跳跃）",
                scrollBefore, setup.scrollSignal.get().intValue());
    }

    /**
     * delta 范式：dragStart[1]=点击点，MOVE delta 从点击点算。
     * 点击 thumb 视觉中心后 MOVE 50px → delta=50（与原视觉中心校准模式结果一致，
     * 因 MOVE 终点也是从点击点 +50）。此测试验证 delta 范式下从点击点起算的正确性。
     */
    @Test
    public void dragDeltaParadigmShouldComputeFromClickPoint() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(setup.viewport);
        int thumbH = setup.scrollbar.thumb().getPreferredHeight();
        int trackRange = 200 - thumbH;

        setup.scrollSignal.set(Integer.valueOf(100));
        doFrame();

        int x = centerX(setup.scrollbar.thumb());
        // 点击 thumb 视觉顶部（非中心），验证 delta 从点击点算
        AnchorRect thumbBox = SceneGeometry.absoluteBox(setup.scrollbar.thumb(), 0, 0);
        float transformY = setup.scrollbar.thumb().getTransform().translateY;
        int clickY = (int) (thumbBox.getY() + transformY); // thumb 视觉顶部
        routePointer(ScenePointerAction.BUTTON_DOWN, x, clickY);
        runtime.flush();
        // MOVE 下移 50px → delta=50, scrollDelta=50*maxScroll/trackRange
        routePointer(ScenePointerAction.MOVE, x, clickY + 50);
        runtime.flush();
        long expectedScroll = (long) 100 + (long) 50 * maxScroll / trackRange;
        Assert.assertEquals("delta 范式从点击点起算（点击视觉顶部 MOVE 50px）",
                (int) Math.min(maxScroll, Math.max(0, expectedScroll)),
                setup.scrollSignal.get().intValue());
    }

    /**
     * 向后兼容：rootAbsY=0 时 hostPointerY == pointerY，现有拖动行为不变。
     * 此测试显式验证 rootAbsY=0 路径仍正确（与 dragThumbShouldScrollContent 互补）。
     */
    @Test
    public void dragWithZeroRootAbsYShouldBeBackwardCompatible() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(setup.viewport);
        int thumbH = setup.scrollbar.thumb().getPreferredHeight();
        int trackRange = 200 - thumbH;

        int x = centerX(setup.scrollbar.thumb());
        int y = centerY(setup.scrollbar.thumb());
        // rootAbs=0 显式传 0,0
        routePointer(ScenePointerAction.BUTTON_DOWN, x, y, 0, 0);
        runtime.flush();
        routePointer(ScenePointerAction.MOVE, x, y + 50, 0, 0);
        runtime.flush();
        long expectedScroll = (long) 50 * maxScroll / trackRange;
        Assert.assertEquals("rootAbsY=0 时拖动行为向后兼容",
                (int) Math.min(maxScroll, Math.max(0, expectedScroll)),
                setup.scrollSignal.get().intValue());
    }

    /**
     * rootAbsY≠0 且 scroll=0 时，点击 thumb 布局区（scroll=0 时布局=视觉，hit-test 命中 thumb 本身），
     * thumb DOWN handler 用 {@code ev.getHostPointerY()} 正确记录拖动起点（host 局部），
     * MOVE 时 hostPointerY delta 正确驱动滚动。
     *
     * <p>此测试覆盖 thumb 自身 DOWN handler 路径（与 {@link #dragFromThumbVisualPositionWithRootAbsYShouldStartDragNotPage}
     * 的 column 转发路径互补）：scroll=0 时 thumb 布局=视觉，hit-test 命中 thumb 而非 column，
     * 走 thumb DOWN handler。若 thumb DOWN handler 误用 {@code ev.getPointerY()}（画布逻辑，含 rootAbsY），
     * dragStart[1] 会偏移 rootAbsY，首帧 MOVE delta ≠ 0 → 跳跃。</p>
     */
    @Test
    public void dragFromThumbLayoutPositionWithRootAbsYShouldDrag() {
        final int rootAbsX = 50;
        final int rootAbsY = 30;
        ScrollSetup setup = build(200, 600);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(setup.viewport);
        int thumbH = setup.scrollbar.thumb().getPreferredHeight();
        int trackRange = 200 - thumbH;

        // scroll=0：thumb 布局=视觉=[200,266)，hit-test 命中 thumb 本身（非 column）
        Assert.assertEquals("scroll=0", 0, setup.scrollSignal.get().intValue());

        AnchorRect thumbBox = SceneGeometry.absoluteBox(setup.scrollbar.thumb(), 0, 0);
        int clickYHost = thumbBox.getY() + thumbBox.getHeight() / 2; // thumb 布局中心 host 局部
        int xCanvas = thumbBox.getX() + thumbBox.getWidth() / 2 + rootAbsX;
        int clickYCanvas = clickYHost + rootAbsY; // 画布逻辑 = host 局部 + rootAbsY

        // DOWN 到 thumb 布局中心 → thumb DOWN handler 用 getHostPointerY 记 dragStart[1]=clickYHost
        routePointer(ScenePointerAction.BUTTON_DOWN, xCanvas, clickYCanvas, rootAbsX, rootAbsY);
        runtime.flush();
        // DOWN 后 scroll 无跳跃（仍 0）
        Assert.assertEquals("rootAbsY≠0 scroll=0 时点击 thumb 布局区 DOWN 后 scroll 无跳跃",
                0, setup.scrollSignal.get().intValue());

        // MOVE 下移 50px（画布坐标）→ hostPointerY delta=50, scrollDelta=50*maxScroll/trackRange
        routePointer(ScenePointerAction.MOVE, xCanvas, clickYCanvas + 50, rootAbsX, rootAbsY);
        runtime.flush();
        long expectedScroll = (long) 50 * maxScroll / trackRange;
        Assert.assertEquals("rootAbsY≠0 scroll=0 时拖动 MOVE 50px 后 scroll = 50*maxScroll/trackRange",
                (int) Math.min(maxScroll, Math.max(0, expectedScroll)),
                setup.scrollSignal.get().intValue());
    }

    /**
     * rootAbsY≠0 时 DOWN → MOVE → CANCEL 清除 dragging，CANCEL 后再 MOVE 不触发滚动。
     * 验证 thumb DOWN handler 路径下 CANCEL 清理在坐标系对齐后仍正确。
     */
    @Test
    public void dragWithRootAbsYShouldClearOnCancel() {
        final int rootAbsX = 50;
        final int rootAbsY = 30;
        ScrollSetup setup = build(200, 600);
        doFrame();

        AnchorRect thumbBox = SceneGeometry.absoluteBox(setup.scrollbar.thumb(), 0, 0);
        int clickYHost = thumbBox.getY() + thumbBox.getHeight() / 2;
        int xCanvas = thumbBox.getX() + thumbBox.getWidth() / 2 + rootAbsX;
        int clickYCanvas = clickYHost + rootAbsY;

        // DOWN 到 thumb 布局中心 → 启动拖动
        routePointer(ScenePointerAction.BUTTON_DOWN, xCanvas, clickYCanvas, rootAbsX, rootAbsY);
        runtime.flush();
        // MOVE 下移 50px → scroll 变化（确认拖动已激活）
        routePointer(ScenePointerAction.MOVE, xCanvas, clickYCanvas + 50, rootAbsX, rootAbsY);
        runtime.flush();
        int scrollAfterMove = setup.scrollSignal.get().intValue();
        Assert.assertTrue("CANCEL 前 scroll 已变化（dragging 已激活）",
                scrollAfterMove > 0);

        // CANCEL → dragging 清除 + capture 释放
        routePointer(ScenePointerAction.CANCEL, xCanvas, clickYCanvas + 50, rootAbsX, rootAbsY);
        runtime.flush();

        // CANCEL 后再 MOVE → dragging 已清除，不触发滚动
        int scrollBeforeCancelMove = setup.scrollSignal.get().intValue();
        routePointer(ScenePointerAction.MOVE, xCanvas, clickYCanvas + 100, rootAbsX, rootAbsY);
        runtime.flush();
        Assert.assertEquals("rootAbsY≠0 时 CANCEL 后再 MOVE 不触发滚动（dragging 已清除）",
                scrollBeforeCancelMove, setup.scrollSignal.get().intValue());
    }

    // ==================== P0：thumb 无手传 signal 也更新 ====================

    /**
     * P0 核心验证：scrollbar Props 不再含 contentChangedSignal，create 内部直接订阅
     * rt.layoutDoneSignal()。作者无需手传任何 layout 完成通知——只需 host 桥接 epoch，
     * thumb 几何即随 content 高度变化在同帧 flush 内更新。
     *
     * <p>步骤：建 scrollbar（Props 无 contentChangedSignal）→ layout+桥接+flush →
     * 断言 thumb 高 > 0 → 改 content 高再走一帧 → 断言 thumb 高变化。全程无作者手传 signal。</p>
     */
    @Test
    public void thumbShouldUpdateWithoutAuthorSuppliedSignal() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int thumbHBefore = setup.scrollbar.thumb().getPreferredHeight();
        Assert.assertTrue("有溢出时 thumb 高 > 0（无手传 signal，靠 rt.layoutDoneSignal 驱动）",
                thumbHBefore > 0);

        // 改 content 高度（1200 → thumb 高应变小），再走一帧
        SceneNode content = setup.viewport.__getChildren().get(0);
        content.setPreferredHeight(1200);
        doFrame();

        int thumbHAfter = setup.scrollbar.thumb().getPreferredHeight();
        Assert.assertTrue("content 高度变化后 thumb 高应变小（同帧 flush 内更新，无手传 signal）",
                thumbHAfter < thumbHBefore);
        Assert.assertTrue("改 content 高后 thumb 高仍 > 0", thumbHAfter > 0);
    }
}
