package club.heiqi.uilib.ui.scene.theme;

import java.util.function.UnaryOperator;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 角色配方字段级派生入口（{@link SceneThemes#derivedSurface}）单测。
 *
 * <p>覆盖：构造期初值 = override(主题基线)；主题切换与 override 内业务信号是两个独立失效源，
 * 各自触发重算且互不吞并；未覆盖字段随主题、覆盖字段恒定；派生不污染基线配方；返回类型是
 * 可显式回收的 {@link Computed}；与手写 {@code Computed.create} 样板在相同输入序列下逐值等价。</p>
 *
 * <p>本入口收编「主题基线 + 局部覆盖」的公共写法，首个生产改造点是
 * {@code ChatToolbarAppearance.recipe}（聊天工具栏动作按钮）。</p>
 */
public class SceneThemesDerivedSurfaceTest {

    private static final SceneTheme.Role ROLE = SceneTheme.Role.GROUP;
    private static final int PATCH_RADIUS = 7;
    private static final int PATCH_TRANSITION = 40;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    /** 覆盖圆角与过渡时长的纯函数变换；其余字段随基线。 */
    private static SceneSurfaceStyle patch(SceneSurfaceStyle style) {
        return style.toBuilder()
                .cornerRadius(PATCH_RADIUS)
                .transitionMillis(PATCH_TRANSITION)
                .build();
    }

    /** 一次构造捕获的派生信号与同角色基线信号（对照用）。 */
    private static final class Probe {
        private Computed<SceneSurfaceStyle> derived;
        private ReadableSignal<SceneSurfaceStyle> baseline;
    }

    /** 在 mount builder（构造期）内捕获；{@code scoped} 为 null 时不加局部主题。 */
    private static Probe capture(SceneRuntime rt, ReadableSignal<SceneTheme> scoped,
                                 UnaryOperator<SceneSurfaceStyle> override) {
        final Probe[] holder = new Probe[1];
        rt.mount(new SceneNode(), () -> {
            Runnable build = () -> {
                Probe probe = new Probe();
                probe.baseline = SceneThemes.surface(rt, ROLE);
                probe.derived = SceneThemes.derivedSurface(rt, ROLE, override);
                holder[0] = probe;
            };
            if (scoped == null) {
                build.run();
            } else {
                SceneThemes.withTheme(scoped, build);
            }
            return new SceneNode();
        });
        return holder[0];
    }

    /** ① 构造期初值 = override(基线)；覆盖字段生效，未覆盖字段逐值等于基线。 */
    @Test
    public void constructionInitialValueIsOverrideOfBaseline() {
        SceneRuntime rt = new SceneRuntime();
        Probe probe = capture(rt, Signal.create(SceneTheme.liquidGlassDark()),
                SceneThemesDerivedSurfaceTest::patch);

        SceneSurfaceStyle baseline = probe.baseline.get();
        Assert.assertNotEquals("测试前提：基线圆角本就 ≠ 覆盖值（否则覆盖不可分辨）",
                baseline.getCornerRadius(), PATCH_RADIUS);
        Assert.assertNotEquals("测试前提：基线过渡时长本就 ≠ 覆盖值",
                baseline.getTransitionMillis(), PATCH_TRANSITION);

        Assert.assertEquals("覆盖字段：圆角", PATCH_RADIUS, probe.derived.get().getCornerRadius());
        Assert.assertEquals("覆盖字段：过渡时长", PATCH_TRANSITION,
                probe.derived.get().getTransitionMillis());
        Assert.assertEquals("未覆盖字段：idle 底色随基线",
                baseline.getIdle().getTint(), probe.derived.get().getIdle().getTint());
        Assert.assertEquals("未覆盖字段：idle 缘色随基线",
                baseline.getIdle().getEdge(), probe.derived.get().getIdle().getEdge());
        Assert.assertEquals("未覆盖字段：边框宽随基线",
                baseline.getBorderWidth(), probe.derived.get().getBorderWidth());
        Assert.assertEquals("未覆盖字段：滤镜随基线",
                baseline.getBackdrop(), probe.derived.get().getBackdrop());
    }

    /** ② 主题切换触发重算：未覆盖字段随新档，覆盖字段恒定。 */
    @Test
    public void themeSwitchReDerivesWhilePatchedFieldsStayPatched() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：两档 GROUP idle 底色必须互异",
                dark.surface(ROLE).getIdle().getTint(), light.surface(ROLE).getIdle().getTint());

        SceneRuntime rt = new SceneRuntime();
        Signal<SceneTheme> page = Signal.create(dark);
        Probe probe = capture(rt, page, SceneThemesDerivedSurfaceTest::patch);
        Assert.assertEquals("初始档：未覆盖字段 = 深色档",
                dark.surface(ROLE).getIdle().getTint(), probe.derived.get().getIdle().getTint());

        page.set(light);
        rt.flush();

        Assert.assertEquals("切换后：未覆盖字段 = 浅色档",
                light.surface(ROLE).getIdle().getTint(), probe.derived.get().getIdle().getTint());
        Assert.assertEquals("切换后：覆盖的圆角仍为覆盖值",
                PATCH_RADIUS, probe.derived.get().getCornerRadius());
        Assert.assertEquals("切换后：覆盖的过渡时长仍为覆盖值",
                PATCH_TRANSITION, probe.derived.get().getTransitionMillis());
        Assert.assertEquals("对照：同角色基线信号也随主题切换",
                light.surface(ROLE).getCornerRadius(), probe.baseline.get().getCornerRadius());
    }

    /** ③ override 内读取的业务信号自动成为失效源。 */
    @Test
    public void businessSignalInsideOverrideBecomesInvalidationSource() {
        SceneRuntime rt = new SceneRuntime();
        Signal<Integer> radius = Signal.create(Integer.valueOf(4));
        Probe probe = capture(rt, Signal.create(SceneTheme.liquidGlassDark()),
                style -> style.toBuilder().cornerRadius(radius.get().intValue()).build());

        Assert.assertEquals("构造期初值取业务信号当前值", 4, probe.derived.get().getCornerRadius());

        radius.set(Integer.valueOf(21));
        rt.flush();

        Assert.assertEquals("业务信号变化触发重算", 21, probe.derived.get().getCornerRadius());
        Assert.assertEquals("基线未被业务覆盖污染",
                SceneTheme.liquidGlassDark().surface(ROLE).getCornerRadius(),
                probe.baseline.get().getCornerRadius());
    }

    /** ④ 主题与业务信号两个独立失效源互不吞并：任一变后值都正确。 */
    @Test
    public void themeAndBusinessSignalsAreIndependentSources() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneRuntime rt = new SceneRuntime();
        Signal<SceneTheme> page = Signal.create(dark);
        Signal<Integer> radius = Signal.create(Integer.valueOf(4));
        Probe probe = capture(rt, page,
                style -> style.toBuilder().cornerRadius(radius.get().intValue()).build());

        Assert.assertEquals(4, probe.derived.get().getCornerRadius());
        Assert.assertEquals(dark.surface(ROLE).getIdle().getTint(),
                probe.derived.get().getIdle().getTint());

        page.set(light);
        rt.flush();
        Assert.assertEquals("仅主题变：业务覆盖值保留", 4, probe.derived.get().getCornerRadius());
        Assert.assertEquals("仅主题变：未覆盖字段换档",
                light.surface(ROLE).getIdle().getTint(), probe.derived.get().getIdle().getTint());

        radius.set(Integer.valueOf(9));
        rt.flush();
        Assert.assertEquals("仅业务变：覆盖值更新", 9, probe.derived.get().getCornerRadius());
        Assert.assertEquals("仅业务变：主题字段保持浅色档",
                light.surface(ROLE).getIdle().getTint(), probe.derived.get().getIdle().getTint());
    }

    /** ⑤ 返回类型是可显式回收的 Computed：dispose 后停止重算（自持 Owner 场景的回收路径）。 */
    @Test
    public void returnedComputedCanBeDisposedAndStopsReDeriving() {
        SceneRuntime rt = new SceneRuntime();
        Signal<SceneTheme> page = Signal.create(SceneTheme.liquidGlassDark());
        Probe probe = capture(rt, page, SceneThemesDerivedSurfaceTest::patch);
        int frozenRadius = probe.derived.get().getCornerRadius();

        probe.derived.dispose();
        page.set(SceneTheme.liquidGlassLight());
        rt.flush();

        Assert.assertEquals("dispose 后覆盖字段不再重算",
                frozenRadius, probe.derived.get().getCornerRadius());
        Assert.assertNotEquals("对照：同角色基线信号仍在跟随主题",
                probe.derived.get().getIdle().getTint(), probe.baseline.get().getIdle().getTint());
    }

    /** ⑥ 与手写 Computed 样板等价：同一输入序列下逐值相等（入口只是样板收编，不改语义）。 */
    @Test
    public void matchesHandWrittenComputedSample() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneRuntime rt = new SceneRuntime();
        Signal<SceneTheme> page = Signal.create(dark);
        Signal<Integer> radius = Signal.create(Integer.valueOf(4));

        final Computed<SceneSurfaceStyle>[] holder = new Computed[2];
        rt.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(page, () -> {
                ReadableSignal<SceneSurfaceStyle> baseline = SceneThemes.surface(rt, ROLE);
                // 改造前 ChatToolbarAppearance.recipe 的形状：外部基线信号 + 无初值 Computed。
                holder[0] = Computed.create(() ->
                        baseline.get().toBuilder().cornerRadius(radius.get().intValue()).build());
                holder[1] = SceneThemes.derivedSurface(rt, ROLE,
                        style -> style.toBuilder().cornerRadius(radius.get().intValue()).build());
            });
            return new SceneNode();
        });

        Assert.assertNotNull("derivedSurface 注入构造期初值（手写无初值样板首帧前为 null）",
                holder[1].get());

        rt.flush();
        Assert.assertEquals("flush 后：覆盖字段一致",
                holder[0].get().getCornerRadius(), holder[1].get().getCornerRadius());

        page.set(light);
        rt.flush();
        Assert.assertEquals("主题切换后一致",
                holder[0].get().getIdle().getTint(), holder[1].get().getIdle().getTint());
        Assert.assertEquals("主题切换后未覆盖字段一致",
                holder[0].get().getTransitionMillis(), holder[1].get().getTransitionMillis());

        radius.set(Integer.valueOf(13));
        rt.flush();
        Assert.assertEquals("业务信号变化后一致",
                holder[0].get().getCornerRadius(), holder[1].get().getCornerRadius());
    }
}
