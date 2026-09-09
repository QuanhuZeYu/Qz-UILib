package club.heiqi.config.ui.field;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

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
 * {@link BooleanFieldRenderer} 的 G15/Boolean 主题跟随证据与源码守卫。
 *
 * <p>本实例的装配边界：字段卡片表面与语义色由 {@link FieldShellBinder}（G15/Support）→
 * {@code FormFieldShell} theme-aware 默认路径消费来源主题（G11/G02），Toggle 外观由
 * {@code SceneToggle}（G05）自持；本测试沿<b>真实渲染器路径</b>
 * （{@code renderer.render → FieldShellBinder.build → FormFieldShell.build}）验证
 * 「默认路径 = 来源主题配方」与「换主题 → 颜色变」（P-04 口径：切换后更新 +
 * 两档配方值不同的 assertNotEquals 前提 + 真实装配构造：{@code mount + withTheme}
 * 页作用域，以及镜像 {@code ConfigScreen} 的 {@code SceneThemes.install} runtime 路径）。</p>
 */
public class BooleanFieldRendererThemeTest {

    private SceneRuntime runtime;
    private ConfigSchema schema;
    private Authority authority;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private BooleanFieldRenderer renderer;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        schema = ConfigSchema.builder("t")
                .section("server")
                    .bool("debug").defaultValue(false).label("Debug").helper("debug mode").build()
                .endSection()
                .build();
        authority = Authority.load(new File("nonexistent-booltheme.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new BooleanFieldRenderer();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 默认路径：真实渲染器装配 = 来源主题 GROUP/INDICATOR 配方 ====================

    /** 默认路径（mount + withTheme 真实装配）：卡片表面/标题/helper 与 Toggle track 全随来源主题。 */
    @Test
    public void defaultPathFollowsSourceThemeThroughRealRendererAssembly() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        FieldSpec spec = schema.field("server.debug");
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(dark), spec, holder);
        runtime.flush();

        SceneNode card = holder[0];
        SceneSurfaceStyle group = dark.surface(SceneTheme.Role.GROUP);
        Assert.assertEquals("卡底色 = 来源主题 GROUP idle tint", group.getIdle().getTint(),
                card.getBackgroundColor());
        Assert.assertEquals("卡缘色 = GROUP idle edge", group.getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("卡圆角 = GROUP 配方圆角", group.getCornerRadius(), card.getCornerRadius());
        Assert.assertEquals("卡边框宽 = GROUP 配方边框宽", group.getBorderWidth(), card.getBorderWidth());
        Assert.assertNotNull("GROUP 语义表面装有滤镜（背景采样）", card.getBackdrop());

        SceneNode title = card.__getChildren().get(0).__getChildren().get(1);
        Assert.assertEquals("标题前景 = 主题 foreground", dark.foreground(), title.getTextColor());
        SceneNode helper = card.__getChildren().get(1);
        Assert.assertEquals("helper 前景 = 主题 mutedForeground",
                dark.mutedForeground(), helper.getTextColor());

        SceneNode track = findTrack(card);
        Assert.assertEquals("track（off 态）底色 = 主题 INDICATOR idle tint",
                dark.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), track.getBackgroundColor());
        handle.dispose();
    }

    // ==================== P-04：换主题 → 颜色变（切换后更新 + 两档前提） ====================

    /** 页面局部主题切换：卡片表面、标题、helper、track 全重派生，节点身份保持，effect 不增。 */
    @Test
    public void themeSwitchUpdatesCardTitleHelperAndTrackViaRenderer() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        // 两档前提：所断言的各分量在深/浅档互不相同，否则切换不传播、断言无意义（Computed 值记忆化）。
        Assert.assertNotEquals("前提：GROUP idle tint 两档不同",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(),
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint());
        Assert.assertNotEquals("前提：GROUP idle edge 两档不同",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getEdge(),
                light.surface(SceneTheme.Role.GROUP).getIdle().getEdge());
        Assert.assertNotEquals("前提：INDICATOR idle tint 两档不同",
                dark.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(),
                light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint());
        Assert.assertNotEquals("前提：foreground 两档不同", dark.foreground(), light.foreground());
        Assert.assertNotEquals("前提：mutedForeground 两档不同",
                dark.mutedForeground(), light.mutedForeground());

