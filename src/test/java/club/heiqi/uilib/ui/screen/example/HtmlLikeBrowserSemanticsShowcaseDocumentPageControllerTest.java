package club.heiqi.uilib.ui.screen.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `HtmlLikeBrowserSemanticsShowcaseDocumentPageController` 的页面集成契约测试。
 */
public class HtmlLikeBrowserSemanticsShowcaseDocumentPageControllerTest {

    /**
     * 验证浏览器语义展示页覆盖已补齐的实际能力分组。
     */
    @Test
    public void shouldBuildExpandedBrowserSemanticsShowcaseDocumentTree() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        HtmlLikeDocumentWidget widget = (HtmlLikeDocumentWidget) blocks.get(0);
        Assert.assertTrue(widget.isViewportRootScrollingEnabled());

        List<String> texts = collectDocumentTexts(widget);
        Assert.assertTrue(containsText(texts, "浏览器语义新功能展示"));
        Assert.assertTrue(containsText(texts, "高级选择器、伪类与伪元素"));
        Assert.assertTrue(containsText(texts, "DOM 批量操作与自定义事件"));
        Assert.assertTrue(containsText(texts, "HTML-like 语义元素"));
        Assert.assertTrue(containsText(texts, "文本排版控制"));
        Assert.assertTrue(containsText(texts, "布局约束与 flex 细节"));
        Assert.assertTrue(containsText(texts, "滚动条、程序化滚动与 visibility"));
        Assert.assertTrue(containsText(texts, "background-image、transform 与图片回退"));
        Assert.assertTrue(containsText(texts, "createDocumentFragment"));
        Assert.assertTrue(containsText(texts, "border-collapse: collapse"));
        Assert.assertTrue(containsText(texts, "scrollbar-color + scrollbar-width: thin"));
        Assert.assertTrue(containsText(texts, "background-image: options_background.png"));
        Assert.assertTrue(containsText(texts, "#semantic-link-target"));
        Assert.assertTrue(containsText(texts, "A. NOWRAP + ELLIPSIS"));
        Assert.assertTrue(containsText(texts, "只执行 scrollTo(0, 72)"));
        Assert.assertTrue(containsText(texts, "只执行末项 scrollIntoView()"));

        ElementNode root = widget.getDocument().getRootElement();
        Assert.assertNotNull(widget.getDocument().querySelector(".pseudo-card"));
        Assert.assertEquals(1, widget.getDocument().getElementsByClassName("selector-stage").size());
        Assert.assertTrue(widget.getDocument().getElementsByTagName("table").size() >= 1);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layoutViewportRoot(root, 900, 600,
                fixture.textMeasureService);
        Assert.assertTrue(rootBox.getChildren().size() >= 15);
    }

    /**
     * 收集文档树中的全部文本。
     *
     * @param widget HTML-like 文档组件
     * @return 文本列表
     */
    private static List<String> collectDocumentTexts(HtmlLikeDocumentWidget widget) {
        List<String> texts = new ArrayList<String>();
        if (widget == null || widget.getDocument() == null) {
            return texts;
        }
        collectTextsFromNode(widget.getDocument().getRootElement(), texts);
        return texts;
    }

    /**
     * 递归收集节点文本。
     *
     * @param node 当前节点
     * @param texts 文本输出列表
     */
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

    /**
     * 判断文本列表是否包含指定片段。
     *
     * @param texts 文本列表
     * @param expectedSnippet 期望片段
     * @return 是否命中
     */
    private static boolean containsText(List<String> texts, String expectedSnippet) {
        for (String text : texts) {
            if (text != null && text.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 页面控制器测试夹具。
     */
    private static final class TestFixture {

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final HtmlLikeBrowserSemanticsShowcaseDocumentPageController controller =
                new HtmlLikeBrowserSemanticsShowcaseDocumentPageController(documentUi, pageSurface);
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
