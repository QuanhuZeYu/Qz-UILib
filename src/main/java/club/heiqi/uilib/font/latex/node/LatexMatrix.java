package club.heiqi.uilib.font.latex.node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.latex.LatexNode;

/**
 * 矩阵环境节点（{@code \begin{matrix/pmatrix/bmatrix/vmatrix/cases}...\end{...}}）。
 *
 * <p>行内以 {@code &} 分列、{@code \\} 换行。rows 为三维结构：
 * 行 → 列（每格一个节点列表）→ 该格内节点；列数可不等，布局时按最长列对齐。</p>
 */
public final class LatexMatrix extends LatexNode {

    /** 矩阵外围定界。 */
    public enum Fence {
        /** matrix：无括号。 */
        NONE,
        /** pmatrix：圆括号。 */
        PAREN,
        /** bmatrix：方括号。 */
        BRACKET,
        /** vmatrix：竖线。 */
        BAR,
        /** cases：左花括号（分段函数，前两列按值/条件对齐）。 */
        CASES,
    }

    private final Fence fence;
    /** 行 → 列 → 格内节点列表（深防御拷贝，不可变）。 */
    private final List<List<List<LatexNode>>> rows;
    /** array 列说明（'l'/'c'/'r'，可为 null = 按 fence 默认：cases 左对齐、其余居中）。 */
    private final List<Character> columnAligns;

    /**
     * 创建矩阵节点（深防御拷贝，无显式列说明）。
     *
     * @param fence 外围定界
     * @param rows  行 → 列 → 格内节点
     */
    public LatexMatrix(Fence fence, List<List<List<LatexNode>>> rows) {
        this(fence, rows, null);
    }

    /**
     * 创建矩阵节点（深防御拷贝）。
     *
     * @param fence        外围定界
     * @param rows         行 → 列 → 格内节点
     * @param columnAligns array 列说明（\{ll\} 等，仅取 l/c/r；null 按 fence 默认）
     */
    public LatexMatrix(Fence fence, List<List<List<LatexNode>>> rows, List<Character> columnAligns) {
        super(Kind.MATRIX);
        if (fence == null) {
            throw new IllegalArgumentException("fence 不能为空");
        }
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("rows 不能为空");
        }
        this.fence = fence;
        List<List<List<LatexNode>>> copy = new ArrayList<List<List<LatexNode>>>(rows.size());
        for (List<List<LatexNode>> row : rows) {
            List<List<LatexNode>> rowCopy = new ArrayList<List<LatexNode>>(row.size());
            for (List<LatexNode> cell : row) {
                rowCopy.add(Collections.unmodifiableList(new ArrayList<LatexNode>(cell)));
            }
            copy.add(Collections.unmodifiableList(rowCopy));
        }
        this.rows = Collections.unmodifiableList(copy);
        this.columnAligns = columnAligns == null ? null
                : Collections.unmodifiableList(new ArrayList<Character>(columnAligns));
    }

    public Fence getFence() {
        return fence;
    }

    /**
     * 指定列的对齐方式：array 列说明（l/c/r）；缺省按 fence 默认——cases 左对齐
     * （TeX array{ll}）、其余环境居中（TeX array c）。
     */
    public char columnAlignOf(int column) {
        if (columnAligns != null && column >= 0 && column < columnAligns.size()) {
            char align = columnAligns.get(column).charValue();
            if (align == 'l' || align == 'r' || align == 'c') {
                return align;
            }
        }
        return fence == Fence.CASES ? 'l' : 'c';
    }

    /** @return 行 → 列 → 格内节点（不可变） */
    public List<List<List<LatexNode>>> getRows() {
        return rows;
    }

    @Override
    public String toString() {
        int cols = 0;
        for (List<List<LatexNode>> row : rows) {
            cols = Math.max(cols, row.size());
        }
        return "Matrix(" + fence + ", rows=" + rows.size() + ", cols=" + cols + ")";
    }
}
