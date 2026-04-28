package club.heiqi.uilib.ui.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * `DocumentHitTestEngine` 的 HTML-like 命中测试契约。
 */
public class DocumentHitTestEngineTest {

    /**
     * 验证 z-index 更高的 relative 子元素在视觉重叠区域优先命中。
     */
    @Test
    public void shouldHitRaisedRelativeChildFirst() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode raised = document.div();
        ElementNode normal = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        raised.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(16))
                .setZIndex(2);
        normal.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20));
        root.append(raised).append(normal);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        Assert.assertSame(raised, DocumentHitTestEngine.hitTest(rootBox, null, 10, 22));
    }

    /**
     * 验证 positioned auto 元素在普通流元素上方命中。
     */
    @Test
    public void shouldHitPositionedAutoAboveNormalFlow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode positioned = document.div();
        ElementNode normal = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        positioned.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(20));
        normal.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20));
        root.append(positioned).append(normal);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        Assert.assertSame(positioned, DocumentHitTestEngine.hitTest(rootBox, null, 10, 22));
    }

    /**
     * 验证 absolute 子元素按脱流后的视觉位置参与命中。
     */
    @Test
    public void shouldHitAbsolutePositionedChildAtInsetPosition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode absolute = document.div();
        ElementNode normal = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        absolute.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(12))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(8))
                .setLeft(UiStyleLength.px(6));
        normal.style()
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(24));
        root.append(absolute).append(normal);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        Assert.assertSame(absolute, DocumentHitTestEngine.hitTest(rootBox, null, 10, 10));
        Assert.assertSame(normal, DocumentHitTestEngine.hitTest(rootBox, null, 10, 22));
    }

    /**
     * 验证 absolute 子元素相对最近 positioned ancestor 的位置参与命中。
     */
    @Test
    public void shouldHitAbsolutePositionedChildAgainstNearestPositionedAncestor() {
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

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 220, 0);

        Assert.assertSame(absolute, DocumentHitTestEngine.hitTest(rootBox, null, 21, 19));
        Assert.assertSame(staticParent, DocumentHitTestEngine.hitTest(rootBox, null, 34, 28));
    }

    /**
     * 验证负 z-index 元素在普通流元素下方命中。
     */
    @Test
    public void shouldHitNormalFlowAboveNegativeZIndex() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode negative = document.div();
        ElementNode normal = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        negative.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(20))
                .setZIndex(-1);
        normal.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20));
        root.append(negative).append(normal);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        Assert.assertSame(normal, DocumentHitTestEngine.hitTest(rootBox, null, 10, 22));
    }
}
