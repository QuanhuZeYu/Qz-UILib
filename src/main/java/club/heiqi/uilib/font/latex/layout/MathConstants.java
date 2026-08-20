package club.heiqi.uilib.font.latex.layout;

/**
 * 数学布局常量。
 *
 * <p>数值对齐 TeX（Computer Modern fontdimen，JLaTeXMath 1.0.7 DefaultTeXFont.xml 取证，
 * 调研文档《调研-TeX排版算法借鉴-JLaTeXMath.md》）：em 基准 = 当前公式正文字号（px），
 * mu = 1/18em。原拍脑袋比例常量已由 TeX 参数表取代。</p>
 */
public final class MathConstants {

    private MathConstants() {
    }

    /** script 字号缩放（上/下标、分数分子分母）。TeX scriptfactor。 */
    public static final float SCRIPT_SCALE = 0.7F;

    /** scriptscript 字号缩放（二级缩放，预留）。TeX scriptscriptfactor。 */
    public static final float SCRIPT_SCRIPT_SCALE = 0.5F;

    /** 数学轴高（em）：分数线/大运算符的垂直中心。 */
    public static final float AXIS_HEIGHT_EM = 0.25F;

    /** 默认规则线粗（em）。TeX defaultrulethickness。 */
    public static final float RULE_THICKNESS_EM = 0.04F;

    // ==================== 分数参数（TeX num1..3/denom1..2） ====================

    /** 显示样式分子最小间隙（em）。 */
    public static final float NUM1_EM = 0.676508F;
    /** 文本样式分子最小间隙（em）。 */
    public static final float NUM2_EM = 0.393732F;
    /** 无分数线分子最小间隙（em）。 */
    public static final float NUM3_EM = 0.443731F;
    /** 显示样式分母最小间隙（em）。 */
    public static final float DENOM1_EM = 0.685951F;
    /** 文本样式分母最小间隙（em）。 */
    public static final float DENOM2_EM = 0.344841F;
    /** 分数两侧 nulldelimiterspace（em，每侧）。 */
    public static final float NULL_DELIMITER_SPACE_EM = 0.12F;

    // ==================== 上下标参数（TeX sup1..3/sub1..2/supdrop/subdrop） ====================

    /** 显示样式上标最小抬升（em）。 */
    public static final float SUP1_EM = 0.412892F;
    /** 文本样式上标最小抬升（em）。 */
    public static final float SUP2_EM = 0.362892F;
    /** cramped 样式上标最小抬升（em）。 */
    public static final float SUP3_EM = 0.288889F;
    /** 无上标时下标最小下沉（em）。 */
    public static final float SUB1_EM = 0.15F;
    /** 有上标时下标最小下沉（em）。 */
    public static final float SUB2_EM = 0.247217F;
    /** 上标基准抬升 = base 高 − supdrop（em）。 */
    public static final float SUP_DROP_EM = 0.386108F;
    /** 下标基准下沉 = base 深 + subdrop（em）。 */
    public static final float SUB_DROP_EM = 0.05F;
    /** scriptspace（em）：上/下标与主体的水平空隙（TeX 0.5pt = 0.05em）。 */
    public static final float SCRIPT_SPACE_EM = 0.05F;

    // ==================== 大运算符 limits 参数 ====================

    /** limits 上标与运算符间隙（em，ScriptsAtom limits 路径 3pt）。 */
    public static final float LIMITS_OVER_GAP_EM = 0.3F;
    /** limits 下标与运算符间隙（em，ScriptsAtom limits 路径 0.3pt）。 */
    public static final float LIMITS_UNDER_GAP_EM = 0.03F;

    // ==================== 根号参数 ====================

    /** 根指数相对根号总高的抬升因子（TeX NthRoot FACTOR）。 */
    public static final float SQRT_INDEX_FACTOR = 0.55F;

    // ==================== 其他 ====================

    /** 伸缩定界符与内容的最小间隙（em，每侧）。 */
    public static final float DELIM_GAP_EM = 0.05F;

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

    /** 数学间距：thin/medium/thick（mu，1mu = 1/18 em）。TeXBook p181。 */
    public static final float THIN_MU = 3.0F;
    public static final float MED_MU = 4.0F;
    public static final float THICK_MU = 5.0F;
}
