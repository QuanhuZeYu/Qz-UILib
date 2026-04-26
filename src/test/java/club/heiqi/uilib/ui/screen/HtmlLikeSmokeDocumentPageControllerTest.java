package club.heiqi.uilib.ui.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.UiControlRuntimeAdapters;
import club.heiqi.uilib.ui.document.DocumentPageWidget;
import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.theme.UiDocumentThemes;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `HtmlLikeSmokeDocumentPageController` 的页面集成契约测试。
 */
public class HtmlLikeSmokeDocumentPageControllerTest {

    /**
     * 验证 smoke 子页会挂接 HTML-like 文档适配组件。
     */
    @Test
    public void shouldBuildHtmlLikeSmokeDocumentTree() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<Widget> blocks = getDocumentBlocks(fixture.pagePanel);
        Assert.assertEquals(3, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof DocumentTextWidget);
        Assert.assertTrue(blocks.get(1) instanceof DocumentTextWidget);
        Assert.assertTrue(blocks.get(2) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(2), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertEquals(3, fixture.controller.getHtmlLikeDocumentWidget().getDocument()
                .getRootElement().getChildren().size());

        List<String> labelTexts = collectLabelTexts(fixture.pagePanel);
        Assert.assertTrue(containsText(labelTexts, "HTML-like Smoke"));
        Assert.assertTrue(containsText(labelTexts, "UiDocument -> style -> layout -> paint command -> UiRenderContext"));
    }

    /**
     * 验证 smoke 子页中的 HTML-like 组件能产生真实 surface 绘制调用。
     */
    @Test
    public void shouldRenderSmokeDocumentToUiRenderContext() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();

        widget.render(renderContext);

