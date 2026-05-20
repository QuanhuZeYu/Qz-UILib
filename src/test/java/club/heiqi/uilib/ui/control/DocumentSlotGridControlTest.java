package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.paint.DocumentPaintCommand;
import club.heiqi.uilib.ui.paint.DocumentPaintCommandType;
import club.heiqi.uilib.ui.paint.DocumentPaintEngine;
import club.heiqi.uilib.ui.slot.SlotContentSnapshot;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.style.UiStyleResolver;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `DocumentSlotGridControl` 的基础行为契约测试。
 */
public class DocumentSlotGridControlTest {

    /**
     * 验证槽位网格仍以真实 table/tbody/tr/td 组织。
     */
    @Test
    public void shouldBuildSlotGridAsRealTableDom() {
        UiDocument document = UiDocument.create();
        DocumentSlotGridControl gridControl = new DocumentSlotGridControl(document, 10, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setContentProvider(new DocumentSlotGridControl.SlotContentProvider() {
                    @Override
                    public SlotContentSnapshot getSlotContent(int localIndex) {
                        return localIndex == 0 ? SlotContentSnapshot.occupied("item", null, "已占用")
                                : SlotContentSnapshot.empty();
                    }
                })
                .commitLayout();

        ElementNode table = gridControl.getElement();
        ElementNode body = tableBodyElement(table);
        ElementNode firstRow = (ElementNode) body.getChildren().get(0);
        ElementNode firstSlot = (ElementNode) firstRow.getChildren().get(0);

        Assert.assertEquals("table", table.getTagName());
        Assert.assertEquals("tbody", body.getTagName());
        Assert.assertEquals("tr", firstRow.getTagName());
        Assert.assertEquals("td", firstSlot.getTagName());
        Assert.assertEquals(UiDisplay.TABLE, UiStyleResolver.compute(table).getDisplay());
        Assert.assertEquals(UiDisplay.TABLE_CELL, UiStyleResolver.compute(firstSlot).getDisplay());
        Assert.assertEquals("button", firstSlot.getAttribute("role"));
        Assert.assertEquals("0", firstSlot.getAttribute("data-slot-index"));
        Assert.assertEquals("true", firstSlot.getAttribute("data-slot-occupied"));
    }

    /**
     * 验证默认布局尺寸仍按期望列数计算。
     */
    @Test
    public void shouldComputePreferredLayoutSize() {
        UiDocument document = UiDocument.create();
        DocumentSlotGridControl gridControl = new DocumentSlotGridControl(document, 9, 9)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .commitLayout();

        Assert.assertEquals(320, (int) gridControl.getElement().style().getWidth().getValue());
        Assert.assertEquals(32, (int) gridControl.getElement().style().getHeight().getValue());
    }

    /**
     * 验证 hover 会同步高亮和 tooltip 通知。
     */
    @Test
    public void shouldNotifyTooltipHoverWithLocalIndex() {
        UiDocument document = UiDocument.create();
        final List<String> tooltipEvents = new ArrayList<String>();
        DocumentSlotGridControl gridControl = new DocumentSlotGridControl(document, 2, 2)
                .setContentProvider(new DocumentSlotGridControl.SlotContentProvider() {
                    @Override
                    public SlotContentSnapshot getSlotContent(int localIndex) {
                        return SlotContentSnapshot.builder()
                                .setOccupied(true)
                                .setDisplayName("物品 " + localIndex)
                                .build();
                    }
                })
                .setSlotTooltipProvider(new DocumentSlotGridControl.SlotTooltipProvider() {
                    @Override
                    public List<String> getSlotTooltip(int localIndex) {
                        return Collections.singletonList("Tooltip " + localIndex);
                    }
                })
                .setSlotHoverHandler(new DocumentSlotGridControl.SlotHoverHandler() {
                    @Override
                    public void onSlotHoverChanged(int localIndex, boolean hovered, List<String> tooltipLines,
                            int documentX, int documentY, long timeNanos) {
                        tooltipEvents.add(localIndex + ":" + hovered + ":"
                                + (tooltipLines.isEmpty() ? "empty" : tooltipLines.get(0)));
                    }
                })
                .commitLayout();

        ElementNode firstSlot = firstSlotElement(gridControl);
        firstSlot.getHoverHandler().onHoverChanged(new DocumentElementHoverEvent(firstSlot, firstSlot, true, 6, 6,
                1L));

        Assert.assertEquals(0, gridControl.getHoveredSlotIndex());
        Assert.assertEquals(Collections.singletonList("0:true:Tooltip 0"), tooltipEvents);
    }

    /**
     * 验证槽位表面仍通过标准 DOM 背景命令绘制。
     */
    @Test
    public void shouldRenderSemiTransparentDomSurfaces() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(100));
        DocumentSlotGridControl gridControl = new DocumentSlotGridControl(document, 2, 2)
                .setSlotGap(4)
                .setPreferredSlotSize(32)
                .setContentProvider(new DocumentSlotGridControl.SlotContentProvider() {
                    @Override
                    public SlotContentSnapshot getSlotContent(int localIndex) {
                        return localIndex == 0 ? SlotContentSnapshot.empty()
                                : SlotContentSnapshot.occupied("item", null, "已占用");
                    }
                })
                .commitLayout();
        root.append(gridControl.getElement());

        List<DocumentPaintCommand> commands = buildPaintCommands(root, 200, 100);

        Assert.assertTrue(containsCommand(commands, DocumentPaintCommandType.BACKGROUND, 0xAA171C24));
        Assert.assertTrue(containsCommand(commands, DocumentPaintCommandType.BACKGROUND, 0xCC202A38));
    }

    /**
     * 验证网格布局几何与原先背包网格保持一致。
     */
    @Test
    public void shouldLayoutSlotsAtExpectedGeometryPositions() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(140));
        DocumentSlotGridControl gridControl = new DocumentSlotGridControl(document, 10, 9)
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
        Assert.assertEquals(36, secondSlotBox.getLeft());
        Assert.assertEquals(36, secondRowBox.getTop());
        Assert.assertEquals(36, tenthSlotBox.getTop());
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

    private static ElementNode firstSlotElement(DocumentSlotGridControl gridControl) {
        ElementNode table = gridControl.getElement();
        ElementNode body = tableBodyElement(table);
        ElementNode row = (ElementNode) body.getChildren().get(0);
        return (ElementNode) row.getChildren().get(0);
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
            if (text == null || text.isEmpty()) {
                return "";
            }
            int maxChars = Math.max(0, targetWidth / 6);
            return text.length() <= maxChars ? text : text.substring(0, maxChars);
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
