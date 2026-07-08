package club.heiqi.uilib.ui.layout;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.BoxContext;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.BoxLocation;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.RootEntry;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.StackingContextResolver;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.TraversalEntry;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.VisualScene;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.base.values.UiTransform;

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

    /**
     * 验证 transform 祖先会成为 fixed containing block，fixed 后代不再清空该祖先 clip 链。
     */
    @Test
    public void shouldKeepFixedDescendantInTransformedAncestorClipChain() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode transformedClip = document.div();
        ElementNode staticWrapper = document.div();
        ElementNode fixed = document.div();

        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(80));
        transformedClip.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(40))
                .setMarginLeft(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTransform(UiTransform.translate(0.01F, 0.0F));
        staticWrapper.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setMarginLeft(UiStyleLength.px(7))
                .setMarginTop(UiStyleLength.px(9));
        fixed.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(5))
                .setLeft(UiStyleLength.px(10));
        staticWrapper.append(fixed);
        transformedClip.append(staticWrapper);
        root.append(transformedClip);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 80);
        BoxContext rootContext = DocumentVisualTraversal.resolveRootBoxContext(rootBox, null);
        BoxContext transformedContext = DocumentVisualTraversal.resolveChildBoxContext(rootContext,
                rootBox.getChildren().get(0), null);
        BoxContext wrapperContext = DocumentVisualTraversal.resolveChildBoxContext(transformedContext,
                transformedContext.getBox().getChildren().get(0), null);
        BoxContext fixedContext = DocumentVisualTraversal.resolveChildBoxContext(wrapperContext,
                wrapperContext.getBox().getChildren().get(0), null);

        Assert.assertEquals(1, fixedContext.getClipChain().size());
        Assert.assertSame(transformedClip, fixedContext.getClipChain().get(0).getBox().getElement());
        Assert.assertEquals(30, fixedContext.getBox().getLeft() + fixedContext.getBoxOffsetX());
        Assert.assertEquals(5, fixedContext.getBox().getTop() + fixedContext.getBoxOffsetY());
    }

    /**
     * 验证运行态 transform 祖先也会成为 fixed containing block 并保留 overflow clip 链。
     */
    @Test
    public void shouldKeepFixedDescendantInRuntimeTransformedAncestorClipChain() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode runtimeClip = document.div();
        ElementNode staticWrapper = document.div();
        ElementNode fixed = document.div();

        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(80));
        runtimeClip.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(40))
                .setMarginLeft(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        staticWrapper.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        fixed.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(5))
                .setLeft(UiStyleLength.px(40));
        staticWrapper.append(fixed);
        runtimeClip.append(staticWrapper);
        root.append(runtimeClip);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();
        timeline.setFloatKeyframeAnimation(runtimeClip, DocumentAnimationProperty.TRANSLATE_X, 0.0F, 40.0F,
                0L, 1_000_000_000L);
        long halfTimeNanos = 500_000_000L;

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 80,
                club.heiqi.uilib.ui.text.DefaultTextMeasureService.getInstance(),
                runtimeTransformResolver(timeline, halfTimeNanos));
        BoxContext rootContext = DocumentVisualTraversal.resolveRootBoxContext(rootBox, null, halfTimeNanos,
                timeline);
        BoxContext runtimeClipContext = DocumentVisualTraversal.resolveChildBoxContext(rootContext,
                rootBox.getChildren().get(0), null);
        BoxContext wrapperContext = DocumentVisualTraversal.resolveChildBoxContext(runtimeClipContext,
                runtimeClipContext.getBox().getChildren().get(0), null);
        BoxContext fixedContext = DocumentVisualTraversal.resolveChildBoxContext(wrapperContext,
                wrapperContext.getBox().getChildren().get(0), null);

        Assert.assertEquals(1, fixedContext.getClipChain().size());
        Assert.assertSame(runtimeClip, fixedContext.getClipChain().get(0).getBox().getElement());
        Assert.assertEquals(60, fixedContext.getBox().getLeft() + fixedContext.getBoxOffsetX());
        Assert.assertTrue(DocumentVisualTraversal.isPointInsideClipChain(fixedContext, 62.0F, 8.0F));
        Assert.assertFalse(DocumentVisualTraversal.isPointInsideClipChain(fixedContext, 72.0F, 8.0F));
    }

    /**
     * 验证共享视觉场景会把普通树放在底部，把后注册 top-layer 放在更上层。
     */
    @Test
    public void shouldExposeRootEntriesInDocumentThenTopLayerOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode firstTopLayer = document.div();
        ElementNode secondTopLayer = document.div();

        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(80));
        firstTopLayer.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(4))
                .setTop(UiStyleLength.px(6))
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10));
        secondTopLayer.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(8))
                .setTop(UiStyleLength.px(12))
                .setWidth(UiStyleLength.px(24))
                .setHeight(UiStyleLength.px(12));
        root.append(firstTopLayer).append(secondTopLayer);
        document.__showTopLayerElement(firstTopLayer);
        document.__showTopLayerElement(secondTopLayer);

        VisualScene scene = DocumentVisualTraversal.resolveVisualScene(DocumentLayoutEngine.layout(root, 120, 80),
                java.util.Arrays.asList(
                        DocumentLayoutEngine.layoutTopLayerElement(firstTopLayer, 120, 80,
                                club.heiqi.uilib.ui.text.DefaultTextMeasureService.getInstance(), null),
                        DocumentLayoutEngine.layoutTopLayerElement(secondTopLayer, 120, 80,
                                club.heiqi.uilib.ui.text.DefaultTextMeasureService.getInstance(), null)),
                null);

        Assert.assertEquals(3, scene.getRootEntries().size());
        RootEntry documentEntry = scene.getRootEntries().get(0);
        RootEntry firstTopLayerEntry = scene.getRootEntries().get(1);
        RootEntry secondTopLayerEntry = scene.getRootEntries().get(2);
        Assert.assertFalse(documentEntry.isTopLayer());
        Assert.assertSame(root, documentEntry.getRootBox().getElement());
        Assert.assertTrue(firstTopLayerEntry.isTopLayer());
        Assert.assertSame(firstTopLayer, firstTopLayerEntry.getRootBox().getElement());
        Assert.assertTrue(secondTopLayerEntry.isTopLayer());
        Assert.assertSame(secondTopLayer, secondTopLayerEntry.getRootBox().getElement());
    }

    /**
     * 验证共享定位查询会返回 top-layer 根盒的视口坐标。
     */
    @Test
    public void shouldFindTopLayerElementLocationThroughSharedTraversal() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode anchor = document.div();
        ElementNode popup = document.div();

        root.style().setWidth(UiStyleLength.px(140)).setHeight(UiStyleLength.px(100));
        anchor.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(16));
        popup.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(18))
                .setTop(UiStyleLength.px(30))
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20));
        root.append(anchor).append(popup);
        document.__showTopLayerElement(popup);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 100);
        List<DocumentLayoutBox> topLayerBoxes = java.util.Collections.singletonList(
                DocumentLayoutEngine.layoutTopLayerElement(popup, 140, 100,
                        club.heiqi.uilib.ui.text.DefaultTextMeasureService.getInstance(), null));

        BoxLocation location = DocumentVisualTraversal.findBoxLocation(rootBox, topLayerBoxes, null, popup);

        Assert.assertNotNull(location);
        Assert.assertTrue(location.isTopLayer());
        Assert.assertSame(popup, location.getBoxContext().getBox().getElement());
        Assert.assertEquals(18, location.getBoxContext().getBox().getLeft() + location.getBoxContext().getBoxOffsetX());
        Assert.assertEquals(30, location.getBoxContext().getBox().getTop() + location.getBoxContext().getBoxOffsetY());
    }

    private static StackingContextResolver staticResolver() {
        return new StackingContextResolver() {
            @Override
            public boolean createsStackingContext(DocumentLayoutBox box) {
                return DocumentEffectChain.resolve(box).createsStackingContext();
            }
        };
    }

    private static DocumentLayoutEngine.LayoutRuntimeValueResolver runtimeTransformResolver(
            final DocumentAnimationTimeline timeline, final long currentTimeNanos) {
        return new DocumentLayoutEngine.LayoutRuntimeValueResolver() {
            @Override
            public int resolve(ElementNode element, DocumentAnimationProperty property, int baseValue) {
                return baseValue;
            }

            @Override
            public boolean createsFixedContainingBlock(ElementNode element, ComputedStyle computedStyle) {
                return DocumentRuntimeTransforms.createsFixedContainingBlock(element, computedStyle,
                        currentTimeNanos, timeline);
            }
        };
    }
}
