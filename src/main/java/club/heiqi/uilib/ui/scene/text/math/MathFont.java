package club.heiqi.uilib.ui.scene.text.math;

import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;

/** 数学盒消费的当前字号字体事实；字体所有权仍属于既有字体服务。 */
public interface MathFont {
    /** OpenType MATH 标识；百分比原值与其他逻辑像素值分开消费。 */
    enum Constant {
        SCRIPT_PERCENT_SCALE_DOWN(0),
        SCRIPT_SCRIPT_PERCENT_SCALE_DOWN(1),
        DELIMITED_SUB_FORMULA_MIN_HEIGHT(2),
        DISPLAY_OPERATOR_MIN_HEIGHT(3),
        MATH_LEADING(4),
        AXIS_HEIGHT(5),
        ACCENT_BASE_HEIGHT(6),
        FLATTENED_ACCENT_BASE_HEIGHT(7),
        SUBSCRIPT_SHIFT_DOWN(8),
        SUBSCRIPT_TOP_MAX(9),
        SUBSCRIPT_BASELINE_DROP_MIN(10),
        SUPERSCRIPT_SHIFT_UP(11),
        SUPERSCRIPT_SHIFT_UP_CRAMPED(12),
        SUPERSCRIPT_BOTTOM_MIN(13),
        SUPERSCRIPT_BASELINE_DROP_MAX(14),
        SUB_SUPERSCRIPT_GAP_MIN(15),
        SUPERSCRIPT_BOTTOM_MAX_WITH_SUBSCRIPT(16),
        SPACE_AFTER_SCRIPT(17),
        UPPER_LIMIT_GAP_MIN(18),
        UPPER_LIMIT_BASELINE_RISE_MIN(19),
        LOWER_LIMIT_GAP_MIN(20),
        LOWER_LIMIT_BASELINE_DROP_MIN(21),
        STACK_TOP_SHIFT_UP(22),
        STACK_TOP_DISPLAY_STYLE_SHIFT_UP(23),
        STACK_BOTTOM_SHIFT_DOWN(24),
        STACK_BOTTOM_DISPLAY_STYLE_SHIFT_DOWN(25),
        STACK_GAP_MIN(26),
        STACK_DISPLAY_STYLE_GAP_MIN(27),
        STRETCH_STACK_TOP_SHIFT_UP(28),
        STRETCH_STACK_BOTTOM_SHIFT_DOWN(29),
        STRETCH_STACK_GAP_ABOVE_MIN(30),
        STRETCH_STACK_GAP_BELOW_MIN(31),
        FRACTION_NUMERATOR_SHIFT_UP(32),
        FRACTION_NUMERATOR_DISPLAY_STYLE_SHIFT_UP(33),
        FRACTION_DENOMINATOR_SHIFT_DOWN(34),
        FRACTION_DENOMINATOR_DISPLAY_STYLE_SHIFT_DOWN(35),
        FRACTION_NUMERATOR_GAP_MIN(36),
        FRACTION_NUM_DISPLAY_STYLE_GAP_MIN(37),
        FRACTION_RULE_THICKNESS(38),
        FRACTION_DENOMINATOR_GAP_MIN(39),
        FRACTION_DENOM_DISPLAY_STYLE_GAP_MIN(40),
        SKEWED_FRACTION_HORIZONTAL_GAP(41),
        SKEWED_FRACTION_VERTICAL_GAP(42),
        OVERBAR_VERTICAL_GAP(43),
        OVERBAR_RULE_THICKNESS(44),
        OVERBAR_EXTRA_ASCENDER(45),
        UNDERBAR_VERTICAL_GAP(46),
        UNDERBAR_RULE_THICKNESS(47),
        UNDERBAR_EXTRA_DESCENDER(48),
        RADICAL_VERTICAL_GAP(49),
        RADICAL_DISPLAY_STYLE_VERTICAL_GAP(50),
        RADICAL_RULE_THICKNESS(51),
        RADICAL_EXTRA_ASCENDER(52),
        RADICAL_KERN_BEFORE_DEGREE(53),
        RADICAL_KERN_AFTER_DEGREE(54),
        RADICAL_DEGREE_BOTTOM_RAISE_PERCENT(55);
        public final int id;

        Constant(int id) {
            this.id = id;
        }

        public boolean percentage() {
            return this == SCRIPT_PERCENT_SCALE_DOWN
                    || this == SCRIPT_SCRIPT_PERCENT_SCALE_DOWN
                    || this == RADICAL_DEGREE_BOTTOM_RAISE_PERCENT;
        }
    }

    float constant(Constant value);

    /** 当前具名数学字体正常整形的文字，沿现有 TEXT 管线绘制。 */
    TextLayout text(String value);

    /** 真实竖向 variant 或部件构建；超出能力时明确报告，不任意变形。 */
    TextLayout stretch(int codepoint, float minimumHeight);

    /** 明确轴的真实伸缩；旧实现保留竖向能力，横向不伪造支持。 */
    default TextLayout stretch(int codepoint, float minimumAdvance, MathAxis axis) {
        java.util.Objects.requireNonNull(axis, "axis");
        if (axis == MathAxis.VERTICAL) return stretch(codepoint, minimumAdvance);
        throw new UnsupportedOperationException("数学字体不支持横向伸缩");
    }

    /** 单字形的真实 top-accent attachment，含布局 X 原点；无单字形数据时为 NaN。 */
    default float topAccentAttachment(TextLayout layout) {
        java.util.Objects.requireNonNull(layout, "layout");
        return Float.NaN;
    }

    /** 精确字形轮廓在 layout 坐标中的并集；UNKNOWN 表示未提供此附加度量能力。 */
    default InkBounds outlineBounds(TextLayout layout) {
        return InkBounds.UNKNOWN;
    }

    /** 显示型大算子选择真实完整字形：优先满足建议高度，否则使用最大的可用变体。 算子并非须覆盖内容的定高根号；不做非等比拉伸。 */
    default TextLayout displayOperator(int codepoint, float preferredHeight) {
        return stretch(codepoint, preferredHeight);
    }

    /** 单字形/伸缩构建的倾斜修正；无修正数据时为零。 */
    float italicCorrection(TextLayout layout);
}
