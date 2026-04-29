package club.heiqi.uilib.ui.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * `DocumentScrollState` 的 HTML-like 滚动命中契约。
 */
public class DocumentScrollStateTest {

    /**
     * 验证 positioned 后代可越过非 stacking context 祖先接收滚轮。
     */
    @Test
    public void shouldScrollPositionedDescendantInNearestStackingContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        ElementNode raisedScroller = document.div();
        ElementNode raisedContent = document.div();
        ElementNode normalCover = document.div();
        ElementNode coverContent = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        parent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        configureScroller(raisedScroller);
        raisedScroller.style()
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(5);
        raisedContent.style().setHeight(UiStyleLength.px(80));
        configureScroller(normalCover);
        coverContent.style().setHeight(UiStyleLength.px(80));
        raisedScroller.append(raisedContent);
        normalCover.append(coverContent);
        parent.append(raisedScroller);
        root.append(parent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);
        DocumentScrollState scrollState = new DocumentScrollState();

        Assert.assertTrue(scrollState.handleWheel(rootBox, 10, 22, -120, 1L));
        Assert.assertTrue(scrollState.getScrollTop(raisedScroller) > 0);
        Assert.assertEquals(0, scrollState.getScrollTop(normalCover));
    }

    /**
     * 验证 stacking context 祖先会阻止高 z-index 后代抢占外部 sibling 的滚轮命中。
     */
    @Test
    public void shouldScrollExternalSiblingAboveIsolatedPositionedDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode isolatedParent = document.div();
        ElementNode raisedScroller = document.div();
        ElementNode raisedContent = document.div();
        ElementNode normalCover = document.div();
        ElementNode coverContent = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        isolatedParent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.98F);
        configureScroller(raisedScroller);
        raisedScroller.style()
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(99);
        raisedContent.style().setHeight(UiStyleLength.px(80));
        configureScroller(normalCover);
        coverContent.style().setHeight(UiStyleLength.px(80));
        raisedScroller.append(raisedContent);
        normalCover.append(coverContent);
        isolatedParent.append(raisedScroller);
        root.append(isolatedParent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);
        DocumentScrollState scrollState = new DocumentScrollState();

        Assert.assertTrue(scrollState.handleWheel(rootBox, 10, 22, -120, 1L));
        Assert.assertEquals(0, scrollState.getScrollTop(raisedScroller));
        Assert.assertTrue(scrollState.getScrollTop(normalCover) > 0);
    }

    /**
     * 验证 overflow clip effect boundary 会阻止越界高 z-index scroller 抢占滚轮。
     */
    @Test
    public void shouldScrollExternalSiblingAboveClippedPositionedDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode clippedParent = document.div();
        ElementNode raisedScroller = document.div();
        ElementNode raisedContent = document.div();
        ElementNode normalCover = document.div();
        ElementNode coverContent = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        clippedParent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        configureScroller(raisedScroller);
        raisedScroller.style()
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(99);
        raisedContent.style().setHeight(UiStyleLength.px(80));
        configureScroller(normalCover);
        coverContent.style().setHeight(UiStyleLength.px(80));
        raisedScroller.append(raisedContent);
        normalCover.append(coverContent);
        clippedParent.append(raisedScroller);
        root.append(clippedParent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);
        DocumentScrollState scrollState = new DocumentScrollState();

        Assert.assertTrue(scrollState.handleWheel(rootBox, 10, 22, -120, 1L));
        Assert.assertEquals(0, scrollState.getScrollTop(raisedScroller));
        Assert.assertTrue(scrollState.getScrollTop(normalCover) > 0);
    }

    /**
     * 验证 fixed 滚动容器在根滚动后仍按视口固定位置接收滚轮。
     */
    @Test
    public void shouldScrollFixedDescendantAtViewportPositionAfterRootScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode spacer = document.div();
        ElementNode fixedScroller = document.div();
        ElementNode fixedContent = document.div();

        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(50))
                .setOverflowY(UiOverflow.AUTO);
        spacer.style().setHeight(UiStyleLength.px(140));
        configureScroller(fixedScroller);
        fixedScroller.style()
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(5))
                .setLeft(UiStyleLength.px(5));
        fixedContent.style().setHeight(UiStyleLength.px(80));
        fixedScroller.append(fixedContent);
        root.append(spacer).append(fixedScroller);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 100, 50);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        Assert.assertTrue(scrollState.setScrollOffset(root, 0, 36));

        Assert.assertTrue(scrollState.handleWheel(rootBox, 10, 10, -120, 1L));
        Assert.assertEquals(36, scrollState.getScrollTop(root));
        Assert.assertTrue(scrollState.getScrollTop(fixedScroller) > 0);
    }

    private static void configureScroller(ElementNode scroller) {
        scroller.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
    }
}
