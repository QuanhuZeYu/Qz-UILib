package club.heiqi.uilib.ui.image;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

/** Minecraft item icon-only 调用序列与 zLevel 恢复测试。 */
public class MinecraftItemIconRendererTest {

    @Test
    public void shouldUseVisibleGuiDepthAndRestorePreviousValue() {
        RecordingItemDepthAccess depth = new RecordingItemDepthAccess(37.0F);

        MinecraftItemIconRenderer.runWithGuiItemDepth(depth,
                () -> Assert.assertEquals(MinecraftItemIconRenderer.GUI_ITEM_Z_LEVEL,
                        depth.get(), 0.0F));

        Assert.assertEquals(37.0F, depth.get(), 0.0F);
    }

    @Test
    public void shouldRestorePreviousDepthWhenItemRenderFails() {
        RecordingItemDepthAccess depth = new RecordingItemDepthAccess(-12.0F);

        try {
            MinecraftItemIconRenderer.runWithGuiItemDepth(depth,
                    () -> { throw new IllegalStateException("render failed"); });
            Assert.fail("异常应继续传播");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("render failed", expected.getMessage());
        }

        Assert.assertEquals(-12.0F, depth.get(), 0.0F);
    }

    @Test
    public void operationSequenceContainsOnlyItemAndEffectRendering() {
        Assert.assertEquals(Arrays.asList(
                "item.matrix-push", "item.prepare-state", "item.lighting-enable", "item.transform",
                "item.blend-prepare", "item.render-effect", "item.lighting-disable", "item.matrix-pop"),
                Arrays.asList(MinecraftItemIconRenderer.itemOperationNames()));
    }

    private static final class RecordingItemDepthAccess implements MinecraftItemIconRenderer.ItemDepthAccess {
        private float zLevel;

        private RecordingItemDepthAccess(float zLevel) {
            this.zLevel = zLevel;
        }

        @Override
        public float get() {
            return zLevel;
        }

        @Override
        public void set(float zLevel) {
            this.zLevel = zLevel;
        }
    }
}
