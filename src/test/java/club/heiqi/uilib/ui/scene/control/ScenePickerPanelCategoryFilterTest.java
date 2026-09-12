package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.PickerCandidateSource;
import club.heiqi.config.ui.editor.PickerQuery;
import club.heiqi.config.ui.editor.PickerSourceVersion;
import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPanelPresentation;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.config.ui.field.PickerSourceGuard;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneGridWindow;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.GridProps;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.Props;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.Result;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
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
 * 选择器「分类筛选」端到端回归：切换左侧分类（受控 categoryKey 信号，即 CategoryNavPane 的
 * 写入目标）后，结果网格的总量、切片与统计必须整体换成该分类的口径。
 *
 * <p><b>缺陷锚定</b>（用户报告：「点击左侧按 Mod 分类后结果网格无变化」）。红先实测 = 修复动作前
 * 已存在的 4 例全红：{@code categorySelectionReslicesResultWindowFromCategoryHits}、
 * {@code equalHitsCategorySwitchStillRefetchesSlice}、{@code sourceVersionChangeRefetchesSliceWithSameTotal}、
 * {@code infoBarCountFollowsCategoryHits}（证据：{@code expected:<80> but was:<280>} 与
 * 「代际变化必须重取切片：2」）。另 2 例（{@code navAllRowKeepsListScaleWhileCategoryIsFiltered}、
 * {@code scrolledCategorySwitchClampsWindowAndKeepsSliceInsideCategory}）在修复后追加，其断言同属本修复面
 * （窗口总量 / 越界偏移），属守卫而非红先证据。三类缺陷如下：</p>
 * <ol>
 *   <li><b>总量口径</b>：SPI 路径的浏览 lane 无条件用 {@code source.size()} 当窗口总量，把分类过滤
 *       彻底排除在窗口数学之外 —— 命中 80 的分类仍按 280 算 totalRows/maxScrollPx/统计行/「全部」徽章；
 *       滚动后切分类时窗口停在「过滤后不存在的偏移」上，切片被拉空（第 4 例锚定）。</li>
 *   <li><b>失效通道</b>：切片重取只由 lane 值变化驱动，而 lane 的值按 {@code (query, counts)} 记忆化
 *       —— 「命中数持平」的查询修订（等命中分类切换）与纯数据修订（语言/资源代际变化）都不会重取切片
 *       （第 2、3 例锚定；修复前实测：版本代际变化后 {@code pageCalls} 不再增长）。</li>
 *   <li><b>标签驻留旧代</b>：单元节点按候选 key 复用（keyed reconcile），标签只读构建期捕获的 item
 *       —— 即使切片重取，同 key 单元的标签文本也不会换代（第 3 例同时锚定）。</li>
 * </ol>
 *
 * <p><b>装置口径</b>：候选源替身按 Miner {@code BlockPickerCandidateSource} 的语义实现分类过滤
 * （浏览 lane + 分类 = 清单序子序列；{@code matchCount} 与 {@code page} 同源），因此本测试
 * 断言的是 UILib 侧的窗口总量与切片通道，不替业务仓做过滤。每个「必须重取」用例都配了
 * <b>无修订对照帧</b>（同帧无变更不得多拉一次切片），避免断言因无关重算而假绿。</p>
 */
public class ScenePickerPanelCategoryFilterTest {

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

    // ==================== 分类切换 → 总量 + 切片 ====================

    /**
     * 选中分类：浏览 lane 的窗口总量 = 该分类命中数，且结果切片 = 该分类候选；
     * 清空分类（全部）：总量回到 {@code source.size()}。
     */
    @Test
    public void categorySelectionReslicesResultWindowFromCategoryHits() {
        CategorySource source = new CategorySource();
        Fixture f = new Fixture(source);
        f.openPanel();

        Assert.assertEquals("浏览 lane 无分类：窗口总量 = size()", 280, windowModel(f).totalItems());
        Assert.assertEquals("浏览 lane 无分类：切片 = 清单序前缀", "a1", cellLabels(f).get(0));

        f.categoryKey.set("beta");
        rt.flush();
        layoutAll();

        Assert.assertEquals("分类切换后窗口总量 = 该分类命中数", 80, windowModel(f).totalItems());
        Assert.assertNotNull("分类切换必须重新拉取切片", source.lastPageQuery);
        Assert.assertEquals("重取的切片请求必须携带 categoryKey", "beta", source.lastPageQuery.categoryKey());
        Assert.assertTrue("切片内容 = 该分类候选: " + cellLabels(f), allLabelsStartWith(cellLabels(f), "b"));

        f.categoryKey.set(null);
        rt.flush();
        layoutAll();

        Assert.assertEquals("回到「全部」：窗口总量 = size()", 280, windowModel(f).totalItems());
        Assert.assertEquals("回到「全部」：切片 = 清单序前缀", "a1", cellLabels(f).get(0));
    }

