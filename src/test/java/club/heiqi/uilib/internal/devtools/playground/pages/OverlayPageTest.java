package club.heiqi.uilib.internal.devtools.playground.pages;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPageRegistry;
import club.heiqi.uilib.internal.devtools.playground.TestPlaygroundHost;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link OverlayPage} 默认外观迁移（G16/OverlayPage）测试。
 *
 * <p>证据面：① 真实宿主装配路径（{@link TestPlaygroundHost} 构造 → 点导航段进入本页）下，
 * 右键演示区容器六项表面属性等于来源主题 GROUP 角色配方，布局属性不变；② 页面节标题/说明文字/
 * 动作日志取主题前景；③ 主题切换后本页外观更新、节点身份不变、演示数据（动作日志）不变、
 * 订阅不增长；④ 右键打开上下文菜单与菜单项回调等演示动作不变；⑤ 页面卸载回收全部外观绑定。</p>
 *
 * <p>本测试位于 {@code pages} 子包，宿主的状态探针是包级可见，故用 {@link ProbeHost} 暴露
 * 基类 protected 的 {@code runtime}/{@code getRoot()}；导航仍走真实点击路径，不直接调页工厂。</p>
 */
public class OverlayPageTest {

    /** 画布宽：9 页导航段横排需 ≥833px（与既有宿主测试同口径）。 */
    private static final int CANVAS_WIDTH = 1000;
    /** 画布高：足够容纳本页三张卡片，右键命中不依赖滚动（真实视口约束由宿主测试覆盖）。 */
    private static final int CANVAS_HEIGHT = 2600;
    /** 浮点比较容差。 */
    private static final float EPSILON = 0.0001F;

    /** 库默认主题（宿主 runtime 默认档）：默认外观断言的唯一来源。 */
    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    /** 浅色档：主题切换断言用（与深色档各角色配方/语义色均不同）。 */
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();

    private static final String DIALOG_TITLE = "Dialog（模态对话框）";
    private static final String TOAST_TITLE = "Toast（非模态通知，底部堆叠 + 类型化 + 动画）";
    private static final String MENU_TITLE = "ContextMenu（右键上下文菜单）";
    private static final String REGION_HINT = "在此区域点击右键打开上下文菜单";
    private static final String LOG_PREFIX = "动作日志：";

    private ProbeHost host;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new ProbeHost();
        layoutHost();
        // 真实导航路径进入本页（点击导航段），不直接调页工厂。
        clickNode(navSegment(overlayPageIndex()));
        layoutHost();
        host.runtime().flush();
        host.runtime().__finishMotionForTest();
        Assert.assertNotNull("导航后本页已挂载", cardWithTitle(pageRoot(), DIALOG_TITLE));
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== ① 真实宿主装配路径：右键演示区容器 ====================

    /**
     * 默认路径：右键演示区容器的 background/border/borderWidth/cornerRadius/surfaceElevation/
     * backdrop 六项全部等于来源主题 GROUP 角色配方（唯一写入者 = 表面绑定器）；
     * 旧的静态写入者（{@code PlaygroundKit.PANEL_BG/BORDER}、{@code RADIUS_MD}）不得残留。
     */
    @Test
    public void rightClickRegionConsumesGroupRoleRecipeInHostAssembly() {
        SceneNode menuCard = cardWithTitle(pageRoot(), MENU_TITLE);
        SceneNode region = rightClickRegion(menuCard);

        assertSurfaceRecipe("右键演示区容器", DARK.surface(SceneTheme.Role.GROUP), region);

        Assert.assertNotEquals("不得回落到旧静态面板底色", PlaygroundKit.PANEL_BG, region.getBackgroundColor());
        Assert.assertNotEquals("不得回落到旧静态边框色", PlaygroundKit.BORDER, region.getBorderColor());

        // 布局仍归本页：主题不接管布局（内边距/高度/交叉轴对齐/宽度填充）。
        Assert.assertEquals("内边距左保持 10", 10, region.getPaddingLeft());
        Assert.assertEquals("内边距上保持 10", 10, region.getPaddingTop());
        Assert.assertEquals("高度保持 48", 48, region.getPreferredHeight());
        Assert.assertTrue("宽度仍填满父轴", region.isFillParentWidth());
        Assert.assertEquals("交叉轴仍居中", CrossAxisAlign.CENTER, region.getCrossAxisAlign());

        // 与所在公共卡片同角色：页面内不出现第二套材质色板。
        assertSurfaceRecipe("右键演示区所在卡片", DARK.surface(SceneTheme.Role.GROUP), menuCard);
    }

