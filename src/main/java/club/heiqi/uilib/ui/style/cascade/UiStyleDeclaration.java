package club.heiqi.uilib.ui.style.cascade;

import club.heiqi.uilib.ui.style.UiStyleProperty;
import club.heiqi.uilib.ui.style.UiStyleChangeImpact;
import club.heiqi.uilib.ui.style.UiStyleChangeListener;

import club.heiqi.uilib.ui.style.props.UiTextOverflow;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiBorderRadius;
import club.heiqi.uilib.ui.style.props.UiAlignContent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiTextTransform;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiObjectFit;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.props.UiVerticalAlign;
import club.heiqi.uilib.ui.style.values.UiTextShadow;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiListStyleType;
import club.heiqi.uilib.ui.style.values.UiOutline;
import club.heiqi.uilib.ui.style.values.UiScrollbarColor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.props.UiWordBreak;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.values.UiPseudoElementContent;
import club.heiqi.uilib.ui.style.props.UiScrollbarWidth;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiTextAlign;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.style.values.UiStyleKeyword;
import club.heiqi.uilib.ui.style.props.UiBorderCollapse;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiAlignSelf;
import club.heiqi.uilib.ui.style.props.UiAnimationDirection;
import club.heiqi.uilib.ui.style.values.UiBorderColors;
import club.heiqi.uilib.ui.style.props.UiTextDecoration;
import club.heiqi.uilib.ui.style.values.UiTransform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;
import club.heiqi.uilib.ui.animation.DocumentTransitionSpec;

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
    private final List<UiStyleChangeListener> additionalChangeListeners = new ArrayList<UiStyleChangeListener>();
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
    private UiOverflow overflowX;
    private UiOverflow overflowY;
    private UiFlexDirection flexDirection;
    private UiAlignItems alignItems;
    private UiAlignContent alignContent;
    private UiJustifyContent justifyContent;
    private UiVerticalAlign verticalAlign;
    private UiStyleLength rowGap;
    private UiStyleLength columnGap;
    private Float flexGrow;
    private Float flexShrink;
    private Integer order;
    private List<DocumentAnimationProperty> transitionProperties;
    private Long transitionDurationNanos;
    private Long transitionDelayNanos;
    private DocumentAnimationTimingFunction transitionTimingFunction;
    private List<DocumentTransitionSpec> transitionSpecs;
    private String animationName;
    private Long animationDurationNanos;
    private Long animationDelayNanos;
    private Integer animationIterationCount;
    private DocumentAnimationFillMode animationFillMode;
    private DocumentAnimationTimingFunction animationTimingFunction;
    private UiAnimationDirection animationDirection;
    private UiStyleLength lineHeight;
    private UiTextAlign textAlign;
    private UiWhiteSpace whiteSpace;
    private UiTextOverflow textOverflow;
    private UiStyleLength minWidth;
    private UiStyleLength maxWidth;
    private UiStyleLength minHeight;
    private UiStyleLength maxHeight;
    private UiStyleLength flexBasis;
    private UiAlignSelf alignSelf;
    private UiFlexWrap flexWrap;
    private UiBorderCollapse borderCollapse;
    private UiTextTransform textTransform;
    private UiStyleLength textIndent;
    private UiFontWeight fontWeight;
    private UiFontStyle fontStyle;
    private UiStyleInsets borderWidthSides;
    private UiStyleLength letterSpacing;
    private UiWordBreak wordBreak;
    private UiOverflowWrap overflowWrap;
    private Float aspectRatio;
    private UiObjectFit objectFit;
    private UiPseudoElementContent content;
    private UiScrollbarWidth scrollbarWidth;
    private UiListStyleType listStyleType;
    private final StyleDeclarationSlot<UiStyleLength> borderRadiusSlot =
            new StyleDeclarationSlot<UiStyleLength>(UiStyleProperty.BORDER_RADIUS, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<Float> opacitySlot =
            new StyleDeclarationSlot<Float>(UiStyleProperty.OPACITY, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<Integer> backgroundColorSlot =
            new StyleDeclarationSlot<Integer>(UiStyleProperty.BACKGROUND_COLOR, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<Integer> borderColorSlot =
            new StyleDeclarationSlot<Integer>(UiStyleProperty.BORDER_COLOR, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<Integer> textColorSlot =
            new StyleDeclarationSlot<Integer>(UiStyleProperty.TEXT_COLOR, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiStyleLength> backdropBlurRadiusSlot =
            new StyleDeclarationSlot<UiStyleLength>(UiStyleProperty.BACKDROP_BLUR_RADIUS,
                    UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<Float> backdropSaturationSlot =
            new StyleDeclarationSlot<Float>(UiStyleProperty.BACKDROP_SATURATION, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiVisibility> visibilitySlot =
            new StyleDeclarationSlot<UiVisibility>(UiStyleProperty.VISIBILITY, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiBoxShadow> boxShadowSlot =
            new StyleDeclarationSlot<UiBoxShadow>(UiStyleProperty.BOX_SHADOW, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiBorderStyle> borderStyleSlot =
            new StyleDeclarationSlot<UiBorderStyle>(UiStyleProperty.BORDER_STYLE, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiCursor> cursorSlot =
            new StyleDeclarationSlot<UiCursor>(UiStyleProperty.CURSOR, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiBorderRadius> borderRadiusCornersSlot =
            new StyleDeclarationSlot<UiBorderRadius>(UiStyleProperty.BORDER_RADIUS_CORNERS,
                    UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiBackgroundImage> backgroundImageSlot =
            new StyleDeclarationSlot<UiBackgroundImage>(UiStyleProperty.BACKGROUND_IMAGE,
                    UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiTextDecoration> textDecorationSlot =
            new StyleDeclarationSlot<UiTextDecoration>(UiStyleProperty.TEXT_DECORATION, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiTextShadow> textShadowSlot =
            new StyleDeclarationSlot<UiTextShadow>(UiStyleProperty.TEXT_SHADOW, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiPointerEvents> pointerEventsSlot =
            new StyleDeclarationSlot<UiPointerEvents>(UiStyleProperty.POINTER_EVENTS, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiOutline> outlineSlot =
            new StyleDeclarationSlot<UiOutline>(UiStyleProperty.OUTLINE, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiBorderColors> borderColorsSlot =
            new StyleDeclarationSlot<UiBorderColors>(UiStyleProperty.BORDER_COLORS, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiScrollbarColor> scrollbarColorSlot =
            new StyleDeclarationSlot<UiScrollbarColor>(UiStyleProperty.SCROLLBAR_COLOR, UiStyleChangeImpact.PAINT);
    private final StyleDeclarationSlot<UiTransform> transformSlot =
            new StyleDeclarationSlot<UiTransform>(UiStyleProperty.TRANSFORM, UiStyleChangeImpact.PAINT);
    private final EnumMap<UiStyleProperty, Object> declaredValues =
            new EnumMap<UiStyleProperty, Object>(UiStyleProperty.class);
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
     * 增加样式声明变更监听器。
     *
     * @param listener 监听器
     */
    void addChangeListener(UiStyleChangeListener listener) {
        UiStyleChangeListener resolvedListener = Objects.requireNonNull(listener, "listener");
        if (!additionalChangeListeners.contains(resolvedListener)) {
            additionalChangeListeners.add(resolvedListener);
        }
    }

    /**
     * 移除样式声明变更监听器。
     *
     * @param listener 监听器
     */
    void removeChangeListener(UiStyleChangeListener listener) {
        additionalChangeListeners.remove(listener);
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

    @SuppressWarnings("unchecked")
    <T> T getDeclaredValue(UiStyleProperty property) {
        return (T) declaredValues.get(Objects.requireNonNull(property, "property"));
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

    /**
     * 设置上边距，其余边距保持不变（未设置过则视为 0）。
     *
     * @param value 上边距长度
     * @return this
     */
    public UiStyleDeclaration setMarginTop(UiStyleLength value) {
        UiStyleInsets current = margin != null ? margin : UiStyleInsets.zero();
        return setMargin(current.withTop(Objects.requireNonNull(value, "value")));
    }

    /**
     * 设置右边距，其余边距保持不变（未设置过则视为 0）。
     *
     * @param value 右边距长度
     * @return this
     */
    public UiStyleDeclaration setMarginRight(UiStyleLength value) {
        UiStyleInsets current = margin != null ? margin : UiStyleInsets.zero();
        return setMargin(current.withRight(Objects.requireNonNull(value, "value")));
    }

    /**
     * 设置下边距，其余边距保持不变（未设置过则视为 0）。
     *
     * @param value 下边距长度
     * @return this
     */
    public UiStyleDeclaration setMarginBottom(UiStyleLength value) {
        UiStyleInsets current = margin != null ? margin : UiStyleInsets.zero();
        return setMargin(current.withBottom(Objects.requireNonNull(value, "value")));
    }

    /**
     * 设置左边距，其余边距保持不变（未设置过则视为 0）。
     *
     * @param value 左边距长度
     * @return this
     */
    public UiStyleDeclaration setMarginLeft(UiStyleLength value) {
        UiStyleInsets current = margin != null ? margin : UiStyleInsets.zero();
        return setMargin(current.withLeft(Objects.requireNonNull(value, "value")));
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

    /**
     * 设置上内边距，其余内边距保持不变（未设置过则视为 0）。
     *
     * @param value 上内边距长度
     * @return this
     */
    public UiStyleDeclaration setPaddingTop(UiStyleLength value) {
        UiStyleInsets current = padding != null ? padding : UiStyleInsets.zero();
        return setPadding(current.withTop(Objects.requireNonNull(value, "value")));
    }

    /**
     * 设置右内边距，其余内边距保持不变（未设置过则视为 0）。
     *
     * @param value 右内边距长度
     * @return this
     */
    public UiStyleDeclaration setPaddingRight(UiStyleLength value) {
        UiStyleInsets current = padding != null ? padding : UiStyleInsets.zero();
        return setPadding(current.withRight(Objects.requireNonNull(value, "value")));
    }

    /**
     * 设置下内边距，其余内边距保持不变（未设置过则视为 0）。
     *
     * @param value 下内边距长度
     * @return this
     */
    public UiStyleDeclaration setPaddingBottom(UiStyleLength value) {
        UiStyleInsets current = padding != null ? padding : UiStyleInsets.zero();
        return setPadding(current.withBottom(Objects.requireNonNull(value, "value")));
    }

    /**
     * 设置左内边距，其余内边距保持不变（未设置过则视为 0）。
     *
     * @param value 左内边距长度
     * @return this
     */
    public UiStyleDeclaration setPaddingLeft(UiStyleLength value) {
        UiStyleInsets current = padding != null ? padding : UiStyleInsets.zero();
        return setPadding(current.withLeft(Objects.requireNonNull(value, "value")));
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
        return borderRadiusSlot.get();
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

    public UiAlignContent getAlignContent() {
        return alignContent;
    }

    public UiStyleDeclaration setAlignContent(UiAlignContent alignContent) {
        return updateAlignContent(Objects.requireNonNull(alignContent, "alignContent"));
    }

    public UiStyleDeclaration clearAlignContent() {
        return updateAlignContent(null);
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

    public Integer getOrder() {
        return order;
    }

    /**
     * 设置 flex item 的排序权重。
     *
     * @param order 排序权重，默认 0，数值越小越靠前
     * @return 当前声明
     */
    public UiStyleDeclaration setOrder(int order) {
        return updateOrder(Integer.valueOf(order));
    }

    public UiStyleDeclaration clearOrder() {
        return updateOrder(null);
    }

    public Float getOpacity() {
        return opacitySlot.get();
    }

    public UiStyleDeclaration setOpacity(float opacity) {
        return updateOpacity(Float.valueOf(Math.max(0.0F, Math.min(1.0F, opacity))));
    }

    public UiStyleDeclaration clearOpacity() {
        return updateOpacity(null);
    }

    public Integer getBackgroundColor() {
        return backgroundColorSlot.get();
    }

    public UiStyleDeclaration setBackgroundColor(int backgroundColor) {
        return updateBackgroundColor(Integer.valueOf(backgroundColor));
    }

    public UiStyleDeclaration clearBackgroundColor() {
        return updateBackgroundColor(null);
    }

    public Integer getBorderColor() {
        return borderColorSlot.get();
    }

    public UiStyleDeclaration setBorderColor(int borderColor) {
        return updateBorderColor(Integer.valueOf(borderColor));
    }

    public UiStyleDeclaration clearBorderColor() {
        return updateBorderColor(null);
    }

    public Integer getTextColor() {
        return textColorSlot.get();
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
     * 返回 per-property transition 四元组声明。
     *
     * @return transition 条目列表；未声明时返回 null
     */
    public List<DocumentTransitionSpec> getTransitionSpecs() {
        return transitionSpecs;
    }

    /**
     * 设置 per-property transition 四元组列表。
     *
     * <p>声明该列表后，运行时会优先使用列表内对应属性的 duration / delay / timing。</p>
     *
     * @param transitionSpecs transition 条目
     * @return 当前声明
     */
    public UiStyleDeclaration setTransitions(DocumentTransitionSpec... transitionSpecs) {
        if (transitionSpecs == null || transitionSpecs.length == 0) {
            return updateTransitionSpecs(Collections.<DocumentTransitionSpec>emptyList());
        }
        List<DocumentTransitionSpec> specs = new ArrayList<DocumentTransitionSpec>();
        for (DocumentTransitionSpec spec : transitionSpecs) {
            if (spec != null) {
                specs.add(spec);
            }
        }
        return updateTransitionSpecs(specs);
    }

    public UiStyleDeclaration clearTransitions() {
        return updateTransitionSpecs(null);
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

    /**
     * 设置 keyframe animation 的迭代次数。
     *
     * <p>`0` 表示无限迭代，其余非负值表示有限次数。</p>
     *
     * @param animationIterationCount 迭代次数，`0` 表示无限
     * @return 当前声明
     */
    public UiStyleDeclaration setAnimationIterationCount(int animationIterationCount) {
        return updateAnimationIterationCount(Integer.valueOf(Math.max(0, animationIterationCount)));
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
     * 返回 keyframe animation 的播放方向。
     *
     * @return 播放方向；未声明时返回 null
     */
    public UiAnimationDirection getAnimationDirection() {
        return animationDirection;
    }

    /**
     * 设置 keyframe animation 的播放方向。
     *
     * @param animationDirection 播放方向
     * @return 当前声明
     */
    public UiStyleDeclaration setAnimationDirection(UiAnimationDirection animationDirection) {
        return updateAnimationDirection(Objects.requireNonNull(animationDirection, "animationDirection"));
    }

    public UiStyleDeclaration clearAnimationDirection() {
        return updateAnimationDirection(null);
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
        return backdropBlurRadiusSlot.get();
    }

    public UiStyleDeclaration setBackdropBlurRadius(UiStyleLength backdropBlurRadius) {
        return updateBackdropBlurRadius(Objects.requireNonNull(backdropBlurRadius, "backdropBlurRadius"));
    }

    public UiStyleDeclaration clearBackdropBlurRadius() {
        return updateBackdropBlurRadius(null);
    }

    public Float getBackdropSaturation() {
        return backdropSaturationSlot.get();
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
        return visibilitySlot.get();
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
        return boxShadowSlot.get();
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
        return borderStyleSlot.get();
    }

    /**
     * 返回 table 边框合并模式。
     *
     * @return 边框合并模式
     */
    public UiBorderCollapse getBorderCollapse() {
        return borderCollapse;
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

    /**
     * 设置 table 边框合并模式。
     *
     * @param borderCollapse 边框合并模式
     * @return 当前声明
     */
    public UiStyleDeclaration setBorderCollapse(UiBorderCollapse borderCollapse) {
        return updateBorderCollapse(Objects.requireNonNull(borderCollapse, "borderCollapse"));
    }

    public UiStyleDeclaration clearBorderCollapse() {
        return updateBorderCollapse(null);
    }

    public UiCursor getCursor() {
        return cursorSlot.get();
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
        return borderRadiusCornersSlot.get();
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

    public UiBackgroundImage getBackgroundImage() {
        return backgroundImageSlot.get();
    }

    /**
     * 设置元素背景图。
     *
     * <p>当前支持单张宿主图片源，绘制阶段拉伸填充元素 border box。</p>
     *
     * @param backgroundImage 背景图值
     * @return 当前声明
     */
    public UiStyleDeclaration setBackgroundImage(UiBackgroundImage backgroundImage) {
        return updateBackgroundImage(Objects.requireNonNull(backgroundImage, "backgroundImage"));
    }

    public UiStyleDeclaration clearBackgroundImage() {
        return updateBackgroundImage(null);
    }

    public UiTextDecoration getTextDecoration() {
        return textDecorationSlot.get();
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

    public UiTextShadow getTextShadow() {
        return textShadowSlot.get();
    }

    /**
     * 设置文本阴影。
     *
     * @param textShadow 文本阴影值
     * @return 当前声明
     */
    public UiStyleDeclaration setTextShadow(UiTextShadow textShadow) {
        return updateTextShadow(Objects.requireNonNull(textShadow, "textShadow"));
    }

    /**
     * 设置文本阴影便捷声明。
     *
     * @param offsetX X 轴偏移（像素）
     * @param offsetY Y 轴偏移（像素）
     * @param blurRadius 模糊半径（像素）
     * @param color 阴影颜色（ARGB）
     * @return 当前声明
     */
    public UiStyleDeclaration setTextShadow(int offsetX, int offsetY, int blurRadius, int color) {
        return setTextShadow(UiTextShadow.of(offsetX, offsetY, blurRadius, color));
    }

    public UiStyleDeclaration clearTextShadow() {
        return updateTextShadow(null);
    }

    public UiTextTransform getTextTransform() {
        return textTransform;
    }

    /**
     * 设置文本大小写变换。
     *
     * @param textTransform 大小写变换模式
     * @return 当前声明
     */
    public UiStyleDeclaration setTextTransform(UiTextTransform textTransform) {
        return updateTextTransform(Objects.requireNonNull(textTransform, "textTransform"));
    }

    public UiStyleDeclaration clearTextTransform() {
        return updateTextTransform(null);
    }

    public UiStyleLength getTextIndent() {
        return textIndent;
    }

    /**
     * 设置首行文本缩进。
     *
     * @param textIndent 缩进长度
     * @return 当前声明
     */
    public UiStyleDeclaration setTextIndent(UiStyleLength textIndent) {
        return updateTextIndent(Objects.requireNonNull(textIndent, "textIndent"));
    }

    public UiStyleDeclaration clearTextIndent() {
        return updateTextIndent(null);
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
        return pointerEventsSlot.get();
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
        return outlineSlot.get();
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
        return borderColorsSlot.get();
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

    /**
     * 设置伪元素内容。
     *
     * @param content 伪元素内容
     * @return 当前声明
     */
    public UiStyleDeclaration setContent(UiPseudoElementContent content) {
        return updateContent(Objects.requireNonNull(content, "content"));
    }

    public UiStyleDeclaration clearContent() {
        return updateContent(null);
    }

    public UiPseudoElementContent getContent() {
        return content;
    }

    public UiScrollbarColor getScrollbarColor() {
        return scrollbarColorSlot.get();
    }

    /**
     * 设置滚动条滑块与轨道颜色。
     *
     * @param scrollbarColor 滚动条颜色
     * @return 当前声明
     */
    public UiStyleDeclaration setScrollbarColor(UiScrollbarColor scrollbarColor) {
        return updateScrollbarColor(Objects.requireNonNull(scrollbarColor, "scrollbarColor"));
    }

    /**
     * 设置滚动条滑块与轨道颜色。
     *
     * @param thumbColor 滑块颜色（ARGB）
     * @param trackColor 轨道颜色（ARGB）
     * @return 当前声明
     */
    public UiStyleDeclaration setScrollbarColor(int thumbColor, int trackColor) {
        return setScrollbarColor(UiScrollbarColor.of(thumbColor, trackColor));
    }

    public UiStyleDeclaration clearScrollbarColor() {
        return updateScrollbarColor(null);
    }

    public UiScrollbarWidth getScrollbarWidth() {
        return scrollbarWidth;
    }

    /**
     * 设置滚动条宽度模式。
     *
     * @param scrollbarWidth 滚动条宽度模式
     * @return 当前声明
     */
    public UiStyleDeclaration setScrollbarWidth(UiScrollbarWidth scrollbarWidth) {
        return updateScrollbarWidth(Objects.requireNonNull(scrollbarWidth, "scrollbarWidth"));
    }

    public UiStyleDeclaration clearScrollbarWidth() {
        return updateScrollbarWidth(null);
    }

    public UiListStyleType getListStyleType() {
        return listStyleType;
    }

    /**
     * 设置列表标记类型。
     *
     * @param listStyleType 列表标记类型
     * @return 当前声明
     */
    public UiStyleDeclaration setListStyleType(UiListStyleType listStyleType) {
        return updateListStyleType(Objects.requireNonNull(listStyleType, "listStyleType"));
    }

    public UiStyleDeclaration clearListStyleType() {
        return updateListStyleType(null);
    }

    public UiTransform getTransform() {
        return transformSlot.get();
    }

    /**
     * 设置元素 transform。
     *
     * <p>transform 只影响绘制与命中测试，不参与布局尺寸计算。</p>
     *
     * @param transform transform 值
     * @return 当前声明
     */
    public UiStyleDeclaration setTransform(UiTransform transform) {
        return updateTransform(Objects.requireNonNull(transform, "transform"));
    }

    public UiStyleDeclaration clearTransform() {
        return updateTransform(null);
    }

    private UiStyleDeclaration updateProperty(UiStyleProperty property, Object previousValue, Object nextValue,
            UiStyleChangeImpact impact) {
        boolean changed = !Objects.equals(previousValue, nextValue);
        if (nextValue == null) {
            declaredValues.remove(property);
        } else {
            declaredValues.put(property, nextValue);
        }
        if (nextValue != null && keywords.remove(property) != null) {
            changed = true;
        }
        if (changed) {
            recordChange(impact);
        }
        return this;
    }

    private UiStyleDeclaration updateDisplay(UiDisplay value) {
        UiDisplay previousValue = display;
        display = value;
        return updateProperty(UiStyleProperty.DISPLAY, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateWidth(UiStyleLength value) {
        UiStyleLength previousValue = width;
        width = value;
        return updateProperty(UiStyleProperty.WIDTH, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateHeight(UiStyleLength value) {
        UiStyleLength previousValue = height;
        height = value;
        return updateProperty(UiStyleProperty.HEIGHT, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateBoxSizing(UiBoxSizing value) {
        UiBoxSizing previousValue = boxSizing;
        boxSizing = value;
        return updateProperty(UiStyleProperty.BOX_SIZING, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updatePosition(UiPosition value) {
        UiPosition previousValue = position;
        position = value;
        return updateProperty(UiStyleProperty.POSITION, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateTop(UiStyleLength value) {
        UiStyleLength previousValue = top;
        top = value;
        return updateProperty(UiStyleProperty.TOP, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateRight(UiStyleLength value) {
        UiStyleLength previousValue = right;
        right = value;
        return updateProperty(UiStyleProperty.RIGHT, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateBottom(UiStyleLength value) {
        UiStyleLength previousValue = bottom;
        bottom = value;
        return updateProperty(UiStyleProperty.BOTTOM, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateLeft(UiStyleLength value) {
        UiStyleLength previousValue = left;
        left = value;
        return updateProperty(UiStyleProperty.LEFT, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateZIndex(Integer value) {
        Integer previousValue = zIndex;
        zIndex = value;
        return updateProperty(UiStyleProperty.Z_INDEX, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateMargin(UiStyleInsets value) {
        UiStyleInsets previousValue = margin;
        margin = value;
        return updateProperty(UiStyleProperty.MARGIN, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updatePadding(UiStyleInsets value) {
        UiStyleInsets previousValue = padding;
        padding = value;
        return updateProperty(UiStyleProperty.PADDING, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateBorderWidth(UiStyleLength value) {
        UiStyleLength previousValue = borderWidth;
        borderWidth = value;
        return updateProperty(UiStyleProperty.BORDER_WIDTH, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateBorderRadius(UiStyleLength value) {
        return borderRadiusSlot.update(value);
    }

    private UiStyleDeclaration updateOverflowX(UiOverflow value) {
        UiOverflow previousValue = overflowX;
        overflowX = value;
        return updateProperty(UiStyleProperty.OVERFLOW_X, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateOverflowY(UiOverflow value) {
        UiOverflow previousValue = overflowY;
        overflowY = value;
        return updateProperty(UiStyleProperty.OVERFLOW_Y, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateFlexDirection(UiFlexDirection value) {
        UiFlexDirection previousValue = flexDirection;
        flexDirection = value;
        return updateProperty(UiStyleProperty.FLEX_DIRECTION, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateAlignItems(UiAlignItems value) {
        UiAlignItems previousValue = alignItems;
        alignItems = value;
        return updateProperty(UiStyleProperty.ALIGN_ITEMS, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateAlignContent(UiAlignContent value) {
        UiAlignContent previousValue = alignContent;
        alignContent = value;
        return updateProperty(UiStyleProperty.ALIGN_CONTENT, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateJustifyContent(UiJustifyContent value) {
        UiJustifyContent previousValue = justifyContent;
        justifyContent = value;
        return updateProperty(UiStyleProperty.JUSTIFY_CONTENT, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateVerticalAlign(UiVerticalAlign value) {
        UiVerticalAlign previousValue = verticalAlign;
        verticalAlign = value;
        return updateProperty(UiStyleProperty.VERTICAL_ALIGN, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateRowGap(UiStyleLength value) {
        UiStyleLength previousValue = rowGap;
        rowGap = value;
        return updateProperty(UiStyleProperty.ROW_GAP, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateColumnGap(UiStyleLength value) {
        UiStyleLength previousValue = columnGap;
        columnGap = value;
        return updateProperty(UiStyleProperty.COLUMN_GAP, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateFlexGrow(Float value) {
        Float previousValue = flexGrow;
        flexGrow = value;
        return updateProperty(UiStyleProperty.FLEX_GROW, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateFlexShrink(Float value) {
        Float previousValue = flexShrink;
        flexShrink = value;
        return updateProperty(UiStyleProperty.FLEX_SHRINK, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateOrder(Integer value) {
        Integer previousValue = order;
        order = value;
        return updateProperty(UiStyleProperty.ORDER, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateOpacity(Float value) {
        return opacitySlot.update(value);
    }

    private UiStyleDeclaration updateBackgroundColor(Integer value) {
        return backgroundColorSlot.update(value);
    }

    private UiStyleDeclaration updateBorderColor(Integer value) {
        return borderColorSlot.update(value);
    }

    private UiStyleDeclaration updateTextColor(Integer value) {
        return textColorSlot.update(value);
    }

    private UiStyleDeclaration updateTransitionProperties(List<DocumentAnimationProperty> value) {
        List<DocumentAnimationProperty> nextValue = value == null ? null
                : Collections.unmodifiableList(new ArrayList<DocumentAnimationProperty>(value));
        List<DocumentAnimationProperty> previousValue = transitionProperties;
        transitionProperties = nextValue;
        return updateProperty(UiStyleProperty.TRANSITION_PROPERTIES, previousValue, nextValue,
                UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateTransitionDurationNanos(Long value) {
        Long previousValue = transitionDurationNanos;
        transitionDurationNanos = value;
        return updateProperty(UiStyleProperty.TRANSITION_DURATION, previousValue, value, UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateTransitionDelayNanos(Long value) {
        Long previousValue = transitionDelayNanos;
        transitionDelayNanos = value;
        return updateProperty(UiStyleProperty.TRANSITION_DELAY, previousValue, value, UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateTransitionTimingFunction(DocumentAnimationTimingFunction value) {
        DocumentAnimationTimingFunction previousValue = transitionTimingFunction;
        transitionTimingFunction = value;
        return updateProperty(UiStyleProperty.TRANSITION_TIMING, previousValue, value, UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateTransitionSpecs(List<DocumentTransitionSpec> value) {
        List<DocumentTransitionSpec> nextValue = value == null ? null
                : Collections.unmodifiableList(new ArrayList<DocumentTransitionSpec>(value));
        List<DocumentTransitionSpec> previousValue = transitionSpecs;
        transitionSpecs = nextValue;
        return updateProperty(UiStyleProperty.TRANSITION_SPECS, previousValue, nextValue,
                UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateAnimationName(String value) {
        String previousValue = animationName;
        animationName = value;
        return updateProperty(UiStyleProperty.ANIMATION_NAME, previousValue, value, UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateAnimationDurationNanos(Long value) {
        Long previousValue = animationDurationNanos;
        animationDurationNanos = value;
        return updateProperty(UiStyleProperty.ANIMATION_DURATION, previousValue, value, UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateAnimationDelayNanos(Long value) {
        Long previousValue = animationDelayNanos;
        animationDelayNanos = value;
        return updateProperty(UiStyleProperty.ANIMATION_DELAY, previousValue, value, UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateAnimationIterationCount(Integer value) {
        Integer previousValue = animationIterationCount;
        animationIterationCount = value;
        return updateProperty(UiStyleProperty.ANIMATION_ITERATION_COUNT, previousValue, value,
                UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateAnimationFillMode(DocumentAnimationFillMode value) {
        DocumentAnimationFillMode previousValue = animationFillMode;
        animationFillMode = value;
        return updateProperty(UiStyleProperty.ANIMATION_FILL_MODE, previousValue, value, UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateAnimationTimingFunction(DocumentAnimationTimingFunction value) {
        DocumentAnimationTimingFunction previousValue = animationTimingFunction;
        animationTimingFunction = value;
        return updateProperty(UiStyleProperty.ANIMATION_TIMING, previousValue, value, UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateAnimationDirection(UiAnimationDirection value) {
        UiAnimationDirection previousValue = animationDirection;
        animationDirection = value;
        return updateProperty(UiStyleProperty.ANIMATION_DIRECTION, previousValue, value, UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateBackdropBlurRadius(UiStyleLength value) {
        return backdropBlurRadiusSlot.update(value);
    }

    private UiStyleDeclaration updateBackdropSaturation(Float value) {
        return backdropSaturationSlot.update(value);
    }

    private UiStyleDeclaration updateLineHeight(UiStyleLength value) {
        UiStyleLength previousValue = lineHeight;
        lineHeight = value;
        return updateProperty(UiStyleProperty.LINE_HEIGHT, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateTextAlign(UiTextAlign value) {
        UiTextAlign previousValue = textAlign;
        textAlign = value;
        return updateProperty(UiStyleProperty.TEXT_ALIGN, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateWhiteSpace(UiWhiteSpace value) {
        UiWhiteSpace previousValue = whiteSpace;
        whiteSpace = value;
        return updateProperty(UiStyleProperty.WHITE_SPACE, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateTextOverflow(UiTextOverflow value) {
        UiTextOverflow previousValue = textOverflow;
        textOverflow = value;
        return updateProperty(UiStyleProperty.TEXT_OVERFLOW, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateVisibility(UiVisibility value) {
        return visibilitySlot.update(value);
    }

    private UiStyleDeclaration updateMinWidth(UiStyleLength value) {
        UiStyleLength previousValue = minWidth;
        minWidth = value;
        return updateProperty(UiStyleProperty.MIN_WIDTH, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateMaxWidth(UiStyleLength value) {
        UiStyleLength previousValue = maxWidth;
        maxWidth = value;
        return updateProperty(UiStyleProperty.MAX_WIDTH, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateMinHeight(UiStyleLength value) {
        UiStyleLength previousValue = minHeight;
        minHeight = value;
        return updateProperty(UiStyleProperty.MIN_HEIGHT, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateMaxHeight(UiStyleLength value) {
        UiStyleLength previousValue = maxHeight;
        maxHeight = value;
        return updateProperty(UiStyleProperty.MAX_HEIGHT, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateFlexBasis(UiStyleLength value) {
        UiStyleLength previousValue = flexBasis;
        flexBasis = value;
        return updateProperty(UiStyleProperty.FLEX_BASIS, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateAlignSelf(UiAlignSelf value) {
        UiAlignSelf previousValue = alignSelf;
        alignSelf = value;
        return updateProperty(UiStyleProperty.ALIGN_SELF, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateFlexWrap(UiFlexWrap value) {
        UiFlexWrap previousValue = flexWrap;
        flexWrap = value;
        return updateProperty(UiStyleProperty.FLEX_WRAP, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateBoxShadow(UiBoxShadow value) {
        return boxShadowSlot.update(value);
    }

    private UiStyleDeclaration updateBorderStyle(UiBorderStyle value) {
        return borderStyleSlot.update(value);
    }

    private UiStyleDeclaration updateBorderCollapse(UiBorderCollapse value) {
        UiBorderCollapse previousValue = borderCollapse;
        borderCollapse = value;
        return updateProperty(UiStyleProperty.BORDER_COLLAPSE, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateCursor(UiCursor value) {
        return cursorSlot.update(value);
    }

    private UiStyleDeclaration updateBorderRadiusCorners(UiBorderRadius value) {
        return borderRadiusCornersSlot.update(value);
    }

    private UiStyleDeclaration updateBackgroundImage(UiBackgroundImage value) {
        return backgroundImageSlot.update(value);
    }

    private UiStyleDeclaration updateTextDecoration(UiTextDecoration value) {
        return textDecorationSlot.update(value);
    }

    private UiStyleDeclaration updateTextShadow(UiTextShadow value) {
        return textShadowSlot.update(value);
    }

    private UiStyleDeclaration updateTextTransform(UiTextTransform value) {
        UiTextTransform previousValue = textTransform;
        textTransform = value;
        return updateProperty(UiStyleProperty.TEXT_TRANSFORM, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateTextIndent(UiStyleLength value) {
        UiStyleLength previousValue = textIndent;
        textIndent = value;
        return updateProperty(UiStyleProperty.TEXT_INDENT, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateFontWeight(UiFontWeight value) {
        UiFontWeight previousValue = fontWeight;
        fontWeight = value;
        return updateProperty(UiStyleProperty.FONT_WEIGHT, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateFontStyle(UiFontStyle value) {
        UiFontStyle previousValue = fontStyle;
        fontStyle = value;
        return updateProperty(UiStyleProperty.FONT_STYLE, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updatePointerEvents(UiPointerEvents value) {
        return pointerEventsSlot.update(value);
    }

    private UiStyleDeclaration updateOutline(UiOutline value) {
        return outlineSlot.update(value);
    }

    private UiStyleDeclaration updateBorderWidthSides(UiStyleInsets value) {
        UiStyleInsets previousValue = borderWidthSides;
        borderWidthSides = value;
        return updateProperty(UiStyleProperty.BORDER_WIDTH_SIDES, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateBorderColors(UiBorderColors value) {
        return borderColorsSlot.update(value);
    }

    private UiStyleDeclaration updateLetterSpacing(UiStyleLength value) {
        UiStyleLength previousValue = letterSpacing;
        letterSpacing = value;
        return updateProperty(UiStyleProperty.LETTER_SPACING, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateWordBreak(UiWordBreak value) {
        UiWordBreak previousValue = wordBreak;
        wordBreak = value;
        return updateProperty(UiStyleProperty.WORD_BREAK, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateOverflowWrap(UiOverflowWrap value) {
        UiOverflowWrap previousValue = overflowWrap;
        overflowWrap = value;
        return updateProperty(UiStyleProperty.OVERFLOW_WRAP, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateAspectRatio(Float value) {
        Float previousValue = aspectRatio;
        aspectRatio = value;
        return updateProperty(UiStyleProperty.ASPECT_RATIO, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateObjectFit(UiObjectFit value) {
        UiObjectFit previousValue = objectFit;
        objectFit = value;
        return updateProperty(UiStyleProperty.OBJECT_FIT, previousValue, value, UiStyleChangeImpact.PAINT);
    }

    private UiStyleDeclaration updateContent(UiPseudoElementContent value) {
        UiPseudoElementContent previousValue = content;
        content = value;
        return updateProperty(UiStyleProperty.CONTENT, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateScrollbarColor(UiScrollbarColor value) {
        return scrollbarColorSlot.update(value);
    }

    private UiStyleDeclaration updateScrollbarWidth(UiScrollbarWidth value) {
        UiScrollbarWidth previousValue = scrollbarWidth;
        scrollbarWidth = value;
        return updateProperty(UiStyleProperty.SCROLLBAR_WIDTH, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateListStyleType(UiListStyleType value) {
        UiListStyleType previousValue = listStyleType;
        listStyleType = value;
        return updateProperty(UiStyleProperty.LIST_STYLE_TYPE, previousValue, value, UiStyleChangeImpact.LAYOUT);
    }

    private UiStyleDeclaration updateTransform(UiTransform value) {
        return transformSlot.update(value);
    }

    private boolean hasConcreteProperty(UiStyleProperty property) {
        return declaredValues.containsKey(property);
    }

    private void clearConcreteProperty(UiStyleProperty property) {
        declaredValues.remove(property);
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
            case OVERFLOW_X: overflowX = null; break;
            case OVERFLOW_Y: overflowY = null; break;
            case FLEX_DIRECTION: flexDirection = null; break;
            case ALIGN_ITEMS: alignItems = null; break;
            case ALIGN_CONTENT: alignContent = null; break;
            case JUSTIFY_CONTENT: justifyContent = null; break;
            case VERTICAL_ALIGN: verticalAlign = null; break;
            case ROW_GAP: rowGap = null; break;
            case COLUMN_GAP: columnGap = null; break;
            case FLEX_GROW: flexGrow = null; break;
            case FLEX_SHRINK: flexShrink = null; break;
            case ORDER: order = null; break;
            case TRANSITION_PROPERTIES: transitionProperties = null; break;
            case TRANSITION_DURATION: transitionDurationNanos = null; break;
            case TRANSITION_DELAY: transitionDelayNanos = null; break;
            case TRANSITION_TIMING: transitionTimingFunction = null; break;
            case TRANSITION_SPECS: transitionSpecs = null; break;
            case ANIMATION_NAME: animationName = null; break;
            case ANIMATION_DURATION: animationDurationNanos = null; break;
            case ANIMATION_DELAY: animationDelayNanos = null; break;
            case ANIMATION_ITERATION_COUNT: animationIterationCount = null; break;
            case ANIMATION_FILL_MODE: animationFillMode = null; break;
            case ANIMATION_TIMING: animationTimingFunction = null; break;
            case ANIMATION_DIRECTION: animationDirection = null; break;
            case LINE_HEIGHT: lineHeight = null; break;
            case TEXT_ALIGN: textAlign = null; break;
            case WHITE_SPACE: whiteSpace = null; break;
            case TEXT_OVERFLOW: textOverflow = null; break;
            case MIN_WIDTH: minWidth = null; break;
            case MAX_WIDTH: maxWidth = null; break;
            case MIN_HEIGHT: minHeight = null; break;
            case MAX_HEIGHT: maxHeight = null; break;
            case FLEX_BASIS: flexBasis = null; break;
            case ALIGN_SELF: alignSelf = null; break;
            case FLEX_WRAP: flexWrap = null; break;
            case BOX_SHADOW: break;
            case BORDER_STYLE: break;
            case BORDER_COLLAPSE: borderCollapse = null; break;
            case CURSOR: break;
            case BORDER_RADIUS_CORNERS: break;
            case BACKGROUND_IMAGE: break;
            case TEXT_DECORATION: break;
            case TEXT_SHADOW: break;
            case TEXT_TRANSFORM: textTransform = null; break;
            case TEXT_INDENT: textIndent = null; break;
            case FONT_WEIGHT: fontWeight = null; break;
            case FONT_STYLE: fontStyle = null; break;
            case POINTER_EVENTS: break;
            case OUTLINE: break;
            case BORDER_WIDTH_SIDES: borderWidthSides = null; break;
            case BORDER_COLORS: break;
            case LETTER_SPACING: letterSpacing = null; break;
            case WORD_BREAK: wordBreak = null; break;
            case OVERFLOW_WRAP: overflowWrap = null; break;
            case ASPECT_RATIO: aspectRatio = null; break;
            case OBJECT_FIT: objectFit = null; break;
            case CONTENT: content = null; break;
            case SCROLLBAR_COLOR: break;
            case SCROLLBAR_WIDTH: scrollbarWidth = null; break;
            case LIST_STYLE_TYPE: listStyleType = null; break;
            case TRANSFORM: break;
            default: break;
        }
    }

    /**
     * 用另一个声明覆盖当前声明的显式值。
     *
     * <p>公开批量复制与逐项 setter 一样会触发变更回调，确保已挂载元素的 layout / paint 缓存立即失效。</p>
     *
     * @param source 来源声明
     * @return 当前声明
     */
    public UiStyleDeclaration copyFrom(UiStyleDeclaration source) {
        EnumMap<UiStyleProperty, Object> previousDeclaredValues = new EnumMap<UiStyleProperty, Object>(declaredValues);
        EnumMap<UiStyleProperty, UiStyleKeyword> previousKeywords =
                new EnumMap<UiStyleProperty, UiStyleKeyword>(keywords);
        EnumSet<UiStyleProperty> previousImportantProperties = EnumSet.copyOf(importantProperties);

        __copyFromSilently(source);
        if (!previousDeclaredValues.equals(declaredValues)
                || !previousKeywords.equals(keywords)
                || !previousImportantProperties.equals(importantProperties)) {
            recordChange(resolveCopyImpact(previousDeclaredValues, previousKeywords, previousImportantProperties));
        }
        return this;
    }

    /**
     * 静默复制另一个声明的显式值。
     *
     * @param source 来源声明
     * @return 当前声明
     * @apiNote 框架内部 API，仅供 clone 等未挂载复制路径使用。业务代码应使用 {@link #copyFrom(UiStyleDeclaration)}。
     */
    public UiStyleDeclaration __copyFromSilently(UiStyleDeclaration source) {
        UiStyleDeclaration resolvedSource = Objects.requireNonNull(source, "source");
        if (resolvedSource == this) {
            return this;
        }
        display = resolvedSource.display;
        width = resolvedSource.width;
        height = resolvedSource.height;
        boxSizing = resolvedSource.boxSizing;
        position = resolvedSource.position;
        top = resolvedSource.top;
        right = resolvedSource.right;
        bottom = resolvedSource.bottom;
        left = resolvedSource.left;
        zIndex = resolvedSource.zIndex;
        margin = resolvedSource.margin;
        padding = resolvedSource.padding;
        borderWidth = resolvedSource.borderWidth;
        overflowX = resolvedSource.overflowX;
        overflowY = resolvedSource.overflowY;
        flexDirection = resolvedSource.flexDirection;
        alignItems = resolvedSource.alignItems;
        alignContent = resolvedSource.alignContent;
        justifyContent = resolvedSource.justifyContent;
        verticalAlign = resolvedSource.verticalAlign;
        rowGap = resolvedSource.rowGap;
        columnGap = resolvedSource.columnGap;
        flexGrow = resolvedSource.flexGrow;
        flexShrink = resolvedSource.flexShrink;
        order = resolvedSource.order;
        transitionProperties = resolvedSource.transitionProperties;
        transitionDurationNanos = resolvedSource.transitionDurationNanos;
        transitionDelayNanos = resolvedSource.transitionDelayNanos;
        transitionTimingFunction = resolvedSource.transitionTimingFunction;
        transitionSpecs = resolvedSource.transitionSpecs;
        animationName = resolvedSource.animationName;
        animationDurationNanos = resolvedSource.animationDurationNanos;
        animationDelayNanos = resolvedSource.animationDelayNanos;
        animationIterationCount = resolvedSource.animationIterationCount;
        animationFillMode = resolvedSource.animationFillMode;
        animationTimingFunction = resolvedSource.animationTimingFunction;
        animationDirection = resolvedSource.animationDirection;
        lineHeight = resolvedSource.lineHeight;
        textAlign = resolvedSource.textAlign;
        whiteSpace = resolvedSource.whiteSpace;
        textOverflow = resolvedSource.textOverflow;
        minWidth = resolvedSource.minWidth;
        maxWidth = resolvedSource.maxWidth;
        minHeight = resolvedSource.minHeight;
        maxHeight = resolvedSource.maxHeight;
        flexBasis = resolvedSource.flexBasis;
        alignSelf = resolvedSource.alignSelf;
        flexWrap = resolvedSource.flexWrap;
        borderCollapse = resolvedSource.borderCollapse;
        textTransform = resolvedSource.textTransform;
        textIndent = resolvedSource.textIndent;
        fontWeight = resolvedSource.fontWeight;
        fontStyle = resolvedSource.fontStyle;
        borderWidthSides = resolvedSource.borderWidthSides;
        letterSpacing = resolvedSource.letterSpacing;
        wordBreak = resolvedSource.wordBreak;
        overflowWrap = resolvedSource.overflowWrap;
        aspectRatio = resolvedSource.aspectRatio;
        objectFit = resolvedSource.objectFit;
        content = resolvedSource.content;
        scrollbarWidth = resolvedSource.scrollbarWidth;
        listStyleType = resolvedSource.listStyleType;
        declaredValues.clear();
        declaredValues.putAll(resolvedSource.declaredValues);
        keywords.clear();
        keywords.putAll(resolvedSource.keywords);
        importantProperties.clear();
        importantProperties.addAll(resolvedSource.importantProperties);
        return this;
    }

    private UiStyleChangeImpact resolveCopyImpact(EnumMap<UiStyleProperty, Object> previousDeclaredValues,
            EnumMap<UiStyleProperty, UiStyleKeyword> previousKeywords,
            EnumSet<UiStyleProperty> previousImportantProperties) {
        EnumSet<UiStyleProperty> changedProperties = EnumSet.noneOf(UiStyleProperty.class);
        collectChangedProperties(changedProperties, previousDeclaredValues, declaredValues);
        collectChangedProperties(changedProperties, previousKeywords, keywords);
        collectChangedImportantProperties(changedProperties, previousImportantProperties, importantProperties);
        for (UiStyleProperty property : changedProperties) {
            if (property.getChangeImpact() == UiStyleChangeImpact.LAYOUT) {
                return UiStyleChangeImpact.LAYOUT;
            }
        }
        return UiStyleChangeImpact.PAINT;
    }

    private static <T> void collectChangedProperties(EnumSet<UiStyleProperty> changedProperties,
            EnumMap<UiStyleProperty, T> previousValues, EnumMap<UiStyleProperty, T> nextValues) {
        for (UiStyleProperty property : previousValues.keySet()) {
            if (!Objects.equals(previousValues.get(property), nextValues.get(property))) {
                changedProperties.add(property);
            }
        }
        for (UiStyleProperty property : nextValues.keySet()) {
            if (!previousValues.containsKey(property)) {
                changedProperties.add(property);
            }
        }
    }

    private static void collectChangedImportantProperties(EnumSet<UiStyleProperty> changedProperties,
            EnumSet<UiStyleProperty> previousProperties, EnumSet<UiStyleProperty> nextProperties) {
        for (UiStyleProperty property : previousProperties) {
            if (!nextProperties.contains(property)) {
                changedProperties.add(property);
            }
        }
        for (UiStyleProperty property : nextProperties) {
            if (!previousProperties.contains(property)) {
                changedProperties.add(property);
            }
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
        for (UiStyleChangeListener listener : new ArrayList<UiStyleChangeListener>(additionalChangeListeners)) {
            listener.onStyleChanged(impact);
        }
    }

    /**
     * 低风险属性族的声明槽，避免类型化字段与声明表各存一份状态。
     */
    private final class StyleDeclarationSlot<T> {

        private final UiStyleProperty property;
        private final UiStyleChangeImpact impact;

        private StyleDeclarationSlot(UiStyleProperty property, UiStyleChangeImpact impact) {
            this.property = Objects.requireNonNull(property, "property");
            this.impact = Objects.requireNonNull(impact, "impact");
        }

        @SuppressWarnings("unchecked")
        private T get() {
            return (T) declaredValues.get(property);
        }

        private UiStyleDeclaration update(T value) {
            return updateProperty(property, get(), value, impact);
        }
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
