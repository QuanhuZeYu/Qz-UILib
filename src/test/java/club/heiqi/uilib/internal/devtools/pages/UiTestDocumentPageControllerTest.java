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
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiTestDocumentPageController` 重构规划页的黑盒测试。
 */
public class UiTestDocumentPageControllerTest {

    /**
     * 验证 `/qzuilib test` 当前只构建重构规划页。
     */
    @Test
    public void shouldBuildTestPageRebuildPlannerDocumentTree() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(0), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(fixture.controller.getHtmlLikeDocumentWidget().isViewportRootScrollingEnabled());

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "Qz UILib Test 页面重构规划"));
        Assert.assertTrue(containsText(texts, "旧 `/qzuilib test` 子页已清空"));
        Assert.assertTrue(containsText(texts, "DOM 与选择器语义"));
        Assert.assertTrue(containsText(texts, "CSS 级联与样式语义"));
        Assert.assertTrue(containsText(texts, "布局与尺寸语义"));
        Assert.assertTrue(containsText(texts, "绘制与视觉语义"));
        Assert.assertTrue(containsText(texts, "输入与事件语义"));
        Assert.assertTrue(containsText(texts, "控件与表单语义"));
        Assert.assertTrue(containsText(texts, "动画与 transition 语义"));
        Assert.assertTrue(containsText(texts, "远程、配置与网络语义"));
    }

    /**
     * 验证规划页直接展示人工预期文本规范。
     */
    @Test
    public void shouldExposeManualExpectationTextContract() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "预期结果：点击按钮后计数加 1"));
        Assert.assertTrue(containsText(texts, "预期结果：滚轮先记录 wheel 事件"));
        Assert.assertTrue(containsText(texts, "预期结果：远程页面点击提交后服务端返回结果页"));
        Assert.assertTrue(containsText(texts, "通过：观察结果与预期一致"));
        Assert.assertTrue(containsText(texts, "失败：观察结果与预期不一致"));
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

    private static boolean containsText(List<String> texts, String expectedSnippet) {
        for (String text : texts) {
            if (text != null && text.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    private static final class TestFixture {

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final UiTestDocumentPageController controller = new UiTestDocumentPageController(documentUi, pageSurface);
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
        public java.util.List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
