package club.heiqi.uilib.ui.scene.control;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.search.GridMetrics;
import club.heiqi.uilib.ui.scene.control.search.PickerChrome;
import club.heiqi.uilib.ui.scene.control.search.PickerDensityPreference;
import club.heiqi.uilib.ui.scene.control.search.PickerMetrics;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneHitTester;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

import java.util.List;

/**
 * SceneScrollbar 宽度信号（P5 第六轮 U-P5-13：滚动条宽度动态派生）验收。
 *
 * <h3>被测主张</h3>
 * <ol>
 *   <li><b>缺省等价</b>：无宽度信号（含场景选择的 null 度量通道）⇒ 逐值等于既有常量口径，
 *       旧构造器/工厂签名保留可用；信号值 ≤0/null 亦回退常量；</li>
 *   <li><b>首帧正确</b>：信号在 create 期被同步读一次驱动首帧几何（无「先天常量再跳变」）；</li>
 *   <li><b>只重派生几何、不重建控件</b>：信号变化后 column/thumb 引用不变、结构不变、宽度与圆角更新；</li>
 *   <li><b>命中面同步</b>：column 的 preferredWidth 即指针可达面 ⇒ 加宽后原带外坐标立即可达（命中链含 column）；</li>
 *   <li><b>拖动判定同步</b>：宽度变化后拖动仍按实时几何工作（不缓存宽度）；</li>
 *   <li><b>有界</b>：多次信号变化不新增订阅（effect 计数恒定）；</li>
 *   <li><b>选择器装配点已接入</b>：picker 派生信号（字号 ⇒ 宽度）驱动滚动条宽度，度量缺省时回落常量。</li>
 * </ol>
 */
public class SceneScrollbarWidthSignalTest {

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    /** 常量宽度（无信号路径的既有口径）。 */
    private static final int BAR_WIDTH = 8;
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
        sceneRoot.setFillParentHeight(true);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** viewport（200 高）+ content（600 高）+ 滚动条（宽度来自给定 Props）挂到 sceneRoot。 */
    private SceneScrollbar.Result build(SceneScrollbar.Props props) {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(200);
        sceneRoot.appendChild(viewport);
        SceneNode content = new SceneNode();
        content.setPreferredHeight(600);
        viewport.appendChild(content);
        Signal<Integer> scrollSignal = SceneScrolls.attach(runtime, viewport);
        SceneScrollbar.Props withScroll = new SceneScrollbar.Props(
                viewport, scrollSignal, scrollSignal::set,
                props.trackColor(), props.thumbColor(), props.barWidth(), props.minThumbHeight(),
                props.onDragStart(), props.hoverColor(), props.dragColor(),
                props.hitBandWidth(), props.thumbVisualWidth(), props.barWidthSignal());
        SceneScrollbar.Result sb = SceneScrollbar.create(runtime, withScroll);
        sceneRoot.appendChild(sb.column());
        return sb;
    }

    /** 布局 → 桥接 layoutDone → flush → 收尾布局（与 SceneScrollbarTest.doFrame 同构）。 */
    private void doFrame() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        runtime.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    private int centerX(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getX() + box.getWidth() / 2;
    }

