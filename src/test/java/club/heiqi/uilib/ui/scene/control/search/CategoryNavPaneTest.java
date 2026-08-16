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
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav.CategoryRow;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * {@link CategoryNavPane} 单元测试。
 *
 * <p>覆盖：3 分类渲染（含全部行）与徽章文本、点击回调 key / 全部 accept(null)、
 * 选中态随 categoryKey 变化、空态提示、实底外壳断言（背景 != 0 / 圆角 RADIUS_MD / 无边框）。</p>
 */
public class CategoryNavPaneTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private Signal<List<CategoryRow>> rows;
    private Signal<String> categoryKey;
    private Signal<Boolean> enabled;
    private List<String> selects;

    private static final int W = 400;
    private static final int H = 600;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
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

    /** 挂载并布局，返回导航根节点（外壳）。外壳 fillParentHeight 依赖确定高的父链，
     *  故包一层固定高度宿主（生产环境由面板卡片提供高度）。 */
    private SceneNode mountPane(List<CategoryRow> initial, String emptyLabel) {
        rows.set(initial);
        SceneNode host = rt.mount(sceneRoot, () -> {
            SceneNode wrapper = new SceneNode();
            wrapper.setPreferredHeight(300);
            wrapper.appendChild(CategoryNavPane.create(rt,
                    new CategoryNavPane.Props(rows, categoryKey, enabled, selects::add, emptyLabel)));
            return wrapper;
        }).getRoot();
        rt.flush();
        layoutAll();
        return host.__getChildren().get(0);
    }

    private void layoutAll() {
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    /** 视口 = nav.children[0]；行容器 = 视口.children[0]。 */
    private SceneNode rowsContainer(SceneNode nav) {
        return nav.__getChildren().get(0).__getChildren().get(0);
    }

    private void click(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int x = box.getX() + box.getWidth() / 2;
        int y = box.getY() + box.getHeight() / 2;
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
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

    // ==================== 选中态 ====================

    @Test
    public void selectedBackgroundTracksCategoryKey() {
        SceneNode nav = mountPane(threeCategories(), null);
        SceneNode rowsContainer = rowsContainer(nav);
        SceneNode first = rowsContainer.__getChildren().get(0);
        // 懒创建时序契约：hover 断言前先声明交互态容器
        SceneInteractionState interaction = rt.interactionState(first);

        Assert.assertEquals("categoryKey 缺省时普通行未选中（BG_DEFAULT）",
                SceneStateColors.standardBackground(true, false, false),
                first.getBackgroundColor());

        categoryKey.set("cat1");
        rt.flush();
        Assert.assertEquals("选中后背景切到 ACCENT 选中态",
                SceneStateColors.selectedBackground(true, false, false),
                first.getBackgroundColor());
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

        // 视口 children = [rows容器, 空提示内容, anchor]（show 内容插在 anchor 之前）
        List<SceneNode> viewportChildren = nav.__getChildren().get(0).__getChildren();
        Assert.assertTrue("空态提示存在", viewportChildren.size() >= 3);
        Assert.assertEquals("空提示文案 = 传入 emptyLabel", "暂无可用分类",
                viewportChildren.get(viewportChildren.size() - 2).getText());
    }

    // ==================== 外壳 ====================

    @Test
    public void shellIsSolidRoundedWithoutBorder() {
        SceneNode nav = mountPane(threeCategories(), null);
        Assert.assertNotEquals("外壳背景非透明", 0, nav.getBackgroundColor());
        Assert.assertEquals("外壳背景 BG_DEFAULT", SceneChromeTokens.BG_DEFAULT, nav.getBackgroundColor());
        Assert.assertEquals("外壳圆角 RADIUS_MD", SceneChromeTokens.RADIUS_MD, nav.getCornerRadius());
        Assert.assertEquals("无边框", 0, nav.getBorderWidth());
        Assert.assertEquals("外壳宽度 NAV_WIDTH", CategoryNavPane.NAV_WIDTH, nav.getPreferredWidth());
        Assert.assertFalse("外壳不参与命中", nav.isHitTestable());
    }
}
