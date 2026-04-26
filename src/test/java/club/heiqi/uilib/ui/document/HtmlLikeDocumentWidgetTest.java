package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * `HtmlLikeDocumentWidget` 的后端适配契约测试。
 */
public class HtmlLikeDocumentWidgetTest {

    /**
     * 验证 HTML-like 文档可以通过 widget 后端绘制到 `UiRenderContext`。
     */
    @Test
    public void shouldRenderDocumentThroughWidgetBackend() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setHeight(UiStyleLength.px(24))
                .setBackgroundColor(0xFF102030)
                .setBorderColor(0xFF80A0FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(6));
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(17, 23, 120, 48);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertSame(document, widget.getDocument());
        Assert.assertEquals(2, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 17, 23, 137, 49, 0xFF102030, 0, 6);
        assertDrawCall(renderContext.drawCalls.get(1), 17, 23, 137, 49, 0, 0xFF80A0FF, 6);
    }

    /**
     * 验证空尺寸组件不会触发绘制。
     */
    @Test
    public void shouldIgnoreEmptyWidgetBounds() {
        UiDocument document = UiDocument.create();
        document.getRootElement().style().setBackgroundColor(0xFFFFFFFF);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 0, 48);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();

        widget.render(renderContext);

        Assert.assertTrue(renderContext.drawCalls.isEmpty());
    }

    /**
     * 验证 HTML-like 文档适配组件会使用注入的文本测量服务生成多行文本绘制命令。
     */
    @Test
    public void shouldRenderWrappedTextThroughWidgetBackend() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(24))
                .setTextColor(0xFFEFF6FF);
        root.appendText("abcdefg");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 80);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();

        widget.render(renderContext);

        Assert.assertEquals(3, renderContext.textCalls.size());
        assertTextCall(renderContext.textCalls.get(0), "abc", 5, 7, 0xFFEFF6FF, false);
        assertTextCall(renderContext.textCalls.get(1), "def", 5, 25, 0xFFEFF6FF, false);
        assertTextCall(renderContext.textCalls.get(2), "g", 5, 43, 0xFFEFF6FF, false);
    }

    private static void assertDrawCall(DrawCall drawCall, int left, int top, int right, int bottom, int fillColor,
            int borderColor, int cornerRadius) {
        Assert.assertEquals(left, drawCall.left);
        Assert.assertEquals(top, drawCall.top);
        Assert.assertEquals(right, drawCall.right);
        Assert.assertEquals(bottom, drawCall.bottom);
        Assert.assertEquals(fillColor, drawCall.surfaceStyle.fillColor);
        Assert.assertEquals(borderColor, drawCall.surfaceStyle.borderColor);
        Assert.assertEquals(cornerRadius, drawCall.surfaceStyle.cornerRadius);
    }

    private static void assertTextCall(TextCall textCall, String text, int x, int y, int color, boolean shadow) {
        Assert.assertEquals(text, textCall.text);
        Assert.assertEquals(x, textCall.x);
        Assert.assertEquals(y, textCall.y);
        Assert.assertEquals(color, textCall.color);
        Assert.assertEquals(shadow, textCall.shadow);
    }

    /**
     * 记录 surface 绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();
        private final List<TextCall> textCalls = new ArrayList<TextCall>();

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(left, top, right, bottom, surfaceStyle));
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
     * 单次文本绘制记录。
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
     * 供 widget 测试使用的确定性文本测量服务。
     */
    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 4;
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
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<String>();
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }
}
