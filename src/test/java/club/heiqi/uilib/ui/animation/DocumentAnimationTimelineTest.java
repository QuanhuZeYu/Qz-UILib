package club.heiqi.uilib.ui.animation;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.style.props.UiAnimationDirection;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;
import club.heiqi.uilib.ui.text.TextMeasureService;

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
        Assert.assertSame(DocumentAnimationProperty.ValueType.COLOR,
                DocumentAnimationProperty.BACKGROUND_COLOR.getValueType());
        Assert.assertTrue(DocumentAnimationProperty.BACKGROUND_COLOR.isColorValue());
        Assert.assertTrue(DocumentAnimationProperty.BORDER_RADIUS.isPaintOnly());
        Assert.assertTrue(DocumentAnimationProperty.BORDER_RADIUS.isFloatValue());
        Assert.assertTrue(DocumentAnimationProperty.BOX_SHADOW_COLOR.isPaintOnly());
        Assert.assertTrue(DocumentAnimationProperty.BOX_SHADOW_OFFSET_X.isPaintOnly());
        Assert.assertTrue(DocumentAnimationProperty.BOX_SHADOW_OFFSET_Y.isPaintOnly());
        Assert.assertTrue(DocumentAnimationProperty.BOX_SHADOW_BLUR_RADIUS.isPaintOnly());
        Assert.assertTrue(DocumentAnimationProperty.BOX_SHADOW_SPREAD_RADIUS.isPaintOnly());
        Assert.assertTrue(DocumentAnimationProperty.TRANSLATE_X.isPaintOnly());
        Assert.assertTrue(DocumentAnimationProperty.TRANSLATE_Y.isPaintOnly());
        Assert.assertTrue(DocumentAnimationProperty.SCALE_X.isPaintOnly());
        Assert.assertTrue(DocumentAnimationProperty.SCALE_Y.isPaintOnly());
        Assert.assertTrue(DocumentAnimationProperty.ROTATE.isPaintOnly());
        Assert.assertSame(DocumentAnimationImpact.EFFECT, DocumentAnimationProperty.OPACITY.getImpact());
        Assert.assertTrue(DocumentAnimationProperty.BACKDROP_BLUR_RADIUS.isEffectAffecting());
        Assert.assertSame(DocumentAnimationImpact.LAYOUT, DocumentAnimationProperty.WIDTH.getImpact());
        Assert.assertTrue(DocumentAnimationProperty.HEIGHT.isLayoutAffecting());
        Assert.assertTrue(DocumentAnimationProperty.TOP.isLayoutAffecting());
        Assert.assertTrue(DocumentAnimationProperty.RIGHT.isLayoutAffecting());
        Assert.assertTrue(DocumentAnimationProperty.BOTTOM.isLayoutAffecting());
        Assert.assertTrue(DocumentAnimationProperty.LEFT.isLayoutAffecting());
        Assert.assertTrue(DocumentAnimationProperty.MARGIN_LEFT.isLayoutAffecting());
        Assert.assertSame(DocumentAnimationImpact.LAYOUT, DocumentAnimationProperty.MARGIN_RIGHT.getImpact());
        Assert.assertTrue(DocumentAnimationProperty.PADDING_LEFT.isLayoutAffecting());
        Assert.assertSame(DocumentAnimationImpact.LAYOUT, DocumentAnimationProperty.PADDING_RIGHT.getImpact());
    }

    /**
     * 验证标准 cubic-bezier 缓动会按 X 轴反解曲线进度。
     */
    @Test
    public void shouldApplyStandardCubicBezierTimingFunction() {
        DocumentAnimationTimingFunction easeIn = DocumentAnimationTimingFunction.cubicBezier(0.42F, 0.0F,
                1.0F, 1.0F);

        Assert.assertEquals(0.0F, easeIn.apply(0.0F), 0.0F);
        Assert.assertEquals(1.0F, easeIn.apply(1.0F), 0.0F);
        Assert.assertEquals(0.315F, easeIn.apply(0.5F), 0.002F);
        Assert.assertTrue(easeIn.apply(0.5F) > 0.25F);
    }

    /**
     * 验证 transform 子属性 transition 会作为 paint-only 数值覆盖层插值。
     */
    @Test
    public void shouldTransitionTransformSubPropertiesWithoutMutatingInlineStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setTransform(UiTransform.identity())
                .setTransitionProperties(DocumentAnimationProperty.TRANSLATE_X, DocumentAnimationProperty.SCALE_X,
                        DocumentAnimationProperty.ROTATE)
                .setTransitionDurationMillis(1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        root.style().setTransform(UiTransform.of(40.0F, 0.0F, 2.0F, 1.0F, 90.0F));
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L));

        Assert.assertTrue(timeline.hasAnimationWork(DocumentAnimationImpact.PAINT));
        Assert.assertEquals(20.0F, timeline.resolveFloat(root, DocumentAnimationProperty.TRANSLATE_X, 40.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(1.5F, timeline.resolveFloat(root, DocumentAnimationProperty.SCALE_X, 2.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(45.0F, timeline.resolveFloat(root, DocumentAnimationProperty.ROTATE, 90.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(40.0F, root.style().getTransform().getTranslateX(), 0.0F);
    }

    /**
     * 验证 keyframe animation 的 reverse 方向会从末帧开始播放。
     */
    @Test
    public void shouldPlayReverseDirectionKeyframes() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("fade")
                .setFloat(DocumentAnimationProperty.OPACITY, 0.0F, 1.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setAnimation("fade", 1000L)
                .setAnimationDirection(UiAnimationDirection.REVERSE);

        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();
        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);

        Assert.assertEquals(1.0F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.0F, 0L), 0.0F);
        Assert.assertEquals(0.5F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.0F,
                500_000_000L), 0.001F);
        Assert.assertEquals(0.0F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.0F,
                1_000_000_000L), 0.0F);
    }

    /**
     * 验证 0 次迭代会被视为无限迭代，并且不会结束。
     */
    @Test
    public void shouldKeepInfiniteKeyframeAnimationRunning() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setFloat(DocumentAnimationProperty.OPACITY, 0.0F, 1.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setAnimation("pulse", 1000L)
                .setAnimationIterationCount(0);

        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();
        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);

        Assert.assertTrue(timeline.hasAnimationWork());
        Assert.assertEquals(0.5F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.0F,
                2_500_000_000L), 0.001F);
        Assert.assertFalse(timeline.pruneFinishedAnimations(5_000_000_000L));
        Assert.assertTrue(timeline.hasAnimationWork());
    }

    /**
     * 验证 top/left/right/bottom 的运行态布局值会通过 layout resolver 生效。
     */
    @Test
    public void shouldAnimatePositionInsetsThroughRuntimeLayoutResolver() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode leftTop = document.div();
        ElementNode rightBottom = document.div();

        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(100));
        leftTop.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(10))
                .setTop(UiStyleLength.px(8))
                .setTransitionProperties(DocumentAnimationProperty.LEFT, DocumentAnimationProperty.TOP)
                .setTransitionDurationMillis(1000L);
        rightBottom.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.ABSOLUTE)
                .setRight(UiStyleLength.px(10))
                .setBottom(UiStyleLength.px(12))
                .setTransitionProperties(DocumentAnimationProperty.RIGHT, DocumentAnimationProperty.BOTTOM)
                .setTransitionDurationMillis(1000L);
        root.append(leftTop).append(rightBottom);

        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();
        DocumentLayoutBox firstLayout = DocumentLayoutEngine.layout(root, 200, 100, new DeterministicTextMeasureService());
        timeline.updateFromLayout(firstLayout, 0L);

        leftTop.style().setLeft(UiStyleLength.px(30)).setTop(UiStyleLength.px(28));
        rightBottom.style().setRight(UiStyleLength.px(30)).setBottom(UiStyleLength.px(32));
        DocumentLayoutBox secondLayout = DocumentLayoutEngine.layout(root, 200, 100,
                new DeterministicTextMeasureService());
        Assert.assertTrue(timeline.updateFromLayout(secondLayout, 0L));

        DocumentLayoutBox runtimeLayout = DocumentLayoutEngine.layout(root, 200, 100,
                new DeterministicTextMeasureService(), new DocumentLayoutEngine.LayoutRuntimeValueResolver() {
                    @Override
                    public int resolve(ElementNode element, DocumentAnimationProperty property, int baseValue) {
                        return Math.round(timeline.resolveFloat(element, property, baseValue, 500_000_000L));
                    }
                });

        Assert.assertEquals(2, runtimeLayout.getChildren().size());
        DocumentLayoutBox runtimeLeftTop = runtimeLayout.getChildren().get(0);
        DocumentLayoutBox runtimeRightBottom = runtimeLayout.getChildren().get(1);
        Assert.assertEquals(20, runtimeLeftTop.getLeft());
        Assert.assertEquals(18, runtimeLeftTop.getTop());
        Assert.assertEquals(160, runtimeRightBottom.getLeft());
        Assert.assertEquals(68, runtimeRightBottom.getTop());
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
     * 验证 layout-affecting 的 width/height transition 会作为数值覆盖层插值。
     */
    @Test
    public void shouldTransitionWidthAndHeightWithoutMutatingInlineStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setTransitionProperties(DocumentAnimationProperty.WIDTH, DocumentAnimationProperty.HEIGHT)
                .setTransitionDurationMillis(1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        DocumentLayoutBox firstLayout = DocumentLayoutEngine.layout(root, 120, 0);
        Assert.assertTrue(timeline.updateFromLayout(firstLayout, 0L));
        Assert.assertEquals(40.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F, 0L), 0.0F);
        Assert.assertEquals(20.0F, timeline.resolveFloat(root, DocumentAnimationProperty.HEIGHT, 20.0F, 0L), 0.0F);

        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        DocumentLayoutBox secondLayout = DocumentLayoutEngine.layout(root, 120, 0);
        Assert.assertTrue(timeline.updateFromLayout(secondLayout, 0L));

        Assert.assertTrue(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(2, timeline.getActiveAnimationCount(500_000_000L));
        Assert.assertEquals(60.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 80.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(30.0F, timeline.resolveFloat(root, DocumentAnimationProperty.HEIGHT, 40.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(UiStyleLength.px(80), root.style().getWidth());
        Assert.assertEquals(UiStyleLength.px(40), root.style().getHeight());
    }

    /**
     * 验证 margin-left/right transition 会作为 layout-affecting 数值覆盖层插值。
     */
    @Test
    public void shouldTransitionMarginLeftAndRightWithoutMutatingInlineStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(4), UiStyleLength.px(0),
                        UiStyleLength.px(2)))
                .setTransitionProperties(DocumentAnimationProperty.MARGIN_LEFT,
                        DocumentAnimationProperty.MARGIN_RIGHT)
                .setTransitionDurationMillis(1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        DocumentLayoutBox firstLayout = DocumentLayoutEngine.layout(root, 120, 0);
        Assert.assertTrue(timeline.updateFromLayout(firstLayout, 0L));
        Assert.assertEquals(2.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_LEFT, 2.0F, 0L),
                0.0F);
        Assert.assertEquals(4.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_RIGHT, 4.0F, 0L),
                0.0F);

        root.style().setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(24), UiStyleLength.px(0),
                UiStyleLength.px(12)));
        DocumentLayoutBox secondLayout = DocumentLayoutEngine.layout(root, 120, 0);
        Assert.assertTrue(timeline.updateFromLayout(secondLayout, 0L));

        Assert.assertTrue(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(2, timeline.getActiveAnimationCount(500_000_000L));
        Assert.assertEquals(7.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_LEFT, 12.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(14.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_RIGHT, 24.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(24), UiStyleLength.px(0),
                UiStyleLength.px(12)), root.style().getMargin());
    }

    /**
     * 验证 margin-left/right 从百分比或 auto 进入像素值时不会创建首期 px-to-px transition。
     */
    @Test
    public void shouldNotTransitionMarginFromPercentOrAutoTarget() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.auto(), UiStyleLength.px(0),
                        UiStyleLength.percent(0.10F)))
                .setTransitionProperties(DocumentAnimationProperty.MARGIN_LEFT,
                        DocumentAnimationProperty.MARGIN_RIGHT)
                .setTransitionDurationMillis(1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 100, 0), 0L);
        root.style().setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(10), UiStyleLength.px(0),
                UiStyleLength.px(20)));
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 100, 0), 0L));

        Assert.assertFalse(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertFalse(timeline.hasRunningTransition(root, DocumentAnimationProperty.MARGIN_LEFT));
        Assert.assertFalse(timeline.hasRunningTransition(root, DocumentAnimationProperty.MARGIN_RIGHT));
        Assert.assertEquals(20.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_LEFT, 20.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(10.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_RIGHT, 10.0F,
                500_000_000L), 0.0F);
    }

    /**
     * 验证 padding-left/right transition 会作为 layout-affecting 数值覆盖层插值。
     */
    @Test
    public void shouldTransitionPaddingLeftAndRightWithoutMutatingInlineStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(4), UiStyleLength.px(0),
                        UiStyleLength.px(2)))
                .setTransitionProperties(DocumentAnimationProperty.PADDING_LEFT,
                        DocumentAnimationProperty.PADDING_RIGHT)
                .setTransitionDurationMillis(1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        DocumentLayoutBox firstLayout = DocumentLayoutEngine.layout(root, 120, 0);
        Assert.assertTrue(timeline.updateFromLayout(firstLayout, 0L));
        Assert.assertEquals(2.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_LEFT, 2.0F, 0L),
                0.0F);
        Assert.assertEquals(4.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_RIGHT, 4.0F, 0L),
                0.0F);

        root.style().setPadding(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(24), UiStyleLength.px(0),
                UiStyleLength.px(12)));
        DocumentLayoutBox secondLayout = DocumentLayoutEngine.layout(root, 120, 0);
        Assert.assertTrue(timeline.updateFromLayout(secondLayout, 0L));

        Assert.assertTrue(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(2, timeline.getActiveAnimationCount(500_000_000L));
        Assert.assertEquals(7.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_LEFT, 12.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(14.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_RIGHT, 24.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(24), UiStyleLength.px(0),
                UiStyleLength.px(12)), root.style().getPadding());
    }

    /**
     * 验证 padding-left/right 从百分比进入像素值时不会创建首期 px-to-px transition。
     */
    @Test
    public void shouldNotTransitionPaddingFromPercentTarget() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.percent(0.10F), UiStyleLength.px(0),
                        UiStyleLength.percent(0.20F)))
                .setTransitionProperties(DocumentAnimationProperty.PADDING_LEFT,
                        DocumentAnimationProperty.PADDING_RIGHT)
                .setTransitionDurationMillis(1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 100, 0), 0L);
        root.style().setPadding(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(10), UiStyleLength.px(0),
                UiStyleLength.px(20)));
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 100, 0), 0L));

        Assert.assertFalse(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertFalse(timeline.hasRunningTransition(root, DocumentAnimationProperty.PADDING_LEFT));
        Assert.assertFalse(timeline.hasRunningTransition(root, DocumentAnimationProperty.PADDING_RIGHT));
        Assert.assertEquals(20.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_LEFT, 20.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(10.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_RIGHT, 10.0F,
                500_000_000L), 0.0F);
    }

    /**
     * 验证 width/height keyframe 会作为 layout-affecting 数值覆盖层插值。
     */
    @Test
    public void shouldRunWidthAndHeightKeyframeAsLayoutRuntimeValues() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("grow")
                .setFloat(DocumentAnimationProperty.WIDTH, 40.0F, 80.0F)
                .setFloat(DocumentAnimationProperty.HEIGHT, 20.0F, 40.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setAnimation("grow", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L));

        Assert.assertTrue(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(60.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(30.0F, timeline.resolveFloat(root, DocumentAnimationProperty.HEIGHT, 20.0F,
                500_000_000L), 0.0F);

        Assert.assertTrue(timeline.pruneFinishedAnimations(1_000_000_000L));
        Assert.assertFalse(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(80.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F,
                1_000_000_000L), 0.0F);
        Assert.assertEquals(40.0F, timeline.resolveFloat(root, DocumentAnimationProperty.HEIGHT, 20.0F,
                1_000_000_000L), 0.0F);
    }

    /**
     * 验证 margin keyframe 会进入 layout 运行值，并在清除声明后移除 forwards fill。
     */
    @Test
    public void shouldRunMarginKeyframeAndClearFillAsLayoutRuntimeValues() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("marginPush")
                .setFloat(DocumentAnimationProperty.MARGIN_LEFT, 2.0F, 12.0F)
                .setFloat(DocumentAnimationProperty.MARGIN_RIGHT, 4.0F, 24.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(4), UiStyleLength.px(0),
                        UiStyleLength.px(2)))
                .setAnimation("marginPush", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L));

        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(7.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_LEFT, 2.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(14.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_RIGHT, 4.0F,
                500_000_000L), 0.0F);

        Assert.assertTrue(timeline.pruneFinishedAnimations(1_000_000_000L));
        Assert.assertFalse(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(12.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_LEFT, 2.0F,
                1_000_000_000L), 0.0F);
        Assert.assertEquals(24.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_RIGHT, 4.0F,
                1_000_000_000L), 0.0F);

        root.style().clearAnimationName();
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 1_000_000_000L));

        Assert.assertFalse(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(2.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_LEFT, 2.0F,
                1_000_000_000L), 0.0F);
        Assert.assertEquals(4.0F, timeline.resolveFloat(root, DocumentAnimationProperty.MARGIN_RIGHT, 4.0F,
                1_000_000_000L), 0.0F);
    }

    /**
     * 验证 padding keyframe 会进入 layout 运行值，并在清除声明后移除 forwards fill。
     */
    @Test
    public void shouldRunPaddingKeyframeAndClearFillAsLayoutRuntimeValues() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("paddingPush")
                .setFloat(DocumentAnimationProperty.PADDING_LEFT, 2.0F, 12.0F)
                .setFloat(DocumentAnimationProperty.PADDING_RIGHT, 4.0F, 24.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(4), UiStyleLength.px(0),
                        UiStyleLength.px(2)))
                .setAnimation("paddingPush", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L));

        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(7.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_LEFT, 2.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(14.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_RIGHT, 4.0F,
                500_000_000L), 0.0F);

        Assert.assertTrue(timeline.pruneFinishedAnimations(1_000_000_000L));
        Assert.assertFalse(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(12.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_LEFT, 2.0F,
                1_000_000_000L), 0.0F);
        Assert.assertEquals(24.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_RIGHT, 4.0F,
                1_000_000_000L), 0.0F);

        root.style().clearAnimationName();
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 1_000_000_000L));

        Assert.assertFalse(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(2.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_LEFT, 2.0F,
                1_000_000_000L), 0.0F);
        Assert.assertEquals(4.0F, timeline.resolveFloat(root, DocumentAnimationProperty.PADDING_RIGHT, 4.0F,
                1_000_000_000L), 0.0F);
    }

    /**
     * 验证 layout keyframe 会复用通用 delay、timing 与 fill-mode 生命周期语义。
     */
    @Test
    public void shouldHonorLayoutKeyframeDelayTimingAndFillMode() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("layoutPulse")
                .setFloat(DocumentAnimationProperty.WIDTH, 40.0F, 80.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setAnimation("layoutPulse", 1000L)
                .setAnimationDelayMillis(250L)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.EASE_IN)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);

        Assert.assertEquals(40.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 50.0F,
                200_000_000L), 0.0F);
        float easedHalf = DocumentAnimationTimingFunction.EASE_IN.apply(0.5F);
        Assert.assertEquals(40.0F + 40.0F * easedHalf, timeline.resolveFloat(root,
                DocumentAnimationProperty.WIDTH, 50.0F, 750_000_000L), 0.001F);
        Assert.assertEquals(80.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 50.0F,
                1_250_000_000L), 0.0F);

        Assert.assertTrue(timeline.pruneFinishedAnimations(1_250_000_000L));
        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(80.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 50.0F,
                1_250_000_000L), 0.0F);
    }

    /**
     * 验证 width 从 auto 进入像素值时不会创建首期 px-to-px transition。
     */
    @Test
    public void shouldNotTransitionWidthFromAutoTarget() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setHeight(UiStyleLength.px(20))
                .setTransition(DocumentAnimationProperty.WIDTH, 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);
        root.style().setWidth(UiStyleLength.px(80));
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L));

        Assert.assertFalse(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertFalse(timeline.hasRunningTransition(root, DocumentAnimationProperty.WIDTH));
        Assert.assertEquals(80.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 80.0F,
                500_000_000L), 0.0F);
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
        Assert.assertEquals(1.0F - DocumentAnimationTimingFunction.EASE_IN.apply(0.5F),
                timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.0F, 750_000_000L), 0.001F);
    }

    /**
     * 验证 prune 结果会暴露 transitionend 与 animationend 记录。
     */
    @Test
    public void shouldCollectCompletedTransitionAndAnimationRecords() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("fade")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setOpacity(1.0F)
                .setTransition(DocumentAnimationProperty.OPACITY, 1000L)
                .setAnimation("fade", 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        root.style().setOpacity(0.0F);
        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);

        DocumentAnimationTimeline.PruneResult pruneResult = timeline.pruneFinishedAnimationsWithResult(
                1_000_000_000L);

        Assert.assertTrue(pruneResult.isChanged());
        Assert.assertEquals(1, pruneResult.getTransitionEndRecords().size());
        Assert.assertEquals(root, pruneResult.getTransitionEndRecords().get(0).getElement());
        Assert.assertEquals(DocumentAnimationProperty.OPACITY,
                pruneResult.getTransitionEndRecords().get(0).getProperty());
        Assert.assertEquals(1_000_000_000L,
                pruneResult.getTransitionEndRecords().get(0).getElapsedTimeNanos());
        Assert.assertEquals(1, pruneResult.getAnimationEndRecords().size());
        Assert.assertEquals(root, pruneResult.getAnimationEndRecords().get(0).getElement());
        Assert.assertEquals("fade", pruneResult.getAnimationEndRecords().get(0).getAnimationName());
        Assert.assertEquals(1_000_000_000L,
                pruneResult.getAnimationEndRecords().get(0).getElapsedTimeNanos());
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
     * 验证 forwards fill 会按影响范围继续暴露运行态覆盖值，但不再计入下一帧动画工作。
     */
    @Test
    public void shouldReportForwardsFillRuntimeValueByImpact() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .setFloat(DocumentAnimationProperty.OPACITY, 1.0F, 0.25F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setOpacity(1.0F)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        Assert.assertTrue(timeline.pruneFinishedAnimations(1_000_000_000L));

        Assert.assertFalse(timeline.hasAnimationWork());
        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.PAINT));
        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.EFFECT));
        Assert.assertFalse(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(0xFFFFFFFF, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF000000, 1_000_000_000L));
        Assert.assertEquals(0.25F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 1.0F,
                1_000_000_000L), 0.0F);
    }

    /**
     * 验证动画诊断快照会按来源和影响范围统计 transition、keyframe 与 forwards fill。
     */
    @Test
    public void shouldExposeDiagnosticsSnapshotBySourceAndImpact() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("layoutFill")
                .setFloat(DocumentAnimationProperty.WIDTH, 40.0F, 80.0F)
                .build());
        ElementNode root = document.getRootElement();
        ElementNode fillElement = document.div();
        ElementNode transitionElement = document.div();
        fillElement.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setAnimation("layoutFill", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        transitionElement.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setTransitionProperties(DocumentAnimationProperty.BACKGROUND_COLOR,
                        DocumentAnimationProperty.HEIGHT)
                .setTransitionDurationMillis(1000L);
        root.append(fillElement).append(transitionElement);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 160, 0), 0L);
        Assert.assertTrue(timeline.pruneFinishedAnimations(1_000_000_000L));
        transitionElement.style()
                .setBackgroundColor(0xFFFFFFFF)
                .setHeight(UiStyleLength.px(40));
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 160, 0), 1_000_000_000L));

        DocumentAnimationTimeline.DiagnosticsSnapshot snapshot = timeline.getDiagnosticsSnapshot(1_500_000_000L);

        Assert.assertEquals(2, snapshot.getActiveAnimationCount());
        Assert.assertEquals(2, snapshot.getTotalTransitionCount());
        Assert.assertEquals(0, snapshot.getTotalKeyframeCount());
        Assert.assertEquals(1, snapshot.getTotalForwardsFillCount());
        Assert.assertEquals(1, snapshot.getTransitionCount(DocumentAnimationImpact.PAINT));
        Assert.assertEquals(1, snapshot.getTransitionCount(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(1, snapshot.getForwardsFillCount(DocumentAnimationImpact.LAYOUT));
        Assert.assertFalse(snapshot.hasRuntimeValue(DocumentAnimationImpact.EFFECT));
        Assert.assertTrue(snapshot.hasRuntimeValue(DocumentAnimationImpact.PAINT));
        Assert.assertTrue(snapshot.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
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
        float easedOpacity = 1.0F - DocumentAnimationTimingFunction.EASE_IN.apply(0.5F);
        Assert.assertEquals(easedOpacity, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                750_000_000L), 0.001F);
        Assert.assertEquals(easedOpacity, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 0.5F,
                1_750_000_000L), 0.001F);
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
     * 验证清除 animation 声明会取消 layout keyframe 和对应 forwards fill。
     */
    @Test
    public void shouldCancelLayoutKeyframeAndFillWhenDeclarationChanges() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("layoutGrow")
                .setFloat(DocumentAnimationProperty.WIDTH, 40.0F, 80.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setAnimation("layoutGrow", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);
        timeline.pruneFinishedAnimations(1_000_000_000L);
        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(80.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F,
                1_000_000_000L), 0.0F);

        root.style().clearAnimationName();
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 1_000_000_000L));

        Assert.assertFalse(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertFalse(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(40.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F,
                1_000_000_000L), 0.0F);
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
     * 验证 layout keyframes 定义替换会重启布局运行值轨道。
     */
    @Test
    public void shouldRestartLayoutKeyframeAnimationWhenRegisteredKeyframesDefinitionChanges() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("layoutGrow")
                .setFloat(DocumentAnimationProperty.WIDTH, 40.0F, 80.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setAnimation("layoutGrow", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);
        Assert.assertEquals(60.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F,
                500_000_000L), 0.0F);

        document.registerKeyframes(DocumentKeyframes.named("layoutGrow")
                .setFloat(DocumentAnimationProperty.WIDTH, 30.0F, 70.0F)
                .build());
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 500_000_000L));

        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(30.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F,
                500_000_000L), 0.0F);
        Assert.assertEquals(50.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F,
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
     * 验证移除 layout keyframes 定义会取消布局运行值和旧 forwards fill。
     */
    @Test
    public void shouldCancelLayoutKeyframeAndFillWhenRegisteredKeyframesDefinitionIsRemoved() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("layoutGrow")
                .setFloat(DocumentAnimationProperty.WIDTH, 40.0F, 80.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setAnimation("layoutGrow", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);
        timeline.pruneFinishedAnimations(1_000_000_000L);
        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(80.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F,
                1_000_000_000L), 0.0F);

        document.unregisterKeyframes("layoutGrow");
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 1_000_000_000L));

        Assert.assertFalse(timeline.hasAnimationWork(DocumentAnimationImpact.LAYOUT));
        Assert.assertFalse(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(40.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F,
                1_000_000_000L), 0.0F);
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

    /**
     * 验证没有 transition 接管时，作者侧同属性目标值变化也会清理旧 forwards fill。
     */
    @Test
    public void shouldSuppressKeyframeFillWhenAuthorTargetChangesWithoutTransition() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        timeline.pruneFinishedAnimations(1_000_000_000L);
        Assert.assertEquals(0xFFFFFFFF, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF000000, 1_000_000_000L));

        root.style().setBackgroundColor(0xFF123456);
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 1_000_000_000L));

        Assert.assertFalse(timeline.hasAnimationWork());
        Assert.assertEquals(0xFF123456, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF123456, 1_000_000_000L));
    }

    /**
     * 验证作者侧 layout 目标值变化会清理同属性 layout keyframe forwards fill。
     */
    @Test
    public void shouldSuppressLayoutKeyframeFillWhenAuthorTargetChanges() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("layoutGrow")
                .setFloat(DocumentAnimationProperty.WIDTH, 40.0F, 80.0F)
                .setFloat(DocumentAnimationProperty.HEIGHT, 20.0F, 40.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setAnimation("layoutGrow", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);
        timeline.pruneFinishedAnimations(1_000_000_000L);
        Assert.assertEquals(80.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F,
                1_000_000_000L), 0.0F);
        Assert.assertEquals(40.0F, timeline.resolveFloat(root, DocumentAnimationProperty.HEIGHT, 20.0F,
                1_000_000_000L), 0.0F);

        root.style().setWidth(UiStyleLength.px(50));
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 1_000_000_000L));

        Assert.assertTrue(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(50.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 50.0F,
                1_000_000_000L), 0.0F);
        Assert.assertEquals(40.0F, timeline.resolveFloat(root, DocumentAnimationProperty.HEIGHT, 20.0F,
                1_000_000_000L), 0.0F);
    }

    /**
     * 验证 layout transition 接管时会清理同属性 keyframe fill，避免旧布局末帧回盖。
     */
    @Test
    public void shouldSuppressLayoutKeyframeFillWhenTransitionTakesOverSameProperty() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("layoutGrow")
                .setFloat(DocumentAnimationProperty.WIDTH, 40.0F, 80.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setAnimation("layoutGrow", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS)
                .setTransition(DocumentAnimationProperty.WIDTH, 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);
        timeline.pruneFinishedAnimations(1_000_000_000L);
        Assert.assertEquals(80.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 40.0F,
                1_000_000_000L), 0.0F);

        root.style().setWidth(UiStyleLength.px(50));
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 1_000_000_000L));

        Assert.assertTrue(timeline.hasRunningTransition(root, DocumentAnimationProperty.WIDTH));
        Assert.assertEquals(65.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 50.0F,
                1_500_000_000L), 0.0F);
        Assert.assertTrue(timeline.pruneFinishedAnimations(2_000_000_000L));
        Assert.assertFalse(timeline.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(50.0F, timeline.resolveFloat(root, DocumentAnimationProperty.WIDTH, 50.0F,
                2_000_000_000L), 0.0F);
    }

    /**
     * 验证多属性 forwards fill 中，作者只改动其中一个属性时不会清掉其他属性 fill。
     */
    @Test
    public void shouldSuppressOnlyChangedPropertyFromMultiPropertyKeyframeFill() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .setFloat(DocumentAnimationProperty.OPACITY, 1.0F, 0.25F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setOpacity(1.0F)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 0L);
        timeline.pruneFinishedAnimations(1_000_000_000L);
        Assert.assertEquals(0xFFFFFFFF, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF000000, 1_000_000_000L));
        Assert.assertEquals(0.25F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 1.0F,
                1_000_000_000L), 0.0F);

        root.style().setBackgroundColor(0xFF123456);
        Assert.assertTrue(timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 80, 0), 1_000_000_000L));

        Assert.assertEquals(0xFF123456, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF123456, 1_000_000_000L));
        Assert.assertEquals(0.25F, timeline.resolveFloat(root, DocumentAnimationProperty.OPACITY, 1.0F,
                1_000_000_000L), 0.0F);
    }

    /**
     * 确定性文本测量服务。
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
            return Collections.singletonList(text);
        }
    }
}
