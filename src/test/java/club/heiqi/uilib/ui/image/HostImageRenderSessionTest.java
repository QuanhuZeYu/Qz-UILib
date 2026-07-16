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
        for (HostImageSource source : sources) session.request(source, 16, 16, rasterizer);
        Assert.assertEquals(2, rasterizer.calls);
        session.beginFrame();
        for (HostImageSource source : sources) session.request(source, 16, 16, rasterizer);
        Assert.assertEquals(4, rasterizer.calls);
        session.beginFrame();
        for (HostImageSource source : sources) session.request(source, 16, 16, rasterizer);
        Assert.assertEquals(5, rasterizer.calls);
        Assert.assertEquals(5, session.getCacheSize());
    }

    @Test
    public void keyUsesSourceIdentityAndRasterSizeAndLruClosesEviction() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(2, 10, Long.MAX_VALUE, clock);
        CountingRasterizer rasterizer = new CountingRasterizer();
        HostImageSource first = source(1);
        session.request(first, 16, 16, rasterizer);
        session.request(first, 32, 16, rasterizer);
        session.request(source(1), 16, 16, rasterizer);
        Assert.assertEquals(3, rasterizer.calls);
        Assert.assertEquals(2, session.getCacheSize());
        Assert.assertEquals(1, rasterizer.closed);
        session.close();
        Assert.assertEquals(3, rasterizer.closed);
    }

    @Test
    public void failedRecoveryAbortsAndCooldownAvoidsRepeatedCall() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 2, Long.MAX_VALUE, clock);
        HostImageSource source = source(0);
        int[] calls = {0};
        HostImageRenderSession.Rasterizer failing = (ignored, w, h) -> {
            calls[0]++;
            return new HostImageRenderSession.RasterizeResult(null,
                    HostImageRenderOutcome.failure("verify", null, false, "drift"));
        };
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.ABORT_FRAME,
                session.request(source, 16, 16, failing).getStatus());
        session.beginFrame();
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.FAILED_RECOVERED,
                session.request(source, 16, 16, failing).getStatus());
        Assert.assertEquals(1, calls[0]);
    }

    @Test
    public void cooldownDoesNotOccupyPendingHead() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 1, Long.MAX_VALUE, clock);
        HostImageSource failed = source(0);
        HostImageSource next = source(1);
        session.request(failed, 16, 16, (ignored, w, h) -> new HostImageRenderSession.RasterizeResult(null,
                HostImageRenderOutcome.failure("render", null, true, "expected")));
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.PLACEHOLDER,
                session.request(next, 16, 16, new CountingRasterizer()).getStatus());

        session.beginFrame();
        CountingRasterizer rasterizer = new CountingRasterizer();
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.FAILED_RECOVERED,
                session.request(failed, 16, 16, rasterizer).getStatus());
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.RASTERIZED,
                session.request(next, 16, 16, rasterizer).getStatus());
        Assert.assertEquals(1, rasterizer.calls);
        Assert.assertEquals(0, session.getPendingCount());
    }

    @Test
    public void sourceInvisibleForACompleteFrameIsRemovedBeforeNextBudget() {
        MutableClock clock = new MutableClock();
        HostImageRenderSession session = new HostImageRenderSession(4, 1, Long.MAX_VALUE, clock);
        CountingRasterizer rasterizer = new CountingRasterizer();
        HostImageSource stale = source(2);
        HostImageSource visible = source(3);
        session.request(source(0), 16, 16, rasterizer);
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.PLACEHOLDER,
                session.request(stale, 16, 16, rasterizer).getStatus());

        session.beginFrame();
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.PLACEHOLDER,
                session.request(visible, 16, 16, rasterizer).getStatus());
        session.beginFrame();
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.RASTERIZED,
                session.request(visible, 16, 16, rasterizer).getStatus());
        Assert.assertEquals(2, rasterizer.calls);
        Assert.assertEquals(0, session.getPendingCount());
    }

    private static HostImageSource source(int damage) {
        return HostImageSource.itemStackSnapshot(new ItemStack(new Item(), 1, damage));
    }

    private static final class MutableClock implements HostImageRenderSession.NanoClock {
        private long now;
        @Override public long nanoTime() { return now; }
    }

    private static final class CountingRasterizer implements HostImageRenderSession.Rasterizer {
        private int calls;
        private int closed;
        @Override public HostImageRenderSession.RasterizeResult rasterize(HostImageSource source, int w, int h) {
            calls++;
            return new HostImageRenderSession.RasterizeResult(() -> closed++, HostImageRenderOutcome.success());
        }
    }
}
