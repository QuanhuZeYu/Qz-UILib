package club.heiqi.config.ui.theme;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.form.FormThemes;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link ConfigTheme} G15/Theme 双路径契约测试。
 *
 * <p><b>默认路径</b>（{@link ConfigTheme#asFormTheme(SceneRuntime)}）：委托 {@link FormThemes}
 * 既有桥，覆盖「来源主题各语义色逐项映射」「页面局部主题继承」「主题切换自动重派生且订阅不增长」
 * 「runtime 默认安装」「无 Owner/无 runtime 默认时安全回落库默认」「卸载回收订阅」
 * 「null runtime 显式拒绝」。</p>
 *
 * <p><b>显式旧路径</b>（无参 {@link ConfigTheme#asFormTheme()} 与静态常量）：像素级锁定旧值、
 * 缓存恒同一对象、不创建订阅；并断言主题切换后旧路径<b>不感知</b>（与默认路径分叉），
 * 与 G11 双路径同构。</p>
 *
 * <p>浅色/深色档在颜色分量上确有差异（台账 P-04 口径：不用深色同值做切换判据），
 * 切换用例以 {@code liquidGlassLight} 为切换目标并逐项断言新主题值。</p>
 */
public class ConfigThemeTest {

    /** 测试用可变持有器：避免泛型数组的 unchecked 警告。 */
    private static final class Holder<T> {
        private T value;
    }

    /** 每用例独立 runtime，避免跨用例串色；tearDown 统一 dispose。 */
    private final List<SceneRuntime> runtimes = new ArrayList<SceneRuntime>();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        for (SceneRuntime runtime : runtimes) {
            runtime.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    private SceneRuntime newRuntime() {
        SceneRuntime rt = new SceneRuntime();
        runtimes.add(rt);
        return rt;
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /**
     * 逐项断言「默认路径各语义色 = 来源主题对应值」：与 {@link FormThemes} 冻结口径一致——
     * 卡片表面取 GROUP idle tint/edge，脏态与错误边框取 accent/errorText，文本语义取
     * foreground/mutedForeground/errorText，页底色按 PANEL tint 不透明化规则派生，
     * 布局与字号分量恒取既有常量（主题不接管几何）。
     */
    private static void assertMapsThemeItemByItem(SceneTheme source, FormTheme mapped) {
        SceneSurfaceStyle group = source.surface(SceneTheme.Role.GROUP);
        int panelTint = source.surface(SceneTheme.Role.PANEL).getIdle().getTint();
        int expectedPageBg = (panelTint >>> 24) == 0xFF ? panelTint : SceneTheme.FALLBACK_BG;

        Assert.assertEquals("cardBg 取来源主题 GROUP idle tint", group.getIdle().getTint(), mapped.cardBg());
        Assert.assertEquals("cardBorder 取来源主题 GROUP idle edge", group.getIdle().getEdge(), mapped.cardBorder());
        Assert.assertEquals("cardBorderDirty 取来源主题 accent", source.accent(), mapped.cardBorderDirty());
        Assert.assertEquals("cardBorderError 取来源主题 errorText", source.errorText(), mapped.cardBorderError());
        Assert.assertEquals("textColor 取来源主题 foreground", source.foreground(), mapped.textColor());
        Assert.assertEquals("mutedColor 取来源主题 mutedForeground", source.mutedForeground(), mapped.mutedColor());
        Assert.assertEquals("errorColor 取来源主题 errorText", source.errorText(), mapped.errorColor());
        Assert.assertEquals("dirtyColor 取来源主题 accent", source.accent(), mapped.dirtyColor());
        Assert.assertEquals("titleColor 取来源主题 foreground", source.foreground(), mapped.titleColor());
        Assert.assertEquals("rootBg 按 PANEL tint 不透明化派生", expectedPageBg, mapped.rootBg());
        Assert.assertEquals("viewportBg 与 rootBg 同源", expectedPageBg, mapped.viewportBg());
        Assert.assertEquals("页底色必须不透明可读", 0xFF, alpha(mapped.rootBg()));

        FormTheme legacy = FormTheme.defaultDark();
        Assert.assertEquals("cardRadius 用既有 RADIUS_LG", SceneChromeTokens.RADIUS_LG, mapped.cardRadius());
        Assert.assertEquals("cardPad 用既有 PAD_LG", SceneChromeTokens.PAD_LG, mapped.cardPad());
        Assert.assertEquals("fieldGap 用既有 GAP_MD", SceneChromeTokens.GAP_MD, mapped.fieldGap());
        Assert.assertEquals("inputHeight 用既有 INPUT_HEIGHT", SceneChromeTokens.INPUT_HEIGHT, mapped.inputHeight());
        Assert.assertEquals("listHeight 不随主题变化", legacy.listHeight(), mapped.listHeight());
        Assert.assertEquals("fontLabel 不随主题变化", legacy.fontLabel(), mapped.fontLabel());
        Assert.assertEquals("fontHelper 不随主题变化", legacy.fontHelper(), mapped.fontHelper());
        Assert.assertEquals("fontError 不随主题变化", legacy.fontError(), mapped.fontError());

        Assert.assertEquals("整包映射等于 FormThemes 既有桥的冻结口径",
                FormThemes.of(source), mapped);
    }

    // ==================== 默认路径：来源主题逐项映射 ====================

    /** 构建期、无局部/运行时主题：默认路径取库默认液态玻璃档的映射，逐项等于主题对应值。 */
    @Test
    public void defaultPathWithoutScopeThemeMapsLibraryDefaultItemByItem() {
        SceneRuntime rt = newRuntime();
        Holder<FormTheme> captured = new Holder<FormTheme>();

        rt.mount(new SceneNode(), () -> {
            captured.value = ConfigTheme.asFormTheme(rt).get();
            return new SceneNode();
        });

        Assert.assertNotNull("构建期即可读到非 null 初值", captured.value);
        assertMapsThemeItemByItem(SceneThemes.DEFAULT, captured.value);
    }

    /** 页面局部主题作用域内：默认路径逐项取页面主题对应值（浅色档，与深色对照组不同）。 */
    @Test
    public void defaultPathUnderLocalPageThemeMapsPageThemeItemByItem() {
        SceneRuntime rt = newRuntime();
        SceneTheme page = SceneTheme.liquidGlassLight();
        Holder<FormTheme> inside = new Holder<FormTheme>();
        Holder<FormTheme> outside = new Holder<FormTheme>();

        rt.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(page), () -> inside.value = ConfigTheme.asFormTheme(rt).get());
            outside.value = ConfigTheme.asFormTheme(rt).get();
            return new SceneNode();
        });

        assertMapsThemeItemByItem(page, inside.value);
        assertMapsThemeItemByItem(SceneThemes.DEFAULT, outside.value);
        // 对照组：深/浅档颜色分量确实不同（P-04：切换判据不落在同值分量上）
        Assert.assertNotEquals("浅色档与库默认档映射必须不同", inside.value, outside.value);
        Assert.assertNotEquals("脏态色在深/浅档不同（accent 分叉）",
                inside.value.dirtyColor(), outside.value.dirtyColor());
    }

    /** 默认路径的深色档卡片底色是玻璃叠色 tint，不回退成旧显式不透明值（默认外观确已改变）。 */
    @Test
    public void defaultPathUsesGlassGroupTintNotLegacyOpaqueCard() {
        SceneRuntime rt = newRuntime();
        Holder<FormTheme> captured = new Holder<FormTheme>();

        rt.mount(new SceneNode(), () -> {
            captured.value = ConfigTheme.asFormTheme(rt).get();
            return new SceneNode();
        });

        Assert.assertTrue("默认档 GROUP idle tint 应为半透明玻璃叠色",
                alpha(captured.value.cardBg()) < 0xFF);
        Assert.assertNotEquals("默认卡底不得被「修回」旧显式不透明值",
                0xFF2B2930, captured.value.cardBg());
        Assert.assertEquals("旧显式入口仍是旧不透明值（两路径并存不互斥）",
                0xFF2B2930, ConfigTheme.asFormTheme().cardBg());
    }

    // ==================== 主题切换：默认路径重派生、旧路径不感知 ====================

    /** 页面主题切换：默认路径信号自动重派生（逐项=新主题值、订阅不增长），旧显式路径不感知。 */
    @Test
    public void themeSwitchReDerivesDefaultPathWhileLegacyUnaffected() {
        SceneRuntime rt = newRuntime();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        Holder<ReadableSignal<FormTheme>> captured = new Holder<ReadableSignal<FormTheme>>();

        rt.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> captured.value = ConfigTheme.asFormTheme(rt));
            return new SceneNode();
        });

        FormTheme before = captured.value.get();
        assertMapsThemeItemByItem(SceneTheme.liquidGlassDark(), before);
        int effectsAfterMount = ReactiveTestProbe.registeredEffectCount();
        FormTheme legacyBefore = ConfigTheme.asFormTheme();

        pageTheme.set(SceneTheme.liquidGlassLight());
        rt.flush();

        FormTheme after = captured.value.get();
        Assert.assertNotSame("切换后默认路径不再是同一对象", before, after);
        Assert.assertNotEquals("切换后默认路径值变化", before, after);
        assertMapsThemeItemByItem(SceneTheme.liquidGlassLight(), after);
        Assert.assertEquals("主题更新只重算不新增订阅",
                effectsAfterMount, ReactiveTestProbe.registeredEffectCount());

        // 旧显式路径不感知：缓存实例与像素值均不变，且与默认路径分叉
        FormTheme legacyAfter = ConfigTheme.asFormTheme();
        Assert.assertSame("旧入口恒返回同一缓存实例", legacyBefore, legacyAfter);
        Assert.assertEquals("旧入口切换后仍等于 defaultDark()", FormTheme.defaultDark(), legacyAfter);
        Assert.assertEquals("旧入口脏态色保持旧值", 0xFFD0BCFF, legacyAfter.dirtyColor());
        Assert.assertEquals("旧入口正文色保持旧值", 0xFFE6E1E5, legacyAfter.textColor());
        Assert.assertNotEquals("切换后默认路径与旧路径分叉（默认随主题、旧不随）",
                legacyAfter.dirtyColor(), after.dirtyColor());
    }

    // ==================== 安全回落与生命周期 ====================

    /** 无当前 Owner：取该 runtime 安装的默认主题，不抛异常、不误取他库默认。 */
    @Test
    public void installedRuntimeDefaultAppliesOutsideBuilder() {
        SceneRuntime rt = newRuntime();
        SceneTheme installed = SceneTheme.liquidGlassLight();
        SceneThemes.install(rt, Signal.create(installed));

        ReadableSignal<FormTheme> resolved = ConfigTheme.asFormTheme(rt);

        Assert.assertNotNull("无 Owner 时也返回非 null 信号", resolved);
        Assert.assertNotNull("无 Owner 时初值非 null", resolved.get());
        assertMapsThemeItemByItem(installed, resolved.get());
    }

    /** 未安装任何主题且无 Owner：安全回落库默认液态玻璃档，不抛异常。 */
    @Test
    public void freshRuntimeWithoutInstallFallsBackToLibraryDefault() {
        SceneRuntime rt = newRuntime();

        ReadableSignal<FormTheme> resolved = ConfigTheme.asFormTheme(rt);
        rt.flush();

        assertMapsThemeItemByItem(SceneThemes.DEFAULT, resolved.get());
    }

    /** 卸载回收：默认路径桥创建的派生信号随 Owner 销毁注销，回到基线订阅数。 */
    @Test
    public void defaultPathDerivedSignalReleasesOnDispose() {
        SceneRuntime rt = newRuntime();
        SceneThemes.install(rt, Signal.create(SceneTheme.liquidGlassDark()));
        int baseline = ReactiveTestProbe.registeredEffectCount();

        MountHandle handle = rt.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(SceneTheme.liquidGlassLight()),
                    () -> ConfigTheme.asFormTheme(rt));
            return new SceneNode();
        });
        Assert.assertTrue("默认路径应新增订阅",
                ReactiveTestProbe.registeredEffectCount() > baseline);

        handle.dispose();
        rt.flush();

        Assert.assertEquals("卸载回收全部订阅", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    /** null runtime 显式拒绝（委托 FormThemes 契约），不静默降级为旧缓存值。 */
    @Test
    public void nullRuntimeRejectedWithoutSilentFallback() {
        try {
            ConfigTheme.asFormTheme((SceneRuntime) null);
            Assert.fail("asFormTheme(null) 应抛 NullPointerException");
        } catch (NullPointerException expected) {
            // 期望路径
        }
    }

    // ==================== 显式旧路径：缓存语义与像素锁 ====================

    /** 无参入口缓存语义：恒同一实例、等于 defaultDark()、不创建任何订阅。 */
    @Test
    public void legacyAsFormThemeKeepsCachedInstanceSemantics() {
        int baseline = ReactiveTestProbe.registeredEffectCount();

        FormTheme first = ConfigTheme.asFormTheme();
        FormTheme second = ConfigTheme.asFormTheme();

        Assert.assertSame("重复调用返回同一缓存实例", first, second);
        Assert.assertEquals("缓存实例等于 FormTheme.defaultDark()", FormTheme.defaultDark(), first);
        Assert.assertEquals("显式路径不创建订阅", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    /** 显式路径静态色常量像素锁：任何默认路径改造都不得动这些旧值。 */
    @Test
    public void legacyExplicitColorConstantsPixelLocked() {
        Assert.assertEquals("ROOT_BG", 0xCC111318, ConfigTheme.ROOT_BG);
        Assert.assertEquals("VIEWPORT_BG", 0xFF1B1B1F, ConfigTheme.VIEWPORT_BG);
        Assert.assertEquals("SURFACE_CONTAINER", 0xFF211F26, ConfigTheme.SURFACE_CONTAINER);
        Assert.assertEquals("SURFACE_CONTAINER_HIGH", 0xFF2B2930, ConfigTheme.SURFACE_CONTAINER_HIGH);
        Assert.assertEquals("TITLE_COLOR", 0xFFE6E1E5, ConfigTheme.TITLE_COLOR);
        Assert.assertEquals("TEXT_COLOR", 0xFFE6E1E5, ConfigTheme.TEXT_COLOR);
        Assert.assertEquals("MUTED_COLOR", 0xFFCAC4D0, ConfigTheme.MUTED_COLOR);
        Assert.assertEquals("ERROR_COLOR", 0xFFFFB4AB, ConfigTheme.ERROR_COLOR);
        Assert.assertEquals("OK_COLOR", 0xFFA8DAB5, ConfigTheme.OK_COLOR);
        Assert.assertEquals("DIRTY_COLOR", 0xFFD0BCFF, ConfigTheme.DIRTY_COLOR);
        Assert.assertEquals("READOUT_BG 与 SURFACE_CONTAINER_HIGH 同源",
                ConfigTheme.SURFACE_CONTAINER_HIGH, ConfigTheme.READOUT_BG);
    }

    /** 尺寸/布局常量与主题无关：仍取 SceneChromeTokens 既有值（主题不接管布局）。 */
    @Test
    public void layoutConstantsStayThemeIndependent() {
        Assert.assertEquals("FIELD_GAP", SceneChromeTokens.GAP_MD, ConfigTheme.FIELD_GAP);
        Assert.assertEquals("INPUT_HEIGHT", SceneChromeTokens.INPUT_HEIGHT, ConfigTheme.INPUT_HEIGHT);
        Assert.assertEquals("NAV_TAB_PADDING", SceneChromeTokens.PAD_LG, ConfigTheme.NAV_TAB_PADDING);
        Assert.assertEquals("MOTION_FAST_MS", SceneChromeTokens.MOTION_FAST_MS, ConfigTheme.MOTION_FAST_MS);
        Assert.assertEquals("MOTION_STANDARD_MS", SceneChromeTokens.MOTION_STANDARD_MS, ConfigTheme.MOTION_STANDARD_MS);
        Assert.assertEquals("MOTION_EMPHASIZED_MS", SceneChromeTokens.MOTION_EMPHASIZED_MS,
                ConfigTheme.MOTION_EMPHASIZED_MS);
    }
}
