package club.heiqi.config.ui.field;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
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
 * G15/SimpleList：{@link SimpleListFieldRenderer} 默认液态玻璃外观测试。
 *
 * <p>本实例的装配边界：字段卡片表面与标题/helper/error/dirty 语义色由
 * {@link FieldShellBinder}（G15/Support）→ {@code FormFieldShell} theme-aware 默认路径消费
 * 来源主题（GROUP 角色）；列表底座（GROUP）、行内输入（INPUT）、添加/删除按钮等外观由已
 * 主题化的 {@code SceneSimpleList}（G12）自持——本测试沿<b>真实渲染器路径</b>
 * （{@code renderer.render → DraftListBridge → SceneSimpleList.create → FieldShellBinder.build}）
 * 验证「默认路径 = 来源主题配方」「换主题 → 颜色变」（P-04：切换更新 + 两档
 * {@code assertNotEquals} 前提 + 真实 {@code mount + withTheme / install} 装配），以及
 * 「行按契约 §4.1 轻量口径、渲染器不叠第二层表面」「listHeight 纯布局常量保留」「dirty/error
 * 与草稿桥行为零改动」。控件本体的主题化证据在 {@code SceneSimpleListTest}（G12），此处不重复。</p>
 */
public class SimpleListFieldRendererThemeTest {

    /**
     * 多行字段控件根高度：与 {@code FormTheme.defaultDark().listHeight()} 同源取值 220。
     * 契约 §4「尺寸/布局属性归控件自身，主题不接管布局」——写死期望值，防止与渲染器同源漂移。
     */
    private static final int EXPECTED_LIST_HEIGHT = 220;

    private SceneRuntime runtime;
    private ConfigSchema schema;
    private FieldSpec spec;
    private Authority authority;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private SimpleListFieldRenderer renderer;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        schema = ConfigSchema.builder("t")
                .section("font")
                    .simpleList("sort").label("Sort").helper("排序").build()
                .endSection()
                .build();
        spec = schema.field("font.sort");
        File file = File.createTempFile("simplelist-themetest-", ".yaml");
        write(file, "font:\n  sort:\n    - a\n    - b\n    - c\n");
        authority = Authority.load(file, schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new SimpleListFieldRenderer();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 默认路径：真实渲染器装配 = 来源主题配方 ====================

    /**
     * 默认路径（mount + withTheme + renderer.render）：卡片 = GROUP 配方、底座 = 控件自持 GROUP、
     * 行内输入 = 控件自持 INPUT、行轻量（无滤镜/透明/无边框圆角）、listHeight 纯布局保留。
     */
    @Test
    public void defaultPathFollowsSourceThemeThroughRealRendererAssembly() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(dark), holder);
        settle();

