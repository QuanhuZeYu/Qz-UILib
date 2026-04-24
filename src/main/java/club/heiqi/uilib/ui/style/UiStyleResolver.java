package club.heiqi.uilib.ui.style;

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
        UiStyleLength rowGap = style.getRowGap() == null ? UiStyleLength.px(0) : style.getRowGap();
        UiStyleLength columnGap = style.getColumnGap() == null ? UiStyleLength.px(0) : style.getColumnGap();
        float flexGrow = style.getFlexGrow() == null ? 0.0F : style.getFlexGrow().floatValue();
        float flexShrink = style.getFlexShrink() == null ? 1.0F : style.getFlexShrink().floatValue();
        int backgroundColor = style.getBackgroundColor() == null ? TRANSPARENT : style.getBackgroundColor().intValue();
        int borderColor = style.getBorderColor() == null ? TRANSPARENT : style.getBorderColor().intValue();
        int textColor = style.getTextColor() == null ? inheritedTextColor(parentStyle) : style.getTextColor().intValue();
        return new ComputedStyle(display, width, height, margin, padding, borderWidth, borderRadius, overflowX,
                overflowY, flexDirection, alignItems, justifyContent, rowGap, columnGap, flexGrow, flexShrink,
                backgroundColor, borderColor, textColor);
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
