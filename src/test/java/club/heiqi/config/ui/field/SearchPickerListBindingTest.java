package club.heiqi.config.ui.field;

import java.util.ArrayList;
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
        assertEquals(2, items.get().size());
        assertSame("成功删除后必须精确保留第一项身份", first, items.get().get(0));
        assertSame("成功删除后必须精确保留尾项身份", tail, items.get().get(1));
        assertNull("成功删除必须清除同一成员的编辑目标", binding.editingId().get());
        try {
            @SuppressWarnings("unchecked") List<Object> immutable = (List<Object>) published.get();
            immutable.add("forbidden");
            fail("published list must be immutable");
        } catch (UnsupportedOperationException expected) { }
    }

    /**
     * E1/E3（P5 §5.5）：删除后撤销必须<b>原顺序、原值</b>插回 —— 重复 raw 场景下
     * 不能靠文本重新定位槽位（那是新的错误来源），直接按 tombstone 的下标插回。
     */
    @Test
    public void undoRestoresRemovedMemberAtOriginalIndexAndValue() {
        Signal<Object> raw = Signal.<Object>create(Arrays.<Object>asList("same:x", "same:x", "tail:y"));
        SceneSimpleList.ListItem first = new SceneSimpleList.ListItem("same:x");
        SceneSimpleList.ListItem second = new SceneSimpleList.ListItem("same:x");
        SceneSimpleList.ListItem tail = new SceneSimpleList.ListItem("tail:y");
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Arrays.asList(first, second, tail));
        AtomicReference<List<?>> published = new AtomicReference<List<?>>();
        SearchPickerListBinding binding = binding(raw, items, value -> {
            raw.set(value);
            published.set((List<?>) value);
        });

        assertTrue(binding.remove(second.getId()));
        ReactiveScheduler.get().flush();
        assertTrue("删除后持有唯一 tombstone", binding.hasRemoved());
        assertEquals("tombstone 记录被删成员 id", second.getId(), binding.removedMemberId());
        assertEquals(Arrays.asList("same:x", "tail:y"), published.get());

        assertFalse("陈旧 id 的撤销必须被拒绝（防误恢复）", binding.restoreRemoved(first.getId()));
        assertTrue("按原下标与原值插回", binding.restoreRemoved(second.getId()));
        ReactiveScheduler.get().flush();
        assertEquals("原顺序恢复（中位元素回到中位）", Arrays.asList("same:x", "same:x", "tail:y"),
                published.get());
        assertSame("恢复的是同一个派生 item 身份", second, items.get().get(1));
        assertFalse("撤销后 tombstone 释放（至多 1 条）", binding.hasRemoved());
        assertFalse("无 tombstone 时再撤销为空操作", binding.restoreRemoved(second.getId()));
    }

    /**
     * E5（P5 §5.5）：一次会话内连续删除 10 项 —— tombstone 恒为 1 条（新删除替换旧的，无累积）、
     * 陈旧 id 撤销被拒、最近一次撤销按原序原值恢复、撤销后释放。
     *
     * <p>有界性判据来自结构而非计数：宿主只持有一个 {@code removed} 槽位，每一步都必须满足
     * 「hasRemoved() == true 且 removedMemberId() == 刚删的 id」——任何累积实现都会在陈旧 id 撤销时暴露。</p>
     */
    @Test
    public void tenConsecutiveRemovalsKeepExactlyOneTombstone() {
        ArrayList<Object> initialRaw = new ArrayList<Object>();
        ArrayList<SceneSimpleList.ListItem> initialItems = new ArrayList<SceneSimpleList.ListItem>();
        for (int index = 0; index < 12; index++) {
            initialRaw.add("m" + index + ":x");
            initialItems.add(new SceneSimpleList.ListItem("m" + index + ":x"));
        }
        Signal<Object> raw = Signal.<Object>create(initialRaw);
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(initialItems);
        SearchPickerListBinding binding = binding(raw, items, raw::set);

        long firstRemovedId = initialItems.get(0).getId();
        long lastRemovedId = -1L;
        for (int step = 0; step < 10; step++) {
            long id = items.get().get(0).getId();
            assertTrue("第 " + (step + 1) + " 次删除必须成功", binding.remove(id));
            // Signal 写是帧末批处理：按生产帧边界 flush 后再读，观察值才是权威值。
            ReactiveScheduler.get().flush();
            lastRemovedId = id;
            assertTrue("任意时刻必须持有可撤销删除", binding.hasRemoved());
            assertEquals("tombstone 恒为最近一次删除（至多 1 条）", id, binding.removedMemberId());
            assertEquals("每次删除原始值恰好减一", 11 - step, ((List<?>) raw.get()).size());
            assertTrue("删除必须按序移除首项", !((List<?>) raw.get()).contains("m" + step + ":x"));
        }

        assertFalse("陈旧 id 的撤销必须被拒（证明无累积 tombstone）",
                binding.restoreRemoved(firstRemovedId));
        assertEquals("被拒的撤销不得改变 tombstone", lastRemovedId, binding.removedMemberId());
        assertTrue("最近一次删除可撤销", binding.restoreRemoved(lastRemovedId));
        ReactiveScheduler.get().flush();
        assertEquals("撤销后原始值回到 3 项（10 删 + 1 撤销）", 3, ((List<?>) raw.get()).size());
        // 10 次删除依次移除 m0..m9：最近一次删除 = m9（原下标 0），撤销必须按原下标插回首位。
        assertEquals("撤销按原下标插回（最近删除项回到首位）", "m9:x", ((List<?>) raw.get()).get(0));
        assertFalse("撤销后 tombstone 释放", binding.hasRemoved());
        assertEquals("释放后探针回落 -1", -1L, binding.removedMemberId());

        assertTrue("再删一次（第 11 次）：tombstone 被新删除替换", binding.remove(items.get().get(0).getId()));
        ReactiveScheduler.get().flush();
        int sizeBeforeDiscard = ((List<?>) raw.get()).size();
        binding.discardRemoved();
        assertFalse("窗口到期/关闭丢弃后 tombstone 释放", binding.hasRemoved());
        assertEquals("丢弃不得改变配置值（丢弃只丢撤销能力）", sizeBeforeDiscard, ((List<?>) raw.get()).size());
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

    /** 未武装（未点「添加」/「编辑」）时 confirm 按隐式新增追加成员（点击候选即添加）。 */
    @Test
    public void unarmedConfirmAppendsImplicitly() {
        Signal<Object> raw = Signal.<Object>create(new ArrayList<Object>(Collections.singletonList("raw:a")));
        SceneSimpleList.ListItem first = new SceneSimpleList.ListItem("raw:a");
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(
                new ArrayList<SceneSimpleList.ListItem>(Collections.singletonList(first)));
        AtomicReference<Object> changed = new AtomicReference<Object>();
        ListMemberCodec codec = new ListMemberCodec() {
            public SearchPickerData.Selection decodeMember(Object value) { return null; }
            public Object encodeMember(Object current, SearchPickerData.Selection selected) {
                return selected.candidateKey() + ":";
            }
            public SearchPickerData.Selection decode(Object value) { return null; }
            public Object encode(SearchPickerData.Selection value) { return null; }
        };
        SearchPickerListBinding binding = new SearchPickerListBinding(raw, items, codec, changed::set);
        assertNull("初始未武装", binding.editingId().get());
        assertTrue(binding.confirm(new SearchPickerData.Selection("picked",
                SearchPickerData.SelectionMode.ALL, Collections.<String>emptyList())));
        ReactiveScheduler.get().flush();
        assertEquals(Arrays.asList("raw:a", "picked:"), changed.get());
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
