package club.heiqi.uilib.font.latex.node;

import club.heiqi.uilib.font.latex.LatexNode;

/**
 * 分数节点 {@code \frac{num}{den}}。
 */
public final class LatexFrac extends LatexNode {

    private final LatexNode numerator;
    private final LatexNode denominator;

    /**
     * 创建分数节点。
     *
     * @param numerator   分子
     * @param denominator 分母
     */
    public LatexFrac(LatexNode numerator, LatexNode denominator) {
        super(Kind.FRAC);
        if (numerator == null || denominator == null) {
            throw new IllegalArgumentException("分子分母不能为空");
        }
        this.numerator = numerator;
        this.denominator = denominator;
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
