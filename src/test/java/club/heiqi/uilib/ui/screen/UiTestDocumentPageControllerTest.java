package club.heiqi.uilib.ui.screen;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.UiControlRuntimeAdapters;
import club.heiqi.uilib.ui.document.DocumentCardWidget;
import club.heiqi.uilib.ui.document.DocumentPageWidget;
import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.event.UiMouseEvent;
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
        fixture.pagePanel.applyLayoutBounds(0, 0, 980, 680);

        List<Widget> blocks = getDocumentBlocks(fixture.pagePanel);
        Assert.assertEquals(5, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof DocumentTextWidget);
        Assert.assertTrue(blocks.get(1) instanceof DocumentTextWidget);
        Assert.assertTrue(blocks.get(2) instanceof DocumentCardWidget);
        Assert.assertTrue(blocks.get(3) instanceof DocumentCardWidget);
        Assert.assertTrue(blocks.get(4) instanceof DocumentCardWidget);
        Assert.assertEquals("诊断菜单页", ((LabelWidget) blocks.get(0)).getText());

        List<String> labelTexts = collectLabelTexts(fixture.pagePanel);
        Assert.assertTrue(containsText(labelTexts, "诊断首页"));
        Assert.assertTrue(containsText(labelTexts, "布局诊断子页"));
        Assert.assertTrue(containsText(labelTexts, "HTML-like Smoke 子页"));
        Assert.assertTrue(containsText(labelTexts, "继续跳到不同的 definition-backed 诊断子页"));
        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
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

        List<ButtonWidget> buttons = getWidgetsByType(fixture.pagePanel, ButtonWidget.class);
        Assert.assertEquals(2, buttons.size());
        ButtonWidget navigateButton = buttons.get(0);
        navigateButton.applyLayoutBounds(0, 0, 220, 36);
        clickButton(navigateButton);

        Assert.assertTrue(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertFalse(menuModel.openHtmlLikeSmokeCalled);
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

        List<ButtonWidget> buttons = getWidgetsByType(fixture.pagePanel, ButtonWidget.class);
        Assert.assertEquals(2, buttons.size());
        ButtonWidget navigateButton = buttons.get(1);
        navigateButton.applyLayoutBounds(0, 0, 260, 36);
        clickButton(navigateButton);

        Assert.assertFalse(menuModel.openLayoutDiagnosticsCalled);
        Assert.assertTrue(menuModel.openHtmlLikeSmokeCalled);
    }

    private static List<Widget> getDocumentBlocks(DocumentPageWidget pagePanel) {
        List<Widget> pageChildren = pagePanel.getChildren();
        Assert.assertFalse(pageChildren.isEmpty());
        return pageChildren.get(0).getChildren();
    }

    private static List<String> collectLabelTexts(Widget root) {
        List<String> labelTexts = new ArrayList<String>();
        collectLabelTexts(root, labelTexts);
        return labelTexts;
    }

    private static boolean containsText(List<String> labelTexts, String expectedSnippet) {
        for (String labelText : labelTexts) {
            if (labelText != null && labelText.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    private static <T extends Widget> List<T> getWidgetsByType(Widget root, Class<T> widgetClass) {
        List<T> matchedWidgets = new ArrayList<T>();
        collectWidgetsByType(root, widgetClass, matchedWidgets);
        return matchedWidgets;
    }

    private static void collectLabelTexts(Widget root, List<String> labelTexts) {
        if (root instanceof LabelWidget) {
            labelTexts.add(((LabelWidget) root).getText());
        }
        for (Widget child : root.getChildren()) {
            collectLabelTexts(child, labelTexts);
        }
    }

    private static <T extends Widget> void collectWidgetsByType(Widget root, Class<T> widgetClass, List<T> matchedWidgets) {
        if (widgetClass.isInstance(root)) {
            matchedWidgets.add(widgetClass.cast(root));
        }
        for (Widget child : root.getChildren()) {
            collectWidgetsByType(child, widgetClass, matchedWidgets);
        }
    }

    private static void clickButton(ButtonWidget buttonWidget) {
        buttonWidget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        buttonWidget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 1L));
    }

    private static final class TestFixture {

        private final UiDocumentTheme documentTheme = UiDocumentThemes.current();
        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(documentTheme, textMeasureService,
                UiControlRuntimeAdapters.empty());
        private final DocumentPageWidget pagePanel = new DocumentPageWidget(documentTheme, textMeasureService);
        private final DocumentPageAuthoringSurface pageSurface = DocumentPageAuthoringSurface.adapt(pagePanel);
        private final UiTestDocumentPageController controller;

        private TestFixture(UiTestMenuModel menuModel) {
            this.controller = new UiTestDocumentPageController(documentUi, pageSurface, menuModel);
        }
    }

    private static final class RecordingMenuModel implements UiTestMenuModel {

        private boolean openLayoutDiagnosticsCalled;
        private boolean openHtmlLikeSmokeCalled;

        @Override
        public void openLayoutDiagnostics() {
            openLayoutDiagnosticsCalled = true;
        }

        @Override
        public void openHtmlLikeSmoke() {
            openHtmlLikeSmokeCalled = true;
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
            return java.util.Collections.singletonList(text == null ? "" : text);
        }
    }
}
