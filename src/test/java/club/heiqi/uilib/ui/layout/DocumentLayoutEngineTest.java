package club.heiqi.uilib.ui.layout;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.paint.DocumentPaintCommand;
import club.heiqi.uilib.ui.paint.DocumentPaintCommandType;
import club.heiqi.uilib.ui.paint.DocumentPaintEngine;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderCollapse;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiPseudoElementContent;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.props.UiTextOverflow;
import club.heiqi.uilib.ui.style.props.UiTextTransform;
import club.heiqi.uilib.ui.style.props.UiVerticalAlign;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.props.UiWordBreak;
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
     * 验证 img 元素在未声明 CSS 尺寸时使用稳定的替换元素默认尺寸。
     */
    @Test
    public void shouldUseImageIntrinsicFallbackSizeForAutoDimensions() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode image = document.img();
        image.setAttribute("src", "qz_uilib:textures/test/icon.png");
        root.style().setWidth(UiStyleLength.px(120));
        root.append(image);

        DocumentLayoutBox imageBox = DocumentLayoutEngine.layout(root, 160, 0).getChildren().get(0);

        Assert.assertEquals(16, imageBox.getContentWidth());
        Assert.assertEquals(16, imageBox.getContentHeight());
    }

    /**
     * 验证 img 元素的 HTML width / height 属性可作为首版固有尺寸来源。
     */
    @Test
    public void shouldUseImageWidthAndHeightAttributesAsIntrinsicSize() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode image = document.img();
        image.setAttribute("src", "qz_uilib:textures/test/icon.png");
        image.setAttribute("width", "28");
        image.setAttribute("height", "18");
        root.style().setWidth(UiStyleLength.px(120));
        root.append(image);

        DocumentLayoutBox imageBox = DocumentLayoutEngine.layout(root, 160, 0).getChildren().get(0);

        Assert.assertEquals(28, imageBox.getContentWidth());
        Assert.assertEquals(18, imageBox.getContentHeight());
    }

    /**
     * 验证远程图片缓存命中后使用真实位图尺寸参与 auto 布局。
     */
    @Test
    public void shouldUseRemoteImageSizeAsIntrinsicSizeWhenLoaded() {
        DocumentRemoteImageCache.getInstance().clearForTesting();
        DocumentRemoteImageCache.getInstance().putForTesting("https://example.test/banner.jpg",
                new BufferedImage(48, 18, BufferedImage.TYPE_INT_ARGB));
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode image = document.img();
        image.setAttribute("src", "https://example.test/banner.jpg");
        root.style().setWidth(UiStyleLength.px(120));
        root.append(image);

        DocumentLayoutBox imageBox = DocumentLayoutEngine.layout(root, 160, 0).getChildren().get(0);

        Assert.assertEquals(48, imageBox.getContentWidth());
        Assert.assertEquals(18, imageBox.getContentHeight());
    }

    /**
     * 验证 img 单边 CSS 尺寸会按固有比例推导另一边。
     */
    @Test
    public void shouldPreserveImageRatioWhenOnlyCssWidthIsSpecified() {
        DocumentRemoteImageCache.getInstance().clearForTesting();
        DocumentRemoteImageCache.getInstance().putForTesting("https://example.test/ratio.png",
                new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB));
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode image = document.img();
        image.setAttribute("src", "https://example.test/ratio.png");
        image.style().setWidth(UiStyleLength.px(80));
        root.style().setWidth(UiStyleLength.px(120));
        root.append(image);

        DocumentLayoutBox imageBox = DocumentLayoutEngine.layout(root, 160, 0).getChildren().get(0);

        Assert.assertEquals(80, imageBox.getContentWidth());
        Assert.assertEquals(40, imageBox.getContentHeight());
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
     * 验证固定宽父容器中的 100% 宽 block 子项按浏览器默认 content-box 语义解析。
     */
    @Test
    public void shouldAllowPercentWidthBlockChildToOverflowWithPaddingAndBorder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(200));
        child.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1));
        root.append(child);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 240, 0);
        DocumentLayoutBox childBox = rootBox.getChildren().get(0);

        Assert.assertEquals(200, rootBox.getContentWidth());
        Assert.assertEquals(200, childBox.getContentWidth());
        Assert.assertEquals(218, childBox.getWidth());
        Assert.assertTrue(childBox.getRight() > rootBox.getContentLeft() + rootBox.getContentWidth());
    }

    /**
     * 验证显式 border-box 可让 100% 宽元素把 padding/border 收进指定宽度。
     */
    @Test
    public void shouldKeepPercentWidthInsideParentWhenBorderBoxSizingIsSpecified() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(200));
        child.style()
                .setDisplay(UiDisplay.BLOCK)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1));
        root.append(child);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 240, 0);
        DocumentLayoutBox childBox = rootBox.getChildren().get(0);

        Assert.assertEquals(182, childBox.getContentWidth());
        Assert.assertEquals(200, childBox.getWidth());
        Assert.assertTrue(childBox.getRight() <= rootBox.getContentLeft() + rootBox.getContentWidth());
    }

    /**
     * 验证 relative 纵向百分比偏移相对 containing block 高度解析。
     */
    @Test
    public void shouldResolveRelativeVerticalPercentOffsetAgainstContainingBlockHeight() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode top = document.div();
        ElementNode bottom = document.div();

        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(100));
        top.style()
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.percent(0.5F));
        bottom.style()
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setBottom(UiStyleLength.percent(0.5F));
        root.append(top).append(bottom);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 160, 0);
        DocumentLayoutBox topBox = rootBox.getChildren().get(0);
        DocumentLayoutBox bottomBox = rootBox.getChildren().get(1);

        Assert.assertEquals(50, topBox.getPositionOffsetY());
        Assert.assertEquals(-50, bottomBox.getPositionOffsetY());
        Assert.assertEquals(20, bottomBox.getTop());
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
     * 验证相邻块级兄弟的垂直 margin collapse 会被 overflow 创建的 BFC 阻断。
     */
    @Test
    public void shouldNotCollapseSiblingMarginsAcrossOverflowBlockFormattingContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();

        root.style().setWidth(UiStyleLength.px(160));
        first.style()
                .setHeight(UiStyleLength.px(20))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(0), UiStyleLength.px(30),
                        UiStyleLength.px(0)))
                .setOverflowY(UiOverflow.HIDDEN);
        second.style()
                .setHeight(UiStyleLength.px(20))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(40), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        root.append(first).append(second);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0);
        DocumentLayoutBox firstBox = rootBox.getChildren().get(0);
        DocumentLayoutBox secondBox = rootBox.getChildren().get(1);

        Assert.assertEquals(0, firstBox.getTop());
        Assert.assertEquals(90, secondBox.getTop());
    }

    /**
     * 验证普通块容器会与首个子块发生父子顶部 margin collapse。
     */
    @Test
    public void shouldCollapseParentAndFirstChildTopMarginsInNormalBlockFlow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(160));
        parent.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(20), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        child.style()
                .setHeight(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(30), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        parent.append(child);
        root.append(parent);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0);
        DocumentLayoutBox parentBox = rootBox.getChildren().get(0);
        DocumentLayoutBox childBox = parentBox.getChildren().get(0);

        Assert.assertEquals(30, parentBox.getTop());
        Assert.assertEquals(30, childBox.getTop());
    }

    /**
     * 验证 overflow 创建的 BFC 会阻断父子顶部 margin collapse。
     */
    @Test
    public void shouldNotCollapseParentAndFirstChildTopMarginsAcrossOverflowBlockFormattingContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(160));
        parent.style()
                .setOverflowY(UiOverflow.HIDDEN)
                .setMargin(UiStyleInsets.of(UiStyleLength.px(20), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        child.style()
                .setHeight(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(30), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        parent.append(child);
        root.append(parent);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0);
        DocumentLayoutBox parentBox = rootBox.getChildren().get(0);
        DocumentLayoutBox childBox = parentBox.getChildren().get(0);

        Assert.assertEquals(20, parentBox.getTop());
        Assert.assertEquals(50, childBox.getTop());
    }

    /**
     * 验证相邻块级负 margin collapse 按正负相加语义处理。
     */
    @Test
    public void shouldCollapseAdjacentPositiveAndNegativeMarginsByAddingThem() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();

        root.style().setWidth(UiStyleLength.px(160));
        first.style()
                .setHeight(UiStyleLength.px(20))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(0), UiStyleLength.px(30),
                        UiStyleLength.px(0)));
        second.style()
                .setHeight(UiStyleLength.px(20))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(-10), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        root.append(first).append(second);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0);
        DocumentLayoutBox secondBox = rootBox.getChildren().get(1);

        Assert.assertEquals(40, secondBox.getTop());
    }

    /**
     * 空块自身上下 margin 应先折叠，再与后续兄弟 margin 折叠。
     */
    @Test
    public void shouldCollapseEmptyBlockOwnVerticalMargins() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode empty = document.div();
        ElementNode next = document.div();

        root.style().setWidth(UiStyleLength.px(160));
        empty.style().setMargin(UiStyleInsets.of(UiStyleLength.px(20), UiStyleLength.px(0), UiStyleLength.px(30),
                UiStyleLength.px(0)));
        next.style()
                .setHeight(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(10), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        root.append(empty).append(next);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0);
        DocumentLayoutBox nextBox = rootBox.getChildren().get(1);

        Assert.assertEquals(30, nextBox.getTop());
    }

    /**
     * 父子顶部 margin collapse 应递归穿透可折叠的第一个子块。
     */
    @Test
    public void shouldCollapseFirstChildMarginsRecursively() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode outer = document.div();
        ElementNode middle = document.div();
        ElementNode inner = document.div();

        root.style().setWidth(UiStyleLength.px(160));
        outer.style().setMargin(UiStyleInsets.top(UiStyleLength.px(10)));
        middle.style().setMargin(UiStyleInsets.top(UiStyleLength.px(20)));
        inner.style()
                .setHeight(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.top(UiStyleLength.px(30)));
        middle.append(inner);
        outer.append(middle);
        root.append(outer);

        DocumentLayoutBox outerBox = DocumentLayoutEngine.layout(root, 200, 0).getChildren().get(0);
        DocumentLayoutBox middleBox = outerBox.getChildren().get(0);
        DocumentLayoutBox innerBox = middleBox.getChildren().get(0);

        Assert.assertEquals(30, outerBox.getTop());
        Assert.assertEquals(30, middleBox.getTop());
        Assert.assertEquals(30, innerBox.getTop());
    }

    /**
     * 验证 min-width 大于 max-width 时 min-width 胜出。
     */
    @Test
    public void shouldLetMinWidthWinWhenGreaterThanMaxWidth() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(200));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setMinWidth(UiStyleLength.px(120))
                .setMaxWidth(UiStyleLength.px(80));
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 240, 0).getChildren().get(0);

        Assert.assertEquals(120, childBox.getContentWidth());
    }

    /**
     * 验证 min-height 大于 max-height 时 min-height 胜出。
     */
    @Test
    public void shouldLetMinHeightWinWhenGreaterThanMaxHeight() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(200));
        child.style()
                .setHeight(UiStyleLength.px(40))
                .setMinHeight(UiStyleLength.px(120))
                .setMaxHeight(UiStyleLength.px(80));
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 240, 0).getChildren().get(0);

        Assert.assertEquals(120, childBox.getContentHeight());
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
     * 验证 border-collapse 会让表格内部不再保留 row/column gap。
     */
    @Test
    public void shouldRemoveInternalGapWhenTableBorderCollapseIsEnabled() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode table = document.table();
        ElementNode row = document.tr();
        ElementNode first = document.td();
        ElementNode second = document.td();

        root.style().setWidth(UiStyleLength.px(220));
        table.style()
                .setWidth(UiStyleLength.px(160))
                .setRowGap(UiStyleLength.px(6))
                .setColumnGap(UiStyleLength.px(8))
                .setBorderCollapse(UiBorderCollapse.COLLAPSE);
        first.style().setWidth(UiStyleLength.px(60)).setBorderWidth(UiStyleLength.px(1)).setPadding(UiStyleLength.px(2));
        second.style().setBorderWidth(UiStyleLength.px(1)).setPadding(UiStyleLength.px(2));
        first.appendText("A");
        second.appendText("B");
        row.append(first).append(second);
        table.append(row);
        root.append(table);

        DocumentLayoutBox rowBox = DocumentLayoutEngine.layout(root, 220, 0,
                new DeterministicTextMeasureService()).getChildren().get(0).getChildren().get(0);
        DocumentLayoutBox firstBox = rowBox.getChildren().get(0);
        DocumentLayoutBox secondBox = rowBox.getChildren().get(1);

        Assert.assertEquals(firstBox.getRight(), secondBox.getLeft());
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
     * 验证 absolute 定位元素相对根 padding box 定位，并脱离普通流。
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

        Assert.assertEquals(11, absoluteBox.getLeft());
        Assert.assertEquals(9, absoluteBox.getTop());
        Assert.assertEquals(30, absoluteBox.getWidth());
        Assert.assertEquals(12, absoluteBox.getHeight());
        Assert.assertEquals(12, followingBox.getLeft());
        Assert.assertEquals(12, followingBox.getTop());
        Assert.assertEquals(40, rootBox.getHeight());
    }

    /**
     * 验证 absolute 定位元素可通过 right/bottom 从根 padding box 反向定位。
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

        Assert.assertEquals(70, absoluteBox.getLeft());
        Assert.assertEquals(52, absoluteBox.getTop());
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

        Assert.assertEquals(20, absoluteBox.getLeft());
        Assert.assertEquals(11, absoluteBox.getTop());
        Assert.assertEquals(172, absoluteBox.getWidth());
        Assert.assertEquals(92, absoluteBox.getHeight());
        Assert.assertEquals(162, absoluteBox.getContentWidth());
        Assert.assertEquals(82, absoluteBox.getContentHeight());
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

        Assert.assertEquals(10, absoluteBox.getLeft());
        Assert.assertEquals(8, absoluteBox.getTop());
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

        Assert.assertEquals(19, absoluteBox.getLeft());
        Assert.assertEquals(17, absoluteBox.getTop());
    }

    /**
     * 验证 right/bottom 会按最近 positioned ancestor 的 padding box 反向定位。
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

        Assert.assertEquals(103, absoluteBox.getLeft());
        Assert.assertEquals(69, absoluteBox.getTop());
    }

    /**
     * 验证没有 positioned ancestor 时 absolute 定位回退到根 padding box。
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

        Assert.assertEquals(9, absoluteBox.getLeft());
        Assert.assertEquals(7, absoluteBox.getTop());
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
     * 验证 NORMAL 默认不在无空格英文长 token 内部断词。
     */
    @Test
    public void shouldKeepLongAsciiTokenUnbrokenWithNormalWordBreak() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style().setWidth(UiStyleLength.px(24));
        root.appendText("abcdefg");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0, new DeterministicTextMeasureService());

        Assert.assertEquals(1, rootBox.getTextRuns().size());
        assertTextRun(rootBox.getTextRuns().get(0), "abcdefg", 0, 0, 24, 18);
        Assert.assertEquals(18, rootBox.getHeight());
    }

    /**
     * 验证 overflow-wrap:break-word 会在长英文 token 溢出时断词。
     */
    @Test
    public void shouldBreakLongAsciiTokenWithOverflowWrapBreakWord() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(24))
                .setOverflowWrap(UiOverflowWrap.BREAK_WORD);
        root.appendText("abcdefg");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0, new DeterministicTextMeasureService());

        Assert.assertEquals(3, rootBox.getTextRuns().size());
        assertTextRun(rootBox.getTextRuns().get(0), "abc", 0, 0, 24, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "def", 0, 18, 24, 18);
        assertTextRun(rootBox.getTextRuns().get(2), "g", 0, 36, 8, 18);
        Assert.assertEquals(54, rootBox.getHeight());
    }

    /**
     * 验证 overflow-wrap:anywhere 会参与 auto 宽固有尺寸，区别于 break-word。
     */
    @Test
    public void shouldUseAnywhereBreaksForAutoWidthIntrinsicMeasurement() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode breakWord = document.div();
        ElementNode anywhere = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setWidth(UiStyleLength.px(100));
        breakWord.style()
                .setWidth(UiStyleLength.auto())
                .setOverflowWrap(UiOverflowWrap.BREAK_WORD);
        anywhere.style()
                .setWidth(UiStyleLength.auto())
                .setOverflowWrap(UiOverflowWrap.ANYWHERE);
        breakWord.appendText("abcdefg");
        anywhere.appendText("abcdefg");
        root.append(breakWord).append(anywhere);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0,
                new DeterministicTextMeasureService());
        DocumentLayoutBox breakWordBox = rootBox.getChildren().get(0);
        DocumentLayoutBox anywhereBox = rootBox.getChildren().get(1);

        Assert.assertEquals(56, breakWordBox.getContentWidth());
        Assert.assertEquals(8, anywhereBox.getContentWidth());
        Assert.assertEquals(7, anywhereBox.getTextRuns().size());
    }

    /**
     * 验证 word-break:break-all 会在普通英文单词内部产生断点。
     */
    @Test
    public void shouldBreakAsciiTextWithWordBreakBreakAll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(24))
                .setWordBreak(UiWordBreak.BREAK_ALL);
        root.appendText("abcdefg");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0, new DeterministicTextMeasureService());

        Assert.assertEquals(3, rootBox.getTextRuns().size());
        assertTextRun(rootBox.getTextRuns().get(0), "abc", 0, 0, 24, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "def", 0, 18, 24, 18);
        assertTextRun(rootBox.getTextRuns().get(2), "g", 0, 36, 8, 18);
    }

    /**
     * 验证 NORMAL 可按 URL 标点断行。
     */
    @Test
    public void shouldBreakUrlAtPunctuationWithNormalWordBreak() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style().setWidth(UiStyleLength.px(16));
        root.appendText("a/b/c");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0, new DeterministicTextMeasureService());

        Assert.assertEquals(3, rootBox.getTextRuns().size());
        assertTextRun(rootBox.getTextRuns().get(0), "a/", 0, 0, 16, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "b/", 0, 18, 16, 18);
        assertTextRun(rootBox.getTextRuns().get(2), "c", 0, 36, 8, 18);
    }

    /**
     * 验证 KEEP_ALL 会禁止 CJK 与英文混排文本在 CJK 边界断行。
     */
    @Test
    public void shouldRespectKeepAllForCjkAndMixedText() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode normal = document.div();
        ElementNode keepAll = document.div();

        root.style().setWidth(UiStyleLength.px(16));
        normal.style().setWidth(UiStyleLength.px(16));
        keepAll.style()
                .setWidth(UiStyleLength.px(16))
                .setWordBreak(UiWordBreak.KEEP_ALL);
        normal.appendText("中文abc");
        keepAll.appendText("中文abc");
        root.append(normal).append(keepAll);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0,
                new DeterministicTextMeasureService());
        DocumentLayoutBox normalBox = rootBox.getChildren().get(0);
        DocumentLayoutBox keepAllBox = rootBox.getChildren().get(1);

        Assert.assertEquals(2, normalBox.getTextRuns().size());
        assertTextRun(normalBox.getTextRuns().get(0), "中文", 0, 0, 16, 18);
        assertTextRun(normalBox.getTextRuns().get(1), "abc", 0, 18, 16, 18);
        Assert.assertEquals(1, keepAllBox.getTextRuns().size());
        assertTextRun(keepAllBox.getTextRuns().get(0), "中文abc", 0, 36, 16, 18);
    }

    /**
     * 验证 nowrap 仍会压过断词属性，并保留 ellipsis 行为。
     */
    @Test
    public void shouldKeepNowrapAndEllipsisBeforeBreakProperties() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(24))
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setTextOverflow(UiTextOverflow.ELLIPSIS)
                .setOverflowWrap(UiOverflowWrap.ANYWHERE)
                .setWordBreak(UiWordBreak.BREAK_ALL);
        root.appendText("abcdefg");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0, new DeterministicTextMeasureService());

        Assert.assertEquals(1, rootBox.getTextRuns().size());
        assertTextRun(rootBox.getTextRuns().get(0), "ab\u2026", 0, 0, 24, 18);
    }

    /**
     * 验证断词拟合会使用当前字体粗细参与测量。
     */
    @Test
    public void shouldUseFontWeightWhenResolvingBreakFit() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(28))
                .setFontWeight(UiFontWeight.BOLD)
                .setOverflowWrap(UiOverflowWrap.BREAK_WORD);
        root.appendText("abcd");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0, new FontAwareTextMeasureService());

        Assert.assertEquals(2, rootBox.getTextRuns().size());
        assertTextRun(rootBox.getTextRuns().get(0), "ab", 0, 0, 28, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "cd", 0, 18, 28, 18);
    }

    /**
     * 验证 ellipsis 宽度会按当前字体粗细测量。
     */
    @Test
    public void shouldUseFontWeightWhenMeasuringEllipsis() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(52))
                .setFontWeight(UiFontWeight.BOLD)
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setTextOverflow(UiTextOverflow.ELLIPSIS);
        root.appendText("abcde");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0, new FontAwareTextMeasureService());

        Assert.assertEquals(1, rootBox.getTextRuns().size());
        assertTextRun(rootBox.getTextRuns().get(0), "ab\u2026", 0, 0, 42, 18);
    }

    /**
     * 验证 text-transform 会参与文本测量与断行，text-indent 只影响首行起点。
     */
    @Test
    public void shouldApplyTextTransformAndTextIndentToTextRuns() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();

        root.style()
                .setWidth(UiStyleLength.px(32))
                .setTextTransform(UiTextTransform.UPPERCASE)
                .setTextIndent(UiStyleLength.px(8));
        root.appendText("ab cd");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0,
                new DeterministicTextMeasureService());

        Assert.assertEquals(2, rootBox.getTextRuns().size());
        assertTextRun(rootBox.getTextRuns().get(0), "AB", 8, 0, 16, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "CD", 0, 18, 16, 18);
    }

    /**
     * 验证新增 white-space 模式会分别保留空白、保留换行或禁止软换行。
     */
    @Test
    public void shouldLayoutPreWhiteSpaceModes() {
        UiDocument preWrapDocument = UiDocument.create();
        ElementNode preWrapRoot = preWrapDocument.getRootElement();
        preWrapRoot.style()
                .setWidth(UiStyleLength.px(64))
                .setWhiteSpace(UiWhiteSpace.PRE_WRAP);
        preWrapRoot.appendText("A  B\nC");

        DocumentLayoutBox preWrapBox = DocumentLayoutEngine.layout(preWrapRoot, 80, 0,
                new DeterministicTextMeasureService());

        Assert.assertEquals(2, preWrapBox.getTextRuns().size());
        assertTextRun(preWrapBox.getTextRuns().get(0), "A  B", 0, 0, 32, 18);
        assertTextRun(preWrapBox.getTextRuns().get(1), "C", 0, 18, 8, 18);

        UiDocument preLineDocument = UiDocument.create();
        ElementNode preLineRoot = preLineDocument.getRootElement();
        preLineRoot.style()
                .setWidth(UiStyleLength.px(64))
                .setWhiteSpace(UiWhiteSpace.PRE_LINE);
        preLineRoot.appendText("A   B\nC");

        DocumentLayoutBox preLineBox = DocumentLayoutEngine.layout(preLineRoot, 80, 0,
                new DeterministicTextMeasureService());

        Assert.assertEquals(2, preLineBox.getTextRuns().size());
        assertTextRun(preLineBox.getTextRuns().get(0), "A B", 0, 0, 24, 18);
        assertTextRun(preLineBox.getTextRuns().get(1), "C", 0, 18, 8, 18);

        UiDocument preDocument = UiDocument.create();
        ElementNode preRoot = preDocument.getRootElement();
        preRoot.style()
                .setWidth(UiStyleLength.px(16))
                .setWhiteSpace(UiWhiteSpace.PRE);
        preRoot.appendText("ABCD\nE");

        DocumentLayoutBox preBox = DocumentLayoutEngine.layout(preRoot, 80, 0,
                new DeterministicTextMeasureService());

        Assert.assertEquals(2, preBox.getTextRuns().size());
        assertTextRun(preBox.getTextRuns().get(0), "ABCD", 0, 0, 16, 18);
        assertTextRun(preBox.getTextRuns().get(1), "E", 0, 18, 8, 18);
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
        span.style().setWordBreak(UiWordBreak.BREAK_ALL);
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
        assertTextRun(rootBox.getTextRuns().get(0), "AA", 0, 0, 16, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "BB", 24, 0, 16, 18);
        assertTextRun(rootBox.getTextRuns().get(2), "CC", 52, 0, 16, 18);
        Assert.assertEquals(1, rootBox.getInlineFragments().size());
        assertInlineFragment(rootBox.getInlineFragments().get(0), span, 20, -3, 26, 26);
        Assert.assertEquals(18, rootBox.getContentHeight());
    }

    /**
     * 验证 inline 元素可按行盒 top/middle/bottom 做垂直对齐。
     */
    @Test
    public void shouldApplyInlineVerticalAlignToTextAndFragments() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode baselineSpan = document.span();
        ElementNode tallInlineBlock = document.div();
        ElementNode topSpan = document.span();
        ElementNode middleSpan = document.span();
        ElementNode bottomSpan = document.span();

        root.style().setWidth(UiStyleLength.px(160));
        baselineSpan.style().setPadding(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(0),
                UiStyleLength.px(6), UiStyleLength.px(0)));
        tallInlineBlock.style()
                .setDisplay(UiDisplay.INLINE_BLOCK)
                .setWidth(UiStyleLength.px(0))
                .setHeight(UiStyleLength.px(30));
        topSpan.style().setVerticalAlign(UiVerticalAlign.TOP);
        middleSpan.style().setVerticalAlign(UiVerticalAlign.MIDDLE);
        bottomSpan.style().setVerticalAlign(UiVerticalAlign.BOTTOM);
        root.appendText("A");
        root.append(tallInlineBlock);
        baselineSpan.appendText("B");
        topSpan.appendText("T");
        middleSpan.appendText("M");
        bottomSpan.appendText("Z");
        root.append(baselineSpan).append(topSpan).append(middleSpan).append(bottomSpan);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 200, 0,
                new DeterministicTextMeasureService());

        Assert.assertEquals(30, rootBox.getContentHeight());
        assertTextRun(rootBox.getTextRuns().get(0), "A", 0, 0, 8, 18);
        assertTextRun(rootBox.getTextRuns().get(1), "B", 8, 0, 8, 18);
        assertTextRun(rootBox.getTextRuns().get(2), "T", 16, 0, 8, 18);
        assertTextRun(rootBox.getTextRuns().get(3), "M", 24, 6, 8, 18);
        assertTextRun(rootBox.getTextRuns().get(4), "Z", 32, 12, 8, 18);
        assertInlineFragment(rootBox.getInlineFragments().get(0), baselineSpan, 8, -6, 8, 30);
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
     * 验证固定高度 column flex 父容器不足时，普通 auto-height 子项不会被压到低于内容高度。
     */
    @Test
    public void shouldKeepVisibleAutoHeightColumnFlexItemsAtContentHeightWhenParentOverflows() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode firstCard = document.div();
        ElementNode secondCard = document.div();
        ElementNode firstTop = document.div();
        ElementNode firstBottom = document.div();
        ElementNode secondTop = document.div();
        ElementNode secondBottom = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(70))
                .setOverflowY(UiOverflow.AUTO);
        firstTop.style().setHeight(UiStyleLength.px(32));
        firstBottom.style().setHeight(UiStyleLength.px(28));
        secondTop.style().setHeight(UiStyleLength.px(32));
        secondBottom.style().setHeight(UiStyleLength.px(28));
        firstCard.append(firstTop).append(firstBottom);
        secondCard.append(secondTop).append(secondBottom);
        root.append(firstCard).append(secondCard);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 160, 0);
        DocumentLayoutBox firstCardBox = rootBox.getChildren().get(0);
        DocumentLayoutBox secondCardBox = rootBox.getChildren().get(1);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);

        Assert.assertEquals(70, rootBox.getContentHeight());
        Assert.assertEquals(60, firstCardBox.getContentHeight());
        Assert.assertEquals(60, secondCardBox.getContentHeight());
        Assert.assertTrue(secondCardBox.getTop() >= firstCardBox.getBottom());
        Assert.assertTrue(secondCardBox.getBottom() > rootBox.getContentTop() + rootBox.getContentHeight());
        Assert.assertTrue(scrollState.getMaxScrollTop(root) > 0);
    }

    /**
     * 验证 column flex 中自身 overflow-y:auto 的 auto-height 子项仍可收缩并承载内部滚动。
     */
    @Test
    public void shouldAllowOverflowAutoColumnFlexItemToShrinkBelowContentHeight() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode scroller = document.div();
        ElementNode content = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(80));
        scroller.style().setOverflowY(UiOverflow.AUTO);
        content.style().setHeight(UiStyleLength.px(160));
        scroller.append(content);
        root.append(scroller);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 160, 0);
        DocumentLayoutBox scrollerBox = rootBox.getChildren().get(0);
        DocumentLayoutBox contentBox = scrollerBox.getChildren().get(0);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);

        Assert.assertEquals(80, rootBox.getContentHeight());
        Assert.assertEquals(80, scrollerBox.getContentHeight());
        Assert.assertEquals(160, contentBox.getContentHeight());
        Assert.assertTrue(scrollState.getMaxScrollTop(scroller) > 0);
    }

    /**
     * 验证显式高度的 column flex 子项仍保留原有 flex-shrink 分配行为。
     */
    @Test
    public void shouldShrinkExplicitHeightColumnFlexItemsWhenContentOverflows() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(100));
        first.style().setHeight(UiStyleLength.px(80));
        second.style().setHeight(UiStyleLength.px(80));
        root.append(first).append(second);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 160, 0);
        DocumentLayoutBox firstBox = rootBox.getChildren().get(0);
        DocumentLayoutBox secondBox = rootBox.getChildren().get(1);

        Assert.assertEquals(50, firstBox.getHeight());
        Assert.assertEquals(50, secondBox.getHeight());
        Assert.assertEquals(50, secondBox.getTop());
    }

    /**
     * 验证 flex column 在非 stretch 下会按固有内容宽度测量 auto 宽子项，而不是压成 0 宽。
     */
    @Test
    public void shouldMeasureAutoWidthItemsInFlexColumnWhenAlignItemsStart() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        ElementNode icon = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setWidth(UiStyleLength.px(120));
        child.style()
                .setPadding(UiStyleLength.px(2))
                .setBorderWidth(UiStyleLength.px(1));
        icon.style()
                .setWidth(UiStyleLength.px(18))
                .setHeight(UiStyleLength.px(18));
        child.append(icon);
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 160, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);

        Assert.assertEquals(18, childBox.getContentWidth());
        Assert.assertEquals(24, childBox.getWidth());
        Assert.assertEquals(0, childBox.getLeft());
    }

    /**
     * 验证 flex column 中嵌套的 auto 宽 flex row 会保留自身内容宽度。
     */
    @Test
    public void shouldMeasureNestedFlexRowAutoWidthInFlexColumn() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode first = document.div();
        ElementNode second = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setWidth(UiStyleLength.px(120));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setColumnGap(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(2))
                .setBorderWidth(UiStyleLength.px(1));
        first.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(12));
        second.style()
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(12));
        row.append(first).append(second);
        root.append(row);

        DocumentLayoutBox rowBox = DocumentLayoutEngine.layout(root, 160, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);

        Assert.assertEquals(58, rowBox.getContentWidth());
        Assert.assertEquals(64, rowBox.getWidth());
        Assert.assertEquals(0, rowBox.getLeft());
    }

    /**
     * 验证 flex column 的交叉轴对齐会基于测得宽度做 center 偏移。
     */
    @Test
    public void shouldCenterAutoWidthItemInFlexColumn() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.CENTER)
                .setWidth(UiStyleLength.px(120));
        child.style()
                .setPadding(UiStyleLength.px(3))
                .setBorderWidth(UiStyleLength.px(1));
        child.appendText("HUD");
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 160, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);

        Assert.assertEquals(24, childBox.getContentWidth());
        Assert.assertEquals(32, childBox.getWidth());
        Assert.assertEquals(44, childBox.getLeft());
    }

    /**
     * 验证 flex column 的交叉轴对齐会基于测得宽度做 end 偏移。
     */
    @Test
    public void shouldEndAlignAutoWidthItemInFlexColumn() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.END)
                .setWidth(UiStyleLength.px(120));
        child.style()
                .setPadding(UiStyleLength.px(2))
                .setBorderWidth(UiStyleLength.px(1));
        child.appendText("UI");
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 160, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);

        Assert.assertEquals(16, childBox.getContentWidth());
        Assert.assertEquals(22, childBox.getWidth());
        Assert.assertEquals(98, childBox.getLeft());
    }

    /**
     * 验证 flex column 中文本块在换行后会保留真实高度，不会把后续兄弟项压叠到前一项文本上。
     */
    @Test
    public void shouldKeepWrappedTextBlocksSeparatedInFlexColumn() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode card = document.div();

        root.style().setWidth(UiStyleLength.px(258));
        card.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(3));
        card.append(createAutoWidthWrappedTextBlock(document, "会话概览"));
        card.append(createAutoWidthWrappedTextBlock(document,
                "容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。"));
        card.append(createAutoWidthWrappedTextBlock(document,
                "滚轮停在这里可查看内部内容，继续补充中文描述，确保在接近 HUD 浮窗宽度的环境下发生明显换行。"));
        root.append(card);

        DocumentLayoutBox cardBox = DocumentLayoutEngine.layout(root, 258, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);
        DocumentLayoutBox titleBox = cardBox.getChildren().get(0);
        DocumentLayoutBox summaryBox = cardBox.getChildren().get(1);
        DocumentLayoutBox bodyBox = cardBox.getChildren().get(2);

        Assert.assertTrue(summaryBox.getTop() >= titleBox.getBottom());
        Assert.assertTrue(bodyBox.getTop() >= summaryBox.getBottom());
        Assert.assertTrue(summaryBox.getContentHeight() > 18);
        Assert.assertTrue(bodyBox.getContentHeight() > 18);
    }

    /**
     * 验证生产 HUD 浮窗等价结构不会横向撑出面板，且主要文本卡片按行序稳定排布。
     */
    @Test
    public void shouldLayoutProductionHudLikePanelWithoutOverflowOrOverlap() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode dragBar = document.div();
        ElementNode heroCard = document.div();
        ElementNode controlCard = document.div();
        ElementNode debugToggleCard = document.div();
        ElementNode scrollContent = document.div();
        ElementNode contentBody = document.div();
        ElementNode overviewCard = document.div();
        ElementNode noteCard = document.div();
        ElementNode tipsCard = document.div();

        root.style().setWidth(UiStyleLength.px(2048));
        panel.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(368))
                .setPadding(UiStyleLength.px(12))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(8));
        dragBar.style().setWidth(UiStyleLength.auto()).setPadding(UiStyleLength.px(4));
        dragBar.appendText("HUD 工具浮窗 · 拖住这里移动");

        heroCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(4));
        heroCard.append(createAutoWidthWrappedTextBlock(document, "INTERACTIVE HUD"));
        heroCard.append(createAutoWidthWrappedTextBlock(document, "容器界面可交互"));
        heroCard.append(createAutoWidthWrappedTextBlock(document, "主浮窗调试台"));
        heroCard.append(createAutoWidthWrappedTextBlock(document,
                "把工具浮窗停在背包右上区域，用于核对 HUD 层可见性、输入接管与滚轮状态。"));

        controlCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(6));
        controlCard.append(createAutoWidthWrappedTextBlock(document, "调试开关"));
        debugToggleCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(6));
        debugToggleCard.append(createAutoWidthWrappedTextBlock(document, "显示 HUD 调试信息"));
        ElementNode toggleHost = document.div();
        toggleHost.style().setDisplay(UiDisplay.BLOCK).setWidth(UiStyleLength.auto());
        toggleHost.append(new club.heiqi.uilib.ui.control.DocumentToggleSwitchControl(document)
                .setToggled(true)
                .getElement());
        debugToggleCard.append(toggleHost);
        controlCard.append(debugToggleCard);
        controlCard.append(createAutoWidthWrappedTextBlock(document, "底部提示标记：保留"));

        scrollContent.style()
                .setFlexGrow(1.0F)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1));
        contentBody.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.STRETCH)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(6));
        scrollContent.append(contentBody);

        overviewCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(3));
        overviewCard.append(createAutoWidthWrappedTextBlock(document, "会话概览"));
        overviewCard.append(createAutoWidthWrappedTextBlock(document,
                "容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。"));
        contentBody.append(overviewCard);

        noteCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.STRETCH)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(4));
        noteCard.append(createAutoWidthWrappedTextBlock(document, "容器备注"));
        DocumentTextInputControl input = new DocumentTextInputControl(document)
                .setPlaceholder("在容器界面中输入备注")
                .setText("把鼠标移到背包界面后尝试编辑我");
        input.getElement().style().setDisplay(UiDisplay.BLOCK).setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F));
        noteCard.append(input.getElement());
        DocumentButtonControl button = new DocumentButtonControl(document, "记录一次点击");
        button.getElement().style().setDisplay(UiDisplay.BLOCK).setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F));
        noteCard.append(button.getElement());
        contentBody.append(noteCard);

        tipsCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.START)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(3));
        tipsCard.append(createAutoWidthWrappedTextBlock(document, "HUD DEBUG"));
        tipsCard.append(createAutoWidthWrappedTextBlock(document, "滚轮监控：有范围但未命中宿主。偏移 0 / 439。"));
        contentBody.append(tipsCard);

        panel.append(dragBar).append(heroCard).append(controlCard).append(scrollContent);
        root.append(panel);

        DocumentLayoutBox panelBox = DocumentLayoutEngine.layout(root, 2048, 1152,
                new DeterministicTextMeasureService()).getChildren().get(0);
        DocumentLayoutBox heroCardBox = panelBox.getChildren().get(1);
        DocumentLayoutBox controlCardBox = panelBox.getChildren().get(2);
        DocumentLayoutBox scrollContentBox = panelBox.getChildren().get(3);
        DocumentLayoutBox overviewCardBox = scrollContentBox.getChildren().get(0).getChildren().get(0);
        DocumentLayoutBox noteCardBox = scrollContentBox.getChildren().get(0).getChildren().get(1);
        DocumentLayoutBox noteInputBox = noteCardBox.getChildren().get(1);
        DocumentLayoutBox noteButtonBox = noteCardBox.getChildren().get(2);

        int panelContentRight = panelBox.getContentLeft() + panelBox.getContentWidth();
        Assert.assertTrue(heroCardBox.getRight() <= panelContentRight);
        Assert.assertTrue(controlCardBox.getRight() <= panelContentRight);
        Assert.assertTrue(scrollContentBox.getRight() <= panelContentRight);
        Assert.assertTrue(heroCardBox.getChildren().get(1).getTop() >= heroCardBox.getChildren().get(0).getBottom());
        Assert.assertTrue(heroCardBox.getChildren().get(2).getTop() >= heroCardBox.getChildren().get(1).getBottom());
        Assert.assertTrue(heroCardBox.getChildren().get(3).getTop() >= heroCardBox.getChildren().get(2).getBottom());
        Assert.assertTrue(controlCardBox.getChildren().get(1).getTop() >= controlCardBox.getChildren().get(0).getBottom());
        Assert.assertTrue(noteCardBox.getChildren().get(1).getTop() >= noteCardBox.getChildren().get(0).getBottom());
        Assert.assertTrue(noteCardBox.getChildren().get(2).getTop() >= noteCardBox.getChildren().get(1).getBottom());
        Assert.assertTrue(overviewCardBox.getRight() <= scrollContentBox.getContentLeft() + scrollContentBox.getContentWidth());
        Assert.assertTrue(noteInputBox.getRight() <= noteCardBox.getContentLeft() + noteCardBox.getContentWidth());
        Assert.assertTrue(noteButtonBox.getRight() <= noteCardBox.getContentLeft() + noteCardBox.getContentWidth());
    }

    /**
     * 验证 `::before` / `::after` 会进入布局树并按文档顺序参与文本排版。
     */
    @Test
    public void shouldLayoutPseudoElementsBeforeAndAfterAroundRealChildren() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode badge = document.span();

        root.style().setWidth(UiStyleLength.px(120));
        badge.appendText("MID");
        root.append(badge);
        document.addStyleSheet(club.heiqi.uilib.ui.style.cascade.UiStyleSheet.create()
                .addRule("span::before", new club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration()
                        .setContent(UiPseudoElementContent.text("PRE")))
                .addRule("span::after", new club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration()
                        .setContent(UiPseudoElementContent.text("POST"))));

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 160, 0,
                new DeterministicTextMeasureService());

        Assert.assertEquals(3, rootBox.getTextRuns().size());
        Assert.assertEquals("PRE", rootBox.getTextRuns().get(0).getText());
        Assert.assertEquals("MID", rootBox.getTextRuns().get(1).getText());
        Assert.assertEquals("POST", rootBox.getTextRuns().get(2).getText());
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
     * 验证 display 从 none 切换到 block 后仍保持正常 block flow，不会挤成一排。
     */
    @Test
    public void shouldPreserveBlockFlowAfterDisplayToggleFromNone() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode pageRoot = document.div();

        root.style().setWidth(UiStyleLength.px(258));
        pageRoot.style().setDisplay(UiDisplay.NONE);
        for (int index = 1; index <= 4; index++) {
            pageRoot.append(createWrappedHudCard(document, index));
        }
        root.append(pageRoot);

        DocumentLayoutBox hiddenRootBox = DocumentLayoutEngine.layout(root, 258, 0,
                new DeterministicTextMeasureService());
        Assert.assertTrue(hiddenRootBox.getChildren().isEmpty());

        pageRoot.style().setDisplay(UiDisplay.BLOCK);
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 258, 0,
                new DeterministicTextMeasureService());
        DocumentLayoutBox pageBox = rootBox.getChildren().get(0);
        DocumentLayoutBox firstCard = pageBox.getChildren().get(0);
        DocumentLayoutBox secondCard = pageBox.getChildren().get(1);

        Assert.assertTrue(secondCard.getTop() >= firstCard.getBottom());
        Assert.assertTrue(pageBox.getContentHeight() > firstCard.getHeight());
    }

    /**
     * 验证 HUD 风格卡片在中文长文本换行后会正确扩展高度，且各文本块不重叠。
     */
    @Test
    public void shouldExpandBlockCardHeightWithWrappedHudText() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode card = createWrappedHudCard(document, 1);

        root.style().setWidth(UiStyleLength.px(258));
        root.append(card);

        DocumentLayoutBox cardBox = DocumentLayoutEngine.layout(root, 258, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);
        DocumentLayoutBox titleBox = cardBox.getChildren().get(0);
        DocumentLayoutBox descriptionBox = cardBox.getChildren().get(1);
        DocumentLayoutBox bodyBox = cardBox.getChildren().get(2);

        Assert.assertTrue(titleBox.getBottom() <= descriptionBox.getTop());
        Assert.assertTrue(descriptionBox.getBottom() <= bodyBox.getTop());
        Assert.assertTrue(cardBox.getContentHeight() > bodyBox.getHeight());
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

    /**
     * 验证 flex order 会改变 flex item 的布局与盒树视觉顺序。
     */
    @Test
    public void shouldLayoutFlexItemsByOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();
        ElementNode third = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(120));
        first.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setOrder(2);
        second.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setOrder(-1);
        third.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10));
        root.append(first).append(second).append(third);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 160, 0);
        DocumentLayoutBox firstVisualBox = rootBox.getChildren().get(0);
        DocumentLayoutBox secondVisualBox = rootBox.getChildren().get(1);
        DocumentLayoutBox thirdVisualBox = rootBox.getChildren().get(2);

        assertElementUid(second, firstVisualBox.getElement());
        assertElementUid(third, secondVisualBox.getElement());
        assertElementUid(first, thirdVisualBox.getElement());
        Assert.assertEquals(0, firstVisualBox.getLeft());
        Assert.assertEquals(20, secondVisualBox.getLeft());
        Assert.assertEquals(40, thirdVisualBox.getLeft());
    }

    /**
     * 验证 calc(percent, px) 会按混合单位参与宽高布局计算。
     */
    @Test
    public void shouldResolveCalcLengthInLayout() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(100));
        child.style()
                .setWidth(UiStyleLength.calc(1.0F, -24.0F))
                .setHeight(UiStyleLength.calc(0.5F, 8.0F));
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 240, 0).getChildren().get(0);

        Assert.assertEquals(176, childBox.getContentWidth());
        Assert.assertEquals(58, childBox.getContentHeight());
    }

    /**
     * 验证 position:sticky 在滚动后参与绘制与命中偏移。
     */
    @Test
    public void shouldApplyStickyPositionDuringScrollPaintAndHitTest() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode spacer = document.div();
        ElementNode sticky = document.div();
        ElementNode tail = document.div();

        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(60))
                .setOverflowY(UiOverflow.AUTO);
        spacer.style().setHeight(UiStyleLength.px(40));
        sticky.style()
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.STICKY)
                .setTop(UiStyleLength.px(0))
                .setBackgroundColor(0xFF224466);
        tail.style().setHeight(UiStyleLength.px(120));
        root.append(spacer).append(sticky).append(tail);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        Assert.assertTrue(scrollState.setScrollOffset(root, 0, 50));

        DocumentPaintCommand stickyBackground = findBackgroundCommand(sticky,
                DocumentPaintEngine.buildPaintCommands(rootBox, scrollState, 0L));

        Assert.assertNotNull(stickyBackground);
        Assert.assertEquals(0, stickyBackground.getTop());
        Assert.assertSame(sticky, DocumentHitTestEngine.hitTest(rootBox, scrollState, 5, 5));
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

    private static DocumentPaintCommand findBackgroundCommand(ElementNode element, List<DocumentPaintCommand> commands) {
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == DocumentPaintCommandType.BACKGROUND && command.getElement() == element) {
                return command;
            }
        }
        return null;
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

    /**
     * 会区分粗体宽度的文本测量服务，用于验证布局阶段传递字体样式。
     */
    private static final class FontAwareTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return widthOf(text, 4);
        }

        @Override
        public int getStringWidth(String text, club.heiqi.uilib.ui.text.TextContentMode textContentMode,
                UiFontWeight fontWeight, club.heiqi.uilib.ui.base.props.UiFontStyle fontStyle) {
            return widthOf(text, fontWeight == UiFontWeight.BOLD ? 7 : 4);
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            return trimByWidth(text, targetWidth, 4);
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth,
                club.heiqi.uilib.ui.text.TextContentMode textContentMode, UiFontWeight fontWeight,
                club.heiqi.uilib.ui.base.props.UiFontStyle fontStyle) {
            return trimByWidth(text, targetWidth, fontWeight == UiFontWeight.BOLD ? 7 : 4);
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            List<String> lines = new ArrayList<String>();
            lines.add(text == null ? "" : text);
            return lines;
        }

        private static int widthOf(String text, int charWidth) {
            return text == null ? 0 : text.length() * charWidth;
        }

        private static String trimByWidth(String text, int targetWidth, int charWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxLength = Math.max(0, targetWidth / charWidth);
            return text.substring(0, Math.min(text.length(), maxLength));
        }
    }

    private static ElementNode createWrappedHudCard(UiDocument document, int index) {
        ElementNode card = document.div();
        card.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setMargin(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1));
        card.append(createWrappedHudTextBlock(document, "卡片 " + index + " 标题"));
        card.append(createWrappedHudTextBlock(document,
                "卡片 " + index + " 描述：用于验证 HUD 中文说明在较窄宽度下的正常换行与块级布局。"));
        card.append(createWrappedHudTextBlock(document,
                "卡片 " + index + " 正文：这是一段较长的中文说明文字，需要在接近 HUD 浮窗宽度的环境下发生二到四行换行。"
                        + "继续补充第二句说明，确保 card 高度会随着文本换行自然增长，而不是把文本块互相压叠。"
                        + "继续补充第三句说明，避免英文短词测试掩盖中文换行高度问题。"));
        return card;
    }

    private static ElementNode createWrappedHudTextBlock(UiDocument document, String text) {
        ElementNode block = document.div();
        block.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F));
        block.appendText(text);
        return block;
    }

    private static ElementNode createAutoWidthWrappedTextBlock(UiDocument document, String text) {
        ElementNode block = document.div();
        block.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.auto());
        block.appendText(text);
        return block;
    }

    /**
     * 验证 box-sizing:border-box 声明 height 后，content height 扣除 padding/border，border-box height 等于声明值。
     */
    @Test
    public void shouldApplyBorderBoxHeightToBlockElement() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(200));
        child.style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setHeight(UiStyleLength.px(100))
                .setPadding(UiStyleLength.px(10))
                .setBorderWidth(UiStyleLength.px(2));
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 240, 0).getChildren().get(0);

        // border-box height = 100；border(2+2) + padding(10+10) = 24；content height = 76
        Assert.assertEquals(100, childBox.getHeight());
        Assert.assertEquals(76, childBox.getContentHeight());
    }

    /**
     * 验证声明宽度且高度 auto 时，aspect-ratio 会导出内容高度。
     */
    @Test
    public void shouldResolveAspectRatioHeightFromSpecifiedWidth() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style().setWidth(UiStyleLength.px(220));
        child.style()
                .setWidth(UiStyleLength.px(128))
                .setAspectRatio(16.0F / 9.0F);
        root.append(child);

        DocumentLayoutBox childBox = DocumentLayoutEngine.layout(root, 240, 0,
                new DeterministicTextMeasureService()).getChildren().get(0);

        Assert.assertEquals(128, childBox.getContentWidth());
        Assert.assertEquals(72, childBox.getContentHeight());
    }

    /**
     * 验证 flex column 子项 box-sizing:border-box height 正确折算主轴尺寸，后续兄弟项不重叠。
     */
    @Test
    public void shouldApplyBorderBoxHeightToFlexColumnItem() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setWidth(UiStyleLength.px(120));
        first.style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setHeight(UiStyleLength.px(60))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1));
        second.style().setHeight(UiStyleLength.px(20));
        root.append(first).append(second);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 160, 0);
        DocumentLayoutBox firstBox = rootBox.getChildren().get(0);
        DocumentLayoutBox secondBox = rootBox.getChildren().get(1);

        // border-box height = 60；border(1+1) + padding(8+8) = 18；content height = 42
        Assert.assertEquals(60, firstBox.getHeight());
        Assert.assertEquals(42, firstBox.getContentHeight());
        // second 紧接在 first 下方，不重叠
        Assert.assertEquals(60, secondBox.getTop());
        Assert.assertEquals(20, secondBox.getHeight());
    }

    /**
     * 验证 img 显式 height + box-sizing:border-box 时 border-box height 等于声明值。
     */
    @Test
    public void shouldApplyBorderBoxHeightToImgWithExplicitHeight() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode img = document.img();

        root.style().setWidth(UiStyleLength.px(200));
        img.style()
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setHeight(UiStyleLength.px(80))
                .setPadding(UiStyleLength.px(4))
                .setBorderWidth(UiStyleLength.px(2));
        root.append(img);

        DocumentLayoutBox imgBox = DocumentLayoutEngine.layout(root, 240, 0).getChildren().get(0);

        // border-box height = 80；border(2+2) + padding(4+4) = 12；content height = 68
        Assert.assertEquals(80, imgBox.getHeight());
        Assert.assertEquals(68, imgBox.getContentHeight());
    }

    /**
     * I7 端到端（flex grow 维度兜底）：DOM 层结构变更只标容器自身，受影响的 grow 兄弟项
     * 仍被 layout 复用闸门按 forcedContentWidth 维度变化捕获重算。
     *
     * <p>flex row 容器含 grow 项 + 固定项，先 layout 一轮，再从 DOM 删除固定项并带上一轮
     * 根盒做增量布局。grow 项的 layout version 未被 DOM 标脏，但其 forcedContentWidth 因
     * 兄弟移除从 240 变为 300，闸门据此判定复用失败并重算，最终宽度正确扩张到 300。</p>
     */
    @Test
    public void shouldRecomputeGrowFlexItemWidthAfterSiblingRemovedViaForcedDimensionGate() {
        DeterministicTextMeasureService measure = new DeterministicTextMeasureService();
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode growing = document.div();
        ElementNode fixed = document.div();

        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(300));
        growing.style()
                .setFlexGrow(1.0F)
                .setHeight(UiStyleLength.px(20));
        fixed.style()
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(20));
        root.append(growing).append(fixed);

        DocumentLayoutBox firstPass = DocumentLayoutEngine.layout(root, 400, 0, measure, null, null);
        Assert.assertEquals(240, firstPass.getChildren().get(0).getWidth());
        Assert.assertEquals(60, firstPass.getChildren().get(1).getWidth());

        // 仅从 DOM 删除固定项：方案 X 下只标容器 self + 被移除节点 self，grow 项 version 不变。
        root.removeChild(fixed);

        DocumentLayoutBox secondPass = DocumentLayoutEngine.layout(root, 400, 0, measure, null, firstPass);
        DocumentLayoutBox growingBox = secondPass.getChildren().get(0);

        // grow 项 forcedContentWidth 从 240 变 300 → 闸门捕获重算，最终占满整行。
        Assert.assertEquals(1, secondPass.getChildren().size());
        Assert.assertEquals(300, growingBox.getWidth());
    }

    /**
     * I7 端到端（block 平移兜底）：在 block 列表中间插入一项，后续稳定兄弟的 layout version
     * 未被 DOM 标脏，约束与 forced 维度也未变，仅 flowTop 改变，闸门据 deltaY 走 translatedTo
     * 平移复用，top 正确下移。
     */
    @Test
    public void shouldTranslateStableBlockSiblingAfterMiddleInsertViaReuseGate() {
        DeterministicTextMeasureService measure = new DeterministicTextMeasureService();
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode last = document.div();

        root.style().setWidth(UiStyleLength.px(200));
        first.style().setHeight(UiStyleLength.px(20));
        last.style().setHeight(UiStyleLength.px(30));
        root.append(first).append(last);

        DocumentLayoutBox firstPass = DocumentLayoutEngine.layout(root, 200, 0, measure, null, null);
        Assert.assertEquals(0, firstPass.getChildren().get(0).getTop());
        Assert.assertEquals(20, firstPass.getChildren().get(1).getTop());

        // 在 first 与 last 之间插入中间项：last version 不变，只是 flowTop 下移。
        ElementNode middle = document.div();
        middle.style().setHeight(UiStyleLength.px(10));
        root.insertBefore(middle, last);

        DocumentLayoutBox secondPass = DocumentLayoutEngine.layout(root, 200, 0, measure, null, firstPass);
        Assert.assertEquals(3, secondPass.getChildren().size());
        Assert.assertEquals(0, secondPass.getChildren().get(0).getTop());
        Assert.assertEquals(20, secondPass.getChildren().get(1).getTop());
        // last 经 translatedTo 平移复用，top 从 20 下移到 30。
        Assert.assertEquals(30, secondPass.getChildren().get(2).getTop());
    }

    /**
     * I7 端到端（table 列宽维度兜底）：删除一行改变 auto 列的跨行 max-intrinsic 宽度，
     * table 容器经冒泡的 subtree 版本被刷新触发整体重算，稳定行的同列 cell 宽度按新列宽更新。
     *
     * <p>两列均 auto：初始第 0 列被第二行的长文本撑大（cell0 宽于 cell1），删除长文本所在行后，
     * 第 0 列 max-intrinsic 回落，剩余空间在两 auto 列间均分，稳定行两 cell 宽度趋于相等且总和守恒。
     * 这验证 DOM 层只标 tbody 自身 + 向上冒泡刷 table/root subtree 版本，闸门据 subtree 版本变化
     * 让祖先重算并下沉到 table，cell forcedContentWidth 维度随之更新。</p>
     */
    @Test
    public void shouldRecomputeTableCellWidthsAfterRowRemovedViaForcedDimensionGate() {
        DeterministicTextMeasureService measure = new DeterministicTextMeasureService();
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode table = document.table();
        ElementNode body = document.tbody();
        ElementNode firstRow = document.tr();
        ElementNode secondRow = document.tr();
        ElementNode firstCell0 = document.td();
        ElementNode firstCell1 = document.td();
        ElementNode secondCell0 = document.td();
        ElementNode secondCell1 = document.td();

        root.style().setWidth(UiStyleLength.px(260));
        table.style()
                .setWidth(UiStyleLength.px(200))
                .setColumnGap(UiStyleLength.px(0))
                .setRowGap(UiStyleLength.px(0));
        // 第一行两列文本等长，第二行第 0 列为长文本，撑大第 0 列 intrinsic。
        firstCell0.appendText("AA");
        firstCell1.appendText("BB");
        secondCell0.appendText("AAAAAAAAAAAAAAAAAAAA");
        secondCell1.appendText("DD");
        firstRow.append(firstCell0).append(firstCell1);
        secondRow.append(secondCell0).append(secondCell1);
        body.append(firstRow).append(secondRow);
        table.append(body);
        root.append(table);

        DocumentLayoutBox firstPass = DocumentLayoutEngine.layout(root, 260, 0, measure, null, null);
        DocumentLayoutBox firstRowBoxBefore = firstPass.getChildren().get(0).getChildren().get(0)
                .getChildren().get(0);
        int cell0WidthBefore = firstRowBoxBefore.getChildren().get(0).getWidth();
        int cell1WidthBefore = firstRowBoxBefore.getChildren().get(1).getWidth();
        // 长文本行把第 0 列撑得比第 1 列宽。
        Assert.assertTrue("删行前第 0 列应被长文本撑得更宽，cell0=" + cell0WidthBefore
                + ", cell1=" + cell1WidthBefore, cell0WidthBefore > cell1WidthBefore);
        Assert.assertEquals("table 内容宽守恒", 200, cell0WidthBefore + cell1WidthBefore);

        // 仅从 DOM 删除长文本所在行：方案 X 下只标 tbody self + 向上冒泡刷 table/root subtree 版本。
        body.removeChild(secondRow);

        DocumentLayoutBox secondPass = DocumentLayoutEngine.layout(root, 260, 0, measure, null, firstPass);
        DocumentLayoutBox firstRowBoxAfter = secondPass.getChildren().get(0).getChildren().get(0)
                .getChildren().get(0);
        int cell0WidthAfter = firstRowBoxAfter.getChildren().get(0).getWidth();
        int cell1WidthAfter = firstRowBoxAfter.getChildren().get(1).getWidth();

        // 长文本行移除后第 0 列 intrinsic 回落，两 auto 列均分剩余空间趋于相等，总宽仍守恒。
        Assert.assertEquals("删行后 tbody 应只剩一行", 1,
                secondPass.getChildren().get(0).getChildren().get(0).getChildren().size());
        Assert.assertEquals("删行后两列等长文本应使列宽相等，cell0=" + cell0WidthAfter
                + ", cell1=" + cell1WidthAfter, cell0WidthAfter, cell1WidthAfter);
        Assert.assertEquals("table 内容宽守恒", 200, cell0WidthAfter + cell1WidthAfter);
    }
}
