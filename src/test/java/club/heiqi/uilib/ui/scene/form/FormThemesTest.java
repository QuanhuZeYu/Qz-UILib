package club.heiqi.uilib.ui.scene.form;

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
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link FormThemes} 主题桥契约测试。
 *
 * <p>覆盖：{@link FormThemes#of(SceneTheme)} 的逐分量映射（GROUP 表面 + 语义色 + PANEL 页底色
 * + 布局/字号不随主题变化）、{@link FormTheme#defaultDark()} 旧语义未被改写、
 * {@link FormThemes#resolve(SceneRuntime)} 的库默认回落、局部主题继承、主题切换驱动重算
 * （值变化且非同一对象、订阅数不增长）、构建期不抛 NPE、卸载回收订阅、null 参数契约。</p>
 */
public class FormThemesTest {

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

    // ==================== of(SceneTheme) 纯函数映射 ====================

    /** 深色档：卡片表面取 GROUP 的 idle tint/edge，语义色取主题对应语义色。 */
    @Test
    public void ofMapsGroupSurfaceAndSemanticColors() {
        SceneTheme theme = SceneTheme.liquidGlassDark();
        SceneSurfaceStyle group = theme.surface(SceneTheme.Role.GROUP);
        FormTheme mapped = FormThemes.of(theme);

        Assert.assertEquals("cardBg 取 GROUP idle tint", group.getIdle().getTint(), mapped.cardBg());
        Assert.assertEquals("cardBorder 取 GROUP idle edge", group.getIdle().getEdge(), mapped.cardBorder());
        Assert.assertEquals("cardBorderDirty 取主题 accent", theme.accent(), mapped.cardBorderDirty());
        Assert.assertEquals("cardBorderError 取主题 errorText", theme.errorText(), mapped.cardBorderError());
        Assert.assertEquals("正文色取 foreground", theme.foreground(), mapped.textColor());
        Assert.assertEquals("次要色取 mutedForeground", theme.mutedForeground(), mapped.mutedColor());
        Assert.assertEquals("错误色取 errorText", theme.errorText(), mapped.errorColor());
        Assert.assertEquals("脏态色取 accent", theme.accent(), mapped.dirtyColor());
        Assert.assertEquals("标题色取 foreground", theme.foreground(), mapped.titleColor());
    }

    /** 深色档：PANEL idle tint 为半透明（叠在玻璃之上），不可直接当静态页底色。 */
    @Test
    public void ofFallsBackToOpaquePanelBackgroundWhenPanelTintIsTranslucent() {
        SceneTheme theme = SceneTheme.liquidGlassDark();
        int panelTint = theme.surface(SceneTheme.Role.PANEL).getIdle().getTint();
        Assert.assertTrue("默认档 PANEL tint 应为半透明叠色", alpha(panelTint) < 0xFF);

        FormTheme mapped = FormThemes.of(theme);

        Assert.assertEquals("rootBg 回落不透明替代底色", SceneTheme.FALLBACK_BG, mapped.rootBg());
        Assert.assertEquals("viewportBg 回落不透明替代底色", SceneTheme.FALLBACK_BG, mapped.viewportBg());
        Assert.assertEquals("页底色必须不透明可读", 0xFF, alpha(mapped.rootBg()));
        Assert.assertNotEquals("不得把半透明 tint 直接当页底色", panelTint, mapped.rootBg());
    }

    /** PANEL tint 自身不透明时直接采用（自定义主题路径）。 */
    @Test
    public void ofTakesPanelTintWhenItIsOpaque() {
        SceneSurfaceStyle panel = SceneSurfaceStyle.builder()
                .idle(new SceneSurfaceStyle.StateStyle(0xFF123456, 0xFF654321, 0.0F, 0.0F))
                .build();
        SceneTheme theme = SceneTheme.builder().surface(SceneTheme.Role.PANEL, panel).build();

        FormTheme mapped = FormThemes.of(theme);

        Assert.assertEquals("不透明 PANEL tint 直接作为 rootBg", 0xFF123456, mapped.rootBg());
        Assert.assertEquals("viewportBg 与 rootBg 同源", 0xFF123456, mapped.viewportBg());
    }

    /** 无滤镜替代档：PANEL tint 即不透明替代底色，卡片底色同样可读。 */
    @Test
    public void ofWithoutBackdropKeepsOpaqueReadableBackground() {
        SceneTheme noBackdrop = SceneTheme.liquidGlassDark().withoutBackdrop();
        FormTheme mapped = FormThemes.of(noBackdrop);

        Assert.assertEquals("无滤镜档 rootBg 取 PANEL 替代 tint",
                noBackdrop.surface(SceneTheme.Role.PANEL).getIdle().getTint(), mapped.rootBg());
        Assert.assertEquals("无滤镜档页底色不透明", 0xFF, alpha(mapped.rootBg()));
        Assert.assertEquals("无滤镜档卡片底色不透明", 0xFF, alpha(mapped.cardBg()));
    }

    /** 布局与字号分量与主题无关：恒等于既有默认档的值。 */
    @Test
    public void ofKeepsLayoutTokensIndependentOfTheme() {
        FormTheme legacy = FormTheme.defaultDark();
        FormTheme mapped = FormThemes.of(SceneTheme.liquidGlassLight());

        Assert.assertEquals("cardRadius 用既有 RADIUS_LG", SceneChromeTokens.RADIUS_LG, mapped.cardRadius());
        Assert.assertEquals("cardPad 用既有 PAD_LG", SceneChromeTokens.PAD_LG, mapped.cardPad());
        Assert.assertEquals("fieldGap 用既有 GAP_MD", SceneChromeTokens.GAP_MD, mapped.fieldGap());
        Assert.assertEquals("inputHeight 用既有 INPUT_HEIGHT",
                SceneChromeTokens.INPUT_HEIGHT, mapped.inputHeight());
        Assert.assertEquals("listHeight 保持既有值", legacy.listHeight(), mapped.listHeight());
        Assert.assertEquals("fontLabel 保持既有值", legacy.fontLabel(), mapped.fontLabel());
        Assert.assertEquals("fontHelper 保持既有值", legacy.fontHelper(), mapped.fontHelper());
        Assert.assertEquals("fontError 保持既有值", legacy.fontError(), mapped.fontError());
        Assert.assertNotEquals("颜色分量确实随主题变化（对照组）", legacy, mapped);
    }

    /** 同一入参恒返回值相等的映射（纯函数，无副作用）。 */
    @Test
    public void ofIsPureAndValueStable() {
        SceneTheme theme = SceneTheme.liquidGlassDark();

        FormTheme first = FormThemes.of(theme);
        FormTheme second = FormThemes.of(theme);

        Assert.assertEquals("同入参映射值相等", first, second);
        Assert.assertNotSame("映射每次返回新实例（不可变值对象）", first, second);
        Assert.assertEquals("重复映射不新增订阅", 0, ReactiveTestProbe.registeredEffectCount());
    }

    /** null 主题按契约抛 NPE，不静默降级。 */
    @Test
    public void ofRejectsNullTheme() {
        try {
            FormThemes.of(null);
            Assert.fail("of(null) 应抛 NullPointerException");
        } catch (NullPointerException expected) {
            // 期望路径
        }
    }

    // ==================== FormTheme.defaultDark() 旧语义回归锁 ====================

    /** 主题桥不得改写默认档的旧显式语义：值逐项锁定。 */
    @Test
    public void defaultDarkStaysLegacyValues() {
        FormTheme legacy = FormTheme.defaultDark();

        Assert.assertEquals("cardBg 保持旧值", 0xFF2B2930, legacy.cardBg());
        Assert.assertEquals("cardBorder 保持旧值", 0xFF2B2930, legacy.cardBorder());
        Assert.assertEquals("cardBorderDirty 保持旧值", 0xFFD0BCFF, legacy.cardBorderDirty());
        Assert.assertEquals("cardBorderError 保持旧值", 0xFFFFB4AB, legacy.cardBorderError());
        Assert.assertEquals("cardRadius 保持旧值", SceneChromeTokens.RADIUS_LG, legacy.cardRadius());
        Assert.assertEquals("cardPad 保持旧值", SceneChromeTokens.PAD_LG, legacy.cardPad());
        Assert.assertEquals("fieldGap 保持旧值", SceneChromeTokens.GAP_MD, legacy.fieldGap());
        Assert.assertEquals("textColor 保持旧值", 0xFFE6E1E5, legacy.textColor());
        Assert.assertEquals("mutedColor 保持旧值", 0xFFCAC4D0, legacy.mutedColor());
        Assert.assertEquals("errorColor 保持旧值", 0xFFFFB4AB, legacy.errorColor());
        Assert.assertEquals("dirtyColor 保持旧值", 0xFFD0BCFF, legacy.dirtyColor());
        Assert.assertEquals("fontLabel 保持旧值", 16, legacy.fontLabel());
        Assert.assertEquals("fontHelper 保持旧值", 13, legacy.fontHelper());
        Assert.assertEquals("fontError 保持旧值", 13, legacy.fontError());
        Assert.assertEquals("inputHeight 保持旧值", SceneChromeTokens.INPUT_HEIGHT, legacy.inputHeight());
        Assert.assertEquals("listHeight 保持旧值", 220, legacy.listHeight());
        Assert.assertEquals("rootBg 保持旧值", 0xFF111318, legacy.rootBg());
        Assert.assertEquals("viewportBg 保持旧值", 0xFF1B1B1F, legacy.viewportBg());
        Assert.assertEquals("titleColor 保持旧值", 0xFFE6E1E5, legacy.titleColor());
    }

    // ==================== resolve(SceneRuntime) 作用域解析 ====================

    /** 无局部/运行时主题时，派生值等于库默认档的映射。 */
    @Test
    public void resolveWithoutLocalThemeEqualsOfLibraryDefault() {
        SceneRuntime rt = newRuntime();
        Holder<FormTheme> captured = new Holder<FormTheme>();

        rt.mount(new SceneNode(), () -> {
            captured.value = FormThemes.resolve(rt).get();
            return new SceneNode();
        });

        Assert.assertNotNull("构建期即可读到非 null 初值", captured.value);
        Assert.assertEquals("无局部主题时等于 of(SceneThemes.DEFAULT)",
                FormThemes.of(SceneThemes.DEFAULT), captured.value);
    }

    /** 局部主题作用域内解析到页面主题，作用域外回落库默认。 */
    @Test
    public void resolveFollowsLocalThemeScope() {
        SceneRuntime rt = newRuntime();
        SceneTheme page = SceneTheme.liquidGlassLight();
        Holder<FormTheme> inside = new Holder<FormTheme>();
        Holder<FormTheme> outside = new Holder<FormTheme>();

        rt.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(page), () -> inside.value = FormThemes.resolve(rt).get());
            outside.value = FormThemes.resolve(rt).get();
            return new SceneNode();
        });

        Assert.assertEquals("作用域内取页面主题的映射", FormThemes.of(page), inside.value);
        Assert.assertEquals("作用域外回落库默认的映射",
                FormThemes.of(SceneThemes.DEFAULT), outside.value);
    }

    /** 无当前 Owner 时经 runtime 根作用域解析已安装主题，不抛 NPE。 */
    @Test
    public void resolveOutsideBuilderFallsBackToRuntimeDefault() {
        SceneRuntime rt = newRuntime();
        SceneTheme installed = SceneTheme.liquidGlassLight();
        SceneThemes.install(rt, Signal.create(installed));

        ReadableSignal<FormTheme> resolved = FormThemes.resolve(rt);

        Assert.assertNotNull("无 Owner 时也返回非 null 信号", resolved);
        Assert.assertEquals("取 runtime 默认主题的映射", FormThemes.of(installed), resolved.get());
    }

    /** 构建期调用不抛 NPE，且 flush 后值仍非 null。 */
    @Test
    public void resolveIsSafeInsideBuilderWithoutNpe() {
        SceneRuntime rt = newRuntime();
        Holder<ReadableSignal<FormTheme>> captured = new Holder<ReadableSignal<FormTheme>>();

        rt.mount(new SceneNode(), () -> {
            captured.value = FormThemes.resolve(rt);
            Assert.assertNotNull("builder 内返回非 null 信号", captured.value);
            Assert.assertNotNull("builder 内读取初值非 null", captured.value.get());
            return new SceneNode();
        });
        rt.flush();

        Assert.assertNotNull("flush 后派生值非 null", captured.value.get());
        Assert.assertEquals("flush 后仍等于库默认映射",
                FormThemes.of(SceneThemes.DEFAULT), captured.value.get());
    }

    /** 主题切换：派生信号重算（值变化、非同一对象），且不新增订阅。 */
    @Test
    public void resolveUpdatesWhenThemeSwitches() {
        SceneRuntime rt = newRuntime();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        Holder<ReadableSignal<FormTheme>> captured = new Holder<ReadableSignal<FormTheme>>();

        rt.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> captured.value = FormThemes.resolve(rt));
            return new SceneNode();
        });
        FormTheme before = captured.value.get();
        Assert.assertEquals("初值来自来源主题",
                FormThemes.of(SceneTheme.liquidGlassDark()), before);
        int effectsAfterMount = ReactiveTestProbe.registeredEffectCount();

        pageTheme.set(SceneTheme.liquidGlassLight());
        rt.flush();

        FormTheme after = captured.value.get();
        Assert.assertNotSame("切换后不再是同一对象", before, after);
        Assert.assertNotEquals("切换后值变化", before, after);
        Assert.assertEquals("切换后取新主题的映射",
                FormThemes.of(SceneTheme.liquidGlassLight()), after);
        Assert.assertEquals("主题更新只重算不新增订阅",
                effectsAfterMount, ReactiveTestProbe.registeredEffectCount());
    }

    /** 卸载回收：桥创建的派生随 Owner 卸载注销。 */
    @Test
    public void resolveDisposesDerivedSignalOnUnmount() {
        SceneRuntime rt = newRuntime();
        SceneThemes.install(rt, Signal.create(SceneTheme.liquidGlassDark()));
        int baseline = ReactiveTestProbe.registeredEffectCount();

        MountHandle handle = rt.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(SceneTheme.liquidGlassLight()), () ->
                    FormThemes.resolve(rt));
            return new SceneNode();
        });
        Assert.assertTrue("桥应新增订阅", ReactiveTestProbe.registeredEffectCount() > baseline);

        handle.dispose();
        rt.flush();

        Assert.assertEquals("卸载回收全部订阅", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    /** null runtime 按契约抛 NPE。 */
    @Test
    public void resolveRejectsNullRuntime() {
        try {
            FormThemes.resolve(null);
            Assert.fail("resolve(null) 应抛 NullPointerException");
        } catch (NullPointerException expected) {
            // 期望路径
        }
    }
}
