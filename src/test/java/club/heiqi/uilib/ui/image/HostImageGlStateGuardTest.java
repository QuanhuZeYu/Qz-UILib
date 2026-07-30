package club.heiqi.uilib.ui.image;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import org.junit.Assert;
import org.junit.Test;

/** 完整状态围栏正常、异常与不可恢复分支测试。 */
public class HostImageGlStateGuardTest {
    @Test
    public void rendererFailureRecoveredReturnsRecoverableOutcome() {
        FakeStateAccess access = new FakeStateAccess();
        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(
                () -> { throw new IllegalStateException("bad item"); });
        Assert.assertTrue(outcome.isUnavailable());
        Assert.assertEquals("render", outcome.getStage());
        Assert.assertTrue(access.restored);
    }

    @Test
    public void driftFailsClosed() {
        FakeStateAccess access = new FakeStateAccess();
        access.drift = "program";
        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(() -> { });
        Assert.assertTrue(outcome.isHostStateLost());
        Assert.assertEquals("verify", outcome.getStage());
    }

    @Test
    public void unavailableCapabilityRestoresSnapshotWithoutInvokingRenderer() {
        FakeStateAccess access = new FakeStateAccess();
        access.unavailableReason = "legacy-state-fence-unavailable";
        int[] calls = {0};

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(() -> calls[0]++);

        Assert.assertTrue(outcome.isUnavailable());
        Assert.assertEquals("legacy-state-fence-unavailable", outcome.getDetail());
        Assert.assertTrue(access.restored);
        Assert.assertEquals(0, calls[0]);
    }

    @Test
    public void capabilityCheckFailureStillRestoresCapturedSnapshot() {
        FakeStateAccess access = new FakeStateAccess();
        access.capabilityFailure = new IllegalStateException("capability failed");

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(() -> { });

        Assert.assertTrue(outcome.isHostStateLost());
        Assert.assertEquals("capture", outcome.getStage());
        Assert.assertEquals("capability-check-failed", outcome.getDetail());
        Assert.assertTrue(access.restored);
    }

    @Test
    public void fatalRendererErrorRestoresBeforeRethrow() {
        FakeStateAccess access = new FakeStateAccess();
        AssertionError failure = new AssertionError("fatal renderer");
        try {
            new HostImageGlStateGuard(access).run(() -> { throw failure; });
            Assert.fail("fatal Error 应在恢复后原样抛出");
        } catch (AssertionError expected) {
            Assert.assertSame(failure, expected);
        }
        Assert.assertTrue(access.restored);
        Assert.assertFalse(HostImageGlErrorTracker.isActive());
    }

    @Test
    public void fatalRendererErrorRemainsPrimaryWhenRestoreFails() {
        FakeStateAccess access = new FakeStateAccess();
        AssertionError fatal = new AssertionError("fatal renderer");
        IllegalStateException restoreFailure = new IllegalStateException("restore failed");
        access.restoreFailure = restoreFailure;

        try {
            new HostImageGlStateGuard(access).run(() -> { throw fatal; });
            Assert.fail("expected");
        } catch (AssertionError actual) {
            Assert.assertSame(fatal, actual);
            Assert.assertTrue(Arrays.asList(actual.getSuppressed()).contains(restoreFailure));
        }
        Assert.assertFalse(HostImageGlErrorTracker.isActive());
    }

    @Test
    public void fatalRendererErrorRemainsPrimaryWhenVerificationFindsDrift() {
        FakeStateAccess access = new FakeStateAccess();
        access.drift = "program";
        AssertionError fatal = new AssertionError("fatal renderer");

        try {
            new HostImageGlStateGuard(access).run(() -> { throw fatal; });
            Assert.fail("expected");
        } catch (AssertionError actual) {
            Assert.assertSame(fatal, actual);
            Assert.assertEquals(1, actual.getSuppressed().length);
            Assert.assertEquals("program", actual.getSuppressed()[0].getMessage());
        }
    }

