package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPanelPresentation;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.GridProps;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.Props;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.Result;
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
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * {@link ScenePickerPanel} L3 runtime 集成测试。
 *
 * <p>覆盖：居中 70% portal 开/关与 ESC 分层；分类列表渲染与切换过滤；候选点击直达 vs 变体浮层两路；
 * 变体勾选/ALL-SELECTED/确认/取消/可拒绝 selectionCommit 保持展开；listMembers 模式成员
 * 增/编辑/删除二次确认/无效重复徽章/空态；键盘导航与焦点意图；数据收缩回夹；受控开合/分类接线。</p>
 */
public class ScenePickerPanelTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;

    private static final int W = 800;
    private static final int H = 600;

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

    // ==================== 夹具 ====================

    /** 面板夹具：受控开合（openSignal + onCloseRequest 写回）、3 列网格、3 可见行。 */
    private final class Fixture {
        final Signal<String> query = Signal.create("");
        final Signal<SearchPickerData.SearchResult> results;
        final Signal<List<SearchPickerData.CurrentMember>> members =
                Signal.create(Collections.<SearchPickerData.CurrentMember>emptyList());
        final Signal<List<SearchPickerCategories.Category>> categories =
                Signal.create(Collections.<SearchPickerCategories.Category>emptyList());
        final Signal<Boolean> openSignal = Signal.create(Boolean.FALSE);
        final Map<String, String> categoryMap = new HashMap<String, String>();
        final List<SearchPickerData.Selection> commits = new ArrayList<SearchPickerData.Selection>();
        final List<Long> edits = new ArrayList<Long>();
        final List<Long> removeCalls = new ArrayList<Long>();
        final AtomicInteger cancels = new AtomicInteger();
        final AtomicInteger beginAdds = new AtomicInteger();
        final AtomicInteger closeRequests = new AtomicInteger();
        final boolean[] commitResult = {true};
        final Result result;

        Fixture(List<SearchPickerData.Candidate> initialCandidates, boolean listMembers) {
            this(initialCandidates, listMembers, false, 3);
        }

        Fixture(List<SearchPickerData.Candidate> initialCandidates, boolean listMembers,
                boolean variantSearchEnabled, int columns) {
            results = Signal.create(new SearchPickerData.SearchResult(initialCandidates));
            Props.Builder builder = Props.builder(query, results, Signal.create(Boolean.TRUE),
                    query::set, ignored -> { }, visualAdapter());
            if (listMembers) builder.currentMembers(members, edits::add);
            builder.selectionCommit(selection -> {
                commits.add(selection);
                return commitResult[0];
            });
            builder.onRemoveCurrent(memberId -> {
                removeCalls.add(Long.valueOf(memberId));
                return true;
            });
            builder.onBeginAdd(beginAdds::incrementAndGet);
            builder.onCancel(cancels::incrementAndGet);
            builder.open(openSignal);
            builder.onCloseRequest(() -> {
                closeRequests.incrementAndGet();
                openSignal.set(Boolean.FALSE);
            });
            builder.grid(GridProps.of(columns, 64, 64, 8, 8, 3));
            builder.categories(categories);
            builder.categoryOf(categoryMap::get);
            builder.variantSearchEnabled(variantSearchEnabled);
            result = create(rt, builder.build());
        }
    }

    private Result create(SceneRuntime runtime, Props props) {
        Result result = ScenePickerPanel.create(runtime, props);
        sceneRoot.appendChild(result.root());
        return result;
    }

    private static VisualAdapter visualAdapter() {
        return new VisualAdapter() {
            @Override
            public String candidateLabel(SearchPickerData.Candidate candidate) {
                return candidate.label();
            }

            @Override
            public String variantLabel(SearchPickerData.Variant variant) {
                return variant.label();
            }
        };
    }

    private static SearchPickerData.Candidate candidate(String key) {
        return new SearchPickerData.Candidate(key, key + ":label",
                Collections.<SearchPickerData.Variant>emptyList());
    }

    private static SearchPickerData.Candidate candidateWithVariants(String key, String... variantKeys) {
        ArrayList<SearchPickerData.Variant> variants = new ArrayList<SearchPickerData.Variant>();
        for (String variantKey : variantKeys) {
            variants.add(new SearchPickerData.Variant(variantKey, variantKey + ":label"));
        }
        return new SearchPickerData.Candidate(key, key + ":label", variants);
    }

    // ==================== 通用辅助 ====================

    private void layoutAll() {
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        for (SceneOverlayHost.Entry entry : rt.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(W, H));
        }
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    private void openPanel(Fixture f) {
        f.openSignal.set(Boolean.TRUE);
        rt.flush();
        layoutAll();
        // 第二次布局：首帧 layoutDone 后回夹/列数推导可能改写几何，再布局一次收敛（对齐宿主逐帧布局）。
        layoutAll();
    }

    private SceneNode overlayRoot(int fromTop) {
        List<SceneOverlayHost.Entry> entries = rt.getOverlayHost().topFirst();
        Assert.assertTrue("缺少 overlay", fromTop < entries.size());
        return entries.get(fromTop).getRoot();
    }

    /** 主面板 overlay root = 透明 scrim；children[0] 才是 70% 卡片。 */
    private SceneNode panelCard(SceneNode overlayRoot) {
        return overlayRoot.__getChildren().get(0);
    }

    /** 结果列表视口 = 卡片 children[1](selectionArea).children[1](center).children[1](stackHost).children[0]。 */
    private SceneNode gridViewport(SceneNode panelRoot) {
        return panelCard(panelRoot).__getChildren().get(1).__getChildren().get(1)
                .__getChildren().get(1).__getChildren().get(0);
    }

    /** 底部横带（listMembers）= 卡片 children[2]；行容器 = 其 children[1]。 */
    private SceneNode membersPanel(SceneNode panelRoot) {
        return panelCard(panelRoot).__getChildren().get(2);
    }

    /** 按指定视口高重新布局面板 overlay 并桥接 layout epoch。 */
    private void layoutOverlayWithHeight(int height) {
        layoutEngine.layout(overlayRoot(0), new Constraints(W, height));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    /** 列表单元：viewport children[0] = rowsContainer，单元按行序平铺。 */
    private SceneNode gridCell(SceneNode viewport, int index) {
        SceneNode rowsContainer = viewport.__getChildren().get(0);
        for (SceneNode row : rowsContainer.__getChildren()) {
            if (index < row.__getChildren().size()) {
                return row.__getChildren().get(index);
            }
            index -= row.__getChildren().size();
        }
        throw new IllegalStateException("cell index out of mounted list: " + index);
    }

    /** 列表已挂载单元数（非虚拟化 = 全部项）。 */
    private int mountedItemCount(SceneNode viewport) {
        SceneNode rowsContainer = viewport.__getChildren().get(0);
        int count = 0;
        for (SceneNode row : rowsContainer.__getChildren()) {
            count += row.__getChildren().size();
        }
        return count;
    }

    private void click(SceneNode node) {
        int[] center = centerOf(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, center[0], center[1]);
        routePointer(ScenePointerAction.BUTTON_UP, center[0], center[1]);
        rt.flush();
    }

    private void pressKey(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, 0, 0, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
    }

    /** 在节点中心派发 SCROLL（负=向下滚）；wheelDelta 透传给 SceneScrolls handler 做 clamp。 */
    private void routeScrollAt(SceneNode node, int wheelDelta) {
        int[] center = centerOf(node);
        InputFrameBuilder fb = new InputFrameBuilder(center[0], center[1]);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, center[0], center[1],
                SceneMouseButton.NONE, wheelDelta, 0, 0,
                false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
    }

    /** 成员行：row = [icon, info, actions]；info = [firstLine, secondLine]；firstLine = [primary, badge]。 */
    private static SceneNode rowBadge(SceneNode row) {
        return row.__getChildren().get(1).__getChildren().get(0).__getChildren().get(1);
    }

    private static SceneNode rowEdit(SceneNode row) {
        return row.__getChildren().get(2).__getChildren().get(0);
    }

    private static SceneNode rowRemove(SceneNode row) {
        return row.__getChildren().get(2).__getChildren().get(1);
    }

    private void typeText(String text) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
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

    // ==================== 70% 面板 portal 开/关与 ESC ====================

    @Test
    public void opensSeventyPercentPanelAndClosesViaEscapeWithCancelFirst() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), false);
        rt.flush();
        layoutAll();
        Assert.assertTrue(rt.getOverlayHost().isEmpty());

        openPanel(f);
        Assert.assertEquals(1, rt.getOverlayHost().size());
        LayoutBox scrimBox = (LayoutBox) overlayRoot(0).getCachedLayout();
        Assert.assertNotNull("overlay 根必须已布局", scrimBox);
        LayoutBox cardBox = (LayoutBox) panelCard(overlayRoot(0)).getCachedLayout();
        Assert.assertNotNull("面板卡片必须已布局", cardBox);
        Assert.assertEquals("面板宽度为 70%", W * 70 / 100, cardBox.getWidth());
        Assert.assertEquals("面板高度为 70%", H * 70 / 100, cardBox.getHeight());

        SceneNode firstFocus = f.result.firstFocusTarget().get();
        Assert.assertNotNull("面板打开后有稳定首焦点目标", firstFocus);
        Assert.assertSame("焦点意图把焦点引导到搜索输入", firstFocus, rt.getFocusedNode());

        pressKey(SceneKey.ESCAPE);
        Assert.assertEquals("ESC 先走 onCancel", 1, f.cancels.get());
        Assert.assertEquals("ESC 再请求受控关闭", 1, f.closeRequests.get());
        Assert.assertFalse(f.result.open().get().booleanValue());
        Assert.assertTrue("面板关闭后 overlay 清空", rt.getOverlayHost().isEmpty());
        Assert.assertNull("面板关闭后无首焦点目标", f.result.firstFocusTarget().get());
    }

    // ==================== 分类列表渲染与切换过滤 ====================

    @Test
    public void categoryNavRendersAndSwitchFiltersGrid() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b"), candidate("c")), false);
        f.categoryMap.put("a", "cat1");
        f.categoryMap.put("b", "cat1");
        f.categoryMap.put("c", "cat2");
        f.categories.set(Arrays.asList(new SearchPickerCategories.Category("cat1", "Tabs"),
                new SearchPickerCategories.Category("cat2", "Mods")));
        openPanel(f);

        SceneNode panelRoot = panelCard(overlayRoot(0));
        SceneNode nav = panelRoot.__getChildren().get(1).__getChildren().get(0);
        SceneNode navRows = nav.__getChildren().get(0).__getChildren().get(0).__getChildren().get(0);
        Assert.assertEquals("全部 + 两个非空分类", 3, navRows.__getChildren().size());

        SceneNode grid = f.result.grid().get();
        Assert.assertNotNull(grid);
        Assert.assertEquals("初始网格显示全部候选", 3, mountedItemCount(grid));

        click(navRows.__getChildren().get(1));
        rt.flush();
        layoutAll();
        Assert.assertEquals("cat1", f.result.currentCategoryKey().get());
        Assert.assertEquals("分类切换后网格只剩 cat1 候选", 2,
                mountedItemCount(f.result.grid().get()));

        click(navRows.__getChildren().get(0));
        rt.flush();
        layoutAll();
        Assert.assertNull("切回全部", f.result.currentCategoryKey().get());
        Assert.assertEquals(3, mountedItemCount(f.result.grid().get()));
    }

    @Test
    public void emptyCategoriesHideAndOnlyAllRowRemains() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), false);
        f.categories.set(Arrays.asList(new SearchPickerCategories.Category("cat1", "Tabs")));
        openPanel(f);
        SceneNode nav = panelCard(overlayRoot(0)).__getChildren().get(1).__getChildren().get(0);
        SceneNode navRows = nav.__getChildren().get(0).__getChildren().get(0).__getChildren().get(0);
        Assert.assertEquals("空分类隐藏，仅剩全部行", 1, navRows.__getChildren().size());
    }

    // ==================== 候选点击直达 vs 变体浮层 ====================

    @Test
    public void clickCandidateWithoutVariantsCommitsAndCloses() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), false);
        openPanel(f);
        SceneNode grid = f.result.grid().get();
        click(gridCell(grid, 0));
        Assert.assertEquals(1, f.commits.size());
        Assert.assertEquals("a", f.commits.get(0).candidateKey());
        Assert.assertEquals(SearchPickerData.SelectionMode.ALL, f.commits.get(0).mode());
        Assert.assertEquals("成功提交请求关闭", 1, f.closeRequests.get());
        Assert.assertFalse(f.result.open().get().booleanValue());
        Assert.assertEquals("成功提交不走 onCancel", 0, f.cancels.get());
    }

    @Test
    public void clickCandidateWithVariantsOpensVariantPanelAndEscReturnsToMain() {
        Fixture f = new Fixture(Arrays.asList(candidateWithVariants("a", "v1", "v2"),
                candidate("b")), false);
        openPanel(f);
        SceneNode grid = f.result.grid().get();
        click(gridCell(grid, 0));
        Assert.assertTrue(f.result.variantsOpen().get().booleanValue());
        Assert.assertEquals("变体浮层为次级 overlay", 2, rt.getOverlayHost().size());
        layoutAll();

        pressKey(SceneKey.ESCAPE);
        Assert.assertFalse("ESC 只退回主面板", f.result.variantsOpen().get().booleanValue());
        Assert.assertTrue("主面板保持展开", f.result.open().get().booleanValue());
        Assert.assertEquals("变体浮层 ESC 不触发面板取消", 0, f.cancels.get());
        Assert.assertEquals(1, rt.getOverlayHost().size());
    }

    // ==================== 变体勾选 / ALL-SELECTED / 确认 / 可拒绝提交 ====================

    @Test
    public void variantFlowSupportsAllSelectedToggleAndRejectedCommitStaysOpen() {
        Fixture f = new Fixture(Arrays.asList(candidateWithVariants("a", "v1", "v2")), false);
        openPanel(f);
        click(gridCell(f.result.grid().get(), 0));
        layoutAll();

        SceneNode variantRoot = overlayRoot(0);
        SceneNode card = variantRoot.__getChildren().get(0);
        SceneNode segmented = card.__getChildren().get(1);
        // 变体列表视口 = children[2](listHost stackHost).children[0]
        SceneNode list = card.__getChildren().get(2).__getChildren().get(0);
        SceneNode footer = card.__getChildren().get(3);
        SceneNode confirm = footer.__getChildren().get(1);
        Assert.assertEquals("变体列表初始全量显示", 2, list.__getChildren().size());

        // ALL 模式下勾选行不可点
        click(list.__getChildren().get(0));
        Assert.assertTrue(f.result.variantKeys().get().isEmpty());
        Assert.assertEquals(SearchPickerData.SelectionMode.ALL, f.result.variantMode().get());

        // 切 SELECTED：未勾选时 confirm 不可确认
        click(segmented.__getChildren().get(1));
        rt.flush();
        Assert.assertEquals(SearchPickerData.SelectionMode.SELECTED, f.result.variantMode().get());
        click(confirm);
        Assert.assertTrue("SELECTED 未勾选时不可确认", f.commits.isEmpty());

        // 勾选 v1 → confirm 可提交
        click(list.__getChildren().get(0));
        Assert.assertEquals(Collections.singletonList("v1"), f.result.variantKeys().get());
        click(confirm);
        Assert.assertEquals(1, f.commits.size());
        Assert.assertEquals(SearchPickerData.SelectionMode.SELECTED, f.commits.get(0).mode());
        Assert.assertEquals(Collections.singletonList("v1"), f.commits.get(0).variantKeys());
        Assert.assertEquals("成功提交后关闭", 1, f.closeRequests.get());
        Assert.assertFalse(f.result.open().get().booleanValue());
    }

    @Test
    public void rejectedSelectionCommitKeepsPanelOpen() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), false);
        f.commitResult[0] = false;
        openPanel(f);
        click(gridCell(f.result.grid().get(), 0));
        Assert.assertEquals(1, f.commits.size());
        Assert.assertEquals("提交被拒时不请求关闭", 0, f.closeRequests.get());
        Assert.assertTrue("面板保持展开", f.result.open().get().booleanValue());

        f.commitResult[0] = true;
        click(gridCell(f.result.grid().get(), 0));
        Assert.assertEquals(2, f.commits.size());
        Assert.assertEquals(1, f.closeRequests.get());
        Assert.assertFalse(f.result.open().get().booleanValue());
    }

    @Test
    public void variantPanelBackButtonReturnsToMainPanel() {
        Fixture f = new Fixture(Arrays.asList(candidateWithVariants("a", "v1")), false);
        openPanel(f);
        click(gridCell(f.result.grid().get(), 0));
        layoutAll();
        SceneNode card = overlayRoot(0).__getChildren().get(0);
        SceneNode footer = card.__getChildren().get(3);
        click(footer.__getChildren().get(0));
        Assert.assertFalse(f.result.variantsOpen().get().booleanValue());
        Assert.assertTrue(f.result.open().get().booleanValue());
        Assert.assertEquals(0, f.cancels.get());
    }

    @Test
    public void variantSearchFiltersVariantList() {
        Fixture f = new Fixture(Arrays.asList(candidateWithVariants("a", "oak", "spruce", "birch")),
                false, true, 3);
        openPanel(f);
        click(gridCell(f.result.grid().get(), 0));
        layoutAll();
        SceneNode card = overlayRoot(0).__getChildren().get(0);
        SceneNode search = card.__getChildren().get(1);
        // 变体列表视口 = children[3](listHost stackHost).children[0]
        SceneNode list = card.__getChildren().get(3).__getChildren().get(0);
        Assert.assertEquals(3, list.__getChildren().size());

        rt.requestFocus(search);
        rt.flush();
        typeText("spru");
        layoutAll();
        Assert.assertEquals("变体列表按 query 过滤", 1, list.__getChildren().size());
    }

    // ==================== listMembers 模式 ====================

    private static SearchPickerData.CurrentMember member(long id, String candidateKey) {
        return new SearchPickerData.CurrentMember(id,
                new SearchPickerData.Selection(candidateKey, SearchPickerData.SelectionMode.ALL,
                        Collections.<String>emptyList()),
                candidate(candidateKey), true);
    }

    private static SearchPickerData.CurrentMember malformedMember(long id) {
        return new SearchPickerData.CurrentMember(id, null, null, false);
    }

    @Test
    public void listMembersRendersBadgesAndConfirmRemoveTwoStep() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), true);
        f.members.set(Arrays.asList(member(0L, "a"), malformedMember(1L), member(2L, "a")));
        openPanel(f);

        SceneNode panelRoot = overlayRoot(0);
        SceneNode membersPanel = membersPanel(panelRoot);
        SceneNode rows = membersPanel.__getChildren().get(1);
        Assert.assertEquals(3, rows.__getChildren().size());

        SceneNode row0 = rows.__getChildren().get(0);
        SceneNode row1 = rows.__getChildren().get(1);
        SceneNode row2 = rows.__getChildren().get(2);
        Assert.assertEquals("malformed 徽章", "Error/Invalid", rowBadge(row1).getText());
        Assert.assertEquals("duplicate 徽章", "Warning/Duplicate", rowBadge(row0).getText());
        Assert.assertEquals("duplicate 徽章", "Warning/Duplicate", rowBadge(row2).getText());

        // 删除二次确认：第一次只进入 pending，第二次才提交
        click(rowRemove(row0));
        Assert.assertTrue("第一次点击只进入 pending", f.removeCalls.isEmpty());
        click(rowRemove(row0));
        Assert.assertEquals(Collections.singletonList(Long.valueOf(0L)), f.removeCalls);

        // 编辑触发稳定 memberId 回调
        click(rowEdit(row1));
        Assert.assertEquals(Collections.singletonList(Long.valueOf(1L)), f.edits);
    }

    @Test
    public void listMembersEditWithVariantsOpensVariantPanel() {
        Fixture f = new Fixture(Arrays.asList(candidateWithVariants("a", "v1", "v2")), true);
        f.members.set(Arrays.asList(new SearchPickerData.CurrentMember(0L,
                new SearchPickerData.Selection("a", SearchPickerData.SelectionMode.SELECTED,
                        Collections.singletonList("v2")),
                candidateWithVariants("a", "v1", "v2"), true)));
        openPanel(f);
        SceneNode membersPanel = membersPanel(overlayRoot(0));
        SceneNode row = membersPanel.__getChildren().get(1).__getChildren().get(0);
        click(rowEdit(row));
        rt.flush();
        layoutAll();
        Assert.assertTrue(f.result.variantsOpen().get().booleanValue());
        Assert.assertEquals("编辑恢复当前选择模式", SearchPickerData.SelectionMode.SELECTED,
                f.result.variantMode().get());
        Assert.assertEquals("编辑恢复当前选择 key", Collections.singletonList("v2"),
                f.result.variantKeys().get());
    }


    @Test
    public void listMembersGridClickAddsDirectlyWithoutArming() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), true);
        openPanel(f);
        // 不点底部「添加」按钮，直接点击网格候选
        click(gridCell(f.result.grid().get(), 0));
        Assert.assertEquals("点击即隐式武装新增（含重新武装 = 2 次 beginAdd）", 2, f.beginAdds.get());
        Assert.assertEquals(1, f.commits.size());
        Assert.assertEquals("a", f.commits.get(0).candidateKey());
        Assert.assertTrue("新增后留在面板重新武装", f.result.open().get().booleanValue());
        Assert.assertEquals(0, f.closeRequests.get());
        // 连续点击继续新增（已武装，仅重新武装 +1）
        click(gridCell(f.result.grid().get(), 1));
        Assert.assertEquals(2, f.commits.size());
        Assert.assertEquals("b", f.commits.get(1).candidateKey());
        Assert.assertEquals("两次点击共 3 次 beginAdd", 3, f.beginAdds.get());
    }

    @Test
    public void listMembersVariantConfirmAddsWithoutArming() {
        Fixture f = new Fixture(Arrays.asList(candidateWithVariants("a", "v1")), true);
        openPanel(f);
        click(gridCell(f.result.grid().get(), 0));
        layoutAll();
        // 未点底部「添加」：切 SELECTED、勾选 v1 后确认，同样隐式新增
        SceneNode card = overlayRoot(0).__getChildren().get(0);
        SceneNode segmented = card.__getChildren().get(1);
        SceneNode list = card.__getChildren().get(2).__getChildren().get(0);
        SceneNode footer = card.__getChildren().get(3);
        click(segmented.__getChildren().get(1));
        rt.flush();
        click(list.__getChildren().get(0));
        click(footer.__getChildren().get(1));
        Assert.assertEquals(1, f.commits.size());
        Assert.assertEquals(Collections.singletonList("v1"), f.commits.get(0).variantKeys());
        Assert.assertEquals(SearchPickerData.SelectionMode.SELECTED, f.commits.get(0).mode());
        Assert.assertTrue("确认后留在面板重新武装", f.result.open().get().booleanValue());
        Assert.assertEquals(0, f.closeRequests.get());
        Assert.assertTrue("隐式武装 + 重新武装", f.beginAdds.get() >= 2);
    }

    @Test
    public void listMembersEmptyStateShowsHintWithoutAddButton() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), true);
        openPanel(f);
        SceneNode membersPanel = membersPanel(overlayRoot(0));
        // panel children = [header, rows, emptyContent(show), anchor]
        Assert.assertEquals("空态占位文本", "No current members",
                membersPanel.__getChildren().get(2).getText());

        // 头栏只剩标题与问题摘要（「添加」按钮已移除，点击上方候选即新增）
        SceneNode header = membersPanel.__getChildren().get(0);
        Assert.assertEquals("头栏无添加按钮", 2, header.__getChildren().size());
    }

    @Test
    public void escapeDuringAddingMemberStillCancelsAndCloses() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), true);
        openPanel(f);
        // 点击候选即隐式武装新增（含重新武装 = 2 次 beginAdd）
        click(gridCell(f.result.grid().get(), 0));
        Assert.assertEquals(2, f.beginAdds.get());
        pressKey(SceneKey.ESCAPE);
        Assert.assertEquals("ESC 应先走 onCancel", 1, f.cancels.get());
        Assert.assertEquals("ESC 应请求关闭", 1, f.closeRequests.get());
        Assert.assertFalse("新增中 ESC 仍应关闭面板", f.result.open().get().booleanValue());
        Assert.assertTrue(rt.getOverlayHost().isEmpty());
        Assert.assertEquals("取消不得重复武装新增", 2, f.beginAdds.get());
    }

    // ==================== 上下分区布局与网格高度自适应 ====================

    @Test
    public void singleValueModeHasNoBottomMembersBand() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), false);
        openPanel(f);
        SceneNode panelRoot = panelCard(overlayRoot(0));
        Assert.assertEquals("SINGLE_VALUE 无底部横带：顶栏 + 选择区", 2,
                panelRoot.__getChildren().size());
        SceneNode selectionArea = panelRoot.__getChildren().get(1);
        Assert.assertEquals("选择区 = 分类导航 + 中栏", 2, selectionArea.__getChildren().size());
    }

    @Test
    public void selectionAreaShellsCarryOuterBorders() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), false);
        openPanel(f);
        SceneNode panelRoot = panelCard(overlayRoot(0));
        SceneNode nav = panelRoot.__getChildren().get(1).__getChildren().get(0);
        SceneNode center = panelRoot.__getChildren().get(1).__getChildren().get(1);
        Assert.assertEquals("分类导航外边框 1px", 1, nav.getBorderWidth());
        Assert.assertEquals("分类导航边框色 BORDER_DEFAULT",
                SceneChromeTokens.BORDER_DEFAULT, nav.getBorderColor());
        Assert.assertEquals("中栏外边框 1px", 1, center.getBorderWidth());
        Assert.assertEquals("中栏边框色 BORDER_DEFAULT",
                SceneChromeTokens.BORDER_DEFAULT, center.getBorderColor());
        Assert.assertEquals("中栏实底背景 BG_DEFAULT",
                SceneChromeTokens.BG_DEFAULT, center.getBackgroundColor());
        Assert.assertEquals("中栏圆角 RADIUS_MD", SceneChromeTokens.RADIUS_MD, center.getCornerRadius());
    }

    @Test
    public void listMembersBottomBandContainsMemberRows() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), true);
        f.members.set(Arrays.asList(member(0L, "a"), member(1L, "b")));
        openPanel(f);
        SceneNode panelRoot = panelCard(overlayRoot(0));
        Assert.assertEquals("listMembers：顶栏 + 选择区 + 底部横带", 3,
                panelRoot.__getChildren().size());
        SceneNode band = membersPanel(overlayRoot(0));
        Assert.assertEquals("底部横带含 2 个成员行", 2,
                band.__getChildren().get(1).__getChildren().size());
    }

    @Test
    public void resultListFillsCenterColumnHeight() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), false);
        openPanel(f);
        layoutAll();
        SceneNode grid = f.result.grid().get();
        Assert.assertNotNull(grid);
        Assert.assertTrue("列表视口可滚动", grid.isScrollable());
        LayoutBox tall = (LayoutBox) grid.getCachedLayout();
        Assert.assertNotNull(tall);
        Assert.assertTrue("布局后列表填充中栏高度", tall.getHeight() > 0);

        // 更矮的宿主 → 列表高度随卡片收缩（fillParentHeight 随父链重排）
        layoutOverlayWithHeight(300);
        LayoutBox shortBox = (LayoutBox) grid.getCachedLayout();
        Assert.assertNotNull(shortBox);
        Assert.assertTrue("宿主变矮后列表高度收缩", shortBox.getHeight() < tall.getHeight());
    }

    @Test
    public void defaultGridDerivesColumnsFromAvailableCenterWidth() {
        ArrayList<SearchPickerData.Candidate> candidates = new ArrayList<SearchPickerData.Candidate>();
        for (int i = 0; i < 24; i++) {
            candidates.add(candidate("key" + i));
        }
        Signal<Boolean> open = Signal.create(Boolean.FALSE);
        Signal<SearchPickerData.SearchResult> results = Signal.create(
                new SearchPickerData.SearchResult(candidates));
        Props props = Props.builder(Signal.create(""), results, Signal.create(Boolean.TRUE),
                ignored -> { }, ignored -> { }, visualAdapter())
                .open(open)
                .onCloseRequest(() -> open.set(Boolean.FALSE))
                .grid(GridProps.of(0, 64, 64, 8, 8, 3))
                .build();
        Result result = ScenePickerPanel.create(rt, props);
        sceneRoot.appendChild(result.root());
        rt.flush();

        open.set(Boolean.TRUE);
        rt.flush();
        layoutAll();
        layoutAll();

        SceneNode grid = result.grid().get();
        Assert.assertNotNull(grid);
        LayoutBox viewportBox = (LayoutBox) grid.getCachedLayout();
        Assert.assertNotNull(viewportBox);
        int expected = SceneVirtualGridNav.deriveColumns(viewportBox.getWidth(), 64, 8);
        Assert.assertTrue("70% 面板中栏至少容纳 4 列", expected >= 4);
        SceneNode rowsContainer = grid.__getChildren().get(0);
        Assert.assertEquals("首行单元数 = 自动推导列数", expected,
                rowsContainer.__getChildren().get(0).__getChildren().size());
    }

    // ==================== 键盘导航与焦点意图 ====================

    @Test
    public void keyboardNavHighlightsAndEnterActivates() {
        ArrayList<SearchPickerData.Candidate> candidates = new ArrayList<SearchPickerData.Candidate>();
        for (int i = 0; i < 6; i++) {
            candidates.add(candidate("key" + i));
        }
        Fixture f = new Fixture(candidates, false);
        openPanel(f);
        SceneNode grid = f.result.grid().get();
        rt.requestFocus(grid);
        rt.flush();
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals(Integer.valueOf(0), f.result.gridHighlight().get());
        pressKey(SceneKey.ARROW_DOWN);
        Assert.assertEquals("下移一行保持列", Integer.valueOf(3), f.result.gridHighlight().get());
        pressKey(SceneKey.ENTER);
        Assert.assertEquals(1, f.commits.size());
        Assert.assertEquals("key3", f.commits.get(0).candidateKey());
    }

    // ==================== 数据收缩回夹 ====================

    @Test
    public void dataShrinkClampsGridScrollAndHighlight() {
        ArrayList<SearchPickerData.Candidate> candidates = new ArrayList<SearchPickerData.Candidate>();
        for (int i = 0; i < 12; i++) {
            candidates.add(candidate("key" + i));
        }
        Fixture f = new Fixture(candidates, false);
        openPanel(f);
        SceneNode grid = f.result.grid().get();
        // 滚轮向下滚超量：SceneScrolls handler 内部 clamp 到 maxScrollY
        routeScrollAt(grid, -5000);
        rt.flush();
        Assert.assertEquals("滚动夹取到最大", SceneGeometry.maxScrollY(grid),
                grid.getScrollOffsetY());

        // 键盘导航把高亮推到 10（12 项、3 列）
        rt.requestFocus(grid);
        rt.flush();
        for (int i = 0; i < 4; i++) {
            pressKey(SceneKey.ARROW_DOWN);
        }
        pressKey(SceneKey.ARROW_RIGHT);
        Assert.assertEquals(Integer.valueOf(10), f.result.gridHighlight().get());

        // 收缩到 2 项：滚动归零、高亮夹取到末项
        f.results.set(new SearchPickerData.SearchResult(
                Arrays.asList(candidate("key0"), candidate("key1"))));
        rt.flush();
        layoutAll();
        Assert.assertEquals("数据收缩后滚动归零", 0, grid.getScrollOffsetY());
        Assert.assertEquals("高亮夹取到数据范围", Integer.valueOf(1),
                f.result.gridHighlight().get());
    }

    // ==================== 受控开合/分类/维度接线 ====================

    @Test
    public void internalOpenSignalIsSelfManagedAndWritable() {
        Signal<SearchPickerData.SearchResult> results = Signal.create(
                new SearchPickerData.SearchResult(Arrays.asList(candidate("a"))));
        Props props = Props.builder(Signal.create(""), results, Signal.create(Boolean.TRUE),
                ignored -> { }, ignored -> { }, visualAdapter()).build();
        Result result = ScenePickerPanel.create(rt, props);
        sceneRoot.appendChild(result.root());
        rt.flush();
        Assert.assertNotNull("默认形态 openSignal 可写", result.openSignal());

        result.openSignal().set(Boolean.TRUE);
        rt.flush();
        layoutAll();
        Assert.assertEquals(1, rt.getOverlayHost().size());

        pressKey(SceneKey.ESCAPE);
        Assert.assertFalse("ESC 后组件自动写回内部开合信号",
                result.openSignal().get().booleanValue());
        Assert.assertTrue(rt.getOverlayHost().isEmpty());
    }

    @Test
    public void controlledOpenCategoryAndDimensionWireThrough() {
        Signal<Boolean> open = Signal.create(Boolean.FALSE);
        Signal<String> categoryKey = Signal.create(null);
        Signal<Integer> dimensionIndex = Signal.create(Integer.valueOf(0));
        AtomicInteger closeRequests = new AtomicInteger();
        Signal<SearchPickerData.SearchResult> results = Signal.create(
                new SearchPickerData.SearchResult(Arrays.asList(candidate("a"), candidate("b"))));
        Props props = Props.builder(Signal.create(""), results, Signal.create(Boolean.TRUE),
                ignored -> { }, ignored -> { }, visualAdapter())
                .open(open)
                .onCloseRequest(() -> {
                    closeRequests.incrementAndGet();
                    open.set(Boolean.FALSE);
                })
                .categories(Signal.create(Arrays.asList(
                        new SearchPickerCategories.Category("cat1", "Tabs"))))
                .categoryOf(key -> "cat1")
                .currentCategoryKey(categoryKey, categoryKey::set)
                .dimension(dimensionIndex, dimensionIndex::set)
                .panelPresentation(SearchPickerPanelPresentation.builder()
                        .categoryDimensions(Arrays.asList("Creative Tabs", "By Mod")).build())
                .build();
        Result result = ScenePickerPanel.create(rt, props);
        sceneRoot.appendChild(result.root());
        rt.flush();
        Assert.assertNull("受控形态 openSignal 为 null", result.openSignal());

        open.set(Boolean.TRUE);
        rt.flush();
        layoutAll();
        Assert.assertEquals(1, rt.getOverlayHost().size());

        SceneNode panelRoot = panelCard(overlayRoot(0));
        // 分类切换经受控回调写回外部信号
        SceneNode nav = panelRoot.__getChildren().get(1).__getChildren().get(0);
        click(nav.__getChildren().get(0).__getChildren().get(0)
                .__getChildren().get(0).__getChildren().get(1));
        Assert.assertEquals("cat1", categoryKey.get());

        // 维度切换经受控回调写回外部信号
        SceneNode topBar = panelRoot.__getChildren().get(0);
        SceneNode segmented = topBar.__getChildren().get(2);
        click(segmented.__getChildren().get(1));
        Assert.assertEquals(Integer.valueOf(1), dimensionIndex.get());

        // 受控关闭：ESC → onCancel(默认空) + onCloseRequest 把 open 置 false
        pressKey(SceneKey.ESCAPE);
        Assert.assertFalse(open.get().booleanValue());
        Assert.assertEquals(1, closeRequests.get());
        Assert.assertTrue(rt.getOverlayHost().isEmpty());
    }
}
