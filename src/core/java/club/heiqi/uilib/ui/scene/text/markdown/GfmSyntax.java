package club.heiqi.uilib.ui.scene.text.markdown;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.internal.TableBlockParser;
import org.commonmark.internal.BlockQuoteParser;
import org.commonmark.internal.BlockStartImpl;
import org.commonmark.internal.FencedCodeBlockParser;
import org.commonmark.internal.HeadingParser;
import org.commonmark.internal.HtmlBlockParser;
import org.commonmark.internal.ListBlockParser;
import org.commonmark.internal.ThematicBreakParser;
import org.commonmark.node.Block;
import org.commonmark.parser.InlineParser;
import org.commonmark.parser.Parser;
import org.commonmark.parser.SourceLine;
import org.commonmark.parser.SourceLines;
import org.commonmark.parser.block.AbstractBlockParser;
import org.commonmark.parser.block.AbstractBlockParserFactory;
import org.commonmark.parser.block.BlockContinue;
import org.commonmark.parser.block.BlockParser;
import org.commonmark.parser.block.BlockParserFactory;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/** 固定 GFM 版本的语法接缝；正式转换与数学配对探查共享同一边界。 */
final class GfmSyntax {
    private GfmSyntax() {}

    /**
     * 固定 CommonMark GFM 0.21 的成熟 factory/split 接缝：该版接受分隔列多于表头； 此处仅校验成熟 split
     * 的数量，不另写表格词法。升级至严格匹配版本后删除反射。
     */
    static Extension extension() {
        return (Parser.ParserExtension)
                builder -> builder.customBlockParserFactory(new StrictTableFactory());
    }

    private static final class StrictTableFactory extends AbstractBlockParserFactory {
        private final TableBlockParser.Factory delegate = new TableBlockParser.Factory();
        private final Method split;
        private final Method separator;

        StrictTableFactory() {
            try {
                split = TableBlockParser.class.getDeclaredMethod("split", SourceLine.class);
                separator =
                        TableBlockParser.class.getDeclaredMethod(
                                "parseSeparator", CharSequence.class);
                split.setAccessible(true);
                separator.setAccessible(true);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("固定 GFM0.21 表格接缝不匹配", failure);
            }
        }

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matched) {
            List<SourceLine> lines = matched.getParagraphLines().getLines();
            if (lines.size() != 1) return BlockStart.none();
            BlockStart start = delegate.tryStart(state, matched);
            if (start == null) return BlockStart.none();
            try {
                List<?> columns =
                        (List<?>)
                                separator.invoke(
                                        null,
                                        state.getLine()
                                                .getContent()
                                                .subSequence(
                                                        state.getIndex(),
                                                        state.getLine().getContent().length()));
                if (columns == null
                        || columns.size() != ((List<?>) split.invoke(null, lines.get(0))).size())
                    return BlockStart.none();
                BlockStartImpl result = (BlockStartImpl) start;
                return BlockStart.of(
                                new TableBoundaryParser(
                                        (TableBlockParser) result.getBlockParsers()[0]))
                        .atIndex(result.getNewIndex())
                        .replaceActiveBlockParser();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("固定 GFM0.21 表格接缝不匹配", failure);
            }
        }
    }

    /**
     * 0.21 的表格继续阶段会吞掉含管道的标题，同时拒绝仅管道的空行。 固定阅读参照允许缺列正文；此接缝仅替换继续判定，块起点仍由成熟 factory 判定， 成熟
     * split/parseCell 仍生成全部行、补空与裁剪。升级匹配参照后删除此包装。
     */
    private static final class TableBoundaryParser extends AbstractBlockParser {
        private static final List<BlockParserFactory> TERMINATORS =
                Arrays.asList(
                        new BlockQuoteParser.Factory(), new HeadingParser.Factory(),
                        new FencedCodeBlockParser.Factory(), new HtmlBlockParser.Factory(),
                        new ThematicBreakParser.Factory(), new ListBlockParser.Factory());
        private final TableBlockParser delegate;

        TableBoundaryParser(TableBlockParser delegate) {
            this.delegate = delegate;
        }

        @Override
        public Block getBlock() {
            return delegate.getBlock();
        }

        @Override
        public void addLine(SourceLine line) {
            delegate.addLine(line);
        }

        @Override
        public void closeBlock() {
            delegate.closeBlock();
        }

        @Override
        public void parseInlines(InlineParser parser) {
            delegate.parseInlines(parser);
        }

        @Override
        public BlockContinue tryContinue(ParserState state) {
            if (state.isBlank()) return BlockContinue.none();
            MatchedBlockParser matched =
                    new MatchedBlockParser() {
                        @Override
                        public BlockParser getMatchedBlockParser() {
                            return TableBoundaryParser.this;
                        }

                        @Override
                        public SourceLines getParagraphLines() {
                            return SourceLines.empty();
                        }
                    };
            for (BlockParserFactory factory : TERMINATORS)
                if (factory.tryStart(state, matched) != null) return BlockContinue.none();
            return BlockContinue.atIndex(state.getIndex());
        }
    }
}
