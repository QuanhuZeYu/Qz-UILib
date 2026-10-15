package club.heiqi.uilib.ui.scene.text.markdown;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.Range;
import org.jsoup.parser.CharacterReader;
import org.jsoup.parser.ParseError;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * jsoup 1.19.1 的公开 trackPosition 接缝；位置是解析 fragment 的 UTF16。 CommonMark
 * 容器剥除的前缀通过字符投影恢复到完整原输入，不用搜索重复正文。 升级解析器须核对 sourceRange/endSourceRange、实体及 CRLF 的真实语料。
 */
final class HtmlSource {
    final String text;
    private final int[] starts, ends;
    final int fallbackStart, fallbackEnd;
    private final ArrayList<ParseError> errors = new ArrayList<>();

    HtmlSource(String text, int[] starts, int[] ends, int fallbackStart, int fallbackEnd) {
        // 与 CommonMark 的 NUL 处理保持同宽；原字符来源仍由投影保留。
        this.text = text.replace((char) 0, (char) 0xfffd);
        this.starts = starts;
        this.ends = ends;
        this.fallbackStart = fallbackStart;
        this.fallbackEnd = fallbackEnd;
    }

    HtmlSource sliceOriginal(int start, int end) {
        // 投影按原文顺序单调排列；保留与范围相交的字符，重复边界不能用任意命中代替。
        int first = boundary(ends, start, true);
        int last = Math.max(first, boundary(starts, end, false));
        int[] slicedStarts = Arrays.copyOfRange(starts, first, last);
        int[] slicedEnds = Arrays.copyOfRange(ends, first, last);
        return new HtmlSource(text.substring(first, last), slicedStarts, slicedEnds, start, end);
    }

    /** 查询第一个大于（exclusive）或大于等于 value 的位置。 */
    private static int boundary(int[] positions, int value, boolean exclusive) {
        int low = 0, high = positions.length;
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (positions[middle] < value || exclusive && positions[middle] == value)
                low = middle + 1;
            else high = middle;
        }
        return low;
    }

    List<Node> parse() {
        Parser parser = Parser.htmlParser().setTrackPosition(true).setTrackErrors(32);
        List<Node> nodes =
                parser.parseFragmentInput(text, new Element(Tag.valueOf("body"), ""), "");
        errors.clear();
        errors.addAll(parser.getErrors());
        return nodes;
    }

    List<ParseError> errors() {
        return errors;
    }

    /**
     * 固定 jsoup1.19.1 没有公开逐实体来源；复用成熟 consumeCharacterReference 及 reader.pos。
     * 不复制实体词法/名称表，也不以解码字符串搜索原文。升级公开来源 token API 后删除反射。 每次 decode 持有自己的
     * parser/reader；接缝变化显式失败，不改变实体解释。
     */
    static final class Decoder implements AutoCloseable {
        private static final Method INITIALISE, REFERENCE;
        private static final Field TOKENISER, READER;

        static {
            try {
                Class<?> treeBuilder = Parser.class.getMethod("getTreeBuilder").getReturnType();
                TOKENISER = treeBuilder.getDeclaredField("tokeniser");
                READER = treeBuilder.getDeclaredField("reader");
                INITIALISE =
                        treeBuilder.getDeclaredMethod(
                                "initialiseParse", Reader.class, String.class, Parser.class);
                REFERENCE =
                        TOKENISER
                                .getType()
                                .getDeclaredMethod(
                                        "consumeCharacterReference",
                                        Character.class,
                                        boolean.class);
                INITIALISE.setAccessible(true);
                REFERENCE.setAccessible(true);
                TOKENISER.setAccessible(true);
                READER.setAccessible(true);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("固定 jsoup1.19.1 实体来源接缝不匹配", failure);
            }
        }

        final CharacterReader reader;
        private final Object tokeniser;

        Decoder(String text) {
            try {
                Parser parser = Parser.htmlParser();
                Object builder = parser.getTreeBuilder();
                INITIALISE.invoke(builder, new StringReader(text), "", parser);
                reader = (CharacterReader) READER.get(builder);
                tokeniser = TOKENISER.get(builder);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("固定 jsoup1.19.1 实体来源接缝不匹配", failure);
            }
        }

        String reference() {
            try {
                int[] points = (int[]) REFERENCE.invoke(tokeniser, null, false);
                if (points == null || points.length == 0) return "&";
                StringBuilder value = new StringBuilder();
                for (int point : points) value.appendCodePoint(point);
                return value.toString();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("固定 jsoup1.19.1 实体来源接缝不匹配", failure);
            }
        }

        @Override
        public void close() {
            reader.close();
        }
    }

    int start(int position) {
        return position >= 0 && position < starts.length
                ? starts[position]
                : position == starts.length ? fallbackEnd : fallbackStart;
    }

    int end(int position) {
        return position > 0 && position <= ends.length ? ends[position - 1] : fallbackStart;
    }

    int start(Node node) {
        Range range = node.sourceRange();
        return range.isTracked() ? start(range.start().pos()) : fallbackStart;
    }

    int end(Node node) {
        if (node instanceof Element) {
            Range closing = ((Element) node).endSourceRange();
            if (closing.isTracked()) return end(closing.end().pos());
        }
        Range range = node.sourceRange();
        int end = range.isTracked() ? end(range.end().pos()) : start(node);
        for (Node child : node.childNodes()) end = Math.max(end, end(child));
        return end;
    }
}
