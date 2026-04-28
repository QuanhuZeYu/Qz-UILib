package club.heiqi.uilib.ui.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;

/**
 * 元素作者侧样式声明。
 *
 * <p>该类型只保存显式声明的属性；缺省值和继承值由 computed style 阶段解析。</p>
 */
public final class UiStyleDeclaration {

    private static final Runnable NO_OP_CHANGE_LISTENER = new Runnable() {
        @Override
        public void run() {}
    };

    private final Runnable changeListener;
    private UiDisplay display;
    private UiStyleLength width;
    private UiStyleLength height;
    private UiPosition position;
    private UiStyleLength top;
    private UiStyleLength right;
    private UiStyleLength bottom;
    private UiStyleLength left;
    private Integer zIndex;
    private UiStyleInsets margin;
    private UiStyleInsets padding;
    private UiStyleLength borderWidth;
    private UiStyleLength borderRadius;
    private UiOverflow overflowX;
    private UiOverflow overflowY;
    private UiFlexDirection flexDirection;
    private UiAlignItems alignItems;
    private UiJustifyContent justifyContent;
    private UiStyleLength rowGap;
    private UiStyleLength columnGap;
    private Float flexGrow;
    private Float flexShrink;
    private Float opacity;
    private Integer backgroundColor;
    private Integer borderColor;
    private Integer textColor;
    private List<DocumentAnimationProperty> transitionProperties;
    private Long transitionDurationNanos;
    private Long transitionDelayNanos;
    private DocumentAnimationTimingFunction transitionTimingFunction;
    private UiStyleLength backdropBlurRadius;
    private Float backdropSaturation;

    public UiStyleDeclaration() {
        this(null);
    }

    public UiStyleDeclaration(Runnable changeListener) {
        this.changeListener = changeListener == null ? NO_OP_CHANGE_LISTENER : changeListener;
    }

    public UiDisplay getDisplay() {
        return display;
    }

    public UiStyleDeclaration setDisplay(UiDisplay display) {
        return updateDisplay(display);
    }

    public UiStyleDeclaration clearDisplay() {
        return updateDisplay(null);
    }

    public UiStyleLength getWidth() {
        return width;
    }

    public UiStyleDeclaration setWidth(UiStyleLength width) {
        return updateWidth(Objects.requireNonNull(width, "width"));
    }

    public UiStyleDeclaration clearWidth() {
        return updateWidth(null);
    }

    public UiStyleLength getHeight() {
        return height;
    }

    public UiStyleDeclaration setHeight(UiStyleLength height) {
        return updateHeight(Objects.requireNonNull(height, "height"));
    }

    public UiStyleDeclaration clearHeight() {
        return updateHeight(null);
    }

    public UiPosition getPosition() {
        return position;
    }

    public UiStyleDeclaration setPosition(UiPosition position) {
        return updatePosition(Objects.requireNonNull(position, "position"));
    }

    public UiStyleDeclaration clearPosition() {
        return updatePosition(null);
    }

    public UiStyleLength getTop() {
        return top;
    }

    public UiStyleDeclaration setTop(UiStyleLength top) {
        return updateTop(Objects.requireNonNull(top, "top"));
    }

    public UiStyleDeclaration clearTop() {
        return updateTop(null);
    }

    public UiStyleLength getRight() {
        return right;
    }

    public UiStyleDeclaration setRight(UiStyleLength right) {
        return updateRight(Objects.requireNonNull(right, "right"));
    }

    public UiStyleDeclaration clearRight() {
        return updateRight(null);
    }

    public UiStyleLength getBottom() {
        return bottom;
    }

    public UiStyleDeclaration setBottom(UiStyleLength bottom) {
        return updateBottom(Objects.requireNonNull(bottom, "bottom"));
    }

    public UiStyleDeclaration clearBottom() {
        return updateBottom(null);
    }

    public UiStyleLength getLeft() {
        return left;
    }

    public UiStyleDeclaration setLeft(UiStyleLength left) {
        return updateLeft(Objects.requireNonNull(left, "left"));
    }

    public UiStyleDeclaration clearLeft() {
        return updateLeft(null);
    }

    public Integer getZIndex() {
        return zIndex;
    }

    public UiStyleDeclaration setZIndex(int zIndex) {
        return updateZIndex(Integer.valueOf(zIndex));
    }

    public UiStyleDeclaration clearZIndex() {
        return updateZIndex(null);
    }

    public UiStyleInsets getMargin() {
        return margin;
    }

    public UiStyleDeclaration setMargin(UiStyleLength margin) {
        return setMargin(UiStyleInsets.all(margin));
    }