    /**
     * 命中数相同的两个分类之间切换：总量不变，切片仍必须重取（失效通道由查询修订驱动，
     * 而不是「总量变化」；否则窗口数学与切片内容错位 —— 显示上一个分类的候选）。
     */
    @Test
    public void equalHitsCategorySwitchStillRefetchesSlice() {
        CategorySource source = new CategorySource();
        Fixture f = new Fixture(source);
        f.openPanel();

        f.categoryKey.set("beta");
        rt.flush();
        layoutAll();
        Assert.assertEquals(80, windowModel(f).totalItems());
        Assert.assertTrue("前置：切片 = beta 候选: " + cellLabels(f), allLabelsStartWith(cellLabels(f), "b"));

        // 对照组（灵敏度自证）：查询条件与源版本都不动的帧不得重取切片。
        int pagesBefore = source.pageCalls;
        rt.flush();
        layoutAll();
        Assert.assertEquals("无修订帧不得重取切片", pagesBefore, source.pageCalls);

        f.categoryKey.set("gamma");
        rt.flush();
        layoutAll();

        Assert.assertEquals("命中数相同：总量不变", 80, windowModel(f).totalItems());
        Assert.assertTrue("查询修订必须触发重取切片（不得靠总量变化）：" + source.pageCalls,
                source.pageCalls > pagesBefore);
        Assert.assertEquals("切片请求携带新 categoryKey", "gamma", source.lastPageQuery.categoryKey());
        Assert.assertTrue("切片内容 = gamma 候选: " + cellLabels(f),
                allLabelsStartWith(cellLabels(f), "c"));
    }

    /**
     * 数据代际（语言/资源 → nameRevision）变化而总量不变：切片必须重取（标签按新代际重建）。
     *
     * <p>这是同一失效通道的非分类证据 —— 语言/资源包变化不改变清单长度，但每个候选的展示标签
     * 全部作废；若切片只由总量驱动，用户会看到旧的本地化标签直到重启。</p>
     */
    @Test
    public void sourceVersionChangeRefetchesSliceWithSameTotal() {
        CategorySource source = new CategorySource();
        Fixture f = new Fixture(source);
        f.openPanel();
        Assert.assertEquals("a1", cellLabels(f).get(0));
        int pagesBefore = source.pageCalls;

        source.labelEpoch = 1;
        f.version.set(new PickerSourceVersion(0L, 1L, 0L));
        rt.flush();
        layoutAll();

        Assert.assertEquals("代际变化不改变总量", 280, windowModel(f).totalItems());
        Assert.assertTrue("代际变化必须重取切片：" + source.pageCalls, source.pageCalls > pagesBefore);
        Assert.assertEquals("切片标签按新代际重建", "a1#1", cellLabels(f).get(0));
    }

