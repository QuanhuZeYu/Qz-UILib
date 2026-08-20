package club.heiqi.uilib.font.latex.layout;

/**
 * 数学布局常量（规划 §7.1 决议：不读字体 MATH 表，用现有度量的比例近似）。
 *
 * <p>em 基准 = 当前公式正文字号（px）。</p>
 */
public final class MathConstants {

    private MathConstants() {
    }

    /** script 字号缩放（上/下标、分数分子分母）。 */
    public static final float SCRIPT_SCALE = 0.7F;

    /** scriptscript 字号缩放（上标的上标等二级缩放，预留）。 */
    public static final float SCRIPT_SCRIPT_SCALE = 0.49F;

    /** 数学轴高（em，相对 ascent 的比例）：关系符垂直中心与分数 bar 定位参考。 */
    public static final float AXIS_HEIGHT_ASCENT_RATIO = 0.25F;

    /** 分数线/根号横线粗细（em）。 */
    public static final float RULE_THICKNESS_EM = 0.06F;

    /** 上标基线抬升（em），与 TextStyle.SUP_RAISE_EM 一致。 */
    public static final float SUP_RAISE_EM = 0.4F;

    /** 下标基线下沉（em），与 TextStyle.SUB_DROP_EM 一致。 */
    public static final float SUB_DROP_EM = 0.25F;

    /** 上/下标最小空隙（em）：与主体盒的最小间距保护。 */
    public static final float SUP_SUB_MIN_GAP_EM = 0.08F;

    /** 分数条上下空隙（em）：分子底/分母顶与 bar 的距离。 */
    public static final float FRAC_GAP_EM = 0.08F;

    /** 分数条两端超出内容宽度（em，每侧）。 */
    public static final float FRAC_OVERHANG_EM = 0.06F;

    /** 根号横线距被开方内容顶的额外高度（em）。 */
    public static final float SQRT_CLEARANCE_EM = 0.1F;

    /** 根指数在根号左上方的抬升（em）。 */
    public static final float SQRT_INDEX_RAISE_EM = 0.35F;

    /** 伸缩定界符与内容的最小间隙（em，每侧）。 */
    public static final float DELIM_GAP_EM = 0.05F;

    /** 大运算符上下限与符号的间隙（em）。 */
    public static final float LIMITS_GAP_EM = 0.05F;

    /** 矩阵列间隙（em，每列两侧合计）。 */
    public static final float MATRIX_COL_GAP_EM = 0.4F;

    /** 矩阵行间隙（em，每行上下合计）。 */
    public static final float MATRIX_ROW_GAP_EM = 0.3F;

    /** 矩阵定界符超出内容高度（em）。 */
    public static final float MATRIX_FENCE_OVERHANG_EM = 0.15F;

    /** binom 上下元素间隙（em）。 */
    public static final float BINOM_GAP_EM = 0.05F;

    /** 重音与基底的间隙（em）。 */
    public static final float ACCENT_GAP_EM = 0.05F;

    /** 数学间距：thin/medium/thick（mu，1mu = 1/18 em）。 */
    public static final float THIN_MU = 3.0F;
    public static final float MED_MU = 4.0F;
    public static final float THICK_MU = 5.0F;
}
