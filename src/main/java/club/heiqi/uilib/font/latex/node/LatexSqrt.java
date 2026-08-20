package club.heiqi.uilib.font.latex.node;

import club.heiqi.uilib.font.latex.LatexNode;

/**
 * 根号节点 {@code \sqrt[index]{radicand}}；index 可为 null（平方根）。
 */
public final class LatexSqrt extends LatexNode {

    private final LatexNode index;
    private final LatexNode radicand;

    /**
     * 创建根号节点。
     *
     * @param index    根指数（可为 null）
     * @param radicand 被开方内容
     */
    public LatexSqrt(LatexNode index, LatexNode radicand) {
        super(Kind.SQRT);
        if (radicand == null) {
            throw new IllegalArgumentException("radicand 不能为空");
        }
        this.index = index;
        this.radicand = radicand;
    }

    /** @return 根指数；null 表示平方根 */
    public LatexNode getIndex() {
        return index;
    }

    public LatexNode getRadicand() {
        return radicand;
    }

    @Override
    public String toString() {
        return "Sqrt(index=" + index + ", radicand=" + radicand + ")";
    }
}
