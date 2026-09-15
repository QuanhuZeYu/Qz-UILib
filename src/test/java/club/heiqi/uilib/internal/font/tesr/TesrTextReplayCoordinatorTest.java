package club.heiqi.uilib.internal.font.tesr;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 宿主 TESR 批量窗口内世界文字延后回放的行为契约测试。
 *
 * <p>覆盖三件事：无宿主钩子时是否 fail-open 回即时绘制；捕获/回放是否严格按「宿主提交之后」的
 * 顺序与 FIFO 进行；宿主未提交或回放失败时是否丢弃滞留项而不静默永久丢字。</p>
 */
public class TesrTextReplayCoordinatorTest {

    /** 可切换的宿主探针。 */
    private static final class FakeProbe implements TesrTextReplayCoordinator.HostProbe {

        boolean pending;

        @Override
        public boolean hasPendingDeferredGeometry() {
            return pending;
        }
    }

    /** 桩矩阵读取：用可辨认常量填充，便于断言捕获快照被带进回放项。 */
    private static final class FakeMatrixReader implements TesrTextReplayCoordinator.MatrixReader {

        @Override
        public void readModelview(float[] target) {
            for (int index = 0; index < target.length; index++) {
                target[index] = 7.0F;
            }
        }

        @Override
        public void readProjection(float[] target) {
            for (int index = 0; index < target.length; index++) {
                target[index] = 9.0F;
            }
        }
    }

    /** 记录回放顺序的出口，可切换为抛异常。 */
    private static final class RecordingSink implements TesrTextReplayCoordinator.ReplaySink {

        final List<TesrTextReplayCoordinator.DeferredText> replayed =
                new ArrayList<TesrTextReplayCoordinator.DeferredText>();
        boolean fail;

        @Override
        public void replay(TesrTextReplayCoordinator.DeferredText text) {
            if (fail) {
                throw new IllegalStateException("replay failure");
            }
            replayed.add(text);
        }
    }

    private FakeProbe probe;
    private RecordingSink sink;

    @Before
    public void setUp() {
        TesrTextReplayCoordinator.resetForTest();
        probe = new FakeProbe();
        sink = new RecordingSink();
        TesrTextReplayCoordinator.installHostProbe(probe);
        TesrTextReplayCoordinator.installMatrixReader(new FakeMatrixReader());
        TesrTextReplayCoordinator.installReplaySink(sink);
    }

    @After
    public void tearDown() {
        TesrTextReplayCoordinator.resetForTest();
    }

    /** 宿主回放钩子未安装（无 Angelica / 版本不匹配 / Mixin 未应用）：不得捕获，必须即时绘制。 */
    @Test
    public void absentHostHookKeepsImmediateDraw() {
        probe.pending = true;

        Assert.assertFalse("宿主钩子未安装时禁止捕获", TesrTextReplayCoordinator.shouldCapture(false));
        Assert.assertEquals(0, TesrTextReplayCoordinator.pendingCount());
    }

    /** UI 延迟批处理边界内的文字必须保持即时绘制（捕获会破坏 UI 批次语义）。 */
    @Test
    public void uiDeferredScopeKeepsImmediateDraw() {
        TesrTextReplayCoordinator.markHostHookInstalled();
        probe.pending = true;

        Assert.assertFalse("UI 延迟边界内禁止捕获", TesrTextReplayCoordinator.shouldCapture(true));
    }

    /** 正常链路：窗口内捕获、宿主提交后按 FIFO 回放，且回放项带捕获时刻矩阵。 */
    @Test
    public void capturesInWindowAndReplaysAfterHostCommitInOrder() {
        TesrTextReplayCoordinator.markHostHookInstalled();
        probe.pending = true;
        Assert.assertTrue(TesrTextReplayCoordinator.shouldCapture(false));

        TesrTextReplayCoordinator.capture("first", 1, 2, 0xFF000000, false, -1);
        TesrTextReplayCoordinator.capture("wrapped", 3, 4, 0xFFFFFFFF, false, 40);
        TesrTextReplayCoordinator.capture("shadowed", 5, 6, 0xFF00FF00, true, -1);
        Assert.assertEquals(3, TesrTextReplayCoordinator.pendingCount());

        // 宿主仍持有未提交几何：此时回放会重新落到几何之前，必须等待。
        TesrTextReplayCoordinator.replayAfterHostCommit();
        Assert.assertEquals("宿主未提交时不得回放", 3, TesrTextReplayCoordinator.pendingCount());
        Assert.assertTrue(sink.replayed.isEmpty());

        probe.pending = false;
        TesrTextReplayCoordinator.replayAfterHostCommit();

        Assert.assertEquals(0, TesrTextReplayCoordinator.pendingCount());
        Assert.assertEquals(3, sink.replayed.size());
        Assert.assertEquals("first", sink.replayed.get(0).text);
        Assert.assertEquals("wrapped", sink.replayed.get(1).text);
        Assert.assertEquals(40, sink.replayed.get(1).wrapWidth);
        Assert.assertEquals("shadowed", sink.replayed.get(2).text);
        Assert.assertTrue(sink.replayed.get(2).dropShadow);
        Assert.assertEquals("捕获时刻模型视图矩阵随项回放", 7.0F, sink.replayed.get(0).modelview[3], 0.0F);
        Assert.assertEquals("捕获时刻投影矩阵随项回放", 9.0F, sink.replayed.get(0).projection[15], 0.0F);
    }

    /** 宿主整帧未提交：帧边界丢弃滞留项，不静默永久丢字。 */
    @Test
    public void frameBoundaryDropsStrandedCaptures() {
        TesrTextReplayCoordinator.markHostHookInstalled();
        probe.pending = true;
        TesrTextReplayCoordinator.capture("stranded", 0, 0, 0xFF000000, false, -1);

        TesrTextReplayCoordinator.onFrameBoundary();

        Assert.assertEquals(0, TesrTextReplayCoordinator.pendingCount());
        Assert.assertTrue(sink.replayed.isEmpty());
    }

    /** 回放出口抛异常：丢弃本帧滞留项、不向渲染链传播异常。 */
    @Test
    public void replayFailureDropsPendingWithoutPropagating() {
        TesrTextReplayCoordinator.markHostHookInstalled();
        probe.pending = true;
        TesrTextReplayCoordinator.capture("boom", 0, 0, 0xFF000000, false, -1);
        sink.fail = true;
        probe.pending = false;

        TesrTextReplayCoordinator.replayAfterHostCommit();

        Assert.assertEquals(0, TesrTextReplayCoordinator.pendingCount());
        Assert.assertTrue(sink.replayed.isEmpty());
    }
}
