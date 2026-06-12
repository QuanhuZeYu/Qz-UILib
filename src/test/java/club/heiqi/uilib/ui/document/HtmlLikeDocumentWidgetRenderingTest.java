package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertTextCall;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.RecordingUiRenderContext;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.TextCall;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * `HtmlLikeDocumentWidget` 的文本、图片回退和字体绘制契约测试。
 */
public class HtmlLikeDocumentWidgetRenderingTest {

    /**
     * 验证 HTML-like 文档适配组件会使用注入的文本测量服务生成多行文本绘制命令。
     */
    @Test
    public void shouldRenderWrappedTextThroughWidgetBackend() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(24))
                .setOverflowWrap(UiOverflowWrap.BREAK_WORD)
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

    /**
     * 验证 HTML-like 文本节点默认按 UILib 原始文本模式绘制。
     */
    @Test
    public void shouldRenderTextNodesInUiLibRawModeByDefault() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style().setWidth(UiStyleLength.px(80));
        root.appendText("价格：§a100金币");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 48);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertEquals(1, renderContext.textCalls.size());
        Assert.assertEquals("价格：§a100金币", renderContext.textCalls.get(0).text);
        Assert.assertEquals(TextContentMode.UILIB_RAW, renderContext.textCalls.get(0).textContentMode);
    }

    /**
     * 验证文本节点可显式切回 Minecraft 文本模式。
     */
    @Test
    public void shouldAllowExplicitMinecraftFormattedTextNodes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style().setWidth(UiStyleLength.px(80));
        root.appendMinecraftText("价格：§a100金币");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 48);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertFalse(renderContext.textCalls.isEmpty());
        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, renderContext.textCalls.get(0).textContentMode);
    }

    /**
     * 验证 img 加载失败时会绘制 alt 文本回退，而不是静默空白。
     */
    @Test
    public void shouldRenderAltFallbackWhenImageLoadFails() {
        DocumentRemoteImageCache.getInstance().clearForTesting();
        DocumentRemoteImageCache.getInstance().putFailedForTesting("https://example.test/missing.png");

        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode image = document.img();
        image.setAttribute("src", "https://example.test/missing.png");
        image.setAttribute("alt", "Missing icon");
        image.style().setWidth(UiStyleLength.px(72)).setHeight(UiStyleLength.px(24));
        root.append(image);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 60,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 60);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertTrue(renderContext.hostImageCalls.isEmpty());
        Assert.assertFalse(renderContext.textCalls.isEmpty());
        Assert.assertEquals("Missing icon", renderContext.textCalls.get(0).text);
    }

    /**
     * 验证字体粗细和斜体会进入文本绘制调用。
     */
    @Test
    public void shouldRenderTextWithFontWeightAndFontStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setFontWeight(UiFontWeight.BOLD)
                .setFontStyle(UiFontStyle.ITALIC);
        root.appendText("bold italic");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 40);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertFalse(renderContext.textCalls.isEmpty());
        for (TextCall textCall : renderContext.textCalls) {
            Assert.assertEquals(UiFontWeight.BOLD, textCall.fontWeight);
            Assert.assertEquals(UiFontStyle.ITALIC, textCall.fontStyle);
        }
    }

    /**
     * 验证 CSS font-size 会同时影响布局尺寸和文本绘制快照。
     */
    @Test
    public void shouldRenderTextWithFontSizeSnapshot() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setFontSize(UiStyleLength.px(12));
        root.appendText("size");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 40);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertEquals(1, renderContext.textCalls.size());
        TextCall textCall = renderContext.textCalls.get(0);
        Assert.assertEquals("size", textCall.text);
        Assert.assertEquals(12, textCall.fontSizePx);
        Assert.assertEquals(21, widget.resolveLayoutBoxForTest().getTextRuns().get(0).getWidth());
        Assert.assertEquals(12, widget.resolveLayoutBoxForTest().getTextRuns().get(0).getHeight());
    }
}
