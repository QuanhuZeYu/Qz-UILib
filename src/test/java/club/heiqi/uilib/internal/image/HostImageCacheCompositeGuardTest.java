package club.heiqi.uilib.internal.image;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.image.HostImageGlStateGuard;
import club.heiqi.uilib.ui.image.HostImageRenderOutcome;

/** cache-hit 窄围栏的 typed outcome 与 fatal cleanup 契约测试。 */
public class HostImageCacheCompositeGuardTest {

    @Test
    public void cleanCompositePublishesAfterRestoreAndVerification() {
        FakeStateAccess access = new FakeStateAccess();
        int[] calls = {0};

        HostImageRenderOutcome outcome = new HostImageCacheCompositeGuard(access).run(() -> calls[0]++);

        Assert.assertTrue(outcome.isPublishable());
        Assert.assertEquals(1, calls[0]);
        Assert.assertEquals(1, access.captureCalls);
        Assert.assertEquals(1, access.restoreCalls);
        Assert.assertEquals(1, access.verifyCalls);
    }

    @Test
    public void unavailableFenceDoesNotInvokeComposite() {
        FakeStateAccess access = new FakeStateAccess();
        access.unavailableReason = "client-attrib-stack-unavailable";
        int[] calls = {0};

        HostImageRenderOutcome outcome = new HostImageCacheCompositeGuard(access).run(() -> calls[0]++);

        Assert.assertTrue(outcome.isUnavailable());
        Assert.assertEquals(0, calls[0]);
        Assert.assertEquals(1, access.restoreCalls);
        Assert.assertEquals(0, access.verifyCalls);
    }

    @Test
    public void fatalCompositeErrorIsRethrownOnlyAfterTrustedRestore() {
        FakeStateAccess access = new FakeStateAccess();
        AssertionError fatal = new AssertionError("fatal");

        try {
            new HostImageCacheCompositeGuard(access).run(() -> { throw fatal; });
            Assert.fail("expected");
        } catch (AssertionError actual) {
            Assert.assertSame(fatal, actual);
        }

        Assert.assertEquals(1, access.restoreCalls);
        Assert.assertEquals(1, access.verifyCalls);
    }

    private static final class FakeStateAccess implements HostImageGlStateGuard.StateAccess {
        private final HostImageGlStateGuard.Snapshot snapshot = new HostImageGlStateGuard.Snapshot() { };
        private String unavailableReason;
        private int captureCalls;
        private int restoreCalls;
        private int verifyCalls;

        @Override public boolean isTessellatorIdle() { return true; }
        @Override public int consumeGlError() { return GL11.GL_NO_ERROR; }
        @Override public HostImageGlStateGuard.Snapshot capture() {
            captureCalls++;
            return snapshot;
        }
        @Override public void restore(HostImageGlStateGuard.Snapshot ignored) { restoreCalls++; }
        @Override public String findDrift(HostImageGlStateGuard.Snapshot ignored) {
            verifyCalls++;
            return null;
        }
        @Override public String unavailableReason(HostImageGlStateGuard.Snapshot ignored) {
            return unavailableReason;
        }
    }
}
