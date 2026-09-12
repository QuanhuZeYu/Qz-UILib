package club.heiqi.uilib.ui.render;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

/**
 * 页面级背景模糊策略的纯 JVM 语义测试。
 *
 * <p>除既有"未声明字段继承全局配置"口径外，这里还钉住 2026-09-12 加入的档位语义：
 * {@link BackdropQuality#OFF}（配置值 solid）在页面未显式声明时关闭整条玻璃链，
 * 显式页面策略优先；{@link BackdropQuality#ECO} 不改变任何启用开关。</p>
 */
public class BackdropBlurPolicyTest {

    @After
    public void resetConfig() {
        BackdropBlurConfig.getInstance().resetToDefaults();
        BackdropQualityService.getInstance().resetForTest();
        // 档位复位同样经调度器，需 flush 后才落到订阅通道。
        ReactiveScheduler.get().flush();
    }

    /**
     * 验证未声明字段会继续继承全局背景模糊配置。
     */
    @Test
    public void shouldInheritGlobalFieldsWhenPolicyLeavesThemUndeclared() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        config.setHostBackgroundBlurEnabled(false);
        config.setMaxBlurRadius(24);
        config.setShaderEnabled(false);
        config.setFixedPipelineEnabled(false);
        config.setTintFallbackEnabled(false);

        BackdropBlurPolicy policy = BackdropBlurPolicy.inheritGlobal();

        Assert.assertFalse(policy.resolveHostBackgroundBlurEnabled(config));
        Assert.assertEquals(24, policy.resolveMaxBlurRadius(config));
        Assert.assertFalse(policy.resolveShaderEnabled(config));
        Assert.assertFalse(policy.resolveFixedPipelineEnabled(config));
        Assert.assertFalse(policy.resolveTintFallbackEnabled(config));
    }

    /**
     * 验证页面禁用策略是当前页面的总开关，不需要重复写入每个底层字段。
     */
    @Test
    public void shouldDisablePageWithoutOverridingInheritedFields() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        config.setHostBackgroundBlurEnabled(true);
        config.setShaderEnabled(true);
        config.setFixedPipelineEnabled(true);
        config.setTintFallbackEnabled(true);

        BackdropBlurPolicy disabledPolicy = BackdropBlurPolicy.disabled();

        Assert.assertFalse(disabledPolicy.resolveEnabled(config));
        Assert.assertFalse(disabledPolicy.resolveHostBackgroundBlurEnabled(config));
        Assert.assertFalse(disabledPolicy.resolveShaderEnabled(config));
        Assert.assertFalse(disabledPolicy.resolveFixedPipelineEnabled(config));
        Assert.assertFalse(disabledPolicy.resolveTintFallbackEnabled(config));
    }

    /**
     * 验证页面策略半径上限与全局配置保持一致的 clamp 范围。
     */
    @Test
    public void shouldClampMaxBlurRadiusToPolicyLimit() {
        BackdropBlurPolicy policy = BackdropBlurPolicy.inheritGlobal()
                .withMaxBlurRadius(BackdropBlurPolicy.MAX_BLUR_RADIUS + 1);

        Assert.assertEquals(BackdropBlurPolicy.MAX_BLUR_RADIUS,
                policy.resolveMaxBlurRadius(BackdropBlurConfig.getInstance()));
    }

    /**
     * 验证宿主级背景模糊默认关闭，未声明的页面策略解析后也保持关闭。
     */
    @Test
    public void shouldDisableHostBackgroundBlurByDefault() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        config.resetToDefaults();

        Assert.assertFalse(config.getHostBackgroundBlurEnabled());
        Assert.assertFalse(BackdropBlurPolicy.inheritGlobal().resolveHostBackgroundBlurEnabled(config));
    }

    /**
     * 验证显式声明启用宿主级模糊的页面策略不受全局默认关闭影响。
     */
    @Test
    public void shouldKeepHostBackgroundBlurForExplicitPolicies() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        config.resetToDefaults();

        Assert.assertTrue(BackdropBlurPolicy.quality().resolveHostBackgroundBlurEnabled(config));
        Assert.assertTrue(BackdropBlurPolicy.performance().resolveHostBackgroundBlurEnabled(config));
        Assert.assertTrue(BackdropBlurPolicy.inheritGlobal()
                .withHostBackgroundBlurEnabled(true)
                .resolveHostBackgroundBlurEnabled(config));
        Assert.assertFalse(BackdropBlurPolicy.disabled().resolveHostBackgroundBlurEnabled(config));
    }

    /**
     * 验证质量/性能全局预设会显式开启宿主级模糊，避免默认关闭后预设无效果。
     */
    @Test
    public void shouldEnableHostBackgroundBlurAfterQualityOrPerformanceConfigPreset() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();

        config.resetToDefaults();
        config.applyQualityPreset();
        Assert.assertTrue(config.getHostBackgroundBlurEnabled());

        config.resetToDefaults();
        config.applyPerformancePreset();
        Assert.assertTrue(config.getHostBackgroundBlurEnabled());
    }

    /**
     * 档位 solid：页面未显式声明时整条玻璃链关闭——总开关、宿主级、shader、固定管线、tint 兜底
     * 必须同为 false（不能只关总开关、让固定管线或 tint 兜底继续画）。
     */
    @Test
    public void shouldDisableEveryBackdropPathWhenQualityIsSolid() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        BackdropQualityService.getInstance().applyConfigured("solid");

        BackdropBlurPolicy policy = BackdropBlurPolicy.inheritGlobal();
        Assert.assertFalse(policy.resolveEnabled(config));
        Assert.assertFalse(policy.resolveHostBackgroundBlurEnabled(config));
        Assert.assertFalse(policy.resolveShaderEnabled(config));
        Assert.assertFalse(policy.resolveFixedPipelineEnabled(config));
        Assert.assertFalse(policy.resolveTintFallbackEnabled(config));
    }

    /** 显式页面策略优先于进程级档位：页面级是更强的意图声明，不得被全局档位覆盖。 */
    @Test
    public void shouldKeepExplicitPagePolicyEnabledWhenQualityIsSolid() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        BackdropQualityService.getInstance().applyConfigured("solid");

        Assert.assertTrue(BackdropBlurPolicy.inheritGlobal().withEnabled(true).resolveEnabled(config));
        Assert.assertTrue(BackdropBlurPolicy.quality().resolveEnabled(config));
        Assert.assertFalse("显式禁用仍然是禁用", BackdropBlurPolicy.disabled().resolveEnabled(config));
    }

    /** eco 档只换抽头预算，不改变任何启用开关：逐项与完整档一致。 */
    @Test
    public void shouldKeepEveryBackdropPathEnabledForEcoQuality() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        config.setHostBackgroundBlurEnabled(true);
        BackdropQualityService.getInstance().applyConfigured("eco");

        BackdropBlurPolicy policy = BackdropBlurPolicy.inheritGlobal();
        Assert.assertTrue(policy.resolveEnabled(config));
        Assert.assertTrue(policy.resolveHostBackgroundBlurEnabled(config));
        Assert.assertTrue(policy.resolveShaderEnabled(config));
        Assert.assertTrue(policy.resolveFixedPipelineEnabled(config));
        Assert.assertTrue(policy.resolveTintFallbackEnabled(config));
    }

    /** 默认档 full：未声明页面策略仍保持启用（与引入档位前逐值一致）。 */
    @Test
    public void shouldKeepBackdropEnabledByDefaultForFullQuality() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        BackdropQualityService.getInstance().applyConfigured("full");

        Assert.assertTrue(BackdropBlurPolicy.inheritGlobal().resolveEnabled(config));
    }
}
