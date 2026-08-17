package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Item;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
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
 * {@link SearchResultList} 单元测试（无上限普通列表 + 滚动条 + 渲染分级回退）。
 *
 * <p>覆盖：全量行挂载（无上限、无虚拟化）、stackHost 滚动条结构、空列表、点击激活 + 高亮回写、
 * hover 回调、ARROW_* 高亮移动与边界 clamp（全量范围）、ENTER 激活、禁用无副作用、自动列数、
 * viewport 可滚动且 maxScrollY 与内容高一致、数据收缩滚动回夹、
 * 渲染分级 UNRENDERABLE → 单元图标回退占位样式。</p>
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
    private static final int STRIDE = CELL_H + GAP_Y;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        ItemRenderTierRegistry.resetForTests();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
        ItemRenderTierRegistry.resetForTests();
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

    /** 测试夹具：itemsSignal + highlightSignal + enabledSignal + 回调记录 + 列表 Result。 */
    private final class Fixture {
        final Signal<List<Item>> itemsSignal;
        final Signal<Integer> highlightSignal;
        final Signal<Boolean> enabledSignal;
        final List<Object> activated = new ArrayList<>();
        final List<Item> hovered = new ArrayList<>();
        final SearchResultList.Result result;

        Fixture(int itemCount) {
            this(items(itemCount), COLUMNS);
        }

        Fixture(List<Item> sourceItems, int columns) {
            this.itemsSignal = Signal.create(sourceItems);
            this.highlightSignal = Signal.create(Integer.valueOf(-1));
            this.enabledSignal = Signal.create(Boolean.TRUE);
            SearchResultList.Props props = new SearchResultList.Props(
                    itemsSignal, columns, CELL_W, CELL_H, GAP_X, GAP_Y,
                    enabledSignal, item -> activated.add(item.key()), highlightSignal,
                    highlightSignal::set, item -> hovered.add(item));
            // 在 mount 作用域内构建，建立 Owner（确保 bind/forEach/on/监听器归属并随组件回收）。
            // scrollable viewport 需要确定高的父链（生产环境由面板卡片提供），测试夹具包固定高宿主。
            SearchResultList.Result[] holder = new SearchResultList.Result[1];
            rt.mount(sceneRoot, () -> {
                SceneNode wrapper = new SceneNode();
                wrapper.setPreferredHeight(200);
                holder[0] = SearchResultList.create(rt, props);
                wrapper.appendChild(holder[0].root());
                return wrapper;
            });
            this.result = holder[0];
            rt.flush();
            layoutAndBridge();
        }

        SceneNode root() {
            return result.root();
        }

        SceneNode vp() {
            return result.viewport();
        }

        /** 行列表容器（viewport 唯一子节点，keyed reconcile 的目标容器）。 */
        SceneNode rowsContainer() {
            return vp().__getChildren().get(0);
        }

        /** 第 rowIndex 行。 */
        SceneNode row(int rowIndex) {
            return rowsContainer().__getChildren().get(rowIndex);
        }

        /** 第 rowIndex 行第 colIndex 个单元。 */
        SceneNode cell(int rowIndex, int colIndex) {
            return row(rowIndex).__getChildren().get(colIndex);
        }
    }

    // ==================== 全量行挂载与滚动条结构 ====================

    @Test
    public void mountsAllRowsWithoutCap() {
        Fixture f = new Fixture(500);
        SceneNode vp = f.vp();
        Assert.assertTrue("viewport 可滚动", vp.isScrollable());
        // 500 项 / 4 列 = 125 行全量挂载（无上限、无虚拟化）
        Assert.assertEquals(125, f.rowsContainer().__getChildren().size());
        // viewport 唯一子节点 = rowsContainer（无溢出提示/锚点）
        Assert.assertEquals(1, vp.__getChildren().size());
        // 首行含 4 个单元
        Assert.assertEquals(4, f.row(0).__getChildren().size());
    }

    @Test
    public void stackHostCarriesViewportAndScrollbar() {
        Fixture f = new Fixture(500);
        SceneNode root = f.root();
        Assert.assertEquals("stackHost = [viewport, 滚动条列]", 2, root.__getChildren().size());
        Assert.assertSame("第 0 子为可滚动视口", f.vp(), root.__getChildren().get(0));
        SceneNode bar = root.__getChildren().get(1);
        Assert.assertEquals("滚动条默认宽度", SceneScrollbar.DEFAULT_BAR_WIDTH, bar.getPreferredWidth());
        Assert.assertTrue("视口仍可滚动", f.vp().isScrollable());
    }

    @Test
    public void emptyItemsMountsNoRows() {
        Fixture f = new Fixture(0);
        Assert.assertEquals(0, f.rowsContainer().__getChildren().size());
        Assert.assertEquals(1, f.vp().__getChildren().size());
    }

    // ==================== 点击与高亮回写 ====================

    @Test
    public void clickActivatesItemAndWritesHighlight() {
        Fixture f = new Fixture(100);
        SceneNode cell = f.cell(0, 0);
        click(cell);
        Assert.assertEquals(1, f.activated.size());
        Assert.assertEquals(Integer.valueOf(0), f.activated.get(0));
        Assert.assertEquals(Integer.valueOf(0), f.highlightSignal.get());
    }

    // ==================== hover 回调 ====================

    @Test
    public void hoverCallsOnHoverItemAndNullOnLeave() {
        Fixture f = new Fixture(100);
        SceneNode cell = f.cell(0, 0);
        // 先声明 hovered（懒创建时序契约）
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
        Fixture f = new Fixture(100);
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
        // 末行 clamp：100 项 / 4 列 = 25 行，末行首项 96，再向下不动
        for (int i = 0; i < 24; i++) {
            pressKey(SceneKey.ARROW_DOWN);
        }
        Assert.assertEquals(Integer.valueOf(96), f.highlightSignal.get());
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals(Integer.valueOf(96), f.highlightSignal.get());
    }

    @Test
    public void enterActivatesHighlighted() {
        Fixture f = new Fixture(100);
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
        Fixture f = new Fixture(100);
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
        // columns <= 0：viewport 宽 = 400 - 滚动条 8 → (392+8)/(64+8) = 5 列
        Signal<List<Item>> itemsSignal = Signal.create(items(20));
        Signal<Integer> highlightSignal = Signal.create(Integer.valueOf(-1));
        Signal<Boolean> enabledSignal = Signal.create(Boolean.TRUE);
        SearchResultList.Props props = new SearchResultList.Props(
                itemsSignal, 0, CELL_W, CELL_H, GAP_X, GAP_Y,
                enabledSignal, item -> { }, highlightSignal, highlightSignal::set, null);
        SceneNode root = rt.mount(sceneRoot, () -> SearchResultList.create(rt, props).root()).getRoot();
        rt.flush();
        layoutAndBridge();
        // 20 项 / 5 列 = 4 行；首行含 5 个单元
        SceneNode viewport = root.__getChildren().get(0);
        SceneNode rowsContainer = viewport.__getChildren().get(0);
        Assert.assertEquals("按推导列数拆 4 行", 4, rowsContainer.__getChildren().size());
        Assert.assertEquals("首行 5 个单元", 5, rowsContainer.__getChildren().get(0)
                .__getChildren().size());
    }

    // ==================== 滚动几何与数据收缩 ====================

    @Test
    public void maxScrollMatchesContentHeight() {
        // Fixture 包装 200 高 + root/视口 fillParentHeight（对齐外壳接线）：maxScrollY = 内容高 - 可视高。
        Fixture f = new Fixture(500);
        SceneNode vp = f.vp();
        layoutAndBridge();
        int rows = 125;
        // 内容底边：125 行（行高 + marginBottom）
        int contentHeight = rows * (CELL_H + GAP_Y);
        int viewportH = ((LayoutBox) vp.getCachedLayout()).getHeight();
        Assert.assertEquals("viewport 高度 = 包装 200", 200, viewportH);
        Assert.assertTrue("maxScrollY 非负", contentHeight - viewportH >= 0);
        Assert.assertEquals(contentHeight - viewportH, SceneGeometry.maxScrollY(vp));
    }

    @Test
    public void dataShrinkClampsScroll() {
        Fixture f = new Fixture(500);
        // 滚轮向下滚超量（-20000 > maxScrollY 8800）：SceneScrolls handler 内部 clamp 到 maxScrollY
        routeScrollAt(f.vp(), -20000);
        rt.flush();
        Assert.assertEquals(SceneGeometry.maxScrollY(f.vp()), f.vp().getScrollOffsetY());
        // 收缩到 100 项 → 25 行 → 布局完成后 layoutDone 回夹 scroll 到新 maxScrollY
        f.itemsSignal.set(items(100));
        rt.flush();
        layoutAndBridge();
        Assert.assertEquals(25 * STRIDE - 200, f.vp().getScrollOffsetY());
    }

    // ==================== 渲染分级回退 ====================

    @Test
    public void unrenderableItemFallsBackToPlaceholderStyle() {
        // registryKey 契约 = 注册名:meta（如 modid:name:0），条目 key = 注册名（如 modid:name）。
        SceneImageSource brokenImage = new SceneImageSource() {
            @Override
            public String registryKey() {
                return "test:broken:0";
            }
        };
        SceneImageSource okImage = new SceneImageSource() {
            @Override
            public String registryKey() {
                return "test:ok:0";
            }
        };
        List<Item> source = new ArrayList<>();
        source.add(new Item("test:broken", brokenImage, "broken"));
        source.add(new Item("test:ok", okImage, "ok"));
        Fixture f = new Fixture(source, COLUMNS);
        // 平台渲染层把 test:broken:0 分级为不可渲染（三次异常）→ 监听器回写 → 单元回退
        ItemRenderTierRegistry.classify("test:broken:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        ItemRenderTierRegistry.classify("test:broken:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        ItemRenderTierRegistry.classify("test:broken:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        rt.flush();
        SceneNode brokenIcon = f.cell(0, 0).__getChildren().get(0);
        Assert.assertEquals("不可渲染项回退占位底色", SearchResultList.DEFAULT_PLACEHOLDER_COLOR,
                brokenIcon.getBackgroundColor());
        Assert.assertNull("不可渲染项不再挂图片源", brokenIcon.getImageSource());
        // 未标记条目保持原图片源
        SceneNode okIcon = f.cell(0, 1).__getChildren().get(0);
        Assert.assertEquals(0x00000000, okIcon.getBackgroundColor());
        Assert.assertSame(okImage, okIcon.getImageSource());
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

    private void routeScrollAt(SceneNode node, int wheelDelta) {
        int[] center = centerOf(node);
        InputFrameBuilder fb = new InputFrameBuilder(center[0], center[1]);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, center[0], center[1],
                SceneMouseButton.NONE, wheelDelta, 0, 0, false, false, false, false, 1000L));
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