    private int centerY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY() + box.getHeight() / 2;
    }

    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /** 常量宽度 Props（旧 7 参形态）。 */
    private static SceneScrollbar.Props constantProps(int barWidth) {
        return new SceneScrollbar.Props(null, null, null,
                SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                barWidth, MIN_THUMB);
    }

    // ==================== 1. 缺省等价 ====================

    /** 无宽度信号 ⇒ column/thumb 宽 = 常量、圆角 = max(1, 常量/2)（与既有行为逐值一致）。 */
    @Test
    public void defaultConstructionWithoutSignalStaysConstant() {
        SceneScrollbar.Props props = constantProps(BAR_WIDTH);
        Assert.assertNull("缺省构造器不携带宽度信号", props.barWidthSignal());
        SceneScrollbar.Result sb = build(props);
        Assert.assertEquals("column 宽 = 常量 barWidth", BAR_WIDTH, sb.column().getPreferredWidth());
        Assert.assertEquals("thumb 宽 = 常量 barWidth", BAR_WIDTH, sb.thumb().getPreferredWidth());
        Assert.assertEquals("column 圆角 = barWidth/2", BAR_WIDTH / 2, sb.column().getCornerRadius());
        Assert.assertEquals("thumb 圆角 = barWidth/2", BAR_WIDTH / 2, sb.thumb().getCornerRadius());

        doFrame();
        Assert.assertEquals("无信号多帧后 column 宽不变", BAR_WIDTH, sb.column().getPreferredWidth());
        Assert.assertEquals("无信号多帧后 thumb 宽不变", BAR_WIDTH, sb.thumb().getPreferredWidth());
    }

    /** 信号存在但值 ≤0 / 初值 null ⇒ 回退常量（不出零宽不可命中滚动条）。 */
    @Test
    public void nonPositiveSignalValueFallsBackToConstant() {
        Signal<Integer> width = Signal.create(Integer.valueOf(0));
        SceneScrollbar.Props props = new SceneScrollbar.Props(
                null, null, null, SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                BAR_WIDTH, MIN_THUMB, null, null, null, 0, 0, width);
        SceneScrollbar.Result sb = build(props);
        Assert.assertEquals("信号 0 ⇒ 回退常量", BAR_WIDTH, sb.column().getPreferredWidth());

        width.set(Integer.valueOf(-3));
        runtime.flush();
        Assert.assertEquals("信号负值 ⇒ 回退常量", BAR_WIDTH, sb.column().getPreferredWidth());
    }

    /** 容器默认入口（选择器路径的缺省形态）：缺省规格的宽度仍 = DEFAULT_BAR_WIDTH。 */
    @Test
    public void defaultContainerSpecStaysConstant() {
        SceneScrollContainer.Result sc = SceneScrollContainer.createDefault(runtime, 0, 0, 0, 0);
        SceneNode column = sc.container().__getChildren().get(1);
        Assert.assertEquals("缺省容器规格 ⇒ 常量宽",
                SceneScrollbar.DEFAULT_BAR_WIDTH, column.getPreferredWidth());
    }

    // ==================== 2. 首帧正确 + 3. 只重派生几何 ====================

    /** 信号值在 create 期即生效（首帧无「常量 → 派生」跳变）。 */
    @Test
    public void signalValueDrivesFirstFrameGeometry() {
        Signal<Integer> width = Signal.create(Integer.valueOf(12));
        SceneScrollbar.Props props = new SceneScrollbar.Props(
                null, null, null, SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                BAR_WIDTH, MIN_THUMB, null, null, null, 0, 0, width);
        SceneScrollbar.Result sb = build(props);
        Assert.assertEquals("create 期即取信号值（column）", 12, sb.column().getPreferredWidth());
        Assert.assertEquals("create 期即取信号值（thumb）", 12, sb.thumb().getPreferredWidth());
        Assert.assertEquals("圆角随信号派生", 6, sb.column().getCornerRadius());
    }

    /** 信号变化 ⇒ 几何更新，控件引用与结构零变化（不重建）。 */
    @Test
    public void widthSignalChangeReDerivesGeometryWithoutRebuild() {
        Signal<Integer> width = Signal.create(Integer.valueOf(12));
        SceneScrollbar.Props props = new SceneScrollbar.Props(
                null, null, null, SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                BAR_WIDTH, MIN_THUMB, null, null, null, 0, 0, width);
        SceneScrollbar.Result sb = build(props);
        SceneNode column = sb.column();
        SceneNode thumb = sb.thumb();
        doFrame();
        int rootChildrenBefore = sceneRoot.__getChildren().size();

        width.set(Integer.valueOf(18));
        doFrame();

        Assert.assertSame("column 引用不变（不重建）", column, sb.column());
        Assert.assertSame("thumb 引用不变（不重建）", thumb, sb.thumb());
        Assert.assertEquals("column 结构不变（仍 1 个 thumb）", 1, column.__getChildren().size());
        Assert.assertEquals("sceneRoot 子数不变（无重建挂载）", rootChildrenBefore,
                sceneRoot.__getChildren().size());
        Assert.assertEquals("column 宽随信号更新", 18, column.getPreferredWidth());
        Assert.assertEquals("thumb 宽随信号更新", 18, thumb.getPreferredWidth());
        Assert.assertEquals("column 圆角随信号更新", 9, column.getCornerRadius());
        Assert.assertEquals("thumb 圆角随信号更新", 9, thumb.getCornerRadius());
        Assert.assertEquals("布局盒宽 = 新带宽", 18,
                ((LayoutBox) column.getCachedLayout()).getWidth());
    }

    // ==================== 4. 命中面同步 ====================

    /** column 的 preferredWidth 即指针可达面：加宽后原带外坐标立即可达。 */
    @Test
    public void widthSignalChangeUpdatesPointerReachableBand() {
        Signal<Integer> width = Signal.create(Integer.valueOf(BAR_WIDTH));
        SceneScrollbar.Props props = new SceneScrollbar.Props(
                null, null, null, SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                BAR_WIDTH, MIN_THUMB, null, null, null, 0, 0, width);
        SceneScrollbar.Result sb = build(props);
        doFrame();
        SceneNode column = sb.column();
        SceneHitTester hitTester = new SceneHitTester();
        int probeX = BAR_WIDTH + 1;                 // 原带外 1px
        int probeY = centerY(column);
        Assert.assertFalse("宽度 8 时带外坐标不命中 column",
                hitTester.hitTest(sceneRoot, probeX, probeY, 0, 0).contains(column));

        width.set(Integer.valueOf(18));
        doFrame();
        Assert.assertEquals("命中带已加宽", 18, column.getPreferredWidth());
        Assert.assertTrue("加宽后同一坐标命中 column（命中面同步）",
                hitTester.hitTest(sceneRoot, probeX, probeY, 0, 0).contains(column));
    }

    // ==================== 5. 拖动判定同步 ====================

    /** 宽度信号变化后拖动仍工作（拖动公式读实时几何，不缓存宽度）。 */
    @Test
    public void dragStillWorksAfterWidthSignalChange() {
        Signal<Integer> width = Signal.create(Integer.valueOf(BAR_WIDTH));
        SceneScrollbar.Props props = new SceneScrollbar.Props(
                null, null, null, SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                BAR_WIDTH, MIN_THUMB, null, null, null, 0, 0, width);
        // 自建（需要 scrollSignal 引用做断言）：与 build 同构但保留 scrollSignal
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(200);
        sceneRoot.appendChild(viewport);
        SceneNode content = new SceneNode();
        content.setPreferredHeight(600);
        viewport.appendChild(content);
        Signal<Integer> scrollSignal = SceneScrolls.attach(runtime, viewport);
        SceneScrollbar.Result sb = SceneScrollbar.create(runtime, new SceneScrollbar.Props(
                viewport, scrollSignal, scrollSignal::set,
                props.trackColor(), props.thumbColor(), props.barWidth(), props.minThumbHeight(),
                null, null, null, 0, 0, width));
        sceneRoot.appendChild(sb.column());

        width.set(Integer.valueOf(16));
        doFrame();
        Assert.assertEquals("拖动前 scroll=0", 0, scrollSignal.get().intValue());

        int x = centerX(sb.thumb());
        int y = centerY(sb.thumb());
        routePointer(ScenePointerAction.BUTTON_DOWN, x, y);
        routePointer(ScenePointerAction.MOVE, x, y + 30);
        routePointer(ScenePointerAction.BUTTON_UP, x, y + 30);
        runtime.flush();
        Assert.assertTrue("宽度变化后拖动仍写入 scroll（实际 " + scrollSignal.get() + "）",
                scrollSignal.get().intValue() > 0);
    }

    // ==================== 6. 有界（不累积订阅） ====================

    /** 多次宽度变化不新增订阅：effect 注册数恒定，且几何仍逐次更新。 */
    @Test
    public void widthSignalChangesStayBounded() {
        Signal<Integer> width = Signal.create(Integer.valueOf(10));
        SceneScrollbar.Props props = new SceneScrollbar.Props(
                null, null, null, SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                BAR_WIDTH, MIN_THUMB, null, null, null, 0, 0, width);
        SceneScrollbar.Result sb = build(props);
        doFrame();
        int effectsAfterCreate = ReactiveTestProbe.registeredEffectCount();

        for (int i = 0; i < 5; i++) {
            width.set(Integer.valueOf(10 + i));
            doFrame();
            Assert.assertEquals("第 " + (i + 1) + " 次变化后带宽更新", 10 + i,
                    sb.column().getPreferredWidth());
        }
        Assert.assertEquals("多次变化后 effect 数不增（订阅有界）",
                effectsAfterCreate, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 7. 显式覆盖优先 ====================

    /** 显式 hitBandWidth/thumbVisualWidth 覆盖优先于宽度信号（与 create 期同口径）。 */
    @Test
    public void explicitHitBandAndThumbVisualWidthWinOverSignal() {
        Signal<Integer> width = Signal.create(Integer.valueOf(12));
        SceneScrollbar.Props props = new SceneScrollbar.Props(
                null, null, null, SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                BAR_WIDTH, MIN_THUMB, null, null, null, 20, 6, width);
        SceneScrollbar.Result sb = build(props);
        Assert.assertEquals("显式命中带 20 生效", 20, sb.column().getPreferredWidth());
        Assert.assertEquals("显式可视宽 6 生效", 6, sb.thumb().getPreferredWidth());

        width.set(Integer.valueOf(18));
        doFrame();
        Assert.assertEquals("信号变化不覆盖显式命中带", 20, sb.column().getPreferredWidth());
        Assert.assertEquals("信号变化不覆盖显式可视宽", 6, sb.thumb().getPreferredWidth());
        Assert.assertEquals("圆角仍随宽度派生", 9, sb.column().getCornerRadius());
    }

    // ==================== 8. 选择器装配点 ====================

    /** picker 派生信号：同步初值 = 字号派生 oracle（U-P6D-3 投影工厂判据），度量缺省 ⇒ null。 */
    @Test
    public void pickerDerivedWidthSignalInitialValueMatchesOracle() {
        Assert.assertNull("无度量通道 ⇒ null（走常量缺省）",
                PickerChrome.scrollbarWidthSignal(null));
        Assert.assertNull("无网格度量通道 ⇒ null（走常量缺省）",
                PickerChrome.scrollbarWidthSignalOf(null));

        PickerMetrics metrics = PickerMetrics.solve(runtime, 1920, 1080, 18,
                PickerDensityPreference.AUTO, 2);
        Signal<PickerMetrics> metricsSignal = Signal.create(metrics);
        ReadableSignal<Integer> widthSignal = PickerChrome.scrollbarWidthSignal(metricsSignal);
        Assert.assertEquals("同步读初值 = 字号派生 oracle（flush 前即可用）",
                PickerChrome.scrollbarWidth(metrics.fontSizePx()),
                widthSignal.get().intValue());

        GridMetrics grid = metrics.grid();
        Assert.assertEquals("网格度量通道同口径",
                PickerChrome.scrollbarWidth(grid.fontSizePx()),
                PickerChrome.scrollbarWidthSignalOf(
                        Signal.create(grid)).get().intValue());
    }

    /** 选择器路径端到端：度量（字号）变化 ⇒ 滚动条宽度与命中面重派生，且不重建、订阅有界。 */
    @Test
    public void pickerPathScrollbarFollowsFontSizeDerivation() {
        PickerMetrics small = PickerMetrics.solve(runtime, 1920, 1080, 12,
                PickerDensityPreference.AUTO, 2);
        PickerMetrics large = PickerMetrics.solve(runtime, 1920, 1080, 18,
                PickerDensityPreference.AUTO, 2);
        Signal<PickerMetrics> metricsSignal = Signal.create(small);

        SceneScrollContainer.Result sc = SceneScrollContainer.createDefault(runtime, 0, 0, 0, 0,
                PickerChrome.scrollbarWidthSignal(metricsSignal));
        SceneNode viewport = sc.viewport();
        List<SceneNode> containerChildren = sc.container().__getChildren();
        SceneNode column = containerChildren.get(containerChildren.size() - 1);
        int smallWidth = PickerChrome.scrollbarWidth(small.fontSizePx());
        int largeWidth = PickerChrome.scrollbarWidth(large.fontSizePx());
        Assert.assertTrue("两档字号的派生宽度必须不同（否则用例空转）",
                smallWidth != largeWidth);
        Assert.assertEquals("首帧宽度 = 小字号派生值", smallWidth, column.getPreferredWidth());

        // 内容撑出溢出，使滚动条可见/可拖动
        SceneNode filler = new SceneNode();
        filler.setPreferredHeight(900);
        sc.content().appendChild(filler);
        sceneRoot.appendChild(sc.container());
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        runtime.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        metricsSignal.set(large);
        runtime.flush();
        runtime.flush();

        Assert.assertEquals("度量变化 ⇒ 滚动条宽度重派生", largeWidth, column.getPreferredWidth());
        Assert.assertSame("不重建控件（viewport 引用不变）", viewport, sc.viewport());
        Assert.assertEquals("订阅有界（无新增 effect）", effectsBefore,
                ReactiveTestProbe.registeredEffectCount());
        Assert.assertTrue("内容溢出（滚动条处于可见形态）",
                SceneGeometry.maxScrollY(viewport) > 0);
    }

    /**
     * 装配点源码守卫（U-P5-13 收口）：选择器四个滚动区（成员带 / 结果区 / 分类导航 / 变体列表）
     * 必须把「字号 ⇒ 宽度」派生信号接进滚动条容器；缺度量通道时助手返回 null ⇒ 常量缺省。
     *
     * <p>变异检查：删掉任一装配点的信号实参（回到四参 {@code createDefault}）⇒ 本用例当场红。
     * 行为面守卫见 {@link #pickerPathScrollbarFollowsFontSizeDerivation}（同一机制端到端）。</p>
     */
    @Test
    public void pickerAssemblySitesPassDerivedWidthSignal() throws java.io.IOException {
        String[][] sites = {
                {"src/main/java/club/heiqi/uilib/ui/scene/control/search/MemberGrid.java",
                        "scrollbarWidthSignal(props.metrics())"},
                {"src/main/java/club/heiqi/uilib/ui/scene/control/search/SearchResultList.java",
                        "scrollbarWidthSignalOf(props.metrics())"},
                {"src/main/java/club/heiqi/uilib/ui/scene/control/search/CategoryNavPane.java",
                        "scrollbarWidthSignal(props.metrics())"},
                {"src/main/java/club/heiqi/uilib/ui/scene/control/search/VariantChooser.java",
                        "scrollbarWidthSignal(props.metrics())"},
        };
        for (String[] site : sites) {
            String code = new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(site[0])), java.nio.charset.StandardCharsets.UTF_8);
            Assert.assertTrue(site[0] + " 必须把派生宽度信号接进滚动条容器（U-P5-13）",
                    code.contains(site[1]));
        }
    }
}
