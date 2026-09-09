package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
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
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintFragment;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link SceneVirtualGrid} 单元测试。
 *
 * <p>覆盖：行级虚拟化挂载数、滚动驱动窗口计算、数据收缩回夹、空列表、点击激活、
 * 四向键盘导航边界、受控高亮、自动列数、禁用不激活，以及 {@link SceneVirtualGridNav}
 * 纯函数导航/窗口数学边界。</p>
 *
 * <p>液态玻璃迁移（G14）覆盖：网格底座 viewport = 主题 GROUP 配方逐项（G07 先例自持一颗）、
 * 复用单元轻量口径（零 BACKDROP/零圆角边框/elevation -1、选中 0x59 &gt; hover 0x1F 与禁用清空）、
 * 重排与整批换代后节点复用不串色、滚动窗口回收与选中重派生（虚拟化不退化）、
 * 主题切换只重派生（节点身份/effect 数/滤镜预算不变，回收态跨切换仍正确）、
 * runtime 默认主题装配路径跟随、卸载回收绑定、图像渲染协议不改色反向钉住。</p>
 */
public class SceneVirtualGridTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

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
        paintEngine = new ScenePaintEngine(measurer);
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

    /** 按给定 key 序列构造数据源（重排/换代用例用，key 相同 → keyed reconcile 复用节点）。 */
    private static List<Item> sequence(int... keys) {
        List<Item> items = new ArrayList<>();
        for (int k : keys) {
            items.add(new Item(Integer.valueOf(k), null, "item" + k));
        }
        return items;
    }

    /**
     * 外观测试夹具：真实装配路径——{@code rt.mount} builder 内构建（可选 {@code withTheme}
     * 局部主题作用域），受控高亮 + 显式 enabled 信号，供轻量覆盖/主题切换/卸载回收用例使用。
     */
    private final class VisualFixture {
        final Signal<List<Item>> itemsSignal;
        final Signal<Integer> highlightSignal;
        final Signal<Boolean> enabledSignal;
        final List<Object> activated = new ArrayList<>();
        final Result result;
        final MountHandle handle;

        VisualFixture(List<Item> sourceItems) {
            this(sourceItems, null);
        }

        /**
         * @param sourceItems 初始数据源
         * @param pageTheme   来源主题信号；非 null 时经 {@code SceneThemes.withTheme} 构建
         */
        VisualFixture(List<Item> sourceItems, Signal<SceneTheme> pageTheme) {
            itemsSignal = Signal.create(sourceItems);
            highlightSignal = Signal.create(Integer.valueOf(-1));
            enabledSignal = Signal.create(Boolean.TRUE);
            Props props = new Props(itemsSignal, COLUMNS, CELL_W, CELL_H, GAP_X, GAP_Y,
                    VISIBLE_ROWS, enabledSignal, item -> activated.add(item.key()),
                    highlightSignal, highlightSignal::set, null);
            Result[] holder = new Result[1];
            handle = rt.mount(sceneRoot, () -> {
                SceneNode wrapper = SceneNode.column();
                wrapper.setPreferredHeight(VISIBLE_ROWS * CELL_H + (VISIBLE_ROWS - 1) * GAP_Y);
                Runnable build = () -> holder[0] = SceneVirtualGrid.create(rt, props);
                if (pageTheme != null) {
                    SceneThemes.withTheme(pageTheme, build);
                } else {
                    build.run();
                }
                wrapper.appendChild(holder[0].root());
                return wrapper;
            });
            result = holder[0];
            rt.flush();
            layoutAndBridge();
        }

        SceneNode vp() {
            return result.viewport();
        }

        /** 行列表容器（viewport 第 2 子 = [topSpacer, rowsContainer, bottomSpacer]）。 */
        SceneNode rowsContainer() {
            return result.viewport().__getChildren().get(1);
        }

        /** 窗口内第 windowRow 行（非全表行号）。 */
        SceneNode row(int windowRow) {
            return rowsContainer().__getChildren().get(windowRow);
        }

        /** 窗口内第 windowRow 行第 col 个单元。 */
        SceneNode cell(int windowRow, int col) {
            return row(windowRow).__getChildren().get(col);
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
                callback::set, null);
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

    // ==================== 液态玻璃迁移：网格底座 GROUP 配方与复用单元零滤镜 ====================

    /**
     * 默认路径：网格底座 viewport 逐项 = 主题 {@code GROUP} 配方（G07 先例自持一颗，
     * {@code SceneSurfaceBinder} 六项独占）；复用单元轻量口径——零 BACKDROP、零圆角、零边框宽、
     * elevation -1、默认透明；整树滤镜预算 = 底座恰好一条 BACKDROP（行/单元各层零采样）；
     * 标签取主题次要前景；图位圆角与占位底色属物品图像渲染协议，保持原样不改色。
     */
    @Test
    public void defaultViewportCarriesGroupSurfaceAndCellsAreLightweight() {
        VisualFixture f = new VisualFixture(items(8));
        SceneSurfaceStyle group = SceneThemes.DEFAULT.surface(SceneTheme.Role.GROUP);
        Assert.assertNotNull("前置：GROUP 配方自带滤镜", group.getBackdrop());

        SceneNode vp = f.vp();
        Assert.assertEquals("底座染色 = GROUP 配方 idle tint",
                group.getIdle().getTint(), vp.getBackgroundColor());
        Assert.assertEquals("底座圆角 = GROUP 配方", group.getCornerRadius(), vp.getCornerRadius());
        Assert.assertEquals("底座边框宽 = GROUP 配方", group.getBorderWidth(), vp.getBorderWidth());
        Assert.assertEquals("底座缘色 = GROUP 配方 idle edge",
                group.getIdle().getEdge(), vp.getBorderColor());
        Assert.assertEquals("底座实体高度 = GROUP 配方 idle elevation",
                group.getIdle().getElevation(), vp.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull("底座默认带液态玻璃滤镜", vp.getBackdrop());
        Assert.assertEquals("底座滤镜模糊半径 = 配方",
                group.getBackdrop().getBlurRadius(), vp.getBackdrop().getBlurRadius());
        Assert.assertEquals("底座滤镜材质 = 配方",
                group.getBackdrop().getEffect().getMaterial(), vp.getBackdrop().getEffect().getMaterial());

        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                SceneNode cell = f.cell(r, c);
                Assert.assertEquals("单元[" + r + "," + c + "] 默认透明", 0, cell.getBackgroundColor());
                Assert.assertNull("单元[" + r + "," + c + "] 不装滤镜", cell.getBackdrop());
                Assert.assertEquals("单元[" + r + "," + c + "] 不写圆角", 0, cell.getCornerRadius());
                Assert.assertEquals("单元[" + r + "," + c + "] 不写边框宽", 0, cell.getBorderWidth());
                Assert.assertEquals("单元[" + r + "," + c + "] elevation 保持 -1（普通绘制）",
                        -1.0F, cell.__getSurfaceElevation(), 0.0001F);
                Assert.assertEquals("单元[" + r + "," + c + "] 标签取主题次要前景",
                        SceneThemes.DEFAULT.mutedForeground(), labelOf(cell).getTextColor());
                // 物品图像渲染协议（契约 §4.1「物品图像不改色」+ §7.3）：图位圆角与占位底色不改
                Assert.assertEquals("单元[" + r + "," + c + "] 图位圆角属渲染协议",
                        SceneChromeTokens.RADIUS_SM, iconOf(cell).getCornerRadius());
                Assert.assertEquals("单元[" + r + "," + c + "] 占位底色不改",
                        SceneVirtualGrid.DEFAULT_PLACEHOLDER_COLOR, iconOf(cell).getBackgroundColor());
            }
        }

        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();
        Assert.assertEquals("底座自身恰好一条 BACKDROP", 1, backdropCount(vp));
        Assert.assertEquals("整树 BACKDROP = 底座 1（复用行零滤镜，每颗表面只采样一次）",
                1, countType(plan.getCommands(), PaintCommandType.BACKDROP));
        Assert.assertEquals("行容器零 BACKDROP", 0, backdropCount(f.rowsContainer()));
        Assert.assertEquals("行节点零 BACKDROP", 0, backdropCount(f.row(0)));
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals("单元自身零 BACKDROP", 0, backdropCount(f.cell(0, i)));
        }
    }

    // ==================== 液态玻璃迁移：复用单元轻量状态覆盖口径 ====================

    /**
     * 单元底色轻量覆盖沿 {@code SceneAutocomplete}/{@code SearchResultList} 已验收 alpha 口径：
     * 选中（主题选区背景 0x59）&gt; hover（主题 accent 0x1F）&gt; 透明，选中明显强于 hover；
     * 禁用时底色清空（不伪造可用选中态）、标签取禁用前景，重新启用后正确恢复；全程不装滤镜。
     */
    @Test
    public void cellStatesFollowSelectionHoverAndDisabledPriority() {
        VisualFixture f = new VisualFixture(sequence(0, 1, 2, 3));
        SceneTheme dark = SceneThemes.DEFAULT;
        int selected = tint(dark.selectionBackground(), 0x59);
        int hoveredTint = tint(dark.accent(), 0x1F);
        Assert.assertNotEquals("选中与 hover 必须是不同强度档", selected, hoveredTint);

        SceneNode cell0 = f.cell(0, 0);
        SceneNode cell2 = f.cell(0, 2);
        Assert.assertEquals("前置：默认透明", 0, cell0.getBackgroundColor());

        f.highlightSignal.set(0);
        rt.flush();
        Assert.assertEquals("选中 = 主题选区背景轻量覆盖", selected, cell0.getBackgroundColor());

        hoverAt(cell2);
        Assert.assertEquals("hover = 主题 accent 轻量覆盖", hoveredTint, cell2.getBackgroundColor());
        Assert.assertNull("hover 单元仍不装滤镜", cell2.getBackdrop());
        Assert.assertEquals("hover 他项不得影响选中档", selected, cell0.getBackgroundColor());
        // 选中优先于 hover：把指针移到已选中单元上，底色仍是选中档
        hoverAt(cell0);
        Assert.assertEquals("选中档强于 hover 档", selected, cell0.getBackgroundColor());
        Assert.assertTrue("高亮 alpha 明显高于 hover alpha",
                (cell0.getBackgroundColor() >>> 24) > (hoveredTint >>> 24));
        Assert.assertEquals("移开后 hover 单元回落透明", 0, cell2.getBackgroundColor());

        // 禁用：底色清空、标签禁用前景（disabled > selected/hover 的轻量口径）
        f.enabledSignal.set(Boolean.FALSE);
        rt.flush();
        Assert.assertEquals("禁用时选中底色清空", 0, cell0.getBackgroundColor());
        Assert.assertEquals("禁用时 hover 底色清空", 0, cell2.getBackgroundColor());
        Assert.assertEquals("禁用时标签取主题禁用前景",
                dark.disabledForeground(), labelOf(cell0).getTextColor());

        // 重新启用：只由数据态决定外观，无禁用残留
        f.enabledSignal.set(Boolean.TRUE);
        rt.flush();
        Assert.assertEquals("重新启用后选中底色恢复", selected, cell0.getBackgroundColor());
        Assert.assertEquals("重新启用后标签回到次要前景",
                dark.mutedForeground(), labelOf(cell0).getTextColor());
        Assert.assertNull("单元全程不装滤镜", cell0.getBackdrop());
    }

    // ==================== 液态玻璃迁移：复用节点重绑不串色 ====================

    /**
     * 虚拟化复用不串态：同 key 单元节点按 key 复用并换位置时，选中档跟随「当前下标 ==
     * highlighted」的实时权威派生——进入选中位的旧节点亮起、离开选中位的旧节点即刻清除
     * （正反两方向），不残留上一项的选中/hover 底色；数据源整批换代后，按 firstIndex 复用的
     * 行节点内挂全新 key 的单元，样式随新数据重派生（标签文本同为证）。
     */
    @Test
    public void recycledCellsAndRowsRebindStyleFromFreshDataWithoutLeaks() {
        VisualFixture f = new VisualFixture(sequence(0, 1, 2, 3, 4, 5));
        SceneTheme dark = SceneThemes.DEFAULT;
        int selected = tint(dark.selectionBackground(), 0x59);

        f.highlightSignal.set(1);
        rt.flush();
        SceneNode key1Cell = f.cell(0, 1);
        SceneNode key2Cell = f.cell(0, 2);
        Assert.assertEquals("前置：key1 选中", selected, key1Cell.getBackgroundColor());
        Assert.assertEquals("前置：key2 透明", 0, key2Cell.getBackgroundColor());

        // 重排：key2 顶到选中位（下标 1）。keyed reconcile 按 key 复用同一批节点。
        f.itemsSignal.set(sequence(0, 2, 1, 4, 3, 5));
        rt.flush();
        layoutAndBridge();
        Assert.assertSame("key2 单元节点按 key 复用", key2Cell, f.cell(0, 1));
        Assert.assertSame("key1 单元节点按 key 复用并迁移", key1Cell, f.cell(0, 2));
        Assert.assertEquals("进入选中位的 key2 亮起", selected, key2Cell.getBackgroundColor());
        Assert.assertEquals("离开选中位的 key1 无选中色残留", 0, key1Cell.getBackgroundColor());

        // 反方向换回：残留同样不得出现。
        f.itemsSignal.set(sequence(0, 1, 2, 3, 4, 5));
        rt.flush();
        layoutAndBridge();
        Assert.assertEquals("key1 回到选中位重新亮起", selected, key1Cell.getBackgroundColor());
        Assert.assertEquals("key2 让位后无选中残留", 0, key2Cell.getBackgroundColor());

        // 整批换代（选择器切换数据源）：行节点按 firstIndex 复用，单元全部换 key；
        // 样式与文本必须随新数据重派生，不吃陈旧快照。
        SceneNode reusedRow = f.row(0);
        f.itemsSignal.set(sequence(10, 11, 12, 13, 14, 15, 16, 17));
        f.highlightSignal.set(4);
        rt.flush();
        layoutAndBridge();
        Assert.assertSame("行节点按 firstIndex 复用", reusedRow, f.row(0));
        Assert.assertNotSame("换代后单元是全新 key 的新节点", key1Cell, f.cell(0, 0));
        Assert.assertEquals("新数据下选中项（下标 4 = key14）亮起",
                selected, f.cell(1, 1).getBackgroundColor());
        Assert.assertEquals("同轮次未选中单元透明", 0, f.cell(0, 0).getBackgroundColor());
        Assert.assertEquals("复用行内标签随新数据重派生", "item10",
                labelOf(f.cell(0, 0)).getText());
        Assert.assertEquals("换代后仍只挂载可见窗口（2+overscan=3 行）", 3,
                f.result.windowModel().get().mountedRows());
    }

    // ==================== 液态玻璃迁移：滚动窗口回收与虚拟化保持 ====================

    /**
     * 滚动驱动行级回收（复用不退化为全量渲染）：20 项 7 行全程只挂载 visibleRows+overscan=3 行；
     * 选中项滚出窗口后其节点被回收，窗口内所有单元透明（无选中色残留），高亮信号不丢；
     * 滚回后新挂载的 key8 单元底色由「key 下标 == highlighted」即时重派生（非旧节点残留）。
     */
    @Test
    public void scrolledWindowRecyclesRowsAndSelectionReDerivesOnReturn() {
        VisualFixture f = new VisualFixture(items(20));
        SceneTheme dark = SceneThemes.DEFAULT;
        int selected = tint(dark.selectionBackground(), 0x59);

        f.highlightSignal.set(8);
        rt.flush();
        Assert.assertEquals("前置：行 2 末元（key8）选中", selected, f.cell(2, 2).getBackgroundColor());

        // 滚出选中行：窗口行键 0,3,6 → 12,15,18，全部换键 → 旧行/单元回收、新节点按新数据构建
        f.result.scrollSignal().set(Integer.valueOf(4 * STRIDE));
        rt.flush();
        layoutAndBridge();
        WindowModel model = f.result.windowModel().get();
        Assert.assertEquals(4, model.windowStartRow());
        Assert.assertEquals("虚拟化保持：仍只挂 3 行（非 7 行全量）", 3, model.mountedRows());
        Assert.assertEquals(3, f.rowsContainer().__getChildren().size());
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < f.row(r).__getChildren().size(); c++) {
                Assert.assertEquals("窗口内无任何选中色残留（key8 已回收）",
                        0, f.cell(r, c).getBackgroundColor());
            }
        }
        Assert.assertEquals("滚动不丢高亮", Integer.valueOf(8), f.result.highlighted().get());

        // 滚回：key8 单元是新构建节点，选中档即时重派生
        f.result.scrollSignal().set(Integer.valueOf(0));
        rt.flush();
        layoutAndBridge();
        Assert.assertEquals(3, f.rowsContainer().__getChildren().size());
        Assert.assertEquals("滚回后 key8 新单元底色即时重派生为选中档",
                selected, f.cell(2, 2).getBackgroundColor());
        Assert.assertNull("回收重建的单元仍零滤镜", f.cell(2, 2).getBackdrop());
    }

    // ==================== 液态玻璃迁移：主题切换与装配路径 ====================

    /**
     * 局部主题（{@code SceneThemes.withTheme} + {@code rt.mount} 真实装配路径）light→切 dark
     * 后全树重派生：底座染色/圆角/滤镜材质、选中档、hover 档、标签前景全部随来源主题更新；
     * 节点身份不变、数据不丢、effect 数不增长、滤镜采样预算不变（底座 1 颗、单元 0 颗）；
     * 切换后按 key 复用的回收态仍正确（换序即换选中，无旧档残留）。
     */
    @Test
    public void themeSwitchReDerivesGridWithoutRebuildingNodesAndStaysCorrectAcrossRecycle() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        VisualFixture f = new VisualFixture(sequence(0, 1, 2, 3), pageTheme);
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkGroup = dark.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle lightGroup = light.surface(SceneTheme.Role.GROUP);
        Assert.assertNotEquals("两档 GROUP idle 必须不同，否则切换不传播",
                darkGroup.getIdle(), lightGroup.getIdle());
        Assert.assertNotEquals("两档选区背景必须不同", dark.selectionBackground(), light.selectionBackground());
        Assert.assertNotEquals("两档次要前景必须不同", dark.mutedForeground(), light.mutedForeground());

        f.highlightSignal.set(0);
        rt.flush();
        SceneNode vp = f.vp();
        SceneNode cell0 = f.cell(0, 0);
        SceneNode label0 = labelOf(cell0);
        int dataBefore = f.itemsSignal.get().size();
        Assert.assertEquals("前置：底座 = 深色 GROUP idle tint",
                darkGroup.getIdle().getTint(), vp.getBackgroundColor());
        Assert.assertEquals("前置：选中档 = 深色选区背景",
                tint(dark.selectionBackground(), 0x59), cell0.getBackgroundColor());
        Assert.assertEquals("前置：标签 = 深色次要前景", dark.mutedForeground(), label0.getTextColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        rt.flush();

        Assert.assertEquals("换主题→底座染色变", lightGroup.getIdle().getTint(), vp.getBackgroundColor());
        Assert.assertEquals("底座圆角随主题更新", lightGroup.getCornerRadius(), vp.getCornerRadius());
        Assert.assertEquals("底座滤镜材质随主题更新",
                lightGroup.getBackdrop().getEffect().getMaterial(),
                vp.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("换主题→选中档变",
                tint(light.selectionBackground(), 0x59), cell0.getBackgroundColor());
        Assert.assertEquals("换主题→标签前景变", light.mutedForeground(), label0.getTextColor());

        Assert.assertSame("主题切换不重建 viewport", vp, f.vp());
        Assert.assertSame("主题切换不重建单元节点", cell0, f.cell(0, 0));
        Assert.assertSame("主题切换不重建标签节点", label0, labelOf(f.cell(0, 0)));
        Assert.assertEquals("主题切换不丢数据", dataBefore, f.itemsSignal.get().size());
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());

        // 浅色档 hover 生效且采样预算不变
        hoverAt(f.cell(0, 1));
        Assert.assertEquals("浅色主题 hover = 浅色 accent 轻量覆盖",
                tint(light.accent(), 0x1F), f.cell(0, 1).getBackgroundColor());
        Assert.assertNull("切换后单元仍不装滤镜", cell0.getBackdrop());
        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();
        Assert.assertEquals("主题切换后整树滤镜预算不变",
                1, countType(plan.getCommands(), PaintCommandType.BACKDROP));

        // 回收态跨切换仍正确：切换后换序，key 复用节点按浅色档重派生选中
        movePointer(1, CANVAS_HEIGHT - 1);
        f.itemsSignal.set(sequence(1, 0, 2, 3));
        rt.flush();
        layoutAndBridge();
        Assert.assertEquals("key1 进入选中位按浅色档亮起",
                tint(light.selectionBackground(), 0x59), f.cell(0, 0).getBackgroundColor());
        Assert.assertEquals("key0 让位后无旧档残留", 0, cell0.getBackgroundColor());
    }

    /**
     * 默认工厂路径跟随 runtime 级默认主题（{@code SceneThemes.install}，零 withTheme 配置）：
     * 不传任何样式参数构建网格，换 runtime 主题后底座与选中档同批变色（P-04「换主题→颜色变」）。
     */
    @Test
    public void installedRuntimeThemeDrivesDefaultFactoryPath() {
        Signal<SceneTheme> runtimeTheme = Signal.create(SceneTheme.liquidGlassDark());
        SceneThemes.install(rt, runtimeTheme);
        VisualFixture f = new VisualFixture(sequence(0, 1, 2, 3));
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("两档 GROUP idle 必须不同",
                dark.surface(SceneTheme.Role.GROUP).getIdle(),
                light.surface(SceneTheme.Role.GROUP).getIdle());

        f.highlightSignal.set(0);
        rt.flush();
        Assert.assertEquals("前置：runtime 默认档 = 深色 GROUP",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(),
                f.vp().getBackgroundColor());
        Assert.assertEquals(tint(dark.selectionBackground(), 0x59), f.cell(0, 0).getBackgroundColor());

        runtimeTheme.set(light);
        rt.flush();
        Assert.assertEquals("换 runtime 主题→底座变色",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(),
                f.vp().getBackgroundColor());
        Assert.assertEquals("换 runtime 主题→选中档变色",
                tint(light.selectionBackground(), 0x59), f.cell(0, 0).getBackgroundColor());
        Assert.assertEquals("换 runtime 主题→标签前景变色",
                light.mutedForeground(), labelOf(f.cell(0, 0)).getTextColor());
    }

    // ==================== 液态玻璃迁移：卸载回收 ====================

    /**
     * 卸载回收：{@code MountHandle.dispose()} 后外观绑定 effect 回到基线，主题更新不再写入
     * 旧节点（底座与单元底色均冻结）。
     */
    @Test
    public void unmountReleasesSurfaceBindingsAndStopsWrites() {
        int before = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        VisualFixture f = new VisualFixture(sequence(0, 1, 2, 3), pageTheme);
        f.highlightSignal.set(0);
        rt.flush();
        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > before);

        SceneNode vp = f.vp();
        SceneNode cell0 = f.cell(0, 0);
        int viewportBg = vp.getBackgroundColor();
        int cellBg = cell0.getBackgroundColor();
        Assert.assertEquals("前置：单元已上色",
                tint(SceneThemes.DEFAULT.selectionBackground(), 0x59), cellBg);

        f.handle.dispose();
        Assert.assertEquals("卸载后外观绑定 effect 应回收到基线",
                before, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        rt.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧底座", viewportBg, vp.getBackgroundColor());
        Assert.assertEquals("卸载后主题更新不再写入旧单元", cellBg, cell0.getBackgroundColor());
    }

    // ==================== 外观断言辅助 ====================

    /** 保留色 RGB、替换 alpha 通道（轻量覆盖口径，与被测实现同源）。 */
    private static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** 单元内图标槽（第 0 子）。 */
    private static SceneNode iconOf(SceneNode cell) {
        return cell.__getChildren().get(0);
    }

    /** 单元内标签节点（第 1 子，夹具项全部带 label）。 */
    private static SceneNode labelOf(SceneNode cell) {
        return cell.__getChildren().get(1);
    }

    /** 节点自身 PaintFragment 内的 BACKDROP 命令数；无 fragment（无绘制内容）视为 0。 */
    private static int backdropCount(SceneNode node) {
        Object cached = node.getCachedPaint();
        if (!(cached instanceof PaintFragment)) {
            return 0;
        }
        int count = 0;
        for (PaintCommand command : ((PaintFragment) cached).getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    /** PaintPlan 中指定类型的命令数。 */
    private static int countType(List<PaintCommand> commands, PaintCommandType type) {
        int count = 0;
        for (PaintCommand command : commands) {
            if (command.getType() == type) {
                count++;
            }
        }
        return count;
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

    /** hover 驱动 MOVE：路由后 flush（外观绑定经响应式 flush 生效；既有 routePointer 语义不动）。 */
    private void movePointer(int x, int y) {
        routePointer(ScenePointerAction.MOVE, x, y);
        rt.flush();
    }

    /** hover 到节点中心并 flush。 */
    private void hoverAt(SceneNode node) {
        int[] c = centerOf(node);
        movePointer(c[0], c[1]);
    }

    private int[] centerOf(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            throw new IllegalStateException("节点未布局或零尺寸，无法取中心: " + box);
        }
        return new int[]{box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2};
    }
}
