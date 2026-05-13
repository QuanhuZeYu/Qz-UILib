package club.heiqi.uilib.ui.dom.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * `DocumentTextInputControl` 的基础行为契约测试。
 */
public class DocumentTextInputControlTest {

    /**
     * 验证文本输入控件使用真实 input 语义。
     */
    @Test
    public void shouldUseInputElementSemantics() {
        UiDocument document = UiDocument.create();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);

        Assert.assertEquals("input", textInputControl.getElement().getTagName());
        Assert.assertEquals("text", textInputControl.getElement().getAttribute("type"));
        Assert.assertEquals("", textInputControl.getElement().getAttribute("value"));
    }

    /**
     * 验证文本输入控件接受输入并返回对应文本。
     */
    @Test
    public void shouldAcceptTextInputAndReturnText() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        root.append(textInputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        widget.onFocusTraversalEntered(true);
        widget.onTextInput(new UiTextInputEvent("Hello", 1L));
        widget.onTextInput(new UiTextInputEvent("World", 2L));

        Assert.assertEquals("HelloWorld", textInputControl.getText());
    }

    /**
     * 验证超长输入会被截断。
     */
    @Test
    public void shouldRespectMaxLength() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        textInputControl.setMaxLength(3);
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        root.append(textInputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        widget.onFocusTraversalEntered(true);
        widget.onTextInput(new UiTextInputEvent("ABCDEF", 1L));

        Assert.assertEquals("ABC", textInputControl.getText());
    }

    /**
     * 验证退格键删除末尾字符。
     */
    @Test
    public void shouldDeleteLastCharacterWithBackspace() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        textInputControl.setText("abcdef");
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        root.append(textInputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        widget.onFocusTraversalEntered(true);
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 2L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        Assert.assertEquals("abc", textInputControl.getText());
    }

    /**
     * 验证 Backspace 通过 offsetByCodePoints 安全删除补充平面字符。
     */
    @Test
    public void shouldDeleteSurrogatePairSafely() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        root.append(textInputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        widget.onFocusTraversalEntered(true);
        widget.onTextInput(new UiTextInputEvent("A\ud83d\ude00B", 1L));

        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 2L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 4L));

        Assert.assertEquals("", textInputControl.getText());
    }

    /**
     * 验证控制字符被过滤。
     */
    @Test
    public void shouldFilterControlCharacters() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        root.append(textInputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        widget.onFocusTraversalEntered(true);
        widget.onTextInput(new UiTextInputEvent("A\nB\rC\tD", 1L));

        Assert.assertEquals("ABCD", textInputControl.getText());
    }

    /**
     * 验证禁用状态禁止输入并无法聚焦。
     */
    @Test
    public void shouldBlockInputWhenDisabled() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        textInputControl.setEnabled(false);
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        root.append(textInputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        widget.onFocusTraversalEntered(true);
        widget.onTextInput(new UiTextInputEvent("Hello", 1L));

        Assert.assertEquals("", textInputControl.getText());
        Assert.assertNull(widget.getFocusedElement());
        Assert.assertFalse(textInputControl.isFocused());
    }

    /**
     * 验证禁用后重新启用不丢失内部焦点状态，无需重新遍历即可继续输入。
     */
    @Test
    public void shouldPreserveFocusAfterDisableReenable() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        root.append(textInputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        widget.onFocusTraversalEntered(true);
        widget.onTextInput(new UiTextInputEvent("A", 1L));
        Assert.assertEquals("A", textInputControl.getText());

        textInputControl.setEnabled(false);
        textInputControl.setEnabled(true);
        widget.onTextInput(new UiTextInputEvent("B", 2L));

        Assert.assertEquals("AB", textInputControl.getText());
    }

    /**
     * 验证聚焦态与非聚焦态的边框颜色区分。
     */
    @Test
    public void shouldShowFocusBorderColorWhenFocused() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        root.append(textInputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        RecordingUiRenderContext unfocusedRenderContext = new RecordingUiRenderContext();
        widget.render(unfocusedRenderContext);
        Assert.assertTrue(containsBorderColor(unfocusedRenderContext.drawCalls, 0xFF555577));

        widget.onFocusTraversalEntered(true);
        RecordingUiRenderContext focusedRenderContext = new RecordingUiRenderContext();
        widget.render(focusedRenderContext);
        Assert.assertTrue(containsBorderColor(focusedRenderContext.drawCalls, 0xFF5A9EF7));
    }

    /**
     * 验证占位文本在内容为空时展示。
     */
    @Test
    public void shouldDisplayPlaceholderWhenEmpty() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        textInputControl.setPlaceholder("Enter text here...");
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        root.append(textInputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        Assert.assertEquals("", textInputControl.getText());
        Assert.assertEquals("", textInputControl.getElement().getAttribute("value"));
        Assert.assertEquals("Enter text here...", textInputControl.getElement().getAttribute("placeholder"));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Enter text here..."));

        widget.onFocusTraversalEntered(true);
        widget.onTextInput(new UiTextInputEvent("Hi", 1L));
        Assert.assertEquals("Hi", textInputControl.getElement().getAttribute("value"));
        Assert.assertEquals("Enter text here...", textInputControl.getElement().getAttribute("placeholder"));
        RecordingUiRenderContext filledRenderContext = new RecordingUiRenderContext();
        widget.render(filledRenderContext);
        Assert.assertTrue(containsTextCall(filledRenderContext.textCalls, "Hi"));
    }

    /**
     * 验证文本变更处理器在每次内容变化时被调用。
     */
    @Test
    public void shouldFireChangeHandlerOnEveryChange() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        final List<String> changeTexts = new ArrayList<String>();
        textInputControl.setChangeHandler(new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                changeTexts.add(event.getText());
            }
        });
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        root.append(textInputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        widget.onFocusTraversalEntered(true);
        widget.onTextInput(new UiTextInputEvent("A", 1L));
        widget.onTextInput(new UiTextInputEvent("B", 2L));
        widget.onTextInput(new UiTextInputEvent("C", 3L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));

        Assert.assertEquals(4, changeTexts.size());
        Assert.assertEquals("A", changeTexts.get(0));
        Assert.assertEquals("AB", changeTexts.get(1));
        Assert.assertEquals("ABC", changeTexts.get(2));
        Assert.assertEquals("AB", changeTexts.get(3));
    }

    /**
     * 验证扩展键盘处理器可接收回车，同时不影响输入框原有退格行为。
     */
    @Test
    public void shouldSupportAdditionalKeyHandlerWithoutBreakingBackspace() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        final List<Integer> enterKeys = new ArrayList<Integer>();
        textInputControl.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (event.getAction() != UiKeyEvent.Action.PRESSED) {
                    return false;
                }
                if (event.getKeyCode() != Keyboard.KEY_RETURN) {
                    return false;
                }
                enterKeys.add(Integer.valueOf(event.getKeyCode()));
                return true;
            }
        });
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        textInputControl.getElement().style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        root.append(textInputControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        widget.onFocusTraversalEntered(true);
        widget.onTextInput(new UiTextInputEvent("AB", 1L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 2L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        Assert.assertEquals("A", textInputControl.getText());
        Assert.assertEquals(1, enterKeys.size());
        Assert.assertEquals(Integer.valueOf(Keyboard.KEY_RETURN), enterKeys.get(0));
    }

    /**
     * 验证通过 setText 外部设置文本不触发变更处理器，只有用户交互才触发。
     */
    @Test
    public void shouldNotFireChangeHandlerOnProgrammaticSetText() {
        UiDocument document = UiDocument.create();
        DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        final List<String> changeTexts = new ArrayList<String>();
        textInputControl.setChangeHandler(new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                changeTexts.add(event.getText());
            }
        });

        textInputControl.setText("Initial");

        Assert.assertEquals(0, changeTexts.size());
        Assert.assertEquals("Initial", textInputControl.getText());
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

    private static boolean containsTextCall(List<TextCall> textCalls, String expectedText) {
        for (TextCall textCall : textCalls) {
            if (expectedText.equals(textCall.text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 记录 surface 与 text 绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();
        private final List<TextCall> textCalls = new ArrayList<TextCall>();

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(surfaceStyle));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            textCalls.add(new TextCall(text));
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
     * 单次文本绘制记录。
     */
    private static final class TextCall {

        private final String text;

        private TextCall(String text) {
            this.text = text;
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
