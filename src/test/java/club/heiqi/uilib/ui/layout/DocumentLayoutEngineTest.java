package club.heiqi.uilib.ui.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;

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
        Assert.assertSame(visible, rootBox.getChildren().get(0).getElement());
        Assert.assertEquals(24, rootBox.getContentHeight());
        Assert.assertEquals(24, rootBox.getHeight());
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
}
