package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.PickerCandidateSource;
import club.heiqi.config.ui.editor.PickerQuery;
import club.heiqi.config.ui.editor.PickerSourceVersion;
import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.config.ui.field.PickerSourceGuard;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneGridWindow;
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
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 面板页窗口切片生产装配（P3 交接 U-1 / ADR §3.2「唯一实现 = ScenePickerPanel」）。
 *
 * <p>覆盖：面板自建 {@code pageProvider} 闭包按<b>控件产出的</b>窗口请求向 {@link PickerCandidateSource}
 * 拉片；总量 = 浏览 lane 的 {@code size()} / 搜索 lane 的 {@code min(matchCount, maxItems)}；
 * 截断真值进入信息条；激活走 {@code exact(key)} 免全表回查；关闭后不再触碰候选源、重开恢复；
 * 源版本信号变化（语言/资源/注册表代际）触发重查；调用点携带运行期主线程断言。</p>
 */
public class ScenePickerPanelSpiWindowTest {

    private static final int W = 800;
    private static final int H = 600;
    private static final int COLUMNS = 4;
    private static final int SEARCH_MAX_ITEMS = 64;

    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private SceneNode sceneRoot;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        PickerSourceGuard.__resetForTests();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
        PickerSourceGuard.__resetForTests();
    }

    // ==================== 浏览 lane：无上限 + 窗口切片 ====================

    /**
     * 浏览 lane：总量 = {@code source.size()}（无 cap/分页），分片按窗口拉取且请求量有界；
     * 滚到底后末项可达（数据范围不被窗口裁剪）。
     */
    @Test
    public void browseLaneTotalsFromSourceAndPullsBoundedWindows() {
        FakeSource source = new FakeSource(5000);
        SpiFixture f = new SpiFixture(source, SEARCH_MAX_ITEMS);
        f.openPanel();

        SceneGridWindow.WindowModel model = windowModel(f);
        Assert.assertEquals("浏览 lane 总量 = source.size()（无上限）", 5000, model.totalItems());
        Assert.assertEquals(1250, model.totalRows());
        Assert.assertEquals("matchCount 不得出现在浏览 lane", 0, source.matchCountCalls);
        Assert.assertEquals("size() 每次 lane 求值读一次（O(1)）", 1, source.sizeCalls);
        Assert.assertTrue("窗口切片必须由面板拉取", source.pageCalls > 0);
        Assert.assertTrue("单次请求量 = 挂载行 × 列数（与 N 无关）：" + source.lastPageLimit,
                source.lastPageLimit <= (model.visibleRows() + 1) * COLUMNS);
        Assert.assertEquals("inv-W1：请求 offset 按整行对齐", 0, source.lastPageOffset % COLUMNS);
        Assert.assertTrue("挂载单元数与 N 无关", mountedItemCount(f) <= (model.visibleRows() + 1) * COLUMNS);

        // 滚到底：窗口推进到末行，末项（k5000）进入挂载窗口。
        routeScrollAt(f.viewport(), -1_000_000);
        rt.flush();
        layoutAll();
        SceneGridWindow.WindowModel bottom = windowModel(f);
        Assert.assertEquals("可滚到底", bottom.maxStartRow(), bottom.windowStartRow());
        Assert.assertEquals("数据范围不被裁剪", 5000, bottom.totalItems());
        Assert.assertTrue("末项必须进入挂载窗口: " + cellLabels(f),
                cellLabels(f).contains("k5000"));
    }

    // ==================== 搜索 lane：上限 + 截断明示 ====================

    /** 搜索 lane：总量 = min(matchCount, searchMaxItems)，请求量不超上限，信息条出现截断提示。 */
    @Test
    public void searchLaneCapsWindowAndSurfacesTruncation() {
        FakeSource source = new FakeSource(5000);
        source.textHits = 200;
        SpiFixture f = new SpiFixture(source, SEARCH_MAX_ITEMS);
        f.openPanel();

        f.query.set("stone");
        rt.flush();
        layoutAll();

        SceneGridWindow.WindowModel model = windowModel(f);
        Assert.assertEquals("搜索 lane 总量 = min(matchCount, maxItems)", SEARCH_MAX_ITEMS, model.totalItems());
        Assert.assertTrue("搜索 lane 必须统计真实命中数", source.matchCountCalls >= 1);
        Assert.assertTrue("请求量不得超过搜索上限：" + source.lastPageLimit,
                source.lastPageLimit <= SEARCH_MAX_ITEMS);
        // P5 §3.3：截断提示落在<b>顶栏统计行右侧</b>（「与统计同行」），不再占用信息条。
        // 断言不降级：截断文案仍必须有可见落点。
        Assert.assertTrue("截断必须明示（顶栏统计行）：" + allText(f.panelRoot()),
                allText(f.panelRoot())
                        .contains(f.panelPresentation().truncatedResults()));

        source.textHits = 10;
        f.query.set("st");
        rt.flush();
        layoutAll();
        Assert.assertEquals("命中数低于上限时总量 = 命中数", 10, windowModel(f).totalItems());
        Assert.assertFalse("未截断时不得出现截断提示",
                allText(f.panelRoot())
                        .contains(f.panelPresentation().truncatedResults()));
    }

    // ==================== 激活路径：exact 免全表回查 ====================

    /** 点击候选：经 {@code source.exact(key)} 取候选本体（不再对窗口切片线性扫描），提交键正确。 */
    @Test
    public void activationResolvesCandidateViaExactWithoutScanningWindow() {
        FakeSource source = new FakeSource(5000);
        SpiFixture f = new SpiFixture(source, SEARCH_MAX_ITEMS);
        f.openPanel();

        SceneNode cell = gridCell(f.viewport(), 2);
        String key = cellLabel(cell);
        click(cell);
        rt.flush();

        Assert.assertEquals("每个被激活的 key 恰好一次 exact", 1, source.exactCalls);
        Assert.assertEquals("提交的必须是该单元的候选键", key, f.commits.get(0).candidateKey());
    }

    // ==================== 关闭即停算 + 重开恢复 ====================

    /**
     * 关闭后不再触碰候选源（含版本信号变化），重开恢复且每次只拉窗口量的分片
     * ——「不打开不花钱、打开不重建」在 SPI 路径的机器证据。
     */
    @Test
    public void closedPanelStopsTouchingSourceAndReopenPullsOnlyWindowSlices() {
        FakeSource source = new FakeSource(5000);
        SpiFixture f = new SpiFixture(source, SEARCH_MAX_ITEMS);
        f.openPanel();
        int pagesAfterFirstOpen = source.pageCalls;
        Assert.assertTrue("首次打开必须拉片", pagesAfterFirstOpen > 0);
        Assert.assertTrue("首次打开的分片量有界：" + source.lastPageLimit,
                source.lastPageLimit <= (windowModel(f).visibleRows() + 1) * COLUMNS);

        f.open.set(Boolean.FALSE);
        rt.flush();
        layoutAll();
        int sizeClosed = source.sizeCalls;
        int pagesClosed = source.pageCalls;
        f.version.set(new PickerSourceVersion(7L, 0L, 0L));
        rt.flush();
        layoutAll();
        Assert.assertEquals("关闭后版本变化不得触发清单读取", sizeClosed, source.sizeCalls);
        Assert.assertEquals("关闭后不得拉取分片", pagesClosed, source.pageCalls);

        f.open.set(Boolean.TRUE);
        rt.flush();
        layoutAll();
        layoutAll();
        Assert.assertTrue("重开后重新求值 lane", source.sizeCalls > sizeClosed);
        Assert.assertTrue("重开后重新拉片", source.pageCalls > pagesClosed);
        Assert.assertEquals("重开只需一次清单读取", sizeClosed + 1, source.sizeCalls);
        Assert.assertTrue("重开不得整表物化：分片量有界", source.lastPageLimit <= (windowModel(f).visibleRows() + 1) * COLUMNS);
    }

    /** 版本信号（语言/资源/注册表代际）变化 → lane 重查：总量随源变化即时更新。 */
    @Test
    public void sourceVersionChangeRequeriesLane() {
        FakeSource source = new FakeSource(5000);
        SpiFixture f = new SpiFixture(source, SEARCH_MAX_ITEMS);
        f.openPanel();
        Assert.assertEquals(5000, windowModel(f).totalItems());
        int sizeBefore = source.sizeCalls;

        source.total = 7;
        f.version.set(new PickerSourceVersion(1L, 0L, 0L));
        rt.flush();
        layoutAll();

        Assert.assertTrue("版本变化必须重新读取源", source.sizeCalls > sizeBefore);
        Assert.assertEquals("清单变化即时反映到总量", 7, windowModel(f).totalItems());
    }

    // ==================== 调用点运行期断言 ====================

    /** SPI 调用点携带运行期主线程断言：打开 + 查询 + 激活路径都必须经过 {@link PickerSourceGuard}。 */
    @Test
    public void panelSourceCallSitesCarryRuntimeThreadAssertion() {
        final int[] checks = { 0 };
        PickerSourceGuard.__installThreadOracleForTests(new PickerSourceGuard.ThreadOracle() {
            @Override
            public boolean isMainThread() {
                checks[0]++;
                return true;
            }

            @Override
            public String describe() {
                return "counting-oracle";
            }
        });
        FakeSource source = new FakeSource(500);
        SpiFixture f = new SpiFixture(source, SEARCH_MAX_ITEMS);
        f.openPanel();
        f.query.set("stone");
        rt.flush();
        layoutAll();

        Assert.assertTrue("面板调用点必须经 PickerSourceGuard.requireMainThread 断言", checks[0] > 0);
    }

    // ==================== 已配置候选：可见 + 可区分 + 点击语义（T5 UX-18 / D-P4-3） ====================

    /**
     * SPI 路径不排除「已配置成员」候选（修 P4 偏差 D-P4-3 的可见回归），且该状态必须可区分：
     * 单元侧圆点（形态）+ 信息条文案（语义，经 Presentation 注入）；点击仍走既有激活语义
     * （不静默丢弃、不引入第二套提交路径）。
     */
    @Test
    public void configuredCandidatesStayVisibleMarkedAndActivateNormally() {
        FakeSource source = new FakeSource(5000);
        SpiFixture f = new SpiFixture(source, SEARCH_MAX_ITEMS);
        f.configureMember("k2");
        f.openPanel();

        Assert.assertTrue("已配置候选不得从结果中消失: " + cellLabels(f), cellLabels(f).contains("k2"));
        Assert.assertTrue("未配置候选照常可见: " + cellLabels(f), cellLabels(f).contains("k1"));

        SceneNode configuredCell = cellByKey(f.viewport(), "k2");
        SceneNode plainCell = cellByKey(f.viewport(), "k1");
        Assert.assertTrue("已配置单元必须挂可见标记", markerOf(configuredCell).getPreferredWidth() > 0);
        Assert.assertEquals("未配置单元不得占用标记宽度（零占位）",
                0, markerOf(plainCell).getPreferredWidth());

        // 信息条语义：悬停已配置候选 → 追加「已配置」文案；悬停未配置候选 → 不追加。
        hover(configuredCell);
        Assert.assertTrue("信息条必须标注已配置（文案经 Presentation）: " + allText(f.panelRoot()),
                allText(f.panelRoot()).contains(f.panelPresentation().alreadyConfiguredBadge()));
        hover(plainCell);
        Assert.assertFalse("未配置候选不得出现已配置标记: " + allText(f.panelRoot()),
                allText(f.panelRoot()).contains(f.panelPresentation().alreadyConfiguredBadge()));

        // 点击语义明确：与未配置候选同一条激活路径（提交该单元候选键），不做静默丢弃。
        click(configuredCell);
        rt.flush();
        Assert.assertEquals("点击已配置候选必须仍走激活路径（一次提交）", 1, f.commits.size());
        Assert.assertEquals("k2", f.commits.get(0).candidateKey());
    }

    /** 指针移到面板外角落（hover 移出）。 */
    private void moveAway() {
        InputFrameBuilder fb = new InputFrameBuilder(1, 1);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE, 1, 1,
                SceneMouseButton.NONE, 0, 0, 0, false, false, false, false, 1001L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
        layoutAll();
    }

    /** 成员卡徽章：cell 顶行 = [icon, primary, badge]（跨行平铺取第 index 张卡）。 */
    private static SceneNode memberBadge(SceneNode panel, int index) {
        for (SceneNode row : memberRows(panel).__getChildren()) {
            if (index < row.__getChildren().size()) {
                return row.__getChildren().get(index).__getChildren().get(0).__getChildren().get(2);
            }
            index -= row.__getChildren().size();
        }
        throw new IllegalStateException("member cell index out of mounted grid: " + index);
    }

    /** 悬停一个已布局单元：先声明 hover 关心（时序契约），再路由指针 MOVE。 */
    private void hover(SceneNode node) {
        rt.interactionState(node).hovered();
        int[] center = centerOf(node);
        InputFrameBuilder fb = new InputFrameBuilder(center[0], center[1]);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE, center[0], center[1],
                SceneMouseButton.NONE, 0, 0, 0, false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
        layoutAll();
    }

    // ==================== 徽章 hover 原因（D5） ====================

    /**
     * D5（P5 §5.4）：悬停成员徽章 → 信息条给出原因（严重级 + 问题 + 稳定 ID），文案全部经
     * Presentation 注入；移出后回到空闲提示（不留原因文案）。
     *
     * <p>默认英文档模板定值为 {@code {severity} · {issue} · ID: {id}}：无效成员 ID = {@code #memberId}
     * （raw 无法解析为候选选择），重复成员 ID = 候选 key；raw 展示文本为空时不追加尾巴。</p>
     */
    @Test
    public void memberBadgeHoverExplainsIssueThroughPresentation() {
        FakeSource source = new FakeSource(20);
        SpiFixture f = new SpiFixture(source, SEARCH_MAX_ITEMS);
        f.members.set(Arrays.asList(
                new SearchPickerData.CurrentMember(0L, null, null, false),
                new SearchPickerData.CurrentMember(1L,
                        new SearchPickerData.Selection("k2", SearchPickerData.SelectionMode.ALL,
                                Collections.<String>emptyList()), null, false),
                new SearchPickerData.CurrentMember(2L,
                        new SearchPickerData.Selection("k2", SearchPickerData.SelectionMode.ALL,
                                Collections.<String>emptyList()), null, false)));
        f.openPanel();
        SceneNode panel = f.panelRoot();

        String invalidReason = f.presentation().memberIssueReason(
                f.presentation().errorSeverity(), f.presentation().invalidIssue(), "#0", "");
        Assert.assertEquals("默认英文档原因模板定值", "Error · Invalid · ID: #0", invalidReason);
        String duplicateReason = f.presentation().memberIssueReason(
                f.presentation().warningSeverity(), f.presentation().duplicateIssue(), "k2", "");
        Assert.assertEquals("默认英文档重复原因模板定值", "Warning · Duplicate · ID: k2", duplicateReason);

        hover(memberBadge(panel, 0));
        Assert.assertTrue("无效徽章 hover 必须给出原因（经 Presentation）: " + allText(panel),
                allText(panel).contains(invalidReason));
        hover(memberBadge(panel, 1));
        Assert.assertTrue("重复徽章 hover 必须给出原因（经 Presentation）: " + allText(panel),
                allText(panel).contains(duplicateReason));
        Assert.assertFalse("原因必须按当前 hover 成员切换，不得残留上一条: " + allText(panel),
                allText(panel).contains(invalidReason));

        // 移出徽章：原因为空 ⇒ 信息条回落空闲提示（不留原因文案）。
        moveAway();
        Assert.assertFalse("移出后不得残留原因文案: " + allText(panel),
                allText(panel).contains(duplicateReason));
        Assert.assertTrue("移出后应回到空闲提示: " + allText(panel),
                allText(panel).contains(f.presentation().searchResultsTitle()));
    }

    // ==================== Tab 环闭合（A7） ====================

    /**
     * A7（P5 §5.1）：Tab 环闭合守卫。
     *
     * <p>不变量：① Tab 环严格限制在面板 overlay 子树内 —— 宿主侧可聚焦节点（即便存在）永不进入环；
     * ② 一个完整环恰好访问面板内每个 focusable 一次（无跳过、无死循环），第 K 次后回到起点；
     * ③ 环内必须包含判据点名的四类落点：搜索框 → 分类导航行 → 结果网格 → 成员操作按钮；
     * ④ Shift+Tab 为同环逆序（一环之前 = 环尾）。</p>
     */
    @Test
    public void tabRingStaysInsidePanelAndCoversSearchNavGridAndMembers() {
        FakeSource source = new FakeSource(20);
        SpiFixture f = new SpiFixture(source, SEARCH_MAX_ITEMS);
        f.configureMember("k2");
        f.configureMember("k3");
        f.openPanel();

        // 宿主侧可聚焦节点（面板外）：Tab 环不得把它吞进来（不逃逸到宿主）。
        SceneNode hostTrigger = new SceneNode();
        hostTrigger.setPreferredHeight(20);
        sceneRoot.appendChild(hostTrigger);
        rt.focusable(hostTrigger);
        layoutAll();

        SceneNode overlayRoot = rt.getOverlayHost().bottomFirst().get(0).getRoot();
        SceneNode start = rt.getFocusedNode();
        Assert.assertNotNull("面板打开后必须有首焦点", start);
        Assert.assertSame("面板打开首焦点 = 搜索框", searchInputRoot(f.panelRoot()), start);

        ArrayList<SceneNode> cycle = new ArrayList<SceneNode>();
        cycle.add(start);
        for (int guard = 0; guard < 64; guard++) {
            pressTab(false);
            SceneNode next = rt.getFocusedNode();
            Assert.assertNotNull("Tab 后焦点不得为空", next);
            Assert.assertNotSame("Tab 环不得逃逸到宿主可聚焦节点", hostTrigger, next);
            Assert.assertTrue("Tab 环必须留在面板 overlay 子树内: " + describe(next),
                    isWithin(next, overlayRoot));
            if (next == start) {
                break;
            }
            Assert.assertFalse("Tab 环内不得重复访问同一节点（无跳过/无死循环）: " + describe(next),
                    cycle.contains(next));
            cycle.add(next);
        }
        Assert.assertSame("Tab 环必须闭合（回到起点）", start, rt.getFocusedNode());
        Assert.assertTrue("环长必须 > 1（搜索→导航→网格→成员）", cycle.size() > 1);
        Assert.assertTrue("环内必须包含结果网格（C1/C2 落点）", cycle.contains(f.viewport()));
        Assert.assertTrue("环内必须包含分类导航行（C4 落点）",
                containsWithin(cycle, categoryNavRows(f.panelRoot())));
        Assert.assertTrue("环内必须包含搜索输入（A3/C3 落点）",
                containsWithin(cycle, searchInputRoot(f.panelRoot())));
        Assert.assertTrue("环内必须包含成员操作按钮（成员带落点）",
                containsWithin(cycle, memberRows(f.panelRoot())));

        // Shift+Tab 逆序：起点反向一步 = 环尾；再正向一步回到起点。
        pressTab(true);
        Assert.assertSame("Shift+Tab 逆序回到环尾", cycle.get(cycle.size() - 1), rt.getFocusedNode());
        pressTab(false);
        Assert.assertSame("正向再一步回到起点", start, rt.getFocusedNode());
    }

    // ==================== 宿主帧驱动 ====================

    private void layoutAll() {
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        for (SceneOverlayHost.Entry entry : rt.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(W, H));
        }
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    private SceneGridWindow.WindowModel windowModel(SpiFixture f) {
        return f.result.windowModel().get();
    }

    private int mountedItemCount(SpiFixture f) {
        int count = 0;
        for (SceneNode row : rowsContainer(f.viewport()).__getChildren()) {
            count += row.__getChildren().size();
        }
        return count;
    }

    private static List<String> cellLabels(SpiFixture f) {
        return cellLabels(f.viewport());
    }

    private static List<String> cellLabels(SceneNode viewport) {
        List<String> labels = new ArrayList<String>();
        for (SceneNode row : rowsContainer(viewport).__getChildren()) {
            for (SceneNode cell : row.__getChildren()) {
                labels.add(cellLabel(cell));
            }
        }
        return labels;
    }

    /** 单元标签文本（单元结构 = column[icon, label]，圆点挂在图位内部）。 */
    private static String cellLabel(SceneNode cell) {
        return cell.__getChildren().get(1).getText();
    }

    /** 「已配置」标记圆点（图位节点的唯一子节点）。 */
    private static SceneNode markerOf(SceneNode cell) {
        return cell.__getChildren().get(0).__getChildren().get(0);
    }

    private static SceneNode cellByKey(SceneNode viewport, String key) {
        for (SceneNode row : rowsContainer(viewport).__getChildren()) {
            for (SceneNode cell : row.__getChildren()) {
                if (key.equals(cellLabel(cell))) return cell;
            }
        }
        throw new IllegalStateException("cell not mounted for key: " + key);
    }

    private static SceneNode rowsContainer(SceneNode viewport) {
        return viewport.__getChildren().get(0).__getChildren().get(1);
    }

    private static SceneNode gridCell(SceneNode viewport, int index) {
        for (SceneNode row : rowsContainer(viewport).__getChildren()) {
            if (index < row.__getChildren().size()) return row.__getChildren().get(index);
            index -= row.__getChildren().size();
        }
        throw new IllegalStateException("cell index out of mounted window: " + index);
    }

    private static SceneNode centerColumn(SceneNode panelRoot) {
        return panelRoot.__getChildren().get(1).__getChildren().get(1);
    }

    private static String allText(SceneNode node) {
        StringBuilder builder = new StringBuilder();
        collectText(node, builder);
        return builder.toString();
    }

    private static void collectText(SceneNode node, StringBuilder out) {
        if (node.getText() != null && !node.getText().isEmpty()) out.append(node.getText()).append('\n');
        for (SceneNode child : node.__getChildren()) collectText(child, out);
    }

    /** Tab / Shift+Tab 一帧：经 Router 默认焦点遍历（focusScope = 栈顶 overlay）。 */
    private void pressTab(boolean shift) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(SceneKey.TAB, SceneKeyAction.PRESSED,
                false, shift, false, false, 0, 0, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
        layoutAll();
    }

    /** @return 节点是否位于 scope 子树内（含自身；父链遍历，不依赖 focusable 注册表） */
    private static boolean isWithin(SceneNode node, SceneNode scope) {
        for (SceneNode current = node; current != null; current = current.__getParent()) {
            if (current == scope) return true;
        }
        return false;
    }

    /** @return 环内是否存在落在 scope 子树内的落点 */
    private static boolean containsWithin(List<SceneNode> cycle, SceneNode scope) {
        for (SceneNode node : cycle) {
            if (isWithin(node, scope)) return true;
        }
        return false;
    }

    private static String describe(SceneNode node) {
        return node == null ? "null" : (node.getText() == null || node.getText().isEmpty()
                ? node.toString() : node.getText());
    }

    /** 面板顶栏 = [标题, 搜索输入, (维度分段), 统计, 关闭]。 */
    private static SceneNode searchInputRoot(SceneNode panel) {
        return panel.__getChildren().get(0).__getChildren().get(1);
    }

    /** 左栏分类导航行容器：中栏 = [导航列, 结果列]；导航列 = [维度标题, 滚动容器, 状态行]。 */
    private static SceneNode categoryNavRows(SceneNode panel) {
        return panel.__getChildren().get(1).__getChildren().get(0)
                .__getChildren().get(1).__getChildren().get(0).__getChildren().get(0);
    }

    /**
     * 成员带卡片行容器：membersPanel = [header, 模式横幅, gridRoot, 空态, 撤销条]；
     * gridRoot = [viewport, 滚动条]，viewport[0] = 行容器（行即容器子节点，与 MemberGridTest 同路径）。
     */
    private static SceneNode memberRows(SceneNode panel) {
        return panel.__getChildren().get(2).__getChildren().get(2)
                .__getChildren().get(0).__getChildren().get(0);
    }

    private void click(SceneNode node) {
        int[] center = centerOf(node);
        InputFrameBuilder fb = new InputFrameBuilder(center[0], center[1]);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, center[0], center[1],
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, center[0], center[1],
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1001L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
    }

    private void routeScrollAt(SceneNode node, int wheelDelta) {
        int[] center = centerOf(node);
        InputFrameBuilder fb = new InputFrameBuilder(center[0], center[1]);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, center[0], center[1],
                SceneMouseButton.NONE, wheelDelta, 0, 0,
                false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
    }

    private static int[] centerOf(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            throw new IllegalStateException("节点未布局或零尺寸，无法取中心: " + box);
        }
        return new int[] { box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2 };
    }

    // ==================== 夹具 ====================

    /** SPI 面板夹具：惰性源 + 受控查询条件（维度/分类键）+ 版本信号 + 受控开合。 */
    private final class SpiFixture {
        final Signal<Boolean> open = Signal.create(Boolean.FALSE);
        final Signal<String> query = Signal.create("");
        final Signal<Integer> dimension = Signal.create(Integer.valueOf(0));
        final Signal<String> categoryKey = Signal.create(null);
        final Signal<PickerSourceVersion> version = Signal.create(PickerSourceVersion.initial());
        final Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(
                Collections.<SearchPickerData.CurrentMember>emptyList());
        final List<SearchPickerData.Selection> commits = new ArrayList<SearchPickerData.Selection>();
        final Result result;
        private final club.heiqi.config.ui.editor.SearchPickerPanelPresentation panelPresentation;
        private final club.heiqi.config.ui.editor.SearchPickerPresentation presentation;

        SpiFixture(PickerCandidateSource source, int searchMaxItems) {
            Signal<Integer> dimensionSignal = dimension;
            Signal<String> categorySignal = categoryKey;
            Computed<PickerQuery> sourceQuery = Computed.create(() -> PickerQuery.text(
                    query.get(), dimensionSignal.get().intValue(), categorySignal.get()));
            Props props = Props.builder(query, Signal.create(SearchPickerData.SearchResult.empty()),
                    Signal.create(Boolean.TRUE), query::set, commits::add, visualAdapter())
                    .open(open)
                    .onCloseRequest(() -> open.set(Boolean.FALSE))
                    .grid(GridProps.of(COLUMNS, 64, 64, 8, 8, 3))
                    .currentMembers(members, memberId -> { })
                    .candidateSource(source, searchMaxItems, sourceQuery, version)
                    .build();
            panelPresentation = props.panelPresentation();
            presentation = props.presentation();
            result = ScenePickerPanel.create(rt, props);
            sceneRoot.appendChild(result.root());
        }

        /** 把某个候选键标成「已在当前规则中」（成员 selection 非 null 即视为已配置）。 */
        void configureMember(String candidateKey) {
            members.set(Arrays.asList(new SearchPickerData.CurrentMember(0L,
                    new SearchPickerData.Selection(candidateKey, SearchPickerData.SelectionMode.ALL,
                            Collections.<String>emptyList()), null, false)));
        }

        void openPanel() {
            open.set(Boolean.TRUE);
            rt.flush();
            layoutAll();
            layoutAll();
        }

        SceneNode panelRoot() {
            return rt.getOverlayHost().bottomFirst().get(0).getRoot().__getChildren().get(0);
        }

        SceneNode viewport() {
            return result.grid().get();
        }

        club.heiqi.config.ui.editor.SearchPickerPanelPresentation panelPresentation() {
            return panelPresentation;
        }

        club.heiqi.config.ui.editor.SearchPickerPresentation presentation() {
            return presentation;
        }
    }

    private static VisualAdapter visualAdapter() {
        return new VisualAdapter() {
            @Override
            public String candidateLabel(SearchPickerData.Candidate candidate) {
                return candidate.key();
            }

            @Override
            public String variantLabel(SearchPickerData.Variant variant) {
                return variant.label();
            }
        };
    }

    /** 内存候选源替身：记录调用次数与最近一次 page 窗口；浏览 lane 总量 = total，搜索 lane 命中 = textHits。 */
    private static final class FakeSource implements PickerCandidateSource {
        private int total;
        private int textHits = -1;
        private int sizeCalls;
        private int pageCalls;
        private int matchCountCalls;
        private int exactCalls;
        private int categoriesCalls;
        private int lastPageOffset = -1;
        private int lastPageLimit = -1;

        FakeSource(int total) {
            this.total = total;
        }

        @Override
        public int size() {
            sizeCalls++;
            return total;
        }

        @Override
        public long registryRevision() {
            return 0L;
        }

        @Override
        public long nameRevision() {
            return 0L;
        }

        @Override
        public long iconRevision() {
            return 0L;
        }

        @Override
        public int matchCount(PickerQuery query) {
            matchCountCalls++;
            return query.isBrowse() ? total : (textHits < 0 ? total : textHits);
        }

        @Override
        public List<SearchPickerData.Candidate> page(PickerQuery query, int offset, int limit) {
            pageCalls++;
            lastPageOffset = offset;
            lastPageLimit = limit;
            int available = query.isBrowse() ? total : (textHits < 0 ? total : textHits);
            ArrayList<SearchPickerData.Candidate> out = new ArrayList<SearchPickerData.Candidate>();
            for (int index = offset; index < Math.min(available, offset + limit); index++) {
                out.add(candidate("k" + (index + 1)));
            }
            return out;
        }

        @Override
        public SearchPickerData.Candidate exact(String candidateKey) {
            exactCalls++;
            return candidateKey.startsWith("k") ? candidate(candidateKey) : null;
        }

        @Override
        public List<SearchPickerCategories.Category> categories(int dimension) {
            categoriesCalls++;
            return Collections.emptyList();
        }

        private static SearchPickerData.Candidate candidate(String key) {
            return new SearchPickerData.Candidate(key, key,
                    Collections.<SearchPickerData.Variant>emptyList());
        }
    }
}
