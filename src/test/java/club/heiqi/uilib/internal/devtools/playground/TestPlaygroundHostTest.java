package club.heiqi.uilib.internal.devtools.playground;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link TestPlaygroundHost} 页面状态机与骨架结构测试。
 *
 * <p>headless 构造（input=null）+ 真实布局引擎驱动：断言骨架树结构、初始 Home 页、
 * 分段导航切换（单槽替换、旧页析构）、同页 no-op、全部注册页可挂载。
 * 纯视觉/交互细节不在此断言。</p>
 *
 * <p>G16/外壳追加：外壳三块表面（root/header/viewport）取对应角色配方、主题切换不重建
 * 节点且不丢当前页/导航选中、局部 {@code withTheme} 只作用于其作用域内内容、卸载回收绑定。</p>
 */
public class TestPlaygroundHostTest {

    /** 画布宽：导航段横排总宽随页数线性增长（9 页实测末段右缘 833px），
     *  旧 720 宽在 8 页时贴边、9 页起末页段整体出界导致点击不可达；与既有断言无耦合。 */
    private static final int CANVAS_WIDTH = 1000;
    private static final int CANVAS_HEIGHT = 520;

    /** 库默认主题（宿主 runtime 默认档）：外壳外观断言的唯一来源。 */
    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    /** 浅色档：主题切换断言用（与深色档各角色配方/语义色均不同）。 */
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();
    /** 浮点比较容差。 */
    private static final float EPSILON = 0.0001F;

    private TestPlaygroundHost host;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new TestPlaygroundHost(null);
        doLayout();
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /** 用宿主布局引擎对齐主树（与真机 render 前的主树 layout 同口径）。 */
    private void doLayout() {
        SceneLayoutEngine engine = host.getLayoutEngine();
        engine.layout(host.__getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** 取第 index 个导航段节点（segmented root 的第 index 个子节点）。 */
    private SceneNode navSegment(int index) {
        SceneNode segmentedRoot = host.__getNavBar().__getChildren().get(0);
        return segmentedRoot.__getChildren().get(index);
    }

    /** 在指定节点中心合成 CLICK（DOWN+UP 两帧 route + flush）。 */
    private void clickNode(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int x = box.getX() + box.getWidth() / 2;
        int y = box.getY() + box.getHeight() / 2;
        clickAt(x, y);
        doLayout();
    }

    private void clickAt(int x, int y) {
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        host.__getRuntime().route(host.__getRoot(), builder.drainFrame(), 0, 0);
        host.__getRuntime().flush();
    }

    // ==================== 骨架结构 ====================

    @Test
    public void shellHasHeaderNavAndScrollViewport() {
        SceneNode root = host.__getRoot();
        List<SceneNode> children = root.__getChildren();
        Assert.assertEquals("root 三子：header/navBar/scrollContainer", 3, children.size());
        Assert.assertSame("header 为首页", host.__getHeader(), children.get(0));
        Assert.assertSame("navBar 为次子", host.__getNavBar(), children.get(1));
        host.__getNavBar();
        // navBar 内挂载了 segmented root（1 个直接子节点）
        Assert.assertEquals("navBar 挂 1 个 segmented", 1, host.__getNavBar().__getChildren().size());
        // 分段数与注册页数一致
        Assert.assertEquals("分段数 = 页数",
                PlaygroundPageRegistry.defaultPages().size(),
                host.__getNavBar().__getChildren().get(0).__getChildren().size());
        // content 挂在 viewport 下
        Assert.assertSame("content 在 viewport 内", host.__getContent().__getParent(), host.__getViewport());
    }

    @Test
    public void headerShowsTitleAndSubtitle() {
        SceneNode header = host.__getHeader();
        Assert.assertEquals("header 两行文本", 2, header.__getChildren().size());
        Assert.assertEquals("标题文本", "Qz UILib 测试场地", header.__getChildren().get(0).getText());
    }

    // ==================== 初始状态与页面切换 ====================

    @Test
    public void homeIsDefaultPage() {
        Assert.assertEquals("默认页下标 0", 0, host.__getDisplayedPageIndex());
        Assert.assertEquals("默认页 id=home", "home", host.__getDisplayedPageId());
        SceneNode pageRoot = host.__getDisplayedPageRoot();
        Assert.assertNotNull("默认页已挂载", pageRoot);
        Assert.assertSame("页面根挂在 content", pageRoot, host.__getContent().__getChildren().get(0));
    }

    @Test
    public void clickingSegmentsSwitchesPages() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        org.junit.Assume.assumeTrue("至少 3 页才演示中间切换", pages.size() >= 3);

        clickNode(navSegment(1));
        Assert.assertEquals("切到第 2 页", 1, host.__getDisplayedPageIndex());
        Assert.assertEquals(pages.get(1).id(), host.__getDisplayedPageId());
        Assert.assertNotNull(host.__getDisplayedPageRoot());

        int last = pages.size() - 1;
        clickNode(navSegment(last));
        Assert.assertEquals("切到末页", pages.get(last).id(), host.__getDisplayedPageId());

        clickNode(navSegment(0));
        Assert.assertEquals("切回首页", pages.get(0).id(), host.__getDisplayedPageId());
    }

    @Test
    public void switchDisposesOutgoingPageAndKeepsSingleLivePage() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        org.junit.Assume.assumeTrue("至少 2 页才演示切换析构", pages.size() >= 2);

        SceneNode homeRoot = host.__getDisplayedPageRoot();
        clickNode(navSegment(1));
        Assert.assertEquals("切到第 2 页", pages.get(1).id(), host.__getDisplayedPageId());
        Assert.assertNull("旧页根已从树摘除", homeRoot.__getParent());
        Assert.assertEquals("content 仅剩 1 个 live 页面", 1, host.__getContent().__getChildren().size());
        Assert.assertSame("页面根仍挂在 content",
                host.__getDisplayedPageRoot(), host.__getContent().__getChildren().get(0));
    }

