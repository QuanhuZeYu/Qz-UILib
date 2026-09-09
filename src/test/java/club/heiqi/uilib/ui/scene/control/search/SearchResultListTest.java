package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
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
 * {@link SearchResultList} 单元测试（无上限普通列表 + 滚动条 + 渲染分级回退）。
 *
 * <p>覆盖：全量行挂载（无上限、无虚拟化）、stackHost 滚动条结构、空列表、点击激活 + 高亮回写、
 * hover 回调、ARROW_* 高亮移动与边界 clamp（全量范围）、ENTER 激活、禁用无副作用、自动列数、
 * viewport 可滚动且 maxScrollY 与内容高一致、数据收缩滚动回夹、
 * 渲染分级 UNRENDERABLE → 单元图标回退占位样式。</p>
 *
 * <p>液态玻璃迁移（G13）覆盖：底座 viewport = 主题 GROUP 配方逐项、单元零 BACKDROP/零圆角边框且
 * 整树每颗表面只采样一次滤镜、选中 0x59 &gt; hover 0x1F 与禁用清空的轻量覆盖口径、
 * 重排/重绑后选中与 hover 不串项、键盘选择跨动态列表更新指向正确项、
 * 主题切换只重派生（节点身份/结果数据/effect 数不变）、卸载回收绑定。</p>
 */
