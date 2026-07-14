package club.heiqi.uilib.ui.image;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

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

    /** 四个稳定阶段均可归因首个 GL error，且错误已消费仍不可恢复。 */
    @Test
    public void attributesFirstGlErrorToEachStablePhase() {
        assertTrackedPhase("capture", 1);
        assertTrackedPhase("delegate", 2);
        assertTrackedPhase("restore", 3);
        assertTrackedPhase("verify", 4);
    }

    /** operation 首错锁存后不被后续检查覆盖。 */
    @Test
    public void trackerLatchesFirstOperationAndStopsConsuming() {
        final Queue<Integer> errors = new ArrayDeque<Integer>(Arrays.asList(
                GL11.GL_INVALID_ENUM, GL11.GL_INVALID_OPERATION));
        HostImageGlErrorTracker.begin(errors::remove);
        try {
            HostImageGlErrorTracker.enterPhase("delegate");
            HostImageGlErrorTracker.checkpoint("item.render-effect");
            HostImageGlErrorTracker.enterPhase("restore");
            HostImageGlErrorTracker.checkpoint("restore.matrix-depths");
            HostImageGlErrorTracker.FirstError first = HostImageGlErrorTracker.firstError();
            Assert.assertEquals("delegate", first.getPhase());
            Assert.assertEquals("item.render-effect", first.getOperation());
            Assert.assertEquals(GL11.GL_INVALID_ENUM, first.getError());
            Assert.assertEquals("首错后不得继续消费或覆盖", 1, errors.size());
        } finally {
            HostImageGlErrorTracker.end();
        }
    }

    /** 围栏所有返回路径均清理线程局部 tracker。 */
    @Test
    public void clearsTrackerAfterRun() {
        new HostImageGlStateGuard(new FakeStateAccess()).run(() -> { });
        Assert.assertFalse(HostImageGlErrorTracker.isActive());
        FakeStateAccess failingAccess = new FakeStateAccess();
        failingAccess.captureFailure = new IllegalStateException("capture failed");
        new HostImageGlStateGuard(failingAccess).run(() -> { });
        Assert.assertFalse(HostImageGlErrorTracker.isActive());
    }

    /** 入口已有错误在 tracker 启动前失败，不会被归入本次操作。 */
    @Test
    public void entryErrorIsNotConsumedByTracker() {
        SequencedStateAccess access = new SequencedStateAccess(GL11.GL_INVALID_ENUM);
        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(() -> { });
        Assert.assertEquals("precheck", outcome.getStage());
        Assert.assertEquals("entry-gl-error=" + GL11.GL_INVALID_ENUM, outcome.getDetail());
        Assert.assertFalse(HostImageGlErrorTracker.isActive());
    }

    private static void assertTrackedPhase(String phase, int errorConsumeIndex) {
        Integer[] errors = new Integer[errorConsumeIndex + 1];
        Arrays.fill(errors, Integer.valueOf(GL11.GL_NO_ERROR));
        errors[errorConsumeIndex] = Integer.valueOf(GL11.GL_INVALID_ENUM);
        SequencedStateAccess access = new SequencedStateAccess(errors);
        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(() -> { });
        Assert.assertFalse(outcome.isRecovered());
        Assert.assertEquals(phase, outcome.getStage());
        Assert.assertTrue(outcome.getDetail().startsWith("phase=" + phase + " operation="));
        Assert.assertTrue(outcome.getDetail().endsWith("gl-error=" + GL11.GL_INVALID_ENUM));
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

    @Test
    public void zeroTextureDepthDisablesOnlyTextureMatrixFence() {
        FakeTextureMatrixOperations operations = new FakeTextureMatrixOperations(0);
        HostImageGlStateGuard.TextureMatrixSnapshot snapshot =
                HostImageGlStateGuard.probeTextureMatrix(operations);

        HostImageGlStateGuard.captureTextureMatrix(operations, snapshot);
        HostImageGlStateGuard.restoreTextureMatrix(operations, snapshot);

        Assert.assertFalse(snapshot.isSupported());
        Assert.assertEquals(1, operations.depthQueries);
        Assert.assertEquals(0, operations.matrixReads);
        Assert.assertEquals(0, operations.pushes);
        Assert.assertEquals(0, operations.pops);
        Assert.assertFalse(HostImageGlStateGuard.hasTextureMatrixDrift(operations, snapshot));
    }

    @Test
    public void textureDepthQueryErrorDisablesFenceAndDrainsProbeErrors() {
        FakeTextureMatrixOperations operations = new FakeTextureMatrixOperations(4,
                GL11.GL_INVALID_ENUM, GL11.GL_INVALID_OPERATION, GL11.GL_NO_ERROR);
        HostImageGlStateGuard.TextureMatrixSnapshot snapshot =
                HostImageGlStateGuard.probeTextureMatrix(operations);

        HostImageGlStateGuard.captureTextureMatrix(operations, snapshot);

        Assert.assertFalse(snapshot.isSupported());
        Assert.assertEquals(3, operations.errorConsumes);
        Assert.assertEquals(0, operations.matrixReads);
        Assert.assertEquals(0, operations.pushes);
        Assert.assertEquals(GL11.GL_NO_ERROR, operations.consumeGlError());
    }

    @Test
    public void supportedTextureMatrixKeepsFullCaptureRestoreAndDriftProtection() {
        FakeTextureMatrixOperations operations = new FakeTextureMatrixOperations(3);
        operations.matrix[0] = 2.0F;
        HostImageGlStateGuard.TextureMatrixSnapshot snapshot =
                HostImageGlStateGuard.probeTextureMatrix(operations);

        HostImageGlStateGuard.captureTextureMatrix(operations, snapshot);
        Assert.assertTrue(snapshot.isSupported());
        Assert.assertEquals(4, operations.depth);
        Assert.assertFalse(HostImageGlStateGuard.hasTextureMatrixDrift(operations, snapshot));
        operations.matrix[0] = 3.0F;
        Assert.assertTrue(HostImageGlStateGuard.hasTextureMatrixDrift(operations, snapshot));
        operations.matrix[0] = 2.0F;
        HostImageGlStateGuard.restoreTextureMatrix(operations, snapshot);

        Assert.assertEquals(3, operations.depth);
        Assert.assertEquals(1, operations.pushes);
        Assert.assertEquals(1, operations.pops);
    }

    @Test
    public void supportedTextureMatrixRendererUnderflowRemainsUnrecovered() {
        FakeTextureMatrixOperations operations = new FakeTextureMatrixOperations(2);
        TextureFenceStateAccess access = new TextureFenceStateAccess(operations);

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(operations::popMatrix);

        Assert.assertFalse(outcome.isRecovered());
        Assert.assertEquals("restore", outcome.getStage());
        Assert.assertEquals("restore-failed", outcome.getDetail());
        Assert.assertTrue(outcome.getFailure() instanceof IllegalStateException);
    }

    /** Core Profile 的 server depth=0 只关闭 server attribute 子围栏。 */
    @Test
    public void zeroServerAttribDepthSkipsPushPop() {
        FakeAttribStackOperations operations = new FakeAttribStackOperations(0);
        HostImageGlStateGuard.AttribStackSnapshot snapshot =
                HostImageGlStateGuard.probeAttribStack(operations, "server-attrib");

        HostImageGlStateGuard.captureAttribStack(operations, snapshot);
        HostImageGlStateGuard.normalizeAttribStack(operations, snapshot);
        HostImageGlStateGuard.popAttribStack(operations, snapshot);

        Assert.assertFalse(snapshot.isSupported());
        Assert.assertEquals(0, operations.pushes);
        Assert.assertEquals(0, operations.pops);
    }

    /** server 深度查询错误须关闭子围栏并清空本次 probe 错误。 */
    @Test
    public void serverAttribQueryErrorDrainsProbeErrors() {
        FakeAttribStackOperations operations = new FakeAttribStackOperations(2,
                GL11.GL_INVALID_ENUM, GL11.GL_INVALID_OPERATION, GL11.GL_NO_ERROR);
        HostImageGlStateGuard.AttribStackSnapshot snapshot =
                HostImageGlStateGuard.probeAttribStack(operations, "server-attrib");

        Assert.assertFalse(snapshot.isSupported());
        Assert.assertEquals(3, operations.errorConsumes);
        Assert.assertEquals(GL11.GL_NO_ERROR, operations.consumeGlError());
        Assert.assertEquals(0, operations.pushes);
        Assert.assertEquals(0, operations.pops);
    }

    /** client 能力独立探测，depth=0 时不得调用 client push/pop。 */
    @Test
    public void zeroClientAttribDepthSkipsPushPop() {
        FakeAttribStackOperations operations = new FakeAttribStackOperations(0);
        HostImageGlStateGuard.AttribStackSnapshot snapshot =
                HostImageGlStateGuard.probeAttribStack(operations, "client-attrib");

        HostImageGlStateGuard.captureAttribStack(operations, snapshot);
        HostImageGlStateGuard.normalizeAttribStack(operations, snapshot);
        HostImageGlStateGuard.popAttribStack(operations, snapshot);

        Assert.assertFalse(snapshot.isSupported());
        Assert.assertEquals(0, operations.pushes);
        Assert.assertEquals(0, operations.pops);
    }

    /** 真正支持的 attribute stack 被 renderer 弹掉围栏帧时仍须 fail-closed。 */
    @Test
    public void supportedServerAttribUnderflowStillFailsClosed() {
        FakeAttribStackOperations operations = new FakeAttribStackOperations(2);
        AttribFenceStateAccess access = new AttribFenceStateAccess(operations);

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(operations::pop);

        Assert.assertFalse(outcome.isRecovered());
        Assert.assertEquals("restore", outcome.getStage());
        Assert.assertEquals("restore-failed", outcome.getDetail());
        Assert.assertTrue(outcome.getFailure() instanceof IllegalStateException);
        Assert.assertEquals("server-attrib stack underflow 2 < 3", outcome.getFailure().getMessage());
        Assert.assertEquals(1, operations.pushes);
        Assert.assertEquals(1, operations.pops);
    }

    /** 生产包装组合只有外层围栏；delegate 破坏其栈帧时必须中止当前帧。 */
    @Test
    public void guardedRendererAttribUnderflowAbortsFrameWithoutNestedRecovery() {
        FakeAttribStackOperations operations = new FakeAttribStackOperations(2);
        AttribFenceStateAccess access = new AttribFenceStateAccess(operations);
        HostImageRenderer delegate = (source, left, top, right, bottom) -> operations.pop();
        GuardedHostImageRenderer renderer = new GuardedHostImageRenderer(
                delegate, new HostImageGlStateGuard(access));
        HostImageSource source = HostImageSource.itemStack(new ItemStack(new Item()));

        HostImageRenderOutcome outcome = renderer.renderGuarded(source, 0, 0, 16, 16);
        HostImageRenderSession session = new HostImageRenderSession(1, 1, 1L, new IncrementingClock());
        HostImageRenderSession.RequestResult request = session.request(source, 16, 16,
                (ignoredSource, width, height) -> new HostImageRenderSession.RasterizeResult(null, outcome));

        Assert.assertFalse(outcome.isRecovered());
        Assert.assertEquals("restore", outcome.getStage());
        Assert.assertEquals("restore-failed", outcome.getDetail());
        Assert.assertEquals(HostImageRenderSession.RequestResult.Status.ABORT_FRAME, request.getStatus());
        Assert.assertEquals("外层只探测一次 attrib 能力", 1, operations.errorConsumes);
        Assert.assertEquals("只有外层压入围栏帧", 1, operations.pushes);
        Assert.assertEquals("delegate 弹栈后外层不得伪恢复", 1, operations.pops);
    }

    /** 普通 delegate 异常仍由唯一外层围栏恢复并按 render 阶段传播。 */
    @Test
    public void guardedRendererFailureRemainsRecoveredRenderFailure() {
        FakeAttribStackOperations operations = new FakeAttribStackOperations(2);
        AttribFenceStateAccess access = new AttribFenceStateAccess(operations);
        IllegalStateException failure = new IllegalStateException("renderer failed");
        HostImageRenderer delegate = (source, left, top, right, bottom) -> { throw failure; };
        GuardedHostImageRenderer renderer = new GuardedHostImageRenderer(
                delegate, new HostImageGlStateGuard(access));

        HostImageRenderOutcome outcome = renderer.renderGuarded(
                HostImageSource.itemStack(new ItemStack(new Item())), 0, 0, 16, 16);

        Assert.assertFalse(outcome.isRendered());
        Assert.assertTrue(outcome.isRecovered());
        Assert.assertEquals("render", outcome.getStage());
        Assert.assertSame(failure, outcome.getFailure());
        Assert.assertEquals(1, operations.errorConsumes);
        Assert.assertEquals(1, operations.pushes);
        Assert.assertEquals(1, operations.pops);
    }

    private static final class FakeSnapshot implements HostImageGlStateGuard.Snapshot { }
    private static final class FakeStateAccess implements HostImageGlStateGuard.StateAccess {
        private boolean idle = true;
        private boolean restored;
        private String drift;
        private RuntimeException captureFailure;
        @Override public boolean isTessellatorIdle() { return idle; }
        @Override public int consumeGlError() { return 0; }
        @Override public HostImageGlStateGuard.Snapshot capture() {
            if (captureFailure != null) throw captureFailure;
            return new FakeSnapshot();
        }
        @Override public void restore(HostImageGlStateGuard.Snapshot snapshot) { restored = true; }
        @Override public String findDrift(HostImageGlStateGuard.Snapshot snapshot) { return drift; }
    }

    /** 按检查点顺序返回 GL error 的状态访问桩。 */
    private static final class SequencedStateAccess implements HostImageGlStateGuard.StateAccess {
        private final Queue<Integer> errors = new ArrayDeque<Integer>();

        private SequencedStateAccess(Integer... errors) { this.errors.addAll(Arrays.asList(errors)); }
        @Override public boolean isTessellatorIdle() { return true; }
        @Override public int consumeGlError() {
            return errors.isEmpty() ? GL11.GL_NO_ERROR : errors.remove().intValue();
        }
        @Override public HostImageGlStateGuard.Snapshot capture() { return new FakeSnapshot(); }
        @Override public void restore(HostImageGlStateGuard.Snapshot snapshot) { }
        @Override public String findDrift(HostImageGlStateGuard.Snapshot snapshot) { return null; }
    }

    private static final class TextureFenceStateAccess implements HostImageGlStateGuard.StateAccess {
        private final FakeTextureMatrixOperations operations;

        private TextureFenceStateAccess(FakeTextureMatrixOperations operations) { this.operations = operations; }

        @Override public boolean isTessellatorIdle() { return true; }
        @Override public int consumeGlError() { return GL11.GL_NO_ERROR; }
        @Override public HostImageGlStateGuard.Snapshot capture() {
            HostImageGlStateGuard.TextureMatrixSnapshot snapshot =
                    HostImageGlStateGuard.probeTextureMatrix(operations);
            HostImageGlStateGuard.captureTextureMatrix(operations, snapshot);
            return snapshot;
        }
        @Override public void restore(HostImageGlStateGuard.Snapshot snapshot) {
            HostImageGlStateGuard.restoreTextureMatrix(operations,
                    (HostImageGlStateGuard.TextureMatrixSnapshot) snapshot);
        }
        @Override public String findDrift(HostImageGlStateGuard.Snapshot snapshot) { return null; }
    }

    /** 只执行 server attribute 子围栏的状态访问桩。 */
    private static final class AttribFenceStateAccess implements HostImageGlStateGuard.StateAccess {
        private final FakeAttribStackOperations operations;

        private AttribFenceStateAccess(FakeAttribStackOperations operations) { this.operations = operations; }

        @Override public boolean isTessellatorIdle() { return true; }
        @Override public int consumeGlError() { return GL11.GL_NO_ERROR; }
        @Override public HostImageGlStateGuard.Snapshot capture() {
            HostImageGlStateGuard.AttribStackSnapshot snapshot =
                    HostImageGlStateGuard.probeAttribStack(operations, "server-attrib");
            HostImageGlStateGuard.captureAttribStack(operations, snapshot);
            return snapshot;
        }
        @Override public void restore(HostImageGlStateGuard.Snapshot snapshot) {
            HostImageGlStateGuard.AttribStackSnapshot attribSnapshot =
                    (HostImageGlStateGuard.AttribStackSnapshot) snapshot;
            HostImageGlStateGuard.normalizeAttribStack(operations, attribSnapshot);
            HostImageGlStateGuard.popAttribStack(operations, attribSnapshot);
        }
        @Override public String findDrift(HostImageGlStateGuard.Snapshot snapshot) { return null; }
    }

    private static final class FakeTextureMatrixOperations
            implements HostImageGlStateGuard.TextureMatrixOperations {
        private final Queue<Integer> errors = new ArrayDeque<Integer>();
        private final float[] matrix = new float[16];
        private int depth;
        private int depthQueries;
        private int errorConsumes;
        private int matrixReads;
        private int pushes;
        private int pops;

        private FakeTextureMatrixOperations(int depth, Integer... errors) {
            this.depth = depth;
            this.errors.addAll(Arrays.asList(errors));
        }

        @Override public int getStackDepth() { depthQueries++; return depth; }
        @Override public int consumeGlError() {
            errorConsumes++;
            return errors.isEmpty() ? GL11.GL_NO_ERROR : errors.remove();
        }
        @Override public void readMatrix(float[] target) {
            matrixReads++;
            System.arraycopy(matrix, 0, target, 0, matrix.length);
        }
        @Override public void pushMatrix() { pushes++; depth++; }
        @Override public void popMatrix() { pops++; depth--; }
    }

    /** 不触发 LWJGL 初始化的 attribute stack 记录桩。 */
    static final class FakeAttribStackOperations implements HostImageGlStateGuard.AttribStackOperations {
        private final Queue<Integer> errors = new ArrayDeque<Integer>();
        private int depth;
        private int errorConsumes;
        private int pushes;
        private int pops;

        FakeAttribStackOperations(int depth, Integer... errors) {
            this.depth = depth;
            this.errors.addAll(Arrays.asList(errors));
        }

        @Override public int getStackDepth() { return depth; }
        @Override public int consumeGlError() {
            errorConsumes++;
            return errors.isEmpty() ? GL11.GL_NO_ERROR : errors.remove();
        }
        @Override public void push() { pushes++; depth++; }
        @Override public void pop() { pops++; depth--; }
    }

    /** 为 session 预算提供严格递增的确定时钟。 */
    private static final class IncrementingClock implements HostImageRenderSession.NanoClock {
        private long now;
        @Override public long nanoTime() { return now++; }
    }
}