        SceneNode card = holder[0];
        SceneSurfaceStyle group = dark.surface(SceneTheme.Role.GROUP);
        Assert.assertEquals("卡底色 = 来源主题 GROUP idle tint（binder→FormFieldShell 主题路径）",
                group.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("卡缘色 = GROUP idle edge", group.getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("卡圆角 = GROUP 配方圆角", group.getCornerRadius(), card.getCornerRadius());
        Assert.assertEquals("卡边框宽 = GROUP 配方边框宽", group.getBorderWidth(), card.getBorderWidth());
        Assert.assertNotNull("卡片语义表面装有滤镜（背景采样）", card.getBackdrop());

        SceneNode title = card.__getChildren().get(0).__getChildren().get(1);
        Assert.assertEquals("标题前景 = 主题 foreground", dark.foreground(), title.getTextColor());
        SceneNode helper = card.__getChildren().get(1);
        Assert.assertEquals("helper 前景 = 主题 mutedForeground",
                dark.mutedForeground(), helper.getTextColor());

        SceneNode viewport = listViewport(card);
        // 结构链：控件根（SimpleList 列根）→ stackHost → viewport（与集成测试探针同源结构）
        SceneNode controlRoot = viewport.__getParent().__getParent();
        Assert.assertEquals("控件根高度 = listHeight 纯布局常量（契约 §4：主题不接管布局，保留）",
                EXPECTED_LIST_HEIGHT, controlRoot.getPreferredHeight());
        Assert.assertEquals("列表底座染色 = GROUP idle tint（控件本体自持，本类只消费）",
                group.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertNotNull("列表底座滤镜由控件本体装配", viewport.getBackdrop());

        Assert.assertEquals("初值应渲染 3 行", 3, viewport.__getChildren().size());
        SceneSurfaceStyle input = dark.surface(SceneTheme.Role.INPUT);
        for (int i = 0; i < 3; i++) {
            SceneNode row = viewport.__getChildren().get(i);
            Assert.assertNull("行[" + i + "] 不装滤镜（契约 §4.1 轻量口径）", row.getBackdrop());
            Assert.assertEquals("行[" + i + "] 默认透明", 0, row.getBackgroundColor());
            Assert.assertEquals("行[" + i + "] 不写边框宽", 0, row.getBorderWidth());
            Assert.assertEquals("行[" + i + "] 不写圆角", 0, row.getCornerRadius());
            SceneNode inputRoot = row.__getChildren().get(0);
            Assert.assertEquals("行[" + i + "] 输入底色 = INPUT idle tint（SceneTextInput 自持）",
                    input.getIdle().getTint(), inputRoot.getBackgroundColor());
            Assert.assertNotNull("行[" + i + "] 输入滤镜由控件本体装配", inputRoot.getBackdrop());
        }
        handle.dispose();
    }

    // ==================== P-04：换主题 → 颜色变（真实 renderer.render 装配路径） ====================

    /**
     * 页面局部主题切换：卡底/卡缘/标题/helper/底座/行内输入全部重派生；行保持轻量；
     * 草稿（列表内容）、节点身份、控件根高度不变；effect 不增长；切回恢复。
     *
     * <p>把装配改回显式 FormTheme 旧路径（绕过主题）或给行/底座叠第二层表面，本用例变红
     * （变异可打红）。</p>
     */
    @Test
    public void themeSwitchRepaintsCardBaseAndInputViaRealAssembly() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        // 两档前提：所断言分量在深/浅档互不相同，否则切换不传播、断言无意义（Computed 值记忆化）。
        Assert.assertNotEquals("前提：GROUP idle tint 两档不同",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(),
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint());
        Assert.assertNotEquals("前提：GROUP idle edge 两档不同",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getEdge(),
                light.surface(SceneTheme.Role.GROUP).getIdle().getEdge());
        Assert.assertNotEquals("前提：INPUT idle tint 两档不同",
                dark.surface(SceneTheme.Role.INPUT).getIdle().getTint(),
                light.surface(SceneTheme.Role.INPUT).getIdle().getTint());
        Assert.assertNotEquals("前提：foreground 两档不同", dark.foreground(), light.foreground());
        Assert.assertNotEquals("前提：mutedForeground 两档不同",
                dark.mutedForeground(), light.mutedForeground());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(pageTheme, holder);
        settle();

        SceneNode card = holder[0];
        SceneNode title = card.__getChildren().get(0).__getChildren().get(1);
        SceneNode helper = card.__getChildren().get(1);
        SceneNode viewport = listViewport(card);
        SceneNode row0 = viewport.__getChildren().get(0);
        SceneNode input0 = row0.__getChildren().get(0);
        int cardBgBefore = card.getBackgroundColor();
        int cardEdgeBefore = card.getBorderColor();
        int titleBefore = title.getTextColor();
        int viewportBgBefore = viewport.getBackgroundColor();
        int inputBgBefore = input0.getBackgroundColor();
        Assert.assertEquals("前置：卡底 = 深色 GROUP idle tint",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(), cardBgBefore);
        Assert.assertEquals("前置：底座 = 深色 GROUP idle tint",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(), viewportBgBefore);

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        settle();

        Assert.assertNotEquals("切换后卡底色变化", cardBgBefore, card.getBackgroundColor());
        Assert.assertEquals("切换后卡底 = 浅色 GROUP idle tint",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertNotEquals("切换后卡缘色变化", cardEdgeBefore, card.getBorderColor());
        Assert.assertEquals("切换后卡缘 = 浅色 GROUP idle edge",
                light.surface(SceneTheme.Role.GROUP).getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("前置：标题前景 = 深色 foreground", dark.foreground(), titleBefore);
        Assert.assertNotEquals("切换后标题前景变化", titleBefore, title.getTextColor());
        Assert.assertEquals("切换后标题 = 浅色 foreground", light.foreground(), title.getTextColor());
        Assert.assertEquals("切换后 helper = 浅色 mutedForeground",
                light.mutedForeground(), helper.getTextColor());
        Assert.assertNotEquals("切换后底座染色变化", viewportBgBefore, viewport.getBackgroundColor());
        Assert.assertEquals("切换后底座 = 浅色 GROUP idle tint",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertNotEquals("切换后行内输入底色变化", inputBgBefore, input0.getBackgroundColor());
        Assert.assertEquals("切换后行内输入 = 浅色 INPUT idle tint",
                light.surface(SceneTheme.Role.INPUT).getIdle().getTint(), input0.getBackgroundColor());

        // 行按 §4.1 轻量口径：切换前后都不装滤镜、保持透明（不随主题叠第二层玻璃）。
        Assert.assertNull("切换后行仍不装滤镜", row0.getBackdrop());
        Assert.assertEquals("切换后行仍默认透明", 0, row0.getBackgroundColor());
        // 布局不随主题漂移（controlHeight 纯 int）。
        Assert.assertEquals("切换后控件根高度不变",
                EXPECTED_LIST_HEIGHT, viewport.__getParent().__getParent().getPreferredHeight());

        Assert.assertSame("换肤不重建：卡片身份不变", card, holder[0]);
        Assert.assertSame("换肤不重建：底座身份不变", viewport, listViewport(holder[0]));
        Assert.assertSame("换肤不重建：行身份不变", row0, listViewport(holder[0]).__getChildren().get(0));
        Assert.assertSame("换肤不重建：行内输入身份不变", input0,
                listViewport(holder[0]).__getChildren().get(0).__getChildren().get(0));
        Assert.assertEquals("主题切换不丢草稿（列表内容）",
                Arrays.asList("a", "b", "c"), adapter.draftSignal("font.sort").get());
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(dark);
        settle();
        Assert.assertEquals("切回深色后卡底恢复", cardBgBefore, card.getBackgroundColor());
        Assert.assertEquals("切回深色后底座恢复", viewportBgBefore, viewport.getBackgroundColor());
        Assert.assertEquals("切回深色后行内输入恢复", inputBgBefore, input0.getBackgroundColor());
        handle.dispose();
    }

    // ==================== runtime install 路径：dirty/error 桥与草稿保持 ====================

    /**
     * 镜像 {@code ConfigScreen} 真实机制的 {@code SceneThemes.install} runtime 主题切换：
     * dirty 缘色 = 主题 accent、error 压过 dirty = 主题 errorText、跨切换重派生；
     * 列表内容随写回重建行（草稿桥行为零改动的证据），dirty/error 桥不动。
     */
    @Test
    public void installedThemeSwitchKeepsDirtyErrorAndDraftBridge() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("前提：accent 两档不同（dirty 缘色跟随）", dark.accent(), light.accent());
        Assert.assertNotEquals("前提：accent 与 errorText 可辨", dark.accent(), dark.errorText());
        Assert.assertNotEquals("前提：errorText 两档不同", dark.errorText(), light.errorText());

        Signal<SceneTheme> runtimeTheme = Signal.create(dark);
        SceneThemes.install(runtime, runtimeTheme);
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            holder[0] = renderer.render(runtime, spec, adapter);
            return holder[0];
        });
        settle();

        // 真实编辑：写回 4 项合法列表 → dirty（dirty 桥不动，缘色 = 主题 accent）。
        adapter.onFieldEdit("font.sort", Arrays.asList("a", "b", "c", "d"));
        settle();
        SceneNode card = holder[0];
        Assert.assertEquals("dirty 缘色 = 深色 accent（经 FormThemes 映射的 cardBorderDirty）",
                dark.accent(), card.getBorderColor());
        Assert.assertEquals("写回后列表视图跟随 draft",
                4, listViewport(card).__getChildren().size());

        // 非法元素：null 元素 fail-closed 校验 → error 压过 dirty（error 桥不动，缘色 = errorText）。
        adapter.onFieldEdit("font.sort", Arrays.asList("a", null));
        settle();
        Assert.assertEquals("error 压过 dirty = 深色 errorText",
                dark.errorText(), card.getBorderColor());

        runtimeTheme.set(light);
        settle();
        Assert.assertEquals("切换后 error 缘色重派生为浅色 errorText",
                light.errorText(), card.getBorderColor());
        Assert.assertEquals("切换后卡底 = 浅色 GROUP idle tint",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("切换后标题 = 浅色 foreground", light.foreground(),
                card.__getChildren().get(0).__getChildren().get(1).getTextColor());
        Assert.assertEquals("主题切换不丢草稿（DraftBuffer）",
                Arrays.asList("a", null), draft.getDraft("font.sort"));
        Assert.assertEquals("展示读路径 null 元素兜底为 \"\"，不重建出多余行",
                2, listViewport(card).__getChildren().size());
        Assert.assertEquals("兜底行文本为空串", "",
                textInputValue(listViewport(card).__getChildren().get(1)));
        handle.dispose();
    }

    // ==================== 生命周期：卸载回收主题订阅 ====================

    /** 卸载后主题派生 effect 全部回收（施工手册 §3.4：绑定归属当前 Owner）。 */
    @Test
    public void unmountReturnsEffectCountToBaseline() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(SceneTheme.liquidGlassDark()), holder);
        settle();
        Assert.assertTrue("挂载后确有外观绑定 effect",
                ReactiveTestProbe.registeredEffectCount() > baseline);
        handle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载后 effect 数回到基线", baseline,
                ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 源码守卫：Renderer 自身零外观写入 ====================

    /**
     * G15/SimpleList 销账守卫：本渲染器代码内不得出现任何主题/表面/前景直接绑定或旧 chrome 接缝
     * ——表面唯一路径 = {@link FieldShellBinder}（→ FormFieldShell GROUP 主题配方）与已主题化控件
     * 本体（G12），本类只组 Props 消费；{@code theme.listHeight()} 为契约 §4 纯布局入参、
     * {@code ConfigTheme.asFormTheme()} 为 binder 兼容占位形参来源（Number 先例，禁止提前摘除），
     * 均钉死保留。
     */
    @Test
    public void sourceGuardRendererHasZeroAppearanceWrites() throws Exception {
        String code = FieldShellBinderTest.codeWithoutComments(
                new String(Files.readAllBytes(Paths.get(
                        "src/main/java/club/heiqi/config/ui/field/SimpleListFieldRenderer.java")),
                        StandardCharsets.UTF_8));
        String[] banned = {
                // 主题/表面/前景直接绑定不得在本文件重复装配（唯一写入者归 binder/控件；契约 §4）
                "SceneThemes", "SceneSurfaceBinder", "SceneSurfaceStyle", "FormFieldShell",
                // 旧 chrome / 静态色板接缝（契约 §4.2）
                "SceneChromeTokens", "SceneControlChrome", "SceneStateColors", "applyPanelChrome",
                // 表面/前景属性写入者（契约 §4 属性归属表）
                "setBackgroundColor", "setBorderColor", "setBorderWidth", "setCornerRadius",
                "setBackdrop", "setTextColor", "setForeground(", "setSurfaceElevation",
                "__setSurfaceElevation", "__bindAnimatedColor", "__bindAnimatedFloat", "rt.bind",
                // 静态语义色直写（本文件唯一 ConfigTheme 取值只能是 listHeight 布局入参）
                "ConfigTheme.TEXT_COLOR", "ConfigTheme.TITLE_COLOR", "ConfigTheme.MUTED_COLOR",
                "ConfigTheme.ERROR_COLOR", "ConfigTheme.OK_COLOR", "ConfigTheme.DIRTY_COLOR",
                "ConfigTheme.READOUT_BG", "ConfigTheme.VIEWPORT_BG", "ConfigTheme.SURFACE_CONTAINER",
                "ConfigTheme.ROOT_BG",
        };
        for (String token : banned) {
            Assert.assertFalse("守卫：SimpleListFieldRenderer 代码不得出现 " + token, code.contains(token));
        }
        // ConfigTheme 唯一触点 = asFormTheme()（listHeight 布局 + 兼容占位形参），不得扩散到别的取色
        String withoutBridgeCall = code.replace("ConfigTheme.asFormTheme()", "");
        Assert.assertFalse("守卫：ConfigTheme 只允许 asFormTheme() 布局/占位用途",
                withoutBridgeCall.contains("ConfigTheme."));
        Assert.assertTrue("守卫：listHeight() 纯布局常量保留（契约 §4 / Support 衔接要点 3）",
                code.contains("theme.listHeight()"));
        // 装配必经支持层与已主题化控件，不另起路径
        Assert.assertTrue("守卫：字段壳装配必须经 FieldShellBinder（复用 Support helper）",
                code.contains("FieldShellBinder.build("));
        Assert.assertTrue("守卫：列表控件必须复用已主题化的 SceneSimpleList（只组 Props 消费）",
                code.contains("SceneSimpleList.create("));
        Assert.assertTrue("守卫：草稿桥必须复用 DraftListBridge（增删/选择/回环守卫行为零改动）",
                code.contains("DraftListBridge.create("));
    }

    // ==================== 夹具 ====================

    /** 真实装配夹具：{@code runtime.mount} + {@code SceneThemes.withTheme} 页作用域内调 {@code renderer.render}。 */
    private MountHandle mountThroughRenderer(Signal<SceneTheme> pageTheme, SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = renderer.render(runtime, spec, adapter));
            return holder[0];
        });
    }

    /** 多次 flush：草稿桥 applier 的 effect 入队需下一轮应用（与集成测试 settle 同口径）。 */
    private void settle() {
        runtime.flush();
        runtime.flush();
    }

    /** 列表底座（viewport）：card 子树中首个 scrollable 节点。 */
    private static SceneNode listViewport(SceneNode card) {
        SceneNode found = findScrollable(card);
        if (found == null) {
            throw new AssertionError("未找到滚动列表视口");
        }
        return found;
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

    /** 行内输入文本（B2 五节点结构 prefix/highlight/suffix，与集成测试同探针）。 */
    private static String textInputValue(SceneNode row) {
        SceneNode input = row.__getChildren().get(0);
        return input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText()
                + input.__getChildren().get(4).getText();
    }

    private static void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }
}
