package club.heiqi.uilib.font.latex;

/**
 * LaTeX 数学 AST 节点基类（不可变）。
 *
 * <p>节点由 {@link LatexParser} 产出、由数学布局层消费；节点树本身不含像素尺寸，
 * 布局结果由后续里程碑的 MathBox 体系承载。</p>
 */
public abstract class LatexNode {

    /** 节点类别，供布局/渲染分派。 */
    public enum Kind {
        /** 普通原子（字符、符号命令、函数名、\text 内容）。 */
        ATOM,
        /** 上/下标（base + sup + sub，sup/sub 可空）。 */
        SUP_SUB,
        /** 分数 \frac{}{}。 */
        FRAC,
        /** 根号 \sqrt[]{}。 */
        SQRT,
        /** 花括号分组 {...}。 */
        GROUP,
        /** 可伸缩括号 \left...\right...。 */
        LEFT_RIGHT,
        /** 矩阵环境（matrix/pmatrix/bmatrix/vmatrix/cases）。 */
        MATRIX,
        /** 组合数 \binom{}{}。 */
        BINOM,
        /** 重音（\hat \bar \vec \dot \ddot \tilde \overline \\underline）。 */
        ACCENT,
        /** 显式间距（\, \: \; \! \quad \qquad）。 */
        SPACE,
    }

    private final Kind kind;

    protected LatexNode(Kind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind 不能为空");
        }
        this.kind = kind;
    }

    /** @return 节点类别 */
    public Kind getKind() {
        return kind;
    }

    @Override
    public String toString() {
        return getKind().name();
    }
}
