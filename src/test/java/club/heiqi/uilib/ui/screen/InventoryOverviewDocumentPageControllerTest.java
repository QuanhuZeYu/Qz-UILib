package club.heiqi.uilib.ui.screen;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.control.ButtonWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.NoOpInventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.control.UiControlRuntimeAdapters;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.DocumentCardWidget;
import club.heiqi.uilib.ui.document.DocumentPageWidget;
import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;
import net.minecraft.item.ItemStack;

/**
 * `InventoryOverviewDocumentPageController` 的黑盒测试。
 */
public class InventoryOverviewDocumentPageControllerTest {

    /**
     * 验证控制器能构建完整页面树并写入诊断文本。
     */
    @Test
    public void shouldBuildDocumentTreeAndRefreshMetrics() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.pagePanel.applyLayoutBounds(0, 0, 980, 680);
        fixture.controller.afterDocumentBuilt();

        List<Widget> blocks = getDocumentBlocks(fixture.pagePanel);
        Assert.assertEquals(6, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof DocumentTextWidget);
        Assert.assertTrue(blocks.get(1) instanceof DocumentTextWidget);
        Assert.assertTrue(blocks.get(2) instanceof DocumentCardWidget);
        Assert.assertTrue(blocks.get(3) instanceof DocumentCardWidget);
        Assert.assertTrue(blocks.get(4) instanceof DocumentCardWidget);
        Assert.assertEquals("背包诊断页", ((LabelWidget) blocks.get(0)).getText());
        Assert.assertEquals("这里不再做左右两栏或摘要联排，只验证网格控件在可靠父宽度下是否能稳定缩放、换列和滚动。", ((LabelWidget) blocks.get(1)).getText());

        List<String> labelTexts = collectLabelTexts(fixture.pagePanel);
        Assert.assertTrue(containsText(labelTexts, "当前状态"));
        Assert.assertTrue(containsText(labelTexts, "快捷栏探针"));
        Assert.assertTrue(containsText(labelTexts, "主背包探针"));
        Assert.assertTrue(containsText(labelTexts, "窗口 1280x720"));
        Assert.assertTrue(containsText(labelTexts, "快捷栏占用 3 / 9"));
        Assert.assertTrue(containsText(labelTexts, "主背包占用 7 / 27"));
    }

    /**
     * 验证页面刷新 hook 与返回按钮行为都由控制器接管。
     */
    @Test
    public void shouldRefreshMetricsAcrossHooksAndHandleBackAction() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.afterDocumentBuilt();

        fixture.model.hotbarOccupiedCount = 5;
        fixture.model.backpackOccupiedCount = 11;
        fixture.runtimeView.setHostSize(1440, 900);
        fixture.controller.onDocumentResized();

        List<String> labelTextsAfterResize = collectLabelTexts(fixture.pagePanel);
        Assert.assertTrue(containsText(labelTextsAfterResize, "窗口 1440x900"));
        Assert.assertTrue(containsText(labelTextsAfterResize, "快捷栏占用 5 / 9"));
        Assert.assertTrue(containsText(labelTextsAfterResize, "主背包占用 11 / 27"));

        fixture.model.hotbarOccupiedCount = 6;
        fixture.model.backpackOccupiedCount = 12;
        fixture.runtimeView.setHostSize(1600, 960);
        fixture.controller.beforeDocumentFrame();

        List<String> labelTextsBeforeFrame = collectLabelTexts(fixture.pagePanel);
        Assert.assertTrue(containsText(labelTextsBeforeFrame, "窗口 1600x960"));
        Assert.assertTrue(containsText(labelTextsBeforeFrame, "快捷栏占用 6 / 9"));
        Assert.assertTrue(containsText(labelTextsBeforeFrame, "主背包占用 12 / 27"));

        ButtonWidget backButton = findFirstWidget(fixture.pagePanel, ButtonWidget.class);
        Assert.assertNotNull(backButton);
        backButton.applyLayoutBounds(0, 0, 200, 40);
        backButton.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        backButton.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 2L));
        Assert.assertEquals(1, fixture.model.returnToVanillaInventoryCalls);
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
     * 递归判断是否存在包含目标片段的标签文本。
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
     * 递归查找首个指定类型组件。
     */
    private static <T extends Widget> T findFirstWidget(Widget root, Class<T> widgetClass) {
        if (widgetClass.isInstance(root)) {
            return widgetClass.cast(root);
        }
        for (Widget child : root.getChildren()) {
            T matched = findFirstWidget(child, widgetClass);
            if (matched != null) {
                return matched;
            }
        }
        return null;
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
     * 控制器测试夹具。
     */
    private static final class TestFixture {

        private final UiDocumentTheme documentTheme = UiDocumentTheme.defaultTheme();
        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final UiControlRuntimeAdapters runtimeAdapters = UiControlRuntimeAdapters.empty()
                .withInventorySlotGridItemRenderer(new NoOpInventorySlotGridItemRenderer());
        private final DocumentUiScope documentUi = new DocumentUiScope(documentTheme, textMeasureService,
                runtimeAdapters);
        private final DocumentPageWidget pagePanel = new DocumentPageWidget(documentTheme, textMeasureService);
        private final DocumentPageAuthoringSurface pageSurface = DocumentPageAuthoringSurface.adapt(pagePanel);
        private final TestRuntimeView runtimeView = new TestRuntimeView();
        private final TestInventoryOverviewModel model = new TestInventoryOverviewModel();
        private final InventoryOverviewDocumentPageController controller = new InventoryOverviewDocumentPageController(
                documentUi, pageSurface, runtimeView, model);
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
            return UiRuntimeStats.empty();
        }

        /**
         * 更新宿主尺寸。
         */
        private void setHostSize(int hostWidth, int hostHeight) {
            this.hostWidth = hostWidth;
            this.hostHeight = hostHeight;
        }
    }

    /**
     * 供测试使用的页面模型桩。
     */
    private static final class TestInventoryOverviewModel implements InventoryOverviewModel {

        private final InventoryOverviewSlotContentProvider emptySlotProvider = new InventoryOverviewSlotContentProvider() {
            @Override
            public ItemStack getStack(int localIndex) {
                return null;
            }
        };

        private int hotbarOccupiedCount = 3;
        private int backpackOccupiedCount = 7;
        private int returnToVanillaInventoryCalls;

        @Override
        public InventoryOverviewSlotContentProvider getHotbarSlotProvider() {
            return emptySlotProvider;
        }

        @Override
        public InventoryOverviewSlotContentProvider getBackpackSlotProvider() {
            return emptySlotProvider;
        }

        @Override
        public int getHotbarOccupiedCount() {
            return hotbarOccupiedCount;
        }

        @Override
        public int getBackpackOccupiedCount() {
            return backpackOccupiedCount;
        }

        @Override
        public void returnToVanillaInventory() {
            returnToVanillaInventoryCalls++;
        }
    }
}
