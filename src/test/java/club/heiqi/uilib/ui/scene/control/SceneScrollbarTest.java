package club.heiqi.uilib.ui.scene.control;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.component.SceneScrolls;
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
 * <p>测试用 {@code contentChangedSignal.set(...) + runtime.flush()} 模拟 host 的 layoutDoneSignal 桥接：
 * doFrame 顺序：layout（产出 LayoutBox）→ bump contentChangedSignal → flush（驱动 effect 读最新 LayoutBox）
 * → layout（清掉 effect 写入的 selfLayoutDirty）。</p>
 */
public class SceneScrollbarTest {

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int BAR_WIDTH = 4;
    private static final int MIN_THUMB = 20;

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    /** content/layout 几何变化通知 signal（模拟 host 的 layoutDoneSignal）。 */
    private Signal<Integer> contentChangedSignal;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer());
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer());
        sceneRoot = new SceneNode();
        sceneRoot.setFillParentHeight(true); // 根填满 canvas 高，使 column 有 free space grow
        contentChangedSignal = Signal.create(Integer.valueOf(0));
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
                viewport, scrollSignal, contentChangedSignal,
                SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                BAR_WIDTH, MIN_THUMB);
        SceneScrollbar.Result sb = SceneScrollbar.create(runtime, props);
        sceneRoot.appendChild(sb.column());
        return new ScrollSetup(viewport, scrollSignal, sb);
    }

    /**
     * 执行 layout + bump contentChangedSignal + flush + layout（模拟宿主帧循环 +
     * layoutDoneSignal 桥接：layout 产出 LayoutBox → bump signal → flush 驱动 effect 重跑
     * 读最新 LayoutBox → layout 清掉 effect 写入的脏标记）。
     */
    private void doFrame() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        // 桥接 layoutDoneSignal：bump signal 驱动 scrollbar effect 重跑读最新 LayoutBox
        contentChangedSignal.set(Integer.valueOf(contentChangedSignal.get().intValue() + 1));
        runtime.flush();
        // 末尾 layout：清掉 effect 写入的 selfLayoutDirty，使树回到干净态
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * 用指定约束执行 doFrame（用于 B3 resize 测试）。
     */
    private void doFrame(int w, int h) {
        layoutEngine.layout(sceneRoot, new Constraints(w, h));
        contentChangedSignal.set(Integer.valueOf(contentChangedSignal.get().intValue() + 1));
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(w, h));
    }

    /**
     * 在指定绝对坐标投递单指针事件并 route 到 sceneRoot（rootAbs=0,0）。
     */
    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
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

        // 滚动：只写 scrollSignal，不 bump contentChangedSignal
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
}
