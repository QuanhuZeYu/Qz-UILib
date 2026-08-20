package club.heiqi.uilib.font.latex;

/**
 * LaTeX 演示公式清单（单一真相源）。
 *
 * <p>与真机演示页 {@code internal/devtools/playground/pages/LatexPage} 的 8 张公式卡
 * 同集，供 headless 软件渲染验收场地批量渲染：解析、二维布局、展平、收集、
 * 光栅化与像素断言均以此清单为输入。</p>
 */
public final class LatexShowcaseFormulas {

    /** 卡片1：公式速览（大字号 32px）。 */
    public static final String[] HERO = {
            "\\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}",
            "\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}",
            "\\int_0^\\infty e^{-x}\\,dx = 1",
    };

    /** 卡片2：分数与根号。 */
    public static final String[] FRAC_SQRT = {
            "\\frac{1}{2} + \\frac{a}{b}",
            "\\sqrt{x} + \\sqrt{x^2+y^2}",
            "\\sqrt[3]{x}",
    };

    /** 卡片3：上下标与函数名。 */
    public static final String[] SUP_SUB = {
            "x^2 + y_i + x_i^2",
            "e^{i\\pi} + 1 = 0",
            "\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1",
    };

    /** 卡片4：希腊字母与运算符。 */
    public static final String[] SYMBOLS = {
            "\\alpha + \\beta = \\gamma, \\Delta \\pi \\sigma",
            "\\epsilon \\varepsilon \\phi \\varphi \\theta \\vartheta",
            "a \\leq b \\geq c, x \\neq y, \\infty \\pm \\times",
    };

    /** 卡片5：矩阵与分段函数。 */
    public static final String[] MATRIX_CASES = {
            "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}",
            "f(x) = \\begin{cases} x & x > 0 \\\\ -x & x \\leq 0 \\end{cases}",
    };

    /** 卡片6：伸缩括号与组合数。 */
    public static final String[] DELIM_BINOM = {
            "\\left( \\frac{a}{b} \\right) \\left[ x \\right]",
            "\\binom{n}{k} = \\frac{n!}{k!(n-k)!}",
    };

    /** 卡片7：重音与上下划线。 */
    public static final String[] ACCENTS = {
            "\\hat{x} + \\bar{y} + \\vec{v} + \\dot{z} + \\tilde{w}",
            "\\overline{AB} + \\underline{x}",
    };

    /** 卡片8：公式内文本与样式继承。 */
    public static final String[] TEXT_INHERIT = {
            "\\text{速度} = \\frac{\\Delta s}{\\Delta t}",
            "x^2",
            "\\sqrt{y}",
            "\\frac{1}{2}",
    };

    private LatexShowcaseFormulas() {}

    /** 全部公式（按卡片顺序展平）。 */
    public static String[] all() {
        return new String[] {
                HERO[0], HERO[1], HERO[2],
                FRAC_SQRT[0], FRAC_SQRT[1], FRAC_SQRT[2],
                SUP_SUB[0], SUP_SUB[1], SUP_SUB[2],
                SYMBOLS[0], SYMBOLS[1], SYMBOLS[2],
                MATRIX_CASES[0], MATRIX_CASES[1],
                DELIM_BINOM[0], DELIM_BINOM[1],
                ACCENTS[0], ACCENTS[1],
                TEXT_INHERIT[0], TEXT_INHERIT[1], TEXT_INHERIT[2], TEXT_INHERIT[3],
        };
    }
}