    /**
     * 滚到「全部」中段后切换分类：窗口总量必须按命中数收缩（scroll 夹回新上限），切片必须整体落在
     * 该分类内 —— 旧实现里总量恒 = {@code size()}，窗口会停在「过滤后不存在的偏移」上，切片被拉空。
     */
    @Test
    public void scrolledCategorySwitchClampsWindowAndKeepsSliceInsideCategory() {
        CategorySource source = new CategorySource();
        Fixture f = new Fixture(source);
        f.openPanel();

        // 滚到底：窗口推进到「全部」末页（偏移远大于分类命中数）。
        routeScrollAt(f.result.grid().get(), -100000);
        rt.flush();
        layoutAll();
        SceneGridWindow.WindowModel scrolled = windowModel(f);
        Assert.assertTrue("前置：窗口已推进到中后段: " + scrolled.windowStartRow(),
                scrolled.windowStartRow() * COLUMNS > 80);

        f.categoryKey.set("beta");
        rt.flush();
        layoutAll();

        SceneGridWindow.WindowModel filtered = windowModel(f);
        Assert.assertEquals("分类总量 = 命中数（窗口数学随之收缩）", 80, filtered.totalItems());
        Assert.assertEquals("totalRows 随命中数收缩", 20, filtered.totalRows());
        Assert.assertTrue("挂载窗口不得越界（offset < 总量）: " + filtered.windowOffset(),
                filtered.windowOffset() < filtered.totalItems());
        Assert.assertTrue("滚动必须夹回新上限: " + f.result.grid().get().getScrollOffsetY(),
                f.result.grid().get().getScrollOffsetY() <= filtered.maxScrollPx());
        Assert.assertTrue("切片必须非空且整体属于该分类: " + cellLabels(f),
                allLabelsStartWith(cellLabels(f), "b"));
    }

    // ==================== 信息条 ====================

    /** 信息条统计行 = 当前查询总量（分类切换后随命中数变化，不残留旧总数）。 */
    @Test
    public void infoBarCountFollowsCategoryHits() {
        CategorySource source = new CategorySource();
        Fixture f = new Fixture(source);
        f.openPanel();
        SearchPickerPresentation presentation = SearchPickerPresentation.defaultEnglish();
        Assert.assertTrue("浏览 lane 无分类：统计 = size(): " + infoBarText(f.panelRoot()),
                infoBarText(f.panelRoot()).contains(presentation.searchResultsTitle(280)));

        f.categoryKey.set("beta");
        rt.flush();
        layoutAll();

        Assert.assertEquals("分类切换后统计 = 该分类命中数", 80, windowModel(f).totalItems());
        Assert.assertTrue("信息条统计必须随分类命中数刷新: " + infoBarText(f.panelRoot()),
                infoBarText(f.panelRoot()).contains(presentation.searchResultsTitle(80)));
        Assert.assertFalse("不得残留旧总数: " + infoBarText(f.panelRoot()),
                infoBarText(f.panelRoot()).contains(presentation.searchResultsTitle(280)));
    }

    /**
     * 分类导航徽章口径：「全部」行 = <b>未收窄的清单规模</b>（该行语义 = 取消分类过滤，故不随过滤
     * 变化，也不被窗口总量牵连）；分类行 = 源给出的该分类候选数。
     */
    @Test
    public void navAllRowKeepsListScaleWhileCategoryIsFiltered() {
        CategorySource source = new CategorySource();
        Fixture f = new Fixture(source);
        f.openPanel();
        String allLabel = SearchPickerPanelPresentation.defaultEnglish().allCategoryLabel();

        Map<String, String> badges = navBadges(f.panelRoot());
        Assert.assertEquals("无过滤时「全部」= 清单规模: " + badges, "280", badges.get(allLabel));
        Assert.assertEquals("分类行 = 该分类候选数: " + badges, "120", badges.get("Alpha"));

        f.categoryKey.set("beta");
        rt.flush();
        layoutAll();

        Map<String, String> filtered = navBadges(f.panelRoot());
        Assert.assertEquals("「全部」行必须保持清单规模（不是被过滤命中数）: " + filtered,
                "280", filtered.get(allLabel));
        Assert.assertEquals("分类行计数仍取源快照: " + filtered, "80", filtered.get("Beta"));
        Assert.assertEquals("窗口总量 = 该分类命中数", 80, windowModel(f).totalItems());
    }

