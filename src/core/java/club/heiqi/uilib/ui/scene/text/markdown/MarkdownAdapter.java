package club.heiqi.uilib.ui.scene.text.markdown;

import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.InlineContent;
import club.heiqi.uilib.ui.scene.text.ParagraphContent;
import club.heiqi.uilib.ui.scene.text.StyledText;
import club.heiqi.uilib.ui.scene.text.TableContent;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyleOverride;
import club.heiqi.uilib.ui.scene.text.TextStyleOverrideSpan;
import club.heiqi.uilib.ui.scene.text.font.MathTextFontSources;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.internal.TableBlockParser;
import org.commonmark.internal.BlockQuoteParser;
import org.commonmark.internal.BlockStartImpl;
import org.commonmark.internal.FencedCodeBlockParser;
import org.commonmark.internal.HeadingParser;
import org.commonmark.internal.HtmlBlockParser;
import org.commonmark.internal.ListBlockParser;
import org.commonmark.internal.ThematicBreakParser;
import org.commonmark.internal.util.Html5Entities;
import org.commonmark.node.Block;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.IncludeSourceSpans;
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
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Range;
import org.jsoup.nodes.TextNode;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 固定 CommonMark 节点到 UILib 声明的转换；不保存库节点或派生几何。 */
public final class MarkdownAdapter {
    private final TextInput input;
    private final String raw;
    private final int[] lines;
    private final List<TextLayout.Diagnostic> diagnostics = new ArrayList<>();
    private static final TextStyleOverride CODE_STYLE =
            TextStyleOverride.builder().font(MathTextFontSources.MONO).build();
    private static final LinkExtractor AUTOLINK =
            LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL, LinkType.EMAIL)).build();

    public MarkdownAdapter(TextInput input) {
        this.input = input;
        raw = input.text;
        for (TextInput.InvalidRange range : input.invalidRanges())
            diagnostics.add(
                    new TextLayout.Diagnostic(
                            TextLayout.Diagnostic.Kind.INVALID_INPUT,
                            range.start,
                            range.end,
                            "原输入含无效编码，保留替代字符及来源"));
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == 0)
                diagnostics.add(
                        new TextLayout.Diagnostic(
                                TextLayout.Diagnostic.Kind.INVALID_INPUT,
                                input.sourceStart(i),
                                input.sourceEnd(i),
                                "Markdown NUL 按 CommonMark 规则显示替代字符"));
            if (raw.charAt(i) == '\r') {
                if (i + 1 < raw.length() && raw.charAt(i + 1) == '\n') i++;
                starts.add(i + 1);
            } else if (raw.charAt(i) == '\n') starts.add(i + 1);
        }
        lines = starts.stream().mapToInt(Integer::intValue).toArray();
    }

    public DocumentContent parse() {
        Map<Integer, Integer> complete = MathSyntax.completePairs(raw, lines);
        Parser parser =
                Parser.builder()
                        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                        .extensions(
                                Arrays.asList(
                                        StrikethroughExtension.builder()
                                                .requireTwoTildes(true)
                                                .build(),
                                        tableExtension()))
                        .inlineParserFactory(
                                context -> tableInlineParser(MathSyntax.inlineParser(context)))
                        .customBlockParserFactory(new MathSyntax.BlockFactory(lines, complete))
                        .build();
        Node document = parser.parse(raw);
        return new DocumentContent(blocks(document), input, diagnostics);
    }

    private List<DocumentContent.Block> blocks(Node parent) {
        List<DocumentContent.Block> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof LinkReferenceDefinition) continue;
            int s = start(n), e = end(n);
            // 代码的末行LF是真实内容；CommonMark的block span本身不含行结束符。
            if (n instanceof FencedCodeBlock || n instanceof IndentedCodeBlock) {
                if (e < raw.length() && raw.charAt(e) == '\r') e++;
                if (e < raw.length() && raw.charAt(e) == '\n') e++;
            }
            int ss = sourceStart(s), se = sourceEnd(e);
            if (n instanceof Paragraph) {
                out.add(DocumentContent.Block.paragraph(paragraph(n), ss, se));
            } else if (n instanceof Heading) {
                out.add(
                        DocumentContent.Block.heading(
                                paragraph(n), ((Heading) n).getLevel(), ss, se));
            } else if (n instanceof BlockQuote)
                out.add(DocumentContent.Block.quote(blocks(n), ss, se));
            else if (n instanceof ListBlock) {
                boolean ordered = n instanceof OrderedList;
                out.add(
                        DocumentContent.Block.list(
                                blocks(n),
                                ordered,
                                ordered ? ((OrderedList) n).getStartNumber() : 1,
                                ((ListBlock) n).isTight(),
                                ss,
                                se));
            } else if (n instanceof ListItem)
                out.add(DocumentContent.Block.listItem(blocks(n), ss, se));
            else if (n instanceof ThematicBreak) out.add(DocumentContent.Block.rule(ss, se));
            else if (n instanceof MathSyntax.MathBlock) {
                MathSyntax.MathBlock b = (MathSyntax.MathBlock) n;
                if (b.closed)
                    out.add(
                            DocumentContent.Block.formula(
                                    MathContent.parse(b.source), MathStyle.DISPLAY, ss, se));
                else out.add(DocumentContent.Block.paragraph(literal(n), ss, se));
            } else if (n instanceof FencedCodeBlock) {
                FencedCodeBlock code = (FencedCodeBlock) n;
                String info = code.getInfo();
                if (info.trim().equalsIgnoreCase("math"))
                    out.add(
                            DocumentContent.Block.formula(
                                    MathContent.parse(code.getLiteral()),
                                    MathStyle.DISPLAY,
                                    ss,
                                    se));
                else out.add(DocumentContent.Block.code(code(n, code.getLiteral()), info, ss, se));
            } else if (n instanceof IndentedCodeBlock) {
                out.add(
                        DocumentContent.Block.code(
                                code(n, ((IndentedCodeBlock) n).getLiteral()), "", ss, se));
            } else if (n instanceof TableBlock) {
                out.add(DocumentContent.Block.table(table(n), ss, se));
            } else if (n instanceof HtmlBlock) {
                out.addAll(htmlBlocks(n));
            } else {
                diagnose(s, e, "无法转换的内容，保留原文");
                out.add(DocumentContent.Block.paragraph(literal(n), ss, se));
            }
        }
        return out;
    }

    /**
     * 固定 CommonMark GFM 0.21 的成熟 factory/split 接缝：该版接受分隔列多于表头； 此处仅校验成熟 split
     * 的数量，不另写表格词法。升级至严格匹配版本后删除反射。
     */
    static Extension tableExtension() {
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

    private TableContent table(Node table) {
        List<TableContent.Row> rows = new ArrayList<>();
        for (Node group = table.getFirstChild(); group != null; group = group.getNext()) {
            for (Node row = group.getFirstChild(); row != null; row = row.getNext()) {
                if (!(row instanceof TableRow)) continue;
                List<TableContent.Cell> cells = new ArrayList<>();
                for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                    TableCell c = (TableCell) cell;
                    int s = c.getSourceSpans().isEmpty() ? end(row) : start(c);
                    int e = c.getSourceSpans().isEmpty() ? s : end(c);
                    TableContent.Alignment alignment =
                            c.getAlignment() == null
                                    ? TableContent.Alignment.DEFAULT
                                    : TableContent.Alignment.valueOf(c.getAlignment().name());
                    cells.add(
                            new TableContent.Cell(
                                    Collections.singletonList(
                                            DocumentContent.Block.paragraph(
                                                    paragraph(c), sourceStart(s), sourceEnd(e))),
                                    alignment,
                                    c.isHeader(),
                                    sourceStart(s),
                                    sourceEnd(e)));
                }
                rows.add(new TableContent.Row(cells, sourceStart(start(row)), sourceEnd(end(row))));
            }
        }
        return new TableContent(rows);
    }

    /** 成熟 split 会去掉转义管道的反斜杠；解析后的 span 按同一单元格字符投影还原。 */
    private InlineParser tableInlineParser(InlineParser delegate) {
        return (source, node) -> {
            if (!(node instanceof TableCell) || node.getSourceSpans().isEmpty()) {
                delegate.parse(source, node);
                return;
            }
            SourceSpan cell = node.getSourceSpans().get(0);
            int begin = at(cell), finish = begin + cell.getLength();
            List<Integer> positions = new ArrayList<>();
            StringBuilder normalized = new StringBuilder();
            for (int p = begin; p < finish; p++) {
                int s = p;
                if (raw.charAt(p) == '\\' && p + 1 < finish && raw.charAt(p + 1) == '|') p++;
                positions.add(s);
                normalized.append(raw.charAt(p));
            }
            int first = 0, last = normalized.length();
            while (first < last
                    && (normalized.charAt(first) == ' ' || normalized.charAt(first) == '\t'))
                first++;
            while (last > first
                    && (normalized.charAt(last - 1) == ' ' || normalized.charAt(last - 1) == '\t'))
                last--;
            // source 的 span 使用已裁剪后的虚拟列，结束位置需映射至原始单元格。
            delegate.parse(source, node);
            int virtual = begin + first;
            remapTableChildren(node, positions, first, last, virtual, finish);
        };
    }

    private void remapTableChildren(
            Node parent, List<Integer> positions, int first, int last, int virtual, int finish) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            List<SourceSpan> mapped = new ArrayList<>();
            for (SourceSpan span : child.getSourceSpans()) {
                int from = at(span) - virtual + first, to = from + span.getLength();
                if (from < first || to > last) throw new IllegalStateException("GFM 行内来源超出单元格");
                int s = from < positions.size() ? positions.get(from) : finish;
                int e = to < positions.size() ? positions.get(to) : finish;
                mapped.add(
                        SourceSpan.of(span.getLineIndex(), s - lines[span.getLineIndex()], e - s));
            }
            child.setSourceSpans(mapped);
            remapTableChildren(child, positions, first, last, virtual, finish);
        }
    }

    private int at(SourceSpan span) {
        return lines[span.getLineIndex()] + span.getColumnIndex();
    }

    private int start(Node n) {
        if (!n.getSourceSpans().isEmpty()) return at(n.getSourceSpans().get(0));
        return n.getFirstChild() == null ? 0 : start(n.getFirstChild());
    }

    private int end(Node n) {
        List<SourceSpan> spans = n.getSourceSpans();
        if (!spans.isEmpty()) {
            SourceSpan s = spans.get(spans.size() - 1);
            return at(s) + s.getLength();
        }
        return n.getLastChild() == null ? start(n) : end(n.getLastChild());
    }

    private int sourceStart(int p) {
        return input.sourceRight(p);
    }

    private int sourceEnd(int p) {
        return input.sourceLeft(p);
    }

    private void diagnose(int start, int end, String message) {
        diagnostics.add(
                new TextLayout.Diagnostic(
                        TextLayout.Diagnostic.Kind.UNSUPPORTED,
                        sourceStart(start),
                        sourceEnd(end),
                        message));
    }

    private TextContent paragraph(Node n) {
        ParagraphBuilder b = new ParagraphBuilder();
        b.contentNode = n;
        inline(n, b, TextStyleOverride.INHERIT, null);
        b.finishHtml(end(n));
        return b.finish();
    }

    private TextContent literal(Node n) {
        ParagraphBuilder b = new ParagraphBuilder();
        List<SourceSpan> spans = n.getSourceSpans();
        for (int i = 0; i < spans.size(); i++) {
            SourceSpan sp = spans.get(i);
            if (i != 0)
                b.newlineBetween(spans.get(i - 1), sp, "\n", TextStyleOverride.INHERIT, null);
            b.verbatim(at(sp), at(sp) + sp.getLength(), TextStyleOverride.INHERIT, null);
        }
        return b.finish();
    }

    private void inline(
            Node parent, ParagraphBuilder b, TextStyleOverride style, TextContent.Link link) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof HtmlInline) {
                htmlToken((HtmlInline) n, b, style, link);
                continue;
            }
            if (b.htmlSuppressed() || b.htmlCodeStart >= 0) {
                inline(n, b, style, link);
                continue;
            }
            if (n instanceof Text) b.text(n, style, link, link == null && b.htmlLink() == null);
            else if (n instanceof Code) b.inlineCode(n, ((Code) n).getLiteral(), style, link);
            else if (n instanceof SoftLineBreak || n instanceof HardLineBreak) {
                int s = start(n), e = end(n);
                if (s == e) {
                    Node prev = n.getPrevious(), next = n.getNext();
                    s = prev == null ? start(parent) : end(prev);
                    e = next == null ? end(parent) : start(next);
                }
                // 源 span 的换行节点在0.21可能无范围，取相邻节点之间的真实行结束符。
                int p = s;
                while (p < e && raw.charAt(p) != '\r' && raw.charAt(p) != '\n') p++;
                if (p < raw.length() && (raw.charAt(p) == '\r' || raw.charAt(p) == '\n')) {
                    int q = p + 1;
                    if (raw.charAt(p) == '\r' && q < raw.length() && raw.charAt(q) == '\n') q++;
                    b.append(n instanceof SoftLineBreak ? " " : "\n", p, q, style, link);
                }
            } else if (n instanceof Image) {
                Image image = (Image) n;
                int offset = b.value.length(), s = start(n), e = end(n);
                b.append("\ufffc", s, e, style, link);
                b.objects.add(
                        InlineContent.image(
                                offset,
                                sourceStart(s),
                                sourceEnd(e),
                                image.getDestination(),
                                alt(n),
                                null,
                                null,
                                ImageOptions.Fit.CONTAIN,
                                title(image.getTitle())));
            } else if (n instanceof MathSyntax.MathInline) {
                int offset = b.value.length(), s = start(n), e = end(n);
                b.append("\ufffc", s, e, style, link);
                b.objects.add(
                        InlineContent.formula(
                                offset,
                                sourceStart(s),
                                sourceEnd(e),
                                MathContent.parse(inlineMathSource((MathSyntax.MathInline) n)),
                                ((MathSyntax.MathInline) n).display
                                        ? MathStyle.DISPLAY
                                        : MathStyle.TEXT));
            } else if (n instanceof Link) {
                inline(
                        n,
                        b,
                        style.toBuilder().underline(true).decorationFromEm(true).build(),
                        new TextContent.Link(
                                ((Link) n).getDestination(), title(((Link) n).getTitle())));
            } else if (n instanceof Emphasis)
                inline(n, b, style.toBuilder().posture(FontStyle.Posture.ITALIC).build(), link);
            else if (n instanceof StrongEmphasis)
                inline(n, b, style.toBuilder().weight(700).build(), link);
            else if (n instanceof Strikethrough)
                inline(n, b, style.toBuilder().strike(true).decorationFromEm(true).build(), link);
            else inline(n, b, style, link);
        }
    }

    // 仅识别 CommonMark 已分类的闭标签 token；开标签及属性交给成熟 HTML parser。
    private static final Pattern HTML_CLOSE =
            Pattern.compile("^</\\s*([A-Za-z][A-Za-z0-9:-]*)\\s*>$");

    private static final class HtmlFrame {
        final String tag;
        final TextContent.Link link;
        final boolean suppressed;

        HtmlFrame(String tag, TextContent.Link link) {
            this.tag = tag;
            this.link = link;
            suppressed = tag.equals("script") || tag.equals("style");
        }
    }

    private static TextStyleOverride htmlStyle(String tag, TextStyleOverride style) {
        switch (tag) {
            case "strong":
            case "b":
                return style.toBuilder().weight(700).build();
            case "em":
            case "i":
                return style.toBuilder().posture(FontStyle.Posture.ITALIC).build();
            case "u":
            case "a":
                return style.toBuilder().underline(true).decorationFromEm(true).build();
            case "s":
            case "del":
                return style.toBuilder().strike(true).decorationFromEm(true).build();
            case "code":
            case "pre":
                return style.toBuilder().font(MathTextFontSources.MONO).build();
            default:
                return style;
        }
    }

    private static boolean htmlBlockTag(String tag) {
        return tag.equals("p")
                || tag.equals("hr")
                || tag.matches("h[1-6]")
                || tag.equals("ul")
                || tag.equals("ol")
                || tag.equals("li")
                || tag.equals("blockquote")
                || tag.equals("pre")
                || tag.equals("table");
    }

    private static boolean htmlContainsBlock(Element element) {
        for (Element child : element.children())
            if (htmlBlockTag(child.normalName()) || htmlContainsBlock(child)) return true;
        return false;
    }

    private static boolean htmlSupported(String tag) {
        return htmlBlockTag(tag)
                || tag.equals("strong")
                || tag.equals("b")
                || tag.equals("em")
                || tag.equals("i")
                || tag.equals("u")
                || tag.equals("s")
                || tag.equals("del")
                || tag.equals("br")
                || tag.equals("code")
                || tag.equals("a")
                || tag.equals("img")
                || tag.equals("thead")
                || tag.equals("tbody")
                || tag.equals("tfoot")
                || tag.equals("tr")
                || tag.equals("th")
                || tag.equals("td");
    }

    private void htmlToken(
            HtmlInline token, ParagraphBuilder b, TextStyleOverride style, TextContent.Link link) {
        int s = start(token), e = end(token);
        String literal = ((HtmlInline) token).getLiteral();
        Matcher closing = HTML_CLOSE.matcher(literal);
        if (closing.matches()) {
            String tag = closing.group(1).toLowerCase(java.util.Locale.ROOT);
            if (b.htmlCodeStart >= 0) {
                if (!tag.equals("code")) return;
                b.finishHtml(s);
            }
            for (int i = b.htmlFrames.size() - 1; i >= 0; i--) {
                if (b.htmlFrames.get(i).tag.equals(tag)) {
                    b.htmlFrames.subList(i, b.htmlFrames.size()).clear();
                    break;
                }
            }
            return;
        }
        if (b.htmlCodeStart >= 0) return;
        HtmlSource source = htmlSource(token);
        List<org.jsoup.nodes.Node> nodes = source.parse();
        Element element = null;
        for (org.jsoup.nodes.Node node : nodes)
            if (node instanceof Element) {
                element = (Element) node;
                break;
            }
        if (element == null) {
            if (!literal.startsWith("<!--")) {
                diagnose(s, e, "无法解释的 HTML 行内内容，保留原文");
                b.verbatim(s, e, style, link);
            }
            return;
        }
        String tag = element.normalName();
        htmlAttributes(element, source);
        if (tag.equals("script") || tag.equals("style")) diagnose(s, e, "HTML 非正文内容不显示");
        else if (!htmlSupported(tag)) diagnose(s, e, "忽略未知 HTML 容器，保留正文");
        if (b.htmlSuppressed()) {
            b.htmlFrames.add(new HtmlFrame(tag, null));
        } else if (tag.equals("br")) b.append("\n", s, e, style, link);
        else if (tag.equals("img")) htmlImage(element, source, b, style, link);
        else {
            TextContent.Link target =
                    tag.equals("a") && element.hasAttr("href")
                            ? new TextContent.Link(element.attr("href"), element.attr("title"))
                            : null;
            b.htmlFrames.add(new HtmlFrame(tag, target));
            if (tag.equals("code")) {
                b.htmlCodeStart = e;
                b.htmlCodeStyle = htmlStyle("code", style);
                b.htmlCodeLink = link;
            }
            if (element.tag().isEmpty() || literal.endsWith("/>"))
                b.htmlFrames.remove(b.htmlFrames.size() - 1);
        }
    }

    private HtmlSource htmlSource(Node node) {
        StringBuilder text = new StringBuilder();
        List<Integer> starts = new ArrayList<>(), ends = new ArrayList<>();
        List<SourceSpan> spans = node.getSourceSpans();
        for (int i = 0; i < spans.size(); i++) {
            SourceSpan span = spans.get(i);
            int s = at(span), e = s + span.getLength();
            for (int p = s; p < e; p++) {
                int originalStart = p;
                if (inTableCell(node)
                        && raw.charAt(p) == '\\'
                        && p + 1 < e
                        && raw.charAt(p + 1) == '|') p++;
                text.append(raw.charAt(p));
                starts.add(originalStart);
                ends.add(p + 1);
            }
            int next = i + 1 < spans.size() ? at(spans.get(i + 1)) : e;
            if (i + 1 < spans.size()
                    && e < next
                    && e < raw.length()
                    && (raw.charAt(e) == '\r' || raw.charAt(e) == '\n')) {
                int q = e + 1;
                if (raw.charAt(e) == '\r' && q < raw.length() && raw.charAt(q) == '\n') q++;
                for (int p = e; p < q; p++) {
                    text.append(raw.charAt(p));
                    starts.add(p);
                    ends.add(p + 1);
                }
            }
        }
        return new HtmlSource(
                text.toString(),
                starts.stream().mapToInt(Integer::intValue).toArray(),
                ends.stream().mapToInt(Integer::intValue).toArray(),
                start(node),
                end(node));
    }

    private void htmlParseDiagnostics(HtmlSource source) {
        for (org.jsoup.parser.ParseError error : source.errors()) {
            int pos = Math.max(0, Math.min(source.text.length(), error.getPosition()));
            int original = source.start(pos);
            diagnose(original, original, "HTML 解析器容错: " + error.getErrorMessage());
        }
    }

    private List<DocumentContent.Block> htmlBlocks(Node node) {
        HtmlSource source = htmlSource(node);
        List<org.jsoup.nodes.Node> nodes = source.parse();
        htmlParseDiagnostics(source);
        return htmlBlocks(nodes, source, TextStyleOverride.INHERIT, null);
    }

    private List<DocumentContent.Block> htmlBlocks(
            List<org.jsoup.nodes.Node> nodes,
            HtmlSource source,
            TextStyleOverride style,
            TextContent.Link link) {
        List<DocumentContent.Block> out = new ArrayList<>();
        ParagraphBuilder paragraph = new ParagraphBuilder();
        int paragraphStart = -1, paragraphEnd = -1;
        for (org.jsoup.nodes.Node node : nodes) {
            if (node instanceof TextNode
                    && ((TextNode) node).isBlank()
                    && paragraph.value.length() == 0) continue;
            if (node instanceof Element && htmlBlockTag(((Element) node).normalName())) {
                if (paragraph.value.length() > 0) {
                    out.add(
                            DocumentContent.Block.paragraph(
                                    paragraph.finish(),
                                    sourceStart(paragraphStart),
                                    sourceEnd(paragraphEnd)));
                    paragraph = new ParagraphBuilder();
                    paragraphStart = -1;
                }
                out.addAll(htmlBlock((Element) node, source, style, link));
            } else if (node instanceof Element
                    && !htmlSupported(((Element) node).normalName())
                    && htmlContainsBlock((Element) node)
                    && !((Element) node).normalName().equals("script")
                    && !((Element) node).normalName().equals("style")) {
                if (paragraph.value.length() > 0) {
                    out.add(
                            DocumentContent.Block.paragraph(
                                    paragraph.finish(),
                                    sourceStart(paragraphStart),
                                    sourceEnd(paragraphEnd)));
                    paragraph = new ParagraphBuilder();
                    paragraphStart = -1;
                }
                Element element = (Element) node;
                htmlAttributes(element, source);
                diagnose(source.start(node), source.end(node), "忽略未知 HTML 容器，保留正文");
                out.addAll(htmlBlocks(element.childNodes(), source, style, link));
            } else {
                if (paragraphStart < 0) paragraphStart = source.start(node);
                paragraphEnd = source.end(node);
                htmlInline(node, source, paragraph, style, link, false);
            }
        }
        if (paragraph.value.length() > 0)
            out.add(
                    DocumentContent.Block.paragraph(
                            paragraph.finish(),
                            sourceStart(paragraphStart),
                            sourceEnd(paragraphEnd)));
        return out;
    }

    private List<DocumentContent.Block> htmlBlock(
            Element element, HtmlSource source, TextStyleOverride style, TextContent.Link link) {
        htmlAttributes(element, source);
        String tag = element.normalName();
        int s = sourceStart(source.start(element)), e = sourceEnd(source.end(element));
        DocumentContent.Block block;
        if (tag.equals("hr")) block = DocumentContent.Block.rule(s, e);
        else if (tag.equals("table"))
            block = DocumentContent.Block.table(htmlTable(element, source, style, link), s, e);
        else if (tag.equals("blockquote"))
            block =
                    DocumentContent.Block.quote(
                            htmlBlocks(element.childNodes(), source, style, link), s, e);
        else if (tag.equals("ul") || tag.equals("ol")) {
            List<DocumentContent.Block> items =
                    htmlBlocks(element.childNodes(), source, style, link);
            block =
                    DocumentContent.Block.list(
                            items,
                            tag.equals("ol"),
                            htmlNumber(element, "start", source, 1, true),
                            false,
                            s,
                            e);
        } else if (tag.equals("li"))
            block =
                    DocumentContent.Block.listItem(
                            htmlBlocks(element.childNodes(), source, style, link), s, e);
        else if (tag.equals("pre")) {
            ParagraphBuilder b = new ParagraphBuilder();
            for (org.jsoup.nodes.Node child : element.childNodes())
                htmlInline(child, source, b, CODE_STYLE, null, true);
            block = DocumentContent.Block.code(b.finish(), "", s, e);
        } else if (tag.equals("p")) {
            ParagraphBuilder b = new ParagraphBuilder();
            for (org.jsoup.nodes.Node child : element.childNodes())
                htmlInline(child, source, b, style, link, false);
            block = DocumentContent.Block.paragraph(b.finish(), s, e);
        } else if (tag.matches("h[1-6]")) {
            ParagraphBuilder b = new ParagraphBuilder();
            for (org.jsoup.nodes.Node child : element.childNodes())
                htmlInline(child, source, b, style, link, false);
            block = DocumentContent.Block.heading(b.finish(), tag.charAt(1) - '0', s, e);
        } else {
            return htmlBlocks(element.childNodes(), source, style, link);
        }
        return Collections.singletonList(block);
    }

    private TableContent htmlTable(
            Element table, HtmlSource source, TextStyleOverride style, TextContent.Link link) {
        List<TableContent.Row> rows = new ArrayList<>();
        htmlRows(table, source, style, link, rows);
        return new TableContent(rows);
    }

    private void htmlRows(
            Element parent,
            HtmlSource source,
            TextStyleOverride style,
            TextContent.Link link,
            List<TableContent.Row> rows) {
        for (Element element : parent.children()) {
            String tag = element.normalName();
            htmlAttributes(element, source);
            if (tag.equals("tr")) {
                List<TableContent.Cell> cells = new ArrayList<>();
                for (Element cell : element.children()) {
                    String name = cell.normalName();
                    if (!name.equals("th") && !name.equals("td")) continue;
                    htmlAttributes(cell, source);
                    cells.add(
                            new TableContent.Cell(
                                    htmlBlocks(cell.childNodes(), source, style, link),
                                    TableContent.Alignment.DEFAULT,
                                    name.equals("th"),
                                    sourceStart(source.start(cell)),
                                    sourceEnd(source.end(cell))));
                }
                rows.add(
                        new TableContent.Row(
                                cells,
                                sourceStart(source.start(element)),
                                sourceEnd(source.end(element))));
            } else if (tag.equals("thead") || tag.equals("tbody") || tag.equals("tfoot"))
                htmlRows(element, source, style, link, rows);
        }
    }

    private void htmlInline(
            org.jsoup.nodes.Node node,
            HtmlSource source,
            ParagraphBuilder b,
            TextStyleOverride style,
            TextContent.Link link,
            boolean code) {
        if (node instanceof TextNode) {
            Range range = node.sourceRange();
            if (!range.isTracked()) {
                diagnose(source.start(node), source.end(node), "HTML 文本缺少解析器来源");
                return;
            }
            htmlDecoded(
                    source,
                    range.start().pos(),
                    range.end().pos(),
                    b,
                    style,
                    link,
                    code,
                    ((TextNode) node).getWholeText());
        } else if (node instanceof Element) {
            Element element = (Element) node;
            String tag = element.normalName();
            htmlAttributes(element, source);
            if (tag.equals("script") || tag.equals("style")) {
                diagnose(source.start(node), source.end(node), "HTML 非正文内容不显示");
                return;
            }
            if (!htmlSupported(tag))
                diagnose(source.start(node), source.end(node), "忽略未知 HTML 容器，保留正文");
            if (tag.equals("br")) b.append("\n", source.start(node), source.end(node), style, link);
            else if (tag.equals("img")) htmlImage(element, source, b, style, link);
            else {
                style = htmlStyle(tag, style);
                if (tag.equals("a") && element.hasAttr("href"))
                    link = new TextContent.Link(element.attr("href"), element.attr("title"));
                for (org.jsoup.nodes.Node child : element.childNodes())
                    htmlInline(child, source, b, style, link, code || tag.equals("code"));
            }
        }
    }

    /** 实体含未终止形式，解码由 jsoup 决定；共享原实体范围，CRLF 与代理对不切断。 */
    private void htmlDecoded(
            HtmlSource source,
            int begin,
            int finish,
            ParagraphBuilder b,
            TextStyleOverride style,
            TextContent.Link link,
            boolean code,
            String expected) {
        StringBuilder decoded = new StringBuilder();
        try (HtmlSource.Decoder decoder =
                new HtmlSource.Decoder(source.text.substring(begin, finish))) {
            while (!decoder.reader.isEmpty()) {
                int p = begin + decoder.reader.pos();
                int point = source.text.codePointAt(p);
                decoder.reader.advance();
                String value;
                if (point == '&') value = decoder.reference();
                else {
                    if (Character.charCount(point) == 2) decoder.reader.advance();
                    value = new String(Character.toChars(point));
                    if (point == '\r') {
                        if (!decoder.reader.isEmpty() && decoder.reader.current() == '\n')
                            decoder.reader.advance();
                        value = "\n";
                    } else if (point == 0) value = "\ufffd";
                }
                int q = begin + decoder.reader.pos();
                decoded.append(point == '\r' ? source.text.substring(p, q) : value);
                if (point == '&') {
                    StringBuilder valid = new StringBuilder();
                    boolean invalid = false;
                    for (int i = 0; i < value.length(); ) {
                        int cp = value.codePointAt(i);
                        if (cp == 0 || cp >= 0xd800 && cp <= 0xdfff) {
                            valid.append('\ufffd');
                            invalid = true;
                        } else valid.appendCodePoint(cp);
                        i += Character.charCount(cp);
                    }
                    if (invalid) {
                        diagnostics.add(
                                new TextLayout.Diagnostic(
                                        TextLayout.Diagnostic.Kind.INVALID_INPUT,
                                        sourceStart(source.start(p)),
                                        sourceEnd(source.end(q)),
                                        "HTML 实体含无效 Unicode，保留替代字符及实体来源"));
                        value = valid.toString();
                    }
                }
                if (!code && value.matches("[\\t\\n\\r ]+")) {
                    while (!decoder.reader.isEmpty()
                            && "\t\n\r ".indexOf(decoder.reader.current()) >= 0) {
                        decoded.append(decoder.reader.current());
                        decoder.reader.advance();
                    }
                    q = begin + decoder.reader.pos();
                    value = " ";
                    if (b.value.length() > 0 && b.value.charAt(b.value.length() - 1) == ' ')
                        continue;
                }
                b.append(value, source.start(p), source.end(q), style, link);
            }
        }
        if (expected != null && !decoded.toString().equals(expected))
            throw new IllegalStateException("固定 jsoup TextNode 来源解码不一致: " + begin + ".." + finish);
    }

    private void htmlImage(
            Element element,
            HtmlSource source,
            ParagraphBuilder b,
            TextStyleOverride style,
            TextContent.Link link) {
        int s = source.start(element), e = source.end(element), offset = b.value.length();
        b.append("\ufffc", s, e, style, link);
        Integer width = htmlDimension(element, "width", source),
                height = htmlDimension(element, "height", source);
        b.objects.add(
                InlineContent.image(
                        offset,
                        sourceStart(s),
                        sourceEnd(e),
                        element.attr("src"),
                        element.attr("alt"),
                        width,
                        height,
                        ImageOptions.Fit.CONTAIN,
                        element.attr("title")));
    }

    private Integer htmlDimension(Element element, String name, HtmlSource source) {
        if (!element.hasAttr(name)) return null;
        int value = htmlNumber(element, name, source, -1, false);
        return value < 0 ? null : value;
    }

    private int htmlNumber(
            Element element, String name, HtmlSource source, int fallback, boolean signed) {
        if (!element.hasAttr(name)) return fallback;
        String text = element.attr(name).trim();
        try {
            if (!text.matches(signed ? "[+-]?[0-9]+" : "[0-9]+")) throw new NumberFormatException();
            int number = Integer.parseInt(text);
            if (!signed && number < 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException invalid) {
            htmlAttributeDiagnostic(element, name, source, "无效 HTML 属性 " + name + "，按未声明处理");
            return fallback;
        }
    }

    private void htmlAttributes(Element element, HtmlSource source) {
        String tag = element.normalName();
        for (Attribute attribute : element.attributes()) {
            String name = attribute.getKey();
            boolean allowed =
                    tag.equals("a") && (name.equals("href") || name.equals("title"))
                            || tag.equals("img")
                                    && (name.equals("src")
                                            || name.equals("alt")
                                            || name.equals("title")
                                            || name.equals("width")
                                            || name.equals("height"))
                            || tag.equals("ol") && name.equals("start");
            if (!allowed) htmlAttributeDiagnostic(element, name, source, "忽略未支持的 HTML 属性 " + name);
        }
    }

    private void htmlAttributeDiagnostic(
            Element element, String name, HtmlSource source, String message) {
        Attribute attribute = element.attributes().attribute(name);
        Range range =
                attribute == null ? element.sourceRange() : attribute.sourceRange().valueRange();
        if (!range.isTracked() && attribute != null) range = attribute.sourceRange().nameRange();
        diagnose(
                range.isTracked() ? source.start(range.start().pos()) : source.start(element),
                range.isTracked() ? source.end(range.end().pos()) : source.end(element),
                message);
    }

    /**
     * 固定插件1.1.2 inlineRenderer只剥一对紧邻首尾的backtick，不trim、不递归处理。
     * DISPLAY走blockRenderer，math围栏及公式块也保持原source；原TextInput和placeholder范围不变。
     */
    private static String inlineMathSource(MathSyntax.MathInline math) {
        String source = math.source;
        return !math.display
                        && source.length() > 2
                        && source.charAt(0) == '`'
                        && source.charAt(source.length() - 1) == '`'
                ? source.substring(1, source.length() - 1)
                : source;
    }

    private static String title(String value) {
        return value == null ? "" : value;
    }

    private String alt(Node n) {
        StringBuilder s = new StringBuilder();
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof Text) s.append(((Text) c).getLiteral());
            else if (c instanceof Code) s.append(((Code) c).getLiteral());
            else if (c instanceof SoftLineBreak || c instanceof HardLineBreak) s.append(' ');
            else s.append(alt(c));
        }
        return s.toString();
    }

    private int lineEnd(int line) {
        int e = line + 1 < lines.length ? lines[line + 1] : raw.length();
        if (e > lines[line] && raw.charAt(e - 1) == '\n') e--;
        if (e > lines[line] && raw.charAt(e - 1) == '\r') e--;
        return e;
    }

    private TextContent code(Node n, String literal) {
        ParagraphBuilder b = new ParagraphBuilder();
        List<SourceSpan> spans = n.getSourceSpans();
        if (spans.isEmpty()) return b.finish();
        int first = spans.get(0).getLineIndex() + (n instanceof FencedCodeBlock ? 1 : 0);
        Map<Integer, SourceSpan> byLine = new HashMap<>();
        for (SourceSpan sp : spans) byLine.put(sp.getLineIndex(), sp);
        String[] output = literal.split("\n", -1);
        for (int row = 0; row < output.length - 1; row++) {
            int lineIndex = first + row;
            int e = lineEnd(lineIndex);
            SourceSpan sp = byLine.get(lineIndex);
            int pos = sp == null ? e : at(sp);
            String source = raw.substring(pos, e).replace((char) 0, (char) 0xfffd),
                    target = output[row];
            // sourceSpans可省略空行；line index与literal行序共同确定来源，不find重复字面串。
            int suffix = 0;
            while (suffix < source.length()
                    && suffix < target.length()
                    && source.charAt(source.length() - suffix - 1)
                            == target.charAt(target.length() - suffix - 1)) suffix++;
            int consumed = source.length() - suffix, expanded = target.length() - suffix;
            if (expanded > 0) {
                int tab = consumed - 1;
                if (tab < 0
                        || source.charAt(tab) != '\t'
                        || !target.substring(0, expanded).matches(" +"))
                    throw new IllegalStateException("CommonMark code缩进来源不一致");
                b.append(target.substring(0, expanded), pos + tab, pos + tab + 1, CODE_STYLE, null);
            }
            b.verbatim(pos + consumed, e, CODE_STYLE, null);
            if (e < raw.length() && (raw.charAt(e) == '\n' || raw.charAt(e) == '\r')) {
                int q = e + 1;
                if (raw.charAt(e) == '\r' && q < raw.length() && raw.charAt(q) == '\n') q++;
                b.append("\n", e, q, CODE_STYLE, null);
            }
            // parser合成的EOF终换行不伪造来源；代码可见原空白保持输入中实际行结束符。
        }
        return b.finish();
    }

    private static final class Unit {
        final String text;
        final int start, end;

        Unit(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    private static boolean inTableCell(Node n) {
        for (Node p = n; p != null; p = p.getParent()) if (p instanceof TableCell) return true;
        return false;
    }

    private List<Unit> rawUnits(Node n, boolean breaks) {
        List<Unit> out = new ArrayList<>();
        List<SourceSpan> spans = n.getSourceSpans();
        for (int k = 0; k < spans.size(); k++) {
            SourceSpan sp = spans.get(k);
            if (breaks && k > 0) {
                SourceSpan prev = spans.get(k - 1);
                int p = at(prev) + prev.getLength();
                if (p < raw.length() && (raw.charAt(p) == '\r' || raw.charAt(p) == '\n')) {
                    int e = p + 1;
                    if (raw.charAt(p) == '\r' && e < raw.length() && raw.charAt(e) == '\n') e++;
                    out.add(new Unit(" ", p, e));
                }
            }
            int p = at(sp), e = p + sp.getLength();
            boolean table = inTableCell(n);
            while (p < e) {
                int s = p;
                if (table && raw.charAt(p) == '\\' && p + 1 < e && raw.charAt(p + 1) == '|') p++;
                int q = p + Character.charCount(raw.codePointAt(p));
                out.add(new Unit(raw.substring(p, q).replace((char) 0, (char) 0xfffd), s, q));
                p = q;
            }
        }
        return out;
    }

    private final class ParagraphBuilder {
        final StringBuilder value = new StringBuilder();
        final List<Integer> starts = new ArrayList<>(), ends = new ArrayList<>();
        final List<TextStyleOverrideSpan> styles = new ArrayList<>();
        final List<TextContent.LinkSpan> links = new ArrayList<>();
        final List<InlineContent> objects = new ArrayList<>();
        final List<HtmlFrame> htmlFrames = new ArrayList<>();
        Node contentNode;
        HtmlSource contentSource;
        int htmlCodeStart = -1;
        TextStyleOverride htmlCodeStyle;
        TextContent.Link htmlCodeLink;

        boolean htmlSuppressed() {
            for (HtmlFrame frame : htmlFrames) if (frame.suppressed) return true;
            return false;
        }

        TextContent.Link htmlLink() {
            for (int i = htmlFrames.size() - 1; i >= 0; i--)
                if (htmlFrames.get(i).link != null) return htmlFrames.get(i).link;
            return null;
        }

        void finishHtml(int end) {
            if (htmlCodeStart >= 0) {
                if (contentSource == null) contentSource = htmlSource(contentNode);
                HtmlSource source = contentSource.sliceOriginal(htmlCodeStart, end);
                List<org.jsoup.nodes.Node> nodes = source.parse();
                htmlParseDiagnostics(source);
                for (org.jsoup.nodes.Node node : nodes)
                    htmlInline(node, source, this, htmlCodeStyle, htmlCodeLink, true);
                htmlCodeStart = -1;
            }
        }

        void append(String text, int s, int e, TextStyleOverride style, TextContent.Link link) {
            if (text.isEmpty() || htmlSuppressed()) return;
            for (HtmlFrame frame : htmlFrames) style = htmlStyle(frame.tag, style);
            if (link == null) link = htmlLink();
            if (link != null)
                style = style.toBuilder().underline(true).decorationFromEm(true).build();
            int p = value.length();
            value.append(text);
            for (int i = 0; i < text.length(); i++) {
                starts.add(s);
                ends.add(e);
            }
            styles.add(new TextStyleOverrideSpan(p, value.length(), style));
            if (link != null) {
                int last = links.size() - 1;
                if (last >= 0 && links.get(last).end == p && links.get(last).link == link) {
                    TextContent.LinkSpan old = links.remove(last);
                    links.add(new TextContent.LinkSpan(old.start, value.length(), link));
                } else links.add(new TextContent.LinkSpan(p, value.length(), link));
            }
        }

        void verbatim(int s, int e, TextStyleOverride style, TextContent.Link link) {
            for (int p = s; p < e; ) {
                int q = p + Character.charCount(raw.codePointAt(p));
                append(raw.substring(p, q).replace((char) 0, (char) 0xfffd), p, q, style, link);
                p = q;
            }
        }

        void newlineBetween(
                SourceSpan prev,
                SourceSpan next,
                String text,
                TextStyleOverride style,
                TextContent.Link link) {
            int p = at(prev) + prev.getLength();
            if (p < raw.length() && (raw.charAt(p) == '\r' || raw.charAt(p) == '\n')) {
                int e = p + 1;
                if (raw.charAt(p) == '\r' && e < raw.length() && raw.charAt(e) == '\n') e++;
                append(text, p, e, style, link);
            }
        }

        void text(Node n, TextStyleOverride style, TextContent.Link link, boolean auto) {
            List<Unit> units = rawUnits(n, false), decoded = new ArrayList<>();
            for (int i = 0; i < units.size(); ) {
                Unit u = units.get(i);
                if (u.text.equals(String.valueOf((char) 0))) {
                    decoded.add(new Unit("\ufffd", u.start, u.end));
                    i++;
                } else if (u.text.equals("\\")
                        && i + 1 < units.size()
                        && units.get(i + 1).text.length() == 1
                        && units.get(i + 1).text.charAt(0) >= 33
                        && units.get(i + 1).text.charAt(0) <= 126
                        && !Character.isLetterOrDigit(units.get(i + 1).text.charAt(0))) {
                    Unit next = units.get(i + 1);
                    decoded.add(new Unit(next.text, u.start, next.end));
                    i += 2;
                } else if (u.text.equals("&")) {
                    int j = i + 1;
                    StringBuilder entity = new StringBuilder("&");
                    while (j < units.size()
                            && j - i < 50
                            && units.get(j).start == units.get(j - 1).end) {
                        entity.append(units.get(j).text);
                        if (units.get(j).text.equals(";")) break;
                        j++;
                    }
                    String named = entity.toString();
                    String expanded =
                            named.matches(
                                            "&(?:#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]*);")
                                    ? Html5Entities.entityToString(named)
                                    : named;
                    if (!expanded.equals(named)) {
                        decoded.add(new Unit(expanded, u.start, units.get(j).end));
                        i = j + 1;
                    } else {
                        decoded.add(u);
                        i++;
                    }
                } else {
                    decoded.add(u);
                    i++;
                }
            }
            String expected = ((Text) n).getLiteral();
            int decodedLength = 0;
            for (Unit u : decoded) decodedLength += u.text.length();
            // CommonMark Text span保留被行末规则去除的空白，literal只发布可见内容。
            while (decodedLength > expected.length() && !decoded.isEmpty()) {
                Unit last = decoded.get(decoded.size() - 1);
                if (!last.text.equals(" ") && !last.text.equals("\t")) break;
                decoded.remove(decoded.size() - 1);
                decodedLength -= last.text.length();
            }
            StringBuilder s = new StringBuilder();
            for (Unit u : decoded) s.append(u.text);
            // 成熟 parser 的 literal 是语义事实；源词法只重建对应范围，绝不定位搜索重复文本。
            if (!s.toString().equals(((Text) n).getLiteral()))
                throw new IllegalStateException("CommonMark Text 来源解码不一致: " + n.getSourceSpans());
            List<LinkSpan> autoLinks = new ArrayList<>();
            if (auto) for (LinkSpan a : AUTOLINK.extractLinks(s)) autoLinks.add(a);
            int logical = 0, ai = 0;
            TextContent.Link active = null;
            for (Unit u : decoded) {
                while (ai < autoLinks.size() && logical >= autoLinks.get(ai).getEndIndex()) {
                    ai++;
                    active = null;
                }
                TextContent.Link current = link;
                if (ai < autoLinks.size()) {
                    LinkSpan a = autoLinks.get(ai);
                    if (logical >= a.getBeginIndex() && logical < a.getEndIndex()) {
                        if (active == null) {
                            String target = s.substring(a.getBeginIndex(), a.getEndIndex());
                            active =
                                    new TextContent.Link(
                                            a.getType() == LinkType.EMAIL
                                                    ? "mailto:" + target
                                                    : target);
                        }
                        current = active;
                    }
                }
                TextStyleOverride resolved =
                        current == null
                                ? style
                                : style.toBuilder().underline(true).decorationFromEm(true).build();
                append(u.text, u.start, u.end, resolved, current);
                logical += u.text.length();
            }
        }

        void inlineCode(Node n, String literal, TextStyleOverride style, TextContent.Link link) {
            style = style.toBuilder().font(MathTextFontSources.MONO).build();
            List<Unit> units = rawUnits(n, true);
            int begin = 0, end = units.size();
            while (begin < end && units.get(begin).text.equals("`")) begin++;
            while (end > begin && units.get(end - 1).text.equals("`")) end--;
            if (end - begin >= 3
                    && units.get(begin).text.equals(" ")
                    && units.get(end - 1).text.equals(" ")) {
                boolean nonspace = false;
                for (int i = begin; i < end; i++)
                    if (!units.get(i).text.equals(" ")) nonspace = true;
                if (nonspace) {
                    begin++;
                    end--;
                }
            }
            StringBuilder decoded = new StringBuilder();
            for (int i = begin; i < end; i++) decoded.append(units.get(i).text);
            if (!decoded.toString().equals(literal))
                throw new IllegalStateException("CommonMark Code 来源解码不一致");
            for (int i = begin; i < end; i++) {
                Unit u = units.get(i);
                append(u.text, u.start, u.end, style, link);
            }
        }

        TextContent finish() {
            int[] ss = starts.stream().mapToInt(Integer::intValue).toArray(),
                    es = ends.stream().mapToInt(Integer::intValue).toArray();
            List<TextInput.SourceSegment> gaps = new ArrayList<>();
            int p = 0;
            for (int i = 0; i < ss.length; ) {
                int next = i + Character.charCount(value.codePointAt(i));
                if (ss[i] > p) gaps.add(new TextInput.SourceSegment(i, i, p, ss[i], true));
                p = es[i];
                i = next;
            }
            if (p < raw.length())
                gaps.add(
                        new TextInput.SourceSegment(
                                value.length(), value.length(), p, raw.length(), true));
            TextInput mapped = TextInput.fromMapped(input, value.toString(), ss, es, gaps);
            StyledText styled =
                    new StyledText(
                            mapped,
                            raw,
                            styles,
                            TextStyleOverride.INHERIT,
                            Collections.<TextLayout.Diagnostic>emptyList());
            return TextContent.paragraph(new ParagraphContent(styled, links, objects));
        }
    }
}
