package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `FlexLayoutHelper` 边界用例测试。
 *
 * <p>helper 自身是包级私有，本测试通过 {@link DocumentLayoutEngine#layout} 间接驱动 flex 子项布局，
 * 重点覆盖被现有 {@link DocumentLayoutEngineTest} 间接覆盖率较弱的负尺寸、嵌套与 auto cross-size 情形。</p>
 */
public class FlexLayoutHelperBoundaryTest {

    /**
     * 父容器内容宽度被 padding/border 吃掉到负值时，子项主轴尺寸不应为负。
     */
    @Test
    public void shouldNotProduceNegativeMainAxisSizeWhenParentContentBoxCollapses() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode item = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setWidth(UiStyleLength.px(20))
                .setPadding(UiStyleLength.px(40));
        item.style()
                .setHeight(UiStyleLength.px(10));
        root.append(item);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0, new DeterministicMeasure());
        DocumentLayoutBox itemBox = rootBox.getChildren().get(0);

        Assert.assertTrue("flex item width should never be negative", itemBox.getWidth() >= 0);
        Assert.assertTrue("flex item height should never be negative", itemBox.getHeight() >= 0);
    }

    /**
     * 嵌套 flex column → flex row 在父 align-items=stretch 时仍按内容宽度收缩。
     */
    @Test
    public void shouldKeepNestedRowAutoCrossSizeWhenColumnAlignItemsStretch() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode columnItem = document.div();
        ElementNode rowItem = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.STRETCH)
                .setWidth(UiStyleLength.px(120));
        columnItem.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW);
        rowItem.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(30));
        columnItem.append(rowItem);
        root.append(columnItem);

        DocumentLayoutBox columnBox = DocumentLayoutEngine.layout(root, 200, 0, new DeterministicMeasure())
                .getChildren().get(0);
        DocumentLayoutBox rowBox = columnBox.getChildren().get(0);

        Assert.assertEquals(120, columnBox.getWidth());
        Assert.assertEquals(30, columnBox.getHeight());
        Assert.assertEquals(40, rowBox.getWidth());
        Assert.assertEquals(30, rowBox.getHeight());
    }

    /**
     * 验证 row flex item 默认 min-width:auto 使用 min-content，而不是整段 max-content 宽度。
     */
    @Test
    public void shouldUseMinContentWidthForRowFlexItemAutoMinWidth() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode item = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setWidth(UiStyleLength.px(24));
        item.appendText("abcd efghij");
        root.append(item);

        DocumentLayoutBox itemBox = DocumentLayoutEngine.layout(root, 80, 0, new DeterministicMeasure())
                .getChildren().get(0);

        Assert.assertEquals(48, itemBox.getContentWidth());
    }

    /**
     * nowrap 文本没有软换行机会，auto 最小宽度应保持整段文本宽度。
     */
    @Test
    public void shouldKeepNoWrapRowFlexItemAtMaxContentAutoMinWidth() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode item = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setWidth(UiStyleLength.px(20));
        item.style().setWhiteSpace(UiWhiteSpace.NOWRAP);
        item.appendText("abcd efghij");
        root.append(item);

        DocumentLayoutBox itemBox = DocumentLayoutEngine.layout(root, 80, 0, new DeterministicMeasure())
                .getChildren().get(0);

        Assert.assertEquals(88, itemBox.getContentWidth());
    }

    /**
     * 验证 flex-basis 在 border-box 下独立于 width:auto 扣除 padding/border。
     */
    @Test
    public void shouldApplyBorderBoxSizingToFlexBasisWhenWidthIsAuto() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode item = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setWidth(UiStyleLength.px(120));
        item.style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setFlexBasis(UiStyleLength.px(60))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1));
        root.append(item);

        DocumentLayoutBox itemBox = DocumentLayoutEngine.layout(root, 160, 0, new DeterministicMeasure())
                .getChildren().get(0);

        Assert.assertEquals(60, itemBox.getWidth());
        Assert.assertEquals(42, itemBox.getContentWidth());
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