    /**
     * 搜索 lane：窗口总量取 {@code min(hits, searchMaxItems)}、窗口行数随之派生、切片请求量不越上限，
     * 截断语义由 truncated 通道（顶栏统计行）表达；导航「全部」行取<b>真实命中数</b>（不夹取上限）。
     *
     * <p>口径依据：分类行徽章来自 {@code source.categories()} 的 count（真实候选规模，与窗口上限
     * 无关），「全部」行必须与各分类行同源；{@code min(hits, maxItems)} 反而是异源，截断由顶栏/
     * 信息条的 truncated 文案单独承担。断言在<b>同一挂载实例</b>内完成（打开后输入查询、不重新 open），
     * 同时看护「行键不变而计数换代必须刷上屏」的导航徽章通路。</p>
     */
    @Test
    public void searchLaneAllRowShowsTrueHitsWhileWindowStaysCapped() {
        CategorySource source = new CategorySource();
        source.textHits = 200;
        Fixture f = new Fixture(source);
        f.openPanel();

        f.query.set("stone");
        rt.flush();
        layoutAll();

        SceneGridWindow.WindowModel model = windowModel(f);
        Assert.assertEquals("搜索 lane 窗口总量 = min(真实命中数, searchMaxItems)",
                SEARCH_MAX_ITEMS, model.totalItems());
        Assert.assertEquals("窗口行数由 capped 总量派生（不得按真实命中数 200 算）", 16, model.totalRows());
        Assert.assertTrue("切片请求量不得超上限: " + source.lastPageLimit,
                source.lastPageLimit <= SEARCH_MAX_ITEMS);

        Assert.assertTrue("截断必须由 truncated 通道表达（顶栏统计行）: " + allText(f.panelRoot()),
                allText(f.panelRoot()).contains(
                        SearchPickerPanelPresentation.defaultEnglish().truncatedResults()));
        Assert.assertTrue("顶栏统计 = min(真实命中数, searchMaxItems): " + allText(f.panelRoot()),
                allText(f.panelRoot()).contains(
                        SearchPickerPresentation.defaultEnglish().resultSummary(SEARCH_MAX_ITEMS)));

        String allLabel = SearchPickerPanelPresentation.defaultEnglish().allCategoryLabel();
        Map<String, String> badges = navBadges(f.panelRoot());
        Assert.assertEquals("同一挂载实例内「全部」行 = 真实命中数（不夹取 searchMaxItems）: " + badges,
                "200", badges.get(allLabel));
    }

    // ==================== 夹具 ====================

    /** 面板夹具：受控 categoryKey（= CategoryNavPane 的写入目标）+ 分类感知惰性源。 */
    private final class Fixture {
        final Signal<Boolean> open = Signal.create(Boolean.FALSE);
        final Signal<String> query = Signal.create("");
        final Signal<Integer> dimension = Signal.create(Integer.valueOf(0));
        final Signal<String> categoryKey = Signal.create(null);
        final Signal<PickerSourceVersion> version = Signal.create(PickerSourceVersion.initial());
        final List<SearchPickerData.Selection> commits = new ArrayList<SearchPickerData.Selection>();
        final Result result;

        Fixture(final CategorySource source) {
            final Signal<String> categorySignal = categoryKey;
            final Signal<Integer> dimensionSignal = dimension;
            Computed<PickerQuery> sourceQuery = Computed.create(() -> PickerQuery.text(
                    query.get(), dimensionSignal.get().intValue(), categorySignal.get()));
            Props props = Props.builder(query, Signal.create(SearchPickerData.SearchResult.empty()),
                    Signal.create(Boolean.TRUE), query::set, commits::add, visualAdapter())
                    .open(open)
                    .onCloseRequest(() -> open.set(Boolean.FALSE))
                    .grid(GridProps.of(COLUMNS, 64, 64, 8, 8, 3))
                    .currentMembers(Signal.create(
                            Collections.<SearchPickerData.CurrentMember>emptyList()), memberId -> { })
                    .categories(Signal.create(Arrays.asList(
                            new SearchPickerCategories.Category("alpha", "Alpha", 120),
                            new SearchPickerCategories.Category("beta", "Beta", 80),
                            new SearchPickerCategories.Category("gamma", "Gamma", 80))))
                    .categoryOf(source::categoryOf)
                    .currentCategoryKey(categoryKey, categoryKey::set)
                    .candidateSource(source, SEARCH_MAX_ITEMS, sourceQuery, version)
                    .build();
            result = ScenePickerPanel.create(rt, props);
            sceneRoot.appendChild(result.root());
        }

