package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiTestDocumentPageController` test 首页与二级页的黑盒测试。
 */
public class UiTestDocumentPageControllerTest {

    /**
     * 验证 `/qzuilib test` 首页保留分组入口，但不再展示旧运行时测试内容。
     */
    @Test
    public void shouldBuildClearedRuntimeTestHomeDocumentTree() {
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
        Assert.assertTrue(containsText(texts, "当前运行时测试内容已清空"));
        Assert.assertTrue(containsText(texts, "二级页数量"));
        Assert.assertTrue(containsText(texts, "DOM 与选择器语义"));
        Assert.assertTrue(containsText(texts, "CSS 级联与样式语义"));
        Assert.assertTrue(containsText(texts, "Layout 布局与尺寸语义"));
        Assert.assertTrue(containsText(texts, "Paint 绘制、命中与视觉语义"));
        Assert.assertTrue(containsText(texts, "Input 输入与事件语义"));
        Assert.assertTrue(containsText(texts, "Controls 控件与表单语义"));
        Assert.assertTrue(containsText(texts, "TextFont 文本、字体与国际化语义"));
        Assert.assertTrue(containsText(texts, "Animation 动画与 Transition 语义"));
        Assert.assertTrue(containsText(texts, "RuntimeHost 宿主运行时语义"));
        Assert.assertTrue(containsText(texts, "RemoteNet 远程、配置与网络语义"));
        Assert.assertTrue(containsText(texts, "打开 DOM 二级页"));
        Assert.assertTrue(containsText(texts, "覆盖用例：0；P0 已接入：0；缺口：0"));
        Assert.assertTrue(containsText(texts, "入口状态：运行时卡片已清空，等待重新规划。"));
        Assert.assertFalse(containsText(texts, "DOM-001"));
        Assert.assertFalse(containsText(texts, "执行自动测试"));
        Assert.assertFalse(containsText(texts, "人工通过"));
        Assert.assertFalse(containsText(texts, "人工失败"));
        Assert.assertFalse(containsText(texts, "已接入 13 张运行时卡片"));
    }

    /**
     * 验证分组二级页只显示空态，不再展示旧用例卡片契约。
     */
    @Test
    public void shouldExposeEmptyGroupSubPageAfterRuntimeCasesCleared() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 DOM 二级页", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "Qz UILib Test / DOM 二级页"));
        Assert.assertTrue(containsText(texts, "本类型运行时测试内容已清空"));
        Assert.assertTrue(containsText(texts, "本类型暂无运行时卡片，暂不需要人工确认。"));
        Assert.assertTrue(containsText(texts, "当前运行时测试卡片规则已清空"));
        Assert.assertFalse(containsText(texts, "用例编号"));
        Assert.assertFalse(containsText(texts, "DOM-001"));
        Assert.assertFalse(containsText(texts, "CSS-001"));
        Assert.assertFalse(containsText(texts, "LAYOUT-001"));
        Assert.assertFalse(containsText(texts, "执行自动测试"));
        Assert.assertFalse(containsText(texts, "人工通过"));
        Assert.assertFalse(containsText(texts, "人工失败"));
    }

    /**
     * 验证空态下仍可在各分组二级页之间切换。
     */
    @Test
    public void shouldNavigateBetweenReservedGroupPages() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 DOM 二级页", 0);
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "CSS", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "Qz UILib Test / CSS 二级页"));
        Assert.assertTrue(containsText(texts, "本类型运行时测试内容已清空"));
        Assert.assertFalse(containsText(texts, "CSS-001"));
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

    private static void clickButtonByLabel(HtmlLikeDocumentWidget widget, String label, int occurrence) {
        List<ElementNode> buttons = new ArrayList<ElementNode>();
        collectButtonsByLabel(widget.getDocument().getRootElement(), label, buttons);
        Assert.assertTrue("找不到按钮：" + label + " #" + occurrence, buttons.size() > occurrence);
        ElementNode button = buttons.get(occurrence);
        Assert.assertNotNull(button.getClickHandler());
        button.getClickHandler().onClick(new DocumentElementClickEvent(button, button, 0, 0, 0, 0L));
    }

    private static void collectButtonsByLabel(DocumentNode node, String label, List<ElementNode> buttons) {
        if (node.getNodeType() != DocumentNodeType.ELEMENT) {
            return;
        }
        ElementNode element = (ElementNode) node;
        if ("button".equals(element.getTagName()) && containsText(collectElementTexts(element), label)) {
            buttons.add(element);
        }
        for (DocumentNode child : element.getChildren()) {
            collectButtonsByLabel(child, label, buttons);
        }
    }

    private static List<String> collectElementTexts(ElementNode element) {
        List<String> texts = new ArrayList<String>();
        collectTextsFromNode(element, texts);
        return texts;
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