    public UiStyleDeclaration setMargin(UiStyleInsets margin) {
        return updateMargin(Objects.requireNonNull(margin, "margin"));
    }

    public UiStyleDeclaration clearMargin() {
        return updateMargin(null);
    }

    public UiStyleInsets getPadding() {
        return padding;
    }

    public UiStyleDeclaration setPadding(UiStyleLength padding) {
        return setPadding(UiStyleInsets.all(padding));
    }

    public UiStyleDeclaration setPadding(UiStyleInsets padding) {
        return updatePadding(Objects.requireNonNull(padding, "padding"));
    }

    public UiStyleDeclaration clearPadding() {
        return updatePadding(null);
    }

    public UiStyleLength getBorderWidth() {
        return borderWidth;
    }

    public UiStyleDeclaration setBorderWidth(UiStyleLength borderWidth) {
        return updateBorderWidth(Objects.requireNonNull(borderWidth, "borderWidth"));
    }

    public UiStyleDeclaration clearBorderWidth() {
        return updateBorderWidth(null);
    }

    public UiStyleLength getBorderRadius() {
        return borderRadius;
    }

    public UiStyleDeclaration setBorderRadius(UiStyleLength borderRadius) {
        return updateBorderRadius(Objects.requireNonNull(borderRadius, "borderRadius"));
    }

    public UiStyleDeclaration clearBorderRadius() {
        return updateBorderRadius(null);
    }

    public UiOverflow getOverflowX() {
        return overflowX;
    }

    public UiStyleDeclaration setOverflowX(UiOverflow overflowX) {
        return updateOverflowX(Objects.requireNonNull(overflowX, "overflowX"));
    }

    public UiStyleDeclaration clearOverflowX() {
        return updateOverflowX(null);
    }

    public UiOverflow getOverflowY() {
        return overflowY;
    }

    public UiStyleDeclaration setOverflowY(UiOverflow overflowY) {
        return updateOverflowY(Objects.requireNonNull(overflowY, "overflowY"));
    }

    public UiStyleDeclaration clearOverflowY() {
        return updateOverflowY(null);
    }

    public UiFlexDirection getFlexDirection() {
        return flexDirection;
    }

    public UiStyleDeclaration setFlexDirection(UiFlexDirection flexDirection) {
        return updateFlexDirection(Objects.requireNonNull(flexDirection, "flexDirection"));
    }

    public UiStyleDeclaration clearFlexDirection() {
        return updateFlexDirection(null);
    }

    public UiAlignItems getAlignItems() {
        return alignItems;
    }

    public UiStyleDeclaration setAlignItems(UiAlignItems alignItems) {
        return updateAlignItems(Objects.requireNonNull(alignItems, "alignItems"));
    }

    public UiStyleDeclaration clearAlignItems() {
        return updateAlignItems(null);
    }

    public UiJustifyContent getJustifyContent() {
        return justifyContent;
    }

    public UiStyleDeclaration setJustifyContent(UiJustifyContent justifyContent) {
        return updateJustifyContent(Objects.requireNonNull(justifyContent, "justifyContent"));
    }

    public UiStyleDeclaration clearJustifyContent() {
        return updateJustifyContent(null);
    }

    public UiStyleLength getRowGap() {
        return rowGap;
    }

    public UiStyleDeclaration setRowGap(UiStyleLength rowGap) {
        return updateRowGap(Objects.requireNonNull(rowGap, "rowGap"));
    }

    public UiStyleDeclaration clearRowGap() {
        return updateRowGap(null);
    }

    public UiStyleLength getColumnGap() {
        return columnGap;
    }

    public UiStyleDeclaration setColumnGap(UiStyleLength columnGap) {
        return updateColumnGap(Objects.requireNonNull(columnGap, "columnGap"));
    }

    public UiStyleDeclaration clearColumnGap() {
        return updateColumnGap(null);
    }

    /**
     * 同时设置 row-gap 与 column-gap。
     *
     * @param gap 间距
     * @return 当前声明
     */
    public UiStyleDeclaration setGap(UiStyleLength gap) {
        UiStyleLength resolvedGap = Objects.requireNonNull(gap, "gap");
        updateRowGap(resolvedGap);
        updateColumnGap(resolvedGap);
        return this;
    }

    public Float getFlexGrow() {
        return flexGrow;
    }

    public UiStyleDeclaration setFlexGrow(float flexGrow) {
        return updateFlexGrow(Float.valueOf(Math.max(0.0F, flexGrow)));
    }

    public UiStyleDeclaration clearFlexGrow() {
        return updateFlexGrow(null);
    }

