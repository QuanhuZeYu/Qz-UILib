package club.heiqi.uilib.internal.devtools.pages;

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
 * `UiFrameworkStructureAuditDocumentPageController` 的页面集成契约测试。
 */
public class UiFrameworkStructureAuditDocumentPageControllerTest {

    /**
     * 验证结构审查展示页会挂接独立 HTML-like 文档并渲染审查关键内容。
     */
    @Test
    public void shouldBuildUiFrameworkStructureAuditDocumentTree() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(0), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(fixture.controller.getHtmlLikeDocumentWidget().isViewportRootScrollingEnabled());

        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        ElementNode root = widget.getDocument().getRootElement();
        Assert.assertEquals("ui-framework-structure-audit", root.getAttribute("data-diagnostic-page"));

        List<String> texts = collectDocumentTexts(widget);
        Assert.assertTrue(containsText(texts, "UI 框架结构审查展示"));
        Assert.assertTrue(containsText(texts, "REVIEW-20260520"));
        Assert.assertTrue(containsText(texts, "dom + style"));
        Assert.assertTrue(containsText(texts, "P0 / 低风险高收益"));
        Assert.assertTrue(containsText(texts, "DocumentLayoutEngine"));
        Assert.assertTrue(containsText(texts, "审查边界"));

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layoutViewportRoot(root, 980, 640,
                fixture.textMeasureService);
        Assert.assertTrue(rootBox.getChildren().size() >= 6);
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
        private final UiFrameworkStructureAuditDocumentPageController controller =
                new UiFrameworkStructureAuditDocumentPageController(documentUi, pageSurface);
    }

    /**
     * 供测试使用的确定性文本测量服务。
     */
    private static final class DeterministicTextMeasureService implements TextMeasureService {

        /**
         * 返回固定字形纪元。
         */
        @Override
        public int getEpoch() {
            return 1;
        }

        /**
         * 按字符数返回固定宽度。
         */
        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        /**
         * 返回固定行高。
         */
        @Override
        public int getLineHeight() {
            return 9;
        }

        /**
         * 测试中不裁剪文本。
         */
        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            return text == null ? "" : text;
        }

        /**
         * 测试中按单行返回文本。
         */
        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
