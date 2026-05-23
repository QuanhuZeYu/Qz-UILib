package club.heiqi.uilib.ui.screen.example;

import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;

import club.heiqi.uilib.ui.screen.page.DocumentUiScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiTestDocumentPageController` 的黑盒测试。
 */
public class UiTestDocumentPageControllerTest {

    /**
     * 验证诊断首页会构建菜单文档树。
     */
    @Test
    public void shouldBuildDiagnosticMenuDocumentTree() {
        RecordingMenuModel menuModel = new RecordingMenuModel();
        TestFixture fixture = new TestFixture(menuModel);

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(0), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(fixture.controller.getHtmlLikeDocumentWidget().isViewportRootScrollingEnabled());

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "诊断指挥台"));
        Assert.assertTrue(containsText(texts, "布局诊断子页"));
        Assert.assertTrue(containsText(texts, "字体性能基线"));
        Assert.assertTrue(containsText(texts, "HTML-like Smoke 子页"));
        Assert.assertTrue(containsText(texts, "Large Glass Lab 子页"));
        Assert.assertTrue(containsText(texts, "背包概览示例页"));
        Assert.assertTrue(containsText(texts, "列表元素组件拖拽"));
        Assert.assertTrue(containsText(texts, "动画能力成功展示"));
        Assert.assertTrue(containsText(texts, "UI 框架结构审查"));
        Assert.assertTrue(containsText(texts, "网络层自检"));
        Assert.assertTrue(containsText(texts, "页面作者层不再拼装旧 Widget"));
        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openFontPerformanceBaselineCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
        Assert.assertFalse(menuModel.openInventoryOverviewCalled);
        Assert.assertFalse(menuModel.openListElementDragCalled);
        Assert.assertFalse(menuModel.openAnimationCapabilityShowcaseCalled);
        Assert.assertFalse(menuModel.openUiFrameworkStructureAuditCalled);
        Assert.assertFalse(menuModel.openNetSelfCheckCalled);
    }

    /**
     * 验证菜单按钮会触发布局诊断跳转。
     */
    @Test
    public void shouldNavigateToLayoutDiagnosticsWhenMenuButtonClicked() {
        RecordingMenuModel menuModel = new RecordingMenuModel();
        TestFixture fixture = new TestFixture(menuModel);

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        clickNavigationButton(fixture.controller.getHtmlLikeDocumentWidget(), "进入布局诊断页", 1L);

        Assert.assertTrue(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openFontPerformanceBaselineCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
        Assert.assertFalse(menuModel.openInventoryOverviewCalled);
    }

    /**
     * 验证菜单按钮会触发字体性能基线诊断页跳转。
     */
    @Test
    public void shouldNavigateToFontPerformanceBaselineWhenMenuButtonClicked() {
        RecordingMenuModel menuModel = new RecordingMenuModel();
        TestFixture fixture = new TestFixture(menuModel);

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        clickNavigationButton(fixture.controller.getHtmlLikeDocumentWidget(), "进入字体基线", 1L);

        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertTrue(menuModel.openFontPerformanceBaselineCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
        Assert.assertFalse(menuModel.openInventoryOverviewCalled);
        Assert.assertFalse(menuModel.openListElementDragCalled);
    }

    /**
     * 验证菜单按钮会触发 HTML-like smoke 跳转。
     */
    @Test
    public void shouldNavigateToHtmlLikeSmokeWhenMenuButtonClicked() {
        RecordingMenuModel menuModel = new RecordingMenuModel();
        TestFixture fixture = new TestFixture(menuModel);

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        clickNavigationButton(fixture.controller.getHtmlLikeDocumentWidget(), "进入 HTML-like Smoke", 1L);

        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openFontPerformanceBaselineCalled);
        Assert.assertTrue(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
        Assert.assertFalse(menuModel.openInventoryOverviewCalled);
    }

    /**
     * 验证菜单按钮会触发大面积磨玻璃测试页跳转。
     */
    @Test
    public void shouldNavigateToHtmlLikeGlassWhenMenuButtonClicked() {
        RecordingMenuModel menuModel = new RecordingMenuModel();
        TestFixture fixture = new TestFixture(menuModel);

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        clickNavigationButton(fixture.controller.getHtmlLikeDocumentWidget(), "进入 Glass Lab", 1L);

        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openFontPerformanceBaselineCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertTrue(menuModel.openHtmlLikeGlassCalled);
        Assert.assertFalse(menuModel.openInventoryOverviewCalled);
    }

    /**
     * 验证菜单按钮会触发背包概览示例页跳转。
     */
    @Test
    public void shouldNavigateToInventoryOverviewWhenMenuButtonClicked() {
        RecordingMenuModel menuModel = new RecordingMenuModel();
        TestFixture fixture = new TestFixture(menuModel);

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        clickNavigationButton(fixture.controller.getHtmlLikeDocumentWidget(), "进入背包概览", 1L);

        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openFontPerformanceBaselineCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
        Assert.assertTrue(menuModel.openInventoryOverviewCalled);
        Assert.assertFalse(menuModel.openListElementDragCalled);
    }

    /**
     * 验证菜单按钮会触发列表元素拖拽测试页跳转。
     */
    @Test
    public void shouldNavigateToListElementDragWhenMenuButtonClicked() {
        RecordingMenuModel menuModel = new RecordingMenuModel();
        TestFixture fixture = new TestFixture(menuModel);

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        clickNavigationButton(fixture.controller.getHtmlLikeDocumentWidget(), "进入拖拽列表", 1L);

        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openFontPerformanceBaselineCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
        Assert.assertFalse(menuModel.openInventoryOverviewCalled);
        Assert.assertTrue(menuModel.openListElementDragCalled);
    }

    /**
     * 验证菜单按钮会触发 UI 框架结构审查展示页跳转。
     */
    @Test
    public void shouldNavigateToUiFrameworkStructureAuditWhenMenuButtonClicked() {
        RecordingMenuModel menuModel = new RecordingMenuModel();
        TestFixture fixture = new TestFixture(menuModel);

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        clickNavigationButton(fixture.controller.getHtmlLikeDocumentWidget(), "进入结构审查", 1L);

        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openFontPerformanceBaselineCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
        Assert.assertFalse(menuModel.openInventoryOverviewCalled);
        Assert.assertFalse(menuModel.openListElementDragCalled);
        Assert.assertTrue(menuModel.openUiFrameworkStructureAuditCalled);
    }

    /**
     * 验证菜单按钮会触发动画能力成功展示页跳转。
     */
    @Test
    public void shouldNavigateToAnimationCapabilityShowcaseWhenMenuButtonClicked() {
        RecordingMenuModel menuModel = new RecordingMenuModel();
        TestFixture fixture = new TestFixture(menuModel);

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        clickNavigationButton(fixture.controller.getHtmlLikeDocumentWidget(), "进入动画展示", 1L);

        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openFontPerformanceBaselineCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
        Assert.assertFalse(menuModel.openInventoryOverviewCalled);
        Assert.assertFalse(menuModel.openListElementDragCalled);
        Assert.assertTrue(menuModel.openAnimationCapabilityShowcaseCalled);
        Assert.assertFalse(menuModel.openUiFrameworkStructureAuditCalled);
    }

    /**
     * 验证菜单按钮会触发网络层自检页跳转。
     */
    @Test
    public void shouldNavigateToNetSelfCheckWhenMenuButtonClicked() {
        RecordingMenuModel menuModel = new RecordingMenuModel();
        TestFixture fixture = new TestFixture(menuModel);

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        clickNavigationButton(fixture.controller.getHtmlLikeDocumentWidget(), "进入网络自检", 1L);

        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openFontPerformanceBaselineCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
        Assert.assertFalse(menuModel.openInventoryOverviewCalled);
        Assert.assertFalse(menuModel.openListElementDragCalled);
        Assert.assertFalse(menuModel.openAnimationCapabilityShowcaseCalled);
        Assert.assertFalse(menuModel.openUiFrameworkStructureAuditCalled);
        Assert.assertTrue(menuModel.openNetSelfCheckCalled);
    }

    /**
     * 验证菜单按钮不会因 width:100% 与横向外边距组合溢出导航卡片。
     */
    @Test
    public void shouldKeepNavigationButtonsInsideCards() {
        TestFixture fixture = new TestFixture(new RecordingMenuModel());

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layoutViewportRoot(widget.getDocument().getRootElement(),
                760, 520, fixture.textMeasureService);

        DocumentLayoutBox navigationGrid = rootBox.getChildren().get(2);
        for (DocumentLayoutBox navigationRow : navigationGrid.getChildren()) {
            for (DocumentLayoutBox cardBox : navigationRow.getChildren()) {
                DocumentLayoutBox buttonBox = cardBox.getChildren().get(cardBox.getChildren().size() - 1);
                int cardContentLeft = cardBox.getContentLeft();
                int cardContentRight = cardContentLeft + cardBox.getContentWidth();
                Assert.assertTrue(buttonBox.getLeft() >= cardContentLeft);
                Assert.assertTrue(buttonBox.getRight() <= cardContentRight);
            }
        }
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

    private static void clickNavigationButton(HtmlLikeDocumentWidget widget, String buttonText, long timeNanos) {
        Assert.assertNotNull(widget);
        ElementNode button = findElementByAttribute(widget.getDocument().getRootElement(), "data-diagnostic-nav",
                buttonText);
        Assert.assertNotNull(button);
        Assert.assertNotNull(button.getClickHandler());
        Assert.assertTrue(button.getClickHandler().onClick(new DocumentElementClickEvent(button, button, 0, 0, 0,
                timeNanos)));
    }

    private static ElementNode findElementByAttribute(ElementNode element, String attributeName, String attributeValue) {
        if (attributeValue.equals(element.getAttribute(attributeName))) {
            return element;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child.getNodeType() == DocumentNodeType.ELEMENT) {
                ElementNode found = findElementByAttribute((ElementNode) child, attributeName, attributeValue);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static final class TestFixture {

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final UiTestDocumentPageController controller;

        private TestFixture(UiTestMenuModel menuModel) {
            this.controller = new UiTestDocumentPageController(documentUi, pageSurface, menuModel);
        }
    }

    private static final class RecordingMenuModel implements UiTestMenuModel {

        private boolean openLayoutDiagnosticsCalled;
        private boolean openFontPerformanceBaselineCalled;
        private boolean openHtmlLikeSmokeCalled;
        private boolean openHtmlLikeGlassCalled;
        private boolean openInventoryOverviewCalled;
        private boolean openListElementDragCalled;
        private boolean openAnimationCapabilityShowcaseCalled;
        private boolean openUiFrameworkStructureAuditCalled;
        private boolean openRuntimeSelfTestCalled;
        private boolean openNetSelfCheckCalled;

        @Override
        public void openLayoutDiagnostics() {
            openLayoutDiagnosticsCalled = true;
        }

        @Override
        public void openFontPerformanceBaseline() {
            openFontPerformanceBaselineCalled = true;
        }

        @Override
        public void openHtmlLikeSmoke() {
            openHtmlLikeSmokeCalled = true;
        }

        @Override
        public void openHtmlLikeGlass() {
            openHtmlLikeGlassCalled = true;
        }

        @Override
        public void openInventoryOverview() {
            openInventoryOverviewCalled = true;
        }

        @Override
        public void openListElementDrag() {
            openListElementDragCalled = true;
        }

        @Override
        public void openBrowserSemanticsShowcase() {
        }

        @Override
        public void openAnimationCapabilityShowcase() {
            openAnimationCapabilityShowcaseCalled = true;
        }

        @Override
        public void openUiFrameworkStructureAudit() {
            openUiFrameworkStructureAuditCalled = true;
        }

        @Override
        public void openRuntimeSelfTest() {
            openRuntimeSelfTestCalled = true;
        }

        @Override
        public void openNetSelfCheck() {
            openNetSelfCheckCalled = true;
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
