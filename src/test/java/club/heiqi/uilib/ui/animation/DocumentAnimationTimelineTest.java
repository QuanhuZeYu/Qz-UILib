package club.heiqi.uilib.ui.animation;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.style.UiStyleDeclaration;
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
        Assert.assertTrue(timeline.hasRunningTransition(root, DocumentAnimationProperty.OPACITY));
        Assert.assertEquals(0.5F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.0F,
                500_000_000L), 0.0F);

        root.style().setTransitionDurationMillis(0L);
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 500_000_000L));

        Assert.assertFalse(timeline.hasAnimationWork());
        Assert.assertFalse(timeline.hasRunningTransition(root, DocumentAnimationProperty.OPACITY));
        Assert.assertEquals(0.0F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.0F,
                500_000_000L), 0.0F);
    }

    /**
     * 验证 keyframe animation 运行值位于 computed style 之上，transition 运行值位于 keyframe 之上。
     */
    @Test
    public void shouldResolveTransitionAboveKeyframeAnimationAndComputedStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(1.0F)
                .setTransition(DocumentAnimationProperty.OPACITY, 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        timeline.setFloatKeyframeAnimation(root, DocumentAnimationProperty.OPACITY, 1.0F, 0.0F, 0L,
                1_000_000_000L);

        Assert.assertTrue(timeline.hasAnimationWork(DocumentAnimationImpact.EFFECT));
        Assert.assertFalse(timeline.hasRunningTransition(root, DocumentAnimationProperty.OPACITY));
        Assert.assertEquals(0.5F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 1.0F,
                500_000_000L), 0.0F);

        root.style().setOpacity(0.25F);
        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 500_000_000L);

        Assert.assertTrue(timeline.hasRunningTransition(root, DocumentAnimationProperty.OPACITY));
        Assert.assertEquals(0.4375F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.25F,
                750_000_000L), 0.0F);
        Assert.assertEquals(Float.valueOf(0.25F), root.style().getOpacity());
        Assert.assertTrue(timeline.pruneFinishedAnimations(1_500_000_000L));
        Assert.assertFalse(timeline.hasAnimationWork());
    }

    /**
     * 验证作者侧 animation 声明会启动命名 keyframes 覆盖层。
     */
    @Test
    public void shouldStartDeclaredKeyframeAnimationFromComputedStyle() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setFloat(DocumentAnimationProperty.OPACITY, 1.0F, 0.25F)
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233)
                .setOpacity(1.0F)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L));

        Assert.assertEquals(2, timeline.getActiveAnimationCount(500_000_000L));
        Assert.assertTrue(timeline.hasAnimationWork(DocumentAnimationImpact.PAINT));
        Assert.assertTrue(timeline.hasAnimationWork(DocumentAnimationImpact.EFFECT));
        Assert.assertEquals(0.625F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 1.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(0xFF808080, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF112233, 500_000_000L));
        Assert.assertEquals(Float.valueOf(1.0F), root.style().getOpacity());

        Assert.assertTrue(timeline.pruneFinishedAnimations(1_000_000_000L));
        Assert.assertEquals(0, timeline.getActiveAnimationCount(1_000_000_000L));
        Assert.assertFalse(timeline.hasAnimationWork());
        Assert.assertEquals(0.25F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 1.0F,
                1_000_000_000L), 0.0F);
        Assert.assertEquals(0xFFFFFFFF, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF112233, 1_000_000_000L));
    }

    /**
     * 验证声明式 keyframe 圆角会先归一化到布局约束，避免 999px 胶囊值在末尾跳变。
     */
    @Test
    public void shouldNormalizeDeclaredBorderRadiusKeyframeToLayoutBounds() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pillMorph")
                .setFloat(DocumentAnimationProperty.BORDER_RADIUS, 999.0F, 10.0F)
                .build());
        ElementNode root = document.getRootElement();
        ElementNode pill = document.div();
        ElementNode sibling = document.div();
        pill.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setAnimation("pillMorph", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20));
        root.append(pill).append(sibling);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);

        Assert.assertEquals(20.0F, timeline.resolveFloat(pill, DocumentAnimationProperty.BORDER_RADIUS, 0.0F,
                0L), 0.0F);
        Assert.assertEquals(15.0F, timeline.resolveFloat(pill, DocumentAnimationProperty.BORDER_RADIUS, 0.0F,
                500_000_000L), 0.0F);

        sibling.style().setBackgroundColor(0xFFFFFFFF);
        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 500_000_000L);

        Assert.assertEquals(15.0F, timeline.resolveFloat(pill, DocumentAnimationProperty.BORDER_RADIUS, 0.0F,
                500_000_000L), 0.0F);
    }

    /**
     * 验证声明式 keyframes 可按 0%/50%/100% 这类多段 stop 列表插值。
     */
    @Test
    public void shouldInterpolateDeclaredKeyframeStopList() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.0F, 0xFF000000)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.5F, 0xFFFFFFFF)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 1.0F, 0xFFFF0000)
                .setFloatStop(DocumentAnimationProperty.BORDER_RADIUS, 0.0F, 999.0F)
                .setFloatStop(DocumentAnimationProperty.BORDER_RADIUS, 0.5F, 20.0F)
                .setFloatStop(DocumentAnimationProperty.BORDER_RADIUS, 1.0F, 10.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setBackgroundColor(0xFF112233)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);

        Assert.assertEquals(0xFF808080, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF112233, 250_000_000L));
        Assert.assertEquals(0xFFFF8080, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF112233, 750_000_000L));
        Assert.assertEquals(20.0F, timeline.resolveFloat(root, DocumentAnimationProperty.BORDER_RADIUS, 0.0F,
                250_000_000L), 0.0F);
        Assert.assertEquals(15.0F, timeline.resolveFloat(root, DocumentAnimationProperty.BORDER_RADIUS, 0.0F,
                750_000_000L), 0.0F);
    }

    /**
     * 验证 animation delay、iteration、fill-mode 与 timing function 会影响 keyframes 生命周期。
     */
    @Test
    public void shouldHonorDeclaredKeyframeTimingAndFillMode() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setFloat(DocumentAnimationProperty.OPACITY, 1.0F, 0.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.5F)
                .setAnimation("pulse", 1000L)
                .setAnimationDelayMillis(250L)
                .setAnimationIterationCount(2)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.EASE_IN);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);

        Assert.assertEquals(1.0F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                200_000_000L), 0.0F);
        Assert.assertEquals(0.75F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                750_000_000L), 0.0F);
        Assert.assertEquals(0.75F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                1_750_000_000L), 0.0F);
        Assert.assertEquals(0.0F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                2_250_000_000L), 0.0F);
    }

    /**
     * 验证 animation duration 等声明变化会重启声明式 keyframe，而非沿用旧进度。
     */
    @Test
    public void shouldRestartDeclaredKeyframeAnimationWhenTimingDeclarationChanges() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setFloat(DocumentAnimationProperty.OPACITY, 1.0F, 0.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.5F)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        Assert.assertEquals(0.5F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                500_000_000L), 0.0F);

        root.style().setAnimationDurationMillis(2000L);
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 500_000_000L));

        Assert.assertEquals(1.0F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                500_000_000L), 0.0F);
        Assert.assertEquals(0.5F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                1_500_000_000L), 0.0F);
    }

    /**
     * 验证布局尺寸变化不会把声明式 keyframe 从头重启，只会刷新 used value 归一化边界。
     */
    @Test
    public void shouldPreserveDeclaredKeyframeProgressWhenLayoutSizeChanges() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pillMorph")
                .setFloat(DocumentAnimationProperty.BORDER_RADIUS, 0.0F, 999.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setAnimation("pillMorph", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);
        Assert.assertEquals(10.0F, timeline.resolveFloat(root, DocumentAnimationProperty.BORDER_RADIUS, 0.0F,
                500_000_000L), 0.0F);

        root.style().setHeight(UiStyleLength.px(80));
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 500_000_000L));

        Assert.assertEquals(20.0F, timeline.resolveFloat(root, DocumentAnimationProperty.BORDER_RADIUS, 0.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(40.0F, timeline.resolveFloat(root, DocumentAnimationProperty.BORDER_RADIUS, 0.0F,
                1_000_000_000L), 0.0F);
    }

    /**
     * 验证清除或失效 animation 声明会取消作者侧 keyframes 覆盖。
     */
    @Test
    public void shouldCancelDeclaredKeyframeAnimationWhenDeclarationChanges() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setFloat(DocumentAnimationProperty.OPACITY, 1.0F, 0.0F)
                .build());
        ElementNode root = document.getRootElement();
        UiStyleDeclaration style = root.style();
        style.setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.5F)
                .setAnimation("pulse", 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        Assert.assertEquals(0.5F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                500_000_000L), 0.0F);

        style.clearAnimationName();
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 500_000_000L));

        Assert.assertFalse(timeline.hasAnimationWork());
        Assert.assertEquals(0.5F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                500_000_000L), 0.0F);
    }

    /**
     * 验证同名 keyframes 定义替换会重启引用它的声明式动画并使用新轨道。
     */
    @Test
    public void shouldRestartDeclaredKeyframeAnimationWhenRegisteredKeyframesDefinitionChanges() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setFloat(DocumentAnimationProperty.OPACITY, 1.0F, 0.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.5F)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        Assert.assertEquals(0.5F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                500_000_000L), 0.0F);

        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setFloat(DocumentAnimationProperty.OPACITY, 0.25F, 0.75F)
                .build());
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 500_000_000L));

        Assert.assertEquals(0.25F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                500_000_000L), 0.0F);
        Assert.assertEquals(0.5F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                1_000_000_000L), 0.0F);
    }

    /**
     * 验证移除 keyframes 定义会取消引用它的动画并清理旧 forwards fill。
     */
    @Test
    public void shouldCancelDeclaredKeyframeAnimationAndFillWhenRegisteredKeyframesDefinitionIsRemoved() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        timeline.pruneFinishedAnimations(1_000_000_000L);
        Assert.assertEquals(0xFFFFFFFF, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF112233, 1_000_000_000L));

        document.unregisterKeyframes("pulse");
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 1_000_000_000L));

        Assert.assertFalse(timeline.hasAnimationWork());
        Assert.assertEquals(0xFF112233, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF112233, 1_000_000_000L));
    }

    /**
     * 验证作者侧目标值变化后，同属性 keyframe fill 不会在后续重绘时重新覆盖 transition 结果。
     */
    @Test
    public void shouldSuppressKeyframeFillWhenAuthorTargetChangesSameProperty() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setFloat(DocumentAnimationProperty.BORDER_RADIUS, 999.0F, 12.0F)
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF38A169, 0xFF805AD5)
                .build());
        ElementNode root = document.getRootElement();
        ElementNode pill = document.div();
        ElementNode sibling = document.div();
        pill.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setBackgroundColor(0xFF38A169)
                .setBorderRadius(UiStyleLength.px(999))
                .setTransitionProperties(DocumentAnimationProperty.BACKGROUND_COLOR,
                        DocumentAnimationProperty.BORDER_RADIUS)
                .setTransitionDurationMillis(450L)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20));
        root.append(pill).append(sibling);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);
        timeline.pruneFinishedAnimations(1_000_000_000L);
        Assert.assertEquals(0xFF805AD5, timeline.resolveColor(pill, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF38A169, 1_000_000_000L));
        Assert.assertEquals(12.0F, timeline.resolveFloat(pill, DocumentAnimationProperty.BORDER_RADIUS, 20.0F,
                1_000_000_000L), 0.0F);

        pill.style()
                .setBackgroundColor(0xFF3182CE)
                .setBorderRadius(UiStyleLength.px(10));
        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 1_000_000_000L);
        timeline.pruneFinishedAnimations(1_450_000_000L);

        Assert.assertEquals(0xFF3182CE, timeline.resolveColor(pill, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF3182CE, 1_450_000_000L));
        Assert.assertEquals(10.0F, timeline.resolveFloat(pill, DocumentAnimationProperty.BORDER_RADIUS, 10.0F,
                1_450_000_000L), 0.0F);

        sibling.style().setBackgroundColor(0xFFFFFFFF);
        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 1_500_000_000L);

        Assert.assertEquals(0xFF3182CE, timeline.resolveColor(pill, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF3182CE, 1_500_000_000L));
        Assert.assertEquals(10.0F, timeline.resolveFloat(pill, DocumentAnimationProperty.BORDER_RADIUS, 10.0F,
                1_500_000_000L), 0.0F);
    }
}
