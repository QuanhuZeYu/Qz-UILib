package club.heiqi.uilib.font.latex.node;

import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.MathStyleOverride;

/**
 * 上/下标节点（{@code base^sup_sub}）；sup 与 sub 至少一个非空。
 */
public final class LatexSupSub extends LatexNode {

    private final LatexNode base;
    private final LatexNode sup;
    private final LatexNode sub;

    /**
     * 创建上/下标节点。
     *
     * @param base 基底原子/节点
     * @param sup  上标内容（可为 null）
     * @param sub  下标内容（可为 null）
     */
    public LatexSupSub(LatexNode base, LatexNode sup, LatexNode sub) {
        this(base, sup, sub, MathStyleOverride.INHERIT);
    }

    /** 创建带局部数学样式声明的节点；样式不得为 null。 */
    public LatexSupSub(LatexNode base, LatexNode sup, LatexNode sub,
            MathStyleOverride mathStyleOverride) {
        super(Kind.SUP_SUB, mathStyleOverride);
        if (base == null) {
            throw new IllegalArgumentException("base 不能为空");
        }
        if (sup == null && sub == null) {
            throw new IllegalArgumentException("sup/sub 至少一个非空");
        }
        this.base = base;
        this.sup = sup;
        this.sub = sub;
    }

    public LatexNode getBase() {
        return base;
    }

    /** @return 上标内容；null 表示无上标 */
    public LatexNode getSup() {
        return sup;
    }

    /** @return 下标内容；null 表示无下标 */
    public LatexNode getSub() {
        return sub;
    }

    @Override
    public String toString() {
        return "SupSub(base=" + base + ", sup=" + sup + ", sub=" + sub + ")";
    }
}
