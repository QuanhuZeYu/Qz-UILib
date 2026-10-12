package club.heiqi.uilib.ui.scene.text.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 源码只解析一次；内部 AST 不承诺为长期公开接口，布局与主题均不修改它。 */
public final class MathContent {
    public final String source;
    public final List<MathLayout.Diagnostic> diagnostics;
    public final List<MathLexer.Token> expandedTokens, tagTokens;
    public final MathMacros nextMacros;
    public final List<MathMacroExpander.RowTag> rowTags;
    public final List<MathMacroExpander.EnvironmentHeader> environmentHeaders;
    public final List<MathMacroExpander.Message> macroMessages;
    final Node root;

    private MathContent(
            String source,
            Node root,
            List<MathLayout.Diagnostic> diagnostics,
            MathMacroExpander.Result expansion) {
        this.source = source;
        this.root = root;
        this.expandedTokens = expansion.tokens;
        this.tagTokens = expansion.tagTokens;
        this.nextMacros = expansion.nextMacros;
        this.rowTags = expansion.rowTags;
        this.environmentHeaders = expansion.environmentHeaders;
        this.macroMessages = expansion.messages;
        this.diagnostics =
                Collections.unmodifiableList(new ArrayList<MathLayout.Diagnostic>(diagnostics));
    }

    public static MathContent parse(String source) {
        return parse(source, MathMacros.empty());
    }

    public static MathContent parse(String source, MathMacros macros) {
        Objects.requireNonNull(source, "source");
        MathMacroExpander.Result expansion = MathMacroExpander.expand(source, macros);
        if (!expansion.diagnostics.isEmpty()) {
            List<MathLayout.Diagnostic> diagnostics = new ArrayList<MathLayout.Diagnostic>();
            for (MathMacroExpander.Diagnostic d : expansion.diagnostics)
                diagnostics.add(
                        new MathLayout.Diagnostic("macro", d.sourceStart, d.sourceEnd, d.message));
            return new MathContent(source, null, diagnostics, expansion);
        }
        MathParser parser =
                new MathParser(
                        expansion.tokens,
                        source.length(),
                        expansion.rowTags,
                        expansion.environmentHeaders);
        try {
            Node root = parser.parse(expansion.tagTokens);
            return new MathContent(
                    source, root, Collections.<MathLayout.Diagnostic>emptyList(), expansion);
        } catch (MathParser.Failure failure) {
            MathLayout.Diagnostic d = failure.diagnostic;
            int[] span = new int[] {d.start, d.end};
            return new MathContent(
                    source,
                    null,
                    Collections.singletonList(
                            new MathLayout.Diagnostic(d.stage, span[0], span[1], d.message)),
                    expansion);
        }
    }

    /** 字符 parser 的私有桥，不向业务发布展开索引；后续 Node payload 复制也由此统一出口。 */
    static final class ParserBridge {
        final String text;
        final int[] starts, ends;
        final int sourceLength;

        ParserBridge(List<MathLexer.Token> tokens, int sourceLength) {
            this.sourceLength = sourceLength;
            StringBuilder value = new StringBuilder();
            List<Integer> ss = new ArrayList<Integer>(), es = new ArrayList<Integer>();
            for (int i = 0; i < tokens.size(); i++) {
                MathLexer.Token token = tokens.get(i);
                String tokenText = token.text;
                for (int n = 0; n < tokenText.length(); n++) {
                    value.append(tokenText.charAt(n));
                    ss.add(token.sourceStart);
                    es.add(token.sourceEnd);
                }
                // 控制词和随后字母仍是两个 token，绝不能合成另一个控制词。
                if (tokenText.matches("\\\\[A-Za-z@]+")
                        && i + 1 < tokens.size()
                        && !tokens.get(i + 1).text.isEmpty()
                        && MathLexer.letter(tokens.get(i + 1).text.charAt(0))) {
                    value.append(' ');
                    ss.add(token.sourceStart);
                    es.add(token.sourceEnd);
                }
            }
            text = value.toString();
            starts = new int[ss.size()];
            ends = new int[es.size()];
            for (int i = 0; i < starts.length; i++) {
                starts[i] = ss.get(i);
                ends[i] = es.get(i);
            }
        }

        int[] span(int start, int end) {
            if (starts.length == 0) return new int[] {sourceLength, sourceLength};
            int s = Math.max(0, Math.min(start, starts.length));
            int e = Math.max(s, Math.min(end, starts.length));
            if (s == e) {
                int p = s == starts.length ? ends[s - 1] : starts[s];
                return new int[] {p, p};
            }
            int lo = starts[s], hi = ends[s];
            for (int i = s + 1; i < e; i++) {
                lo = Math.min(lo, starts[i]);
                hi = Math.max(hi, ends[i]);
            }
            return new int[] {lo, hi};
        }

