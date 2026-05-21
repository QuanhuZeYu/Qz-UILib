package club.heiqi.uilib.ui.style.cascade;

import club.heiqi.uilib.ui.style.UiStyleProperty;

import club.heiqi.uilib.ui.style.props.UiTextOverflow;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiBorderRadius;
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
import club.heiqi.uilib.ui.style.selector.UiPseudoElement;
import club.heiqi.uilib.ui.style.props.UiWordBreak;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.values.UiPseudoElementContent;
import club.heiqi.uilib.ui.style.props.UiScrollbarWidth;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiTextAlign;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.selector.UiPseudoClass;
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

import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * HTML-like 样式计算入口。
 *
 * <p>支持样式表规则、inline style、选择器特异性、声明顺序、{@code !important}
 * 以及 {@code inherit}/{@code initial}/{@code unset} 级联关键字。</p>
 */
public final class UiStyleResolver {

    private static final int TRANSPARENT = 0x00000000;
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_LINK_TEXT_COLOR = 0xFF4E8DFF;
    private static final UiStyleInsets DEFAULT_LIST_ITEM_PADDING = UiStyleInsets.of(UiStyleLength.px(0),
            UiStyleLength.px(0), UiStyleLength.px(0), UiStyleLength.px(24));

    private UiStyleResolver() {}

    /**
     * 计算元素最终样式（含级联计算）。
     *
     * <p>自动从元素所属文档的已挂载样式表中查找匹配规则，与 inline style 合并。</p>
     *
     * @param element 目标元素
     * @return 计算样式
     */
    public static ComputedStyle compute(ElementNode element) {
        if (element == null) {
            throw new NullPointerException("element");
        }
        if (element.isPseudoElement()) {
            return computePseudoElement(element.getPseudoOriginElement(), element.getPseudoElement(), null);
        }
        UiDocument document = element.getOwnerDocument();
        List<UiStyleRule> matchingRules = document.findMatchingRules(element);
        return compute(element, computeParentStyle(element), matchingRules);
    }

    /**
     * 计算元素最终样式（含级联计算，考虑伪类状态）。
     *
     * <p>自动从元素所属文档的已挂载样式表中查找匹配规则（含伪类匹配），与 inline style 合并。</p>
     *
     * @param element 目标元素
     * @param activeStates 元素当前激活的伪类状态集合；为 null 时交互伪类选择器不匹配
     * @return 计算样式
     */
    public static ComputedStyle compute(ElementNode element, java.util.Set<UiPseudoClass> activeStates) {
        if (element == null) {
            throw new NullPointerException("element");
        }
        if (element.isPseudoElement()) {
            return computePseudoElement(element.getPseudoOriginElement(), element.getPseudoElement(), activeStates);
        }
        UiDocument document = element.getOwnerDocument();
        List<UiStyleRule> matchingRules = document.findMatchingRules(element, activeStates);
        return compute(element, computeParentStyle(element), matchingRules);
    }

    /**
     * 使用已解析的父级样式计算目标元素最终样式。
     *
     * <p>该入口用于布局等热路径在单次遍历中复用父级 {@link ComputedStyle}，避免每个元素重复递归计算祖先样式。</p>
     *
     * @param element 目标元素
     * @param parentStyle 已解析的父级样式；根元素为 null
     * @return 计算样式
     */
    public static ComputedStyle computeWithParentStyle(ElementNode element, ComputedStyle parentStyle) {
        if (element == null) {
            throw new NullPointerException("element");
        }
        UiDocument document = element.getOwnerDocument();
        if (element.isPseudoElement()) {
            ElementNode originElement = element.getPseudoOriginElement();
            List<UiStyleRule> matchingRules = document.findMatchingRules(originElement, null,
                    element.getPseudoElement());
            return compute(element, parentStyle == null ? compute(originElement) : parentStyle, matchingRules);
        }
        List<UiStyleRule> matchingRules = document.findMatchingRules(element);
        return compute(element, parentStyle, matchingRules);
    }

