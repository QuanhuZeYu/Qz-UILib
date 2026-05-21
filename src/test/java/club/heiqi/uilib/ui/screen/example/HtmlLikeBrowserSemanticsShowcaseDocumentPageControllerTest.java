package club.heiqi.uilib.ui.screen.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.style.values.UiTransform;
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
        Assert.assertTrue(containsText(texts, "scrollbar-width:none：只隐藏滚动条，内容仍可见"));
        Assert.assertTrue(containsText(texts, "内容行 1（滚动条本身隐藏）"));
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
     * 验证 hover / focus 反馈不会崩溃，并会把预期样式直接回写到卡片。
     */
    @Test
    public void shouldApplyVisibleHoverAndFocusFeedbackWithoutCrashing() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = (HtmlLikeDocumentWidget) fixture.pageSurface.getBlocks().get(0);

        ElementNode hoverCard = findElementContainingText(widget.getDocument().getRootElement(),
                "hover 我：应明显变亮并上移");
        Assert.assertNotNull(hoverCard);
        Assert.assertNotNull(hoverCard.getHoverHandler());
        hoverCard.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(hoverCard, hoverCard, true, 0, 0, 1L));
        Assert.assertEquals(Integer.valueOf(0xFF38BDF8), Integer.valueOf(hoverCard.style().getBackgroundColor()));
        Assert.assertEquals(Integer.valueOf(0xFFE0F2FE), Integer.valueOf(hoverCard.style().getBorderColor()));
        Assert.assertEquals(UiTransform.translate(0.0F, -4.0F), hoverCard.style().getTransform());
        hoverCard.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(hoverCard, hoverCard, false, 0, 0, 2L));
        Assert.assertEquals(Integer.valueOf(0xFF1A2A44), Integer.valueOf(hoverCard.style().getBackgroundColor()));
        Assert.assertEquals(UiTransform.identity(), hoverCard.style().getTransform());

        ElementNode focusCard = findElementContainingText(widget.getDocument().getRootElement(),
                "点击聚焦：:focus / :focus-visible");
        Assert.assertNotNull(focusCard);
        Assert.assertNotNull(focusCard.getFocusHandler());
        focusCard.getFocusHandler().onFocusChanged(new DocumentElementFocusEvent(focusCard, true, true));
        Assert.assertNotNull(focusCard.style().getOutline());
        focusCard.getFocusHandler().onFocusChanged(new DocumentElementFocusEvent(focusCard, false, false));
        Assert.assertNull(focusCard.style().getOutline());
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
     * 在文档树中查找包含指定直系文本的元素。
     */
    private static ElementNode findElementContainingText(ElementNode element, String expectedText) {
        if (element == null) {
            return null;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child.getNodeType() == DocumentNodeType.TEXT) {
                String text = ((TextNode) child).getText();
                if (text != null && text.contains(expectedText)) {
                    return element;
                }
            } else if (child.getNodeType() == DocumentNodeType.ELEMENT) {
                ElementNode found = findElementContainingText((ElementNode) child, expectedText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
