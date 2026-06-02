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
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiTestDocumentPageController` test P0 首页的黑盒测试。
 */
public class UiTestDocumentPageControllerTest {

    /**
     * 验证 `/qzuilib test` 当前构建 P0 语义首页。
     */
    @Test
    public void shouldBuildTestPageP0HomeDocumentTree() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(0), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(fixture.controller.getHtmlLikeDocumentWidget().isViewportRootScrollingEnabled());

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "Qz UILib Test 首页"));
        Assert.assertTrue(containsText(texts, "P0 语义首页"));
        Assert.assertTrue(containsText(texts, "DOM 与选择器语义"));
        Assert.assertTrue(containsText(texts, "CSS 级联与样式语义"));
        Assert.assertTrue(containsText(texts, "Layout 布局与尺寸语义"));
        Assert.assertTrue(containsText(texts, "Paint 绘制、命中与视觉语义"));
        Assert.assertTrue(containsText(texts, "Input、Controls、TextFont、Animation、RuntimeHost、RemoteNet 按规格后续接入"));
        Assert.assertFalse(containsText(texts, "HTML-like Smoke"));
        Assert.assertFalse(containsText(texts, "Glass Lab"));
    }

    /**
     * 验证 P0 首页直接展示统一运行时用例卡片字段。
     */
    @Test
    public void shouldExposeRuntimeCaseCardContract() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "用例编号"));
        Assert.assertTrue(containsText(texts, "覆盖语义"));
        Assert.assertTrue(containsText(texts, "自动断言"));
        Assert.assertTrue(containsText(texts, "操作步骤"));
        Assert.assertTrue(containsText(texts, "预期结果"));
        Assert.assertTrue(containsText(texts, "实际结果"));
        Assert.assertTrue(containsText(texts, "状态"));
        Assert.assertTrue(containsText(texts, "DOM-001"));
        Assert.assertTrue(containsText(texts, "CSS-001"));
        Assert.assertTrue(containsText(texts, "LAYOUT-001"));
        Assert.assertTrue(containsText(texts, "PAINT-001"));
        Assert.assertTrue(containsText(texts, "预期结果：点击执行后 A 节点移动到 B 节点后方"));
        Assert.assertTrue(containsText(texts, "预期结果：同一元素最终显示为 inline 指定颜色"));
        Assert.assertTrue(containsText(texts, "预期结果：三块内容从上到下排列"));
        Assert.assertTrue(containsText(texts, "预期结果：背景在最底层"));
    }

    /**
     * 验证运行时测试结果状态固定为规范文本。
     */
    @Test
    public void shouldExposeFixedRuntimeResultStatuses() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "未执行"));
        Assert.assertTrue(containsText(texts, "执行中"));
        Assert.assertTrue(containsText(texts, "通过：观察结果与预期一致"));
        Assert.assertTrue(containsText(texts, "失败：观察结果与预期不一致 - <差异说明>"));
    }

    /**
     * 验证环境信息会跟随运行时视图刷新。
     */
    @Test
    public void shouldRefreshRuntimeEnvironmentText() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.runtimeView.hostWidth = 960;
        fixture.runtimeView.hostHeight = 540;
        fixture.runtimeView.mouseX = 12;
        fixture.runtimeView.mouseY = 34;
        fixture.controller.beforeDocumentFrame();

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "Minecraft=1.7.10"));
        Assert.assertTrue(containsText(texts, "字体 epoch=1"));
        Assert.assertTrue(containsText(texts, "窗口尺寸=960x540"));
        Assert.assertTrue(containsText(texts, "鼠标=12,34"));
        Assert.assertTrue(containsText(texts, "网络传输模式=vanilla"));
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
        private final MutableRuntimeView runtimeView = new MutableRuntimeView();
        private final UiTestDocumentPageController controller = new UiTestDocumentPageController(documentUi,
                pageSurface, runtimeView);
    }

    private static final class MutableRuntimeView implements DocumentPageRuntimeView {

        private int hostWidth;
        private int hostHeight;
        private int mouseX;
        private int mouseY;

        @Override
        public int getHostWidth() {
            return hostWidth;
        }

        @Override
        public int getHostHeight() {
            return hostHeight;
        }

        @Override
        public int getMouseX() {
            return mouseX;
        }

        @Override
        public int getMouseY() {
            return mouseY;
        }

        @Override
        public UiRuntimeStats getUiRuntimeStats() {
            return UiRuntimeStats.empty();
        }
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