        Node remap(Node node) {
            if (node == null) return null;
            List<Node> children = new ArrayList<Node>();
            for (Node child : node.children) children.add(remap(child));
            int[] range = span(node.start, node.end);
            return new Node(
                    node.kind,
                    range[0],
                    range[1],
                    node.value,
                    children,
                    remap(node.first),
                    remap(node.second),
                    remap(node.third),
                    node.payload);
        }
    }

    enum Kind {
        ROW,
        GLYPH,
        TEXT,
        EMBED,
        FRAC,
        BINOM,
        GENFRAC,
        DELIMITED,
        DELIMITER,
        MIDDLE,
        ROOT,
        SCRIPTS,
        FONT,
        STYLE,
        SPACE,
        OPERATOR,
        ARRAY,
        ACCENT,
        OVERUNDER,
        XARROW,
        HORIZBRACE,
        COLOR,
        SIZE,
        CLASSIFY,
        PHANTOM,
        BOX,
        RAISE,
        RULE,
        MATHCHOICE,
        VCENTER,
        LAP,
        SMASH,
        ENCLOSE,
        PMB,
        HTMLMATHML,
        VERB,
        UNSUPPORTED,
        TAG,
        LINEBREAK,
        INTERNAL
    }

    /** 参数事实独立于树；所有子内容仅在 Node 的 children/first/second/third。 */
    interface Payload {}

    static final class Node {
        final Kind kind;
        final int start, end;
        final String value;
        final List<Node> children;
        final Node first, second, third;
        final Payload payload;

        Node(
                Kind kind,
                int start,
                int end,
                String value,
                List<Node> children,
                Node first,
                Node second,
                Node third) {
            this(kind, start, end, value, children, first, second, third, null);
        }

        Node(
                Kind kind,
                int start,
                int end,
                String value,
                List<Node> children,
                Node first,
                Node second,
                Node third,
                Payload payload) {
            this.kind = kind;
            this.start = start;
            this.end = end;
            this.value = value;
            this.children = immutable(children == null ? Collections.<Node>emptyList() : children);
            this.first = first;
            this.second = second;
            this.third = third;
            this.payload = payload;
        }
    }

    static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    static final class GlyphSpec implements Payload {
        final String originalToken, mode, atomGroup, catalogFont, replacement;
        final String styledFont;

        GlyphSpec(
                String token,
                String mode,
                String group,
                String font,
                String replacement,
                String styledFont) {
            this.originalToken = token;
            this.mode = mode;
            this.atomGroup = group;
            this.catalogFont = font;
            this.replacement = replacement;
            this.styledFont = styledFont;
        }
    }

    static final class FractionSpec implements Payload {
        final String leftDelimiter, rightDelimiter, style, align;
        final boolean hasBar, continued;
        final MathDimension thickness;

        FractionSpec(
                String left,
                String right,
                String style,
                String align,
                boolean bar,
                boolean continued,
                MathDimension thickness) {
            this.leftDelimiter = left;
            this.rightDelimiter = right;
            this.style = style;
            this.align = align;
            this.hasBar = bar;
            this.continued = continued;
            this.thickness = thickness;
        }
    }

    static final class DelimiterSpec implements Payload {
        final String originalToken, atomGroup;
        final int size;

        DelimiterSpec(String token, String group, int size) {
            this.originalToken = token;
            this.atomGroup = group;
            this.size = size;
        }
    }

    static final class OperatorSpec implements Payload {
        final String originalToken, limits;
        final boolean symbol, defaultLimits, alwaysHandleSupSub;
        final GlyphSpec symbolFacts;

        OperatorSpec(
                String token,
                String limits,
                boolean symbol,
                boolean defaultLimits,
                boolean always) {
            this(token, limits, symbol, defaultLimits, always, null);
        }

        OperatorSpec(
                String token,
                String limits,
                boolean symbol,
                boolean defaultLimits,
                boolean always,
                GlyphSpec symbolFacts) {
            this.originalToken = token;
            this.limits = limits;
            this.symbol = symbol;
            this.defaultLimits = defaultLimits;
            this.alwaysHandleSupSub = always;
            this.symbolFacts = symbolFacts;
        }
    }

    static final class FontSpec implements Payload {
        final String font, mode;

        FontSpec(String font, String mode) {
            this.font = font;
            this.mode = mode;
        }
    }

    static final class StyleSpec implements Payload {
        final String style;

        StyleSpec(String style) {
            this.style = style;
        }
    }

    static final class SizeSpec implements Payload {
        final int size;

        SizeSpec(int size) {
            this.size = size;
        }
    }

    static final class ColorSpec implements Payload {
        final String css;

        ColorSpec(String css) {
            this.css = css;
        }
    }

    static final class ClassSpec implements Payload {
        final String atomGroup;
        final boolean characterBox;

