package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutTextRun;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `HtmlLikeDocumentWidget` 的 inline layout 缓存回归测试。
 */
public class HtmlLikeDocumentWidgetInlineLayoutCacheTest {

    /**
     * 验证同一 inline formatting 上下文内，前置文本导致换行变化后未脏 inline-block 可复用并平移到新行。
     */
    @Test
    public void shouldReuseCleanInlineBlockWhenInlineWrapChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        TextNode prefix = document.text("aa");
        ElementNode inlineBlock = document.div();
        inlineBlock.style()
                .setDisplay(UiDisplay.INLINE_BLOCK)
                .setWidth(UiStyleLength.px(24))
                .setHeight(UiStyleLength.px(10));
        inlineBlock.appendText("chip");
        root.style().setWidth(UiStyleLength.px(40));
        root.appendChild(prefix);
        root.append(inlineBlock);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 40, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 40, 80);

        widget.resolveLayoutBoxForTest();
        int measureCountAfterInitialLayout = textMeasureService.getMeasureCount();
        prefix.setText("aaaaa");
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot =
                widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox inlineBlockBox = findLayoutBox(rootBox, inlineBlock);

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertEquals(2, textMeasureService.getMeasureCount() - measureCountAfterInitialLayout);
        Assert.assertNotNull(inlineBlockBox);
        Assert.assertEquals(0, inlineBlockBox.getLeft());
        Assert.assertEquals(18, inlineBlockBox.getTop());
        assertTextRun(inlineBlockBox.getTextRuns().get(0), "chip", 0, 18, 24, 18);
    }

    /**
     * 验证复用 inline-block 时仍保留同一行内的落位、行高和后续文本基线位置。
     */
    @Test
    public void shouldKeepInlineBlockPlacementAndBaselineTextWhenReused() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        TextNode prefix = document.text("a");
        ElementNode inlineBlock = document.div();
        TextNode suffix = document.text("tail");
        inlineBlock.style()
                .setDisplay(UiDisplay.INLINE_BLOCK)
                .setWidth(UiStyleLength.px(16))
                .setHeight(UiStyleLength.px(30));
        root.style().setWidth(UiStyleLength.px(80));
        root.appendChild(prefix);
        root.append(inlineBlock);
        root.appendChild(suffix);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 80, 80);

        widget.resolveLayoutBoxForTest();
        int measureCountAfterInitialLayout = textMeasureService.getMeasureCount();
        prefix.setText("aa");
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot =
                widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox inlineBlockBox = findLayoutBox(rootBox, inlineBlock);

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertEquals(4, textMeasureService.getMeasureCount() - measureCountAfterInitialLayout);
        Assert.assertEquals(30, rootBox.getContentHeight());
        Assert.assertNotNull(inlineBlockBox);
        Assert.assertEquals(16, inlineBlockBox.getLeft());
        Assert.assertEquals(0, inlineBlockBox.getTop());
        assertTextRun(rootBox.getTextRuns().get(0), "aa", 0, 0, 16, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "tail", 32, 0, 32, 18);
    }

    /**
     * 验证文本测量 epoch 变化会让 inline-block 布局盒保守重算，避免复用旧文本尺寸。
     */
    @Test
    public void shouldInvalidateInlineBlockReuseWhenTextMeasureEpochChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode inlineBlock = document.div();
        inlineBlock.style()
                .setDisplay(UiDisplay.INLINE_BLOCK)
                .setWidth(UiStyleLength.px(32));
        inlineBlock.appendText("epoch");
        root.style().setWidth(UiStyleLength.px(80));
        root.append(inlineBlock);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 80, 80);

        widget.resolveLayoutBoxForTest();
        int measureCountAfterInitialLayout = textMeasureService.getMeasureCount();
        textMeasureService.advanceEpoch();
        widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot =
                widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertTrue(textMeasureService.getMeasureCount() > measureCountAfterInitialLayout);
    }

    /**
     * 验证含 absolute 后代的 inline-block 仍不复用，避免外部 containing block 坐标被错误平移。
     */
    @Test
    public void shouldNotReuseInlineBlockContainingOutOfFlowDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        TextNode prefix = document.text("aa");
        ElementNode inlineBlock = document.div();
        ElementNode absolute = document.div();
        inlineBlock.style()
                .setDisplay(UiDisplay.INLINE_BLOCK)
                .setWidth(UiStyleLength.px(24))
                .setHeight(UiStyleLength.px(10));
        absolute.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(7))
                .setTop(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(8))
                .setHeight(UiStyleLength.px(6));
        inlineBlock.append(absolute);
        root.style().setWidth(UiStyleLength.px(40));
        root.appendChild(prefix);
        root.append(inlineBlock);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 40, 80,
                new CountingTextMeasureService());
        widget.applyLayoutBounds(0, 0, 40, 80);

        widget.resolveLayoutBoxForTest();
        prefix.setText("aaaaa");
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot =
                widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox inlineBlockBox = findLayoutBox(rootBox, inlineBlock);
        DocumentLayoutBox absoluteBox = findLayoutBox(rootBox, absolute);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(inlineBlockBox);
        Assert.assertNotNull(absoluteBox);
        Assert.assertEquals(18, inlineBlockBox.getTop());
        Assert.assertEquals(7, absoluteBox.getLeft());
        Assert.assertEquals(4, absoluteBox.getTop());
    }

    private static DocumentLayoutBox findLayoutBox(DocumentLayoutBox box, ElementNode element) {
        if (box == null) {
            return null;
        }
        if (box.getElement() == element) {
            return box;
        }
        for (DocumentLayoutBox child : box.getChildren()) {
            DocumentLayoutBox match = findLayoutBox(child, element);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static void assertTextRun(DocumentLayoutTextRun run, String text, int left, int top, int width,
            int height) {
        Assert.assertEquals(text, run.getText());
        Assert.assertEquals(left, run.getLeft());
        Assert.assertEquals(top, run.getTop());
        Assert.assertEquals(width, run.getWidth());
        Assert.assertEquals(height, run.getHeight());
    }

    /**
     * 记录测量次数的确定性文本测量服务。
     */
    private static final class CountingTextMeasureService implements TextMeasureService {

        private int measureCount;
        private int epoch = 1;

        private int getMeasureCount() {
            return measureCount;
        }

        private void advanceEpoch() {
            epoch++;
        }

        @Override
        public int getEpoch() {
            return epoch;
        }

        @Override
        public int getStringWidth(String text) {
            measureCount++;
            return text == null ? 0 : text.length() * 4;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            measureCount++;
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            measureCount++;
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
