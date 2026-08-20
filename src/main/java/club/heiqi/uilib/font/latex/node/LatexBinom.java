package club.heiqi.uilib.font.latex.node;

import club.heiqi.uilib.font.latex.LatexNode;

/**
 * 组合数节点 {@code \binom{n}{k}}。
 */
public final class LatexBinom extends LatexNode {

    private final LatexNode upper;
    private final LatexNode lower;

    /**
     * 创建组合数节点。
     *
     * @param upper 上元素
     * @param lower 下元素
     */
    public LatexBinom(LatexNode upper, LatexNode lower) {
        super(Kind.BINOM);
        if (upper == null || lower == null) {
            throw new IllegalArgumentException("upper/lower 不能为空");
        }
        this.upper = upper;
        this.lower = lower;
    }

    public LatexNode getUpper() {
        return upper;
    }

    public LatexNode getLower() {
        return lower;
    }

    @Override
    public String toString() {
        return "Binom(" + upper + ", " + lower + ")";
    }
}