    @Test
    public void nestedGuardFailsBeforeAnyInnerGlAccessAndOuterDoesNotPublish() {
        FakeStateAccess outerAccess = new FakeStateAccess();
        FakeStateAccess innerAccess = new FakeStateAccess();
        HostImageGlStateGuard inner = new HostImageGlStateGuard(innerAccess);

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(outerAccess).run(
                () -> inner.run(() -> { }));

        Assert.assertTrue(outcome.isUnavailable());
        Assert.assertEquals("render", outcome.getStage());
        Assert.assertEquals(0, innerAccess.idleChecks);
        Assert.assertEquals(0, innerAccess.captureCalls);
        Assert.assertTrue(outerAccess.restored);
        Assert.assertFalse(HostImageGlErrorTracker.isActive());
    }

    @Test
    public void trackerRejectsReentryWithoutReplacingTheOuterSession() {
        HostImageGlErrorTracker.begin(() -> GL11.GL_NO_ERROR);
        try {
            try {
                HostImageGlErrorTracker.begin(() -> GL11.GL_NO_ERROR);
                Assert.fail("expected");
            } catch (IllegalStateException expected) {
                Assert.assertEquals("HostImage GL error tracker reentry", expected.getMessage());
            }
            Assert.assertTrue(HostImageGlErrorTracker.isActive());
        } finally {
            HostImageGlErrorTracker.end();
        }
    }

    /** 四个稳定阶段均可归因首个 GL error，且错误已消费仍不可恢复。 */
    @Test
    public void attributesFirstGlErrorToEachStablePhase() {
        assertTrackedPhase("capture", 1);
        assertTrackedPhase("delegate", 2);
        assertTrackedPhase("restore", 3);
        assertTrackedPhase("verify", 4);
    }

    /** 同一检查点排空多个错误，并只锁存第一个错误。 */
    @Test
    public void trackerDrainsMultipleErrorsAtSameCheckpoint() {
        final Queue<Integer> errors = new ArrayDeque<Integer>(Arrays.asList(
                GL11.GL_INVALID_ENUM, GL11.GL_INVALID_OPERATION, GL11.GL_NO_ERROR));
        HostImageGlErrorTracker.begin(errors::remove);
        try {
            HostImageGlErrorTracker.enterPhase("delegate");
            HostImageGlErrorTracker.checkpoint("item.render-effect");
            HostImageGlErrorTracker.FirstError first = HostImageGlErrorTracker.firstError();
            Assert.assertEquals("delegate", first.getPhase());
            Assert.assertEquals("item.render-effect", first.getOperation());
            Assert.assertEquals(GL11.GL_INVALID_ENUM, first.getError());
            Assert.assertTrue("当前检查点须排空全部错误", errors.isEmpty());
        } finally {
            HostImageGlErrorTracker.end();
        }
    }

    /** 首错锁存后，后续检查点仍排空错误且不覆盖归因。 */
    @Test
    public void trackerDrainsLaterCheckpointWithoutOverwritingFirstError() {
        final Queue<Integer> errors = new ArrayDeque<Integer>(Arrays.asList(
                GL11.GL_INVALID_ENUM, GL11.GL_NO_ERROR,
                GL11.GL_INVALID_OPERATION, GL11.GL_NO_ERROR));
        HostImageGlErrorTracker.begin(errors::remove);
        try {
            HostImageGlErrorTracker.enterPhase("delegate");
            HostImageGlErrorTracker.checkpoint("item.render-effect");
            HostImageGlErrorTracker.enterPhase("restore");
            HostImageGlErrorTracker.checkpoint("restore.matrix-depths");
            HostImageGlErrorTracker.recordConsumedError("client-active-query", GL11.GL_OUT_OF_MEMORY);
            HostImageGlErrorTracker.FirstError first = HostImageGlErrorTracker.firstError();
            Assert.assertEquals("delegate", first.getPhase());
            Assert.assertEquals("item.render-effect", first.getOperation());
            Assert.assertEquals(GL11.GL_INVALID_ENUM, first.getError());
            Assert.assertTrue("后续检查点须继续排空错误", errors.isEmpty());
        } finally {
            HostImageGlErrorTracker.end();
        }
    }

    /** 已消费错误仅在活动 session 中锁存，NO_ERROR 不创建首错。 */
    @Test
    public void consumedErrorRequiresActiveSessionAndNonZeroError() {
        HostImageGlErrorTracker.recordConsumedError("client-active-query", GL11.GL_OUT_OF_MEMORY);
        Assert.assertNull(HostImageGlErrorTracker.firstError());

        HostImageGlErrorTracker.begin(() -> GL11.GL_NO_ERROR);
        try {
            HostImageGlErrorTracker.recordConsumedError("client-active-query", GL11.GL_NO_ERROR);
            Assert.assertNull(HostImageGlErrorTracker.firstError());
        } finally {
            HostImageGlErrorTracker.end();
        }
    }

