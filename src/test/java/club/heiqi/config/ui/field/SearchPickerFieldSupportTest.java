package club.heiqi.config.ui.field;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.editor.CategorizedValueEditorProvider;
import club.heiqi.config.ui.editor.Codec;
import club.heiqi.config.ui.editor.CurrentValuePresenter;
import club.heiqi.config.ui.editor.ListMemberCodec;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPanelPresentation;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

import static org.junit.Assert.*;

/**
 * SearchPickerFieldSupport 全屏面板接线契约测试。
 *
 * <p>覆盖：装配 fail-fast、SINGLE_VALUE 行触发器（CurrentValuePresenter 展示）与受控居中 70%
 * 开合、ESC 先 onCancel 再关闭并恢复焦点到触发器、可拒绝 selectionCommit（encode 校验、
 * 面板保持展开、query 保留）、decode/search 错误可见性与清错、分组透传与退化、LIST_MEMBERS
 * 摘要与管理入口、稳定成员删除事务、变体浮层搜索接线（SINGLE_VALUE 与 LIST_MEMBERS
 * 均无条件渲染变体搜索输入）。</p>
 */
public class SearchPickerFieldSupportTest {
    private static final int PANEL_W = 1000;
    private static final int PANEL_H = 700;

    /** 清理无 owner 的响应式测试状态。 */
    @After public void tearDown() { ReactiveScheduler.get().reset(); }

    // ==================== 装配边界 ====================

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

    /** LIST_MEMBERS widget 必须走显式列表绑定入口。 */
    @Test
    public void listMembersModeRequiresExplicitBinding() {
        Registry registry = registry(statelessCodec((current, selected) -> selected), (query, max) -> result());
        ValueSpec spec = ValueSpec.string().withWidget(new SearchPickerSpec("test:picker", 8,
                SearchPickerSpec.BindingMode.LIST_MEMBERS));
        try {
            SearchPickerFieldSupport.createControlledIfPresent(null, spec, Signal.<Object>create(""),
                    registry, value -> { });
            fail("expected LIST_MEMBERS binding failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("LIST_MEMBERS"));
        }
    }

    /** 非搜索 widget 返回 null，不参与渲染。 */
    @Test
    public void nonPickerWidgetReturnsNull() {
        Registry registry = new Registry();
        registry.freeze();
        assertNull(SearchPickerFieldSupport.createIfPresent(null, ValueSpec.string(), "", registry, value -> { }));
    }

    // ==================== SINGLE_VALUE 行触发器与受控开合 ====================

    /** 行触发器常驻 presenter 展示；点击打开全屏面板，ESC 先 onCancel（清 query）再关闭并恢复焦点。 */
    @Test
    public void triggerShowsCurrentValueOpensPanelAndEscapeCancelsWithFocusRestore() {
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected.candidateKey()),
                Signal.<Object>create("before"), ignored -> { });
        assertTrue("触发器应展示 presenter 主文本", containsText(fixture.trigger, "before"));
        assertTrue("触发器应展示 presenter 副文本", containsText(fixture.trigger, "summary-before"));
        assertTrue(fixture.runtime.getOverlayHost().isEmpty());

        fixture.openPanel();
        assertEquals("点击触发器应打开全屏面板", 1, fixture.runtime.getOverlayHost().size());
        fixture.type("draft");
        assertEquals("draft", fixture.searchText());

        fixture.pressEscape();
        assertTrue("ESC 后应请求受控关闭", fixture.runtime.getOverlayHost().isEmpty());
        assertSame("关闭后焦点应恢复到行触发器", fixture.trigger, fixture.runtime.getFocusedNode());

