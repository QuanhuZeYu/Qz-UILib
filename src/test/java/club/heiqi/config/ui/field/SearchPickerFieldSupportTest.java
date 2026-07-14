package club.heiqi.config.ui.field;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.editor.Codec;
import club.heiqi.config.ui.editor.ListMemberCodec;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

import static org.junit.Assert.*;

/** SearchPickerFieldSupport 的注册表边界测试。 */
public class SearchPickerFieldSupportTest {
    /** 清理无 owner 的响应式测试状态。 */
    @After public void tearDown() { ReactiveScheduler.get().reset(); }

    /** 搜索 widget 引用缺失 provider 时必须在装配点 fail-fast。 */
    @Test
    public void missingProviderFailsFast() {
        Registry registry = new Registry();
        registry.freeze();
        ValueSpec spec = ValueSpec.string().withWidget(new SearchPickerSpec("test:missing", 8));

        try {
            SearchPickerFieldSupport.createIfPresent(null, spec, "", registry, value -> { });
            fail("expected missing provider failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("test:missing"));
        }
    }

    /** 旧单参 codec 仍由双参默认方法兼容调用。 */
    @Test
    public void legacyCodecRemainsCompatible() {
        AtomicReference<SearchPickerData.Selection> encodedSelection = new AtomicReference<SearchPickerData.Selection>();
        Codec codec = new Codec() {
            public SearchPickerData.Selection decode(Object value) { return selection(String.valueOf(value)); }
            @Deprecated public Object encode(SearchPickerData.Selection selection) {
                encodedSelection.set(selection);
                return selection.candidateKey();
            }
        };
        PickerFixture fixture = fixture(codec, Signal.<Object>create("before"), ignored -> { });
        fixture.selectCandidate();
        assertEquals("picked", encodedSelection.get().candidateKey());
        fixture.dispose();
    }

    /** 双参 codec 收到确认瞬间 current，且成功写回后清空 query。 */
    @Test
    public void currentValueIsReadAtConfirmationAndSuccessClearsQuery() {
        Signal<Object> value = Signal.<Object>create("initial");
        AtomicReference<Object> encodedCurrent = new AtomicReference<Object>();
        AtomicReference<Object> changed = new AtomicReference<Object>();
        Codec codec = statelessCodec((current, selected) -> {
            encodedCurrent.set(current);
            return current + ":" + selected.candidateKey();
        });
        PickerFixture fixture = fixture(codec, value, changed::set);
        fixture.type("draft");
        value.set("instant");
        ReactiveScheduler.get().flush();
        fixture.selectCandidate();
        assertEquals("instant", encodedCurrent.get());
        assertEquals("instant:picked", changed.get());
        assertEquals("", textOf(fixture.input));
        fixture.dispose();
    }

    /** 两个结构行共享同一无状态 codec，交错确认不会串用另一行当前值。 */
    @Test
    public void sharedCodecInterleavedRowsDoNotLeakCurrentValue() {
        Codec codec = statelessCodec((current, selected) -> current + ":" + selected.candidateKey());
        Signal<Object> first = Signal.<Object>create("row-a");
        Signal<Object> second = Signal.<Object>create("row-b");
        AtomicReference<Object> firstWrite = new AtomicReference<Object>();
        AtomicReference<Object> secondWrite = new AtomicReference<Object>();
        PickerFixture firstFixture = fixture(codec, first, firstWrite::set);
        PickerFixture secondFixture = fixture(codec, second, secondWrite::set);
        first.set("row-a-now");
        secondFixture.selectCandidate();
        second.set("row-b-now");
        firstFixture.selectCandidate();
        assertEquals("row-a-now:picked", firstWrite.get());
        assertEquals("row-b:picked", secondWrite.get());
        firstFixture.dispose();
        secondFixture.dispose();
    }

    /** canonical decode 不进入 query；reset/reload 等价的外部值更新不覆盖用户草稿。 */
    @Test
    public void canonicalAndExternalValueChangesDoNotPopulateOrOverwriteQuery() {
        Signal<Object> value = Signal.<Object>create("canonical");
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected), value, ignored -> { });
        assertEquals("Search", textOf(fixture.input));
        fixture.type("draft");
        value.set("reset-value");
        ReactiveScheduler.get().flush();
        assertEquals("draft", textOf(fixture.input));
        value.set("reload-value");
        ReactiveScheduler.get().flush();
        assertEquals("draft", textOf(fixture.input));
        fixture.dispose();
    }

    /** encode 异常、null 及 onChange 异常均零写并保留 Draft。 */
    @Test
    public void failedEncodingDoesNotWriteOrClearDraft() {
        assertFailedEncodingKeepsDraft(statelessCodec((current, selected) -> null));
        assertFailedEncodingKeepsDraft(statelessCodec((current, selected) -> {
            throw new IllegalStateException("encode");
        }));
        AtomicInteger writes = new AtomicInteger();
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> "encoded"),
                Signal.<Object>create("current"), ignored -> {
                    writes.incrementAndGet();
                    throw new IllegalStateException("onChange");
                });
        fixture.type("draft");
        fixture.selectCandidate();
        assertEquals(1, writes.get());
        assertEquals("draft", textOf(fixture.input));
        fixture.dispose();
    }

    /** decode 异常与 null 显示阶段错误，后续成功解码清错且不写 Draft。 */
    @Test
    public void decodeFailureAndNullAreVisibleAndSuccessClearsError() {
        AtomicInteger mode = new AtomicInteger();
        Codec codec = new Codec() {
            public SearchPickerData.Selection decode(Object value) {
                if (mode.get() == 0) throw new IllegalStateException("decode");
                if (mode.get() == 1) return null;
                return selection(String.valueOf(value));
            }
            public Object encode(SearchPickerData.Selection value) { return value; }
        };
        Signal<Object> value = Signal.<Object>create("current");
        PickerFixture fixture = fixture(codec, value, ignored -> { });
        assertEquals("Decode failed", fixture.errorText());
        mode.set(1); value.set("null"); ReactiveScheduler.get().flush();
        assertEquals("Decode failed", fixture.errorText());
        mode.set(2); value.set("valid"); ReactiveScheduler.get().flush();
        assertEquals("", fixture.errorText());
        fixture.dispose();
    }

    /** search 异常与 null 返回空结果并显示错误，新 query 成功后清错。 */
    @Test
    public void searchFailureAndNullAreVisibleAndNewQueryClearsError() {
        AtomicInteger mode = new AtomicInteger();
        ValueEditorProvider.SearchFunction search = (query, max) -> {
            if (mode.get() == 0) throw new IllegalStateException("search");
            if (mode.get() == 1) return null;
            return result();
        };
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected),
                Signal.<Object>create("current"), ignored -> { }, search);
        assertEquals("Search failed", fixture.errorText());
        mode.set(1); fixture.type("a"); ReactiveScheduler.get().flush();
        assertEquals("Search failed", fixture.errorText());
        mode.set(2); fixture.type("b"); ReactiveScheduler.get().flush();
        assertEquals("", fixture.errorText());
        fixture.dispose();
    }

    /** 列表绑定以稳定 id 精确替换重复候选，并在确认前重排后仍命中同一成员。 */
    @Test
    public void listBindingUsesStableIdAcrossDuplicateKeysAndReorder() {
        Signal<Object> raw = Signal.<Object>create(new java.util.ArrayList<Object>(Arrays.<Object>asList("same:a", "same:b")));
        SceneSimpleList.ListItem first = new SceneSimpleList.ListItem("same:a");
        SceneSimpleList.ListItem second = new SceneSimpleList.ListItem("same:b");
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Arrays.asList(first, second));
        AtomicReference<Object> changed = new AtomicReference<Object>();
        SearchPickerListBinding binding = new SearchPickerListBinding(raw, items, memberCodec(), changed::set);

        binding.edit(second.getId());
        raw.set(new java.util.ArrayList<Object>(Arrays.<Object>asList("same:b", "same:a")));
        items.set(Arrays.asList(second, first));
        ReactiveScheduler.get().flush();
        assertTrue(binding.confirm(selection("picked")));
        assertEquals(Arrays.asList("picked:b", "same:a"), changed.get());
        assertEquals(second.getId(), items.get().get(0).getId());
    }

    /** 删除目标后的迟到确认、异常/null/非 String 与非 String raw 全部零写。 */
    @Test
    public void listBindingRejectsStaleAndInvalidConfirmationWithoutWrites() {
        assertListBindingDoesNotWrite(memberCodec(), "raw", true);
        assertListBindingDoesNotWrite(memberCodec(), Integer.valueOf(1), false);
        assertListBindingDoesNotWrite(memberCodecReturning(null), "raw", false);
        assertListBindingDoesNotWrite(memberCodecReturning(Integer.valueOf(1)), "raw", false);
        assertListBindingDoesNotWrite(memberCodecThrowing(), "raw", false);
        Signal<Object> nonList = Signal.<Object>create("not-a-list");
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Collections.<SceneSimpleList.ListItem>emptyList());
        AtomicInteger writes = new AtomicInteger();
        SearchPickerListBinding binding = new SearchPickerListBinding(nonList, items, memberCodec(),
                ignored -> writes.incrementAndGet());
        binding.add(); ReactiveScheduler.get().flush();
        assertFalse(binding.confirm(selection("picked")));
        assertEquals(0, writes.get());
        assertEquals("not-a-list", nonList.get());
    }

    /** 提交回调抛错后 raw、items 与编辑目标全部保持不变。 */
    @Test
    public void listBindingStopsInternalPublicationWhenConsumerThrows() {
        Signal<Object> raw = Signal.<Object>create(Collections.singletonList("raw:x"));
        SceneSimpleList.ListItem item = new SceneSimpleList.ListItem("raw:x");
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Collections.singletonList(item));
        AtomicInteger entered = new AtomicInteger();
        SearchPickerListBinding binding = new SearchPickerListBinding(raw, items, memberCodec(), value -> {
            entered.incrementAndGet(); throw new IllegalStateException("consumer");
        });
        binding.edit(item.getId()); ReactiveScheduler.get().flush();
        assertFalse(binding.confirm(selection("picked")));
        assertEquals(1, entered.get());
        assertEquals(Collections.singletonList("raw:x"), raw.get());
        assertEquals("raw:x", items.get().get(0).getValue());
        assertEquals(Long.valueOf(item.getId()), binding.editingId().get());
    }

    /** 列表确认失败保留 query、portal 与编辑目标；Manage click 后聚焦 portal 搜索框。 */
    @Test
    public void listPickerFailedCommitKeepsDraftAndPortalVisible() {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        SceneNode preexistingFocus = new SceneNode();
        runtime.focusable(preexistingFocus);
        runtime.requestFocus(preexistingFocus);
        Signal<Object> raw = Signal.<Object>create(Collections.singletonList("raw:x"));
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(
                Collections.singletonList(new SceneSimpleList.ListItem("raw:x")));
        SceneNode picker = SearchPickerFieldSupport.createListMembersIfPresent(runtime,
                ValueSpec.list(ValueSpec.string()).withWidget(new SearchPickerSpec("test:picker", 8,
                        SearchPickerSpec.BindingMode.LIST_MEMBERS)), raw, items,
                registry(memberCodec(), (query, max) -> result()), ignored -> { throw new IllegalStateException("adapter"); });
        harness.mountRoot(picker, 320, 240);
        SceneNode manage = picker.__getChildren().get(1).__getChildren().get(0);
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(picker, new Constraints(320, 240));
        assertSame("builder/装配阶段不得抢走既有焦点", preexistingFocus, runtime.getFocusedNode());
        harness.click(manage);
        ReactiveScheduler.get().flush();
        SceneNode portal = runtime.getOverlayHost().bottomFirst().get(0).getRoot();
        SceneNode input = portal.__getChildren().get(0);
        assertSame("Manage click+flush 且 portal 注册 effect 后必须聚焦搜索框",
                input, runtime.getFocusedNode());
        harness.typeText("draft");
        new SceneLayoutEngine(new FixedTextMeasurer(8, 16)).layout(portal, new Constraints(320, 240));
        harness.click(portal.__getChildren().get(4).__getChildren().get(0).__getChildren().get(0));
        ReactiveScheduler.get().flush();
        assertEquals("draft", textOf(input));
        assertEquals(1, runtime.getOverlayHost().size());
        assertEquals("Encode failed", portal.__getChildren().get(5).getText());
        assertEquals(Collections.singletonList("raw:x"), raw.get());
        assertEquals("raw:x", items.get().get(0).getValue());
        runtime.dispose();
    }

    /** LIST_MEMBERS 删除第一次零写，确认回调异常后保留 portal/确认态并复用错误槽。 */
    @Test
    public void listPickerRejectedDeleteKeepsAuthorityAndShowsExistingError() {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        Signal<Object> raw = Signal.<Object>create(Collections.singletonList("raw:x"));
        SceneSimpleList.ListItem item = new SceneSimpleList.ListItem("raw:x");
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Collections.singletonList(item));
        AtomicInteger attempts = new AtomicInteger();
        SceneNode picker = SearchPickerFieldSupport.createListMembersIfPresent(runtime,
                ValueSpec.list(ValueSpec.string()).withWidget(new SearchPickerSpec("test:picker", 8,
                        SearchPickerSpec.BindingMode.LIST_MEMBERS)), raw, items,
                registry(memberCodec(), (query, max) -> result()), ignored -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("reject");
                });
        harness.mountRoot(picker, 360, 300);
        SceneLayoutEngine layout = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        SceneNode manage = picker.__getChildren().get(1).__getChildren().get(0);
        layout.layout(picker, new Constraints(360, 300));
        harness.click(manage);
        SceneNode portal = runtime.getOverlayHost().bottomFirst().get(0).getRoot();
        layout.layout(portal, new Constraints(360, 300));
        SceneNode row = portal.__getChildren().get(2).__getChildren().get(0).__getChildren().get(0);
        harness.click(visibleActions(row).__getChildren().get(1));
        ReactiveScheduler.get().flush();
        layout.layout(portal, new Constraints(360, 300));
        assertEquals("第一次删除只能进入确认态", 0, attempts.get());
        assertEquals(Arrays.asList("Cancel", "Confirm remove"), texts(visibleActions(row)));

        harness.click(visibleActions(row).__getChildren().get(1));
        ReactiveScheduler.get().flush();
        assertEquals(1, attempts.get());
        assertEquals(Collections.singletonList("raw:x"), raw.get());
        assertSame(item, items.get().get(0));
        assertEquals(1, runtime.getOverlayHost().size());
        assertEquals("Encode failed", portal.__getChildren().get(5).getText());
        assertEquals(Arrays.asList("Cancel", "Confirm remove"), texts(visibleActions(row)));
        runtime.dispose();
    }

    /** 新增只追加；未知与 malformed 成员可见，取消清目标且不写。 */
    @Test
    public void listBindingAppendsAndPreservesUnknownMalformedRaw() {
        Signal<Object> raw = Signal.<Object>create(new java.util.ArrayList<Object>(Arrays.<Object>asList("unknown:x", Integer.valueOf(7))));
        SceneSimpleList.ListItem unknown = new SceneSimpleList.ListItem("unknown:x");
        SceneSimpleList.ListItem malformed = new SceneSimpleList.ListItem("7");
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Arrays.asList(unknown, malformed));
        AtomicReference<Object> changed = new AtomicReference<Object>();
        SearchPickerListBinding binding = new SearchPickerListBinding(raw, items, memberCodec(), changed::set);
        List<SearchPickerData.CurrentMember> shown = binding.currentMembers(result());
        assertEquals("unknown", shown.get(0).selection().candidateKey());
        assertFalse(shown.get(0).enumerated());
        assertNull(shown.get(1).selection());
        assertEquals("7", binding.rawFallback(malformed.getId()));
        assertEquals(Arrays.<Object>asList("unknown:x", Integer.valueOf(7)), raw.get());

        binding.add();
        ReactiveScheduler.get().flush();
        assertTrue(binding.confirm(selection("new")));
        assertEquals(Arrays.<Object>asList("unknown:x", Integer.valueOf(7), "new:"), changed.get());
        binding.edit(unknown.getId());
        binding.cancel();
        ReactiveScheduler.get().flush();
        assertNull(binding.editingId().get());
        assertEquals(Arrays.<Object>asList("unknown:x", Integer.valueOf(7), "new:"), changed.get());
    }

    /** 可添加结果只按合法成员的精确 registry identity 过滤，完整结果仍用于成员解析。 */
    @Test
    public void listPickerExcludesSelectedRegistryKeysWithoutFilteringNamesOrMalformedRaw() {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        Signal<Object> raw = Signal.<Object>create(Arrays.<Object>asList(
                "selected:block@*", "selected:block@4", "selected:block@[4,8]", Integer.valueOf(7)));
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Arrays.asList(
                new SceneSimpleList.ListItem("selected:block@*"),
                new SceneSimpleList.ListItem("selected:block@4"),
                new SceneSimpleList.ListItem("selected:block@[4,8]"),
                new SceneSimpleList.ListItem("7")));
        SearchPickerData.SearchResult complete = new SearchPickerData.SearchResult(Arrays.asList(
                new SearchPickerData.Candidate("selected:block", "Same name",
                        Collections.<SearchPickerData.Variant>emptyList()),
                new SearchPickerData.Candidate("other:block", "Same name",
                        Collections.<SearchPickerData.Variant>emptyList()),
                new SearchPickerData.Candidate("7", "Malformed key remains",
                        Collections.<SearchPickerData.Variant>emptyList())));
        SceneNode picker = SearchPickerFieldSupport.createListMembersIfPresent(runtime,
                ValueSpec.list(ValueSpec.string()).withWidget(new SearchPickerSpec("test:picker", 8,
                        SearchPickerSpec.BindingMode.LIST_MEMBERS)), raw, items,
                registry(registryIdentityMemberCodec(), (query, max) -> complete), ignored -> { });
        harness.mountRoot(picker, 420, 360);
        SceneNode management = picker.__getChildren().get(1);
        harness.click(management.__getChildren().get(0));
        ReactiveScheduler.get().flush();
        SceneNode portal = runtime.getOverlayHost().bottomFirst().get(0).getRoot();

        assertEquals("完整结果必须继续解析三个同 identity 合法成员", 3,
                countText(portal.__getChildren().get(2), "Same name"));
        List<String> addable = texts(portal.__getChildren().get(4));
        assertEquals("已选 registry identity 必须整体排除且不同 key 同名项只留一行",
                1, countText(portal.__getChildren().get(4), "Same name"));
        assertTrue("同显示名不同 key 仍可添加", addable.contains("Same name"));
        assertTrue("malformed raw 不得误过滤同文本 candidate key", addable.contains("Malformed key remains"));
        runtime.dispose();
    }

    /** 空 query 保持空候选，但当前成员按唯一精确 key 独立解析且不接受模糊首项。 */
    @Test
    public void listPickerResolvesCurrentMembersByExactKeyWhenQueryIsEmpty() {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        Signal<Object> raw = Signal.<Object>create(Arrays.<Object>asList("known:a", "known:b", "unknown:x"));
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Arrays.asList(
                new SceneSimpleList.ListItem("known:a"), new SceneSimpleList.ListItem("known:b"),
                new SceneSimpleList.ListItem("unknown:x")));
        AtomicInteger knownSearches = new AtomicInteger();
        ValueEditorProvider.SearchFunction search = (query, max) -> {
            if (query.isEmpty()) return SearchPickerData.SearchResult.empty();
            if ("known".equals(query)) {
                knownSearches.incrementAndGet();
                return new SearchPickerData.SearchResult(Arrays.asList(
                        new SearchPickerData.Candidate("fuzzy", "Wrong", Collections.<SearchPickerData.Variant>emptyList()),
                        new SearchPickerData.Candidate("known", "Known exact", Collections.<SearchPickerData.Variant>emptyList())));
            }
            return SearchPickerData.SearchResult.empty();
        };
        SceneNode picker = SearchPickerFieldSupport.createListMembersIfPresent(runtime,
                ValueSpec.list(ValueSpec.string()).withWidget(new SearchPickerSpec("test:picker", 8,
                        SearchPickerSpec.BindingMode.LIST_MEMBERS)), raw, items,
                registry(memberCodec(), search), ignored -> { });
        harness.mountRoot(picker, 420, 360);
        harness.click(picker.__getChildren().get(1).__getChildren().get(0));
        ReactiveScheduler.get().flush();
        SceneNode portal = runtime.getOverlayHost().bottomFirst().get(0).getRoot();

        assertEquals("重复 key 只应精确搜索一次", 1, knownSearches.get());
        assertEquals("两个已知成员都应投影精确候选", 2,
                countText(portal.__getChildren().get(2), "Known exact"));
        assertTrue("unknown selection 应保留原 key", texts(portal.__getChildren().get(2)).contains("unknown"));
        assertTrue("空 query 不得展示全量候选", texts(portal.__getChildren().get(4)).contains("No matching results"));
        assertFalse("模糊首项不得用于当前成员", texts(portal.__getChildren().get(2)).contains("Wrong"));
        runtime.dispose();
    }

    private static void assertListBindingDoesNotWrite(ListMemberCodec codec, Object rawMember, boolean stale) {
        Signal<Object> raw = Signal.<Object>create(Collections.singletonList(rawMember));
        SceneSimpleList.ListItem item = new SceneSimpleList.ListItem(String.valueOf(rawMember));
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Collections.singletonList(item));
        AtomicInteger writes = new AtomicInteger();
        SearchPickerListBinding binding = new SearchPickerListBinding(raw, items, codec, ignored -> writes.incrementAndGet());
        binding.edit(item.getId());
        if (stale) items.set(Collections.<SceneSimpleList.ListItem>emptyList());
        ReactiveScheduler.get().flush();
        assertFalse(binding.confirm(selection("picked")));
        assertEquals(0, writes.get());
        assertEquals(Collections.singletonList(rawMember), raw.get());
    }

    private static ListMemberCodec memberCodec() { return memberCodecReturningMarker(); }

    /** 测试用 codec：metadata 表达均归一为 @ 前的 registry identity。 */
    private static ListMemberCodec registryIdentityMemberCodec() {
        return new ListMemberCodec() {
            public SearchPickerData.Selection decodeMember(Object raw) {
                if (!(raw instanceof String)) return null;
                String value = (String) raw;
                int metadata = value.indexOf('@');
                return selection(metadata < 0 ? value : value.substring(0, metadata));
            }
            public Object encodeMember(Object current, SearchPickerData.Selection selected) {
                return selected.candidateKey() + "@*";
            }
            public SearchPickerData.Selection decode(Object value) { return null; }
            public Object encode(SearchPickerData.Selection value) { return null; }
        };
    }

    private static ListMemberCodec memberCodecReturningMarker() {
        return new ListMemberCodec() {
            public SearchPickerData.Selection decodeMember(Object raw) {
                if (!(raw instanceof String)) return null;
                String value = (String) raw;
                int split = value.indexOf(':');
                return selection(split < 0 ? value : value.substring(0, split));
            }
            public Object encodeMember(Object current, SearchPickerData.Selection selected) {
                String value = (String) current;
                int split = value.indexOf(':');
                return selected.candidateKey() + (split < 0 ? ":" : value.substring(split));
            }
            public SearchPickerData.Selection decode(Object value) { return null; }
            public Object encode(SearchPickerData.Selection value) { return null; }
        };
    }

    private static ListMemberCodec memberCodecReturning(final Object encoded) {
        return new ListMemberCodec() {
            public SearchPickerData.Selection decodeMember(Object raw) { return selection("raw"); }
            public Object encodeMember(Object raw, SearchPickerData.Selection selected) { return encoded; }
            public SearchPickerData.Selection decode(Object value) { return null; }
            public Object encode(SearchPickerData.Selection value) { return null; }
        };
    }

    private static ListMemberCodec memberCodecThrowing() {
        return new ListMemberCodec() {
            public SearchPickerData.Selection decodeMember(Object raw) { return selection("raw"); }
            public Object encodeMember(Object raw, SearchPickerData.Selection selected) { throw new IllegalStateException("encode"); }
            public SearchPickerData.Selection decode(Object value) { return null; }
            public Object encode(SearchPickerData.Selection value) { return null; }
        };
    }

    private static void assertFailedEncodingKeepsDraft(Codec codec) {
        AtomicInteger writes = new AtomicInteger();
        PickerFixture fixture = fixture(codec, Signal.<Object>create("current"), ignored -> writes.incrementAndGet());
        fixture.type("draft");
        fixture.selectCandidate();
        assertEquals(0, writes.get());
        assertEquals("draft", textOf(fixture.input));
        assertEquals("Encode failed", fixture.errorText());
        fixture.dispose();
    }

    private static PickerFixture fixture(Codec codec, Signal<Object> value, java.util.function.Consumer<Object> onChange) {
        return fixture(codec, value, onChange, (query, max) -> result());
    }

    private static PickerFixture fixture(Codec codec, Signal<Object> value, java.util.function.Consumer<Object> onChange,
                                         ValueEditorProvider.SearchFunction search) {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        SceneNode picker = SearchPickerFieldSupport.createControlledIfPresent(runtime, spec(), value,
                registry(codec, search), onChange);
        harness.mountRoot(picker, 320, 240);
        ReactiveScheduler.get().flush();
        return new PickerFixture(harness, runtime, picker, picker.__getChildren().get(1));
    }

    private static ValueSpec spec() {
        return ValueSpec.string().withWidget(new SearchPickerSpec("test:picker", 8));
    }

    private static Registry registry(final Codec codec) {
        return registry(codec, (query, max) -> result());
    }

    private static Registry registry(final Codec codec, final ValueEditorProvider.SearchFunction search) {
        Registry registry = new Registry();
        registry.register(new ValueEditorProvider() {
            public String id() { return "test:picker"; }
            public Codec codec() { return codec; }
            public VisualAdapter visualAdapter() { return new VisualAdapter() {
                public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
                public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
            }; }
            public SearchFunction searchFunction() { return search; }
            public SearchPickerPresentation presentation() { return SearchPickerPresentation.builder()
                    .decodeError("Decode failed").searchError("Search failed").encodeError("Encode failed").build(); }
        });
        registry.freeze();
        return registry;
    }

    private static SearchPickerData.SearchResult result() {
        return new SearchPickerData.SearchResult(Collections.singletonList(new SearchPickerData.Candidate(
                "picked", "Picked", Collections.<SearchPickerData.Variant>emptyList())));
    }

    private static SearchPickerData.Selection selection(String key) {
        return new SearchPickerData.Selection(key, SearchPickerData.SelectionMode.ALL,
                Collections.<String>emptyList());
    }

    private static String textOf(SceneNode node) {
        for (SceneNode child : node.__getChildren()) {
            if (child.getText() != null && !child.getText().isEmpty()) return child.getText();
            String nested = textOf(child);
            if (!nested.isEmpty()) return nested;
        }
        return "";
    }

    private static List<String> texts(SceneNode node) {
        java.util.ArrayList<String> values = new java.util.ArrayList<String>();
        if (node.getText() != null && !node.getText().isEmpty()) values.add(node.getText());
        for (SceneNode child : node.__getChildren()) values.addAll(texts(child));
        return values;
    }

    private static int countText(SceneNode node, String expected) {
        int count = 0;
        for (String value : texts(node)) if (expected.equals(value)) count++;
        return count;
    }

    private static SceneNode visibleActions(SceneNode row) {
        for (int index = 2; index < row.__getChildren().size(); index++) {
            SceneNode host = row.__getChildren().get(index);
            if (!texts(host).isEmpty()) return host;
        }
        throw new AssertionError("current member row has no visible actions");
    }

    private interface Encoder {
        Object encode(Object current, SearchPickerData.Selection selection);
    }

    private static Codec statelessCodec(final Encoder encoder) {
        return new Codec() {
            public SearchPickerData.Selection decode(Object value) { return selection(String.valueOf(value)); }
            public Object encode(Object currentValue, SearchPickerData.Selection selection) {
                return encoder.encode(currentValue, selection);
            }
        };
    }

    private static final class PickerFixture {
        private final SceneInteractionHarness harness;
        private final SceneRuntime runtime;
        private final SceneNode picker;
        private final SceneNode input;
        private final SceneLayoutEngine layout = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));

        private PickerFixture(SceneInteractionHarness harness, SceneRuntime runtime, SceneNode picker, SceneNode input) {
            this.harness = harness;
            this.runtime = runtime;
            this.picker = picker;
            this.input = input;
        }

        private void type(String value) {
            runtime.requestFocus(input);
            ReactiveScheduler.get().flush();
            harness.typeText(value);
        }

        private void selectCandidate() {
            layout.layout(picker, new Constraints(320, 240));
            harness.click(input);
            SceneNode portal = runtime.getOverlayHost().bottomFirst().get(0).getRoot();
            layout.layout(portal, new Constraints(320, 240));
            SceneNode item = portal
                    .__getChildren().get(0).__getChildren().get(0);
            harness.click(item);
            ReactiveScheduler.get().flush();
        }

        private String errorText() { return picker.__getChildren().get(2).getText(); }

        private void dispose() { runtime.dispose(); }
    }
}
