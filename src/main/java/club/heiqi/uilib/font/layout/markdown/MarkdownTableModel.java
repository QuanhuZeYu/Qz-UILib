package club.heiqi.uilib.font.layout.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.layout.TextSegment;

/**
 * 表格的最小跨层数据契约：仅行、单元格与对齐，不包含像素、度量或完整块子树。
 *
 * <p>由 {@link MarkdownDocument#toTableModels} 导出。TABLE 于 T2 前只解析、不布局；
 * 旧段流与行接缝继续保持历史字面降级。集合均不可修改。</p>
 */
public final class MarkdownTableModel {

    /** 未指定对齐与显式左、中、右对齐彼此区分。 */
    public enum Alignment { NONE, LEFT, CENTER, RIGHT }

    private final List<Integer> blockPath;
    private final List<Alignment> alignments;
    private final Row header;
    private final List<Row> rows;

    MarkdownTableModel(List<Integer> blockPath, List<Alignment> alignments, Row header, List<Row> rows) {
        this.blockPath = immutable(blockPath);
        this.alignments = immutable(alignments);
        this.header = header;
        this.rows = immutable(rows);
    }

    /**
     * @return 文档语义块树的零基索引路径（顶层块、逐层子块）；同一文档内唯一。
     * 此锚不等同于旧字面降级行的 blockId，也不承诺跨文档编辑稳定。
     */
    public List<Integer> getBlockPath() { return blockPath; }

    /** @return 各列对齐，列数由表头与分隔行共同确定 */
    public List<Alignment> getAlignments() { return alignments; }

    /** @return 唯一表头行 */
    public Row getHeader() { return header; }

    /** @return 正文行（不含表头），允许为空 */
    public List<Row> getRows() { return rows; }

    /** 一行单元格，列数恒等于表格的对齐列表长度。 */
    public static final class Row {
        private final List<Cell> cells;

        Row(List<Cell> cells) { this.cells = immutable(cells); }

        /** @return 按列排列的单元格 */
        public List<Cell> getCells() { return cells; }
    }

    /** 单元格行内内容，复用现有 TextSegment（包括 latex 原子与链接）。 */
    public static final class Cell {
        private final List<TextSegment> segments;

        Cell(List<TextSegment> segments) { this.segments = copySegments(segments); }

        /** @return 经过既有行内解析器生成的片段；空单元格为空列表 */
        public List<TextSegment> getSegments() { return copySegments(segments); }

        // TextStyle 可变：构造和读取两端均隔离，包含 latex 原子。
        private static List<TextSegment> copySegments(List<TextSegment> source) {
            List<TextSegment> copy = new ArrayList<TextSegment>();
            for (TextSegment segment : source) {
                copy.add(segment.isLatex()
                        ? TextSegment.forLatex(segment.getLatexSource(), segment.getStyle().copy())
                        : new TextSegment(segment.getText(), segment.getStyle().copy()));
            }
            return Collections.unmodifiableList(copy);
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
