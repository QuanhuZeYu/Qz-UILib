package club.heiqi.uilib.ui.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;

/**
 * 元素最终计算样式。
 */
public final class ComputedStyle {

    private final UiDisplay display;
    private final UiStyleLength width;
    private final UiStyleLength height;
    private final UiBoxSizing boxSizing;
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
    private final UiVerticalAlign verticalAlign;
    private final UiStyleLength rowGap;
    private final UiStyleLength columnGap;
    private final float flexGrow;
    private final float flexShrink;
    private final float opacity;
    private final int backgroundColor;
    private final int borderColor;
    private final int textColor;
    private final List<DocumentAnimationProperty> transitionProperties;
    private final long transitionDurationNanos;
    private final long transitionDelayNanos;
    private final DocumentAnimationTimingFunction transitionTimingFunction;
    private final String animationName;
    private final long animationDurationNanos;
    private final long animationDelayNanos;
    private final int animationIterationCount;
    private final DocumentAnimationFillMode animationFillMode;
    private final DocumentAnimationTimingFunction animationTimingFunction;
    private final UiStyleLength backdropBlurRadius;
    private final float backdropSaturation;
    // 新增字段
    private final UiStyleLength lineHeight;
    private final UiTextAlign textAlign;
    private final UiWhiteSpace whiteSpace;
    private final UiTextOverflow textOverflow;
    private final UiVisibility visibility;
    private final UiStyleLength minWidth;
    private final UiStyleLength maxWidth;
    private final UiStyleLength minHeight;
    private final UiStyleLength maxHeight;
    private final UiStyleLength flexBasis;
    private final UiAlignSelf alignSelf;
    private final UiFlexWrap flexWrap;
    private final UiBoxShadow boxShadow;
    private final UiBorderStyle borderStyle;
    private final UiCursor cursor;