    // ==================== ② 页面文字/提示取主题前景 ====================

    /** 节标题取主题正文前景、说明文字与动作日志取主题次要前景；字号保持既有口径。 */
    @Test
    public void pageTitlesHintsAndLogConsumeThemeForeground() {
        SceneNode page = pageRoot();
        assertCardTitleAndHint(page, DIALOG_TITLE);
        assertCardTitleAndHint(page, TOAST_TITLE);

        SceneNode menuCard = cardWithTitle(page, MENU_TITLE);
        SceneNode menuTitle = menuCard.__getChildren().get(0);
        Assert.assertEquals("ContextMenu 标题取主题正文前景", DARK.foreground(), menuTitle.getTextColor());
        Assert.assertEquals("ContextMenu 标题字号保持 16", 16, menuTitle.getFontSize());

        SceneNode regionHint = rightClickRegion(menuCard).__getChildren().get(0);
        Assert.assertEquals("演示区说明文字取主题次要前景", DARK.mutedForeground(), regionHint.getTextColor());
        Assert.assertEquals("演示区说明文字字号保持 12", 12, regionHint.getFontSize());

        SceneNode log = logNode(menuCard);
        Assert.assertEquals("动作日志取主题次要前景", DARK.mutedForeground(), log.getTextColor());
        Assert.assertEquals("动作日志字号保持 12", 12, log.getFontSize());
        Assert.assertEquals("动作日志初始文案不变", LOG_PREFIX + "（暂无，右键面板试试）", log.getText());
    }

    /**
     * 源码守卫：本页不得再自带静态取色写入者（结构用例只证明「现在能用」，证明不了「没退回手搓」）。
     *
     * <p>本页无刻意色差样本：诊断内容是 Dialog/Toast/ContextMenu 三类浮层本体，其外观由各自
     * 已主题化的控件负责；页面自身文字全部是语义前景，因此不存在需要保留的显式颜色样本。</p>
     */
    @Test
    public void pageSourceHasNoStaticPaletteWriters() throws Exception {
        Path path = Paths.get(
                "src/main/java/club/heiqi/uilib/internal/devtools/playground/pages/OverlayPage.java");
        String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        StringBuilder code = new StringBuilder();
        for (String line : raw.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            code.append(trimmed).append('\n');
        }
        String src = code.toString();

        Assert.assertTrue("演示区容器表面必须走表面绑定器（唯一外观写入者）",
                src.contains("SceneSurfaceBinder.bind("));
        Assert.assertTrue("演示区容器必须取 GROUP 角色配方", src.contains("SceneTheme.Role.GROUP"));
        Assert.assertTrue("标题必须取主题正文前景", src.contains("SceneThemes.foreground("));
        Assert.assertTrue("说明/日志必须取主题次要前景", src.contains("SceneThemes.mutedForeground("));
        Assert.assertFalse("不得残留 PlaygroundKit 静态色常量", src.contains("PlaygroundKit.MUTED")
                || src.contains("PlaygroundKit.TEXT")
                || src.contains("PlaygroundKit.BORDER")
                || src.contains("PlaygroundKit.PANEL_BG")
                || src.contains("PlaygroundKit.ROOT_BG"));
        Assert.assertFalse("不得残留静态边框/圆角/底色写入者", src.contains("setBorderWidth(")
                || src.contains("setBorderColor(")
                || src.contains("setCornerRadius(")
                || src.contains("setBackgroundColor("));
        Assert.assertFalse("不得直接取 SceneChromeTokens 颜色", src.contains("SceneChromeTokens"));
        Assert.assertFalse("本页无刻意色差样本，不得自带 0xFF 色值", src.contains("0xFF"));
    }

    // ==================== ③ 主题切换 ====================

