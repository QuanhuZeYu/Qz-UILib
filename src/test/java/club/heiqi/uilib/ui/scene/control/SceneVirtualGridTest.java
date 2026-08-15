package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Item;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Props;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Result;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.WindowModel;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
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
 * {@link SceneVirtualGrid} 单元测试。
 *
 * <p>覆盖：行级虚拟化挂载数、滚动驱动窗口计算、数据收缩回夹、空列表、点击激活、
 * 四向键盘导航边界、受控高亮、自动列数、禁用不激活，以及 {@link SceneVirtualGridNav}
 * 纯函数导航/窗口数学边界。</p>
 */
public class SceneVirtualGridTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int COLUMNS = 3;
    private static final int CELL_W = 64;
    private static final int CELL_H = 64;
    private static final int GAP_X = 8;
    private static final int GAP_Y = 8;
    private static final int VISIBLE_ROWS = 2;
    private static final int STRIDE = CELL_H + GAP_Y;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    private void layoutAndBridge() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    private static List<Item> items(int count) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new Item(Integer.valueOf(i), null, "item" + i));
        }
        return items;
    }

    private GridFixture fixture(int itemCount) {
        GridFixture fixture = new GridFixture(itemCount);
        sceneRoot.appendChild(fixture.result.root());
        rt.flush();
        layoutAndBridge();
        return fixture;
    }

    /** 测试夹具：itemsSignal + Result + 激活记录。 */
    private final class GridFixture {
        final Signal<List<Item>> itemsSignal;
        final List<Object> activated = new ArrayList<>();
        final Result result;

        GridFixture(int itemCount) {
            itemsSignal = Signal.create(items(itemCount));
            Props props = Props.of(itemsSignal, COLUMNS, CELL_W, CELL_H, GAP_X, GAP_Y,
                    VISIBLE_ROWS, Signal.create(Boolean.TRUE), item -> activated.add(item.key()));
            result = SceneVirtualGrid.create(rt, props);
        }

        SceneNode rowsContainer() {
            return result.viewport().__getChildren().get(1);
        }
    }

    // ==================== 窗口计算与虚拟化 ====================

    @Test
    public void mountsOnlyVisibleWindowRows() {
        GridFixture f = fixture(20);
        WindowModel model = f.result.windowModel().get();
        Assert.assertEquals(3, model.columns());
        Assert.assertEquals(20, model.totalItems());
        Assert.assertEquals(7, model.totalRows());
        Assert.assertEquals(0, model.windowStartRow());
        // visibleRows(2) + overscan(1) = 3
        Assert.assertEquals(3, model.mountedRows());
        Assert.assertEquals(3, f.rowsContainer().__getChildren().size());
        Assert.assertEquals(5, model.maxStartRow());
        Assert.assertEquals(7 * STRIDE - (2 * CELL_H + GAP_Y), model.maxScrollPx());
    }

    @Test
    public void scrollDrivesWindowComputation() {
        GridFixture f = fixture(20);
        f.result.scrollSignal().set(Integer.valueOf(2 * STRIDE));
        rt.flush();
        layoutAndBridge();
        WindowModel model = f.result.windowModel().get();
        Assert.assertEquals(2, model.windowStartRow());
        Assert.assertEquals(2 * STRIDE, f.result.viewport().__getChildren().get(0).getPreferredHeight());
    }

    @Test
    public void scrollClampsOnDataShrink() {
        GridFixture f = fixture(20);
        f.result.scrollSignal().set(Integer.valueOf(7 * STRIDE - (2 * CELL_H + GAP_Y)));
        rt.flush();
        Assert.assertEquals(7 * STRIDE - (2 * CELL_H + GAP_Y),
                f.result.scrollSignal().get().intValue());
        // 收缩到 4 项 → totalRows=2 → maxScrollPx = 2*STRIDE - viewportH = 8
        f.itemsSignal.set(items(4));
        rt.flush();
        Assert.assertEquals(2 * STRIDE - (2 * CELL_H + GAP_Y),
                f.result.scrollSignal().get().intValue());
    }

    @Test
    public void emptyListClearsRowsAndScroll() {
        GridFixture f = fixture(20);
        f.itemsSignal.set(new ArrayList<Item>());
        rt.flush();
        layoutAndBridge();
        WindowModel model = f.result.windowModel().get();
        Assert.assertEquals(0, model.totalItems());
        Assert.assertEquals(0, model.totalRows());
        Assert.assertEquals(0, f.rowsContainer().__getChildren().size());
        Assert.assertEquals(0, model.maxScrollPx());
        Assert.assertEquals(0, f.result.scrollSignal().get().intValue());
    }

    // ==================== 交互 ====================

    @Test
    public void clickActivatesItem() {
        GridFixture f = fixture(20);
        SceneNode cell = f.rowsContainer().__getChildren().get(0).__getChildren().get(0);
        click(cell);
        Assert.assertEquals(1, f.activated.size());
        Assert.assertEquals(Integer.valueOf(0), f.activated.get(0));
        Assert.assertEquals(Integer.valueOf(0), f.result.highlighted().get());
    }

    @Test
    public void disabledGridIgnoresClick() {
        Signal<List<Item>> itemsSignal = Signal.create(items(20));
        List<Object> activated = new ArrayList<>();
        Props props = Props.of(itemsSignal, COLUMNS, CELL_W, CELL_H, GAP_X, GAP_Y, VISIBLE_ROWS,
                Signal.create(Boolean.FALSE), item -> activated.add(item.key()));
        Result result = SceneVirtualGrid.create(rt, props);
        sceneRoot.appendChild(result.root());
        rt.flush();
        layoutAndBridge();
        SceneNode cell = result.viewport().__getChildren().get(1).__getChildren().get(0)
                .__getChildren().get(0);
        click(cell);
        Assert.assertTrue(activated.isEmpty());
    }

    @Test
    public void keyboardNavMovesHighlightAndScrollsIntoView() {
        GridFixture f = fixture(20);
        rt.requestFocus(f.result.viewport());
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals(Integer.valueOf(0), f.result.highlighted().get());
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals(Integer.valueOf(3), f.result.highlighted().get());
        pressKey(SceneKey.ARROW_LEFT);
        Assert.assertEquals(Integer.valueOf(2), f.result.highlighted().get());
        pressKey(SceneKey.ARROW_UP);
        Assert.assertEquals(Integer.valueOf(2), f.result.highlighted().get());
        // 连续向下 5 次到 17（行 5）→ 自动滚动到 (5-2+1)*stride = 288
        for (int i = 0; i < 5; i++) {
            pressKey(SceneKey.ARROW_DOWN);
        }
        Assert.assertEquals(Integer.valueOf(17), f.result.highlighted().get());
        rt.flush();
        Assert.assertEquals(4 * STRIDE, f.result.scrollSignal().get().intValue());
    }

    @Test
    public void controlledHighlightDelegatesWrites() {
        Signal<Integer> external = Signal.create(Integer.valueOf(-1));
        AtomicInteger callback = new AtomicInteger(-2);
        Props props = new Props(Signal.create(items(20)), COLUMNS, CELL_W, CELL_H, GAP_X, GAP_Y,
                VISIBLE_ROWS, Signal.create(Boolean.TRUE), item -> { }, external,
                callback::set);
        Result result = SceneVirtualGrid.create(rt, props);
        sceneRoot.appendChild(result.root());
        rt.flush();
        layoutAndBridge();
        rt.requestFocus(result.viewport());
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("导航经 onHighlightChange 回写", 0, callback.get());
        Assert.assertSame("显示信号即受控外部信号", external, result.highlighted());
    }

    @Test
    public void autoColumnsDerivedFromViewportWidth() {
        // columns<=0：viewport 宽 400 → (400+8)/(64+8)=5 列
        Props props = Props.of(Signal.create(items(20)), 0, CELL_W, CELL_H, GAP_X, GAP_Y,
                VISIBLE_ROWS, Signal.create(Boolean.TRUE), item -> { });
        Result result = SceneVirtualGrid.create(rt, props);
        sceneRoot.appendChild(result.root());
        rt.flush();
        layoutAndBridge();
        WindowModel model = result.windowModel().get();
        Assert.assertEquals(5, model.columns());
        Assert.assertEquals(4, model.totalRows());
    }

    // ==================== 动态可见行数覆盖 ====================

    @Test
    public void dynamicVisibleRowsOverrideDrivesViewportAndWindowModel() {
        Signal<List<Item>> itemsSignal = Signal.create(items(20));
        Signal<Integer> override = Signal.create(Integer.valueOf(5));
        Props props = Props.of(itemsSignal, COLUMNS, CELL_W, CELL_H, GAP_X, GAP_Y, VISIBLE_ROWS,
                Signal.create(Boolean.TRUE), item -> { });
        Result result = SceneVirtualGrid.create(rt, props, override);
        sceneRoot.appendChild(result.root());
        rt.flush();
        layoutAndBridge();

        Assert.assertEquals("5 可见行驱动 viewport 高度", 5 * CELL_H + 4 * GAP_Y,
                result.viewport().getPreferredHeight());
        WindowModel model = result.windowModel().get();
        Assert.assertEquals("maxStartRow 用生效行数", 7 - 5, model.maxStartRow());
        Assert.assertEquals("mountedRows 含 overscan", 6, model.mountedRows());
        Assert.assertEquals("maxScrollPx 用生效行数", 7 * STRIDE - (5 * CELL_H + 4 * GAP_Y),
                model.maxScrollPx());

        // 收缩到 3 行：viewport 高度与窗口数学全部重算
        override.set(Integer.valueOf(3));
        rt.flush();
        layoutAndBridge();
        Assert.assertEquals("3 可见行驱动 viewport 高度", 3 * CELL_H + 2 * GAP_Y,
                result.viewport().getPreferredHeight());
        model = result.windowModel().get();
        Assert.assertEquals(7 - 3, model.maxStartRow());
        Assert.assertEquals(4, model.mountedRows());

        // 滚动超出新 maxScrollPx 时回夹
        result.scrollSignal().set(Integer.valueOf(9999));
        rt.flush();
        Assert.assertEquals("收缩后滚动回夹到新 maxScrollPx",
                7 * STRIDE - (3 * CELL_H + 2 * GAP_Y), result.scrollSignal().get().intValue());
    }

    @Test
    public void dynamicVisibleRowsClampsNonPositiveToOne() {
        Signal<List<Item>> itemsSignal = Signal.create(items(20));
        Signal<Integer> override = Signal.create(Integer.valueOf(0));
        Props props = Props.of(itemsSignal, COLUMNS, CELL_W, CELL_H, GAP_X, GAP_Y, VISIBLE_ROWS,
                Signal.create(Boolean.TRUE), item -> { });
        Result result = SceneVirtualGrid.create(rt, props, override);
        sceneRoot.appendChild(result.root());
        rt.flush();
        layoutAndBridge();
        Assert.assertEquals("0 夹取到 1 行", CELL_H, result.viewport().getPreferredHeight());
        Assert.assertEquals(7 - 1, result.windowModel().get().maxStartRow());

        override.set(Integer.valueOf(-3));
        rt.flush();
        Assert.assertEquals("负值夹取到 1 行", CELL_H, result.viewport().getPreferredHeight());
    }

    @Test
    public void nullVisibleRowsOverrideMatchesLegacyPath() {
        Signal<List<Item>> itemsSignal = Signal.create(items(20));
        Props props = Props.of(itemsSignal, COLUMNS, CELL_W, CELL_H, GAP_X, GAP_Y, VISIBLE_ROWS,
                Signal.create(Boolean.TRUE), item -> { });
        Result result = SceneVirtualGrid.create(rt, props, null);
        sceneRoot.appendChild(result.root());
        rt.flush();
        layoutAndBridge();
        Assert.assertEquals("无 override 时 viewport 高度同旧路径",
                VISIBLE_ROWS * CELL_H + (VISIBLE_ROWS - 1) * GAP_Y,
                result.viewport().getPreferredHeight());
        WindowModel model = result.windowModel().get();
        Assert.assertEquals(7 - VISIBLE_ROWS, model.maxStartRow());
        Assert.assertEquals(VISIBLE_ROWS + 1, model.mountedRows());
        Assert.assertEquals(7 * STRIDE - (VISIBLE_ROWS * CELL_H + (VISIBLE_ROWS - 1) * GAP_Y),
                model.maxScrollPx());
    }

    // ==================== 纯函数导航/窗口数学边界 ====================

    @Test
    public void navBoundariesClampAndWrap() {
        // 首项/末项夹取
        Assert.assertEquals(0, SceneVirtualGridNav.navigate(0, SceneKey.ARROW_LEFT, 3, 20));
        Assert.assertEquals(19, SceneVirtualGridNav.navigate(19, SceneKey.ARROW_RIGHT, 3, 20));
        Assert.assertEquals(19, SceneVirtualGridNav.navigate(19, SceneKey.ARROW_DOWN, 3, 20));
        Assert.assertEquals(0, SceneVirtualGridNav.navigate(0, SceneKey.ARROW_UP, 3, 20));
        // 行边界自然换行
        Assert.assertEquals(3, SceneVirtualGridNav.navigate(2, SceneKey.ARROW_RIGHT, 3, 20));
        Assert.assertEquals(2, SceneVirtualGridNav.navigate(3, SceneKey.ARROW_LEFT, 3, 20));
        // 上下移动保持列，末行缺列夹取到末项
        Assert.assertEquals(8, SceneVirtualGridNav.navigate(5, SceneKey.ARROW_DOWN, 3, 20));
        Assert.assertEquals(6, SceneVirtualGridNav.navigate(4, SceneKey.ARROW_DOWN, 3, 7));
        Assert.assertEquals(3, SceneVirtualGridNav.navigate(6, SceneKey.ARROW_UP, 3, 7));
        // 无高亮进入 0；非方向键不变；空列表 -1
        Assert.assertEquals(0, SceneVirtualGridNav.navigate(-1, SceneKey.ARROW_UP, 3, 20));
        Assert.assertEquals(5, SceneVirtualGridNav.navigate(5, SceneKey.ENTER, 3, 20));
        Assert.assertEquals(-1, SceneVirtualGridNav.navigate(5, SceneKey.ARROW_DOWN, 3, 0));
    }

    @Test
    public void scrollTargetForRowSemantics() {
        // 上方 → 滚到该行；下方 → 滚到 row-visibleRows+1；视野内 → -1
        Assert.assertEquals(0, SceneVirtualGridNav.scrollTargetForRow(0, 2, 2, 7, STRIDE));
        Assert.assertEquals(4 * STRIDE, SceneVirtualGridNav.scrollTargetForRow(5, 0, 2, 7, STRIDE));
        Assert.assertEquals(5 * STRIDE, SceneVirtualGridNav.scrollTargetForRow(6, 0, 2, 7, STRIDE));
        Assert.assertEquals(-1, SceneVirtualGridNav.scrollTargetForRow(3, 2, 2, 7, STRIDE));
        Assert.assertEquals(-1, SceneVirtualGridNav.scrollTargetForRow(2, 2, 2, 7, STRIDE));
    }

    @Test
    public void windowStartRowForScrollClamps() {
        Assert.assertEquals(0, SceneVirtualGridNav.windowStartRowForScroll(-10, STRIDE, 5));
        Assert.assertEquals(2, SceneVirtualGridNav.windowStartRowForScroll(2 * STRIDE + 4, STRIDE, 5));
        Assert.assertEquals(5, SceneVirtualGridNav.windowStartRowForScroll(9999, STRIDE, 5));
        Assert.assertEquals(0, SceneVirtualGridNav.windowStartRowForScroll(100, STRIDE, 0));
    }

    // ==================== 输入注入辅助 ====================

    private void click(SceneNode node) {
        int[] c = centerOf(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1]);
        routePointer(ScenePointerAction.BUTTON_UP, c[0], c[1]);
        rt.flush();
    }

    private void pressKey(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, 0, 0, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
    }

    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
    }

    private int[] centerOf(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            throw new IllegalStateException("节点未布局或零尺寸，无法取中心: " + box);
        }
        return new int[]{box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2};
    }
}