    /**
     * 计算元素最终样式（使用指定的匹配规则列表）。
     *
     * <p>规则列表应按优先级升序排列（最后一个优先级最高）。</p>
     *
     * @param element 目标元素
     * @param matchingRules 匹配规则列表（按优先级升序）
     * @return 计算样式
     */
    public static ComputedStyle compute(ElementNode element, List<UiStyleRule> matchingRules) {
        if (element == null) {
            throw new NullPointerException("element");
        }
        if (element.isPseudoElement()) {
            return computePseudoElement(element.getPseudoOriginElement(), element.getPseudoElement(), null);
        }
        return compute(element, computeParentStyle(element), matchingRules);
    }

    public static ComputedStyle computePseudoElement(ElementNode originElement, UiPseudoElement pseudoElement,
            java.util.Set<UiPseudoClass> activeStates) {
        if (originElement == null) {
            throw new NullPointerException("originElement");
        }
        UiDocument document = originElement.getOwnerDocument();
        ElementNode pseudoRuntimeElement = document.__createPseudoElementRuntime(originElement, pseudoElement);
        List<UiStyleRule> matchingRules = document.findMatchingRules(originElement, activeStates, pseudoElement);
        ComputedStyle parentStyle = compute(originElement, activeStates);
        return compute(pseudoRuntimeElement, parentStyle, matchingRules);
    }