    /**
     * 主题切换：右键演示区容器六项随新主题更新、页面节点身份不变、演示动作写入的日志数据不变、
     * 订阅不增长；标题/说明/日志前景随主题更新。
     */
    @Test
    public void themeSwitchUpdatesPageWithoutRebuildOrLosingDemoState() {
        Assert.assertNotEquals("测试前提：深浅 GROUP 配方必须不同",
                DARK.surface(SceneTheme.Role.GROUP), LIGHT.surface(SceneTheme.Role.GROUP));
        Assert.assertNotEquals("测试前提：深浅正文色必须不同", DARK.foreground(), LIGHT.foreground());
        Assert.assertNotEquals("测试前提：深浅次要前景必须不同",
                DARK.mutedForeground(), LIGHT.mutedForeground());

        Signal<SceneTheme> pageTheme = Signal.create(DARK);
        SceneNode container = new SceneNode();
        MountHandle probe = host.runtime().mount(container, () -> {
            SceneNode box = SceneNode.column();
            SceneThemes.withTheme(pageTheme, () -> box.appendChild(new OverlayPage().build(host.runtime()).get()));
            return box;
        });
        host.runtime().flush();
        layoutProbe(probe.getRoot());

        SceneNode page = probe.getRoot().__getChildren().get(0);
        SceneNode menuCard = cardWithTitle(page, MENU_TITLE);
        SceneNode region = rightClickRegion(menuCard);
        SceneNode log = logNode(menuCard);
        assertSurfaceRecipe("默认档右键演示区容器", DARK.surface(SceneTheme.Role.GROUP), region);

        // 演示动作：右键打开上下文菜单 → 键盘激活首项「复制当前时间」→ 写动作日志。
        Assert.assertEquals("初始无浮层（对话框未打开）", 0, host.runtime().getOverlayHost().size());
        rightClickNode(probe.getRoot(), region);
        Assert.assertEquals("右键打开上下文菜单（演示动作未变）", 1, host.runtime().getOverlayHost().size());
        routeKey(probe.getRoot(), SceneKey.ARROW_DOWN);
        routeKey(probe.getRoot(), SceneKey.ENTER);
        Assert.assertEquals("选择后菜单关闭", 0, host.runtime().getOverlayHost().size());
        String logText = log.getText();
        Assert.assertTrue("演示日志已写入：" + logText, logText.startsWith(LOG_PREFIX + "已复制当前时间："));

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(LIGHT);
        host.runtime().flush();
        host.runtime().__finishMotionForTest();

        Assert.assertSame("主题切换不重建页面", page, probe.getRoot().__getChildren().get(0));
        Assert.assertSame("主题切换不重建右键演示区容器", region, rightClickRegion(menuCard));
        Assert.assertSame("主题切换不重建动作日志节点", log, logNode(menuCard));
        assertSurfaceRecipe("切换后右键演示区容器", LIGHT.surface(SceneTheme.Role.GROUP), region);
        Assert.assertEquals("标题随主题更新", LIGHT.foreground(), menuCard.__getChildren().get(0).getTextColor());
        Assert.assertEquals("说明文字随主题更新", LIGHT.mutedForeground(),
                rightClickRegion(menuCard).__getChildren().get(0).getTextColor());
        Assert.assertEquals("动作日志前景随主题更新", LIGHT.mutedForeground(), log.getTextColor());
        Assert.assertEquals("演示数据（动作日志）不因主题切换改变", logText, log.getText());
        Assert.assertEquals("主题切换不重开浮层", 0, host.runtime().getOverlayHost().size());
        Assert.assertEquals("主题切换不新增订阅", effectsBeforeSwitch, ReactiveTestProbe.registeredEffectCount());

        probe.dispose();
        host.runtime().flush();
    }

    // ==================== ④ 无滤镜替代档 ====================