    @Test
    public void switchingToSameIndexIsNoOp() {
        SceneNode homeRoot = host.__getDisplayedPageRoot();
        clickNode(navSegment(0));
        Assert.assertEquals("仍在 home", 0, host.__getDisplayedPageIndex());
        Assert.assertSame("页面根实例不变（未重建）", homeRoot, host.__getDisplayedPageRoot());
    }

    /** 滚动几何回归：root 高度受画布约束、viewport 高度解耦于内容、maxScrollY &gt; 0。
     *  （真机「只能看到样式继承、无法滚动」根因：固定兄弟不可先验 → grow 求解器早退） */
    @Test
    public void latexPageScrollsWithinConstrainedViewport() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        int latexIndex = -1;
        for (int i = 0; i < pages.size(); i++) {
            if ("latex".equals(pages.get(i).id())) {
                latexIndex = i;
                break;
            }
        }
        org.junit.Assume.assumeTrue("注册表含 latex 页", latexIndex >= 0);
        clickNode(navSegment(latexIndex));
        doLayout();
        SceneNode viewport = host.__getViewport();
        SceneNode content = host.__getContent();
        AnchorRect vb = SceneGeometry.absoluteBox(viewport, 0, 0);
        AnchorRect cb = SceneGeometry.absoluteBox(content, 0, 0);
        int maxScroll = SceneGeometry.maxScrollY(viewport);
        AnchorRect rb = SceneGeometry.absoluteBox(host.__getRoot(), 0, 0);
        Assert.assertEquals("root 高度应受画布高度约束", CANVAS_HEIGHT, rb.getHeight());
        Assert.assertTrue("viewport 高度应小于内容高（视口被约束而非随内容撑开）: v="
                + vb.getHeight() + " c=" + cb.getHeight(), vb.getHeight() < cb.getHeight());
        Assert.assertTrue("视口应有纵向滚动区间（maxScrollY>0），实测=" + maxScroll,
                maxScroll > 0);
    }

    /** 正向锚：注册表必须含 markdown 页（M3 验收面之一）。既有 latex 锚用 Assume 门控，
     *  漏注册只会静默跳过仍算绿；本锚用硬断言把「注册存在 + 可挂载 + 可滚动」钉死。 */
    @Test
    public void registryContainsMarkdownPageAndMounts() {
        Assert.assertNotNull("注册表含 markdown 页（PlaygroundPageRegistry.lookup）",
                PlaygroundPageRegistry.lookup("markdown"));
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        int markdownIndex = -1;
        for (int i = 0; i < pages.size(); i++) {
            if ("markdown".equals(pages.get(i).id())) {
                Assert.assertTrue("markdown 页 id 唯一", markdownIndex < 0);
                markdownIndex = i;
            }
        }
        Assert.assertTrue("注册表含 id=markdown 的演示页", markdownIndex >= 0);
        clickNode(navSegment(markdownIndex));
        doLayout();
        Assert.assertEquals("可切到 markdown 页", "markdown", host.__getDisplayedPageId());
        SceneNode pageRoot = host.__getDisplayedPageRoot();
        Assert.assertNotNull("markdown 页根已挂载", pageRoot);
        Assert.assertTrue("markdown 页含全部样本卡（实测 " + pageRoot.__getChildren().size() + "）",
                pageRoot.__getChildren().size() >= 8);
        Assert.assertTrue("markdown 页应有纵向滚动区间，实测=" + SceneGeometry.maxScrollY(host.__getViewport()),
                SceneGeometry.maxScrollY(host.__getViewport()) > 0);
    }

    @Test
    public void everyRegistryPageMountsInHost() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        for (int i = 0; i < pages.size(); i++) {
            clickNode(navSegment(i));
            Assert.assertEquals("页 id 与注册表一致: " + pages.get(i).id(), i, host.__getDisplayedPageIndex());
            Assert.assertEquals(pages.get(i).id(), host.__getDisplayedPageId());
            Assert.assertNotNull("页面根非 null: " + pages.get(i).id(), host.__getDisplayedPageRoot());
        }
    }

    // ==================== 主动刷新（F5 语义） ====================

    /**
     * 刷新是「新 occurrence」：丢弃 live 页实例、重执行页工厂，产出全新根节点，
     * 且仍只有一个 live 页根占据同一独占槽位。
     */
    @Test
    public void refreshPageRebuildsNewInstanceInSameSlot() {
        SceneNode before = host.__getDisplayedPageRoot();
        Assert.assertNotNull("前置：当前页已挂载", before);

        SceneNode after = host.refreshPage();

        Assert.assertNotNull("刷新后仍有 live 页", after);
        Assert.assertNotSame("刷新必须产出新实例（不得复用旧树）", before, after);
        Assert.assertNull("旧页根已从树摘除", before.__getParent());
        Assert.assertEquals("content 仍只有一个 live 页根",
                1, host.__getContent().__getChildren().size());
        Assert.assertSame("新页根占据原槽位", after, host.__getContent().__getChildren().get(0));
        Assert.assertEquals("刷新不改变当前页下标", 0, host.__getDisplayedPageIndex());
        Assert.assertEquals("刷新不改变当前页 id", "home", host.__getDisplayedPageId());
    }

    /**
     * 位置保持来自「独占内容容器」，不来自任何占位/锚点节点：
     * 刷新前后 viewport 的子节点集合与几何必须一致。
     */
    @Test
    public void refreshPageKeepsSlotGeometryWithoutPlaceholder() {
        SceneNode viewport = host.__getViewport();
        int childrenBefore = viewport.__getChildren().size();
        int gapBefore = viewport.getGap();
        AnchorRect boxBefore = SceneGeometry.absoluteBox(viewport, 0, 0);

        host.refreshPage();
        doLayout();

        AnchorRect boxAfter = SceneGeometry.absoluteBox(viewport, 0, 0);
        Assert.assertEquals("刷新不得增删 viewport 子节点", childrenBefore, viewport.__getChildren().size());
        Assert.assertEquals("刷新不得改变视口 gap", gapBefore, viewport.getGap());
        Assert.assertEquals("刷新不得改变视口宽", boxBefore.getWidth(), boxAfter.getWidth());
        Assert.assertEquals("刷新不得改变视口高", boxBefore.getHeight(), boxAfter.getHeight());
        Assert.assertEquals("content 仍是视口唯一子节点", 1, viewport.__getChildren().size());
    }

    /**
     * 重复刷新不得累积节点：每次刷新都必须是「dispose 旧的 + mount 新的」。
     */
    @Test
    public void repeatedRefreshDoesNotAccumulateNodes() {
        for (int i = 0; i < 6; i++) {
            host.refreshPage();
        }
        Assert.assertEquals("重复刷新不累积 content 子节点",
                1, host.__getContent().__getChildren().size());
        Assert.assertEquals("重复刷新不累积 viewport 子节点",
                1, host.__getViewport().__getChildren().size());
    }

    /**
     * 刷新保留阅读位置（对齐浏览器 F5）；导航切页仍归零滚动——两条路径不得混同。
     */
    @Test
    public void refreshPageKeepsScrollOffsetWhileNavigationResetsIt() {
        org.junit.Assume.assumeTrue("至少 2 页才演示导航对照", PlaygroundPageRegistry.defaultPages().size() >= 2);

        host.__getViewport().setScrollOffsetY(37);
        int kept = host.__getViewport().getScrollOffsetY();

        host.refreshPage();
        Assert.assertEquals("刷新不主动重置滚动位置", kept, host.__getViewport().getScrollOffsetY());

        host.__getActivePageSignal().set(Integer.valueOf(1));
        host.__getRuntime().flush();
        Assert.assertEquals("导航切页仍把滚动归零", 0, host.__getViewport().getScrollOffsetY());
    }

    /**
     * 刷新与同页导航是两条路径：同页导航仍是 no-op（不重建），只有显式刷新才重建。
     */
    @Test
    public void sameIndexNavigationIsNoOpWhileRefreshRebuilds() {
        SceneNode before = host.__getDisplayedPageRoot();

        host.__getActivePageSignal().set(Integer.valueOf(0));
        host.__getRuntime().flush();
        Assert.assertSame("同页导航不得重建", before, host.__getDisplayedPageRoot());

        SceneNode after = host.refreshPage();
        Assert.assertNotSame("显式刷新才重建", before, after);
    }

    // ==================== G16/外壳主题化 ====================

    /** 角色配方取值：断言引用配方而不是硬编码色号，主题集中调参时本类自动跟随。 */
    private static SceneSurfaceStyle role(SceneTheme theme, SceneTheme.Role role) {
        return theme.surface(role);
    }

    /** 表面六属性 = 角色配方（idle 档）：底色/缘色/边框宽/圆角/实体高度/滤镜。 */
    private static void assertSurfaceRecipe(String where, SceneSurfaceStyle style, SceneNode node) {
        Assert.assertEquals(where + " 底色 = 角色 idle tint",
                style.getIdle().getTint(), node.getBackgroundColor());
        Assert.assertEquals(where + " 边框色 = 角色 idle edge",
                style.getIdle().getEdge(), node.getBorderColor());
        Assert.assertEquals(where + " 边框宽 = 角色配方", style.getBorderWidth(), node.getBorderWidth());
        Assert.assertEquals(where + " 圆角 = 角色配方", style.getCornerRadius(), node.getCornerRadius());
        Assert.assertEquals(where + " 实体高度 = 角色 idle elevation",
                style.getIdle().getElevation(), node.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull(where + " 配方必须带滤镜", style.getBackdrop());
        Assert.assertNotNull(where + " 滤镜必须已写入节点", node.getBackdrop());
        Assert.assertEquals(where + " 滤镜材质 = 角色配方",
                style.getBackdrop().getEffect().getMaterial(), node.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals(where + " 滤镜模糊半径 = 角色配方",
                style.getBackdrop().getBlurRadius(), node.getBackdrop().getBlurRadius());
    }

    /**
     * 默认工厂路径：外壳 root=PANEL、header=TOOLBAR、viewport=GROUP 的六属性等于对应角色配方；
     * 顶栏文字取主题语义色；导航底座（SceneSegmented 根，G09 只读复用）仍是 TOOLBAR。
     */
    @Test
    public void shellSurfacesConsumeRoleRecipesByDefault() {
        host.__getRuntime().flush();

        assertSurfaceRecipe("root", role(DARK, SceneTheme.Role.PANEL), host.__getRoot());
        assertSurfaceRecipe("header", role(DARK, SceneTheme.Role.TOOLBAR), host.__getHeader());
        assertSurfaceRecipe("viewport", role(DARK, SceneTheme.Role.GROUP), host.__getViewport());

        // 顶栏文字：主题前景派生（旧静态 TEXT/MUTED 取色已删）。
        SceneNode title = host.__getHeader().__getChildren().get(0);
        SceneNode subtitle = host.__getHeader().__getChildren().get(1);
        Assert.assertEquals("顶栏主标题取主题正文色", DARK.foreground(), title.getTextColor());
        Assert.assertEquals("顶栏副标题取主题次要色", DARK.mutedForeground(), subtitle.getTextColor());

        // 导航底座：SceneSegmented 根自行绑定 TOOLBAR（G09），宿主 navBar 不再叠第二层玻璃。
        SceneNode navBase = host.__getNavBar().__getChildren().get(0);
        Assert.assertEquals("导航底座底色 = TOOLBAR 配方",
                role(DARK, SceneTheme.Role.TOOLBAR).getIdle().getTint(), navBase.getBackgroundColor());
        Assert.assertEquals("导航底座圆角 = TOOLBAR 配方",
                role(DARK, SceneTheme.Role.TOOLBAR).getCornerRadius(), navBase.getCornerRadius());
        Assert.assertEquals("navBar 自身不装表面（不叠第二层玻璃）", 0, host.__getNavBar().getBackgroundColor());

        // 页面公共构件：真实装配路径（页面工厂 → PlaygroundKit.card）同样取 GROUP 配方。
        assertSurfaceRecipe("页面卡片（PlaygroundKit.card）", role(DARK, SceneTheme.Role.GROUP),
                host.__getDisplayedPageRoot().__getChildren().get(0));
    }

    /**
     * 主题切换：外壳三块表面随新主题更新，当前页根与导航选中不丢、节点身份不变、
     * effect 数不增长。
     */
    @Test
    public void themeSwitchUpdatesShellWithoutRebuildOrLosingPageAndNav() {
        Assert.assertNotEquals("测试前提：深浅 PANEL 配方必须不同",
                role(DARK, SceneTheme.Role.PANEL), role(LIGHT, SceneTheme.Role.PANEL));
        Assert.assertNotEquals("测试前提：深浅 TOOLBAR 配方必须不同",
                role(DARK, SceneTheme.Role.TOOLBAR), role(LIGHT, SceneTheme.Role.TOOLBAR));
        Assert.assertNotEquals("测试前提：深浅 GROUP 配方必须不同",
                role(DARK, SceneTheme.Role.GROUP), role(LIGHT, SceneTheme.Role.GROUP));
        Assert.assertNotEquals("测试前提：深浅正文色必须不同", DARK.foreground(), LIGHT.foreground());

        host.__getRuntime().flush();
        // 先离开首页：验证主题切换不丢「当前页」与「导航选中」。
        host.__getActivePageSignal().set(Integer.valueOf(1));
        host.__getRuntime().flush();

        SceneNode root = host.__getRoot();
        SceneNode header = host.__getHeader();
        SceneNode viewport = host.__getViewport();
        SceneNode navBase = host.__getNavBar().__getChildren().get(0);
        SceneNode pageRoot = host.__getDisplayedPageRoot();
        SceneNode selectedSegment = navSegment(1);
        SceneNode idleSegment = navSegment(0);
        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();

        host.__getThemeSignal().set(LIGHT);
        host.__getRuntime().flush();
        // 颜色/浮点属性经 Motion 过渡：推进到配方目标值再断言（滤镜材质即刻重派生）。
        host.__getRuntime().__finishMotionForTest();

        Assert.assertSame("主题切换不重建 root", root, host.__getRoot());
        Assert.assertSame("主题切换不重建 header", header, host.__getHeader());
        Assert.assertSame("主题切换不重建 viewport", viewport, host.__getViewport());
        assertSurfaceRecipe("切换后 root", role(LIGHT, SceneTheme.Role.PANEL), root);
        assertSurfaceRecipe("切换后 header", role(LIGHT, SceneTheme.Role.TOOLBAR), header);
        assertSurfaceRecipe("切换后 viewport", role(LIGHT, SceneTheme.Role.GROUP), viewport);
        Assert.assertEquals("顶栏文字随主题更新", LIGHT.foreground(),
                host.__getHeader().__getChildren().get(0).getTextColor());
        Assert.assertEquals("顶栏副标题随主题更新", LIGHT.mutedForeground(),
                host.__getHeader().__getChildren().get(1).getTextColor());

        Assert.assertSame("主题切换不重建当前页", pageRoot, host.__getDisplayedPageRoot());
        Assert.assertEquals("导航选中不丢", Integer.valueOf(1), host.__getActivePageSignal().get());
        Assert.assertSame("主题切换不重建导航底座", navBase, host.__getNavBar().__getChildren().get(0));
        Assert.assertEquals("导航底座随主题更新",
                role(LIGHT, SceneTheme.Role.TOOLBAR).getIdle().getTint(), navBase.getBackgroundColor());
        Assert.assertEquals("未选中段随主题更新",
                role(LIGHT, SceneTheme.Role.INDICATOR).getIdle().getTint(), idleSegment.getBackgroundColor());
        Assert.assertNotEquals("选中段与未选中段仍必须可区分（选中不丢）",
                idleSegment.getBackgroundColor(), selectedSegment.getBackgroundColor());
        // 页面公共构件在真实装配路径上随主题更新（页面经 Owner 作用域继承 runtime 默认主题）。
        SceneNode pageCard = pageRoot.__getChildren().get(0);
        Assert.assertSame("主题切换不重建页面卡片", pageCard, pageRoot.__getChildren().get(0));
        Assert.assertEquals("页面卡片（PlaygroundKit.card）随主题更新",
                role(LIGHT, SceneTheme.Role.GROUP).getIdle().getTint(), pageCard.getBackgroundColor());
        Assert.assertEquals("主题切换不新增订阅", effectsBeforeSwitch,
                ReactiveTestProbe.registeredEffectCount());
    }

    /**
     * 局部主题：{@link SceneThemes#withTheme} 作用域内构建的公共构件（{@code PlaygroundKit.card()}）
     * 跟随局部主题更新，且不外泄到外壳（外壳取 runtime 默认主题）。
     */
    @Test
    public void withThemeScopedKitCardFollowsLocalThemeWithoutLeakingToShell() {
        host.__getRuntime().flush();
        Signal<SceneTheme> localTheme = Signal.create(DARK);
        SceneNode probeHost = new SceneNode();
        MountHandle probe = host.__getRuntime().mount(probeHost, () -> {
            SceneNode box = SceneNode.column();
            SceneThemes.withTheme(localTheme, () -> box.appendChild(PlaygroundKit.card()));
            return box;
        });
        host.__getRuntime().flush();

        SceneNode card = probe.getRoot().__getChildren().get(0);
        assertSurfaceRecipe("局部主题下的卡片", role(DARK, SceneTheme.Role.GROUP), card);

        localTheme.set(LIGHT);
        host.__getRuntime().flush();
        host.__getRuntime().__finishMotionForTest();

        Assert.assertSame("局部主题切换不重建卡片", card, probe.getRoot().__getChildren().get(0));
        assertSurfaceRecipe("局部主题切换后的卡片", role(LIGHT, SceneTheme.Role.GROUP), card);
        Assert.assertEquals("局部主题不外泄到外壳（外壳取 runtime 默认主题）",
                role(DARK, SceneTheme.Role.PANEL).getIdle().getTint(), host.__getRoot().getBackgroundColor());

        probe.dispose();
        host.__getRuntime().flush();
        Assert.assertTrue("探针卸载后挂载点无残留节点", probeHost.__getChildren().isEmpty());
        Assert.assertEquals("页面单槽不受探针影响", 1, host.__getContent().__getChildren().size());
    }

    /**
     * 卸载即回收：宿主销毁后外壳/页面绑定全部退订，effect 数回到构造前基线。
     */
    @Test
    public void disposeReclaimsShellAndPageBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        TestPlaygroundHost probe = new TestPlaygroundHost(null);
        try {
            probe.__getRuntime().flush();
            Assert.assertTrue("外壳与页面绑定应注册响应式工作，baseline=" + baseline
                            + ", mounted=" + ReactiveTestProbe.registeredEffectCount(),
                    ReactiveTestProbe.registeredEffectCount() > baseline);
        } finally {
            probe.dispose();
        }
        Assert.assertEquals("卸载后外壳/页面绑定全部回收",
                baseline, ReactiveTestProbe.registeredEffectCount());
    }
}