    private static ComputedStyle compute(ElementNode element, ComputedStyle parentStyle,
            List<UiStyleRule> matchingRules) {
        UiStyleDeclaration inlineStyle = element.style();

        UiDisplay displayInitial = defaultDisplay(element.getTagName());
        UiDisplay display = cascade(inlineStyle, matchingRules, UiStyleProperty.DISPLAY, displayInitial,
                parentStyle == null ? displayInitial : parentStyle.getDisplay());
        UiStyleLength width = cascade(inlineStyle, matchingRules, UiStyleProperty.WIDTH, UiStyleLength.auto(),
                parentStyle == null ? UiStyleLength.auto() : parentStyle.getWidth());
        UiStyleLength height = cascade(inlineStyle, matchingRules, UiStyleProperty.HEIGHT, UiStyleLength.auto(),
                parentStyle == null ? UiStyleLength.auto() : parentStyle.getHeight());
        UiBoxSizing boxSizing = cascade(inlineStyle, matchingRules, UiStyleProperty.BOX_SIZING,
                UiBoxSizing.CONTENT_BOX, parentStyle == null ? UiBoxSizing.CONTENT_BOX : parentStyle.getBoxSizing());
        UiPosition position = cascade(inlineStyle, matchingRules, UiStyleProperty.POSITION, UiPosition.STATIC,
                parentStyle == null ? UiPosition.STATIC : parentStyle.getPosition());
        UiStyleLength top = cascade(inlineStyle, matchingRules, UiStyleProperty.TOP, UiStyleLength.auto(),
                parentStyle == null ? UiStyleLength.auto() : parentStyle.getTop());
        UiStyleLength right = cascade(inlineStyle, matchingRules, UiStyleProperty.RIGHT, UiStyleLength.auto(),
                parentStyle == null ? UiStyleLength.auto() : parentStyle.getRight());
        UiStyleLength bottom = cascade(inlineStyle, matchingRules, UiStyleProperty.BOTTOM, UiStyleLength.auto(),
                parentStyle == null ? UiStyleLength.auto() : parentStyle.getBottom());
        UiStyleLength left = cascade(inlineStyle, matchingRules, UiStyleProperty.LEFT, UiStyleLength.auto(),
                parentStyle == null ? UiStyleLength.auto() : parentStyle.getLeft());
        Integer zIndex = cascade(inlineStyle, matchingRules, UiStyleProperty.Z_INDEX, null,
                parentStyle == null ? null : parentStyle.getZIndex());
        UiStyleInsets margin = cascade(inlineStyle, matchingRules, UiStyleProperty.MARGIN, UiStyleInsets.zero(),
                parentStyle == null ? UiStyleInsets.zero() : parentStyle.getMargin());
        UiStyleInsets padding = cascade(inlineStyle, matchingRules, UiStyleProperty.PADDING, UiStyleInsets.zero(),
                parentStyle == null ? UiStyleInsets.zero() : parentStyle.getPadding());
        UiStyleLength borderWidth = cascade(inlineStyle, matchingRules, UiStyleProperty.BORDER_WIDTH,
                UiStyleLength.px(0), parentStyle == null ? UiStyleLength.px(0) : parentStyle.getBorderWidth());
        UiStyleLength borderRadius = cascade(inlineStyle, matchingRules, UiStyleProperty.BORDER_RADIUS,
                UiStyleLength.px(0), parentStyle == null ? UiStyleLength.px(0) : parentStyle.getBorderRadius());
        UiOverflow overflowX = cascade(inlineStyle, matchingRules, UiStyleProperty.OVERFLOW_X, UiOverflow.VISIBLE,
                parentStyle == null ? UiOverflow.VISIBLE : parentStyle.getOverflowX());
        UiOverflow overflowY = cascade(inlineStyle, matchingRules, UiStyleProperty.OVERFLOW_Y, UiOverflow.VISIBLE,
                parentStyle == null ? UiOverflow.VISIBLE : parentStyle.getOverflowY());
        UiFlexDirection flexDirection = cascade(inlineStyle, matchingRules, UiStyleProperty.FLEX_DIRECTION,
                UiFlexDirection.ROW, parentStyle == null ? UiFlexDirection.ROW : parentStyle.getFlexDirection());
        UiAlignItems alignItems = cascade(inlineStyle, matchingRules, UiStyleProperty.ALIGN_ITEMS,
                UiAlignItems.STRETCH, parentStyle == null ? UiAlignItems.STRETCH : parentStyle.getAlignItems());
        UiJustifyContent justifyContent = cascade(inlineStyle, matchingRules, UiStyleProperty.JUSTIFY_CONTENT,
                UiJustifyContent.START, parentStyle == null ? UiJustifyContent.START : parentStyle.getJustifyContent());
        UiVerticalAlign verticalAlign = cascade(inlineStyle, matchingRules, UiStyleProperty.VERTICAL_ALIGN,
                UiVerticalAlign.BASELINE, parentStyle == null ? UiVerticalAlign.BASELINE : parentStyle.getVerticalAlign());
        UiStyleLength rowGap = cascade(inlineStyle, matchingRules, UiStyleProperty.ROW_GAP, UiStyleLength.px(0),
                parentStyle == null ? UiStyleLength.px(0) : parentStyle.getRowGap());
        UiStyleLength columnGap = cascade(inlineStyle, matchingRules, UiStyleProperty.COLUMN_GAP, UiStyleLength.px(0),
                parentStyle == null ? UiStyleLength.px(0) : parentStyle.getColumnGap());
        Float flexGrowValue = cascade(inlineStyle, matchingRules, UiStyleProperty.FLEX_GROW, Float.valueOf(0.0F),
                parentStyle == null ? Float.valueOf(0.0F) : Float.valueOf(parentStyle.getFlexGrow()));
        Float flexShrinkValue = cascade(inlineStyle, matchingRules, UiStyleProperty.FLEX_SHRINK, Float.valueOf(1.0F),
                parentStyle == null ? Float.valueOf(1.0F) : Float.valueOf(parentStyle.getFlexShrink()));
        Integer orderValue = cascade(inlineStyle, matchingRules, UiStyleProperty.ORDER, Integer.valueOf(0),
                parentStyle == null ? Integer.valueOf(0) : Integer.valueOf(parentStyle.getOrder()));
        Float opacityValue = cascade(inlineStyle, matchingRules, UiStyleProperty.OPACITY, Float.valueOf(1.0F),
                parentStyle == null ? Float.valueOf(1.0F) : Float.valueOf(parentStyle.getOpacity()));
        Integer backgroundColorValue = cascade(inlineStyle, matchingRules, UiStyleProperty.BACKGROUND_COLOR,
                Integer.valueOf(TRANSPARENT), parentStyle == null ? Integer.valueOf(TRANSPARENT)
                        : Integer.valueOf(parentStyle.getBackgroundColor()));
        Integer borderColorValue = cascade(inlineStyle, matchingRules, UiStyleProperty.BORDER_COLOR,
                Integer.valueOf(TRANSPARENT), parentStyle == null ? Integer.valueOf(TRANSPARENT)
                        : Integer.valueOf(parentStyle.getBorderColor()));
        Integer textColorValue = cascade(inlineStyle, matchingRules, UiStyleProperty.TEXT_COLOR,
                Integer.valueOf(DEFAULT_TEXT_COLOR), parentStyle == null ? Integer.valueOf(DEFAULT_TEXT_COLOR)
                        : Integer.valueOf(parentStyle.getTextColor()));

        List<DocumentAnimationProperty> transitionProperties = cascade(inlineStyle, matchingRules,
                UiStyleProperty.TRANSITION_PROPERTIES, Collections.<DocumentAnimationProperty>emptyList(),
                parentStyle == null ? Collections.<DocumentAnimationProperty>emptyList()
                        : parentStyle.getTransitionProperties());
        Long transitionDurationNanos = cascade(inlineStyle, matchingRules, UiStyleProperty.TRANSITION_DURATION,
                Long.valueOf(0L), parentStyle == null ? Long.valueOf(0L)
                        : Long.valueOf(parentStyle.getTransitionDurationNanos()));
        Long transitionDelayNanos = cascade(inlineStyle, matchingRules, UiStyleProperty.TRANSITION_DELAY,
                Long.valueOf(0L), parentStyle == null ? Long.valueOf(0L)
                        : Long.valueOf(parentStyle.getTransitionDelayNanos()));
        DocumentAnimationTimingFunction transitionTimingFunction = cascade(inlineStyle, matchingRules,
                UiStyleProperty.TRANSITION_TIMING, DocumentAnimationTimingFunction.LINEAR,
                parentStyle == null ? DocumentAnimationTimingFunction.LINEAR : parentStyle.getTransitionTimingFunction());
        String animationName = cascade(inlineStyle, matchingRules, UiStyleProperty.ANIMATION_NAME, null,
                parentStyle == null ? null : parentStyle.getAnimationName());
        Long animationDurationNanos = cascade(inlineStyle, matchingRules, UiStyleProperty.ANIMATION_DURATION,
                Long.valueOf(0L), parentStyle == null ? Long.valueOf(0L)
                        : Long.valueOf(parentStyle.getAnimationDurationNanos()));
        Long animationDelayNanos = cascade(inlineStyle, matchingRules, UiStyleProperty.ANIMATION_DELAY,
                Long.valueOf(0L), parentStyle == null ? Long.valueOf(0L)
                        : Long.valueOf(parentStyle.getAnimationDelayNanos()));
        Integer animationIterationCount = cascade(inlineStyle, matchingRules,
                UiStyleProperty.ANIMATION_ITERATION_COUNT, Integer.valueOf(1), parentStyle == null ? Integer.valueOf(1)
                        : Integer.valueOf(parentStyle.getAnimationIterationCount()));
        DocumentAnimationFillMode animationFillMode = cascade(inlineStyle, matchingRules,
                UiStyleProperty.ANIMATION_FILL_MODE, DocumentAnimationFillMode.NONE,
                parentStyle == null ? DocumentAnimationFillMode.NONE : parentStyle.getAnimationFillMode());
        DocumentAnimationTimingFunction animationTimingFunction = cascade(inlineStyle, matchingRules,
                UiStyleProperty.ANIMATION_TIMING, DocumentAnimationTimingFunction.LINEAR,
                parentStyle == null ? DocumentAnimationTimingFunction.LINEAR : parentStyle.getAnimationTimingFunction());
        UiAnimationDirection animationDirection = cascade(inlineStyle, matchingRules,
                UiStyleProperty.ANIMATION_DIRECTION, UiAnimationDirection.NORMAL,
                parentStyle == null ? UiAnimationDirection.NORMAL : parentStyle.getAnimationDirection());
        UiStyleLength backdropBlurRadius = cascade(inlineStyle, matchingRules,
                UiStyleProperty.BACKDROP_BLUR_RADIUS, UiStyleLength.px(0),
                parentStyle == null ? UiStyleLength.px(0) : parentStyle.getBackdropBlurRadius());
        Float backdropSaturationValue = cascade(inlineStyle, matchingRules, UiStyleProperty.BACKDROP_SATURATION,
                Float.valueOf(1.0F), parentStyle == null ? Float.valueOf(1.0F)
                        : Float.valueOf(parentStyle.getBackdropSaturation()));

        UiStyleLength lineHeight = cascade(inlineStyle, matchingRules, UiStyleProperty.LINE_HEIGHT, UiStyleLength.auto(),
                parentStyle == null ? UiStyleLength.auto() : parentStyle.getLineHeight());
        UiTextAlign textAlign = cascade(inlineStyle, matchingRules, UiStyleProperty.TEXT_ALIGN, UiTextAlign.START,
                parentStyle == null ? UiTextAlign.START : parentStyle.getTextAlign());
        UiWhiteSpace whiteSpace = cascade(inlineStyle, matchingRules, UiStyleProperty.WHITE_SPACE, UiWhiteSpace.NORMAL,
                parentStyle == null ? UiWhiteSpace.NORMAL : parentStyle.getWhiteSpace());
        UiTextOverflow textOverflow = cascade(inlineStyle, matchingRules, UiStyleProperty.TEXT_OVERFLOW,
                UiTextOverflow.CLIP, parentStyle == null ? UiTextOverflow.CLIP : parentStyle.getTextOverflow());
        UiVisibility visibility = cascade(inlineStyle, matchingRules, UiStyleProperty.VISIBILITY, UiVisibility.VISIBLE,
                parentStyle == null ? UiVisibility.VISIBLE : parentStyle.getVisibility());
        UiStyleLength minWidth = cascade(inlineStyle, matchingRules, UiStyleProperty.MIN_WIDTH, UiStyleLength.px(0),
                parentStyle == null ? UiStyleLength.px(0) : parentStyle.getMinWidth());
        UiStyleLength maxWidth = cascade(inlineStyle, matchingRules, UiStyleProperty.MAX_WIDTH, UiStyleLength.auto(),
                parentStyle == null ? UiStyleLength.auto() : parentStyle.getMaxWidth());
        UiStyleLength minHeight = cascade(inlineStyle, matchingRules, UiStyleProperty.MIN_HEIGHT, UiStyleLength.px(0),
                parentStyle == null ? UiStyleLength.px(0) : parentStyle.getMinHeight());
        UiStyleLength maxHeight = cascade(inlineStyle, matchingRules, UiStyleProperty.MAX_HEIGHT, UiStyleLength.auto(),
                parentStyle == null ? UiStyleLength.auto() : parentStyle.getMaxHeight());
        UiStyleLength flexBasis = cascade(inlineStyle, matchingRules, UiStyleProperty.FLEX_BASIS, UiStyleLength.auto(),
                parentStyle == null ? UiStyleLength.auto() : parentStyle.getFlexBasis());
        UiAlignSelf alignSelf = cascade(inlineStyle, matchingRules, UiStyleProperty.ALIGN_SELF, UiAlignSelf.AUTO,
                parentStyle == null ? UiAlignSelf.AUTO : parentStyle.getAlignSelf());
        UiFlexWrap flexWrap = cascade(inlineStyle, matchingRules, UiStyleProperty.FLEX_WRAP, UiFlexWrap.NOWRAP,
                parentStyle == null ? UiFlexWrap.NOWRAP : parentStyle.getFlexWrap());
        UiBoxShadow boxShadow = cascade(inlineStyle, matchingRules, UiStyleProperty.BOX_SHADOW, null,
                parentStyle == null ? null : parentStyle.getBoxShadow());
        UiBorderStyle borderStyle = cascade(inlineStyle, matchingRules, UiStyleProperty.BORDER_STYLE,
                UiBorderStyle.NONE, parentStyle == null ? UiBorderStyle.NONE : parentStyle.getBorderStyle());
        UiBorderCollapse borderCollapse = cascade(inlineStyle, matchingRules, UiStyleProperty.BORDER_COLLAPSE,
                UiBorderCollapse.SEPARATE,
                parentStyle == null ? UiBorderCollapse.SEPARATE : parentStyle.getBorderCollapse());
        UiCursor cursor = cascade(inlineStyle, matchingRules, UiStyleProperty.CURSOR, UiCursor.DEFAULT,
                parentStyle == null ? UiCursor.DEFAULT : parentStyle.getCursor());
        UiBorderRadius borderRadiusCorners = cascade(inlineStyle, matchingRules,
                UiStyleProperty.BORDER_RADIUS_CORNERS, null, parentStyle == null ? null : parentStyle.getBorderRadiusCorners());
        UiBackgroundImage backgroundImage = cascade(inlineStyle, matchingRules, UiStyleProperty.BACKGROUND_IMAGE, null,
                parentStyle == null ? null : parentStyle.getBackgroundImage());
        UiTextDecoration textDecoration = cascade(inlineStyle, matchingRules, UiStyleProperty.TEXT_DECORATION,
                UiTextDecoration.NONE, parentStyle == null ? UiTextDecoration.NONE : parentStyle.getTextDecoration());
        UiTextShadow textShadow = cascade(inlineStyle, matchingRules, UiStyleProperty.TEXT_SHADOW, null,
                parentStyle == null ? null : parentStyle.getTextShadow());
        UiTextTransform textTransform = cascade(inlineStyle, matchingRules, UiStyleProperty.TEXT_TRANSFORM,
                UiTextTransform.NONE, parentStyle == null ? UiTextTransform.NONE : parentStyle.getTextTransform());
        UiStyleLength textIndent = cascade(inlineStyle, matchingRules, UiStyleProperty.TEXT_INDENT, UiStyleLength.px(0),
                parentStyle == null ? UiStyleLength.px(0) : parentStyle.getTextIndent());
        UiFontWeight fontWeight = cascade(inlineStyle, matchingRules, UiStyleProperty.FONT_WEIGHT, UiFontWeight.NORMAL,
                parentStyle == null ? UiFontWeight.NORMAL : parentStyle.getFontWeight());
        UiFontStyle fontStyle = cascade(inlineStyle, matchingRules, UiStyleProperty.FONT_STYLE, UiFontStyle.NORMAL,
                parentStyle == null ? UiFontStyle.NORMAL : parentStyle.getFontStyle());
        UiPointerEvents pointerEvents = cascade(inlineStyle, matchingRules, UiStyleProperty.POINTER_EVENTS,
                UiPointerEvents.AUTO, parentStyle == null ? UiPointerEvents.AUTO : parentStyle.getPointerEvents());
        UiOutline outline = cascade(inlineStyle, matchingRules, UiStyleProperty.OUTLINE, null,
                parentStyle == null ? null : parentStyle.getOutline());
        UiStyleInsets borderWidthSides = cascade(inlineStyle, matchingRules, UiStyleProperty.BORDER_WIDTH_SIDES, null,
                parentStyle == null ? null : parentStyle.getBorderWidthSides());
        UiBorderColors borderColors = cascade(inlineStyle, matchingRules, UiStyleProperty.BORDER_COLORS, null,
                parentStyle == null ? null : parentStyle.getBorderColors());
        UiStyleLength letterSpacing = cascade(inlineStyle, matchingRules, UiStyleProperty.LETTER_SPACING, null,
                parentStyle == null ? null : parentStyle.getLetterSpacing());
        UiWordBreak wordBreak = cascade(inlineStyle, matchingRules, UiStyleProperty.WORD_BREAK, UiWordBreak.NORMAL,
                parentStyle == null ? UiWordBreak.NORMAL : parentStyle.getWordBreak());
        UiOverflowWrap overflowWrap = cascade(inlineStyle, matchingRules, UiStyleProperty.OVERFLOW_WRAP,
                UiOverflowWrap.NORMAL, parentStyle == null ? UiOverflowWrap.NORMAL : parentStyle.getOverflowWrap());
        Float aspectRatio = cascade(inlineStyle, matchingRules, UiStyleProperty.ASPECT_RATIO, null,
                parentStyle == null ? null : parentStyle.getAspectRatio());
        UiObjectFit objectFit = cascade(inlineStyle, matchingRules, UiStyleProperty.OBJECT_FIT, UiObjectFit.FILL,
                parentStyle == null ? UiObjectFit.FILL : parentStyle.getObjectFit());
        UiPseudoElementContent content = cascade(inlineStyle, matchingRules, UiStyleProperty.CONTENT,
                UiPseudoElementContent.none(), UiPseudoElementContent.none());
        UiScrollbarColor scrollbarColor = cascade(inlineStyle, matchingRules, UiStyleProperty.SCROLLBAR_COLOR,
                UiScrollbarColor.auto(), parentStyle == null ? UiScrollbarColor.auto() : parentStyle.getScrollbarColor());
        UiScrollbarWidth scrollbarWidth = cascade(inlineStyle, matchingRules, UiStyleProperty.SCROLLBAR_WIDTH,
                UiScrollbarWidth.AUTO, parentStyle == null ? UiScrollbarWidth.AUTO : parentStyle.getScrollbarWidth());
        UiListStyleType listStyleType = cascade(inlineStyle, matchingRules, UiStyleProperty.LIST_STYLE_TYPE,
                UiListStyleType.NONE, parentStyle == null ? UiListStyleType.NONE : parentStyle.getListStyleType());
        UiTransform transform = cascade(inlineStyle, matchingRules, UiStyleProperty.TRANSFORM, UiTransform.identity(),
                parentStyle == null ? UiTransform.identity() : parentStyle.getTransform());

        if (!hasDeclaredProperty(inlineStyle, matchingRules, UiStyleProperty.LIST_STYLE_TYPE)) {
            if ("ol".equals(element.getTagName()) && listStyleType == UiListStyleType.NONE) {
                listStyleType = UiListStyleType.DECIMAL;
            } else if ("ul".equals(element.getTagName()) && listStyleType == UiListStyleType.NONE) {
                listStyleType = UiListStyleType.DISC;
            }
        }

        if (isLinkElement(element) && !hasDeclaredProperty(inlineStyle, matchingRules, UiStyleProperty.TEXT_COLOR)) {
            textColorValue = Integer.valueOf(DEFAULT_LINK_TEXT_COLOR);
        }
        if (isLinkElement(element)
                && !hasDeclaredProperty(inlineStyle, matchingRules, UiStyleProperty.TEXT_DECORATION)) {
            textDecoration = UiTextDecoration.UNDERLINE;
        }
        if (isLinkElement(element) && !hasDeclaredProperty(inlineStyle, matchingRules, UiStyleProperty.CURSOR)) {
            cursor = UiCursor.POINTER;
        }
        if (isListItemElement(element) && isInsideListContainer(element)
                && listStyleType != UiListStyleType.NONE
                && !hasDeclaredProperty(inlineStyle, matchingRules, UiStyleProperty.PADDING)) {
            padding = DEFAULT_LIST_ITEM_PADDING;
        }

        return new ComputedStyle(display, width, height, boxSizing, position, top, right, bottom, left, zIndex, margin,
                padding, borderWidth, borderRadius, overflowX, overflowY, flexDirection, alignItems, justifyContent,
                verticalAlign, rowGap, columnGap, flexGrowValue.floatValue(), flexShrinkValue.floatValue(),
                orderValue.intValue(), opacityValue.floatValue(), backgroundColorValue.intValue(),
                borderColorValue.intValue(), textColorValue.intValue(), transitionProperties,
                transitionDurationNanos.longValue(),
                transitionDelayNanos.longValue(), transitionTimingFunction, animationName,
                animationDurationNanos.longValue(), animationDelayNanos.longValue(), animationIterationCount.intValue(),
                animationFillMode, animationTimingFunction, animationDirection, backdropBlurRadius,
                backdropSaturationValue.floatValue(),
                lineHeight, textAlign, whiteSpace, textOverflow, visibility,
                minWidth, maxWidth, minHeight, maxHeight, flexBasis, alignSelf, flexWrap,
                boxShadow, borderStyle, borderCollapse, cursor, borderRadiusCorners, backgroundImage, textDecoration,
                textShadow, textTransform, textIndent, fontWeight, fontStyle,
                pointerEvents, outline, borderWidthSides, borderColors, letterSpacing, wordBreak, overflowWrap,
                aspectRatio, objectFit, content, scrollbarColor, scrollbarWidth, listStyleType, transform);
    }

