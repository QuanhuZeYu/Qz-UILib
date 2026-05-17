package club.heiqi.uilib.ui.dom.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * `DocumentButtonControl` 的基础行为契约测试。
 */
public class DocumentButtonControlTest {

    /**
     * 验证按钮控件会通过鼠标与键盘触发动作事件。
     */
    @Test
    public void shouldActivateButtonFromMouseAndKeyboard() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentButtonActionEvent> events = new ArrayList<DocumentButtonActionEvent>();
        DocumentButtonControl buttonControl = new DocumentButtonControl(document, "Run");
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(80));
        buttonControl.getElement().style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(32));
        buttonControl.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                events.add(event);
            }
        });
        root.append(buttonControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 80);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 8, 8, 0, 0, 0, 0, 2L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        Assert.assertEquals(2, events.size());
        Assert.assertEquals("button", buttonControl.getElement().getTagName());
        Assert.assertEquals("button", buttonControl.getElement().getAttribute("type"));
        Assert.assertSame(buttonControl, events.get(0).getSource());
        assertElementUid(buttonControl.getElement(), events.get(0).getElement());
        Assert.assertFalse(events.get(0).isKeyboardTriggered());
        Assert.assertEquals(0, events.get(0).getButton());
        Assert.assertTrue(events.get(1).isKeyboardTriggered());
        Assert.assertEquals(Keyboard.KEY_SPACE, events.get(1).getKeyCode());
    }

    /**
     * 验证禁用按钮不会触发动作，也不会被焦点遍历选中。
     */
    @Test
    public void shouldSkipActivationAndFocusWhenDisabled() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentButtonActionEvent> events = new ArrayList<DocumentButtonActionEvent>();
        DocumentButtonControl buttonControl = new DocumentButtonControl(document, "Disabled");
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(80));
        buttonControl.getElement().style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(32));
        buttonControl.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                events.add(event);
            }
        }).setEnabled(false);
        root.append(buttonControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 80);

        widget.onFocusTraversalEntered(false);
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 8, 8, 0, 0, 0, 0, 2L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        Assert.assertNull(widget.getFocusedElement());
        Assert.assertTrue(events.isEmpty());
        Assert.assertFalse(buttonControl.isEnabled());
        Assert.assertEquals("true", buttonControl.getElement().getAttribute("disabled"));
    }

    /**
     * 验证按钮控件区分鼠标按下、键盘焦点提示与普通聚焦。
     */
    @Test
    public void shouldSeparateActiveStateFromFocusVisibleState() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentButtonControl buttonControl = new DocumentButtonControl(document, "Run");
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(80));
        buttonControl.getElement().style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(32));
        root.append(buttonControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 80);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 8, 8, 0, 0, 0, 0, 1L));
        RecordingUiRenderContext mouseDownRenderContext = new RecordingUiRenderContext();
        widget.render(mouseDownRenderContext);
        Assert.assertTrue(containsFillColor(mouseDownRenderContext.drawCalls, 0xFF2B6CB0));
        Assert.assertFalse(containsBorderColor(mouseDownRenderContext.drawCalls, 0xFFBEE3F8));

        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 8, 8, 0, 0, 0, 0, 2L));
        widget.onFocusTraversalEntered(false);
        RecordingUiRenderContext focusVisibleRenderContext = new RecordingUiRenderContext();
        widget.render(focusVisibleRenderContext);
        Assert.assertTrue(containsFillColor(focusVisibleRenderContext.drawCalls, 0xFF3182CE));
        Assert.assertTrue(containsBorderColor(focusVisibleRenderContext.drawCalls, 0xFFBEE3F8));
    }

    private static boolean containsFillColor(List<DrawCall> drawCalls, int expectedColor) {
        for (DrawCall drawCall : drawCalls) {
            if (drawCall.surfaceStyle.fillColor == expectedColor) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBorderColor(List<DrawCall> drawCalls, int expectedColor) {
        for (DrawCall drawCall : drawCalls) {
            if (drawCall.surfaceStyle.borderColor == expectedColor) {
                return true;
            }
        }
        return false;
    }

    private static void assertElementUid(ElementNode expectedElement, ElementNode actualElement) {
        Assert.assertNotNull(actualElement);
        Assert.assertEquals(expectedElement.__getElementUid(), actualElement.__getElementUid());
    }

    /**
     * 记录 surface 绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(surfaceStyle));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {}

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow,
                TextContentMode textContentMode) {}

        @Override
        public boolean supportsDeferredTextBatching() {
            return false;
        }

        @Override
        public int measureTextWidth(String text) {
            return text == null ? 0 : text.length() * 12;
        }

        @Override
        public int getTextLineHeight() {
            return 18;
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {}

        @Override
        public void popClip() {}
    }

    /**
     * 单次 surface 绘制记录。
     */
    private static final class DrawCall {

        private final UiSurfaceStyle surfaceStyle;

        private DrawCall(UiSurfaceStyle surfaceStyle) {
            this.surfaceStyle = surfaceStyle;
        }
    }

    /**
     * 供测试使用的确定性文本测量服务。
     */
    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
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
            return text == null ? "" : text;
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
