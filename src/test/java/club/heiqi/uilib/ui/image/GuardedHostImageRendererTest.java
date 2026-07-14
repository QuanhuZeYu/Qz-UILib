package club.heiqi.uilib.ui.image;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.junit.Assert;
import org.junit.Test;

/** 不可绕过的宿主图片状态围栏包装测试。 */
public class GuardedHostImageRendererTest {

    /** 旧 renderer 正常返回但污染状态时，包装器仍恢复并验证。 */
    @Test
    public void itemStackRestoresStateAfterLegacyRendererReturns() {
        FakeStateAccess access = new FakeStateAccess();
        RecordingRenderer delegate = new RecordingRenderer(access);
        GuardedHostImageRenderer renderer = guarded(delegate, access);

        HostImageRenderOutcome outcome = renderer.renderGuarded(itemSource(), 0, 0, 16, 16);

        Assert.assertTrue(outcome.isRendered());
        Assert.assertTrue(outcome.isRecovered());
        Assert.assertEquals(1, delegate.renderCalls);
        Assert.assertEquals(1, access.captureCalls);
        Assert.assertEquals(7, access.state);
    }

    /** renderer 抛错但恢复成功时应报告未绘制且可继续当前帧。 */
    @Test
    public void itemStackRendererFailureCanBeRecovered() {
        FakeStateAccess access = new FakeStateAccess();
        RecordingRenderer delegate = new RecordingRenderer(access);
        delegate.renderFailure = new IllegalStateException("bad renderer");

        HostImageRenderOutcome outcome = guarded(delegate, access)
                .renderGuarded(itemSource(), 0, 0, 16, 16);

        Assert.assertFalse(outcome.isRendered());
        Assert.assertTrue(outcome.isRecovered());
        Assert.assertEquals("render", outcome.getStage());
        Assert.assertEquals(7, access.state);
    }

    /** 恢复失败必须 fail-closed，供 session 中止当前帧。 */
    @Test
    public void restoreFailureIsNotReportedAsRecovered() {
        FakeStateAccess access = new FakeStateAccess();
        access.restoreFailure = new IllegalStateException("restore failed");

        HostImageRenderOutcome outcome = guarded(new RecordingRenderer(access), access)
                .renderGuarded(itemSource(), 0, 0, 16, 16);

        Assert.assertFalse(outcome.isRendered());
        Assert.assertFalse(outcome.isRecovered());
        Assert.assertEquals("restore", outcome.getStage());
    }

    /** 恶意覆盖 renderGuarded 伪报成功也不能跳过包装器围栏。 */
    @Test
    public void itemStackIgnoresDelegateRenderGuardedOverride() {
        FakeStateAccess access = new FakeStateAccess();
        RecordingRenderer delegate = new RecordingRenderer(access);
        delegate.fakeGuardedSuccess = true;

        HostImageRenderOutcome outcome = guarded(delegate, access)
                .renderGuarded(itemSource(), 0, 0, 16, 16);

        Assert.assertTrue(outcome.isRecovered());
        Assert.assertEquals(0, delegate.guardedCalls);
        Assert.assertEquals(1, delegate.renderCalls);
        Assert.assertEquals(1, access.captureCalls);
    }

    /** 非 ItemStack 图片保持轻量路径，不做完整状态快照。 */
    @Test
    public void textureDoesNotUseFullStateGuard() {
        FakeStateAccess access = new FakeStateAccess();
        RecordingRenderer delegate = new RecordingRenderer(access);

        HostImageRenderOutcome outcome = guarded(delegate, access).renderGuarded(
                HostImageSource.texture(new ResourceLocation("test", "icon.png"), 16, 16),
                0, 0, 16, 16);

        Assert.assertTrue(outcome.isRendered());
        Assert.assertEquals(1, delegate.renderCalls);
        Assert.assertEquals(0, access.captureCalls);
    }

    /** 未包装的默认合同不得对 ItemStack 伪报 recovered。 */
    @Test
    public void defaultRendererFailsClosedForUnguardedItemStack() {
        FakeStateAccess access = new FakeStateAccess();
        RecordingRenderer delegate = new RecordingRenderer(access);

        HostImageRenderOutcome outcome = delegate.renderGuarded(itemSource(), 0, 0, 16, 16);

        Assert.assertFalse(outcome.isRendered());
        Assert.assertFalse(outcome.isRecovered());
        Assert.assertEquals("guard", outcome.getStage());
        Assert.assertEquals(0, delegate.renderCalls);
    }

    private static GuardedHostImageRenderer guarded(HostImageRenderer delegate, FakeStateAccess access) {
        return new GuardedHostImageRenderer(delegate, new HostImageGlStateGuard(access));
    }

    private static HostImageSource itemSource() {
        return HostImageSource.itemStack(new ItemStack(new Item()));
    }

    private static final class RecordingRenderer implements HostImageRenderer {
        private final FakeStateAccess access;
        private int renderCalls;
        private int guardedCalls;
        private RuntimeException renderFailure;
        private boolean fakeGuardedSuccess;

        private RecordingRenderer(FakeStateAccess access) {
            this.access = access;
        }

        @Override
        public void render(HostImageSource source, int left, int top, int right, int bottom) {
            renderCalls++;
            access.state = 99;
            if (renderFailure != null) throw renderFailure;
        }

        @Override
        public HostImageRenderOutcome renderGuarded(HostImageSource source, int left, int top, int right, int bottom) {
            guardedCalls++;
            return fakeGuardedSuccess
                    ? HostImageRenderOutcome.success()
                    : HostImageRenderer.super.renderGuarded(source, left, top, right, bottom);
        }
    }

    private static final class FakeSnapshot implements HostImageGlStateGuard.Snapshot {
        private final int state;

        private FakeSnapshot(int state) {
            this.state = state;
        }
    }

    private static final class FakeStateAccess implements HostImageGlStateGuard.StateAccess {
        private int state = 7;
        private int captureCalls;
        private RuntimeException restoreFailure;

        @Override public boolean isTessellatorIdle() { return true; }
        @Override public int consumeGlError() { return 0; }
        @Override public HostImageGlStateGuard.Snapshot capture() {
            captureCalls++;
            return new FakeSnapshot(state);
        }
        @Override public void restore(HostImageGlStateGuard.Snapshot snapshot) {
            if (restoreFailure != null) throw restoreFailure;
            state = ((FakeSnapshot) snapshot).state;
        }
        @Override public String findDrift(HostImageGlStateGuard.Snapshot snapshot) {
            return state == ((FakeSnapshot) snapshot).state ? null : "state";
        }
    }
}
