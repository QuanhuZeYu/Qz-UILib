package club.heiqi.uilib.config.modern;

import org.junit.Assert;
import org.junit.Test;

/** 主菜单全屏与世界内露出行为的 headless 契约测试。 */
public class ModernConfigViewportPolicyTest {

    @Test
    public void mainMenuKeepsVanillaBackgroundAndFullHeight() {
        Assert.assertTrue(ModernConfigViewportPolicy.shouldRenderDefaultBackground(false));
        Assert.assertEquals(1080, ModernConfigViewportPolicy.resolveSurfaceHeight(1080, false));
    }

    @Test
    public void worldContextUsesTopEightyPercentPanel() {
        Assert.assertFalse(ModernConfigViewportPolicy.shouldRenderDefaultBackground(true));
        Assert.assertEquals(864, ModernConfigViewportPolicy.resolveSurfaceHeight(1080, true));
        Assert.assertEquals(576, ModernConfigViewportPolicy.resolveSurfaceHeight(720, true));
    }

    @Test
    public void smallFramebufferFallsBackToFullHeight() {
        int threshold = ModernConfigViewportPolicy.MIN_PANEL_HEIGHT
                + ModernConfigViewportPolicy.MIN_WORLD_REVEAL;
        Assert.assertEquals(threshold - 1,
                ModernConfigViewportPolicy.resolveSurfaceHeight(threshold - 1, true));
        Assert.assertEquals(ModernConfigViewportPolicy.MIN_PANEL_HEIGHT,
                ModernConfigViewportPolicy.resolveSurfaceHeight(threshold, true));
        Assert.assertEquals(0, ModernConfigViewportPolicy.resolveSurfaceHeight(-1, true));
    }
}
