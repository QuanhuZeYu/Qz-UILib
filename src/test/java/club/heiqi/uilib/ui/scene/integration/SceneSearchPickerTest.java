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
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.overlay.AnchoredPortalLayout;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
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
        key(key, action, false);
    }

    private void key(SceneKey key, SceneKeyAction action, boolean shiftDown) {
        InputFrameBuilder builder = new InputFrameBuilder(0, 0);
        builder.push(RawInputEvent.ofKey(key, action, false, shiftDown, false, false,
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

    /** LIST_MEMBERS portal 当前成员最多六行、候选最多十二行，且保持 keyed 与命中契约。 */
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
        SceneNode preexistingFocus = new SceneNode();
        runtime.focusable(preexistingFocus);
        runtime.requestFocus(preexistingFocus);
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, value -> { }, adapter).currentMembers(members, edited::set).build()));
        Assert.assertSame("组件 builder 阶段不得改变既有焦点", preexistingFocus, runtime.getFocusedNode());
        runtime.flush(); input = sceneRoot.__getChildren().get(0).__getChildren().get(1)
                .__getChildren().get(0);
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
        Assert.assertSame("portal 注册后的焦点 effect 应聚焦顶部搜索框",
                portal().__getChildren().get(0), runtime.getFocusedNode());
        SceneNode portalInput = portal().__getChildren().get(0);
        runtime.requestFocus(input); runtime.flush();
        harness.click(portalInput); runtime.flush();
        Assert.assertSame("直接点击 Portal 输入应恢复权威焦点", portalInput, runtime.getFocusedNode());
        Assert.assertEquals("直接点击 Portal 输入应显示 focus border", SceneChromeTokens.BORDER_FOCUS,
                portalInput.getBorderColor());
        Assert.assertEquals("直接点击 Portal 输入应显示 caret", SceneChromeTokens.BORDER_FOCUS,
                portalInput.__getChildren().get(1).getBackgroundColor());
        Assert.assertEquals("Current values (4)", portal().__getChildren().get(1).getText());
        SceneNode currentRows = portal().__getChildren().get(2).__getChildren().get(0);
        Assert.assertEquals(4, visibleRowCount(currentRows));
        Assert.assertEquals(4 * 42, portal().__getChildren().get(2).getPreferredHeight());
        Assert.assertEquals("Search results (7)", portal().__getChildren().get(3).getText());
        Assert.assertEquals(7, visibleRowCount(portal().__getChildren().get(4).__getChildren().get(0)));
        SceneNode malformed = currentRows.__getChildren().get(2);
        Assert.assertFalse(malformed.__getChildren().get(0).isHitTestable());
        Assert.assertFalse(memberInfoColumn(malformed).isHitTestable());
        SceneNode malformedBadge = memberBadge(malformed);
        Assert.assertEquals(Collections.singletonList("Error/Invalid"), texts(malformedBadge));
        Assert.assertFalse(malformedBadge.isHitTestable());
        SceneNode malformedActions = visibleActions(malformed);
        Assert.assertFalse(malformedActions.isHitTestable());
        Assert.assertFalse(malformedActions.__getChildren().get(0).isHitTestable());
        Assert.assertFalse(malformedActions.__getChildren().get(1).isHitTestable());
        harness.click(memberAction(malformed, 0).__getChildren().get(0));
        Assert.assertEquals(12L, edited.get());
        Assert.assertEquals(1, runtime.getOverlayHost().size());
        Assert.assertSame("点击内部 Edit 后应与直接点击一致地恢复搜索焦点",
                portal().__getChildren().get(0), runtime.getFocusedNode());
        Assert.assertEquals("内部 Edit 后搜索输入应保持 focus border", SceneChromeTokens.BORDER_FOCUS,
                portal().__getChildren().get(0).getBorderColor());
        Assert.assertEquals("内部 Edit 后搜索输入应保持 caret", SceneChromeTokens.BORDER_FOCUS,
                portal().__getChildren().get(0).__getChildren().get(1).getBackgroundColor());
        harness.click(memberAction(currentRows.__getChildren().get(0), 0).__getChildren().get(0)); doLayout();
        Assert.assertEquals(10L, edited.get());
        Assert.assertEquals("LIST_MEMBERS variant portal 顶部仍应是输入框三节点结构", 3,
                portal().__getChildren().get(0).__getChildren().size());
        Assert.assertSame("进入 variants 后应在 portal 注册完成后聚焦顶部搜索框",
                portal().__getChildren().get(0), runtime.getFocusedNode());
        Assert.assertTrue(texts(portal()).contains("V"));
        key(SceneKey.ESCAPE, SceneKeyAction.PRESSED);
        Assert.assertEquals("variants Escape 应返回 candidates", 1, runtime.getOverlayHost().size());
        Assert.assertEquals("返回 candidates 后顶部仍应是输入框三节点结构", 3,
                portal().__getChildren().get(0).__getChildren().size());
        Assert.assertSame("返回 candidates 后应恢复搜索焦点",
                portal().__getChildren().get(0), runtime.getFocusedNode());
    }

    /** LIST_MEMBERS 摘要仅展示，Manage 打开管理 portal；两区按自然行数增长并分别在 6/12 行封顶。 */
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
        input = picker.__getChildren().get(1).__getChildren().get(0);
        SceneNode summary = picker.__getChildren().get(1).__getChildren().get(1);
        harness.mountRoot(sceneRoot, 320, 420);
        Assert.assertTrue(texts(picker).containsAll(Arrays.asList("No items configured", "Manage")));
        Assert.assertFalse("关闭态不得常驻搜索输入", texts(picker).contains("Search"));

        harness.click(summary);
        Assert.assertTrue("点击摘要不得打开管理 portal", runtime.getOverlayHost().isEmpty());
        open(); runtime.flush(); doLayout();
        Assert.assertEquals("点击 Manage 应打开管理 portal", 1, runtime.getOverlayHost().size());
        SceneNode portalSearch = portal().__getChildren().get(0);
        Assert.assertSame("Manage click+flush 后应聚焦 portal 搜索框", portalSearch, runtime.getFocusedNode());
        key(SceneKey.TAB, SceneKeyAction.PRESSED);
        Assert.assertSame("只有搜索框可聚焦时 Tab 应在 active overlay 内环绕",
                portalSearch, runtime.getFocusedNode());
        key(SceneKey.TAB, SceneKeyAction.PRESSED, true);
        Assert.assertSame("Shift+Tab 也应在 active overlay 内反向环绕",
                portalSearch, runtime.getFocusedNode());
        SceneAnchorResolver.ResolvedAnchor upward = SceneAnchorResolver.resolveAuto(
                new AnchorRect(0, 380, 96, 24), 320, 420, 360,
                runtime.getOverlayHost().bottomFirst().get(0).getAnchoredLayout());
        Assert.assertTrue("底部锚点应触发向上展开", upward.getY() < 380);
        Assert.assertEquals("向上展开不得反转搜索框与 section 的物理顺序", Arrays.asList(
                "Current values (0)", "No current members",
                "Search results (0)", "No matching results"), texts(portal()));
        Assert.assertEquals("Current values (0)", portal().__getChildren().get(1).getText());
        Assert.assertEquals("Search results (0)", portal().__getChildren().get(3).getText());
        Assert.assertTrue(texts(portal().__getChildren().get(2)).contains("No current members"));
        Assert.assertTrue(texts(portal().__getChildren().get(4)).contains("No matching results"));
        Assert.assertEquals(42, portal().__getChildren().get(2).getPreferredHeight());
        Assert.assertEquals(34, portal().__getChildren().get(4).getPreferredHeight());

        for (int count : new int[] {1, 5, 6, 7}) {
            members.set(currentMembers(count)); runtime.flush(); doLayout();
            Assert.assertEquals(Math.min(count, 6) * 42,
                    portal().__getChildren().get(2).getPreferredHeight());
        }
        for (int count : new int[] {1, 11, 12, 13}) {
            results.set(resultCount(count)); runtime.flush(); doLayout();
            Assert.assertEquals(Math.min(count, 12) * 34,
                    portal().__getChildren().get(4).getPreferredHeight());
            Assert.assertEquals(Math.min(count, 12), visibleRowCount(
                    portal().__getChildren().get(4).__getChildren().get(0)));
        }
        Assert.assertFalse("LIST_MEMBERS 不得保留重复结果 footer", texts(portal()).contains("13 results"));
        Assert.assertEquals(0, writes.get());

        SceneAnchorResolver.ResolvedAnchor shortHost = SceneAnchorResolver.resolveAuto(
                new AnchorRect(0, 110, 96, 24), 320, 160, 1000,
                runtime.getOverlayHost().bottomFirst().get(0).getAnchoredLayout());
        layout.layout(portal(), new Constraints(shortHost.getWidth(), shortHost.getMaxHeight()));
        Assert.assertTrue("portal 总高度必须由 anchor maxHeight 最终裁剪",
                ((LayoutBox) portal().getCachedLayout()).getHeight() <= shortHost.getMaxHeight());
        doLayout();

        SceneNode currentSection = portal().__getChildren().get(2);
        SceneNode currentRows = currentSection.__getChildren().get(0);
        SceneNode resultSection = portal().__getChildren().get(4);
        SceneNode resultRows = resultSection.__getChildren().get(0);
        harness.scroll(currentSection, -1000); runtime.flush(); doLayout();
        Assert.assertEquals("七个成员滚到底应保留一行合法偏移", 42, currentSection.getScrollOffsetY());

        members.set(currentMembers(6)); runtime.flush(); doLayout();
        Assert.assertEquals("成员收缩到 cap 后应回夹到顶部", 0, currentSection.getScrollOffsetY());
        Assert.assertEquals("六行成员 viewport 应保持 252px 物理高度", 252,
                currentSection.getPreferredHeight());
        Assert.assertEquals(6, visibleRowCount(currentRows));
        members.set(currentMembers(1)); runtime.flush(); doLayout();
        Assert.assertEquals("成员收缩到一项后应回夹到顶部", 0, currentSection.getScrollOffsetY());
        Assert.assertEquals("单项成员 viewport 应按自然高度收缩", 42,
                currentSection.getPreferredHeight());
        Assert.assertEquals(1, visibleRowCount(currentRows));

        harness.scroll(portal(), -1000); runtime.flush(); doLayout();
        harness.scroll(resultRows, -1); runtime.flush(); doLayout();
        Assert.assertEquals("十三个结果滚到底应从第二项开始", "R1",
                resultRows.__getChildren().get(0).__getChildren().get(1).getText());
        results.set(resultCount(12)); runtime.flush(); doLayout();
        Assert.assertEquals("结果收缩到 cap 后窗口应回夹到首项", "R0",
                resultRows.__getChildren().get(0).__getChildren().get(1).getText());
        Assert.assertTrue("十二个现存结果行都应完整可见", allRowsFitViewport(resultSection, resultRows));

        members.set(currentMembers(7)); runtime.flush(); doLayout();
        harness.scroll(portal(), 1000); runtime.flush(); doLayout();
        harness.scroll(currentSection, -42); runtime.flush(); doLayout();
        members.set(currentMembers(8)); runtime.flush(); doLayout();
        Assert.assertEquals("成员增长不得重置仍合法的滚动位置", 42, currentSection.getScrollOffsetY());
        members.set(currentMembers(1)); results.set(resultCount(13)); runtime.flush(); doLayout();
        harness.scroll(portal(), -1000); runtime.flush(); doLayout();
        harness.scroll(resultRows, -1); runtime.flush(); doLayout();
        results.set(resultCount(14)); runtime.flush(); doLayout();
        Assert.assertEquals("结果增长不得重置仍合法的窗口位置", "R1",
                resultRows.__getChildren().get(0).__getChildren().get(1).getText());

        results.set(resultCount(0)); runtime.flush(); doLayout();
        Assert.assertTrue("空结果不得残留旧窗口行", resultRows.__getChildren().isEmpty());
        Assert.assertTrue(texts(resultSection).contains("No matching results"));

        runtime.requestFocus(portal().__getChildren().get(0)); harness.typeText("draft"); runtime.flush();
        Assert.assertEquals("draft", query.get());
        runtime.getOverlayHost().bottomFirst().get(0).requestDismiss(); runtime.flush();
        Assert.assertEquals("", query.get());
        Assert.assertEquals(0, writes.get());
        Assert.assertSame("外部 dismiss 后焦点应回到 Manage", input, runtime.getFocusedNode());
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
            SceneNode manage = management.__getChildren().get(0);
            SceneNode summary = management.__getChildren().get(1);
            LayoutBox managementBox = (LayoutBox) management.getCachedLayout();
            LayoutBox summaryBox = (LayoutBox) summary.getCachedLayout();
            LayoutBox manageBox = (LayoutBox) manage.getCachedLayout();

            Assert.assertEquals("Manage 应有稳定先验宽度", 96, manage.getPreferredWidth());
            Assert.assertEquals("Manage 应保留稳定布局宽度", 96, manageBox.getWidth());
            Assert.assertTrue("Manage 必须位于摘要之前",
                    manageBox.getX() + manageBox.getWidth() <= summaryBox.getX());
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

    /**
     * LIST_MEMBERS 问题提示只作展示：duplicate 按成员计，malformed 优先且不参与重复。
     * 删除重复项后剩余行必须即时清除提示，成员顺序和配置内容不得被合并或重排。
     */
    @Test
    public void listMemberIssuesCountMembersAndReactWithoutChangingConfiguration() {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        runtime = harness.getRuntime(); sceneRoot = new SceneNode();
        query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        results = Signal.create(SearchPickerData.SearchResult.empty());
        SearchPickerData.Candidate same = candidate("same", "Same");
        SearchPickerData.Candidate other = candidate("other", "Other");
        SearchPickerData.CurrentMember first = member(10L, same);
        SearchPickerData.CurrentMember second = member(11L, same);
        SearchPickerData.CurrentMember malformed = new SearchPickerData.CurrentMember(12L, null, null, false);
        SearchPickerData.CurrentMember distinct = member(13L, other);
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(Arrays.asList(
                first, malformed, second, distinct));
        AtomicInteger edits = new AtomicInteger();
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, value -> { }, adapter).currentMembers(members, ignored -> edits.incrementAndGet())
                .onRemoveCurrent(memberId -> {
                    ArrayList<SearchPickerData.CurrentMember> next = new ArrayList<SearchPickerData.CurrentMember>();
                    for (SearchPickerData.CurrentMember value : members.get()) {
                        if (value.memberId() != memberId) next.add(value);
                    }
                    members.set(next);
                    return true;
                }).build()));
        runtime.flush();
        SceneNode picker = sceneRoot.__getChildren().get(0);
        input = picker.__getChildren().get(1).__getChildren().get(0);
        harness.mountRoot(sceneRoot, 320, 420);
        Assert.assertTrue(texts(picker).containsAll(Arrays.asList("Configured 4 items", "invalid 1 · duplicate 2")));
        Assert.assertEquals(Arrays.asList(10L, 12L, 11L, 13L), memberIds(members.get()));

        open(); runtime.flush(); doLayout();
        SceneNode rows = portal().__getChildren().get(2).__getChildren().get(0);
        Assert.assertTrue(texts(rows.__getChildren().get(0)).contains("Warning/Duplicate"));
        Assert.assertTrue(texts(rows.__getChildren().get(2)).contains("Warning/Duplicate"));
        Assert.assertTrue(texts(rows.__getChildren().get(1)).contains("Error/Invalid"));
        Assert.assertFalse(texts(rows.__getChildren().get(1)).contains("Warning/Duplicate"));
        Assert.assertFalse(texts(rows.__getChildren().get(3)).contains("Warning/Duplicate"));
        Assert.assertEquals("提示不得触发编辑回调", 0, edits.get());
        Assert.assertEquals(Arrays.asList(10L, 12L, 11L, 13L), memberIds(members.get()));

        layout.layout(portal(), new Constraints(360, 420));
        clickMemberAction(rows.__getChildren().get(2), 1);
        runtime.flush(); layout.layout(portal(), new Constraints(360, 420));
        clickMemberAction(rows.__getChildren().get(2), 1);
        runtime.flush(); layout.layout(portal(), new Constraints(360, 420));
        Assert.assertEquals(Arrays.asList(10L, 12L, 13L), memberIds(members.get()));
        Assert.assertTrue(texts(picker).containsAll(Arrays.asList("Configured 3 items", "invalid 1")));
        Assert.assertFalse(texts(picker).contains("duplicate 1"));
        rows = portal().__getChildren().get(2).__getChildren().get(0);
        Assert.assertFalse("删除后剩余同 key 成员应即时清除 duplicate", texts(rows).contains("Warning/Duplicate"));

        members.set(Arrays.asList(first, member(14L, same), member(15L, same), malformed));
        runtime.flush(); doLayout();
        Assert.assertTrue("三个同 key 合法成员的 duplicate 计数应为 3",
                texts(picker).contains("invalid 1 · duplicate 3"));
        rows = portal().__getChildren().get(2).__getChildren().get(0);
        int duplicateBadges = 0;
        for (SceneNode row : rows.__getChildren()) {
            if (texts(row).contains("Warning/Duplicate")) duplicateBadges++;
        }
        Assert.assertEquals(3, duplicateBadges);

        members.set(Arrays.asList(first, distinct, malformed));
        runtime.flush(); doLayout();
        Assert.assertTrue(texts(picker).containsAll(Arrays.asList("Configured 3 items", "invalid 1")));
        Assert.assertFalse("不同 candidate 不得标 duplicate", texts(portal()).contains("Warning/Duplicate"));
    }

    /** 同一 memberId 的合法性与重复状态双向变化只更新绑定，不重建 keyed 行。 */
    @Test
    public void listMemberIssueBindingsFollowCurrentMemberWithoutRebuildingRow() {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        runtime = harness.getRuntime(); sceneRoot = new SceneNode();
        query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        results = Signal.create(SearchPickerData.SearchResult.empty());
        SearchPickerData.Candidate same = candidate("same", "Same");
        SearchPickerData.Candidate other = candidate("other", "Other");
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(Arrays.asList(
                member(10L, same), member(11L, other)));
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, value -> { }, adapter).currentMembers(members, ignored -> { }).build()));
        runtime.flush(); input = sceneRoot.__getChildren().get(0).__getChildren().get(1)
                .__getChildren().get(0);
        harness.mountRoot(sceneRoot, 320, 300); open(); runtime.flush(); doLayout();
        SceneNode rows = portal().__getChildren().get(2).__getChildren().get(0);
        SceneNode stableRow = rows.__getChildren().get(0);
        SceneNode badge = memberBadge(stableRow);
        Assert.assertEquals("Same", memberPrimary(stableRow).getText());
        Assert.assertTrue(texts(badge).isEmpty());

        members.set(Arrays.asList(new SearchPickerData.CurrentMember(10L, null, null, false),
                member(11L, other)));
        runtime.flush(); doLayout();
        Assert.assertSame("valid→malformed 不得重建同 id 行", stableRow, rows.__getChildren().get(0));
        Assert.assertEquals("Unable to read this value", memberPrimary(stableRow).getText());
        Assert.assertEquals(Collections.singletonList("Error/Invalid"), texts(badge));
        Assert.assertNotEquals("malformed 应切换危险背景", 0, badge.getBackgroundColor());

        members.set(Arrays.asList(member(10L, same), member(11L, same)));
        runtime.flush(); doLayout();
        Assert.assertSame("malformed→valid 不得重建同 id 行", stableRow, rows.__getChildren().get(0));
        Assert.assertEquals("Same", memberPrimary(stableRow).getText());
        Assert.assertEquals(Collections.singletonList("Warning/Duplicate"), texts(badge));
        Assert.assertEquals("duplicate 不得残留 malformed 背景", 0, badge.getBackgroundColor());

        members.set(Arrays.asList(member(10L, same), member(11L, other)));
        runtime.flush(); doLayout();
        Assert.assertSame("duplicate 消失不得重建同 id 行", stableRow, rows.__getChildren().get(0));
        Assert.assertTrue("duplicate 消失应即时清空 badge", texts(badge).isEmpty());
    }

    /** 问题 badge 在窄 portal 中不参与命中，固定行高不变且编辑/删除操作仍在行内可达。 */
    @Test
    public void listMemberIssueBadgesKeepNarrowActionsReachable() {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        runtime = harness.getRuntime(); sceneRoot = new SceneNode();
        query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        results = Signal.create(SearchPickerData.SearchResult.empty());
        SearchPickerData.Candidate same = candidate("same", "Same");
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(Arrays.asList(
                member(1L, same), member(2L, same)));
        AtomicInteger edits = new AtomicInteger();
        AtomicInteger removes = new AtomicInteger();
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, value -> { }, adapter).currentMembers(members, ignored -> edits.incrementAndGet())
                .onRemoveCurrent(ignored -> { removes.incrementAndGet(); return true; })
                .build()));
        runtime.flush(); input = sceneRoot.__getChildren().get(0).__getChildren().get(1)
                .__getChildren().get(0);
        harness.mountRoot(sceneRoot, 360, 300); open(); runtime.flush();
        layout.layout(portal(), new Constraints(360, 300));
        SceneNode row = currentFirstRow();
        SceneNode badgeHost = memberBadge(row);
        SceneNode actions = visibleActions(row);
        LayoutBox actionsBox = (LayoutBox) actions.getCachedLayout();
        Assert.assertEquals(42, row.getPreferredHeight());
        Assert.assertFalse(badgeHost.isHitTestable());
        Assert.assertEquals("操作区应保留确认删除态所需宽度", 174, actionsBox.getWidth());
        Assert.assertTrue("编辑按钮不得被 badge 挤成零宽",
                ((LayoutBox) memberAction(row, 0).getCachedLayout()).getWidth() > 0);
        Assert.assertTrue("删除按钮不得被 badge 挤成零宽",
                ((LayoutBox) memberAction(row, 1).getCachedLayout()).getWidth() > 0);
        harness.click(memberAction(row, 0));
        Assert.assertEquals("窄宽下编辑仍可达", 1, edits.get());
        layout.layout(portal(), new Constraints(360, 300));
        harness.click(memberAction(row, 1));
        runtime.flush(); layout.layout(portal(), new Constraints(360, 300));
        Assert.assertEquals(Arrays.asList("Cancel", "Confirm remove"), texts(actions));
        harness.click(memberAction(row, 1));
        runtime.flush(); layout.layout(portal(), new Constraints(360, 300));
        Assert.assertEquals("窄宽下 Remove→Confirm remove 应可达且只写一次", 1, removes.get());
    }

    /** 360/480px 下长标签、问题提示与固定操作 rail 依次排布，状态切换不移动操作区。 */
    @Test
    public void listMemberLongLabelsStayBeforeStableRightActionsAcrossStates() {
        String longLabel = "超长中文 English registry namespace:block/with_canonical_tail";
        SearchPickerData.Candidate candidate = candidate("namespace:block/with_canonical_tail", longLabel);
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(
                Collections.singletonList(member(42L, candidate)));
        mountListMembersPicker(members, new AtomicInteger(), new AtomicInteger());
        open();
        SceneNode stableRow = currentFirstRow();
        SceneNode label = memberPrimary(stableRow);
        String completeText = label.getText();
        Assert.assertTrue("完整显示原文必须保留在 label 数据中", completeText.contains(longLabel));

        for (int width : new int[] {360, 480}) {
            members.set(Collections.singletonList(member(42L, candidate)));
            runtime.flush();
            layout.layout(portal(), new Constraints(width, 420));
            stableRow = currentFirstRow();
            int normalActionsX = assertMemberRailOrder(stableRow, width, false);
            LayoutBox normalFirst = (LayoutBox) memberAction(stableRow, 0).getCachedLayout();
            LayoutBox normalSecond = (LayoutBox) memberAction(stableRow, 1).getCachedLayout();
            int normalFirstX = normalFirst.getX();
            int normalSecondX = normalSecond.getX();

            clickMemberAction(stableRow, 1);
            runtime.flush();
            layout.layout(portal(), new Constraints(width, 420));
            Assert.assertEquals("pending 不得移动操作区", normalActionsX,
                    assertMemberRailOrder(stableRow, width, false));
            Assert.assertEquals("pending 不得移动第一按钮", normalFirstX,
                    ((LayoutBox) memberAction(stableRow, 0).getCachedLayout()).getX());
            Assert.assertEquals("pending 不得移动第二按钮", normalSecondX,
                    ((LayoutBox) memberAction(stableRow, 1).getCachedLayout()).getX());
            clickMemberAction(stableRow, 0);
            runtime.flush();

            members.set(Collections.singletonList(
                    new SearchPickerData.CurrentMember(42L, null, null, false)));
            runtime.flush();
            layout.layout(portal(), new Constraints(width, 420));
            Assert.assertEquals("invalid badge 不得移动操作区", normalActionsX,
                    assertMemberRailOrder(stableRow, width, true));

            members.set(Arrays.asList(member(42L, candidate), member(43L, candidate)));
            runtime.flush();
            layout.layout(portal(), new Constraints(width, 420));
            Assert.assertSame("状态切换不得重建稳定 id 行", stableRow, currentFirstRow());
            Assert.assertEquals("duplicate badge 不得移动操作区", normalActionsX,
                    assertMemberRailOrder(stableRow, width, true));
        }
    }

    /** 中文操作文案在 360/480px 与 normal/pending 间复用同一组固定按钮盒。 */
    @Test
    public void listMemberChineseActionsKeepExactBoxesAcrossWidthsAndPendingState() {
        SearchPickerData.Candidate candidate = candidate("minecraft:stone", "石头");
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(
                Collections.singletonList(member(42L, candidate)));
        SearchPickerPresentation chinese = SearchPickerPresentation.builder()
                .edit("编辑").remove("移除").cancelRemove("取消")
                .confirmRemove("确认移除")
                .currentMemberPrimaryFormatter(member -> "石头")
                .currentMemberSecondaryFormatter(member -> "minecraft:stone@3")
                .build();
        mountListMembersPicker(members, new AtomicInteger(), new AtomicInteger(), chinese);
        open();

        for (int width : new int[] {360, 480}) {
            layout.layout(portal(), new Constraints(width, 420));
            SceneNode row = currentFirstRow();
            AnchorRect normalFirst = SceneGeometry.absoluteBox(memberAction(row, 0), 0, 0);
            AnchorRect normalSecond = SceneGeometry.absoluteBox(memberAction(row, 1), 0, 0);
            Assert.assertEquals(54, normalFirst.getWidth());
            Assert.assertEquals(118, normalSecond.getWidth());

            clickMemberAction(row, 1);
            runtime.flush();
            layout.layout(portal(), new Constraints(width, 420));
            Assert.assertEquals(Arrays.asList("取消", "确认移除"), texts(visibleActions(row)));
            AnchorRect pendingFirst = SceneGeometry.absoluteBox(memberAction(row, 0), 0, 0);
            AnchorRect pendingSecond = SceneGeometry.absoluteBox(memberAction(row, 1), 0, 0);
            Assert.assertEquals(normalFirst.getX(), pendingFirst.getX());
            Assert.assertEquals(normalFirst.getWidth(), pendingFirst.getWidth());
            Assert.assertEquals(normalSecond.getX(), pendingSecond.getX());
            Assert.assertEquals(normalSecond.getWidth(), pendingSecond.getWidth());
            clickMemberAction(row, 0);
            runtime.flush();
        }
    }

    /** 当前成员 label 自身的 clip 必须包住完整 TEXT，操作按钮绘制位于该 clip 之外。 */
    @Test
    public void listMemberLabelPaintIsClippedBeforeActionPaint() {
        String longLabel = "中文 English very long canonical namespace:block[property=value] tail";
        SearchPickerData.Candidate candidate = candidate("namespace:block[property=value]", longLabel);
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(
                Collections.singletonList(member(42L, candidate)));
        mountListMembersPicker(members, new AtomicInteger(), new AtomicInteger());
        open();
        layout.layout(portal(), new Constraints(360, 420));
        SceneNode row = currentFirstRow();
        SceneNode label = memberPrimary(row);
        SceneNode actions = visibleActions(row);
        PaintPlan plan = new ScenePaintEngine(new FixedTextMeasurer(8, 16)).paint(portal()).getPlan();
        List<PaintCommand> commands = plan.getCommands();
        AnchorRect labelBox = SceneGeometry.absoluteBox(memberPrimaryBox(row), 0, 0);
        int labelText = commandIndex(commands, PaintCommandType.TEXT, label.getText());
        int editText = commandIndex(commands, PaintCommandType.TEXT, "Edit");
        int labelClip = matchingClipIndex(commands, labelBox);
        int labelClipPop = firstCommandAfter(commands, PaintCommandType.CLIP_POP, labelText);

        Assert.assertTrue("完整 label TEXT 命令必须存在", labelText >= 0);
        Assert.assertEquals("PaintPlan 必须保留完整 label 原文", label.getText(), commands.get(labelText).getText());
        Assert.assertTrue("label clip 必须先于自身 TEXT", labelClip >= 0 && labelClip < labelText);
        Assert.assertTrue("label clip 必须在自身 TEXT 后闭合", labelClipPop > labelText);
        Assert.assertTrue("操作按钮绘制不得落入 label clip", editText > labelClipPop);
        Assert.assertFalse("操作容器自身不参与命中", actions.isHitTestable());
    }

    /** 非零 overlay anchor/rootAbs 下仅可见按钮盒可触发动作，其余成员行区域均为零动作。 */
    @Test
    public void listMemberActionsUseExactVisibleHitBoxesWithTranslatedRoots() {
        String longLabel = "超长中文 English canonical namespace:block tail";
        SearchPickerData.Candidate candidate = candidate("namespace:block", longLabel);
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(Arrays.asList(
                member(42L, candidate), member(43L, candidate)));
        AtomicInteger edits = new AtomicInteger();
        AtomicInteger removes = new AtomicInteger();
        mountListMembersPicker(members, edits, removes);
        open();
        SceneOverlayHost.Entry entry = runtime.getOverlayHost().bottomFirst().get(0);
        entry.setAnchorX(37);
        entry.setAnchorY(41);
        layout.layout(portal(), new Constraints(480, 420));
        SceneNode row = currentFirstRow();
        SceneNode icon = row.__getChildren().get(0);
        SceneNode label = memberPrimary(row);
        SceneNode badge = memberBadge(row);
        SceneNode actions = visibleActions(row);
        SceneNode edit = memberAction(row, 0);
        SceneNode remove = memberAction(row, 1);
        AnchorRect actionsBox = SceneGeometry.absoluteBox(actions, entry.getAnchorX(), entry.getAnchorY());
        AnchorRect editBox = SceneGeometry.absoluteBox(edit, entry.getAnchorX(), entry.getAnchorY());
        AnchorRect removeBox = SceneGeometry.absoluteBox(remove, entry.getAnchorX(), entry.getAnchorY());

        routeClick(centerX(editBox), centerY(editBox), 101, 53);
        Assert.assertEquals("Edit 中心只触发编辑", 1, edits.get());
        Assert.assertEquals(0, removes.get());
        routeClick(centerX(removeBox), centerY(removeBox), 101, 53);
        runtime.flush();
        layout.layout(portal(), new Constraints(480, 420));
        Assert.assertEquals("Delete 中心第一次只进入 pending", 1, edits.get());
        Assert.assertEquals(0, removes.get());
        AnchorRect cancelBox = SceneGeometry.absoluteBox(memberAction(row, 0),
                entry.getAnchorX(), entry.getAnchorY());
        routeClick(centerX(cancelBox), centerY(cancelBox), 101, 53);
        runtime.flush();
        layout.layout(portal(), new Constraints(480, 420));

        int unchangedEdits = edits.get();
        int unchangedRemoves = removes.get();
        assertNoMemberActionAt(icon, entry, edits, removes, unchangedEdits, unchangedRemoves);
        assertNoMemberActionAt(label, entry, edits, removes, unchangedEdits, unchangedRemoves);
        assertNoMemberActionAt(badge, entry, edits, removes, unchangedEdits, unchangedRemoves);
        routeClick(right(editBox) + 1, centerY(editBox), 101, 53);
        routeClick(editBox.getX() - 1, centerY(editBox), 101, 53);
        routeClick(right(removeBox) + 1, centerY(removeBox), 101, 53);
        Assert.assertEquals("按钮 gap、透明预留区与边界外 1px 均不得编辑", unchangedEdits, edits.get());
        Assert.assertEquals("按钮 gap、透明预留区与边界外 1px 均不得删除", unchangedRemoves, removes.get());
        Assert.assertFalse("成员行结构容器不得成为命中目标", row.isHitTestable());
    }

    /** 16/19px 真实行高下，成员内容与滚动算术始终共用固定 42px pitch。 */
    @Test
    public void listMemberRowsKeepExactFixedPitchAcrossSupportedFontMetrics() {
        for (int lineHeight : new int[] {16, 19}) {
            for (int count : new int[] {0, 1, 6, 7, 14}) {
                Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(currentMembers(count));
                mountListMembersPicker(members, new AtomicInteger(), new AtomicInteger(),
                        SearchPickerPresentation.defaultEnglish(), lineHeight);
                open();
                SceneNode section = portal().__getChildren().get(2);
                SceneNode rows = section.__getChildren().get(0);
                LayoutBox rowsBox = (LayoutBox) rows.getCachedLayout();

                Assert.assertEquals("成员内容总高必须严格等于 N×42", count * 42, rowsBox.getHeight());
                Assert.assertEquals("成员 viewport 应在六行封顶", count == 0 ? 42 : Math.min(count, 6) * 42,
                        ((LayoutBox) section.getCachedLayout()).getHeight());
                for (int index = 0; index < count; index++) {
                    SceneNode row = rows.__getChildren().get(index);
                    LayoutBox rowBox = (LayoutBox) row.getCachedLayout();
                    LayoutBox firstBox = (LayoutBox) memberInfoColumn(row).__getChildren().get(0)
                            .getCachedLayout();
                    LayoutBox secondBox = (LayoutBox) memberInfoColumn(row).__getChildren().get(1)
                            .getCachedLayout();
                    Assert.assertEquals(42, rowBox.getHeight());
                    Assert.assertEquals(42, row.getMaxHeight());
                    Assert.assertEquals(index * 42, rowBox.getY());
                    Assert.assertEquals(lineHeight, firstBox.getHeight());
                    Assert.assertEquals(lineHeight, secondBox.getHeight());
                    Assert.assertEquals("双行之间必须保留 2px gap", 2,
                            secondBox.getY() - firstBox.getY() - firstBox.getHeight());
                    Assert.assertEquals(lineHeight,
                            ((LayoutBox) memberPrimary(row).getCachedLayout()).getHeight());
                    Assert.assertEquals(lineHeight,
                            ((LayoutBox) memberSecondary(row).getCachedLayout()).getHeight());
                    Assert.assertTrue("按钮不得超过真实 lineHeight",
                            ((LayoutBox) memberAction(row, 0).getCachedLayout()).getHeight() <= lineHeight);
                    Assert.assertTrue("按钮不得超过真实 lineHeight",
                            ((LayoutBox) memberAction(row, 1).getCachedLayout()).getHeight() <= lineHeight);
                }

                harness.scroll(section, -1000); runtime.flush(); doLayout();
                int expectedOffset = Math.max(0, count - 6) * 42;
                Assert.assertEquals(expectedOffset, section.getScrollOffsetY());
                if (count > 0) {
                    AnchorRect viewport = SceneGeometry.absoluteBox(section, 0, 0);
                    AnchorRect last = SceneGeometry.absoluteBox(rows.__getChildren().get(count - 1), 0, 0);
                    int expectedTop = count >= 6 ? 210 : (count - 1) * 42;
                    Assert.assertEquals(expectedTop, last.getY() - viewport.getY());
                    Assert.assertEquals(expectedTop + 42, last.getBottom() - viewport.getY());
                }
            }
        }
    }

    /** 19px 生产行高滚到底后，末项文字、绘制裁剪、状态盒与精确命中均保持完整一致。 */
    @Test
    public void listMemberLastRowPaintAndHitStayInsideViewportAtProductionLineHeight() {
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(currentMembers(14));
        AtomicInteger edits = new AtomicInteger();
        AtomicInteger removes = new AtomicInteger();
        SearchPickerPresentation presentation = SearchPickerPresentation.builder()
                .currentMemberPrimaryFormatter(member -> member.selection() == null
                        ? "invalid" : "primary-" + member.selection().candidateKey())
                .currentMemberSecondaryFormatter(member -> member.selection() == null
                        ? "" : member.selection().candidateKey() + "-descender-gyp")
                .build();
        mountListMembersPicker(members, edits, removes, presentation, 19);
        open();
        layout.layout(portal(), new Constraints(480, 420));
        SceneNode section = portal().__getChildren().get(2);
        SceneNode rows = section.__getChildren().get(0);
        harness.scroll(section, -1000); runtime.flush(); doLayout();
        layout.layout(portal(), new Constraints(480, 420));
        SceneNode lastRow = rows.__getChildren().get(13);
        SceneNode secondary = memberSecondary(lastRow);
        AnchorRect viewport = SceneGeometry.absoluteBox(section, 0, 0);
        AnchorRect last = SceneGeometry.absoluteBox(lastRow, 0, 0);
        AnchorRect secondaryBox = SceneGeometry.absoluteBox(secondary, 0, 0);

        Assert.assertEquals(336, section.getScrollOffsetY());
        Assert.assertEquals(210, last.getY() - viewport.getY());
        Assert.assertEquals(252, last.getBottom() - viewport.getY());
        Assert.assertEquals(19, secondaryBox.getHeight());
        Assert.assertTrue("末项 secondary 顶部必须在 viewport 内", secondaryBox.getY() >= viewport.getY());
        Assert.assertTrue("末项 secondary descender 底部必须在 viewport 内",
                secondaryBox.getBottom() <= viewport.getBottom());

        PaintPlan plan = new ScenePaintEngine(new FixedTextMeasurer(8, 19)).paint(portal()).getPlan();
        List<PaintCommand> commands = plan.getCommands();
        int secondaryText = commandIndex(commands, PaintCommandType.TEXT, "m13-descender-gyp");
        int sectionClip = matchingClipIndex(commands, viewport);
        int sectionClipPop = firstCommandAfter(commands, PaintCommandType.CLIP_POP, secondaryText);
        Assert.assertTrue("末项 secondary 必须产出完整 TEXT 命令", secondaryText >= 0);
        Assert.assertTrue("成员 viewport clip 必须先于末项文字", sectionClip >= 0 && sectionClip < secondaryText);
        Assert.assertTrue("末项文字绘制后必须闭合 viewport clip", sectionClipPop > secondaryText);

        SceneOverlayHost.Entry entry = runtime.getOverlayHost().bottomFirst().get(0);
        AnchorRect normalEdit = SceneGeometry.absoluteBox(memberAction(lastRow, 0),
                entry.getAnchorX(), entry.getAnchorY());
        AnchorRect normalRemove = SceneGeometry.absoluteBox(memberAction(lastRow, 1),
                entry.getAnchorX(), entry.getAnchorY());
        AnchorRect routedViewport = SceneGeometry.absoluteBox(section, entry.getAnchorX(), entry.getAnchorY());
        LayoutBox normalEditLocal = (LayoutBox) memberAction(lastRow, 0).getCachedLayout();
        LayoutBox normalRemoveLocal = (LayoutBox) memberAction(lastRow, 1).getCachedLayout();
        SceneNode searchResultsTitle = portal().__getChildren().get(3);
        AnchorRect titleBox = SceneGeometry.absoluteBox(searchResultsTitle,
                entry.getAnchorX(), entry.getAnchorY());
        routeClick(centerX(titleBox), centerY(titleBox), 0, 0);
        routeClick(centerX(normalEdit), routedViewport.getBottom(), 0, 0);
        Assert.assertEquals("标题与 clip bottom 不得触发编辑", 0, edits.get());
        Assert.assertEquals("标题与 clip bottom 不得触发删除", 0, removes.get());

        routeClick(centerX(normalRemove), centerY(normalRemove), 0, 0);
        runtime.flush(); doLayout();
        layout.layout(portal(), new Constraints(480, 420));
        Assert.assertEquals(0, removes.get());
        Assert.assertEquals(Arrays.asList("Cancel", "Confirm remove"), texts(visibleActions(lastRow)));
        Assert.assertEquals(normalEditLocal, memberAction(lastRow, 0).getCachedLayout());
        Assert.assertEquals(normalRemoveLocal, memberAction(lastRow, 1).getCachedLayout());
        AnchorRect pendingEdit = SceneGeometry.absoluteBox(memberAction(lastRow, 0),
                entry.getAnchorX(), entry.getAnchorY());
        routeClick(centerX(pendingEdit), centerY(pendingEdit), 0, 0);
        runtime.flush(); doLayout();
        layout.layout(portal(), new Constraints(480, 420));
        AnchorRect editable = SceneGeometry.absoluteBox(memberAction(lastRow, 0),
                entry.getAnchorX(), entry.getAnchorY());
        routeClick(centerX(editable), centerY(editable), 0, 0);
        Assert.assertEquals("末项 Edit 中心必须精确命中", 1, edits.get());

        ArrayList<SearchPickerData.CurrentMember> issueMembers =
                new ArrayList<SearchPickerData.CurrentMember>(members.get());
        issueMembers.set(13, new SearchPickerData.CurrentMember(14L, null, null, false));
        members.set(issueMembers); runtime.flush(); doLayout();
        layout.layout(portal(), new Constraints(480, 420));
        Assert.assertSame("issue 状态不得重建稳定 id 行", lastRow, rows.__getChildren().get(13));
        Assert.assertEquals(normalEditLocal, memberAction(lastRow, 0).getCachedLayout());
        Assert.assertEquals(normalRemoveLocal, memberAction(lastRow, 1).getCachedLayout());

        members.set(currentMembers(14)); runtime.flush(); doLayout();
        layout.layout(portal(), new Constraints(480, 420));
        Assert.assertEquals(1, edits.get());
        Assert.assertEquals(0, removes.get());
    }

    /** 超过双行 42px 预算的字体指标必须显式失败，禁止依赖 maxHeight 静默裁切。 */
    @Test
    public void listMemberRowsRejectUnsupportedLineHeight() {
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(currentMembers(1));
        mountListMembersPicker(members, new AtomicInteger(), new AtomicInteger(),
                SearchPickerPresentation.defaultEnglish(), 21);
        try {
            open();
            Assert.fail("lineHeight=21 应超过固定成员行预算");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("exceeds the fixed 42px row budget"));
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

        harness.click(memberAction(row, 0).__getChildren().get(0));
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
        Assert.assertSame("candidates Escape 后焦点应回 Manage", input, runtime.getFocusedNode());
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

    /** LIST_MEMBERS 从关闭态用方向键打开后可连续直接新增，提交保持 portal 与搜索焦点。 */
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
                .__getChildren().get(0);
        harness.mountRoot(sceneRoot, 320, 240); doLayout(); runtime.requestFocus(input);

        key(SceneKey.ARROW_UP, SceneKeyAction.PRESSED);
        Assert.assertEquals(1, beginAddCount.get());
        Assert.assertFalse(runtime.getOverlayHost().isEmpty());
        runtime.requestFocus(portal().__getChildren().get(0));
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        Assert.assertEquals("dirt", selection.candidateKey());
        Assert.assertEquals(1, commitCount.get());
        Assert.assertEquals(2, beginAddCount.get());
        Assert.assertEquals(1, runtime.getOverlayHost().size());
        Assert.assertSame(portal().__getChildren().get(0), runtime.getFocusedNode());

        runtime.requestFocus(portal().__getChildren().get(0));
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        Assert.assertEquals(2, commitCount.get());
        Assert.assertEquals(3, beginAddCount.get());
        Assert.assertEquals(1, runtime.getOverlayHost().size());
        Assert.assertSame(portal().__getChildren().get(0), runtime.getFocusedNode());
    }

    /** unknown 与无 variants 成员编辑进入候选替换态，并按稳定 id 原位替换而非追加。 */
    @Test
    public void listMemberUnknownAndNoVariantEditEnterFocusedReplacementState() {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16)); runtime = harness.getRuntime();
        sceneRoot = new SceneNode(); query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        SearchPickerData.Candidate plain = candidate("plain", "Plain");
        SearchPickerData.Candidate replacement = candidate("replacement", "Replacement");
        results = Signal.create(result(replacement));
        Signal<List<SearchPickerData.CurrentMember>> members = Signal.create(Arrays.asList(
                new SearchPickerData.CurrentMember(41L, new SearchPickerData.Selection("unknown",
                        SearchPickerData.SelectionMode.ALL, Collections.<String>emptyList()), null, false),
                member(42L, plain)));
        AtomicLong editingId = new AtomicLong(-1L);
        AtomicInteger beginAdds = new AtomicInteger();
        AtomicInteger replacements = new AtomicInteger();
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, ignored -> { }, adapter).selectionCommit(selected -> {
                    ArrayList<SearchPickerData.CurrentMember> next =
                            new ArrayList<SearchPickerData.CurrentMember>(members.get());
                    for (int index = 0; index < next.size(); index++) {
                        if (next.get(index).memberId() == editingId.get()) {
                            next.set(index, new SearchPickerData.CurrentMember(editingId.get(), selected,
                                    replacement, true));
                            members.set(next);
                            replacements.incrementAndGet();
                            return true;
                        }
                    }
                    return false;
                }).currentMembers(members, editingId::set).onBeginAdd(beginAdds::incrementAndGet).build()));
        runtime.flush(); input = sceneRoot.__getChildren().get(0).__getChildren().get(1)
                .__getChildren().get(0);
        harness.mountRoot(sceneRoot, 420, 360); open(); runtime.flush(); doLayout();

        clickMemberAction(currentFirstRow(), 0); runtime.flush(); doLayout();
        Assert.assertEquals(41L, editingId.get());
        Assert.assertEquals("编辑 unknown 不得重新进入新增态", 1, beginAdds.get());
        Assert.assertSame("unknown 编辑应聚焦候选搜索框", portal().__getChildren().get(0),
                runtime.getFocusedNode());
        harness.click(portal().__getChildren().get(4).__getChildren().get(0).__getChildren().get(0));
        runtime.flush();
        Assert.assertEquals(1, replacements.get());
        Assert.assertEquals("替换不得改变列表长度", 2, members.get().size());
        Assert.assertEquals("替换必须保留稳定 memberId", 41L, members.get().get(0).memberId());

        open(); runtime.flush(); doLayout();
        SceneNode secondRow = portal().__getChildren().get(2).__getChildren().get(0).__getChildren().get(1);
        clickMemberAction(secondRow, 0); runtime.flush(); doLayout();
        Assert.assertEquals(42L, editingId.get());
        Assert.assertEquals("无 variants 编辑不得调用 beginAdd", 2, beginAdds.get());
        Assert.assertSame("无 variants 编辑应进入候选替换态并聚焦搜索", portal().__getChildren().get(0),
                runtime.getFocusedNode());
    }

    /** 直接与变体新增均保留 query/focus，合法窗口保留且结果收缩越界时回夹。 */
    @Test
    public void listMembersConsecutiveDirectAndVariantAddsKeepPortalDraftFocusAndClampWindow() {
        runtime.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16)); runtime = harness.getRuntime();
        sceneRoot = new SceneNode(); query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        ArrayList<SearchPickerData.Candidate> candidates = new ArrayList<SearchPickerData.Candidate>();
        for (int index = 0; index < 14; index++) {
            candidates.add(index == 2
                    ? new SearchPickerData.Candidate("r2", "R2", Collections.singletonList(
                            new SearchPickerData.Variant("r2@0", "R2 variant")))
                    : candidate("r" + index, "R" + index));
        }
        results = Signal.create(new SearchPickerData.SearchResult(candidates));
        AtomicInteger commits = new AtomicInteger();
        AtomicInteger beginAdds = new AtomicInteger();
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, ignored -> { }, adapter)
                .selectionCommit(selected -> {
                    ArrayList<SearchPickerData.Candidate> remaining =
                            new ArrayList<SearchPickerData.Candidate>(results.get().candidates());
                    for (int index = 0; index < remaining.size(); index++) {
                        if (remaining.get(index).key().equals(selected.candidateKey())) {
                            remaining.remove(index);
                            break;
                        }
                    }
                    results.set(new SearchPickerData.SearchResult(remaining));
                    commits.incrementAndGet();
                    return true;
                })
                .currentMembers(Signal.create(Collections.<SearchPickerData.CurrentMember>emptyList()), ignored -> { })
                .onBeginAdd(beginAdds::incrementAndGet).build()));
        runtime.flush(); input = sceneRoot.__getChildren().get(0).__getChildren().get(1)
                .__getChildren().get(0);
        harness.mountRoot(sceneRoot, 420, 420); open(); runtime.flush(); doLayout();
        SceneNode search = portal().__getChildren().get(0);
        runtime.requestFocus(search); harness.typeText("draft"); runtime.flush(); doLayout();
        SceneNode resultSection = portal().__getChildren().get(4);
        SceneNode rows = resultSection.__getChildren().get(0);
        runtime.requestFocus(search);
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        harness.scroll(portal(), -1000); runtime.flush(); doLayout();
        harness.scroll(rows, -1); runtime.flush(); doLayout();
        Assert.assertEquals("R1", rows.__getChildren().get(0).__getChildren().get(1).getText());

        runtime.requestFocus(search);
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        Assert.assertEquals(1, commits.get());
        Assert.assertEquals("draft", query.get());
        Assert.assertEquals(1, runtime.getOverlayHost().size());
        Assert.assertSame(portal().__getChildren().get(0), runtime.getFocusedNode());
        rows = portal().__getChildren().get(4).__getChildren().get(0);
        Assert.assertEquals("合法窗口起点应保留", "R2",
                rows.__getChildren().get(0).__getChildren().get(1).getText());

        runtime.requestFocus(portal().__getChildren().get(0));
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        runtime.flush(); doLayout();
        Assert.assertEquals(2, commits.get());
        Assert.assertEquals("draft", query.get());
        Assert.assertEquals(1, runtime.getOverlayHost().size());
        Assert.assertSame(portal().__getChildren().get(0), runtime.getFocusedNode());
        rows = portal().__getChildren().get(4).__getChildren().get(0);
        Assert.assertEquals("结果收缩到十二项后越界窗口必须回夹", "R0",
                rows.__getChildren().get(0).__getChildren().get(1).getText());
        Assert.assertEquals("首次打开及两次成功后均应武装新增态", 3, beginAdds.get());
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
                .__getChildren().get(0);
        harness.mountRoot(sceneRoot, 320, 300);
    }

    /** 挂载可动态切换成员状态的 LIST_MEMBERS picker。 */
    private void mountListMembersPicker(Signal<List<SearchPickerData.CurrentMember>> members,
                                        AtomicInteger edits, AtomicInteger removes) {
        SearchPickerPresentation presentation = SearchPickerPresentation.builder()
                .currentMemberPrimaryFormatter(member -> member.selection() == null
                        ? "Unable to read this value" : member.enumerated()
                        ? member.candidate().label() : member.selection().candidateKey())
                .currentMemberSecondaryFormatter(member -> member.selection() == null
                        ? "" : member.selection().candidateKey() + "/canonical-tail-that-must-be-clipped")
                .build();
        mountListMembersPicker(members, edits, removes, presentation);
    }

    /** 按指定领域文案挂载可动态切换成员状态的 LIST_MEMBERS picker。 */
    private void mountListMembersPicker(Signal<List<SearchPickerData.CurrentMember>> members,
                                         AtomicInteger edits, AtomicInteger removes,
                                         SearchPickerPresentation presentation) {
        mountListMembersPicker(members, edits, removes, presentation, 16);
    }

    /** 按指定字体行高挂载可动态切换成员状态的 LIST_MEMBERS picker。 */
    private void mountListMembersPicker(Signal<List<SearchPickerData.CurrentMember>> members,
                                         AtomicInteger edits, AtomicInteger removes,
                                         SearchPickerPresentation presentation, int lineHeight) {
        runtime.dispose();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, lineHeight);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layout = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode(); query = Signal.create(""); enabled = Signal.create(Boolean.TRUE);
        results = Signal.create(SearchPickerData.SearchResult.empty());
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, SceneSearchPicker.Props.builder(query, results,
                enabled, query::set, value -> { }, adapter).presentation(presentation)
                .currentMembers(members, ignored -> edits.incrementAndGet())
                .onRemoveCurrent(ignored -> { removes.incrementAndGet(); return true; }).build()));
        runtime.flush();
        input = sceneRoot.__getChildren().get(0).__getChildren().get(1).__getChildren().get(0);
        harness.mountRoot(sceneRoot, 520, 420);
    }

    /** 断言成员行双行结构与最右固定操作区，并返回 actions.x。 */
    private static int assertMemberRailOrder(SceneNode row, int width, boolean issueVisible) {
        SceneNode icon = row.__getChildren().get(0);
        SceneNode info = memberInfoColumn(row);
        SceneNode primary = memberPrimary(row);
        SceneNode secondary = memberSecondary(row);
        SceneNode badge = memberBadge(row);
        SceneNode actions = visibleActions(row);
        LayoutBox rowBox = (LayoutBox) row.getCachedLayout();
        LayoutBox iconBox = (LayoutBox) icon.getCachedLayout();
        LayoutBox infoBox = (LayoutBox) info.getCachedLayout();
        LayoutBox primaryBox = (LayoutBox) memberPrimaryBox(row).getCachedLayout();
        LayoutBox secondaryBox = (LayoutBox) memberSecondaryBox(row).getCachedLayout();
        LayoutBox badgeBox = (LayoutBox) badge.getCachedLayout();
        LayoutBox actionsBox = (LayoutBox) actions.getCachedLayout();
        Assert.assertTrue("测试应覆盖指定 portal 宽度", rowBox.getWidth() <= width);
        Assert.assertEquals("图标宽度不得压缩", 18, iconBox.getWidth());
        AnchorRect rowAbs = SceneGeometry.absoluteBox(row, 0, 0);
        AnchorRect iconAbs = SceneGeometry.absoluteBox(icon, 0, 0);
        AnchorRect infoAbs = SceneGeometry.absoluteBox(info, 0, 0);
        AnchorRect primaryAbs = SceneGeometry.absoluteBox(memberPrimaryBox(row), 0, 0);
        AnchorRect secondaryAbs = SceneGeometry.absoluteBox(memberSecondaryBox(row), 0, 0);
        AnchorRect badgeAbs = SceneGeometry.absoluteBox(badge, 0, 0);
        AnchorRect actionsAbs = SceneGeometry.absoluteBox(actions, 0, 0);
        Assert.assertTrue("icon 必须位于信息列之前", right(iconAbs) <= infoAbs.getX());
        Assert.assertTrue("主文案不得进入 actions", right(primaryAbs) <= actionsAbs.getX());
        Assert.assertTrue("补充文案不得进入 badge", right(secondaryAbs) <= badgeAbs.getX());
        Assert.assertEquals("操作区宽度固定", 174, actionsBox.getWidth());
        Assert.assertTrue("操作区必须完整留在成员行内", right(actionsAbs) <= right(rowAbs) - row.getPaddingRight());
        Assert.assertEquals("问题状态宽度必须显式为 0/136", issueVisible ? 136 : 0, badgeBox.getWidth());
        Assert.assertTrue("主文案必须显式裁剪完整 TEXT", memberPrimaryBox(row).isClipChildren());
        Assert.assertTrue("补充文案必须显式裁剪完整 TEXT", memberSecondaryBox(row).isClipChildren());
        Assert.assertEquals("第一按钮槽固定 54px", 54,
                ((LayoutBox) actions.__getChildren().get(0).getCachedLayout()).getWidth());
        Assert.assertEquals("第二按钮槽固定 118px", 118,
                ((LayoutBox) actions.__getChildren().get(1).getCachedLayout()).getWidth());
        Assert.assertEquals("第一按钮必须填满固定槽", 54,
                ((LayoutBox) memberAction(row, 0).getCachedLayout()).getWidth());
        Assert.assertEquals("第二按钮必须填满固定槽", 118,
                ((LayoutBox) memberAction(row, 1).getCachedLayout()).getWidth());
        Assert.assertEquals("按钮槽间距固定 2px", 2,
                ((LayoutBox) actions.__getChildren().get(1).getCachedLayout()).getX()
                        - (((LayoutBox) actions.__getChildren().get(0).getCachedLayout()).getX() + 54));
        return actionsAbs.getX();
    }

    /** 按类型与文本定位 PaintPlan 命令。 */
    private static int commandIndex(List<PaintCommand> commands, PaintCommandType type, String text) {
        for (int index = 0; index < commands.size(); index++) {
            PaintCommand command = commands.get(index);
            if (command.getType() == type && text.equals(command.getText())) return index;
        }
        return -1;
    }

    /** 定位与目标盒完全对齐的 clip push。 */
    private static int matchingClipIndex(List<PaintCommand> commands, AnchorRect box) {
        for (int index = 0; index < commands.size(); index++) {
            PaintCommand command = commands.get(index);
            if (command.getType() == PaintCommandType.CLIP_PUSH
                    && command.getLeft() == box.getX() && command.getTop() == box.getY()
                    && command.getRight() == right(box) && command.getBottom() == box.getBottom()) return index;
        }
        return -1;
    }

    /** 从指定索引后定位首个目标类型命令。 */
    private static int firstCommandAfter(List<PaintCommand> commands, PaintCommandType type, int after) {
        for (int index = Math.max(0, after + 1); index < commands.size(); index++) {
            if (commands.get(index).getType() == type) return index;
        }
        return -1;
    }

    /** 白盒回退（host margin/rootAbs）：用真实绝对坐标路由非零 overlay anchor。 */
    private void routeClick(int x, int y, int rootAbsX, int rootAbsY) {
        InputFrameBuilder down = new InputFrameBuilder(x, y);
        down.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        runtime.route(sceneRoot, down.drainFrame(), rootAbsX, rootAbsY);
        InputFrameBuilder up = new InputFrameBuilder(x, y);
        up.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        runtime.route(sceneRoot, up.drainFrame(), rootAbsX, rootAbsY);
        runtime.flush();
    }

    /** 断言装饰节点中心不触发任何成员动作。 */
    private void assertNoMemberActionAt(SceneNode node, SceneOverlayHost.Entry entry,
                                        AtomicInteger editCounter, AtomicInteger removeCounter,
                                        int edits, int removes) {
        AnchorRect box = SceneGeometry.absoluteBox(node, entry.getAnchorX(), entry.getAnchorY());
        routeClick(centerX(box), centerY(box), 101, 53);
        Assert.assertEquals("装饰节点不得触发编辑", edits, editCounter.get());
        Assert.assertEquals("装饰节点不得触发删除", removes, removeCounter.get());
    }

    private static int centerX(AnchorRect box) { return box.getX() + box.getWidth() / 2; }
    private static int centerY(AnchorRect box) { return box.getY() + box.getHeight() / 2; }
    private static int right(AnchorRect box) { return box.getX() + box.getWidth(); }

    private SceneNode currentFirstRow() {
        return portal().__getChildren().get(2).__getChildren().get(0).__getChildren().get(0);
    }

    private void enterDeleteConfirmation(SceneNode row) {
        clickMemberAction(row, 1);
        runtime.flush();
        doLayout();
    }

    private void clickMemberAction(SceneNode row, int index) {
        harness.click(memberAction(row, index));
    }

    private static SceneNode visibleActions(SceneNode row) {
        return memberInfoColumn(row).__getChildren().get(0).__getChildren().get(1);
    }

    private static SceneNode memberInfoColumn(SceneNode row) { return row.__getChildren().get(1); }
    private static SceneNode memberPrimaryBox(SceneNode row) {
        return memberInfoColumn(row).__getChildren().get(0).__getChildren().get(0);
    }
    private static SceneNode memberPrimary(SceneNode row) {
        return memberPrimaryBox(row).__getChildren().get(0);
    }
    private static SceneNode memberSecondaryBox(SceneNode row) {
        return memberInfoColumn(row).__getChildren().get(1).__getChildren().get(0);
    }
    private static SceneNode memberSecondary(SceneNode row) {
        return memberSecondaryBox(row).__getChildren().get(0);
    }
    private static SceneNode memberBadge(SceneNode row) {
        return memberInfoColumn(row).__getChildren().get(1).__getChildren().get(1);
    }
    private static SceneNode memberAction(SceneNode row, int index) {
        return visibleActions(row).__getChildren().get(index).__getChildren().get(0);
    }

    private static List<String> texts(SceneNode node) {
        List<String> values = new ArrayList<String>();
        if (node.getText() != null && !node.getText().isEmpty()) values.add(node.getText());
        for (SceneNode child : node.__getChildren()) values.addAll(texts(child));
        return values;
    }

    private static int visibleRowCount(SceneNode node) {
        int count = 0;
        for (SceneNode child : node.__getChildren()) {
            if (child.getPreferredHeight() == 34 || child.getPreferredHeight() == 42) count++;
        }
        return count;
    }

    private static boolean allRowsFitViewport(SceneNode viewport, SceneNode rows) {
        LayoutBox viewportBox = (LayoutBox) viewport.getCachedLayout();
        LayoutBox rowsBox = (LayoutBox) rows.getCachedLayout();
        int top = viewport.getScrollOffsetY();
        int bottom = top + viewportBox.getHeight();
        for (SceneNode row : rows.__getChildren()) {
            LayoutBox rowBox = (LayoutBox) row.getCachedLayout();
            int rowTop = rowsBox.getY() + rowBox.getY();
            if (rowTop < top || rowTop + rowBox.getHeight() > bottom) return false;
        }
        return true;
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

    private static SearchPickerData.CurrentMember member(long memberId, SearchPickerData.Candidate candidate) {
        return new SearchPickerData.CurrentMember(memberId, new SearchPickerData.Selection(candidate.key(),
                SearchPickerData.SelectionMode.ALL, Collections.<String>emptyList()), candidate, true);
    }

    private static List<Long> memberIds(List<SearchPickerData.CurrentMember> members) {
        ArrayList<Long> ids = new ArrayList<Long>();
        for (SearchPickerData.CurrentMember member : members) ids.add(Long.valueOf(member.memberId()));
        return ids;
    }

    private static SearchPickerData.Candidate candidate(String key, String label) {
        return new SearchPickerData.Candidate(key, label, Collections.<SearchPickerData.Variant>emptyList());
    }
}
