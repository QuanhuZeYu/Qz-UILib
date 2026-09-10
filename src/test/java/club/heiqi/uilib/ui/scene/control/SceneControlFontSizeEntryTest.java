package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.input.mock.MockPlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintResult;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * 控件级字号入口：编写者通过 API 调整控件内文字大小（规划-控件字号.md 批 1）。
 *
 * <p>断言取自真实帧的 PaintCommand 字号，不读节点内部字段——入口若只改了 root 却没
 * 传到已绘制文本上，本组用例必须红。度量器按字号算宽，字号错误不能被固定宽高掩盖。</p>
 *
 * <p>三条边界在此钉死：① 不传字号时视觉零变化（沿用 {@link SceneNode} 默认字号）；
 * ② 运行期信号变化与构建期定值同帧生效；③ 字号只作用于控件自己画的文字，不进业务端子控件。</p>
 */
public class SceneControlFontSizeEntryTest {

    /** {@link SceneNode} 默认字号；不传字号的调用方必须保持此值。 */
    private static final int DEFAULT_FONT_SIZE = 16;
    private static final String BUTTON_LABEL = "Button";
    private static final String INPUT_TEXT = "abcdef";
    private static final String CHILD_TEXT = "child";
    private static final Runnable NOOP = new Runnable() {
        @Override
        public void run() {
        }
    };

