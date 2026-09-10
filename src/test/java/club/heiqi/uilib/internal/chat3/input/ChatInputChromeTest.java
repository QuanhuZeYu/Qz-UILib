package club.heiqi.uilib.internal.chat3.input;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.MaxLengthUnit;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneTextInputPrimitive;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/** GL-free：真实 primitive 输入路由与聊天外观装配，不加载 Minecraft 宿主。 */
public class ChatInputChromeTest {

    private SceneInteractionHarness harness;
    private SceneRuntime rt;
    private SceneNode scene;
    private Signal<String> value;
    private Signal<Boolean> enabled;
    private SceneTextInputPrimitive.Result input;
    private ChatInputChrome chrome;
    private boolean savedGlass;
    private int savedBlur;
    private float savedLens;
    private int savedAlpha;

    @Before
    public void setUp() {
        savedGlass = ChatMarkdownSettings.isGlassEnabled();
        savedBlur = ChatMarkdownSettings.getGlassBlurRadiusPx();
        savedLens = ChatMarkdownSettings.getGlassLensStrength();
        savedAlpha = ChatMarkdownSettings.getGlassInputAlpha();
        ChatMarkdownSettings.setGlassEnabled(false);
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        rt = harness.getRuntime();
        scene = new SceneNode();
        value = Signal.create("");
        enabled = Signal.create(Boolean.TRUE);
    }

    @After
    public void tearDown() {
        harness.dispose();
        ChatMarkdownSettings.setGlassEnabled(savedGlass);
        ChatMarkdownSettings.setGlassBlurRadiusPx(savedBlur);
        ChatMarkdownSettings.setGlassLensStrength(savedLens);
        ChatMarkdownSettings.setGlassInputAlpha(savedAlpha);
    }

    private void mount() {
        SceneTextInputPrimitive.Props props = new SceneTextInputPrimitive.Props(value, enabled,
                Signal.create(Boolean.FALSE), "输入消息…", 100, SceneInputType.TEXT, value::set,
                MaxLengthUnit.UTF16, "\u00A7");
        input = SceneTextInputPrimitive.create(rt, props);
        chrome = ChatInputChrome.attach(rt, enabled, input);
        scene.appendChild(input.root());
        harness.mountRoot(scene, 400, 100);
        harness.flush();
    }

    /**
     * G17/Input 接缝装配：{@code localStyle} 为 null = 无聊天局部设置（纯通用 INPUT 主题配方）；
     * 非 null = 局部覆盖层。其余与 {@link #mount()} 相同。
     */
    private void mountWithLocalStyle(Supplier<ChatInputChrome.LocalStyle> localStyle) {
        SceneTextInputPrimitive.Props props = new SceneTextInputPrimitive.Props(value, enabled,
                Signal.create(Boolean.FALSE), "输入消息…", 100, SceneInputType.TEXT, value::set,
                MaxLengthUnit.UTF16, "\u00A7");
        input = SceneTextInputPrimitive.create(rt, props);
        chrome = ChatInputChrome.attach(rt, enabled, input, localStyle);
        scene.appendChild(input.root());
        harness.mountRoot(scene, 400, 100);
        harness.flush();
    }

    private void frame(long timeNanos) {
        rt.__tickFrame(timeNanos);
        harness.flush();
    }

