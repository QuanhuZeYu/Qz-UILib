package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiRuntimeSelfTestDocumentPageController` 的页面集成契约测试。
 */
public class UiRuntimeSelfTestDocumentPageControllerTest {

    /**
     * 验证运行时自检页会构建浏览器事件语义自检入口与日志区。
     */
    @Test
    public void shouldBuildRuntimeSelfTestDocumentTree() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);

        HtmlLikeDocumentWidget widget = (HtmlLikeDocumentWidget) blocks.get(0);
        Assert.assertTrue(widget.isViewportRootScrollingEnabled());

        List<String> texts = collectDocumentTexts(widget.getDocument().getRootElement());
        Assert.assertTrue(containsText(texts, "运行时自检"));
        Assert.assertTrue(containsText(texts, "浏览器事件语义"));
        Assert.assertTrue(containsText(texts, "运行时日志（最近 18 条）"));
        Assert.assertTrue(containsText(texts, "全部依次执行"));

        Assert.assertNotNull(findElementByAttribute(widget.getDocument().getRootElement(), "data-runtime-self-test",
                "浏览器事件语义"));
    }

    private static List<String> collectDocumentTexts(ElementNode root) {
        List<String> texts = new ArrayList<String>();
        collectDocumentTexts(root, texts);
        return texts;
    }

    private static void collectDocumentTexts(DocumentNode node, List<String> texts) {
        if (node == null) {
            return;
        }
        if (node.getNodeType() == DocumentNodeType.TEXT) {
            String text = ((TextNode) node).getText();
            if (text != null && !text.isEmpty()) {
                texts.add(text);
            }
            return;
        }
        if (node.getNodeType() == DocumentNodeType.ELEMENT) {
            for (DocumentNode child : ((ElementNode) node).getChildren()) {
                collectDocumentTexts(child, texts);
            }
        }
    }

    private static boolean containsText(List<String> texts, String expectedSnippet) {
        for (String text : texts) {
            if (text != null && text.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    private static ElementNode findElementByAttribute(ElementNode element, String attributeName, String expectedValue) {
        if (element == null) {
            return null;
        }
        if (expectedValue.equals(element.getAttribute(attributeName))) {
            return element;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child.getNodeType() != DocumentNodeType.ELEMENT) {
                continue;
            }
            ElementNode found = findElementByAttribute((ElementNode) child, attributeName, expectedValue);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static final class TestFixture {

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final UiRuntimeSelfTestDocumentPageController controller =
                new UiRuntimeSelfTestDocumentPageController(documentUi, pageSurface);
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
            return text == null ? "" : text;
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return java.util.Collections.singletonList(text == null ? "" : text);
        }
    }
}
