package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav.CategoryRow;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link CategoryNavPane} 单元测试（G13 液态玻璃迁移口径）。
 *
 * <p>行为基线（必须继续通过）：3 分类渲染与徽章文本格式、点击回调 key / 全部 accept(null)、
 * 选中态随 categoryKey 变化、空态提示、滚动结构、行命中与禁用拦截。</p>
 *
 * <p>主题化验收（导航族口径，契约 §4.1 + G09 裁决的实例化）：</p>
 * <ul>
 *   <li>底座 = {@code TOOLBAR} 配方，六项（background/border/borderWidth/cornerRadius/backdrop/
 *       surfaceElevation）由 {@code SceneSurfaceBinder} 独占；旧 {@code applyPanelChrome}
 *       实色四件套不再出现。</li>
 *   <li>行 = {@code selectableSurface(INDICATOR, selected)} 的<b>轻量状态覆盖</b>（只写背景色）：
 *       选中/悬停/禁用三态取配方档，<b>行零 BACKDROP、行不装滤镜</b>（底座恰好新增 1 颗采样）。</li>
 *   <li>复用/重绑不串状态：换 key 重建的行不带上一项的选中/hover 残留；同 key 换位节点身份不变、
 *       选中跟随 key。</li>
 *   <li>文字取主题 {@code foreground}/{@code mutedForeground}，禁用取 {@code disabledForeground}。</li>
 *   <li>主题切换只重派生：节点身份不变、effect 数不增长；卸载回收全部绑定。</li>
 * </ul>
 */
public class CategoryNavPaneTest {

    private SceneNode sceneRoot;
    private FixedTextMeasurer measurer;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private Signal<List<CategoryRow>> rows;
    private Signal<String> categoryKey;
    private Signal<Boolean> enabled;
    private List<String> selects;
    /** 最近一次 {@link #mountPane} 的挂载句柄（供卸载回收用例使用）。 */
    private MountHandle lastHandle;

    private static final int W = 400;
    private static final int H = 600;
    private static final float EPSILON = 0.0001F;

