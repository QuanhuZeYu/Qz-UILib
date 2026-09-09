package club.heiqi.uilib.ui.scene.control;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
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
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneLabel 通用文本组件测试：属性装配、富文本模式透传、信号驱动文本更新。
 *
 * <p>前景语义（契约 §2.7）：无颜色参数的普通调用跟随来源主题；显式 color 继续生效，
 * 含色值恰好等于旧 {@link SceneChromeTokens#TEXT_PRIMARY} 的情况。</p>
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

    /** 在局部主题作用域内挂载标签：验证来源主题继承与主题信号切换。 */
    private void mountLabelInTheme(SceneLabel.Props props, ReadableSignal<SceneTheme> theme) {
        runtime.mount(sceneRoot, () -> {
            SceneNode host = new SceneNode();
            SceneThemes.withTheme(theme, () ->
                    labelRoot = runtime.mount(host, SceneLabel.create(runtime, props)).getRoot());
            return host;
        });
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
        // 默认路径前景跟随当前主题（库默认为深色液态玻璃档），不再是写死的 TEXT_PRIMARY 静态写入
        Assert.assertEquals("默认 Label 前景跟随库默认主题",
                SceneThemes.DEFAULT.foreground(), labelRoot.getTextColor());
        Assert.assertEquals("库默认正文色与旧默认 token 同值（默认外观数值不变）",
                SceneChromeTokens.TEXT_PRIMARY, labelRoot.getTextColor());
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
    public void builderShouldProduceEquivalentPropsAndDelegatedAccessors() {
        textSignal = Signal.create("builder");
        SceneLabel.Props built = SceneLabel.Props.builder(textSignal)
                .color(0xFF00FF00)
                .fontSizePx(22)
                .contentMode(TextStyle.TEXT_MODE_RICH_TAGS)
                .horizontalAlign(TextHorizontalAlign.CENTER)
                .verticalAlign(TextVerticalAlign.BOTTOM)
                .wrapWidth(120)
                .lineHeightMultiplier(1.4D)
                .lineHeightPx(30)
                .maxLines(2)
                .ellipsis(true)
                .onLinkClick(url -> { })
                .build();
        SceneLabel.Props flat = new SceneLabel.Props(textSignal, 0xFF00FF00, 22,
                TextStyle.TEXT_MODE_RICH_TAGS, TextHorizontalAlign.CENTER, TextVerticalAlign.BOTTOM,
                120, 1.4D, 30, 2, true, url -> { });

        // 历史 accessor 委托分组，与平铺构造器逐字段等价
        Assert.assertEquals(flat.text(), built.text());
        Assert.assertEquals(flat.color(), built.color());
        Assert.assertEquals(flat.fontSizePx(), built.fontSizePx());
        Assert.assertEquals(flat.contentMode(), built.contentMode());
        Assert.assertEquals(flat.horizontalAlign(), built.horizontalAlign());
        Assert.assertEquals(flat.verticalAlign(), built.verticalAlign());
        Assert.assertEquals(flat.wrapWidth(), built.wrapWidth());
        Assert.assertEquals(flat.lineHeightMultiplier(), built.lineHeightMultiplier(), 0.001D);
        Assert.assertEquals(flat.lineHeightPx(), built.lineHeightPx());
        Assert.assertEquals(flat.maxLines(), built.maxLines());
        Assert.assertEquals(flat.ellipsis(), built.ellipsis());

        // 分组 accessor 与历史 accessor 一致
        Assert.assertEquals(built.color(), built.textSpec().color());
        Assert.assertEquals(built.wrapWidth(), built.layoutSpec().wrapWidth());
        Assert.assertEquals(built.horizontalAlign(), built.alignSpec().horizontalAlign());
        Assert.assertEquals(0, built.layoutSpec().maxLines() - built.maxLines());
    }

    @Test
    public void builderDefaultsShouldMatchSingleArgConstructor() {
        textSignal = Signal.create("defaults");
        SceneLabel.Props built = SceneLabel.Props.builder(textSignal).build();
        SceneLabel.Props single = new SceneLabel.Props(textSignal);

        Assert.assertEquals(single.color(), built.color());
        Assert.assertEquals(single.fontSizePx(), built.fontSizePx());
        Assert.assertEquals(single.contentMode(), built.contentMode());
        Assert.assertEquals(single.horizontalAlign(), built.horizontalAlign());
        Assert.assertEquals(single.verticalAlign(), built.verticalAlign());
        Assert.assertEquals(single.wrapWidth(), built.wrapWidth());
        Assert.assertEquals(single.lineHeightMultiplier(), built.lineHeightMultiplier(), 0.001D);
        Assert.assertEquals(single.maxLines(), built.maxLines());
        Assert.assertEquals(single.ellipsis(), built.ellipsis());
        Assert.assertEquals("未指定 color 的 builder 与单参构造器同语义（跟随主题）",
                single.textSpec().followTheme(), built.textSpec().followTheme());
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

    /** 默认路径（未传颜色参数）前景跟随来源主题；切换深色/浅色/局部主题后更新，且不追加玻璃。 */
    @Test
    public void defaultLabelFollowsThemeForeground() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        textSignal = Signal.create("theme");
        mountLabelInTheme(new SceneLabel.Props(textSignal), pageTheme);

        Assert.assertEquals("默认 Label 前景跟随来源主题（深色档）",
                SceneTheme.liquidGlassDark().foreground(), labelRoot.getTextColor());

        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("切浅色档后前景更新",
                SceneTheme.liquidGlassLight().foreground(), labelRoot.getTextColor());

        pageTheme.set(SceneTheme.builder().foreground(0xFF00FF00).build());
        runtime.flush();
        Assert.assertEquals("局部主题前景覆盖", 0xFF00FF00, labelRoot.getTextColor());

        PaintPlan plan = frame();
        PaintCommand text = firstTextCommand(plan);
        Assert.assertNotNull(text);
        Assert.assertEquals("绘制前景跟随主题", 0xFF00FF00, text.getTextStyle().getColor());
        Assert.assertEquals("文本不变", "theme", text.getText());
        Assert.assertEquals("节点结构不变（单节点）", 0, labelRoot.__getChildren().size());
        Assert.assertNull("Label 不参与表面绑定", labelRoot.getBackdrop());
        for (PaintCommand command : plan.getCommands()) {
            Assert.assertNotEquals("Label 不追加玻璃", PaintCommandType.BACKDROP, command.getType());
        }
    }

    /** 显式传入与旧默认相同的色值也必须保持显式，不得被主题覆盖。 */
    @Test
    public void explicitLegacyDefaultColorStaysExplicitUnderTheme() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassLight());
        textSignal = Signal.create("legacy");
        mountLabelInTheme(new SceneLabel.Props(textSignal, SceneChromeTokens.TEXT_PRIMARY, 16), pageTheme);

        Assert.assertEquals("显式旧默认色保持显式",
                SceneChromeTokens.TEXT_PRIMARY, labelRoot.getTextColor());

        pageTheme.set(SceneTheme.builder().foreground(0xFF00FF00).build());
        runtime.flush();
        Assert.assertEquals("主题更新不覆盖显式 color",
                SceneChromeTokens.TEXT_PRIMARY, labelRoot.getTextColor());
    }

    /** 显式任意颜色保持；富文本内部显式颜色与解析原文不被主题改写。 */
    @Test
    public void explicitColorAndRichTextInnerColorStayExplicitUnderTheme() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassLight());
        textSignal = Signal.create("<color=#FF5533>红</color>默认");
        mountLabelInTheme(new SceneLabel.Props(textSignal, 0xFF00FF00, 16,
                TextStyle.TEXT_MODE_RICH_TAGS), pageTheme);

        Assert.assertEquals("显式任意色保持", 0xFF00FF00, labelRoot.getTextColor());

        pageTheme.set(SceneTheme.builder().foreground(0xFF0000FF).build());
        runtime.flush();
        Assert.assertEquals("主题更新不影响显式 color", 0xFF00FF00, labelRoot.getTextColor());

        PaintCommand text = firstTextCommand(frame());
        Assert.assertNotNull(text);
        Assert.assertEquals("富文本原文与内部显式颜色不被改写",
                "<color=#FF5533>红</color>默认", text.getText());
        Assert.assertEquals("富文本模式不变",
                TextStyle.TEXT_MODE_RICH_TAGS, text.getTextStyle().getTextMode());
        Assert.assertEquals("基线前景仍是显式 color",
                0xFF00FF00, text.getTextStyle().getColor());
    }

    /** 卸载回收主题前景绑定，卸载后主题更新不再写入旧节点。 */
    @Test
    public void unmountReleasesThemeForegroundBinding() {
        Signal<SceneTheme> installed = Signal.create(SceneTheme.liquidGlassDark());
        SceneThemes.install(runtime, installed);
        textSignal = Signal.create("x");
        int before = ReactiveTestProbe.registeredEffectCount();

        MountHandle handle = runtime.mount(sceneRoot,
                SceneLabel.create(runtime, new SceneLabel.Props(textSignal)));
        labelRoot = handle.getRoot();
        runtime.flush();
        Assert.assertTrue("默认 Label 应注册响应式前景绑定",
                ReactiveTestProbe.registeredEffectCount() > before);

        handle.dispose();
        Assert.assertEquals("卸载后前景绑定 effect 应回收",
                before, ReactiveTestProbe.registeredEffectCount());

        installed.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧节点",
                SceneTheme.liquidGlassDark().foreground(), labelRoot.getTextColor());
    }
}
