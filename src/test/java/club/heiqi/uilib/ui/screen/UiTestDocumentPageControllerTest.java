package club.heiqi.uilib.ui.screen;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeStats;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.SegmentedSelectorWidget;
import club.heiqi.uilib.ui.control.ToggleSwitchWidget;
import club.heiqi.uilib.ui.control.UiControlRuntimeAdapters;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.DocumentCardWidget;
import club.heiqi.uilib.ui.document.DocumentFlowRowWidget;
import club.heiqi.uilib.ui.document.DocumentPageWidget;
import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `UiTestDocumentPageController` 的黑盒测试。
 */
public class UiTestDocumentPageControllerTest {

    /**
     * 验证控制器能构建完整页面树并刷新关键诊断文本。
     */
    @Test
    public void shouldBuildDocumentTreeAndRefreshDiagnostics() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.pagePanel.applyLayoutBounds(0, 0, 980, 680);
        fixture.controller.afterDocumentBuilt();

        List<Widget> blocks = getDocumentBlocks(fixture.pagePanel);
        Assert.assertEquals(7, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof DocumentTextWidget);
        Assert.assertTrue(blocks.get(1) instanceof DocumentTextWidget);
        Assert.assertTrue(blocks.get(2) instanceof DocumentCardWidget);
        Assert.assertTrue(blocks.get(3) instanceof DocumentFlowRowWidget);
        Assert.assertTrue(blocks.get(4) instanceof DocumentCardWidget);
        Assert.assertTrue(blocks.get(5) instanceof DocumentCardWidget);
        Assert.assertTrue(blocks.get(6) instanceof DocumentCardWidget);
        Assert.assertEquals("布局诊断页", ((LabelWidget) blocks.get(0)).getText());
        Assert.assertEquals(
                "如果这一页的两张卡片仍然在不合理的宽度下并排、中文换行异常、表单行不按父宽度变化，或者卡片不能同时按 flex-basis 和增长权重自然分配空间，那么说明底层尺寸链路仍然有问题。",
                ((LabelWidget) blocks.get(1)).getText());