    /** 前一次围栏产生的多个错误不得污染下一次围栏入口。 */
    @Test
    public void consecutiveRunsLeaveSecondEntryClean() {
        SequencedStateAccess access = new SequencedStateAccess(
                GL11.GL_NO_ERROR,
                GL11.GL_INVALID_ENUM, GL11.GL_INVALID_OPERATION, GL11.GL_NO_ERROR);
        HostImageGlStateGuard guard = new HostImageGlStateGuard(access);

        HostImageRenderOutcome first = guard.run(() -> { });
        HostImageRenderOutcome second = guard.run(() -> { });

        Assert.assertEquals("capture", first.getStage());
        Assert.assertTrue(first.getDetail().endsWith("gl-error=" + GL11.GL_INVALID_ENUM));
        Assert.assertTrue("第二次入口不得读取前次遗留错误", second.isPublishable());
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
        Assert.assertEquals("入口不清洁时不得进入任何 capability probe", 0, access.captureCalls);
    }

    @Test
    public void entryPrecheckDrainsAllQueuedErrorsBeforeTheNextRun() {
        SequencedStateAccess access = new SequencedStateAccess(
                GL11.GL_INVALID_ENUM, GL11.GL_INVALID_OPERATION, GL11.GL_NO_ERROR);
        HostImageGlStateGuard guard = new HostImageGlStateGuard(access);

        HostImageRenderOutcome first = guard.run(() -> { });
        HostImageRenderOutcome second = guard.run(() -> { });

        Assert.assertTrue(first.isHostStateLost());
        Assert.assertEquals("entry-gl-error=" + GL11.GL_INVALID_ENUM, first.getDetail());
        Assert.assertTrue(second.isPublishable());
    }

    /** Core Profile query INVALID_ENUM 只关闭 client 子能力并排空 probe error。 */
    @Test
    public void clientActiveQueryInvalidEnumDisablesOnlyClientCapability() {
        FakeTextureBindingOperations operations = new FakeTextureBindingOperations();
        operations.errors.addAll(Arrays.asList(GL11.GL_INVALID_ENUM, GL11.GL_NO_ERROR));

        HostImageGlStateGuard.TextureBindingSnapshot snapshot =
                HostImageGlStateGuard.captureTextureBindings(operations);
        HostImageGlStateGuard.restoreTextureBindings(operations, snapshot);

        Assert.assertFalse(snapshot.isClientActiveTextureSupported());
        Assert.assertEquals(1, operations.clientQueries);
        Assert.assertEquals(0, operations.clientSets);
        Assert.assertTrue("client 不支持时 server binding 仍须捕获", operations.bindingQueries >= 2);
        Assert.assertTrue("client 不支持时 server binding 仍须恢复", operations.bindingSets >= 2);
        Assert.assertTrue(operations.errors.isEmpty());
    }

    /** Compatibility Profile 用原值探测 setter，并在 restore 对称恢复。 */
    @Test
    public void supportedClientActiveTextureProbesSameValueAndRestores() {
        FakeTextureBindingOperations operations = new FakeTextureBindingOperations();
        operations.clientActiveTexture = GL13.GL_TEXTURE1;
        HostImageGlStateGuard.TextureBindingSnapshot snapshot =
                HostImageGlStateGuard.captureTextureBindings(operations);

        operations.clientActiveTexture = GL13.GL_TEXTURE0;
        HostImageGlStateGuard.restoreTextureBindings(operations, snapshot);

        Assert.assertTrue(snapshot.isClientActiveTextureSupported());
        Assert.assertEquals(GL13.GL_TEXTURE1, operations.clientSetValues.get(0).intValue());
        Assert.assertEquals(GL13.GL_TEXTURE1, operations.clientActiveTexture);
        Assert.assertEquals(2, operations.clientSets);
    }

