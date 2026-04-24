package club.heiqi.uilib.ui.style;

import java.util.Objects;

/**
 * 元素最终计算样式。
 */
public final class ComputedStyle {

    private final UiDisplay display;
    private final UiStyleLength width;
    private final UiStyleLength height;
    private final UiStyleInsets margin;
    private final UiStyleInsets padding;
    private final UiStyleLength borderWidth;
    private final UiStyleLength borderRadius;
    private final UiOverflow overflowX;
    private final UiOverflow overflowY;
    private final int backgroundColor;
    private final int borderColor;
    private final int textColor;

    ComputedStyle(UiDisplay display, UiStyleLength width, UiStyleLength height, UiStyleInsets margin,
            UiStyleInsets padding, UiStyleLength borderWidth, UiStyleLength borderRadius, UiOverflow overflowX,
            UiOverflow overflowY, int backgroundColor, int borderColor, int textColor) {
        this.display = Objects.requireNonNull(display, "display");
        this.width = Objects.requireNonNull(width, "width");
        this.height = Objects.requireNonNull(height, "height");
        this.margin = Objects.requireNonNull(margin, "margin");
        this.padding = Objects.requireNonNull(padding, "padding");
        this.borderWidth = Objects.requireNonNull(borderWidth, "borderWidth");
        this.borderRadius = Objects.requireNonNull(borderRadius, "borderRadius");
        this.overflowX = Objects.requireNonNull(overflowX, "overflowX");
        this.overflowY = Objects.requireNonNull(overflowY, "overflowY");
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.textColor = textColor;
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

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getBorderColor() {
        return borderColor;
    }

    public int getTextColor() {
        return textColor;
    }
}
