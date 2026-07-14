package club.heiqi.uilib.ui.render;

import org.junit.Assert;
import org.junit.Test;

/** 离屏目标无效尺寸与资源初态测试。 */
public class UiRenderTargetTest {
    @Test
    public void invalidSizeDoesNotAllocateGlResources() {
        UiRenderTarget target = new UiRenderTarget();
        target.ensureSize(0, 32);
        Assert.assertEquals(0, target.getWidth());
        Assert.assertEquals(0, target.getHeight());
        Assert.assertEquals(0, target.getColorTextureId());
    }
}
