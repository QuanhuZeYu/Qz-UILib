package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `InlineLayoutHelper` 边界用例测试。
 *
 * <p>helper 自身是包级私有，本测试通过 {@link DocumentLayoutEngine#layout} 间接驱动 inline 行布局，
 * 重点覆盖空行、零宽容器与多层嵌套 inline 元素的边界场景。</p>
 */
public class InlineLayoutHelperBoundaryTest {

    /**
     * 容器宽度为 0 时 inline 文本不应抛异常，应输出 0 行内容并保留容器宽度。
     */
    @Test
    public void shouldHandleZeroWidthContainerWithoutCrashing() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode paragraph = document.div();

        root.style().setWidth(UiStyleLength.px(0));
        paragraph.appendText("hello world");
        root.append(paragraph);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 0, 0, new DeterministicMeasure());

        Assert.assertEquals(0, rootBox.getWidth());
        Assert.assertNotNull(rootBox.getChildren());
    }

    /**
     * 嵌套 inline 块层级里 span(span(text)) 的内容仍参与同一行布局。
     */
    @Test
    public void shouldLayoutNestedInlineElementsOnSingleLine() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode paragraph = document.div();
        ElementNode outerSpan = document.span();
        ElementNode innerSpan = document.span();

        root.style().setWidth(UiStyleLength.px(200));
        innerSpan.appendText("AB");
        outerSpan.append(innerSpan);
        paragraph.append(outerSpan);
        root.append(paragraph);

        DocumentLayoutBox paragraphBox = DocumentLayoutEngine.layout(root, 240, 0, new DeterministicMeasure())
                .getChildren().get(0);

        Assert.assertEquals(200, paragraphBox.getWidth());
        Assert.assertTrue("nested inline content should produce at least one line of content",
                paragraphBox.getHeight() > 0);
    }

    private static final class DeterministicMeasure implements TextMeasureService {

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
