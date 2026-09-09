package club.heiqi.uilib.ui.scene.theme;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 语义色公共便捷派生入口（G19/P-02 收编）单测。
 *
 * <p>覆盖：{@code warningText/errorText/danger/successText} 四个入口在两个主题档
 * （liquidGlassDark / liquidGlassLight，另验 solidDark 与 withoutBackdrop 替代档）下的
 * 构造期初值与主题切换重派生；兜底 runtime（未 install）取库默认；install 后新构建跟随
 * runtime 默认档。裁决钉：successText 现值恒等于同档 accent（「success 借道 accent」），
 * danger 与 errorText 是不同 role（底图色 ≠ 文本色，防误互相顶替）。</p>
 *
 * <p>P-04 口径：所有「跟随」断言均以两档 {@code assertNotEquals} 为前提，且经构造期真实
 * 装配（mount builder 内取信号）而非离线拼值。</p>
 */
public class SceneThemesSemanticColorTest {

    /** 一次构建捕获的四路语义派生信号。 */
    private static final class Captured {
        private ReadableSignal<Integer> warning;
        private ReadableSignal<Integer> error;
        private ReadableSignal<Integer> danger;
        private ReadableSignal<Integer> success;
        private ReadableSignal<Integer> accent;
    }

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    /** 在 mount builder（构造期）内捕获四路派生信号；{@code scoped} 为 null 时不加局部主题。 */
    private static Captured captureEntries(SceneRuntime rt, SceneNode root,
                                           ReadableSignal<SceneTheme> scoped) {
        final Captured[] holder = new Captured[1];
        rt.mount(root, () -> {
            final Captured captured = new Captured();
            Runnable build = () -> {
                captured.warning = SceneThemes.warningText(rt);
                captured.error = SceneThemes.errorText(rt);
                captured.danger = SceneThemes.danger(rt);
                captured.success = SceneThemes.successText(rt);
                captured.accent = SceneThemes.accent(rt);
            };
            if (scoped == null) {
                build.run();
            } else {
                SceneThemes.withTheme(scoped, build);
            }
            holder[0] = captured;
            return new SceneNode();
        });
        return holder[0];
    }

    private static void assertMatchesTheme(Captured captured, SceneTheme theme, String stage) {
        Assert.assertEquals(stage + "：warningText 入口 = 主题槽值",
                Integer.valueOf(theme.warningText()), captured.warning.get());
        Assert.assertEquals(stage + "：errorText 入口 = 主题槽值",
                Integer.valueOf(theme.errorText()), captured.error.get());
        Assert.assertEquals(stage + "：danger 入口 = 主题槽值",
                Integer.valueOf(theme.danger()), captured.danger.get());
        Assert.assertEquals(stage + "：successText 入口 = 主题 accent（借道裁决）",
                Integer.valueOf(theme.accent()), captured.success.get());
        Assert.assertEquals(stage + "：successText 与 accent 入口恒等值",
                captured.accent.get(), captured.success.get());
    }

    /** 四槽在两档间必须互异（否则「切换跟随」不可证，P-04 前提）。 */
    private static void assertTiersDiffer(SceneTheme dark, SceneTheme light) {
        Assert.assertNotEquals("测试前提：两档 warningText 不同", dark.warningText(), light.warningText());
        Assert.assertNotEquals("测试前提：两档 errorText 不同", dark.errorText(), light.errorText());
        Assert.assertNotEquals("测试前提：两档 danger 不同", dark.danger(), light.danger());
        Assert.assertNotEquals("测试前提：两档 accent 不同", dark.accent(), light.accent());
    }

    @Test
    public void semanticEntriesMatchThemeSlotsAcrossTiersAtConstruction() {
        SceneTheme[] tiers = {
                SceneTheme.liquidGlassDark(),
                SceneTheme.liquidGlassLight(),
                SceneTheme.solidDark(),
                SceneTheme.liquidGlassDark().withoutBackdrop(),
        };
        for (SceneTheme tier : tiers) {
            SceneRuntime rt = new SceneRuntime();
            Captured captured = captureEntries(rt, new SceneNode(), Signal.create(tier));
            // 构造期初值即正确（Effect.untrack 预读），无需 flush。
            assertMatchesTheme(captured, tier, tier.toString());
        }
    }

    @Test
    public void semanticEntriesFollowThemeSwitchWithoutRebuild() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        assertTiersDiffer(dark, light);

