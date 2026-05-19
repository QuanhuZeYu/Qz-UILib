package club.heiqi.uilib.ui.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;

/**
 * 元素作者侧样式声明。
 *
 * <p>该类型只保存显式声明的属性；缺省值和继承值由 computed style 阶段解析。</p>
 */
public final class UiStyleDeclaration {

    private static final UiStyleChangeListener NO_OP_CHANGE_LISTENER = new UiStyleChangeListener() {
        @Override
        public void onStyleChanged(UiStyleChangeImpact impact) {}
    };

    private final UiStyleChangeListener changeListener;
    private UiDisplay display;
    private UiStyleLength width;
    private UiStyleLength height;
    private UiBoxSizing boxSizing;
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
    private UiVerticalAlign verticalAlign;
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
    private String animationName;
    private Long animationDurationNanos;
    private Long animationDelayNanos;
    private Integer animationIterationCount;
    private DocumentAnimationFillMode animationFillMode;
    private DocumentAnimationTimingFunction animationTimingFunction;
    private UiStyleLength backdropBlurRadius;
    private Float backdropSaturation;
    private UiStyleLength lineHeight;
    private UiTextAlign textAlign;
    private UiWhiteSpace whiteSpace;
    private UiTextOverflow textOverflow;
    private UiVisibility visibility;
    private UiStyleLength minWidth;
    private UiStyleLength maxWidth;
    private UiStyleLength minHeight;
    private UiStyleLength maxHeight;
    private UiStyleLength flexBasis;
    private UiAlignSelf alignSelf;
    private UiFlexWrap flexWrap;
    private UiBoxShadow boxShadow;
    private UiBorderStyle borderStyle;
    private UiCursor cursor;
    private UiBorderRadius borderRadiusCorners;
    private UiTextDecoration textDecoration;
    private UiFontWeight fontWeight;
    private UiFontStyle fontStyle;
    private UiPointerEvents pointerEvents;
    private UiOutline outline;
    private UiStyleInsets borderWidthSides;
    private UiBorderColors borderColors;
    private UiStyleLength letterSpacing;
    private UiWordBreak wordBreak;
    private UiOverflowWrap overflowWrap;
    private Float aspectRatio;
    private UiObjectFit objectFit;
    private final EnumMap<UiStyleProperty, UiStyleKeyword> keywords =
            new EnumMap<UiStyleProperty, UiStyleKeyword>(UiStyleProperty.class);
    private final EnumSet<UiStyleProperty> importantProperties = EnumSet.noneOf(UiStyleProperty.class);

    public UiStyleDeclaration() {
        this((UiStyleChangeListener) null);
    }

    public UiStyleDeclaration(Runnable changeListener) {
        this(changeListener == null ? null : new RunnableStyleChangeListener(changeListener));
    }

    public UiStyleDeclaration(UiStyleChangeListener changeListener) {
        this.changeListener = changeListener == null ? NO_OP_CHANGE_LISTENER : changeListener;
    }

    /**
     * 返回指定属性的级联关键字声明。
     *
     * @param property 样式属性
     * @return 级联关键字；未声明时返回 null
     */
    public UiStyleKeyword getKeyword(UiStyleProperty property) {
        UiStyleProperty resolvedProperty = Objects.requireNonNull(property, "property");
        return hasConcreteProperty(resolvedProperty) ? null : keywords.get(resolvedProperty);
    }

    /**
     * 设置指定属性的级联关键字。
     *
     * <p>设置关键字会清除同属性当前的类型化值；例如对 {@code WIDTH} 设置
     * {@code INHERIT} 后，原先的 {@link #setWidth(UiStyleLength)} 声明不再参与级联。</p>
     *
     * @param property 样式属性
     * @param keyword 级联关键字
     * @return 当前声明
     */
    public UiStyleDeclaration setKeyword(UiStyleProperty property, UiStyleKeyword keyword) {
        UiStyleProperty resolvedProperty = Objects.requireNonNull(property, "property");
        UiStyleKeyword resolvedKeyword = Objects.requireNonNull(keyword, "keyword");
        UiStyleKeyword previousKeyword = keywords.get(resolvedProperty);
        boolean hadConcreteValue = hasConcreteProperty(resolvedProperty);
        clearConcreteProperty(resolvedProperty);
        if (previousKeyword != resolvedKeyword) {
            keywords.put(resolvedProperty, resolvedKeyword);
        }
        if (hadConcreteValue || previousKeyword != resolvedKeyword) {
            recordPropertyChange(resolvedProperty);
        }
        return this;
    }

    /**
     * 清除指定属性的级联关键字声明。
     *
     * @param property 样式属性
     * @return 当前声明
     */
    public UiStyleDeclaration clearKeyword(UiStyleProperty property) {
        UiStyleProperty resolvedProperty = Objects.requireNonNull(property, "property");
        if (keywords.remove(resolvedProperty) != null) {
            recordPropertyChange(resolvedProperty);
        }
        return this;
    }

    /**
     * 将指定属性声明标记为 {@code !important}。
     *
     * @param property 样式属性
     * @return 当前声明
     */
    public UiStyleDeclaration setImportant(UiStyleProperty property) {
        return setImportant(property, true);
    }

