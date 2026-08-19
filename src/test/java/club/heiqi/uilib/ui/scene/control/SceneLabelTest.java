package club.heiqi.uilib.ui.scene.control;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.TextStyle;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneLabel 通用文本组件测试：属性装配、富文本模式透传、信号驱动文本更新。
 */
public class SceneLabelTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private Signal<String> textSignal;
    private SceneNode labelRoot;

    private static final int CANVAS_WIDTH = 200;
    private static final int CANVAS_HEIGHT = 100;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        SceneInteractionHarness harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    private void mountLabel(SceneLabel.Props props) {
        MountHandle handle = runtime.mount(sceneRoot, SceneLabel.create(runtime, props));
        labelRoot = handle.getRoot();
        runtime.flush();
    }

    private PaintPlan frame() {
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        return paintEngine.paint(sceneRoot).getPlan();
    }

    private static PaintCommand firstTextCommand(PaintPlan plan) {
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.TEXT) {
                return command;
            }
        }
        return null;
    }

    @Test
    public void shouldAssemblePlainTextNode() {
        textSignal = Signal.create("Hi");
        mountLabel(new SceneLabel.Props(textSignal));

        Assert.assertFalse(labelRoot.isHitTestable());
        Assert.assertEquals(SceneChromeTokens.TEXT_PRIMARY, labelRoot.getTextColor());
        Assert.assertEquals(SceneLabel.DEFAULT_FONT_SIZE_PX, labelRoot.getFontSize());
        Assert.assertEquals(TextStyle.TEXT_MODE_UILIB_RAW, labelRoot.getTextContentMode());
        Assert.assertEquals(0, labelRoot.getMaxTextWidth());
        Assert.assertEquals(TextHorizontalAlign.LEFT, labelRoot.getTextHorizontalAlign());
        Assert.assertEquals(TextVerticalAlign.TOP, labelRoot.getTextVerticalAlign());
        Assert.assertEquals("Hi", labelRoot.getText());

        PaintCommand text = firstTextCommand(frame());
        Assert.assertNotNull(text);
        Assert.assertEquals("Hi", text.getText());
        Assert.assertEquals(TextStyle.TEXT_MODE_UILIB_RAW, text.getTextStyle().getTextMode());
    }

    @Test
    public void shouldAssembleRichTextNodeWithWrap() {
        textSignal = Signal.create("<color=red>富<b>文本</b></color>");
        mountLabel(new SceneLabel.Props(textSignal, 0xFFFFFFFF, 18, TextStyle.TEXT_MODE_RICH_TAGS,
                TextHorizontalAlign.CENTER, TextVerticalAlign.CENTER, 80, 0.0D, 0, 0, false, null));

        Assert.assertEquals(0xFFFFFFFF, labelRoot.getTextColor());
        Assert.assertEquals(18, labelRoot.getFontSize());
        Assert.assertEquals(TextStyle.TEXT_MODE_RICH_TAGS, labelRoot.getTextContentMode());
        Assert.assertEquals(80, labelRoot.getMaxTextWidth());
        Assert.assertEquals(TextHorizontalAlign.CENTER, labelRoot.getTextHorizontalAlign());
        Assert.assertEquals(TextVerticalAlign.CENTER, labelRoot.getTextVerticalAlign());

        PaintCommand text = firstTextCommand(frame());
        Assert.assertNotNull(text);
        Assert.assertEquals("<color=red>富<b>文本</b></color>", text.getText());
        Assert.assertEquals(TextStyle.TEXT_MODE_RICH_TAGS, text.getTextStyle().getTextMode());
        Assert.assertEquals(18, text.getTextStyle().getFontSize());
    }

    @Test
    public void shouldUpdateTextOnSignalChange() {
        textSignal = Signal.create("before");
        mountLabel(new SceneLabel.Props(textSignal));

        textSignal.set("after");
        runtime.flush();

        Assert.assertEquals("after", labelRoot.getText());
        PaintCommand text = firstTextCommand(frame());
        Assert.assertNotNull(text);
        Assert.assertEquals("after", text.getText());
    }

    @Test
    public void shouldKeepImmutabilityByConstruction() {
        textSignal = Signal.create("x");
        SceneLabel.Props props = new SceneLabel.Props(textSignal);
        mountLabel(props);
        List<SceneNode> children = labelRoot.__getChildren();

        // 组件只建单节点树（无子节点），样式全部落在根节点
        Assert.assertEquals(0, children.size());
    }

    @Test
    public void shouldApplyLineHeightPropsToNode() {
        textSignal = Signal.create("x");
        SceneLabel.Props props = new SceneLabel.Props(textSignal, 0xFFFFFFFF, 16,
                TextStyle.TEXT_MODE_UILIB_RAW, TextHorizontalAlign.LEFT, TextVerticalAlign.TOP,
                0, 1.5D, 24, 0, false, null);
        mountLabel(props);

        Assert.assertEquals(1.5D, labelRoot.getLineHeightMultiplier(), 0.001D);
        Assert.assertEquals(24, labelRoot.getLineHeightPx());
    }

    @Test
    public void shouldApplyMaxLinesAndEllipsisPropsToNode() {
        textSignal = Signal.create("x");
        SceneLabel.Props props = new SceneLabel.Props(textSignal, 0xFFFFFFFF, 16,
                TextStyle.TEXT_MODE_UILIB_RAW, TextHorizontalAlign.LEFT, TextVerticalAlign.TOP,
                320, 0.0D, 0, 3, true, null);
        mountLabel(props);

        Assert.assertEquals(3, labelRoot.getMaxLines());
        Assert.assertTrue(labelRoot.isEllipsis());
    }
}
