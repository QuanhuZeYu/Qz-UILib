package club.heiqi.uilib.ui.runtime;

import net.minecraft.item.ItemStack;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.image.HostImageRenderer;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.image.ItemIconRenderer;
import club.heiqi.uilib.ui.image.MinecraftHostImageRenderer;

/** 普通 image/item guard 分离及 adapter 资源所有权测试。 */
public class UiRuntimeAdaptersTest {

    @Test
    public void plainRendererInjectionKeepsLightweightDelegateIdentity() {
        HostImageRenderer delegate = new RecordingHostImageRenderer();

        HostImageRenderer actual = UiRuntimeAdapters.empty()
                .withHostImageRenderer(delegate)
                .getHostImageRenderer();

        Assert.assertSame(delegate, actual);
        Assert.assertFalse(actual instanceof ItemIconRenderer);
    }

    @Test
    public void customItemRendererRemainsTheContentDelegate() {
        ItemIconRenderer delegate = itemRenderer();

        ItemIconRenderer actual = UiRuntimeAdapters.empty()
                .withItemIconRenderer(delegate)
                .getItemIconRenderer();

        Assert.assertSame(delegate, actual);
    }

    @Test
    public void rebuildingAdaptersPreservesItemIdentityAndPlainSeparation() {
        RecordingHostImageRenderer plain = new RecordingHostImageRenderer();
        UiRuntimeAdapters adapters = UiRuntimeAdapters.empty()
                .withHostImageRenderer(plain)
                .withItemIconRenderer(itemRenderer());
        ItemIconRenderer item = adapters.getItemIconRenderer();

        UiRuntimeAdapters rebuilt = adapters.withHostImageRenderer(new RecordingHostImageRenderer());

        Assert.assertSame(item, rebuilt.getItemIconRenderer());
        Assert.assertNotSame(plain, rebuilt.getHostImageRenderer());
    }

    @Test
    public void minecraftDefaultsCreateSeparatePlainAndItemRenderers() {
        UiRuntimeAdapters adapters = UiRuntimeAdapters.minecraftDefaults();
        try {
            Assert.assertTrue(adapters.getHostImageRenderer() instanceof MinecraftHostImageRenderer);
            Assert.assertTrue(adapters.getItemIconRenderer()
                    instanceof club.heiqi.uilib.ui.image.MinecraftItemIconRenderer);
        } finally {
            adapters.close();
        }
    }

    @Test
    public void closeReleasesOnlyInternallyOwnedPlainRenderer() {
        RecordingHostImageRenderer owned = new RecordingHostImageRenderer();
        RecordingHostImageRenderer injected = new RecordingHostImageRenderer();
        UiRuntimeAdapters adapters = new UiRuntimeAdapters(owned, itemRenderer(), owned)
                .withHostImageRenderer(injected);

        adapters.close();

        Assert.assertEquals(1, owned.closeCalls);
        Assert.assertEquals("用户注入/shared renderer 不得被 adapter 关闭", 0, injected.closeCalls);
    }

    @Test
    public void emptyAdapterNeverOwnsInjectedRenderer() {
        RecordingHostImageRenderer injected = new RecordingHostImageRenderer();
        UiRuntimeAdapters adapters = UiRuntimeAdapters.empty().withHostImageRenderer(injected);

        adapters.close();

        Assert.assertEquals(0, injected.closeCalls);
    }

    @Test
    public void fluentAliasesShareOneExactlyOnceOwnedLifecycle() {
        RecordingHostImageRenderer owned = new RecordingHostImageRenderer();
        UiRuntimeAdapters source = new UiRuntimeAdapters(owned, itemRenderer(), owned);
        UiRuntimeAdapters derived = source.withItemIconRenderer(itemRenderer());

        derived.close();
        source.close();

        Assert.assertEquals(1, owned.closeCalls);
        try {
            source.getHostImageRenderer();
            Assert.fail("expected");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("runtime adapters already closed", expected.getMessage());
        }
    }

    @Test
    public void failedOwnedCloseCanBeRetriedThroughAnyAlias() {
        RecordingHostImageRenderer owned = new RecordingHostImageRenderer();
        owned.closeFailuresRemaining = 1;
        UiRuntimeAdapters source = new UiRuntimeAdapters(owned, itemRenderer(), owned);
        UiRuntimeAdapters derived = source.withItemIconRenderer(itemRenderer());

        try {
            source.close();
            Assert.fail("expected");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("close-once", expected.getMessage());
        }
        derived.close();

        Assert.assertEquals(2, owned.closeCalls);
    }

    @Test
    public void fatalOwnedCloseIsRethrownByIdentityAndRemainsRetryable() {
        RecordingHostImageRenderer owned = new RecordingHostImageRenderer();
        AssertionError fatal = new AssertionError("fatal-close");
        owned.fatalCloseFailure = fatal;
        UiRuntimeAdapters adapters = new UiRuntimeAdapters(owned, itemRenderer(), owned);

        try {
            adapters.close();
            Assert.fail("expected");
        } catch (AssertionError actual) {
            Assert.assertSame(fatal, actual);
        }
        owned.fatalCloseFailure = null;
        adapters.close();

        Assert.assertEquals(2, owned.closeCalls);
    }

    private static ItemIconRenderer itemRenderer() {
        return (ItemStack stack, int left, int top, int side) -> { };
    }

    private static final class RecordingHostImageRenderer implements HostImageRenderer {
        private int closeCalls;
        private int closeFailuresRemaining;
        private AssertionError fatalCloseFailure;

        @Override
        public void render(HostImageSource source, int left, int top, int right, int bottom) { }

        @Override
        public void close() {
            closeCalls++;
            if (fatalCloseFailure != null) throw fatalCloseFailure;
            if (closeFailuresRemaining > 0) {
                closeFailuresRemaining--;
                throw new IllegalStateException("close-once");
            }
        }
    }
}
