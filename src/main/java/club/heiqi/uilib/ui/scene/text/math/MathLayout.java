package club.heiqi.uilib.ui.scene.text.math;

import club.heiqi.uilib.ui.scene.text.TextLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 不可变公式几何，所有坐标以公式局部顶部为原点，字形仍使用既有 TextLayout。 */
public final class MathLayout {
    public final float width, height, baseline;

    /** 唯一有序绘制事实；分类列表只从此快照派生。 */
    public final List<Entry> entries;

    public final List<Stroke> strokes;
    public final List<TextPiece> pieces;
    public final List<Rule> rules;
    public final List<Embed> embeds;
    public final List<Diagnostic> diagnostics;
    public final boolean fallback;

    /** 供同文档下一公式显式串联，重新布局本公式不会改变输入编号。 */
    public final int nextEquationNumber;

    /** 降级时完整原文的正常文本布局；也同时作为一个 TextPiece 发布。 */
    public final TextLayout sourceFallback;

    MathLayout(
            float width,
            float height,
            float baseline,
            List<TextPiece> pieces,
            List<Rule> rules,
            List<Embed> embeds,
            List<Diagnostic> diagnostics,
            boolean fallback,
            TextLayout sourceFallback) {
        this(
                width,
                height,
                baseline,
                legacyEntries(pieces, rules, embeds),
                diagnostics,
                fallback,
                sourceFallback);
    }

    MathLayout(
            float width,
            float height,
            float baseline,
            List<Entry> entries,
            List<Diagnostic> diagnostics,
            boolean fallback,
            TextLayout sourceFallback) {
        this(width, height, baseline, entries, diagnostics, fallback, sourceFallback, 1);
    }

    MathLayout(
            float width,
            float height,
            float baseline,
            List<Entry> entries,
            List<Diagnostic> diagnostics,
            boolean fallback,
            TextLayout sourceFallback,
            int nextEquationNumber) {
        this.nextEquationNumber = nextEquationNumber;
        this.width = width;
        this.height = height;
        this.baseline = baseline;
        this.entries = immutable(entries);
        List<TextPiece> texts = new ArrayList<TextPiece>();
        List<Rule> fills = new ArrayList<Rule>();
        List<Stroke> lines = new ArrayList<Stroke>();
        List<Embed> items = new ArrayList<Embed>();
        for (Entry entry : this.entries) {
            if (entry.kind == Entry.Kind.TEXT) texts.add(entry.piece);
            else if (entry.kind == Entry.Kind.FILL) fills.add(entry.rule);
            else if (entry.kind == Entry.Kind.STROKE) lines.add(entry.stroke);
            else items.add(entry.embed);
        }
        this.pieces = immutable(texts);
        this.rules = immutable(fills);
        this.strokes = immutable(lines);
        this.embeds = immutable(items);
        this.diagnostics = immutable(diagnostics);
        this.fallback = fallback;
        this.sourceFallback = sourceFallback;
    }

    private static List<Entry> legacyEntries(
            List<TextPiece> pieces, List<Rule> rules, List<Embed> embeds) {
        List<Entry> entries = new ArrayList<Entry>();
        for (TextPiece piece : pieces) entries.add(Entry.text(piece));
        for (Rule rule : rules) entries.add(Entry.fill(rule));
        for (Embed embed : embeds) entries.add(Entry.embed(embed));
        return entries;
    }

    public static final class Entry {
        public enum Kind {
            TEXT,
            FILL,
            STROKE,
            EMBED
        }

        public final Kind kind;
        public final TextPiece piece;
        public final Rule rule;
        public final Stroke stroke;
        public final Embed embed;

        private Entry(Kind kind, TextPiece piece, Rule rule, Stroke stroke, Embed embed) {
            this.kind = kind;
            this.piece = piece;
            this.rule = rule;
            this.stroke = stroke;
            this.embed = embed;
        }

        static Entry text(TextPiece value) {
            return new Entry(Kind.TEXT, value, null, null, null);
        }

        static Entry fill(Rule value) {
            return new Entry(Kind.FILL, null, value, null, null);
        }

        static Entry stroke(Stroke value) {
            return new Entry(Kind.STROKE, null, null, value, null);
        }

        static Entry embed(Embed value) {
            return new Entry(Kind.EMBED, null, null, null, value);
        }

        Entry translated(float x, float y) {
            if (piece != null)
                return text(
                        new TextPiece(
                                piece.text,
                                piece.x + x,
                                piece.y + y,
                                piece.sourceStart,
                                piece.sourceEnd));
            if (rule != null)
                return fill(
                        new Rule(
                                rule.x + x,
                                rule.y + y,
                                rule.width,
                                rule.height,
                                rule.color,
                                rule.sourceStart,
                                rule.sourceEnd));
            if (stroke != null)
                return stroke(
                        new Stroke(
                                stroke.x1 + x,
                                stroke.y1 + y,
                                stroke.x2 + x,
                                stroke.y2 + y,
                                stroke.width,
                                stroke.color,
                                stroke.sourceStart,
                                stroke.sourceEnd));
            return embed(
                    new Embed(
                            embed.id,
                            embed.x + x,
                            embed.y + y,
                            embed.width,
                            embed.height,
                            embed.baseline,
                            embed.sourceStart,
                            embed.sourceEnd));
        }
    }

    public static final class Stroke {
        public final float x1, y1, x2, y2, width;
        public final int color, sourceStart, sourceEnd;

        Stroke(float x1, float y1, float x2, float y2, float width, int color, int start, int end) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.width = width;
            this.color = color;
            this.sourceStart = start;
            this.sourceEnd = end;
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public static final class TextPiece {
        public final TextLayout text;
        public final float x, y;
        public final int sourceStart, sourceEnd;

        TextPiece(TextLayout text, float x, float y, int sourceStart, int sourceEnd) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd;
        }
    }

    public static final class Rule {
        public final float x, y, width, height;
        public final int color, sourceStart, sourceEnd;

        Rule(float x, float y, float width, float height) {
            this(x, y, width, height, 0xffffffff, 0, 0);
        }

        Rule(float x, float y, float width, float height, int color, int start, int end) {
            this.color = color;
            this.sourceStart = start;
            this.sourceEnd = end;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static final class Embed {
        public final String id;
        public final float x, y, width, height, baseline;
        public final int sourceStart, sourceEnd;

        Embed(
                String id,
                float x,
                float y,
                float width,
                float height,
                float baseline,
                int sourceStart,
                int sourceEnd) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.baseline = baseline;
            this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd;
        }
    }

    public static final class Diagnostic {
        public final String stage, message;
        public final int start, end;

        Diagnostic(String stage, int start, int end, String message) {
            this.stage = stage;
            this.start = start;
            this.end = end;
            this.message = message;
        }
    }
}
