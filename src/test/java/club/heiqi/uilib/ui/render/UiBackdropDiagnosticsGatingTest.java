package club.heiqi.uilib.ui.render;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * W3 诊断串按需构造：{@code BackdropBlurConfig.getDiagnosticsEnabled() == false} 时
 * detail 不再拼接（每表面每帧的字符串成本），{@code lastRenderPath} 仍恒写。
 *
 * <p>detail 构造器包内可见（与本包 {@code resolveBackdropShaderRadius} 同法）：门控发生在字符串
 * 拼接之前，而真实拼接点在 GL 路径内，无 GL 环境下无法端到端验证。这里逐条钉住"关闭时不构造、
 * 开启时格式不变"。</p>
 */
public class UiBackdropDiagnosticsGatingTest {

    private static final String DISABLED = "diagnostics-disabled";

    @After
    public void resetConfig() {
        BackdropBlurConfig.getInstance().resetToDefaults();
    }

    /** shader 成功路径：开启时格式与引入门控前逐字符一致，关闭时只返回常量。 */
    @Test
    public void shaderPathDetailIsBuiltOnlyWhenDiagnosticsAreEnabled() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();

        config.setDiagnosticsEnabled(true);
        String enabled = UiBackdropFilterRenderer.shaderPathDetail(20, 0.7F, null, null);
        Assert.assertTrue("开启时保留原格式: " + enabled, enabled.startsWith("blur=20, saturation=0.70"));
        Assert.assertTrue("snapshot 段保留: " + enabled, enabled.endsWith("snapshot=none"));

        config.setDiagnosticsEnabled(false);
        Assert.assertEquals("关闭时不得拼接 detail", DISABLED,
                UiBackdropFilterRenderer.shaderPathDetail(20, 0.7F, null, null));
    }

    /** 其余四条 detail 走同一门控（固定管线降级 / tint 兜底 / tint 禁用 / 快照与着色器不可用）。 */
    @Test
    public void everyBackdropDetailIsGatedTheSameWay() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();

        config.setDiagnosticsEnabled(false);
        Assert.assertEquals(DISABLED, UiBackdropFilterRenderer.fixedPipelinePathDetail(8, null, null));
        Assert.assertEquals(DISABLED, UiBackdropFilterRenderer.tintFallbackDetail("snapshot-unavailable", null));
        Assert.assertEquals(DISABLED, UiBackdropFilterRenderer.tintFallbackDisabledDetail("snapshot-unavailable"));
        Assert.assertEquals(DISABLED,
                UiBackdropFilterRenderer.snapshotUnavailableDetail(new UiMainLayerSnapshotService()));
        Assert.assertEquals(DISABLED,
                UiBackdropFilterRenderer.shaderUnavailableDetail(UiBackdropShaderProgram.programFor(13)));

        config.setDiagnosticsEnabled(true);
        Assert.assertEquals("shader-unavailable, samples=8, snapshot=none",
                UiBackdropFilterRenderer.fixedPipelinePathDetail(8, null, null));
        Assert.assertEquals("snapshot-unavailable",
                UiBackdropFilterRenderer.tintFallbackDetail("snapshot-unavailable", null));
        Assert.assertEquals("tint-fallback-disabled: x",
                UiBackdropFilterRenderer.tintFallbackDisabledDetail("x"));
        Assert.assertEquals("snapshot-unavailable: not-run",
                UiBackdropFilterRenderer.snapshotUnavailableDetail(new UiMainLayerSnapshotService()));
        Assert.assertTrue(UiBackdropFilterRenderer.shaderUnavailableDetail(
                UiBackdropShaderProgram.programFor(13)).startsWith("shader unavailable: "));
    }

    /** 默认值必须是开启：默认观感与默认诊断行为都不变（引入门控前逐字符一致）。 */
    @Test
    public void diagnosticsAreEnabledByDefault() {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        config.resetToDefaults();

        Assert.assertTrue(config.getDiagnosticsEnabled());
        Assert.assertEquals("blur=12, saturation=1.00, snapshot=none",
                UiBackdropFilterRenderer.shaderPathDetail(12, 1.0F, null, null));
    }
}
