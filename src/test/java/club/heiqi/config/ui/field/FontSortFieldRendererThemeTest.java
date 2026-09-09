package club.heiqi.config.ui.field;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link FontSortFieldRenderer} 默认液态玻璃主题跟随证据（G15/FontSort）。
 *
 * <p><b>实例口径</b>：字段卡片表面与 dirty/error 语义色由 {@link FieldShellBinder}（G15/Support，
 * theme-aware {@code FormFieldShell.build} 默认路径）派生；把手/筛选框/索引输入框/清空按钮/滚动条
 * 的表面与前景由已迁移控件本体（G12/G04/G03/G07，契约 §4.1）自持，Renderer 只组 Props、
 * 不叠加第二层表面。Renderer 侧仅有的两处文本前景写入点（字体名行标签、空结果提示）已切
 * {@link SceneThemes#foreground(SceneRuntime)} / {@link SceneThemes#mutedForeground(SceneRuntime)}
 * 主题信号。本测试类经「真实宿主装配路径」（{@code runtime.mount} + {@code SceneThemes.withTheme}
 * 内调 {@code renderer.render}，同 G15/Choice、G15/Number 夹具口径）钉住：
 * ① 默认路径各属性恰等于来源主题对应角色配方/语义色（证「零叠加、零竞争写入」——renderer 侧
 * 任何补色都会偏离配方值）；② 换主题→行标签/卡底/筛选框/把手/空提示颜色变（P-04 口径：
 * 两档 {@code assertNotEquals} 前提 + 切换后逐项重派生 + 草稿/节点身份/effect 数不动）；
 * ③ 排序事务（keyed 行复用、拖拽/索引提交路径归 integration 既有测试）与 dirty/error 行为零改动。</p>
 *
 * <p>配套源码守卫钉「本类零表面写入、前景唯一来源 = 主题信号」与占位参现状（G15/Support 裁决②：
 * Renderer 期间禁自行摘除 binder 的 {@code theme} 兼容占位形参）；弹层与显式配方覆盖归控件本体证据。</p>
 */
public class FontSortFieldRendererThemeTest {

    private SceneRuntime runtime;
    private ConfigSchema schema;
    private Authority authority;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private FontSortFieldRenderer renderer;
    private FieldSpec spec;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        schema = ConfigSchema.builder("t")
                .section("fontSystem")
                    .simpleList("fontSort").label("字体排序").helper("按优先级拖拽排序").build()
                .endSection()
                .build();
        spec = schema.field("fontSystem.fontSort");
        authority = Authority.load(new File("nonexistent-fontsort-theme.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new FontSortFieldRenderer(Arrays.asList("Font A", "Font B"));
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() throws Exception {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 默认路径：真实装配下逐属性 = 来源主题配方/语义色恰等 ====================

    /**
     * 默认路径（withTheme=深色档）：卡片 = GROUP 配方、筛选框 = INPUT 配方、清空按钮 =
     * BUTTON_STANDARD 配方、把手 = INDICATOR idle tint + 图标 mutedForeground、行标签 =
     * foreground——逐属性恰等；行包装不叠第二层表面（底色/backdrop/浮雕 = 未写入基线）。
     */
    @Test
    public void defaultAssemblyMatchesSourceThemeAndControlsKeepOwnSurfaces() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(dark), holder);
        runtime.flush();

        SceneNode card = holder[0];
        // 卡片：Support→FormFieldShell theme-aware 默认路径（本类不复制）
        Assert.assertEquals("卡底色 = GROUP 配方 idle tint",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("静默态卡缘 = GROUP 配方 idle edge",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getEdge(), card.getBorderColor());

        // 筛选框 / 清空按钮：已迁移控件自持（G04 / G03），renderer 只组 Props
        SceneNode filterBar = controlRoot(card).__getChildren().get(0);
        SceneNode filterInput = filterBar.__getChildren().get(0);
        SceneNode clearButton = filterBar.__getChildren().get(1);
        Assert.assertEquals("筛选框底色 = INPUT 配方 idle tint",
                dark.surface(SceneTheme.Role.INPUT).getIdle().getTint(), filterInput.getBackgroundColor());
        Assert.assertNotNull("筛选框滤镜由控件本体装配", filterInput.getBackdrop());
        Assert.assertEquals("清空按钮底色 = BUTTON_STANDARD 配方 idle tint",
                dark.surface(SceneTheme.Role.BUTTON_STANDARD).getIdle().getTint(),
                clearButton.getBackgroundColor());

        // 行结构：把手（G12 自持）+ 索引输入（G04 自持）+ 字体名标签（本类经主题信号绑定）
        SceneNode line = rowAt(card, 0);
        SceneNode dragHandle = line.__getChildren().get(0);
        SceneNode handleIcon = dragHandle.__getChildren().get(0);
        SceneNode label = line.__getChildren().get(2);
        Assert.assertEquals("把手底色 = INDICATOR 配方 idle tint",
                dark.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), dragHandle.getBackgroundColor());
        Assert.assertEquals("把手图标 = 主题 mutedForeground",
                dark.mutedForeground(), handleIcon.getTextColor());
        Assert.assertEquals("字体名行标签前景 = 主题 foreground",
                dark.foreground(), label.getTextColor());
        Assert.assertEquals("行标签字号 = FormTheme 排版常量（契约 §4：主题不接管布局）",
                ConfigTheme.asFormTheme().fontLabel(), label.getFontSize());

        // 行包装只是布局容器：不写底色、不装滤镜、不带浮雕（不叠第二层玻璃）
        SceneNode fresh = new SceneNode();
        Assert.assertEquals("ROW 底色保持节点默认（未写第二表面）",
                fresh.getBackgroundColor(), line.getBackgroundColor());
        Assert.assertNull("ROW 不装 backdrop", line.getBackdrop());
        Assert.assertEquals("ROW 未绑定表面浮雕（契约 §4：未绑定节点保持 -1）",
                fresh.__getSurfaceElevation(), line.__getSurfaceElevation(), 0.0001f);
        handle.dispose();
    }

    // ==================== P-04：换主题→颜色变（真实 renderer.render 装配 + 两档前提） ====================

    /**
     * 主题切换：卡片/筛选框/把手/图标/行标签前景全部重派生为浅色档配方/语义色；dirty 缘色跟随
     * 新主题 accent；节点身份不变、编辑后的行顺序与草稿不丢、effect 数不增。构造期若回退成
     * {@code .get()} 固定快照或错绑语义色，本用例变红（变异检查 M-A/M-B 对象）。
     */
    @Test
    public void themeSwitchRepaintsLabelsCardsAndHandlesKeepingDraftAndIdentity() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        // 两档前提（P-04）：以下切换断言的取值档必须两档不同，否则切换不传播也恒绿
        Assert.assertNotEquals("前提：两档 foreground 不同", dark.foreground(), light.foreground());
        Assert.assertNotEquals("前提：两档 mutedForeground 不同",
                dark.mutedForeground(), light.mutedForeground());
        Assert.assertNotEquals("前提：两档 GROUP idle 配方不同",
                dark.surface(SceneTheme.Role.GROUP).getIdle(),
                light.surface(SceneTheme.Role.GROUP).getIdle());
        Assert.assertNotEquals("前提：两档 INPUT idle 配方不同",
                dark.surface(SceneTheme.Role.INPUT).getIdle(),
                light.surface(SceneTheme.Role.INPUT).getIdle());
        Assert.assertNotEquals("前提：两档 INDICATOR idle 配方不同",
                dark.surface(SceneTheme.Role.INDICATOR).getIdle(),
                light.surface(SceneTheme.Role.INDICATOR).getIdle());
        Assert.assertNotEquals("前提：两档 accent 不同", dark.accent(), light.accent());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(pageTheme, holder);
        runtime.flush();
        SceneNode card = holder[0];

        // 编辑草稿（交换两行的完整 order）→ dirty 缘色 = 深色档 accent；keyed 行按新序复用
        adapter.onFieldEdit("fontSystem.fontSort", Arrays.asList("Font B", "Font A"));
        runtime.flush();
        Assert.assertEquals("编辑后 dirty 缘色 = 深色档 accent", dark.accent(), card.getBorderColor());
        SceneNode lineA = rowAt(card, 1);
        SceneNode lineB = rowAt(card, 0);
        Assert.assertEquals("编辑后首行 = Font B", "Font B",
                lineB.__getChildren().get(2).getText());

        SceneNode viewportBefore = findScrollable(card);
        int bgBefore = card.getBackgroundColor();
        SceneNode filterInput = controlRoot(card).__getChildren().get(0).__getChildren().get(0);
        int inputBgBefore = filterInput.getBackgroundColor();
        int handleBgBefore = lineB.__getChildren().get(0).getBackgroundColor();
        int labelBefore = lineB.__getChildren().get(2).getTextColor();
        int iconBefore = lineB.__getChildren().get(0).__getChildren().get(0).getTextColor();
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        pageTheme.set(light);
        runtime.flush();

        Assert.assertNotEquals("切换后卡底色变（P-04：换主题→颜色变）", bgBefore, card.getBackgroundColor());
        Assert.assertNotEquals("切换后筛选框底色变", inputBgBefore,
                controlRoot(card).__getChildren().get(0).__getChildren().get(0).getBackgroundColor());
        Assert.assertNotEquals("切换后把手底色变", handleBgBefore,
                lineB.__getChildren().get(0).getBackgroundColor());
        Assert.assertNotEquals("切换后行标签前景变（本实例迁移点）", labelBefore,
                lineB.__getChildren().get(2).getTextColor());
        Assert.assertNotEquals("切换后把手图标前景变", iconBefore,
                lineB.__getChildren().get(0).__getChildren().get(0).getTextColor());
        Assert.assertEquals("切换后卡底色 = 浅色档 GROUP idle tint",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("切换后行标签前景 = 浅色档 foreground",
                light.foreground(), lineB.__getChildren().get(2).getTextColor());
        Assert.assertEquals("切换后把手底色 = 浅色档 INDICATOR idle tint",
                light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(),
                lineB.__getChildren().get(0).getBackgroundColor());
        Assert.assertEquals("切换后把手图标 = 浅色档 mutedForeground",
                light.mutedForeground(), lineB.__getChildren().get(0).__getChildren().get(0).getTextColor());
        Assert.assertEquals("切换后 dirty 缘色跟随新主题 accent", light.accent(), card.getBorderColor());
        Assert.assertSame("切换不重建卡片节点", card, holder[0]);
        Assert.assertSame("切换不重建视口节点", viewportBefore, findScrollable(card));
        Assert.assertSame("切换复用 keyed 行节点（Font B）", lineB, rowAt(card, 0));
        Assert.assertSame("切换复用 keyed 行节点（Font A）", lineA, rowAt(card, 1));
        Assert.assertEquals("切换不丢草稿值",
                Arrays.asList("Font B", "Font A"), draft.getDraft("fontSystem.fontSort"));
        Assert.assertEquals("切换不新增 effect", effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    // ==================== runtime 默认档（镜像 ConfigScreen 真实机制） ====================

    /**
     * 不建局部作用域、只经 {@code SceneThemes.install} 的 runtime 默认档同样驱动本渲染器前景：
     * 行标签与卡底随 runtime 主题切换重派生（G15/Shell 的 runtime 级默认机制销账证据）。
     */
    @Test
    public void runtimeInstalledThemeDrivesRendererForegroundWithoutWithTheme() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Signal<SceneTheme> runtimeTheme = Signal.create(dark);
        SceneThemes.install(runtime, runtimeTheme);
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(new SceneNode(),
                () -> holder[0] = renderer.render(runtime, spec, adapter));
        runtime.flush();

        SceneNode label = rowAt(holder[0], 0).__getChildren().get(2);
        Assert.assertEquals("runtime 默认档：行标签 = 深色 foreground",
                dark.foreground(), label.getTextColor());
        Assert.assertEquals("runtime 默认档：卡底 = 深色 GROUP idle tint",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(), holder[0].getBackgroundColor());

        int labelBefore = label.getTextColor();
        runtimeTheme.set(light);
        runtime.flush();
        Assert.assertNotEquals("runtime 档切换后颜色变（P-04）", labelBefore, label.getTextColor());
        Assert.assertEquals("runtime 档切换后行标签 = 浅色 foreground",
                light.foreground(), label.getTextColor());
        Assert.assertEquals("runtime 档切换后卡底 = 浅色 GROUP idle tint",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), holder[0].getBackgroundColor());
        handle.dispose();
    }

    // ==================== 空结果提示：mutedForeground 信号 + 切换重派生（真实交互） ====================

    /**
     * 交互路径（{@code SceneInteractionHarness}）：筛选无匹配 → 空提示出现，前景 = 来源主题
     * mutedForeground；换主题后同一提示节点重派生为新档次要色（构造期快照变异会让本用例变红）；
     * 点清空按钮恢复 2 行且 draft/dirty 零写入——筛选只改可见投影的既有语义保持。
     */
    @Test
    public void emptyResultHintFollowsMutedForegroundAcrossThemeSwitch() throws Exception {
        SceneInteractionHarness harness = SceneInteractionHarness.create();
        SceneRuntime rt = harness.getRuntime();
        Authority auth = Authority.load(new File("nonexistent-fontsort-hint.yaml"), schema);
        DraftBuffer d = DraftBuffer.from(auth);
        final DraftSignalAdapter a = new DraftSignalAdapter(rt, d);
        Owner renderOwner = new Owner();
        try {
            ReactiveScheduler.get().flush();

            SceneTheme dark = SceneTheme.liquidGlassDark();
            SceneTheme light = SceneTheme.liquidGlassLight();
            Assert.assertNotEquals("前提：两档 mutedForeground 不同",
                    dark.mutedForeground(), light.mutedForeground());
            Signal<SceneTheme> pageTheme = Signal.create(dark);

            final SceneNode[] holder = new SceneNode[1];
            renderOwner.run(() -> SceneThemes.withTheme(pageTheme,
                    () -> holder[0] = renderer.render(rt, spec, a)));
            SceneNode sceneRoot = new SceneNode();
            sceneRoot.appendChild(holder[0]);
            rt.flush();
            rt.flush();
            harness.mountRoot(sceneRoot, 520, 360);

            SceneNode filterBar = controlRoot(holder[0]).__getChildren().get(0);
            harness.click(filterBar.__getChildren().get(0));
            harness.typeText("zzz");
            rt.flush();
            rt.flush();

            SceneNode hint = findNodeWithText(holder[0], "无匹配字体");
            Assert.assertNotNull("空筛选应显示紧凑提示", hint);
            Assert.assertEquals("空提示前景 = 深色档 mutedForeground",
                    dark.mutedForeground(), hint.getTextColor());
            Assert.assertFalse("筛选不 dirty", d.isDirty("fontSystem.fontSort"));

            int before = hint.getTextColor();
            pageTheme.set(light);
            rt.flush();
            SceneNode hintAfter = findNodeWithText(holder[0], "无匹配字体");
            Assert.assertSame("切换不重建提示节点", hint, hintAfter);
            Assert.assertNotEquals("切换后提示颜色变（P-04）", before, hintAfter.getTextColor());
            Assert.assertEquals("切换后提示前景 = 浅色档 mutedForeground",
                    light.mutedForeground(), hintAfter.getTextColor());

            // 清空按钮行为保持：恢复全量行、提示撤下、仍不写 draft
            harness.click(filterBar.__getChildren().get(1));
            rt.flush();
            rt.flush();
            Assert.assertNull("清空后提示撤下", findNodeWithText(holder[0], "无匹配字体"));
            Assert.assertEquals("清空后恢复 2 行", 2,
                    findScrollable(holder[0]).__getChildren().get(0).__getChildren().size());
            Assert.assertEquals("draft 仍空", java.util.Collections.emptyList(),
                    d.getDraft("fontSystem.fontSort"));
        } finally {
            renderOwner.dispose();
            a.dispose();
            harness.dispose();
            ReactiveScheduler.get().reset();
        }
    }

    // ==================== 卸载回收 ====================

    /** 挂载→卸载 + flush 后注册 effect 数回到基线（契约 §9 / 施工手册 §3.4）。 */
    @Test
    public void disposeReturnsEffectCountToBaseline() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(SceneTheme.liquidGlassDark()), holder);
        runtime.flush();
        Assert.assertTrue("挂载后 effect 数高于基线",
                ReactiveTestProbe.registeredEffectCount() > baseline);
        handle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载回到基线", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 源码守卫：前景唯一来源 = 主题信号；零表面写入；占位参现状（裁决②） ====================

    /**
     * 守卫：{@code FontSortFieldRenderer} 零表面/边框/滤镜/浮雕写入、零旧接缝、零静态取色直写——
     * {@code setTextColor(} 直写禁出现，前景只允许 {@code ::setTextColor} 方法引用挂在
     * {@code SceneThemes.foreground(rt)} / {@code SceneThemes.mutedForeground(rt)} 信号上（各恰 1 处）；
     * 不取角色配方（{@code SceneThemes.surface} 等）、不直接组装 FormFieldShell；
     * {@code ConfigTheme.asFormTheme()} 只允许 binder 兼容占位 + 本类布局/排版取值，计数钉死。
     *
     * <p><b>G15/收口实例同步义务</b>：摘除 binder 占位形参时，须同批把本守卫的占位计数断言
     * （{@code ConfigTheme} 恰 2 处、{@code asFormTheme()} 恰 1 处）与本类唯一调用点一并更新，
     * 不得只改一侧；同时 7 个 Renderer 守卫计数（Choice 恰 2、FontSort 恰 1 等）由收口实例统一销账。</p>
     */
    @Test
    public void sourceGuardForegroundOnlyViaThemeSignalsNoSurfaceWritesPlaceholderKept() throws Exception {
        String code = FieldShellBinderTest.codeWithoutComments(
                new String(Files.readAllBytes(Paths.get(
                        "src/main/java/club/heiqi/config/ui/field/FontSortFieldRenderer.java")),
                        StandardCharsets.UTF_8));
        String[] banned = {
                "setBackgroundColor", "setBorderColor", "setBorderWidth", "setCornerRadius",
                "setBackdrop", "setTextColor(", "SurfaceElevation",
                "bindStandardBorder", "bindSelectableBackground",
                "SceneSurfaceBinder", "SceneControlChrome", "SceneStateColors", "SceneChromeTokens",
                "SceneThemes.surface", "selectableSurface", "accentSurface",
                "theme.textColor", "theme.mutedColor", "theme.disabledColor",
                "FormFieldShell", "FormPageShell", "buildBorderless", "0x",
        };
        for (String token : banned) {
            Assert.assertFalse("守卫：FontSortFieldRenderer 代码不得出现 " + token, code.contains(token));
        }
        // 前景唯一来源 = 主题信号绑定（构建期捕获、无 .get() 快照；恰各 1 处）
        Assert.assertEquals("行标签前景绑定恰 1 处",
                1, countOccurrences(code, "rt.bind(SceneThemes.foreground(rt), label::setTextColor)"));
        Assert.assertEquals("空提示前景绑定恰 1 处",
                1, countOccurrences(code, "rt.bind(SceneThemes.mutedForeground(rt), node::setTextColor)"));
        Assert.assertEquals("::setTextColor 方法引用总数 = 2 处绑定目标",
                2, countOccurrences(code, "::setTextColor"));
        // 装配唯一经 Support binder；theme 实参 = 兼容占位（裁决②），本类只余布局/排版消费
        Assert.assertEquals("外壳装配只经 FieldShellBinder 恰 1 处",
                1, countOccurrences(code, "FieldShellBinder.build(rt, spec, adapter,"));
        Assert.assertEquals("显式主题只允许 asFormTheme() 兼容占位形态恰 1 处",
                1, countOccurrences(code, "ConfigTheme.asFormTheme()"));
        Assert.assertEquals("ConfigTheme 引用总数 = import + 1 占位实参",
                2, countOccurrences(code, "ConfigTheme"));
        // 布局/排版常量保留（契约 §4 + Support 衔接要点 3），非外观写入点
        Assert.assertEquals("listHeight 布局入参恰 2 处（binder 高度 + 视口高）",
                2, countOccurrences(code, "theme.listHeight()"));
        Assert.assertEquals("fontLabel 排版常量恰 1 处", 1, countOccurrences(code, "theme.fontLabel()"));
        Assert.assertEquals("fontHelper 排版常量恰 1 处", 1, countOccurrences(code, "theme.fontHelper()"));
        // 已迁移控件只读复用：各经工厂挂载，无第二份样式构造
        Assert.assertEquals("TextInput 工厂挂载恰 2 处（筛选框 + 索引输入）",
                2, countOccurrences(code, "SceneTextInput.create(rt, "));
        Assert.assertEquals("Button 工厂挂载恰 1 处",
                1, countOccurrences(code, "SceneButton.create(rt, "));
        Assert.assertEquals("DragReorder 只经 buildHandle 恰 1 处",
                1, countOccurrences(code, "SceneDragReorder.buildHandle("));
    }

    // ==================== 夹具 ====================

    /** 真实装配夹具：{@code runtime.mount} + {@code SceneThemes.withTheme} 内调 renderer.render。 */
    private MountHandle mountThroughRenderer(Signal<SceneTheme> pageTheme, SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = renderer.render(runtime, spec, adapter));
            return holder[0];
        });
    }

    /** 控件根（buildControl 产出的 column）：视口的父的父（同 integration FontSortFieldRendererTest 口径）。 */
    private static SceneNode controlRoot(SceneNode card) {
        SceneNode viewport = findScrollable(card);
        Assert.assertNotNull("card 内应找到滚动视口", viewport);
        return viewport.__getParent().__getParent();
    }

    /** rowsContainer 的第 index 行（行结构 [handle, index input, label]）。 */
    private static SceneNode rowAt(SceneNode card, int index) {
        SceneNode viewport = findScrollable(card);
        return viewport.__getChildren().get(0).__getChildren().get(index);
    }

    private static SceneNode findScrollable(SceneNode node) {
        if (node.isScrollable()) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findScrollable(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 按文本内容深度查找节点（bindText/setText 派生在 flush 后可见）。 */
    private static SceneNode findNodeWithText(SceneNode node, String text) {
        if (text.equals(node.getText())) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findNodeWithText(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int idx = haystack.indexOf(needle, from);
            if (idx < 0) {
                return count;
            }
            count++;
            from = idx + needle.length();
        }
    }
}
