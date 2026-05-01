package club.heiqi.uilib.ui.animation;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * `DocumentAnimationTimeline` 的 paint/effect transition 契约测试。
 */
public class DocumentAnimationTimelineTest {

    /**
     * 验证动画属性会暴露 paint/effect/layout 分层分类。
     */
    @Test
    public void shouldExposeAnimationPropertyImpactClassification() {
        Assert.assertSame(DocumentAnimationImpact.PAINT, DocumentAnimationProperty.BACKGROUND_COLOR.getImpact());
        Assert.assertTrue(DocumentAnimationProperty.BORDER_RADIUS.isPaintOnly());
        Assert.assertSame(DocumentAnimationImpact.EFFECT, DocumentAnimationProperty.OPACITY.getImpact());
        Assert.assertTrue(DocumentAnimationProperty.BACKDROP_BLUR_RADIUS.isEffectAffecting());
    }

    /**
     * 验证颜色 transition 会基于 computed style 变化创建动画覆盖，不污染 inline style。
     */
    @Test
    public void shouldTransitionBackgroundColorWithoutMutatingInlineStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setTransition(DocumentAnimationProperty.BACKGROUND_COLOR, 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        DocumentLayoutBox firstLayout = DocumentLayoutEngine.layout(root, 80, 0);
        Assert.assertTrue(timeline.updateFromLayout(firstLayout, 0L));
        Assert.assertEquals(0xFF000000, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF000000, 0L));

        root.style().setBackgroundColor(0xFFFFFFFF);
        DocumentLayoutBox secondLayout = DocumentLayoutEngine.layout(root, 80, 0);
        Assert.assertTrue(timeline.updateFromLayout(secondLayout, 0L));