    /**
     * 设置或取消指定属性的 {@code !important} 标记。
     *
     * @param property 样式属性
     * @param important 是否重要声明
     * @return 当前声明
     */
    public UiStyleDeclaration setImportant(UiStyleProperty property, boolean important) {
        UiStyleProperty resolvedProperty = Objects.requireNonNull(property, "property");
        boolean changed = important ? importantProperties.add(resolvedProperty)
                : importantProperties.remove(resolvedProperty);
        if (changed) {
            recordPropertyChange(resolvedProperty);
        }
        return this;
    }

    /**
     * 清除指定属性的 {@code !important} 标记。
     *
     * @param property 样式属性
     * @return 当前声明
     */
    public UiStyleDeclaration clearImportant(UiStyleProperty property) {
        return setImportant(property, false);
    }

    /**
     * 判断指定属性是否被标记为 {@code !important}。
     *
     * @param property 样式属性
     * @return 是否重要声明
     */
    public boolean isImportant(UiStyleProperty property) {
        Objects.requireNonNull(property, "property");
        return importantProperties.contains(property);
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

    public UiBoxSizing getBoxSizing() {
        return boxSizing;
    }

    public UiStyleDeclaration setBoxSizing(UiBoxSizing boxSizing) {
        return updateBoxSizing(Objects.requireNonNull(boxSizing, "boxSizing"));
    }

    public UiStyleDeclaration clearBoxSizing() {
        return updateBoxSizing(null);
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

    public UiVerticalAlign getVerticalAlign() {
        return verticalAlign;
    }

    public UiStyleDeclaration setVerticalAlign(UiVerticalAlign verticalAlign) {
        return updateVerticalAlign(Objects.requireNonNull(verticalAlign, "verticalAlign"));
    }

    public UiStyleDeclaration clearVerticalAlign() {
        return updateVerticalAlign(null);
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

    public String getAnimationName() {
        return animationName;
    }

    public UiStyleDeclaration setAnimationName(String animationName) {
        String resolvedName = Objects.requireNonNull(animationName, "animationName").trim();
        if (resolvedName.isEmpty()) {
            throw new IllegalArgumentException("animationName cannot be empty");
        }
        return updateAnimationName(resolvedName);
    }

    public UiStyleDeclaration clearAnimationName() {
        return updateAnimationName(null);
    }

    public Long getAnimationDurationNanos() {
        return animationDurationNanos;
    }

    public UiStyleDeclaration setAnimationDurationMillis(long animationDurationMillis) {
        return setAnimationDurationNanos(animationDurationMillis * 1_000_000L);
    }

    public UiStyleDeclaration setAnimationDurationNanos(long animationDurationNanos) {
        return updateAnimationDurationNanos(Long.valueOf(Math.max(0L, animationDurationNanos)));
    }

    public UiStyleDeclaration clearAnimationDuration() {
        return updateAnimationDurationNanos(null);
    }

    public Long getAnimationDelayNanos() {
        return animationDelayNanos;
    }

    public UiStyleDeclaration setAnimationDelayMillis(long animationDelayMillis) {
        return setAnimationDelayNanos(animationDelayMillis * 1_000_000L);
    }

    public UiStyleDeclaration setAnimationDelayNanos(long animationDelayNanos) {
        return updateAnimationDelayNanos(Long.valueOf(Math.max(0L, animationDelayNanos)));
    }

    public UiStyleDeclaration clearAnimationDelay() {
        return updateAnimationDelayNanos(null);
    }

    public Integer getAnimationIterationCount() {
        return animationIterationCount;
    }

    public UiStyleDeclaration setAnimationIterationCount(int animationIterationCount) {
        return updateAnimationIterationCount(Integer.valueOf(Math.max(1, animationIterationCount)));
    }

    public UiStyleDeclaration clearAnimationIterationCount() {
        return updateAnimationIterationCount(null);
    }

    public DocumentAnimationFillMode getAnimationFillMode() {
        return animationFillMode;
    }

    public UiStyleDeclaration setAnimationFillMode(DocumentAnimationFillMode animationFillMode) {
        return updateAnimationFillMode(Objects.requireNonNull(animationFillMode, "animationFillMode"));
    }

    public UiStyleDeclaration clearAnimationFillMode() {
        return updateAnimationFillMode(null);
    }

    public DocumentAnimationTimingFunction getAnimationTimingFunction() {
        return animationTimingFunction;
    }

    public UiStyleDeclaration setAnimationTimingFunction(DocumentAnimationTimingFunction animationTimingFunction) {
        return updateAnimationTimingFunction(Objects.requireNonNull(animationTimingFunction, "animationTimingFunction"));
    }

    public UiStyleDeclaration clearAnimationTimingFunction() {
        return updateAnimationTimingFunction(null);
    }

    /**
     * 设置单个 keyframe animation 便捷声明。
     *
     * @param animationName keyframes 名称
     * @param durationMillis 持续时间，单位毫秒
     * @return 当前声明
     */
    public UiStyleDeclaration setAnimation(String animationName, long durationMillis) {
        setAnimationName(animationName);
        setAnimationDurationMillis(durationMillis);
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

    public UiStyleLength getLineHeight() {
        return lineHeight;
    }

    /**
     * 设置行高。auto 表示跟随字体默认行高。
     *
     * @param lineHeight 行高
     * @return 当前声明
     */
    public UiStyleDeclaration setLineHeight(UiStyleLength lineHeight) {
        return updateLineHeight(Objects.requireNonNull(lineHeight, "lineHeight"));
    }

    public UiStyleDeclaration clearLineHeight() {
        return updateLineHeight(null);
    }

    public UiTextAlign getTextAlign() {
        return textAlign;
    }

    /**
     * 设置文本水平对齐方式。
     *
     * @param textAlign 对齐方式
     * @return 当前声明
     */
    public UiStyleDeclaration setTextAlign(UiTextAlign textAlign) {
        return updateTextAlign(Objects.requireNonNull(textAlign, "textAlign"));
    }

    public UiStyleDeclaration clearTextAlign() {
        return updateTextAlign(null);
    }

    public UiWhiteSpace getWhiteSpace() {
        return whiteSpace;
    }

    /**
     * 设置空白字符处理与换行行为。
     *
     * @param whiteSpace 空白处理
     * @return 当前声明
     */
    public UiStyleDeclaration setWhiteSpace(UiWhiteSpace whiteSpace) {
        return updateWhiteSpace(Objects.requireNonNull(whiteSpace, "whiteSpace"));
    }

    public UiStyleDeclaration clearWhiteSpace() {
        return updateWhiteSpace(null);
    }

    public UiTextOverflow getTextOverflow() {
        return textOverflow;
    }

    /**
     * 设置文本溢出处理方式。
     *
     * @param textOverflow 文本溢出
     * @return 当前声明
     */
    public UiStyleDeclaration setTextOverflow(UiTextOverflow textOverflow) {
        return updateTextOverflow(Objects.requireNonNull(textOverflow, "textOverflow"));
    }

    public UiStyleDeclaration clearTextOverflow() {
        return updateTextOverflow(null);
    }

    public UiVisibility getVisibility() {
        return visibility;
    }

    /**
     * 设置元素可见性。{@code HIDDEN} 保留布局空间但不可见且不响应命中测试。
     *
     * @param visibility 可见性
     * @return 当前声明
     */
    public UiStyleDeclaration setVisibility(UiVisibility visibility) {
        return updateVisibility(Objects.requireNonNull(visibility, "visibility"));
    }

    public UiStyleDeclaration clearVisibility() {
        return updateVisibility(null);
    }

    public UiStyleLength getMinWidth() {
        return minWidth;
    }

    /**
     * 设置最小宽度约束。
     *
     * @param minWidth 最小宽度
     * @return 当前声明
     */
    public UiStyleDeclaration setMinWidth(UiStyleLength minWidth) {
        return updateMinWidth(Objects.requireNonNull(minWidth, "minWidth"));
    }

    public UiStyleDeclaration clearMinWidth() {
        return updateMinWidth(null);
    }

    public UiStyleLength getMaxWidth() {
        return maxWidth;
    }

    /**
     * 设置最大宽度约束。auto 表示无上限。
     *
     * @param maxWidth 最大宽度
     * @return 当前声明
     */
    public UiStyleDeclaration setMaxWidth(UiStyleLength maxWidth) {
        return updateMaxWidth(Objects.requireNonNull(maxWidth, "maxWidth"));
    }

    public UiStyleDeclaration clearMaxWidth() {
        return updateMaxWidth(null);
    }

    public UiStyleLength getMinHeight() {
        return minHeight;
    }

    /**
     * 设置最小高度约束。
     *
     * @param minHeight 最小高度
     * @return 当前声明
     */
    public UiStyleDeclaration setMinHeight(UiStyleLength minHeight) {
        return updateMinHeight(Objects.requireNonNull(minHeight, "minHeight"));
    }

    public UiStyleDeclaration clearMinHeight() {
        return updateMinHeight(null);
    }

    public UiStyleLength getMaxHeight() {
        return maxHeight;
    }

    /**
     * 设置最大高度约束。auto 表示无上限。
     *
     * @param maxHeight 最大高度
     * @return 当前声明
     */
    public UiStyleDeclaration setMaxHeight(UiStyleLength maxHeight) {
        return updateMaxHeight(Objects.requireNonNull(maxHeight, "maxHeight"));
    }

    public UiStyleDeclaration clearMaxHeight() {
        return updateMaxHeight(null);
    }

    public UiStyleLength getFlexBasis() {
        return flexBasis;
    }

    /**
     * 设置 flex item 主轴初始尺寸。auto 时退回 width/height。
     *
     * @param flexBasis flex-basis
     * @return 当前声明
     */
    public UiStyleDeclaration setFlexBasis(UiStyleLength flexBasis) {
        return updateFlexBasis(Objects.requireNonNull(flexBasis, "flexBasis"));
    }

    public UiStyleDeclaration clearFlexBasis() {
        return updateFlexBasis(null);
    }

    public UiAlignSelf getAlignSelf() {
        return alignSelf;
    }

    /**
     * 设置 flex item 交叉轴对齐方式（覆盖父容器 align-items）。
     *
     * @param alignSelf align-self
     * @return 当前声明
     */
    public UiStyleDeclaration setAlignSelf(UiAlignSelf alignSelf) {
        return updateAlignSelf(Objects.requireNonNull(alignSelf, "alignSelf"));
    }

    public UiStyleDeclaration clearAlignSelf() {
        return updateAlignSelf(null);
    }

    public UiFlexWrap getFlexWrap() {
        return flexWrap;
    }

    /**
     * 设置 flex 换行行为。
     *
     * @param flexWrap flex-wrap
     * @return 当前声明
     */
    public UiStyleDeclaration setFlexWrap(UiFlexWrap flexWrap) {
        return updateFlexWrap(Objects.requireNonNull(flexWrap, "flexWrap"));
    }

    public UiStyleDeclaration clearFlexWrap() {
        return updateFlexWrap(null);
    }

    public UiBoxShadow getBoxShadow() {
        return boxShadow;
    }

    /**
     * 设置元素阴影。
     *
     * @param boxShadow 阴影值
     * @return 当前声明
     */
    public UiStyleDeclaration setBoxShadow(UiBoxShadow boxShadow) {
        return updateBoxShadow(Objects.requireNonNull(boxShadow, "boxShadow"));
    }

    public UiStyleDeclaration clearBoxShadow() {
        return updateBoxShadow(null);
    }

    public UiBorderStyle getBorderStyle() {
        return borderStyle;
    }

    /**
     * 设置边框样式。
     *
     * @param borderStyle 边框样式
     * @return 当前声明
     */
    public UiStyleDeclaration setBorderStyle(UiBorderStyle borderStyle) {
        return updateBorderStyle(Objects.requireNonNull(borderStyle, "borderStyle"));
    }

    public UiStyleDeclaration clearBorderStyle() {
        return updateBorderStyle(null);
    }

    public UiCursor getCursor() {
        return cursor;
    }

    /**
     * 设置光标样式。
     *
     * @param cursor 光标样式
     * @return 当前声明
     */
    public UiStyleDeclaration setCursor(UiCursor cursor) {
        return updateCursor(Objects.requireNonNull(cursor, "cursor"));
    }

    public UiStyleDeclaration clearCursor() {
        return updateCursor(null);
    }

    /**
     * 返回分角圆角值。
     *
     * <p>当设置了分角圆角时，优先于统一 borderRadius 生效。</p>
     *
     * @return 分角圆角值；未设置时返回 null
     */
    public UiBorderRadius getBorderRadiusCorners() {
        return borderRadiusCorners;
    }

    /**
     * 设置分角圆角。
     *
     * <p>设置后优先于统一 borderRadius 生效。</p>
     *
     * @param borderRadiusCorners 分角圆角值
     * @return 当前声明
     */
    public UiStyleDeclaration setBorderRadiusCorners(UiBorderRadius borderRadiusCorners) {
        return updateBorderRadiusCorners(Objects.requireNonNull(borderRadiusCorners, "borderRadiusCorners"));
    }

    public UiStyleDeclaration clearBorderRadiusCorners() {
        return updateBorderRadiusCorners(null);
    }

    public UiTextDecoration getTextDecoration() {
        return textDecoration;
    }

    /**
     * 设置文本装饰线样式。
     *
     * @param textDecoration 文本装饰线
     * @return 当前声明
     */
    public UiStyleDeclaration setTextDecoration(UiTextDecoration textDecoration) {
        return updateTextDecoration(Objects.requireNonNull(textDecoration, "textDecoration"));
    }

    public UiStyleDeclaration clearTextDecoration() {
        return updateTextDecoration(null);
    }

    public UiFontWeight getFontWeight() {
        return fontWeight;
    }

    /**
     * 设置字体粗细。
     *
     * @param fontWeight 字体粗细
     * @return 当前声明
     */
    public UiStyleDeclaration setFontWeight(UiFontWeight fontWeight) {
        return updateFontWeight(Objects.requireNonNull(fontWeight, "fontWeight"));
    }

    public UiStyleDeclaration clearFontWeight() {
        return updateFontWeight(null);
    }

    public UiFontStyle getFontStyle() {
        return fontStyle;
    }

    /**
     * 设置字体样式。
     *
     * @param fontStyle 字体样式
     * @return 当前声明
     */
    public UiStyleDeclaration setFontStyle(UiFontStyle fontStyle) {
        return updateFontStyle(Objects.requireNonNull(fontStyle, "fontStyle"));
    }

    public UiStyleDeclaration clearFontStyle() {
        return updateFontStyle(null);
    }

    public UiPointerEvents getPointerEvents() {
        return pointerEvents;
    }

    /**
     * 设置元素的指针事件响应模式。
     *
     * <p>{@code NONE} 表示元素不响应鼠标事件，事件穿透到下层。</p>
     *
     * @param pointerEvents 指针事件模式
     * @return 当前声明
     */
    public UiStyleDeclaration setPointerEvents(UiPointerEvents pointerEvents) {
        return updatePointerEvents(Objects.requireNonNull(pointerEvents, "pointerEvents"));
    }

    public UiStyleDeclaration clearPointerEvents() {
        return updatePointerEvents(null);
    }

    public UiOutline getOutline() {
        return outline;
    }

    /**
     * 设置轮廓线样式（焦点指示）。
     *
     * <p>outline 不占据布局空间，绘制在 border 外侧。</p>
     *
     * @param outline 轮廓线值
     * @return 当前声明
     */
    public UiStyleDeclaration setOutline(UiOutline outline) {
        return updateOutline(Objects.requireNonNull(outline, "outline"));
    }

    public UiStyleDeclaration clearOutline() {
        return updateOutline(null);
    }

    /**
     * 返回分边 border-width 值。
     *
     * <p>当设置了分边 border-width 时，优先于统一 borderWidth 生效。</p>
     *
     * @return 分边 border-width；未设置时返回 null
     */
    public UiStyleInsets getBorderWidthSides() {
        return borderWidthSides;
    }

    /**
     * 设置分边 border-width。
     *
     * <p>设置后优先于统一 borderWidth 生效。</p>
     *
     * @param borderWidthSides 分边 border-width（top/right/bottom/left）
     * @return 当前声明
     */
    public UiStyleDeclaration setBorderWidthSides(UiStyleInsets borderWidthSides) {
        return updateBorderWidthSides(Objects.requireNonNull(borderWidthSides, "borderWidthSides"));
    }

    public UiStyleDeclaration clearBorderWidthSides() {
        return updateBorderWidthSides(null);
    }

    /**
     * 返回分边 border-color 值。
     *
     * <p>当设置了分边 border-color 时，优先于统一 borderColor 生效。</p>
     *
     * @return 分边 border-color；未设置时返回 null
     */
    public UiBorderColors getBorderColors() {
        return borderColors;
    }

    /**
     * 设置分边 border-color。
     *
     * <p>设置后优先于统一 borderColor 生效。</p>
     *
     * @param borderColors 分边 border-color
     * @return 当前声明
     */
    public UiStyleDeclaration setBorderColors(UiBorderColors borderColors) {
        return updateBorderColors(Objects.requireNonNull(borderColors, "borderColors"));
    }

    public UiStyleDeclaration clearBorderColors() {
        return updateBorderColors(null);
    }

    public UiStyleLength getLetterSpacing() {
        return letterSpacing;
    }

    /**
     * 设置字间距。
     *
     * @param letterSpacing 字间距
     * @return 当前声明
     */
    public UiStyleDeclaration setLetterSpacing(UiStyleLength letterSpacing) {
        return updateLetterSpacing(Objects.requireNonNull(letterSpacing, "letterSpacing"));
    }

    public UiStyleDeclaration clearLetterSpacing() {
        return updateLetterSpacing(null);
    }

    public UiWordBreak getWordBreak() {
        return wordBreak;
    }

    /**
     * 设置单词断行规则。
     *
     * @param wordBreak 断行规则
     * @return 当前声明
     */
    public UiStyleDeclaration setWordBreak(UiWordBreak wordBreak) {
        return updateWordBreak(Objects.requireNonNull(wordBreak, "wordBreak"));
    }

    public UiStyleDeclaration clearWordBreak() {
        return updateWordBreak(null);
    }

    public UiOverflowWrap getOverflowWrap() {
        return overflowWrap;
    }

    /**
     * 设置溢出换行规则。
     *
     * @param overflowWrap 溢出换行规则
     * @return 当前声明
     */
    public UiStyleDeclaration setOverflowWrap(UiOverflowWrap overflowWrap) {
        return updateOverflowWrap(Objects.requireNonNull(overflowWrap, "overflowWrap"));
    }

    public UiStyleDeclaration clearOverflowWrap() {
        return updateOverflowWrap(null);
    }

    public Float getAspectRatio() {
        return aspectRatio;
    }

    /**
     * 设置宽高比约束。
     *
     * <p>值为 width/height 比率，例如 16.0f/9.0f 表示 16:9。
     * 当元素只有一个维度确定时，另一个维度会按此比率自动计算。</p>
     *
     * @param aspectRatio 宽高比（width/height）
     * @return 当前声明
     */
    public UiStyleDeclaration setAspectRatio(float aspectRatio) {
        return updateAspectRatio(Float.valueOf(aspectRatio));
    }

    public UiStyleDeclaration clearAspectRatio() {
        return updateAspectRatio(null);
    }

    public UiObjectFit getObjectFit() {
        return objectFit;
    }

    /**
     * 设置替换元素（如 img）的内容适配模式。
     *
     * @param objectFit 内容适配模式
     * @return 当前声明
     */
    public UiStyleDeclaration setObjectFit(UiObjectFit objectFit) {
        return updateObjectFit(Objects.requireNonNull(objectFit, "objectFit"));
    }

    public UiStyleDeclaration clearObjectFit() {
        return updateObjectFit(null);
    }

    private UiStyleDeclaration updateDisplay(UiDisplay value) {
        if (display != value) {
            display = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateWidth(UiStyleLength value) {
        if (!Objects.equals(width, value)) {
            width = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateHeight(UiStyleLength value) {
        if (!Objects.equals(height, value)) {
            height = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBoxSizing(UiBoxSizing value) {
        if (boxSizing != value) {
            boxSizing = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updatePosition(UiPosition value) {
        if (position != value) {
            position = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTop(UiStyleLength value) {
        if (!Objects.equals(top, value)) {
            top = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateRight(UiStyleLength value) {
        if (!Objects.equals(right, value)) {
            right = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBottom(UiStyleLength value) {
        if (!Objects.equals(bottom, value)) {
            bottom = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateLeft(UiStyleLength value) {
        if (!Objects.equals(left, value)) {
            left = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateZIndex(Integer value) {
        if (!Objects.equals(zIndex, value)) {
            zIndex = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateMargin(UiStyleInsets value) {
        if (!Objects.equals(margin, value)) {
            margin = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updatePadding(UiStyleInsets value) {
        if (!Objects.equals(padding, value)) {
            padding = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBorderWidth(UiStyleLength value) {
        if (!Objects.equals(borderWidth, value)) {
            borderWidth = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBorderRadius(UiStyleLength value) {
        if (!Objects.equals(borderRadius, value)) {
            borderRadius = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateOverflowX(UiOverflow value) {
        if (overflowX != value) {
            overflowX = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateOverflowY(UiOverflow value) {
        if (overflowY != value) {
            overflowY = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateFlexDirection(UiFlexDirection value) {
        if (flexDirection != value) {
            flexDirection = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateAlignItems(UiAlignItems value) {
        if (alignItems != value) {
            alignItems = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateJustifyContent(UiJustifyContent value) {
        if (justifyContent != value) {
            justifyContent = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateVerticalAlign(UiVerticalAlign value) {
        if (verticalAlign != value) {
            verticalAlign = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateRowGap(UiStyleLength value) {
        if (!Objects.equals(rowGap, value)) {
            rowGap = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateColumnGap(UiStyleLength value) {
        if (!Objects.equals(columnGap, value)) {
            columnGap = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateFlexGrow(Float value) {
        if (!Objects.equals(flexGrow, value)) {
            flexGrow = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateFlexShrink(Float value) {
        if (!Objects.equals(flexShrink, value)) {
            flexShrink = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateOpacity(Float value) {
        if (!Objects.equals(opacity, value)) {
            opacity = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBackgroundColor(Integer value) {
        if (!Objects.equals(backgroundColor, value)) {
            backgroundColor = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBorderColor(Integer value) {
        if (!Objects.equals(borderColor, value)) {
            borderColor = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTextColor(Integer value) {
        if (!Objects.equals(textColor, value)) {
            textColor = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTransitionProperties(List<DocumentAnimationProperty> value) {
        List<DocumentAnimationProperty> nextValue = value == null ? null
                : Collections.unmodifiableList(new ArrayList<DocumentAnimationProperty>(value));
        if (!Objects.equals(transitionProperties, nextValue)) {
            transitionProperties = nextValue;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTransitionDurationNanos(Long value) {
        if (!Objects.equals(transitionDurationNanos, value)) {
            transitionDurationNanos = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTransitionDelayNanos(Long value) {
        if (!Objects.equals(transitionDelayNanos, value)) {
            transitionDelayNanos = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTransitionTimingFunction(DocumentAnimationTimingFunction value) {
        if (transitionTimingFunction != value) {
            transitionTimingFunction = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateAnimationName(String value) {
        if (!Objects.equals(animationName, value)) {
            animationName = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateAnimationDurationNanos(Long value) {
        if (!Objects.equals(animationDurationNanos, value)) {
            animationDurationNanos = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateAnimationDelayNanos(Long value) {
        if (!Objects.equals(animationDelayNanos, value)) {
            animationDelayNanos = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateAnimationIterationCount(Integer value) {
        if (!Objects.equals(animationIterationCount, value)) {
            animationIterationCount = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateAnimationFillMode(DocumentAnimationFillMode value) {
        if (animationFillMode != value) {
            animationFillMode = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateAnimationTimingFunction(DocumentAnimationTimingFunction value) {
        if (animationTimingFunction != value) {
            animationTimingFunction = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBackdropBlurRadius(UiStyleLength value) {
        if (!Objects.equals(backdropBlurRadius, value)) {
            backdropBlurRadius = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBackdropSaturation(Float value) {
        if (!Objects.equals(backdropSaturation, value)) {
            backdropSaturation = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateLineHeight(UiStyleLength value) {
        if (!Objects.equals(lineHeight, value)) {
            lineHeight = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTextAlign(UiTextAlign value) {
        if (textAlign != value) {
            textAlign = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateWhiteSpace(UiWhiteSpace value) {
        if (whiteSpace != value) {
            whiteSpace = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTextOverflow(UiTextOverflow value) {
        if (textOverflow != value) {
            textOverflow = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateVisibility(UiVisibility value) {
        if (visibility != value) {
            visibility = value;
            // visibility 变化不影响布局，只影响绘制和命中测试
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateMinWidth(UiStyleLength value) {
        if (!Objects.equals(minWidth, value)) {
            minWidth = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateMaxWidth(UiStyleLength value) {
        if (!Objects.equals(maxWidth, value)) {
            maxWidth = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateMinHeight(UiStyleLength value) {
        if (!Objects.equals(minHeight, value)) {
            minHeight = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateMaxHeight(UiStyleLength value) {
        if (!Objects.equals(maxHeight, value)) {
            maxHeight = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateFlexBasis(UiStyleLength value) {
        if (!Objects.equals(flexBasis, value)) {
            flexBasis = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateAlignSelf(UiAlignSelf value) {
        if (alignSelf != value) {
            alignSelf = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateFlexWrap(UiFlexWrap value) {
        if (flexWrap != value) {
            flexWrap = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBoxShadow(UiBoxShadow value) {
        if (!Objects.equals(boxShadow, value)) {
            boxShadow = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBorderStyle(UiBorderStyle value) {
        if (borderStyle != value) {
            borderStyle = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateCursor(UiCursor value) {
        if (cursor != value) {
            cursor = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBorderRadiusCorners(UiBorderRadius value) {
        if (!Objects.equals(borderRadiusCorners, value)) {
            borderRadiusCorners = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateTextDecoration(UiTextDecoration value) {
        if (textDecoration != value) {
            textDecoration = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateFontWeight(UiFontWeight value) {
        if (fontWeight != value) {
            fontWeight = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateFontStyle(UiFontStyle value) {
        if (fontStyle != value) {
            fontStyle = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updatePointerEvents(UiPointerEvents value) {
        if (pointerEvents != value) {
            pointerEvents = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateOutline(UiOutline value) {
        if (!Objects.equals(outline, value)) {
            outline = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBorderWidthSides(UiStyleInsets value) {
        if (!Objects.equals(borderWidthSides, value)) {
            borderWidthSides = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateBorderColors(UiBorderColors value) {
        if (!Objects.equals(borderColors, value)) {
            borderColors = value;
            recordPaintChange();
        }
        return this;
    }

    private UiStyleDeclaration updateLetterSpacing(UiStyleLength value) {
        if (!Objects.equals(letterSpacing, value)) {
            letterSpacing = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateWordBreak(UiWordBreak value) {
        if (wordBreak != value) {
            wordBreak = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateOverflowWrap(UiOverflowWrap value) {
        if (overflowWrap != value) {
            overflowWrap = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateAspectRatio(Float value) {
        if (!Objects.equals(aspectRatio, value)) {
            aspectRatio = value;
            recordLayoutChange();
        }
        return this;
    }

    private UiStyleDeclaration updateObjectFit(UiObjectFit value) {
        if (objectFit != value) {
            objectFit = value;
            recordPaintChange();
        }
        return this;
    }

    private boolean hasConcreteProperty(UiStyleProperty property) {
        switch (property) {
            case DISPLAY: return display != null;
            case WIDTH: return width != null;
            case HEIGHT: return height != null;
            case BOX_SIZING: return boxSizing != null;
            case POSITION: return position != null;
            case TOP: return top != null;
            case RIGHT: return right != null;
            case BOTTOM: return bottom != null;
            case LEFT: return left != null;
            case Z_INDEX: return zIndex != null;
            case MARGIN: return margin != null;
            case PADDING: return padding != null;
            case BORDER_WIDTH: return borderWidth != null;
            case BORDER_RADIUS: return borderRadius != null;
            case OVERFLOW_X: return overflowX != null;
            case OVERFLOW_Y: return overflowY != null;
            case FLEX_DIRECTION: return flexDirection != null;
            case ALIGN_ITEMS: return alignItems != null;
            case JUSTIFY_CONTENT: return justifyContent != null;
            case VERTICAL_ALIGN: return verticalAlign != null;
            case ROW_GAP: return rowGap != null;
            case COLUMN_GAP: return columnGap != null;
            case FLEX_GROW: return flexGrow != null;
            case FLEX_SHRINK: return flexShrink != null;
            case OPACITY: return opacity != null;
            case BACKGROUND_COLOR: return backgroundColor != null;
            case BORDER_COLOR: return borderColor != null;
            case TEXT_COLOR: return textColor != null;
            case TRANSITION_PROPERTIES: return transitionProperties != null;
            case TRANSITION_DURATION: return transitionDurationNanos != null;
            case TRANSITION_DELAY: return transitionDelayNanos != null;
            case TRANSITION_TIMING: return transitionTimingFunction != null;
            case ANIMATION_NAME: return animationName != null;
            case ANIMATION_DURATION: return animationDurationNanos != null;
            case ANIMATION_DELAY: return animationDelayNanos != null;
            case ANIMATION_ITERATION_COUNT: return animationIterationCount != null;
            case ANIMATION_FILL_MODE: return animationFillMode != null;
            case ANIMATION_TIMING: return animationTimingFunction != null;
            case BACKDROP_BLUR_RADIUS: return backdropBlurRadius != null;
            case BACKDROP_SATURATION: return backdropSaturation != null;
            case LINE_HEIGHT: return lineHeight != null;
            case TEXT_ALIGN: return textAlign != null;
            case WHITE_SPACE: return whiteSpace != null;
            case TEXT_OVERFLOW: return textOverflow != null;
            case VISIBILITY: return visibility != null;
            case MIN_WIDTH: return minWidth != null;
            case MAX_WIDTH: return maxWidth != null;
            case MIN_HEIGHT: return minHeight != null;
            case MAX_HEIGHT: return maxHeight != null;
            case FLEX_BASIS: return flexBasis != null;
            case ALIGN_SELF: return alignSelf != null;
            case FLEX_WRAP: return flexWrap != null;
            case BOX_SHADOW: return boxShadow != null;
            case BORDER_STYLE: return borderStyle != null;
            case CURSOR: return cursor != null;
            case BORDER_RADIUS_CORNERS: return borderRadiusCorners != null;
            case TEXT_DECORATION: return textDecoration != null;
            case FONT_WEIGHT: return fontWeight != null;
            case FONT_STYLE: return fontStyle != null;
            case POINTER_EVENTS: return pointerEvents != null;
            case OUTLINE: return outline != null;
            case BORDER_WIDTH_SIDES: return borderWidthSides != null;
            case BORDER_COLORS: return borderColors != null;
            case LETTER_SPACING: return letterSpacing != null;
            case WORD_BREAK: return wordBreak != null;
            case OVERFLOW_WRAP: return overflowWrap != null;
            case ASPECT_RATIO: return aspectRatio != null;
            case OBJECT_FIT: return objectFit != null;
            default: return false;
        }
    }

    private void clearConcreteProperty(UiStyleProperty property) {
        switch (property) {
            case DISPLAY: display = null; break;
            case WIDTH: width = null; break;
            case HEIGHT: height = null; break;
            case BOX_SIZING: boxSizing = null; break;
            case POSITION: position = null; break;
            case TOP: top = null; break;
            case RIGHT: right = null; break;
            case BOTTOM: bottom = null; break;
            case LEFT: left = null; break;
            case Z_INDEX: zIndex = null; break;
            case MARGIN: margin = null; break;
            case PADDING: padding = null; break;
            case BORDER_WIDTH: borderWidth = null; break;
            case BORDER_RADIUS: borderRadius = null; break;
            case OVERFLOW_X: overflowX = null; break;
            case OVERFLOW_Y: overflowY = null; break;
            case FLEX_DIRECTION: flexDirection = null; break;
            case ALIGN_ITEMS: alignItems = null; break;
            case JUSTIFY_CONTENT: justifyContent = null; break;
            case VERTICAL_ALIGN: verticalAlign = null; break;
            case ROW_GAP: rowGap = null; break;
            case COLUMN_GAP: columnGap = null; break;
            case FLEX_GROW: flexGrow = null; break;
            case FLEX_SHRINK: flexShrink = null; break;
            case OPACITY: opacity = null; break;
            case BACKGROUND_COLOR: backgroundColor = null; break;
            case BORDER_COLOR: borderColor = null; break;
            case TEXT_COLOR: textColor = null; break;
            case TRANSITION_PROPERTIES: transitionProperties = null; break;
            case TRANSITION_DURATION: transitionDurationNanos = null; break;
            case TRANSITION_DELAY: transitionDelayNanos = null; break;
            case TRANSITION_TIMING: transitionTimingFunction = null; break;
            case ANIMATION_NAME: animationName = null; break;
            case ANIMATION_DURATION: animationDurationNanos = null; break;
            case ANIMATION_DELAY: animationDelayNanos = null; break;
            case ANIMATION_ITERATION_COUNT: animationIterationCount = null; break;
            case ANIMATION_FILL_MODE: animationFillMode = null; break;
            case ANIMATION_TIMING: animationTimingFunction = null; break;
            case BACKDROP_BLUR_RADIUS: backdropBlurRadius = null; break;
            case BACKDROP_SATURATION: backdropSaturation = null; break;
            case LINE_HEIGHT: lineHeight = null; break;
            case TEXT_ALIGN: textAlign = null; break;
            case WHITE_SPACE: whiteSpace = null; break;
            case TEXT_OVERFLOW: textOverflow = null; break;
            case VISIBILITY: visibility = null; break;
            case MIN_WIDTH: minWidth = null; break;
            case MAX_WIDTH: maxWidth = null; break;
            case MIN_HEIGHT: minHeight = null; break;
            case MAX_HEIGHT: maxHeight = null; break;
            case FLEX_BASIS: flexBasis = null; break;
            case ALIGN_SELF: alignSelf = null; break;
            case FLEX_WRAP: flexWrap = null; break;
            case BOX_SHADOW: boxShadow = null; break;
            case BORDER_STYLE: borderStyle = null; break;
            case CURSOR: cursor = null; break;
            case BORDER_RADIUS_CORNERS: borderRadiusCorners = null; break;
            case TEXT_DECORATION: textDecoration = null; break;
            case FONT_WEIGHT: fontWeight = null; break;
            case FONT_STYLE: fontStyle = null; break;
            case POINTER_EVENTS: pointerEvents = null; break;
            case OUTLINE: outline = null; break;
            case BORDER_WIDTH_SIDES: borderWidthSides = null; break;
            case BORDER_COLORS: borderColors = null; break;
            case LETTER_SPACING: letterSpacing = null; break;
            case WORD_BREAK: wordBreak = null; break;
            case OVERFLOW_WRAP: overflowWrap = null; break;
            case ASPECT_RATIO: aspectRatio = null; break;
            case OBJECT_FIT: objectFit = null; break;
            default: break;
        }
    }

    private void recordPropertyChange(UiStyleProperty property) {
        recordChange(property.getChangeImpact());
    }

    private void recordLayoutChange() {
        recordChange(UiStyleChangeImpact.LAYOUT);
    }

    private void recordPaintChange() {
        recordChange(UiStyleChangeImpact.PAINT);
    }

    private void recordChange(UiStyleChangeImpact impact) {
        changeListener.onStyleChanged(impact);
    }

    /**
     * 兼容旧 Runnable 监听器的适配器。
     */
    private static final class RunnableStyleChangeListener implements UiStyleChangeListener {

        private final Runnable runnable;

        private RunnableStyleChangeListener(Runnable runnable) {
            this.runnable = Objects.requireNonNull(runnable, "runnable");
        }

        @Override
        public void onStyleChanged(UiStyleChangeImpact impact) {
            runnable.run();
        }
    }
}
