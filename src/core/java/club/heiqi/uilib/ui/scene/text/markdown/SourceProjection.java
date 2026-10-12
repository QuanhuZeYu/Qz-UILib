package club.heiqi.uilib.ui.scene.text.markdown;

import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;

import java.util.ArrayList;
import java.util.List;

/** 成熟 AST 消耗后的字符视图；仅投影来源，不解释实体或 Markdown 词法。 */
final class SourceProjection {
    final String text;
    private final int[] starts, ends;
    final int fallbackStart, fallbackEnd;

    private SourceProjection(String text, int[] starts, int[] ends, int begin, int finish) {
        this.text = text;
        this.starts = starts;
        this.ends = ends;
        fallbackStart = begin;
        fallbackEnd = finish;
    }

    static SourceProjection of(Node node, String raw, int[] lines, boolean breaks) {
        boolean table = false;
        for (Node parent = node; parent != null; parent = parent.getParent())
            if (parent instanceof TableCell) table = true;
        Builder builder = new Builder(raw, table);
        List<SourceSpan> spans = node.getSourceSpans();
        for (int i = 0; i < spans.size(); i++) {
            SourceSpan span = spans.get(i);
            int begin = lines[span.getLineIndex()] + span.getColumnIndex();
            if (breaks && i > 0) {
                SourceSpan previous = spans.get(i - 1);
                int end =
                        lines[previous.getLineIndex()]
                                + previous.getColumnIndex()
                                + previous.getLength();
                // 换行来自上一 span 后的真实 CRLF；容器前缀不进入虚拟正文。
                builder.range(end, newlineEnd(raw, end));
            }
            builder.range(begin, begin + span.getLength());
        }
        return builder.finish();
    }

    static SourceProjection cell(String raw, int begin, int finish) {
        Builder builder = new Builder(raw, true);
        builder.range(begin, finish);
        return builder.finish();
    }

    static int newlineEnd(String raw, int start) {
        if (start >= raw.length() || raw.charAt(start) != '\r' && raw.charAt(start) != '\n')
            return start;
        int end = start + 1;
        if (raw.charAt(start) == '\r' && end < raw.length() && raw.charAt(end) == '\n') end++;
        return end;
    }

    int start(int index) {
        return index < starts.length ? starts[index] : fallbackEnd;
    }

    int end(int exclusive) {
        return exclusive > 0 ? ends[exclusive - 1] : fallbackStart;
    }

    HtmlSource html(int begin, int finish) {
        return new HtmlSource(text, starts, ends, begin, finish);
    }

    private static final class Builder {
        private final String raw;
        private final boolean table;
        private final StringBuilder text = new StringBuilder();
        private final List<Integer> starts = new ArrayList<>(), ends = new ArrayList<>();
        private int begin = -1, finish;

        Builder(String raw, boolean table) {
            this.raw = raw;
            this.table = table;
        }

        void range(int start, int end) {
            if (begin < 0) begin = start;
            finish = end;
            for (int p = start; p < end; p++) {
                int original = p;
                // GFM split 去掉的反斜杠仍属于这个管道的原来源区间。
                if (table && raw.charAt(p) == '\\' && p + 1 < end && raw.charAt(p + 1) == '|') p++;
                char value = raw.charAt(p);
                text.append(value == 0 ? '\ufffd' : value);
                starts.add(original);
                ends.add(p + 1);
            }
        }

        SourceProjection finish() {
            return new SourceProjection(
                    text.toString(),
                    starts.stream().mapToInt(Integer::intValue).toArray(),
                    ends.stream().mapToInt(Integer::intValue).toArray(),
                    Math.max(0, begin),
                    finish);
        }
    }
}