    /**
     * 关闭滤镜档：切到 {@link SceneTheme#withoutBackdrop()} 后演示区不再写 backdrop，tint 取配方
     * 不透明替代底色（可读），圆角/边框等几何保持；节点身份不变。
     */
    @Test
    public void withoutBackdropThemeDropsRegionFilterAndKeepsOpaqueTint() {
        Signal<SceneTheme> pageTheme = Signal.create(DARK);
        SceneNode container = new SceneNode();
        MountHandle probe = host.runtime().mount(container, () -> {
            SceneNode box = SceneNode.column();
            SceneThemes.withTheme(pageTheme, () -> box.appendChild(new OverlayPage().build(host.runtime()).get()));
            return box;
        });
        host.runtime().flush();
        SceneNode region = rightClickRegion(cardWithTitle(probe.getRoot().__getChildren().get(0), MENU_TITLE));
        Assert.assertNotNull("默认档应装滤镜", region.getBackdrop());

        pageTheme.set(DARK.withoutBackdrop());
        host.runtime().flush();
        host.runtime().__finishMotionForTest();

        SceneSurfaceStyle noFilter = DARK.withoutBackdrop().surface(SceneTheme.Role.GROUP);
        Assert.assertNull("关闭滤镜档不得再写 backdrop", region.getBackdrop());
        Assert.assertEquals("关闭滤镜档底色 = 配方不透明替代", noFilter.getIdle().getTint(),
                region.getBackgroundColor());
        Assert.assertEquals("关闭滤镜档 tint 必须不透明可读", 0xFF,
                (region.getBackgroundColor() >>> 24) & 0xFF);
        Assert.assertEquals("关闭滤镜档边框色 = 配方缘色", noFilter.getIdle().getEdge(), region.getBorderColor());
        Assert.assertEquals("关闭滤镜档圆角保持配方", noFilter.getCornerRadius(), region.getCornerRadius());
        Assert.assertSame("关闭滤镜档不重建演示区容器", region,
                rightClickRegion(cardWithTitle(probe.getRoot().__getChildren().get(0), MENU_TITLE)));

        probe.dispose();
        host.runtime().flush();
    }

    // ==================== ⑤ 演示动作保持 ====================

    /** 右键打开菜单、ESC 关闭、菜单项回调写日志：本页交互合同不因换肤改变。 */
    @Test
    public void rightClickMenuAndItemActivationStillWork() {
        SceneNode menuCard = cardWithTitle(pageRoot(), MENU_TITLE);
        SceneNode region = rightClickRegion(menuCard);
        SceneNode log = logNode(menuCard);

        rightClickNode(host.root(), region);
        Assert.assertEquals("右键打开上下文菜单", 1, host.runtime().getOverlayHost().size());
        SceneNode menu = host.runtime().getOverlayHost().bottomFirst().get(0).getRoot();
        Assert.assertEquals("菜单项清单不变（5 项，含分隔线）", 5, menu.__getChildren().size());

        routeKey(host.root(), SceneKey.ESCAPE);
        Assert.assertEquals("ESC 关闭菜单", 0, host.runtime().getOverlayHost().size());
        Assert.assertEquals("ESC 不改日志", LOG_PREFIX + "（暂无，右键面板试试）", log.getText());

        rightClickNode(host.root(), region);
        routeKey(host.root(), SceneKey.ARROW_DOWN);
        routeKey(host.root(), SceneKey.ENTER);
        Assert.assertEquals("菜单项回调后菜单关闭", 0, host.runtime().getOverlayHost().size());
        Assert.assertTrue("菜单项回调写入动作日志：" + log.getText(),
                log.getText().startsWith(LOG_PREFIX + "已复制当前时间："));
    }

    // ==================== ⑥ 卸载回收 ====================