    /** harness 单键入口无修饰键，组合键通过既有 RawInputEvent 路由。 */
    private void key(SceneKey key, boolean shift) {
        InputFrameBuilder builder = new InputFrameBuilder(0, 0);
        builder.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, shift, false, false, 0, 0, 1000L));
        rt.route(scene, builder.drainFrame(), 0, 0);
        harness.flush();
    }

    @Test
    public void focusAndDisabledChromeKeepOriginalColorsAndCursor() {
        mount();
        Assert.assertEquals("输入消息…", input.prefixText().getText());
        Assert.assertEquals(ChatMarkdownSettings.getInputPlaceholderArgb(), input.prefixText().getTextColor());
        Assert.assertEquals(SceneCursor.TEXT, input.root().getCursor());
        Assert.assertEquals(1, input.root().getBorderWidth());
        Assert.assertEquals(0, input.root().getBorderColor());
        Assert.assertEquals(0, input.caret().getBackgroundColor());
        rt.requestFocus(input.root());
        harness.flush();
        Assert.assertEquals(ChatMarkdownSettings.getInputFocusBorderArgb(), input.root().getBorderColor());
        Assert.assertEquals(SceneChromeTokens.BORDER_FOCUS, input.caret().getBackgroundColor());
        harness.typeText("abc");
        Assert.assertEquals("正文前景 = 主题 foreground", SceneThemes.DEFAULT.foreground(),
                input.prefixText().getTextColor());
        enabled.set(Boolean.FALSE);
        harness.flush();
        Assert.assertEquals(SceneCursor.NOT_ALLOWED, input.root().getCursor());
        Assert.assertFalse(input.root().isHitTestable());
        Assert.assertEquals("禁用前景 = 主题 disabledForeground", SceneThemes.DEFAULT.disabledForeground(),
                input.prefixText().getTextColor());
        Assert.assertEquals(0, input.caret().getBackgroundColor());
        Assert.assertEquals(ChatMarkdownSettings.getInputBackgroundArgb(), input.root().getBackgroundColor());
    }

    @Test
    public void selectionUsesBothCaretSlotsAndRemainsVisibleAfterBlur() {
        mount();
        rt.requestFocus(input.root());
        harness.flush();
        harness.typeText("abc");
        key(SceneKey.ARROW_LEFT, true);
        Assert.assertEquals("c", input.highlightText().getText());
        Assert.assertEquals(SceneChromeTokens.SELECTION_BG, input.highlightText().getBackgroundColor());
        Assert.assertEquals(SceneChromeTokens.SELECTION_TEXT, input.highlightText().getTextColor());
        Assert.assertEquals(SceneChromeTokens.BORDER_FOCUS, input.caret().getBackgroundColor());
        Assert.assertEquals(0, input.caretAfter().getBackgroundColor());
        key(SceneKey.HOME, false);
        key(SceneKey.ARROW_RIGHT, true);
        Assert.assertEquals("a", input.highlightText().getText());
        Assert.assertEquals(0, input.caret().getBackgroundColor());
        Assert.assertEquals(SceneChromeTokens.BORDER_FOCUS, input.caretAfter().getBackgroundColor());
        harness.clickAt(390, 90);
        Assert.assertEquals(0, input.root().getBorderColor());
        Assert.assertEquals(0, input.caretAfter().getBackgroundColor());
        Assert.assertEquals(SceneChromeTokens.SELECTION_BG, input.highlightText().getBackgroundColor());
        Assert.assertEquals(SceneChromeTokens.SELECTION_TEXT, input.highlightText().getTextColor());
        rt.requestFocus(input.root());
        harness.flush();
        harness.typeText("X");
        Assert.assertEquals("Xbc", value.get());
        Assert.assertEquals(0, input.highlightText().getBackgroundColor());
    }

    @Test
    public void glassSettingsUpdateSameInputTreeOnNextFrame() {
        mount();
        rt.requestFocus(input.root());
        harness.flush();
        harness.typeText("abc");
        key(SceneKey.ARROW_LEFT, true);
        SceneNode prefix = input.prefixText();
        Assert.assertNull(input.root().getBackdrop());
        ChatMarkdownSettings.setGlassEnabled(true);
        ChatMarkdownSettings.setGlassBlurRadiusPx(9);
        ChatMarkdownSettings.setGlassLensStrength(0.25F);
        ChatMarkdownSettings.setGlassInputAlpha(96);
        frame(1L);
        Assert.assertSame(prefix, input.root().__getChildren().get(0));
        Assert.assertEquals("abc", value.get());
        Assert.assertEquals("c", input.highlightText().getText());
        Assert.assertTrue(Boolean.TRUE.equals(rt.interactionState(input.root()).focused().get()));
        Assert.assertEquals(0x601E232A, input.root().getBackgroundColor());
        UiBackdrop backdrop = input.root().getBackdrop();
        Assert.assertEquals(9, backdrop.getBlurRadius());
        Assert.assertEquals(0.25F, backdrop.getEffect().getLensStrength(), 0.0F);
        Assert.assertEquals(UiGlassMaterial.DARK_THIN, backdrop.getEffect().getMaterial());
        frame(2L);
        Assert.assertSame("未改变配置时复用现有 backdrop", backdrop, input.root().getBackdrop());
        ChatMarkdownSettings.setGlassBlurRadiusPx(13);
        ChatMarkdownSettings.setGlassLensStrength(0.75F);
        frame(3L);
        Assert.assertEquals(13, input.root().getBackdrop().getBlurRadius());
        Assert.assertEquals(0.75F, input.root().getBackdrop().getEffect().getLensStrength(), 0.0F);
        ChatMarkdownSettings.setGlassEnabled(false);
        frame(4L);
        Assert.assertNull(input.root().getBackdrop());
        Assert.assertEquals(ChatMarkdownSettings.getInputBackgroundArgb(), input.root().getBackgroundColor());
        Assert.assertEquals(ChatMarkdownSettings.getInputFocusBorderArgb(), input.root().getBorderColor());
    }

    @Test
    public void motionCannotOverwriteChatBackgroundOrBorder() {
        rt.__enableMotion();
        mount();
        rt.requestFocus(input.root());
        harness.flush();
        rt.__sampleMotion(0L);
        rt.__sampleMotion(1_000_000_000L);
        Assert.assertEquals(ChatMarkdownSettings.getInputBackgroundArgb(), input.root().getBackgroundColor());
        Assert.assertEquals(ChatMarkdownSettings.getInputFocusBorderArgb(), input.root().getBorderColor());
        harness.clickAt(390, 90);
        rt.__sampleMotion(2_000_000_000L);
        Assert.assertEquals(0, input.root().getBorderColor());
        Assert.assertEquals(ChatMarkdownSettings.getInputBackgroundArgb(), input.root().getBackgroundColor());
    }

    @Test
    public void manualDisposeStopsAllChromeBindingsAndMotion() {
        rt.__enableMotion();
        mount();
        rt.requestFocus(input.root());
        harness.flush();
        chrome.dispose();
        chrome.dispose();
        input.root().setBackgroundColor(0xFF123456);
        input.root().setBorderColor(0xFF654321);
        input.caret().setBackgroundColor(0xFFABCDEF);
        ChatMarkdownSettings.setGlassEnabled(true);
        enabled.set(Boolean.FALSE);
        frame(1L);
        rt.__sampleMotion(1_000_000_000L);
        Assert.assertEquals(0xFF123456, input.root().getBackgroundColor());
        Assert.assertEquals(0xFF654321, input.root().getBorderColor());
        Assert.assertEquals(0xFFABCDEF, input.caret().getBackgroundColor());
        Assert.assertNull(input.root().getBackdrop());
        Assert.assertEquals(SceneCursor.TEXT, input.root().getCursor());
    }

    @Test
    public void parentOwnerDisposalUnsubscribesFrameAppearance() {
        Owner owner = new Owner();
        owner.run(this::mount);
        owner.dispose();
        ChatMarkdownSettings.setGlassEnabled(true);
        frame(1L);
        Assert.assertNull(input.root().getBackdrop());
        Assert.assertEquals(ChatMarkdownSettings.getInputBackgroundArgb(), input.root().getBackgroundColor());
        chrome.dispose();
    }

    @Test
    public void runtimeDisposalUnsubscribesDirectlyAttachedChrome() {
        mount();
        rt.dispose();
        ChatMarkdownSettings.setGlassEnabled(true);
        frame(1L);
        Assert.assertNull(input.root().getBackdrop());
        Assert.assertEquals(ChatMarkdownSettings.getInputBackgroundArgb(), input.root().getBackgroundColor());
        chrome.dispose();
    }

    // ==================== G17/Input：通用主题接缝 + 聊天局部覆盖 ====================

    /** 与聊天既有取值刻意不同的通用 INPUT 配方主题，用于证明「显式聊天设置 > 主题默认」方向。 */
    private static SceneTheme competingInputTheme() {
        SceneSurfaceStyle themedInput = SceneSurfaceStyle.builder()
                .backdrop(UiBackdrop.liquidGlass(UiGlassMaterial.DARK_REGULAR, 20, 0.9F))
                .cornerRadius(6)
                .borderWidth(2)
                .focusEdge(0xFF112233)
                .idle(new SceneSurfaceStyle.StateStyle(0xFF403020, 0x66FFFFFF, 0.5F, 0.5F))
                .hovered(new SceneSurfaceStyle.StateStyle(0xFF463626, 0x80FFFFFF, 1.0F, 0.7F))
                .pressed(new SceneSurfaceStyle.StateStyle(0xFF3A2C1C, 0x4DFFFFFF, 0.0F, 0.4F))
                .disabled(new SceneSurfaceStyle.StateStyle(0xFF2A2420, 0x33FFFFFF, 0.35F, 0.3F))
                .build();
        return SceneTheme.builder()
                .surface(SceneTheme.Role.INPUT, themedInput)
                .foreground(0xFF102030)
                .mutedForeground(0xFF403020)
                .disabledForeground(0xFF555555)
                .build();
    }

    /**
     * ① 默认（无聊天局部设置）：chrome 外观 = 通用 INPUT/主题配方的对应值。
     * 圆角/边框宽/底色/缘色/滤镜与占位前景全部取自 {@code SceneThemes.DEFAULT.surface(INPUT)}，
     * 不掺入任何聊天设置值。
     */
    @Test
    public void withoutChatLocalStyleChromeMatchesGenericInputRecipe() {
        SceneSurfaceStyle themed = SceneThemes.DEFAULT.surface(SceneTheme.Role.INPUT);
        mountWithLocalStyle(null);

        SceneNode root = input.root();
        Assert.assertEquals("圆角 = 主题 INPUT 配方值", themed.getCornerRadius(), root.getCornerRadius());
        Assert.assertEquals("边框宽 = 主题 INPUT 配方值", themed.getBorderWidth(), root.getBorderWidth());
        Assert.assertEquals("底色 = 主题 idle tint", themed.getIdle().getTint(), root.getBackgroundColor());
        Assert.assertEquals("未聚焦缘色 = 主题 idle edge", themed.getIdle().getEdge(), root.getBorderColor());
        // 占位前景无局部设置 → 跟随主题 mutedForeground（未聚焦时 placeholder 显示）。
        Assert.assertEquals("占位跟随主题 muted", SceneThemes.DEFAULT.mutedForeground(),
                input.prefixText().getTextColor());

        rt.requestFocus(root);
        harness.flush();
        Assert.assertEquals("聚焦缘色 = 主题 focusEdge", themed.getFocusEdge(), root.getBorderColor());

        UiBackdrop backdrop = root.getBackdrop();
        Assert.assertNotNull("主题档保留配方滤镜", backdrop);
        Assert.assertEquals(themed.getBackdrop().getBlurRadius(), backdrop.getBlurRadius());
        Assert.assertEquals(themed.getBackdrop().getEffect().getMaterial(),
                backdrop.getEffect().getMaterial());
        Assert.assertEquals("节点滤镜 = 配方 lens × idle.lensFactor",
                themed.getBackdrop().getEffect().getLensStrength() * themed.getIdle().getLensFactor(),
                backdrop.getEffect().getLensStrength(), 1.0E-4F);

        // 正文前景跟随主题 foreground。
        harness.typeText("abc");
        Assert.assertEquals("正文跟随主题 foreground", SceneThemes.DEFAULT.foreground(),
                input.prefixText().getTextColor());
    }

    /**
     * ② 显式聊天设置覆盖生效且优先于主题：安装一份与聊天取值全面冲突的 INPUT 主题，
     * 圆角/底色/滤镜（blur、材质）/focus 缘色/占位色仍取聊天设置；聊天未设置的属性
     * （边框宽、正文前景）由主题供给——两个方向的优先级同框断言。
     */
    @Test
    public void chatSettingsOverrideWinsOverConflictingTheme() {
        ChatMarkdownSettings.setGlassEnabled(true);
        ChatMarkdownSettings.setGlassBlurRadiusPx(9);
        ChatMarkdownSettings.setGlassLensStrength(0.25F);
        ChatMarkdownSettings.setGlassInputAlpha(96);
        SceneThemes.install(rt, Signal.create(competingInputTheme()));
        mount();

        SceneNode root = input.root();
        Assert.assertEquals("同心圆角是聊天设置管辖：12 ≠ 主题 6",
                ChatMarkdownSettings.getInputCornerRadiusPx(), root.getCornerRadius());
        Assert.assertEquals(0x601E232A, root.getBackgroundColor());
        UiBackdrop backdrop = root.getBackdrop();
        Assert.assertEquals(9, backdrop.getBlurRadius());
        Assert.assertEquals(0.25F, backdrop.getEffect().getLensStrength(), 0.0F);
        Assert.assertEquals(UiGlassMaterial.DARK_THIN, backdrop.getEffect().getMaterial());
        // 未聚焦 → placeholder 显示：占位色是显式聊天设置，压过主题 mutedForeground。
        Assert.assertEquals("占位色 = 聊天设置", ChatMarkdownSettings.getInputPlaceholderArgb(),
                input.prefixText().getTextColor());
        rt.requestFocus(root);
        harness.flush();
        Assert.assertEquals("focus 缘色 = 聊天设置", ChatMarkdownSettings.getInputFocusBorderArgb(),
                root.getBorderColor());
        // 聊天设置不管辖的属性由主题供给：主题 borderWidth=2 直通；正文前景取主题 foreground。
        harness.typeText("abc");
        Assert.assertEquals("未管辖属性跟随主题（边框宽）", 2, root.getBorderWidth());
        Assert.assertEquals("未管辖属性跟随主题（正文前景）", 0xFF102030,
                input.prefixText().getTextColor());

        // 反向优先：聊天「关闭玻璃」= 显式局部关滤镜，压过带滤镜的主题配方。
        ChatMarkdownSettings.setGlassEnabled(false);
        frame(1L);
        Assert.assertNull(root.getBackdrop());
        Assert.assertEquals(ChatMarkdownSettings.getInputBackgroundArgb(), root.getBackgroundColor());
    }

    /**
     * ③ 主题切换只重派生：节点身份不变、effect 数不增长、输入文本/焦点不丢、
     * 布局合同（高度/padding/字号）不受主题影响；外观属性全部随新主题更新。
     */
    @Test
    public void themeSwitchOnlyRederivesNeutralChrome() {
        Signal<SceneTheme> themeSignal = Signal.create(competingInputTheme());
        SceneThemes.install(rt, themeSignal);
        mountWithLocalStyle(null);

        SceneNode root = input.root();
        SceneNode prefix = input.prefixText();
        rt.requestFocus(root);
        harness.flush();
        harness.typeText("abc");
        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();

        themeSignal.set(SceneTheme.liquidGlassLight());
        // 主题切换后再推进多个普通 UI 帧：残留的按帧采样竞争绑定会在后续帧覆写主题值
        // （守「禁止靠执行顺序覆盖」——单帧次序掩盖不了持续竞争）。
        frame(1L);
        frame(2L);

        SceneSurfaceStyle light = SceneTheme.liquidGlassLight().surface(SceneTheme.Role.INPUT);
        Assert.assertSame("主题切换不重建输入根", root, scene.__getChildren().get(0));
        Assert.assertSame("主题切换不重建文本节点", prefix, root.__getChildren().get(0));
        Assert.assertEquals("编辑状态不丢", "abc", value.get());
        Assert.assertEquals("effect 数不增长", effectsBeforeSwitch,
                ReactiveTestProbe.registeredEffectCount());
        // 布局合同：主题不得改布局（契约 §4）。
        Assert.assertEquals(24, root.getPreferredHeight());
        Assert.assertEquals(10, root.getPaddingLeft());
        Assert.assertEquals(14, root.getFontSize());
        // 外观合同：以下属性全部改由新主题供给。
        Assert.assertEquals(light.getCornerRadius(), root.getCornerRadius());
        Assert.assertEquals(light.getBorderWidth(), root.getBorderWidth());
        Assert.assertEquals(light.getIdle().getTint(), root.getBackgroundColor());
        Assert.assertEquals(light.getFocusEdge(), root.getBorderColor());
        Assert.assertEquals("正文前景随主题切换", SceneTheme.liquidGlassLight().foreground(),
                prefix.getTextColor());
        Assert.assertEquals(light.getBackdrop().getBlurRadius(), root.getBackdrop().getBlurRadius());
        Assert.assertEquals(UiGlassMaterial.THIN, root.getBackdrop().getEffect().getMaterial());

        // 优先级方向补全：「无局部设置」的 chrome 对聊天设置变更无感；若残留按帧采样聊天设置
        // 的竞争绑定（旧外观链形态），此处聊天色板会把主题属性拉回。
        ChatMarkdownSettings.setGlassEnabled(true);
        frame(3L);
        Assert.assertEquals(light.getIdle().getTint(), root.getBackgroundColor());
        Assert.assertNotNull(root.getBackdrop());
        Assert.assertEquals(light.getBackdrop().getBlurRadius(), root.getBackdrop().getBlurRadius());
        Assert.assertEquals("聊天设置扰动不新增订阅", effectsBeforeSwitch,
                ReactiveTestProbe.registeredEffectCount());
    }

    /**
     * ③′ 聊天设置改变同样只重派生：effect 数不增长、节点身份与输入状态不变、不重建输入条。
     */
    @Test
    public void chatSettingsChangeOnlyRederivesProductionChrome() {
        ChatMarkdownSettings.setGlassEnabled(true);
        mount();
        SceneNode root = input.root();
        SceneNode prefix = input.prefixText();
        rt.requestFocus(root);
        harness.flush();
        harness.typeText("abc");
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        ChatMarkdownSettings.setGlassBlurRadiusPx(13);
        ChatMarkdownSettings.setGlassInputAlpha(120);
        frame(1L);

        Assert.assertSame(prefix, root.__getChildren().get(0));
        Assert.assertEquals("abc", value.get());
        Assert.assertEquals("设置变更不新增订阅", effectsBefore,
                ReactiveTestProbe.registeredEffectCount());
        Assert.assertEquals(13, root.getBackdrop().getBlurRadius());
        Assert.assertEquals(0x781E232A, root.getBackgroundColor());
    }

    /**
     * ④ 源码守卫：chrome 内不再残留竞争性的静态色板写入者（底色/缘色/选区/caret 静态 token、
     * 静态圆角/边框宽设值），表面属性唯一归 {@code SceneSurfaceBinder}；
     * 同时守消费方向——{@code ui.scene.theme} 绝不 import chat3/internal（契约 §4.1）。
     */
    @Test
    public void chatChromeKeepsSingleSurfaceWriterAndOneWayThemeDependency() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/club/heiqi/uilib/internal/chat3/input/ChatInputChrome.java")),
                StandardCharsets.UTF_8);
        Assert.assertFalse("静态色板 SceneStateColors 不得再被 chrome 引用",
                source.contains("SceneStateColors"));
        Assert.assertFalse("选区色不得再取 SceneChromeTokens.SELECTION_BG",
                source.contains("SELECTION_BG"));
        Assert.assertFalse("选区色不得再取 SceneChromeTokens.SELECTION_TEXT",
                source.contains("SELECTION_TEXT"));
        Assert.assertFalse("caret 色不得再取 SceneChromeTokens.BORDER_FOCUS",
                source.contains("BORDER_FOCUS"));
        Assert.assertFalse("不得残留 root.setBackgroundColor 竞争绑定",
                source.contains("root::setBackgroundColor"));
        Assert.assertFalse("不得残留 root.setBorderColor 竞争绑定",
                source.contains("root::setBorderColor"));
        Assert.assertFalse("不得残留 root.setBackdrop 竞争绑定",
                source.contains("root::setBackdrop"));
        Assert.assertFalse("不得残留静态 borderWidth 设值", source.contains("setBorderWidth(1)"));
        Assert.assertFalse("不得残留按设置直写的静态圆角",
                source.contains("setCornerRadius(ChatMarkdownSettings"));
        Assert.assertTrue("表面属性唯一写入者 = 通用绑定器", source.contains("SceneSurfaceBinder.bind("));
        Assert.assertTrue("输入配方来自通用主题接缝", source.contains("SceneThemes.surface("));

        try (Stream<Path> walk = Files.walk(Paths.get("src/main/java/club/heiqi/uilib/ui/scene/theme"))) {
            for (Path file : (Iterable<Path>) walk.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String themeSource = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                Assert.assertFalse("通用主题不得 import internal/chat3: " + file,
                        themeSource.matches("(?s).*import\\s+club\\.heiqi\\.uilib\\.internal\\..*"));
            }
        }
    }
}
