package club.heiqi.uilib.ui.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;

/**
 * 元素最终计算样式。
 */
public final class ComputedStyle {

    private final UiDisplay display;
    private final UiStyleLength width;
    private final UiStyleLength height;
    private final UiPosition position;
    private final UiStyleLength top;
    private final UiStyleLength right;
    private final UiStyleLength bottom;
    private final UiStyleLength left;
    private final Integer zIndex;
    private final UiStyleInsets margin;
    private final UiStyleInsets padding;
    private final UiStyleLength borderWidth;
    private final UiStyleLength borderRadius;
    private final UiOverflow overflowX;
    private final UiOverflow overflowY;
    private final UiFlexDirection flexDirection;
    private final UiAlignItems alignItems;
    private final UiJustifyContent justifyContent;
    private final UiStyleLength rowGap;
    private final UiStyleLength columnGap;
    private final float flexGrow;
    private final float flexShrink;
    private final int backgroundColor;
    private final int borderColor;
    private final int textColor;
    private final List<DocumentAnimationProperty> transitionProperties;
    private final long transitionDurationNanos;
    private final long transitionDelayNanos;
    private final DocumentAnimationTimingFunction transitionTimingFunction;
    private final UiStyleLength backdropBlurRadius;
    private final float backdropSaturation;

    ComputedStyle(UiDisplay display, UiStyleLength width, UiStyleLength height, UiPosition position,
            UiStyleLength top, UiStyleLength right, UiStyleLength bottom, UiStyleLength left, Integer zIndex,
            UiStyleInsets margin, UiStyleInsets padding, UiStyleLength borderWidth, UiStyleLength borderRadius,
            UiOverflow overflowX, UiOverflow overflowY, UiFlexDirection flexDirection, UiAlignItems alignItems,
            UiJustifyContent justifyContent, UiStyleLength rowGap, UiStyleLength columnGap, float flexGrow,
            float flexShrink, int backgroundColor, int borderColor, int textColor,
            List<DocumentAnimationProperty> transitionProperties, long transitionDurationNanos,
            long transitionDelayNanos, DocumentAnimationTimingFunction transitionTimingFunction,
            UiStyleLength backdropBlurRadius, float backdropSaturation) {
        this.display = Objects.requireNonNull(display, "display");
        this.width = Objects.requireNonNull(width, "width");
        this.height = Objects.requireNonNull(height, "height");
        this.position = Objects.requireNonNull(position, "position");
        this.top = Objects.requireNonNull(top, "top");
        this.right = Objects.requireNonNull(right, "right");
        this.bottom = Objects.requireNonNull(bottom, "bottom");
        this.left = Objects.requireNonNull(left, "left");
        this.zIndex = zIndex;
        this.margin = Objects.requireNonNull(margin, "margin");
        this.padding = Objects.requireNonNull(padding, "padding");
        this.borderWidth = Objects.requireNonNull(borderWidth, "borderWidth");
        this.borderRadius = Objects.requireNonNull(borderRadius, "borderRadius");
        this.overflowX = Objects.requireNonNull(overflowX, "overflowX");
        this.overflowY = Objects.requireNonNull(overflowY, "overflowY");
        this.flexDirection = Objects.requireNonNull(flexDirection, "flexDirection");
        this.alignItems = Objects.requireNonNull(alignItems, "alignItems");
        this.justifyContent = Objects.requireNonNull(justifyContent, "justifyContent");
        this.rowGap = Objects.requireNonNull(rowGap, "rowGap");
        this.columnGap = Objects.requireNonNull(columnGap, "columnGap");
        this.flexGrow = Math.max(0.0F, flexGrow);
        this.flexShrink = Math.max(0.0F, flexShrink);
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.textColor = textColor;
        this.transitionProperties = Collections.unmodifiableList(new ArrayList<DocumentAnimationProperty>(
                Objects.requireNonNull(transitionProperties, "transitionProperties")));
        this.transitionDurationNanos = Math.max(0L, transitionDurationNanos);
        this.transitionDelayNanos = Math.max(0L, transitionDelayNanos);
        this.transitionTimingFunction = Objects.requireNonNull(transitionTimingFunction, "transitionTimingFunction");
        this.backdropBlurRadius = Objects.requireNonNull(backdropBlurRadius, "backdropBlurRadius");
        this.backdropSaturation = Math.max(0.0F, backdropSaturation);
    }

    public UiDisplay getDisplay() {
        return display;
    }

    public UiStyleLength getWidth() {
        return width;
    }

    public UiStyleLength getHeight() {
        return height;
    }

    public UiPosition getPosition() {
        return position;
    }

    public UiStyleLength getTop() {
        return top;
    }

    public UiStyleLength getRight() {
        return right;
    }

    public UiStyleLength getBottom() {
        return bottom;
    }

    public UiStyleLength getLeft() {
        return left;
    }

    public Integer getZIndex() {
        return zIndex;
    }

    public UiStyleInsets getMargin() {
        return margin;
    }

    public UiStyleInsets getPadding() {
        return padding;
    }

    public UiStyleLength getBorderWidth() {
        return borderWidth;
    }

    public UiStyleLength getBorderRadius() {
        return borderRadius;
    }

    public UiOverflow getOverflowX() {
        return overflowX;
    }

    public UiOverflow getOverflowY() {
        return overflowY;
    }

    public UiFlexDirection getFlexDirection() {
        return flexDirection;
    }

    public UiAlignItems getAlignItems() {
        return alignItems;
    }

    public UiJustifyContent getJustifyContent() {
        return justifyContent;
    }

    public UiStyleLength getRowGap() {
        return rowGap;
    }

    public UiStyleLength getColumnGap() {
        return columnGap;
    }

    public float getFlexGrow() {
        return flexGrow;
    }

    public float getFlexShrink() {
        return flexShrink;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getBorderColor() {
        return borderColor;
    }

    public int getTextColor() {
        return textColor;
    }

    public List<DocumentAnimationProperty> getTransitionProperties() {
        return transitionProperties;
    }

    public long getTransitionDurationNanos() {
        return transitionDurationNanos;
    }

    public long getTransitionDelayNanos() {
        return transitionDelayNanos;
    }

    public DocumentAnimationTimingFunction getTransitionTimingFunction() {
        return transitionTimingFunction;
    }

    public UiStyleLength getBackdropBlurRadius() {
        return backdropBlurRadius;
    }

    public float getBackdropSaturation() {
        return backdropSaturation;
    }
}
