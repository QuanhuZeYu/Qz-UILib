package club.heiqi.uilib.ui.scene.text.markdown;

import org.commonmark.internal.InlineParserImpl;
import org.commonmark.internal.inline.InlineContentParser;
import org.commonmark.internal.inline.InlineParserState;
import org.commonmark.internal.inline.ParsedInline;
import org.commonmark.internal.inline.Position;
import org.commonmark.internal.inline.Scanner;
import org.commonmark.internal.util.Parsing;
import org.commonmark.node.Block;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.InlineParser;
import org.commonmark.parser.InlineParserContext;
import org.commonmark.parser.Parser;
import org.commonmark.parser.SourceLine;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 只扩展数学词法；CommonMark 仍决定文档、代码、转义及其它行内语法的优先级。 */
final class MathSyntax {
    static final class MathInline extends CustomNode {
        final String source;
        final boolean display;

        MathInline(String source, boolean display) {
            this.source = source;
            this.display = display;
        }
    }

    static final class MathBlock extends CustomBlock {
        String source;
        boolean closed;
    }

    /**
     * 0.21 是 Java8 可用的固定版本，尚无公开的 InlineContentParser 注册 hook。 此适配只在新 parser 实例上注册触发符，不读取或修改 scanner
     * 的运行状态。 Java 基线允许采用0.23以后版本时，删除反射并使用公开 beta factory 注册。 字段不匹配必须显式失败；不能悄悄退回另一套数学或 Markdown 解释。
     */
    @SuppressWarnings("unchecked")
    static InlineParser inlineParser(InlineParserContext context) {
        InlineParserImpl parser = new InlineParserImpl(context);
        try {
            Field parsers = InlineParserImpl.class.getDeclaredField("inlineParsers");
            Field special = InlineParserImpl.class.getDeclaredField("specialCharacters");
            parsers.setAccessible(true);
            special.setAccessible(true);
            Map<Character, List<InlineContentParser>> map =
                    (Map<Character, List<InlineContentParser>>) parsers.get(parser);
            map.put('$', Collections.singletonList(new DollarParser()));
            ((BitSet) special.get(parser)).set('$');
            return parser;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("固定 CommonMark0.21 数学注册接缝不匹配", failure);
        }
    }

    private static boolean word(int c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
    }

    private static final class DollarParser implements InlineContentParser {
        @Override
        public ParsedInline tryParse(InlineParserState state) {
            Scanner scanner = state.scanner();
            Position start = scanner.position();
            int before = scanner.peekPreviousCodePoint();
            if (before == '$' || before == '\\' || word(before)) return ParsedInline.none();
            scanner.next();
            boolean display = scanner.next('$');
            if (display && scanner.peek() == '$') return ParsedInline.none();
            Position content = scanner.position();
            boolean escaped = false;
            while (scanner.peek() != Scanner.END) {
                char c = scanner.peek();
                if (c == '$' && !escaped) {
                    Position close = scanner.position();
                    scanner.next();
                    if (display && !scanner.next('$')) {
                        escaped = false;
                        continue;
                    }
                    int after = scanner.peekCodePoint();
                    if (after != '$' && !word(after)) {
                        String source = scanner.getSource(content, close).getContent();
                        if (!source.isEmpty())
                            return ParsedInline.of(
                                    new MathInline(source, display), scanner.position());
                    }
                    // 首个未转义闭合候选boundary失败时，原开定界也失败；不能越过$10吃后文。
                    scanner.setPosition(start);
                    return ParsedInline.none();
                }
                escaped = c == '\\' && !escaped;
                if (c != '\\') escaped = false;
                scanner.next();
            }
            scanner.setPosition(start);
            return ParsedInline.none();
        }
    }

    /**
     * 容器匹配交给同版成熟block parser，不用原字符串prefix推测quote/list continuation。
     * 不闭合的探查块可能遮住其后的正常文档结构：排除这些起点并重新识别，直到没有 新排除项。每轮至少排除一个真实source起点，所以由输入长度给出有限上界。
     * 这里只收集完整配对的decoded source offsets，不修复、替换输入或保留探查AST。
     */
    static Map<Integer, Integer> completePairs(String raw, int[] starts) {
        Set<Integer> rejected = new HashSet<>();
        while (true) {
            Scan scan = new Scan();
            Parser.builder()
                    .includeSourceSpans(IncludeSourceSpans.BLOCKS)
                    .extensions(Collections.singletonList(MarkdownAdapter.tableExtension()))
                    .inlineParserFactory(context -> (source, node) -> {})
                    .customBlockParserFactory(new BlockFactory(starts, rejected, scan))
                    .build()
                    .parse(raw);
            if (scan.unclosed.isEmpty()) return scan.closed;
            if (!rejected.addAll(scan.unclosed)) {
                throw new IllegalStateException("数学完整配对识别没有收敛");
            }
        }
    }

