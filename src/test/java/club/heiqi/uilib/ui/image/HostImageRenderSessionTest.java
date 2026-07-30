package club.heiqi.uilib.ui.image;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.Assert;
import org.junit.Test;

/** 跨帧预算、identity key、公平补图、LRU 与关闭测试。 */
public class HostImageRenderSessionTest {
    @Test
    public void budgetIsTwoAndDeferredSourcesAreFilledFairly() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(128, 2, 2_000_000L, clock);
        List<HostImageSource> sources = new ArrayList<HostImageSource>();
        for (int i = 0; i < 5; i++) sources.add(source(i));
        CountingRasterizer rasterizer = new CountingRasterizer();
        for (HostImageSource source : sources) session.request(source, 16, rasterizer);
        Assert.assertEquals(2, rasterizer.calls);
        session.beginFrame();
        for (HostImageSource source : sources) session.request(source, 16, rasterizer);
        Assert.assertEquals(4, rasterizer.calls);
        session.beginFrame();
        for (HostImageSource source : sources) session.request(source, 16, rasterizer);
        Assert.assertEquals(5, rasterizer.calls);
        Assert.assertEquals(5, session.getCacheSize());
    }

    @Test
    public void keyUsesSourceIdentityAndSingleSquareSideAndLruClosesEviction() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(2, 10, Long.MAX_VALUE, clock);
        CountingRasterizer rasterizer = new CountingRasterizer();
        HostImageSource first = source(1);
        session.request(first, 16, rasterizer);
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.CACHE_HIT,
                session.request(first, 16, rasterizer).getStatus());
        session.request(first, 32, rasterizer);
        session.request(source(1), 16, rasterizer);
        Assert.assertEquals(3, rasterizer.calls);
        Assert.assertEquals(java.util.Arrays.asList(16, 32, 16), rasterizer.rasterSides);
        Assert.assertEquals(2, session.getCacheSize());
        Assert.assertEquals(1, rasterizer.closed);
        session.close();
        Assert.assertEquals(3, rasterizer.closed);
    }

    @Test
    public void failedRecoveryAbortsAndRetriesTheNextFrame() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 2, Long.MAX_VALUE, clock);
        HostImageSource source = source(0);
        int[] calls = {0};
        HostImageRenderSession.Rasterizer failing = (ignored, side) -> {
            calls[0]++;
            return new HostImageRenderSession.RasterizeResult(null,
                    HostImageRenderOutcome.hostStateLost("verify", null, "drift"));
        };
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.ABORT_FRAME,
                session.request(source, 16, failing).getStatus());
        session.beginFrame();
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.ABORT_FRAME,
                session.request(source, 16, failing).getStatus());
        Assert.assertEquals(2, calls[0]);
    }

    @Test
    public void cooldownDoesNotOccupyPendingHead() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 1, Long.MAX_VALUE, clock);
        HostImageSource failed = source(0);
        HostImageSource next = source(1);
        session.request(failed, 16, (ignored, side) -> new HostImageRenderSession.RasterizeResult(null,
                HostImageRenderOutcome.unavailable("render", null, "expected")));
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.PLACEHOLDER,
                session.request(next, 16, new CountingRasterizer()).getStatus());

        session.beginFrame();
        CountingRasterizer rasterizer = new CountingRasterizer();
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.UNAVAILABLE,
                session.request(failed, 16, rasterizer).getStatus());
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.RASTERIZED,
                session.request(next, 16, rasterizer).getStatus());
        Assert.assertEquals(1, rasterizer.calls);
        Assert.assertEquals(0, session.getPendingCount());
    }

    @Test
    public void expiredCooldownsAreRemovedEvenWhenTheSourceDoesNotReturn() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 2, Long.MAX_VALUE, clock);
        session.request(source(0), 16, (ignored, side) -> new HostImageRenderSession.RasterizeResult(
                null, HostImageRenderOutcome.unavailable("render", null, "expected")));
        Assert.assertEquals(1, session.getFailureCooldownCount());

        clock.now = HostImageRenderSession.FAILURE_COOLDOWN_NANOS;
        session.beginFrame();

        Assert.assertEquals(0, session.getFailureCooldownCount());
    }

    @Test
    public void cooldownTableIsBoundedBySessionCapacity() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(2, 10, Long.MAX_VALUE, clock);
        for (int i = 0; i < 4; i++) {
            session.request(source(i), 16, (ignored, side) -> new HostImageRenderSession.RasterizeResult(
                    null, HostImageRenderOutcome.unavailable("render", null, "expected")));
        }

        Assert.assertEquals(2, session.getFailureCooldownCount());
    }

    @Test
    public void sourceInvisibleForACompleteFrameIsRemovedBeforeNextBudget() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 1, Long.MAX_VALUE, clock);
        CountingRasterizer rasterizer = new CountingRasterizer();
        HostImageSource stale = source(2);
        HostImageSource visible = source(3);
        session.request(source(0), 16, rasterizer);
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.PLACEHOLDER,
                session.request(stale, 16, rasterizer).getStatus());

        session.beginFrame();
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.PLACEHOLDER,
                session.request(visible, 16, rasterizer).getStatus());
        session.beginFrame();
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.RASTERIZED,
                session.request(visible, 16, rasterizer).getStatus());
        Assert.assertEquals(2, rasterizer.calls);
        Assert.assertEquals(0, session.getPendingCount());
    }

    @Test
    public void onlyPublishableNonNullRasterIsCachedAndClearReleasesIt() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 4, Long.MAX_VALUE, clock);
        HostImageSource missingRaster = source(4);

        HostImageRenderSession.RequestResult missing = session.request(missingRaster, 16,
                (ignored, side) -> new HostImageRenderSession.RasterizeResult(
                        null, HostImageRenderOutcome.publishable()));

        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.UNAVAILABLE, missing.getStatus());
        Assert.assertTrue(missing.getOutcome().isUnavailable());
        Assert.assertEquals(0, session.getCacheSize());

        int[] discarded = {0};
        HostImageRenderSession.RequestResult unavailable = session.request(source(6), 16,
                (ignored, side) -> new HostImageRenderSession.RasterizeResult(
                        () -> discarded[0]++,
                        HostImageRenderOutcome.unavailable("render", null, "unsupported")));
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.UNAVAILABLE, unavailable.getStatus());
        Assert.assertEquals(1, discarded[0]);
        Assert.assertEquals(0, session.getCacheSize());

        CountingRasterizer rasterizer = new CountingRasterizer();
        HostImageRenderSession.RequestResult rendered = session.request(source(5), 16, rasterizer);
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.RASTERIZED, rendered.getStatus());
        session.clear();
        Assert.assertEquals(0, session.getCacheSize());
        Assert.assertEquals(1, rasterizer.closed);
    }

    @Test
    public void clearContinuesClosingAllRastersAndPropagatesTheFirstFailure() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 4, Long.MAX_VALUE, clock);
        int[] secondClosed = {0};
        session.request(source(7), 16, (ignored, side) -> new HostImageRenderSession.RasterizeResult(
                () -> { throw new IllegalStateException("close-first"); },
                HostImageRenderOutcome.publishable()));
        session.request(source(8), 16, (ignored, side) -> new HostImageRenderSession.RasterizeResult(
                () -> secondClosed[0]++, HostImageRenderOutcome.publishable()));

        try {
            session.clear();
            Assert.fail("expected");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("close-first", expected.getMessage());
        }

        Assert.assertEquals(1, secondClosed[0]);
        Assert.assertEquals(0, session.getCacheSize());
    }

    @Test
    public void discardCloseFailureAbortsAndRetainsRasterForCleanupRetry() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 4, Long.MAX_VALUE, clock);
        int[] closeAttempts = {0};
        HostImageRenderSession.CachedRaster failOnce = () -> {
            closeAttempts[0]++;
            if (closeAttempts[0] == 1) {
                throw new IllegalStateException("close-once");
            }
        };

        HostImageRenderSession.RequestResult result = session.request(source(9), 16,
                (ignored, side) -> new HostImageRenderSession.RasterizeResult(
                        failOnce, HostImageRenderOutcome.unavailable("render", null, "unsupported")));

        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.ABORT_FRAME, result.getStatus());
        Assert.assertTrue(result.getOutcome().isHostStateLost());
        Assert.assertEquals(1, session.getPendingCleanupCount());
        session.beginFrame();
        Assert.assertEquals(2, closeAttempts[0]);
        Assert.assertEquals(0, session.getPendingCleanupCount());
    }

    @Test
    public void failedCleanupIsRetriedAtFrameStartAndBlocksNewRasterization() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 4, Long.MAX_VALUE, clock);
        int[] closeAttempts = {0};
        HostImageRenderSession.CachedRaster neverCloses = () -> {
            closeAttempts[0]++;
            throw new IllegalStateException("still-open");
        };
        session.request(source(10), 16,
                (ignored, side) -> new HostImageRenderSession.RasterizeResult(
                        neverCloses, HostImageRenderOutcome.unavailable("render", null, "unsupported")));

        session.beginFrame();
        CountingRasterizer rasterizer = new CountingRasterizer();
        HostImageRenderSession.RequestResult blocked = session.request(source(11), 16, rasterizer);

        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.ABORT_FRAME, blocked.getStatus());
        Assert.assertTrue(blocked.getOutcome().isHostStateLost());
        Assert.assertEquals(0, rasterizer.calls);
        Assert.assertEquals(2, closeAttempts[0]);
        Assert.assertEquals(1, session.getPendingCleanupCount());
    }

    @Test
    public void pendingCleanupDoesNotBlockAnExistingTrustedCacheHit() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 4, Long.MAX_VALUE, clock);
        HostImageSource cachedSource = source(12);
        CountingRasterizer cachedRasterizer = new CountingRasterizer();
        session.request(cachedSource, 16, cachedRasterizer);
        session.request(source(13), 16,
                (ignored, side) -> new HostImageRenderSession.RasterizeResult(
                        () -> { throw new IllegalStateException("still-open"); },
                        HostImageRenderOutcome.unavailable("render", null, "unsupported")));

        session.beginFrame();
        HostImageRenderSession.RequestResult result = session.request(cachedSource, 16, cachedRasterizer);

        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.CACHE_HIT, result.getStatus());
        Assert.assertEquals(1, cachedRasterizer.calls);
        Assert.assertEquals(1, session.getPendingCleanupCount());
    }

    @Test
    public void fatalCleanupErrorIsRetainedAndRethrownByIdentity() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 4, Long.MAX_VALUE, clock);
        AssertionError fatal = new AssertionError("fatal-close");

        try {
            session.request(source(14), 16,
                    (ignored, side) -> new HostImageRenderSession.RasterizeResult(
                            () -> { throw fatal; },
                            HostImageRenderOutcome.unavailable("render", null, "unsupported")));
            Assert.fail("expected");
        } catch (AssertionError actual) {
            Assert.assertSame(fatal, actual);
        }

        Assert.assertEquals(1, session.getPendingCleanupCount());
    }

    private static HostImageSource source(int damage) {
        return HostImageSource.itemIcon(new ItemStack(new Item(), 1, damage));
    }

    private static final class MutableClock implements HostImageRenderSession.NanoClock {
        private long now;
        @Override public long nanoTime() { return now; }
    }

    private static final class CountingRasterizer implements HostImageRenderSession.Rasterizer {
        private int calls;
        private int closed;
        private final List<Integer> rasterSides = new ArrayList<Integer>();
        @Override public HostImageRenderSession.RasterizeResult rasterize(HostImageSource source, int rasterSide) {
            calls++;
            rasterSides.add(Integer.valueOf(rasterSide));
            return new HostImageRenderSession.RasterizeResult(
                    () -> closed++, HostImageRenderOutcome.publishable());
        }
    }
}
