package club.heiqi.uilib.ui.hud;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.input.UiKeyboardCaptureState;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `UiHudDocumentHost` 的稳定契约测试。
 */
public class UiHudDocumentHostTest {

    /**
     * 验证 HUD 屏幕分类会把纯游戏、容器和菜单页区分开。
     */
    @Test
    public void shouldClassifyHudScreenCategories() {
        Assert.assertEquals(UiHudScreenCategory.INGAME, UiHudDocumentHost.classifyScreen(null, null));
        Assert.assertEquals(UiHudScreenCategory.CONTAINER,
                UiHudDocumentHost.classifyScreen(new Object(), "net.minecraft.client.gui.inventory.GuiChest"));
        Assert.assertEquals(UiHudScreenCategory.CONTAINER,
                UiHudDocumentHost.classifyScreen(new Object(), "net.minecraft.client.gui.GuiChat"));
        Assert.assertEquals(UiHudScreenCategory.MENU,
                UiHudDocumentHost.classifyScreen(new Object(), "net.minecraft.client.gui.GuiIngameMenu"));
        Assert.assertEquals(UiHudScreenCategory.MENU,
                UiHudDocumentHost.classifyScreen(new Object(), "example.custom.Screen"));
    }

    /**
     * 验证被动 HUD 根节点默认命中隐藏且使用全视口可见容器契约。
     */
    @Test
    public void shouldApplyPassiveHudRootContract() {
        UiDocument document = captureRegisteredDocument(UiHudLayerType.PASSIVE);
        ElementNode root = document.getRootElement();

        Assert.assertEquals(UiStyleLength.percent(1.0F), root.style().getWidth());
        Assert.assertEquals(UiStyleLength.percent(1.0F), root.style().getHeight());
        Assert.assertEquals(UiOverflow.VISIBLE, root.style().getOverflowX());
        Assert.assertEquals(UiOverflow.VISIBLE, root.style().getOverflowY());
        Assert.assertEquals("passive", root.getAttribute("data-hud-layer"));
        Assert.assertEquals("true", root.getAttribute("data-hit-test-hidden"));
    }

    /**
     * 验证交互 HUD 根节点沿用全视口可见容器契约，但不会被默认标记为命中隐藏。
     */
    @Test
    public void shouldApplyInteractiveHudRootContract() {
        UiDocument document = captureRegisteredDocument(UiHudLayerType.INTERACTIVE);
        ElementNode root = document.getRootElement();

        Assert.assertEquals(UiStyleLength.percent(1.0F), root.style().getWidth());
        Assert.assertEquals(UiStyleLength.percent(1.0F), root.style().getHeight());
        Assert.assertEquals(UiOverflow.VISIBLE, root.style().getOverflowX());
        Assert.assertEquals(UiOverflow.VISIBLE, root.style().getOverflowY());
        Assert.assertEquals("interactive", root.getAttribute("data-hud-layer"));
        Assert.assertNull(root.getAttribute("data-hit-test-hidden"));
    }

    /**
     * 验证交互 HUD 在非菜单且鼠标已释放时都允许接通输入。
     */
    @Test
    public void shouldEnableInteractiveHudInputOnAnyOpenedScreenWhenMouseIsFree() {
        Assert.assertFalse(UiHudDocumentHost.isInteractiveInputEnabled(null, null, false));
        Assert.assertTrue(UiHudDocumentHost.isInteractiveInputEnabled(new Object(),
                "net.minecraft.client.gui.inventory.GuiChest", false));
        Assert.assertTrue(UiHudDocumentHost.isInteractiveInputEnabled(new Object(),
                "example.custom.Screen", false));
        Assert.assertTrue(UiHudDocumentHost.isInteractiveInputEnabled(new Object(),
                "net.minecraft.client.gui.GuiIngameMenu", false));
        Assert.assertFalse(UiHudDocumentHost.isInteractiveInputEnabled(null, null, true));
    }