    private final List<Fixture> fixtures = new ArrayList<Fixture>();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        for (Fixture fixture : fixtures) {
            fixture.harness.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    /** 构建期定值：首帧就按给定字号绘制，不靠后续帧补正。 */
    @Test
    public void fontSizePxAppliesFromFirstPaintForEveryControl() {
        for (Control control : Control.values()) {
            Fixture fixture = fixture();
            fixture.mountPx(control, 28);
            fixture.frame();
            assertPaintedFontSize(fixture, 28);
        }
    }

    /** 运行期可调：信号变化后同帧内文字跟随，无需调用方手动标脏。 */
    @Test
    public void fontSizeSignalDrivesRuntimeChangeForEveryControl() {
        for (Control control : Control.values()) {
            Fixture fixture = fixture();
            Signal<Integer> fontSize = Signal.create(Integer.valueOf(DEFAULT_FONT_SIZE));
            fixture.mountSignal(control, fontSize);
            fixture.frame();
            assertPaintedFontSize(fixture, DEFAULT_FONT_SIZE);
            for (int expected : new int[]{24, 12, 20}) {
                fontSize.set(Integer.valueOf(expected));
                fixture.frame();
                assertPaintedFontSize(fixture, expected);
            }
        }
    }

    /** 不传字号：沿用节点默认字号，视觉零变化（本规划的硬约束）。 */
    @Test
    public void absentFontSizeKeepsNodeDefaultForEveryControl() {
        for (Control control : Control.values()) {
            Fixture fixture = fixture();
            fixture.mountDefault(control);
            fixture.frame();
            assertPaintedFontSize(fixture, DEFAULT_FONT_SIZE);
        }
    }

    /**
     * 作用域边界：控件字号只覆盖自己画的文字，业务方塞进来的子控件是独立控件。
     *
     * <p>与「不做子树继承」的裁定同源：父容器改字号不得改掉子控件的字号。</p>
     */
    @Test
    public void controlFontSizeDoesNotLeakIntoBusinessChild() {
        Fixture fixture = fixture();
        fixture.mountPx(Control.BUTTON, 28);
        SceneNode child = new SceneNode();
        child.setText(CHILD_TEXT);
        fixture.mount.getRoot().appendChild(child);
        fixture.frame();
        assertPaintedFontSize(fixture, BUTTON_LABEL, 28);
        assertPaintedFontSize(fixture, CHILD_TEXT, DEFAULT_FONT_SIZE);
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixtures.add(fixture);
        return fixture;
    }

    private static void assertPaintedFontSize(Fixture fixture, int fontSize) {
        List<PaintCommand> texts = textCommands(fixture);
        Assert.assertFalse("必须产生 TEXT 命令", texts.isEmpty());
        for (PaintCommand text : texts) {
            Assert.assertEquals("所有已绘制片段使用控件字号：" + text.getText(), fontSize,
                    text.getTextStyle().getFontSize());
        }
    }

    private static void assertPaintedFontSize(Fixture fixture, String expectedText, int fontSize) {
        for (PaintCommand text : textCommands(fixture)) {
            if (expectedText.equals(text.getText())) {
                Assert.assertEquals("片段 '" + expectedText + "' 的字号", fontSize,
                        text.getTextStyle().getFontSize());
                return;
            }
        }
        Assert.fail("未绘制出期望片段：" + expectedText);
    }

    private static List<PaintCommand> textCommands(Fixture fixture) {
        List<PaintCommand> result = new ArrayList<PaintCommand>();
        for (PaintCommand command : fixture.paint.commands) {
            if (command.getType() == PaintCommandType.TEXT && !command.getText().isEmpty()) {
                result.add(command);
            }
        }
        return result;
    }

    private enum Control { BUTTON, INPUT, AREA }

    /** 度量随字号变化：字号没传到绘制上时断言必须失败。 */
    private static final class FontMeasurer implements SceneTextMeasurer {

        @Override
        public int measureWidth(String text, int fontSizePx) {
            return text == null ? 0 : text.codePointCount(0, text.length()) * fontSizePx / 2;
        }

        @Override
        public int lineHeight(int fontSizePx) {
            return fontSizePx;
        }

        @Override
        public int epoch() {
            return 0;
        }
    }

    /** 只观察 super.paint 的产物：FramePipeline 仍执行真实 paint/replay。 */
    private static final class CapturingPaintEngine extends ScenePaintEngine {

        List<PaintCommand> commands = new ArrayList<PaintCommand>();

        CapturingPaintEngine(SceneTextMeasurer measurer) {
            super(measurer);
        }

        @Override
        public PaintResult paint(SceneNode root) {
            PaintResult result = super.paint(root);
            commands = new ArrayList<PaintCommand>(result.getPlan().getCommands());
            return result;
        }
    }

    private static final class Fixture {

        final FontMeasurer measurer = new FontMeasurer();
        final SceneInteractionHarness harness;
        final SceneRuntime runtime;
        final SceneNode scene = SceneNode.column();
        final CapturingPaintEngine paint;
        final SceneFramePipeline pipeline;
        final MockPlatformInputSource input;
        final int width = 240;
        final int height = 640;
        Signal<String> value;
        MountHandle mount;

        Fixture() {
            harness = SceneInteractionHarness.create(measurer);
            runtime = harness.getRuntime();
            harness.mountRoot(scene, width, height);
            paint = new CapturingPaintEngine(measurer);
            input = new MockPlatformInputSource(width, height);
            pipeline = new SceneFramePipeline(runtime, new SceneLayoutEngine(measurer), paint,
                    new ScenePaintReplayer(), measurer, input);
            pipeline.__setAssertionsEnabled(true);
        }

        void mountPx(Control kind, int fontSizePx) {
            mount(kind, Integer.valueOf(fontSizePx), null);
        }

        void mountSignal(Control kind, ReadableSignal<Integer> fontSize) {
            mount(kind, null, fontSize);
        }

        void mountDefault(Control kind) {
            mount(kind, null, null);
        }

        private void mount(Control kind, Integer fontSizePx, ReadableSignal<Integer> fontSize) {
            value = Signal.create(kind == Control.BUTTON ? BUTTON_LABEL : INPUT_TEXT);
            Supplier<SceneNode> component;
            switch (kind) {
                case BUTTON:
                    SceneButton.Props.Builder button = SceneButton.Props.builder(value).onClick(NOOP);
                    if (fontSizePx != null) {
                        button.fontSizePx(fontSizePx.intValue());
                    }
                    if (fontSize != null) {
                        button.fontSize(fontSize);
                    }
                    component = SceneButton.create(runtime, button.build());
                    break;
                case INPUT:
                    SceneTextInput.Props.Builder inputBuilder = SceneTextInput.Props.builder(value)
                            .onChange(value::set);
                    if (fontSizePx != null) {
                        inputBuilder.fontSizePx(fontSizePx.intValue());
                    }
                    if (fontSize != null) {
                        inputBuilder.fontSize(fontSize);
                    }
                    component = SceneTextInput.create(runtime, inputBuilder.build());
                    break;
                case AREA:
                    SceneTextArea.Props.Builder areaBuilder = SceneTextArea.Props.builder(value)
                            .onChange(value::set);
                    if (fontSizePx != null) {
                        areaBuilder.fontSizePx(fontSizePx.intValue());
                    }
                    if (fontSize != null) {
                        areaBuilder.fontSize(fontSize);
                    }
                    component = SceneTextArea.create(runtime, areaBuilder.build());
                    break;
                default:
                    throw new AssertionError(kind);
            }
            mount = runtime.mount(scene, component);
        }

        void frame() {
            // harness 的输入时间为 1000ns；使用同一时钟避免测试偶然落在 caret 闪烁关闭区间。
            pipeline.run(scene, width, height, new RecordingRenderBackend(), 0, 0, 1000L);
            Assert.assertFalse("正常帧必须在现有 settle 上限内收敛", pipeline.__isSettleDeferred());
            Assert.assertFalse("paint 前不能残留布局脏标", scene.__isSelfLayoutDirty());
            Assert.assertFalse("paint 前子树布局必须已完成", scene.__isDescendantLayoutDirty());
        }
    }
}
