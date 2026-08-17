package club.heiqi.config.ui.field;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

/** fontSort 单屏 presentation 的 signal、拖拽和提交边界测试。 */
public class FontSortPresentationTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    @Test
    public void filterAndClearArePresentationOnly() {
        AtomicInteger commits = new AtomicInteger();
        FontSortPresentation presentation = presentation(commits, null);
        ReactiveScheduler.get().flush();

        presentation.setFilter("ser");
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList("Serif"), values(presentation.filteredSignal().get()));
        Assert.assertEquals(Arrays.asList("Serif", "Sans", "Mono"), presentation.fullValues());
        Assert.assertEquals(0, commits.get());
        presentation.setFilter("");
        ReactiveScheduler.get().flush();
        Assert.assertEquals(3, presentation.filteredSignal().get().size());
    }

    @Test
    public void filteredDragCommitsOnceAndCancelRestoresFrozenFullOrder() {
        AtomicInteger commits = new AtomicInteger();
        AtomicReference<List<String>> submitted = new AtomicReference<List<String>>();
        FontSortPresentation presentation = dragPresentation(commits, submitted);
        ReactiveScheduler.get().flush();
        presentation.setFilter("visible");
        ReactiveScheduler.get().flush();
        presentation.beginDrag();
        List<FontSortPresentation.Row> visible = presentation.filteredSignal().get();
        List<FontSortPresentation.Row> reordered = Arrays.asList(visible.get(1), visible.get(0));
        presentation.previewVisible(reordered);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList("Hidden A", "Visible B", "Hidden B", "Visible A"),
                presentation.fullValues());
        presentation.finishDrag(reordered);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(1, commits.get());
        Assert.assertEquals(presentation.fullValues(), submitted.get());

        presentation.beginDrag();
        reordered = Arrays.asList(presentation.filteredSignal().get().get(1),
                presentation.filteredSignal().get().get(0));
        presentation.previewVisible(reordered);
        ReactiveScheduler.get().flush();
        presentation.cancelDrag();
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList("Hidden A", "Visible B", "Hidden B", "Visible A"),
                presentation.fullValues());
        Assert.assertEquals(1, commits.get());
        FontSortPresentation.DragStateSnapshot state = presentation.__getDragStateForTest();
        Assert.assertFalse("CANCEL 后 dragActive 必须复位", state.dragActive);
        Assert.assertFalse("CANCEL flush 后 draggingSignal 必须复位", state.draggingSignal);
        Assert.assertEquals("冻结 filter 复位为当前实时 filter", "visible", state.frozenFilter);
        Assert.assertTrue("CANCEL 后 dragStartFull 必须清空", state.dragStartFull.isEmpty());
        Assert.assertTrue("CANCEL 后 dragStartVisible 必须清空", state.dragStartVisible.isEmpty());
        Assert.assertTrue("CANCEL 后 dragStartDraft 必须清空", state.dragStartDraft.isEmpty());
        Assert.assertFalse("CANCEL 后 deferred draft 门闩必须清空", state.deferredDraftPending);
        Assert.assertTrue(state.deferredDraft.isEmpty());
    }

    @Test
    public void finishResetsAllDragStateAndFilteredReadsLiveFilterAfterwards() {
        FontSortPresentation presentation = dragPresentation(new AtomicInteger(), new AtomicReference<List<String>>());
        ReactiveScheduler.get().flush();
        presentation.setFilter("visible");
        ReactiveScheduler.get().flush();
        presentation.beginDrag();
        List<FontSortPresentation.Row> visible = presentation.filteredSignal().get();
        List<FontSortPresentation.Row> reordered = Arrays.asList(visible.get(1), visible.get(0));
        presentation.previewVisible(reordered);
        ReactiveScheduler.get().flush();

        presentation.finishDrag(reordered);
        ReactiveScheduler.get().flush();

        FontSortPresentation.DragStateSnapshot state = presentation.__getDragStateForTest();
        Assert.assertFalse("UP 后 dragActive 必须复位", state.dragActive);
        Assert.assertFalse("UP 后 draggingSignal 必须复位", state.draggingSignal);
        Assert.assertEquals("UP 后冻结 filter 应回到实时 filter", "visible", state.frozenFilter);
        Assert.assertTrue("UP 后 dragStartFull 必须清空", state.dragStartFull.isEmpty());
        Assert.assertTrue("UP 后 dragStartVisible 必须清空", state.dragStartVisible.isEmpty());
        Assert.assertTrue("UP 后 dragStartDraft 必须清空", state.dragStartDraft.isEmpty());
        Assert.assertFalse("UP 后 deferred draft 门闩必须清空", state.deferredDraftPending);
        Assert.assertTrue(state.deferredDraft.isEmpty());
        Assert.assertEquals("reset 不得改写用户 filter signal", "visible", presentation.filterSignal().get());

        presentation.setFilter("hidden");
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList("Hidden A", "Hidden B"), values(presentation.filteredSignal().get()));
    }

    @Test
    public void commitFailurePropagatesAfterPresentationStateIsReset() {
        final RuntimeException failure = new IllegalStateException("commit failed");
        FontSortPresentation presentation = new FontSortPresentation(
                Arrays.asList("Hidden A", "Visible A", "Hidden B", "Visible B"),
                Arrays.<String>asList(),
                values -> { throw failure; });
        ReactiveScheduler.get().flush();
        presentation.setFilter("visible");
        ReactiveScheduler.get().flush();
        presentation.beginDrag();
        List<FontSortPresentation.Row> visible = presentation.filteredSignal().get();
        List<FontSortPresentation.Row> reordered = Arrays.asList(visible.get(1), visible.get(0));
        presentation.previewVisible(reordered);
        ReactiveScheduler.get().flush();

        try {
            presentation.finishDrag(reordered);
            Assert.fail("commitConsumer 异常必须传播");
        } catch (RuntimeException actual) {
            Assert.assertSame("不得吞掉 commitConsumer 原异常", failure, actual);
        }
        ReactiveScheduler.get().flush();

        FontSortPresentation.DragStateSnapshot state = presentation.__getDragStateForTest();
        Assert.assertFalse("异常后 dragActive 必须复位", state.dragActive);
        Assert.assertFalse("异常后 draggingSignal 必须复位", state.draggingSignal);
        Assert.assertEquals("异常后冻结 filter 必须复位", "visible", state.frozenFilter);
        Assert.assertTrue("异常后 dragStartFull 必须清空", state.dragStartFull.isEmpty());
        Assert.assertTrue("异常后 dragStartVisible 必须清空", state.dragStartVisible.isEmpty());
        Assert.assertTrue("异常后 dragStartDraft 必须清空", state.dragStartDraft.isEmpty());
        Assert.assertFalse("异常后 deferred draft 门闩必须清空", state.deferredDraftPending);
        Assert.assertTrue(state.deferredDraft.isEmpty());
        Assert.assertEquals("异常后用户 filter signal 不变", "visible", presentation.filterSignal().get());
    }

    @Test
    public void externalDraftDuringDragWinsOverStaleDrop() {
        AtomicInteger commits = new AtomicInteger();
        FontSortPresentation presentation = dragPresentation(commits, new AtomicReference<List<String>>());
        ReactiveScheduler.get().flush();
        presentation.setFilter("visible");
        ReactiveScheduler.get().flush();
        presentation.beginDrag();
        List<FontSortPresentation.Row> visible = presentation.filteredSignal().get();
        List<FontSortPresentation.Row> reordered = Arrays.asList(visible.get(1), visible.get(0));
        presentation.previewVisible(reordered);

        presentation.resetFromDraft(Arrays.asList("Visible B", "Hidden B"));
        presentation.finishDrag(reordered);
        ReactiveScheduler.get().flush();

        Assert.assertEquals("外部 draft 更新不得被旧拖拽快照覆盖",
                Arrays.asList("Visible B", "Hidden B", "Hidden A", "Visible A"),
                presentation.fullValues());
        Assert.assertEquals("冲突手势不得提交旧排序", 0, commits.get());
    }

    @Test
    public void externalDraftDuringDragAlsoWinsOverCancelSnapshot() {
        AtomicInteger commits = new AtomicInteger();
        FontSortPresentation presentation = dragPresentation(commits, new AtomicReference<List<String>>());
        ReactiveScheduler.get().flush();
        presentation.beginDrag();
        presentation.resetFromDraft(Arrays.asList("Visible B", "Hidden B"));

        presentation.cancelDrag();
        ReactiveScheduler.get().flush();

        Assert.assertEquals(Arrays.asList("Visible B", "Hidden B", "Hidden A", "Visible A"),
                presentation.fullValues());
        Assert.assertEquals(0, commits.get());
    }

    @Test
    public void sameFramePreviewAndFinishCommitExplicitFinalOrder() {
        AtomicInteger commits = new AtomicInteger();
        AtomicReference<List<String>> submitted = new AtomicReference<List<String>>();
        FontSortPresentation presentation = dragPresentation(commits, submitted);
        ReactiveScheduler.get().flush();
        presentation.setFilter("visible");
        ReactiveScheduler.get().flush();
        presentation.beginDrag();
        List<FontSortPresentation.Row> visible = presentation.filteredSignal().get();
        List<FontSortPresentation.Row> reordered = Arrays.asList(visible.get(1), visible.get(0));

        presentation.previewVisible(reordered);
        presentation.finishDrag(reordered);
        ReactiveScheduler.get().flush();

        Assert.assertEquals("同帧 MOVE→UP 必须提交事件坐标对应的最终顺序", 1, commits.get());
        Assert.assertEquals(Arrays.asList("Hidden A", "Visible B", "Hidden B", "Visible A"),
                submitted.get());
        Assert.assertEquals(submitted.get(), presentation.fullValues());
    }

    @Test
    public void beginDragImmediatelyFreezesCurrentFilterForEventReads() {
        FontSortPresentation presentation = dragPresentation(
                new AtomicInteger(), new AtomicReference<List<String>>());
        ReactiveScheduler.get().flush();
        presentation.setFilter("visible");
        ReactiveScheduler.get().flush();

        presentation.beginDrag();

        Assert.assertEquals("beginDrag 同帧不得回读旧 frozenFilter signal",
                Arrays.asList("Visible A", "Visible B"), values(presentation.immediateFilteredRows()));
    }

    @Test
    public void sameFramePreviewAndCancelForceStartOrderBackIntoSignal() {
        AtomicInteger commits = new AtomicInteger();
        FontSortPresentation presentation = dragPresentation(commits, new AtomicReference<List<String>>());
        ReactiveScheduler.get().flush();
        presentation.setFilter("visible");
        ReactiveScheduler.get().flush();
        List<String> start = presentation.fullValues();
        presentation.beginDrag();
        List<FontSortPresentation.Row> visible = presentation.filteredSignal().get();

        presentation.previewVisible(Arrays.asList(visible.get(1), visible.get(0)));
        presentation.cancelDrag();
        ReactiveScheduler.get().flush();

        Assert.assertEquals("同帧 MOVE→CANCEL 必须覆盖尚未 flush 的预览", start,
                presentation.fullValues());
        Assert.assertEquals(0, commits.get());
    }

    @Test
    public void indexMoveAndRestoreSubmitCompleteMergedList() {
        AtomicReference<List<String>> submitted = new AtomicReference<List<String>>();
        FontSortPresentation presentation = presentation(new AtomicInteger(), submitted);
        ReactiveScheduler.get().flush();
        FontSortPresentation.Row mono = presentation.fullOrderSignal().get().get(2);
        Assert.assertTrue(presentation.moveRow(mono, 1));
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList("Mono", "Serif", "Sans"), submitted.get());
        Assert.assertTrue(presentation.restoreDefault());
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList("Sans", "Serif", "Mono"), submitted.get());
    }

    @Test
    public void sameScreenReloadReMergesFreshDraftAgainstOriginalSnapshotWithoutCommit() {
        AtomicInteger commits = new AtomicInteger();
        FontSortPresentation presentation = presentation(commits, null);
        ReactiveScheduler.get().flush();

        presentation.resetFromDraft(Arrays.asList(" Mono ", "Stale", "Sans"));
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList("Mono", "Sans", "Serif"), presentation.fullValues());
        Assert.assertEquals("reload presentation 不提交", 0, commits.get());
    }

    private static FontSortPresentation presentation(AtomicInteger commits,
                                                       AtomicReference<List<String>> submitted) {
        return new FontSortPresentation(
                Arrays.asList("Sans", "Serif", "Mono", "Sans"),
                Arrays.asList(" Serif ", "Stale", "Sans"),
                values -> {
                    commits.incrementAndGet();
                    if (submitted != null) {
                        submitted.set(values);
                    }
                });
    }

    private static FontSortPresentation dragPresentation(AtomicInteger commits,
                                                          AtomicReference<List<String>> submitted) {
        return new FontSortPresentation(
                Arrays.asList("Hidden A", "Visible A", "Hidden B", "Visible B"),
                Arrays.<String>asList(),
                values -> {
                    commits.incrementAndGet();
                    submitted.set(values);
                });
    }

    private static List<String> values(List<FontSortPresentation.Row> rows) {
        java.util.ArrayList<String> result = new java.util.ArrayList<String>();
        for (FontSortPresentation.Row row : rows) {
            result.add(row.getValue());
        }
        return result;
    }
}