        FieldSpec spec = schema.field("server.debug");
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(pageTheme, spec, holder);
        runtime.flush();

        SceneNode card = holder[0];
        SceneNode title = card.__getChildren().get(0).__getChildren().get(1);
        SceneNode helper = card.__getChildren().get(1);
        SceneNode toggleRoot = findToggleRoot(card);
        SceneNode track = toggleRoot.__getChildren().get(0);
        int bgBefore = card.getBackgroundColor();
        int edgeBefore = card.getBorderColor();
        int titleBefore = title.getTextColor();
        int trackBefore = track.getBackgroundColor();
        Assert.assertEquals("前置：静默底色 = 深色 GROUP idle tint",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(), bgBefore);
        Assert.assertEquals("前置：静默缘色 = 深色 GROUP idle edge",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getEdge(), edgeBefore);

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertNotEquals("切换后卡底色变化", bgBefore, card.getBackgroundColor());
        Assert.assertEquals("切换后卡底色 = 浅色 GROUP idle tint",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertNotEquals("切换后卡缘色变化", edgeBefore, card.getBorderColor());
        Assert.assertEquals("切换后卡缘色 = 浅色 GROUP idle edge",
                light.surface(SceneTheme.Role.GROUP).getIdle().getEdge(), card.getBorderColor());
        Assert.assertNotEquals("切换后标题前景变化", titleBefore, title.getTextColor());
        Assert.assertEquals("切换后标题前景 = 浅色 foreground", light.foreground(), title.getTextColor());
        Assert.assertEquals("切换后 helper = 浅色 mutedForeground",
                light.mutedForeground(), helper.getTextColor());
        Assert.assertNotEquals("切换后 track 底色变化", trackBefore, track.getBackgroundColor());
        Assert.assertEquals("切换后 track = 浅色 INDICATOR idle tint",
                light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), track.getBackgroundColor());

        Assert.assertSame("换肤不重建：卡片身份不变", card, holder[0]);
        Assert.assertSame("换肤不重建：标题节点身份不变", title,
                holder[0].__getChildren().get(0).__getChildren().get(1));
        Assert.assertSame("换肤不重建：toggle 根身份不变", toggleRoot,
                findToggleRoot(holder[0]));
        Assert.assertSame("换肤不重建：track 身份不变", track,
                findToggleRoot(holder[0]).__getChildren().get(0));
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    /** 镜像 ConfigScreen 真实机制：{@code SceneThemes.install} runtime 主题切换，dirty/on 语义跨切换保持并重派生。 */
    @Test
    public void runtimeInstalledThemeSwitchKeepsDraftAndRederivesDirtyEdgeAndOnTint() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("前提：accent 两档不同（dirty 缘色/选中 tint 跟随）",
                dark.accent(), light.accent());