    /** 页面卸载后外观/前景/日志绑定全部回收，effect 数回到挂载前基线。 */
    @Test
    public void unmountReclaimsPageAppearanceBindings() {
        host.runtime().flush();
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(DARK);
        SceneNode container = new SceneNode();
        MountHandle probe = host.runtime().mount(container, () -> {
            SceneNode box = SceneNode.column();
            SceneThemes.withTheme(pageTheme, () -> box.appendChild(new OverlayPage().build(host.runtime()).get()));
            return box;
        });
        host.runtime().flush();
        Assert.assertTrue("挂载后应注册响应式工作", ReactiveTestProbe.registeredEffectCount() > baseline);

        probe.dispose();
        host.runtime().flush();
        Assert.assertTrue("探针挂载点无残留节点", container.__getChildren().isEmpty());
        Assert.assertEquals("卸载后本页外观绑定全部回收",
                baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 辅助：宿主/导航/命中 ====================

    /**
     * 测试探针宿主：暴露基类 protected 的 runtime 与根节点。
     *
     * <p>本测试与宿主不同包（{@code ...playground.pages} vs {@code ...playground}），
     * {@code TestPlaygroundHost} 的状态探针是包级可见，故用子类桥接；页工厂与主题解析仍走
     * 真实宿主装配路径。</p>
     */
    private static final class ProbeHost extends TestPlaygroundHost {

        ProbeHost() {
            super(null);
        }

        SceneRuntime runtime() {
            return runtime;
        }

        SceneNode root() {
            return getRoot();
        }
    }

    private static int overlayPageIndex() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        for (int i = 0; i < pages.size(); i++) {
            if ("overlay".equals(pages.get(i).id())) {
                return i;
            }
        }
        throw new AssertionError("注册表缺少 id=overlay 的演示页");
    }

    private void layoutHost() {
        host.getLayoutEngine().layout(host.root(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    private void layoutProbe(SceneNode probeRoot) {
        host.getLayoutEngine().layout(probeRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** 导航段节点：navBar → segmented root → 第 index 段（与宿主结构同口径）。 */
    private SceneNode navSegment(int index) {
        SceneNode navBar = host.root().__getChildren().get(1);
        SceneNode segmented = navBar.__getChildren().get(0);
        return segmented.__getChildren().get(index);
    }

    /** 当前 live 页根：root → scrollContainer → viewport → content → pageRoot。 */
    private SceneNode pageRoot() {
        SceneNode scrollContainer = host.root().__getChildren().get(2);
        SceneNode viewport = scrollContainer.__getChildren().get(0);
        SceneNode content = viewport.__getChildren().get(0);
        Assert.assertEquals("单槽：content 仅一个 live 页", 1, content.__getChildren().size());
        return content.__getChildren().get(0);
    }

    private void clickNode(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        clickAt(host.root(), box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2);
    }

    private void clickAt(SceneNode routeRoot, int x, int y) {
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        host.runtime().route(routeRoot, builder.drainFrame(), 0, 0);
        host.runtime().flush();
        layoutHost();
    }

    /** 右键按下+抬起（页面按 POINTER_DOWN 打开菜单；抬起清按压态，避免表面停在 PRESSED 档）。 */
    private void rightClickNode(SceneNode routeRoot, SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int x = box.getX() + box.getWidth() / 2;
        int y = box.getY() + box.getHeight() / 2;
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.RIGHT,
                0, 0, 0, false, false, false, false, 2000L));
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.RIGHT,
                0, 0, 0, false, false, false, false, 2001L));
        host.runtime().route(routeRoot, builder.drainFrame(), 0, 0);
        host.runtime().flush();
    }

    private void routeKey(SceneNode routeRoot, SceneKey key) {
        InputFrameBuilder builder = new InputFrameBuilder(0, 0);
        builder.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED, false, false, false, false,
                0, 0, 3000L));
        host.runtime().route(routeRoot, builder.drainFrame(), 0, 0);
        host.runtime().flush();
    }

    // ==================== 辅助：节点定位/断言 ====================

    private static SceneNode cardWithTitle(SceneNode pageRoot, String title) {
        for (SceneNode card : pageRoot.__getChildren()) {
            List<SceneNode> children = card.__getChildren();
            if (!children.isEmpty() && title.equals(children.get(0).getText())) {
                return card;
            }
        }
        Assert.fail("页面缺少标题为「" + title + "」的卡片");
        return null;
    }

    private static SceneNode rightClickRegion(SceneNode menuCard) {
        for (SceneNode child : menuCard.__getChildren()) {
            List<SceneNode> children = child.__getChildren();
            if (!children.isEmpty() && REGION_HINT.equals(children.get(0).getText())) {
                return child;
            }
        }
        Assert.fail("右键演示区容器缺失");
        return null;
    }

    private static SceneNode logNode(SceneNode menuCard) {
        for (SceneNode child : menuCard.__getChildren()) {
            String text = child.getText();
            if (text != null && text.startsWith(LOG_PREFIX)) {
                return child;
            }
        }
        Assert.fail("动作日志节点缺失");
        return null;
    }

    private void assertCardTitleAndHint(SceneNode pageRoot, String cardTitle) {
        List<SceneNode> children = cardWithTitle(pageRoot, cardTitle).__getChildren();
        SceneNode title = children.get(0);
        SceneNode hint = children.get(children.size() - 1);
        Assert.assertEquals(cardTitle + " 标题取主题正文前景", DARK.foreground(), title.getTextColor());
        Assert.assertEquals(cardTitle + " 标题字号保持 16", 16, title.getFontSize());
        Assert.assertEquals(cardTitle + " 说明文字取主题次要前景", DARK.mutedForeground(), hint.getTextColor());
        Assert.assertEquals(cardTitle + " 说明文字字号保持 12", 12, hint.getFontSize());
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
}
