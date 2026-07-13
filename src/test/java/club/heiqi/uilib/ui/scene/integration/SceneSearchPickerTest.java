package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneSearchPicker;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.overlay.AnchoredPortalLayout;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/** SceneSearchPicker 主树、双 portal 与输入行为回归。 */
public class SceneSearchPickerTest {
    private SceneInteractionHarness harness;
    private SceneRuntime runtime;
    private SceneLayoutEngine layout;
    private SceneNode sceneRoot;
    private SceneNode input;
    private Signal<String> query;
    private Signal<SearchPickerData.SearchResult> results;
    private Signal<Boolean> enabled;
    private String lastQuery;
    private SearchPickerData.Selection selection;
    private AtomicInteger selectCount;
    private final SceneImageSource image = new SceneImageSource() { };

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layout = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        query = Signal.create("");
        results = Signal.create(result(candidate("stone", "Stone"), candidate("dirt", "Dirt")));
        enabled = Signal.create(Boolean.TRUE);
        selectCount = new AtomicInteger(0);
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
            public SceneImageSource candidateImage(SearchPickerData.Candidate value) {
                return "stone".equals(value.key()) ? image : null;
            }
            public SceneImageSource variantImage(SearchPickerData.Variant value) {
                return "smooth".equals(value.key()) ? image : null;
            }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, new SceneSearchPicker.Props(
                query, results, enabled, value -> lastQuery = value, value -> {
                    selection = value;
                    selectCount.incrementAndGet();
                }, adapter)));
        runtime.flush();
        input = sceneRoot.__getChildren().get(0).__getChildren().get(1);
        harness.mountRoot(sceneRoot, 320, 240);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    private void doLayout() {
        layout.layout(sceneRoot, new Constraints(320, 240));
        runtime.getOverlayHost().bottomFirst().forEach(entry ->
                layout.layout(entry.getRoot(), new Constraints(320, 240)));
    }

    private SceneNode portal() { return runtime.getOverlayHost().bottomFirst().get(0).getRoot(); }
    private SceneNode items() { return portal().__getChildren().get(0); }

    private void open() { doLayout(); harness.click(input); doLayout(); }

    private void key(SceneKey key, SceneKeyAction action) {
        InputFrameBuilder builder = new InputFrameBuilder(0, 0);
        builder.push(RawInputEvent.ofKey(key, action, false, false, false, false,
                RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1L));
        runtime.route(sceneRoot, builder.drainFrame(), 0, 0);
        runtime.flush();
        doLayout();
    }

    /** 主树只含输入，候选只进入 portal；key 更新复用稳定节点。 */
    @Test
    public void mainTreePortalAndKeyReuse() {
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());
        open();
        Assert.assertEquals(1, sceneRoot.__getChildren().size());
        Assert.assertEquals(2, items().__getChildren().size());
        Assert.assertSame("普通 picker 应继续走默认 anchored 策略", AnchoredPortalLayout.DEFAULT,
                runtime.getOverlayHost().bottomFirst().get(0).getAnchoredLayout());
        Assert.assertEquals("普通 picker portal 根应继续 SHRINK", WidthSizing.SHRINK,
                portal().getWidthSizing());
        SceneNode stone = items().__getChildren().get(0);
        results.set(result(candidate("stone", "Stone"), candidate("sand", "Sand")));
        runtime.flush();
        Assert.assertSame(stone, items().__getChildren().get(0));
    }

    /** 图标固定 18x18，有图与占位均保留 label。 */
    @Test
    public void imagePlaceholderAndLabel() {
        open();
        SceneNode icon = items().__getChildren().get(0).__getChildren().get(0);
        SceneNode placeholder = items().__getChildren().get(1).__getChildren().get(0);
        Assert.assertSame(image, icon.getImageSource());
        Assert.assertEquals(18, icon.getPreferredWidth());
        Assert.assertEquals(18, icon.getPreferredHeight());
        Assert.assertNotEquals(0, placeholder.getBackgroundColor());
        Assert.assertEquals("Dirt", items().__getChildren().get(1).__getChildren().get(1).getText());
    }

    /** 无变体直接提交；有变体打开第二 portal，并返回 candidate+variant key。 */
    @Test
    public void directAndVariantSelection() {
        open();
        harness.pressReleaseAcrossFrames(items().__getChildren().get(1), this::doLayout);
        Assert.assertEquals("dirt", selection.candidateKey());
        Assert.assertNull(selection.variantKey());
        results.set(result(new SearchPickerData.Candidate("stone", "Stone", Arrays.asList(
                new SearchPickerData.Variant("smooth", "Smooth")))));
        runtime.flush();
        open();
        harness.click(items().__getChildren().get(0));
        doLayout();
        Assert.assertEquals(1, runtime.getOverlayHost().size());
        SceneNode variantPortal = portal();
        SceneNode variantItems = variantPortal.__getChildren().get(1);
        SceneNode variantRow = variantItems.__getChildren().get(0);
        Assert.assertSame(image, variantRow.__getChildren().get(0).getImageSource());
        Assert.assertFalse(variantRow.__getChildren().get(0).isHitTestable());
        harness.click(variantRow.__getChildren().get(1));
        SceneNode actions = variantPortal.__getChildren().get(2);
        harness.pressReleaseAcrossFrames(actions.__getChildren().get(1), this::doLayout);
        Assert.assertEquals("stone", selection.candidateKey());
        Assert.assertEquals(SearchPickerData.SelectionMode.ALL, selection.mode());
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());
    }

    /** 变体整行是唯一交互根，装饰子节点点击、行尾点击和 hover 均落到该行且不双提交。 */
    @Test
    public void variantRowOwnsHoverAndEveryClickSurface() {
        results.set(result(new SearchPickerData.Candidate("stone", "Stone", Arrays.asList(
                new SearchPickerData.Variant("a", "A"), new SearchPickerData.Variant("b", "B"),
                new SearchPickerData.Variant("c", "C")))));
        runtime.flush(); open(); harness.click(items().__getChildren().get(0)); doLayout();
        harness.click(portal().__getChildren().get(0).__getChildren().get(1));
        SceneNode variants = portal().__getChildren().get(1);
        SceneNode first = variants.__getChildren().get(0);
        Assert.assertFalse(first.__getChildren().get(0).isHitTestable());
        Assert.assertFalse(first.__getChildren().get(1).isHitTestable());
        Assert.assertFalse(first.__getChildren().get(2).isHitTestable());
        int idle = first.getBackgroundColor();
        harness.moveTo(first.__getChildren().get(0));
        Assert.assertNotEquals("图标区域 hover 应由整行呈现", idle, first.getBackgroundColor());
        harness.click(first.__getChildren().get(0));
        harness.click(variants.__getChildren().get(1).__getChildren().get(1));
        harness.click(variants.__getChildren().get(2));
        harness.pressReleaseAcrossFrames(portal().__getChildren().get(2).__getChildren().get(1), this::doLayout);
        Assert.assertEquals(Arrays.asList("a", "b", "c"), selection.variantKeys());
        Assert.assertEquals("每次点击只能经整行回调一次，确认也只能提交一次", 1, selectCount.get());
    }

    /** 候选窗口固定八行，滚轮可推进到末尾，键盘跨窗且 hover 不改键盘高亮。 */
    @Test
    public void candidateWindowScrollKeyboardHoverAndFinalSelection() {
        ArrayList<SearchPickerData.Candidate> many = new ArrayList<SearchPickerData.Candidate>();
        for (int i = 0; i < 12; i++) many.add(candidate("k" + i, "V" + i));
        results.set(new SearchPickerData.SearchResult(many));
        runtime.flush(); open();
        Assert.assertEquals(8, items().__getChildren().size());
        for (int i = 0; i < 20; i++) { harness.scroll(items(), -1); doLayout(); }
        Assert.assertEquals("V4", items().__getChildren().get(0).__getChildren().get(1).getText());
        Assert.assertEquals("V11", items().__getChildren().get(7).__getChildren().get(1).getText());

        runtime.getOverlayHost().bottomFirst().get(0).requestDismiss(); runtime.flush();
        runtime.requestFocus(input);
        harness.typeText("x"); runtime.flush(); doLayout();
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        for (int i = 0; i < 7; i++) key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        Assert.assertEquals("V0", items().__getChildren().get(0).__getChildren().get(1).getText());
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        Assert.assertEquals("第九项高亮时窗口应自动跨过第八行", "V1",
                items().__getChildren().get(0).__getChildren().get(1).getText());
        harness.moveTo(items().__getChildren().get(7));
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        Assert.assertEquals("hover 不得覆盖键盘高亮的最终选择", "k8", selection.candidateKey());

        selection = null; runtime.requestFocus(input); harness.typeText("y"); runtime.flush(); doLayout();
        key(SceneKey.ARROW_UP, SceneKeyAction.PRESSED);
        Assert.assertEquals("无高亮按 Up 应定位末项并滚到末窗", "V11",
                items().__getChildren().get(7).__getChildren().get(1).getText());
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        Assert.assertEquals("k11", selection.candidateKey());
    }

    /** 变体面板取消不提交，Confirm 只提交一次。 */
    @Test public void variantCancelAndConfirmWriteCounts() {
        results.set(result(new SearchPickerData.Candidate("stone", "Stone", Arrays.asList(
                new SearchPickerData.Variant("a", "A"), new SearchPickerData.Variant("b", "B")))));
        runtime.flush(); open(); harness.click(items().__getChildren().get(0)); doLayout();
        harness.pressReleaseAcrossFrames(portal().__getChildren().get(2).__getChildren().get(0), this::doLayout);
        Assert.assertNull(selection);
        Assert.assertEquals(0, selectCount.get());
        open(); harness.click(items().__getChildren().get(0)); doLayout();
        harness.pressReleaseAcrossFrames(portal().__getChildren().get(2).__getChildren().get(1), this::doLayout);
        Assert.assertEquals(SearchPickerData.SelectionMode.ALL, selection.mode());
        Assert.assertEquals(1, selectCount.get());
        key(SceneKey.ENTER, SceneKeyAction.RELEASED);
        key(SceneKey.ENTER, SceneKeyAction.RELEASED);
        harness.releaseAt(0, 0);
        Assert.assertEquals(1, selectCount.get());
    }

    /** SELECTED 支持 checkbox 多选并按候选顺序提交 keys。 */
    @Test
    public void singleAndMultipleInputPreserveCandidateOrder() {
        results.set(result(new SearchPickerData.Candidate("stone", "Stone", Arrays.asList(
                new SearchPickerData.Variant("b", "B"), new SearchPickerData.Variant("a", "A"),
                new SearchPickerData.Variant("c", "C")))));
        runtime.flush(); open(); harness.click(items().__getChildren().get(0)); doLayout();
        SceneNode variantPortal = portal();
        harness.click(variantPortal.__getChildren().get(0).__getChildren().get(1));
        runtime.requestFocus(input);
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        key(SceneKey.SPACE, SceneKeyAction.PRESSED);
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        Assert.assertEquals(SearchPickerData.SelectionMode.SELECTED, selection.mode());
        Assert.assertEquals(Collections.singletonList("a"), selection.variantKeys());

        open(); harness.click(items().__getChildren().get(0)); doLayout();
        variantPortal = portal();
        harness.click(variantPortal.__getChildren().get(0).__getChildren().get(1));
        SceneNode variantItems = variantPortal.__getChildren().get(1);
        harness.click(variantItems.__getChildren().get(0));
        harness.click(variantItems.__getChildren().get(2));
        SceneNode confirm = variantPortal.__getChildren().get(2).__getChildren().get(1);
        harness.pressReleaseAcrossFrames(confirm, this::doLayout);
        Assert.assertEquals(SearchPickerData.SelectionMode.SELECTED, selection.mode());
        Assert.assertEquals(Arrays.asList("b", "c"), selection.variantKeys());
    }

    /** 键盘仅处理 PRESSED；repeat 不重复移动，Enter 提交，Escape 关闭。 */
    @Test
    public void keyboardRepeatAndEscape() {
        doLayout();
        runtime.requestFocus(input);
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        key(SceneKey.ARROW_DOWN, SceneKeyAction.REPEATED);
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        Assert.assertEquals("stone", selection.candidateKey());
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        Assert.assertFalse(runtime.getOverlayHost().isEmpty());
        key(SceneKey.ESCAPE, SceneKeyAction.PRESSED);
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());
    }

    /** 完整结果不截断；禁用时不打开。 */
    @Test
    public void dismissTruncatedAndDisabled() {
        ArrayList<SearchPickerData.Candidate> many = new ArrayList<SearchPickerData.Candidate>();
        for (int i = 0; i < 65; i++) many.add(candidate("k" + i, "V" + i));
        results.set(new SearchPickerData.SearchResult(many).limitedTo(2));
        runtime.flush();
        open();
        Assert.assertEquals(8, items().__getChildren().size());
        SceneNode footer = portal().__getChildren().get(1);
        Assert.assertTrue(texts(footer).contains("65 results"));
        Assert.assertFalse(texts(footer).contains("Results truncated"));
        runtime.getOverlayHost().bottomFirst().get(0).requestDismiss();
        runtime.flush();
        enabled.set(Boolean.FALSE);
        runtime.flush();
        harness.click(input);
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());
        Assert.assertNull(lastQuery);
    }

    /** 旧构造器保留默认英文标题、占位、空态、摘要与截断文案。 */
    @Test
    public void legacyConstructorUsesDefaultEnglishPresentation() {
        SceneNode picker = sceneRoot.__getChildren().get(0);
        Assert.assertEquals("Select a value", picker.__getChildren().get(0).getText());
        Assert.assertEquals("Search", firstText(input));
        results.set(SearchPickerData.SearchResult.empty());
        runtime.flush(); open();
        Assert.assertTrue(texts(portal()).contains("No results"));
        Assert.assertFalse(texts(portal()).contains("0 results"));
    }

    /** builder 渲染全部领域文案及错误信号。 */
    @Test
    public void builderRendersCompletePresentationAndError() {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        runtime = harness.getRuntime(); sceneRoot = new SceneNode();
        query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        results = Signal.create(SearchPickerData.SearchResult.limitedTo(Arrays.asList(
                new SearchPickerData.Candidate("x", "X", Collections.singletonList(
                        new SearchPickerData.Variant("v", "V"))), candidate("y", "Y")), 1));
        Signal<String> error = Signal.create("E");
        SearchPickerPresentation p = SearchPickerPresentation.builder().title("T").placeholder("P")
                .all("A").selected("S").unavailableVariant("U:{key}").cancel("C").confirm("OK").empty("Z")
                .truncated("TR").searchResultsTitle("R").edit("ED").remove("RM")
                .cancelRemove("RC").confirmRemove("RD").resultSummaryFormatter(count -> "N=" + count)
                .decodeError("D").searchError("Q").encodeError("W").build();
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, value -> { }, adapter).presentation(p).error(error).build()));
        runtime.flush(); input = sceneRoot.__getChildren().get(0).__getChildren().get(1);
        harness.mountRoot(sceneRoot, 320, 240);
        Assert.assertTrue(texts(sceneRoot).containsAll(Arrays.asList("T", "P", "E")));
        open();
        Assert.assertTrue(texts(portal()).containsAll(Arrays.asList("X", "Y", "N=2")));
        harness.click(items().__getChildren().get(0)); doLayout();
        Assert.assertTrue(texts(portal()).containsAll(Arrays.asList("A", "S", "C", "OK", "V")));
    }

    /** LIST_MEMBERS portal 顶部最多三行、候选至少五行，成员 keyed 且装饰节点不截获点击。 */
    @Test
    public void listMembersPortalUsesStableIdsAndBoundedSections() {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16)); runtime = harness.getRuntime();
        sceneRoot = new SceneNode(); query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        SearchPickerData.Candidate known = new SearchPickerData.Candidate("known", "Known",
                Collections.singletonList(new SearchPickerData.Variant("v", "V")));
        ArrayList<SearchPickerData.Candidate> candidates = new ArrayList<SearchPickerData.Candidate>();
        candidates.add(known);
        for (int i = 0; i < 6; i++) candidates.add(candidate("k" + i, "K" + i));
        results = Signal.create(new SearchPickerData.SearchResult(candidates));
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(Arrays.asList(
                new SearchPickerData.CurrentMember(10L, new SearchPickerData.Selection("known",
                        SearchPickerData.SelectionMode.SELECTED, Collections.singletonList("v")), known, true),
                new SearchPickerData.CurrentMember(11L, new SearchPickerData.Selection("unknown",
                        SearchPickerData.SelectionMode.ALL, Collections.<String>emptyList()), null, false),
                new SearchPickerData.CurrentMember(12L, null, null, false),
                new SearchPickerData.CurrentMember(13L, null, null, false)));
        AtomicLong edited = new AtomicLong(-1L);
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, value -> { }, adapter).currentMembers(members, edited::set).build()));
        runtime.flush(); input = sceneRoot.__getChildren().get(0).__getChildren().get(1)
                .__getChildren().get(1);
        harness.mountRoot(sceneRoot, 320, 420); open();
        SceneOverlayHost.Entry entry = runtime.getOverlayHost().bottomFirst().get(0);
        Assert.assertEquals(480, entry.getAnchoredLayout().getPreferredWidth());
        Assert.assertEquals(360, entry.getAnchoredLayout().getMinWidth());
        Assert.assertEquals(8, entry.getAnchoredLayout().getSafeInset());
        Assert.assertEquals("LIST_MEMBERS portal 根必须消费确定宽度", WidthSizing.FILL,
                portal().getWidthSizing());
        Assert.assertTrue("LIST_MEMBERS portal 根必须消费总高度 cap", portal().isScrollable());
        Assert.assertTrue("LIST_MEMBERS portal 根必须裁剪溢出内容", portal().isClipChildren());
        Assert.assertSame("管理 portal 必须锚定 Manage", input, entry.getAnchorProvider().getNode());
        Assert.assertSame("portal builder 不得在建树期抢走 Manage 焦点", input, runtime.getFocusedNode());
        Assert.assertNotSame(portal().__getChildren().get(0), runtime.getFocusedNode());
        Assert.assertEquals("Current values (4)", portal().__getChildren().get(1).getText());
        SceneNode currentRows = portal().__getChildren().get(2).__getChildren().get(0);
        Assert.assertEquals(4, visibleRowCount(currentRows));
        Assert.assertEquals(3 * 34, portal().__getChildren().get(2).getPreferredHeight());
        Assert.assertEquals("Search results (7)", portal().__getChildren().get(3).getText());
        Assert.assertEquals(5, visibleRowCount(portal().__getChildren().get(4).__getChildren().get(0)));
        SceneNode malformed = currentRows.__getChildren().get(2);
        Assert.assertFalse(malformed.__getChildren().get(0).isHitTestable());
        Assert.assertFalse(malformed.__getChildren().get(1).isHitTestable());
        SceneNode malformedActions = malformed.__getChildren().get(2);
        Assert.assertFalse(malformedActions.__getChildren().get(0).__getChildren().get(0).isHitTestable());
        Assert.assertFalse(malformedActions.__getChildren().get(1).__getChildren().get(0).isHitTestable());
        harness.click(malformedActions.__getChildren().get(0).__getChildren().get(0));
        Assert.assertEquals(12L, edited.get());
        Assert.assertEquals(1, runtime.getOverlayHost().size());
        harness.click(currentRows.__getChildren().get(0).__getChildren().get(2)
                .__getChildren().get(0).__getChildren().get(0)); doLayout();
        Assert.assertEquals(10L, edited.get());
        Assert.assertEquals("LIST_MEMBERS variant portal 顶部仍应是搜索框", "Search",
                firstText(portal().__getChildren().get(0)));
        Assert.assertNotSame("variant portal builder 不得在建树期请求焦点",
                portal().__getChildren().get(0), runtime.getFocusedNode());
        Assert.assertTrue(texts(portal()).contains("V"));
    }

    /** LIST_MEMBERS 关闭态紧凑，管理 portal 两区按自然行数增长并分别在 3/5 行封顶。 */
    @Test
    public void listMembersSummaryPortalOrderDynamicHeightsAndDismissReset() {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16)); runtime = harness.getRuntime();
        sceneRoot = new SceneNode(); query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        results = Signal.create(resultCount(0));
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(currentMembers(0));
        AtomicInteger writes = new AtomicInteger();
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, ignored -> writes.incrementAndGet(), adapter)
                .currentMembers(members, ignored -> writes.incrementAndGet())
                .onRemoveCurrent(ignored -> { writes.incrementAndGet(); return true; }).build()));
        runtime.flush();
        SceneNode picker = sceneRoot.__getChildren().get(0);
        input = picker.__getChildren().get(1).__getChildren().get(1);
        harness.mountRoot(sceneRoot, 320, 420);
        Assert.assertTrue(texts(picker).containsAll(Arrays.asList("No items configured", "Manage")));
        Assert.assertFalse("关闭态不得常驻搜索输入", texts(picker).contains("Search"));

        open(); runtime.flush(); doLayout();
        Assert.assertSame("portal builder 不得覆盖 Manage 的事件期焦点", input, runtime.getFocusedNode());
        SceneAnchorResolver.ResolvedAnchor upward = SceneAnchorResolver.resolveAuto(
                new AnchorRect(0, 380, 96, 24), 320, 420, 360,
                runtime.getOverlayHost().bottomFirst().get(0).getAnchoredLayout());
        Assert.assertTrue("底部锚点应触发向上展开", upward.getY() < 380);
        Assert.assertEquals("向上展开不得反转搜索框与 section 的物理顺序", Arrays.asList(
                "Search", "Current values (0)", "No current members",
                "Search results (0)", "No matching results"), texts(portal()));
        Assert.assertEquals("Current values (0)", portal().__getChildren().get(1).getText());
        Assert.assertEquals("Search results (0)", portal().__getChildren().get(3).getText());
        Assert.assertTrue(texts(portal().__getChildren().get(2)).contains("No current members"));
        Assert.assertTrue(texts(portal().__getChildren().get(4)).contains("No matching results"));
        Assert.assertEquals(34, portal().__getChildren().get(2).getPreferredHeight());
        Assert.assertEquals(34, portal().__getChildren().get(4).getPreferredHeight());

        for (int count : new int[] {1, 2, 3, 4}) {
            members.set(currentMembers(count)); runtime.flush(); doLayout();
            Assert.assertEquals(Math.min(count, 3) * 34,
                    portal().__getChildren().get(2).getPreferredHeight());
        }
        for (int count : new int[] {1, 4, 5, 6}) {
            results.set(resultCount(count)); runtime.flush(); doLayout();
            Assert.assertEquals(Math.min(count, 5) * 34,
                    portal().__getChildren().get(4).getPreferredHeight());
            Assert.assertEquals(Math.min(count, 5), visibleRowCount(
                    portal().__getChildren().get(4).__getChildren().get(0)));
        }
        Assert.assertFalse("LIST_MEMBERS 不得保留重复结果 footer", texts(portal()).contains("6 results"));
        Assert.assertEquals(0, writes.get());

        runtime.requestFocus(portal().__getChildren().get(0)); harness.typeText("draft"); runtime.flush();
        Assert.assertEquals("draft", query.get());
        runtime.getOverlayHost().bottomFirst().get(0).requestDismiss(); runtime.flush();
        Assert.assertEquals("", query.get());
        Assert.assertEquals(0, writes.get());
        harness.click(input); runtime.flush(); doLayout();
        Assert.assertEquals("关闭后 Manage 必须可再次打开", 1, runtime.getOverlayHost().size());
    }

    /** LIST_MEMBERS 关闭态在窄宽下为摘要分配确定剩余宽，且不触发 ROW grow 诊断。 */
    @Test
    public void listMembersNarrowSummaryReservesManageWidthWithoutLayoutWarn() {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        runtime = harness.getRuntime(); sceneRoot = new SceneNode();
        query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        results = Signal.create(SearchPickerData.SearchResult.empty());
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, value -> { }, adapter)
                .currentMembers(Signal.create(Collections.<SearchPickerData.CurrentMember>emptyList()),
                        ignored -> { }).build()));
        runtime.flush();

        ConstraintResolverWarnTest.CollectingAppender appender =
                new ConstraintResolverWarnTest.CollectingAppender("SceneSearchPickerNarrowWarnTest");
        appender.start();
        org.apache.logging.log4j.core.LoggerContext context = (org.apache.logging.log4j.core.LoggerContext)
                org.apache.logging.log4j.LogManager.getContext(false);
        org.apache.logging.log4j.core.config.LoggerConfig loggerConfig = context.getConfiguration()
                .getLoggerConfig("QzUiLib/Layout");
        loggerConfig.addAppender(appender, org.apache.logging.log4j.Level.WARN, null);
        context.updateLoggers();
        try {
            harness.mountRoot(sceneRoot, 120, 120);
            SceneNode management = sceneRoot.__getChildren().get(0).__getChildren().get(1);
            SceneNode summary = management.__getChildren().get(0);
            SceneNode manage = management.__getChildren().get(1);
            LayoutBox managementBox = (LayoutBox) management.getCachedLayout();
            LayoutBox summaryBox = (LayoutBox) summary.getCachedLayout();
            LayoutBox manageBox = (LayoutBox) manage.getCachedLayout();

            Assert.assertEquals("Manage 应有稳定先验宽度", 96, manage.getPreferredWidth());
            Assert.assertEquals("Manage 应保留稳定布局宽度", 96, manageBox.getWidth());
            Assert.assertTrue("摘要右边界不得挤压 Manage",
                    summaryBox.getX() + summaryBox.getWidth() <= manageBox.getX());
            Assert.assertTrue("Manage 不得溢出关闭态管理行",
                    manageBox.getX() + manageBox.getWidth() <= managementBox.getWidth());
            Assert.assertEquals("关闭态布局不得触发 ConstraintResolver grow WARN", 0,
                    appender.warnCount());
        } finally {
            loggerConfig.removeAppender(appender.getName());
            appender.stop();
            context.updateLoggers();
        }
    }

    /** LIST_MEMBERS 删除先确认，编辑与删除动作互不串行，成功确认只提交一次且 portal 保持。 */
    @Test
    public void listMemberDeleteIsTwoStepAndActionsDoNotCrossFire() {
        AtomicInteger edits = new AtomicInteger();
        AtomicInteger removes = new AtomicInteger();
        mountListMembersPicker(edits, removes, true);
        open();
        SceneNode row = currentFirstRow();
        SceneNode normalActions = visibleActions(row);
        Assert.assertEquals(Arrays.asList("Edit", "Remove"), texts(normalActions));

        harness.click(normalActions.__getChildren().get(0).__getChildren().get(0));
        Assert.assertEquals(1, edits.get());
        Assert.assertEquals(0, removes.get());
        doLayout();
        row = currentFirstRow();
        clickMemberAction(row, 1);
        runtime.flush(); doLayout();
        Assert.assertEquals("第一次删除只能进入确认态", 0, removes.get());
        Assert.assertEquals(1, edits.get());
        SceneNode confirmActions = visibleActions(row);
        Assert.assertEquals(Arrays.asList("Cancel", "Confirm remove"), texts(confirmActions));
        clickMemberAction(row, 1);
        runtime.flush(); doLayout();
        Assert.assertEquals(1, removes.get());
        Assert.assertEquals("成功删除不应强制关闭 portal", 1, runtime.getOverlayHost().size());
        Assert.assertEquals(Arrays.asList("Edit", "Remove"), texts(visibleActions(row)));
    }

    /** 取消、Escape、外部 dismiss 与开始新增都清除删除确认态且不提交删除。 */
    @Test
    public void listMemberPendingDeleteClearsOnEveryCancellationBoundary() {
        AtomicInteger edits = new AtomicInteger();
        AtomicInteger removes = new AtomicInteger();
        mountListMembersPicker(edits, removes, false);
        open();
        SceneNode row = currentFirstRow();

        enterDeleteConfirmation(row); row = currentFirstRow();
        clickMemberAction(row, 0);
        runtime.flush(); doLayout();
        Assert.assertEquals(0, removes.get());
        Assert.assertEquals(Arrays.asList("Edit", "Remove"), texts(visibleActions(currentFirstRow())));

        enterDeleteConfirmation(currentFirstRow());
        key(SceneKey.ESCAPE, SceneKeyAction.PRESSED);
        Assert.assertEquals(0, removes.get());
        open();
        Assert.assertEquals(Arrays.asList("Edit", "Remove"), texts(visibleActions(currentFirstRow())));

        enterDeleteConfirmation(currentFirstRow());
        runtime.getOverlayHost().bottomFirst().get(0).requestDismiss(); runtime.flush();
        Assert.assertEquals(0, removes.get());
        open();
        Assert.assertEquals(Arrays.asList("Edit", "Remove"), texts(visibleActions(currentFirstRow())));

        enterDeleteConfirmation(currentFirstRow());
        runtime.requestFocus(portal().__getChildren().get(0)); runtime.flush();
        harness.typeText("x"); runtime.flush(); doLayout();
        Assert.assertEquals(0, removes.get());
        Assert.assertEquals(Arrays.asList("Edit", "Remove"), texts(visibleActions(currentFirstRow())));
    }

    /** 删除回调拒绝时保留 portal 与确认态，便于显示错误后重试或取消。 */
    @Test
    public void rejectedListMemberDeleteKeepsPortalAndConfirmation() {
        AtomicInteger removes = new AtomicInteger();
        mountListMembersPicker(new AtomicInteger(), removes, false);
        open();
        enterDeleteConfirmation(currentFirstRow());
        SceneNode confirmActions = visibleActions(currentFirstRow());
        clickMemberAction(currentFirstRow(), 1);
        runtime.flush(); doLayout();

        Assert.assertEquals(1, removes.get());
        Assert.assertEquals(1, runtime.getOverlayHost().size());
        Assert.assertEquals(Arrays.asList("Cancel", "Confirm remove"),
                texts(visibleActions(currentFirstRow())));
    }

    /** LIST_MEMBERS 从关闭态用上下方向键打开时先建立新增目标，Enter 提交后关闭。 */
    @Test
    public void listMembersArrowOpenBeginsAddBeforeEnterCommit() {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16)); runtime = harness.getRuntime();
        sceneRoot = new SceneNode(); query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        results = Signal.create(result(candidate("stone", "Stone"), candidate("dirt", "Dirt")));
        AtomicInteger beginAddCount = new AtomicInteger(0);
        AtomicInteger commitCount = new AtomicInteger(0);
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, value -> { selection = value; commitCount.incrementAndGet(); }, adapter)
                .currentMembers(Signal.create(Collections.<SearchPickerData.CurrentMember>emptyList()), ignored -> { })
                .onBeginAdd(beginAddCount::incrementAndGet).build()));
        runtime.flush(); input = sceneRoot.__getChildren().get(0).__getChildren().get(1)
                .__getChildren().get(1);
        harness.mountRoot(sceneRoot, 320, 240); doLayout(); runtime.requestFocus(input);

        key(SceneKey.ARROW_UP, SceneKeyAction.PRESSED);
        Assert.assertEquals(1, beginAddCount.get());
        Assert.assertFalse(runtime.getOverlayHost().isEmpty());
        runtime.requestFocus(portal().__getChildren().get(0));
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        Assert.assertEquals("dirt", selection.candidateKey());
        Assert.assertEquals(1, commitCount.get());
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());

        runtime.requestFocus(input);
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        Assert.assertEquals(2, beginAddCount.get());
        Assert.assertFalse(runtime.getOverlayHost().isEmpty());
        runtime.requestFocus(portal().__getChildren().get(0));
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        Assert.assertEquals(2, commitCount.get());
        Assert.assertEquals(2, beginAddCount.get());
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());
    }

    /** SELECTED 空草稿禁确认，ALL 往返保留草稿且不会自动首选。 */
    @Test public void selectedEmptyDisablesConfirmAndAllRoundTripKeepsDraft() {
        results.set(result(new SearchPickerData.Candidate("stone", "Stone", Arrays.asList(
                new SearchPickerData.Variant("a", "A"), new SearchPickerData.Variant("b", "B")))));
        runtime.flush(); open(); harness.click(items().__getChildren().get(0)); doLayout();
        SceneNode modes = portal().__getChildren().get(0);
        harness.click(modes.__getChildren().get(1));
        SceneNode confirm = portal().__getChildren().get(2).__getChildren().get(1);
        harness.click(confirm);
        Assert.assertNull(selection);
        Assert.assertFalse(runtime.getOverlayHost().isEmpty());
        harness.click(portal().__getChildren().get(1).__getChildren().get(0));
        harness.click(modes.__getChildren().get(0));
        harness.click(modes.__getChildren().get(1));
        harness.pressReleaseAcrossFrames(confirm, this::doLayout);
        Assert.assertEquals(Collections.singletonList("a"), selection.variantKeys());
    }

    /** 当前未枚举 key 显示为通用行，可无损确认，也可主动移除。 */
    @Test public void unavailableCurrentKeyIsVisiblePreservedAndRemovable() {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16)); runtime = harness.getRuntime();
        sceneRoot = new SceneNode(); query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        results = Signal.create(result(new SearchPickerData.Candidate("stone", "Stone", Collections.singletonList(
                new SearchPickerData.Variant("known", "Known")))));
        Signal<SearchPickerData.Selection> current = Signal.create(new SearchPickerData.Selection("stone",
                SearchPickerData.SelectionMode.SELECTED, Collections.singletonList("legacy")));
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, value -> { selection = value; selectCount.incrementAndGet(); }, adapter)
                .currentSelection(current).build()));
        runtime.flush(); input = sceneRoot.__getChildren().get(0).__getChildren().get(1);
        harness.mountRoot(sceneRoot, 320, 240); open(); harness.click(items().__getChildren().get(0)); doLayout();
        SceneNode variantItems = portal().__getChildren().get(1);
        Assert.assertEquals(2, variantItems.__getChildren().size());
        Assert.assertTrue(texts(variantItems).contains("Currently unavailable (legacy)"));
        SceneNode confirm = portal().__getChildren().get(2).__getChildren().get(1);
        harness.pressReleaseAcrossFrames(confirm, this::doLayout);
        Assert.assertEquals(Collections.singletonList("legacy"), selection.variantKeys());

        selection = null; open(); harness.click(items().__getChildren().get(0)); doLayout();
        harness.click(portal().__getChildren().get(1).__getChildren().get(1));
        harness.click(portal().__getChildren().get(2).__getChildren().get(1));
        Assert.assertNull(selection);
        Assert.assertFalse(runtime.getOverlayHost().isEmpty());
    }

    private void mountListMembersPicker(AtomicInteger edits, AtomicInteger removes, boolean removeAccepted) {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        runtime = harness.getRuntime();
        sceneRoot = new SceneNode(); query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        SearchPickerData.Candidate known = candidate("known", "Known");
        results = Signal.create(result(known));
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(Collections.singletonList(
                new SearchPickerData.CurrentMember(42L, new SearchPickerData.Selection("known",
                        SearchPickerData.SelectionMode.ALL, Collections.<String>emptyList()), known, true)));
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, value -> { }, adapter).currentMembers(members, ignored -> edits.incrementAndGet())
                .onRemoveCurrent(ignored -> { removes.incrementAndGet(); return removeAccepted; }).build()));
        runtime.flush();
        input = sceneRoot.__getChildren().get(0).__getChildren().get(1)
                .__getChildren().get(1);
        harness.mountRoot(sceneRoot, 320, 300);
    }

    private SceneNode currentFirstRow() {
        return portal().__getChildren().get(2).__getChildren().get(0).__getChildren().get(0);
    }

    private void enterDeleteConfirmation(SceneNode row) {
        clickMemberAction(row, 1);
        runtime.flush();
        doLayout();
    }

    private void clickMemberAction(SceneNode row, int index) {
        SceneNode host = visibleActions(row);
        harness.click(host.__getChildren().get(index));
    }

    private static SceneNode visibleActions(SceneNode row) {
        for (int index = 2; index < row.__getChildren().size(); index++) {
            SceneNode host = row.__getChildren().get(index);
            if (!texts(host).isEmpty()) return host;
        }
        throw new AssertionError("current member row has no visible actions");
    }

    private static List<String> texts(SceneNode node) {
        List<String> values = new ArrayList<String>();
        if (node.getText() != null && !node.getText().isEmpty()) values.add(node.getText());
        for (SceneNode child : node.__getChildren()) values.addAll(texts(child));
        return values;
    }

    private static int visibleRowCount(SceneNode node) {
        int count = 0;
        for (SceneNode child : node.__getChildren()) if (child.getPreferredHeight() == 34) count++;
        return count;
    }

    private static String firstText(SceneNode node) {
        List<String> values = texts(node);
        return values.isEmpty() ? "" : values.get(0);
    }
    private static SearchPickerData.SearchResult result(SearchPickerData.Candidate... values) {
        return new SearchPickerData.SearchResult(Arrays.asList(values));
    }

    private static SearchPickerData.SearchResult resultCount(int count) {
        ArrayList<SearchPickerData.Candidate> values = new ArrayList<SearchPickerData.Candidate>();
        for (int index = 0; index < count; index++) values.add(candidate("r" + index, "R" + index));
        return new SearchPickerData.SearchResult(values);
    }

    private static List<SearchPickerData.CurrentMember> currentMembers(int count) {
        ArrayList<SearchPickerData.CurrentMember> values = new ArrayList<SearchPickerData.CurrentMember>();
        for (int index = 0; index < count; index++) {
            SearchPickerData.Candidate candidate = candidate("m" + index, "M" + index);
            values.add(new SearchPickerData.CurrentMember(index + 1L,
                    new SearchPickerData.Selection(candidate.key(), SearchPickerData.SelectionMode.ALL,
                            Collections.<String>emptyList()), candidate, true));
        }
        return values;
    }

    private static SearchPickerData.Candidate candidate(String key, String label) {
        return new SearchPickerData.Candidate(key, label, Collections.<SearchPickerData.Variant>emptyList());
    }
}
