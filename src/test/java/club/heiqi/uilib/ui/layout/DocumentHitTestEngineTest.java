package club.heiqi.uilib.ui.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.UiOverflow;
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

        assertHitElement(raised, rootBox, 10, 22);
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

        assertHitElement(positioned, rootBox, 10, 22);
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

        assertHitElement(absolute, rootBox, 10, 10);
        assertHitElement(normal, rootBox, 10, 22);
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

        assertHitElement(absolute, rootBox, 21, 19);
        assertHitElement(staticParent, rootBox, 34, 28);
    }

    /**
     * 验证 fixed 元素在根滚动后仍按视口固定位置参与命中。
     */
    @Test
    public void shouldHitFixedPositionedChildAfterRootScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode spacer = document.div();
        ElementNode fixed = document.div();

        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(50))
                .setOverflowY(UiOverflow.AUTO);
        spacer.style().setHeight(UiStyleLength.px(140));
        fixed.style()
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(12))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(6))
                .setLeft(UiStyleLength.px(10));
        root.append(spacer).append(fixed);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 100, 50);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        Assert.assertTrue(scrollState.setScrollOffset(root, 0, 36));

        assertHitElement(fixed, rootBox, scrollState, 12, 8);
        assertHitElement(spacer, rootBox, scrollState, 12, 24);
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

        assertHitElement(normal, rootBox, 10, 22);
    }

    /**
     * 验证 positioned 后代可越过非 stacking context 祖先参与最近上下文命中排序。
     */
    @Test
    public void shouldHitPositionedDescendantInNearestStackingContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        ElementNode raisedDescendant = document.div();
        ElementNode normalCover = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        parent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        raisedDescendant.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(5);
        normalCover.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        parent.append(raisedDescendant);
        root.append(parent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);

        assertHitElement(raisedDescendant, rootBox, 10, 22);
    }

    /**
     * 验证 stacking context 祖先会阻止高 z-index 后代逃出上下文。
     */
    @Test
    public void shouldHitExternalSiblingAboveIsolatedPositionedDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode isolatedParent = document.div();
        ElementNode raisedDescendant = document.div();
        ElementNode normalCover = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        isolatedParent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.98F);
        raisedDescendant.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(99);
        normalCover.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        isolatedParent.append(raisedDescendant);
        root.append(isolatedParent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);

        assertHitElement(normalCover, rootBox, 10, 22);
    }

    /**
     * 验证 overflow clip effect boundary 会阻止高 z-index 后代越界命中。
     */
    @Test
    public void shouldHitExternalSiblingAboveClippedPositionedDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode clippedParent = document.div();
        ElementNode raisedDescendant = document.div();
        ElementNode normalCover = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        clippedParent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        raisedDescendant.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(99);
        normalCover.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        clippedParent.append(raisedDescendant);
        root.append(clippedParent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);

        assertHitElement(normalCover, rootBox, 10, 22);
    }

    private static void assertHitElement(ElementNode expectedElement, DocumentLayoutBox rootBox, int x, int y) {
        ElementNode actualElement = DocumentHitTestEngine.hitTest(rootBox, null, x, y);
        Assert.assertNotNull(actualElement);
        Assert.assertEquals(expectedElement.__getElementUid(), actualElement.__getElementUid());
    }

    private static void assertHitElement(ElementNode expectedElement, DocumentLayoutBox rootBox,
            DocumentScrollState scrollState, int x, int y) {
        ElementNode actualElement = DocumentHitTestEngine.hitTest(rootBox, scrollState, x, y);
        Assert.assertNotNull(actualElement);
        Assert.assertEquals(expectedElement.__getElementUid(), actualElement.__getElementUid());
    }
}
