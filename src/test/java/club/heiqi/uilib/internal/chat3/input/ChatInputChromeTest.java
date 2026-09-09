package club.heiqi.uilib.internal.chat3.input;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.reactive.Owner;
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
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

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
        Assert.assertEquals(SceneStateColors.standardText(true, false), input.prefixText().getTextColor());
        enabled.set(Boolean.FALSE);
        harness.flush();
        Assert.assertEquals(SceneCursor.NOT_ALLOWED, input.root().getCursor());
        Assert.assertFalse(input.root().isHitTestable());
        Assert.assertEquals(SceneStateColors.standardText(false, false), input.prefixText().getTextColor());
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
}
