package club.heiqi.uilib.ui.scene.text.markdown;

import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.InlineContent;
import club.heiqi.uilib.ui.scene.text.ParagraphContent;
import club.heiqi.uilib.ui.scene.text.StyledText;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyleOverride;
import club.heiqi.uilib.ui.scene.text.TextStyleOverrideSpan;
import club.heiqi.uilib.ui.scene.text.font.MathTextFontSources;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.internal.util.Html5Entities;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
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
import org.commonmark.parser.Parser;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                                Collections.singletonList(
                                        StrikethroughExtension.builder()
                                                .requireTwoTildes(true)
                                                .build()))
                        .inlineParserFactory(MathSyntax::inlineParser)
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
                if (isTable(n)) {
                    diagnose(s, e, "管道表格尚未接入，保留原文");
                    out.add(DocumentContent.Block.paragraph(literal(n), ss, se));
                } else out.add(DocumentContent.Block.paragraph(paragraph(n), ss, se));
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
            } else {
                diagnose(s, e, "HTML 内容尚未接入，保留原文");
                out.add(DocumentContent.Block.paragraph(literal(n), ss, se));
            }
        }
        return out;
    }

    private boolean isTable(Node n) {
        List<SourceSpan> spans = n.getSourceSpans();
        if (spans.size() < 2) return false;
        String header = spanText(spans.get(0)), delimiter = spanText(spans.get(1));
        if (!header.contains("|")) return false;
        return delimiter.trim().matches("\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?");
    }

    private int at(SourceSpan span) {
        return lines[span.getLineIndex()] + span.getColumnIndex();
    }

    private String spanText(SourceSpan span) {
        int s = at(span);
        return raw.substring(s, s + span.getLength());
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
        inline(n, b, TextStyleOverride.INHERIT, null);
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
            if (n instanceof Text) b.text(n, style, link, link == null);
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
                                ImageOptions.Fit.CONTAIN));
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
                        new TextContent.Link(((Link) n).getDestination()));
            } else if (n instanceof Emphasis)
                inline(n, b, style.toBuilder().posture(FontStyle.Posture.ITALIC).build(), link);
            else if (n instanceof StrongEmphasis)
                inline(n, b, style.toBuilder().weight(700).build(), link);
            else if (n instanceof Strikethrough)
                inline(n, b, style.toBuilder().strike(true).decorationFromEm(true).build(), link);
            else if (n instanceof HtmlInline) {
                diagnose(start(n), end(n), "HTML 内容尚未接入，保留原文");
                b.verbatim(start(n), end(n), style, link);
            } else inline(n, b, style, link);
        }
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
            while (p < e) {
                int q = p + Character.charCount(raw.codePointAt(p));
                out.add(new Unit(raw.substring(p, q).replace((char) 0, (char) 0xfffd), p, q));
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

        void append(String text, int s, int e, TextStyleOverride style, TextContent.Link link) {
            if (text.isEmpty()) return;
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