        void openPanel() {
            open.set(Boolean.TRUE);
            rt.flush();
            layoutAll();
            layoutAll();
        }

        /** 面板卡片根（overlay 根的第 0 子）。 */
        SceneNode panelRoot() {
            return rt.getOverlayHost().bottomFirst().get(0).getRoot().__getChildren().get(0);
        }
    }

    private void layoutAll() {
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        for (SceneOverlayHost.Entry entry : rt.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(W, H));
        }
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    private SceneGridWindow.WindowModel windowModel(Fixture f) {
        return f.result.windowModel().get();
    }

    private List<String> cellLabels(Fixture f) {
        return cellLabels(f.result.grid().get());
    }

    /** 信息条当前显示文案（中栏第 4 子 = 信息条；条体唯一文本叶 = 第 1 子）。 */
    private static String infoBarText(SceneNode panel) {
        SceneNode center = panel.__getChildren().get(1).__getChildren().get(1);
        return center.__getChildren().get(3).__getChildren().get(0).getText();
    }

    /** 在节点中心投一次滚轮（不 flush，由调用方决定拍子）。 */
    private void routeScrollAt(SceneNode node, int wheelDelta) {
        int[] center = centerOf(node);
        InputFrameBuilder fb = new InputFrameBuilder(center[0], center[1]);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, center[0], center[1],
                SceneMouseButton.NONE, wheelDelta, 0, 0, false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
    }

