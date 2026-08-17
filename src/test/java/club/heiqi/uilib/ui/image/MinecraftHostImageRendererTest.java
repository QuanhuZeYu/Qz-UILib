package club.heiqi.uilib.ui.image;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.junit.Assert;
import org.junit.Test;

/** Minecraft 普通 texture/bitmap renderer 的降级与资源生命周期测试。 */
public class MinecraftHostImageRendererTest {

    @Test
    public void plainRendererIsPhysicallySeparateFromItemRenderer() {
        Assert.assertFalse(ItemIconRenderer.class.isAssignableFrom(MinecraftHostImageRenderer.class));
    }

    /** 缺失纹理源在绑定前跳过，避免 Minecraft 自动绘制紫黑 missing texture。 */
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

    @Test
    public void closeDeletesEveryUploadedDynamicBitmapTextureAndClearsCache() {
        RecordingDynamicImageTextureAccess textures = new RecordingDynamicImageTextureAccess();
        MinecraftHostImageRenderer renderer = new MinecraftHostImageRenderer(
                new RecordingTextureResourceChecker(true), textures);
        HostImageSource first = HostImageSource.bufferedImage(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
                "first");
        HostImageSource second = HostImageSource.bufferedImage(new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB),
                "second");

        ResourceLocation firstTexture = renderer.resolveDynamicImageTexture(first);
        Assert.assertSame(firstTexture, renderer.resolveDynamicImageTexture(first));
        renderer.resolveDynamicImageTexture(second);
        Assert.assertEquals(2, textures.created);

        renderer.close();
        Assert.assertEquals(2, textures.deleted.size());
        renderer.close();
        Assert.assertEquals("close 必须幂等", 2, textures.deleted.size());

        Assert.assertNotSame(firstTexture, renderer.resolveDynamicImageTexture(first));
        Assert.assertEquals("close 后重新使用会重新上传", 3, textures.created);
        renderer.close();
        Assert.assertEquals(3, textures.deleted.size());
    }

    @Test
    public void failedTextureDeletionKeepsOnlyThatTextureForRetry() {
        RecordingDynamicImageTextureAccess textures = new RecordingDynamicImageTextureAccess();
        MinecraftHostImageRenderer renderer = new MinecraftHostImageRenderer(
                new RecordingTextureResourceChecker(true), textures);
        HostImageSource first = HostImageSource.bufferedImage(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
                "retry-first");
        HostImageSource second = HostImageSource.bufferedImage(new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB),
                "retry-second");
        ResourceLocation firstTexture = renderer.resolveDynamicImageTexture(first);
        ResourceLocation secondTexture = renderer.resolveDynamicImageTexture(second);
        textures.failOnce = firstTexture;

        try {
            renderer.close();
            Assert.fail("expected");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("delete-once", expected.getMessage());
        }
        Assert.assertEquals(1, attemptsFor(textures, firstTexture));
        Assert.assertEquals(1, attemptsFor(textures, secondTexture));
        Assert.assertTrue(textures.deleted.contains(secondTexture));

        renderer.close();

        Assert.assertEquals(2, attemptsFor(textures, firstTexture));
        Assert.assertEquals("已成功删除的纹理不得重复删除", 1, attemptsFor(textures, secondTexture));
        Assert.assertTrue(textures.deleted.contains(firstTexture));
    }

    private static int attemptsFor(RecordingDynamicImageTextureAccess textures, ResourceLocation texture) {
        int count = 0;
        for (ResourceLocation attempted : textures.deleteAttempts) {
            if (texture.equals(attempted)) count++;
        }
        return count;
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

    private static final class RecordingDynamicImageTextureAccess implements DynamicImageTextureAccess {
        private int created;
        private final List<ResourceLocation> deleted = new ArrayList<ResourceLocation>();
        private final List<ResourceLocation> deleteAttempts = new ArrayList<ResourceLocation>();
        private ResourceLocation failOnce;

        @Override
        public ResourceLocation create(String key, BufferedImage image) {
            created++;
            return new ResourceLocation("test", key + "/" + created);
        }

        @Override
        public void delete(ResourceLocation texture) {
            deleteAttempts.add(texture);
            if (texture.equals(failOnce)) {
                failOnce = null;
                throw new IllegalStateException("delete-once");
            }
            deleted.add(texture);
        }
    }
}