        Assert.assertEquals(1, timeline.getActiveAnimationCount(500_000_000L));
        Assert.assertEquals(0xFF808080, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFFFFFFFF, 500_000_000L));
        Assert.assertEquals(Integer.valueOf(0xFFFFFFFF), root.style().getBackgroundColor());
        Assert.assertEquals(0xFFFFFFFF, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFFFFFFFF, 1_000_000_000L));
        Assert.assertTrue(timeline.pruneFinishedAnimations(1_000_000_000L));
        Assert.assertFalse(timeline.hasAnimationWork());
    }

    /**
     * 验证 opacity transition 会作为数值动画覆盖层插值。
     */
    @Test
    public void shouldTransitionOpacityWithoutMutatingInlineStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(1.0F)
                .setTransition(DocumentAnimationProperty.OPACITY, 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        DocumentLayoutBox firstLayout = DocumentLayoutEngine.layout(root, 80, 0);
        Assert.assertTrue(timeline.updateFromLayout(firstLayout, 0L));
        Assert.assertEquals(1.0F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 1.0F, 0L), 0.0F);

        root.style().setOpacity(0.25F);
        DocumentLayoutBox secondLayout = DocumentLayoutEngine.layout(root, 80, 0);
        Assert.assertTrue(timeline.updateFromLayout(secondLayout, 0L));

        Assert.assertEquals(1, timeline.getActiveAnimationCount(500_000_000L));
        Assert.assertEquals(0.625F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.25F,
                500_000_000L), 0.0F);
        Assert.assertEquals(Float.valueOf(0.25F), root.style().getOpacity());
        Assert.assertEquals(0.25F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.25F,
                1_000_000_000L), 0.0F);
        Assert.assertTrue(timeline.pruneFinishedAnimations(1_000_000_000L));
        Assert.assertFalse(timeline.hasAnimationWork());
    }

    /**
     * 验证 border-radius transition 会作为 paint-only 数值覆盖层插值。
     */
    @Test
    public void shouldTransitionBorderRadiusWithoutMutatingInlineStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setBorderRadius(UiStyleLength.px(0))
                .setTransition(DocumentAnimationProperty.BORDER_RADIUS, 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        DocumentLayoutBox firstLayout = DocumentLayoutEngine.layout(root, 120, 0);
        Assert.assertTrue(timeline.updateFromLayout(firstLayout, 0L));
        Assert.assertEquals(0.0F, timeline.resolveFloat(root, DocumentAnimationProperty.BORDER_RADIUS, 0.0F, 0L),
                0.0F);

        root.style().setBorderRadius(UiStyleLength.px(20));
        DocumentLayoutBox secondLayout = DocumentLayoutEngine.layout(root, 120, 0);
        Assert.assertTrue(timeline.updateFromLayout(secondLayout, 0L));

        Assert.assertEquals(1, timeline.getActiveAnimationCount(500_000_000L));
        Assert.assertEquals(10.0F, timeline.resolveFloat(root, DocumentAnimationProperty.BORDER_RADIUS, 20.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(UiStyleLength.px(20), root.style().getBorderRadius());
        Assert.assertEquals(20.0F, timeline.resolveFloat(root, DocumentAnimationProperty.BORDER_RADIUS, 20.0F,
                1_000_000_000L), 0.0F);
        Assert.assertTrue(timeline.pruneFinishedAnimations(1_000_000_000L));
        Assert.assertFalse(timeline.hasAnimationWork());
    }

    /**
     * 验证 effect-affecting 长度类 backdrop blur transition 会按运行值插值。
     */
    @Test
    public void shouldTransitionBackdropBlurRadiusWithoutMutatingInlineStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setBackdropBlurRadius(UiStyleLength.px(4))
                .setTransition(DocumentAnimationProperty.BACKDROP_BLUR_RADIUS, 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        DocumentLayoutBox firstLayout = DocumentLayoutEngine.layout(root, 120, 0);
        Assert.assertTrue(timeline.updateFromLayout(firstLayout, 0L));
        Assert.assertEquals(4.0F, timeline.resolveFloat(root, DocumentAnimationProperty.BACKDROP_BLUR_RADIUS, 4.0F,
                0L), 0.0F);

        root.style().setBackdropBlurRadius(UiStyleLength.px(20));
        DocumentLayoutBox secondLayout = DocumentLayoutEngine.layout(root, 120, 0);
        Assert.assertTrue(timeline.updateFromLayout(secondLayout, 0L));

        Assert.assertTrue(timeline.hasAnimationWork(DocumentAnimationImpact.EFFECT));
        Assert.assertEquals(1, timeline.getActiveAnimationCount(500_000_000L));
        Assert.assertEquals(12.0F, timeline.resolveFloat(root, DocumentAnimationProperty.BACKDROP_BLUR_RADIUS, 20.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(UiStyleLength.px(20), root.style().getBackdropBlurRadius());
        Assert.assertEquals(20.0F, timeline.resolveFloat(root, DocumentAnimationProperty.BACKDROP_BLUR_RADIUS, 20.0F,
                1_000_000_000L), 0.0F);
    }

    /**
     * 验证 transition delay 与 timing function 会影响运行值进度。
     */
    @Test
    public void shouldHonorDelayAndTimingFunction() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(1.0F)
                .setTransition(DocumentAnimationProperty.OPACITY, 1000L)
                .setTransitionDelayMillis(250L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_IN);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        root.style().setOpacity(0.0F);
        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);

        Assert.assertEquals(1.0F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.0F,
                200_000_000L), 0.0F);
        Assert.assertEquals(0.75F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.0F,
                750_000_000L), 0.0F);
    }

    /**
     * 验证作者侧关闭 transition 后，运行中的动画会立即取消并回到 computed style 值。
     */
    @Test
    public void shouldCancelRunningTransitionWhenDeclarationNoLongerAllowsIt() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(1.0F)
                .setTransition(DocumentAnimationProperty.OPACITY, 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        root.style().setOpacity(0.0F);
        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        Assert.assertEquals(0.5F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.0F,
                500_000_000L), 0.0F);

        root.style().setTransitionDurationMillis(0L);
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 500_000_000L));

        Assert.assertFalse(timeline.hasAnimationWork());
        Assert.assertEquals(0.0F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.0F,
                500_000_000L), 0.0F);
    }
}