    ComputedStyle(UiDisplay display, UiStyleLength width, UiStyleLength height, UiBoxSizing boxSizing,
            UiPosition position,
            UiStyleLength top, UiStyleLength right, UiStyleLength bottom, UiStyleLength left, Integer zIndex,
            UiStyleInsets margin, UiStyleInsets padding, UiStyleLength borderWidth, UiStyleLength borderRadius,
            UiOverflow overflowX, UiOverflow overflowY, UiFlexDirection flexDirection, UiAlignItems alignItems,
            UiJustifyContent justifyContent, UiVerticalAlign verticalAlign, UiStyleLength rowGap,
            UiStyleLength columnGap, float flexGrow, float flexShrink, float opacity, int backgroundColor, int borderColor,
            int textColor,
            List<DocumentAnimationProperty> transitionProperties, long transitionDurationNanos,
            long transitionDelayNanos, DocumentAnimationTimingFunction transitionTimingFunction,
            String animationName, long animationDurationNanos, long animationDelayNanos, int animationIterationCount,
            DocumentAnimationFillMode animationFillMode, DocumentAnimationTimingFunction animationTimingFunction,
            UiStyleLength backdropBlurRadius, float backdropSaturation,
            UiStyleLength lineHeight, UiTextAlign textAlign, UiWhiteSpace whiteSpace, UiTextOverflow textOverflow,
            UiVisibility visibility,
            UiStyleLength minWidth, UiStyleLength maxWidth, UiStyleLength minHeight, UiStyleLength maxHeight,
            UiStyleLength flexBasis, UiAlignSelf alignSelf, UiFlexWrap flexWrap,
            UiBoxShadow boxShadow, UiBorderStyle borderStyle, UiCursor cursor) {
        this.display = Objects.requireNonNull(display, "display");
        this.width = Objects.requireNonNull(width, "width");
        this.height = Objects.requireNonNull(height, "height");
        this.boxSizing = Objects.requireNonNull(boxSizing, "boxSizing");
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
        this.verticalAlign = Objects.requireNonNull(verticalAlign, "verticalAlign");
        this.rowGap = Objects.requireNonNull(rowGap, "rowGap");
        this.columnGap = Objects.requireNonNull(columnGap, "columnGap");
        this.flexGrow = Math.max(0.0F, flexGrow);
        this.flexShrink = Math.max(0.0F, flexShrink);
        this.opacity = Math.max(0.0F, Math.min(1.0F, opacity));
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.textColor = textColor;
        this.transitionProperties = Collections.unmodifiableList(new ArrayList<DocumentAnimationProperty>(
                Objects.requireNonNull(transitionProperties, "transitionProperties")));
        this.transitionDurationNanos = Math.max(0L, transitionDurationNanos);
        this.transitionDelayNanos = Math.max(0L, transitionDelayNanos);
        this.transitionTimingFunction = Objects.requireNonNull(transitionTimingFunction, "transitionTimingFunction");
        this.animationName = animationName;
        this.animationDurationNanos = Math.max(0L, animationDurationNanos);
        this.animationDelayNanos = Math.max(0L, animationDelayNanos);
        this.animationIterationCount = Math.max(1, animationIterationCount);
        this.animationFillMode = Objects.requireNonNull(animationFillMode, "animationFillMode");
        this.animationTimingFunction = Objects.requireNonNull(animationTimingFunction, "animationTimingFunction");
        this.backdropBlurRadius = Objects.requireNonNull(backdropBlurRadius, "backdropBlurRadius");
        this.backdropSaturation = Math.max(0.0F, backdropSaturation);
        this.lineHeight = Objects.requireNonNull(lineHeight, "lineHeight");
        this.textAlign = Objects.requireNonNull(textAlign, "textAlign");
        this.whiteSpace = Objects.requireNonNull(whiteSpace, "whiteSpace");
        this.textOverflow = Objects.requireNonNull(textOverflow, "textOverflow");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.minWidth = Objects.requireNonNull(minWidth, "minWidth");
        this.maxWidth = Objects.requireNonNull(maxWidth, "maxWidth");
        this.minHeight = Objects.requireNonNull(minHeight, "minHeight");
        this.maxHeight = Objects.requireNonNull(maxHeight, "maxHeight");
        this.flexBasis = Objects.requireNonNull(flexBasis, "flexBasis");
        this.alignSelf = Objects.requireNonNull(alignSelf, "alignSelf");
        this.flexWrap = Objects.requireNonNull(flexWrap, "flexWrap");
        this.boxShadow = boxShadow; // 可为 null（无阴影）
        this.borderStyle = borderStyle == null ? UiBorderStyle.NONE : borderStyle;
        this.cursor = cursor == null ? UiCursor.DEFAULT : cursor;
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

    public UiBoxSizing getBoxSizing() {
        return boxSizing;
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

    public UiVerticalAlign getVerticalAlign() {
        return verticalAlign;
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

    public float getOpacity() {
        return opacity;
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

    public String getAnimationName() {
        return animationName;
    }

    public long getAnimationDurationNanos() {
        return animationDurationNanos;
    }

    public long getAnimationDelayNanos() {
        return animationDelayNanos;
    }

    public int getAnimationIterationCount() {
        return animationIterationCount;
    }

    public DocumentAnimationFillMode getAnimationFillMode() {
        return animationFillMode;
    }

    public DocumentAnimationTimingFunction getAnimationTimingFunction() {
        return animationTimingFunction;
    }

    public UiStyleLength getBackdropBlurRadius() {
        return backdropBlurRadius;
    }

    public float getBackdropSaturation() {
        return backdropSaturation;
    }

    /**
     * 返回行高。auto 表示跟随字体默认行高。
     *
     * @return 行高
     */
    public UiStyleLength getLineHeight() {
        return lineHeight;
    }

    /**
     * 返回文本水平对齐方式。
     *
     * @return 文本对齐
     */
    public UiTextAlign getTextAlign() {
        return textAlign;
    }

    /**
     * 返回空白字符处理与换行行为。
     *
     * @return 空白处理
     */
    public UiWhiteSpace getWhiteSpace() {
        return whiteSpace;
    }

    /**
     * 返回文本溢出处理方式。
     *
     * @return 文本溢出
     */
    public UiTextOverflow getTextOverflow() {
        return textOverflow;
    }

    /**
     * 返回元素可见性。
     *
     * @return 可见性
     */
    public UiVisibility getVisibility() {
        return visibility;
    }

    /**
     * 返回最小宽度约束。
     *
     * @return 最小宽度
     */
    public UiStyleLength getMinWidth() {
        return minWidth;
    }

    /**
     * 返回最大宽度约束。auto 表示无上限。
     *
     * @return 最大宽度
     */
    public UiStyleLength getMaxWidth() {
        return maxWidth;
    }

    /**
     * 返回最小高度约束。
     *
     * @return 最小高度
     */
    public UiStyleLength getMinHeight() {
        return minHeight;
    }

    /**
     * 返回最大高度约束。auto 表示无上限。
     *
     * @return 最大高度
     */
    public UiStyleLength getMaxHeight() {
        return maxHeight;
    }

    /**
     * 返回 flex item 主轴初始尺寸。auto 时退回 width/height。
     *
     * @return flex-basis
     */
    public UiStyleLength getFlexBasis() {
        return flexBasis;
    }

    /**
     * 返回 flex item 交叉轴对齐方式（覆盖父容器 align-items）。
     *
     * @return align-self
     */
    public UiAlignSelf getAlignSelf() {
        return alignSelf;
    }

    /**
     * 返回 flex 换行行为。
     *
     * @return flex-wrap
     */
    public UiFlexWrap getFlexWrap() {
        return flexWrap;
    }

    /**
     * 返回元素阴影。
     *
     * @return 阴影值；无阴影时返回 null
     */
    public UiBoxShadow getBoxShadow() {
        return boxShadow;
    }

    /**
     * 返回边框样式。
     *
     * @return 边框样式
     */
    public UiBorderStyle getBorderStyle() {
        return borderStyle;
    }

    /**
     * 返回光标样式。
     *
     * @return 光标样式
     */
    public UiCursor getCursor() {
        return cursor;
    }
}
