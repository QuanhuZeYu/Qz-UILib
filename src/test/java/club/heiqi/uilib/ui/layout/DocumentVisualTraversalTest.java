package club.heiqi.uilib.ui.layout;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.BoxContext;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.StackingContextResolver;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.TraversalEntry;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `DocumentVisualTraversal` 的浏览器语义契约测试。
 */
public class DocumentVisualTraversalTest {

    /**
     * 验证 overflow clip 祖先只提供裁剪链，不会把 positioned 后代变成独立 stacking context。
     */
    @Test
    public void shouldNotTreatOverflowClipAsStackingContextByItself() {
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
        BoxContext rootContext = DocumentVisualTraversal.resolveRootBoxContext(rootBox, null);
        List<TraversalEntry> positiveItems = DocumentVisualTraversal.collectStackingPhaseEntries(rootBox, rootContext,
                null, staticResolver(), DocumentStackingPhase.POSITIVE_POSITIONED);
        List<TraversalEntry> normalFlowItems = DocumentVisualTraversal.getNormalFlowEntries(rootBox, rootContext, null,
                staticResolver(), false);

        Assert.assertEquals(1, positiveItems.size());
        Assert.assertSame(raisedDescendant, positiveItems.get(0).getBoxContext().getBox().getElement());
        Assert.assertTrue(positiveItems.get(0).isStackingContext());
        Assert.assertEquals(2, normalFlowItems.size());
        Assert.assertSame(clippedParent, normalFlowItems.get(0).getBoxContext().getBox().getElement());
        Assert.assertSame(normalCover, normalFlowItems.get(1).getBoxContext().getBox().getElement());
    }

    /**
     * 验证 fixed 后代会脱离祖先 scroll/clip 链，改为以视口为基准解析视觉上下文。
     */
    @Test
    public void shouldResetScrollAndClipChainForFixedDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode spacer = document.div();
        ElementNode fixed = document.div();

        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(50))
                .setOverflowX(UiOverflow.HIDDEN)
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

        BoxContext rootContext = DocumentVisualTraversal.resolveRootBoxContext(rootBox, scrollState);
        BoxContext fixedContext = DocumentVisualTraversal.resolveChildBoxContext(rootContext, rootBox.getChildren().get(1),
                scrollState);

        Assert.assertEquals(0, fixedContext.getBoxOffsetX());
        Assert.assertEquals(0, fixedContext.getBoxOffsetY());
        Assert.assertEquals(10, fixedContext.getBox().getLeft() + fixedContext.getBoxOffsetX());
        Assert.assertEquals(6, fixedContext.getBox().getTop() + fixedContext.getBoxOffsetY());
        Assert.assertTrue(fixedContext.getClipChain().isEmpty());
        Assert.assertTrue(fixedContext.getChildClipChain().isEmpty());
    }

    private static StackingContextResolver staticResolver() {
        return new StackingContextResolver() {
            @Override
            public boolean createsStackingContext(DocumentLayoutBox box) {
                return DocumentEffectChain.resolve(box).createsStackingContext();
            }
        };
    }
}
