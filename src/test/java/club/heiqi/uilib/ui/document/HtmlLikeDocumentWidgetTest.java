package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertDrawCall;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.RecordingUiRenderContext;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `HtmlLikeDocumentWidget` 的基础后端适配契约测试。
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
                .setBorderStyle(UiBorderStyle.SOLID)
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
}