        SceneRuntime rt = new SceneRuntime();
        Signal<SceneTheme> page = Signal.create(dark);
        Captured captured = captureEntries(rt, new SceneNode(), page);
        assertMatchesTheme(captured, dark, "初始深色档");

        final ReadableSignal<Integer> warningSignal = captured.warning;
        page.set(light);
        rt.flush();

        assertMatchesTheme(captured, light, "切换浅色档");
        Assert.assertSame("派生信号对象跨切换复用（重派生值而非重建链路）", warningSignal, captured.warning);
        Assert.assertEquals("信号对象身份不变而值已随主题重派生",
                Integer.valueOf(light.warningText()), warningSignal.get());
    }

    @Test
    public void fallbackRuntimeWithoutInstallYieldsLibraryDefault() {
        SceneRuntime rt = new SceneRuntime();
        Captured captured = captureEntries(rt, new SceneNode(), null);
        assertMatchesTheme(captured, SceneThemes.DEFAULT, "兜底 runtime（未 install）");
    }

    @Test
    public void installedRuntimeDefaultDrivesNewConstructionsAndSwitch() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        assertTiersDiffer(dark, light);

        SceneRuntime rt = new SceneRuntime();
        Signal<SceneTheme> installed = Signal.create(dark);
        SceneThemes.install(rt, installed);
        Captured captured = captureEntries(rt, new SceneNode(), null);
        assertMatchesTheme(captured, dark, "install 深色档");

        installed.set(light);
        rt.flush();
        assertMatchesTheme(captured, light, "换 runtime 默认档");
    }

    /**
     * danger 与 errorText 是不同 role：入口值互异且各归各档（防止未来「对比度治理」把
     * danger 误接成 errorText 或反向）；数值钉与 {@link SceneTheme} 各档常量同源。
     */
    @Test
    public void dangerAndErrorTextAreDistinctRoles() {
        SceneRuntime rt = new SceneRuntime();
        Captured dark = captureEntries(rt, new SceneNode(), Signal.create(SceneTheme.liquidGlassDark()));
        Captured light = captureEntries(rt, new SceneNode(), Signal.create(SceneTheme.liquidGlassLight()));

        Assert.assertEquals("深色档 danger = 0xFF7F1D1D（底图系暗红）",
                Integer.valueOf(0xFF7F1D1D), dark.danger.get());
        Assert.assertEquals("深色档 errorText = 0xFFFFB4AB（文本系亮红）",
                Integer.valueOf(0xFFFFB4AB), dark.error.get());
        Assert.assertEquals("浅色档 danger = 0xFFB3261E",
                Integer.valueOf(0xFFB3261E), light.danger.get());
        Assert.assertEquals("浅色档 errorText = 0xFF8C1D18",
                Integer.valueOf(0xFF8C1D18), light.error.get());
        Assert.assertEquals("深色档 warningText = 0xFFFBBF24",
                Integer.valueOf(0xFFFBBF24), dark.warning.get());
        Assert.assertEquals("浅色档 warningText = 0xFF8B5000",
                Integer.valueOf(0xFF8B5000), light.warning.get());

        Assert.assertNotEquals("danger ≠ errorText（底图色禁止当文本色）",
                dark.danger.get(), dark.error.get());
        Assert.assertNotEquals("danger ≠ warningText", dark.danger.get(), dark.warning.get());
        Assert.assertNotEquals("两档 danger 互异（切换可证）",
                dark.danger.get(), light.danger.get());
    }

    /**
     * successText 委托 accent 的裁决钉：两档下 success 与 accent 信号逐值相等，
     * 且 success 可随主题切换变化（借道≠静态常量）。
     */
    @Test
    public void successTextDelegatesToAccentAcrossTiers() {
        SceneRuntime rt = new SceneRuntime();
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Signal<SceneTheme> page = Signal.create(dark);
        Captured captured = captureEntries(rt, new SceneNode(), page);

        Assert.assertEquals("深色档 success == accent", dark.accent(), captured.success.get().intValue());
        page.set(light);
        rt.flush();
        Assert.assertEquals("浅色档 success == accent（切换后仍恒等）",
                Integer.valueOf(light.accent()), captured.success.get());
        Assert.assertNotEquals("success 借道的两档值互异（证明真实跟随主题）",
                Integer.valueOf(dark.accent()), captured.success.get());
    }
}
