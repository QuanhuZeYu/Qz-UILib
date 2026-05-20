package club.heiqi.uilib.ui.style;

/**
 * 可参与级联控制的样式属性。
 *
 * <p>该枚举用于声明 {@code !important} 和通用级联关键字，不改变各属性原有的
 * 类型化 setter。属性是否默认继承会影响 {@code unset} 的解析语义。</p>
 */
public enum UiStyleProperty {

    DISPLAY(false, UiStyleChangeImpact.LAYOUT),
    WIDTH(false, UiStyleChangeImpact.LAYOUT),
    HEIGHT(false, UiStyleChangeImpact.LAYOUT),
    BOX_SIZING(false, UiStyleChangeImpact.LAYOUT),
    POSITION(false, UiStyleChangeImpact.LAYOUT),
    TOP(false, UiStyleChangeImpact.LAYOUT),
    RIGHT(false, UiStyleChangeImpact.LAYOUT),
    BOTTOM(false, UiStyleChangeImpact.LAYOUT),
    LEFT(false, UiStyleChangeImpact.LAYOUT),
    Z_INDEX(false, UiStyleChangeImpact.LAYOUT),
    MARGIN(false, UiStyleChangeImpact.LAYOUT),
    PADDING(false, UiStyleChangeImpact.LAYOUT),
    BORDER_WIDTH(false, UiStyleChangeImpact.LAYOUT),
    BORDER_RADIUS(false, UiStyleChangeImpact.PAINT),
    OVERFLOW_X(false, UiStyleChangeImpact.LAYOUT),
    OVERFLOW_Y(false, UiStyleChangeImpact.LAYOUT),
    FLEX_DIRECTION(false, UiStyleChangeImpact.LAYOUT),
    ALIGN_ITEMS(false, UiStyleChangeImpact.LAYOUT),
    JUSTIFY_CONTENT(false, UiStyleChangeImpact.LAYOUT),
    VERTICAL_ALIGN(false, UiStyleChangeImpact.LAYOUT),
    ROW_GAP(false, UiStyleChangeImpact.LAYOUT),
    COLUMN_GAP(false, UiStyleChangeImpact.LAYOUT),
    FLEX_GROW(false, UiStyleChangeImpact.LAYOUT),
    FLEX_SHRINK(false, UiStyleChangeImpact.LAYOUT),
    ORDER(false, UiStyleChangeImpact.LAYOUT),
    OPACITY(false, UiStyleChangeImpact.PAINT),
    BACKGROUND_COLOR(false, UiStyleChangeImpact.PAINT),
    BORDER_COLOR(false, UiStyleChangeImpact.PAINT),
    TEXT_COLOR(true, UiStyleChangeImpact.PAINT),
    TRANSITION_PROPERTIES(false, UiStyleChangeImpact.PAINT),
    TRANSITION_DURATION(false, UiStyleChangeImpact.PAINT),
    TRANSITION_DELAY(false, UiStyleChangeImpact.PAINT),
    TRANSITION_TIMING(false, UiStyleChangeImpact.PAINT),
    ANIMATION_NAME(false, UiStyleChangeImpact.PAINT),
    ANIMATION_DURATION(false, UiStyleChangeImpact.PAINT),
    ANIMATION_DELAY(false, UiStyleChangeImpact.PAINT),
    ANIMATION_ITERATION_COUNT(false, UiStyleChangeImpact.PAINT),
    ANIMATION_FILL_MODE(false, UiStyleChangeImpact.PAINT),
    ANIMATION_TIMING(false, UiStyleChangeImpact.PAINT),
    BACKDROP_BLUR_RADIUS(false, UiStyleChangeImpact.PAINT),
    BACKDROP_SATURATION(false, UiStyleChangeImpact.PAINT),
    LINE_HEIGHT(true, UiStyleChangeImpact.LAYOUT),
    TEXT_ALIGN(true, UiStyleChangeImpact.LAYOUT),
    WHITE_SPACE(true, UiStyleChangeImpact.LAYOUT),
    TEXT_OVERFLOW(false, UiStyleChangeImpact.LAYOUT),
    VISIBILITY(true, UiStyleChangeImpact.PAINT),
    MIN_WIDTH(false, UiStyleChangeImpact.LAYOUT),
    MAX_WIDTH(false, UiStyleChangeImpact.LAYOUT),
    MIN_HEIGHT(false, UiStyleChangeImpact.LAYOUT),
    MAX_HEIGHT(false, UiStyleChangeImpact.LAYOUT),
    FLEX_BASIS(false, UiStyleChangeImpact.LAYOUT),
    ALIGN_SELF(false, UiStyleChangeImpact.LAYOUT),
    FLEX_WRAP(false, UiStyleChangeImpact.LAYOUT),
    BOX_SHADOW(false, UiStyleChangeImpact.PAINT),
    BORDER_STYLE(false, UiStyleChangeImpact.PAINT),
    BORDER_COLLAPSE(false, UiStyleChangeImpact.LAYOUT),
    CURSOR(true, UiStyleChangeImpact.PAINT),
    BORDER_RADIUS_CORNERS(false, UiStyleChangeImpact.PAINT),
    TEXT_DECORATION(false, UiStyleChangeImpact.PAINT),
    FONT_WEIGHT(true, UiStyleChangeImpact.LAYOUT),
    FONT_STYLE(true, UiStyleChangeImpact.PAINT),
    POINTER_EVENTS(false, UiStyleChangeImpact.PAINT),
    OUTLINE(false, UiStyleChangeImpact.PAINT),
    BORDER_WIDTH_SIDES(false, UiStyleChangeImpact.LAYOUT),
    BORDER_COLORS(false, UiStyleChangeImpact.PAINT),
    LETTER_SPACING(true, UiStyleChangeImpact.LAYOUT),
    WORD_BREAK(true, UiStyleChangeImpact.LAYOUT),
    OVERFLOW_WRAP(true, UiStyleChangeImpact.LAYOUT),
    ASPECT_RATIO(false, UiStyleChangeImpact.LAYOUT),
    OBJECT_FIT(false, UiStyleChangeImpact.PAINT),
    CONTENT(false, UiStyleChangeImpact.LAYOUT),
    SCROLLBAR_COLOR(true, UiStyleChangeImpact.PAINT),
    SCROLLBAR_WIDTH(false, UiStyleChangeImpact.LAYOUT),
    LIST_STYLE_TYPE(true, UiStyleChangeImpact.LAYOUT);

    private final boolean inheritedByDefault;
    private final UiStyleChangeImpact changeImpact;

    UiStyleProperty(boolean inheritedByDefault, UiStyleChangeImpact changeImpact) {
        this.inheritedByDefault = inheritedByDefault;
        this.changeImpact = changeImpact;
    }

    /**
     * 判断属性是否默认继承父元素计算值。
     *
     * @return 是否默认继承
     */
    public boolean isInheritedByDefault() {
        return inheritedByDefault;
    }

    /**
     * 返回该属性变化默认造成的失效范围。
     *
     * @return 样式变化影响范围
     */
    public UiStyleChangeImpact getChangeImpact() {
        return changeImpact;
    }
}
