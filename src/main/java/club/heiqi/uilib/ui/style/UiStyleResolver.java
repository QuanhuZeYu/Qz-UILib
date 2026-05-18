package club.heiqi.uilib.ui.style;

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
 * <p>支持级联计算：样式表规则按特异性排序后逐属性合并，inline style 拥有最高优先级。</p>
 * <p>级联优先级（从低到高）：tag 选择器 &lt; class 选择器 &lt; id 选择器 &lt; inline style。</p>
 */
public final class UiStyleResolver {

    private static final int TRANSPARENT = 0x00000000;
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;

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
     * @param activeStates 元素当前激活的伪类状态集合；为 null 时伪类选择器不匹配
     * @return 计算样式
     */
    public static ComputedStyle compute(ElementNode element, java.util.Set<UiPseudoClass> activeStates) {
        if (element == null) {
            throw new NullPointerException("element");
        }
        UiDocument document = element.getOwnerDocument();
        List<UiStyleRule> matchingRules = document.findMatchingRules(element, activeStates);
        return compute(element, computeParentStyle(element), matchingRules);
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
        return compute(element, computeParentStyle(element), matchingRules);
    }

    private static ComputedStyle compute(ElementNode element, ComputedStyle parentStyle,
            List<UiStyleRule> matchingRules) {
        UiStyleDeclaration inlineStyle = element.style();

        // 级联解析每个属性：inline > 规则（按特异性降序遍历）> 默认/继承
        UiDisplay display = cascadeDisplay(inlineStyle, matchingRules, element.getTagName());
        UiStyleLength width = cascadeStyleLength(inlineStyle.getWidth(), matchingRules, StyleProperty.WIDTH, UiStyleLength.auto());
        UiStyleLength height = cascadeStyleLength(inlineStyle.getHeight(), matchingRules, StyleProperty.HEIGHT, UiStyleLength.auto());
        UiBoxSizing boxSizing = cascadeBoxSizing(inlineStyle, matchingRules);
        UiPosition position = cascadePosition(inlineStyle, matchingRules);
        UiStyleLength top = cascadeStyleLength(inlineStyle.getTop(), matchingRules, StyleProperty.TOP, UiStyleLength.auto());
        UiStyleLength right = cascadeStyleLength(inlineStyle.getRight(), matchingRules, StyleProperty.RIGHT, UiStyleLength.auto());
        UiStyleLength bottom = cascadeStyleLength(inlineStyle.getBottom(), matchingRules, StyleProperty.BOTTOM, UiStyleLength.auto());
        UiStyleLength left = cascadeStyleLength(inlineStyle.getLeft(), matchingRules, StyleProperty.LEFT, UiStyleLength.auto());
        Integer zIndex = cascadeInteger(inlineStyle.getZIndex(), matchingRules, StyleProperty.Z_INDEX);
        UiStyleInsets margin = cascadeInsets(inlineStyle.getMargin(), matchingRules, StyleProperty.MARGIN, UiStyleInsets.zero());
        UiStyleInsets padding = cascadeInsets(inlineStyle.getPadding(), matchingRules, StyleProperty.PADDING, UiStyleInsets.zero());
        UiStyleLength borderWidth = cascadeStyleLength(inlineStyle.getBorderWidth(), matchingRules, StyleProperty.BORDER_WIDTH, UiStyleLength.px(0));
        UiStyleLength borderRadius = cascadeStyleLength(inlineStyle.getBorderRadius(), matchingRules, StyleProperty.BORDER_RADIUS, UiStyleLength.px(0));
        UiOverflow overflowX = cascadeOverflow(inlineStyle.getOverflowX(), matchingRules, StyleProperty.OVERFLOW_X, UiOverflow.VISIBLE);
        UiOverflow overflowY = cascadeOverflow(inlineStyle.getOverflowY(), matchingRules, StyleProperty.OVERFLOW_Y, UiOverflow.VISIBLE);
        UiFlexDirection flexDirection = cascadeFlexDirection(inlineStyle, matchingRules);
        UiAlignItems alignItems = cascadeAlignItems(inlineStyle, matchingRules);
        UiJustifyContent justifyContent = cascadeJustifyContent(inlineStyle, matchingRules);
        UiVerticalAlign verticalAlign = cascadeVerticalAlign(inlineStyle, matchingRules);
        UiStyleLength rowGap = cascadeStyleLength(inlineStyle.getRowGap(), matchingRules, StyleProperty.ROW_GAP, UiStyleLength.px(0));
        UiStyleLength columnGap = cascadeStyleLength(inlineStyle.getColumnGap(), matchingRules, StyleProperty.COLUMN_GAP, UiStyleLength.px(0));
        float flexGrow = cascadeFloat(inlineStyle.getFlexGrow(), matchingRules, StyleProperty.FLEX_GROW, 0.0F);
        float flexShrink = cascadeFloat(inlineStyle.getFlexShrink(), matchingRules, StyleProperty.FLEX_SHRINK, 1.0F);
        float opacity = cascadeFloat(inlineStyle.getOpacity(), matchingRules, StyleProperty.OPACITY, 1.0F);
        int backgroundColor = cascadeColor(inlineStyle.getBackgroundColor(), matchingRules, StyleProperty.BACKGROUND_COLOR, TRANSPARENT);
        int borderColor = cascadeColor(inlineStyle.getBorderColor(), matchingRules, StyleProperty.BORDER_COLOR, TRANSPARENT);
        int textColor = cascadeTextColor(inlineStyle, matchingRules, parentStyle);
        List<DocumentAnimationProperty> transitionProperties = cascadeTransitionProperties(inlineStyle, matchingRules);
        long transitionDurationNanos = cascadeLong(inlineStyle.getTransitionDurationNanos(), matchingRules, StyleProperty.TRANSITION_DURATION, 0L);
        long transitionDelayNanos = cascadeLong(inlineStyle.getTransitionDelayNanos(), matchingRules, StyleProperty.TRANSITION_DELAY, 0L);
        DocumentAnimationTimingFunction transitionTimingFunction = cascadeTimingFunction(
                inlineStyle.getTransitionTimingFunction(), matchingRules, StyleProperty.TRANSITION_TIMING,
                DocumentAnimationTimingFunction.LINEAR);
        String animationName = cascadeString(inlineStyle.getAnimationName(), matchingRules, StyleProperty.ANIMATION_NAME);
        long animationDurationNanos = cascadeLong(inlineStyle.getAnimationDurationNanos(), matchingRules, StyleProperty.ANIMATION_DURATION, 0L);
        long animationDelayNanos = cascadeLong(inlineStyle.getAnimationDelayNanos(), matchingRules, StyleProperty.ANIMATION_DELAY, 0L);
        int animationIterationCount = cascadeInt(inlineStyle.getAnimationIterationCount(), matchingRules, StyleProperty.ANIMATION_ITERATION_COUNT, 1);
        DocumentAnimationFillMode animationFillMode = cascateAnimationFillMode(inlineStyle, matchingRules);
        DocumentAnimationTimingFunction animationTimingFunction = cascadeTimingFunction(
                inlineStyle.getAnimationTimingFunction(), matchingRules, StyleProperty.ANIMATION_TIMING,
                DocumentAnimationTimingFunction.LINEAR);
        UiStyleLength backdropBlurRadius = cascadeStyleLength(inlineStyle.getBackdropBlurRadius(), matchingRules, StyleProperty.BACKDROP_BLUR_RADIUS, UiStyleLength.px(0));
        float backdropSaturation = cascadeFloat(inlineStyle.getBackdropSaturation(), matchingRules, StyleProperty.BACKDROP_SATURATION, 1.0F);

        // 可继承属性
        UiStyleLength lineHeight = cascadeInheritableLength(inlineStyle.getLineHeight(), matchingRules, StyleProperty.LINE_HEIGHT, inheritedLineHeight(parentStyle));
        UiTextAlign textAlign = cascadeTextAlign(inlineStyle, matchingRules, parentStyle);
        UiWhiteSpace whiteSpace = cascadeWhiteSpace(inlineStyle, matchingRules, parentStyle);
        UiTextOverflow textOverflow = cascadeTextOverflow(inlineStyle, matchingRules);
        UiVisibility visibility = cascadeVisibility(inlineStyle, matchingRules, parentStyle);

        // min/max 尺寸约束
        UiStyleLength minWidth = cascadeStyleLength(inlineStyle.getMinWidth(), matchingRules, StyleProperty.MIN_WIDTH, UiStyleLength.px(0));
        UiStyleLength maxWidth = cascadeStyleLength(inlineStyle.getMaxWidth(), matchingRules, StyleProperty.MAX_WIDTH, UiStyleLength.auto());
        UiStyleLength minHeight = cascadeStyleLength(inlineStyle.getMinHeight(), matchingRules, StyleProperty.MIN_HEIGHT, UiStyleLength.px(0));
        UiStyleLength maxHeight = cascadeStyleLength(inlineStyle.getMaxHeight(), matchingRules, StyleProperty.MAX_HEIGHT, UiStyleLength.auto());

        // flex 增强
        UiStyleLength flexBasis = cascadeStyleLength(inlineStyle.getFlexBasis(), matchingRules, StyleProperty.FLEX_BASIS, UiStyleLength.auto());
        UiAlignSelf alignSelf = cascadeAlignSelf(inlineStyle, matchingRules);
        UiFlexWrap flexWrap = cascadeFlexWrap(inlineStyle, matchingRules);

        // box-shadow：不可继承
        UiBoxShadow boxShadow = cascadeBoxShadow(inlineStyle, matchingRules);

        // border-style：不可继承
        UiBorderStyle borderStyle = cascadeBorderStyle(inlineStyle, matchingRules);

        // cursor：可继承
        UiCursor cursor = cascadeCursor(inlineStyle, matchingRules, parentStyle);

        // border-radius 分角：不可继承
        UiBorderRadius borderRadiusCorners = cascadeBorderRadiusCorners(inlineStyle, matchingRules);

        // text-decoration：不可继承
        UiTextDecoration textDecoration = cascadeTextDecoration(inlineStyle, matchingRules);

        // pointer-events：不可继承
        UiPointerEvents pointerEvents = cascadePointerEvents(inlineStyle, matchingRules);

        // outline：不可继承
        UiOutline outline = cascadeOutline(inlineStyle, matchingRules);

        // border 分边：不可继承
        UiStyleInsets borderWidthSides = cascadeBorderWidthSides(inlineStyle, matchingRules);
        UiBorderColors borderColors = cascadeBorderColors(inlineStyle, matchingRules);

        // 文本排版：可继承
        UiStyleLength letterSpacing = cascadeInheritableLength(inlineStyle.getLetterSpacing(), matchingRules, StyleProperty.LETTER_SPACING, inheritedLetterSpacing(parentStyle));
        UiWordBreak wordBreak = cascadeWordBreak(inlineStyle, matchingRules, parentStyle);
        UiOverflowWrap overflowWrap = cascadeOverflowWrap(inlineStyle, matchingRules, parentStyle);

        return new ComputedStyle(display, width, height, boxSizing, position, top, right, bottom, left, zIndex, margin,
                padding, borderWidth, borderRadius, overflowX, overflowY, flexDirection, alignItems, justifyContent,
                verticalAlign, rowGap, columnGap, flexGrow, flexShrink, opacity, backgroundColor, borderColor, textColor,
                transitionProperties, transitionDurationNanos, transitionDelayNanos, transitionTimingFunction,
                animationName, animationDurationNanos, animationDelayNanos, animationIterationCount, animationFillMode,
                animationTimingFunction,
                backdropBlurRadius, backdropSaturation,
                lineHeight, textAlign, whiteSpace, textOverflow, visibility,
                minWidth, maxWidth, minHeight, maxHeight,
                flexBasis, alignSelf, flexWrap,
                boxShadow, borderStyle, cursor,
                borderRadiusCorners, textDecoration, pointerEvents,
                outline, borderWidthSides, borderColors,
                letterSpacing, wordBreak, overflowWrap);
    }

