package club.heiqi.uilib.font.latex.node;

import club.heiqi.uilib.font.latex.LatexNode;

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
        super(Kind.SPACE);
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
