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
    /** scriptspace（em）：TeX 0.5pt = 0.05em（加在上标盒内部，不改变上标与主体间隙）。 */
    public static final float SCRIPT_SPACE_EM = 0.05F;

    /** 大运算符 limits 尾部间距（em）= TeX MEDMUSKIP（ScriptsAtom big-op 分支 deltaSymbol）。 */
    public static final float BIG_OPERATOR_TAIL_SPACE_EM = 0.2222F;

    // ==================== 大运算符 limits 参数（BigOperatorAtom limits 路径） ====================

    /** bigopspacing1（em）：limits 上标最小间隙。 */
    public static final float BIGOP1_EM = 0.111112F;
    /** bigopspacing2（em）：limits 下标最小间隙。 */
    public static final float BIGOP2_EM = 0.166667F;
    /** bigopspacing3（em）：limits 上标间隙上限参照。 */
    public static final float BIGOP3_EM = 0.2F;
    /** bigopspacing4（em）：limits 下标间隙上限参照。 */
    public static final float BIGOP4_EM = 0.6F;
    /** bigopspacing5（em）：limits 外沿留白。 */
    public static final float BIGOP5_EM = 0.1F;

    // ==================== script 口径 drop 参数 ====================

    /** script 口径上标基准抬升（em）= supdrop × scriptfactor（JLaTeXMath getSupDrop(subStyle) 语义）。 */
    public static final float SCRIPT_SUP_DROP_EM = SUP_DROP_EM * SCRIPT_SCALE;

    /** script 口径下标基准下沉（em）= subdrop × scriptfactor。 */
    public static final float SCRIPT_SUB_DROP_EM = SUB_DROP_EM * SCRIPT_SCALE;

    // ==================== 根号参数 ====================

    /** 根指数相对根号总高的抬升因子（TeX NthRoot FACTOR）。 */
    public static final float SQRT_INDEX_FACTOR = 0.55F;

    /**
     * 根号字形变体阶梯（em，cmex radical 变体深度：最小变体 0.96 → 更大 1.16/1.76/2.36）。
     * TeX DelimiterFactory 选最小 ≥ 目标高度的档位，多余一半补入 clr（NthRoot delta/2）。
     */
    public static final float[] SQRT_VARIANT_DEPTH_EM = {0.96F, 1.16F, 1.76F, 2.36F};

    /** 根指数与根号的负 kern（mu，TeX NthRoot -10mu）。 */
    public static final float SQRT_INDEX_NEG_KERN_MU = 10.0F;

    // ==================== 其他 ====================

    /** 数学单位 mu（em）= 1/18 em。 */
    public static final float MU_EM = 1.0F / 18.0F;

    /** 伸缩定界符最小高度系数（TeX FencedAtom DELIMITER_FACTOR = 901/500）。 */
    public static final float DELIMITER_FACTOR = 901.0F / 500.0F;

    /** 伸缩定界符短欠量（em，TeX DELIMITER_SHORTFALL = 5pt = 0.5em@10pt）。 */
    public static final float DELIMITER_SHORTFALL_EM = 0.5F;

    /** 矩阵列间隙（em，MatrixAtom hsep：列间全额 1em）。 */
    public static final float MATRIX_COL_GAP_EM = 1.0F;

    /** 矩阵行间隙（ex，MatrixAtom vsep_in：行间 1ex）。 */
    public static final float MATRIX_ROW_SEP_EX = 1.0F;

    /** 矩阵外沿 padding（ex，MatrixAtom vsep_ext_top/bot 0.4ex）。 */
    public static final float MATRIX_OUTER_PAD_EX = 0.4F;

    /** 无分数线分数（\binom）最小间隙 = 3 × 规则线粗（TeX FractionAtom no-rule clr）。 */
    public static final float NO_RULE_CLR_FACTOR = 3.0F;

    /** 上/下横线（\overline/\\underline）与内容间隙 = 3 × 规则线粗。 */
    public static final float OVERBAR_KERN_FACTOR = 3.0F;

    /** 上/下横线盒外沿余量 = 5 × 规则线粗（TeX OverlinedAtom/UnderlinedAtom）。 */
    public static final float OVERBAR_BOX_FACTOR = 5.0F;

    /** 数学间距：thin/medium/thick（mu，1mu = 1/18 em）。TeXBook p181。 */
    public static final float THIN_MU = 3.0F;
    public static final float MED_MU = 4.0F;
    public static final float THICK_MU = 5.0F;
}
