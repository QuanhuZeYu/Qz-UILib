package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.testkit.SceneTestEnvironments;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Item;
import club.heiqi.uilib.ui.scene.control.search.SearchResultList;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 窗口切片不变式测试（ADR §3.2 inv-W1..W4，C7 对账点名的类）。
 *
 * <p>覆盖两层：</p>
 * <ol>
 *   <li><b>纯函数层</b>：{@link SceneGridWindow#compute} 的参数扫掠 —— inv-W1（{@code windowOffset ==
 *       windowStartRow * columns}）、inv-W3（offset 非负且按整行对齐）、inv-W4（{@code totalRows}
 *       由 {@code totalItems} 派生而非切片长度）、inv-W2（每行切片长度 = {@code min(columns, 剩余总量)}）；</li>
 *   <li><b>控件层</b>：{@link SearchResultList} 的 {@code pageProvider} 形态 —— 宿主拿到的
 *       {@code WindowRequest} 必须由控件按 WindowModel 产出（宿主不得自算 offset），
 *       滚动后 offset/limit 跟随窗口首行与挂载行数。</li>
 * </ol>
 *
 * <p>这是「窗口数学唯一实现」的回归契约：任何第二份 {@code windowStartRow * columns} 推导都会在此变红。</p>
 */
public class SceneGridWindowSliceTest {

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int CELL_W = 64;
    private static final int CELL_H = 64;
    private static final int GAP_X = 8;
    private static final int GAP_Y = 8;
    private static final int VISIBLE_ROWS = 3;

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = SceneTestEnvironments.runtime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 纯函数层：inv-W1 / W2 / W3 / W4 参数扫掠 ====================