        List<String> labelTexts = collectLabelTexts(fixture.pagePanel);
        Assert.assertTrue(containsText(labelTexts, "当前状态"));
        Assert.assertTrue(containsText(labelTexts, "表单约束探针"));
        Assert.assertTrue(containsText(labelTexts, "文本换行与最小宽度探针"));
        Assert.assertTrue(containsText(labelTexts, "高频字符变更探针"));
        Assert.assertTrue(containsText(labelTexts, "窗口 1280x720"));
        Assert.assertTrue(containsText(labelTexts, "最近状态：尚未操作"));
        Assert.assertTrue(containsText(labelTexts, "探针状态：已停止"));
        Assert.assertTrue(containsText(labelTexts, "探针未启用。开启后可以直接观察"));
        Assert.assertTrue(containsText(labelTexts, "性能采样尚未稳定"));
    }

    /**
     * 验证探针切换与 before-frame hook 会驱动文本刷新。
     */
    @Test
    public void shouldToggleMutationProbeAndRefreshBeforeFrame() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.afterDocumentBuilt();

        ToggleSwitchWidget mutationToggle = getWidgetsByType(fixture.pagePanel, ToggleSwitchWidget.class).get(1);
        mutationToggle.applyLayoutBounds(0, 0, 220, 36);
        clickToggle(mutationToggle);

        List<String> labelTextsAfterToggle = collectLabelTexts(fixture.pagePanel);
        Assert.assertTrue(containsText(labelTextsAfterToggle, "最近状态：已启用高频字符变更探针"));
        Assert.assertTrue(containsText(labelTextsAfterToggle, "探针状态：运行中"));
        Assert.assertTrue(containsText(labelTextsAfterToggle, "探针已重置，等待下一次文本变更。"));

        SegmentedSelectorWidget mutationModeSelector = getWidgetsByType(fixture.pagePanel, SegmentedSelectorWidget.class).get(1);
        mutationModeSelector.applyLayoutBounds(0, 0, 300, 36);
        clickSelectorSegment(mutationModeSelector, 2);

        fixture.controller.beforeDocumentFrame();

        List<String> labelTextsAfterFrame = collectLabelTexts(fixture.pagePanel);
        Assert.assertTrue(containsText(labelTextsAfterFrame, "最近状态：已切换变更模式到 长文重排"));
        Assert.assertTrue(containsText(labelTextsAfterFrame, "探针状态：运行中；模式：长文重排"));
        Assert.assertTrue(containsText(labelTextsAfterFrame, "实际 setText 次数：1"));
        Assert.assertTrue(containsText(labelTextsAfterFrame, "长文重排样本 000001"));
    }

    /**
     * 验证 resize / before-frame hook 会刷新宿主尺寸与性能统计，并保留稳定 page id 语义。
     */
    @Test
    public void shouldRefreshHooksAndRespectInjectedScreenName() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.afterDocumentBuilt();

        fixture.runtimeView.setHostSize(1440, 900);
        fixture.controller.onDocumentResized();

        List<String> labelTextsAfterResize = collectLabelTexts(fixture.pagePanel);
        Assert.assertTrue(containsText(labelTextsAfterResize, "窗口 1440x900"));
        Assert.assertTrue(containsText(labelTextsAfterResize, "性能采样尚未稳定"));

        fixture.runtimeView.setRuntimeStats(createRuntimeStats("UiTestDocumentPageController"));
        fixture.controller.beforeDocumentFrame();

        List<String> labelTextsAfterMismatch = collectLabelTexts(fixture.pagePanel);
        Assert.assertTrue(containsText(labelTextsAfterMismatch, "性能采样尚未稳定"));
        Assert.assertFalse(containsText(labelTextsAfterMismatch, "当前帧 12.00 ms"));

        fixture.runtimeView.setHostSize(1600, 960);
        fixture.runtimeView.setRuntimeStats(createRuntimeStats(UiDocumentScreens.UI_TEST.getPageId()));
        fixture.controller.beforeDocumentFrame();

        List<String> labelTextsAfterMatch = collectLabelTexts(fixture.pagePanel);
        Assert.assertTrue(containsText(labelTextsAfterMatch, "窗口 1600x960"));
        Assert.assertTrue(containsText(labelTextsAfterMatch, "当前帧 12.00 ms"));
        Assert.assertTrue(containsText(labelTextsAfterMatch, "渲染 8.00 ms；贴屏 1.50 ms；输入路由 0.50 ms"));
        Assert.assertTrue(containsText(labelTextsAfterMatch, "最慢自身组件：LabelWidget 3.25 ms"));
        Assert.assertTrue(containsText(labelTextsAfterMatch, "阶段热点：measure=4.0ms, layout=2.0ms"));
    }

    /**
     * 获取文档页面的块级内容列表。
     */
    private static List<Widget> getDocumentBlocks(DocumentPageWidget pagePanel) {
        List<Widget> pageChildren = pagePanel.getChildren();
        Assert.assertFalse(pageChildren.isEmpty());
        return pageChildren.get(0).getChildren();
    }

    /**
     * 递归收集全部标签文本。
     */
    private static List<String> collectLabelTexts(Widget root) {
        List<String> labelTexts = new ArrayList<String>();
        collectLabelTexts(root, labelTexts);
        return labelTexts;
    }

    /**
     * 判断是否存在包含目标片段的标签文本。
     */
    private static boolean containsText(List<String> labelTexts, String expectedSnippet) {
        for (String labelText : labelTexts) {
            if (labelText != null && labelText.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 递归收集指定类型组件。
     */
    private static <T extends Widget> List<T> getWidgetsByType(Widget root, Class<T> widgetClass) {
        List<T> matchedWidgets = new ArrayList<T>();
        collectWidgetsByType(root, widgetClass, matchedWidgets);
        return matchedWidgets;
    }

    /**
     * 递归收集全部标签文本。
     */
    private static void collectLabelTexts(Widget root, List<String> labelTexts) {
        if (root instanceof LabelWidget) {
            labelTexts.add(((LabelWidget) root).getText());
        }
        for (Widget child : root.getChildren()) {
            collectLabelTexts(child, labelTexts);
        }
    }

    /**
     * 递归收集指定类型组件。
     */
    private static <T extends Widget> void collectWidgetsByType(Widget root, Class<T> widgetClass, List<T> matchedWidgets) {
        if (widgetClass.isInstance(root)) {
            matchedWidgets.add(widgetClass.cast(root));
        }
        for (Widget child : root.getChildren()) {
            collectWidgetsByType(child, widgetClass, matchedWidgets);
        }
    }

    /**
     * 触发开关点击。
     */
    private static void clickToggle(ToggleSwitchWidget toggleWidget) {
        toggleWidget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 1L));
    }

    /**
     * 触发分段选择器点击。
     */
    private static void clickSelectorSegment(SegmentedSelectorWidget selectorWidget, int segmentIndex) {
        int segmentWidth = 100;
        int mouseX = segmentIndex * segmentWidth + 10;
        selectorWidget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, mouseX, 10, 0, 0, 0, 0, 1L));
        Assert.assertEquals(segmentIndex, selectorWidget.getSelectedIndex());
    }

    /**
     * 创建测试用运行时统计快照。
     */
    private static UiRuntimeStats createRuntimeStats(String screenName) {
        return new UiRuntimeStats(
                screenName,
                960,
                540,
                1920,
                1080,
                12_000_000L,
                10_000_000L,
                18_000_000L,
                83.3D,
                8_000_000L,
                7_500_000L,
                1_500_000L,
                3,
                2,
                1,
                500_000L,
                42L,
                77,
                9,
                "LabelWidget",
                3_250_000L,
                "DocumentCardWidget",
                6_500_000L,
                "measure=4.0ms, layout=2.0ms",
                2,
                30);
    }

    /**
     * 创建测试用字体运行时统计快照。
     */
    private static FontRuntimeStats createFontRuntimeStats() {
        return new FontRuntimeStats(3, 64, 2, 1, 4, 96, 12L, 1L, 24L, 2L);
    }

    /**
     * 控制器测试夹具。
     */
    private static final class TestFixture {

        private final UiDocumentTheme documentTheme = UiDocumentTheme.defaultTheme();
        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(documentTheme, textMeasureService,
                UiControlRuntimeAdapters.empty());
        private final DocumentPageWidget pagePanel = new DocumentPageWidget(documentTheme, textMeasureService);
        private final DocumentPageAuthoringSurface pageSurface = DocumentPageAuthoringSurface.adapt(pagePanel);
        private final TestRuntimeView runtimeView = new TestRuntimeView();
        private final FontRuntimeStatsSource fontRuntimeStatsSource = new FontRuntimeStatsSource() {
            @Override
            public FontRuntimeStats getRuntimeStats() {
                return createFontRuntimeStats();
            }
        };
        private final UiTestDocumentPageController controller = new UiTestDocumentPageController(documentUi,
                pageSurface, runtimeView, UiDocumentScreens.UI_TEST.getPageId(), fontRuntimeStatsSource);
    }

    /**
     * 供测试使用的确定性文本测量桩。
     */
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
            StringBuilder trimmed = new StringBuilder();
            int width = 0;
            for (int index = 0; index < text.length();) {
                int codepoint = text.codePointAt(index);
                if (codepoint == '\r' || codepoint == '\n') {
                    break;
                }
                if (codepoint == '§' && index < text.length() - 1) {
                    trimmed.append(text.charAt(index)).append(text.charAt(index + 1));
                    index += 2;
                    continue;
                }
                int codePointWidth = resolveCodePointWidth(codepoint);
                if (width + codePointWidth > targetWidth) {
                    break;
                }
                trimmed.appendCodePoint(codepoint);
                width += codePointWidth;
                index += Character.charCount(codepoint);
            }
            return trimmed.toString();
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            List<String> wrappedLines = new ArrayList<String>();
            if (text == null || text.isEmpty()) {
                wrappedLines.add("");
                return wrappedLines;
            }

            int safeWrapWidth = Math.max(1, wrapWidth);
            StringBuilder currentLine = new StringBuilder();
            int currentLineWidth = 0;
            for (int index = 0; index < text.length();) {
                int codepoint = text.codePointAt(index);
                if (codepoint == '\r') {
                    index += Character.charCount(codepoint);
                    continue;
                }
                if (codepoint == '\n') {
                    wrappedLines.add(currentLine.toString());
                    currentLine.setLength(0);
                    currentLineWidth = 0;
                    index += Character.charCount(codepoint);
                    continue;
                }
                if (codepoint == '§' && index < text.length() - 1) {
                    currentLine.append(text.charAt(index)).append(text.charAt(index + 1));
                    index += 2;
                    continue;
                }
                int codePointWidth = resolveCodePointWidth(codepoint);
                if (currentLineWidth > 0 && currentLineWidth + codePointWidth > safeWrapWidth) {
                    wrappedLines.add(currentLine.toString());
                    currentLine.setLength(0);
                    currentLineWidth = 0;
                }
                currentLine.appendCodePoint(codepoint);
                currentLineWidth += codePointWidth;
                index += Character.charCount(codepoint);
            }
            wrappedLines.add(currentLine.toString());
            return wrappedLines;
        }

        /**
         * 解析单个字符的逻辑宽度。
         */
        private int resolveCodePointWidth(int codepoint) {
            if (Character.isWhitespace(codepoint)) {
                return ASCII_CHAR_WIDTH;
            }
            return codepoint <= 0x7F ? ASCII_CHAR_WIDTH : WIDE_CHAR_WIDTH;
        }
    }

    /**
     * 供测试使用的运行时视图桩。
     */
    private static final class TestRuntimeView implements DocumentPageRuntimeView {

        private int hostWidth = 1280;
        private int hostHeight = 720;
        private UiRuntimeStats runtimeStats = UiRuntimeStats.empty();

        @Override
        public int getHostWidth() {
            return hostWidth;
        }

        @Override
        public int getHostHeight() {
            return hostHeight;
        }

        @Override
        public UiRuntimeStats getUiRuntimeStats() {
            return runtimeStats;
        }

        /**
         * 更新宿主尺寸。
         */
        private void setHostSize(int hostWidth, int hostHeight) {
            this.hostWidth = hostWidth;
            this.hostHeight = hostHeight;
        }

        /**
         * 更新运行时统计。
         */
        private void setRuntimeStats(UiRuntimeStats runtimeStats) {
            this.runtimeStats = runtimeStats;
        }
    }
}
