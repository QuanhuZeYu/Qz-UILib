package club.heiqi.uilib.ui.image;

import org.junit.Assert;
import org.junit.Test;

/** 完整状态围栏正常、异常与不可恢复分支测试。 */
public class HostImageGlStateGuardTest {
    @Test
    public void rendererFailureRecoveredReturnsRecoverableOutcome() {
        FakeStateAccess access = new FakeStateAccess();
        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(
                () -> { throw new IllegalStateException("bad item"); });
        Assert.assertFalse(outcome.isRendered());
        Assert.assertTrue(outcome.isRecovered());
        Assert.assertEquals("render", outcome.getStage());
        Assert.assertTrue(access.restored);
    }

    @Test
    public void driftFailsClosed() {
        FakeStateAccess access = new FakeStateAccess();
        access.drift = "program";
        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(() -> { });
        Assert.assertFalse(outcome.isRecovered());
        Assert.assertEquals("verify", outcome.getStage());
    }

    @Test
    public void nonIdleTessellatorNeverInvokesRenderer() {
        FakeStateAccess access = new FakeStateAccess();
        access.idle = false;
        int[] calls = {0};
        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(() -> calls[0]++);
        Assert.assertFalse(outcome.isRecovered());
        Assert.assertEquals(0, calls[0]);
    }

    private static final class FakeSnapshot implements HostImageGlStateGuard.Snapshot { }
    private static final class FakeStateAccess implements HostImageGlStateGuard.StateAccess {
        private boolean idle = true;
        private boolean restored;
        private String drift;
        @Override public boolean isTessellatorIdle() { return idle; }
        @Override public int consumeGlError() { return 0; }
        @Override public HostImageGlStateGuard.Snapshot capture() { return new FakeSnapshot(); }
        @Override public void restore(HostImageGlStateGuard.Snapshot snapshot) { restored = true; }
        @Override public String findDrift(HostImageGlStateGuard.Snapshot snapshot) { return drift; }
    }
}