public class SearchResultListTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

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
        paintEngine = new ScenePaintEngine(measurer);
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

    private static Item item(int key) {
        return new Item(Integer.valueOf(key), null, "item" + key);
    }

    /** 按给定 key 序列构造数据源（重排/替换用例用，key 相同 → keyed reconcile 复用节点）。 */
    private static List<Item> sequence(int... keys) {
        List<Item> items = new ArrayList<>();
        for (int k : keys) {
            items.add(item(k));
        }
        return items;
    }

    /** 测试夹具：itemsSignal + highlightSignal + enabledSignal + 回调记录 + 列表 Result + 挂载句柄。 */
    private final class Fixture {
        final Signal<List<Item>> itemsSignal;
        final Signal<Integer> highlightSignal;
        final Signal<Boolean> enabledSignal;
        final List<Object> activated = new ArrayList<>();
        final List<Item> hovered = new ArrayList<>();
        final SearchResultList.Result result;
        final MountHandle handle;

        Fixture(int itemCount) {
            this(items(itemCount), COLUMNS);
        }

        Fixture(List<Item> sourceItems, int columns) {
            this(sourceItems, columns, null);
        }

        /**
         * @param sourceItems 初始数据源
         * @param columns     列数（&lt;=0 自动推导）
         * @param pageTheme   来源主题信号；非 null 时经 {@code SceneThemes.withTheme} 构建
         */
        Fixture(List<Item> sourceItems, int columns, Signal<SceneTheme> pageTheme) {
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
            this.handle = rt.mount(sceneRoot, () -> {
                SceneNode wrapper = new SceneNode();
                wrapper.setPreferredHeight(200);
                Runnable build = () -> holder[0] = SearchResultList.create(rt, props);
                if (pageTheme != null) {
                    SceneThemes.withTheme(pageTheme, build);
                } else {
                    build.run();
                }
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

    // ==================== 液态玻璃迁移：底座 GROUP 配方与单元零滤镜 ====================

    /**
     * 默认路径：底座 viewport 逐项 = 主题 {@code GROUP} 配方（经 SceneScrollContainer 默认路径的
     * {@code SceneSurfaceBinder} 独占绑定）；结果单元只做轻量底色覆盖——零 BACKDROP、零圆角、
     * 零边框宽，默认透明；整树滤镜采样预算 = 底座恰好一条 BACKDROP（每颗表面只采样一次）；
     * 标签取主题次要前景；图位圆角与占位底色属物品图像渲染协议，保持原样不改色。
     */
    @Test
    public void defaultViewportFollowsGroupRecipeAndCellsCarryNoBackdrop() {
        Fixture f = new Fixture(8);
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
            for (int c = 0; c < 4; c++) {
                SceneNode cell = f.cell(r, c);
                Assert.assertEquals("单元[" + r + "," + c + "] 默认透明", 0, cell.getBackgroundColor());
                Assert.assertNull("单元[" + r + "," + c + "] 不装滤镜", cell.getBackdrop());
                Assert.assertEquals("单元[" + r + "," + c + "] 不写圆角", 0, cell.getCornerRadius());
                Assert.assertEquals("单元[" + r + "," + c + "] 不写边框宽", 0, cell.getBorderWidth());
                Assert.assertEquals("单元[" + r + "," + c + "] 标签取主题次要前景",
                        SceneThemes.DEFAULT.mutedForeground(), labelOf(cell).getTextColor());
                // 物品图像/缩略图渲染协议：图位圆角与占位底色不改（items 全部无图片源）
                Assert.assertEquals("单元[" + r + "," + c + "] 图位圆角属渲染协议",
                        SceneChromeTokens.RADIUS_SM, iconOf(cell).getCornerRadius());
                Assert.assertEquals("单元[" + r + "," + c + "] 占位底色不改",
                        SearchResultList.DEFAULT_PLACEHOLDER_COLOR, iconOf(cell).getBackgroundColor());
            }
        }

        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();
        Assert.assertEquals("底座自身恰好一条 BACKDROP", 1, backdropCount(vp));
        Assert.assertEquals("整树 BACKDROP = 底座 1（每颗表面只采样一次）",
                1, countType(plan.getCommands(), PaintCommandType.BACKDROP));
        for (int i = 0; i < 4; i++) {
            Assert.assertEquals("单元自身零 BACKDROP", 0, backdropCount(f.cell(0, i)));
        }
    }

    // ==================== 液态玻璃迁移：单元轻量状态覆盖口径 ====================

    /**
     * 单元底色轻量覆盖沿 {@code SceneAutocomplete} 已验收 alpha 口径：选中（主题选区背景 0x59）
     * &gt; hover（主题 accent 0x1F）&gt; 透明，选中明显强于 hover；禁用时底色清空（不伪造可用
     * 选中态）、标签取禁用前景，重新启用后正确恢复；全程单元不装滤镜。
     */
    @Test
    public void cellStatesFollowSelectionHoverAndDisabledPriority() {
        Fixture f = new Fixture(8);
        SceneTheme dark = SceneThemes.DEFAULT;
        int selected = (0x59 << 24) | (dark.selectionBackground() & 0x00FFFFFF);
        int hoveredTint = (0x1F << 24) | (dark.accent() & 0x00FFFFFF);
        Assert.assertNotEquals("选中与 hover 必须是不同强度档", selected, hoveredTint);

        SceneNode cell0 = f.cell(0, 0);
        SceneNode cell2 = f.cell(0, 2);
        Assert.assertEquals("前置：默认透明", 0, cell0.getBackgroundColor());

        f.highlightSignal.set(0);
        rt.flush();
        Assert.assertEquals("选中 = 主题选区背景轻量覆盖", selected, cell0.getBackgroundColor());

        int[] p2 = centerOf(cell2);
        routePointer(ScenePointerAction.MOVE, p2[0], p2[1]);
        Assert.assertEquals("hover = 主题 accent 轻量覆盖", hoveredTint, cell2.getBackgroundColor());
        Assert.assertNull("hover 单元仍不装滤镜", cell2.getBackdrop());
        Assert.assertEquals("hover 他项不得影响选中档", selected, cell0.getBackgroundColor());
        // 选中优先于 hover：把指针移到已选中单元上，底色仍是选中档
        int[] p0 = centerOf(cell0);
        routePointer(ScenePointerAction.MOVE, p0[0], p0[1]);
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

    // ==================== 液态玻璃迁移：复用节点重绑不串态 ====================

    /**
     * 动态结果列表重绑/重排后选中与 hover 不串项：同 key 单元节点被复用并换位置时，
     * 选中档必须跟随「当前下标 == highlighted」的权威派生——进入选中位的新单元亮、
     * 离开选中位的旧单元即刻清除（正反两个方向）；hover 由指针命中驱动、不随节点迁移残留。
     */
    @Test
    public void reboundCellsDoNotLeakSelectionOrHoverAcrossItems() {
        Fixture f = new Fixture(sequence(0, 1, 2, 3), COLUMNS);
        SceneTheme dark = SceneThemes.DEFAULT;
        int selected = (0x59 << 24) | (dark.selectionBackground() & 0x00FFFFFF);
        int hoveredTint = (0x1F << 24) | (dark.accent() & 0x00FFFFFF);

        f.highlightSignal.set(0);
        rt.flush();
        SceneNode key0Cell = f.cell(0, 0);
        SceneNode key1Cell = f.cell(0, 1);
        Assert.assertEquals("前置：key0 选中", selected, key0Cell.getBackgroundColor());
        Assert.assertEquals("前置：key1 透明", 0, key1Cell.getBackgroundColor());

        // 重排：key1 顶到选中位。keyed reconcile 复用同一节点，选中态不得留在旧位置/旧项上。
        f.itemsSignal.set(sequence(1, 0, 2, 3));
        rt.flush();
        layoutAndBridge();
        Assert.assertSame("key1 单元节点按 key 复用", key1Cell, f.cell(0, 0));
        Assert.assertSame("key0 单元节点按 key 复用", key0Cell, f.cell(0, 1));
        Assert.assertEquals("进入选中位的 key1 亮起", selected, key1Cell.getBackgroundColor());
        Assert.assertEquals("离开选中位的 key0 无选中残留", 0, key0Cell.getBackgroundColor());

        // 反方向：换回来，残留同样不得出现。
        f.itemsSignal.set(sequence(0, 1, 2, 3));
        rt.flush();
        layoutAndBridge();
        Assert.assertEquals("key0 回到选中位重新亮起", selected, key0Cell.getBackgroundColor());
        Assert.assertEquals("key1 让位后无选中残留", 0, key1Cell.getBackgroundColor());

        // hover 不串项：指针在选中位时数据重排换项，hover 只属于当前命中项，不随旧节点迁移。
        f.highlightSignal.set(-1);
        rt.flush();
        f.hovered.clear();
        int[] p0 = centerOf(key0Cell);
        routePointer(ScenePointerAction.MOVE, p0[0], p0[1]);
        Assert.assertEquals("前置：key0 获得 hover", hoveredTint, key0Cell.getBackgroundColor());
        Assert.assertEquals(Integer.valueOf(0), f.hovered.get(f.hovered.size() - 1).key());

        f.itemsSignal.set(sequence(1, 2, 3, 0));
        rt.flush();
        layoutAndBridge();
        int[] pNew0 = centerOf(f.cell(0, 0));
        routePointer(ScenePointerAction.MOVE, pNew0[0], pNew0[1]);
        Assert.assertEquals("重排后同位置的新项（key1）获得 hover",
                hoveredTint, f.cell(0, 0).getBackgroundColor());
        Assert.assertEquals("key0 已迁到末位，不得带走 hover", 0, key0Cell.getBackgroundColor());
        Assert.assertSame("key0 节点按 key 复用并迁移到末位", key0Cell, f.cell(0, 3));
        Assert.assertEquals("hover 回调指向当前命中项",
                Integer.valueOf(1), f.hovered.get(f.hovered.size() - 1).key());
    }

    /**
     * 键盘选择跨动态列表更新仍指向正确项：highlighted 是「完整列表下标」权威，数据源整体替换
     * （搜索更新）后 ENTER/箭头继续解析到新列表同一位置的项，激活回调携带的 key 与数据一致。
     */
    @Test
    public void keyboardSelectionPointsToCorrectItemAcrossDynamicListUpdates() {
        Fixture f = new Fixture(8);
        rt.requestFocus(f.vp());
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals(Integer.valueOf(0), f.highlightSignal.get());

        // 模拟搜索更新：结果整体替换为 key 10..17（同下标语义）
        f.itemsSignal.set(sequence(10, 11, 12, 13, 14, 15, 16, 17));
        rt.flush();
        layoutAndBridge();

        pressKey(SceneKey.ENTER);
        Assert.assertEquals("ENTER 激活更新后下标 0 的项", Integer.valueOf(10),
                f.activated.get(f.activated.size() - 1));
        pressKey(SceneKey.ARROW_RIGHT);
        Assert.assertEquals(Integer.valueOf(1), f.highlightSignal.get());
        pressKey(SceneKey.ENTER);
        Assert.assertEquals("箭头导航后 ENTER 指向更新后的下标 1 项", Integer.valueOf(11),
                f.activated.get(f.activated.size() - 1));
        // 选中视觉同样跟随：下标 1 亮、下标 0 不残留
        SceneTheme dark = SceneThemes.DEFAULT;
        int selected = (0x59 << 24) | (dark.selectionBackground() & 0x00FFFFFF);
        Assert.assertEquals(selected, f.cell(0, 1).getBackgroundColor());
        Assert.assertEquals(0, f.cell(0, 0).getBackgroundColor());
    }

    // ==================== 液态玻璃迁移：主题切换与卸载回收 ====================

    /**
     * 主题切换只重派生：底座染色/圆角/滤镜材质、选中/hover 档、标签前景全部随来源主题更新；
     * 节点身份不变、结果数据不丢、effect 数不增长；切换后滤镜采样预算不变（底座恰好一条
     * BACKDROP、单元零 BACKDROP）。
     */
    @Test
    public void themeSwitchReDerivesWithoutRebuildingNodesOrGrowingEffects() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        Fixture f = new Fixture(sequence(0, 1, 2, 3), COLUMNS, pageTheme);
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkGroup = dark.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle lightGroup = light.surface(SceneTheme.Role.GROUP);
        Assert.assertNotEquals("两档 GROUP idle 必须不同，否则切换不传播",
                darkGroup.getIdle(), lightGroup.getIdle());

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

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        rt.flush();

        Assert.assertEquals("底座染色随主题更新", lightGroup.getIdle().getTint(), vp.getBackgroundColor());
        Assert.assertEquals("底座圆角随主题更新", lightGroup.getCornerRadius(), vp.getCornerRadius());
        Assert.assertEquals("底座滤镜材质随主题更新",
                lightGroup.getBackdrop().getEffect().getMaterial(),
                vp.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("选中档随主题更新",
                tint(light.selectionBackground(), 0x59), cell0.getBackgroundColor());
        Assert.assertEquals("标签前景随主题更新", light.mutedForeground(), label0.getTextColor());

        Assert.assertSame("主题切换不重建 viewport", vp, f.vp());
        Assert.assertSame("主题切换不重建单元节点", cell0, f.cell(0, 0));
        Assert.assertSame("主题切换不重建标签节点", label0, labelOf(f.cell(0, 0)));
        Assert.assertEquals("主题切换不丢结果数据", dataBefore, f.itemsSignal.get().size());
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());

        // 浅色主题下 hover 档同步更新，且采样预算不变
        int[] p1 = centerOf(f.cell(0, 1));
        routePointer(ScenePointerAction.MOVE, p1[0], p1[1]);
        Assert.assertEquals("浅色主题 hover = 浅色 accent 轻量覆盖",
                tint(light.accent(), 0x1F), f.cell(0, 1).getBackgroundColor());
        Assert.assertNull("切换后单元仍不装滤镜", cell0.getBackdrop());
        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();
        Assert.assertEquals("主题切换后底座仍恰好一条 BACKDROP", 1, backdropCount(vp));
        Assert.assertEquals("主题切换后整树滤镜预算不变",
                1, countType(plan.getCommands(), PaintCommandType.BACKDROP));
    }

    /**
     * 卸载回收：dispose 后外观绑定 effect 回到基线，主题更新不再写入旧节点
     * （底座与单元底色均冻结）。
     */
    @Test
    public void unmountReleasesSurfaceBindingsAndStopsWrites() {
        int before = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        Fixture f = new Fixture(sequence(0, 1, 2, 3), COLUMNS, pageTheme);
        f.highlightSignal.set(0);
        rt.flush();
        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > before);

        SceneNode vp = f.vp();
        SceneNode cell0 = f.cell(0, 0);
        int viewportBg = vp.getBackgroundColor();
        int cellBg = cell0.getBackgroundColor();
        Assert.assertEquals("前置：单元已上色", tint(SceneThemes.DEFAULT.selectionBackground(), 0x59), cellBg);

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
