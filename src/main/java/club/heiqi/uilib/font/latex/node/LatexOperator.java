package club.heiqi.uilib.font.latex.node;

import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.MathStyleOverride;

/** 命名算子：主体保留完整数学结构，对外始终为 OP。 */
public final class LatexOperator extends LatexNode {

    private final LatexNode body;
    private final boolean limitsInDisplayStyle;
    private int limitsFlag = LatexAtom.LIMITS_DEFAULT;

    public LatexOperator(LatexNode body, boolean limitsInDisplayStyle) {
        this(body, limitsInDisplayStyle, MathStyleOverride.INHERIT);
    }

    public LatexOperator(LatexNode body, boolean limitsInDisplayStyle, MathStyleOverride mathStyleOverride) {
        super(Kind.OPERATOR, mathStyleOverride);
        if (body == null) {
            throw new IllegalArgumentException("body 不能为空");
        }
        this.body = body;
        this.limitsInDisplayStyle = limitsInDisplayStyle;
    }

    public LatexNode getBody() {
        return body;
    }

    public boolean isLimitsInDisplayStyle() {
        return limitsInDisplayStyle;
    }

    public int getLimitsFlag() {
        return limitsFlag;
    }

    /** 解析期修饰；默认、强制堆叠、强制侧挂沿用 LatexAtom 的三态常量。 */
    public void setLimitsFlag(int limitsFlag) {
        if (limitsFlag != LatexAtom.LIMITS_DEFAULT && limitsFlag != LatexAtom.LIMITS_LIMITS
                && limitsFlag != LatexAtom.LIMITS_NOLIMITS) {
            throw new IllegalArgumentException("未知 limitsFlag: " + limitsFlag);
        }
        this.limitsFlag = limitsFlag;
    }

    @Override
    public String toString() {
        return "Operator(" + body + ", displayLimits=" + limitsInDisplayStyle + ")";
    }
}
