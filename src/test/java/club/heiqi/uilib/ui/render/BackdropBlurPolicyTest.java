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
}
