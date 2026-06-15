package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextContentMode;
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
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 4L));
        widget.onTextInput(new UiTextInputEvent("World", 5L));
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_UP, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 6L));
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_END, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 7L));
        widget.onTextInput(new UiTextInputEvent("!", 8L));
        Assert.assertEquals("Hello!\nWorld", textAreaControl.getText());

        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_LEFT, 0, 0, UiKeyEvent.Action.PRESSED, false, true, false,
                false, 9L));
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 10L));
        Assert.assertEquals("Hello\nWorld", textAreaControl.getText());

        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_A, 0, 0, UiKeyEvent.Action.PRESSED, true, false, false,
                false, 11L));
        widget.onTextInput(new UiTextInputEvent("Done", 12L));
        Assert.assertEquals("Done", textAreaControl.getText());
    }

    /**
     * 验证删除换行后会丢弃旧视觉行缓存，避免光标滚动复用过期逻辑行索引。
     */
    @Test
    public void shouldDropStaleVisualLinesWhenDeletingLineBreak() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        textAreaControl.setText("A\nB");
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(80));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(54));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 80);

        widget.onFocusTraversalEntered(false);
        widget.render(new ControlTestRenderContext(160, 80));
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_LEFT, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 2L));

        Assert.assertEquals("AB", textAreaControl.getText());
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
     * 验证超长单行编辑会通过软换行保持横向滚动在起点。
     */
    @Test
    public void shouldKeepHorizontalScrollAtOriginForSoftWrappedLongLine() {
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

        Assert.assertEquals(0, widget.getScrollLeft(textAreaControl.getElement()));
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

        ControlTestRenderContext renderContext = new ControlTestRenderContext(160, 80);
        widget.render(renderContext);
        DocumentElementBounds bounds = textAreaControl.getElement().getDocumentBounds();
        int clickX = bounds.getContentLeft() + renderContext.measureTextWidth("abc", TextContentMode.UILIB_RAW);
        int clickY = bounds.getContentTop() + 4;
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, clickX, clickY, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, clickX, clickY, 0, 0, 0, 0, 2L));
        widget.onTextInput(new UiTextInputEvent("X", 3L));

        Assert.assertEquals("abcXdef", textAreaControl.getText());
    }

    /**
     * 验证多行文本光标使用实际内容盒坐标，与对应文本行末尾对齐。
     */
    @Test
    public void shouldRenderMultilineCaretAtTextContentBoxLineEnd() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        textAreaControl.setText("Alpha\nBeta");
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(120));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(80));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 120);

        widget.onFocusTraversalEntered(false);
        ControlTestRenderContext renderContext = new ControlTestRenderContext(200, 120);
        widget.render(renderContext);

        ControlTestRenderContext.TextCall betaText = findTextCall(renderContext, "Beta");
        ControlTestRenderContext.FillRectCall caret = findCaretFillRect(renderContext);

        Assert.assertNotNull(betaText);
        Assert.assertNotNull(caret);
        Assert.assertEquals(betaText.x + renderContext.measureTextWidth("Beta", TextContentMode.UILIB_RAW),
                caret.left);
        Assert.assertEquals(betaText.y, caret.top);
        Assert.assertEquals(caret.left + 2, caret.right);
        Assert.assertEquals(betaText.y + 18, caret.bottom);
    }

    /**
     * 验证 textarea 光标使用独立高对比颜色，不被普通文本色覆盖到不可见。
     */
    @Test
    public void shouldRenderCaretWithDedicatedColor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document)
                .setText("Visible caret")
                .setTextColors(0x00000000, 0xFF777799, 0xFF666677, 0x664F86F7)
                .setCaretColor(0xFFFFCC00);
        root.style()
                .setWidth(UiStyleLength.px(220))
                .setHeight(UiStyleLength.px(90));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(54));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 220, 90,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 220, 90);

        widget.onFocusTraversalEntered(false);
        ControlTestRenderContext renderContext = new ControlTestRenderContext(220, 90);
        widget.render(renderContext);

        ControlTestRenderContext.FillRectCall caret = findFillRectByColor(renderContext, 0xFFFFCC00);
        Assert.assertNotNull(caret);
        Assert.assertEquals(2, caret.right - caret.left);
        Assert.assertEquals(18, caret.bottom - caret.top);
    }

    /**
     * 验证 widget 位于非零屏幕坐标时，textarea 光标仍使用屏幕坐标与文本对齐。
     */
    @Test
    public void shouldRenderCaretAtScreenOffsetWhenWidgetOffsetIsNonZero() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        textAreaControl.setText("Offset");
        root.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(90));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(140))
                .setHeight(UiStyleLength.px(54));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 180, 90,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(30, 20, 180, 90);

        widget.onFocusTraversalEntered(false);
        ControlTestRenderContext renderContext = new ControlTestRenderContext(240, 160);
        widget.render(renderContext);

        ControlTestRenderContext.TextCall text = findTextCall(renderContext, "Offset");
        ControlTestRenderContext.FillRectCall caret = findCaretFillRect(renderContext);

        Assert.assertNotNull(text);
        Assert.assertNotNull(caret);
        Assert.assertEquals(text.x + renderContext.measureTextWidth("Offset", TextContentMode.UILIB_RAW), caret.left);
        Assert.assertEquals(text.y, caret.top);
    }

    /**
     * 验证超长逻辑行会按内容宽度软换行，而不是继续生成横向滚动。
     */
    @Test
    public void shouldSoftWrapLongLogicalLineToTextareaWidth() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        textAreaControl.setText("abcdefghi");
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(120));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(48))
                .setHeight(UiStyleLength.px(44));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 120);

        ControlTestRenderContext renderContext = new ControlTestRenderContext(200, 120);
        widget.render(renderContext);

        Assert.assertNotNull(findTextCall(renderContext, "abcd"));
        Assert.assertNotNull(findTextCall(renderContext, "efgh"));
        Assert.assertNotNull(findTextCall(renderContext, "i"));
        Assert.assertEquals(0, widget.getScrollLeft(textAreaControl.getElement()));
        Assert.assertTrue(widget.getMaxScrollTop(textAreaControl.getElement()) > 0);
    }

    /**
     * 验证点击软换行后的第二条视觉行时，光标会落在同一逻辑行的对应位置。
     */
    @Test
    public void shouldPlaceCaretByClickedSoftWrappedVisualLine() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        textAreaControl.setText("abcdefghi");
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(120));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(48))
                .setHeight(UiStyleLength.px(62));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 120);

        ControlTestRenderContext renderContext = new ControlTestRenderContext(200, 120);
        widget.render(renderContext);
        DocumentElementBounds bounds = textAreaControl.getElement().getDocumentBounds();
        int clickX = bounds.getContentLeft() + renderContext.measureTextWidth("efg", TextContentMode.UILIB_RAW);
        int clickY = bounds.getContentTop() + 18 + 4;
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, clickX, clickY, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, clickX, clickY, 0, 0, 0, 0, 2L));
        widget.onTextInput(new UiTextInputEvent("X", 3L));

        Assert.assertEquals("abcdefgXhi", textAreaControl.getText());
    }

    /**
     * 验证上下方向键在软换行产生的视觉行之间移动，而不是直接跳过整条逻辑行。
     */
    @Test
    public void shouldMoveCaretVerticallyAcrossSoftWrappedVisualLines() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        textAreaControl.setText("abcdefghi");
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(120));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(48))
                .setHeight(UiStyleLength.px(62));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 120);

        widget.onFocusTraversalEntered(false);
        widget.render(new ControlTestRenderContext(200, 120));
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_UP, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));
        widget.onTextInput(new UiTextInputEvent("X", 2L));

        Assert.assertEquals("abcdeXfghi", textAreaControl.getText());
    }

    /**
     * 验证 CJK 文本第二行的 caret 像素位置对齐到前缀宽度（每码点等宽，前缀向量增量一致）。
     */
    @Test
    public void shouldRenderCaretForCjkMultilineText() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        textAreaControl.setText("中文\n汉字");
        root.style()
                .setWidth(UiStyleLength.px(220))
                .setHeight(UiStyleLength.px(120));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(80));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 220, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 220, 120);

        widget.onFocusTraversalEntered(false);
        ControlTestRenderContext renderContext = new ControlTestRenderContext(220, 120);
        widget.render(renderContext);

        ControlTestRenderContext.TextCall secondLine = findTextCall(renderContext, "汉字");
        ControlTestRenderContext.FillRectCall caret = findCaretFillRect(renderContext);
        Assert.assertNotNull(secondLine);
        Assert.assertNotNull(caret);
        // 光标在第二行末尾："汉字" 末尾 = 行起点 X + measureTextWidth("汉字")
        Assert.assertEquals(secondLine.x + renderContext.measureTextWidth("汉字", TextContentMode.UILIB_RAW),
                caret.left);
        Assert.assertEquals(secondLine.y, caret.top);
    }

    /**
     * 验证空行（仅换行符）也能正确渲染 caret，前缀向量退化为零向量不抛异常。
     */
    @Test
    public void shouldRenderCaretOnEmptyLine() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextAreaControl textAreaControl = new DocumentTextAreaControl(document);
        textAreaControl.setText("a\n\nb");
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(120));
        textAreaControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(90));
        root.append(textAreaControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 120);

        widget.onFocusTraversalEntered(false);
        // 光标移到第二行（空行）行首：上、上从末尾移动
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_UP, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));
        ControlTestRenderContext renderContext = new ControlTestRenderContext(200, 120);
        widget.render(renderContext);

        ControlTestRenderContext.FillRectCall caret = findCaretFillRect(renderContext);
        Assert.assertNotNull("空行也应渲染 caret", caret);
        Assert.assertEquals(2, caret.right - caret.left);
        Assert.assertEquals(18, caret.bottom - caret.top);
    }

    private static ControlTestRenderContext.TextCall findTextCall(ControlTestRenderContext renderContext,
            String text) {
        for (ControlTestRenderContext.TextCall textCall : renderContext.textCalls) {
            if (text.equals(textCall.text)) {
                return textCall;
            }
        }
        return null;
    }

    private static ControlTestRenderContext.FillRectCall findCaretFillRect(ControlTestRenderContext renderContext) {
        for (ControlTestRenderContext.FillRectCall fillRectCall : renderContext.fillRectCalls) {
            if (fillRectCall.right - fillRectCall.left == 2
                    && fillRectCall.bottom - fillRectCall.top == 18
                    && fillRectCall.color == 0xFFFFFFFF) {
                return fillRectCall;
            }
        }
        return null;
    }

    private static ControlTestRenderContext.FillRectCall findFillRectByColor(ControlTestRenderContext renderContext,
            int color) {
        for (ControlTestRenderContext.FillRectCall fillRectCall : renderContext.fillRectCalls) {
            if (fillRectCall.color == color) {
                return fillRectCall;
            }
        }
        return null;
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