        Assert.assertFalse(renderContext.drawCalls.isEmpty());
        DrawCall firstCall = renderContext.drawCalls.get(0);
        Assert.assertEquals(31, firstCall.left);
        Assert.assertEquals(47, firstCall.top);
        Assert.assertEquals(791, firstCall.right);
        Assert.assertTrue(firstCall.bottom > firstCall.top);
        Assert.assertEquals(0xEE151A24, firstCall.surfaceStyle.fillColor);
        Assert.assertFalse(renderContext.clipCalls.isEmpty());
        Assert.assertTrue(renderContext.popClipCount > 0);
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "TEXT paint command"));
    }

    /**
     * 验证 smoke 子页中的 HTML-like 点击目标会产生可见反馈。
     */
    @Test
    public void shouldUpdateSmokeClickTargetWhenClicked() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);
        RecordingUiRenderContext initialRenderContext = new RecordingUiRenderContext();

        widget.render(initialRenderContext);
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls, "Click target: 0"));

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 70, 280, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 70, 280, 0, 0, 0, 0, 2L));
        RecordingUiRenderContext clickedRenderContext = new RecordingUiRenderContext();
        widget.render(clickedRenderContext);

        Assert.assertTrue(containsTextCall(clickedRenderContext.textCalls, "Click target: 1"));
        Assert.assertTrue(containsFillColor(clickedRenderContext.drawCalls, 0xFF3182CE));
    }

    /**
     * 验证 smoke 子页中的 HTML-like 输入目标会响应焦点、文本输入和退格键。
     */
    @Test
    public void shouldUpdateSmokeInputTargetWhenFocusedAndTyped() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);
        RecordingUiRenderContext initialRenderContext = new RecordingUiRenderContext();

        widget.render(initialRenderContext);
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls, "Type target: click then type"));

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 350, 280, 0, 0, 0, 0, 1L));
        widget.onTextInput(new UiTextInputEvent("A\nB", 2L));
        RecordingUiRenderContext typedRenderContext = new RecordingUiRenderContext();
        widget.render(typedRenderContext);

        Assert.assertTrue(containsTextCall(typedRenderContext.textCalls, "AB"));
        Assert.assertTrue(containsFillColor(typedRenderContext.drawCalls, 0xFFC53030));
        Assert.assertTrue(containsBorderColor(typedRenderContext.drawCalls, 0xFFD69E2E));

        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.REPEATED, false, false, false,
                false, 4L));
        RecordingUiRenderContext deletedRenderContext = new RecordingUiRenderContext();
        widget.render(deletedRenderContext);

        Assert.assertTrue(containsTextCall(deletedRenderContext.textCalls, "Type target: click then type"));
    }

    /**
     * 验证 smoke 子页中的 HTML-like Tab 目标会响应内部焦点遍历。
     */
    @Test
    public void shouldUpdateSmokeTabTargetWhenTraversed() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);

        widget.onFocusTraversalEntered(false);
        RecordingUiRenderContext inputFocusedRenderContext = new RecordingUiRenderContext();
        widget.render(inputFocusedRenderContext);

        Assert.assertTrue(containsTextCall(inputFocusedRenderContext.textCalls, "Type target: click then type"));
        Assert.assertTrue(containsFillColor(inputFocusedRenderContext.drawCalls, 0xFFD69E2E));

        Assert.assertTrue(widget.onFocusTraversal(false));
        RecordingUiRenderContext tabFocusedRenderContext = new RecordingUiRenderContext();
        widget.render(tabFocusedRenderContext);

        Assert.assertTrue(containsTextCall(tabFocusedRenderContext.textCalls, "Tab target: focused"));
        Assert.assertTrue(containsBorderColor(tabFocusedRenderContext.drawCalls, 0xFFD6BCFA));

        Assert.assertTrue(widget.onFocusTraversal(true));
        RecordingUiRenderContext reverseRenderContext = new RecordingUiRenderContext();
        widget.render(reverseRenderContext);

        Assert.assertTrue(containsTextCall(reverseRenderContext.textCalls, "Tab target: idle"));
        Assert.assertTrue(containsFillColor(reverseRenderContext.drawCalls, 0xFFD69E2E));
    }

    /**
     * 验证 smoke 子页中的 HTML-like 按钮控件可通过键盘激活。
     */
    @Test
    public void shouldUpdateSmokeButtonControlWhenKeyboardActivated() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);

        widget.onFocusTraversalEntered(false);
        Assert.assertTrue(widget.onFocusTraversal(false));
        Assert.assertTrue(widget.onFocusTraversal(false));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 4L));
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Button ctrl: 1"));
        Assert.assertTrue(containsFillColor(renderContext.drawCalls, 0xFF2C5282));
        Assert.assertTrue(containsBorderColor(renderContext.drawCalls, 0xFFBEE3F8));
    }

    /**
     * 验证 smoke 子页中的 HTML-like 开关控件默认开启，可点击切换关闭。
     */
    @Test
    public void shouldUpdateSmokeToggleControlWhenActivated() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);

        widget.onFocusTraversalEntered(false);
        widget.onFocusTraversal(false);
        widget.onFocusTraversal(false);
        widget.onFocusTraversal(false);
        RecordingUiRenderContext onRenderContext = new RecordingUiRenderContext();
        widget.render(onRenderContext);
        Assert.assertTrue(containsFillColor(onRenderContext.drawCalls, 0xFF48BB78));

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 710, 280, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 710, 280, 0, 0, 0, 0, 2L));
        RecordingUiRenderContext offRenderContext = new RecordingUiRenderContext();
        widget.render(offRenderContext);
        Assert.assertTrue(containsFillColor(offRenderContext.drawCalls, 0xFF718096));
    }

    private static List<Widget> getDocumentBlocks(DocumentPageWidget pagePanel) {
        List<Widget> pageChildren = pagePanel.getChildren();
        Assert.assertFalse(pageChildren.isEmpty());
        return pageChildren.get(0).getChildren();
    }

    private static List<String> collectLabelTexts(Widget root) {
        List<String> labelTexts = new ArrayList<String>();
        collectLabelTexts(root, labelTexts);
        return labelTexts;
    }

    private static void collectLabelTexts(Widget root, List<String> labelTexts) {
        if (root instanceof LabelWidget) {
            labelTexts.add(((LabelWidget) root).getText());
        }
        for (Widget child : root.getChildren()) {
            collectLabelTexts(child, labelTexts);
        }
    }

    private static boolean containsText(List<String> labelTexts, String expectedSnippet) {
        for (String labelText : labelTexts) {
            if (labelText != null && labelText.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTextCall(List<TextCall> textCalls, String expectedSnippet) {
        for (TextCall textCall : textCalls) {
            if (textCall.text != null && textCall.text.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
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

    /**
     * 页面控制器测试夹具。
     */
    private static final class TestFixture {

        private final UiDocumentTheme documentTheme = UiDocumentThemes.current();
        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(documentTheme, textMeasureService,
                UiControlRuntimeAdapters.empty());
        private final DocumentPageWidget pagePanel = new DocumentPageWidget(documentTheme, textMeasureService);
        private final DocumentPageAuthoringSurface pageSurface = DocumentPageAuthoringSurface.adapt(pagePanel);
        private final HtmlLikeSmokeDocumentPageController controller = new HtmlLikeSmokeDocumentPageController(
                documentUi, pageSurface, textMeasureService);
    }

    /**
     * 记录 surface 绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();
        private final List<ClipCall> clipCalls = new ArrayList<ClipCall>();
        private final List<TextCall> textCalls = new ArrayList<TextCall>();
        private int popClipCount;

        private RecordingUiRenderContext() {
            super(1024, 768, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(left, top, right, bottom, surfaceStyle));
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
            clipCalls.add(new ClipCall(left, top, right, bottom, cornerRadius));
        }

        @Override
        public void popClip() {
            popClipCount++;
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            textCalls.add(new TextCall(text, x, y, color, shadow));
        }
    }

    /**
     * 单次 surface 绘制记录。
     */
    private static final class DrawCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final UiSurfaceStyle surfaceStyle;

        private DrawCall(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.surfaceStyle = surfaceStyle;
        }
    }

    /**
     * 单次 clip 投影记录。
     */
    private static final class ClipCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int cornerRadius;

        private ClipCall(int left, int top, int right, int bottom, int cornerRadius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cornerRadius = cornerRadius;
        }
    }

    /**
     * 单次 HTML-like 文本绘制记录。
     */
    private static final class TextCall {

        private final String text;
        private final int x;
        private final int y;
        private final int color;
        private final boolean shadow;

        private TextCall(String text, int x, int y, int color, boolean shadow) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
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
