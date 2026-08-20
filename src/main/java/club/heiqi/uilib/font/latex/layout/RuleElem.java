package club.heiqi.uilib.font.latex.layout;

/**
 * 公式盒内的矩形规则线（分数线、根号横线、overline/underline、竖线定界）。
 *
 * <p>渲染侧（M3）映射到 {@code FontBatchRenderer.collectDecoration}。</p>
 */
public final class RuleElem {

    private final float x;
    private final float y;
    private final float width;
    private final float thickness;

    /**
     * 创建规则线。
     *
     * @param x         相对盒基线的水平偏移
     * @param y         矩形<b>顶边</b>相对盒基线的垂直偏移（负 = 基线上方）
     * @param width     宽度
     * @param thickness 粗细（高度）
     */
    public RuleElem(float x, float y, float width, float thickness) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.thickness = thickness;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getThickness() {
        return thickness;
    }

    @Override
    public String toString() {
        return "Rule(x=" + x + ", y=" + y + ", w=" + width + ", t=" + thickness + ")";
    }
}
