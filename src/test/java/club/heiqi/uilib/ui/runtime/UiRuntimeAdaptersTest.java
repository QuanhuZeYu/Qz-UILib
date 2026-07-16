package club.heiqi.uilib.ui.runtime;

import java.lang.reflect.Constructor;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.image.GuardedHostImageRenderer;
import club.heiqi.uilib.ui.image.HostImageRenderer;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.image.MinecraftHostImageRenderer;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemRenderer;

/** 运行时宿主图片安全包装边界测试。 */
public class UiRuntimeAdaptersTest {

    /** 自定义 renderer 注入时必须统一包装。 */
    @Test
    public void customRendererIsWrapped() {
        HostImageRenderer delegate = renderer();

        HostImageRenderer actual = UiRuntimeAdapters.empty()
                .withHostImageRenderer(delegate)
                .getHostImageRenderer();

        Assert.assertTrue(actual instanceof GuardedHostImageRenderer);
        Assert.assertNotSame(delegate, actual);
    }

    /** 已包装 renderer 再注入时必须保持同一实例，避免双重快照。 */
    @Test
    public void wrappedRendererInjectionIsIdempotent() {
        HostImageRenderer guarded = GuardedHostImageRenderer.wrap(renderer());

        HostImageRenderer actual = UiRuntimeAdapters.empty()
                .withHostImageRenderer(guarded)
                .getHostImageRenderer();

        Assert.assertSame(guarded, actual);
    }

    /** 复制其它适配器能力时不得重复包装现有宿主 renderer。 */
    @Test
    public void rebuildingAdaptersPreservesGuardIdentity() {
        UiRuntimeAdapters adapters = UiRuntimeAdapters.empty().withHostImageRenderer(renderer());
        HostImageRenderer guarded = adapters.getHostImageRenderer();

        UiRuntimeAdapters rebuilt = adapters.withInventorySlotGridItemRenderer((geometry, slots) -> { });

        Assert.assertSame(guarded, rebuilt.getHostImageRenderer());
    }

    /** Minecraft 默认 delegate 经过所有工厂共用的构造边界时也必须被包装。 */
    @Test
    public void constructorBoundaryWrapsMinecraftDefaultRenderer() throws Exception {
        Constructor<UiRuntimeAdapters> constructor = UiRuntimeAdapters.class.getDeclaredConstructor(
                InventorySlotGridItemRenderer.class, HostImageRenderer.class);
        constructor.setAccessible(true);

        UiRuntimeAdapters adapters = constructor.newInstance(null, new MinecraftHostImageRenderer());

        Assert.assertTrue(adapters.getHostImageRenderer() instanceof GuardedHostImageRenderer);
    }

    private static HostImageRenderer renderer() {
        return new HostImageRenderer() {
            @Override
            public void render(HostImageSource source, int left, int top, int right, int bottom) { }
        };
    }
}