    /** 已布局节点的几何中心。 */
    private static int[] centerOf(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            throw new IllegalStateException("节点未布局或零尺寸，无法取中心: " + box);
        }
        return new int[] { box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2 };
    }

    /** 分类导航行容器（左栏导航列 → 滚动容器 → 内容容器）。 */
    private static SceneNode navRows(SceneNode panel) {
        return panel.__getChildren().get(1).__getChildren().get(0)
                .__getChildren().get(1).__getChildren().get(0).__getChildren().get(0);
    }

    /**
     * 导航徽章（label → 计数）：逐行收集行内全部文本叶，「数字 = 计数、其余 = 行标签」。
     *
     * <p>不假定行内部层级（pill 行结构演进时本取数不失效），只看语义文本。</p>
     */
    private static Map<String, String> navBadges(SceneNode panel) {
        Map<String, String> badges = new LinkedHashMap<String, String>();
        for (SceneNode row : navRows(panel).__getChildren()) {
            List<String> texts = new ArrayList<String>();
            collectTexts(row, texts);
            String label = null;
            String count = null;
            for (String text : texts) {
                if (isNumeric(text)) {
                    count = text;
                } else if (!text.isEmpty()) {
                    label = text;
                }
            }
            if (label != null && count != null) {
                badges.put(label, count);
            }
        }
        return badges;
    }

    /** 子树全部可见文本（换行拼接），用于「截断提示必须上屏」这类文案断言。 */
    private static String allText(SceneNode node) {
        List<String> texts = new ArrayList<String>();
        collectTexts(node, texts);
        StringBuilder out = new StringBuilder();
        for (String text : texts) {
            out.append(text).append('\n');
        }
        return out.toString();
    }

    private static boolean isNumeric(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static void collectTexts(SceneNode node, List<String> out) {
        String text = node.getText();
        if (text != null && !text.isEmpty()) {
            out.add(text);
        }
        for (SceneNode child : node.__getChildren()) {
            collectTexts(child, out);
        }
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

    /** 单元标签文本（单元结构 = column[icon, label]）。 */
    private static String cellLabel(SceneNode cell) {
        return cell.__getChildren().get(1).getText();
    }

    private static SceneNode rowsContainer(SceneNode viewport) {
        return viewport.__getChildren().get(0).__getChildren().get(1);
    }

    private static boolean allLabelsStartWith(List<String> labels, String prefix) {
        if (labels.isEmpty()) {
            return false;
        }
        for (String label : labels) {
            if (!label.startsWith(prefix)) {
                return false;
            }
        }
        return true;
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

    /**
     * 分类感知内存源替身（语义对齐 Miner {@code BlockPickerCandidateSource}）：
     * 浏览 lane + 分类 = 清单序子序列；{@code matchCount}/{@code page} 同源；
     * 无过滤浏览 = 清单恒等序。{@code labelEpoch} 模拟语言/资源代际变化（键不变、标签变）。
     */
    private static final class CategorySource implements PickerCandidateSource {
        private final List<SearchPickerData.Candidate> all = new ArrayList<SearchPickerData.Candidate>();
        private final List<String> mods = new ArrayList<String>();

        int pageCalls;
        int sizeCalls;
        int matchCountCalls;
        int labelEpoch;
        /** 文本 lane 命中规模（>=0 生效；-1 = 不模拟文本查询，按清单全量）。 */
        int textHits = -1;
        PickerQuery lastPageQuery;
        int lastPageOffset = -1;
        int lastPageLimit = -1;

        CategorySource() {
            addRange("alpha", "a", 120);
            addRange("beta", "b", 80);
            addRange("gamma", "c", 80);
        }

        private void addRange(String mod, String prefix, int count) {
            for (int i = 1; i <= count; i++) {
                all.add(candidate(prefix + i, prefix + i));
                mods.add(mod);
            }
        }

        String categoryOf(String candidateKey) {
            int index = indexOf(candidateKey);
            return index < 0 ? null : mods.get(index);
        }

        private int indexOf(String candidateKey) {
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).key().equals(candidateKey)) {
                    return i;
                }
            }
            return -1;
        }

        private List<Integer> order(PickerQuery query) {
            List<Integer> out = new ArrayList<Integer>();
            String category = query.hasCategoryFilter() ? query.categoryKey() : null;
            // 文本 lane 的命中 = 清单前 textHits 项（未设置时 = 清单全量）；浏览 lane = 清单全量。
            int scope = query.isBrowse() ? all.size()
                    : Math.min(textHits < 0 ? all.size() : textHits, all.size());
            for (int i = 0; i < scope; i++) {
                if (category != null && !category.equals(mods.get(i))) {
                    continue;
                }
                out.add(Integer.valueOf(i));
            }
            return out;
        }

        @Override
        public int size() {
            sizeCalls++;
            return all.size();
        }

        @Override
        public long registryRevision() {
            return 0L;
        }

        @Override
        public long nameRevision() {
            return labelEpoch;
        }

        @Override
        public long iconRevision() {
            return 0L;
        }

        @Override
        public int matchCount(PickerQuery query) {
            matchCountCalls++;
            return query.isBrowse() && !query.hasCategoryFilter() ? all.size() : order(query).size();
        }

        @Override
        public List<SearchPickerData.Candidate> page(PickerQuery query, int offset, int limit) {
            pageCalls++;
            lastPageQuery = query;
            lastPageOffset = offset;
            lastPageLimit = limit;
            List<Integer> order = query.isBrowse() && !query.hasCategoryFilter()
                    ? null : order(query);
            int total = order == null ? all.size() : order.size();
            List<SearchPickerData.Candidate> out = new ArrayList<SearchPickerData.Candidate>();
            for (int i = offset; i < Math.min(total, offset + limit); i++) {
                int index = order == null ? i : order.get(i).intValue();
                SearchPickerData.Candidate base = all.get(index);
                out.add(candidate(base.key(), base.key() + (labelEpoch == 0 ? "" : "#" + labelEpoch)));
            }
            return Collections.unmodifiableList(out);
        }

        @Override
        public SearchPickerData.Candidate exact(String candidateKey) {
            int index = indexOf(candidateKey);
            return index < 0 ? null
                    : candidate(candidateKey, candidateKey + (labelEpoch == 0 ? "" : "#" + labelEpoch));
        }

        @Override
        public List<SearchPickerCategories.Category> categories(int dimension) {
            return Arrays.asList(
                    new SearchPickerCategories.Category("alpha", "Alpha", 120),
                    new SearchPickerCategories.Category("beta", "Beta", 80),
                    new SearchPickerCategories.Category("gamma", "Gamma", 80));
        }

        private static SearchPickerData.Candidate candidate(String key, String label) {
            return new SearchPickerData.Candidate(key, label,
                    Collections.<SearchPickerData.Variant>emptyList());
        }
    }
}
