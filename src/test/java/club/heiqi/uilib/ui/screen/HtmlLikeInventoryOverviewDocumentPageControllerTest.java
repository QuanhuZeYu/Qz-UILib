package club.heiqi.uilib.ui.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;
import club.heiqi.uilib.ui.inventory.NoOpInventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemGeometry;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiStyleResolver;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;
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
        Assert.assertTrue(containsText(blockTexts, "背包概览"));
        Assert.assertTrue(containsText(blockTexts, "快捷栏"));
        Assert.assertTrue(containsText(blockTexts, "主背包"));
        Assert.assertFalse(containsText(blockTexts, "真实迁移验证卡"));
        Assert.assertFalse(containsText(blockTexts, "inline pill"));
        Assert.assertFalse(containsText(blockTexts, "Inventory fixed hint"));
        Assert.assertTrue(containsText(blockTexts, "窗口 720x600"));
        Assert.assertTrue(containsText(blockTexts, "快捷栏占用 3 / 9。当前持有槽 1"));
        Assert.assertTrue(containsText(blockTexts, "主背包占用 7 / 27。鼠标携带 空"));

        ElementNode root = fixture.controller.getHtmlLikeDocumentWidget().getDocument().getRootElement();
        Assert.assertEquals(1, collectElementsByTag(root, "main").size());
        Assert.assertEquals(1, collectElementsByTag(root, "header").size());
        Assert.assertEquals(2, collectElementsByTag(root, "section").size());
        Assert.assertEquals(1, collectElementsByTag(root, "footer").size());
        Assert.assertEquals(1, collectElementsByTag(root, "h1").size());
        Assert.assertEquals(2, collectElementsByTag(root, "h2").size());
        Assert.assertFalse(collectElementsByTag(root, "p").isEmpty());
        Assert.assertEquals("button", collectElementsByTag(root, "button").get(0).getTagName());
        Assert.assertEquals("true", collectElementsByAttribute(root, "data-cursor-item-layer", "true").get(0)
                .getAttribute("data-hit-test-hidden"));
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
        Assert.assertTrue(containsText(labelTextsAfterResize, "快捷栏占用 5 / 9。当前持有槽 1"));
        Assert.assertTrue(containsText(labelTextsAfterResize, "主背包占用 11 / 27。鼠标携带 空"));

        fixture.model.hotbarOccupiedCount = 6;
        fixture.model.backpackOccupiedCount = 12;
        fixture.runtimeView.setHostSize(1600, 960);
        fixture.controller.beforeDocumentFrame();

        List<String> labelTextsBeforeFrame = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(labelTextsBeforeFrame, "窗口 1600x960"));
        Assert.assertTrue(containsText(labelTextsBeforeFrame, "快捷栏占用 6 / 9。当前持有槽 1"));
        Assert.assertTrue(containsText(labelTextsBeforeFrame, "主背包占用 12 / 27。鼠标携带 空"));

        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(0, 0, 720, 600);
        widget.onFocusTraversalEntered(false);
        while (widget.onFocusTraversal(false)) {}
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));

        Assert.assertEquals(1, fixture.model.returnToVanillaInventoryCalls);
    }

    /**
     * 验证背包页会把 slot hover/click 代理到模型，并刷新当前持有槽高亮。
     */
    @Test
    public void shouldProxySlotHoverClickAndSelectedHotbarStateToModel() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.afterDocumentBuilt();

        ElementNode hotbarSlot = findFirstSlotInGrid(fixture.controller.getHtmlLikeDocumentWidget(), 0);
        ElementNode backpackSlot = findFirstSlotInGrid(fixture.controller.getHtmlLikeDocumentWidget(), 1);

        hotbarSlot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(hotbarSlot, hotbarSlot, true,
                0, 0, 1L));
        Assert.assertEquals(Integer.valueOf(0xDD263349), hotbarSlot.style().getBackgroundColor());
        hotbarSlot.getClickHandler().onClick(new DocumentElementClickEvent(hotbarSlot, hotbarSlot, 0, 0, 1, 2L));
        backpackSlot.getClickHandler().onClick(new DocumentElementClickEvent(backpackSlot, backpackSlot, 0, 0, 0,
                3L));

        Assert.assertEquals("hotbar:0:1", fixture.model.slotClickCalls.get(0));
        Assert.assertEquals("backpack:0:0", fixture.model.slotClickCalls.get(1));
        Assert.assertEquals("hotbar:0", fixture.model.tooltipCalls.get(0));

        fixture.model.selectedHotbarSlotIndex = 4;
        fixture.controller.beforeDocumentFrame();
        ElementNode selectedHotbarSlot = findSlotInGrid(fixture.controller.getHtmlLikeDocumentWidget(), 0, 4);
        Assert.assertEquals(Integer.valueOf(0xDD273B20), selectedHotbarSlot.style().getBackgroundColor());
        Assert.assertTrue(containsText(collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget()),
                "当前持有槽 5"));
    }

    /**
     * 验证显式空白投放区域会代理为丢弃鼠标携带物品的模型操作。
     */
    @Test
    public void shouldProxyDropZoneClickAsOutsideInventoryClick() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.afterDocumentBuilt();

        ElementNode dropZone = collectElementsByTag(fixture.controller.getHtmlLikeDocumentWidget()
                .getDocument().getRootElement(), "main").get(0);
        Assert.assertEquals("true", dropZone.getAttribute("data-inventory-drop-zone"));
        dropZone.getClickHandler().onClick(new DocumentElementClickEvent(dropZone, dropZone, 4, 4, 0, 1L));

        Assert.assertEquals(Collections.singletonList("backpack:-1:0"), fixture.model.slotClickCalls);
    }

    /**
     * 验证 slot tooltip 由 DOM fixed tooltip surface 表达，不再由 slot 控件手绘 overlay。
     */
    @Test
    public void shouldRenderSlotTooltipAsDomSurface() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.afterDocumentBuilt();

        ElementNode hotbarSlot = findFirstSlotInGrid(fixture.controller.getHtmlLikeDocumentWidget(), 0);
        hotbarSlot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(hotbarSlot, hotbarSlot, true,
                32, 44, 1L));

        ElementNode tooltip = collectElementsByAttribute(fixture.controller.getHtmlLikeDocumentWidget()
                .getDocument().getRootElement(), "data-inventory-tooltip", "true").get(0);

        Assert.assertEquals("aside", tooltip.getTagName());
        Assert.assertEquals("tooltip", tooltip.getAttribute("role"));
        Assert.assertEquals("false", tooltip.getAttribute("aria-hidden"));
        Assert.assertEquals(UiDisplay.BLOCK, UiStyleResolver.compute(tooltip).getDisplay());
        Assert.assertTrue(tooltip.style().getWidth().getValue() > 0.0F);
        Assert.assertEquals(66.0F, tooltip.style().getTop().getValue(), 0.001F);
        Assert.assertTrue(containsText(collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget()),
                "Tooltip 0"));
        fixture.controller.getHtmlLikeDocumentWidget().applyLayoutBounds(0, 0, 720, 600);
        ElementNode tooltipText = collectElementsByTag(tooltip, "p").get(0);
        ElementNode hitElement = fixture.controller.getHtmlLikeDocumentWidget().findElementAt(50, 60);
        Assert.assertTrue(hitElement == null || hitElement.__getElementUid() != tooltip.__getElementUid());
        Assert.assertTrue(hitElement == null || hitElement.__getElementUid() != tooltipText.__getElementUid());

        hotbarSlot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(hotbarSlot, hotbarSlot, false,
                -1, -1, 2L));
        Assert.assertEquals("true", tooltip.getAttribute("aria-hidden"));
        Assert.assertEquals(0.0F, tooltip.style().getWidth().getValue(), 0.001F);
    }

    /**
     * 验证鼠标携带物品由页面级语义 layer 单次登记 overlay。
     */
    @Test
    public void shouldRegisterCursorItemOverlayFromPageLayer() {
        TestFixture fixture = new TestFixture();
        RecordingInventorySlotGridItemRenderer itemRenderer = new RecordingInventorySlotGridItemRenderer();
        fixture.runtimeAdapters = UiRuntimeAdapters.empty().withInventorySlotGridItemRenderer(itemRenderer);
        fixture.model.carriedSlotSnapshot = InventorySlotSnapshot.occupied();
        fixture.recreateController();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        fixture.controller.afterDocumentBuilt();

        ElementNode hotbarSlot = findFirstSlotInGrid(fixture.controller.getHtmlLikeDocumentWidget(), 0);
        hotbarSlot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(hotbarSlot, hotbarSlot, true,
                20, 20, 1L));
        ElementNode tooltip = collectElementsByAttribute(fixture.controller.getHtmlLikeDocumentWidget()
                .getDocument().getRootElement(), "data-inventory-tooltip", "true").get(0);
        Assert.assertEquals("true", tooltip.getAttribute("aria-hidden"));

        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(0, 0, 720, 600);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext(720, 600, 90, 72);
        widget.render(renderContext);
        for (club.heiqi.uilib.ui.render.UiRenderContext.DeferredPostMainPassReplay replay
                : renderContext.deferredReplays) {
            replay.replay();
        }

        ElementNode cursorLayer = collectElementsByAttribute(widget.getDocument().getRootElement(),
                "data-cursor-item-layer", "true").get(0);

        Assert.assertEquals("aside", cursorLayer.getTagName());
        Assert.assertEquals("true", cursorLayer.getAttribute("aria-hidden"));
        Assert.assertNotNull(cursorLayer.getCustomRenderer());
        Assert.assertEquals(Collections.singletonList("90:72:true"), itemRenderer.cursorCalls);
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

    private static List<ElementNode> collectElementsByTag(DocumentNode node, String tagName) {
        List<ElementNode> elements = new ArrayList<ElementNode>();
        collectElementsByTag(node, tagName, elements);
        return elements;
    }

    private static List<ElementNode> collectElementsByAttribute(DocumentNode node, String attributeName,
            String attributeValue) {
        List<ElementNode> elements = new ArrayList<ElementNode>();
        collectElementsByAttribute(node, attributeName, attributeValue, elements);
        return elements;
    }

    private static void collectElementsByAttribute(DocumentNode node, String attributeName, String attributeValue,
            List<ElementNode> elements) {
        if (node.getNodeType() == DocumentNodeType.ELEMENT) {
            ElementNode element = (ElementNode) node;
            if (attributeValue.equals(element.getAttribute(attributeName))) {
                elements.add(element);
            }
            for (DocumentNode child : element.getChildren()) {
                collectElementsByAttribute(child, attributeName, attributeValue, elements);
            }
        }
    }

    private static ElementNode findFirstSlotInGrid(HtmlLikeDocumentWidget widget, int gridIndex) {
        return findSlotInGrid(widget, gridIndex, 0);
    }

    private static ElementNode findSlotInGrid(HtmlLikeDocumentWidget widget, int gridIndex, int slotIndex) {
        List<ElementNode> tables = new ArrayList<ElementNode>();
        collectElementsByTag(widget.getDocument().getRootElement(), "table", tables);
        ElementNode table = tables.get(gridIndex);
        ElementNode body = tableBodyElement(table);
        ElementNode row = (ElementNode) body.getChildren().get(slotIndex / 9);
        return (ElementNode) row.getChildren().get(slotIndex % 9);
    }

    private static ElementNode tableBodyElement(ElementNode table) {
        for (DocumentNode child : table.getChildren()) {
            if (child instanceof ElementNode && "tbody".equals(((ElementNode) child).getTagName())) {
                return (ElementNode) child;
            }
        }
        throw new AssertionError("table body not found");
    }

    private static void collectElementsByTag(DocumentNode node, String tagName, List<ElementNode> elements) {
        if (node.getNodeType() == DocumentNodeType.ELEMENT) {
            ElementNode element = (ElementNode) node;
            if (tagName.equals(element.getTagName())) {
                elements.add(element);
            }
            for (DocumentNode child : element.getChildren()) {
                collectElementsByTag(child, tagName, elements);
            }
        }
    }

    private static final class TestFixture {

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private UiRuntimeAdapters runtimeAdapters = UiRuntimeAdapters.empty()
                .withInventorySlotGridItemRenderer(new NoOpInventorySlotGridItemRenderer());
        private DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, runtimeAdapters);
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final TestRuntimeView runtimeView = new TestRuntimeView();
        private final TestInventoryOverviewModel model = new TestInventoryOverviewModel();
        private HtmlLikeInventoryOverviewDocumentPageController controller = new HtmlLikeInventoryOverviewDocumentPageController(
                documentUi, pageSurface, runtimeView, model);

        private void recreateController() {
            documentUi = new DocumentUiScope(textMeasureService, runtimeAdapters);
            controller = new HtmlLikeInventoryOverviewDocumentPageController(documentUi, pageSurface, runtimeView,
                    model);
        }
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

        private int hostWidth = 720;
        private int hostHeight = 600;

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

    private static final class RecordingInventorySlotGridItemRenderer implements InventorySlotGridItemRenderer {

        private final List<String> cursorCalls = new ArrayList<String>();

        @Override
        public void renderItems(InventorySlotGridItemGeometry geometry, InventorySlotSnapshot[] slotSnapshots) {}

        @Override
        public void renderCursorItem(InventorySlotSnapshot carriedSnapshot, int mouseX, int mouseY) {
            cursorCalls.add(mouseX + ":" + mouseY + ":" + carriedSnapshot.isOccupied());
        }
    }

    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DeferredPostMainPassReplay> deferredReplays = new ArrayList<DeferredPostMainPassReplay>();

        private RecordingUiRenderContext(int width, int height, int mouseX, int mouseY) {
            super(width, height, mouseX, mouseY, 1.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {}

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {}

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {}

        @Override
        public void popClip() {}

        @Override
        public void enqueueDeferredPostMainPass(DeferredPostMainPassReplay replay) {
            deferredReplays.add(replay);
        }

        @Override
        public void enqueueDeferredPostMainOverlayPass(DeferredPostMainPassReplay replay) {
            deferredReplays.add(replay);
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
        private int selectedHotbarSlotIndex;
        private InventorySlotSnapshot carriedSlotSnapshot = InventorySlotSnapshot.empty();
        private int returnToVanillaInventoryCalls;
        private final List<String> slotClickCalls = new ArrayList<String>();
        private final List<String> tooltipCalls = new ArrayList<String>();

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
        public int getSelectedHotbarSlotIndex() {
            return selectedHotbarSlotIndex;
        }

        @Override
        public InventorySlotSnapshot getCarriedSlotSnapshot() {
            return carriedSlotSnapshot;
        }

        @Override
        public List<String> getSlotTooltip(boolean hotbar, int localIndex) {
            tooltipCalls.add((hotbar ? "hotbar" : "backpack") + ":" + localIndex);
            return Collections.singletonList("Tooltip " + localIndex);
        }

        @Override
        public boolean handleSlotClick(boolean hotbar, int localIndex, int button) {
            slotClickCalls.add((hotbar ? "hotbar" : "backpack") + ":" + localIndex + ":" + button);
            return true;
        }

        @Override
        public void returnToVanillaInventory() {
            returnToVanillaInventoryCalls++;
        }
    }
}
