package club.heiqi.uilib.ui.scene.control.search;

import club.heiqi.uilib.ui.scene.testkit.SceneTestEnvironments;
import java.util.AbstractList;
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
 *   <li>行 = <b>pill 行</b>：{@code selectableSurface(INDICATOR, selected)} 为主题基线，
 *       本地只放大非选中档的 tint 步进（hover +0x18 / pressed +0x2C）并关滤镜/关浮雕；形状
 *       （圆角 + 1px 描边）取角色配方，左右内缩与行间间距由生效字号派生；<b>行零 BACKDROP</b>
 *       （底座恰好新增 1 颗采样）。状态档量化判据见 {@link #rowStatesAreQuantifiablyDistinctFromIdle}。</li>
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
    /**
     * 行 hover/pressed 的 tint 强度步进（单位 alpha）：与 {@code CategoryNavPane} 同一口径
     * （本地覆盖，不是全局主题配方；步进叠加在<b>主题 idle alpha</b> 上 ⇒ 主题换档时测试自动跟随）。
     */
    private static final int ROW_HOVER_TINT_STEP = 0x18;
    private static final int ROW_PRESS_TINT_STEP = 0x2C;
    /** 行三档：idle/disabled 取角色原档；hover/pressed = 主题 idle alpha + 步进（RGB 取主题对应档）。 */
    private static final int ROW_IDLE = INDICATOR.getIdle().getTint();
    private static final int ROW_DISABLED = INDICATOR.getDisabled().getTint();
    private static final int ROW_HOVER = withAlpha(INDICATOR.getHovered().getTint(),
            alphaOf(ROW_IDLE) + ROW_HOVER_TINT_STEP);
    private static final int ROW_PRESSED = withAlpha(INDICATOR.getPressed().getTint(),
            alphaOf(ROW_IDLE) + ROW_PRESS_TINT_STEP);
    /** 行缘色三档：本地不覆盖缘色，hover/pressed 取主题对应档（idle 缘 = 静息轮廓）。 */
    private static final int ROW_IDLE_EDGE = INDICATOR.getIdle().getEdge();
    private static final int ROW_HOVER_EDGE = INDICATOR.getHovered().getEdge();
    private static final int ROW_PRESSED_EDGE = INDICATOR.getPressed().getEdge();
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
        rt = SceneTestEnvironments.runtime(measurer);
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

    /** 保留色 RGB、替换 alpha（与实现同一助手语义，测试侧独立表达避免自证）。 */
    private static int withAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
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

    /** 带 P5 度量通道的挂载：行高/内缩/行间距全部由生效字号派生（字号变化走同一节点重派生）。 */
    private SceneNode mountWithMetrics(Signal<PickerMetrics> metrics, List<CategoryRow> initial) {
        rows.set(initial);
        lastHandle = rt.mount(sceneRoot, () -> {
            SceneNode wrapper = new SceneNode();
            wrapper.setPreferredHeight(300);
            final SceneNode[] holder = new SceneNode[1];
            SceneThemes.withTheme(Signal.create(SceneTheme.liquidGlassDark()), () -> holder[0] =
                    CategoryNavPane.create(rt, new CategoryNavPane.Props(rows, categoryKey, enabled,
                            selects::add, "暂无分类", null, null, null, metrics)));
            wrapper.appendChild(holder[0]);
            return wrapper;
        });
        rt.flush();
        layoutAll();
        return lastHandle.getRoot().__getChildren().get(0);
    }

    private void layoutAll() {
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    /**
     * C4（P5 §5.3）：分类导航键盘化 —— 行可聚焦、↑↓ 移焦点、HOME/END 首末、ENTER 切换。
     *
     * <p>切换走与点击同一个 onSelect 单点（selects 记录），焦点只在导航行之间移动。</p>
     */
    @Test
    public void keyboardNavigatesRowsAndSelectsWithEnter() {
        SceneNode nav = mountPane(Arrays.asList(
                CategoryRow.allRow("全部", 3),
                CategoryRow.categoryRow("cat1", "cat1", 2),
                CategoryRow.categoryRow("cat2", "cat2", 1)), "暂无分类");

        rt.requestFocus(rowAt(nav, 0));
        rt.flush();
        Assert.assertSame("行可聚焦（首焦点）", rowAt(nav, 0), rt.getFocusedNode());

        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertSame("↓ 移到下一行", rowAt(nav, 1), rt.getFocusedNode());
        pressKey(SceneKey.END);
        Assert.assertSame("END 到末行", rowAt(nav, 2), rt.getFocusedNode());
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertSame("末行 ↓ 不越界", rowAt(nav, 2), rt.getFocusedNode());
        pressKey(SceneKey.HOME);
        Assert.assertSame("HOME 回首行", rowAt(nav, 0), rt.getFocusedNode());
        pressKey(SceneKey.ARROW_UP);
        Assert.assertSame("首行 ↑ 不越界", rowAt(nav, 0), rt.getFocusedNode());

        pressKey(SceneKey.ENTER);
        Assert.assertEquals("ENTER 切换分类（与点击同一 onSelect）",
                Arrays.asList((String) null), selects);
        Assert.assertTrue("全部行选中态生效",
                categoryKey.get() == null || categoryKey.get().isEmpty());
    }

    /** 在焦点节点上注入一次按键（PRESSED）。 */
    private void pressKey(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED, false, false, false, false, 0, 0, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
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

    // ==================== 选中态（INDICATOR 选中语义：强调色 + 0x59，取代旧 SceneStateColors） ====================

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
            // 行表面归绑定器独占：形状取角色配方（pill 圆角 + 1px 描边），但零滤镜、不进浮雕通道。
            Assert.assertEquals("行[" + i + "] 描边宽 = 角色配方", INDICATOR.getBorderWidth(), row.getBorderWidth());
            Assert.assertEquals("行[" + i + "] 圆角 = 角色配方（pill）",
                    INDICATOR.getCornerRadius(), row.getCornerRadius());
            Assert.assertEquals("行[" + i + "] 不绑定实体高度（-1=普通绘制）", -1.0F,
                    row.__getSurfaceElevation(), EPSILON);
        }
    }

    // ==================== 行三态：主题基线 + 本地步进（选中/悬停/禁用） ====================

    @Test
    public void rowStatesFollowIndicatorLightweightTiers() {
        SceneNode nav = mountPane(threeCategories(), null);
        categoryKey.set("cat1");
        rt.flush();
        SceneNode cat1 = rowAt(nav, 0);
        SceneNode cat2 = rowAt(nav, 1);

        Assert.assertEquals("未选中 idle = 配方 idle 档", ROW_IDLE, cat2.getBackgroundColor());

        pointerAt(nav, 1, ScenePointerAction.MOVE);
        Assert.assertEquals("hover = 主题基线 + 本地步进（主题 idle alpha + 0x18）",
                ROW_HOVER, cat2.getBackgroundColor());
        Assert.assertNotEquals("hover 有可见变化", ROW_IDLE, cat2.getBackgroundColor());

        pointerAt(nav, 1, ScenePointerAction.BUTTON_DOWN);
        Assert.assertEquals("pressed 压过 hovered = 主题基线 + 本地步进（主题 idle alpha + 0x2C）",
                ROW_PRESSED, cat2.getBackgroundColor());
        pointerAt(nav, 1, ScenePointerAction.BUTTON_UP);
        Assert.assertEquals("释放回 hover 档（主题基线 + 本地步进）", ROW_HOVER, cat2.getBackgroundColor());
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
        Assert.assertEquals("MOVE 帧转移 hover：cat2 进 hover 档（主题基线 + 本地步进）",
                ROW_HOVER, cat2.getBackgroundColor());
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

    // ==================== T2：pill 形状与状态档量化 ====================

    /** pill 形状：圆角/描边取角色配方；左右内缩与行间间距由生效字号派生（旧口径：满宽矩形色块）。 */
    @Test
    public void pillRowsCarryRecipeShapeAndFontDerivedInsets() {
        SceneNode nav = mountPane(threeCategories(), null);
        int inset = PickerChrome.navRowInset(CategoryNavPane.FONT_SIZE);
        int gap = PickerChrome.navRowGap(CategoryNavPane.FONT_SIZE);
        Assert.assertTrue("前置：内缩必须 > 0（pill 不贴栏边）", inset > 0);
        Assert.assertTrue("前置：行间距必须 > 0（相邻 pill 不粘连）", gap > 0);
        // 节点属性 = 配方真值（INDICATOR 配方的 PILL_RADIUS 999）；实际几何由渲染层夹取：
        // UiRoundedRectGeometry.clampCornerRadius(:221-223) = max(0, min(radius, min(w, h) / 2))
        // ⇒ 行高 32 时圆角被夹到 16，即完整胶囊。断言节点属性为 999 是钉「配方真值」，
        // 不表示真机上画 999px 圆角（勿误读）。
        Assert.assertTrue("前置：pill 圆角必须远大于行高（配方真值 999，渲染层夹到半高 = 完整胶囊）",
                INDICATOR.getCornerRadius() > PickerChrome.navRowHeight(CategoryNavPane.FONT_SIZE));
        for (int i = 0; i < 3; i++) {
            SceneNode row = rowAt(nav, i);
            Assert.assertEquals("行[" + i + "] 圆角 = 角色配方真值（几何由渲染层夹到半高）",
                    INDICATOR.getCornerRadius(), row.getCornerRadius());
            Assert.assertEquals("行[" + i + "] 描边宽 = 角色配方", INDICATOR.getBorderWidth(), row.getBorderWidth());
            Assert.assertEquals("行[" + i + "] 左内缩 = 字号派生", inset, row.getMarginLeft());
            Assert.assertEquals("行[" + i + "] 右内缩 = 字号派生", inset, row.getMarginRight());
            Assert.assertEquals("行[" + i + "] 行间间距 = 字号派生", gap, row.getMarginBottom());
            Assert.assertEquals("行[" + i + "] 静息缘色 = 主题 idle 档", ROW_IDLE_EDGE, row.getBorderColor());
        }
        // 内容合同不变：标签(第 0 孩)/计数徽章(第 1 孩) 仍在行内，垂直居中由 CrossAxisAlign.CENTER 承担。
        Assert.assertEquals("行标签仍是行首孩子", "分类一", labelAt(nav, 0).getText());
        Assert.assertEquals("数量徽章仍是行第二个孩子", "3", badgeAt(nav, 0).getText());
    }

    /** 状态档量化：hover/pressed 与 idle 同屏可辨（tint 与缘色双通道，不是「感觉更明显」）。 */
    @Test
    public void rowStatesAreQuantifiablyDistinctFromIdle() {
        SceneNode nav = mountPane(threeCategories(), null);
        SceneNode row = rowAt(nav, 1);
        int idleTint = row.getBackgroundColor();
        int idleEdge = row.getBorderColor();
        Assert.assertEquals("静息 tint = 角色 idle 档", ROW_IDLE, idleTint);
        Assert.assertEquals("静息缘色 = 角色 idle 缘", ROW_IDLE_EDGE, idleEdge);

        pointerAt(nav, 1, ScenePointerAction.MOVE);
        int hoverTint = row.getBackgroundColor();
        int hoverEdge = row.getBorderColor();
        Assert.assertEquals("hover tint = 主题 idle alpha + 步进", ROW_HOVER, hoverTint);
        Assert.assertEquals("hover 缘色 = 主题 hovered 档", ROW_HOVER_EDGE, hoverEdge);
        Assert.assertTrue("hover − idle tint Δalpha ≥ 0x12（≥7.1pp）",
                alphaOf(hoverTint) - alphaOf(idleTint) >= 0x12);
        Assert.assertTrue("hover − idle 缘色 Δalpha ≥ 0x40（≥2×）",
                alphaOf(hoverEdge) - alphaOf(idleEdge) >= 0x40);
        Assert.assertTrue("本地 hover 步进必须 > 主题原生步进(+6)",
                alphaOf(hoverTint) - alphaOf(idleTint) > 6);

        pointerAt(nav, 1, ScenePointerAction.BUTTON_DOWN);
        int pressTint = row.getBackgroundColor();
        int pressEdge = row.getBorderColor();
        Assert.assertEquals("pressed tint = 主题 idle alpha + 步进", ROW_PRESSED, pressTint);
        Assert.assertTrue("pressed − hovered tint Δalpha ≥ 0x10（填充更深）",
                alphaOf(pressTint) - alphaOf(hoverTint) >= 0x10);
        // 行可聚焦：POINTER_DOWN 的隐式聚焦（SceneInputRouter「隐式聚焦」块：命中链里最深的
        // focusable）让行同时进入 focused；绑定器按契约把「非禁用 + focused」的缘色切到主题聚焦缘
        // （SceneSurfaceBinder:118-121）。故鼠标按下时可见缘色 = focusEdge，而 tint 仍取 pressed 档
        // —— 按压依然与 hover 可辨（填充更深 + 缘色换色相），但不是「rim 变暗」。
        Assert.assertEquals("按下即聚焦：缘色 = 主题聚焦缘（绑定器 focus 覆盖语义）",
                INDICATOR.getFocusEdge(), pressEdge);
        Assert.assertNotEquals("聚焦缘 ≠ hovered 缘（按下与悬停在同屏可辨）",
                ROW_HOVER_EDGE, pressEdge);

        // 配方层缘色阶梯（运行时被 focusEdge 覆盖，故在配方真值上钉住设计意图）：
        // pressed 缘比 hovered 缘更暗 = 「按下去 rim 回落」；这条不被 focusEdge 掩盖。
        Assert.assertTrue("配方缘色阶梯：pressed 缘比 hovered 缘更暗",
                alphaOf(ROW_PRESSED_EDGE) < alphaOf(ROW_HOVER_EDGE));
        Assert.assertTrue("配方缘色阶梯：hovered 缘比 idle 缘更亮（hover 轮廓可读）",
                alphaOf(ROW_HOVER_EDGE) > alphaOf(ROW_IDLE_EDGE));

        pointerAt(nav, 1, ScenePointerAction.BUTTON_UP);
        pointerAway();
        Assert.assertEquals("移开回 idle tint（焦点不吞 tint 档）", ROW_IDLE, row.getBackgroundColor());
        Assert.assertEquals("焦点行移开指针后仍显聚焦缘（焦点线索不丢）",
                INDICATOR.getFocusEdge(), row.getBorderColor());

        // 失焦（点击树外 ⇒ Router 隐式 clearFocus，权威焦点只经 Router 改写，本控件不直写）后缘色回状态档。
        clickAt(W - 2, H - 2);
        Assert.assertEquals("失焦后缘色回 idle 档", ROW_IDLE_EDGE, row.getBorderColor());
    }

    /** 动态化：生效字号变化后行高/内缩/行间距在同一节点上重派生（不重建行、不改配方形状）。 */
    @Test
    public void rowGeometryReDerivesFromMetricsWithoutRebuild() {
        Signal<PickerMetrics> metrics = Signal.create(PickerMetrics.solve(rt, W, H, 12, null, 2));
        SceneNode nav = mountWithMetrics(metrics, threeCategories());
        SceneNode row = rowAt(nav, 0);
        Assert.assertEquals("初始内缩 = 字号派生(12)",
                PickerChrome.navRowInset(12), row.getMarginLeft());
        Assert.assertEquals("初始行高 = 字号派生(12)",
                PickerChrome.navRowHeight(12), row.getPreferredHeight());

        metrics.set(PickerMetrics.solve(rt, W, H, 20, null, 2));
        rt.flush();
        layoutAll();
        Assert.assertSame("字号变化不重建行节点", row, rowAt(nav, 0));
        Assert.assertEquals("内缩重派生", PickerChrome.navRowInset(20), row.getMarginLeft());
        Assert.assertEquals("行间距重派生", PickerChrome.navRowGap(20), row.getMarginBottom());
        Assert.assertEquals("行高重派生", PickerChrome.navRowHeight(20), row.getPreferredHeight());
        Assert.assertTrue("两档内缩必须不同（否则本用例失去意义）",
                PickerChrome.navRowInset(20) != PickerChrome.navRowInset(12));
        Assert.assertTrue("两档行高必须不同（否则本用例失去意义）",
                PickerChrome.navRowHeight(20) != PickerChrome.navRowHeight(12));
        Assert.assertEquals("pill 形状仍取角色配方（字号变化不改配方）",
                INDICATOR.getCornerRadius(), row.getCornerRadius());
    }

    /**
     * pill 几何是<b>布局</b>而非只改绘制：内缩/行间间距走行节点 margin（LayoutBox 收缩），
     * 行的命中盒 = 视觉 pill 盒 —— 不存在「看着是 pill 却点不到边缘」或反向的隐形命中区。
     * 判据：pill 左边缘内 1px 命中该行；内缩 gutter（pill 之外、视口之内）不命中任何行。
     */
    @Test
    public void pillHitRegionMatchesItsVisualBounds() {
        SceneNode nav = mountPane(threeCategories(), null);
        SceneNode row = rowAt(nav, 1);
        AnchorRect box = SceneGeometry.absoluteBox(row, 0, 0);
        int inset = row.getMarginLeft();
        Assert.assertTrue("前置：行确实内缩（否则本用例失去意义）", inset > 0);

        // gutter：x 在视口内、pill 左边缘之外（保证在视觉 pill 之外，且远离 1px 描边）
        clickAt(box.getX() - Math.max(1, inset / 2), box.getY() + box.getHeight() / 2);
        Assert.assertTrue("pill 之外的 gutter 不命中任何行（无隐形命中区）", selects.isEmpty());

        // pill 边缘内 1px：命中该行（命中盒与视觉盒同一 LayoutBox）
        clickAt(box.getX() + 1, box.getY() + box.getHeight() / 2);
        Assert.assertEquals("pill 边缘命中该行（视觉与命中一致）",
                Collections.singletonList("cat2"), selects);
    }

    /** 在画布绝对坐标处注入一次完整点击（DOWN + UP）。 */
    private void clickAt(int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
    }

    /**
     * 键盘导航 + 最小位移滚动在内缩/行间距引入后仍正确：焦点行不得滚出视口、位移不得算错。
     *
     * <p>既有 {@link #keyboardNavigatesRowsAndSelectsWithEnter} 只覆盖焦点移动（3 行不滚动），
     * 本用例用 20 行（内容超出 300px 宿主）覆盖 {@code scrollIntoView} 的最小位移路径。</p>
     */
    @Test
    public void keyboardFocusStaysVisibleWithPillGeometry() {
        List<CategoryRow> many = new ArrayList<CategoryRow>();
        many.add(CategoryRow.allRow("全部", 20));
        for (int i = 0; i < 19; i++) {
            many.add(CategoryRow.categoryRow("cat" + i, "分类" + i, i));
        }
        SceneNode nav = mountPane(many, null);
        SceneNode viewport = nav.__getChildren().get(0).__getChildren().get(0);
        Assert.assertTrue("前置：内容超出视口（滚动可发生）", SceneGeometry.maxScrollY(viewport) > 0);

        SceneNode first = rowAt(nav, 0);
        SceneNode last = rowAt(nav, many.size() - 1);
        rt.requestFocus(first);
        rt.flush();
        Assert.assertSame("首行可聚焦", first, rt.getFocusedNode());

        pressKey(SceneKey.END);
        layoutAll();
        Assert.assertSame("END 到末行", last, rt.getFocusedNode());
        Assert.assertTrue("末行焦点触发最小位移滚动（偏移 > 0 且不超过 maxScrollY）",
                viewport.getScrollOffsetY() > 0
                        && viewport.getScrollOffsetY() <= SceneGeometry.maxScrollY(viewport));
        AnchorRect viewportBox = SceneGeometry.absoluteBox(viewport, 0, 0);
        AnchorRect lastBox = SceneGeometry.absoluteBox(last, 0, 0);
        Assert.assertTrue("焦点行顶边不越视口上界", lastBox.getY() >= viewportBox.getY());
        Assert.assertTrue("焦点行底边不越视口下界",
                lastBox.getY() + lastBox.getHeight() <= viewportBox.getY() + viewportBox.getHeight());

        pressKey(SceneKey.HOME);
        layoutAll();
        Assert.assertSame("HOME 回首行", first, rt.getFocusedNode());
        Assert.assertEquals("回首行滚回顶部（最小位移而非居中）", 0, viewport.getScrollOffsetY());
        AnchorRect firstBox = SceneGeometry.absoluteBox(first, 0, 0);
        Assert.assertTrue("首行焦点完整可见", firstBox.getY() >= viewportBox.getY()
                && firstBox.getY() + firstBox.getHeight() <= viewportBox.getY() + viewportBox.getHeight());
    }

    // ==================== 行内容换代（keyed 复用吃构建期文本） ====================

    /**
     * 行键不变而 label/count 换代 ⇒ 文本必须刷新，且**节点不重建、行键不变**（零结构变化）。
     *
     * <p>缺陷锚定：行节点由 {@code forEach} 按 {@code identityKey} 复用，行内标签/徽章一度只写构建期
     * 快照 ⇒ 同一行键的换代（「全部」行随查询总量变化、分类行随清单/语言代际重建）永远刷不上屏。
     * 修复前本用例红：标签/徽章停在换代前的文本。</p>
     */
    @Test
    public void rowTextsFollowCurrentSnapshotForSameRowKey() {
        List<CategoryRow> first = threeCategories();
        SceneNode nav = mountPane(first, "暂无分类");
        SceneNode rowBefore = rowAt(nav, 0);
        Assert.assertEquals("前置：旧标签", "分类一", labelAt(nav, 0).getText());
        Assert.assertEquals("前置：旧计数", "3", badgeAt(nav, 0).getText());
        Assert.assertEquals("前置：「全部」计数", "8", badgeAt(nav, 2).getText());

        // 同一行键换代：cat1 的 label/count 变、「全部」计数变、cat2 保持不变。
        rows.set(Arrays.asList(
                CategoryRow.categoryRow("cat1", "分类一·改", 42),
                first.get(1),
                CategoryRow.allRow("全部", 999)));
        rt.flush();
        layoutAll();

        Assert.assertSame("行键不变 ⇒ 节点复用、结构零变化", rowBefore, rowAt(nav, 0));
        Assert.assertEquals("同键换代：标签刷新", "分类一·改", labelAt(nav, 0).getText());
        Assert.assertEquals("同键换代：徽章刷新", "42", badgeAt(nav, 0).getText());
        Assert.assertEquals("同键换代：「全部」行计数刷新", "999", badgeAt(nav, 2).getText());
        Assert.assertEquals("未换代的行不串改：cat2 计数保持", "5", badgeAt(nav, 1).getText());
        Assert.assertEquals("未换代的行不串改：cat2 标签保持", "分类二", labelAt(nav, 1).getText());
    }

    /**
     * 静默帧（rows 不变）不得重读行列表、不得触发节点重写：索引必须是<b>响应式派生</b>
     * （换代才 O(rows) 一次、行内 O(1)），行内文本投影按值记忆化。
     *
     * <p>观测面：行列表读取计数（构造期快照会随每次读取变化）+ <b>几何变更纪元</b>
     * （{@link SceneLayoutEngine#layoutChangeEpoch()}：只有真几何变化才前进；{@code layoutEpoch()}
     * 每批都自增，不适用于本判据）+ 徽章绘制缓存身份。三者任一被静默帧推动，即为
     * 「每帧重建索引 / 每帧重写节点」。</p>
     */
    @Test
    public void unchangedFrameDoesNotRereadRowsOrRewriteTexts() {
        CountingRows counting = new CountingRows(threeCategories());
        SceneNode nav = mountPane(counting, "暂无分类");
        // 收敛：先让 paint 自身的首批失效（绘制缓存建立/呈现偏移落位）过去，再取静默帧基线。
        layoutAll();
        paintEngine.paint(nav);
        layoutAll();
        layoutAll();
        int reads = counting.reads;
        int changeEpoch = layoutEngine.layoutChangeEpoch();
        Object cachedPaint = badgeAt(nav, 0).getCachedPaint();
        Assert.assertNotNull("前置：绘制缓存已生成", cachedPaint);

        rt.flush();
        layoutAll();

        Assert.assertEquals("静默帧不得重读行列表（索引必须响应式派生，不得每帧重建）",
                reads, counting.reads);
        Assert.assertEquals("静默帧不得触发几何失效", changeEpoch, layoutEngine.layoutChangeEpoch());
        Assert.assertSame("静默帧不得重写节点（绘制缓存身份保持）",
                cachedPaint, badgeAt(nav, 0).getCachedPaint());
        Assert.assertEquals("文本保持", "分类一", labelAt(nav, 0).getText());
        Assert.assertEquals("徽章保持", "3", badgeAt(nav, 0).getText());
    }

    /** 记录 {@code get}/{@code size} 读取次数的行列表（索引重建探测）。 */
    private static final class CountingRows extends AbstractList<CategoryRow> {
        private final List<CategoryRow> delegate;
        private int reads;

        private CountingRows(List<CategoryRow> delegate) {
            this.delegate = delegate;
        }

        @Override
        public CategoryRow get(int index) {
            reads++;
            return delegate.get(index);
        }

        @Override
        public int size() {
            reads++;
            return delegate.size();
        }
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
