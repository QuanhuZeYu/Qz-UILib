package club.heiqi.uilib.ui.image;

import net.minecraft.util.ResourceLocation;

import org.junit.Assert;
import org.junit.Test;

/**
 * `MinecraftHostImageRenderer` 的纹理资源降级测试。
 */
public class MinecraftHostImageRendererTest {

    /** Minecraft delegate 不再自带第二层完整围栏，统一由适配器包装。 */
    @Test
    public void shouldNotDeclareIndependentGuardedRenderPath() {
        try {
            MinecraftHostImageRenderer.class.getDeclaredMethod("renderGuarded",
                    HostImageSource.class, int.class, int.class, int.class, int.class);
            Assert.fail("Minecraft delegate 不应声明第二层 renderGuarded");
        } catch (NoSuchMethodException expected) {
            // 统一包装边界成立。
        }
    }

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

    /** GUI 物品深度必须在正常绘制期间可见，并恢复调用前值。 */
    @Test
    public void shouldUseVisibleGuiDepthAndRestorePreviousValue() {
        RecordingItemDepthAccess depth = new RecordingItemDepthAccess(37.0F);

        MinecraftHostImageRenderer.runWithGuiItemDepth(depth,
                () -> Assert.assertEquals(MinecraftHostImageRenderer.GUI_ITEM_Z_LEVEL,
                        depth.get(), 0.0F));

        Assert.assertEquals("正常返回后恢复调用前 zLevel", 37.0F, depth.get(), 0.0F);
    }

    /** 绘制动作异常时也必须恢复调用前深度。 */
    @Test
    public void shouldRestorePreviousDepthWhenItemRenderFails() {
        RecordingItemDepthAccess depth = new RecordingItemDepthAccess(-12.0F);

        try {
            MinecraftHostImageRenderer.runWithGuiItemDepth(depth,
                    () -> { throw new IllegalStateException("render failed"); });
            Assert.fail("异常应继续传播");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("render failed", expected.getMessage());
        }

        Assert.assertEquals("异常后恢复调用前 zLevel", -12.0F, depth.get(), 0.0F);
    }

    /** 不触发 Minecraft/GL 初始化的 zLevel 记录桩。 */
    private static final class RecordingItemDepthAccess implements MinecraftHostImageRenderer.ItemDepthAccess {
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
