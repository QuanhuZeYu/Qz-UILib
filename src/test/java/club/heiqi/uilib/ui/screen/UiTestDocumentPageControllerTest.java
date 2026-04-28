package club.heiqi.uilib.ui.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.control.UiControlRuntimeAdapters;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.theme.UiDocumentThemes;
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
        Assert.assertTrue(containsText(texts, "HTML-like Smoke 子页"));
        Assert.assertTrue(containsText(texts, "Large Glass Lab 子页"));
        Assert.assertTrue(containsText(texts, "页面作者层不再拼装旧 Widget"));
        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
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

        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(0, 0, 760, 520);
        widget.onFocusTraversalEntered(false);
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));

        Assert.assertTrue(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
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

        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(0, 0, 760, 520);
        widget.onFocusTraversalEntered(false);
        Assert.assertTrue(widget.onFocusTraversal(false));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));

        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertTrue(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertFalse(menuModel.openHtmlLikeGlassCalled);
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

        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(0, 0, 760, 520);
        widget.onFocusTraversalEntered(false);
        Assert.assertTrue(widget.onFocusTraversal(false));
        Assert.assertTrue(widget.onFocusTraversal(false));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));

        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
        Assert.assertTrue(menuModel.openHtmlLikeGlassCalled);
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

        DocumentLayoutBox navigationRow = rootBox.getChildren().get(2);
        for (DocumentLayoutBox cardBox : navigationRow.getChildren()) {
            DocumentLayoutBox buttonBox = cardBox.getChildren().get(0);
            int cardContentLeft = cardBox.getContentLeft();
            int cardContentRight = cardContentLeft + cardBox.getContentWidth();
            Assert.assertTrue(buttonBox.getLeft() >= cardContentLeft);
            Assert.assertTrue(buttonBox.getRight() <= cardContentRight);
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

    private static final class TestFixture {

        private final UiDocumentTheme documentTheme = UiDocumentThemes.current();
        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(documentTheme, textMeasureService,
                UiControlRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final UiTestDocumentPageController controller;

        private TestFixture(UiTestMenuModel menuModel) {
            this.controller = new UiTestDocumentPageController(documentUi, pageSurface, menuModel);
        }
    }

    private static final class RecordingMenuModel implements UiTestMenuModel {

        private boolean openLayoutDiagnosticsCalled;
        private boolean openHtmlLikeSmokeCalled;
        private boolean openHtmlLikeGlassCalled;

        @Override
        public void openLayoutDiagnostics() {
            openLayoutDiagnosticsCalled = true;
        }

        @Override
        public void openHtmlLikeSmoke() {
            openHtmlLikeSmokeCalled = true;
        }

        @Override
        public void openHtmlLikeGlass() {
            openHtmlLikeGlassCalled = true;
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
