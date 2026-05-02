package club.heiqi.uilib.ui.dom.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemGeometry;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.paint.DocumentPaintCommand;
import club.heiqi.uilib.ui.paint.DocumentPaintCommandType;
import club.heiqi.uilib.ui.paint.DocumentPaintEngine;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.style.UiStyleResolver;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * `DocumentInventorySlotGridControl` 的基础行为契约测试。
 */
public class DocumentInventorySlotGridControlTest {

    /**
     * 验证空槽网格渲染时不触发物品渲染器。
     */
    @Test
    public void shouldRenderEmptySlotGridWithoutCallingItemRenderer() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(400))
                .setHeight(UiStyleLength.px(200));
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 9, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setContentProvider(new DocumentInventorySlotGridControl.SlotContentProvider() {
                    @Override
                    public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                        return InventorySlotSnapshot.empty();
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 400, 200,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 400, 200);
        widget.render(renderContext);

        Assert.assertTrue(renderContext.deferredReplays.isEmpty());
        List<DocumentPaintCommand> commands = buildPaintCommands(root, 400, 200);
        Assert.assertEquals(1, countCommands(commands, DocumentPaintCommandType.CUSTOM));
        Assert.assertTrue(containsCommand(commands, DocumentPaintCommandType.BACKGROUND, 0xAA171C24));
    }

    /**
     * 验证占用槽触发物品渲染器。
     */
    @Test
    public void shouldCallItemRendererWhenSlotsAreOccupied() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(400))
                .setHeight(UiStyleLength.px(200));
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        final List<InventorySlotSnapshot[]> receivedSnapshots = new ArrayList<InventorySlotSnapshot[]>();
        final List<InventorySlotGridItemGeometry> receivedGeometries = new ArrayList<InventorySlotGridItemGeometry>();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 9, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setContentProvider(new DocumentInventorySlotGridControl.SlotContentProvider() {
                    @Override
                    public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                        return localIndex == 0 ? InventorySlotSnapshot.occupied() : InventorySlotSnapshot.empty();
                    }
                })
                .setItemRenderer(new InventorySlotGridItemRenderer() {
                    @Override
                    public void renderItems(InventorySlotGridItemGeometry geometry, InventorySlotSnapshot[] slotSnapshots) {
                        receivedGeometries.add(geometry);
                        receivedSnapshots.add(slotSnapshots);
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 400, 200,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 400, 200);
        widget.render(renderContext);

        Assert.assertEquals(1, renderContext.deferredReplays.size());
        renderContext.deferredReplays.get(0).replay();
        Assert.assertEquals(1, receivedSnapshots.size());
        Assert.assertEquals(9, receivedSnapshots.get(0).length);
        Assert.assertTrue(receivedSnapshots.get(0)[0].isOccupied());
        Assert.assertFalse(receivedSnapshots.get(0)[1].isOccupied());
        Assert.assertEquals(9, receivedGeometries.get(0).getSlotCount());
        Assert.assertEquals(1, countCommands(buildPaintCommands(root, 400, 200), DocumentPaintCommandType.CUSTOM));
    }

    /**
     * 验证无 contentProvider 时全部识别为空槽。
     */
    @Test
    public void shouldTreatAllSlotsAsEmptyWhenNoContentProvider() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(400))
                .setHeight(UiStyleLength.px(200));
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        final boolean[] rendererCalled = new boolean[] { false };
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 9, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setItemRenderer(new InventorySlotGridItemRenderer() {
                    @Override
                    public void renderItems(InventorySlotGridItemGeometry geometry, InventorySlotSnapshot[] slotSnapshots) {
                        rendererCalled[0] = true;
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 400, 200,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 400, 200);
        widget.render(renderContext);

        Assert.assertTrue(renderContext.deferredReplays.isEmpty());
        Assert.assertFalse(rendererCalled[0]);
    }

    /**
     * 验证网格布局尺寸按期望列数计算。
     */
    @Test
    public void shouldComputePreferredLayoutSize() {
        UiDocument document = UiDocument.create();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 9, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .commitLayout();

        int expectedWidth = 9 * 32 + 8 * 4;
        int expectedHeight = 1 * 32;
        Assert.assertEquals(expectedWidth, (int) gridControl.getElement().style().getWidth().getValue());
        Assert.assertEquals(expectedHeight, (int) gridControl.getElement().style().getHeight().getValue());
    }

    /**
     * 验证槽位 DOM 表面落在元素内容盒内，不侵入 padding 区域。
     */
    @Test
    public void shouldRenderSlotsInsideContentBox() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(100));
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 1, 1)
                .setPreferredSlotSize(32)
                .setSlotColors(0xFF111111, 0, 0xFF222222, 0)
                .commitLayout();
        gridControl.getElement().style()
                .setBorderWidth(UiStyleLength.px(1))
                .setPadding(UiStyleLength.px(10));
        root.append(gridControl.getElement());
        Assert.assertTrue(containsCommand(buildPaintCommands(root, 200, 100), DocumentPaintCommandType.BACKGROUND,
                0xFF111111, 11, 11, 43, 43));
    }

    /**
     * 验证默认槽位底色仍可保持半透明，并通过标准 DOM 背景命令绘制。
     */
    @Test
    public void shouldRenderDefaultSlotsWithSemiTransparentDomSurfaces() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(100));
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 2, 2)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setContentProvider(new DocumentInventorySlotGridControl.SlotContentProvider() {
                    @Override
                    public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                        return localIndex == 0 ? InventorySlotSnapshot.empty() : InventorySlotSnapshot.occupied();
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());

        List<DocumentPaintCommand> commands = buildPaintCommands(root, 200, 100);

        Assert.assertTrue(containsCommand(commands, DocumentPaintCommandType.BACKGROUND, 0xAA171C24));
        Assert.assertTrue(containsCommand(commands, DocumentPaintCommandType.BACKGROUND, 0xCC202A38));
        Assert.assertTrue(containsCommand(commands, DocumentPaintCommandType.BORDER, 0xFF465468));
        Assert.assertTrue(containsCommand(commands, DocumentPaintCommandType.BORDER, 0xFF9AB8F2));
        Assert.assertEquals(1, countCommands(commands, DocumentPaintCommandType.CUSTOM));
    }

    /**
     * 验证槽位表面由真实 table/tr/td 文档流子元素组成，而不是自定义绘制器直接画格子。
     */
    @Test
    public void shouldBuildSlotCellsAsTableDomElements() {
        UiDocument document = UiDocument.create();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 10, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setContentProvider(new DocumentInventorySlotGridControl.SlotContentProvider() {
                    @Override
                    public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                        return localIndex == 0 ? InventorySlotSnapshot.occupied() : InventorySlotSnapshot.empty();
                    }
                })
                .commitLayout();

        ElementNode table = gridControl.getElement();
        ElementNode body = (ElementNode) table.getChildren().get(1);
        ElementNode firstRow = (ElementNode) body.getChildren().get(0);
        ElementNode firstSlot = (ElementNode) firstRow.getChildren().get(0);
        ElementNode secondSlot = (ElementNode) firstRow.getChildren().get(1);

        Assert.assertEquals("table", table.getTagName());
        Assert.assertEquals("tbody", body.getTagName());
        Assert.assertEquals("tr", firstRow.getTagName());
        Assert.assertEquals("td", firstSlot.getTagName());
        Assert.assertEquals(UiDisplay.TABLE, UiStyleResolver.compute(table).getDisplay());
        Assert.assertEquals(UiDisplay.TABLE_ROW_GROUP, UiStyleResolver.compute(body).getDisplay());
        Assert.assertEquals(UiDisplay.TABLE_ROW, UiStyleResolver.compute(firstRow).getDisplay());
        Assert.assertEquals(UiDisplay.TABLE_CELL, UiStyleResolver.compute(firstSlot).getDisplay());
        Assert.assertEquals(2, body.getChildCount());
        Assert.assertEquals(9, firstRow.getChildCount());
        Assert.assertEquals(Integer.valueOf(0xCC202A38), firstSlot.style().getBackgroundColor());
        Assert.assertEquals(Integer.valueOf(0xFF9AB8F2), firstSlot.style().getBorderColor());
        Assert.assertEquals(Integer.valueOf(0xAA171C24), secondSlot.style().getBackgroundColor());
        Assert.assertEquals(Integer.valueOf(0xFF465468), secondSlot.style().getBorderColor());
        Assert.assertEquals(UiStyleLength.px(30), firstSlot.style().getWidth());
        Assert.assertEquals(UiStyleLength.px(30), firstSlot.style().getHeight());
        for (DocumentNode rowNode : body.getChildren()) {
            Assert.assertTrue(rowNode instanceof ElementNode);
        }
    }

    /**
     * 验证背包槽位 table 布局仍与底层物品几何保持一致。
     */
    @Test
    public void shouldLayoutTableSlotsAtInventoryGeometryPositions() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(140));
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 10, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .commitLayout();
        root.append(gridControl.getElement());

        DocumentLayoutBox tableBox = DocumentLayoutEngine.layout(root, 240, 140,
                new DeterministicTextMeasureService()).getChildren().get(0);
        DocumentLayoutBox bodyBox = tableBox.getChildren().get(0);
        DocumentLayoutBox firstRowBox = bodyBox.getChildren().get(0);
        DocumentLayoutBox secondRowBox = bodyBox.getChildren().get(1);
        DocumentLayoutBox firstSlotBox = firstRowBox.getChildren().get(0);
        DocumentLayoutBox secondSlotBox = firstRowBox.getChildren().get(1);
        DocumentLayoutBox tenthSlotBox = secondRowBox.getChildren().get(0);

        Assert.assertEquals(320, tableBox.getWidth());
        Assert.assertEquals(68, tableBox.getHeight());
        Assert.assertEquals(32, firstSlotBox.getWidth());
        Assert.assertEquals(32, firstSlotBox.getHeight());
        Assert.assertEquals(36, secondSlotBox.getLeft());
        Assert.assertEquals(36, secondRowBox.getTop());
        Assert.assertEquals(0, tenthSlotBox.getLeft());
        Assert.assertEquals(36, tenthSlotBox.getTop());
    }

    /**
     * 验证槽位 hover 会更新高亮、tooltip，并在鼠标离开时恢复。
     */
    @Test
    public void shouldHighlightHoveredSlotAndRenderTooltipOverlay() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(140));
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 2, 2)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setSlotTooltipProvider(new DocumentInventorySlotGridControl.SlotTooltipProvider() {
                    @Override
                    public List<String> getSlotTooltip(int localIndex) {
                        return Collections.singletonList("Tooltip " + localIndex);
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());
        ElementNode firstSlot = firstSlotElement(gridControl);

        firstSlot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(firstSlot, firstSlot, true, 6, 6,
                1L));
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext(240, 140, 20, 20);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 140,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 140);
        widget.render(renderContext);

        Assert.assertEquals(0, gridControl.getHoveredSlotIndex());
        Assert.assertEquals(Integer.valueOf(0xDD263349), firstSlot.style().getBackgroundColor());
        Assert.assertFalse(renderContext.deferredReplays.isEmpty());
        renderContext.deferredReplays.get(renderContext.deferredReplays.size() - 1).replay();
        Assert.assertTrue(renderContext.textCalls.contains("Tooltip 0"));

        firstSlot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(firstSlot, firstSlot, false, -1, -1,
                2L));
        Assert.assertEquals(-1, gridControl.getHoveredSlotIndex());
        Assert.assertEquals(Integer.valueOf(0xAA171C24), firstSlot.style().getBackgroundColor());
    }

    /**
     * 验证槽位点击 handler 会接收按钮和本地索引。
     */
    @Test
    public void shouldDispatchSlotClickWithButtonToHandler() {
        UiDocument document = UiDocument.create();
        final List<Integer> clickedSlots = new ArrayList<Integer>();
        final List<Integer> clickedButtons = new ArrayList<Integer>();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 2, 2)
                .setSlotClickHandler(new DocumentInventorySlotGridControl.SlotClickHandler() {
                    @Override
                    public boolean onSlotClick(int localIndex, int button, long timeNanos) {
                        clickedSlots.add(Integer.valueOf(localIndex));
                        clickedButtons.add(Integer.valueOf(button));
                        return true;
                    }
                })
                .commitLayout();
        ElementNode firstSlot = firstSlotElement(gridControl);

        Assert.assertTrue(firstSlot.getClickHandler().onClick(new DocumentElementClickEvent(firstSlot, firstSlot,
                4, 4, 1, 3L)));

        Assert.assertEquals(Collections.singletonList(Integer.valueOf(0)), clickedSlots);
        Assert.assertEquals(Collections.singletonList(Integer.valueOf(1)), clickedButtons);
    }

    /**
     * 验证当前选中槽位和按下态拥有独立高亮样式。
     */
    @Test
    public void shouldApplySelectedAndActiveSlotHighlights() {
        UiDocument document = UiDocument.create();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 2, 2)
                .setSelectedSlotIndex(1)
                .commitLayout();
        ElementNode firstSlot = firstSlotElement(gridControl);
        ElementNode secondSlot = secondSlotElement(gridControl);

        Assert.assertEquals(Integer.valueOf(0xDD273B20), secondSlot.style().getBackgroundColor());
        Assert.assertEquals(Integer.valueOf(0xFFFFD166), secondSlot.style().getBorderColor());

        firstSlot.getActiveHandler().onActiveChanged(new DocumentElementActiveEvent(firstSlot, firstSlot, true,
                0, 1L));

        Assert.assertEquals(Integer.valueOf(0xEE334155), firstSlot.style().getBackgroundColor());
        Assert.assertEquals(Integer.valueOf(0xFFFFFFFF), firstSlot.style().getBorderColor());
    }

    /**
     * 验证鼠标携带物品使用顶层延迟回放，不依赖 slot 物品批次。
     */
    @Test
    public void shouldRenderCarriedSnapshotThroughOverlayRenderer() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(140));
        final List<String> cursorCalls = new ArrayList<String>();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 1, 1)
                .setCarriedSnapshot(InventorySlotSnapshot.occupied())
                .setItemRenderer(new InventorySlotGridItemRenderer() {
                    @Override
                    public void renderItems(InventorySlotGridItemGeometry geometry,
                            InventorySlotSnapshot[] slotSnapshots) {}

                    @Override
                    public void renderCursorItem(InventorySlotSnapshot carriedSnapshot, int mouseX, int mouseY) {
                        cursorCalls.add(mouseX + ":" + mouseY + ":" + carriedSnapshot.isOccupied());
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext(240, 140, 88, 66);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 140,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 140);

        widget.render(renderContext);
        Assert.assertEquals(1, renderContext.deferredReplays.size());
        renderContext.deferredReplays.get(0).replay();

        Assert.assertEquals(Collections.singletonList("88:66:true"), cursorCalls);
    }

    private static List<DocumentPaintCommand> buildPaintCommands(ElementNode root, int width, int height) {
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, width, height,
                new DeterministicTextMeasureService());
        return DocumentPaintEngine.buildPaintCommands(rootBox);
    }

    private static boolean containsCommand(List<DocumentPaintCommand> commands, DocumentPaintCommandType type,
            int color) {
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == type && command.getColor() == color) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCommand(List<DocumentPaintCommand> commands, DocumentPaintCommandType type,
            int color, int left, int top, int right, int bottom) {
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == type && command.getColor() == color && command.getLeft() == left
                    && command.getTop() == top && command.getRight() == right && command.getBottom() == bottom) {
                return true;
            }
        }
        return false;
    }

    private static int countCommands(List<DocumentPaintCommand> commands, DocumentPaintCommandType type) {
        int count = 0;
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == type) {
                count++;
            }
        }
        return count;
    }

    private static ElementNode firstSlotElement(DocumentInventorySlotGridControl gridControl) {
        ElementNode table = gridControl.getElement();
        ElementNode body = (ElementNode) table.getChildren().get(1);
        ElementNode row = (ElementNode) body.getChildren().get(0);
        return (ElementNode) row.getChildren().get(0);
    }

    private static ElementNode secondSlotElement(DocumentInventorySlotGridControl gridControl) {
        ElementNode table = gridControl.getElement();
        ElementNode body = (ElementNode) table.getChildren().get(1);
        ElementNode row = (ElementNode) body.getChildren().get(0);
        return (ElementNode) row.getChildren().get(1);
    }

    /**
     * 记录延迟回放与 surface 绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DeferredPostMainPassReplay> deferredReplays = new ArrayList<DeferredPostMainPassReplay>();
        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();
        private final List<String> textCalls = new ArrayList<String>();

        private RecordingUiRenderContext() {
            super(400, 200, 0, 0, 1.0F);
        }

        private RecordingUiRenderContext(int width, int height, int mouseX, int mouseY) {
            super(width, height, mouseX, mouseY, 1.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(left, top, right, bottom, surfaceStyle));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            textCalls.add(text);
        }

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {}

        @Override
        public void drawBorder(int left, int top, int right, int bottom, int color) {}

        @Override
        public int measureTextWidth(String text) {
            return text == null ? 0 : text.length() * 12;
        }

        @Override
        public int getTextLineHeight() {
            return 18;
        }

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

    /**
     * 单次 surface 绘制记录。
     */
    private static final class DrawCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final UiSurfaceStyle surfaceStyle;

        private DrawCall(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.surfaceStyle = surfaceStyle;
        }
    }

    /**
     * 供测试使用的确定性文本测量服务。
     */
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
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
