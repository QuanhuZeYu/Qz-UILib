package club.heiqi.config.ui.field;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

import club.heiqi.config.ui.editor.ListMemberCodec;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;

import static org.junit.Assert.*;

/** SearchPickerListBinding 稳定成员删除事务测试。 */
public class SearchPickerListBindingTest {
    /** 清理响应式测试状态。 */
    @After public void tearDown() { ReactiveScheduler.get().reset(); }

    /** 重复 raw/candidate 只按最新稳定 id 删除第二项，且只提交一次不可变列表。 */
    @Test
    public void removesSecondDuplicateByStableIdExactlyOnce() {
        Signal<Object> raw = Signal.<Object>create(Arrays.<Object>asList("same:x", "same:x", "tail:y"));
        SceneSimpleList.ListItem first = new SceneSimpleList.ListItem("same:x");
        SceneSimpleList.ListItem second = new SceneSimpleList.ListItem("same:x");
        SceneSimpleList.ListItem tail = new SceneSimpleList.ListItem("tail:y");
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Arrays.asList(first, second, tail));
        AtomicInteger writes = new AtomicInteger();
        AtomicReference<List<?>> published = new AtomicReference<List<?>>();
        SearchPickerListBinding binding = binding(raw, items, value -> {
            writes.incrementAndGet();
            published.set((List<?>) value);
        });
        binding.edit(second.getId());
        ReactiveScheduler.get().flush();

        assertTrue(binding.remove(second.getId()));
        ReactiveScheduler.get().flush();
        assertEquals(1, writes.get());
        assertEquals(Arrays.asList("same:x", "tail:y"), published.get());
        assertSame("派生 items 必须等待权威回灌，不得抢先推进", second, items.get().get(1));
        assertNull("成功删除必须清除同一成员的编辑目标", binding.editingId().get());
        try {
            @SuppressWarnings("unchecked") List<Object> immutable = (List<Object>) published.get();
            immutable.add("forbidden");
            fail("published list must be immutable");
        } catch (UnsupportedOperationException expected) { }
    }

    /** malformed 成员无需 decode 即可按稳定 id 删除。 */
    @Test
    public void removesMalformedMemberWithoutDecoding() {
        Signal<Object> raw = Signal.<Object>create(Arrays.<Object>asList(Integer.valueOf(7), "ok:x"));
        SceneSimpleList.ListItem malformed = new SceneSimpleList.ListItem("7");
        SceneSimpleList.ListItem valid = new SceneSimpleList.ListItem("ok:x");
        AtomicReference<Object> changed = new AtomicReference<Object>();
        SearchPickerListBinding binding = binding(raw, Signal.create(Arrays.asList(malformed, valid)), changed::set);

        assertTrue(binding.remove(malformed.getId()));
        assertEquals(Collections.singletonList("ok:x"), changed.get());
    }

    /** stale、非列表与长度失配均零提交。 */
    @Test
    public void rejectsStaleNonListAndLengthMismatchWithoutWrites() {
        assertRejected(Signal.<Object>create(Collections.singletonList("x")),
                Signal.create(Collections.<SceneSimpleList.ListItem>emptyList()), 99L);
        SceneSimpleList.ListItem item = new SceneSimpleList.ListItem("x");
        assertRejected(Signal.<Object>create("not-list"), Signal.create(Collections.singletonList(item)), item.getId());
        assertRejected(Signal.<Object>create(Arrays.<Object>asList("x", "y")),
                Signal.create(Collections.singletonList(item)), item.getId());
    }

    /** 提交回调异常视为拒绝，raw、items 与编辑目标均不推进。 */
    @Test
    public void consumerFailureLeavesEveryInternalStateUntouched() {
        Signal<Object> raw = Signal.<Object>create(Collections.singletonList(Integer.valueOf(7)));
        SceneSimpleList.ListItem item = new SceneSimpleList.ListItem("7");
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Collections.singletonList(item));
        AtomicInteger calls = new AtomicInteger();
        SearchPickerListBinding binding = binding(raw, items, ignored -> {
            calls.incrementAndGet();
            throw new IllegalStateException("reject");
        });
        binding.edit(item.getId());
        ReactiveScheduler.get().flush();

        assertFalse(binding.remove(item.getId()));
        assertEquals(1, calls.get());
        assertEquals(Collections.singletonList(Integer.valueOf(7)), raw.get());
        assertSame(item, items.get().get(0));
        assertEquals(Long.valueOf(item.getId()), binding.editingId().get());
    }

    private static void assertRejected(Signal<Object> raw, Signal<List<SceneSimpleList.ListItem>> items,
                                       long memberId) {
        AtomicInteger writes = new AtomicInteger();
        SearchPickerListBinding binding = binding(raw, items, ignored -> writes.incrementAndGet());
        assertFalse(binding.remove(memberId));
        assertEquals(0, writes.get());
    }

    private static SearchPickerListBinding binding(Signal<Object> raw,
                                                    Signal<List<SceneSimpleList.ListItem>> items,
                                                    java.util.function.Consumer<Object> onChange) {
        return new SearchPickerListBinding(raw, items, new ListMemberCodec() {
            public SearchPickerData.Selection decodeMember(Object value) {
                throw new AssertionError("delete must not decode");
            }
            public Object encodeMember(Object current, SearchPickerData.Selection selected) {
                throw new AssertionError("delete must not encode");
            }
            public SearchPickerData.Selection decode(Object value) { return null; }
            public Object encode(SearchPickerData.Selection value) { return null; }
        }, onChange);
    }
}
