package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.GridProps;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.Props;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.Result;
import club.heiqi.uilib.ui.scene.control.search.CategoryNavPane;
import club.heiqi.uilib.ui.scene.control.search.MemberGrid;
import club.heiqi.uilib.ui.scene.control.search.PickerInfoBar;
import club.heiqi.uilib.ui.scene.control.search.SearchResultList;
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
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
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
 * {@link ScenePickerPanel} L3 runtime 集成测试。
 *
 * <p>覆盖：居中 70% portal 开/关与 ESC 分层；分类列表渲染与切换过滤；候选点击直达 vs 变体浮层两路；
 * 变体勾选/ALL-SELECTED/确认/取消/可拒绝 selectionCommit 保持展开；listMembers 模式成员
 * 增/编辑/一步删除/无效重复徽章/空态；键盘导航与焦点意图；数据收缩回夹；受控开合/分类接线。</p>
 *
 * <p>G14 液态玻璃整合新增覆盖：宿主外层恰一颗 PANEL 配方表面（六项逐项）；中栏实底外壳与成员带
 * 线框外壳的表面写入已删除（surface 归属断言：结果区/成员区表面归各自 GROUP 底座一颗）；整树
 * BACKDROP 构成表（每颗表面各采样一次，复用行/单元格/遮罩零滤镜）；G13 五配件在真实装配中的
 * 集成调用证据（与单实例已验收口径逐项一致）；主题切换选择/草稿不丢、节点身份不变、effect
 * 不增长；物品图像渲染协议不改色（反向钉住）；开关面板/查询行为合同保持；卸载与关闭回收。</p>
 */
