package club.heiqi.uilib.ui.screen.example;

import club.heiqi.uilib.ui.screen.DirectDocumentPageAuthoringSurface;

import club.heiqi.uilib.ui.screen.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.DocumentUiScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.font.FontRuntimeStats;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiLayoutDiagnosticsDocumentPageController` 的 HTML-like 页面契约测试。
 */
public class UiLayoutDiagnosticsDocumentPageControllerTest {

    @Test
    public void shouldBuildDocumentTreeAndRefreshDiagnostics() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.afterDocumentBuilt();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(0), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(fixture.controller.getHtmlLikeDocumentWidget().isViewportRootScrollingEnabled());

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "布局诊断页"));
        Assert.assertTrue(containsText(texts, "当前状态"));
        Assert.assertTrue(containsText(texts, "表单约束探针"));
        Assert.assertTrue(containsText(texts, "文本换行与最小宽度探针"));
        Assert.assertTrue(containsText(texts, "高频字符变更探针"));
        Assert.assertTrue(containsText(texts, "窗口 1280x720"));
        Assert.assertTrue(containsText(texts, "最近状态：尚未操作"));
        Assert.assertTrue(containsText(texts, "探针状态：已停止"));
        Assert.assertTrue(containsText(texts, "探针未启用。开启后可以直接观察"));
        Assert.assertTrue(containsText(texts, "性能采样尚未稳定"));
    }

    @Test
    public void shouldToggleMutationProbeAndRefreshBeforeFrame() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(0, 0, 760, 940);
        fixture.controller.afterDocumentBuilt();

        clickControl(widget, "mutation-toggle", 1L);

        List<String> textsAfterToggle = collectDocumentTexts(widget);
        Assert.assertTrue(containsText(textsAfterToggle, "最近状态：已启用高频字符变更探针"));
        Assert.assertTrue(containsText(textsAfterToggle, "探针状态：运行中"));
        Assert.assertTrue(containsText(textsAfterToggle, "探针已重置，等待下一次文本变更。"));

        clickSegmentedOption(widget, "mutation-mode", "长文重排", 2L);

        fixture.controller.beforeDocumentFrame();

        List<String> textsAfterFrame = collectDocumentTexts(widget);
        Assert.assertTrue(containsText(textsAfterFrame, "最近状态：已切换变更模式到 长文重排"));
        Assert.assertTrue(containsText(textsAfterFrame, "探针状态：运行中；模式：长文重排"));
        Assert.assertTrue(containsText(textsAfterFrame, "实际 setText 次数：1"));
        Assert.assertTrue(containsText(textsAfterFrame, "长文重排样本 000001"));
    }

    @Test
    public void shouldRefreshHooksAndRespectInjectedScreenName() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.afterDocumentBuilt();

        fixture.runtimeView.setHostSize(1440, 900);
        fixture.controller.onDocumentResized();

        List<String> textsAfterResize = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(textsAfterResize, "窗口 1440x900"));
        Assert.assertTrue(containsText(textsAfterResize, "性能采样尚未稳定"));

        fixture.runtimeView.setRuntimeStats(createRuntimeStats("UiLayoutDiagnosticsDocumentPageController"));
        fixture.controller.beforeDocumentFrame();

        List<String> textsAfterMismatch = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(textsAfterMismatch, "性能采样尚未稳定"));
        Assert.assertFalse(containsText(textsAfterMismatch, "当前帧 12.00 ms"));

        fixture.runtimeView.setHostSize(1600, 960);
        fixture.runtimeView.setRuntimeStats(createRuntimeStats("ui_test_layout"));
        fixture.controller.beforeDocumentFrame();

        List<String> textsAfterMatch = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(textsAfterMatch, "窗口 1600x960"));
        Assert.assertTrue(containsText(textsAfterMatch, "当前帧 12.00 ms"));
        Assert.assertTrue(containsText(textsAfterMatch, "渲染 8.00 ms；贴屏 1.50 ms；输入路由 0.50 ms"));
        Assert.assertTrue(containsText(textsAfterMatch, "最慢自身组件：HtmlLikeDocumentWidget 3.25 ms"));
        Assert.assertTrue(containsText(textsAfterMatch, "阶段热点：measure=4.0ms, layout=2.0ms"));
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

    private static void clickControl(HtmlLikeDocumentWidget widget, String controlName, long timeNanos) {
        ElementNode control = findElementByAttribute(widget.getDocument().getRootElement(), "data-layout-probe-control",
                controlName);
        Assert.assertNotNull(control);
        Assert.assertNotNull(control.getClickHandler());
        Assert.assertTrue(control.getClickHandler().onClick(new DocumentElementClickEvent(control, control, 0, 0, 0,
                timeNanos)));
    }

    private static void clickSegmentedOption(HtmlLikeDocumentWidget widget, String controlName, String optionText,
            long timeNanos) {
        ElementNode control = findElementByAttribute(widget.getDocument().getRootElement(), "data-layout-probe-control",
                controlName);
        Assert.assertNotNull(control);
        ElementNode option = findSegmentedOption(control, controlName, optionText);
        Assert.assertNotNull(option);
        Assert.assertNotNull(option.getClickHandler());
        Assert.assertTrue(option.getClickHandler().onClick(new DocumentElementClickEvent(option, option, 0, 0, 0,
                timeNanos)));
    }

    private static ElementNode findSegmentedOption(ElementNode control, String controlName, String optionText) {
        if (control == null) {
            return null;
        }
        if ("mutation-mode".equals(controlName)) {
            if ("§k渲染".equals(optionText)) {
                return findElementByAttribute(control, "data-layout-probe-mutation-mode-option", "0");
            }
            if ("同长替换".equals(optionText)) {
                return findElementByAttribute(control, "data-layout-probe-mutation-mode-option", "1");
            }
            if ("长文重排".equals(optionText)) {
                return findElementByAttribute(control, "data-layout-probe-mutation-mode-option", "2");
            }
        }
        return findElementContainingDirectText(control, optionText);
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

    private static ElementNode findElementContainingDirectText(ElementNode element, String expectedText) {
        for (DocumentNode child : element.getChildren()) {
            if (child.getNodeType() == DocumentNodeType.TEXT && expectedText.equals(((TextNode) child).getText())) {
                return element;
            }
            if (child.getNodeType() == DocumentNodeType.ELEMENT) {
                ElementNode found = findElementContainingDirectText((ElementNode) child, expectedText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static UiRuntimeStats createRuntimeStats(String screenName) {
        return new UiRuntimeStats(screenName, 960, 540, 1920, 1080, 12_000_000L, 10_000_000L, 18_000_000L, 83.3D,
                8_000_000L, 7_500_000L, 1_500_000L, 3, 2, 1, 500_000L, 42L, 77, 9, "HtmlLikeDocumentWidget", 3_250_000L,
                "HtmlLikeDocumentWidget", 6_500_000L, "measure=4.0ms, layout=2.0ms", 2, 30);
    }

    private static FontRuntimeStats createFontRuntimeStats() {
        return new FontRuntimeStats(3, 64, 2, 1, 4, 96, 2, 2, 2, 2, 2, 12L, 1L, 10L, 1L, 24L,
                2L);
    }

    private static final class TestFixture {

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final TestRuntimeView runtimeView = new TestRuntimeView();
        private final FontRuntimeStatsSource fontRuntimeStatsSource = new FontRuntimeStatsSource() {
            @Override
            public FontRuntimeStats getRuntimeStats() {
                return createFontRuntimeStats();
            }
        };
        private final UiLayoutDiagnosticsDocumentPageController controller = new UiLayoutDiagnosticsDocumentPageController(
                documentUi, pageSurface, runtimeView, "ui_test_layout",
                fontRuntimeStatsSource);
    }

    private static final class TestRuntimeView implements DocumentPageRuntimeView {

        private int hostWidth = 1280;
        private int hostHeight = 720;
        private int mouseX = 0;
        private int mouseY = 0;
        private UiRuntimeStats runtimeStats = createRuntimeStats("");

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
            return runtimeStats;
        }

        private void setHostSize(int hostWidth, int hostHeight) {
            this.hostWidth = hostWidth;
            this.hostHeight = hostHeight;
        }

        private void setMousePosition(int mouseX, int mouseY) {
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        private void setRuntimeStats(UiRuntimeStats runtimeStats) {
            this.runtimeStats = runtimeStats;
        }
    }

    private static final class DeterministicTextMeasureService implements TextMeasureService {

        private static final int ASCII_CHAR_WIDTH = 6;
        private static final int WIDE_CHAR_WIDTH = 12;
        private static final int LINE_HEIGHT = 9;

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            int width = 0;
            for (int index = 0; index < text.length();) {
                int codepoint = text.codePointAt(index);
                if (codepoint == '\r' || codepoint == '\n') {
                    index += Character.charCount(codepoint);
                    continue;
                }
                if (codepoint == '§' && index < text.length() - 1) {
                    index += 2;
                    continue;
                }
                width += resolveCodePointWidth(codepoint);
                index += Character.charCount(codepoint);
            }
            return width;
        }

        @Override
        public int getLineHeight() {
            return LINE_HEIGHT;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int width = 0;
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < text.length();) {
                int codepoint = text.codePointAt(index);
                int charWidth = resolveCodePointWidth(codepoint);
                if (width + charWidth > targetWidth) {
                    break;
                }
                builder.appendCodePoint(codepoint);
                width += charWidth;
                index += Character.charCount(codepoint);
            }
            return builder.toString();
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<String>();
            StringBuilder line = new StringBuilder();
            int lineWidth = 0;
            for (int index = 0; index < text.length();) {
                int codepoint = text.codePointAt(index);
                if (codepoint == '\r') {
                    index += Character.charCount(codepoint);
                    continue;
                }
                if (codepoint == '\n') {
                    lines.add(line.toString());
                    line.setLength(0);
                    lineWidth = 0;
                    index += Character.charCount(codepoint);
                    continue;
                }
                int charWidth = resolveCodePointWidth(codepoint);
                if (line.length() > 0 && lineWidth + charWidth > wrapWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                    lineWidth = 0;
                }
                line.appendCodePoint(codepoint);
                lineWidth += charWidth;
                index += Character.charCount(codepoint);
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
            return lines.isEmpty() ? Collections.singletonList("") : lines;
        }

        private static int resolveCodePointWidth(int codepoint) {
            return codepoint <= 0x007F ? ASCII_CHAR_WIDTH : WIDE_CHAR_WIDTH;
        }
    }
}
