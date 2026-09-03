package club.heiqi.uilib.font.latex.layout;

/**
 * 公式盒内的矩形规则线（分数线、根号横线、overline/underline、竖线定界）。
 *
 * <p>渲染侧（M3）映射到 {@code FontBatchRenderer.collectDecoration}。</p>
 */
public final class RuleElem {

    /** 该线没有"与上下相邻内容的间隙"这一语义（根号横线、overline 等）时的取值。 */
    public static final float NO_CLEARANCE = Float.NaN;

    private final float x;
    private final float y;
    private final float width;
    private final float thickness;
    private final float clearanceAbove;
    private final float clearanceBelow;

    /**
     * 创建规则线（无间隙语义）。
     *
     * @param x         相对盒原点的水平偏移
     * @param y         矩形<b>中心</b>相对盒基线的垂直偏移（负 = 基线上方）——渲染侧按中心消费，
     *                  先换算中心→顶再落整像素行（{@code DefaultFontRendererAdapter} 装饰线收集处）
     * @param width     宽度
     * @param thickness 粗细（高度）
     */
    public RuleElem(float x, float y, float width, float thickness) {
        this(x, y, width, thickness, NO_CLEARANCE, NO_CLEARANCE);
    }

    /**
     * 创建带<b>设计间隙</b>的规则线：由布局方在算出托底量（如 TeX 的 {@code kern1}/{@code kern2}）
     * 时一并记录，供验收直接读取。
     *
     * <p>为什么必须由布局方记录而不是让判据自己量：渲染侧的 quad 是<b>供纹理采样的外扩盒</b>
     * （{@code FontBatchRenderer} 按 {@code INK_BLEED × glyphScale} 四周扩过），线顶又被有意量化到
     * 整像素行 —— 两者合起来的系统偏置与阈值同量级，渲染像素量不回"间隙 ≥ 0.5px"这句话。
     * 该间隙是布局在这条线上真正兑现的设计事实，说得出准。</p>
     *
     * @param clearanceAbove 上方内容 ink 底到<b>线顶</b>的设计间隙（px，NaN = 不适用）
     * @param clearanceBelow 线<b>底</b>到下方内容 ink 顶的设计间隙（px，NaN = 不适用）
     */
    public RuleElem(float x, float y, float width, float thickness, float clearanceAbove,
            float clearanceBelow) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.thickness = thickness;
        this.clearanceAbove = clearanceAbove;
        this.clearanceBelow = clearanceBelow;
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

    /** 上方内容到线顶的设计间隙（px）；{@link #NO_CLEARANCE} 表示该线无此语义。 */
    public float getClearanceAbove() {
        return clearanceAbove;
    }

    /** 线底到下方内容的设计间隙（px）；{@link #NO_CLEARANCE} 表示该线无此语义。 */
    public float getClearanceBelow() {
        return clearanceBelow;
    }

    @Override
    public String toString() {
        return "Rule(x=" + x + ", y=" + y + ", w=" + width + ", t=" + thickness
                + ", above=" + clearanceAbove + ", below=" + clearanceBelow + ")";
    }
}
