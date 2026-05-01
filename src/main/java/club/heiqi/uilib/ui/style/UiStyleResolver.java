package club.heiqi.uilib.ui.style;

import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * HTML-like 样式计算入口。
 */
public final class UiStyleResolver {

    private static final int TRANSPARENT = 0x00000000;
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;

    private UiStyleResolver() {}

    /**
     * 计算元素最终样式。
     *
     * @param element 目标元素
     * @return 计算样式
     */
    public static ComputedStyle compute(ElementNode element) {
        if (element == null) {
            throw new NullPointerException("element");
        }
        return compute(element, computeParentStyle(element));
    }

    private static ComputedStyle compute(ElementNode element, ComputedStyle parentStyle) {
        UiStyleDeclaration style = element.style();
        UiDisplay display = style.getDisplay() == null ? defaultDisplay(element.getTagName()) : style.getDisplay();
        UiStyleLength width = style.getWidth() == null ? UiStyleLength.auto() : style.getWidth();
        UiStyleLength height = style.getHeight() == null ? UiStyleLength.auto() : style.getHeight();
        UiPosition position = style.getPosition() == null ? UiPosition.STATIC : style.getPosition();
        UiStyleLength top = style.getTop() == null ? UiStyleLength.auto() : style.getTop();
        UiStyleLength right = style.getRight() == null ? UiStyleLength.auto() : style.getRight();
        UiStyleLength bottom = style.getBottom() == null ? UiStyleLength.auto() : style.getBottom();
        UiStyleLength left = style.getLeft() == null ? UiStyleLength.auto() : style.getLeft();
        Integer zIndex = style.getZIndex();
        UiStyleInsets margin = style.getMargin() == null ? UiStyleInsets.zero() : style.getMargin();
        UiStyleInsets padding = style.getPadding() == null ? UiStyleInsets.zero() : style.getPadding();
        UiStyleLength borderWidth = style.getBorderWidth() == null ? UiStyleLength.px(0) : style.getBorderWidth();
        UiStyleLength borderRadius = style.getBorderRadius() == null ? UiStyleLength.px(0) : style.getBorderRadius();
        UiOverflow overflowX = style.getOverflowX() == null ? UiOverflow.VISIBLE : style.getOverflowX();
        UiOverflow overflowY = style.getOverflowY() == null ? UiOverflow.VISIBLE : style.getOverflowY();
        UiFlexDirection flexDirection = style.getFlexDirection() == null ? UiFlexDirection.ROW : style.getFlexDirection();
        UiAlignItems alignItems = style.getAlignItems() == null ? UiAlignItems.STRETCH : style.getAlignItems();
        UiJustifyContent justifyContent = style.getJustifyContent() == null ? UiJustifyContent.START
                : style.getJustifyContent();
        UiVerticalAlign verticalAlign = style.getVerticalAlign() == null ? UiVerticalAlign.BASELINE
                : style.getVerticalAlign();
        UiStyleLength rowGap = style.getRowGap() == null ? UiStyleLength.px(0) : style.getRowGap();
        UiStyleLength columnGap = style.getColumnGap() == null ? UiStyleLength.px(0) : style.getColumnGap();
        float flexGrow = style.getFlexGrow() == null ? 0.0F : style.getFlexGrow().floatValue();
        float flexShrink = style.getFlexShrink() == null ? 1.0F : style.getFlexShrink().floatValue();
        float opacity = style.getOpacity() == null ? 1.0F : style.getOpacity().floatValue();
        int backgroundColor = style.getBackgroundColor() == null ? TRANSPARENT : style.getBackgroundColor().intValue();
        int borderColor = style.getBorderColor() == null ? TRANSPARENT : style.getBorderColor().intValue();
        int textColor = style.getTextColor() == null ? inheritedTextColor(parentStyle) : style.getTextColor().intValue();
        List<DocumentAnimationProperty> transitionProperties = style.getTransitionProperties() == null
                ? Collections.<DocumentAnimationProperty>emptyList()
                : style.getTransitionProperties();
        long transitionDurationNanos = style.getTransitionDurationNanos() == null ? 0L
                : style.getTransitionDurationNanos().longValue();
        long transitionDelayNanos = style.getTransitionDelayNanos() == null ? 0L
                : style.getTransitionDelayNanos().longValue();
        DocumentAnimationTimingFunction transitionTimingFunction = style.getTransitionTimingFunction() == null
                ? DocumentAnimationTimingFunction.LINEAR
                : style.getTransitionTimingFunction();
        UiStyleLength backdropBlurRadius = style.getBackdropBlurRadius() == null ? UiStyleLength.px(0)
                : style.getBackdropBlurRadius();
        float backdropSaturation = style.getBackdropSaturation() == null ? 1.0F
                : style.getBackdropSaturation().floatValue();
        return new ComputedStyle(display, width, height, position, top, right, bottom, left, zIndex, margin,
                padding, borderWidth, borderRadius, overflowX, overflowY, flexDirection, alignItems, justifyContent,
                verticalAlign, rowGap, columnGap, flexGrow, flexShrink, opacity, backgroundColor, borderColor, textColor,
                transitionProperties, transitionDurationNanos, transitionDelayNanos, transitionTimingFunction,
                backdropBlurRadius, backdropSaturation);
    }

    private static ComputedStyle computeParentStyle(ElementNode element) {
        DocumentNode parent = element.getParent();
        if (!(parent instanceof ElementNode)) {
            return null;
        }
        return compute((ElementNode) parent);
    }

    private static int inheritedTextColor(ComputedStyle parentStyle) {
        return parentStyle == null ? DEFAULT_TEXT_COLOR : parentStyle.getTextColor();
    }

    private static UiDisplay defaultDisplay(String tagName) {
        if ("span".equals(tagName)) {
            return UiDisplay.INLINE;
        }
        return UiDisplay.BLOCK;
    }
}
