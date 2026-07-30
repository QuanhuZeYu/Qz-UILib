package club.heiqi.uilib.ui.render;

import club.heiqi.uilib.internal.image.HostImageResourceEpoch;
import club.heiqi.uilib.ui.image.HostImageRenderOutcome;
import club.heiqi.uilib.ui.image.HostImageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.Assert;
import org.junit.Test;

/** FBO 借用计数异常安全测试。 */
public class PaintContextCompositorTest {
    @Test
    public void ensureSizeFailureDoesNotIncrementBorrowCount() {
        FailingTarget target = new FailingTarget();
        PaintContextCompositor compositor = new PaintContextCompositor(() -> target);
        try {
            compositor.borrowIsolatedLayer(32, 32);
            Assert.fail("expected");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("ensure", expected.getMessage());
        }
        Assert.assertEquals(0, compositor.__getBorrowedLayerCount());
        Assert.assertEquals(0, compositor.__getPooledLayerCount());
        Assert.assertEquals(1, target.closeCalls);
    }

    @Test
    public void closeContinuesAndRetainsTheLayerThatFailedOutsideTheReusablePool() {
        ClosingTarget first = new ClosingTarget(1);
        ClosingTarget second = new ClosingTarget(0);
        ClosingTarget[] targets = {first, second};
        int[] next = {0};
        PaintContextCompositor compositor = new PaintContextCompositor(() -> targets[next[0]++]);
        compositor.borrowIsolatedLayer(32, 32);
        compositor.borrowIsolatedLayer(32, 32);

        try {
            compositor.close();
            Assert.fail("expected");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("close", expected.getMessage());
        }

        Assert.assertEquals(1, first.closeCalls);
        Assert.assertEquals(1, second.closeCalls);
        Assert.assertEquals(0, compositor.__getPooledLayerCount());
        Assert.assertEquals(1, compositor.__getPendingCloseLayerCount());

        compositor.close();
        Assert.assertEquals(2, first.closeCalls);
        Assert.assertEquals(0, compositor.__getPendingCloseLayerCount());
    }

    @Test
    public void failedBorrowCloseIsRetainedAndRetriedBeforeTheNextFrame() {
        EnsureAndCloseFailingTarget target = new EnsureAndCloseFailingTarget();
        PaintContextCompositor compositor = new PaintContextCompositor(() -> target);
        try {
            compositor.borrowIsolatedLayer(32, 32);
            Assert.fail("expected");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("ensure", expected.getMessage());
            Assert.assertEquals(1, expected.getSuppressed().length);
        }
        Assert.assertEquals(0, compositor.__getPooledLayerCount());
        Assert.assertEquals(1, compositor.__getPendingCloseLayerCount());

        compositor.beginFrame();

        Assert.assertEquals(2, target.closeCalls);
        Assert.assertEquals(0, compositor.__getPendingCloseLayerCount());
    }

    @Test
    public void fatalFailedBorrowCleanupBecomesPrimaryAndRetainsTheLayer() {
        AssertionError fatal = new AssertionError("fatal-close");
        FatalCloseTarget target = new FatalCloseTarget(fatal);
        PaintContextCompositor compositor = new PaintContextCompositor(() -> target);

        try {
            compositor.borrowIsolatedLayer(32, 32);
            Assert.fail("expected");
        } catch (AssertionError actual) {
            Assert.assertSame(fatal, actual);
            Assert.assertEquals(1, actual.getSuppressed().length);
            Assert.assertEquals("ensure", actual.getSuppressed()[0].getMessage());
        }

        Assert.assertEquals(1, compositor.__getPendingCloseLayerCount());
    }

    @Test
    public void resourceEpochChangeClearsPublishedItemRasters() {
        PaintContextCompositor compositor = new PaintContextCompositor(ClosingTarget::new);
        int[] closed = {0};
        compositor.getHostImageRenderSession().request(
                HostImageSource.itemIcon(new ItemStack(new Item())), 16,
                (source, side) -> new club.heiqi.uilib.ui.image.HostImageRenderSession.RasterizeResult(
                        () -> closed[0]++, HostImageRenderOutcome.publishable()));
        Assert.assertEquals(1, compositor.getHostImageRenderSession().getCacheSize());

        HostImageResourceEpoch.advance();
        compositor.beginFrame();

        Assert.assertEquals(0, compositor.getHostImageRenderSession().getCacheSize());
        Assert.assertEquals(1, closed[0]);
        compositor.close();
    }

    @Test
    public void resourceEpochCleanupFailureUsesFrameBarrierAndRetriesNextFrame() {
        PaintContextCompositor compositor = new PaintContextCompositor(ClosingTarget::new);
        int[] closeAttempts = {0};
        compositor.getHostImageRenderSession().request(
                HostImageSource.itemIcon(new ItemStack(new Item(), 1, 20)), 16,
                (source, side) -> new club.heiqi.uilib.ui.image.HostImageRenderSession.RasterizeResult(
                        () -> {
                            closeAttempts[0]++;
                            if (closeAttempts[0] == 1) throw new IllegalStateException("close-once");
                        }, HostImageRenderOutcome.publishable()));
        HostImageResourceEpoch.advance();

        compositor.beginFrame();
        club.heiqi.uilib.ui.image.HostImageRenderSession.RequestResult blocked =
                compositor.getHostImageRenderSession().request(
                        HostImageSource.itemIcon(new ItemStack(new Item(), 1, 21)), 16,
                        (source, side) -> new club.heiqi.uilib.ui.image.HostImageRenderSession.RasterizeResult(
                                () -> { }, HostImageRenderOutcome.publishable()));

        Assert.assertEquals(
                club.heiqi.uilib.ui.image.HostImageRenderSession.RequestResult.Status.ABORT_FRAME,
                blocked.getStatus());
        compositor.beginFrame();
        Assert.assertEquals(2, closeAttempts[0]);
        Assert.assertFalse(compositor.getHostImageRenderSession().hasPendingCleanup());
        compositor.close();
    }

    private static final class FailingTarget extends UiRenderTarget {
        private int closeCalls;
        @Override public void ensureSize(int width, int height) { throw new IllegalStateException("ensure"); }
        @Override public void close() { closeCalls++; }
    }

    private static final class ClosingTarget extends UiRenderTarget {
        private int failuresRemaining;
        private int closeCalls;

        private ClosingTarget(int failuresRemaining) { this.failuresRemaining = failuresRemaining; }
        private ClosingTarget() { this(0); }
        @Override public void ensureSize(int width, int height) { }
        @Override public void close() {
            closeCalls++;
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new IllegalStateException("close");
            }
        }
    }

    private static final class EnsureAndCloseFailingTarget extends UiRenderTarget {
        private int closeCalls;
        @Override public void ensureSize(int width, int height) { throw new IllegalStateException("ensure"); }
        @Override public void close() {
            closeCalls++;
            if (closeCalls == 1) throw new IllegalStateException("close");
        }
    }

    private static final class FatalCloseTarget extends UiRenderTarget {
        private final AssertionError fatal;
        private FatalCloseTarget(AssertionError fatal) { this.fatal = fatal; }
        @Override public void ensureSize(int width, int height) { throw new IllegalStateException("ensure"); }
        @Override public void close() { throw fatal; }
    }
}