    private static boolean hasDeclaredProperty(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules,
            UiStyleProperty property) {
        return findDeclaration(inlineStyle, rules, property) != null;
    }

    private static <T> T cascade(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules, UiStyleProperty property,
            T initialValue, T inheritedValue) {
        CascadedDeclaration<T> declaration = findDeclaration(inlineStyle, rules, property);
        if (declaration == null) {
            return property.isInheritedByDefault() ? inheritedValue : initialValue;
        }
        if (declaration.keyword != null) {
            switch (declaration.keyword) {
                case INHERIT: return inheritedValue;
                case INITIAL: return initialValue;
                case UNSET: return property.isInheritedByDefault() ? inheritedValue : initialValue;
                default: return initialValue;
            }
        }
        return declaration.value;
    }

    private static <T> CascadedDeclaration<T> findDeclaration(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules,
            UiStyleProperty property) {
        CascadedDeclaration<T> inlineDeclaration = declarationFrom(inlineStyle, property);
        if (inlineDeclaration != null && inlineStyle.isImportant(property)) {
            return inlineDeclaration;
        }
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiStyleDeclaration declaration = rules.get(i).getDeclaration();
            if (declaration.isImportant(property)) {
                CascadedDeclaration<T> ruleDeclaration = declarationFrom(declaration, property);
                if (ruleDeclaration != null) {
                    return ruleDeclaration;
                }
            }
        }
        if (inlineDeclaration != null) {
            return inlineDeclaration;
        }
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiStyleDeclaration declaration = rules.get(i).getDeclaration();
            if (!declaration.isImportant(property)) {
                CascadedDeclaration<T> ruleDeclaration = declarationFrom(declaration, property);
                if (ruleDeclaration != null) {
                    return ruleDeclaration;
                }
            }
        }
        return null;
    }

    private static <T> CascadedDeclaration<T> declarationFrom(UiStyleDeclaration declaration,
            UiStyleProperty property) {
        T value = readDeclarationValue(declaration, property);
        UiStyleKeyword keyword = declaration.getKeyword(property);
        if (value == null && keyword == null) {
            return null;
        }
        if (value != null) {
            return new CascadedDeclaration<T>(value, null);
        }
        return new CascadedDeclaration<T>(null, keyword);
    }

    @SuppressWarnings("unchecked")
    private static <T> T readDeclarationValue(UiStyleDeclaration declaration, UiStyleProperty property) {
        return declaration.getDeclaredValue(property);
    }

    private static ComputedStyle computeParentStyle(ElementNode element) {
        DocumentNode parent = element.getParent();
        if (!(parent instanceof ElementNode)) {
            return null;
        }
        return compute((ElementNode) parent);
    }

    private static UiDisplay defaultDisplay(String tagName) {
        if ("span".equals(tagName) || "a".equals(tagName)) {
            return UiDisplay.INLINE;
        }
        if ("button".equals(tagName) || "input".equals(tagName) || "textarea".equals(tagName)
                || "select".equals(tagName) || "img".equals(tagName)) {
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

    private static boolean isAnchorElement(ElementNode element) {
        return element != null && "a".equals(element.getTagName());
    }

    private static boolean isLinkElement(ElementNode element) {
        return isAnchorElement(element) && hasLinkHref(element);
    }

    private static boolean isListItemElement(ElementNode element) {
        return element != null && "li".equals(element.getTagName());
    }

    private static boolean isInsideListContainer(ElementNode element) {
        if (element == null) {
            return false;
        }
        DocumentNode parent = element.getParent();
        if (!(parent instanceof ElementNode)) {
            return false;
        }
        String parentTagName = ((ElementNode) parent).getTagName();
        return "ul".equals(parentTagName) || "ol".equals(parentTagName);
    }

    private static boolean hasLinkHref(ElementNode element) {
        if (element == null) {
            return false;
        }
        String href = element.getAttribute("href");
        return href != null && !href.trim().isEmpty();
    }

    private static final class CascadedDeclaration<T> {
        private final T value;
        private final UiStyleKeyword keyword;

        private CascadedDeclaration(T value, UiStyleKeyword keyword) {
            this.value = value;
            this.keyword = keyword;
        }
    }
}
