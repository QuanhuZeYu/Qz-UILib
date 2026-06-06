package club.heiqi.uilib.ui.image;

import net.minecraft.util.ResourceLocation;

import org.junit.Assert;
import org.junit.Test;

/**
 * `MinecraftHostImageRenderer` 的纹理资源降级测试。
 */
public class MinecraftHostImageRendererTest {

    /**
     * 验证缺失纹理源会在绑定前被跳过，避免 Minecraft 自动绘制紫黑 missing texture。
     */
    @Test
    public void shouldSkipMissingTextureBeforeMinecraftBindsDefaultMissingTexture() {
        RecordingTextureResourceChecker checker = new RecordingTextureResourceChecker(false);
        MinecraftHostImageRenderer renderer = new MinecraftHostImageRenderer(checker);

        renderer.render(HostImageSource.texture(new ResourceLocation("missing", "nonexistent.png"), 16, 16),
                0, 0, 16, 16);

        Assert.assertEquals(1, checker.checkCount);
        Assert.assertEquals("missing", checker.lastTexture.getResourceDomain());
        Assert.assertEquals("nonexistent.png", checker.lastTexture.getResourcePath());
    }

    private static final class RecordingTextureResourceChecker implements HostTextureResourceChecker {

        private final boolean available;
        private int checkCount;
        private ResourceLocation lastTexture;

        private RecordingTextureResourceChecker(boolean available) {
            this.available = available;
        }

        @Override
        public boolean isTextureAvailable(ResourceLocation texture) {
            checkCount++;
            lastTexture = texture;
            return available;
        }
    }
}
