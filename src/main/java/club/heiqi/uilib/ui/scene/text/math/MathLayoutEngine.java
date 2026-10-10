package club.heiqi.uilib.ui.scene.text.math;

import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;
import club.heiqi.uilib.ui.scene.text.math.MathContent.Node;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static club.heiqi.uilib.ui.scene.text.math.MathFont.Constant.*;

/** 自有数学盒主路径；不持有资源或场景状态，可由纯派生按输入变化重新求值。 */
public final class MathLayoutEngine {
    public MathLayout layout(
            MathContent content,
            TextStyle style,
            MathStyle mathStyle,
            SceneTextMeasurer text,
            Map<String, MathEmbedMetrics> embeds) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(mathStyle, "mathStyle");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(embeds, "embeds");
        List<MathLayout.Diagnostic> diagnostics =
                new ArrayList<MathLayout.Diagnostic>(content.diagnostics);
        if (content.root == null) return fallback(content, style, text, diagnostics);
        if (content.root.children.isEmpty()) return new Box().finish(diagnostics);
        try {
            Session session = new Session(style, text, embeds, diagnostics, content.root);
            Box result = session.box(content.root, new Context(mathStyle, "mathnormal", false));
            if (!Float.isFinite(result.width) || !Float.isFinite(result.ascent + result.descent))
                throw new LayoutFailure("layout", content.root, "公式几何超出有限范围");
            return result.finish(diagnostics);
        } catch (LayoutFailure failure) {
            diagnostics.add(failure.diagnostic);
            return fallback(content, style, text, diagnostics);
        }
    }

    private static MathLayout fallback(
            MathContent content,
            TextStyle style,
            SceneTextMeasurer text,
            List<MathLayout.Diagnostic> diagnostics) {
        TextLayout source =
                text.layout(content.source, style, Integer.MAX_VALUE, TextPolicy.NO_WRAP);
        float baseline = source.lines().isEmpty() ? 0 : source.lines().get(0).baseline;
        for (TextLayout.Diagnostic warning : source.diagnostics())
            diagnostics.add(
                    new MathLayout.Diagnostic(
                            "text", warning.sourceStart, warning.sourceEnd, warning.message));
        return new MathLayout(
                source.advanceWidth,
                source.advanceHeight,
                baseline,
                Collections.singletonList(
                        new MathLayout.TextPiece(source, 0, 0, 0, content.source.length())),
                Collections.<MathLayout.Rule>emptyList(),
                Collections.<MathLayout.Embed>emptyList(),
                diagnostics,
                true,
                source);
    }

    private static final class LayoutFailure extends RuntimeException {
        final MathLayout.Diagnostic diagnostic;

        LayoutFailure(String stage, Node node, String message) {
            super(message);
            diagnostic = new MathLayout.Diagnostic(stage, node.start, node.end, message);
        }
    }

    private static final class Context {
        final MathStyle style;
        final String alphabet;
        final boolean cramped;

        Context(MathStyle style, String alphabet, boolean cramped) {
            this.style = style;
            this.alphabet = alphabet;
            this.cramped = cramped;
        }

        Context style(MathStyle value) {
            return new Context(value, alphabet, cramped);
        }

        Context cramped() {
            return new Context(style, alphabet, true);
        }

        Context alphabet(String value) {
            return new Context(style, value, cramped);
        }
    }

    private enum Atom {
        ORD,
        OP,
        BIN,
        REL,
        OPEN,
        CLOSE,
        PUNCT,
        INNER,
        NONE
    }

    private static final class Session {
        final TextStyle base;
        final SceneTextMeasurer text;
        final Map<String, MathEmbedMetrics> embeds;
        final List<MathLayout.Diagnostic> diagnostics;
        final Map<MathStyle, MathFont> fonts = new EnumMap<MathStyle, MathFont>(MathStyle.class);
        final float scriptPercent, scriptScriptPercent;

        Session(
                TextStyle base,
                SceneTextMeasurer text,
                Map<String, MathEmbedMetrics> embeds,
                List<MathLayout.Diagnostic> diagnostics,
                Node rootNode) {
            this.base = base;
            this.text = text;
            this.embeds = embeds;
            this.diagnostics = diagnostics;
            try {
                MathFont root = text.mathFont(mathTextStyle(base.logicalEm));
                fonts.put(MathStyle.TEXT, root);
                fonts.put(MathStyle.DISPLAY, root);
                scriptPercent = root == null ? 0 : root.constant(SCRIPT_PERCENT_SCALE_DOWN);
                scriptScriptPercent =
                        root == null ? 0 : root.constant(SCRIPT_SCRIPT_PERCENT_SCALE_DOWN);
            } catch (UnsupportedOperationException error) {
                throw new LayoutFailure("font", rootNode, error.getMessage());
            }
        }

        TextStyle mathTextStyle(float em) {
            // Unicode alphabet 选择真实 glyph，避免再叠加合成 italic/bold。
            return new TextStyle(
                    base.font,
                    em,
                    base.color,
                    base.candidates(),
                    base.effects,
                    base.decoration,
                    FontStyle.NORMAL);
        }

        TextStyle ordinaryTextStyle(Context context) {
            return new TextStyle(
                    base.font,
                    em(context),
                    base.color,
                    base.candidates(),
                    base.effects,
                    base.decoration,
                    base.fontStyle);
        }

        float em(Context context) {
            return base.logicalEm
                    * (context.style == MathStyle.SCRIPT
                            ? scriptPercent / 100f
                            : context.style == MathStyle.SCRIPT_SCRIPT
                                    ? scriptScriptPercent / 100f
                                    : 1);
        }

        MathFont font(Context context, Node node) {
            if (fonts.get(MathStyle.TEXT) == null)
                throw new LayoutFailure("font", node, "当前候选链没有OpenType MATH字体");
            MathFont font = fonts.get(context.style);
            if (!fonts.containsKey(context.style)) {
                font = text.mathFont(mathTextStyle(em(context)));
                fonts.put(context.style, font);
            }
            if (font == null) throw new LayoutFailure("font", node, "当前脚本字号没有OpenType MATH字体");
            return font;
        }

        float c(Context context, Node node, MathFont.Constant constant) {
            return font(context, node).constant(constant);
        }

        Box box(Node node, Context context) {
            try {
                switch (node.kind) {
                    case ROW:
                        return row(node, context);
                    case FONT:
                        return box(node.first, context.alphabet(node.value));
                    case STYLE:
                        return new Box();
                    case SPACE:
                        {
                            font(context, node);
                            Box value = new Box();
                            value.width = em(context) * space(node.value);
                            value.atom = Atom.NONE;
                            return value;
                        }
                    case EMBED:
                        return embed(node, context);
                    case TEXT:
                        font(context, node);
                        return glyph(
                                text.layout(
                                        node.value,
                                        ordinaryTextStyle(context),
                                        Integer.MAX_VALUE,
                                        TextPolicy.NO_WRAP),
                                node,
                                context,
                                false);
                    case GLYPH:
                        {
                            Box value =
                                    glyph(
                                            font(context, node)
                                                    .text(alphabet(node.value, context.alphabet)),
                                            node,
                                            context,
                                            true);
                            value.atom = classify(node.value);
                            return value;
                        }
                    case OPERATOR:
                        return operator(node, context);
                    case SCRIPTS:
                        return scripts(node, context);
                    case FRAC:
                        return fraction(node, context);
                    case ROOT:
                        return radical(node, context);
                    default:
                        throw new AssertionError(node.kind);
                }
            } catch (UnsupportedOperationException error) {
                // 声明的字体能力缺口整式降级，程序错误不吞掉。
                throw new LayoutFailure("font", node, error.getMessage());
            }
        }

        Box glyph(TextLayout layout, Node node, Context context, boolean mathematical) {
            for (TextLayout.Diagnostic diagnostic : layout.diagnostics()) {
                diagnostics.add(
                        new MathLayout.Diagnostic(
                                diagnostic.kind == TextLayout.Diagnostic.Kind.FONT_RESOURCE
                                        ? "resource"
                                        : "font",
                                node.start,
                                node.end,
                                diagnostic.message));
                if (mathematical
                        && (diagnostic.kind == TextLayout.Diagnostic.Kind.MISSING_GLYPH
                                || diagnostic.kind == TextLayout.Diagnostic.Kind.INVALID_INPUT))
                    throw new LayoutFailure("font", node, "数学字形不可用: " + diagnostic.message);
            }
            float baseline = layout.lines().isEmpty() ? 0 : layout.lines().get(0).baseline;
            float left = 0, right = layout.advanceWidth, top = 0, bottom = 0;
            boolean ink = false;
            for (TextLayout.Line line : layout.lines()) {
                for (TextLayout.Glyph glyph : line.glyphs()) {
                    if (mathematical && glyph.replacement)
                        throw new LayoutFailure("font", node, "数学字形发生替代");
                    InkBounds bounds = glyph.inkBounds;
                    if (!bounds.known) throw new LayoutFailure("font", node, "字形墨迹边界不可用");
                    left = Math.min(left, glyph.x + bounds.left);
                    right = Math.max(right, glyph.x + bounds.right);
                    top = Math.min(top, glyph.y + bounds.top - baseline);
                    bottom = Math.max(bottom, glyph.y + bounds.bottom - baseline);
                    ink |= bounds.bottom > bounds.top || bounds.right > bounds.left;
                }
            }
            Box value = new Box();
            value.width = right - left;
            value.ascent = -top;
            value.descent = bottom;
            if (!ink && !node.value.isEmpty()) {
                value.ascent = baseline;
                value.descent = Math.max(0, layout.advanceHeight - baseline);
            }
            value.pieces.add(
                    new MathLayout.TextPiece(layout, -left, -baseline, node.start, node.end));
            if (mathematical) value.italic = font(context, node).italicCorrection(layout);
            return value;
        }

        Box embed(Node node, Context context) {
            MathEmbedMetrics metrics = embeds.get(node.value);
            if (metrics == null) throw new LayoutFailure("layout", node, "未绑定嵌入标识: " + node.value);
            if (!metrics.valid())
                throw new LayoutFailure("layout", node, "嵌入尺寸必须有限、正值且基线在盒内: " + node.value);
            font(context, node);
            float em = em(context);
            Box value = new Box();
            value.width = metrics.widthEm * em;
            value.ascent = metrics.baselineEm * em;
            value.descent = (metrics.heightEm - metrics.baselineEm) * em;
            if (!Float.isFinite(value.width) || !Float.isFinite(value.ascent + value.descent))
                throw new LayoutFailure("layout", node, "嵌入按当前字号换算后超出有限范围: " + node.value);
            value.embeds.add(
                    new MathLayout.Embed(
                            node.value,
                            0,
                            -value.ascent,
                            value.width,
                            value.ascent + value.descent,
                            value.ascent,
                            node.start,
                            node.end));
            return value;
        }

        Box row(Node node, Context initial) {
            List<Box> boxes = new ArrayList<Box>();
            List<Context> contexts = new ArrayList<Context>();
            Context context = initial;
            for (Node child : node.children) {
                if (child.kind == MathContent.Kind.STYLE) {
                    context = context.style(style(child.value));
                    continue;
                }
                boxes.add(box(child, context));
                contexts.add(context);
            }
            // TeX BIN 归一化忽略显式 kern，按实际原子序列判定。
            Atom previous = Atom.NONE;
            for (int i = 0; i < boxes.size(); i++) {
                Box current = boxes.get(i);
                if (current.atom == Atom.NONE) continue;
                Atom next = Atom.NONE;
                for (int j = i + 1; j < boxes.size(); j++)
                    if (boxes.get(j).atom != Atom.NONE) {
                        next = boxes.get(j).atom;
                        break;
                    }
                if (current.atom == Atom.BIN
                        && (previous == Atom.NONE
                                || previous == Atom.BIN
                                || previous == Atom.OP
                                || previous == Atom.REL
                                || previous == Atom.OPEN
                                || previous == Atom.PUNCT
                                || next == Atom.NONE
                                || next == Atom.REL
                                || next == Atom.CLOSE
                                || next == Atom.PUNCT)) current.atom = Atom.ORD;
                previous = current.atom;
            }
            Box value = new Box();
            float cursor = 0, left = 0, right = 0;
            previous = Atom.NONE;
            for (int i = 0; i < boxes.size(); i++) {
                Box current = boxes.get(i);
                Context currentContext = contexts.get(i);
                if (current.atom != Atom.NONE) {
                    cursor +=
                            spacing(previous, current.atom, currentContext.style)
                                    * em(currentContext)
                                    / 18f;
                    previous = current.atom;
                }
                value.add(current, cursor, 0);
                if (current.atom != Atom.NONE) {
                    left = Math.min(left, cursor);
                    right = Math.max(right, cursor + current.width);
                    value.italic = current.italic;
                }
                cursor += current.width;
            }
            value.width = Math.max(right, cursor) - left;
            if (left < 0) value = value.shifted(-left, 0, value.width);
            value.atom = Atom.ORD;
            return value;
        }

        Box operator(Node node, Context context) {
            String name = node.value;
            int cp =
                    name.equals("int")
                            ? 0x222b
                            : name.equals("sum") ? 0x2211 : name.equals("prod") ? 0x220f : 0;
            MathFont font = font(context, node);
            TextLayout layout =
                    cp == 0
                            ? font.text(
                                    name.startsWith("@") || name.startsWith("*")
                                            ? name.substring(1)
                                            : name)
                            : context.style == MathStyle.DISPLAY
                                    ? font.displayOperator(
                                            cp, font.constant(DISPLAY_OPERATOR_MIN_HEIGHT))
                                    : font.text(new String(Character.toChars(cp)));
            Box value = glyph(layout, node, context, true);
            value.atom = Atom.OP;
            if (cp != 0) {
                float center = (value.descent - value.ascent) / 2;
                float delta = -c(context, node, AXIS_HEIGHT) - center;
                value = value.shifted(0, delta, value.width);
                value.atom = Atom.OP;
            }
            return value;
        }

        Box scripts(Node node, Context context) {
            Box base = box(node.first, context);
            Box upper =
                    node.second == null
                            ? null
                            : box(node.second, context.style(context.style.script()));
            Box lower =
                    node.third == null
                            ? null
                            : box(node.third, context.style(context.style.script()).cramped());
            boolean limits =
                    node.value.equals("limits")
                            || node.value.equals("default")
                                    && context.style == MathStyle.DISPLAY
                                    && node.first.kind == MathContent.Kind.OPERATOR
                                    && (node.first.value.equals("sum")
                                            || node.first.value.equals("prod")
                                            || node.first.value.equals("lim")
                                            || node.first.value.startsWith("*"));
            if (limits) return limits(node, context, base, upper, lower);
            float up = 0, down = 0;
            if (upper != null) {
                up =
                        Math.max(
                                c(
                                        context,
                                        node,
                                        context.cramped
                                                ? SUPERSCRIPT_SHIFT_UP_CRAMPED
                                                : SUPERSCRIPT_SHIFT_UP),
                                Math.max(
                                        base.ascent
                                                - c(context, node, SUPERSCRIPT_BASELINE_DROP_MAX),
                                        upper.descent + c(context, node, SUPERSCRIPT_BOTTOM_MIN)));
            }
            if (lower != null) {
                down =
                        Math.max(
                                c(context, node, SUBSCRIPT_SHIFT_DOWN),
                                Math.max(
                                        base.descent
                                                + c(context, node, SUBSCRIPT_BASELINE_DROP_MIN),
                                        lower.ascent - c(context, node, SUBSCRIPT_TOP_MAX)));
            }
            if (upper != null && lower != null) {
                float deficit =
                        c(context, node, SUB_SUPERSCRIPT_GAP_MIN)
                                - (up + down - upper.descent - lower.ascent);
                if (deficit > 0) {
                    float raise =
                            Math.min(
                                    deficit,
                                    Math.max(
                                            0,
                                            c(context, node, SUPERSCRIPT_BOTTOM_MAX_WITH_SUBSCRIPT)
                                                    - (up - upper.descent)));
                    up += raise;
                    down += deficit - raise;
                }
            }
            Box value = new Box();
            value.add(base, 0, 0);
            float width = base.width;
            if (upper != null) {
                value.add(upper, base.width + base.italic, -up);
                width = Math.max(width, base.width + base.italic + upper.width);
            }
            if (lower != null) {
                value.add(lower, base.width, down);
                width = Math.max(width, base.width + lower.width);
            }
            value.width =
                    width
                            + (upper != null || lower != null
                                    ? c(context, node, SPACE_AFTER_SCRIPT)
                                    : 0);
            value.atom = base.atom;
            return value;
        }

        Box limits(Node node, Context context, Box base, Box upper, Box lower) {
            float upperX = upper == null ? 0 : (base.width - upper.width) / 2 + base.italic / 2;
            float lowerX = lower == null ? 0 : (base.width - lower.width) / 2 - base.italic / 2;
            float left = Math.min(0, Math.min(upperX, lowerX));
            float right =
                    Math.max(
                            base.width,
                            Math.max(
                                    upper == null ? 0 : upperX + upper.width,
                                    lower == null ? 0 : lowerX + lower.width));
            Box value = new Box();
            value.add(base, -left, 0);
            if (upper != null) {
                float up =
                        base.ascent
                                + Math.max(
                                        c(context, node, UPPER_LIMIT_BASELINE_RISE_MIN),
                                        c(context, node, UPPER_LIMIT_GAP_MIN) + upper.descent);
                value.add(upper, upperX - left, -up);
            }
            if (lower != null) {
                float down =
                        base.descent
                                + Math.max(
                                        c(context, node, LOWER_LIMIT_BASELINE_DROP_MIN),
                                        c(context, node, LOWER_LIMIT_GAP_MIN) + lower.ascent);
                value.add(lower, lowerX - left, down);
            }
            value.width = right - left;
            value.atom = Atom.OP;
            return value;
        }

        Box fraction(Node node, Context inherited) {
            Context context = inherited;
            if (node.value.equals("dfrac")) context = context.style(MathStyle.DISPLAY);
            else if (node.value.equals("tfrac")) context = context.style(MathStyle.TEXT);
            Box numerator = box(node.first, context.style(context.style.fraction()));
            Box denominator = box(node.second, context.style(context.style.fraction()).cramped());
            boolean display = context.style == MathStyle.DISPLAY;
            float axis = c(context, node, AXIS_HEIGHT);
            float rule = c(context, node, FRACTION_RULE_THICKNESS);
            float up =
                    Math.max(
                            c(
                                    context,
                                    node,
                                    display
                                            ? FRACTION_NUMERATOR_DISPLAY_STYLE_SHIFT_UP
                                            : FRACTION_NUMERATOR_SHIFT_UP),
                            axis
                                    + rule / 2
                                    + numerator.descent
                                    + c(
                                            context,
                                            node,
                                            display
                                                    ? FRACTION_NUM_DISPLAY_STYLE_GAP_MIN
                                                    : FRACTION_NUMERATOR_GAP_MIN));
            float down =
                    Math.max(
                            c(
                                    context,
                                    node,
                                    display
                                            ? FRACTION_DENOMINATOR_DISPLAY_STYLE_SHIFT_DOWN
                                            : FRACTION_DENOMINATOR_SHIFT_DOWN),
                            denominator.ascent
                                    - axis
                                    + rule / 2
                                    + c(
                                            context,
                                            node,
                                            display
                                                    ? FRACTION_DENOM_DISPLAY_STYLE_GAP_MIN
                                                    : FRACTION_DENOMINATOR_GAP_MIN));
            float width = Math.max(numerator.width, denominator.width) + 2 * rule;
            Box value = new Box();
            value.add(numerator, (width - numerator.width) / 2, -up);
            value.add(denominator, (width - denominator.width) / 2, down);
            value.rule(0, -axis - rule / 2, width, rule);
            value.width = width;
            value.atom = Atom.INNER;
            return value;
        }

        Box radical(Node node, Context context) {
            Box radicand = box(node.first, context.cramped());
            float gap =
                    c(
                            context,
                            node,
                            context.style == MathStyle.DISPLAY
                                    ? RADICAL_DISPLAY_STYLE_VERTICAL_GAP
                                    : RADICAL_VERTICAL_GAP);
            float rule = c(context, node, RADICAL_RULE_THICKNESS);
            float target = radicand.ascent + radicand.descent + gap + rule;
            Box sign = glyph(font(context, node).stretch(0x221a, target), node, context, true);
            float extra = Math.max(0, sign.ascent + sign.descent - target);
            gap += extra / 2;
            float ruleTop = -radicand.ascent - gap - rule;
            float signY = ruleTop + sign.ascent;
            Box degree =
                    node.second == null
                            ? null
                            : box(node.second, context.style(MathStyle.SCRIPT_SCRIPT));
            float signX = 0, degreeX = 0, degreeY = 0;
            if (degree != null) {
                float before = c(context, node, RADICAL_KERN_BEFORE_DEGREE);
                float after = c(context, node, RADICAL_KERN_AFTER_DEGREE);
                degreeX = before;
                signX = before + degree.width + after;
                float left = Math.min(0, Math.min(signX, degreeX));
                signX -= left;
                degreeX -= left;
                float signBottom = signY + sign.descent;
                degreeY =
                        signBottom
                                - (sign.ascent + sign.descent)
                                        * c(context, node, RADICAL_DEGREE_BOTTOM_RAISE_PERCENT)
                                        / 100f
                                - degree.descent;
            }
            Box value = new Box();
            value.add(sign, signX, signY);
            value.add(radicand, signX + sign.width, 0);
            value.rule(signX + sign.width, ruleTop, radicand.width, rule);
            value.ascent =
                    Math.max(value.ascent, -ruleTop + c(context, node, RADICAL_EXTRA_ASCENDER));
            if (degree != null) value.add(degree, degreeX, degreeY);
            value.width =
                    Math.max(
                            signX + sign.width + radicand.width,
                            degree == null ? 0 : degreeX + degree.width);
            return value;
        }
    }

    /** 内部坐标以基线为原点；出口才转换为局部顶部，保留 TextLayout 原生基线。 */
    private static final class Box {
        float width, ascent, descent, italic;
        Atom atom = Atom.ORD;
        final List<MathLayout.TextPiece> pieces = new ArrayList<MathLayout.TextPiece>();
        final List<MathLayout.Rule> rules = new ArrayList<MathLayout.Rule>();
        final List<MathLayout.Embed> embeds = new ArrayList<MathLayout.Embed>();

        void add(Box child, float x, float y) {
            ascent = Math.max(ascent, child.ascent - y);
            descent = Math.max(descent, child.descent + y);
            for (MathLayout.TextPiece piece : child.pieces)
                pieces.add(
                        new MathLayout.TextPiece(
                                piece.text,
                                piece.x + x,
                                piece.y + y,
                                piece.sourceStart,
                                piece.sourceEnd));
            for (MathLayout.Rule rule : child.rules)
                rules.add(new MathLayout.Rule(rule.x + x, rule.y + y, rule.width, rule.height));
            for (MathLayout.Embed embed : child.embeds)
                embeds.add(
                        new MathLayout.Embed(
                                embed.id,
                                embed.x + x,
                                embed.y + y,
                                embed.width,
                                embed.height,
                                embed.baseline,
                                embed.sourceStart,
                                embed.sourceEnd));
        }

        void rule(float x, float y, float width, float height) {
            rules.add(new MathLayout.Rule(x, y, width, height));
            ascent = Math.max(ascent, -y);
            descent = Math.max(descent, y + height);
        }

        Box shifted(float x, float y, float width) {
            Box value = new Box();
            value.add(this, x, y);
            value.width = width;
            value.atom = atom;
            value.italic = italic;
            return value;
        }

        MathLayout finish(List<MathLayout.Diagnostic> diagnostics) {
            Box shifted = shifted(0, ascent, width);
            return new MathLayout(
                    Math.max(0, width),
                    ascent + descent,
                    ascent,
                    shifted.pieces,
                    shifted.rules,
                    shifted.embeds,
                    diagnostics,
                    false,
                    null);
        }
    }

    private static MathStyle style(String name) {
        if (name.equals("displaystyle")) return MathStyle.DISPLAY;
        if (name.equals("textstyle")) return MathStyle.TEXT;
        if (name.equals("scriptstyle")) return MathStyle.SCRIPT;
        return MathStyle.SCRIPT_SCRIPT;
    }

    private static float space(String name) {
        if (name.equals(",")) return 3f / 18;
        if (name.equals(":")) return 4f / 18;
        if (name.equals(";")) return 5f / 18;
        if (name.equals("!")) return -3f / 18;
        if (name.equals("quad")) return 1;
        if (name.equals("qquad")) return 2;
        return 1f / 3;
    }

    private static Atom classify(String value) {
        int cp = value.codePointAt(0);
        if ("+-−±×⋅*".indexOf(cp) >= 0) return Atom.BIN;
        if ("=<>≤≥≠→".indexOf(cp) >= 0) return Atom.REL;
        if ("([{".indexOf(cp) >= 0) return Atom.OPEN;
        if (")]}".indexOf(cp) >= 0) return Atom.CLOSE;
        if (",;".indexOf(cp) >= 0) return Atom.PUNCT;
        return Atom.ORD;
    }

    /** TeX 八类间距表，script 仅保留无括号的 thin spacing。单位为 mu。 */
    private static float spacing(Atom left, Atom right, MathStyle style) {
        if (left == Atom.NONE || right == Atom.NONE) return 0;
        int[][] table = {
            {0, 1, 2, 3, 0, 0, 0, 1},
            {1, 1, 0, 3, 0, 0, 0, 1},
            {2, 2, 0, 0, 2, 0, 0, 2},
            {3, 3, 0, 0, 3, 0, 0, 3},
            {0, 0, 0, 0, 0, 0, 0, 0},
            {0, 1, 2, 3, 0, 0, 0, 1},
            {1, 1, 0, 1, 1, 1, 1, 1},
            {1, 1, 2, 3, 1, 0, 1, 1}
        };
        int code = table[left.ordinal()][right.ordinal()];
        if (style == MathStyle.SCRIPT || style == MathStyle.SCRIPT_SCRIPT) {
            if (code != 1
                    || !(left == Atom.ORD && right == Atom.OP
                            || left == Atom.OP && (right == Atom.ORD || right == Atom.OP)
                            || left == Atom.CLOSE && right == Atom.OP
                            || left == Atom.INNER && right == Atom.OP)) return 0;
        }
        return code == 1 ? 3 : code == 2 ? 4 : code == 3 ? 5 : 0;
    }

    private static String alphabet(String value, String alphabet) {
        if (alphabet.equals("mathrm")) return value;
        boolean bold = alphabet.equals("mathbf");
        StringBuilder mapped = new StringBuilder();
        for (int offset = 0; offset < value.length(); ) {
            int cp = value.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp >= 'A' && cp <= 'Z') cp = (bold ? 0x1d400 : 0x1d434) + cp - 'A';
            else if (cp >= 'a' && cp <= 'z')
                cp = !bold && cp == 'h' ? 0x210e : (bold ? 0x1d41a : 0x1d44e) + cp - 'a';
            else if (bold && cp >= '0' && cp <= '9') cp = 0x1d7ce + cp - '0';
            else if (cp >= 0x3b1 && cp <= 0x3c9) cp = (bold ? 0x1d6c2 : 0x1d6fc) + cp - 0x3b1;
            mapped.appendCodePoint(cp);
        }
        return mapped.toString();
    }
}
