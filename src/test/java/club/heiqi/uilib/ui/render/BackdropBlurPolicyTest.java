package club.heiqi.uilib.ui.render;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * 页面级背景模糊策略的纯 JVM 语义测试。
 */
public class BackdropBlurPolicyTest {

    @After
    public void resetConfig() {
        BackdropBlurConfig.getInstance().resetToDefaults();
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
}
