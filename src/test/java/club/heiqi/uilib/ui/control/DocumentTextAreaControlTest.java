package club.heiqi.uilib.ui.control;

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
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `DocumentTextAreaControl` 的基础行为契约测试。
 */
public class DocumentTextAreaControlTest {

    /**
     * 验证多行文本输入控件使用真实 textarea 语义。
     */
    @Test
    public void shouldUseTextareaElementSemantics() {
        UiDocument document = UiDocument.create();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);

        Assert.assertEquals("textarea", textAreaControl.getElement().getTagName());
        Assert.assertEquals("textbox", textAreaControl.getElement().getSemanticRole());
        Assert.assertEquals("true", textAreaControl.getElement().getAttribute("aria-multiline"));
        Assert.assertEquals("", textAreaControl.getElement().getAttribute("value"));
    }

    /**
     * 验证鼠标聚焦后支持多行输入、键盘移动和选择替换。
     */
    @Test
    public void shouldSupportMouseFocusAndKeyboardEditing() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(120));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(72));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 12, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 12, 12, 0, 0, 0, 0, 2L));
        Assert.assertTrue(textAreaControl.isFocused());

        widget.onTextInput(new UiTextInputEvent("Hello", 3L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 4L));
        widget.onTextInput(new UiTextInputEvent("World", 5L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_UP, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 6L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_END, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 7L));
        widget.onTextInput(new UiTextInputEvent("!", 8L));
        Assert.assertEquals("Hello!\nWorld", textAreaControl.getText());

        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_LEFT, 0, 0, UiKeyEvent.Action.PRESSED, false, true, false,
                false, 9L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 10L));
        Assert.assertEquals("Hello\nWorld", textAreaControl.getText());

        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_A, 0, 0, UiKeyEvent.Action.PRESSED, true, false, false,
                false, 11L));
        widget.onTextInput(new UiTextInputEvent("Done", 12L));
        Assert.assertEquals("Done", textAreaControl.getText());
    }

    /**
     * 验证多行内容在聚焦时会滚动到当前光标行附近。
     */
    @Test
    public void shouldRevealCaretLineWhenFocused() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        textAreaControl.setText("L1\nL2\nL3\nL4\nL5");
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(120));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(54));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 12, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 12, 12, 0, 0, 0, 0, 2L));

        Assert.assertTrue(widget.getMaxScrollTop(textAreaControl.getElement()) > 0);
        Assert.assertTrue(widget.getScrollTop(textAreaControl.getElement()) > 0);
    }

    /**
     * 验证超长单行编辑时会横向滚动到光标位置，而不是每次重置到最左侧。
     */
    @Test
    public void shouldRevealCaretHorizontallyForLongLine() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(80));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 80);

        widget.render(new ControlTestRenderContext(160, 80));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 12, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 12, 12, 0, 0, 0, 0, 2L));
        widget.onTextInput(new UiTextInputEvent("abcdefghijklmnopqrstuvwxyz", 3L));
        widget.render(new ControlTestRenderContext(160, 80));

        Assert.assertTrue(widget.getMaxScrollLeft(textAreaControl.getElement()) > 0);
        Assert.assertTrue(widget.getScrollLeft(textAreaControl.getElement()) > 0);
    }

    /**
     * 验证鼠标点击行内位置会按 X 坐标定位光标，而不是固定跳到行尾。
     */
    @Test
    public void shouldPlaceCaretByClickedLineX() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        textAreaControl.setText("abcdef");
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(80));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 80);

        widget.render(new ControlTestRenderContext(160, 80));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 20, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 20, 12, 0, 0, 0, 0, 2L));
        widget.onTextInput(new UiTextInputEvent("X", 3L));

        Assert.assertEquals("abcXdef", textAreaControl.getText());
    }

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
            return text == null || targetWidth <= 0 ? "" : text.substring(0,
                    Math.min(text.length(), targetWidth / 6));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty()) {
                return Collections.singletonList("");
            }
            return Collections.singletonList(text);
        }
    }
}