    public Float getFlexShrink() {
        return flexShrink;
    }

    public UiStyleDeclaration setFlexShrink(float flexShrink) {
        return updateFlexShrink(Float.valueOf(Math.max(0.0F, flexShrink)));
    }

    public UiStyleDeclaration clearFlexShrink() {
        return updateFlexShrink(null);
    }

    public Float getOpacity() {
        return opacity;
    }

    public UiStyleDeclaration setOpacity(float opacity) {
        return updateOpacity(Float.valueOf(Math.max(0.0F, Math.min(1.0F, opacity))));
    }

    public UiStyleDeclaration clearOpacity() {
        return updateOpacity(null);
    }

    public Integer getBackgroundColor() {
        return backgroundColor;
    }

    public UiStyleDeclaration setBackgroundColor(int backgroundColor) {
        return updateBackgroundColor(Integer.valueOf(backgroundColor));
    }

    public UiStyleDeclaration clearBackgroundColor() {
        return updateBackgroundColor(null);
    }

    public Integer getBorderColor() {
        return borderColor;
    }

    public UiStyleDeclaration setBorderColor(int borderColor) {
        return updateBorderColor(Integer.valueOf(borderColor));
    }

    public UiStyleDeclaration clearBorderColor() {
        return updateBorderColor(null);
    }

    public Integer getTextColor() {
        return textColor;
    }

    public UiStyleDeclaration setTextColor(int textColor) {
        return updateTextColor(Integer.valueOf(textColor));
    }

    public UiStyleDeclaration clearTextColor() {
        return updateTextColor(null);
    }

    public List<DocumentAnimationProperty> getTransitionProperties() {
        return transitionProperties;
    }

    public UiStyleDeclaration setTransitionProperties(DocumentAnimationProperty... transitionProperties) {
        if (transitionProperties == null || transitionProperties.length == 0) {
            return updateTransitionProperties(Collections.<DocumentAnimationProperty>emptyList());
        }
        List<DocumentAnimationProperty> properties = new ArrayList<DocumentAnimationProperty>();
        for (DocumentAnimationProperty property : transitionProperties) {
            if (property != null && !properties.contains(property)) {
                properties.add(property);
            }
        }
        return updateTransitionProperties(properties);
    }

    public UiStyleDeclaration clearTransitionProperties() {
        return updateTransitionProperties(null);
    }

    public Long getTransitionDurationNanos() {
        return transitionDurationNanos;
    }

    public UiStyleDeclaration setTransitionDurationMillis(long transitionDurationMillis) {
        return setTransitionDurationNanos(transitionDurationMillis * 1_000_000L);
    }

    public UiStyleDeclaration setTransitionDurationNanos(long transitionDurationNanos) {
        return updateTransitionDurationNanos(Long.valueOf(Math.max(0L, transitionDurationNanos)));
    }

    public UiStyleDeclaration clearTransitionDuration() {
        return updateTransitionDurationNanos(null);
    }

    public Long getTransitionDelayNanos() {
        return transitionDelayNanos;
    }

    public UiStyleDeclaration setTransitionDelayMillis(long transitionDelayMillis) {
        return setTransitionDelayNanos(transitionDelayMillis * 1_000_000L);
    }

    public UiStyleDeclaration setTransitionDelayNanos(long transitionDelayNanos) {
        return updateTransitionDelayNanos(Long.valueOf(Math.max(0L, transitionDelayNanos)));
    }

    public UiStyleDeclaration clearTransitionDelay() {
        return updateTransitionDelayNanos(null);
    }

    public DocumentAnimationTimingFunction getTransitionTimingFunction() {
        return transitionTimingFunction;
    }

    public UiStyleDeclaration setTransitionTimingFunction(DocumentAnimationTimingFunction transitionTimingFunction) {
        return updateTransitionTimingFunction(Objects.requireNonNull(transitionTimingFunction,
                "transitionTimingFunction"));
    }

    public UiStyleDeclaration clearTransitionTimingFunction() {
        return updateTransitionTimingFunction(null);
    }

    /**
     * 设置单属性 transition 便捷声明。
     *
     * @param property transition 属性
     * @param durationMillis 持续时间，单位毫秒
     * @return 当前声明
     */
    public UiStyleDeclaration setTransition(DocumentAnimationProperty property, long durationMillis) {
        setTransitionProperties(Objects.requireNonNull(property, "property"));
        setTransitionDurationMillis(durationMillis);
        return this;
    }

    public UiStyleLength getBackdropBlurRadius() {
        return backdropBlurRadius;
    }

