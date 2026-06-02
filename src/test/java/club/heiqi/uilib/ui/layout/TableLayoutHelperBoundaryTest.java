package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiBorderCollapse;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `TableLayoutHelper` 边界用例测试。
 *
 * <p>helper 自身是包级私有，本测试通过 {@link DocumentLayoutEngine#layout} 间接驱动 table 子项布局，
 * 重点覆盖空表格、空单元格、行嵌套等被现有 {@link DocumentLayoutEngineTest} 间接覆盖率较弱的场景。</p>
 */
public class TableLayoutHelperBoundaryTest {

    /**
     * 没有 row / cell 的空 table 不应抛异常，宽度仍按声明值落位。
     */
    @Test
    public void shouldLayoutEmptyTableWithoutNegativeOrNaNDimensions() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode table = document.table();

        root.style().setWidth(UiStyleLength.px(160));
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setWidth(UiStyleLength.px(120))
                .setBorderCollapse(UiBorderCollapse.SEPARATE);
        root.append(table);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0, new DeterministicMeasure());
        DocumentLayoutBox tableBox = rootBox.getChildren().get(0);

        Assert.assertEquals(120, tableBox.getWidth());
        Assert.assertTrue("empty table height should never be negative", tableBox.getHeight() >= 0);
        Assert.assertTrue("empty table should expose no row children", tableBox.getChildren().isEmpty());
    }

    /**
     * 单 row 内的空 cell 在 border-collapse 下只应消除内部 gap，不影响 row 高度参与布局。
     */
    @Test
    public void shouldLayoutTableRowWithEmptyCellsUnderBorderCollapse() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode table = document.table();
        ElementNode row = document.div();
        ElementNode firstCell = document.div();
        ElementNode secondCell = document.div();

        root.style().setWidth(UiStyleLength.px(200));
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setWidth(UiStyleLength.px(200))
                .setBorderCollapse(UiBorderCollapse.COLLAPSE);
        row.style().setDisplay(UiDisplay.TABLE_ROW);
        firstCell.style()
                .setDisplay(UiDisplay.TABLE_CELL)
                .setHeight(UiStyleLength.px(20));
        secondCell.style()
                .setDisplay(UiDisplay.TABLE_CELL)
                .setHeight(UiStyleLength.px(20));
        row.append(firstCell).append(secondCell);
        table.append(row);
        root.append(table);

        DocumentLayoutBox tableBox = DocumentLayoutEngine.layout(root, 240, 0, new DeterministicMeasure())
                .getChildren().get(0);
        DocumentLayoutBox rowBox = tableBox.getChildren().get(0);
        DocumentLayoutBox firstBox = rowBox.getChildren().get(0);
        DocumentLayoutBox secondBox = rowBox.getChildren().get(1);

        Assert.assertEquals("collapsed cells should sit flush against each other",
                firstBox.getLeft() + firstBox.getWidth(), secondBox.getLeft());
        Assert.assertEquals(20, firstBox.getHeight());
        Assert.assertEquals(20, secondBox.getHeight());
    }

    /**
     * auto 列宽应先考虑单元格内容固有宽度，再分配剩余空间。
     */
    @Test
    public void shouldUseCellContentWhenResolvingAutoColumnWidths() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode table = document.table();
        ElementNode row = document.div();
        ElementNode shortCell = document.div();
        ElementNode longCell = document.div();

        root.style().setWidth(UiStyleLength.px(160));
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.TABLE_ROW);
        shortCell.style().setDisplay(UiDisplay.TABLE_CELL);
        longCell.style().setDisplay(UiDisplay.TABLE_CELL);
        shortCell.appendText("A");
        longCell.appendText("ABCDEFGHIJ");
        row.append(shortCell).append(longCell);
        table.append(row);
        root.append(table);

        DocumentLayoutBox rowBox = DocumentLayoutEngine.layout(root, 200, 0, new DeterministicMeasure())
                .getChildren().get(0).getChildren().get(0);
        DocumentLayoutBox shortBox = rowBox.getChildren().get(0);
        DocumentLayoutBox longBox = rowBox.getChildren().get(1);

        Assert.assertTrue("long content column should be wider than short content column",
                longBox.getWidth() > shortBox.getWidth());
    }

    private static final class DeterministicMeasure implements TextMeasureService {

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
