package club.heiqi.uilib.ui.scene.form;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link FormPageShell} 单元测试。
 *
 * <p>守护 {@code build} 的 {@code attachScroll} 分支契约：
 * attachScroll=false 时不建 scrollSignal / scrollbar（ConfigScreen 复用此分支自建 per-section
 * scroll），attachScroll=true 时挂滚动受控源 + 可视滚动条。并验证 root/titleBar/viewport/
 * scrollContainer 骨架结构与父子关系。</p>
 *
 * <p>G11/PageShell 追加两条外观路径的证据：默认路径（不传 {@code FormTheme}）的 root/viewport
 * 消费来源主题 PANEL 配方、标题取主题前景，主题切换只重派生（保留滚动偏移与节点身份）；
 * 显式 {@code FormTheme} 路径完全覆盖主题且不订阅主题；卸载回收全部外观绑定。</p>
 *
 * <p>分层：build 内部经 {@link SceneRuntime} 注册 scroll bind/handler 与 scrollbar，触 runtime
 * 子系统，属 L3 集成范畴。照 control 包测试范式（同为 runtime 消费方）直接 new SceneRuntime，
 * 放 form 同包（守分层约定：其余子包按各自子系统归属，白盒测试留对应包）。build 不跑 layout/flush，
 * 仅做节点装配与 effect 注册，无需真机字体度量。</p>
 */
public class FormPageShellTest {