    /**
     * 验证交互 HUD 在点击回调里立即注销自身时，不会破坏当前输入帧遍历。
     */
    @Test
    public void shouldAllowInteractiveHudToUnregisterDuringInputRouting() {
        UiHudDocumentHost host = UiHudDocumentHost.getInstance();
        final UiHudDocumentRegistration[] registrationHolder = new UiHudDocumentRegistration[1];
        final int[] actionCount = new int[1];
        registrationHolder[0] = host.register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        ElementNode root = document.getRootElement();
                        root.style()
                                .setWidth(UiStyleLength.px(160))
                                .setHeight(UiStyleLength.px(80));

                        DocumentButtonControl buttonControl = new DocumentButtonControl(document, "Hide");
                        buttonControl.getElement().style()
                                .setWidth(UiStyleLength.px(120))
                                .setHeight(UiStyleLength.px(32));
                        buttonControl.setActionHandler(new DocumentButtonActionHandler() {
                            @Override
                            public void onAction(DocumentButtonActionEvent event) {
                                actionCount[0]++;
                                registrationHolder[0].unregister();
                            }
                        });
                        root.append(buttonControl.getElement());
                    }
        }, new DeterministicTextMeasureService(), UiRuntimeAdapters.empty());

        try {
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 1L),
                    UiHudScreenCategory.CONTAINER, 160, 80);
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.BUTTON_UP, 8, 8, 2L),
                    UiHudScreenCategory.CONTAINER, 160, 80);

            Assert.assertEquals(1, actionCount[0]);
        } finally {
            registrationHolder[0].unregister();
        }
    }

    /**
     * 验证原生文本输入框持有键盘时，HUD 仍可收鼠标但不会继续收到键盘与文本事件。
     */
    @Test
    public void shouldStripKeyboardEventsWhenNativeTextInputOwnsKeyboard() {
        UiInputFrame frame = new UiInputFrame(12, 18,
                Collections.singletonList(new UiMouseEvent(UiMouseEvent.Action.MOVE, 12, 18, -1, 0, 0, 0, 1L)),
                Collections.singletonList(new UiKeyEvent(Keyboard.KEY_TAB, 0, 0, UiKeyEvent.Action.PRESSED, false,
                        false, false, false, 2L)),
                Collections.singletonList(new UiTextInputEvent("a", 3L)));

        UiInputFrame filtered = UiHudDocumentHost.filterKeyboardInput(frame, true, false);

        Assert.assertEquals(1, filtered.getMouseEvents().size());
        Assert.assertTrue(filtered.getKeyEvents().isEmpty());
        Assert.assertTrue(filtered.getTextEvents().isEmpty());
    }

    /**
     * 验证 UILib 已接管键盘后，不会再因为原生文本框聚焦而剥离自身键盘事件。
     */
    @Test
    public void shouldKeepKeyboardEventsWhenUiLibAlreadyCapturedKeyboard() {
        UiInputFrame frame = new UiInputFrame(12, 18, Collections.<UiMouseEvent>emptyList(),
                Collections.singletonList(new UiKeyEvent(Keyboard.KEY_TAB, 0, 0, UiKeyEvent.Action.PRESSED, false,
                        false, false, false, 2L)),
                Collections.singletonList(new UiTextInputEvent("a", 3L)));

        UiInputFrame filtered = UiHudDocumentHost.filterKeyboardInput(frame, true, true);

        Assert.assertEquals(1, filtered.getKeyEvents().size());
        Assert.assertEquals(1, filtered.getTextEvents().size());
    }

    /**
     * 验证当 HUD 输入框已获得焦点时，会在宿主原生键盘处理前抢先接管键盘。
     */
    @Test
    public void shouldCaptureImmediateKeyboardInputBeforeNativeScreenConsumesIt() {
        UiHudDocumentHost host = UiHudDocumentHost.getInstance();
        UiKeyboardCaptureState.getInstance().clear();
        UiHudDocumentRegistration registration = host.register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        ElementNode root = document.getRootElement();
                        root.style()
                                .setWidth(UiStyleLength.px(160))
                                .setHeight(UiStyleLength.px(80));
                        DocumentTextInputControl inputControl = new DocumentTextInputControl(document);
                        inputControl.getElement().style()
                                .setWidth(UiStyleLength.px(120))
                                .setHeight(UiStyleLength.px(24));
                        root.append(inputControl.getElement());
                    }
                }, new DeterministicTextMeasureService(), UiRuntimeAdapters.empty());
        try {
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 1L),
                    UiHudScreenCategory.CONTAINER, 160, 80);

            boolean captured = host.handleImmediateKeyboardInputForTest(
                    new UiInputFrame(8, 8, Collections.<UiMouseEvent>emptyList(),
                            Collections.singletonList(new UiKeyEvent(Keyboard.KEY_TAB, 0, 0,
                                    UiKeyEvent.Action.PRESSED, false, false, false, false, 2L)),
                            Collections.<UiTextInputEvent>emptyList()),
                    UiHudScreenCategory.CONTAINER);

            Assert.assertTrue(captured);
            Assert.assertTrue(UiKeyboardCaptureState.getInstance().isUiLibKeyboardCaptured());
        } finally {
            registration.unregister();
            UiKeyboardCaptureState.getInstance().clear();
        }
    }

    /**
     * 验证 HUD 即时键盘抢占只处理按键语义，不会把同一字符作为即时文本再次注入。
     */
    @Test
    public void shouldNotInjectImmediateTextDuringHudKeyboardCapture() {
        UiHudDocumentHost host = UiHudDocumentHost.getInstance();
        UiKeyboardCaptureState.getInstance().clear();
        final String[] textHolder = new String[1];
        UiHudDocumentRegistration registration = host.register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        ElementNode root = document.getRootElement();
                        root.style()
                                .setWidth(UiStyleLength.px(160))
                                .setHeight(UiStyleLength.px(80));
                        DocumentTextInputControl inputControl = new DocumentTextInputControl(document);
                        inputControl.getElement().style()
                                .setWidth(UiStyleLength.px(120))
                                .setHeight(UiStyleLength.px(24));
                        inputControl.setChangeHandler(event -> textHolder[0] = event.getText());
                        root.append(inputControl.getElement());
                    }
                }, new DeterministicTextMeasureService(), UiRuntimeAdapters.empty());
        try {
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 1L),
                    UiHudScreenCategory.CONTAINER, 160, 80);

            boolean captured = host.handleImmediateKeyboardInputForTest(
                    new UiInputFrame(8, 8, Collections.<UiMouseEvent>emptyList(),
                            Collections.singletonList(new UiKeyEvent(Keyboard.KEY_1, 0, 0,
                                    UiKeyEvent.Action.PRESSED, false, false, false, false, 2L)),
                            Collections.<UiTextInputEvent>emptyList()),
                    UiHudScreenCategory.CONTAINER);

            Assert.assertTrue(captured);
            Assert.assertNull(textHolder[0]);

            host.handleInputFrameForTest(new UiInputFrame(8, 8, Collections.<UiMouseEvent>emptyList(),
                    Collections.<UiKeyEvent>emptyList(),
                    Collections.singletonList(new UiTextInputEvent("1", 3L))), UiHudScreenCategory.CONTAINER, 160, 80);

            Assert.assertEquals("1", textHolder[0]);
        } finally {
            registration.unregister();
            UiKeyboardCaptureState.getInstance().clear();
        }
    }

    /**
     * 验证 HUD 未通过鼠标先获得焦点时，单独按 Tab 不会把键盘焦点直接激活到 HUD。
     *
     * <p>这是当前产品约束：交互 HUD 必须先鼠标聚焦，才允许继续接管后续键盘输入。</p>
     */
    @Test
    public void shouldNotCaptureImmediateKeyboardInputWithoutPriorHudFocus() {
        UiHudDocumentHost host = UiHudDocumentHost.getInstance();
        UiKeyboardCaptureState.getInstance().clear();
        UiHudDocumentRegistration registration = host.register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        ElementNode root = document.getRootElement();
                        root.style()
                                .setWidth(UiStyleLength.px(160))
                                .setHeight(UiStyleLength.px(80));
                        DocumentTextInputControl inputControl = new DocumentTextInputControl(document);
                        inputControl.getElement().style()
                                .setWidth(UiStyleLength.px(120))
                                .setHeight(UiStyleLength.px(24));
                        root.append(inputControl.getElement());
                    }
                }, new DeterministicTextMeasureService(), UiRuntimeAdapters.empty());
        try {
            boolean captured = host.handleImmediateKeyboardInputForTest(
                    new UiInputFrame(8, 8, Collections.<UiMouseEvent>emptyList(),
                            Collections.singletonList(new UiKeyEvent(Keyboard.KEY_TAB, 0, 0,
                                    UiKeyEvent.Action.PRESSED, false, false, false, false, 2L)),
                            Collections.<UiTextInputEvent>emptyList()),
                    UiHudScreenCategory.CONTAINER);

            Assert.assertFalse(captured);
            Assert.assertFalse(UiKeyboardCaptureState.getInstance().isHudKeyboardCaptured());
        } finally {
            registration.unregister();
            UiKeyboardCaptureState.getInstance().clear();
        }
    }

    /**
     * 验证命中交互元素时，HUD 会在原生页面之前拦截鼠标按下。
     */
    @Test
    public void shouldCaptureImmediateMouseInputWhenInteractiveHudElementIsHit() {
        UiHudDocumentHost host = UiHudDocumentHost.getInstance();
        UiHudDocumentRegistration registration = host.register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        ElementNode root = document.getRootElement();
                        root.style()
                                .setWidth(UiStyleLength.px(160))
                                .setHeight(UiStyleLength.px(80));
                        DocumentButtonControl buttonControl = new DocumentButtonControl(document, "Hit");
                        buttonControl.getElement().style()
                                .setWidth(UiStyleLength.px(120))
                                .setHeight(UiStyleLength.px(24));
                        root.append(buttonControl.getElement());
                    }
                }, new DeterministicTextMeasureService(), UiRuntimeAdapters.empty());
        try {
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.MOVE, 8, 8, 1L), UiHudScreenCategory.CONTAINER,
                    160, 80);

            boolean captured = host.handleImmediateMouseInputForTest(
                    mouseFrame(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 2L),
                    UiHudScreenCategory.CONTAINER);

            Assert.assertTrue(captured);
        } finally {
            registration.unregister();
        }
    }

    /**
     * 验证即时鼠标拦截与常规 HUD 输入路由使用同一套原生像素坐标，不会因缩放坐标不一致导致点击穿透。
     */
    @Test
    public void shouldCaptureImmediateMouseInputUsingSameNativeCoordinatesAsHudRouting() {
        UiHudDocumentHost host = UiHudDocumentHost.getInstance();
        UiHudDocumentRegistration registration = host.register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        ElementNode root = document.getRootElement();
                        root.style()
                                .setWidth(UiStyleLength.px(320))
                                .setHeight(UiStyleLength.px(180));
                        DocumentButtonControl buttonControl = new DocumentButtonControl(document, "Hit");
                        buttonControl.getElement().style()
                                .setWidth(UiStyleLength.px(120))
                                .setHeight(UiStyleLength.px(24));
                        root.append(buttonControl.getElement());
                    }
                }, new DeterministicTextMeasureService(), UiRuntimeAdapters.empty());
        try {
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.MOVE, 12, 12, 1L), UiHudScreenCategory.CONTAINER,
                    320, 180);

            boolean captured = host.handleImmediateMouseInputForTest(
                    mouseFrame(UiMouseEvent.Action.BUTTON_DOWN, 12, 12, 2L),
                    UiHudScreenCategory.CONTAINER);

            Assert.assertTrue(captured);
        } finally {
            registration.unregister();
        }
    }

    /**
     * 验证常规 HUD 输入路由会更新交互会话记录的最近鼠标位置，供后续渲染复用。
     */
    @Test
    public void shouldReuseLatestPointerRecordedByHudInteractionSession() {
        UiHudDocumentHost host = UiHudDocumentHost.getInstance();
        UiHudDocumentRegistration registration = host.register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        document.getRootElement().style()
                                .setWidth(UiStyleLength.px(160))
                                .setHeight(UiStyleLength.px(80));
                    }
                }, new DeterministicTextMeasureService(), UiRuntimeAdapters.empty());
        try {
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.MOVE, 18, 26, 1L),
                    UiHudScreenCategory.CONTAINER, 160, 80);

            HtmlLikeDocumentWidget widget = host.getFirstInteractiveWidgetForDiagnostics();
            Assert.assertNotNull(widget);
            Assert.assertNotNull(widget.findElementAt(18, 26));
        } finally {
            registration.unregister();
        }
    }

    /**
     * 验证普通浮窗面板空白区域默认会阻止点击穿透到底层原生页面。
     */
    @Test
    public void shouldCaptureImmediateMouseInputOnPanelWhitespace() {
        UiHudDocumentHost host = UiHudDocumentHost.getInstance();
        UiHudDocumentRegistration registration = host.register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        ElementNode root = document.getRootElement();
                        root.style()
                                .setWidth(UiStyleLength.px(320))
                                .setHeight(UiStyleLength.px(180));
                        ElementNode panel = document.div();
                        panel.style()
                                .setWidth(UiStyleLength.px(140))
                                .setHeight(UiStyleLength.px(80))
                                .setBackgroundColor(0xFF222233);
                        root.append(panel);
                    }
                }, new DeterministicTextMeasureService(), UiRuntimeAdapters.empty());
        try {
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.MOVE, 12, 12, 1L), UiHudScreenCategory.CONTAINER,
                    320, 180);

            boolean captured = host.handleImmediateMouseInputForTest(
                    mouseFrame(UiMouseEvent.Action.BUTTON_DOWN, 12, 12, 2L),
                    UiHudScreenCategory.CONTAINER);

            Assert.assertTrue(captured);
        } finally {
            registration.unregister();
        }
    }

    /**
     * 验证显式声明为可穿透的非交互面板空白区域，会放行到底层原生鼠标输入。
     */
    @Test
    public void shouldNotCaptureImmediateMouseInputOnPassthroughPanelWhitespace() {
        UiHudDocumentHost host = UiHudDocumentHost.getInstance();
        UiHudDocumentRegistration registration = host.register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        ElementNode root = document.getRootElement();
                        root.style()
                                .setWidth(UiStyleLength.px(320))
                                .setHeight(UiStyleLength.px(180));
                        ElementNode panel = document.div();
                        panel.style()
                                .setWidth(UiStyleLength.px(140))
                                .setHeight(UiStyleLength.px(80))
                                .setBackgroundColor(0xFF222233);
                        panel.setAttribute("data-hit-test-passthrough", "true");
                        root.append(panel);
                    }
                }, new DeterministicTextMeasureService(), UiRuntimeAdapters.empty());
        try {
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.MOVE, 12, 12, 1L), UiHudScreenCategory.CONTAINER,
                    320, 180);

            boolean captured = host.handleImmediateMouseInputForTest(
                    mouseFrame(UiMouseEvent.Action.BUTTON_DOWN, 12, 12, 2L),
                    UiHudScreenCategory.CONTAINER);

            Assert.assertFalse(captured);
        } finally {
            registration.unregister();
        }
    }

    /**
     * 验证只命中 HUD 根空白区域时，也会按默认阻断契约拦截原生鼠标输入。
     */
    @Test
    public void shouldCaptureImmediateMouseInputOnHudRootWhitespaceByDefault() {
        UiHudDocumentHost host = UiHudDocumentHost.getInstance();
        UiHudDocumentRegistration registration = host.register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        ElementNode root = document.getRootElement();
                        root.style()
                                .setWidth(UiStyleLength.px(160))
                                .setHeight(UiStyleLength.px(80));
                    }
                }, new DeterministicTextMeasureService(), UiRuntimeAdapters.empty());
        try {
            host.handleInputFrameForTest(mouseFrame(UiMouseEvent.Action.MOVE, 8, 8, 1L), UiHudScreenCategory.CONTAINER,
                    160, 80);

            boolean captured = host.handleImmediateMouseInputForTest(
                    mouseFrame(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 2L),
                    UiHudScreenCategory.CONTAINER);

            Assert.assertTrue(captured);
        } finally {
            registration.unregister();
        }
    }

    private static UiDocument captureRegisteredDocument(UiHudLayerType layerType) {
        final UiDocument[] holder = new UiDocument[1];
        UiHudDocumentRegistration registration = UiHudDocumentHost.getInstance().register(layerType,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiDocument document) {
                        holder[0] = document;
                    }
                }, DefaultTextMeasureService.getInstance(), UiRuntimeAdapters.empty());
        try {
            return holder[0];
        } finally {
            registration.unregister();
        }
    }

    private static UiInputFrame mouseFrame(UiMouseEvent.Action action, int mouseX, int mouseY, long timeNanos) {
        return new UiInputFrame(mouseX, mouseY,
                Collections.singletonList(new UiMouseEvent(action, mouseX, mouseY, 0, 0, 0, 0, timeNanos)),
                Collections.emptyList(), Collections.emptyList());
    }

    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 0;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxChars = Math.max(0, targetWidth / 6);
            return text.length() <= maxChars ? text : text.substring(0, maxChars);
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : trimStringToWidth(text, wrapWidth));
        }
    }
}
