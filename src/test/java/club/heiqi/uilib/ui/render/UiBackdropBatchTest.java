package club.heiqi.uilib.ui.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;

/**
 * backdrop 批次（兄弟玻璃共享同一份背景采样）契约测试。
 *
 * <p>这是聊天"每条气泡一块玻璃"能上生产的先决条件：没有批次时，每块玻璃绘制完都会
 * bump 主层 revision，而快照 tile 复用以 revision 为键，于是第 N 条气泡必然重捕一遍
 * ——一屏十几条气泡就是十几次全屏级拷贝，帧率直接崩。冻结 revision 后同帧兄弟玻璃
 * 共享同一份采样，既对得上 iOS"一个 visual effect 层级共享背景"的语义，也把 N 次
 * 捕获压成 1 次。</p>
 */
public class UiBackdropBatchTest {

    private static UiRenderContext newContext() {
        return new UiRenderContext(320, 240, 0, 0, 0.0F, new PaintContextCompositor(),
                new UiMainLayerSnapshotService(), UiRuntimeAdapters.empty());
    }

    /** 批次内所有写入只记一笔脏，批次结束统一 bump 一次。 */
    @Test
    public void revisionIsFrozenInsideBatchAndBumpedOnceOnClose() {
        UiRenderContext context = newContext();
        int before = context.getMainLayerContentRevisionForDiagnostics();

        context.beginBackdropBatch();
        assertTrue("批次内应报告处于批次中", context.isInBackdropBatch());
        for (int i = 0; i < 12; i++) {
            context.notifyMainLayerContentChanged();
        }
        assertEquals("批次内 revision 必须冻结（兄弟玻璃才能共享同一份快照）",
                before, context.getMainLayerContentRevisionForDiagnostics());
        context.endBackdropBatch();

        assertFalse("收尾后应退出批次", context.isInBackdropBatch());
        assertEquals("批次结束只补 bump 一次", before + 1,
                context.getMainLayerContentRevisionForDiagnostics());
    }

    /** 空批次不得产生虚假 bump：否则每帧白刷一次快照复用。 */
    @Test
    public void emptyBatchDoesNotBumpRevision() {
        UiRenderContext context = newContext();
        int before = context.getMainLayerContentRevisionForDiagnostics();
        context.beginBackdropBatch();
        context.endBackdropBatch();
        assertEquals("无写入的批次不应改动 revision", before,
                context.getMainLayerContentRevisionForDiagnostics());
    }

    /** 嵌套：只有最外层收尾才真正 bump（overlay 与主树各自批次不得互相提前解冻）。 */
    @Test
    public void nestedBatchBumpsOnlyAtOutermostClose() {
        UiRenderContext context = newContext();
        int before = context.getMainLayerContentRevisionForDiagnostics();

        context.beginBackdropBatch();
        context.beginBackdropBatch();
        context.notifyMainLayerContentChanged();
        context.endBackdropBatch();
        assertEquals("内层收尾不得提前 bump", before, context.getMainLayerContentRevisionForDiagnostics());
        assertTrue("仍在批次内", context.isInBackdropBatch());
        context.endBackdropBatch();
        assertEquals("最外层收尾才 bump", before + 1,
                context.getMainLayerContentRevisionForDiagnostics());
    }

    /** 多余的 end 必须无害（不产生负深度、不误 bump）。 */
    @Test
    public void unmatchedEndIsHarmless() {
        UiRenderContext context = newContext();
        int before = context.getMainLayerContentRevisionForDiagnostics();
        context.endBackdropBatch();
        context.endBackdropBatch();
        assertEquals(before, context.getMainLayerContentRevisionForDiagnostics());
        assertFalse(context.isInBackdropBatch());

        context.beginBackdropBatch();
        context.notifyMainLayerContentChanged();
        context.endBackdropBatch();
        assertEquals("多余 end 不得破坏后续正常配对", before + 1,
                context.getMainLayerContentRevisionForDiagnostics());
    }

    /** 批次外行为必须与引入本机制前完全一致（零回归）。 */
    @Test
    public void outsideBatchEveryWriteBumpsImmediately() {
        UiRenderContext context = newContext();
        int before = context.getMainLayerContentRevisionForDiagnostics();
        context.notifyMainLayerContentChanged();
        context.notifyMainLayerContentChanged();
        assertEquals(before + 2, context.getMainLayerContentRevisionForDiagnostics());
    }
}