    public UiStyleDeclaration setBackdropBlurRadius(UiStyleLength backdropBlurRadius) {
        return updateBackdropBlurRadius(Objects.requireNonNull(backdropBlurRadius, "backdropBlurRadius"));
    }

    public UiStyleDeclaration clearBackdropBlurRadius() {
        return updateBackdropBlurRadius(null);
    }

    public Float getBackdropSaturation() {
        return backdropSaturation;
    }

    public UiStyleDeclaration setBackdropSaturation(float backdropSaturation) {
        return updateBackdropSaturation(Float.valueOf(Math.max(0.0F, backdropSaturation)));
    }

    public UiStyleDeclaration clearBackdropSaturation() {
        return updateBackdropSaturation(null);
    }

    private UiStyleDeclaration updateDisplay(UiDisplay value) {
        if (display != value) {
            display = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateWidth(UiStyleLength value) {
        if (!Objects.equals(width, value)) {
            width = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateHeight(UiStyleLength value) {
        if (!Objects.equals(height, value)) {
            height = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updatePosition(UiPosition value) {
        if (position != value) {
            position = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTop(UiStyleLength value) {
        if (!Objects.equals(top, value)) {
            top = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateRight(UiStyleLength value) {
        if (!Objects.equals(right, value)) {
            right = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBottom(UiStyleLength value) {
        if (!Objects.equals(bottom, value)) {
            bottom = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateLeft(UiStyleLength value) {
        if (!Objects.equals(left, value)) {
            left = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateZIndex(Integer value) {
        if (!Objects.equals(zIndex, value)) {
            zIndex = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateMargin(UiStyleInsets value) {
        if (!Objects.equals(margin, value)) {
            margin = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updatePadding(UiStyleInsets value) {
        if (!Objects.equals(padding, value)) {
            padding = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBorderWidth(UiStyleLength value) {
        if (!Objects.equals(borderWidth, value)) {
            borderWidth = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBorderRadius(UiStyleLength value) {
        if (!Objects.equals(borderRadius, value)) {
            borderRadius = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateOverflowX(UiOverflow value) {
        if (overflowX != value) {
            overflowX = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateOverflowY(UiOverflow value) {
        if (overflowY != value) {
            overflowY = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateFlexDirection(UiFlexDirection value) {
        if (flexDirection != value) {
            flexDirection = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateAlignItems(UiAlignItems value) {
        if (alignItems != value) {
            alignItems = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateJustifyContent(UiJustifyContent value) {
        if (justifyContent != value) {
            justifyContent = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateRowGap(UiStyleLength value) {
        if (!Objects.equals(rowGap, value)) {
            rowGap = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateColumnGap(UiStyleLength value) {
        if (!Objects.equals(columnGap, value)) {
            columnGap = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateFlexGrow(Float value) {
        if (!Objects.equals(flexGrow, value)) {
            flexGrow = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateFlexShrink(Float value) {
        if (!Objects.equals(flexShrink, value)) {
            flexShrink = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateOpacity(Float value) {
        if (!Objects.equals(opacity, value)) {
            opacity = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBackgroundColor(Integer value) {
        if (!Objects.equals(backgroundColor, value)) {
            backgroundColor = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBorderColor(Integer value) {
        if (!Objects.equals(borderColor, value)) {
            borderColor = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTextColor(Integer value) {
        if (!Objects.equals(textColor, value)) {
            textColor = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTransitionProperties(List<DocumentAnimationProperty> value) {
        List<DocumentAnimationProperty> nextValue = value == null ? null
                : Collections.unmodifiableList(new ArrayList<DocumentAnimationProperty>(value));
        if (!Objects.equals(transitionProperties, nextValue)) {
            transitionProperties = nextValue;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTransitionDurationNanos(Long value) {
        if (!Objects.equals(transitionDurationNanos, value)) {
            transitionDurationNanos = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTransitionDelayNanos(Long value) {
        if (!Objects.equals(transitionDelayNanos, value)) {
            transitionDelayNanos = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTransitionTimingFunction(DocumentAnimationTimingFunction value) {
        if (transitionTimingFunction != value) {
            transitionTimingFunction = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBackdropBlurRadius(UiStyleLength value) {
        if (!Objects.equals(backdropBlurRadius, value)) {
            backdropBlurRadius = value;
            recordChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBackdropSaturation(Float value) {
        if (!Objects.equals(backdropSaturation, value)) {
            backdropSaturation = value;
            recordChange();
        }
        return this;
    }

    private void recordChange() {
        changeListener.run();
    }
}
