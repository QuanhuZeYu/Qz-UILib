package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.SearchPickerData;
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
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
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
        VisualAdapter adapter = new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
            public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
            public SceneImageSource candidateImage(SearchPickerData.Candidate value) {
                return "stone".equals(value.key()) ? image : null;
            }
        };
        runtime.mount(sceneRoot, SceneSearchPicker.create(runtime, new SceneSearchPicker.Props(
                query, results, enabled, value -> lastQuery = value, value -> selection = value, adapter)));
        runtime.flush();
        input = sceneRoot.__getChildren().get(0).__getChildren().get(0);
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
        harness.click(items().__getChildren().get(0));
        Assert.assertEquals("stone", selection.candidateKey());
        Assert.assertEquals("smooth", selection.variantKey());
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());
    }

    /** 键盘仅处理 PRESSED；repeat 不重复移动，Enter 提交，Escape 关闭。 */
    @Test
    public void keyboardRepeatAndEscape() {
        doLayout();
        runtime.requestFocus(input);
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        key(SceneKey.ARROW_DOWN, SceneKeyAction.REPEATED);
        key(SceneKey.ENTER, SceneKeyAction.PRESSED);
        Assert.assertEquals("dirt", selection.candidateKey());
        key(SceneKey.ARROW_DOWN, SceneKeyAction.PRESSED);
        Assert.assertFalse(runtime.getOverlayHost().isEmpty());
        key(SceneKey.ESCAPE, SceneKeyAction.PRESSED);
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());
    }

    /** 外部 DOWN dismiss；截断提示显示；禁用时不打开。 */
    @Test
    public void dismissTruncatedAndDisabled() {
        open();
        harness.pressAt(319, 239);
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());
        ArrayList<SearchPickerData.Candidate> many = new ArrayList<SearchPickerData.Candidate>();
        for (int i = 0; i < 65; i++) many.add(candidate("k" + i, "V" + i));
        results.set(new SearchPickerData.SearchResult(many));
        runtime.flush();
        open();
        Assert.assertEquals(64, items().__getChildren().size());
        SceneNode footer = portal().__getChildren().get(1);
        Assert.assertEquals("Results truncated", footer.__getChildren().get(0).getText());
        runtime.getOverlayHost().bottomFirst().get(0).requestDismiss();
        runtime.flush();
        enabled.set(Boolean.FALSE);
        runtime.flush();
        harness.click(input);
        Assert.assertTrue(runtime.getOverlayHost().isEmpty());
        Assert.assertNull(lastQuery);
    }

    private static SearchPickerData.SearchResult result(SearchPickerData.Candidate... values) {
        return new SearchPickerData.SearchResult(Arrays.asList(values));
    }

    private static SearchPickerData.Candidate candidate(String key, String label) {
        return new SearchPickerData.Candidate(key, label, Collections.<SearchPickerData.Variant>emptyList());
    }
}
