package club.heiqi.uilib.ui.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;
import club.heiqi.uilib.ui.inventory.NoOpInventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `HtmlLikeInventoryOverviewDocumentPageController` 的基础契约测试。
 */
public class HtmlLikeInventoryOverviewDocumentPageControllerTest {

    /**
     * 验证控制器能构建完整页面并刷新 HTML-like 指标。
     */
    @Test
    public void shouldBuildDocumentTreeAndRefreshMetrics() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.afterDocumentBuilt();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(0), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(fixture.controller.getHtmlLikeDocumentWidget().isViewportRootScrollingEnabled());

        List<String> blockTexts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(blockTexts, "背包诊断中枢"));
        Assert.assertTrue(containsText(blockTexts, "快捷栏探针"));
        Assert.assertTrue(containsText(blockTexts, "主背包探针"));
        Assert.assertTrue(containsText(blockTexts, "真实迁移验证卡"));
        Assert.assertTrue(containsText(blockTexts, "inline pill"));
        Assert.assertTrue(containsText(blockTexts, "Inventory fixed hint"));
        Assert.assertTrue(containsText(blockTexts, "窗口 1280x720"));
        Assert.assertTrue(containsText(blockTexts, "快捷栏占用 3 / 9"));
        Assert.assertTrue(containsText(blockTexts, "主背包占用 7 / 27"));
    }

    /**
     * 验证页面刷新 hook 与返回按钮行为。
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

        List<String> labelTextsAfterResize = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(labelTextsAfterResize, "窗口 1440x900"));
        Assert.assertTrue(containsText(labelTextsAfterResize, "快捷栏占用 5 / 9"));
        Assert.assertTrue(containsText(labelTextsAfterResize, "主背包占用 11 / 27"));

        fixture.model.hotbarOccupiedCount = 6;
        fixture.model.backpackOccupiedCount = 12;
        fixture.runtimeView.setHostSize(1600, 960);
        fixture.controller.beforeDocumentFrame();

        List<String> labelTextsBeforeFrame = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(labelTextsBeforeFrame, "窗口 1600x960"));
        Assert.assertTrue(containsText(labelTextsBeforeFrame, "快捷栏占用 6 / 9"));
        Assert.assertTrue(containsText(labelTextsBeforeFrame, "主背包占用 12 / 27"));

        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(0, 0, 720, 600);
        widget.onFocusTraversalEntered(false);
        Assert.assertTrue(widget.onFocusTraversal(false));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));

        Assert.assertEquals(1, fixture.model.returnToVanillaInventoryCalls);
    }

    /**
     * 验证真实迁移卡组合了动画、toggle、inline、fixed 与 overflow 文本路径。
     */
    @Test
    public void shouldExposeInteractiveMigrationValidationCard() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.afterDocumentBuilt();

        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        ElementNode migrationCard = findElementContainingText(widget, "迁移卡片：紧凑");
        Assert.assertNotNull(migrationCard);
        Assert.assertTrue(migrationCard.style().getTransitionProperties().contains(DocumentAnimationProperty.WIDTH));
        Assert.assertTrue(migrationCard.style().getTransitionProperties()
                .contains(DocumentAnimationProperty.BACKGROUND_COLOR));
        Assert.assertTrue(migrationCard.style().getTransitionProperties()
                .contains(DocumentAnimationProperty.BORDER_RADIUS));
        Assert.assertEquals(UiStyleLength.px(150), migrationCard.style().getWidth());
        Assert.assertNotNull(findElementContainingText(widget, "inline pill"));
        Assert.assertNotNull(findElementContainingText(widget, "Inventory fixed hint"));

        migrationCard.getClickHandler().onClick(new DocumentElementClickEvent(migrationCard, migrationCard, 0, 0, 0,
                1L));

        List<String> expandedTexts = collectDocumentTexts(widget);
        Assert.assertEquals(UiStyleLength.px(220), migrationCard.style().getWidth());
        Assert.assertTrue(containsText(expandedTexts, "迁移卡片：展开"));
        Assert.assertTrue(containsText(expandedTexts, "状态：展开模式"));
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

    private static ElementNode findElementContainingText(HtmlLikeDocumentWidget widget, String expectedSnippet) {
        return findElementContainingText(widget.getDocument().getRootElement(), expectedSnippet);
    }

    private static ElementNode findElementContainingText(ElementNode element, String expectedSnippet) {
        for (DocumentNode child : element.getChildren()) {
            if (child.getNodeType() == DocumentNodeType.TEXT
                    && ((TextNode) child).getText().contains(expectedSnippet)) {
                return element;
            }
            if (child.getNodeType() == DocumentNodeType.ELEMENT) {
                ElementNode matched = findElementContainingText((ElementNode) child, expectedSnippet);
                if (matched != null) {
                    return matched;
                }
            }
        }
        return null;
    }

    private static final class TestFixture {

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final UiRuntimeAdapters runtimeAdapters = UiRuntimeAdapters.empty()
                .withInventorySlotGridItemRenderer(new NoOpInventorySlotGridItemRenderer());
        private final DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, runtimeAdapters);
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final TestRuntimeView runtimeView = new TestRuntimeView();
        private final TestInventoryOverviewModel model = new TestInventoryOverviewModel();
        private final HtmlLikeInventoryOverviewDocumentPageController controller = new HtmlLikeInventoryOverviewDocumentPageController(
                documentUi, pageSurface, runtimeView, model);
    }

    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            return text.length() * 6;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            return text == null ? "" : text.length() <= targetWidth / 6 ? text : text.substring(0, targetWidth / 6);
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }

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

        private void setHostSize(int hostWidth, int hostHeight) {
            this.hostWidth = hostWidth;
            this.hostHeight = hostHeight;
        }
    }

    private static final class TestInventoryOverviewModel implements InventoryOverviewModel {

        private final InventoryOverviewSlotContentProvider hotbarSlotProvider = new InventoryOverviewSlotContentProvider() {
            @Override
            public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                return localIndex < hotbarOccupiedCount
                        ? InventorySlotSnapshot.occupied() : InventorySlotSnapshot.empty();
            }
        };

        private final InventoryOverviewSlotContentProvider backpackSlotProvider = new InventoryOverviewSlotContentProvider() {
            @Override
            public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                return localIndex < backpackOccupiedCount
                        ? InventorySlotSnapshot.occupied() : InventorySlotSnapshot.empty();
            }
        };

        private int hotbarOccupiedCount = 3;
        private int backpackOccupiedCount = 7;
        private int returnToVanillaInventoryCalls;

        @Override
        public InventoryOverviewSlotContentProvider getHotbarSlotProvider() {
            return hotbarSlotProvider;
        }

        @Override
        public InventoryOverviewSlotContentProvider getBackpackSlotProvider() {
            return backpackSlotProvider;
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
