package club.heiqi.uilib.ui.render;

import org.junit.Assert;
import org.junit.Test;

/**
 * `UiBackdropEffectSpec` 的基础契约测试。
 */
public class UiBackdropEffectSpecTest {

    /**
     * 验证空 effect 会保持关闭状态。
     */
    @Test
    public void shouldKeepNoneBackdropEffectDisabled() {
        UiBackdropEffectSpec effectSpec = UiBackdropEffectSpec.none();

        Assert.assertFalse(effectSpec.enabled);
        Assert.assertEquals(0, effectSpec.strength);
        Assert.assertEquals(0, effectSpec.tintColor);
        Assert.assertEquals(0, effectSpec.cornerRadius);
    }

    /**
     * 验证 effect 配置会钳制负值并保留独立圆角提示。
     */
    @Test
    public void shouldClampNegativeBackdropValues() {
        UiBackdropEffectSpec effectSpec = new UiBackdropEffectSpec(true, -3, 0x66FFFFFF, -16);

        Assert.assertTrue(effectSpec.enabled);
        Assert.assertEquals(0, effectSpec.strength);
        Assert.assertEquals(0x66FFFFFF, effectSpec.tintColor);
        Assert.assertEquals(0, effectSpec.cornerRadius);
    }
}
