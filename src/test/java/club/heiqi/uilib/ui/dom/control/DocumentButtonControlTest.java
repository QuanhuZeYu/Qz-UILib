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
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

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
        Assert.assertSame(buttonControl, events.get(0).getSource());
        Assert.assertSame(buttonControl.getElement(), events.get(0).getElement());
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
