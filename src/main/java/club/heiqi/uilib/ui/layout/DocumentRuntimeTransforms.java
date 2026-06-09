package club.heiqi.uilib.ui.layout;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * HTML-like 运行态 transform 解析辅助。
 */
public final class DocumentRuntimeTransforms {

    private DocumentRuntimeTransforms() {}

    /**
     * 解析布局盒在指定动画时刻实际参与视觉语义的 transform。
     *
     * @param box 布局盒
     * @param currentTimeNanos 当前动画时间
     * @param animationTimeline 动画时间线；为 null 时只使用 computed style
     * @return transform 值
     */
    public static UiTransform resolveTransform(DocumentLayoutBox box, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        return resolveTransform(box.getElement(), box.getComputedStyle(), currentTimeNanos, animationTimeline);
    }

    /**
     * 解析元素在指定动画时刻实际参与视觉语义的 transform。
     *
     * @param element 元素
     * @param computedStyle computed style 基准值
     * @param currentTimeNanos 当前动画时间
     * @param animationTimeline 动画时间线；为 null 时只使用 computed style
     * @return transform 值
     */
    public static UiTransform resolveTransform(ElementNode element, ComputedStyle computedStyle, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        UiTransform baseTransform = computedStyle.getTransform();
        if (baseTransform == null) {
            baseTransform = UiTransform.identity();
        }
        if (animationTimeline == null) {
            return baseTransform;
        }
        float translateX = animationTimeline.resolveFloat(element, DocumentAnimationProperty.TRANSLATE_X,
                baseTransform.getTranslateX(), currentTimeNanos);
        float translateY = animationTimeline.resolveFloat(element, DocumentAnimationProperty.TRANSLATE_Y,
                baseTransform.getTranslateY(), currentTimeNanos);
        float scaleX = animationTimeline.resolveFloat(element, DocumentAnimationProperty.SCALE_X,
                baseTransform.getScaleX(), currentTimeNanos);
        float scaleY = animationTimeline.resolveFloat(element, DocumentAnimationProperty.SCALE_Y,
                baseTransform.getScaleY(), currentTimeNanos);
        float rotate = animationTimeline.resolveFloat(element, DocumentAnimationProperty.ROTATE,
                baseTransform.getRotateDegrees(), currentTimeNanos);
        return UiTransform.of(translateX, translateY, scaleX, scaleY, rotate, baseTransform.getOriginX(),
                baseTransform.getOriginY());
    }

    /**
     * 判断当前运行态 transform 是否会建立 fixed containing block。
     *
     * @param element 元素
     * @param computedStyle computed style 基准值
     * @param currentTimeNanos 当前动画时间
     * @param animationTimeline 动画时间线；为 null 时只使用 computed style
     * @return 是否建立 fixed containing block
     */
    public static boolean createsFixedContainingBlock(ElementNode element, ComputedStyle computedStyle,
            long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        UiTransform transform = resolveTransform(element, computedStyle, currentTimeNanos, animationTimeline);
        return transform != null && !transform.isIdentity();
    }
}
