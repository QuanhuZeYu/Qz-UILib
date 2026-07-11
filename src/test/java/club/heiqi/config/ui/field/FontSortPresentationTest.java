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
        presentation.previewVisible(Arrays.asList(visible.get(1), visible.get(0)));
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList("Hidden A", "Visible B", "Hidden B", "Visible A"),
                presentation.fullValues());
        presentation.finishDrag();
        ReactiveScheduler.get().flush();
        Assert.assertEquals(1, commits.get());
        Assert.assertEquals(presentation.fullValues(), submitted.get());

        presentation.beginDrag();
        presentation.previewVisible(Arrays.asList(presentation.filteredSignal().get().get(1),
                presentation.filteredSignal().get().get(0)));
        ReactiveScheduler.get().flush();
        presentation.cancelDrag();
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList("Hidden A", "Visible B", "Hidden B", "Visible A"),
                presentation.fullValues());
        Assert.assertEquals(1, commits.get());
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