        Signal<SceneTheme> runtimeTheme = Signal.create(dark);
        SceneThemes.install(runtime, runtimeTheme);
        FieldSpec spec = schema.field("server.debug");
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            holder[0] = renderer.render(runtime, spec, adapter);
            return holder[0];
        });
        runtime.flush();

        // 编辑草稿：BOOLEAN 置 true → dirty + on 双态。
        adapter.onFieldEdit("server.debug", Boolean.TRUE);
        runtime.flush();
        SceneNode card = holder[0];
        SceneNode track = findTrack(card);
        Assert.assertEquals("dirty 缘色 = 深色 accent（经 FormThemes 映射的 cardBorderDirty）",
                dark.accent(), card.getBorderColor());
        Assert.assertEquals("on 态 track = 深色 accent 选中 tint",
                selectedTint(dark.accent()), track.getBackgroundColor());

        runtimeTheme.set(light);
        runtime.flush();

        Assert.assertEquals("切换后 dirty 缘色重派生为浅色 accent", light.accent(), card.getBorderColor());
        Assert.assertEquals("切换后 on 态 track tint 跟随浅色 accent",
                selectedTint(light.accent()), track.getBackgroundColor());
        Assert.assertEquals("切换后卡底色 = 浅色 GROUP idle tint",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("主题切换不丢草稿（draftSignal）",
                Boolean.TRUE, adapter.draftSignal("server.debug").get());
        Assert.assertEquals("主题切换不丢草稿（DraftBuffer）",
                Boolean.TRUE, draft.getDraft("server.debug"));
        handle.dispose();
    }

    // ==================== 生命周期：卸载回收主题订阅 ====================

    /** 卸载后主题派生 effect 全部回收（施工手册 §3.4：绑定归属当前 Owner）。 */
    @Test
    public void unmountReturnsEffectCountToBaseline() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        FieldSpec spec = schema.field("server.debug");
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(SceneTheme.liquidGlassDark()), spec, holder);
        runtime.flush();
        Assert.assertTrue("挂载后确有外观绑定 effect",
                ReactiveTestProbe.registeredEffectCount() > baseline);
        handle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载后 effect 数回到基线", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 源码守卫：Renderer 自身零外观写入 ====================

    /**
     * G15/Boolean 销账守卫：{@code BooleanFieldRenderer} 代码内不得出现任何显式主题快照、
     * 旧 chrome/静态色接缝或表面/前景写入——装配只经 {@link FieldShellBinder}（表面归属
     * binder→FormFieldShell），控件外观只归 {@code SceneToggle} 自持。
     */
    @Test
    public void sourceGuardRendererHasZeroAppearanceWrites() throws Exception {
        String code = FieldShellBinderTest.codeWithoutComments(
                new String(Files.readAllBytes(
                        Paths.get("src/main/java/club/heiqi/config/ui/field/BooleanFieldRenderer.java")),
                        StandardCharsets.UTF_8));
        String[] banned = {
                // 显式旧主题 / 快照喂装配（G15/Theme 书面警示 + 契约 §3）
                "ConfigTheme", "asFormTheme", "FormTheme",
                // 旧 chrome / 静态色板接缝（契约 §4.2）
                "SceneChromeTokens", "SceneControlChrome", "SceneStateColors",
                // 主题与表面绑定不得在本文件重复装配（唯一写入者归 binder/控件；契约 §4）
                "SceneThemes", "SceneSurfaceBinder", "FormFieldShell", "SceneSurfaceStyle",
                // 属性归属表中的表面/前景写入者（契约 §4）
                "setBackgroundColor", "setBorderColor", "setBorderWidth", "setCornerRadius",
                "setBackdrop", "setTextColor", "setForeground(", "setSurfaceElevation",
                "__bindAnimatedColor", "__bindAnimatedFloat",
        };
        for (String token : banned) {
            Assert.assertFalse("守卫：BooleanFieldRenderer 代码不得出现 " + token, code.contains(token));
        }
        Assert.assertTrue("守卫：字段壳装配必须经 FieldShellBinder（复用 Support helper，不重复绑定）",
                code.contains("FieldShellBinder.build("));
        Assert.assertTrue("守卫：控件必须复用已主题化的 SceneToggle（只挂载不加工）",
                code.contains("SceneToggle.create("));
    }

    // ==================== 夹具 ====================

    /** 真实渲染器装配夹具：{@code runtime.mount} + {@code SceneThemes.withTheme} 页作用域内调 {@code renderer.render}。 */
    private MountHandle mountThroughRenderer(Signal<SceneTheme> pageTheme, FieldSpec spec, SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = renderer.render(runtime, spec, adapter));
            return holder[0];
        });
    }

    /** Toggle 根：跳过 header（index 0，同为 2 子 dot+title），找含 track+label 两子的控件根。 */
    private static SceneNode findToggleRoot(SceneNode card) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode c = card.__getChildren().get(i);
            if (c.__getChildren().size() == 2) {
                return c;
            }
        }
        Assert.fail("应找到 Toggle 控件根");
        return null;
    }

    /** Toggle track（控件根首子）。 */
    private static SceneNode findTrack(SceneNode card) {
        return findToggleRoot(card).__getChildren().get(0);
    }

    /** 选中态 tint：{@code SceneThemes.SELECTED_TINT_ALPHA}(0x59) + 主题 accent RGB（G05 既有口径）。 */
    private static int selectedTint(int accent) {
        return (0x59 << 24) | (accent & 0x00FFFFFF);
    }
}
