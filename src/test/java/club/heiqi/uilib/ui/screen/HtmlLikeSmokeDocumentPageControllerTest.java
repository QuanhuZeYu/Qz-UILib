package club.heiqi.uilib.ui.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.control.UiControlRuntimeAdapters;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
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

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(0), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(fixture.controller.getHtmlLikeDocumentWidget().isViewportRootScrollingEnabled());
        Assert.assertEquals(3, fixture.controller.getHtmlLikeDocumentWidget().getDocument()
                .getRootElement().getChildren().size());

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "HTML-like Smoke Lab"));
        Assert.assertTrue(containsText(texts, "UiDocument -> style -> layout -> paint command -> UiRenderContext"));
        Assert.assertTrue(containsText(texts, "Same-layer sampling grid"));
        Assert.assertTrue(containsText(texts, "ABS containing probe"));
        Assert.assertTrue(containsText(texts, "static wrapper is not anchor"));
        Assert.assertTrue(containsText(texts, "ABS card anchor"));
        Assert.assertTrue(containsText(texts, "ABS badge"));
        Assert.assertTrue(containsText(texts, "pink stripe behind glass"));
        Assert.assertTrue(containsText(texts, "amber UI behind this card"));
        Assert.assertTrue(containsText(texts, "Backdrop glass overlap: blur 14px / saturate 140%"));
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
        Assert.assertEquals(0xF00B1020, firstCall.surfaceStyle.fillColor);
        Assert.assertFalse(renderContext.clipCalls.isEmpty());
        Assert.assertTrue(renderContext.popClipCount > 0);
        Assert.assertEquals(1, renderContext.backdropCalls.size());
        Assert.assertEquals(14, renderContext.backdropCalls.get(0).blurRadius);
        Assert.assertEquals(1.4F, renderContext.backdropCalls.get(0).saturation, 0.001F);
        Assert.assertEquals(12, renderContext.backdropCalls.get(0).cornerRadius);
        Assert.assertTrue(containsFillColor(renderContext.drawCalls, 0xFFFFD166));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "ABS card anchor"));
        assertAbsoluteProbeIsAnchoredToCard(renderContext.drawCalls);
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
        Assert.assertTrue(widget.getActiveAnimationCount() >= 2);
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

    private static List<String> collectDocumentTexts(HtmlLikeDocumentWidget widget) {
        List<String> texts = new ArrayList<String>();
        if (widget == null || widget.getDocument() == null) {
            return texts;
        }
        collectTextsFromNode(widget.getDocument().getRootElement(), texts);
        return texts;
    }

    private static void collectTextsFromNode(DocumentNode node, List<String> texts) {
        if (node.getNodeType() == DocumentNodeType.TEXT) {
            String text = ((TextNode) node).getText();
            if (text != null && !text.isEmpty()) {
                texts.add(text);
            }
        }
        if (node.getNodeType() == DocumentNodeType.ELEMENT) {
            ElementNode element = (ElementNode) node;
            for (DocumentNode child : element.getChildren()) {
                collectTextsFromNode(child, texts);
            }
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

    private static void assertAbsoluteProbeIsAnchoredToCard(List<DrawCall> drawCalls) {
        DrawCall staticWrapperCall = findFillColor(drawCalls, 0xFF111827);
        DrawCall nestedAbsoluteCall = findFillColor(drawCalls, 0xFFFFD166);
        Assert.assertNotNull(staticWrapperCall);
        Assert.assertNotNull(nestedAbsoluteCall);
        Assert.assertTrue(nestedAbsoluteCall.top < staticWrapperCall.top);
        Assert.assertTrue(nestedAbsoluteCall.right <= staticWrapperCall.right);
    }

    private static DrawCall findFillColor(List<DrawCall> drawCalls, int expectedColor) {
        for (DrawCall drawCall : drawCalls) {
            if (drawCall.surfaceStyle.fillColor == expectedColor) {
                return drawCall;
            }
        }
        return null;
    }

    /**
     * 页面控制器测试夹具。
     */
    private static final class TestFixture {

        private final UiDocumentTheme documentTheme = UiDocumentThemes.current();
        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(documentTheme, textMeasureService,
                UiControlRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
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
        private final List<BackdropCall> backdropCalls = new ArrayList<BackdropCall>();
        private int popClipCount;

        private RecordingUiRenderContext() {
            super(1024, 768, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(left, top, right, bottom, surfaceStyle));
        }

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                int cornerRadius) {
            backdropCalls.add(new BackdropCall(left, top, right, bottom, blurRadius, saturation, cornerRadius));
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
     * 单次 backdrop filter 投影记录。
     */
    private static final class BackdropCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int blurRadius;
        private final float saturation;
        private final int cornerRadius;

        private BackdropCall(int left, int top, int right, int bottom, int blurRadius, float saturation,
                int cornerRadius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.blurRadius = blurRadius;
            this.saturation = saturation;
            this.cornerRadius = cornerRadius;
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
