package club.heiqi.uilib.ui.style;

import java.util.ArrayList;
import java.util.Collections;
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

    public UiStyleDeclaration() {
        this((UiStyleChangeListener) null);
    }

    public UiStyleDeclaration(Runnable changeListener) {
        this(changeListener == null ? null : new RunnableStyleChangeListener(changeListener));
    }

    public UiStyleDeclaration(UiStyleChangeListener changeListener) {
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