    /**
     * 参数扫掠：任意 (itemCount, columns, visibleRows, overscan, stride, scroll, viewportH) 组合下，
     * WindowModel 的切片不变量恒成立。
     */
    @Test
    public void windowSliceInvariantsHoldAcrossParameterSweep() {
        int[] itemCounts = { 0, 1, 7, 64, 4096, 5000 };
        int[] columns = { 1, 2, 4, 7 };
        int[] visibleRows = { 1, 3, 8 };
        int[] overscans = { 0, 1, 2 };
        int[] strides = { 1, 24, 72 };
        int[] scrolls = { 0, 1, 48, 5000, 1_000_000 };
        int checked = 0;
        for (int itemCount : itemCounts) {
            for (int column : columns) {
                for (int rows : visibleRows) {
                    for (int overscan : overscans) {
                        for (int stride : strides) {
                            for (int scroll : scrolls) {
                                SceneGridWindow.WindowModel model = SceneGridWindow.compute(
                                        itemCount, column, rows, overscan, stride, scroll, CANVAS_HEIGHT);
                                String where = "(" + itemCount + "," + column + "," + rows + ","
                                        + overscan + "," + stride + "," + scroll + ")";
                                int expectedRows = itemCount == 0 ? 0
                                        : (itemCount + column - 1) / column;

                                assertEquals("inv-W1：windowOffset = windowStartRow * columns " + where,
                                        model.windowStartRow() * model.columns(), model.windowOffset());
                                assertEquals("inv-W3：offset 非负 " + where,
                                        0, Math.max(0, -model.windowOffset()));
                                assertEquals("inv-W3：offset 按整行对齐 " + where,
                                        0, model.windowOffset() % model.columns());
                                assertEquals("inv-W4：totalRows 由 totalItems 派生 " + where,
                                        expectedRows, model.totalRows());
                                assertTrue("窗口不得越出总行数 " + where,
                                        model.windowStartRow() + model.mountedRows() <= model.totalRows());
                                assertEquals("窗口行区间数量 = 挂载行数 " + where,
                                        model.mountedRows(), model.rows().size());

                                int expectedOffset = model.windowStartRow() * model.columns();
                                for (int index = 0; index < model.rows().size(); index++) {
                                    SceneGridWindow.RowRange range = model.rows().get(index);
                                    int firstIndex = expectedOffset + index * model.columns();
                                    assertEquals("inv-W2：行首下标连续且按整行推进 " + where,
                                            firstIndex, range.firstIndex());
                                    assertEquals("inv-W2：行切片长度 = min(columns, 剩余总量) " + where,
                                            Math.min(model.columns(), Math.max(0, itemCount - firstIndex)),
                                            range.count());
                                }
                                checked++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue("扫掠规模必须真实（> 1000 组合）: " + checked, checked > 1000);
    }

    // ==================== 控件层：请求由控件按 WindowModel 产出 ====================

    /**
     * {@code pageProvider} 收到的 WindowRequest 必须与 {@code Result.windowModel()} 同源：
     * offset == windowStartRow * columns（inv-W1）、limit == 挂载行数 * columns、
     * 切片长度 == min(limit, totalItems - offset)（inv-W2）；滚动后同步推进。
     */
    @Test
    public void pageProviderRequestIsRowAlignedAndMatchesWindowModel() {
        Fixture fixture = new Fixture(4096, 4);
        assertRequestMatchesModel(fixture, "首帧");

        routeScrollAt(fixture.viewport(), -1_000_000);
        rt.flush();
        layoutAndBridge();
        SceneGridWindow.WindowModel bottom = fixture.windowModel();
        assertEquals("滚到底：窗口贴住末行", bottom.totalRows(),
                bottom.windowStartRow() + bottom.mountedRows());
        assertRequestMatchesModel(fixture, "滚到底");

        routeScrollAt(fixture.viewport(), 1_000_000);
        rt.flush();
        layoutAndBridge();
        assertEquals("滚回顶部：窗口首行归零", 0, fixture.windowModel().windowStartRow());
        assertRequestMatchesModel(fixture, "滚回顶部");
    }

    /** 多个 (总量, 列数) 组合下，控件产出的 offset 恒按整行对齐且与模型一致（列数变化不破坏对齐）。 */
    @Test
    public void requestOffsetStaysRowAlignedAcrossColumnsAndTotals() {
        int[][] cases = { { 1, 1 }, { 3, 2 }, { 4096, 4 }, { 4095, 7 }, { 5000, 3 } };
        for (int[] testCase : cases) {
            Fixture fixture = new Fixture(testCase[0], testCase[1]);
            assertRequestMatchesModel(fixture, "(" + testCase[0] + "," + testCase[1] + ")");
        }
    }

    private void assertRequestMatchesModel(Fixture fixture, String where) {
        SceneGridWindow.WindowModel model = fixture.windowModel();
        SearchResultList.WindowRequest request = fixture.lastRequest();
        assertNotNull("必须向 pageProvider 拉取切片: " + where, request);
        assertEquals("inv-W1：offset == windowStartRow * columns（" + where + "）",
                model.windowStartRow() * model.columns(), request.offset());
        assertEquals("inv-W3：offset 按整行对齐（" + where + "）",
                0, request.offset() % model.columns());
        assertEquals("limit = 挂载行数 * 列数（" + where + "）",
                model.mountedRows() * model.columns(), request.limit());
        int expectedSlice = Math.min(request.limit(), model.totalItems() - request.offset());
        assertEquals("inv-W2：切片长度 = min(limit, totalItems - offset)（" + where + "）",
                expectedSlice, fixture.mountedItems());
        assertEquals("切片全局起点由控件给出（" + where + "）",
                model.windowOffset(), request.offset());
    }

    // ==================== 夹具与驱动 ====================

    private final class Fixture {
        private final List<SearchResultList.WindowRequest> requests =
                new ArrayList<SearchResultList.WindowRequest>();
        private final int totalItems;
        private final SearchResultList.Result result;

        Fixture(int totalItems, int columns) {
            this.totalItems = totalItems;
            SearchResultList.PageProvider provider = request -> {
                requests.add(request);
                return new SearchResultList.WindowPage(slice(request.offset(), request.limit()), totalItems);
            };
            Signal<Integer> highlight = Signal.create(Integer.valueOf(-1));
            SearchResultList.Props props = new SearchResultList.Props(
                    Signal.<List<Item>>create(new ArrayList<Item>()), columns,
                    CELL_W, CELL_H, GAP_X, GAP_Y, Signal.create(Boolean.TRUE),
                    item -> { }, highlight, highlight::set, item -> { },
                    provider, totalItems, 0, VISIBLE_ROWS, null);
            SearchResultList.Result[] holder = new SearchResultList.Result[1];
            rt.mount(sceneRoot, () -> {
                SceneNode wrapper = new SceneNode();
                wrapper.setPreferredHeight(200);
                holder[0] = SearchResultList.create(rt, props);
                wrapper.appendChild(holder[0].root());
                return wrapper;
            });
            result = holder[0];
            rt.flush();
            layoutAndBridge();
            layoutAndBridge();
        }

        private List<Item> slice(int offset, int limit) {
            int available = Math.max(0, totalItems - offset);
            ArrayList<Item> page = new ArrayList<Item>(Math.min(limit, available));
            for (int index = 0; index < Math.min(limit, available); index++) {
                page.add(new Item(Integer.valueOf(offset + index), null, "item" + (offset + index)));
            }
            return page;
        }

        SceneNode viewport() {
            return result.viewport();
        }

        SceneGridWindow.WindowModel windowModel() {
            return result.windowModel().get();
        }

        SearchResultList.WindowRequest lastRequest() {
            return requests.isEmpty() ? null : requests.get(requests.size() - 1);
        }

        int mountedItems() {
            return countMountedItems(viewport());
        }
    }

    private static int countMountedItems(SceneNode viewport) {
        SceneNode rows = viewport.__getChildren().get(0).__getChildren().get(1);
        int count = 0;
        for (SceneNode row : rows.__getChildren()) {
            count += row.__getChildren().size();
        }
        return count;
    }

    private void layoutAndBridge() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    private void routeScrollAt(SceneNode node, int wheelDelta) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int x = box.getX() + box.getWidth() / 2;
        int y = box.getY() + box.getHeight() / 2;
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, x, y,
                SceneMouseButton.NONE, wheelDelta, 0, 0, false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
    }
}
