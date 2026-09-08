package club.heiqi.uilib.font.latex.node;

import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.MathStyleOverride;

/**
 * 显式间距节点（{@code \, \: \; \! \quad \qquad}）。
 */
public final class LatexSpace extends LatexNode {

    private final double emWidth;

    /**
     * 创建间距节点。
     *
     * @param emWidth 间距宽度（em，可为负，如 \! 为 -1/6 em）
     */
    public LatexSpace(double emWidth) {
        this(emWidth, MathStyleOverride.INHERIT);
    }

    /** 创建带局部数学样式声明的节点；样式不得为 null。 */
    public LatexSpace(double emWidth,
            MathStyleOverride mathStyleOverride) {
        super(Kind.SPACE, mathStyleOverride);
        this.emWidth = emWidth;
    }

    /** @return 间距宽度（em） */
    public double getEmWidth() {
        return emWidth;
    }

    @Override
    public String toString() {
        return "Space(" + emWidth + "em)";
    }
}