        ClassSpec(String group, boolean characterBox) {
            this.atomGroup = group;
            this.characterBox = characterBox;
        }
    }

    static final class SpaceSpec implements Payload {
        final MathDimension dimension;
        final boolean nonBreaking, allowBreak;
        final GlyphSpec symbolFacts;

        SpaceSpec(MathDimension dimension, boolean nonBreaking, boolean allowBreak) {
            this(dimension, nonBreaking, allowBreak, null);
        }

        SpaceSpec(
                MathDimension dimension,
                boolean nonBreaking,
                boolean allowBreak,
                GlyphSpec symbolFacts) {
            this.dimension = dimension;
            this.nonBreaking = nonBreaking;
            this.allowBreak = allowBreak;
            this.symbolFacts = symbolFacts;
        }
    }

    static final class DecorationSpec implements Payload {
        final String command;
        final boolean below, stretchy, shifty;

        DecorationSpec(String command, boolean below, boolean stretchy, boolean shifty) {
            this.command = command;
            this.below = below;
            this.stretchy = stretchy;
            this.shifty = shifty;
        }
    }

    static final class BoxSpec implements Payload {
        final MathDimension width, height, shift;
        final String background, border;
        final boolean keepWidth, keepHeight, keepDepth, visible;

        BoxSpec(
                MathDimension width,
                MathDimension height,
                MathDimension shift,
                String background,
                String border,
                boolean keepWidth,
                boolean keepHeight,
                boolean keepDepth,
                boolean visible) {
            this.width = width;
            this.height = height;
            this.shift = shift;
            this.background = background;
            this.border = border;
            this.keepWidth = keepWidth;
            this.keepHeight = keepHeight;
            this.keepDepth = keepDepth;
            this.visible = visible;
        }
    }

    static final class VerbSpec implements Payload {
        final boolean starred;

        VerbSpec(boolean starred) {
            this.starred = starred;
        }
    }

    static final class UnsupportedSpec implements Payload {
        final String command, reason;

        UnsupportedSpec(String command) {
            this.command = command;
            this.reason = "trust=false";
        }
    }

    static final class TagSpec implements Payload {
        final boolean displayRequired, parenthesized;

        TagSpec(boolean displayRequired, boolean parenthesized) {
            this.displayRequired = displayRequired;
            this.parenthesized = parenthesized;
        }
    }

    static final class ColumnSpec {
        enum Type {
            ALIGN,
            SOLID,
            DASHED,
            INSERT
        }

        final Type type;
        final String align;
        final double preGap, postGap;
        final int insertChild;

        ColumnSpec(Type type, String align, double preGap, double postGap, int insertChild) {
            this.type = type;
            this.align = align;
            this.preGap = preGap;
            this.postGap = postGap;
            this.insertChild = insertChild;
        }
    }

    static final class RowSpec {
        final int firstCell, cellCount, tagChild;
        final MathDimension gapAfter;
        final boolean automaticTag;

        RowSpec(
                int firstCell,
                int count,
                MathDimension gapAfter,
                int tagChild,
                boolean automaticTag) {
            this.firstCell = firstCell;
            this.cellCount = count;
            this.gapAfter = gapAfter;
            this.tagChild = tagChild;
            this.automaticTag = automaticTag;
        }
    }

    static final class LineSpec {
        final int beforeRow;
        final boolean dashed;

        LineSpec(int beforeRow, boolean dashed) {
            this.beforeRow = beforeRow;
            this.dashed = dashed;
        }
    }

    /** flat children: 每行连续 cell，行号/列号仅由 RowSpec 索引；附加插入列与 tag 也用 child index。 */
    static final class ArraySpec implements Payload {
        final String environment, cellStyle, separation, leftDelimiter, rightDelimiter;
        final List<RowSpec> rows;
        final List<ColumnSpec> columns;
        final List<LineSpec> lines;
        final double stretch;
        final boolean displayRequired, inlineAutoDisplay, addJot, outerSpacing, singleRow;

        ArraySpec(
                String environment,
                String cellStyle,
                String separation,
                String left,
                String right,
                List<RowSpec> rows,
                List<ColumnSpec> columns,
                List<LineSpec> lines,
                double stretch,
                boolean displayRequired,
                boolean inlineAutoDisplay,
                boolean addJot,
                boolean outerSpacing,
                boolean singleRow) {
            this.environment = environment;
            this.cellStyle = cellStyle;
            this.separation = separation;
            this.leftDelimiter = left;
            this.rightDelimiter = right;
            this.rows = immutable(rows);
            this.columns = immutable(columns);
            this.lines = immutable(lines);
            this.stretch = stretch;
            this.displayRequired = displayRequired;
            this.inlineAutoDisplay = inlineAutoDisplay;
            this.addJot = addJot;
            this.outerSpacing = outerSpacing;
            this.singleRow = singleRow;
        }
    }
}
