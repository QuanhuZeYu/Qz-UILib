package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 共同文档表格的不可变内容；单元格保留块结构，来源沿入口的 BYTE/UTF16 单位。 */
public final class TableContent {
    public enum Alignment {
        DEFAULT,
        LEFT,
        CENTER,
        RIGHT
    }

    public static final class Cell {
        public final Alignment alignment;
        public final boolean header;
        public final int sourceStart, sourceEnd;
        private final List<DocumentContent.Block> content;

        public Cell(
                List<DocumentContent.Block> content,
                Alignment alignment,
                boolean header,
                int sourceStart,
                int sourceEnd) {
            range(sourceStart, sourceEnd);
            this.content = immutable(content);
            this.alignment = Objects.requireNonNull(alignment, "alignment");
            this.header = header;
            this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd;
        }

        public List<DocumentContent.Block> content() {
            return content;
        }
    }

    public static final class Row {
        public final int sourceStart, sourceEnd;
        private final List<Cell> cells;

        public Row(List<Cell> cells, int sourceStart, int sourceEnd) {
            range(sourceStart, sourceEnd);
            this.cells = immutable(cells);
            this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd;
        }

        public List<Cell> cells() {
            return cells;
        }
    }

    private final List<Row> rows;
    private final int columnCount;

    public TableContent(List<Row> rows) {
        this.rows = immutable(rows);
        int columns = 0;
        for (Row row : this.rows) columns = Math.max(columns, row.cells().size());
        columnCount = columns;
    }

    public List<Row> rows() {
        return rows;
    }

    public int columnCount() {
        return columnCount;
    }

    private static void range(int start, int end) {
        if (start < 0 || end < start) throw new IllegalArgumentException("无效表格来源");
    }

    private static <T> List<T> immutable(List<T> values) {
        List<T> copy = new ArrayList<>(Objects.requireNonNull(values, "values"));
        for (T value : copy) Objects.requireNonNull(value, "value");
        return Collections.unmodifiableList(copy);
    }
}
