package club.heiqi.uilib.font.latex.node;

import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.MathStyleOverride;

/**
 * 分数节点 {@code \frac{num}{den}}。
 */
public final class LatexFrac extends LatexNode {

    /** 仅覆盖分式自身，不改变外层脚本和列表间距的样式。 */
    public enum FractionStyle { INHERIT, DISPLAY, TEXT }

    private final FractionStyle fractionStyle;
    private final LatexNode numerator;
    private final LatexNode denominator;

    /**
     * 创建分数节点。
     *
     * @param numerator   分子
     * @param denominator 分母
     */
    public LatexFrac(LatexNode numerator, LatexNode denominator) {
        this(numerator, denominator, MathStyleOverride.INHERIT, FractionStyle.INHERIT);
    }

    /** 创建分式；两种样式参数均不得为 null。 */
    public LatexFrac(LatexNode numerator, LatexNode denominator,
            MathStyleOverride mathStyleOverride, FractionStyle fractionStyle) {
        super(Kind.FRAC, mathStyleOverride);
        if (fractionStyle == null) {
            throw new IllegalArgumentException("fractionStyle 不能为空");
        }
        this.fractionStyle = fractionStyle;
        if (numerator == null || denominator == null) {
            throw new IllegalArgumentException("分子分母不能为空");
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public FractionStyle getFractionStyle() {
        return fractionStyle;
    }

    public LatexNode getNumerator() {
        return numerator;
    }

    public LatexNode getDenominator() {
        return denominator;
    }

    @Override
    public String toString() {
        return "Frac(" + numerator + "/" + denominator + ")";
    }
}
