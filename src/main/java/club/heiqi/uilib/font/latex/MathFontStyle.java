package club.heiqi.uilib.font.latex;

/** 数学原子的局部字体选择；INHERIT 延续既有数学字形规则。 */
public enum MathFontStyle {
    INHERIT,
    UPRIGHT,
    ITALIC,
    /** 显式粗正体数学 alphabet；不等于所有符号通用加粗。 */
    BOLD,
    /** 显式复位局部数学字体，仍屏蔽宿主斜体的再次叠加。 */
    MATH_NORMAL
}