    /**
     * 库默认主题配方：默认外观唯一来源。断言引用配方值而不是硬编码色号，
     * 主题集中调参（G19）时本类自动跟随。
     */
    private static final SceneSurfaceStyle TOOLBAR =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.TOOLBAR);
    private static final SceneSurfaceStyle INDICATOR =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.INDICATOR);
    /** 行三档：未选中/hover/pressed 取 INDICATOR 配方档。 */
    private static final int ROW_IDLE = INDICATOR.getIdle().getTint();
    private static final int ROW_HOVER = INDICATOR.getHovered().getTint();
    private static final int ROW_PRESSED = INDICATOR.getPressed().getTint();
    private static final int ROW_DISABLED = INDICATOR.getDisabled().getTint();
    /** 选中档：tint RGB 换主题强调色、强度取主题统一选中强度 0x59（选中不只靠透明度）。 */
    private static final int ROW_SELECTED = selectedTint(SceneThemes.DEFAULT.accent());
    private static final int ROW_SELECTED_HOVER = selectedTint(SceneThemes.DEFAULT.accentHover());
    /** 文字前景：行标签=正文、徽章/空态=次要、禁用=disabledForeground。 */
    private static final int FG = SceneThemes.DEFAULT.foreground();
    private static final int FG_MUTED = SceneThemes.DEFAULT.mutedForeground();
    private static final int FG_DISABLED = SceneThemes.DEFAULT.disabledForeground();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
        rows = Signal.create(Collections.<CategoryRow>emptyList());
        categoryKey = Signal.create((String) null);
        enabled = Signal.create(Boolean.TRUE);
        selects = new ArrayList<String>();
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 夹具 ====================

    /** 构造 3 行快照（cat1/cat2 计数 3/5 + 「全部」计数 8；工厂已由主控公开化）。 */
    private static List<CategoryRow> threeCategories() {
        return Arrays.asList(
                CategoryRow.categoryRow("cat1", "分类一", 3),
                CategoryRow.categoryRow("cat2", "分类二", 5),
                CategoryRow.allRow("全部", 8));
    }

    /**
     * 选中配方语义：RGB 换成强调色、alpha 用主题统一选中强度 {@code 0x59}
     * （与 {@code SceneThemes.selectableSurface} / SceneNavListTest 同一口径）。
     */
    private static int selectedTint(int accent) {
        return (0x59 << 24) | (accent & 0x00FFFFFF);
    }

    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int rgbOf(int argb) {
        return argb & 0x00FFFFFF;
    }

    /** 节点子树内的 BACKDROP 命令数（每颗采样滤镜的表面恰好 1 条）。 */
    private static int backdropCount(ScenePaintEngine engine, SceneNode node) {
        PaintPlan plan = engine.paint(node).getPlan();
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    /** 挂载并布局，返回导航根节点（外壳）。外壳 fillParentHeight 依赖确定高的父链，
     *  故包一层固定高度宿主（生产环境由面板卡片提供高度）。挂载句柄记入 {@link #lastHandle}。 */
    private SceneNode mountPane(List<CategoryRow> initial, String emptyLabel) {
        rows.set(initial);
        SceneNode host = mountThemedPane(Signal.create(SceneTheme.liquidGlassDark()), initial, emptyLabel)
                .getRoot();
        return host.__getChildren().get(0);
    }

    /** 在 withTheme 局部主题作用域下挂载（主题切换用例入口），返回 wrapper 的挂载句柄。 */
    private MountHandle mountThemedPane(Signal<SceneTheme> pageTheme,
                                        List<CategoryRow> initial, String emptyLabel) {
        rows.set(initial);
        lastHandle = rt.mount(sceneRoot, () -> {
            SceneNode wrapper = new SceneNode();
            wrapper.setPreferredHeight(300);
            final SceneNode[] holder = new SceneNode[1];
            SceneThemes.withTheme(pageTheme, () -> holder[0] = CategoryNavPane.create(rt,
                    new CategoryNavPane.Props(rows, categoryKey, enabled, selects::add, emptyLabel)));
            wrapper.appendChild(holder[0]);
            return wrapper;
        });
        rt.flush();
        layoutAll();
        return lastHandle;
    }

    private void layoutAll() {
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    /** 结构：nav = [stackHost]；stackHost = [viewport, 滚动条]；行容器 = viewport.children[0]。 */
    private SceneNode rowsContainer(SceneNode nav) {
        return nav.__getChildren().get(0).__getChildren().get(0).__getChildren().get(0);
    }

    /** 第 i 行节点。 */
    private SceneNode rowAt(SceneNode nav, int i) {
        return rowsContainer(nav).__getChildren().get(i);
    }

    /** 第 i 行的标签节点（行第一个孩子）。 */
    private SceneNode labelAt(SceneNode nav, int i) {
        return rowAt(nav, i).__getChildren().get(0);
    }

    /** 第 i 行的数量徽章节点（行第二个孩子）。 */
    private SceneNode badgeAt(SceneNode nav, int i) {
        return rowAt(nav, i).__getChildren().get(1);
    }

    /** 在第 i 行中心注入单个指针帧（MOVE=悬停；BUTTON_DOWN/UP=按下/释放）。 */
    private void pointerAt(SceneNode nav, int i, ScenePointerAction action) {
        route(action, centerOf(rowAt(nav, i)));
    }

    /** 指针移出全部内容区（触发 hover 退出）。 */
    private void pointerAway() {
        route(ScenePointerAction.MOVE, new int[]{W - 2, H - 2});
    }

    private void route(ScenePointerAction action, int[] c) {
        InputFrameBuilder fb = new InputFrameBuilder(c[0], c[1]);
        fb.push(RawInputEvent.ofPointer(action, c[0], c[1], SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
    }

    private void click(SceneNode node) {
        int[] c = centerOf(node);
        InputFrameBuilder fb = new InputFrameBuilder(c[0], c[1]);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, c[0], c[1], SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, c[0], c[1], SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
    }

    private int[] centerOf(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return new int[]{box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2};
    }

    // ==================== 渲染 ====================

    @Test
    public void rendersThreeRowsWithCorrectBadges() {
        SceneNode nav = mountPane(threeCategories(), null);
        SceneNode rowsContainer = rowsContainer(nav);
        Assert.assertEquals("3 个分类渲染 3 行", 3, rowsContainer.__getChildren().size());

        String[] labels = {"分类一", "分类二", "全部"};
        String[] counts = {"3", "5", "8"};
        for (int i = 0; i < 3; i++) {
            SceneNode row = rowsContainer.__getChildren().get(i);
            Assert.assertEquals("标签 " + i, labels[i], row.__getChildren().get(0).getText());
            // 数量徽章格式保持：十进制计数、无千分位/单位后缀
            Assert.assertEquals("徽章 " + i, counts[i], row.__getChildren().get(1).getText());
        }
    }

    // ==================== 点击回调 ====================

    @Test
    public void clickRowAndAllRowInvokeOnSelect() {
        SceneNode nav = mountPane(threeCategories(), null);
        SceneNode rowsContainer = rowsContainer(nav);

        click(rowsContainer.__getChildren().get(0));
        Assert.assertEquals("点击分类行回传 key", Collections.singletonList("cat1"), selects);

        click(rowsContainer.__getChildren().get(2));
        Assert.assertEquals("点击全部行回传 null", Arrays.asList("cat1", null), selects);
    }

    // ==================== 选中态（INDICATOR 轻量档，取代旧 SceneStateColors） ====================

    @Test
    public void selectedBackgroundTracksCategoryKey() {
        SceneNode nav = mountPane(threeCategories(), null);
        SceneNode cat1 = rowAt(nav, 0);
        SceneNode allRow = rowAt(nav, 2);

        Assert.assertEquals("categoryKey 缺省时普通行未选中（INDICATOR idle 档）",
                ROW_IDLE, cat1.getBackgroundColor());
        Assert.assertEquals("categoryKey 缺省时「全部」行选中（强调色系选中档）",
                ROW_SELECTED, allRow.getBackgroundColor());

        categoryKey.set("cat1");
        rt.flush();
        Assert.assertEquals("选中后背景切到强调色选中档", ROW_SELECTED, cat1.getBackgroundColor());
        Assert.assertEquals("选中 RGB = 主题强调色", rgbOf(SceneThemes.DEFAULT.accent()),
                rgbOf(cat1.getBackgroundColor()));
        Assert.assertEquals("选中强度 = 主题统一选中强度 0x59", 0x59, alphaOf(cat1.getBackgroundColor()));
        Assert.assertTrue("选中强度必须高于未选中档（不只靠透明度区分）",
                alphaOf(cat1.getBackgroundColor()) > alphaOf(ROW_IDLE));
        Assert.assertEquals("「全部」行退选回 idle 档", ROW_IDLE, allRow.getBackgroundColor());
        // 旧接缝 SceneStateColors 已随类删除而消失：「不再取旧静态档」此后由编译期保证
        // （引用不存在的类无法通过编译），运行期无对照物可断言。
    }

    // ==================== 空态 ====================

    @Test
    public void emptyRowsShowEmptyLabel() {
        rows.set(threeCategories());
        SceneNode nav = rt.mount(sceneRoot, () ->
                CategoryNavPane.create(rt,
                        new CategoryNavPane.Props(rows, categoryKey, enabled, selects::add, "暂无可用分类")))
                .getRoot();
        rt.flush();
        layoutAll();

        rows.set(Collections.<CategoryRow>emptyList());
        rt.flush();

        // 结构：nav.children[0] = stackHost → children[0] = 视口；
        // 视口 children = [rows容器, 空提示内容, anchor]（show 内容插在 anchor 之前）
        List<SceneNode> viewportChildren = nav.__getChildren().get(0)
                .__getChildren().get(0).__getChildren();
        Assert.assertTrue("空态提示存在", viewportChildren.size() >= 3);
        Assert.assertEquals("空提示文案 = 传入 emptyLabel", "暂无可用分类",
                viewportChildren.get(viewportChildren.size() - 2).getText());
    }

    // ==================== 滚动条结构 ====================

    @Test
    public void stackHostCarriesViewportAndScrollbar() {
        SceneNode nav = mountPane(threeCategories(), null);
        SceneNode stackHost = nav.__getChildren().get(0);
        Assert.assertEquals("stackHost = [viewport, 滚动条列]", 2, stackHost.__getChildren().size());
        Assert.assertTrue("viewport 可滚动", stackHost.__getChildren().get(0).isScrollable());
        Assert.assertEquals("滚动条默认宽度", SceneScrollbar.DEFAULT_BAR_WIDTH,
                stackHost.__getChildren().get(1).getPreferredWidth());
    }

    // ==================== 底座：TOOLBAR 配方六项独占 ====================

    @Test
    public void shellCarriesToolbarRecipeOwnedBySurfaceBinder() {
        SceneNode nav = mountPane(threeCategories(), null);

        Assert.assertEquals("底座背景 = TOOLBAR idle 染色", TOOLBAR.getIdle().getTint(),
                nav.getBackgroundColor());
        Assert.assertNotEquals("底座背景仍非透明可寻址（旧「背景 != 0」语义保持）", 0, nav.getBackgroundColor());
        Assert.assertEquals("底座边框宽 = 配方独占（旧 applyPanelChrome 1px 移除）",
                TOOLBAR.getBorderWidth(), nav.getBorderWidth());
        Assert.assertEquals("底座边框色 = 配方 idle 缘色", TOOLBAR.getIdle().getEdge(), nav.getBorderColor());
        Assert.assertEquals("底座圆角 = 配方独占（不再静态 RADIUS_MD）",
                TOOLBAR.getCornerRadius(), nav.getCornerRadius());
        Assert.assertNotNull("底座默认带液态玻璃滤镜", nav.getBackdrop());
        Assert.assertEquals("底座模糊半径 = 配方", TOOLBAR.getBackdrop().getBlurRadius(),
                nav.getBackdrop().getBlurRadius());
        Assert.assertEquals("底座材质 = 配方", TOOLBAR.getBackdrop().getEffect().getMaterial(),
                nav.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("底座实体高度 = 配方 idle 档", TOOLBAR.getIdle().getElevation(),
                nav.__getSurfaceElevation(), EPSILON);

        // 布局与命中语义不变（主题不得改布局）
        Assert.assertEquals("外壳宽度 NAV_WIDTH", CategoryNavPane.NAV_WIDTH, nav.getPreferredWidth());
        Assert.assertFalse("外壳不参与命中", nav.isHitTestable());
        Assert.assertTrue("外壳裁剪保持（clip 非绑定器属性，组件自持）", nav.isClipChildren());

        // 旧静态外观写入者已删除
        Assert.assertNotEquals("不再取旧实色 BG_DEFAULT", SceneChromeTokens.BG_DEFAULT, nav.getBackgroundColor());
        Assert.assertNotEquals("不再取旧 BORDER_DEFAULT", SceneChromeTokens.BORDER_DEFAULT, nav.getBorderColor());
    }

    // ==================== 滤镜预算：底座 1 颗、行零 BACKDROP ====================

    @Test
    public void baseSamplesFilterOnceAndRowsCarryNoBackdrop() {
        SceneNode nav = mountPane(threeCategories(), null);
        SceneNode stackHost = nav.__getChildren().get(0);

        // 整树相对滚动内容子树恰好新增底座这 1 颗采样（viewport 那颗归 G07 滚动容器自持）
        Assert.assertEquals("底座恰好采样一次滤镜", 1,
                backdropCount(paintEngine, nav) - backdropCount(paintEngine, stackHost));
        Assert.assertNotNull("底座声明滤镜", nav.getBackdrop());

        for (int i = 0; i < 3; i++) {
            SceneNode row = rowAt(nav, i);
            Assert.assertEquals("行[" + i + "] 子树零 BACKDROP（行不装滤镜）", 0,
                    backdropCount(paintEngine, row));
            Assert.assertNull("行[" + i + "] 不声明 backdrop", row.getBackdrop());
            Assert.assertNull("行内标签不采样背景", labelAt(nav, i).getBackdrop());
            Assert.assertNull("行内徽章不采样背景", badgeAt(nav, i).getBackdrop());
            // 轻量覆盖只写背景色：不与底座争其它属性槽（保持普通绘制路径）
            Assert.assertEquals("行[" + i + "] 不写边框宽", 0, row.getBorderWidth());
            Assert.assertEquals("行[" + i + "] 不写圆角", 0, row.getCornerRadius());
            Assert.assertEquals("行[" + i + "] 不绑定实体高度（-1=普通绘制）", -1.0F,
                    row.__getSurfaceElevation(), EPSILON);
        }
    }

    // ==================== 行三态：INDICATOR 轻量档（选中/悬停/禁用） ====================

    @Test
    public void rowStatesFollowIndicatorLightweightTiers() {
        SceneNode nav = mountPane(threeCategories(), null);
        categoryKey.set("cat1");
        rt.flush();
        SceneNode cat1 = rowAt(nav, 0);
        SceneNode cat2 = rowAt(nav, 1);

        Assert.assertEquals("未选中 idle = 配方 idle 档", ROW_IDLE, cat2.getBackgroundColor());

        pointerAt(nav, 1, ScenePointerAction.MOVE);
        Assert.assertEquals("hover = 配方 hovered 档", ROW_HOVER, cat2.getBackgroundColor());
        Assert.assertNotEquals("hover 有可见变化", ROW_IDLE, cat2.getBackgroundColor());

        pointerAt(nav, 1, ScenePointerAction.BUTTON_DOWN);
        Assert.assertEquals("pressed 压过 hovered = 配方 pressed 档", ROW_PRESSED, cat2.getBackgroundColor());
        pointerAt(nav, 1, ScenePointerAction.BUTTON_UP);
        Assert.assertEquals("释放回 hovered 档", ROW_HOVER, cat2.getBackgroundColor());
        pointerAway();
        Assert.assertEquals("移开回 idle 档", ROW_IDLE, cat2.getBackgroundColor());

        Assert.assertEquals("选中行 idle = 强调色选中档", ROW_SELECTED, cat1.getBackgroundColor());
        pointerAt(nav, 0, ScenePointerAction.MOVE);
        Assert.assertEquals("选中 + hover = 强调悬停档", ROW_SELECTED_HOVER, cat1.getBackgroundColor());
        Assert.assertNotEquals("选中行 hover 仍区别于普通 hover", ROW_HOVER, cat1.getBackgroundColor());

        // 禁用优先级最高：选中 + 悬停中的行也取禁用档
        enabled.set(Boolean.FALSE);
        rt.flush();
        Assert.assertEquals("disabled 压过 selected+hover（选中行取禁用档）", ROW_DISABLED,
                cat1.getBackgroundColor());
        Assert.assertEquals("禁用切换后未悬停行同样取禁用档", ROW_DISABLED,
                cat2.getBackgroundColor());
        int before = selects.size();
        click(rowAt(nav, 1));
        Assert.assertEquals("禁用态点击不触发 onSelect", before, selects.size());

        enabled.set(Boolean.TRUE);
        rt.flush();
        Assert.assertEquals("恢复启用：指针自 MOVE 帧起一直悬停在 cat1 上（BUTTON 帧不转移 hover）→ 选中+悬停档",
                ROW_SELECTED_HOVER, cat1.getBackgroundColor());
        Assert.assertEquals("恢复启用：cat2 无 hover 回 idle 档", ROW_IDLE, cat2.getBackgroundColor());
        pointerAt(nav, 1, ScenePointerAction.MOVE);
        Assert.assertEquals("MOVE 帧转移 hover：cat2 进 hovered 档", ROW_HOVER, cat2.getBackgroundColor());
        Assert.assertEquals("cat1 退 hover 后仍保持选中档", ROW_SELECTED, cat1.getBackgroundColor());
        pointerAway();
        Assert.assertEquals("指针移开：cat2 回 idle 档", ROW_IDLE, cat2.getBackgroundColor());
    }

    // ==================== 复用/重绑：状态不串到下一项 ====================

    @Test
    public void reboundRowsNeverCarryPreviousRowStates() {
        List<CategoryRow> first = threeCategories();
        SceneNode nav = mountPane(first, null);
        categoryKey.set("cat1");
        rt.flush();
        pointerAt(nav, 0, ScenePointerAction.MOVE);
        SceneNode staleNode = rowAt(nav, 0);
        Assert.assertEquals("重绑前：cat1 行 = 选中 + hover 强调悬停档",
                ROW_SELECTED_HOVER, staleNode.getBackgroundColor());

        // 换 key：cat1 消失、cat3 顶替到同一位置（虚拟化重绑场景）
        List<CategoryRow> second = Arrays.asList(
                CategoryRow.categoryRow("cat3", "分类三", 7),
                first.get(1),
                first.get(2));
        rows.set(second);
        rt.flush();
        layoutAll();

        SceneNode fresh = rowAt(nav, 0);
        Assert.assertNotSame("cat1 节点被回收、cat3 新建", staleNode, fresh);
        Assert.assertEquals("重绑行标签", "分类三", labelAt(nav, 0).getText());
        Assert.assertEquals("重绑行徽章格式保持", "7", badgeAt(nav, 0).getText());
        Assert.assertEquals("选中态不串：新行未选中（categoryKey 仍为 cat1）",
                ROW_IDLE, fresh.getBackgroundColor());
        Assert.assertEquals("hover 不串：指针未发新帧前不残留悬停档",
                ROW_IDLE, fresh.getBackgroundColor());
        Assert.assertNull("滤镜不串：新行零 BACKDROP", fresh.getBackdrop());
        Assert.assertEquals(0, backdropCount(paintEngine, fresh));
        Assert.assertEquals("禁用不串：其余行仍 idle 档", ROW_IDLE, rowAt(nav, 1).getBackgroundColor());
        Assert.assertEquals("「全部」行不残留选中", ROW_IDLE, rowAt(nav, 2).getBackgroundColor());

        // 重绑行交互与选中照常工作
        pointerAt(nav, 0, ScenePointerAction.MOVE);
        Assert.assertEquals("新行 hover 正常进入", ROW_HOVER, fresh.getBackgroundColor());
        click(rowAt(nav, 0));
        Assert.assertEquals("新行点击回传自身 key", "cat3", selects.get(selects.size() - 1));
        pointerAway();
        categoryKey.set("cat3");
        rt.flush();
        Assert.assertEquals("新行正常进入选中档", ROW_SELECTED, fresh.getBackgroundColor());

        // 同 key 换位（复用节点移动位置）：节点身份不变、选中跟随 key 而非位置
        SceneNode movedAll = rowAt(nav, 2);
        rows.set(Arrays.asList(first.get(2), second.get(0), first.get(1)));
        rt.flush();
        layoutAll();
        Assert.assertSame("同 key 换位复用同一节点", movedAll, rowAt(nav, 0));
        Assert.assertEquals("「全部」行换位后仍未选中", ROW_IDLE, rowAt(nav, 0).getBackgroundColor());
        Assert.assertEquals("选中行换位后保持选中（状态跟 key 不跟位置）",
                ROW_SELECTED, rowAt(nav, 1).getBackgroundColor());
        Assert.assertEquals("其余行换位后仍 idle", ROW_IDLE, rowAt(nav, 2).getBackgroundColor());
    }

    // ==================== 文字前景：foreground / mutedForeground ====================

    @Test
    public void textFollowsThemeForegroundAndMutedForeground() {
        SceneNode nav = mountPane(threeCategories(), "暂无可用分类");

        Assert.assertEquals("行标签取主题正文色", FG, labelAt(nav, 0).getTextColor());
        Assert.assertEquals("数量徽章（次要信息）取主题 mutedForeground", FG_MUTED, badgeAt(nav, 0).getTextColor());

        // 选中行文字口径（SceneNavList 已验收裁决）：取 foreground，不取 onAccentForeground
        categoryKey.set("cat1");
        rt.flush();
        Assert.assertEquals("选中行文字仍取正文色", FG, labelAt(nav, 0).getTextColor());
        Assert.assertNotEquals("选中行不取 onAccentForeground（强调中间底上可读性不足）",
                SceneThemes.DEFAULT.onAccentForeground(), labelAt(nav, 0).getTextColor());
        Assert.assertNotEquals("旧 TEXT_SECONDARY 静态写入已删除（标签不再取实色）",
                SceneChromeTokens.TEXT_SECONDARY, labelAt(nav, 0).getTextColor());

        // 禁用：行标签/徽章取 disabledForeground（不透明可读）
        enabled.set(Boolean.FALSE);
        rt.flush();
        Assert.assertEquals("禁用行标签取 disabledForeground", FG_DISABLED, labelAt(nav, 0).getTextColor());
        Assert.assertEquals("禁用徽章取 disabledForeground", FG_DISABLED, badgeAt(nav, 0).getTextColor());
        Assert.assertEquals("禁用前景不透明可读", 0xFF, alphaOf(FG_DISABLED));
        enabled.set(Boolean.TRUE);
        rt.flush();
        Assert.assertEquals("恢复启用标签回正文色", FG, labelAt(nav, 0).getTextColor());
        Assert.assertEquals("恢复启用徽章回 muted", FG_MUTED, badgeAt(nav, 0).getTextColor());

        // 空态提示 = 次要前景；禁用空态取 disabledForeground
        rows.set(Collections.<CategoryRow>emptyList());
        rt.flush();
        List<SceneNode> viewportChildren = nav.__getChildren().get(0)
                .__getChildren().get(0).__getChildren();
        SceneNode emptyNode = viewportChildren.get(viewportChildren.size() - 2);
        Assert.assertEquals("空态提示取 mutedForeground", FG_MUTED, emptyNode.getTextColor());
        enabled.set(Boolean.FALSE);
        rt.flush();
        Assert.assertEquals("禁用空态取 disabledForeground", FG_DISABLED, emptyNode.getTextColor());
    }

    // ==================== 主题切换：只重派生、身份不变、effect 不增长 ====================

    @Test
    public void themeSwitchReDerivesWithoutRebuildOrExtraEffects() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：深/浅 TOOLBAR 配方不同",
                dark.surface(SceneTheme.Role.TOOLBAR), light.surface(SceneTheme.Role.TOOLBAR));
        Assert.assertNotEquals("测试前提：深/浅 INDICATOR 配方不同",
                dark.surface(SceneTheme.Role.INDICATOR), light.surface(SceneTheme.Role.INDICATOR));

        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        MountHandle themed = mountThemedPane(pageTheme, threeCategories(), null);
        SceneNode nav = themed.getRoot().__getChildren().get(0);
        SceneNode selectedRow = rowAt(nav, 2); // 「全部」行（categoryKey 缺省 = 选中）
        SceneNode plainRow = rowAt(nav, 0);
        SceneNode label = labelAt(nav, 2);
        SceneNode badge = badgeAt(nav, 2);
        Assert.assertEquals("初始底座取深色 TOOLBAR 档",
                dark.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), nav.getBackgroundColor());
        Assert.assertEquals("初始选中行取深色强调选中档", ROW_SELECTED, selectedRow.getBackgroundColor());

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        rt.flush();
        layoutAll();

        Assert.assertSame("主题切换不重建底座节点", nav, themed.getRoot().__getChildren().get(0));
        Assert.assertSame("主题切换不重建行节点", selectedRow, rowAt(nav, 2));
        Assert.assertSame("主题切换不重建标签节点", label, labelAt(nav, 2));
        Assert.assertSame("主题切换不重建徽章节点", badge, badgeAt(nav, 2));
        Assert.assertEquals("底座随主题更新",
                light.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), nav.getBackgroundColor());
        Assert.assertEquals("底座圆角随主题更新",
                light.surface(SceneTheme.Role.TOOLBAR).getCornerRadius(), nav.getCornerRadius());
        Assert.assertEquals("底座滤镜材质随主题更新",
                light.surface(SceneTheme.Role.TOOLBAR).getBackdrop().getEffect().getMaterial(),
                nav.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("选中行随主题更新且不丢选中",
                selectedTint(light.accent()), selectedRow.getBackgroundColor());
        Assert.assertEquals("未选中行随主题更新",
                light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), plainRow.getBackgroundColor());
        Assert.assertEquals("标签随主题更新", light.foreground(), label.getTextColor());
        Assert.assertEquals("徽章随主题更新", light.mutedForeground(), badge.getTextColor());
        Assert.assertEquals("行依旧零 BACKDROP（切主题不给行加装滤镜）",
                0, backdropCount(paintEngine, plainRow));
        Assert.assertNull("受控分类不受主题切换影响", categoryKey.get());
        Assert.assertEquals("主题切换不新增订阅",
                effectsBeforeSwitch, ReactiveTestProbe.registeredEffectCount());

        themed.dispose();
        Assert.assertEquals("卸载后回收该实例全部绑定", baseline,
                ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 关闭滤镜：底座无 BACKDROP 且底色不透明可读 ====================

    /**
     * 无滤镜替代档（{@code SceneTheme.withoutBackdrop()}）：底座 backdrop=null、不发 BACKDROP，
     * 底色取不透明替代色可读；行本就零滤镜不受影响，选中仍靠强调 tint 区分，文字仍取主题前景。
     */
    @Test
    public void withoutBackdropThemeKeepsShellReadableAndRowsFlat() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark().withoutBackdrop());
        mountThemedPane(pageTheme, threeCategories(), null);
        SceneNode nav = lastHandle.getRoot().__getChildren().get(0);

        Assert.assertNull("关闭滤镜档底座不声明 backdrop", nav.getBackdrop());
        Assert.assertEquals("关闭滤镜档底座底色不透明可读", 0xFF, alphaOf(nav.getBackgroundColor()));
        Assert.assertEquals("底座底色 = 无滤镜替代色（TOOLBAR 档）",
                SceneTheme.FALLBACK_BG, nav.getBackgroundColor());
        SceneNode allRow = rowAt(nav, 2);
        Assert.assertNull("行本就零滤镜", allRow.getBackdrop());
        Assert.assertEquals("选中行仍靠强调 tint 区分（0x59）", 0x59, alphaOf(allRow.getBackgroundColor()));
        Assert.assertNotEquals("选中与未选中底色可区分", rowAt(nav, 0).getBackgroundColor(),
                allRow.getBackgroundColor());
        Assert.assertEquals("文字仍取主题正文色", SceneThemes.DEFAULT.foreground(),
                labelAt(nav, 2).getTextColor());
    }

    // ==================== 卸载回收 ====================

    @Test
    public void disposeReclaimsAllBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        MountHandle handle = mountThemedPane(Signal.create(SceneTheme.liquidGlassDark()),
                threeCategories(), null);

        int mounted = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("挂载应注册响应式绑定，baseline=" + baseline + ", mounted=" + mounted,
                mounted > baseline);

        handle.dispose();
        Assert.assertEquals("卸载回收全部绑定（含行与空态）", baseline,
                ReactiveTestProbe.registeredEffectCount());
    }
}