    /** setter 明确不支持时安全降级，restore 不重试 legacy API。 */
    @Test
    public void clientActiveSetterFailureDisablesRestoreRetry() {
        FakeTextureBindingOperations operations = new FakeTextureBindingOperations();
        operations.errors.addAll(Arrays.asList(GL11.GL_NO_ERROR,
                GL11.GL_INVALID_OPERATION, GL11.GL_NO_ERROR));
        HostImageGlStateGuard.TextureBindingSnapshot snapshot =
                HostImageGlStateGuard.captureTextureBindings(operations);

        HostImageGlStateGuard.restoreTextureBindings(operations, snapshot);

        Assert.assertFalse(snapshot.isClientActiveTextureSupported());
        Assert.assertEquals("仅 same-value probe 调一次 setter", 1, operations.clientSets);
        Assert.assertTrue(operations.errors.isEmpty());
    }

    /** 未知 probe error 不得伪装成 capability 缺失。 */
    @Test
    public void unknownClientProbeErrorFailsClosedWithEvidence() {
        FakeTextureBindingOperations operations = new FakeTextureBindingOperations();
        operations.errors.addAll(Arrays.asList(GL11.GL_OUT_OF_MEMORY, GL11.GL_NO_ERROR));
        try {
            HostImageGlStateGuard.captureTextureBindings(operations);
            Assert.fail("未知错误必须中止 capture");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("client-active-query-gl-error=" + GL11.GL_OUT_OF_MEMORY,
                    expected.getMessage());
        }
    }

    /** query 未知错误由完整围栏保留稳定 operation，而非退化为 capture-failed。 */
    @Test
    public void unknownClientQueryErrorIsPreservedInGuardOutcome() {
        FakeTextureBindingOperations operations = new FakeTextureBindingOperations();
        operations.clientQueryErrors.addAll(Arrays.asList(GL11.GL_OUT_OF_MEMORY, GL11.GL_NO_ERROR));

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(
                new TextureBindingFenceStateAccess(operations)).run(() -> { });

        Assert.assertTrue(outcome.isHostStateLost());
        Assert.assertEquals("capture", outcome.getStage());
        Assert.assertEquals("phase=capture operation=client-active-query gl-error=" + GL11.GL_OUT_OF_MEMORY,
                outcome.getDetail());
    }

    /** setter 未知错误由完整围栏保留稳定 operation。 */
    @Test
    public void unknownClientSetterErrorIsPreservedInGuardOutcome() {
        FakeTextureBindingOperations operations = new FakeTextureBindingOperations();
        operations.clientSetterErrors.addAll(Arrays.asList(GL11.GL_OUT_OF_MEMORY, GL11.GL_NO_ERROR));

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(
                new TextureBindingFenceStateAccess(operations)).run(() -> { });

        Assert.assertTrue(outcome.isHostStateLost());
        Assert.assertEquals("capture", outcome.getStage());
        Assert.assertEquals("phase=capture operation=client-active-setter gl-error=" + GL11.GL_OUT_OF_MEMORY,
                outcome.getDetail());
    }

    /** recognized client capability 缺失须拒绝 delegate，但不升级为宿主状态丢失。 */
    @Test
    public void expectedClientQueryDowngradeReturnsUnavailableWithoutRenderer() {
        FakeTextureBindingOperations operations = new FakeTextureBindingOperations();
        operations.clientQueryErrors.addAll(Arrays.asList(GL11.GL_INVALID_ENUM, GL11.GL_NO_ERROR));
        int[] rendererCalls = {0};

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(
                new TextureBindingFenceStateAccess(operations)).run(() -> rendererCalls[0]++);

        Assert.assertTrue(outcome.isUnavailable());
        Assert.assertEquals("legacy-state-fence-unavailable", outcome.getDetail());
        Assert.assertEquals(0, rendererCalls[0]);
        Assert.assertEquals(0, operations.clientSets);
    }

    /** client 降级不得削弱 server binding drift 的 fail-closed 判定。 */
    @Test
    public void serverTextureBindingDriftStillDetectedWhenClientUnsupported() {
        FakeTextureBindingOperations operations = new FakeTextureBindingOperations();
        operations.errors.addAll(Arrays.asList(GL11.GL_INVALID_ENUM, GL11.GL_NO_ERROR));
        HostImageGlStateGuard.TextureBindingSnapshot snapshot =
                HostImageGlStateGuard.captureTextureBindings(operations);

        operations.bindings.put(GL13.GL_TEXTURE0, 999);

        Assert.assertTrue(HostImageGlStateGuard.hasServerTextureBindingDrift(operations, snapshot));
    }

