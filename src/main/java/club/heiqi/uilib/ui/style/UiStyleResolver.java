package club.heiqi.uilib.ui.style;

import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
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
        UiBoxSizing boxSizing = style.getBoxSizing() == null ? UiBoxSizing.CONTENT_BOX : style.getBoxSizing();
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
        String animationName = style.getAnimationName();
        long animationDurationNanos = style.getAnimationDurationNanos() == null ? 0L
                : style.getAnimationDurationNanos().longValue();
        long animationDelayNanos = style.getAnimationDelayNanos() == null ? 0L
                : style.getAnimationDelayNanos().longValue();
        int animationIterationCount = style.getAnimationIterationCount() == null ? 1
                : style.getAnimationIterationCount().intValue();
        DocumentAnimationFillMode animationFillMode = style.getAnimationFillMode() == null
                ? DocumentAnimationFillMode.NONE
                : style.getAnimationFillMode();
        DocumentAnimationTimingFunction animationTimingFunction = style.getAnimationTimingFunction() == null
                ? DocumentAnimationTimingFunction.LINEAR
                : style.getAnimationTimingFunction();
        UiStyleLength backdropBlurRadius = style.getBackdropBlurRadius() == null ? UiStyleLength.px(0)
                : style.getBackdropBlurRadius();
        float backdropSaturation = style.getBackdropSaturation() == null ? 1.0F
                : style.getBackdropSaturation().floatValue();

        // 新增属性解析
        // line-height：可继承，子元素未声明时从父元素继承
        UiStyleLength lineHeight = style.getLineHeight() == null ? inheritedLineHeight(parentStyle) : style.getLineHeight();

        // text-align：可继承，子元素未声明时从父元素继承
        UiTextAlign textAlign = style.getTextAlign() == null ? inheritedTextAlign(parentStyle) : style.getTextAlign();

        // white-space：可继承
        UiWhiteSpace whiteSpace = style.getWhiteSpace() == null ? inheritedWhiteSpace(parentStyle) : style.getWhiteSpace();

        // text-overflow：不可继承，默认 CLIP
        UiTextOverflow textOverflow = style.getTextOverflow() == null ? UiTextOverflow.CLIP : style.getTextOverflow();

        // visibility：可继承，子元素未声明时从父元素继承
        UiVisibility visibility = style.getVisibility() == null ? inheritedVisibility(parentStyle) : style.getVisibility();

        // min/max 尺寸约束：不可继承
        UiStyleLength minWidth = style.getMinWidth() == null ? UiStyleLength.px(0) : style.getMinWidth();
        UiStyleLength maxWidth = style.getMaxWidth() == null ? UiStyleLength.auto() : style.getMaxWidth();
        UiStyleLength minHeight = style.getMinHeight() == null ? UiStyleLength.px(0) : style.getMinHeight();
        UiStyleLength maxHeight = style.getMaxHeight() == null ? UiStyleLength.auto() : style.getMaxHeight();

        // flex 增强：不可继承
        UiStyleLength flexBasis = style.getFlexBasis() == null ? UiStyleLength.auto() : style.getFlexBasis();
        UiAlignSelf alignSelf = style.getAlignSelf() == null ? UiAlignSelf.AUTO : style.getAlignSelf();
        UiFlexWrap flexWrap = style.getFlexWrap() == null ? UiFlexWrap.NOWRAP : style.getFlexWrap();

        return new ComputedStyle(display, width, height, boxSizing, position, top, right, bottom, left, zIndex, margin,
                padding, borderWidth, borderRadius, overflowX, overflowY, flexDirection, alignItems, justifyContent,
                verticalAlign, rowGap, columnGap, flexGrow, flexShrink, opacity, backgroundColor, borderColor, textColor,
                transitionProperties, transitionDurationNanos, transitionDelayNanos, transitionTimingFunction,
                animationName, animationDurationNanos, animationDelayNanos, animationIterationCount, animationFillMode,
                animationTimingFunction,
                backdropBlurRadius, backdropSaturation,
                lineHeight, textAlign, whiteSpace, textOverflow, visibility,
                minWidth, maxWidth, minHeight, maxHeight,
                flexBasis, alignSelf, flexWrap);
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

    private static UiStyleLength inheritedLineHeight(ComputedStyle parentStyle) {
        return parentStyle == null ? UiStyleLength.auto() : parentStyle.getLineHeight();
    }

    private static UiTextAlign inheritedTextAlign(ComputedStyle parentStyle) {
        return parentStyle == null ? UiTextAlign.START : parentStyle.getTextAlign();
    }

    private static UiWhiteSpace inheritedWhiteSpace(ComputedStyle parentStyle) {
        return parentStyle == null ? UiWhiteSpace.NORMAL : parentStyle.getWhiteSpace();
    }

    private static UiVisibility inheritedVisibility(ComputedStyle parentStyle) {
        // 父元素 HIDDEN 时子元素继承 HIDDEN；父不存在或 VISIBLE 则默认 VISIBLE
        return parentStyle == null ? UiVisibility.VISIBLE : parentStyle.getVisibility();
    }

    private static UiDisplay defaultDisplay(String tagName) {
        if ("span".equals(tagName)) {
            return UiDisplay.INLINE;
        }
        if ("button".equals(tagName) || "input".equals(tagName) || "img".equals(tagName)) {
            return UiDisplay.INLINE_BLOCK;
        }
        if ("table".equals(tagName)) {
            return UiDisplay.TABLE;
        }
        if ("thead".equals(tagName)) {
            return UiDisplay.TABLE_HEADER_GROUP;
        }
        if ("tbody".equals(tagName)) {
            return UiDisplay.TABLE_ROW_GROUP;
        }
        if ("tfoot".equals(tagName)) {
            return UiDisplay.TABLE_FOOTER_GROUP;
        }
        if ("tr".equals(tagName)) {
            return UiDisplay.TABLE_ROW;
        }
        if ("td".equals(tagName) || "th".equals(tagName)) {
            return UiDisplay.TABLE_CELL;
        }
        return UiDisplay.BLOCK;
    }
}