        fixture.openPanel();
        assertEquals("ESC 的 onCancel 必须清空 query", "", fixture.searchText());
        fixture.dispose();
    }

    /** 旧单参 codec 仍由双参默认方法兼容调用。 */
    @Test
    public void legacyCodecRemainsCompatible() {
        AtomicReference<SearchPickerData.Selection> encodedSelection =
                new AtomicReference<SearchPickerData.Selection>();
        Codec codec = new Codec() {
            public SearchPickerData.Selection decode(Object value) { return selection(String.valueOf(value)); }
            @Deprecated public Object encode(SearchPickerData.Selection selection) {
                encodedSelection.set(selection);
                return selection.candidateKey();
            }
        };
        PickerFixture fixture = fixture(codec, Signal.<Object>create("before"), ignored -> { });
        fixture.openPanel();
        fixture.selectCandidate();
        assertEquals("picked", encodedSelection.get().candidateKey());
        fixture.dispose();
    }

    /** 双参 codec 收到确认瞬间 current，成功写回后关闭并清空 query。 */
    @Test
    public void currentValueIsReadAtConfirmationAndSuccessClearsQuery() {
        Signal<Object> value = Signal.<Object>create("initial");
        AtomicReference<Object> encodedCurrent = new AtomicReference<Object>();
        AtomicReference<Object> changed = new AtomicReference<Object>();
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> {
            encodedCurrent.set(current);
            return current + ":" + selected.candidateKey();
        }), value, changed::set);
        fixture.openPanel();
        fixture.type("draft");
        value.set("instant");
        ReactiveScheduler.get().flush();
        fixture.selectCandidate();
        assertEquals("instant", encodedCurrent.get());
        assertEquals("instant:picked", changed.get());
        assertTrue("成功提交应请求关闭面板", fixture.runtime.getOverlayHost().isEmpty());
        fixture.openPanel();
        assertEquals("成功写回应清空 query", "", fixture.searchText());
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
        secondFixture.openPanel();
        secondFixture.selectCandidate();
        second.set("row-b-now");
        firstFixture.openPanel();
        firstFixture.selectCandidate();
        assertEquals("row-a-now:picked", firstWrite.get());
        assertEquals("row-b:picked", secondWrite.get());
        firstFixture.dispose();
        secondFixture.dispose();
    }

    /** reset/reload 等价的外部值更新不覆盖面板内用户草稿。 */
    @Test
    public void canonicalAndExternalValueChangesDoNotOverwritePanelQuery() {
        Signal<Object> value = Signal.<Object>create("canonical");
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected), value, ignored -> { });
        fixture.openPanel();
        assertEquals("", fixture.searchText());
        fixture.type("draft");
        value.set("reset-value");
        ReactiveScheduler.get().flush();
        assertEquals("draft", fixture.searchText());
        value.set("reload-value");
        ReactiveScheduler.get().flush();
        assertEquals("draft", fixture.searchText());
        fixture.dispose();
    }

    /** encode 异常、null 及 onChange 异常均零写、保留面板展开与 query。 */
    @Test
    public void failedEncodingKeepsPanelOpenAndPreservesQuery() {
        assertFailedEncodingKeepsPanelOpen(statelessCodec((current, selected) -> null));
        assertFailedEncodingKeepsPanelOpen(statelessCodec((current, selected) -> {
            throw new IllegalStateException("encode");
        }));
        AtomicInteger writes = new AtomicInteger();
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> "encoded"),
                Signal.<Object>create("current"), ignored -> {
                    writes.incrementAndGet();
                    throw new IllegalStateException("onChange");
                });
        fixture.openPanel();
        fixture.type("draft");
        fixture.selectCandidate();
        assertEquals(1, writes.get());
        assertEquals("提交被拒后面板保持展开", 1, fixture.runtime.getOverlayHost().size());
        assertEquals("draft", fixture.searchText());
        assertEquals("Encode failed", fixture.errorText());
        fixture.dispose();
    }

    /** decode 异常与 null 在面板打开时显示错误，后续成功解码清错且不写 Draft。 */
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
        fixture.openPanel();
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
        fixture.openPanel();
        assertEquals("Search failed", fixture.errorText());
        mode.set(1); fixture.type("a");
        assertEquals("Search failed", fixture.errorText());
        mode.set(2); fixture.type("b");
        assertEquals("", fixture.errorText());
        fixture.dispose();
    }

    // ==================== 分组透传 ====================

    /** 分组 provider 注册后透传分类导航：全部 + 声明分类，切换后按分类过滤网格。 */
    @Test
    public void categorizedProviderWiresCategoriesIntoPanelNav() {
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected.candidateKey()),
                Signal.<Object>create("before"), ignored -> { }, (query, max) -> new SearchPickerData.SearchResult(
                        Arrays.asList(candidate("a"), candidate("b"), candidate("c"))),
                categorizedProvider());
        fixture.openPanel();
        SceneNode panel = panelRoot(fixture.runtime);
        SceneNode navRows = categoryNav(panel).__getChildren().get(0);
        assertEquals("全部 + 两个声明分类", 3, navRows.__getChildren().size());
        assertEquals("初始网格显示全部候选", 3, gridCellCount(panel));

        fixture.harness.click(navRows.__getChildren().get(1));
        fixture.runtime.flush();
        fixture.layoutPanel();
        assertEquals("分类切换后网格只剩 cat1 候选", 2, gridCellCount(panel));
        assertTrue(containsText(gridViewport(panel), "a:label"));
        assertTrue(containsText(gridViewport(panel), "b:label"));
        assertFalse(containsText(gridViewport(panel), "c:label"));
        fixture.dispose();
    }

    /** 未实现分组契约的 provider 退化单分类：分类导航只剩全部行。 */
    @Test
    public void plainProviderDegradesToSingleAllCategory() {
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected.candidateKey()),
                Signal.<Object>create("before"), ignored -> { });
        fixture.openPanel();
        SceneNode navRows = categoryNav(panelRoot(fixture.runtime)).__getChildren().get(0);
        assertEquals("无分组时只渲染全部行", 1, navRows.__getChildren().size());
        fixture.dispose();
    }

    // ==================== 多维度分组接线 ====================

    /** 多维度 provider 渲染维度分段；切换维度后分类重派生、当前分类 key 复位为「全部」。 */
    @Test
    public void multiDimensionProviderWiresDimensionSwitchingAndResetsCategoryKey() {
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected.candidateKey()),
                Signal.<Object>create("before"), ignored -> { }, (query, max) -> new SearchPickerData.SearchResult(
                        Arrays.asList(candidate("a"), candidate("b"), candidate("c"))),
                multiDimensionProvider());
        fixture.openPanel();
        SceneNode panel = panelRoot(fixture.runtime);
        SceneNode segmented = panel.__getChildren().get(0).__getChildren().get(2);
        assertTrue("顶栏应渲染维度分段控件", containsText(segmented, "Tabs"));
        assertTrue("顶栏应渲染维度分段控件", containsText(segmented, "Mods"));

        SceneNode navRows = categoryNav(panel).__getChildren().get(0);
        assertEquals("初始维度 0：全部 + 两个分类", 3, navRows.__getChildren().size());
        assertTrue(containsText(navRows, "Blocks"));
        assertTrue(containsText(navRows, "Misc"));

        fixture.harness.click(navRows.__getChildren().get(1));
        fixture.runtime.flush();
        fixture.layoutPanel();
        assertEquals("维度 0 选择 Blocks 后网格只剩 a/b", 2, gridCellCount(panel));

        fixture.harness.click(segmented.__getChildren().get(1));
        fixture.runtime.flush();
        fixture.layoutPanel();
        SceneNode switchedNav = categoryNav(panel).__getChildren().get(0);
        assertEquals("维度 1：全部 + 两个分类", 3, switchedNav.__getChildren().size());
        assertTrue("切换维度后分类列表重派生", containsText(switchedNav, "Mod A"));
        assertTrue(containsText(switchedNav, "Mod B"));
        assertEquals("切换维度后 currentCategoryKey 复位为全部", 3, gridCellCount(panel));
        fixture.dispose();
    }

    /** 单维度/无分组 provider 保持静态透传：不渲染维度分段、顶栏结构不变。 */
    @Test
    public void singleDimensionAndPlainProvidersDoNotWireDimensionSwitching() {
        assertNoDimensionSegmented(categorizedProvider());
        assertNoDimensionSegmented(new ValueEditorProvider() {
            public String id() { return "test:picker"; }
            public Codec codec() { return statelessCodec((current, selected) -> selected.candidateKey()); }
            public VisualAdapter visualAdapter() { return SearchPickerFieldSupportTest.visualAdapter(); }
            public SearchFunction searchFunction() { return (query, max) -> result(); }
            public SearchPickerPresentation presentation() { return failurePresentation(); }
            public CurrentValuePresenter currentValuePresenter() { return value ->
                    new CurrentValuePresenter.Presentation(String.valueOf(value), "summary-" + value, null); };
        });
    }

    /** LIST_MEMBERS 接线同样覆盖：管理面板渲染维度分段，切换维度后分类重派生。 */
    @Test
    public void listMembersMultiDimensionProviderWiresDimensionSwitching() {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        Signal<Object> raw = Signal.<Object>create(Arrays.<Object>asList("raw:x"));
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(
                Arrays.asList(new SceneSimpleList.ListItem("raw:x")));
        Registry registry = new Registry();
        registry.register(multiDimensionProvider(memberCodec()));
        registry.freeze();
        SceneNode picker = SearchPickerFieldSupport.createListMembersIfPresent(runtime,
                ValueSpec.list(ValueSpec.string()).withWidget(new SearchPickerSpec("test:picker", 8,
                        SearchPickerSpec.BindingMode.LIST_MEMBERS)), raw, items, registry, ignored -> { });
        harness.mountRoot(picker, 640, 420);
        harness.click(picker.__getChildren().get(0).__getChildren().get(0));
        ReactiveScheduler.get().flush();
        SceneNode panel = panelRoot(runtime);
        layoutPanel(runtime);
        SceneNode segmented = panel.__getChildren().get(0).__getChildren().get(2);
        assertTrue("LIST_MEMBERS 面板应渲染维度分段", containsText(segmented, "Mods"));
        assertEquals("维度 0 分类导航为全部 + 2 行", 3,
                categoryNav(panel).__getChildren().get(0).__getChildren().size());

        harness.click(segmented.__getChildren().get(1));
        ReactiveScheduler.get().flush();
        layoutPanel(runtime);
        SceneNode switchedNav = categoryNav(panel).__getChildren().get(0);
        assertTrue("切换维度后分类列表重派生", containsText(switchedNav, "Mod A"));
        runtime.dispose();
    }

    // ==================== panelPresentation 透传 ====================

    /** provider 覆盖 panelPresentation 时全屏面板渲染注入的中文标题。 */
    @Test
    public void providerPanelPresentationIsWiredIntoPanel() {
        ValueEditorProvider chinese = new ValueEditorProvider() {
            public String id() { return "test:picker"; }
            public Codec codec() { return statelessCodec((current, selected) -> selected.candidateKey()); }
            public VisualAdapter visualAdapter() { return SearchPickerFieldSupportTest.visualAdapter(); }
            public SearchFunction searchFunction() { return (query, max) -> result(); }
            public SearchPickerPanelPresentation panelPresentation() {
                return SearchPickerPanelPresentation.builder().panelTitle("选择物品").build();
            }
        };
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected.candidateKey()),
                Signal.<Object>create("before"), ignored -> { }, (query, max) -> result(), chinese);
        fixture.openPanel();
        assertTrue("面板应渲染 provider 注入的中文标题",
                containsText(panelRoot(fixture.runtime), "选择物品"));
        fixture.dispose();
    }

    /** provider 未覆盖 panelPresentation 时面板回退英文默认文案。 */
    @Test
    public void missingPanelPresentationFallsBackToEnglishDefault() {
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected.candidateKey()),
                Signal.<Object>create("before"), ignored -> { });
        fixture.openPanel();
        SceneNode panel = panelRoot(fixture.runtime);
        assertTrue("缺省应回退英文默认标题", containsText(panel, "Select a value"));
        assertFalse("缺省不得渲染自定义标题", containsText(panel, "选择物品"));
        fixture.dispose();
    }

    // ==================== LIST_MEMBERS binding 单元 ====================

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

    // ==================== LIST_MEMBERS 全屏面板 ====================

    /** 行触发器保持配置摘要与管理按钮；点击打开含右栏成员的全屏面板，ESC 复位并恢复焦点。 */
    @Test
    public void listMembersTriggerShowsSummaryAndManageOpensPanel() {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        Signal<Object> raw = Signal.<Object>create(Arrays.<Object>asList("raw:x", "raw:y"));
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(Arrays.asList(
                new SceneSimpleList.ListItem("raw:x"), new SceneSimpleList.ListItem("raw:y")));
        SceneNode picker = SearchPickerFieldSupport.createListMembersIfPresent(runtime,
                ValueSpec.list(ValueSpec.string()).withWidget(new SearchPickerSpec("test:picker", 8,
                        SearchPickerSpec.BindingMode.LIST_MEMBERS)), raw, items,
                registry(memberCodec(), (query, max) -> result()), ignored -> { });
        harness.mountRoot(picker, 640, 420);
        ReactiveScheduler.get().flush();
        SceneNode management = picker.__getChildren().get(0);
        SceneNode manage = management.__getChildren().get(0);
        assertTrue("摘要应显示已配置数量", containsText(picker, "Configured 2 items"));

        harness.click(manage);
        ReactiveScheduler.get().flush();
        assertEquals("管理按钮应打开全屏面板", 1, runtime.getOverlayHost().size());
        SceneNode panel = panelRoot(runtime);
        layoutPanel(runtime);
        layoutPanel(runtime);
        assertEquals("右栏应渲染两个当前成员卡片", 2, memberCellCount(panel));

        harness.pressKey(SceneKey.ESCAPE);
        ReactiveScheduler.get().flush();
        assertTrue("ESC 后应关闭面板", runtime.getOverlayHost().isEmpty());
        assertSame("关闭后焦点应恢复到管理按钮", manage, runtime.getFocusedNode());
        runtime.dispose();
    }

    /** 列表确认失败保留面板、query 与成员；错误显示在面板中栏。 */
    @Test
    public void listPickerFailedCommitKeepsPanelOpenAndShowsError() {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        Signal<Object> raw = Signal.<Object>create(Collections.singletonList("raw:x"));
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(
                Collections.singletonList(new SceneSimpleList.ListItem("raw:x")));
        SceneNode picker = SearchPickerFieldSupport.createListMembersIfPresent(runtime,
                ValueSpec.list(ValueSpec.string()).withWidget(new SearchPickerSpec("test:picker", 8,
                        SearchPickerSpec.BindingMode.LIST_MEMBERS)), raw, items,
                registry(memberCodec(), (query, max) -> result()),
                ignored -> { throw new IllegalStateException("adapter"); });
        harness.mountRoot(picker, 640, 420);
        harness.click(picker.__getChildren().get(0).__getChildren().get(0));
        ReactiveScheduler.get().flush();
        SceneNode panel = panelRoot(runtime);
        layoutPanel(runtime);
        harness.click(memberAction(memberCell(panel, 0), 0));
        ReactiveScheduler.get().flush();
        SceneNode input = searchInput(panel);
        runtime.requestFocus(input);
        ReactiveScheduler.get().flush();
        harness.typeText("draft");
        ReactiveScheduler.get().flush();
        layoutPanel(runtime);
        harness.click(gridCell(panel, 0));
        ReactiveScheduler.get().flush();
        assertEquals("draft", textOf(input));
        assertEquals("提交失败后面板保持展开", 1, runtime.getOverlayHost().size());
        assertEquals("Encode failed", errorText(panel).getText());
        assertEquals(Collections.singletonList("raw:x"), raw.get());
        assertEquals("raw:x", items.get().get(0).getValue());
        runtime.dispose();
    }

    /** LIST_MEMBERS 删除一步直达：提交回调异常后保留面板与权威数据并复用错误槽。 */
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
        harness.mountRoot(picker, 640, 420);
        harness.click(picker.__getChildren().get(0).__getChildren().get(0));
        ReactiveScheduler.get().flush();
        SceneNode panel = panelRoot(runtime);
        layoutPanel(runtime);
        SceneNode row = memberCell(panel, 0);
        harness.click(memberAction(row, 1));
        ReactiveScheduler.get().flush();
        layoutPanel(runtime);
        assertEquals("删除点击直达提交", 1, attempts.get());
        assertEquals(Collections.singletonList("raw:x"), raw.get());
        assertSame(item, items.get().get(0));
        assertEquals("删除被拒后面板保持展开", 1, runtime.getOverlayHost().size());
        assertEquals("Encode failed", errorText(panel).getText());
        assertTrue("删除槽保持删除文案", containsText(memberAction(row, 1), "Remove"));
        runtime.dispose();
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
        harness.mountRoot(picker, 640, 420);
        harness.click(picker.__getChildren().get(0).__getChildren().get(0));
        ReactiveScheduler.get().flush();
        SceneNode panel = panelRoot(runtime);
        layoutPanel(runtime);

        assertEquals("完整结果必须继续解析三个同 identity 合法成员", 3,
                countText(memberRows(panel), "Same name"));
        assertEquals("网格应挂载两个可添加单元", 2, gridCellCount(panel));
        List<String> labels = texts(gridViewport(panel));
        assertEquals("已选 registry identity 必须整体排除且不同 key 同名项只留一行",
                1, countStartingWith(labels, "Same"));
        assertEquals("malformed raw 不得误过滤同文本 candidate key",
                1, countStartingWith(labels, "Mal"));
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
        harness.mountRoot(picker, 640, 420);
        harness.click(picker.__getChildren().get(0).__getChildren().get(0));
        ReactiveScheduler.get().flush();
        SceneNode panel = panelRoot(runtime);
        layoutPanel(runtime);

        assertEquals("重复 key 只应精确搜索一次", 1, knownSearches.get());
        assertEquals("两个已知成员都应投影精确候选", 2,
                countText(memberRows(panel), "Known exact"));
        assertTrue("unknown selection 应保留原 key", texts(memberRows(panel)).contains("unknown"));
        assertEquals("空 query 不得展示全量候选", 0, gridCellCount(panel));
        assertFalse("模糊首项不得用于当前成员", texts(memberRows(panel)).contains("Wrong"));
        runtime.dispose();
    }

    // ==================== 变体搜索接线 ====================

    /** SINGLE_VALUE：点击带变体候选打开变体浮层，无条件渲染变体搜索输入且 query 过滤变体列表。 */
    @Test
    public void singleValueVariantOverlayRendersSearchAndFiltersVariants() {
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected.candidateKey()),
                Signal.<Object>create("before"), ignored -> { }, (query, max) -> variantResult());
        fixture.openPanel();
        fixture.selectCandidate();
        assertEquals("带变体候选应叠加打开变体浮层", 2, fixture.runtime.getOverlayHost().size());
        SceneNode card = variantCard(fixture.runtime);
        assertEquals("变体卡片应包含 header/search/segmented/list/footer",
                5, card.__getChildren().size());
        SceneNode list = card.__getChildren().get(3);
        assertTrue("打开时应展示全部变体", containsText(list, "Alpha"));
        assertTrue("打开时应展示全部变体", containsText(list, "Beta"));

        SceneNode search = card.__getChildren().get(1);
        fixture.runtime.requestFocus(search);
        fixture.runtime.flush();
        fixture.harness.typeText("alpha");
        fixture.runtime.flush();
        assertTrue("变体 query 过滤后应保留匹配项", containsText(list, "Alpha"));
        assertFalse("变体 query 过滤后应隐藏不匹配项", containsText(list, "Beta"));
        fixture.dispose();
    }

    /** LIST_MEMBERS：编辑带变体当前成员打开变体浮层，无条件渲染变体搜索输入且 query 过滤变体列表。 */
    @Test
    public void listMembersVariantOverlayRendersSearchAndFiltersVariants() {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        Signal<Object> raw = Signal.<Object>create(Collections.singletonList("raw:x"));
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(
                Collections.singletonList(new SceneSimpleList.ListItem("raw:x")));
        ValueEditorProvider.SearchFunction search = (query, max) -> query.isEmpty()
                ? SearchPickerData.SearchResult.empty()
                : new SearchPickerData.SearchResult(Collections.singletonList(new SearchPickerData.Candidate(
                        "raw", "Raw", Arrays.asList(
                                new SearchPickerData.Variant("v-alpha", "Alpha"),
                                new SearchPickerData.Variant("v-beta", "Beta")))));
        SceneNode picker = SearchPickerFieldSupport.createListMembersIfPresent(runtime,
                ValueSpec.list(ValueSpec.string()).withWidget(new SearchPickerSpec("test:picker", 8,
                        SearchPickerSpec.BindingMode.LIST_MEMBERS)), raw, items,
                registry(memberCodec(), search), ignored -> { });
        harness.mountRoot(picker, 640, 420);
        harness.click(picker.__getChildren().get(0).__getChildren().get(0));
        ReactiveScheduler.get().flush();
        SceneNode panel = panelRoot(runtime);
        layoutPanel(runtime);
        harness.click(memberAction(memberCell(panel, 0), 0));
        ReactiveScheduler.get().flush();
        assertEquals("编辑带变体成员应叠加打开变体浮层", 2, runtime.getOverlayHost().size());
        SceneNode card = variantCard(runtime);
        assertEquals("变体卡片应包含 header/search/segmented/list/footer",
                5, card.__getChildren().size());
        SceneNode list = card.__getChildren().get(3);
        assertTrue("打开时应展示全部变体", containsText(list, "Alpha"));
        assertTrue("打开时应展示全部变体", containsText(list, "Beta"));

        runtime.requestFocus(card.__getChildren().get(1));
        ReactiveScheduler.get().flush();
        harness.typeText("beta");
        ReactiveScheduler.get().flush();
        assertTrue("变体 query 过滤后应保留匹配项", containsText(list, "Beta"));
        assertFalse("变体 query 过滤后应隐藏不匹配项", containsText(list, "Alpha"));
        runtime.dispose();
    }

    // ==================== 夹具与断言助手 ====================

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

    private static void assertFailedEncodingKeepsPanelOpen(Codec codec) {
        AtomicInteger writes = new AtomicInteger();
        PickerFixture fixture = fixture(codec, Signal.<Object>create("current"), ignored -> writes.incrementAndGet());
        fixture.openPanel();
        fixture.type("draft");
        fixture.selectCandidate();
        assertEquals(0, writes.get());
        assertEquals("提交被拒后面板保持展开", 1, fixture.runtime.getOverlayHost().size());
        assertEquals("draft", fixture.searchText());
        assertEquals("Encode failed", fixture.errorText());
        fixture.dispose();
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

    private static PickerFixture fixture(Codec codec, Signal<Object> value, java.util.function.Consumer<Object> onChange) {
        return fixture(codec, value, onChange, (query, max) -> result(), null);
    }

    private static PickerFixture fixture(Codec codec, Signal<Object> value, java.util.function.Consumer<Object> onChange,
                                         ValueEditorProvider.SearchFunction search) {
        return fixture(codec, value, onChange, search, null);
    }

    private static PickerFixture fixture(Codec codec, Signal<Object> value, java.util.function.Consumer<Object> onChange,
                                         ValueEditorProvider.SearchFunction search,
                                         ValueEditorProvider extraProvider) {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        SceneNode picker = SearchPickerFieldSupport.createControlledIfPresent(runtime, spec(), value,
                registry(codec, search, extraProvider), onChange);
        harness.mountRoot(picker, 640, 420);
        ReactiveScheduler.get().flush();
        return new PickerFixture(harness, runtime, picker, picker.__getChildren().get(0));
    }

    private static ValueSpec spec() {
        return ValueSpec.string().withWidget(new SearchPickerSpec("test:picker", 8));
    }

    private static Registry registry(final Codec codec) {
        return registry(codec, (query, max) -> result(), null);
    }

    private static Registry registry(final Codec codec, final ValueEditorProvider.SearchFunction search) {
        return registry(codec, search, null);
    }

    private static Registry registry(final Codec codec, final ValueEditorProvider.SearchFunction search,
                                     final ValueEditorProvider extraProvider) {
        Registry registry = new Registry();
        final ValueEditorProvider base = extraProvider != null ? extraProvider : new ValueEditorProvider() {
            public String id() { return "test:picker"; }
            public Codec codec() { return codec; }
            public VisualAdapter visualAdapter() { return SearchPickerFieldSupportTest.visualAdapter(); }
            public SearchFunction searchFunction() { return search; }
            public SearchPickerPresentation presentation() { return failurePresentation(); }
            public CurrentValuePresenter currentValuePresenter() { return value ->
                    new CurrentValuePresenter.Presentation(String.valueOf(value), "summary-" + value, null); };
        };
        registry.register(base);
        registry.freeze();
        return registry;
    }

    private static ValueEditorProvider categorizedProvider() {
        return new CategorizedValueEditorProvider() {
            public String id() { return "test:picker"; }
            public Codec codec() { return statelessCodec((current, selected) -> selected.candidateKey()); }
            public VisualAdapter visualAdapter() { return SearchPickerFieldSupportTest.visualAdapter(); }
            public SearchFunction searchFunction() { return (query, max) -> new SearchPickerData.SearchResult(
                    Arrays.asList(candidate("a"), candidate("b"), candidate("c"))); }
            public SearchPickerPresentation presentation() { return failurePresentation(); }
            public CurrentValuePresenter currentValuePresenter() { return value ->
                    new CurrentValuePresenter.Presentation(String.valueOf(value), "summary-" + value, null); };
            public java.util.List<SearchPickerCategories.Category> categories() {
                return Arrays.asList(new SearchPickerCategories.Category("cat1", "Tabs"),
                        new SearchPickerCategories.Category("cat2", "Mods"));
            }
            public String categoryOf(String candidateKey) {
                return "c".equals(candidateKey) ? "cat2" : "cat1";
            }
        };
    }

    /** 双维度分组 provider：维度 0「创造栏」、维度 1「按 Mod」，维度切换由面板受控。 */
    private static ValueEditorProvider multiDimensionProvider() {
        return multiDimensionProvider(statelessCodec((current, selected) -> selected.candidateKey()));
    }

    private static CategorizedValueEditorProvider multiDimensionProvider(final Codec codec) {
        return new CategorizedValueEditorProvider() {
            public String id() { return "test:picker"; }
            public Codec codec() { return codec; }
            public VisualAdapter visualAdapter() { return SearchPickerFieldSupportTest.visualAdapter(); }
            public SearchFunction searchFunction() { return (query, max) -> new SearchPickerData.SearchResult(
                    Arrays.asList(candidate("a"), candidate("b"), candidate("c"))); }
            public SearchPickerPresentation presentation() { return failurePresentation(); }
            public CurrentValuePresenter currentValuePresenter() { return value ->
                    new CurrentValuePresenter.Presentation(String.valueOf(value), "summary-" + value, null); };
            public SearchPickerPanelPresentation panelPresentation() {
                return SearchPickerPanelPresentation.builder().panelTitle("选择物品")
                        .categoryDimensions(Arrays.asList("Tabs", "Mods")).build();
            }
            public int categoryDimensionCount() { return 2; }
            public java.util.List<SearchPickerCategories.Category> categories() { return categories(0); }
            public java.util.List<SearchPickerCategories.Category> categories(int dimension) {
                if (dimension == 0) return Arrays.asList(
                        new SearchPickerCategories.Category("cat1", "Blocks"),
                        new SearchPickerCategories.Category("cat2", "Misc"));
                return Arrays.asList(
                        new SearchPickerCategories.Category("mod1", "Mod A"),
                        new SearchPickerCategories.Category("mod2", "Mod B"));
            }
            public String categoryOf(int dimension, String candidateKey) {
                if (dimension == 0) return "c".equals(candidateKey) ? "cat2" : "cat1";
                return "c".equals(candidateKey) ? "mod2" : "mod1";
            }
        };
    }

    /** 断言顶栏无维度分段控件（标题/搜索/统计三段），即未传 dimension 接线。 */
    private static void assertNoDimensionSegmented(ValueEditorProvider provider) {
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected.candidateKey()),
                Signal.<Object>create("before"), ignored -> { }, (query, max) -> result(), provider);
        fixture.openPanel();
        SceneNode topBar = panelRoot(fixture.runtime).__getChildren().get(0);
        assertEquals("无维度切换时顶栏只含标题/搜索/统计", 3, topBar.__getChildren().size());
        fixture.dispose();
    }

    private static VisualAdapter visualAdapter() {
        return new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
    }

    private static SearchPickerPresentation failurePresentation() {
        return SearchPickerPresentation.builder()
                .decodeError("Decode failed").searchError("Search failed").encodeError("Encode failed").build();
    }

    private static SearchPickerData.Candidate candidate(String key) {
        return new SearchPickerData.Candidate(key, key + ":label",
                Collections.<SearchPickerData.Variant>emptyList());
    }

    private static SearchPickerData.SearchResult result() {
        return new SearchPickerData.SearchResult(Collections.singletonList(new SearchPickerData.Candidate(
                "picked", "Picked", Collections.<SearchPickerData.Variant>emptyList())));
    }

    /** 带两个变体的单候选结果，用于驱动变体浮层搜索。 */
    private static SearchPickerData.SearchResult variantResult() {
        return new SearchPickerData.SearchResult(Collections.singletonList(new SearchPickerData.Candidate(
                "picked", "Picked", Arrays.asList(
                        new SearchPickerData.Variant("v-alpha", "Alpha"),
                        new SearchPickerData.Variant("v-beta", "Beta")))));
    }

    /** 变体浮层卡片：overlay 栈第 2 项（变体 portal）为 scrim，scrim.children[0] = card。 */
    private static SceneNode variantCard(SceneRuntime runtime) {
        SceneNode scrim = runtime.getOverlayHost().bottomFirst().get(1).getRoot();
        return scrim.__getChildren().get(0);
    }

    private static SearchPickerData.Selection selection(String key) {
        return new SearchPickerData.Selection(key, SearchPickerData.SelectionMode.ALL,
                Collections.<String>emptyList());
    }

    /** overlay root = 透明 scrim；面板卡片 = overlay root.children[0]。 */
    private static SceneNode panelOverlayRoot(SceneRuntime runtime) {
        return runtime.getOverlayHost().bottomFirst().get(0).getRoot();
    }

    private static SceneNode panelRoot(SceneRuntime runtime) {
        return panelOverlayRoot(runtime).__getChildren().get(0);
    }

    /** 布局面板并桥接 layout epoch，虚拟网格窗口在 flush 后按最新视口挂载。 */
    private static void layoutPanel(SceneRuntime runtime) {
        SceneNode panel = panelOverlayRoot(runtime);
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        engine.layout(panel, new Constraints(PANEL_W, PANEL_H));
        runtime.__bridgeLayoutEpoch(engine.layoutEpoch());
        runtime.flush();
    }

    /** 分类导航 = 卡片 selectionArea.children[0]；本方法返回其内部滚动视口
     *（nav 外壳 → stackHost → viewport）。 */
    private static SceneNode categoryNav(SceneNode panel) {
        return panel.__getChildren().get(1).__getChildren().get(0)
                .__getChildren().get(0).__getChildren().get(0);
    }

    /** 结果列表 viewport：中栏 children = [error, stackHost, infoBar]，stackHost.children[0] = viewport。 */
    private static SceneNode gridViewport(SceneNode panel) {
        return panel.__getChildren().get(1).__getChildren().get(1)
                .__getChildren().get(1).__getChildren().get(0);
    }

    private static SceneNode searchInput(SceneNode panel) {
        return panel.__getChildren().get(0).__getChildren().get(1);
    }

    private static SceneNode errorText(SceneNode panel) {
        return panel.__getChildren().get(1).__getChildren().get(1).__getChildren().get(0);
    }

    /** 成员网格内容：membersPanel[1]=gridRoot → [0]=viewport → [0]=content。 */
    private static SceneNode memberRows(SceneNode panel) {
        return panel.__getChildren().get(2).__getChildren().get(1)
                .__getChildren().get(0).__getChildren().get(0);
    }

    /** 第 index 个成员卡片（跨行平铺）。 */
    private static SceneNode memberCell(SceneNode panel, int index) {
        for (SceneNode rowNode : memberRows(panel).__getChildren()) {
            if (index < rowNode.__getChildren().size()) return rowNode.__getChildren().get(index);
            index -= rowNode.__getChildren().size();
        }
        throw new IllegalStateException("member cell index out of mounted grid: " + index);
    }

    /** 已挂载成员卡片总数。 */
    private static int memberCellCount(SceneNode panel) {
        int count = 0;
        for (SceneNode rowNode : memberRows(panel).__getChildren()) count += rowNode.__getChildren().size();
        return count;
    }

    /** 成员行 = [icon, info, actions]；actions = [edit, remove]。 */
    private static SceneNode memberAction(SceneNode row, int index) {
        return row.__getChildren().get(2).__getChildren().get(index);
    }

    /** 结果列表单元：viewport children[0] = rowsContainer。 */
    private static SceneNode gridCell(SceneNode panel, int index) {
        SceneNode rowsContainer = gridViewport(panel).__getChildren().get(0);
        for (SceneNode row : rowsContainer.__getChildren()) {
            if (index < row.__getChildren().size()) return row.__getChildren().get(index);
            index -= row.__getChildren().size();
        }
        throw new IllegalStateException("cell index out of mounted window: " + index);
    }

    private static int gridCellCount(SceneNode panel) {
        SceneNode rowsContainer = gridViewport(panel).__getChildren().get(0);
        int count = 0;
        for (SceneNode row : rowsContainer.__getChildren()) count += row.__getChildren().size();
        return count;
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

    private static boolean containsText(SceneNode node, String expected) {
        if (expected.equals(node.getText())) return true;
        for (SceneNode child : node.__getChildren()) {
            if (containsText(child, expected)) return true;
        }
        return false;
    }

    private static int countText(SceneNode node, String expected) {
        int count = 0;
        for (String value : texts(node)) if (expected.equals(value)) count++;
        return count;
    }

    private static int countStartingWith(List<String> values, String prefix) {
        int count = 0;
        for (String value : values) if (value.startsWith(prefix)) count++;
        return count;
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

    /** SINGLE_VALUE 面板夹具：行触发器 + 受控全屏面板交互助手。 */
    private static final class PickerFixture {
        final SceneInteractionHarness harness;
        final SceneRuntime runtime;
        final SceneNode picker;
        final SceneNode trigger;

        private PickerFixture(SceneInteractionHarness harness, SceneRuntime runtime, SceneNode picker,
                              SceneNode trigger) {
            this.harness = harness;
            this.runtime = runtime;
            this.picker = picker;
            this.trigger = trigger;
        }

        private void openPanel() {
            harness.click(trigger);
            runtime.flush();
            layoutPanel();
        }

        private void layoutPanel() {
            SearchPickerFieldSupportTest.layoutPanel(runtime);
        }

        private void type(String text) {
            SceneNode input = searchInput(panelRoot(runtime));
            runtime.requestFocus(input);
            runtime.flush();
            harness.typeText(text);
            runtime.flush();
        }

        private void selectCandidate() {
            layoutPanel();
            harness.click(gridCell(panelRoot(runtime), 0));
            runtime.flush();
        }

        private void pressEscape() {
            harness.pressKey(SceneKey.ESCAPE);
            runtime.flush();
        }

        private String searchText() {
            return textOf(searchInput(panelRoot(runtime)));
        }

        private String errorText() {
            return SearchPickerFieldSupportTest.errorText(panelRoot(runtime)).getText();
        }

        private void dispose() { runtime.dispose(); }
    }
}