    /** server binding 无法恢复时，总围栏仍以 verify drift fail-closed。 */
    @Test
    public void serverTextureBindingRestoreFailureRemainsUnrecovered() {
        FakeTextureBindingOperations operations = new FakeTextureBindingOperations();
        TextureBindingFenceStateAccess access = new TextureBindingFenceStateAccess(operations);

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(() -> {
            operations.bindings.put(GL13.GL_TEXTURE0, 999);
            operations.ignoreBindingSets = true;
        });

        Assert.assertTrue(outcome.isHostStateLost());
        Assert.assertEquals("verify", outcome.getStage());
        Assert.assertEquals("texture-binding", outcome.getDetail());
    }

    private static void assertTrackedPhase(String phase, int errorConsumeIndex) {
        Integer[] errors = new Integer[errorConsumeIndex + 1];
        Arrays.fill(errors, Integer.valueOf(GL11.GL_NO_ERROR));
        errors[errorConsumeIndex] = Integer.valueOf(GL11.GL_INVALID_ENUM);
        SequencedStateAccess access = new SequencedStateAccess(errors);
        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(() -> { });
        Assert.assertTrue(outcome.isHostStateLost());
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
        Assert.assertTrue(outcome.isHostStateLost());
        Assert.assertEquals("tessellator-not-idle", outcome.getDetail());
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
    public void restoredTextureMatrixDriftIncludesStackDepth() {
        FakeTextureMatrixOperations operations = new FakeTextureMatrixOperations(3);
        HostImageGlStateGuard.TextureMatrixSnapshot snapshot =
                HostImageGlStateGuard.probeTextureMatrix(operations);

        Assert.assertFalse(HostImageGlStateGuard.hasRestoredTextureMatrixDrift(operations, snapshot));
        operations.pushMatrix();
        Assert.assertTrue(HostImageGlStateGuard.hasRestoredTextureMatrixDrift(operations, snapshot));
    }

    @Test
    public void textureMatrixRestoreSelectsTheCapturedServerTextureUnit() {
        FakeTextureBindingOperations bindings = new FakeTextureBindingOperations();
        bindings.activeTexture = GL13.GL_TEXTURE1;
        final int[] depth = {2};
        final int[] popUnit = {-1};
        HostImageGlStateGuard.TextureMatrixOperations matrices =
                new HostImageGlStateGuard.TextureMatrixOperations() {
                    @Override public int getStackDepth() { return depth[0]; }
                    @Override public int consumeGlError() { return GL11.GL_NO_ERROR; }
                    @Override public void readMatrix(float[] target) { }
                    @Override public void pushMatrix() { depth[0]++; }
                    @Override public void popMatrix() {
                        popUnit[0] = bindings.activeTexture;
                        depth[0]--;
                    }
                };
        HostImageGlStateGuard.TextureMatrixSnapshot snapshot =
                HostImageGlStateGuard.probeTextureMatrix(matrices);
        HostImageGlStateGuard.captureTextureMatrix(matrices, snapshot);
        bindings.activeTexture = GL13.GL_TEXTURE0;

        HostImageGlStateGuard.restoreTextureMatrixOnUnit(
                bindings, matrices, snapshot, GL13.GL_TEXTURE1);

        Assert.assertEquals(GL13.GL_TEXTURE1, popUnit[0]);
        Assert.assertEquals(GL13.GL_TEXTURE1, bindings.activeTexture);
        Assert.assertEquals(2, depth[0]);
    }

    @Test
    public void supportedTextureMatrixRendererUnderflowRemainsUnrecovered() {
        FakeTextureMatrixOperations operations = new FakeTextureMatrixOperations(2);
        TextureFenceStateAccess access = new TextureFenceStateAccess(operations);

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(operations::popMatrix);

        Assert.assertTrue(outcome.isHostStateLost());
        Assert.assertEquals("restore", outcome.getStage());
        Assert.assertEquals("restore-failed", outcome.getDetail());
        Assert.assertTrue(outcome.getFailure() instanceof IllegalStateException);
    }

    /** Compatibility Profile 的合法 server depth=0 仍须压入完整 attribute 围栏。 */
    @Test
    public void zeroServerAttribDepthStillPushesAndPops() {
        FakeAttribStackOperations operations = new FakeAttribStackOperations(0);
        HostImageGlStateGuard.AttribStackSnapshot snapshot =
                HostImageGlStateGuard.probeAttribStack(operations, "server-attrib");

        HostImageGlStateGuard.captureAttribStack(operations, snapshot);
        HostImageGlStateGuard.normalizeAttribStack(operations, snapshot);
        HostImageGlStateGuard.popAttribStack(operations, snapshot);

        Assert.assertTrue(snapshot.isSupported());
        Assert.assertEquals(1, operations.pushes);
        Assert.assertEquals(1, operations.pops);
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

    /** Compatibility Profile 的合法 client depth=0 仍须压入完整 client attribute 围栏。 */
    @Test
    public void zeroClientAttribDepthStillPushesAndPops() {
        FakeAttribStackOperations operations = new FakeAttribStackOperations(0);
        HostImageGlStateGuard.AttribStackSnapshot snapshot =
                HostImageGlStateGuard.probeAttribStack(operations, "client-attrib");

        HostImageGlStateGuard.captureAttribStack(operations, snapshot);
        HostImageGlStateGuard.normalizeAttribStack(operations, snapshot);
        HostImageGlStateGuard.popAttribStack(operations, snapshot);

        Assert.assertTrue(snapshot.isSupported());
        Assert.assertEquals(1, operations.pushes);
        Assert.assertEquals(1, operations.pops);
    }

    /** 真正支持的 attribute stack 被 renderer 弹掉围栏帧时仍须 fail-closed。 */
    @Test
    public void supportedServerAttribUnderflowStillFailsClosed() {
        FakeAttribStackOperations operations = new FakeAttribStackOperations(2);
        AttribFenceStateAccess access = new AttribFenceStateAccess(operations);

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(operations::pop);

        Assert.assertTrue(outcome.isHostStateLost());
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
        ItemIconRenderer delegate = (itemStack, left, top, side) -> {
            operations.pop();
            return HostImageRenderOutcome.publishable();
        };
        HostImageSource source = HostImageSource.itemIcon(new ItemStack(new Item()));

        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(
                () -> delegate.render(source.getItemIconStack(), 0, 0, 16));
        HostImageRenderSession session = new HostImageRenderSession(1, 1, 1L, new IncrementingClock());
        HostImageRenderSession.RequestResult request = session.request(source, 16,
                (ignoredSource, side) -> new HostImageRenderSession.RasterizeResult(null, outcome));

        Assert.assertTrue(outcome.isHostStateLost());
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
        ItemIconRenderer delegate = (itemStack, left, top, side) -> { throw failure; };
        HostImageRenderOutcome outcome = new HostImageGlStateGuard(access).run(
                () -> delegate.render(new ItemStack(new Item()), 0, 0, 16));

        Assert.assertTrue(outcome.isUnavailable());
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
        private String unavailableReason;
        private RuntimeException captureFailure;
        private RuntimeException capabilityFailure;
        private RuntimeException restoreFailure;
        private int idleChecks;
        private int captureCalls;
        @Override public boolean isTessellatorIdle() { idleChecks++; return idle; }
        @Override public int consumeGlError() { return 0; }
        @Override public HostImageGlStateGuard.Snapshot capture() {
            captureCalls++;
            if (captureFailure != null) throw captureFailure;
            return new FakeSnapshot();
        }
        @Override public void restore(HostImageGlStateGuard.Snapshot snapshot) {
            restored = true;
            if (restoreFailure != null) throw restoreFailure;
        }
        @Override public String findDrift(HostImageGlStateGuard.Snapshot snapshot) { return drift; }
        @Override public String unavailableReason(HostImageGlStateGuard.Snapshot snapshot) {
            if (capabilityFailure != null) throw capabilityFailure;
            return unavailableReason;
        }
    }

    /** 按检查点顺序返回 GL error 的状态访问桩。 */
    private static final class SequencedStateAccess implements HostImageGlStateGuard.StateAccess {
        private final Queue<Integer> errors = new ArrayDeque<Integer>();
        private int captureCalls;

        private SequencedStateAccess(Integer... errors) { this.errors.addAll(Arrays.asList(errors)); }
        @Override public boolean isTessellatorIdle() { return true; }
        @Override public int consumeGlError() {
            return errors.isEmpty() ? GL11.GL_NO_ERROR : errors.remove().intValue();
        }
        @Override public HostImageGlStateGuard.Snapshot capture() { captureCalls++; return new FakeSnapshot(); }
        @Override public void restore(HostImageGlStateGuard.Snapshot snapshot) { }
        @Override public String findDrift(HostImageGlStateGuard.Snapshot snapshot) { return null; }
    }

    /** 不触发 LWJGL 初始化的 server/client texture 状态桩。 */
    private static final class FakeTextureBindingOperations
            implements HostImageGlStateGuard.TextureBindingOperations {
        private final Queue<Integer> errors = new ArrayDeque<Integer>();
        private final Queue<Integer> clientQueryErrors = new ArrayDeque<Integer>();
        private final Queue<Integer> clientSetterErrors = new ArrayDeque<Integer>();
        private final java.util.Map<Integer, Integer> bindings = new java.util.HashMap<Integer, Integer>();
        private final java.util.List<Integer> clientSetValues = new java.util.ArrayList<Integer>();
        private int activeTexture = GL13.GL_TEXTURE1;
        private int clientActiveTexture = GL13.GL_TEXTURE0;
        private int clientQueries;
        private int clientSets;
        private int bindingQueries;
        private int bindingSets;
        private boolean ignoreBindingSets;

        private FakeTextureBindingOperations() {
            bindings.put(GL13.GL_TEXTURE0, 10);
            bindings.put(GL13.GL_TEXTURE1, 11);
        }

        @Override public int getActiveTexture() { return activeTexture; }
        @Override public void setActiveTexture(int unit) { activeTexture = unit; }
        @Override public int getClientActiveTexture() {
            clientQueries++;
            errors.addAll(clientQueryErrors);
            clientQueryErrors.clear();
            return clientActiveTexture;
        }
        @Override public void setClientActiveTexture(int unit) {
            clientSets++;
            clientSetValues.add(unit);
            clientActiveTexture = unit;
            errors.addAll(clientSetterErrors);
            clientSetterErrors.clear();
        }
        @Override public int getTexture2dBinding() {
            bindingQueries++;
            Integer binding = bindings.get(activeTexture);
            return binding == null ? 0 : binding.intValue();
        }
        @Override public void bindTexture2d(int texture) {
            bindingSets++;
            if (!ignoreBindingSets) bindings.put(activeTexture, texture);
        }
        @Override public int consumeGlError() {
            return errors.isEmpty() ? GL11.GL_NO_ERROR : errors.remove().intValue();
        }
    }

    /** 只执行 texture binding 子围栏的状态访问桩。 */
    private static final class TextureBindingFenceStateAccess implements HostImageGlStateGuard.StateAccess {
        private final FakeTextureBindingOperations operations;

        private TextureBindingFenceStateAccess(FakeTextureBindingOperations operations) {
            this.operations = operations;
        }

        @Override public boolean isTessellatorIdle() { return true; }
        @Override public int consumeGlError() { return operations.consumeGlError(); }
        @Override public HostImageGlStateGuard.Snapshot capture() {
            return HostImageGlStateGuard.captureTextureBindings(operations);
        }
        @Override public void restore(HostImageGlStateGuard.Snapshot snapshot) {
            HostImageGlStateGuard.restoreTextureBindings(operations,
                    (HostImageGlStateGuard.TextureBindingSnapshot) snapshot);
        }
        @Override public String findDrift(HostImageGlStateGuard.Snapshot snapshot) {
            return HostImageGlStateGuard.hasServerTextureBindingDrift(operations,
                    (HostImageGlStateGuard.TextureBindingSnapshot) snapshot)
                    ? "texture-binding" : null;
        }
        @Override public String unavailableReason(HostImageGlStateGuard.Snapshot snapshot) {
            return ((HostImageGlStateGuard.TextureBindingSnapshot) snapshot)
                    .isClientActiveTextureSupported()
                    ? null : "legacy-state-fence-unavailable";
        }
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