public class ScenePickerPanelTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

    private static final int W = 800;
    private static final int H = 600;
    private static final float EPSILON = 0.0001F;
    /** 透明底（轻量行/外壳表面删除/图像协议断言用）。 */
    private static final int BG_TRANSPARENT = 0x00000000;
    /** {@link SceneNode} 默认字号：不设字号时控件内文字必须保持的值（视觉零变化约束）。 */
    private static final int DEFAULT_FONT_SIZE = 16;

    /** 库默认主题配方：默认外观唯一来源，断言引用配方值而非硬编码色号。 */
    private static final SceneSurfaceStyle PANEL = SceneThemes.DEFAULT.surface(SceneTheme.Role.PANEL);
    private static final SceneSurfaceStyle TOOLBAR = SceneThemes.DEFAULT.surface(SceneTheme.Role.TOOLBAR);
    private static final SceneSurfaceStyle INPUT = SceneThemes.DEFAULT.surface(SceneTheme.Role.INPUT);
    private static final SceneSurfaceStyle GROUP = SceneThemes.DEFAULT.surface(SceneTheme.Role.GROUP);
    private static final SceneSurfaceStyle OVERLAY = SceneThemes.DEFAULT.surface(SceneTheme.Role.OVERLAY);
    private static final SceneSurfaceStyle INDICATOR =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.INDICATOR);
    private static final SceneSurfaceStyle BUTTON_STANDARD =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.BUTTON_STANDARD);

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
            this(initialCandidates, listMembers, variantSearchEnabled, columns, true);
        }

        Fixture(List<SearchPickerData.Candidate> initialCandidates, boolean listMembers,
                boolean variantSearchEnabled, int columns, boolean enabled) {
            results = Signal.create(new SearchPickerData.SearchResult(initialCandidates));
            Props.Builder builder = Props.builder(query, results,
                    Signal.create(Boolean.valueOf(enabled)),
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

    /** 成员卡片：cell = [top, secondary, actions]；top = [icon, primary, badge]。 */
    private static SceneNode rowBadge(SceneNode cell) {
        return cell.__getChildren().get(0).__getChildren().get(2);
    }

    private static SceneNode rowEdit(SceneNode cell) {
        return cell.__getChildren().get(2).__getChildren().get(0);
    }

    private static SceneNode rowRemove(SceneNode cell) {
        return cell.__getChildren().get(2).__getChildren().get(1);
    }

    /** 成员网格内容：membersPanel = [header, gridRoot]；gridRoot(container) = [viewport, scrollbar]；viewport = [content]。 */
    private static SceneNode memberRows(SceneNode membersPanel) {
        return membersPanel.__getChildren().get(1).__getChildren().get(0).__getChildren().get(0);
    }

    /** 第 index 个成员卡片（跨行平铺）。 */
    private static SceneNode memberCell(SceneNode membersPanel, int index) {
        for (SceneNode rowNode : memberRows(membersPanel).__getChildren()) {
            if (index < rowNode.__getChildren().size()) return rowNode.__getChildren().get(index);
            index -= rowNode.__getChildren().size();
        }
        throw new IllegalStateException("member cell index out of mounted grid: " + index);
    }

    /** 已挂载成员卡片总数。 */
    private static int memberCellCount(SceneNode membersPanel) {
        int count = 0;
        for (SceneNode rowNode : memberRows(membersPanel).__getChildren()) count += rowNode.__getChildren().size();
        return count;
    }

    /** 变体行容器：listHost(container) = [viewport]；viewport = [content]。 */
    private static SceneNode variantRows(SceneNode card, int listHostIndex) {
        return card.__getChildren().get(listHostIndex).__getChildren().get(0).__getChildren().get(0);
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

    /**
     * 把指针移到卡片外的 scrim 角落（清除全部 hover；(0,0) 属 scrim，MOVE 不触发关闭/取消），
     * 使轻量行断言落在无悬停干扰的纯选中/idle 档。
     */
    private void pointerAway() {
        routePointer(ScenePointerAction.MOVE, 0, 0);
        rt.flush();
    }

    private int[] centerOf(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            throw new IllegalStateException("节点未布局或零尺寸，无法取中心: " + box);
        }
        return new int[]{box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2};
    }

    // ==================== 通用辅助（G14 外观断言） ====================

    /** 选中轻量档语义：RGB 换源色、alpha = 主题统一选中强度 0x59（selectableSurface 口径）。 */
    private static int selectedTint(int sourceColor) {
        return (0x59 << 24) | (sourceColor & 0x00FFFFFF);
    }

    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int rgbOf(int argb) {
        return argb & 0x00FFFFFF;
    }

    /** 节点子树 PaintPlan 内的 BACKDROP 命令数（每颗表面恰好采样一次 = 每节点至多 1 条）。 */
    private int backdropCount(SceneNode root) {
        PaintPlan plan = paintEngine.paint(root).getPlan();
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    /** 节点自身 PaintFragment 内的 BACKDROP 命令数（须先对含该节点的树执行过 paint）。 */
    private static int ownBackdropCount(SceneNode node) {
        Object cached = node.getCachedPaint();
        Assert.assertTrue("节点应已绘制出自身 fragment", cached instanceof PaintFragment);
        int count = 0;
        for (PaintCommand command : ((PaintFragment) cached).getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    /** 收集子树内声明了滤镜（backdrop != null）的全部节点（身份集合，构成表用）。 */
    private static void collectSurfaceNodes(SceneNode node, List<SceneNode> out) {
        if (node.getBackdrop() != null) {
            out.add(node);
        }
        for (SceneNode child : node.__getChildren()) {
            collectSurfaceNodes(child, out);
        }
    }

    /** 子树内声明滤镜的节点数（与 backdropCount 命令数配对，验证「每颗表面只采样一次、无漏无重」）。 */
    private static int surfaceNodeCount(SceneNode root) {
        List<SceneNode> surfaces = new ArrayList<SceneNode>();
        collectSurfaceNodes(root, surfaces);
        return surfaces.size();
    }

    /** 中栏节点 = 卡片 children[1](selectionArea).children[1](center)。 */
    private SceneNode centerColumn(SceneNode overlayRoot) {
        return panelCard(overlayRoot).__getChildren().get(1).__getChildren().get(1);
    }

    /** 顶栏节点 = 卡片 children[0]。 */
    private SceneNode topBar(SceneNode overlayRoot) {
        return panelCard(overlayRoot).__getChildren().get(0);
    }

    /** 左导航节点 = 卡片 children[1](selectionArea).children[0](nav)。 */
    private SceneNode navPane(SceneNode overlayRoot) {
        return panelCard(overlayRoot).__getChildren().get(1).__getChildren().get(0);
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
        // 变体列表视口 = children[2](listHost).children[0](viewport).children[0](content)
        SceneNode list = variantRows(card, 2);
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
        // 变体列表视口 = children[3](listHost).children[0](viewport).children[0](content)
        SceneNode list = variantRows(card, 3);
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
    public void listMembersRendersBadgesAndRemovesDirectly() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), true);
        f.members.set(Arrays.asList(member(0L, "a"), malformedMember(1L), member(2L, "a")));
        openPanel(f);

        SceneNode panelRoot = overlayRoot(0);
        SceneNode membersPanel = membersPanel(panelRoot);
        Assert.assertEquals(3, memberCellCount(membersPanel));

        SceneNode row0 = memberCell(membersPanel, 0);
        SceneNode row1 = memberCell(membersPanel, 1);
        SceneNode row2 = memberCell(membersPanel, 2);
        Assert.assertEquals("malformed 徽章", "Error/Invalid", rowBadge(row1).getText());
        Assert.assertEquals("duplicate 徽章", "Warning/Duplicate", rowBadge(row0).getText());
        Assert.assertEquals("duplicate 徽章", "Warning/Duplicate", rowBadge(row2).getText());

        // 删除一步直达：点击即提交，无需二次确认
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
        SceneNode row = memberCell(membersPanel, 0);
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
    public void listMembersCancelThenReopenAddsDirectlyAgain() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), true);
        openPanel(f);
        click(gridCell(f.result.grid().get(), 0));
        Assert.assertEquals(1, f.commits.size());
        Assert.assertEquals(2, f.beginAdds.get());
        pressKey(SceneKey.ESCAPE);
        Assert.assertEquals("ESC 先取消后关闭", 1, f.cancels.get());
        Assert.assertEquals(1, f.closeRequests.get());
        Assert.assertFalse(f.result.open().get().booleanValue());
        // 重新打开：取消路径不得残留武装/编辑态，点击候选仍直接隐式新增
        openPanel(f);
        click(gridCell(f.result.grid().get(), 1));
        Assert.assertEquals(2, f.commits.size());
        Assert.assertEquals("b", f.commits.get(1).candidateKey());
        Assert.assertEquals("重开后再次隐式武装（+2）", 4, f.beginAdds.get());
        Assert.assertTrue(f.result.open().get().booleanValue());
    }

    @Test
    public void listMembersRemoveClearsArmingThenGridClickAddsDirectly() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), true);
        f.members.set(Arrays.asList(member(0L, "a")));
        openPanel(f);
        // 先新增一条（隐式武装 + 重新武装 = 2 次 beginAdd）
        click(gridCell(f.result.grid().get(), 0));
        Assert.assertEquals(2, f.beginAdds.get());
        Assert.assertEquals(1, f.commits.size());
        // 一步删除已配置成员：删除是独立意图，成功后清除新增武装
        SceneNode membersPanel = membersPanel(overlayRoot(0));
        SceneNode row0 = memberCell(membersPanel, 0);
        click(rowRemove(row0));
        Assert.assertEquals(1, f.removeCalls.size());
        // 清除武装后，点击候选走隐式新增路径（+2），而非已武装路径（+1）
        click(gridCell(f.result.grid().get(), 1));
        Assert.assertEquals(2, f.commits.size());
        Assert.assertEquals("删除后武装已清除，点击再隐式新增", 4, f.beginAdds.get());
        Assert.assertTrue(f.result.open().get().booleanValue());
    }

    @Test
    public void listMembersRemovingVariantEditingMemberResetsTransientState() {
        Fixture f = new Fixture(Arrays.asList(candidateWithVariants("a", "v1")), true);
        f.members.set(Arrays.asList(new SearchPickerData.CurrentMember(0L,
                new SearchPickerData.Selection("a", SearchPickerData.SelectionMode.ALL,
                        Collections.<String>emptyList()),
                candidateWithVariants("a", "v1"), true)));
        openPanel(f);
        // 编辑带变体成员 → 变体浮层展开
        SceneNode membersPanel = membersPanel(overlayRoot(0));
        click(rowEdit(memberCell(membersPanel, 0)));
        Assert.assertTrue("编辑带变体成员应展开浮层", f.result.variantsOpen().get().booleanValue());
        Assert.assertNotNull("浮层应有 activeCandidate", f.result.activeCandidate().get());
        // 一步删除成功后：变体浮层/候选与武装全部归位（对齐 finishSelection/cancelPanel 收尾集合）
        click(rowRemove(memberCell(membersPanel, 0)));
        Assert.assertEquals(Collections.singletonList(Long.valueOf(0L)), f.removeCalls);
        Assert.assertFalse("删除成功后变体浮层应收起", f.result.variantsOpen().get().booleanValue());
        Assert.assertNull("删除成功后 activeCandidate 应清空", f.result.activeCandidate().get());
    }

    @Test
    public void listMembersRemoveIgnoredWhenDisabled() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), true, false, 3, false);
        f.members.set(Arrays.asList(member(0L, "a")));
        openPanel(f);
        SceneNode membersPanel = membersPanel(overlayRoot(0));
        click(rowRemove(memberCell(membersPanel, 0)));
        Assert.assertTrue("disabled 面板删除按钮不得触发 onRemove", f.removeCalls.isEmpty());
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
        SceneNode list = variantRows(card, 2);
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

    /**
     * G14 预裁决 1 落点：左导航底座仍取 TOOLBAR 配方（G13 已验收），中栏实底外壳的表面写入已
     * 删除——背景/边框/圆角归零、不装滤镜，结果区表面归 SearchResultList 的 GROUP 底座一颗；
     * clip 与 padding 属布局合同保留。原「中栏 BORDER_DEFAULT / BG_DEFAULT / RADIUS_MD」断言按
     * 契约 §10.1 更新（改默认外观所致：从宿主实底改为 surface 归属断言）。
     */
    @Test
    public void navKeepsToolbarBaseAndCenterShellSurfaceRemoved() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), false);
        openPanel(f);
        SceneNode overlayRoot = overlayRoot(0);
        SceneNode nav = navPane(overlayRoot);
        SceneNode center = centerColumn(overlayRoot);

        Assert.assertEquals("分类导航外边框宽 = TOOLBAR 配方独占", TOOLBAR.getBorderWidth(),
                nav.getBorderWidth());
        Assert.assertEquals("分类导航边框色 = 库默认 TOOLBAR 配方 idle 缘色",
                TOOLBAR.getIdle().getEdge(), nav.getBorderColor());
        Assert.assertEquals("分类导航背景 = TOOLBAR idle 染色（G13 已验收口径保持）",
                TOOLBAR.getIdle().getTint(), nav.getBackgroundColor());

        Assert.assertEquals("中栏不再自绘底色（全透明，表面归内容底座）",
                BG_TRANSPARENT, center.getBackgroundColor());
        Assert.assertEquals("中栏不再自绘边框", 0, center.getBorderWidth());
        Assert.assertEquals("中栏不再自绘圆角", 0, center.getCornerRadius());
        Assert.assertNull("中栏不装滤镜（不给容器叠第二层玻璃）", center.getBackdrop());
        Assert.assertEquals("中栏不绑定实体高度（-1=普通绘制）", -1.0F,
                center.__getSurfaceElevation(), EPSILON);
        Assert.assertTrue("中栏裁剪合同保留（clip 属布局合同）", center.isClipChildren());
        Assert.assertEquals("中栏内边距布局常量不变", SceneChromeTokens.PAD_SM,
                center.getPaddingTop());

        // surface 归属：结果区底座（viewport）恰好一颗 GROUP 表面。
        SceneNode grid = f.result.grid().get();
        Assert.assertEquals("结果底座背景 = GROUP idle 染色",
                GROUP.getIdle().getTint(), grid.getBackgroundColor());
        Assert.assertNotNull("结果底座自带滤镜", grid.getBackdrop());
        Assert.assertEquals("结果底座自身恰好一条 BACKDROP", 1, backdropCount(grid));
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
        Assert.assertEquals("底部横带含 2 个成员卡片", 2, memberCellCount(band));
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

    // ==================== G14 液态玻璃整合：宿主表面与外观归属 ====================

    /** 宿主外层：卡片恰装一颗 PANEL 配方表面（六项逐项），clip/padding 布局合同保留。 */
    @Test
    public void hostCardBindsPanelRecipeOwnedBySurfaceBinder() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), false);
        openPanel(f);
        SceneNode card = panelCard(overlayRoot(0));

        Assert.assertNotNull("前置：PANEL 配方自带滤镜", PANEL.getBackdrop());
        Assert.assertEquals("卡片背景 = PANEL idle 染色",
                PANEL.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("卡片边框色 = PANEL idle 缘色",
                PANEL.getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("卡片边框宽 = PANEL 配方独占", PANEL.getBorderWidth(), card.getBorderWidth());
        Assert.assertEquals("卡片圆角 = PANEL 配方独占（不再静态 RADIUS_LG）",
                PANEL.getCornerRadius(), card.getCornerRadius());
        Assert.assertEquals("卡片实体高度 = PANEL idle 档",
                PANEL.getIdle().getElevation(), card.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull("卡片装 PANEL 滤镜（宿主外层恰一颗表面）", card.getBackdrop());
        Assert.assertEquals("滤镜模糊半径 = 配方",
                PANEL.getBackdrop().getBlurRadius(), card.getBackdrop().getBlurRadius());
        Assert.assertEquals("滤镜材质 = 配方", PANEL.getBackdrop().getEffect().getMaterial(),
                card.getBackdrop().getEffect().getMaterial());
        Assert.assertTrue("卡片裁剪合同保留（clip 非绑定器六项，宿主自持）", card.isClipChildren());
        Assert.assertEquals("卡片内边距布局常量不变", SceneChromeTokens.PAD_MD, card.getPaddingTop());
        Assert.assertEquals("卡片间隙布局常量不变", SceneChromeTokens.PAD_MD, card.getGap());

        Assert.assertNotEquals("不再取旧实色 BG_DEFAULT", SceneChromeTokens.BG_DEFAULT,
                card.getBackgroundColor());
        Assert.assertNotEquals("不再取旧 BORDER_DEFAULT", SceneChromeTokens.BORDER_DEFAULT,
                card.getBorderColor());
        // 先整树 paint 刷新 fragment 缓存，再核对节点自身只发一条 BACKDROP。
        Assert.assertEquals("整树（本场景 6 颗表面）", 6, backdropCount(overlayRoot(0)));
        Assert.assertEquals("卡片自身恰好一条 BACKDROP（每颗表面只采样一次）",
                1, ownBackdropCount(card));
    }

    /** 底部成员带：外壳表面写入已删除（surface 归属断言），成员区表面归 MemberGrid GROUP 底座。 */
    @Test
    public void membersBandSurfaceBelongsToGridBase() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), true);
        f.members.set(Arrays.asList(member(0L, "a")));
        openPanel(f);
        SceneNode band = membersPanel(overlayRoot(0));

        Assert.assertEquals("成员带不再自绘底色", BG_TRANSPARENT, band.getBackgroundColor());
        Assert.assertEquals("成员带不再自绘边框", 0, band.getBorderWidth());
        Assert.assertEquals("成员带不再自绘圆角", 0, band.getCornerRadius());
        Assert.assertNull("成员带不装滤镜", band.getBackdrop());
        Assert.assertTrue("成员带裁剪合同保留", band.isClipChildren());

        SceneNode memberViewport = band.__getChildren().get(1).__getChildren().get(0);
        Assert.assertEquals("成员网格底座 = GROUP idle 染色",
                GROUP.getIdle().getTint(), memberViewport.getBackgroundColor());
        Assert.assertNotNull("成员网格底座自带滤镜（表面归内容底座一颗）", memberViewport.getBackdrop());
        backdropCount(overlayRoot(0));
        Assert.assertEquals("成员网格底座自身恰好一条 BACKDROP（卡内按钮表面另计）",
                1, ownBackdropCount(memberViewport));

        SceneNode header = band.__getChildren().get(0);
        Assert.assertEquals("成员带标题 = 主题正文前景",
                Integer.valueOf(SceneThemes.DEFAULT.foreground()),
                Integer.valueOf(header.__getChildren().get(0).getTextColor()));
        Assert.assertEquals("问题摘要 = 主题次要前景",
                Integer.valueOf(SceneThemes.DEFAULT.mutedForeground()),
                Integer.valueOf(header.__getChildren().get(1).getTextColor()));
    }

    /** 宿主自持文字与错误行取主题语义前景（正文/次要/错误档），不再停留在节点默认白色。 */
    @Test
    public void hostTextsFollowThemeForegrounds() {
        Signal<String> query = Signal.create("");
        Signal<String> error = Signal.create("boom");
        Signal<SearchPickerData.SearchResult> results = Signal.create(
                new SearchPickerData.SearchResult(Arrays.asList(candidate("a"))));
        Signal<Boolean> open = Signal.create(Boolean.FALSE);
        Props props = Props.builder(query, results, Signal.create(Boolean.TRUE),
                query::set, ignored -> { }, visualAdapter())
                .open(open).onCloseRequest(() -> open.set(Boolean.FALSE))
                .error(error).build();
        Result result = create(rt, props);
        rt.flush();
        open.set(Boolean.TRUE);
        rt.flush();
        layoutAll();
        layoutAll();

        SceneNode scrim = overlayRoot(0);
        SceneNode bar = topBar(scrim);
        Assert.assertEquals("顶栏标题 = 主题正文前景",
                Integer.valueOf(SceneThemes.DEFAULT.foreground()),
                Integer.valueOf(bar.__getChildren().get(0).getTextColor()));
        Assert.assertEquals("结果统计 = 主题次要前景",
                Integer.valueOf(SceneThemes.DEFAULT.mutedForeground()),
                Integer.valueOf(bar.__getChildren().get(2).getTextColor()));
        SceneNode errorNode = centerColumn(scrim).__getChildren().get(0);
        Assert.assertEquals("错误行文本同步", "boom", errorNode.getText());
        Assert.assertEquals("错误行 = 主题 errorText 前景",
                Integer.valueOf(SceneThemes.DEFAULT.errorText()),
                Integer.valueOf(errorNode.getTextColor()));
        // G19/P-02 收编钉：错误行前景 = 公共 SceneThemes.errorText 入口现值（逐位等值）。
        Assert.assertEquals("错误行前景 = SceneThemes.errorText 公共入口现值",
                SceneThemes.errorText(rt).get().intValue(), errorNode.getTextColor());
    }

    /**
     * G19/P-02 收编钉（P-04 口径）：错误行色经公共 {@code SceneThemes.errorText} 入口取色，
     * 换来源主题（深→浅）后重派生、节点不重建；两档 {@code assertNotEquals} 为前提。
     */
    @Test
    public void errorRowColorFollowsPublicEntryAcrossThemeSwitch() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：两档 errorText 必须不同",
                dark.errorText(), light.errorText());
        Signal<String> query = Signal.create("");
        Signal<String> error = Signal.create("boom");
        ThemedPanel t = new ThemedPanel(Props.builder(query,
                Signal.create(new SearchPickerData.SearchResult(Arrays.asList(candidate("a")))),
                Signal.create(Boolean.TRUE), query::set, ignored -> { }, visualAdapter())
                .error(error));
        t.open();

        SceneNode errorNode = centerColumn(overlayRoot(0)).__getChildren().get(0);
        Assert.assertEquals("初始错误行 = 深色 errorText",
                dark.errorText(), errorNode.getTextColor());

        t.pageTheme.set(light);
        rt.flush();

        Assert.assertEquals("换主题后错误行 = 浅色 errorText（公共入口重派生，不重建节点）",
                light.errorText(), errorNode.getTextColor());
        Assert.assertSame("主题切换不重建错误行节点", errorNode,
                centerColumn(overlayRoot(0)).__getChildren().get(0));
    }

    /** 成员带空态提示 = 主题次要前景（CategoryNavPane 空态同口径）。 */    @Test
    public void membersEmptyHintUsesMutedForeground() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), true);
        openPanel(f);
        SceneNode empty = membersPanel(overlayRoot(0)).__getChildren().get(2);
        Assert.assertEquals("空态占位文本合同不变", "No current members", empty.getText());
        Assert.assertEquals("空态提示 = 主题次要前景",
                Integer.valueOf(SceneThemes.DEFAULT.mutedForeground()),
                Integer.valueOf(empty.getTextColor()));
    }

    // ==================== 控件根字号：宿主六处内建文字跟随 ====================

    /**
     * 宿主六处内建文字跟随控件根字号（字号真值 = {@code Result.root}，也就是编写者 mount 后拿到的节点）：
     * 不设字号时保持 {@link SceneNode} 默认 16，{@link MountHandle#fontSize(int)} 后推进一帧即生效。
     *
     * <p>证据取自绘制产物——各宿主文字节点自身 fragment 内 TEXT 命令的
     * {@code getTextStyle().getFontSize()}（与 {@code hostTextsFollowThemeForegrounds} 互补：
     * 那里钉取色走节点属性，这里钉绘制字号走 fragment），不读节点属性代替绘制结果。</p>
     *
     * <p>两处分状态断言的原因：问题摘要在零问题（invalid=0 / duplicate=0）时文本是空串，
     * 空串不产 TEXT 命令，故先置入无效成员再断言；空态提示只在成员为空时挂载，故它在成员清空的那段断言。</p>
     */
    @Test
    public void hostTextFontSizeFollowsPanelRootAcrossEveryHostText() {
        Signal<String> query = Signal.create("");
        Signal<String> error = Signal.create("boom");
        Signal<List<SearchPickerData.CurrentMember>> members =
                Signal.create(Collections.<SearchPickerData.CurrentMember>emptyList());
        Signal<Boolean> open = Signal.create(Boolean.FALSE);
        Props props = Props.builder(query,
                Signal.create(new SearchPickerData.SearchResult(Arrays.asList(candidate("a")))),
                Signal.create(Boolean.TRUE), query::set, ignored -> { }, visualAdapter())
                .open(open).onCloseRequest(() -> open.set(Boolean.FALSE))
                .error(error)
                .currentMembers(members, ignored -> { })
                .build();
        // 推荐写法挂载：MountHandle.getRoot() 就是 Result.root（字号真值所在），编写者对它设字号即可。
        Result[] holder = new Result[1];
        MountHandle handle = rt.mount(sceneRoot, () -> {
            holder[0] = ScenePickerPanel.create(rt, props);
            return holder[0].root();
        });
        open.set(Boolean.TRUE);
        rt.flush();
        layoutAll();
        layoutAll();

        SceneNode scrim = overlayRoot(0);

        // ① 缺省不设字号：已出文字的五处宿主文字绘制字号 = 节点默认 16（空态提示见 ④）。
        paintEngine.paint(scrim);
        assertPaintedFontSize("顶栏标题", topBar(scrim).__getChildren().get(0), DEFAULT_FONT_SIZE);
        assertPaintedFontSize("结果统计", topBar(scrim).__getChildren().get(2), DEFAULT_FONT_SIZE);
        assertPaintedFontSize("错误行", centerColumn(scrim).__getChildren().get(0), DEFAULT_FONT_SIZE);
        assertPaintedFontSize("成员区标题",
                membersPanel(scrim).__getChildren().get(0).__getChildren().get(0), DEFAULT_FONT_SIZE);
        assertPaintedFontSize("空态提示", membersPanel(scrim).__getChildren().get(2), DEFAULT_FONT_SIZE);

        // ② 问题摘要：零问题时是空串（无 TEXT 命令），置入无效成员后才有可断言的绘制产物。
        members.set(Arrays.asList(malformedMember(0L)));
        rt.flush();
        layoutAll();
        layoutAll();
        SceneNode issues = membersPanel(scrim).__getChildren().get(0).__getChildren().get(1);
        Assert.assertFalse("前置：问题摘要应有文案", issues.getText().isEmpty());
        paintEngine.paint(scrim);
        assertPaintedFontSize("问题摘要", issues, DEFAULT_FONT_SIZE);

        // ③ 字号生效：handle.fontSize(24) → 推进一帧 → 在场五处宿主文字绘制字号 = 24。
        handle.fontSize(24);
        layoutAll();
        layoutAll();
        paintEngine.paint(scrim);
        assertPaintedFontSize("顶栏标题", topBar(scrim).__getChildren().get(0), 24);
        assertPaintedFontSize("结果统计", topBar(scrim).__getChildren().get(2), 24);
        assertPaintedFontSize("错误行", centerColumn(scrim).__getChildren().get(0), 24);
        assertPaintedFontSize("成员区标题",
                membersPanel(scrim).__getChildren().get(0).__getChildren().get(0), 24);
        assertPaintedFontSize("问题摘要",
                membersPanel(scrim).__getChildren().get(0).__getChildren().get(1), 24);
        // 字号真值确实落在 MountHandle.getRoot()（= Result.root）这个节点上。
        Assert.assertSame("MountHandle.getRoot() 即 Result.root（字号真值所在）",
                holder[0].root(), handle.getRoot());
        Assert.assertEquals("控件根字号 = 句柄写入值", 24, handle.getRoot().getFontSize());

        // ④ 空态提示跟随：成员清空后空态提示重新挂载（按当前字号播种），绘制字号 = 24。
        members.set(Collections.<SearchPickerData.CurrentMember>emptyList());
        rt.flush();
        layoutAll();
        layoutAll();
        SceneNode hint = membersPanel(scrim).__getChildren().get(2);
        Assert.assertEquals("空态占位文本合同不变", "No current members", hint.getText());
        paintEngine.paint(scrim);
        assertPaintedFontSize("空态提示", hint, 24);
    }

    /** 断言某宿主文字节点绘制出的 TEXT 字号（绘制产物证据；须先对该节点所在树执行过 paint）。 */
    private static void assertPaintedFontSize(String name, SceneNode node, int fontSize) {
        Assert.assertEquals(name + " 绘制字号（取自自身 fragment 的 TEXT 命令）",
                fontSize, ownPaintedFontSize(node));
    }

    /** 节点自身绘制片段内的 TEXT 字号（与 {@link #ownBackdropCount} 同款：先整树 paint 再读缓存 fragment）。 */
    private static int ownPaintedFontSize(SceneNode node) {
        Object cached = node.getCachedPaint();
        Assert.assertTrue("宿主文字节点应已绘制出自身 fragment", cached instanceof PaintFragment);
        for (PaintCommand command : ((PaintFragment) cached).getCommands()) {
            if (command.getType() == PaintCommandType.TEXT) {
                return command.getTextStyle().getFontSize();
            }
        }
        throw new AssertionError("宿主文字节点未绘制出 TEXT 命令：" + node.getText());
    }

    // ==================== G14 整树 BACKDROP 构成表 ====================

    /**
     * 主面板树构成（SINGLE_VALUE、2 候选、1 分类、无维度、浮层未开）：
     * 宿主卡片 PANEL 1 + 导航底座 TOOLBAR 1 + 导航视口 GROUP 1 + 搜索框 INPUT 1
     * + 结果底座 GROUP 1 + 信息条 TOOLBAR 1 = 整树 6 颗，每颗各采样一次；
     * 复用行/结果单元/遮罩零滤镜；宿主外壳（中栏）零表面。
     */
    @Test
    public void wholeTreeBackdropCompositionMainPanel() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), false);
        f.categoryMap.put("a", "cat1");
        f.categoryMap.put("b", "cat1");
        f.categories.set(Arrays.asList(new SearchPickerCategories.Category("cat1", "Cat1")));
        openPanel(f);
        SceneNode scrim = overlayRoot(0);
        SceneNode card = panelCard(scrim);
        SceneNode nav = navPane(scrim);
        SceneNode navViewport = nav.__getChildren().get(0).__getChildren().get(0);
        SceneNode searchInput = f.result.firstFocusTarget().get();
        SceneNode resultViewport = f.result.grid().get();
        SceneNode infoBar = centerColumn(scrim).__getChildren().get(2);

        Assert.assertEquals("整树声明滤镜的节点 = 6 颗表面", 6, surfaceNodeCount(scrim));
        Set<SceneNode> surfaces = identitySet(collect(scrim));
        Assert.assertTrue("含宿主卡片（PANEL）", surfaces.contains(card));
        Assert.assertTrue("含导航底座（TOOLBAR）", surfaces.contains(nav));
        Assert.assertTrue("含导航滚动视口（GROUP，G07 容器自持）", surfaces.contains(navViewport));
        Assert.assertTrue("含搜索输入框（INPUT，SceneTextInput 复用）", surfaces.contains(searchInput));
        Assert.assertTrue("含结果底座（GROUP）", surfaces.contains(resultViewport));
        Assert.assertTrue("含信息条（TOOLBAR）", surfaces.contains(infoBar));

        Assert.assertEquals("整树 BACKDROP 命令 = 6（每颗表面各采样一次，无漏无重）",
                6, backdropCount(scrim));
        for (SceneNode surface : collect(scrim)) {
            Assert.assertEquals("单节点至多一条 BACKDROP", 1, ownBackdropCount(surface));
        }
        Assert.assertEquals("中栏外壳零 BACKDROP（实底已除，无第二层玻璃）", 0,
                ownBackdropCount(centerColumn(scrim)));
        Assert.assertNull("scrim 透明壳不声明滤镜", scrim.getBackdrop());
        Assert.assertEquals("scrim 无实底", BG_TRANSPARENT, scrim.getBackgroundColor());

        SceneNode navRows = navViewport.__getChildren().get(0);
        for (SceneNode row : navRows.__getChildren()) {
            Assert.assertEquals("导航复用行零 BACKDROP", 0, ownBackdropCount(row));
        }
        for (int i = 0; i < 2; i++) {
            SceneNode cell = gridCell(resultViewport, i);
            Assert.assertEquals("结果单元零 BACKDROP", 0, ownBackdropCount(cell));
            Assert.assertNull("结果单元不声明滤镜", cell.getBackdrop());
        }
    }

    /**
     * 变体浮层树构成：浮层面板 OVERLAY 1 + 分段底座 TOOLBAR 1 + 两个段 INDICATOR 轻滤镜 2
     * + 变体表视口 GROUP 1 + 取消/确认按钮 2 = 7 颗；勾选复用行零滤镜；scrim 只负责遮罩。
     * 主面板树不受浮层影响（仍 6 颗）。
     */
    @Test
    public void wholeTreeBackdropCompositionVariantOverlay() {
        Fixture f = new Fixture(Arrays.asList(candidateWithVariants("a", "v1", "v2")), false);
        openPanel(f);
        click(gridCell(f.result.grid().get(), 0));
        layoutAll();
        Assert.assertEquals(2, rt.getOverlayHost().size());

        SceneNode scrim = overlayRoot(0);
        SceneNode variantCard = scrim.__getChildren().get(0);
        SceneNode segmented = variantCard.__getChildren().get(1);
        SceneNode variantViewport = variantCard.__getChildren().get(2).__getChildren().get(0);
        SceneNode footer = variantCard.__getChildren().get(3);

        Assert.assertEquals("浮层树声明滤镜节点 = 7", 7, surfaceNodeCount(scrim));
        Set<SceneNode> surfaces = identitySet(collect(scrim));
        Assert.assertTrue("含浮层面板（OVERLAY，VariantChooser 一颗）", surfaces.contains(variantCard));
        Assert.assertTrue("含分段底座（TOOLBAR）", surfaces.contains(segmented));
        Assert.assertTrue("含段 0（INDICATOR 轻滤镜，G09 非虚拟化口径）",
                surfaces.contains(segmented.__getChildren().get(0)));
        Assert.assertTrue("含段 1（INDICATOR 轻滤镜）",
                surfaces.contains(segmented.__getChildren().get(1)));
        Assert.assertTrue("含变体表视口（GROUP）", surfaces.contains(variantViewport));
        Assert.assertTrue("含取消按钮（BUTTON_STANDARD，G03 复用）",
                surfaces.contains(footer.__getChildren().get(0)));
        Assert.assertTrue("含确认按钮（BUTTON_STANDARD）",
                surfaces.contains(footer.__getChildren().get(1)));
        Assert.assertEquals("浮层树 BACKDROP = 7", 7, backdropCount(scrim));
        Assert.assertNull("变体 scrim 不装玻璃", scrim.getBackdrop());
        Assert.assertEquals("变体 scrim 保持静态遮罩底（只负责遮罩）", 0xCC000000,
                scrim.getBackgroundColor());
        for (SceneNode row : variantViewport.__getChildren().get(0).__getChildren()) {
            Assert.assertEquals("变体复用行零 BACKDROP", 0, ownBackdropCount(row));
        }
        Assert.assertEquals("主面板树不受浮层影响（仍 6 颗）", 6, backdropCount(overlayRoot(1)));
    }

    private List<SceneNode> collect(SceneNode root) {
        List<SceneNode> out = new ArrayList<SceneNode>();
        collectSurfaceNodes(root, out);
        return out;
    }

    private static Set<SceneNode> identitySet(List<SceneNode> nodes) {
        Set<SceneNode> set = Collections.newSetFromMap(
                new IdentityHashMap<SceneNode, Boolean>());
        set.addAll(nodes);
        return set;
    }

    // ==================== G14 五配件集成调用证据（G13 已验收口径在真实装配中保持） ====================

    /** 集成证据① CategoryNavPane：底座 TOOLBAR 逐项 = 单实例已验收；选中行强调 0x59 轻量档；行零滤镜。 */
    @Test
    public void integratedCategoryNavPaneMatchesAcceptedRecipe() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), false);
        f.categoryMap.put("a", "cat1");
        f.categoryMap.put("b", "cat2");
        f.categories.set(Arrays.asList(new SearchPickerCategories.Category("cat1", "Cat1"),
                new SearchPickerCategories.Category("cat2", "Cat2")));
        openPanel(f);
        SceneNode scrim = overlayRoot(0);
        SceneNode nav = navPane(scrim);

        Assert.assertEquals("底座背景 = TOOLBAR idle 染色",
                TOOLBAR.getIdle().getTint(), nav.getBackgroundColor());
        Assert.assertEquals("底座边框宽 = 配方", TOOLBAR.getBorderWidth(), nav.getBorderWidth());
        Assert.assertEquals("底座圆角 = 配方", TOOLBAR.getCornerRadius(), nav.getCornerRadius());
        Assert.assertEquals("底座缘色 = TOOLBAR idle 缘色",
                TOOLBAR.getIdle().getEdge(), nav.getBorderColor());
        Assert.assertEquals("底座实体高度 = 配方 idle 档",
                TOOLBAR.getIdle().getElevation(), nav.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull("底座装 TOOLBAR 滤镜", nav.getBackdrop());
        Assert.assertEquals("底座宽度布局合同不变", CategoryNavPane.NAV_WIDTH, nav.getPreferredWidth());

        SceneNode rows = nav.__getChildren().get(0).__getChildren().get(0).__getChildren().get(0);
        Assert.assertEquals("全部 + 两分类", 3, rows.__getChildren().size());
        SceneNode allRow = rows.__getChildren().get(0);
        Assert.assertEquals("初始选中「全部」行 = 强调选中档 0x59",
                selectedTint(SceneThemes.DEFAULT.accent()), allRow.getBackgroundColor());
        click(rows.__getChildren().get(1));
        rt.flush();
        pointerAway();
        Assert.assertEquals("切换后 cat1 行 = 选中档（cat 行行为合同保持）", "cat1",
                f.result.currentCategoryKey().get());
        Assert.assertEquals("切换后 cat1 行 = 选中档",
                selectedTint(SceneThemes.DEFAULT.accent()),
                rows.__getChildren().get(1).getBackgroundColor());
        Assert.assertEquals("全部行退选回 INDICATOR idle 档",
                INDICATOR.getIdle().getTint(), allRow.getBackgroundColor());

        backdropCount(scrim);
        for (SceneNode row : rows.__getChildren()) {
            Assert.assertEquals("行不叠第二层玻璃", 0, ownBackdropCount(row));
            Assert.assertEquals("行不写边框宽", 0, row.getBorderWidth());
            Assert.assertEquals("行不写圆角", 0, row.getCornerRadius());
            Assert.assertEquals("行普通绘制路径", -1.0F, row.__getSurfaceElevation(), EPSILON);
        }
        Assert.assertEquals("行标签 = 主题正文前景",
                Integer.valueOf(SceneThemes.DEFAULT.foreground()),
                Integer.valueOf(rows.__getChildren().get(0).__getChildren().get(0).getTextColor()));
        Assert.assertEquals("数量徽章 = 主题次要前景",
                Integer.valueOf(SceneThemes.DEFAULT.mutedForeground()),
                Integer.valueOf(rows.__getChildren().get(0).__getChildren().get(1).getTextColor()));
    }

    /** 集成证据② SearchResultList：底座 GROUP 逐项；单元轻量覆盖（选中 0x59 选区底）零滤镜。 */
    @Test
    public void integratedSearchResultListMatchesAcceptedRecipe() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), false);
        f.commitResult[0] = false;
        openPanel(f);
        SceneNode vp = f.result.grid().get();
        Assert.assertEquals("结果底座背景 = GROUP idle 染色",
                GROUP.getIdle().getTint(), vp.getBackgroundColor());
        Assert.assertEquals("底座边框宽 = GROUP 配方", GROUP.getBorderWidth(), vp.getBorderWidth());
        Assert.assertEquals("底座圆角 = GROUP 配方", GROUP.getCornerRadius(), vp.getCornerRadius());
        Assert.assertEquals("底座缘色 = GROUP idle 缘色",
                GROUP.getIdle().getEdge(), vp.getBorderColor());
        Assert.assertEquals("底座实体高度 = GROUP idle",
                GROUP.getIdle().getElevation(), vp.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull("底座默认带液态玻璃滤镜", vp.getBackdrop());
        Assert.assertEquals("底座自身恰好一条 BACKDROP", 1, backdropCount(vp));

        SceneNode cell0 = gridCell(vp, 0);
        SceneNode cell1 = gridCell(vp, 1);
        Assert.assertEquals("默认单元透明（露出底座玻璃）", BG_TRANSPARENT, cell0.getBackgroundColor());
        click(cell1);
        rt.flush();
        Assert.assertEquals("选中单元 = 主题选区背景 0x59 轻量覆盖",
                selectedTint(SceneThemes.DEFAULT.selectionBackground()), cell1.getBackgroundColor());
        Assert.assertEquals("未选中单元保持透明", BG_TRANSPARENT, cell0.getBackgroundColor());
        Assert.assertNull("选中单元仍零滤镜", cell1.getBackdrop());
        Assert.assertEquals("单元标签 = 主题次要前景",
                Integer.valueOf(SceneThemes.DEFAULT.mutedForeground()),
                Integer.valueOf(cell1.__getChildren().get(1).getTextColor()));
        Assert.assertEquals("图位圆角属渲染协议",
                SceneChromeTokens.RADIUS_SM, cell1.__getChildren().get(0).getCornerRadius());
        Assert.assertEquals("无图占位底色属渲染协议",
                SearchResultList.DEFAULT_PLACEHOLDER_COLOR,
                cell1.__getChildren().get(0).getBackgroundColor());
    }

    /** 集成证据③ PickerInfoBar：TOOLBAR 条逐项；文本次要前景；自身恰一颗 BACKDROP、宿主未包第二层。 */
    @Test
    public void integratedPickerInfoBarMatchesAcceptedStrip() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), false);
        openPanel(f);
        SceneNode infoBar = centerColumn(overlayRoot(0)).__getChildren().get(2);

        Assert.assertEquals("信息条高度合同不变", PickerInfoBar.INFO_BAR_HEIGHT, infoBar.getPreferredHeight());
        Assert.assertEquals("信息条背景 = TOOLBAR idle 染色",
                TOOLBAR.getIdle().getTint(), infoBar.getBackgroundColor());
        Assert.assertEquals("信息条边框宽 = 配方", TOOLBAR.getBorderWidth(), infoBar.getBorderWidth());
        Assert.assertEquals("信息条圆角 = 配方", TOOLBAR.getCornerRadius(), infoBar.getCornerRadius());
        Assert.assertNotNull("信息条装 TOOLBAR 滤镜", infoBar.getBackdrop());
        Assert.assertEquals("信息条自身恰好一条 BACKDROP", 1, backdropCount(infoBar));
        Assert.assertEquals("信息条文本 = 主题次要前景",
                Integer.valueOf(SceneThemes.DEFAULT.mutedForeground()),
                Integer.valueOf(infoBar.__getChildren().get(0).getTextColor()));
        Assert.assertNull("信息条文本不装滤镜", infoBar.__getChildren().get(0).getBackdrop());
    }

    /** 集成证据④ MemberGrid：底座 GROUP 逐项；单元零表面写入；卡内按钮复用 G03 已主题化 SceneButton。 */
    @Test
    public void integratedMemberGridMatchesAcceptedBase() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), true);
        f.members.set(Arrays.asList(member(0L, "a")));
        openPanel(f);
        SceneNode band = membersPanel(overlayRoot(0));
        SceneNode viewport = band.__getChildren().get(1).__getChildren().get(0);

        Assert.assertEquals("成员网格底座 = GROUP idle 染色",
                GROUP.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("底座圆角 = GROUP 配方", GROUP.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("底座边框宽 = GROUP 配方", GROUP.getBorderWidth(), viewport.getBorderWidth());
        Assert.assertEquals("底座缘色 = GROUP idle 缘色",
                GROUP.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("底座实体高度 = GROUP idle",
                GROUP.getIdle().getElevation(), viewport.__getSurfaceElevation(), EPSILON);
        backdropCount(overlayRoot(0));
        Assert.assertEquals("底座自身恰好一条 BACKDROP（宿主不再包第二层玻璃）",
                1, ownBackdropCount(viewport));

        SceneNode cell = memberCell(band, 0);
        Assert.assertEquals("单元零底色", BG_TRANSPARENT, cell.getBackgroundColor());
        Assert.assertEquals("单元零圆角", 0, cell.getCornerRadius());
        Assert.assertEquals("单元零边框宽", 0, cell.getBorderWidth());
        Assert.assertNull("单元零滤镜", cell.getBackdrop());
        Assert.assertEquals("单元普通绘制路径", -1.0F, cell.__getSurfaceElevation(), EPSILON);
        Assert.assertEquals("单元主文本 = 主题正文前景",
                Integer.valueOf(SceneThemes.DEFAULT.foreground()),
                Integer.valueOf(cell.__getChildren().get(0).__getChildren().get(1).getTextColor()));
        Assert.assertEquals("单元副文本 = 主题次要前景",
                Integer.valueOf(SceneThemes.DEFAULT.mutedForeground()),
                Integer.valueOf(cell.__getChildren().get(1).getTextColor()));
        SceneNode edit = rowEdit(cell);
        Assert.assertEquals("卡内编辑按钮复用 BUTTON_STANDARD 配方（G03 已验收）",
                BUTTON_STANDARD.getIdle().getTint(), edit.getBackgroundColor());
        Assert.assertNotNull("按钮滤镜归按钮自身表面（宿主不覆写）", edit.getBackdrop());
    }

    /** 集成证据⑤ VariantChooser：浮层卡 OVERLAY 逐项；复用行轻量档零滤镜；scrim 只遮罩。 */
    @Test
    public void integratedVariantChooserMatchesAcceptedOverlay() {
        Fixture f = new Fixture(Arrays.asList(candidateWithVariants("a", "v1", "v2")), false);
        openPanel(f);
        click(gridCell(f.result.grid().get(), 0));
        layoutAll();
        SceneNode scrim = overlayRoot(0);
        SceneNode card = scrim.__getChildren().get(0);

        Assert.assertEquals("浮层面板背景 = OVERLAY idle 染色",
                OVERLAY.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("面板边框宽 = 配方", OVERLAY.getBorderWidth(), card.getBorderWidth());
        Assert.assertEquals("面板圆角 = 配方", OVERLAY.getCornerRadius(), card.getCornerRadius());
        Assert.assertEquals("面板缘色 = OVERLAY idle 缘色",
                OVERLAY.getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("面板实体高度 = 配方 idle 档",
                OVERLAY.getIdle().getElevation(), card.__getSurfaceElevation(), EPSILON);
        backdropCount(scrim);
        Assert.assertEquals("面板自身恰一颗 BACKDROP（浮层表面一颗，宿主不叠加；内部控件表面另计）",
                1, ownBackdropCount(card));
        Assert.assertNull("scrim 不装玻璃", scrim.getBackdrop());
        Assert.assertEquals("scrim 静态遮罩底不变", 0xCC000000, scrim.getBackgroundColor());

        SceneNode segmented = card.__getChildren().get(1);
        click(segmented.__getChildren().get(1));
        rt.flush();
        SceneNode rows = card.__getChildren().get(2).__getChildren().get(0).__getChildren().get(0);
        click(rows.__getChildren().get(0));
        rt.flush();
        pointerAway();
        Assert.assertEquals("勾选行为草稿 [v1]（受控行为合同保持）",
                Collections.singletonList("v1"), f.result.variantKeys().get());
        Assert.assertEquals("勾选行 = 强调选中档 0x59",
                selectedTint(SceneThemes.DEFAULT.accent()),
                rows.__getChildren().get(0).getBackgroundColor());
        Assert.assertEquals("未勾选行 = INDICATOR idle 档",
                INDICATOR.getIdle().getTint(), rows.__getChildren().get(1).getBackgroundColor());
        for (SceneNode row : rows.__getChildren()) {
            Assert.assertEquals("复用行零滤镜（G13 裁决口径在装配中保持）", 0, backdropCount(row));
        }
        Assert.assertEquals("宿主未给浮层再包一层（scrim 贡献 0）",
                backdropCount(card), backdropCount(scrim));
    }

    // ==================== G14 主题切换 / 图像协议 / 行为合同 / 回收 ====================

    /** 可切换局部主题作用域下构建的面板：mount + withTheme 来源作用域（portal 内容继承来源主题）。 */
    private final class ThemedPanel {
        final Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        final Signal<Boolean> openSignal = Signal.create(Boolean.FALSE);
        final Signal<String> query = Signal.create("");
        final List<SearchPickerData.Selection> commits = new ArrayList<SearchPickerData.Selection>();
        final boolean[] commitResult = {true};
        final Result result;
        final MountHandle handle;

        ThemedPanel(Props.Builder builder) {
            builder.selectionCommit(selection -> {
                commits.add(selection);
                return commitResult[0];
            });
            builder.open(openSignal).onCloseRequest(() -> openSignal.set(Boolean.FALSE));
            final Props props = builder.build();
            final Result[] holder = new Result[1];
            handle = rt.mount(sceneRoot, () -> {
                SceneThemes.withTheme(pageTheme,
                        () -> holder[0] = ScenePickerPanel.create(rt, props));
                return holder[0].root();
            });
            rt.flush();
            result = holder[0];
        }

        void open() {
            openSignal.set(Boolean.TRUE);
            rt.flush();
            layoutAll();
            layoutAll();
        }
    }

    /**
     * 主题切换（深→浅）：宿主 PANEL 与五配件表面各自重派生为新档、选择（高亮 + 选中底色）不丢
     * 且跟随新主题选区色、节点身份不变、effect 不增长；中栏/成员带外壳仍零表面（实底复活会被本
     * 用例抓住——P-04 变异检查点）。
     */
    @Test
    public void themeSwitchKeepsSelectionIdentityAndEffects() {
        Signal<String> query = Signal.create("");
        Signal<SearchPickerData.SearchResult> results = Signal.create(
                new SearchPickerData.SearchResult(Arrays.asList(
                        candidate("a"), candidate("b"), candidate("c"))));
        ThemedPanel t = new ThemedPanel(Props.builder(query, results,
                Signal.create(Boolean.TRUE), query::set, ignored -> { }, visualAdapter()));
        t.commitResult[0] = false;
        t.open();

        SceneNode scrim = overlayRoot(0);
        SceneNode card = panelCard(scrim);
        SceneNode nav = navPane(scrim);
        SceneNode searchInput = t.result.firstFocusTarget().get();
        SceneNode infoBar = centerColumn(scrim).__getChildren().get(2);
        SceneNode vp = t.result.grid().get();
        SceneNode cell = gridCell(vp, 1);
        click(cell);
        Assert.assertEquals("前置：高亮写入 1", Integer.valueOf(1), t.result.gridHighlight().get());
        Assert.assertEquals("前置：选中单元 = 深色主题选区色 0x59 档",
                selectedTint(SceneThemes.DEFAULT.selectionBackground()), cell.getBackgroundColor());
        Assert.assertEquals("前置：卡片 = 深色 PANEL 档",
                PANEL.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("前置：导航底座 = 深色 TOOLBAR 档",
                TOOLBAR.getIdle().getTint(), nav.getBackgroundColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        SceneTheme light = SceneTheme.liquidGlassLight();
        t.pageTheme.set(light);
        rt.flush();

        Assert.assertEquals("卡片背景 = 浅色 PANEL idle 染色（宿主配方重派生）",
                light.surface(SceneTheme.Role.PANEL).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("导航底座 = 浅色 TOOLBAR（配件跟随来源主题，宿主未钉死）",
                light.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), nav.getBackgroundColor());
        Assert.assertEquals("结果底座 = 浅色 GROUP",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), vp.getBackgroundColor());
        Assert.assertEquals("信息条 = 浅色 TOOLBAR",
                light.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), infoBar.getBackgroundColor());
        Assert.assertEquals("搜索框 = 浅色 INPUT",
                light.surface(SceneTheme.Role.INPUT).getIdle().getTint(),
                searchInput.getBackgroundColor());
        Assert.assertEquals("顶栏标题 = 浅色主题正文前景", Integer.valueOf(light.foreground()),
                Integer.valueOf(topBar(scrim).__getChildren().get(0).getTextColor()));
        Assert.assertEquals("结果统计 = 浅色主题次要前景", Integer.valueOf(light.mutedForeground()),
                Integer.valueOf(topBar(scrim).__getChildren().get(2).getTextColor()));

        Assert.assertEquals("主题切换后高亮不丢", Integer.valueOf(1), t.result.gridHighlight().get());
        Assert.assertEquals("选中单元重派生为浅色主题选区色（证明非静态回退）",
                selectedTint(light.selectionBackground()), cell.getBackgroundColor());
        Assert.assertEquals("中栏仍零表面底色（实底复活会被本断言抓住）",
                BG_TRANSPARENT, centerColumn(scrim).getBackgroundColor());
        Assert.assertEquals("中栏仍零边框", 0, centerColumn(scrim).getBorderWidth());
        Assert.assertNull("结果单元不因主题切换长出滤镜", cell.getBackdrop());
        Assert.assertSame("卡片节点身份不变", card, panelCard(overlayRoot(0)));
        Assert.assertSame("选中单元节点身份不变", cell, gridCell(t.result.grid().get(), 1));
        Assert.assertEquals("主题切换不增长 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    /** 变体草稿跨主题切换：模式/勾选 key/浮层展开保持，OVERLAY 卡重派生新档、行仍零滤镜。 */
    @Test
    public void variantDraftSurvivesThemeSwitch() {
        Signal<String> query = Signal.create("");
        Signal<SearchPickerData.SearchResult> results = Signal.create(
                new SearchPickerData.SearchResult(Arrays.asList(candidateWithVariants("a", "v1", "v2"))));
        ThemedPanel t = new ThemedPanel(Props.builder(query, results,
                Signal.create(Boolean.TRUE), query::set, ignored -> { }, visualAdapter()));
        t.open();
        click(gridCell(t.result.grid().get(), 0));
        layoutAll();
        SceneNode card = overlayRoot(0).__getChildren().get(0);
        SceneNode segmented = card.__getChildren().get(1);
        SceneNode rows = card.__getChildren().get(2).__getChildren().get(0).__getChildren().get(0);
        click(segmented.__getChildren().get(1));
        rt.flush();
        click(rows.__getChildren().get(0));
        pointerAway();
        Assert.assertEquals("前置：草稿 = [v1]", Collections.singletonList("v1"),
                t.result.variantKeys().get());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        SceneTheme light = SceneTheme.liquidGlassLight();
        t.pageTheme.set(light);
        rt.flush();

        Assert.assertEquals("主题切换：选择模式不丢", SearchPickerData.SelectionMode.SELECTED,
                t.result.variantMode().get());
        Assert.assertEquals("主题切换：勾选草稿不丢", Collections.singletonList("v1"),
                t.result.variantKeys().get());
        Assert.assertTrue("主题切换：浮层保持展开", t.result.variantsOpen().get().booleanValue());
        Assert.assertEquals("面板 = 浅色 OVERLAY idle 染色（重派生不重建）",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertSame("面板节点身份不变", card, overlayRoot(0).__getChildren().get(0));
        Assert.assertEquals("勾选行跟随浅色主题强调 0x59 档",
                selectedTint(light.accent()), rows.__getChildren().get(0).getBackgroundColor());
        Assert.assertNull("行切换后仍零滤镜", rows.__getChildren().get(0).getBackdrop());
        Assert.assertEquals("主题切换不增长 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    /**
     * 反向钉住「物品图像不改色」（契约 §4.1/§7.3）：真实装配中，有图单元图片源与透明底、无图
     * 占位底、图位圆角、无效徽章底跨深→浅逐字节不变；图像节点全程零滤镜。
     */
    @Test
    public void itemImagesKeepRenderProtocolAcrossThemeSwitch() {
        final SceneImageSource imageA = new SceneImageSource() {
            @Override
            public String registryKey() {
                return "test:a:0";
            }
        };
        VisualAdapter adapter = new VisualAdapter() {
            @Override
            public String candidateLabel(SearchPickerData.Candidate candidate) {
                return candidate.label();
            }

            @Override
            public String variantLabel(SearchPickerData.Variant variant) {
                return variant.label();
            }

            @Override
            public SceneImageSource candidateImage(SearchPickerData.Candidate candidate) {
                return "a".equals(candidate.key()) ? imageA : null;
            }
        };
        Signal<String> query = Signal.create("");
        Signal<SearchPickerData.SearchResult> results = Signal.create(
                new SearchPickerData.SearchResult(Arrays.asList(candidate("a"), candidate("b"))));
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(
                Arrays.asList(member(0L, "a"), malformedMember(1L)));
        ThemedPanel t = new ThemedPanel(Props.builder(query, results,
                Signal.create(Boolean.TRUE), query::set, ignored -> { }, adapter)
                .currentMembers(members, ignored -> { }));
        t.open();

        SceneNode scrim = overlayRoot(0);
        SceneNode grid = t.result.grid().get();
        SceneNode iconWithImage = gridCell(grid, 0).__getChildren().get(0);
        SceneNode iconPlaceholder = gridCell(grid, 1).__getChildren().get(0);
        SceneNode band = membersPanel(scrim);
        SceneNode memberIcon = memberCell(band, 0).__getChildren().get(0).__getChildren().get(0);
        SceneNode malformedIcon = memberCell(band, 1).__getChildren().get(0).__getChildren().get(0);
        SceneNode malformedBadge = rowBadge(memberCell(band, 1));

        Assert.assertSame("结果单元有图挂原图片源", imageA, iconWithImage.getImageSource());
        Assert.assertEquals("有图单元底色透明（不改色）", BG_TRANSPARENT, iconWithImage.getBackgroundColor());
        Assert.assertEquals("无图结果单元 = 占位底（渲染协议）",
                SearchResultList.DEFAULT_PLACEHOLDER_COLOR, iconPlaceholder.getBackgroundColor());
        Assert.assertEquals("图位圆角属协议", SceneChromeTokens.RADIUS_SM, iconPlaceholder.getCornerRadius());
        Assert.assertSame("成员卡图标挂原图片源", imageA, memberIcon.getImageSource());
        Assert.assertEquals("无候选成员图标 = MemberGrid 占位底",
                MemberGrid.PLACEHOLDER_COLOR, malformedIcon.getBackgroundColor());
        Assert.assertEquals("无效徽章底 = DANGER_BG_SUBTLE（状态徽标协议）",
                SceneChromeTokens.DANGER_BG_SUBTLE, malformedBadge.getBackgroundColor());

        t.pageTheme.set(SceneTheme.liquidGlassLight());
        rt.flush();

        Assert.assertSame("主题切换不动图片源", imageA, iconWithImage.getImageSource());
        Assert.assertEquals("主题切换不重染有图底", BG_TRANSPARENT, iconWithImage.getBackgroundColor());
        Assert.assertEquals("主题切换不重染占位底",
                SearchResultList.DEFAULT_PLACEHOLDER_COLOR, iconPlaceholder.getBackgroundColor());
        Assert.assertEquals("主题切换不重染成员占位底",
                MemberGrid.PLACEHOLDER_COLOR, malformedIcon.getBackgroundColor());
        Assert.assertEquals("主题切换不重染徽章底",
                SceneChromeTokens.DANGER_BG_SUBTLE, malformedBadge.getBackgroundColor());
        Assert.assertEquals("图位圆角不变", SceneChromeTokens.RADIUS_SM, iconPlaceholder.getCornerRadius());
        Assert.assertNull("图像节点不因主题装滤镜", iconPlaceholder.getBackdrop());
        Assert.assertNull("成员图标不装滤镜", malformedIcon.getBackdrop());
    }

    /** 行为合同（查询路径）：搜索框输入透传 onQuery；结果换代按新 key 渲染、旧单元不残留。 */
    @Test
    public void typingInSearchInputPropagatesQueryAndReplacesResults() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")), false);
        openPanel(f);
        SceneNode input = f.result.firstFocusTarget().get();
        Assert.assertSame("首焦点即搜索框", input, rt.getFocusedNode());

        typeText("stone");
        rt.flush();
        Assert.assertEquals("搜索框输入透传 onQuery", "stone", f.query.get());

        f.results.set(new SearchPickerData.SearchResult(Arrays.asList(candidate("stone:x"))));
        rt.flush();
        layoutAll();
        SceneNode grid = f.result.grid().get();
        Assert.assertEquals("结果换代后按新数据渲染", 1, mountedItemCount(grid));
        // 标签按 64px 单元宽省略号截断（TextEllipsizer 既有合同），核对前缀来自新数据、旧数据不残留。
        String cellLabel = gridCell(grid, 0).__getChildren().get(1).getText();
        Assert.assertTrue("换代后单元标签来自新数据（截断后仍含前缀）: " + cellLabel,
                cellLabel.startsWith("stone:"));
    }

    /** 开关面板/卸载回收：portal 子树（宿主表面 + 五配件绑定）关闭即回收；句柄 dispose 后回基线。 */
    @Test
    public void closeReclaimsPortalTreeAndDisposeReturnsEffectsToBaseline() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SearchPickerData.SearchResult> results = Signal.create(
                new SearchPickerData.SearchResult(Arrays.asList(candidate("a"))));
        ThemedPanel t = new ThemedPanel(Props.builder(Signal.create(""), results,
                Signal.create(Boolean.TRUE), ignored -> { }, ignored -> { }, visualAdapter()));
        int created = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("面板构建登记响应式绑定", created > baseline);

        t.open();
        int opened = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("打开挂载 portal 子树（宿主表面与配件绑定注册在 portal Owner）",
                opened > created);

        t.openSignal.set(Boolean.FALSE);
        rt.flush();
        Assert.assertEquals("关闭回收 portal 子树全部外观绑定（回到构建基线）",
                created, ReactiveTestProbe.registeredEffectCount());
        Assert.assertTrue(rt.getOverlayHost().isEmpty());

        t.handle.dispose();
        rt.flush();
        Assert.assertEquals("卸载后 effect 回到基线",
                baseline, ReactiveTestProbe.registeredEffectCount());
    }
}