    private static final class Scan {
        final Map<Integer, Integer> closed = new HashMap<>();
        final Set<Integer> unclosed = new HashSet<>();
    }

    static final class BlockFactory extends AbstractBlockParserFactory {
        private final int[] starts;
        private final Map<Integer, Integer> complete;
        private final Set<Integer> rejected;
        private final Scan scan;

        BlockFactory(int[] starts, Map<Integer, Integer> complete) {
            this.starts = starts;
            this.complete = complete;
            rejected = Collections.emptySet();
            scan = null;
        }

        private BlockFactory(int[] starts, Set<Integer> rejected, Scan scan) {
            this.starts = starts;
            complete = null;
            this.rejected = rejected;
            this.scan = scan;
        }

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matched) {
            if (state.getIndent() >= Parsing.CODE_BLOCK_INDENT
                    || state.getActiveBlockParser().getBlock() instanceof FencedCodeBlock
                    || state.getActiveBlockParser().getBlock() instanceof IndentedCodeBlock) {
                return BlockStart.none();
            }
            CharSequence line = state.getLine().getContent();
            int p = state.getNextNonSpaceIndex();
            if (p + 1 >= line.length()
                    || line.charAt(p) != '$'
                    || line.charAt(p + 1) != '$'
                    || p + 2 < line.length() && line.charAt(p + 2) == '$') {
                return BlockStart.none();
            }
            SourceSpan span = state.getLine().getSourceSpan();
            if (span == null) return BlockStart.none();
            int opening = starts[span.getLineIndex()] + p;
            if (rejected.contains(opening) || scan == null && !complete.containsKey(opening)) {
                return BlockStart.none();
            }
            String tail = line.subSequence(p + 2, line.length()).toString();
            int close = closing(tail);
            return BlockStart.of(
                            new DollarBlock(
                                    close < 0 ? tail : tail.substring(0, close),
                                    close >= 0,
                                    opening,
                                    scan == null ? complete.get(opening) : -1,
                                    starts,
                                    scan))
                    .atIndex(line.length());
        }
    }

    private static int closing(String text) {
        int e = text.length();
        while (e > 0 && (text.charAt(e - 1) == ' ' || text.charAt(e - 1) == '\t')) e--;
        if (e < 2 || text.charAt(e - 1) != '$' || text.charAt(e - 2) != '$') return -1;
        int p = e - 2;
        if (p > 0 && text.charAt(p - 1) == '$') return -1;
        int slash = p - 1;
        while (slash >= 0 && text.charAt(slash) == '\\') slash--;
        if ((p - 1 - slash) % 2 != 0) return -1;
        return p;
    }

    private static final class DollarBlock extends AbstractBlockParser {
        private final MathBlock block = new MathBlock();
        private final StringBuilder source = new StringBuilder();
        private final boolean single;
        private final int opening, expectedEnd;
        private final int[] starts;
        private final Scan scan;
        private boolean first = true;

        DollarBlock(
                String initial,
                boolean single,
                int opening,
                int expectedEnd,
                int[] starts,
                Scan scan) {
            source.append(initial);
            this.single = single;
            this.opening = opening;
            this.expectedEnd = expectedEnd;
            this.starts = starts;
            this.scan = scan;
            block.closed = single;
        }

        @Override
        public Block getBlock() {
            return block;
        }

        @Override
        public BlockContinue tryContinue(ParserState state) {
            if (single || block.closed) return BlockContinue.none();
            String line =
                    state.getLine()
                            .getContent()
                            .subSequence(state.getIndex(), state.getLine().getContent().length())
                            .toString();
            int close = closing(line);
            if (close >= 0) {
                source.append('\n').append(line.substring(0, close));
                block.closed = true;
                // DocumentParser在finished路径登记关闭行span；容器匹配已由成熟parser完成。
                return BlockContinue.finished();
            }
            // 固定0.21的-1哨兵表示继续且不修改index/column，保留容器消耗到Tab内部的状态。
            // 同坐标atIndex/atColumn都会清除partialTab；剩余空白由成熟addLine统一展开。
            return BlockContinue.atIndex(-1);
        }

        @Override
        public void addLine(SourceLine line) {
            if (first) {
                first = false;
                return;
            }
            source.append('\n').append(line.getContent());
        }

        @Override
        public void closeBlock() {
            block.source = source.toString();
            List<SourceSpan> spans = block.getSourceSpans();
            SourceSpan last = spans.get(spans.size() - 1);
            int end = starts[last.getLineIndex()] + last.getColumnIndex() + last.getLength();
            if (scan != null) {
                if (block.closed) scan.closed.put(opening, end);
                else scan.unclosed.add(opening);
            } else if (!block.closed || end != expectedEnd) {
                throw new IllegalStateException("数学正式parse与成熟配对识别的来源边界不一致");
            }
        }
    }
}