    // ========== 级联属性解析方法 ==========

    private static UiDisplay cascadeDisplay(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules, String tagName) {
        if (inlineStyle.getDisplay() != null) return inlineStyle.getDisplay();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiDisplay value = rules.get(i).getDeclaration().getDisplay();
            if (value != null) return value;
        }
        return defaultDisplay(tagName);
    }

    private static UiStyleLength cascadeStyleLength(UiStyleLength inlineValue, List<UiStyleRule> rules, StyleProperty property, UiStyleLength defaultValue) {
        if (inlineValue != null) return inlineValue;
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiStyleLength value = getStyleLength(rules.get(i).getDeclaration(), property);
            if (value != null) return value;
        }
        return defaultValue;
    }

    private static UiStyleLength cascadeInheritableLength(UiStyleLength inlineValue, List<UiStyleRule> rules, StyleProperty property, UiStyleLength inheritedValue) {
        if (inlineValue != null) return inlineValue;
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiStyleLength value = getStyleLength(rules.get(i).getDeclaration(), property);
            if (value != null) return value;
        }
        return inheritedValue;
    }

    private static UiStyleInsets cascadeInsets(UiStyleInsets inlineValue, List<UiStyleRule> rules, StyleProperty property, UiStyleInsets defaultValue) {
        if (inlineValue != null) return inlineValue;
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiStyleInsets value = getStyleInsets(rules.get(i).getDeclaration(), property);
            if (value != null) return value;
        }
        return defaultValue;
    }

    private static Integer cascadeInteger(Integer inlineValue, List<UiStyleRule> rules, StyleProperty property) {
        if (inlineValue != null) return inlineValue;
        for (int i = rules.size() - 1; i >= 0; i--) {
            Integer value = getInteger(rules.get(i).getDeclaration(), property);
            if (value != null) return value;
        }
        return null;
    }

    private static float cascadeFloat(Float inlineValue, List<UiStyleRule> rules, StyleProperty property, float defaultValue) {
        if (inlineValue != null) return inlineValue.floatValue();
        for (int i = rules.size() - 1; i >= 0; i--) {
            Float value = getFloat(rules.get(i).getDeclaration(), property);
            if (value != null) return value.floatValue();
        }
        return defaultValue;
    }

    private static int cascadeColor(Integer inlineValue, List<UiStyleRule> rules, StyleProperty property, int defaultValue) {
        if (inlineValue != null) return inlineValue.intValue();
        for (int i = rules.size() - 1; i >= 0; i--) {
            Integer value = getInteger(rules.get(i).getDeclaration(), property);
            if (value != null) return value.intValue();
        }
        return defaultValue;
    }

    private static int cascadeTextColor(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules, ComputedStyle parentStyle) {
        if (inlineStyle.getTextColor() != null) return inlineStyle.getTextColor().intValue();
        for (int i = rules.size() - 1; i >= 0; i--) {
            Integer value = rules.get(i).getDeclaration().getTextColor();
            if (value != null) return value.intValue();
        }
        return inheritedTextColor(parentStyle);
    }

    private static long cascadeLong(Long inlineValue, List<UiStyleRule> rules, StyleProperty property, long defaultValue) {
        if (inlineValue != null) return inlineValue.longValue();
        for (int i = rules.size() - 1; i >= 0; i--) {
            Long value = getLong(rules.get(i).getDeclaration(), property);
            if (value != null) return value.longValue();
        }
        return defaultValue;
    }

    private static int cascadeInt(Integer inlineValue, List<UiStyleRule> rules, StyleProperty property, int defaultValue) {
        if (inlineValue != null) return inlineValue.intValue();
        for (int i = rules.size() - 1; i >= 0; i--) {
            Integer value = getInteger(rules.get(i).getDeclaration(), property);
            if (value != null) return value.intValue();
        }
        return defaultValue;
    }

    private static String cascadeString(String inlineValue, List<UiStyleRule> rules, StyleProperty property) {
        if (inlineValue != null) return inlineValue;
        for (int i = rules.size() - 1; i >= 0; i--) {
            String value = getString(rules.get(i).getDeclaration(), property);
            if (value != null) return value;
        }
        return null;
    }

    private static UiBoxSizing cascadeBoxSizing(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getBoxSizing() != null) return inlineStyle.getBoxSizing();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiBoxSizing value = rules.get(i).getDeclaration().getBoxSizing();
            if (value != null) return value;
        }
        return UiBoxSizing.CONTENT_BOX;
    }

    private static UiPosition cascadePosition(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getPosition() != null) return inlineStyle.getPosition();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiPosition value = rules.get(i).getDeclaration().getPosition();
            if (value != null) return value;
        }
        return UiPosition.STATIC;
    }

    private static UiOverflow cascadeOverflow(UiOverflow inlineValue, List<UiStyleRule> rules, StyleProperty property, UiOverflow defaultValue) {
        if (inlineValue != null) return inlineValue;
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiOverflow value = getOverflow(rules.get(i).getDeclaration(), property);
            if (value != null) return value;
        }
        return defaultValue;
    }

    private static UiFlexDirection cascadeFlexDirection(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getFlexDirection() != null) return inlineStyle.getFlexDirection();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiFlexDirection value = rules.get(i).getDeclaration().getFlexDirection();
            if (value != null) return value;
        }
        return UiFlexDirection.ROW;
    }

    private static UiAlignItems cascadeAlignItems(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getAlignItems() != null) return inlineStyle.getAlignItems();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiAlignItems value = rules.get(i).getDeclaration().getAlignItems();
            if (value != null) return value;
        }
        return UiAlignItems.STRETCH;
    }

    private static UiJustifyContent cascadeJustifyContent(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getJustifyContent() != null) return inlineStyle.getJustifyContent();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiJustifyContent value = rules.get(i).getDeclaration().getJustifyContent();
            if (value != null) return value;
        }
        return UiJustifyContent.START;
    }

    private static UiVerticalAlign cascadeVerticalAlign(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getVerticalAlign() != null) return inlineStyle.getVerticalAlign();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiVerticalAlign value = rules.get(i).getDeclaration().getVerticalAlign();
            if (value != null) return value;
        }
        return UiVerticalAlign.BASELINE;
    }

    private static UiAlignSelf cascadeAlignSelf(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getAlignSelf() != null) return inlineStyle.getAlignSelf();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiAlignSelf value = rules.get(i).getDeclaration().getAlignSelf();
            if (value != null) return value;
        }
        return UiAlignSelf.AUTO;
    }

    private static UiFlexWrap cascadeFlexWrap(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getFlexWrap() != null) return inlineStyle.getFlexWrap();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiFlexWrap value = rules.get(i).getDeclaration().getFlexWrap();
            if (value != null) return value;
        }
        return UiFlexWrap.NOWRAP;
    }

    private static UiBoxShadow cascadeBoxShadow(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getBoxShadow() != null) return inlineStyle.getBoxShadow();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiBoxShadow value = rules.get(i).getDeclaration().getBoxShadow();
            if (value != null) return value;
        }
        return null;
    }

    private static UiBorderStyle cascadeBorderStyle(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getBorderStyle() != null) return inlineStyle.getBorderStyle();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiBorderStyle value = rules.get(i).getDeclaration().getBorderStyle();
            if (value != null) return value;
        }
        return UiBorderStyle.NONE;
    }

    private static UiCursor cascadeCursor(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules, ComputedStyle parentStyle) {
        if (inlineStyle.getCursor() != null) return inlineStyle.getCursor();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiCursor value = rules.get(i).getDeclaration().getCursor();
            if (value != null) return value;
        }
        return inheritedCursor(parentStyle);
    }

    private static UiBorderRadius cascadeBorderRadiusCorners(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getBorderRadiusCorners() != null) return inlineStyle.getBorderRadiusCorners();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiBorderRadius value = rules.get(i).getDeclaration().getBorderRadiusCorners();
            if (value != null) return value;
        }
        return null;
    }

    private static UiTextDecoration cascadeTextDecoration(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getTextDecoration() != null) return inlineStyle.getTextDecoration();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiTextDecoration value = rules.get(i).getDeclaration().getTextDecoration();
            if (value != null) return value;
        }
        return UiTextDecoration.NONE;
    }

    private static UiPointerEvents cascadePointerEvents(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getPointerEvents() != null) return inlineStyle.getPointerEvents();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiPointerEvents value = rules.get(i).getDeclaration().getPointerEvents();
            if (value != null) return value;
        }
        return UiPointerEvents.AUTO;
    }

    private static UiOutline cascadeOutline(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getOutline() != null) return inlineStyle.getOutline();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiOutline value = rules.get(i).getDeclaration().getOutline();
            if (value != null) return value;
        }
        return null;
    }

    private static UiStyleInsets cascadeBorderWidthSides(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getBorderWidthSides() != null) return inlineStyle.getBorderWidthSides();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiStyleInsets value = rules.get(i).getDeclaration().getBorderWidthSides();
            if (value != null) return value;
        }
        return null;
    }

    private static UiBorderColors cascadeBorderColors(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getBorderColors() != null) return inlineStyle.getBorderColors();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiBorderColors value = rules.get(i).getDeclaration().getBorderColors();
            if (value != null) return value;
        }
        return null;
    }

    private static UiTextAlign cascadeTextAlign(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules, ComputedStyle parentStyle) {
        if (inlineStyle.getTextAlign() != null) return inlineStyle.getTextAlign();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiTextAlign value = rules.get(i).getDeclaration().getTextAlign();
            if (value != null) return value;
        }
        return inheritedTextAlign(parentStyle);
    }

    private static UiWhiteSpace cascadeWhiteSpace(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules, ComputedStyle parentStyle) {
        if (inlineStyle.getWhiteSpace() != null) return inlineStyle.getWhiteSpace();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiWhiteSpace value = rules.get(i).getDeclaration().getWhiteSpace();
            if (value != null) return value;
        }
        return inheritedWhiteSpace(parentStyle);
    }

    private static UiTextOverflow cascadeTextOverflow(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getTextOverflow() != null) return inlineStyle.getTextOverflow();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiTextOverflow value = rules.get(i).getDeclaration().getTextOverflow();
            if (value != null) return value;
        }
        return UiTextOverflow.CLIP;
    }

    private static UiVisibility cascadeVisibility(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules, ComputedStyle parentStyle) {
        if (inlineStyle.getVisibility() != null) return inlineStyle.getVisibility();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiVisibility value = rules.get(i).getDeclaration().getVisibility();
            if (value != null) return value;
        }
        return inheritedVisibility(parentStyle);
    }

    private static List<DocumentAnimationProperty> cascadeTransitionProperties(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getTransitionProperties() != null) return inlineStyle.getTransitionProperties();
        for (int i = rules.size() - 1; i >= 0; i--) {
            List<DocumentAnimationProperty> value = rules.get(i).getDeclaration().getTransitionProperties();
            if (value != null) return value;
        }
        return Collections.<DocumentAnimationProperty>emptyList();
    }

    private static DocumentAnimationTimingFunction cascadeTimingFunction(DocumentAnimationTimingFunction inlineValue,
            List<UiStyleRule> rules, StyleProperty property, DocumentAnimationTimingFunction defaultValue) {
        if (inlineValue != null) return inlineValue;
        for (int i = rules.size() - 1; i >= 0; i--) {
            DocumentAnimationTimingFunction value = getTimingFunction(rules.get(i).getDeclaration(), property);
            if (value != null) return value;
        }
        return defaultValue;
    }

    private static DocumentAnimationFillMode cascateAnimationFillMode(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules) {
        if (inlineStyle.getAnimationFillMode() != null) return inlineStyle.getAnimationFillMode();
        for (int i = rules.size() - 1; i >= 0; i--) {
            DocumentAnimationFillMode value = rules.get(i).getDeclaration().getAnimationFillMode();
            if (value != null) return value;
        }
        return DocumentAnimationFillMode.NONE;
    }

    // ========== 属性访问器 ==========

    private static UiStyleLength getStyleLength(UiStyleDeclaration decl, StyleProperty property) {
        switch (property) {
            case WIDTH: return decl.getWidth();
            case HEIGHT: return decl.getHeight();
            case TOP: return decl.getTop();
            case RIGHT: return decl.getRight();
            case BOTTOM: return decl.getBottom();
            case LEFT: return decl.getLeft();
            case BORDER_WIDTH: return decl.getBorderWidth();
            case BORDER_RADIUS: return decl.getBorderRadius();
            case ROW_GAP: return decl.getRowGap();
            case COLUMN_GAP: return decl.getColumnGap();
            case MIN_WIDTH: return decl.getMinWidth();
            case MAX_WIDTH: return decl.getMaxWidth();
            case MIN_HEIGHT: return decl.getMinHeight();
            case MAX_HEIGHT: return decl.getMaxHeight();
            case FLEX_BASIS: return decl.getFlexBasis();
            case LINE_HEIGHT: return decl.getLineHeight();
            case BACKDROP_BLUR_RADIUS: return decl.getBackdropBlurRadius();
            case LETTER_SPACING: return decl.getLetterSpacing();
            default: return null;
        }
    }

    private static UiStyleInsets getStyleInsets(UiStyleDeclaration decl, StyleProperty property) {
        switch (property) {
            case MARGIN: return decl.getMargin();
            case PADDING: return decl.getPadding();
            default: return null;
        }
    }

    private static Integer getInteger(UiStyleDeclaration decl, StyleProperty property) {
        switch (property) {
            case Z_INDEX: return decl.getZIndex();
            case BACKGROUND_COLOR: return decl.getBackgroundColor();
            case BORDER_COLOR: return decl.getBorderColor();
            case ANIMATION_ITERATION_COUNT: return decl.getAnimationIterationCount();
            default: return null;
        }
    }

    private static Float getFloat(UiStyleDeclaration decl, StyleProperty property) {
        switch (property) {
            case FLEX_GROW: return decl.getFlexGrow();
            case FLEX_SHRINK: return decl.getFlexShrink();
            case OPACITY: return decl.getOpacity();
            case BACKDROP_SATURATION: return decl.getBackdropSaturation();
            default: return null;
        }
    }

    private static Long getLong(UiStyleDeclaration decl, StyleProperty property) {
        switch (property) {
            case TRANSITION_DURATION: return decl.getTransitionDurationNanos();
            case TRANSITION_DELAY: return decl.getTransitionDelayNanos();
            case ANIMATION_DURATION: return decl.getAnimationDurationNanos();
            case ANIMATION_DELAY: return decl.getAnimationDelayNanos();
            default: return null;
        }
    }

    private static String getString(UiStyleDeclaration decl, StyleProperty property) {
        switch (property) {
            case ANIMATION_NAME: return decl.getAnimationName();
            default: return null;
        }
    }

    private static UiOverflow getOverflow(UiStyleDeclaration decl, StyleProperty property) {
        switch (property) {
            case OVERFLOW_X: return decl.getOverflowX();
            case OVERFLOW_Y: return decl.getOverflowY();
            default: return null;
        }
    }

    private static DocumentAnimationTimingFunction getTimingFunction(UiStyleDeclaration decl, StyleProperty property) {
        switch (property) {
            case TRANSITION_TIMING: return decl.getTransitionTimingFunction();
            case ANIMATION_TIMING: return decl.getAnimationTimingFunction();
            default: return null;
        }
    }

    // ========== 继承与默认值 ==========

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
        return parentStyle == null ? UiVisibility.VISIBLE : parentStyle.getVisibility();
    }

    private static UiCursor inheritedCursor(ComputedStyle parentStyle) {
        return parentStyle == null ? UiCursor.DEFAULT : parentStyle.getCursor();
    }

    private static UiStyleLength inheritedLetterSpacing(ComputedStyle parentStyle) {
        return parentStyle == null ? null : parentStyle.getLetterSpacing();
    }

    private static UiWordBreak cascadeWordBreak(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules, ComputedStyle parentStyle) {
        if (inlineStyle.getWordBreak() != null) return inlineStyle.getWordBreak();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiWordBreak value = rules.get(i).getDeclaration().getWordBreak();
            if (value != null) return value;
        }
        return parentStyle == null ? UiWordBreak.NORMAL : parentStyle.getWordBreak();
    }

    private static UiOverflowWrap cascadeOverflowWrap(UiStyleDeclaration inlineStyle, List<UiStyleRule> rules, ComputedStyle parentStyle) {
        if (inlineStyle.getOverflowWrap() != null) return inlineStyle.getOverflowWrap();
        for (int i = rules.size() - 1; i >= 0; i--) {
            UiOverflowWrap value = rules.get(i).getDeclaration().getOverflowWrap();
            if (value != null) return value;
        }
        return parentStyle == null ? UiOverflowWrap.NORMAL : parentStyle.getOverflowWrap();
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

    /**
     * 样式属性枚举，用于级联属性查找的分发。
     */
    private enum StyleProperty {
        WIDTH, HEIGHT, TOP, RIGHT, BOTTOM, LEFT, Z_INDEX,
        MARGIN, PADDING, BORDER_WIDTH, BORDER_RADIUS,
        OVERFLOW_X, OVERFLOW_Y,
        ROW_GAP, COLUMN_GAP,
        FLEX_GROW, FLEX_SHRINK, FLEX_BASIS,
        OPACITY, BACKGROUND_COLOR, BORDER_COLOR,
        TRANSITION_DURATION, TRANSITION_DELAY, TRANSITION_TIMING,
        ANIMATION_NAME, ANIMATION_DURATION, ANIMATION_DELAY, ANIMATION_ITERATION_COUNT, ANIMATION_TIMING,
        BACKDROP_BLUR_RADIUS, BACKDROP_SATURATION,
        LINE_HEIGHT, MIN_WIDTH, MAX_WIDTH, MIN_HEIGHT, MAX_HEIGHT,
        LETTER_SPACING
    }
}