    /** 场景运行时（build 消费方），每用例独立 new，避免跨用例污染。 */
    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer(8, 16));
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== attachScroll=false 分支（ConfigScreen 复用） ====================

    /** attachScroll=false：不创建滚动受控源，scrollSignal 为 null。 */
    @Test
    public void attachScrollFalseYieldsNullScrollSignal() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", false, FormTheme.defaultDark());
        Assert.assertNull("attachScroll=false 时 scrollSignal 应为 null", parts.scrollSignal());
    }

    /** attachScroll=false：不创建可视滚动条，scrollbarColumn 为 null。 */
    @Test
    public void attachScrollFalseYieldsNullScrollbarColumn() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", false, FormTheme.defaultDark());
        Assert.assertNull("attachScroll=false 时 scrollbarColumn 应为 null", parts.scrollbarColumn());
    }

    /**
     * attachScroll=false：scrollContainer 仅含 viewport 一个子（不含 scrollbar 列）。
     *
     * <p>这是 ConfigScreen 依赖的关键契约——ConfigScreen 随后自建 per-section scrollbar 并
     * appendChild 到 scrollContainer 得到 viewport + scrollbar 两子。若 shell 在 false 分支误建
     * scrollbar，ConfigScreen 的 scrollContainer 会变 3 子，该回归断言即挂。</p>
     */
    @Test
    public void attachScrollFalseScrollContainerHoldsViewportOnly() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", false, FormTheme.defaultDark());
        Assert.assertEquals("attachScroll=false 时 scrollContainer 仅含 viewport",
                1, parts.scrollContainer().__getChildren().size());
        Assert.assertSame("scrollContainer 唯一子应为 viewport",
                parts.viewport(), parts.scrollContainer().__getChildren().get(0));
    }

    // ==================== attachScroll=true 分支 ====================

    /** attachScroll=true：创建滚动受控源 + 可视滚动条，scrollSignal 与 scrollbarColumn 均非 null。 */
    @Test
    public void attachScrollTrueYieldsScrollSignalAndScrollbar() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", true, FormTheme.defaultDark());
        Assert.assertNotNull("attachScroll=true 时 scrollSignal 应非 null", parts.scrollSignal());
        Assert.assertNotNull("attachScroll=true 时 scrollbarColumn 应非 null", parts.scrollbarColumn());
    }

    /** attachScroll=true：scrollContainer 含 viewport + scrollbarColumn 两子，viewport 在前。 */
    @Test
    public void attachScrollTrueScrollContainerHoldsViewportAndScrollbar() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", true, FormTheme.defaultDark());
        Assert.assertEquals("attachScroll=true 时 scrollContainer 含 viewport + scrollbar",
                2, parts.scrollContainer().__getChildren().size());
        Assert.assertSame("scrollContainer 第一子为 viewport",
                parts.viewport(), parts.scrollContainer().__getChildren().get(0));
        Assert.assertSame("scrollContainer 第二子为 scrollbarColumn",
                parts.scrollbarColumn(), parts.scrollContainer().__getChildren().get(1));
    }

    // ==================== 骨架结构与父子关系 ====================

    /** root/viewport/scrollContainer 非 null，父子关系：root ⊃ scrollContainer ⊃ viewport。 */
    @Test
    public void skeletonNodesNonNullAndParentChildWired() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "标题", "副标题", false, FormTheme.defaultDark());
        Assert.assertNotNull("root 非 null", parts.root());
        Assert.assertNotNull("viewport 非 null", parts.viewport());
        Assert.assertNotNull("scrollContainer 非 null", parts.scrollContainer());
        Assert.assertSame("scrollContainer 父应为 root",
                parts.root(), parts.scrollContainer().__getParent());
        Assert.assertSame("viewport 父应为 scrollContainer",
                parts.scrollContainer(), parts.viewport().__getParent());
    }

    /** titleBar 为 root 首子，含标题文本；subtitle 非空时含副标题文本（共 2 个 text 子）。 */
    @Test
    public void titleBarIsFirstChildOfRootWithTitleText() {
        FormPageShell.Parts parts = FormPageShell.build(
                runtime, "我的标题", "我的副标题", false, FormTheme.defaultDark());
        SceneNode titleBar = parts.root().__getChildren().get(0);
        Assert.assertNotNull("titleBar（root 首子）非 null", titleBar);
        Assert.assertEquals("titleBar 含 title + subtitle 两个文本子",
                2, titleBar.__getChildren().size());
        Assert.assertEquals("titleBar 首子文本为标题",
                "我的标题", titleBar.__getChildren().get(0).getText());
        Assert.assertEquals("titleBar 次子文本为副标题",
                "我的副标题", titleBar.__getChildren().get(1).getText());
        Assert.assertNotSame("root 首子是 titleBar 而非 scrollContainer",
                parts.scrollContainer(), titleBar);
    }

    // ==================== 默认路径：外观消费来源主题 PANEL 配方 ====================

    /** 在可切换局部主题作用域内构建跟随主题的页骨架（不传 FormTheme 的重载）。 */
    private MountHandle mountThemedShell(Signal<SceneTheme> theme, final FormPageShell.Parts[] holder,
                                         boolean attachScroll, boolean buildTitleBar) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(theme, () -> {
                holder[0] = FormPageShell.build(runtime, "我的标题", "我的副标题",
                        attachScroll, buildTitleBar);
            });
            return holder[0].root();
        });
    }

    /**
     * 默认路径：root/viewport 表面逐项等于来源主题 PANEL 配方
     * （染色/边框色/边框宽/圆角/浮雕高度/滤镜），不再静态写 FormTheme 页底色。
     */
    @Test
    public void defaultBuildBindsPanelRecipeOnRootAndViewport() {
        SceneSurfaceStyle panel = SceneThemes.DEFAULT.surface(SceneTheme.Role.PANEL);
        Assert.assertNotNull("前置：PANEL 配方自带滤镜", panel.getBackdrop());
        final FormPageShell.Parts[] holder = new FormPageShell.Parts[1];
        MountHandle handle = mountThemedShell(
                Signal.create(SceneTheme.liquidGlassDark()), holder, false, true);
        runtime.flush();

        SceneNode root = holder[0].root();
        SceneNode viewport = holder[0].viewport();
        Assert.assertEquals("root 背景 = PANEL idle 染色",
                panel.getIdle().getTint(), root.getBackgroundColor());
        Assert.assertEquals("root 边框色 = PANEL idle 缘色",
                panel.getIdle().getEdge(), root.getBorderColor());
        Assert.assertEquals("root 边框宽 = PANEL 配方", panel.getBorderWidth(), root.getBorderWidth());
        Assert.assertEquals("root 圆角 = PANEL 配方", panel.getCornerRadius(), root.getCornerRadius());
        Assert.assertEquals("root 浮雕高度 = PANEL 配方",
                panel.getIdle().getElevation(), root.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull("root 装 PANEL 滤镜", root.getBackdrop());
        Assert.assertEquals("root 滤镜模糊半径 = 配方",
                panel.getBackdrop().getBlurRadius(), root.getBackdrop().getBlurRadius());

        Assert.assertEquals("viewport 背景 = PANEL idle 染色",
                panel.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("viewport 边框色 = PANEL idle 缘色",
                panel.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("viewport 边框宽 = PANEL 配方",
                panel.getBorderWidth(), viewport.getBorderWidth());
        Assert.assertEquals("viewport 圆角 = PANEL 配方",
                panel.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("viewport 浮雕高度 = PANEL 配方",
                panel.getIdle().getElevation(), viewport.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull("viewport 装 PANEL 滤镜", viewport.getBackdrop());
        Assert.assertEquals("viewport 滤镜材质 = 配方",
                panel.getBackdrop().getEffect().getMaterial(),
                viewport.getBackdrop().getEffect().getMaterial());
        handle.dispose();
    }

    /** 默认路径：标题取主题 foreground，副标题取主题 mutedForeground。 */
    @Test
    public void defaultBuildTitleTakesThemeForeground() {
        SceneTheme theme = SceneTheme.liquidGlassDark();
        final FormPageShell.Parts[] holder = new FormPageShell.Parts[1];
        MountHandle handle = mountThemedShell(Signal.create(theme), holder, false, true);
        runtime.flush();

        SceneNode titleNode = holder[0].titleBar().__getChildren().get(0);
        SceneNode subtitleNode = holder[0].titleBar().__getChildren().get(1);
        Assert.assertEquals("标题取主题 foreground", theme.foreground(), titleNode.getTextColor());
        Assert.assertEquals("副标题取主题 mutedForeground",
                theme.mutedForeground(), subtitleNode.getTextColor());
        handle.dispose();
    }

    /** 默认路径：骨架结构与布局尺寸与显式路径一致（titleBar 首子、滚动容器两子、尺寸不变）。 */
    @Test
    public void defaultBuildKeepsSkeletonStructureAndLayout() {
        final FormPageShell.Parts[] holder = new FormPageShell.Parts[1];
        MountHandle handle = mountThemedShell(
                Signal.create(SceneTheme.liquidGlassDark()), holder, true, true);
        runtime.flush();

        FormPageShell.Parts parts = holder[0];
        Assert.assertSame("titleBar 为 root 首子",
                parts.titleBar(), parts.root().__getChildren().get(0));
        Assert.assertSame("scrollContainer 为 root 次子",
                parts.scrollContainer(), parts.root().__getChildren().get(1));
        Assert.assertEquals("attachScroll=true 时 scrollContainer 含 viewport + scrollbar",
                2, parts.scrollContainer().__getChildren().size());
        Assert.assertNotNull("scrollSignal 非 null", parts.scrollSignal());
        Assert.assertNotNull("scrollbarColumn 非 null", parts.scrollbarColumn());

        Assert.assertEquals("root 内边距不变",
                FormPageShell.DEFAULT_ROOT_PADDING, parts.root().getPaddingLeft());
        Assert.assertEquals("root 间距不变",
                FormPageShell.DEFAULT_ROOT_GAP, parts.root().getGap());
        Assert.assertEquals("标题条高度不变",
                FormPageShell.DEFAULT_TITLE_BAR_HEIGHT, parts.titleBar().getPreferredHeight());
        Assert.assertEquals("视口内边距不变",
                FormPageShell.DEFAULT_VIEWPORT_PADDING, parts.viewport().getPaddingLeft());
        Assert.assertEquals("视口间距不变",
                FormPageShell.DEFAULT_VIEWPORT_GAP, parts.viewport().getGap());
        Assert.assertTrue("视口仍可滚动", parts.viewport().isScrollable());
        Assert.assertTrue("视口仍裁剪子节点", parts.viewport().isClipChildren());
        handle.dispose();
    }

    /** 默认路径：buildTitleBar=false 时跳过标题条构造，root 首子直接是 scrollContainer。 */
    @Test
    public void defaultBuildWithoutTitleBarSkipsTitleBar() {
        final FormPageShell.Parts[] holder = new FormPageShell.Parts[1];
        MountHandle handle = mountThemedShell(
                Signal.create(SceneTheme.liquidGlassDark()), holder, false, false);
        runtime.flush();

        Assert.assertNull("buildTitleBar=false 时 titleBar 为 null", holder[0].titleBar());
        Assert.assertSame("root 首子为 scrollContainer",
                holder[0].scrollContainer(), holder[0].root().__getChildren().get(0));
        handle.dispose();
    }

    // ==================== 显式路径：FormTheme 完全覆盖主题 ====================

    /**
     * 显式 FormTheme 完全覆盖来源主题：root/viewport 底色、视口圆角、标题色全部取显式值，
     * 不装滤镜/浮雕，且不注册任何主题订阅（attachScroll=false 时 effect 数零增长）。
     */
    @Test
    public void explicitThemeFullyOverridesThemeAndSkipsBindings() {
        FormTheme explicit = FormTheme.defaultDark();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassLight());
        final FormPageShell.Parts[] holder = new FormPageShell.Parts[1];
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> {
                holder[0] = FormPageShell.build(runtime, "我的标题", "我的副标题", false, explicit);
            });
            return holder[0].root();
        });
        runtime.flush();

        FormPageShell.Parts parts = holder[0];
        Assert.assertEquals("root 底色 = 显式 rootBg",
                explicit.rootBg(), parts.root().getBackgroundColor());
        Assert.assertEquals("viewport 底色 = 显式 viewportBg",
                explicit.viewportBg(), parts.viewport().getBackgroundColor());
        Assert.assertEquals("视口圆角 = 显式形参",
                FormPageShell.DEFAULT_VIEWPORT_RADIUS, parts.viewport().getCornerRadius());
        Assert.assertNull("显式路径 root 不装滤镜", parts.root().getBackdrop());
        Assert.assertNull("显式路径 viewport 不装滤镜", parts.viewport().getBackdrop());
        Assert.assertEquals("显式路径不绑浮雕", -1.0F, parts.root().__getSurfaceElevation(), 0.0001F);
        Assert.assertEquals("显式路径 viewport 不绑浮雕",
                -1.0F, parts.viewport().__getSurfaceElevation(), 0.0001F);
        Assert.assertEquals("标题取显式 titleColor",
                explicit.titleColor(), parts.titleBar().__getChildren().get(0).getTextColor());
        Assert.assertEquals("副标题取显式 mutedColor",
                explicit.mutedColor(), parts.titleBar().__getChildren().get(1).getTextColor());
        Assert.assertEquals("显式路径不订阅主题", effectsBefore, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassDark());
        runtime.flush();

        Assert.assertEquals("主题切换不改显式 root 底色",
                explicit.rootBg(), parts.root().getBackgroundColor());
        Assert.assertEquals("主题切换不改显式 viewport 底色",
                explicit.viewportBg(), parts.viewport().getBackgroundColor());
        Assert.assertEquals("主题切换不改显式标题色",
                explicit.titleColor(), parts.titleBar().__getChildren().get(0).getTextColor());
        handle.dispose();
    }

    // ==================== 主题切换与卸载 ====================

    /**
     * 主题切换：{@code withTheme} 来源主题信号变化 + flush 后 root/viewport/标题更新，
     * 滚动偏移、节点身份与订阅数不变（外观重派生不重建节点、不重复订阅）。
     */
    @Test
    public void themeSwitchUpdatesShellAndKeepsScrollOffsetAndNodeIdentity() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkPanel = dark.surface(SceneTheme.Role.PANEL);
        SceneSurfaceStyle lightPanel = light.surface(SceneTheme.Role.PANEL);
        Assert.assertNotEquals("两个主题的 PANEL 配方必须不同，否则切换不传播",
                darkPanel.getIdle(), lightPanel.getIdle());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        final FormPageShell.Parts[] holder = new FormPageShell.Parts[1];
        MountHandle handle = mountThemedShell(pageTheme, holder, true, true);
        runtime.flush();

        FormPageShell.Parts parts = holder[0];
        SceneNode root = parts.root();
        SceneNode viewport = parts.viewport();
        SceneNode titleNode = parts.titleBar().__getChildren().get(0);
        Signal<Integer> scrollSignal = parts.scrollSignal();
        Assert.assertEquals("前置：深色档 root 背景",
                darkPanel.getIdle().getTint(), root.getBackgroundColor());
        Assert.assertEquals("前置：深色档标题色", dark.foreground(), titleNode.getTextColor());

        scrollSignal.set(Integer.valueOf(42));
        runtime.flush();
        Assert.assertEquals("前置：滚动偏移已应用", 42, viewport.getScrollOffsetY());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("切换后 root 背景 = 浅色 PANEL idle",
                lightPanel.getIdle().getTint(), root.getBackgroundColor());
        Assert.assertEquals("切换后 viewport 背景 = 浅色 PANEL idle",
                lightPanel.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("切换后 viewport 边框色 = 浅色 PANEL idle 缘色",
                lightPanel.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("切换后标题取新主题前景", light.foreground(), titleNode.getTextColor());
        Assert.assertSame("主题切换不重建 root", root, parts.root());
        Assert.assertSame("主题切换不重建 viewport", viewport, parts.viewport());
        Assert.assertSame("主题切换不重建标题节点", titleNode, parts.titleBar().__getChildren().get(0));
        Assert.assertSame("主题切换不重建 scrollSignal", scrollSignal, parts.scrollSignal());
        Assert.assertEquals("主题切换保留滚动偏移", 42, viewport.getScrollOffsetY());
        Assert.assertEquals("主题切换保留 scrollSignal 值", 42, scrollSignal.get().intValue());
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    /** 卸载回收：默认路径的外观/滚动绑定 effect 随 mount 卸载全部退订，主题更新不再写入旧节点。 */
    @Test
    public void unmountReleasesThemeSurfaceBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        final FormPageShell.Parts[] holder = new FormPageShell.Parts[1];
        MountHandle handle = mountThemedShell(pageTheme, holder, true, true);
        runtime.flush();
        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > baseline);

        SceneNode root = holder[0].root();
        int colorBeforeDispose = root.getBackgroundColor();
        handle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载后外观绑定 effect 应回收",
                baseline, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧页壳",
                colorBeforeDispose, root.getBackgroundColor());
    }
}
