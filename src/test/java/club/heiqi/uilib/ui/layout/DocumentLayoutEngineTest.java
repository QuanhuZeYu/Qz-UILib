package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.style.UiVerticalAlign;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `DocumentLayoutEngine` 的 HTML-like 盒模型布局契约测试。
 */
public class DocumentLayoutEngineTest {

    /**
     * 验证 block flow 会按 margin、border、padding 与 auto 高度生成布局盒。
     */
    @Test
    public void shouldLayoutBlockFlowWithBoxModel() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();

        root.style()
                .setWidth(UiStyleLength.px(300))
                .setPadding(UiStyleLength.px(10))
                .setBorderWidth(UiStyleLength.px(2));
        first.style()
                .setHeight(UiStyleLength.px(20))
                .setMargin(UiStyleLength.px(5))
                .setPadding(UiStyleLength.px(3))
                .setBorderWidth(UiStyleLength.px(1));
        second.style().setHeight(UiStyleLength.px(10));
        root.append(first).append(second);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 500, 0);
        DocumentLayoutBox firstBox = rootBox.getChildren().get(0);
        DocumentLayoutBox secondBox = rootBox.getChildren().get(1);

        Assert.assertEquals(324, rootBox.getWidth());
        Assert.assertEquals(72, rootBox.getHeight());
        Assert.assertEquals(12, rootBox.getContentLeft());
        Assert.assertEquals(12, rootBox.getContentTop());
        Assert.assertEquals(300, rootBox.getContentWidth());
        Assert.assertEquals(48, rootBox.getContentHeight());

        Assert.assertEquals(17, firstBox.getLeft());
        Assert.assertEquals(17, firstBox.getTop());
        Assert.assertEquals(290, firstBox.getWidth());
        Assert.assertEquals(28, firstBox.getHeight());
        Assert.assertEquals(282, firstBox.getContentWidth());
        Assert.assertEquals(20, firstBox.getContentHeight());
        Assert.assertEquals(50, firstBox.getMarginBoxBottom());

        Assert.assertEquals(12, secondBox.getLeft());
        Assert.assertEquals(50, secondBox.getTop());
        Assert.assertEquals(300, secondBox.getWidth());
        Assert.assertEquals(10, secondBox.getHeight());
    }

    /**
     * 验证百分比宽度会相对父 content box 解析。
     */
    @Test
    public void shouldResolvePercentWidthAgainstContainingContentBox() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(400));
        child.style()
                .setWidth(UiStyleLength.percent(0.5F))
                .setHeight(UiStyleLength.px(16));
        root.append(child);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 640, 0);
        DocumentLayoutBox childBox = rootBox.getChildren().get(0);

        Assert.assertEquals(400, rootBox.getContentWidth());
        Assert.assertEquals(200, childBox.getWidth());
        Assert.assertEquals(200, childBox.getContentWidth());
    }

    /**
     * 验证固定宽父容器中的 block 子项在 auto 宽下不会因自身 padding/border 撑出父内容盒。
     */
    @Test
    public void shouldKeepAutoWidthBlockChildInsideParentContentBox() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(320));
        panel.style()
                .setWidth(UiStyleLength.px(248))
                .setPadding(UiStyleLength.px(12))
                .setBorderWidth(UiStyleLength.px(1));
        child.style()
                .setDisplay(UiDisplay.BLOCK)
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1));
        panel.append(child);
        root.append(panel);

        DocumentLayoutBox panelBox = DocumentLayoutEngine.layout(root, 320, 0).getChildren().get(0);
        DocumentLayoutBox childBox = panelBox.getChildren().get(0);

        Assert.assertEquals(248, panelBox.getContentWidth());
        Assert.assertEquals(248, childBox.getWidth());
        Assert.assertTrue(childBox.getRight() <= panelBox.getContentLeft() + panelBox.getContentWidth());
    }

    /**
     * 验证 display none 元素不会进入 layout box tree。
     */
    @Test
    public void shouldSkipDisplayNoneElementBoxes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode hidden = document.div();
        ElementNode visible = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        hidden.style()
                .setDisplay(UiDisplay.NONE)
                .setHeight(UiStyleLength.px(80));
        visible.style().setHeight(UiStyleLength.px(24));
        root.append(hidden).append(visible);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0);

        Assert.assertEquals(1, rootBox.getChildren().size());
        assertElementUid(visible, rootBox.getChildren().get(0).getElement());
        Assert.assertEquals(24, rootBox.getContentHeight());
        Assert.assertEquals(24, rootBox.getHeight());
    }

    /**
     * 验证 table 布局按列宽、行高和行列间距生成 table row/cell 盒。
     */
    @Test
    public void shouldLayoutTableRowsAndCells() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode table = document.table();
        ElementNode body = document.tbody();
        ElementNode firstRow = document.tr();
        ElementNode secondRow = document.tr();
        ElementNode firstName = document.td();
        ElementNode firstValue = document.td();
        ElementNode secondName = document.td();
        ElementNode secondValue = document.td();

        root.style().setWidth(UiStyleLength.px(260));
        table.style()
                .setWidth(UiStyleLength.px(200))
                .setRowGap(UiStyleLength.px(4))
                .setColumnGap(UiStyleLength.px(6));
        firstName.style()
                .setWidth(UiStyleLength.px(70))
                .setPadding(UiStyleLength.px(3))
                .setBorderWidth(UiStyleLength.px(1));
        firstValue.style()
                .setPadding(UiStyleLength.px(3))
                .setBorderWidth(UiStyleLength.px(1));
        secondName.style()
                .setPadding(UiStyleLength.px(3))
                .setBorderWidth(UiStyleLength.px(1));
        secondValue.style()
                .setPadding(UiStyleLength.px(3))
                .setBorderWidth(UiStyleLength.px(1));
        firstName.appendText("A");
        firstValue.appendText("B");
        secondName.appendText("C");
        secondValue.appendText("D");
        firstRow.append(firstName).append(firstValue);
        secondRow.append(secondName).append(secondValue);
        body.append(firstRow).append(secondRow);
        table.append(body);
        root.append(table);

        DocumentLayoutBox tableBox = DocumentLayoutEngine.layout(root, 260, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);
        DocumentLayoutBox bodyBox = tableBox.getChildren().get(0);
        DocumentLayoutBox firstRowBox = bodyBox.getChildren().get(0);
        DocumentLayoutBox secondRowBox = bodyBox.getChildren().get(1);
        DocumentLayoutBox firstNameBox = firstRowBox.getChildren().get(0);
        DocumentLayoutBox firstValueBox = firstRowBox.getChildren().get(1);

        Assert.assertEquals(200, tableBox.getContentWidth());
        Assert.assertEquals(200, bodyBox.getWidth());
        Assert.assertEquals(200, firstRowBox.getWidth());
        Assert.assertEquals(78, firstNameBox.getWidth());
        Assert.assertEquals(116, firstValueBox.getWidth());
        Assert.assertEquals(84, firstValueBox.getLeft());
        Assert.assertEquals(firstNameBox.getHeight(), firstRowBox.getHeight());
        Assert.assertEquals(firstRowBox.getBottom() + 4, secondRowBox.getTop());
        Assert.assertEquals(secondRowBox.getBottom(), tableBox.getContentHeight());
    }

    /**
     * 验证 table 布局会让同一行的单元格拉伸到统一行高。
     */
    @Test
    public void shouldStretchTableCellsToResolvedRowHeight() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode table = document.table();
        ElementNode row = document.tr();
        ElementNode shortCell = document.td();
        ElementNode tallCell = document.td();

        root.style().setWidth(UiStyleLength.px(220));
        table.style().setWidth(UiStyleLength.px(160));
        row.style().setHeight(UiStyleLength.px(40));
        shortCell.style()
                .setWidth(UiStyleLength.px(60))
                .setPadding(UiStyleLength.px(2))
                .setBorderWidth(UiStyleLength.px(1));
        tallCell.style()
                .setPadding(UiStyleLength.px(2))
                .setBorderWidth(UiStyleLength.px(1));
        shortCell.appendText("A");
        tallCell.appendText("B");
        row.append(shortCell).append(tallCell);
        table.append(row);
        root.append(table);

        DocumentLayoutBox rowBox = DocumentLayoutEngine.layout(root, 220, 0,
                new DeterministicTextMeasureService()).getChildren().get(0).getChildren().get(0);
        DocumentLayoutBox shortCellBox = rowBox.getChildren().get(0);
        DocumentLayoutBox tallCellBox = rowBox.getChildren().get(1);

        Assert.assertEquals(40, rowBox.getHeight());
        Assert.assertEquals(40, shortCellBox.getHeight());
        Assert.assertEquals(40, tallCellBox.getHeight());
        Assert.assertEquals(34, shortCellBox.getContentHeight());
    }

    /**
     * 验证四边 margin 会影响子盒在父 content box 内的流式位置。
     */
    @Test
    public void shouldApplyIndependentMarginsInBlockFlow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(200));
        child.style()
                .setHeight(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(3), UiStyleLength.px(5), UiStyleLength.px(7),
                        UiStyleLength.px(11)));
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 240, 0).getChildren().get(0);

        Assert.assertEquals(11, childBox.getLeft());
        Assert.assertEquals(3, childBox.getTop());
        Assert.assertEquals(184, childBox.getWidth());
        Assert.assertEquals(20, childBox.getMarginBoxBottom());
    }

    /**
     * 验证 relative 定位只记录视觉偏移，不改变普通流排布。
     */
    @Test
    public void shouldKeepRelativePositionedElementInNormalFlow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode relative = document.div();
        ElementNode following = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        relative.style()
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(-6))
                .setLeft(UiStyleLength.px(9));
        following.style().setHeight(UiStyleLength.px(12));
        root.append(relative).append(following);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 160, 0);
        DocumentLayoutBox relativeBox = rootBox.getChildren().get(0);
        DocumentLayoutBox followingBox = rootBox.getChildren().get(1);

        Assert.assertEquals(0, relativeBox.getLeft());
        Assert.assertEquals(0, relativeBox.getTop());
        Assert.assertEquals(9, relativeBox.getPositionOffsetX());
        Assert.assertEquals(-6, relativeBox.getPositionOffsetY());
        Assert.assertEquals(20, followingBox.getTop());
        Assert.assertEquals(32, rootBox.getContentHeight());
    }

    /**
     * 验证 absolute 定位元素相对根 content box 定位，并脱离普通流。
     */
    @Test
    public void shouldLayoutAbsolutePositionedElementOutOfNormalFlow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode absolute = document.div();
        ElementNode following = document.div();

        root.style()
                .setWidth(UiStyleLength.px(120))
                .setPadding(UiStyleLength.px(10))
                .setBorderWidth(UiStyleLength.px(2));
        absolute.style()
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(12))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(7))
                .setLeft(UiStyleLength.px(9));
        following.style().setHeight(UiStyleLength.px(16));
        root.append(absolute).append(following);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 180, 0);
        DocumentLayoutBox absoluteBox = rootBox.getChildren().get(0);
        DocumentLayoutBox followingBox = rootBox.getChildren().get(1);

        Assert.assertEquals(21, absoluteBox.getLeft());
        Assert.assertEquals(19, absoluteBox.getTop());
        Assert.assertEquals(30, absoluteBox.getWidth());
        Assert.assertEquals(12, absoluteBox.getHeight());
        Assert.assertEquals(12, followingBox.getLeft());
        Assert.assertEquals(12, followingBox.getTop());
        Assert.assertEquals(40, rootBox.getHeight());
    }

    /**
     * 验证 absolute 定位元素可通过 right/bottom 从根 content box 反向定位。
     */
    @Test
    public void shouldLayoutAbsolutePositionedElementFromRightAndBottomInsets() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode spacer = document.div();
        ElementNode absolute = document.div();

        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(60))
                .setPadding(UiStyleLength.px(4));
        spacer.style().setHeight(UiStyleLength.px(20));
        absolute.style()
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.ABSOLUTE)
                .setRight(UiStyleLength.px(8))
                .setBottom(UiStyleLength.px(6));
        root.append(spacer).append(absolute);

        DocumentLayoutBox absoluteBox = DocumentLayoutEngine.layout(root, 140, 0).getChildren().get(1);

        Assert.assertEquals(66, absoluteBox.getLeft());
        Assert.assertEquals(48, absoluteBox.getTop());
    }

    /**
     * 验证 absolute 定位元素在两侧 inset 同时存在且尺寸为 auto 时会 stretch 到 containing block。
     */
    @Test
    public void shouldStretchAbsolutePositionedElementBetweenInsets() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode absolute = document.div();

        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(100))
                .setPadding(UiStyleLength.px(10))
                .setBorderWidth(UiStyleLength.px(2));
        absolute.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(15))
                .setRight(UiStyleLength.px(25))
                .setTop(UiStyleLength.px(7))
                .setBottom(UiStyleLength.px(13))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(2), UiStyleLength.px(5), UiStyleLength.px(6),
                        UiStyleLength.px(3)))
                .setPadding(UiStyleLength.px(4))
                .setBorderWidth(UiStyleLength.px(1));
        root.append(absolute);

        DocumentLayoutBox absoluteBox = DocumentLayoutEngine.layout(root, 260, 0).getChildren().get(0);

        Assert.assertEquals(30, absoluteBox.getLeft());
        Assert.assertEquals(21, absoluteBox.getTop());
        Assert.assertEquals(152, absoluteBox.getWidth());
        Assert.assertEquals(72, absoluteBox.getHeight());
        Assert.assertEquals(142, absoluteBox.getContentWidth());
        Assert.assertEquals(62, absoluteBox.getContentHeight());
    }

    /**
     * 验证 fixed 定位元素相对 HTML-like 视口定位，并脱离普通流。
     */
    @Test
    public void shouldLayoutFixedPositionedElementAgainstViewport() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode fixed = document.div();
        ElementNode following = document.div();

        root.style()
                .setWidth(UiStyleLength.px(120))
                .setPadding(UiStyleLength.px(10))
                .setBorderWidth(UiStyleLength.px(2));
        fixed.style()
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(12))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(7))
                .setRight(UiStyleLength.px(9));
        following.style().setHeight(UiStyleLength.px(16));
        root.append(fixed).append(following);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 100);
        DocumentLayoutBox fixedBox = rootBox.getChildren().get(0);
        DocumentLayoutBox followingBox = rootBox.getChildren().get(1);

        Assert.assertEquals(161, fixedBox.getLeft());
        Assert.assertEquals(7, fixedBox.getTop());
        Assert.assertEquals(30, fixedBox.getWidth());
        Assert.assertEquals(12, fixedBox.getHeight());
        Assert.assertEquals(12, followingBox.getLeft());
        Assert.assertEquals(12, followingBox.getTop());
        Assert.assertEquals(40, rootBox.getHeight());
    }

    /**
     * 验证 absolute 定位元素会相对最近的 positioned ancestor，而不是直接静态父元素。
     */
    @Test
    public void shouldLayoutAbsolutePositionedElementAgainstNearestPositionedAncestor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode positioned = document.div();
        ElementNode staticParent = document.div();
        ElementNode absolute = document.div();

        root.style().setWidth(UiStyleLength.px(180));
        positioned.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(60))
                .setPosition(UiPosition.RELATIVE)
                .setBorderWidth(UiStyleLength.px(2))
                .setPadding(UiStyleLength.px(10));
        staticParent.style()
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleLength.px(3));
        absolute.style()
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(6))
                .setLeft(UiStyleLength.px(8));
        staticParent.append(absolute);
        positioned.append(staticParent);
        root.append(positioned);

        DocumentLayoutBox absoluteBox = DocumentLayoutEngine.layout(root, 220, 0)
                .getChildren().get(0)
                .getChildren().get(0)
                .getChildren().get(0);

        Assert.assertEquals(20, absoluteBox.getLeft());
        Assert.assertEquals(18, absoluteBox.getTop());
    }

    /**
     * 验证嵌套 positioned ancestor 会覆盖更外层的 containing block。
     */
    @Test
    public void shouldLayoutAbsolutePositionedElementAgainstNestedPositionedAncestor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode outer = document.div();
        ElementNode inner = document.div();
        ElementNode staticParent = document.div();
        ElementNode absolute = document.div();

        root.style().setWidth(UiStyleLength.px(200));
        outer.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(100))
                .setPosition(UiPosition.RELATIVE)
                .setBorderWidth(UiStyleLength.px(1))
                .setPadding(UiStyleLength.px(10));
        inner.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(40))
                .setPosition(UiPosition.RELATIVE)
                .setBorderWidth(UiStyleLength.px(2))
                .setPadding(UiStyleLength.px(5));
        staticParent.style()
                .setHeight(UiStyleLength.px(10))
                .setPadding(UiStyleLength.px(1));
        absolute.style()
                .setWidth(UiStyleLength.px(8))
                .setHeight(UiStyleLength.px(6))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(4))
                .setLeft(UiStyleLength.px(6));
        staticParent.append(absolute);
        inner.append(staticParent);
        outer.append(inner);
        root.append(outer);

        DocumentLayoutBox absoluteBox = DocumentLayoutEngine.layout(root, 240, 0)
                .getChildren().get(0)
                .getChildren().get(0)
                .getChildren().get(0)
                .getChildren().get(0);

        Assert.assertEquals(24, absoluteBox.getLeft());
        Assert.assertEquals(22, absoluteBox.getTop());
    }

    /**
     * 验证 right/bottom 会按最近 positioned ancestor 的 content box 反向定位。
     */
    @Test
    public void shouldLayoutAbsolutePositionedElementFromNearestPositionedAncestorRightAndBottomInsets() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode positioned = document.div();
        ElementNode staticParent = document.div();
        ElementNode absolute = document.div();

        root.style().setWidth(UiStyleLength.px(180));
        positioned.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(60))
                .setPosition(UiPosition.RELATIVE)
                .setBorderWidth(UiStyleLength.px(2))
                .setPadding(UiStyleLength.px(10));
        staticParent.style()
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleLength.px(3));
        absolute.style()
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8))
                .setPosition(UiPosition.ABSOLUTE)
                .setRight(UiStyleLength.px(7))
                .setBottom(UiStyleLength.px(5));
        staticParent.append(absolute);
        positioned.append(staticParent);
        root.append(positioned);

        DocumentLayoutBox absoluteBox = DocumentLayoutEngine.layout(root, 220, 0)
                .getChildren().get(0)
                .getChildren().get(0)
                .getChildren().get(0);

        Assert.assertEquals(93, absoluteBox.getLeft());
        Assert.assertEquals(59, absoluteBox.getTop());
    }

    /**
     * 验证没有 positioned ancestor 时 absolute 定位回退到根 content box。
     */
    @Test
    public void shouldLayoutAbsolutePositionedElementAgainstRootWhenNoPositionedAncestorExists() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode staticParent = document.div();
        ElementNode absolute = document.div();

        root.style()
                .setWidth(UiStyleLength.px(120))
                .setPadding(UiStyleLength.px(5));
        staticParent.style()
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleLength.px(30));
        absolute.style()
                .setWidth(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(8))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(7))
                .setLeft(UiStyleLength.px(9));
        staticParent.append(absolute);
        root.append(staticParent);

        DocumentLayoutBox absoluteBox = DocumentLayoutEngine.layout(root, 160, 0)
                .getChildren().get(0)
                .getChildren().get(0);

        Assert.assertEquals(14, absoluteBox.getLeft());
        Assert.assertEquals(12, absoluteBox.getTop());
    }

    /**
     * 验证布局盒能按 CSS-like stacking phase 提供稳定子盒顺序。
     */
    @Test
    public void shouldExposeChildrenInStackingPhaseOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode autoPositioned = document.div();
        ElementNode positive = document.div();
        ElementNode normal = document.div();
        ElementNode negative = document.div();
        ElementNode zero = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        autoPositioned.style()
                .setHeight(UiStyleLength.px(8))
                .setPosition(UiPosition.RELATIVE);
        positive.style()
                .setHeight(UiStyleLength.px(8))
                .setPosition(UiPosition.RELATIVE)
                .setZIndex(2);
        normal.style()
                .setHeight(UiStyleLength.px(8))
                .setZIndex(9);
        negative.style()
                .setHeight(UiStyleLength.px(8))
                .setPosition(UiPosition.RELATIVE)
                .setZIndex(-1);
        zero.style()
                .setHeight(UiStyleLength.px(8))
                .setPosition(UiPosition.RELATIVE)
                .setZIndex(0);
        root.append(autoPositioned).append(positive).append(normal).append(negative).append(zero);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 160, 0);
        List<DocumentLayoutBox> orderedChildren = rootBox.getChildrenInStackingOrder();

        assertElementUid(negative, orderedChildren.get(0).getElement());
        assertElementUid(normal, orderedChildren.get(1).getElement());
        assertElementUid(autoPositioned, orderedChildren.get(2).getElement());
        assertElementUid(zero, orderedChildren.get(3).getElement());
        assertElementUid(positive, orderedChildren.get(4).getElement());
        assertElementUid(negative, rootBox.getChildrenInStackingPhase(DocumentStackingPhase.NEGATIVE_POSITIONED)
                .get(0).getElement());
    }

    /**
     * 验证直接文本子节点会按单行 block 文本参与父元素 auto 高度与流式排布。
     */
    @Test
    public void shouldLayoutDirectTextRunsInBlockFlow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(160))
                .setPadding(UiStyleLength.px(4));
        root.appendText("abc");
        child.style().setHeight(UiStyleLength.px(10));
        root.append(child);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0);
        DocumentLayoutTextRun textRun = rootBox.getTextRuns().get(0);
        DocumentLayoutBox childBox = rootBox.getChildren().get(0);

        Assert.assertEquals(1, rootBox.getTextRuns().size());
        Assert.assertEquals("abc", textRun.getText());
        Assert.assertEquals(4, textRun.getLeft());
        Assert.assertEquals(4, textRun.getTop());
        Assert.assertEquals(24, textRun.getWidth());
        Assert.assertEquals(18, textRun.getHeight());
        Assert.assertEquals(36, rootBox.getHeight());
        Assert.assertEquals(4, childBox.getLeft());
        Assert.assertEquals(22, childBox.getTop());
    }

    /**
     * 验证直接文本子节点会使用注入的文本测量服务按可用宽度换行。
     */
    @Test
    public void shouldWrapDirectTextRunsWithInjectedTextMeasureService() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style().setWidth(UiStyleLength.px(24));
        root.appendText("abcdefg");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0, new DeterministicTextMeasureService());

        Assert.assertEquals(3, rootBox.getTextRuns().size());
        assertTextRun(rootBox.getTextRuns().get(0), "abc", 0, 0, 24, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "def", 0, 18, 24, 18);
        assertTextRun(rootBox.getTextRuns().get(2), "g", 0, 36, 8, 18);
        Assert.assertEquals(54, rootBox.getHeight());
    }

    /**
     * 验证包含 span 的文本会按 inline flow 混排，并保留各文本片段所属元素。
     */
    @Test
    public void shouldLayoutTextAndSpanInInlineFlow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode span = document.span();

        root.style().setWidth(UiStyleLength.px(48));
        root.appendText("AA");
        span.appendText("BBBB");
        root.append(span);
        root.appendText("CC");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0,
                new DeterministicTextMeasureService());

        Assert.assertEquals(3, rootBox.getTextRuns().size());
        assertTextRun(rootBox.getTextRuns().get(0), "AA", 0, 0, 16, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "BBBB", 16, 0, 32, 18);
        assertTextRun(rootBox.getTextRuns().get(2), "CC", 0, 18, 16, 18);
        assertElementUid(root, rootBox.getTextRuns().get(0).getOwnerElement());
        assertElementUid(span, rootBox.getTextRuns().get(1).getOwnerElement());
        assertElementUid(root, rootBox.getTextRuns().get(2).getOwnerElement());
        Assert.assertEquals(36, rootBox.getHeight());
    }

    /**
     * 验证 inline fragment 会按行分片，保留跨行 span 的逐行背景几何。
     */
    @Test
    public void shouldSplitInlineFragmentsAcrossLines() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode span = document.span();

        root.style().setWidth(UiStyleLength.px(32));
        span.appendText("AABBCC");
        root.append(span);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0,
                new DeterministicTextMeasureService());

        Assert.assertEquals(2, rootBox.getInlineFragments().size());
        assertInlineFragment(rootBox.getInlineFragments().get(0), span, 0, 0, 32, 18);
        assertInlineFragment(rootBox.getInlineFragments().get(1), span, 0, 18, 16, 18);
        Assert.assertTrue(rootBox.getInlineFragments().get(0).isFirstForElement());
        Assert.assertFalse(rootBox.getInlineFragments().get(0).isLastForElement());
        Assert.assertFalse(rootBox.getInlineFragments().get(1).isFirstForElement());
        Assert.assertTrue(rootBox.getInlineFragments().get(1).isLastForElement());
    }

    /**
     * 验证父 inline fragment 会合并覆盖嵌套 inline 子内容。
     */
    @Test
    public void shouldMergeInlineFragmentsAcrossNestedInlineDescendants() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode outerSpan = document.span();
        ElementNode innerSpan = document.span();

        root.style().setWidth(UiStyleLength.px(80));
        outerSpan.appendText("AA");
        innerSpan.appendText("BB");
        outerSpan.append(innerSpan);
        outerSpan.appendText("CC");
        root.append(outerSpan);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 100, 0,
                new DeterministicTextMeasureService());

        Assert.assertEquals(2, rootBox.getInlineFragments().size());
        assertInlineFragment(rootBox.getInlineFragments().get(0), outerSpan, 0, 0, 48, 18);
        assertInlineFragment(rootBox.getInlineFragments().get(1), innerSpan, 16, 0, 16, 18);
        assertElementUid(outerSpan, rootBox.getTextRuns().get(0).getOwnerElement());
        assertElementUid(innerSpan, rootBox.getTextRuns().get(1).getOwnerElement());
        assertElementUid(outerSpan, rootBox.getTextRuns().get(2).getOwnerElement());
    }

    /**
     * 验证 inline 元素的 margin、border 与 padding 会参与行内流和 fragment 几何。
     */
    @Test
    public void shouldLayoutInlineFragmentWithBoxEdges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode span = document.span();

        root.style().setWidth(UiStyleLength.px(80));
        span.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(6), UiStyleLength.px(0),
                        UiStyleLength.px(4)))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(2), UiStyleLength.px(5), UiStyleLength.px(4),
                        UiStyleLength.px(3)))
                .setBorderWidth(UiStyleLength.px(1));
        root.appendText("AA");
        span.appendText("BB");
        root.append(span);
        root.appendText("CC");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 100, 0,
                new DeterministicTextMeasureService());

        Assert.assertEquals(3, rootBox.getTextRuns().size());
        assertTextRun(rootBox.getTextRuns().get(0), "AA", 0, 3, 16, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "BB", 24, 3, 16, 18);
        assertTextRun(rootBox.getTextRuns().get(2), "CC", 52, 3, 16, 18);
        Assert.assertEquals(1, rootBox.getInlineFragments().size());
        assertInlineFragment(rootBox.getInlineFragments().get(0), span, 20, 0, 26, 26);
        Assert.assertEquals(26, rootBox.getContentHeight());
    }

    /**
     * 验证 inline 元素可按行盒 top/middle/bottom 做垂直对齐。
     */
    @Test
    public void shouldApplyInlineVerticalAlignToTextAndFragments() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode baselineSpan = document.span();
        ElementNode topSpan = document.span();
        ElementNode middleSpan = document.span();
        ElementNode bottomSpan = document.span();

        root.style().setWidth(UiStyleLength.px(160));
        baselineSpan.style().setPadding(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(0),
                UiStyleLength.px(6), UiStyleLength.px(0)));
        topSpan.style().setVerticalAlign(UiVerticalAlign.TOP);
        middleSpan.style().setVerticalAlign(UiVerticalAlign.MIDDLE);
        bottomSpan.style().setVerticalAlign(UiVerticalAlign.BOTTOM);
        root.appendText("A");
        baselineSpan.appendText("B");
        topSpan.appendText("T");
        middleSpan.appendText("M");
        bottomSpan.appendText("Z");
        root.append(baselineSpan).append(topSpan).append(middleSpan).append(bottomSpan);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0,
                new DeterministicTextMeasureService());

        Assert.assertEquals(30, rootBox.getContentHeight());
        assertTextRun(rootBox.getTextRuns().get(0), "A", 0, 6, 8, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "B", 8, 6, 8, 18);
        assertTextRun(rootBox.getTextRuns().get(2), "T", 16, 0, 8, 18);
        assertTextRun(rootBox.getTextRuns().get(3), "M", 24, 6, 8, 18);
        assertTextRun(rootBox.getTextRuns().get(4), "Z", 32, 12, 8, 18);
        assertInlineFragment(rootBox.getInlineFragments().get(0), baselineSpan, 8, 0, 8, 30);
        assertInlineFragment(rootBox.getInlineFragments().get(1), topSpan, 16, 0, 8, 18);
        assertInlineFragment(rootBox.getInlineFragments().get(2), middleSpan, 24, 6, 8, 18);
        assertInlineFragment(rootBox.getInlineFragments().get(3), bottomSpan, 32, 12, 8, 18);
    }

    /**
     * 验证 flex row 会分配 grow 空间并应用 column-gap。
     */
    @Test
    public void shouldLayoutFlexRowWithGapAndGrow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode growing = document.div();
        ElementNode last = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(300))
                .setColumnGap(UiStyleLength.px(10));
        first.style()
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(20));
        growing.style()
                .setFlexGrow(1.0F)
                .setHeight(UiStyleLength.px(20));
        last.style()
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(20));
        root.append(first).append(growing).append(last);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 400, 0);
        DocumentLayoutBox firstBox = rootBox.getChildren().get(0);
        DocumentLayoutBox growingBox = rootBox.getChildren().get(1);
        DocumentLayoutBox lastBox = rootBox.getChildren().get(2);

        Assert.assertEquals(20, rootBox.getContentHeight());
        Assert.assertEquals(0, firstBox.getLeft());
        Assert.assertEquals(60, firstBox.getWidth());
        Assert.assertEquals(70, growingBox.getLeft());
        Assert.assertEquals(190, growingBox.getWidth());
        Assert.assertEquals(270, lastBox.getLeft());
        Assert.assertEquals(30, lastBox.getWidth());
    }

    /**
     * 验证 absolute 子元素不参与 flex item 空间分配。
     */
    @Test
    public void shouldExcludeAbsolutePositionedElementFromFlexLayout() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode absolute = document.div();
        ElementNode fixed = document.div();
        ElementNode growing = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(100));
        absolute.style()
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(8))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(5));
        fixed.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10));
        growing.style()
                .setFlexGrow(1.0F)
                .setHeight(UiStyleLength.px(10));
        root.append(absolute).append(fixed).append(growing);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);
        DocumentLayoutBox absoluteBox = rootBox.getChildren().get(0);
        DocumentLayoutBox fixedBox = rootBox.getChildren().get(1);
        DocumentLayoutBox growingBox = rootBox.getChildren().get(2);

        Assert.assertEquals(5, absoluteBox.getLeft());
        Assert.assertEquals(0, fixedBox.getLeft());
        Assert.assertEquals(40, fixedBox.getWidth());
        Assert.assertEquals(40, growingBox.getLeft());
        Assert.assertEquals(60, growingBox.getWidth());
        Assert.assertEquals(10, rootBox.getContentHeight());
    }

    /**
     * 验证 flex column 会在显式高度内分配 grow 空间。
     */
    @Test
    public void shouldLayoutFlexColumnWithGrowAndRowGap() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode top = document.div();
        ElementNode middle = document.div();
        ElementNode bottom = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(200))
                .setRowGap(UiStyleLength.px(10));
        top.style().setHeight(UiStyleLength.px(40));
        middle.style().setFlexGrow(1.0F);
        bottom.style().setHeight(UiStyleLength.px(20));
        root.append(top).append(middle).append(bottom);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0);
        DocumentLayoutBox topBox = rootBox.getChildren().get(0);
        DocumentLayoutBox middleBox = rootBox.getChildren().get(1);
        DocumentLayoutBox bottomBox = rootBox.getChildren().get(2);

        Assert.assertEquals(200, rootBox.getContentHeight());
        Assert.assertEquals(0, topBox.getTop());
        Assert.assertEquals(40, topBox.getHeight());
        Assert.assertEquals(50, middleBox.getTop());
        Assert.assertEquals(120, middleBox.getHeight());
        Assert.assertEquals(180, bottomBox.getTop());
        Assert.assertEquals(20, bottomBox.getHeight());
        Assert.assertEquals(100, middleBox.getWidth());
    }

    /**
     * 验证 flex row 会按 shrink 压缩超出的主轴空间。
     */
    @Test
    public void shouldShrinkFlexRowItemsWhenContentOverflows() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(100));
        first.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(10));
        second.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(10));
        root.append(first).append(second);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 160, 0);
        DocumentLayoutBox firstBox = rootBox.getChildren().get(0);
        DocumentLayoutBox secondBox = rootBox.getChildren().get(1);

        Assert.assertEquals(50, firstBox.getWidth());
        Assert.assertEquals(50, secondBox.getWidth());
        Assert.assertEquals(50, secondBox.getLeft());
    }

    /**
     * 验证 flex row 的交叉轴和主轴对齐。
     */
    @Test
    public void shouldAlignFlexRowItems() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(60))
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.END);
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 160, 0).getChildren().get(0);

        Assert.assertEquals(80, childBox.getLeft());
        Assert.assertEquals(20, childBox.getTop());
    }

    /**
     * 验证 flex row 中 auto 宽度文本子项会按同一行内容总宽测量。
     */
    @Test
    public void shouldMeasureAutoWidthInlineContentItemInFlexRow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        ElementNode span = document.span();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(100))
                .setJustifyContent(UiJustifyContent.CENTER);
        child.style()
                .setPadding(UiStyleLength.px(3))
                .setBorderWidth(UiStyleLength.px(1));
        child.appendText("HUD");
        span.style()
                .setPadding(UiStyleLength.px(2))
                .setBorderWidth(UiStyleLength.px(1));
        span.appendText("UI");
        child.append(span);
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 140, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);

        Assert.assertEquals(46, childBox.getContentWidth());
        Assert.assertEquals(54, childBox.getWidth());
        Assert.assertEquals(23, childBox.getLeft());
    }

    /**
     * 验证 flex row 中 auto 宽度非文本子项会按显式子元素尺寸测量。
     */
    @Test
    public void shouldMeasureAutoWidthNonTextItemInFlexRow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        ElementNode icon = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(80))
                .setJustifyContent(UiJustifyContent.CENTER);
        child.style()
                .setPadding(UiStyleLength.px(2))
                .setBorderWidth(UiStyleLength.px(1));
        icon.style()
                .setWidth(UiStyleLength.px(18))
                .setHeight(UiStyleLength.px(18));
        child.append(icon);
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 100, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);

        Assert.assertEquals(18, childBox.getContentWidth());
        Assert.assertEquals(24, childBox.getWidth());
        Assert.assertEquals(28, childBox.getLeft());
    }

    /**
     * 验证 flex row 固有宽度测量会保留 0 宽子项后的 column-gap。
     */
    @Test
    public void shouldKeepColumnGapAfterZeroWidthIntrinsicFlexItem() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        ElementNode zeroWidth = document.div();
        ElementNode icon = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(80))
                .setJustifyContent(UiJustifyContent.CENTER);
        child.style()
                .setDisplay(UiDisplay.FLEX)
                .setColumnGap(UiStyleLength.px(4));
        icon.style()
                .setWidth(UiStyleLength.px(18))
                .setHeight(UiStyleLength.px(18));
        child.append(zeroWidth).append(icon);
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 100, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);

        Assert.assertEquals(22, childBox.getContentWidth());
        Assert.assertEquals(22, childBox.getWidth());
        Assert.assertEquals(29, childBox.getLeft());
    }

    /**
     * 验证 flex row 固有宽度测量会合并文本子项、非文本子项与 column-gap。
     */
    @Test
    public void shouldMeasureMixedTextAndNonTextIntrinsicFlexRowContent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        ElementNode label = document.div();
        ElementNode icon = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(100))
                .setJustifyContent(UiJustifyContent.CENTER);
        child.style()
                .setDisplay(UiDisplay.FLEX)
                .setColumnGap(UiStyleLength.px(4));
        label.appendText("TXT");
        icon.style()
                .setWidth(UiStyleLength.px(18))
                .setHeight(UiStyleLength.px(18));
        child.append(label).append(icon);
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 120, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);

        Assert.assertEquals(46, childBox.getContentWidth());
        Assert.assertEquals(46, childBox.getWidth());
        Assert.assertEquals(27, childBox.getLeft());
    }

    private static void assertTextRun(DocumentLayoutTextRun textRun, String text, int left, int top, int width,
            int height) {
        Assert.assertEquals(text, textRun.getText());
        Assert.assertEquals(left, textRun.getLeft());
        Assert.assertEquals(top, textRun.getTop());
        Assert.assertEquals(width, textRun.getWidth());
        Assert.assertEquals(height, textRun.getHeight());
    }

    private static void assertInlineFragment(DocumentLayoutInlineFragment inlineFragment, ElementNode expectedElement,
            int left, int top, int width, int height) {
        assertElementUid(expectedElement, inlineFragment.getOwnerElement());
        Assert.assertEquals(left, inlineFragment.getLeft());
        Assert.assertEquals(top, inlineFragment.getTop());
        Assert.assertEquals(width, inlineFragment.getWidth());
        Assert.assertEquals(height, inlineFragment.getHeight());
    }

    private static void assertElementUid(ElementNode expectedElement, ElementNode actualElement) {
        Assert.assertNotNull(actualElement);
        Assert.assertEquals(expectedElement.__getElementUid(), actualElement.__getElementUid());
    }

    /**
     * 供布局测试使用的确定性文本测量服务。
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
