package club.heiqi.uilib.ui.dom.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
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
 * `DocumentTableControl` 的 HTML-like 表格契约测试。
 */
public class DocumentTableControlTest {

    /**
     * 验证表格控件生成真实 table/thead/tbody/tr/th/td DOM 结构。
     */
    @Test
    public void shouldBuildSemanticTableDomTree() {
        UiDocument document = UiDocument.create();
        DocumentTableControl tableControl = new DocumentTableControl(document)
                .setHeader("名称", "数量")
                .addRow("铁锭", "64")
                .addRow("铜锭", "32");

        ElementNode table = tableControl.getElement();
        ElementNode thead = tableControl.getHeaderSectionElement();
        ElementNode tbody = tableControl.getBodySectionElement();
        ElementNode headerRow = (ElementNode) thead.getChildren().get(0);
        ElementNode firstHeaderCell = (ElementNode) headerRow.getChildren().get(0);
        ElementNode firstBodyRow = (ElementNode) tbody.getChildren().get(0);
        ElementNode firstBodyCell = (ElementNode) firstBodyRow.getChildren().get(0);

        Assert.assertEquals("table", table.getTagName());
        Assert.assertEquals("thead", thead.getTagName());
        Assert.assertEquals("tbody", tbody.getTagName());
        Assert.assertEquals("tr", headerRow.getTagName());
        Assert.assertEquals("th", firstHeaderCell.getTagName());
        Assert.assertEquals("td", firstBodyCell.getTagName());
        Assert.assertEquals(UiDisplay.TABLE, UiStyleResolver.compute(table).getDisplay());
        Assert.assertEquals(UiDisplay.TABLE_CELL, UiStyleResolver.compute(firstBodyCell).getDisplay());
        Assert.assertEquals(2, tbody.getChildCount());
    }

    /**
     * 验证表格控件通过标准背景/边框/文本 paint command 绘制，不引入 CUSTOM。
     */
    @Test
    public void shouldPaintTableCellsWithStandardCommands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style().setWidth(UiStyleLength.px(260));
        DocumentTableControl tableControl = new DocumentTableControl(document)
                .setCellGap(4, 6)
                .setColors(0xFF102030, 0xFF203040, 0xFF405060, 0xFFE0E8F0, 0xFFFFFFFF)
                .setHeader("名称", "数量")
                .addRow("铁锭", "64")
                .setColumnWidth(0, UiStyleLength.px(80));
        tableControl.getElement().style().setWidth(UiStyleLength.px(200));
        root.append(tableControl.getElement());

        List<DocumentPaintCommand> commands = buildPaintCommands(root, 260, 0);

        Assert.assertTrue(containsCommand(commands, DocumentPaintCommandType.BACKGROUND, 0xFF102030));
        Assert.assertTrue(containsCommand(commands, DocumentPaintCommandType.BACKGROUND, 0xFF203040));
        Assert.assertTrue(containsCommand(commands, DocumentPaintCommandType.BORDER, 0xFF405060));
        Assert.assertTrue(countCommands(commands, DocumentPaintCommandType.TEXT) >= 4);
        Assert.assertEquals(0, countCommands(commands, DocumentPaintCommandType.CUSTOM));
    }

    /**
     * 验证无表头表格不会保留空 thead 节点。
     */
    @Test
    public void shouldOmitHeaderSectionWhenNoHeaderIsSet() {
        UiDocument document = UiDocument.create();
        DocumentTableControl tableControl = new DocumentTableControl(document)
                .addRow("铁锭", "64");
        ElementNode table = tableControl.getElement();

        Assert.assertEquals(1, table.getChildCount());
        Assert.assertEquals("tbody", ((ElementNode) table.getChildren().get(0)).getTagName());
        Assert.assertSame(tableControl.getBodySectionElement(), table.getChildren().get(0));

        tableControl.setHeader("名称", "数量");
        Assert.assertEquals(2, table.getChildCount());
        Assert.assertSame(tableControl.getHeaderSectionElement(), table.getChildren().get(0));
        Assert.assertSame(tableControl.getBodySectionElement(), table.getChildren().get(1));

        tableControl.setHeader();
        Assert.assertEquals(1, table.getChildCount());
        Assert.assertSame(tableControl.getBodySectionElement(), table.getChildren().get(0));
    }

    /**
     * 验证控件列宽设置会参与 table 布局，并保留自动列分配。
     */
    @Test
    public void shouldApplyColumnWidthThroughTableLayout() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style().setWidth(UiStyleLength.px(260));
        DocumentTableControl tableControl = new DocumentTableControl(document)
                .setCellGap(0, 6)
                .setCellPadding(3)
                .setHeader("名称", "数量")
                .addRow("铁锭", "64")
                .setColumnWidth(0, UiStyleLength.px(80));
        tableControl.getElement().style().setWidth(UiStyleLength.px(200));
        root.append(tableControl.getElement());

        DocumentLayoutBox tableBox = DocumentLayoutEngine.layout(root, 260, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);
        DocumentLayoutBox headerRow = tableBox.getChildren().get(0).getChildren().get(0);
        DocumentLayoutBox firstColumnCell = headerRow.getChildren().get(0);
        DocumentLayoutBox secondColumnCell = headerRow.getChildren().get(1);

        Assert.assertEquals(88, firstColumnCell.getWidth());
        Assert.assertEquals(106, secondColumnCell.getWidth());
        Assert.assertEquals(94, secondColumnCell.getLeft());
        Assert.assertEquals(200, headerRow.getWidth());
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

    private static int countCommands(List<DocumentPaintCommand> commands, DocumentPaintCommandType type) {
        int count = 0;
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == type) {
                count++;
            }
        }
        return count;
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
            return text == null ? 0 : text.length() * 4;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<String>();
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }
}
