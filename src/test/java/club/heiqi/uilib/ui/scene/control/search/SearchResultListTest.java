package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Item;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Result;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.WindowModel;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGridNav;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * {@link SearchResultList} 单元测试（虚拟化网格薄封装）。
 *
 * <p>覆盖：行级虚拟化挂载数（可见行 + overscan）、空列表、点击激活 + 高亮回写、hover 回调
 * （进入回写 item、移出回写 null）、ARROW_* 导航与边界 clamp、ENTER 激活、禁用无副作用、
 * 自动列数、滚动驱动窗口与数据收缩回夹、动态可见行数（visibleRowsOverride）重算视口高。</p>
 */
public class SearchResultListTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int COLUMNS = 4;
    private static final int CELL_W = 64;
    private static final int CELL_H = 64;
    private static final int GAP_X = 8;
    private static final int GAP_Y = 8;
    private static final int VISIBLE_ROWS = 3;
    private static final int STRIDE = CELL_H + GAP_Y;
    /** viewport 高闭式：{@code rows*cellH + (rows-1)*gapY}。 */
    private static final int VIEWPORT_H = VISIBLE_ROWS * CELL_H + (VISIBLE_ROWS - 1) * GAP_Y;

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

    private Fixture fixture(int itemCount) {
        Fixture fixture = new Fixture(itemCount);
        sceneRoot.appendChild(fixture.result.root());
        rt.flush();
        layoutAndBridge();
        return fixture;
    }

    /** 测试夹具：itemsSignal + 高亮/启用信号 + 回调记录 + 虚拟网格 Result。 */
    private final class Fixture {
        final Signal<List<Item>> itemsSignal;
        final Signal<Integer> highlightSignal;
        final Signal<Boolean> enabledSignal;
        final List<Object> activated = new ArrayList<>();
        final List<Item> hovered = new ArrayList<>();
        final Result result;

        Fixture(int itemCount) {
            this(itemCount, COLUMNS, VISIBLE_ROWS, null);
        }

        Fixture(int itemCount, int columns, int visibleRows, ReadableSignal<Integer> visibleRowsOverride) {
            itemsSignal = Signal.create(items(itemCount));
            highlightSignal = Signal.create(Integer.valueOf(-1));
            enabledSignal = Signal.create(Boolean.TRUE);
            SearchResultList.Props props = new SearchResultList.Props(
                    itemsSignal, columns, CELL_W, CELL_H, GAP_X, GAP_Y, visibleRows,
                    enabledSignal, item -> activated.add(item.key()), highlightSignal,
                    highlightSignal::set, item -> hovered.add(item), visibleRowsOverride);
            result = SearchResultList.create(rt, props);
        }

        SceneNode vp() {
            return result.viewport();
        }

        /** 行列表容器（viewport 子节点 = [topSpacer, rowsContainer, bottomSpacer]）。 */
        SceneNode rowsContainer() {
            return vp().__getChildren().get(1);
        }

        /** 第 rowIndex 行（相对已挂载窗口）。 */
        SceneNode row(int rowIndex) {
            return rowsContainer().__getChildren().get(rowIndex);
        }

        /** 第 rowIndex 行第 colIndex 个单元。 */
        SceneNode cell(int rowIndex, int colIndex) {
            return row(rowIndex).__getChildren().get(colIndex);
        }
    }

    // ==================== 行级虚拟化 ====================

    @Test
    public void mountsOnlyVisibleWindowRowsWithOverscan() {
        Fixture f = fixture(500);
        WindowModel model = f.result.windowModel().get();
        Assert.assertEquals(4, model.columns());
        Assert.assertEquals(500, model.totalItems());
        Assert.assertEquals(125, model.totalRows());
        Assert.assertEquals(0, model.windowStartRow());
        // 可见 3 行 + overscan 1 行 = 挂载 4 行
        Assert.assertEquals(4, model.mountedRows());
        Assert.assertEquals(4, f.rowsContainer().__getChildren().size());
        // viewport = [topSpacer, rowsContainer, bottomSpacer]
        Assert.assertEquals(3, f.vp().__getChildren().size());
        Assert.assertEquals(4, f.row(0).__getChildren().size());
        Assert.assertEquals(125 * STRIDE - VIEWPORT_H, model.maxScrollPx());
    }

    @Test
    public void emptyItemsMountsNoRows() {
        Fixture f = fixture(0);
        WindowModel model = f.result.windowModel().get();
        Assert.assertEquals(0, model.totalItems());
        Assert.assertEquals(0, model.totalRows());
        Assert.assertEquals(0, model.mountedRows());
        Assert.assertEquals(0, f.rowsContainer().__getChildren().size());
    }

    // ==================== 点击与高亮回写 ====================

    @Test
    public void clickActivatesItemAndWritesHighlight() {
        Fixture f = fixture(100);
        SceneNode cell = f.cell(0, 0);
        click(cell);
        Assert.assertEquals(1, f.activated.size());
        Assert.assertEquals(Integer.valueOf(0), f.activated.get(0));
        Assert.assertEquals(Integer.valueOf(0), f.highlightSignal.get());
    }

    // ==================== hover 回调 ====================

    @Test
    public void hoverCallsOnHoverItemAndNullOnLeave() {
        Fixture f = fixture(100);
        SceneNode cell = f.cell(0, 0);
        // 先声明 hovered（懒创建时序契约）；挂载时 onCellMount 已声明同一状态
        rt.interactionState(cell).hovered();
        int[] c = centerOf(cell);
        routePointer(ScenePointerAction.MOVE, c[0], c[1]);
        // 挂载时每个单元经 bind 初始求值各回调一次 null（框架语义），hover 进入追加 item
        Assert.assertEquals(Integer.valueOf(0),
                f.hovered.get(f.hovered.size() - 1).key());
        // 移出到视口外（-10,-10 落在根外/远端），hover 置空回写 null
        routePointer(ScenePointerAction.MOVE, -10, -10);
        Assert.assertNull(f.hovered.get(f.hovered.size() - 1));
    }

    // ==================== 键盘导航 ====================

    @Test
    public void arrowKeysMoveHighlightWithClamp() {
        Fixture f = fixture(100);
        rt.requestFocus(f.vp());
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals(Integer.valueOf(0), f.highlightSignal.get());
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals(Integer.valueOf(4), f.highlightSignal.get());
        pressKey(SceneKey.ARROW_UP);
        Assert.assertEquals(Integer.valueOf(0), f.highlightSignal.get());
        // 边界 clamp：首项再向上不动
        pressKey(SceneKey.ARROW_UP);
        Assert.assertEquals(Integer.valueOf(0), f.highlightSignal.get());
        pressKey(SceneKey.ARROW_LEFT);
        Assert.assertEquals(Integer.valueOf(0), f.highlightSignal.get());
    }

    @Test
    public void enterActivatesHighlighted() {
        Fixture f = fixture(100);
        rt.requestFocus(f.vp());
        pressKey(SceneKey.ARROW_DOWN);
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals(Integer.valueOf(4), f.highlightSignal.get());
        pressKey(SceneKey.ENTER);
        Assert.assertEquals(1, f.activated.size());
        Assert.assertEquals(Integer.valueOf(4), f.activated.get(0));
    }

    // ==================== 禁用 ====================

    @Test
    public void disabledIgnoresClickAndKeys() {
        Fixture f = fixture(100);
        f.enabledSignal.set(Boolean.FALSE);
        rt.flush();
        // 点击
        SceneNode cell = f.cell(0, 0);
        click(cell);
        Assert.assertTrue(f.activated.isEmpty());
        // 键盘
        rt.requestFocus(f.vp());
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals(Integer.valueOf(-1), f.highlightSignal.get());
        pressKey(SceneKey.ENTER);
        Assert.assertTrue(f.activated.isEmpty());
    }

    // ==================== 自动列数 ====================

    @Test
    public void autoColumnsDerivedFromViewportWidth() {
        // columns <= 0：viewport 宽 400 -> (400+8)/(64+8) = 5 列
        Fixture f = new Fixture(20, 0, VISIBLE_ROWS, null);
        sceneRoot.appendChild(f.result.root());
        rt.flush();
        layoutAndBridge();
        Assert.assertEquals("推导列数", SceneVirtualGridNav.deriveColumns(400, CELL_W, GAP_X),
                f.result.windowModel().get().columns());
        // 20 项 / 5 列 = 4 行；首行含 5 个单元
        Assert.assertEquals(5, f.row(0).__getChildren().size());
        Assert.assertEquals(4, f.result.windowModel().get().mountedRows());
    }

    // ==================== 滚动驱动窗口与数据收缩 ====================

    @Test
    public void scrollDrivesWindowAndClampsToMaxStartRow() {
        Fixture f = fixture(500);
        f.result.scrollSignal().set(Integer.valueOf(10 * STRIDE));
        rt.flush();
        layoutAndBridge();
        WindowModel model = f.result.windowModel().get();
        Assert.assertEquals(10, model.windowStartRow());
        Assert.assertEquals(40, model.rows().get(0).firstIndex());
        Assert.assertEquals(10 * STRIDE, f.vp().__getChildren().get(0).getPreferredHeight());
        // 超量滚动夹取到最大窗口首行（totalRows - visibleRows = 122）
        f.result.scrollSignal().set(Integer.valueOf(Integer.MAX_VALUE));
        rt.flush();
        layoutAndBridge();
        Assert.assertEquals(122, f.result.windowModel().get().windowStartRow());
        Assert.assertEquals(122 * STRIDE, f.vp().__getChildren().get(0).getPreferredHeight());
    }

    @Test
    public void dataShrinkClampsScroll() {
        Fixture f = fixture(500);
        int maxScroll = 125 * STRIDE - VIEWPORT_H;
        f.result.scrollSignal().set(Integer.valueOf(maxScroll));
        rt.flush();
        Assert.assertEquals(maxScroll, f.result.scrollSignal().get().intValue());
        // 收缩到 100 项 → totalRows=25 → maxScrollPx = 25*STRIDE - VIEWPORT_H = 1592
        f.itemsSignal.set(items(100));
        rt.flush();
        layoutAndBridge();
        Assert.assertEquals(25 * STRIDE - VIEWPORT_H, f.result.scrollSignal().get().intValue());
        Assert.assertEquals(22, f.result.windowModel().get().windowStartRow());
    }

    // ==================== 动态可见行数 ====================

    @Test
    public void visibleRowsOverrideResizesViewportHeight() {
        Signal<Integer> override = Signal.create(Integer.valueOf(VISIBLE_ROWS));
        Fixture f = new Fixture(500, COLUMNS, 2, override);
        sceneRoot.appendChild(f.result.root());
        rt.flush();
        layoutAndBridge();
        LayoutBox initial = (LayoutBox) f.vp().getCachedLayout();
        Assert.assertNotNull(initial);
        Assert.assertEquals(3 * CELL_H + 2 * GAP_Y, initial.getHeight());
        Assert.assertEquals(4, f.result.windowModel().get().mountedRows());
        // 可见行数升到 5 → 视口高 = 5*64 + 4*8 = 352，挂载 6 行
        override.set(Integer.valueOf(5));
        rt.flush();
        layoutAndBridge();
        LayoutBox resized = (LayoutBox) f.vp().getCachedLayout();
        Assert.assertNotNull(resized);
        Assert.assertEquals(5 * CELL_H + 4 * GAP_Y, resized.getHeight());
        Assert.assertEquals(6, f.result.windowModel().get().mountedRows());
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
        rt.flush();
    }

    private int[] centerOf(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            throw new IllegalStateException("节点未布局或零尺寸，无法取中心: " + box);
        }
        return new int[]{box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2};
    }
}
