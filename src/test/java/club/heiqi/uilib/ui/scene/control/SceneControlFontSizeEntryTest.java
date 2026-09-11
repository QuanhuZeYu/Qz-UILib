package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
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
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintResult;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
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
    /** 显式声明一层以切断继承的子节点文本。 */
    private static final String ISOLATED_TEXT = "isolated";
    /** 分段/页签共用标签集（长度 3，便于逐段断言宽度）。 */
    private static final List<String> TAB_LABELS = Arrays.asList("Day", "Week", "Month");
    /** 页签内容构建器：与标签同长同序，各页一个空 panel。 */
    private static final List<Supplier<SceneNode>> TAB_PANELS = Arrays.<Supplier<SceneNode>>asList(
            () -> new SceneNode(), () -> new SceneNode(), () -> new SceneNode());
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
     * 作用域边界（S2/S3 新语义，取代旧「不做子树继承」裁定）：<b>字号随节点树沿父链就近竞争</b>——
     * 业务方 {@code appendChild} 进来的子节点默认继承最近声明；需要「不被祖先作用域影响」时，
     * 在该子节点<b>显式声明一层</b>（层 1 显式值或层 2 作用域），而不是「取消继承」。
     *
     * <p>两半都必须能证伪：改坏继承 ⇒ 第一半红；改坏「同节点声明优先于祖先」⇒ 第二半红。</p>
     */
    @Test
    public void businessChildInheritsParentScopeUnlessExplicitlyDeclared() {
        Fixture fixture = fixture();
        fixture.mountPx(Control.BUTTON, 28);

        SceneNode inherited = new SceneNode();
        inherited.setText(CHILD_TEXT);
        fixture.mount.getRoot().appendChild(inherited);

        SceneNode isolated = new SceneNode();
        isolated.setText(ISOLATED_TEXT);
        // 显式声明一层（层 1）⇒ 该子树不再跟随祖先声明。
        // 钉住「显式即声明」：未声明节点写 16 也必须构成声明（旧「按框架默认值同值早退」语义已删除）。
        isolated.setFontSize(DEFAULT_FONT_SIZE);
        fixture.mount.getRoot().appendChild(isolated);

        fixture.frame();
        assertPaintedFontSize(fixture, BUTTON_LABEL, 28);
        assertPaintedFontSize(fixture, CHILD_TEXT, 28);                    // 继承生效
        assertPaintedFontSize(fixture, ISOLATED_TEXT, DEFAULT_FONT_SIZE);  // 显式声明可切断
    }

    /**
     * 统一入口：{@link MountHandle#fontSize(int)} 对任何控件都生效——编写者不必知道控件内部结构，
     * 十六个控件写法相同（这是「字号是节点属性、在基类做一次」的口径）。
     */
    @Test
    public void mountHandleFontSizeCoversEveryControl() {
        for (Control control : Control.values()) {
            Fixture fixture = fixture();
            fixture.mountDefault(control);
            fixture.frame();
            assertPaintedFontSize(fixture, DEFAULT_FONT_SIZE);
            fixture.mount.fontSize(26);
            fixture.frame();
            assertPaintedFontSize(fixture, 26);
        }
    }

    /** 统一入口的运行时可调形态：句柄上接信号，变化同帧生效且随组件卸载退订。 */
    @Test
    public void mountHandleFontSizeSignalDrivesRuntimeChange() {
        for (Control control : Control.values()) {
            Fixture fixture = fixture();
            fixture.mountDefault(control);
            fixture.frame();
            Signal<Integer> fontSize = Signal.create(Integer.valueOf(18));
            fixture.mount.fontSize(fontSize);
            fixture.frame();
            assertPaintedFontSize(fixture, 18);
            fontSize.set(Integer.valueOf(30));
            fixture.frame();
            assertPaintedFontSize(fixture, 30);
        }
    }

    /**
     * root 是字号唯一声明点：绕过所有 Props 入口、直接设控件根字号，控件内文字同样跟随。
     *
     * <p>现口径（S5 起）：入口写的是控件根的**层 2 声明**（{@code setFontScope}）；唯一取值点是
     * {@code effectiveFontSize()}（= {@code getFontSize()}，已含用户倍率），沿父链就近竞争解析
     * （显式值 &gt; 最近作用域 &gt; runtime/环境默认 &gt; 节点回落）。控件若另给构建期入口，
     * 写的仍是同一个 root 声明，不存在第二套真值。</p>
     */
    @Test
    public void rootFontSizeIsTheSingleSourceOfTruthForEveryControl() {
        for (Control control : Control.values()) {
            Fixture fixture = fixture();
            fixture.mountDefault(control);
            fixture.frame();
            assertPaintedFontSize(fixture, DEFAULT_FONT_SIZE);
            fixture.mount.getRoot().setFontSize(24);
            fixture.frame();
            assertPaintedFontSize(fixture, 24);
        }
    }

    /** 直接设 root 字号时，Segmented 按字号算出来的几何也要跟上（段宽与条高）。 */
    @Test
    public void segmentedGeometryFollowsRootFontSizeSetDirectly() {
        Fixture fixture = fixture();
        fixture.mountDefault(Control.SEGMENTED);
        fixture.frame();
        assertSegmentWidths(fixture, DEFAULT_FONT_SIZE);
        fixture.mount.getRoot().setFontSize(20);
        fixture.frame();
        assertPaintedFontSize(fixture, 20);
        assertSegmentWidths(fixture, 20);
        assertSegmentedBarHeight(fixture, 20);
    }

    /** 直接设 root 字号时，fill 模式 Tab 的显式条高也要跟上。 */
    @Test
    public void tabBarHeightFollowsRootFontSizeSetDirectly() {
        Fixture fixture = fixture();
        fixture.mountTabFill(null);
        fixture.frame();
        SceneNode tabBar = fixture.mount.getRoot().__getChildren().get(0);
        fixture.mount.getRoot().setFontSize(28);
        fixture.frame();
        assertPaintedFontSize(fixture, 28);
        Assert.assertEquals("直接设 root 字号后条高必须重算",
                lineHeight(28) + 2 * SceneChromeTokens.PAD_LG, tabBar.getPreferredHeight());
    }

    /**
     * 字号参与几何的控件（Segmented）：段宽 = 按字号测出的文本宽 + 2*内边距。
     *
     * <p>构建期定值与运行期信号都必须重算段宽——只改标签字号不改段宽会让文字溢出或留白。</p>
     */
    @Test
    public void segmentedSegmentWidthFollowsConfiguredAndRuntimeFontSize() {
        Fixture built = fixture();
        built.mountPx(Control.SEGMENTED, 24);
        built.frame();
        assertPaintedFontSize(built, 24);
        assertSegmentWidths(built, 24);

        Fixture runtime = fixture();
        Signal<Integer> fontSize = Signal.create(Integer.valueOf(DEFAULT_FONT_SIZE));
        runtime.mountSignal(Control.SEGMENTED, fontSize);
        runtime.frame();
        assertSegmentWidths(runtime, DEFAULT_FONT_SIZE);
        fontSize.set(Integer.valueOf(12));
        runtime.frame();
        assertPaintedFontSize(runtime, 12);
        assertSegmentWidths(runtime, 12);
    }

    /** Segmented 条高同样按字号算：root.preferredHeight = lineHeight(字号) + 2*内边距。 */
    @Test
    public void segmentedBarHeightFollowsRuntimeFontSize() {
        Fixture fixture = fixture();
        Signal<Integer> fontSize = Signal.create(Integer.valueOf(DEFAULT_FONT_SIZE));
        fixture.mountSignal(Control.SEGMENTED, fontSize);
        fixture.frame();
        assertSegmentedBarHeight(fixture, DEFAULT_FONT_SIZE);
        fontSize.set(Integer.valueOf(28));
        fixture.frame();
        assertSegmentedBarHeight(fixture, 28);
    }

    /** 页签标签字号：默认（非 fill）模式条高按内容自然高收缩，标签字号仍须跟随。 */
    @Test
    public void tabLabelFontSizeFollowsRuntimeSignal() {
        Fixture fixture = fixture();
        Signal<Integer> fontSize = Signal.create(Integer.valueOf(DEFAULT_FONT_SIZE));
        fixture.mountSignal(Control.TAB, fontSize);
        fixture.frame();
        assertPaintedFontSize(fixture, DEFAULT_FONT_SIZE);
        fontSize.set(Integer.valueOf(24));
        fixture.frame();
        assertPaintedFontSize(fixture, 24);
    }

    /** fill 模式页签：条高是显式 preferredHeight，必须随字号重算，否则标签会溢出导航条。 */
    @Test
    public void tabBarHeightFollowsRuntimeFontSizeInFillMode() {
        Fixture fixture = fixture();
        Signal<Integer> fontSize = Signal.create(Integer.valueOf(DEFAULT_FONT_SIZE));
        fixture.mountTabFill(fontSize);
        fixture.frame();
        SceneNode tabBar = fixture.mount.getRoot().__getChildren().get(0);
        Assert.assertEquals("fill 模式条高 = lineHeight(16) + 2*内边距",
                lineHeight(DEFAULT_FONT_SIZE) + 2 * SceneChromeTokens.PAD_LG,
                tabBar.getPreferredHeight());
        fontSize.set(Integer.valueOf(28));
        fixture.frame();
        assertPaintedFontSize(fixture, 28);
        Assert.assertEquals("字号变化后条高必须重算",
                lineHeight(28) + 2 * SceneChromeTokens.PAD_LG, tabBar.getPreferredHeight());
    }

    /**
     * Tab 段宽 = max(最小宽 72, 文本宽 + 2*内边距)：短标签保持等宽节奏，长标签随字号变宽。
     *
     * <p>这是 ③ 类「显式尺寸与字号无关」的收口——调大字号不再被固定段宽截断。</p>
     */
    @Test
    public void tabSegmentWidthFollowsFontSize() {
        Fixture small = fixture();
        small.mountPx(Control.TAB, DEFAULT_FONT_SIZE);
        small.frame();
        // 度量器口径：文本宽 = 码点数 * 字号 / 2。字号 16 时最长标签也只有 64，全部落在最小宽上。
        assertTabSegmentWidths(small, DEFAULT_FONT_SIZE);

        Fixture large = fixture();
        large.mountPx(Control.TAB, 32);
        large.frame();
        assertTabSegmentWidths(large, 32);
        // 大字号下必须真的测到「长标签撑开」，否则本用例失去意义。
        List<SceneNode> segments = large.mount.getRoot().__getChildren().get(0).__getChildren();
        Assert.assertTrue("大字号下应有段宽超过最小宽",
                ((LayoutBox) segments.get(2).getCachedLayout()).getWidth() > TAB_MIN_WIDTH);
    }

    /** {@code SceneTab.TAB_WIDTH}：段最小宽（控件内私有常量，此处按其语义断言）。 */
    private static final int TAB_MIN_WIDTH = 72;

    private static void assertTabSegmentWidths(Fixture fixture, int fontSize) {
        SceneNode tabBar = fixture.mount.getRoot().__getChildren().get(0);
        List<SceneNode> segments = tabBar.__getChildren();
        Assert.assertEquals("段数应与标签数一致", TAB_LABELS.size(), segments.size());
        for (int i = 0; i < segments.size(); i++) {
            String title = TAB_LABELS.get(i);
            int textWidth = title.codePointCount(0, title.length()) * fontSize / 2;
            int expected = Math.max(TAB_MIN_WIDTH, textWidth + 2 * SceneChromeTokens.PAD_LG);
            // S5 收口：段宽改 SHRINK 派生（布局阶段按生效字号测自然宽），断言改读 LayoutBox。
            Assert.assertEquals("段[" + i + "] 宽（文本宽 " + textWidth + "，字号 " + fontSize + "）",
                    expected, ((LayoutBox) segments.get(i).getCachedLayout()).getWidth());
        }
    }

    private static void assertSegmentWidths(Fixture fixture, int fontSize) {
        List<SceneNode> segments = fixture.mount.getRoot().__getChildren();
        Assert.assertEquals("段数应与标签数一致", TAB_LABELS.size(), segments.size());
        for (int i = 0; i < segments.size(); i++) {
            String title = TAB_LABELS.get(i);
            int textWidth = title.codePointCount(0, title.length()) * fontSize / 2;
            Assert.assertEquals("段[" + i + "]宽 = 按字号 " + fontSize + " 测出的文本宽 + 2*内边距",
                    textWidth + 2 * SceneChromeTokens.PAD_LG,
                    ((LayoutBox) segments.get(i).getCachedLayout()).getWidth());
        }
    }

    private static void assertSegmentedBarHeight(Fixture fixture, int fontSize) {
        Assert.assertEquals("条高 = lineHeight(" + fontSize + ") + 2*内边距",
                lineHeight(fontSize) + 2 * SceneChromeTokens.PAD_LG,
                fixture.mount.getRoot().getPreferredHeight());
    }

    /** 夹具内 FontMeasurer 的行高口径：与字号同值。 */
    private static int lineHeight(int fontSizePx) {
        return fontSizePx;
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

    private enum Control { BUTTON, INPUT, AREA, SEGMENTED, TAB, LABEL }

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
        final Signal<Integer> selected = Signal.create(Integer.valueOf(0));
        final Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
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
                case SEGMENTED:
                    component = SceneSegmented.create(runtime, new SceneSegmented.Props(
                            selected, TAB_LABELS, enabled, index -> { },
                            sizeSignal(fontSizePx, fontSize)));
                    break;
                case TAB:
                    component = SceneTab.create(runtime, new SceneTab.Props(
                            selected, TAB_LABELS, TAB_PANELS, enabled, index -> { }, false,
                            sizeSignal(fontSizePx, fontSize)));
                    break;
                case LABEL:
                    SceneLabel.Builder labelBuilder = SceneLabel.Props.builder(value);
                    if (fontSizePx != null) {
                        labelBuilder.fontSizePx(fontSizePx.intValue());
                    }
                    if (fontSize != null) {
                        labelBuilder.fontSize(fontSize);
                    }
                    component = SceneLabel.create(runtime, labelBuilder.build());
                    break;
                default:
                    throw new AssertionError(kind);
            }
            doMount(component);
        }

        /** fill 模式页签：条高是显式 preferredHeight，必须随字号重算。 */
        void mountTabFill(ReadableSignal<Integer> fontSize) {
            doMount(SceneTab.create(runtime, new SceneTab.Props(
                    selected, TAB_LABELS, TAB_PANELS, enabled, index -> { }, true, fontSize)));
        }

        private void doMount(Supplier<SceneNode> component) {
            mount = runtime.mount(scene, component);
        }

        /** Segmented/Tab 无 Builder：构建期定值包成常量信号，与信号路径走同一入口。 */
        private static ReadableSignal<Integer> sizeSignal(Integer fontSizePx,
                ReadableSignal<Integer> fontSize) {
            return fontSizePx != null ? Signal.create(fontSizePx) : fontSize;
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
