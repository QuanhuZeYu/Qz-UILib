package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.paint.DocumentPaintCommand;
import club.heiqi.uilib.ui.paint.DocumentPaintCommandType;
import club.heiqi.uilib.ui.paint.DocumentPaintEngine;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.style.UiStyleResolver;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `DocumentInventorySlotGridControl` 的基础行为契约测试。
 */
public class DocumentInventorySlotGridControlTest {

    /**
     * 验证空槽网格不会生成可见物品图片子元素。
     */
    @Test
    public void shouldKeepSlotImageHiddenWhenSlotsAreEmpty() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(400))
                .setHeight(UiStyleLength.px(200));
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
        List<DocumentPaintCommand> commands = buildPaintCommands(root, 400, 200);
        Assert.assertTrue(containsCommand(commands, DocumentPaintCommandType.BACKGROUND, 0xAA171C24));
        Assert.assertEquals(UiDisplay.NONE, UiStyleResolver.compute(slotImageElement(firstSlotElement(gridControl))).getDisplay());
    }

    /**
     * 验证占用槽会挂载宿主图片子元素占位。
     */
    @Test
    public void shouldAttachHostImageElementWhenSlotsAreOccupied() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(400))
                .setHeight(UiStyleLength.px(200));
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 9, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setContentProvider(new DocumentInventorySlotGridControl.SlotContentProvider() {
                    @Override
                    public InventorySlotSnapshot getSlotSnapshot(int localIndex) {
                        return localIndex == 0 ? InventorySlotSnapshot.occupied() : InventorySlotSnapshot.empty();
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());

        ElementNode firstImage = slotImageElement(firstSlotElement(gridControl));
        ElementNode secondImage = slotImageElement(secondSlotElement(gridControl));

        Assert.assertEquals(UiDisplay.NONE, UiStyleResolver.compute(firstImage).getDisplay());
        Assert.assertEquals(UiDisplay.NONE, UiStyleResolver.compute(secondImage).getDisplay());
        Assert.assertEquals("img", firstImage.getTagName());
        Assert.assertEquals("true", firstImage.getAttribute("aria-hidden"));
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
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 9, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .commitLayout();
        root.append(gridControl.getElement());
        Assert.assertEquals(UiDisplay.NONE, UiStyleResolver.compute(slotImageElement(firstSlotElement(gridControl))).getDisplay());
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
                0xFF111111));
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
        ElementNode body = tableBodyElement(table);
        ElementNode firstRow = (ElementNode) body.getChildren().get(0);
        ElementNode firstSlot = (ElementNode) firstRow.getChildren().get(0);
        ElementNode secondSlot = (ElementNode) firstRow.getChildren().get(1);

        Assert.assertEquals("table", table.getTagName());
        Assert.assertEquals(1, table.getChildCount());
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
        Assert.assertEquals("button", firstSlot.getAttribute("role"));
        Assert.assertEquals("0", firstSlot.getAttribute("tabindex"));
        Assert.assertEquals("0", firstSlot.getAttribute("data-slot-index"));
        Assert.assertEquals("true", firstSlot.getAttribute("data-slot-occupied"));
        Assert.assertEquals("槽位 1，已占用", firstSlot.getAttribute("aria-label"));
        Assert.assertEquals("img", slotImageElement(firstSlot).getTagName());
        Assert.assertEquals(Integer.valueOf(0xAA171C24), secondSlot.style().getBackgroundColor());
        Assert.assertEquals(Integer.valueOf(0xFF465468), secondSlot.style().getBorderColor());
        Assert.assertEquals("false", secondSlot.getAttribute("data-slot-occupied"));
        Assert.assertEquals("槽位 2，空", secondSlot.getAttribute("aria-label"));
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

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 240, 140,
                new DeterministicTextMeasureService());
        DocumentLayoutBox tableBox = rootBox.getChildren().get(0);
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
    public void shouldHighlightHoveredSlotAndNotifyTooltipHover() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(140));
        final List<String> tooltipEvents = new ArrayList<String>();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 2, 2)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setSlotTooltipProvider(new DocumentInventorySlotGridControl.SlotTooltipProvider() {
                    @Override
                    public List<String> getSlotTooltip(int localIndex) {
                        return Collections.singletonList("Tooltip " + localIndex);
                    }
                })
                .setSlotHoverHandler(new DocumentInventorySlotGridControl.SlotHoverHandler() {
                    @Override
                    public void onSlotHoverChanged(int localIndex, boolean hovered, List<String> tooltipLines,
                            int documentX, int documentY, long timeNanos) {
                        tooltipEvents.add(localIndex + ":" + hovered + ":"
                                + (tooltipLines.isEmpty() ? "empty" : tooltipLines.get(0)));
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());
        ElementNode firstSlot = firstSlotElement(gridControl);

        firstSlot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(firstSlot, firstSlot, true, 6, 6,
                1L));

        Assert.assertEquals(0, gridControl.getHoveredSlotIndex());
        Assert.assertEquals(Integer.valueOf(0xDD263349), firstSlot.style().getBackgroundColor());
        Assert.assertEquals("true", firstSlot.getAttribute("data-slot-hovered"));
        Assert.assertEquals(Collections.singletonList("0:true:Tooltip 0"), tooltipEvents);

        firstSlot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(firstSlot, firstSlot, false, -1, -1,
                2L));
        Assert.assertEquals(-1, gridControl.getHoveredSlotIndex());
        Assert.assertEquals(Integer.valueOf(0xAA171C24), firstSlot.style().getBackgroundColor());
        Assert.assertEquals("false", firstSlot.getAttribute("data-slot-hovered"));
    }

    /**
     * 验证 hover 中刷新槽位状态会同步更新 tooltip 文本。
     */
    @Test
    public void shouldRefreshVisibleTooltipWhenSlotStateRefreshes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(140));
        final int[] tooltipVersion = new int[] { 1 };
        final List<String> tooltipEvents = new ArrayList<String>();
        DocumentInventorySlotGridControl gridControl = new DocumentInventorySlotGridControl(document, 2, 2)
                .setSlotTooltipProvider(new DocumentInventorySlotGridControl.SlotTooltipProvider() {
                    @Override
                    public List<String> getSlotTooltip(int localIndex) {
                        return Collections.singletonList("Tooltip " + tooltipVersion[0]);
                    }
                })
                .setSlotHoverHandler(new DocumentInventorySlotGridControl.SlotHoverHandler() {
                    @Override
                    public void onSlotHoverChanged(int localIndex, boolean hovered, List<String> tooltipLines,
                            int documentX, int documentY, long timeNanos) {
                        tooltipEvents.add(tooltipLines.isEmpty() ? "empty" : tooltipLines.get(0));
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());
        ElementNode firstSlot = firstSlotElement(gridControl);
        firstSlot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(firstSlot, firstSlot, true, 6, 6,
                1L));
        tooltipVersion[0] = 2;

        gridControl.refreshSlotStates();

        Assert.assertTrue(tooltipEvents.contains("Tooltip 1"));
        Assert.assertEquals("Tooltip 2", tooltipEvents.get(tooltipEvents.size() - 1));
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
     * 验证槽位可通过 Enter/Space 键盘激活为左键点击。
     */
    @Test
    public void shouldActivateSlotWithKeyboardAsLeftClick() {
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

        Assert.assertTrue(firstSlot.isFocusable());
        Assert.assertTrue(firstSlot.getKeyHandler().onKey(new DocumentElementKeyEvent(firstSlot, firstSlot,
                new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                        false, 4L))));
        Assert.assertTrue(clickedSlots.isEmpty());
        Assert.assertTrue(firstSlot.getKeyHandler().onKey(new DocumentElementKeyEvent(firstSlot, firstSlot,
                new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false, false,
                        false, 5L))));

        Assert.assertEquals(Collections.singletonList(Integer.valueOf(0)), clickedSlots);
        Assert.assertEquals(Collections.singletonList(Integer.valueOf(0)), clickedButtons);
        Assert.assertEquals(Integer.valueOf(0xAA171C24), firstSlot.style().getBackgroundColor());
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
        ElementNode body = tableBodyElement(table);
        ElementNode row = (ElementNode) body.getChildren().get(0);
        return (ElementNode) row.getChildren().get(0);
    }

    private static ElementNode secondSlotElement(DocumentInventorySlotGridControl gridControl) {
        ElementNode table = gridControl.getElement();
        ElementNode body = tableBodyElement(table);
        ElementNode row = (ElementNode) body.getChildren().get(0);
        return (ElementNode) row.getChildren().get(1);
    }

    private static ElementNode slotImageElement(ElementNode slotElement) {
        for (DocumentNode child : slotElement.getChildren()) {
            if (child instanceof ElementNode && "true".equals(((ElementNode) child).getAttribute("data-slot-image"))) {
                return (ElementNode) child;
            }
        }
        throw new AssertionError("slot image element not found");
    }

    private static ElementNode tableBodyElement(ElementNode table) {
        for (DocumentNode child : table.getChildren()) {
            if (child instanceof ElementNode && "tbody".equals(((ElementNode) child).getTagName())) {
                return (ElementNode) child;
            }
        }
        throw new AssertionError("table body not found");
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
