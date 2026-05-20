package club.heiqi.uilib.ui.layout;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `DocumentEffectChain` 的效果链契约测试。
 */
public class DocumentEffectChainTest {

    /**
     * 验证 paint context、backdrop 与 overflow clip 会按绘制顺序进入效果链。
     */
    @Test
    public void shouldResolvePaintEffectOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        ElementNode content = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackdropBlurRadius(UiStyleLength.px(12))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        content.style().setHeight(UiStyleLength.px(40));
        child.append(content);
        root.append(child);

        DocumentEffectChain effectChain = DocumentEffectChain.resolve(DocumentLayoutEngine.layout(root, 120, 0)
                .getChildren().get(0));

        Assert.assertEquals(Arrays.asList(DocumentEffectType.BACKDROP_FILTER, DocumentEffectType.OVERFLOW_CLIP),
                effectChain.getStaticEffects());
        Assert.assertEquals(Arrays.asList(DocumentEffectType.PAINT_CONTEXT, DocumentEffectType.BACKDROP_FILTER,
                DocumentEffectType.OVERFLOW_CLIP), effectChain.getPaintEffects(false, 0.5F));
        Assert.assertTrue(effectChain.createsStackingContext());
        Assert.assertTrue(effectChain.isStackingBoundary());
    }

    /**
     * 验证 overflow clip 的裁剪几何以 padding box 为边界，且 visible 轴保持开放。
     */
    @Test
    public void shouldResolveAxisAwareChildClipBounds() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(30))
                .setBorderWidth(UiStyleLength.px(2))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.HIDDEN);
        child.style().setHeight(UiStyleLength.px(40));
        root.append(child);

        DocumentEffectChain effectChain = DocumentEffectChain.resolve(DocumentLayoutEngine.layout(root, 100, 0));
        DocumentEffectChain.ClipBounds clipBounds = effectChain.resolveChildClipBounds(7, 11);

        Assert.assertTrue(effectChain.clipsChildren());
        Assert.assertTrue(clipBounds.getLeft() < -1000000);
        Assert.assertTrue(clipBounds.getRight() > 1000000);
        Assert.assertEquals(13, clipBounds.getTop());
        Assert.assertEquals(43, clipBounds.getBottom());
        Assert.assertTrue(effectChain.canReachChildrenAt(-5000, 20, 7, 11));
        Assert.assertFalse(effectChain.canReachChildrenAt(10, 44, 7, 11));
    }

    /**
     * 验证 positioned z-index 和 opacity 均会建立 stacking boundary。
     */
    @Test
    public void shouldDetectStackingBoundaries() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode positioned = document.div();
        ElementNode transparent = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        positioned.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.RELATIVE)
                .setZIndex(1);
        transparent.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setOpacity(0.5F);
        root.append(positioned).append(transparent);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        Assert.assertTrue(DocumentEffectChain.resolve(rootBox.getChildren().get(0)).isStackingBoundary());
        Assert.assertTrue(DocumentEffectChain.resolve(rootBox.getChildren().get(1)).isStackingBoundary());
    }
}
