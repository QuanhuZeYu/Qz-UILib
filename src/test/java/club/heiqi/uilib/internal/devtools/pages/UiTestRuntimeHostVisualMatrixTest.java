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

/**
 * `/qzuilib test` RuntimeHost 视觉矩阵专项测试。
 */
public class UiTestRuntimeHostVisualMatrixTest {

    /**
     * 验证 RuntimeHost 分组接入五张视觉样例，并区分自动断言和人工诊断。
     */
    @Test
    public void shouldRenderRuntimeHostSamplesAndRunRuntimeHostAssertions() {
        TestFixture fixture = new TestFixture();
        fixture.runtimeView.hostWidth = 960;
        fixture.runtimeView.hostHeight = 540;
        fixture.runtimeView.mouseX = 22;
        fixture.runtimeView.mouseY = 33;

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "打开 HOST", 0);

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-HOST-001"));
        Assert.assertTrue(containsText(texts, "open timing 开屏时序"));
        Assert.assertTrue(containsText(texts, "聊天命令"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertEquals(UiTestSemanticStatus.MANUAL_PENDING,
                getCaseResult(fixture, "VIS-HOST-001").getSemanticStatus());
        Assert.assertTrue(getCaseResult(fixture, "VIS-HOST-001").getActualResult()
                .contains("openTimingDiff=状态牌可机器诊断"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-HOST-002"));
        Assert.assertTrue(containsText(texts, "resize 与 viewport fill"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-HOST-002").getActualResult()
                .contains("resizeViewportDiff=预览盒与 fill 摘要可机器诊断"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-HOST-003"));
        Assert.assertTrue(containsText(texts, "runtime stats 状态摘要"));
        Assert.assertTrue(containsText(texts, "DocumentPageRuntimeView#getUiRuntimeStats"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertEquals(UiTestSemanticStatus.AUTO_PASSED,
                getCaseResult(fixture, "VIS-HOST-003").getSemanticStatus());
        Assert.assertTrue(getCaseResult(fixture, "VIS-HOST-003").getActualResult()
                .contains("runtimeStatsDiff=expected frame/render/input stats summary"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-HOST-004"));
        Assert.assertTrue(containsText(texts, "HUD/container input 链路"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-HOST-004").getActualResult()
                .contains("hostInputDiff=HUD 输入链路状态牌可机器诊断"));

        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "下一张", 0);
        texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "VIS-HOST-005"));
        Assert.assertTrue(containsText(texts, "exception panel 故障展示"));
        clickButtonByLabel(fixture.controller.getHtmlLikeDocumentWidget(), "运行当前样例断言", 0);
        Assert.assertTrue(getCaseResult(fixture, "VIS-HOST-005").getActualResult()
                .contains("exceptionPanelDiff=面板结构可机器诊断"));
    }

    /**
     * 验证 RuntimeHost 分组统计已从缺口变为首轮接入完成。
     */
    @Test
    public void shouldExposeRuntimeHostGroupStateInMatrix() {
        TestFixture fixture = new TestFixture();

        UiTestMatrixState state = fixture.controller.getMatrixState();
        UiTestGroupState hostState = state.getGroupState("HOST");

        Assert.assertEquals(58, fixture.controller.getRegistry().getCases().size());
        Assert.assertEquals(58, state.getTotalImplementedCaseCount());
        Assert.assertEquals(6, state.getTotalGapCount());
        Assert.assertEquals(5, hostState.getImplementedCaseCount());
        Assert.assertEquals(0, hostState.getGapCount());
        Assert.assertEquals(UiTestSemanticStatus.MANUAL_PENDING, hostState.getSemanticStatus());
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

    private static UiTestCaseResult getCaseResult(TestFixture fixture, String caseId) {
        UiTestCaseResult result = fixture.controller.getMatrixState().getCaseResult(caseId);
        Assert.assertNotNull("找不到样例结果：" + caseId, result);
        return result;
    }

    private static void clickButtonByLabel(HtmlLikeDocumentWidget widget, String label, int occurrence) {
        List<ElementNode> buttons = new ArrayList<ElementNode>();
        collectButtonsByLabel(widget.getDocument().getRootElement(), label, buttons);
        Assert.assertTrue("找不到按钮：" + label + " #" + occurrence, buttons.size() > occurrence);
        ElementNode button = buttons.get(occurrence);
        Assert.assertNotNull(button.getClickHandler());
        clickElement(button, 0L);
    }

    private static void clickElement(ElementNode element, long timeNanos) {
        Assert.assertNotNull(element.getClickHandler());
        element.getClickHandler().onClick(new DocumentElementClickEvent(element, element, 0, 0, 0, timeNanos));
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

        private final TextMeasureService textMeasureService;
        private final DocumentUiScope documentUi;
        private final DirectDocumentPageAuthoringSurface pageSurface;
        private final MutableRuntimeView runtimeView;
        private final UiTestDocumentPageController controller;

        private TestFixture() {
            this.textMeasureService = new DeterministicTextMeasureService();
            this.documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
            this.pageSurface = new DirectDocumentPageAuthoringSurface();
            this.runtimeView = new MutableRuntimeView();
            this.controller = new UiTestDocumentPageController(documentUi, pageSurface, runtimeView);
        }
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
            return new UiRuntimeStats("host-test", hostWidth, hostHeight, hostWidth, hostHeight, 2_000_000L,
                    2_000_000L, 3_000_000L, 60.0D, 1_000_000L, 1_000_000L, 0L, 1, 1, 0,
                    200_000L, 4L, 7, 3, "HtmlLikeDocumentWidget", 800_000L, "root", 1_200_000L,
                    "layout>render", 0, 1);
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
