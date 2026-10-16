package club.heiqi.uilib.ui.scene.text.math;

import static club.heiqi.uilib.ui.scene.text.math.MathFont.Constant.*;

import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.MathTextFontSources;
import club.heiqi.uilib.ui.scene.text.math.MathBox.Atom;
import club.heiqi.uilib.ui.scene.text.math.MathContent.Node;
import club.heiqi.uilib.ui.scene.text.math.MathLayoutEngine.Session;

import java.util.Locale;

/** 数学装饰与局部作用域：只展开为同一有序盒，不保存 AST 或第二棵几何树。 宽饰件沿 specs/content/uilib-math-content-spec.md 第 4 节的数学路径；括号仍请求真实 HORIZONTAL MATH。 */
final class MathDecorations {
    private MathDecorations() {}

    static MathBox layout(Session s, Node n, MathLayoutContext c) {
        switch (n.kind) {
            case COLOR:
            case SIZE:
                return body(s, n, scope(s, n, c));
            case CLASSIFY:
                {
                    MathBox b = body(s, n, c);
                    b.atom = s.atom(((MathContent.ClassSpec) n.payload).atomGroup);
                    return b;
                }
            case PHANTOM:
                {
                    MathBox b = body(s, n, c), out = new MathBox();
                    MathContent.BoxSpec spec = (MathContent.BoxSpec) n.payload;
                    out.width = spec.keepWidth ? b.width : 0;
                    out.ascent = spec.keepHeight ? b.ascent : 0;
                    out.descent = spec.keepDepth ? b.descent : 0;
                    out.atom = Atom.ORD;
                    return out;
                }
            case SMASH:
                {
                    MathBox b = body(s, n, c);
                    MathContent.BoxSpec spec = (MathContent.BoxSpec) n.payload;
                    if (!spec.keepHeight) b.ascent = 0;
                    if (!spec.keepDepth) b.descent = 0;
                    return b;
                }
            case VCENTER:
                {
                    MathBox b = body(s, n, c);
                    float axis = s.c(c, n, AXIS_HEIGHT);
                    return b.shifted(0, (b.ascent - b.descent) / 2 - axis, b.width);
                }
            case RAISE:
                {
                    MathBox b = body(s, n, c);
                    return b.shifted(
                            0,
                            -s.dimension(((MathContent.BoxSpec) n.payload).shift, c, n),
                            b.width);
                }
            case LAP:
                {
                    MathBox b = body(s, n, c);
                    String command = command(n);
                    float x =
                            command.contains("llap")
                                    ? -b.width
                                    : command.contains("clap") ? -b.width / 2 : 0;
                    return b.shifted(x, 0, 0);
                }
            case RULE:
                {
                    MathContent.BoxSpec spec = (MathContent.BoxSpec) n.payload;
                    float w = s.dimension(spec.width, c, n),
                            h = s.dimension(spec.height, c, n),
                            raise = s.dimension(spec.shift, c, n);
                    if (w < 0 || h < 0) throw failure(n, "rule 宽高不能为负");
                    MathBox b = new MathBox();
                    b.width = w;
                    if (w > 0 && h > 0)
                        b.rule(0, -raise - h, w, h, c.textStyle.color, n.start, n.end);
                    b.ascent = Math.max(0, raise + h);
                    b.descent = Math.max(0, -raise);
                    return b;
                }
            case BOX:
                return boxed(s, n, c);
            case ENCLOSE:
                return enclose(s, n, c);
            case PMB:
                return pmb(s, n, c);
            case ACCENT:
                return accent(s, n, c);
            case HORIZBRACE:
                return brace(s, n, c);
            case OVERUNDER:
                return overUnder(s, n, c);
            case XARROW:
                return xArrow(s, n, c);
            case MATHCHOICE:
                {
                    int index =
                            c.style == MathStyle.DISPLAY
                                    ? 0
                                    : c.style == MathStyle.TEXT
                                            ? 1
                                            : c.style == MathStyle.SCRIPT ? 2 : 3;
                    return s.box(n.children.get(index), c);
                }
            case HTMLMATHML:
                return s.box(n.first, c);
            case VERB:
                {
                    String value = n.value;
                    if (((MathContent.VerbSpec) n.payload).starred) value = value.replace(' ', '␣');
                    TextStyle inherited = s.ordinaryTextStyle(c);
                    // 具名 Mono 是默认选择；显式候选沿全局合同完整覆盖名称，含显式空列表。
                    TextStyle mono =
                            new TextStyle(
                                    MathTextFontSources.MONO,
                                    c.em,
                                    inherited.color,
                                    inherited.candidates(),
                                    inherited.effects,
                                    inherited.decoration,
                                    inherited.fontStyle);
                    return s.glyph(
                            s.text.layout(value, mono, Integer.MAX_VALUE, TextPolicy.NO_WRAP),
                            n,
                            c,
                            false);
                }
            case UNSUPPORTED:
                {
                    MathContent.UnsupportedSpec spec = (MathContent.UnsupportedSpec) n.payload;
                    // 固定参照 Parser.formatUnsupportedCmd 只呈现命令名，默认 errorColor=#cc0000。
                    // 这是合法局部拒绝，不附带 URL/子内容/交互，也不触发整式源文降级。
                    TextStyle rejected = s.ordinaryTextStyle(c).withColor(0xffcc0000);
                    return s.glyph(
                            s.text.layout(
                                    spec.command, rejected, Integer.MAX_VALUE, TextPolicy.NO_WRAP),
                            n,
                            c,
                            false);
                }
            case TAG:
                return tag(s, n, c);
            case LINEBREAK:
                return new MathBox();
            case INTERNAL:
                {
                    if (command(n).equals("relax")) {
                        MathBox b = new MathBox();
                        b.atom = Atom.NONE;
                        return b;
                    }
                    throw failure(n, "解析内部 helper 不能作为可见数学字形: " + n.value);
                }
            default:
                throw failure(n, "非装饰 family: " + n.kind);
        }
    }

    /**
     * 固定 @not 的明确符号形式：零 advance、向右延伸的否定斜线。 宏 not 经 mathrlap 与下一 relation 叠加；真实墨迹须保留但不能用
     * pathEnvelope 拓宽逻辑 advance。尺寸消费当前字体 axis/x-height/rule， 不整形孤立 U+0338，不读取参照字体，不在缺字失败后选择这条路径。
     */
    static MathBox negation(Session s, Node n, MathLayoutContext c) {
        float axis = s.c(c, n, AXIS_HEIGHT), height = s.xHeight(c, n), t = thickness(s, n, c);
        MathBox relation = s.glyph(s.font(c, n).text("="), n, c, true);
        float center = relation.width / 2, halfWidth = height / 2;
        MathBox result = new MathBox();
        line(
                result,
                n,
                c,
                center - halfWidth,
                -axis + height,
                center + halfWidth,
                -axis - height,
                t);
        result.width = 0;
        result.atom =
                n.payload instanceof MathContent.GlyphSpec
                        ? s.atom(((MathContent.GlyphSpec) n.payload).atomGroup)
                        : Atom.REL;
        return result;
    }

    /** 局部颜色/字号共用派生规则，供顶层可见换行的透明作用域收集使用。 派生只消费当前节点，不布局 body，不缓存，不改变其他 family。 */
    static MathLayoutContext scope(Session s, Node n, MathLayoutContext c) {
        switch (n.kind) {
            case COLOR:
                return c.color(color(((MathContent.ColorSpec) n.payload).css, c, n));
            case SIZE:
                {
                    int size = ((MathContent.SizeSpec) n.payload).size;
                    float[] sizes = {
                        0, .5f, .6f, .7f, .8f, .9f, 1, 1.2f, 1.44f, 1.728f, 2.074f, 2.488f
                    };
                    if (size < 1 || size >= sizes.length) throw failure(n, "非法数学字号");
                    return c.size(s.base.logicalEm * sizes[size]);
                }
            default:
                throw failure(n, "非颜色/字号作用域: " + n.kind);
        }
    }

    private static MathBox body(Session s, Node n, MathLayoutContext c) {
        return n.first == null ? s.row(n, c) : s.box(n.first, c);
    }

    private static String command(Node n) {
        String value =
                n.payload instanceof MathContent.DecorationSpec
                        ? ((MathContent.DecorationSpec) n.payload).command
                        : n.value;
        while (value.startsWith("\\")) value = value.substring(1);
        return value;
    }

    private static MathLayoutEngine.LayoutFailure failure(Node n, String message) {
        return new MathLayoutEngine.LayoutFailure("layout", n, message);
    }

    private static float thickness(Session s, Node n, MathLayoutContext c) {
        float t = s.c(c, n, FRACTION_RULE_THICKNESS);
        if (!(t > 0)) throw failure(n, "MATH rule thickness 不可用");
        return t;
    }

    private static void line(
            MathBox b, Node n, MathLayoutContext c, float x, float y, float xx, float yy, float t) {
        if (x != xx || y != yy) b.stroke(x, y, xx, yy, t, c.textStyle.color, n.start, n.end);
    }

    private static MathBox tag(Session s, Node n, MathLayoutContext c) {
        MathContent.TagSpec spec = (MathContent.TagSpec) n.payload;
        if (spec.displayRequired && !c.display) throw failure(n, "标签仅允许显示公式");
        MathLayoutContext tc = c.style(MathStyle.TEXT);
        MathBox b = s.box(n.second == null ? n.first : n.second, tc);
        if (n.second != null) {
            MathBox base = s.box(n.first, c), out = new MathBox();
            out.add(base, 0, 0);
            out.add(b, base.width + c.em, 0);
            out.width = base.width + c.em + b.width;
            out.atom = base.atom;
            return out;
        }
        if (!spec.parenthesized) return b;
        MathBox l = s.glyph(s.font(tc, n).text("("), n, tc, true),
                r = s.glyph(s.font(tc, n).text(")"), n, tc, true);
        MathBox out = new MathBox();
        out.add(l, 0, 0);
        out.add(b, l.width, 0);
        out.add(r, l.width + b.width, 0);
        out.width = l.width + b.width + r.width;
        return out;
    }

    private static MathBox boxed(Session s, Node n, MathLayoutContext c) {
        MathContent.BoxSpec spec =
                n.payload instanceof MathContent.BoxSpec
                        ? (MathContent.BoxSpec) n.payload
                        : new MathContent.BoxSpec(
                                null, null, null, null, "currentColor", true, true, true, true);
        MathBox b = body(s, n, c), out = new MathBox();
        float t = thickness(s, n, c), pad = .3f * c.em, border = spec.border == null ? 0 : t;
        float inset = pad + border,
                w = b.width + 2 * inset,
                top = -b.ascent - inset,
                h = b.ascent + b.descent + 2 * inset;
        // 背景先于 body/Item；边框明确四个 RECT，不能把整个盒涂成边框色。
        if (spec.background != null)
            out.rule(0, top, w, h, color(spec.background, c, n), n.start, n.end);
        out.add(b, inset, 0);
        if (spec.border != null) {
            int ink = color(spec.border, c, n);
            out.rule(0, top, w, t, ink, n.start, n.end);
            out.rule(0, top + h - t, w, t, ink, n.start, n.end);
            out.rule(0, top + t, t, Math.max(0, h - 2 * t), ink, n.start, n.end);
            out.rule(w - t, top + t, t, Math.max(0, h - 2 * t), ink, n.start, n.end);
        }
        out.width = w;
        out.ascent = b.ascent + inset;
        out.descent = b.descent + inset;
        out.atom = Atom.ORD;
        return out;
    }

    private static MathBox enclose(Session s, Node n, MathLayoutContext c) {
        if (n.payload instanceof MathContent.BoxSpec
                || command(n).equals("fbox")
                || command(n).equals("boxed")) return boxed(s, n, c);
        MathBox b = body(s, n, c), out = b.shifted(0, 0, b.width);
        String name = command(n);
        float t = thickness(s, n, c), pad = .1f * c.em;
        if (name.equals("cancel") || name.equals("xcancel"))
            line(out, n, c, 0, b.descent, b.width, -b.ascent, t);
        if (name.equals("bcancel") || name.equals("xcancel"))
            line(out, n, c, 0, -b.ascent, b.width, b.descent, t);
        if (name.equals("sout"))
            line(out, n, c, 0, -s.xHeight(c, n) / 2, b.width, -s.xHeight(c, n) / 2, t);
        if (name.equals("angl") || name.equals("actuarial")) {
            line(out, n, c, 0, -b.ascent - pad, b.width + pad, -b.ascent - pad, t);
            line(out, n, c, b.width + pad, -b.ascent - pad, b.width + pad, b.descent, t);
            out.width += pad + t / 2;
        }
        if (name.equals("phase")) {
            float lead = .6f * c.em;
            out = b.shifted(lead, 0, b.width + lead);
            line(out, n, c, 0, b.descent + pad, lead, -b.ascent - pad, t);
            line(out, n, c, 0, b.descent + pad, out.width, b.descent + pad, t);
        }
        return pathEnvelope(out);
    }

    private static MathBox pmb(Session s, Node n, MathLayoutContext c) {
        MathBox b = body(s, n, c), out = new MathBox();
        float delta = .02f * c.em;
        // 唯一显式 poor-man-bold：仅 TEXT 增添字形层，EMBED/背景/边框不复制。
        out.width = b.width;
        out.ascent = b.ascent;
        out.descent = b.descent;
        for (MathLayout.Entry e : b.entries) {
            out.entry(e);
            if (e.kind == MathLayout.Entry.Kind.TEXT) {
                out.entry(e.translated(delta, 0));
                out.entry(e.translated(2 * delta, -delta));
                MathLayout.TextPiece piece = e.piece;
                for (TextLayout.Line l : piece.text.lines())
                    for (TextLayout.Glyph g : l.glyphs()) {
                        out.width =
                                Math.max(out.width, piece.x + g.x + g.inkBounds.right + 2 * delta);
                        out.ascent = Math.max(out.ascent, -piece.y - g.y - g.inkBounds.top + delta);
                    }
            }
        }
        out.atom = b.atom;
        out.italic = b.italic;
        return out;
    }

    private static MathBox overUnder(Session s, Node n, MathLayoutContext c) {
        String name = command(n);
        if (name.equals("overline") || name.equals("underline")) {
            boolean below = name.equals("underline");
            MathBox b = body(s, n, below ? c : c.cramped());
            MathBox out = b.shifted(0, 0, b.width);
            float t = s.c(c, n, below ? UNDERBAR_RULE_THICKNESS : OVERBAR_RULE_THICKNESS);
            float gap = s.c(c, n, below ? UNDERBAR_VERTICAL_GAP : OVERBAR_VERTICAL_GAP);
            float y = below ? b.descent + gap : -b.ascent - gap - t;
            out.rule(0, y, b.width, t, c.textStyle.color, n.start, n.end);
            if (below) out.descent += s.c(c, n, UNDERBAR_EXTRA_DESCENDER);
            else out.ascent += s.c(c, n, OVERBAR_EXTRA_ASCENDER);
            return out;
        }
        MathBox base = s.box(n.first, c), label = s.box(n.second, c.style(c.style.script()));
        boolean below =
                n.payload instanceof MathContent.DecorationSpec
                        ? ((MathContent.DecorationSpec) n.payload).below
                        : name.equals("underset");
        float width = Math.max(base.width, label.width);
        MathBox out = new MathBox();
        out.add(base, (width - base.width) / 2, 0);
        float offset =
                below
                        ? base.descent
                                + Math.max(
                                        s.c(c, n, LOWER_LIMIT_BASELINE_DROP_MIN),
                                        label.ascent + s.c(c, n, LOWER_LIMIT_GAP_MIN))
                        : -base.ascent
                                - Math.max(
                                        s.c(c, n, UPPER_LIMIT_BASELINE_RISE_MIN),
                                        label.descent + s.c(c, n, UPPER_LIMIT_GAP_MIN));
        out.add(label, (width - label.width) / 2, offset);
        out.width = width;
        out.atom = name.equals("stackrel") ? Atom.REL : base.atom;
        return out;
    }

    private static MathBox brace(Session s, Node n, MathLayoutContext c) {
        MathContent.DecorationSpec spec = (MathContent.DecorationSpec) n.payload;
        MathBox base = body(s, n, c.style(MathStyle.DISPLAY));
        int cp = spec.below ? 0x23df : 0x23de;
        MathBox mark =
                s.glyph(s.font(c, n).stretch(cp, base.width, MathAxis.HORIZONTAL), n, c, true);
        float width = Math.max(base.width, mark.width),
                gap =
                        s.c(
                                c,
                                n,
                                spec.below
                                        ? STRETCH_STACK_GAP_BELOW_MIN
                                        : STRETCH_STACK_GAP_ABOVE_MIN);
        MathBox out = new MathBox();
        out.add(base, (width - base.width) / 2, 0);
        out.add(
                mark,
                (width - mark.width) / 2,
                spec.below ? base.descent + gap + mark.ascent : -base.ascent - gap - mark.descent);
        out.width = width;
        out.atom = Atom.ORD;
        return out;
    }

    private static MathBox accent(Session s, Node n, MathLayoutContext c) {
        MathContent.DecorationSpec spec = (MathContent.DecorationSpec) n.payload;
        String name = command(n);
        if (name.equals("overline") || name.equals("underline")) return overUnder(s, n, c);
        MathBox b = body(s, n, spec.below ? c : c.cramped());
        MathSymbols.Entry textMark = MathSymbols.find("text", "\\" + name);
        if (textMark != null && textMark.group.equals("accent-token"))
            return textAccent(s, n, c, b, textMark.replace, name.equals("c"));
        MathBox mark;
        if (name.equals("widehat")
                || name.equals("widecheck")
                || name.equals("widetilde")
                || name.equals("utilde")) {
            mark = wide(s, n, c, name, b.width);
        } else if (name.contains("arrow") || name.contains("harpoon") || name.equals("vec")) {
            mark = arrow(s, n, c, name, Math.max(b.width, .6f * c.em), 0);
        } else if (name.equals("overgroup") || name.equals("undergroup")) {
            mark =
                    s.glyph(
                            s.font(c, n)
                                    .stretch(
                                            spec.below ? 0x23dd : 0x23dc,
                                            b.width,
                                            MathAxis.HORIZONTAL),
                            n,
                            c,
                            true);
        } else if (name.endsWith("linesegment")) {
            mark = new MathBox();
            mark.width = b.width;
            float t = thickness(s, n, c);
            line(mark, n, c, 0, 0, b.width, 0, t);
            line(mark, n, c, 0, -.07f * c.em, 0, .07f * c.em, t);
            line(mark, n, c, b.width, -.07f * c.em, b.width, .07f * c.em, t);
        } else if (name.equals("textcircled")) {
            return circled(s, n, c, b);
        } else {
            int cp = accentCodepoint(name, n);
            // 孤立 combining mark 的普通整形会插入 dotted-circle。HORIZONTAL 目标为零
            // 明确请求最小真实完整 MATH glyph，不拉伸轮廓，不是字体失败后的替代。
            mark = s.glyph(s.font(c, n).stretch(cp, 0, MathAxis.HORIZONTAL), n, c, true);
            float ba = attachment(s, n, c, b), ma = attachment(s, n, c, mark);
            // 单字形必须使用实际记录；复合盒按整体中心定位，不声称有 MATH 附件点。
            float x = ba - ma;
            return attach(s, n, c, b, mark, x, spec.below, false);
        }
        mark = pathEnvelope(mark);
        return attach(s, n, c, b, mark, (b.width - mark.width) / 2, spec.below, true);
    }

    /**
     * 正文重音按固定 accent.js 的 textord/正文 x-height/cedilla 语义定位。 使用正文 spacing 字形及真实 advance/ink，不查询或伪造
     * MATH 附件点； 此命令族是明确布局路径，不在数学重音查询失败后切换到它。
     */
    private static MathBox textAccent(
            Session s, Node n, MathLayoutContext c, MathBox body, String value, boolean below) {
        TextStyle style = s.ordinaryTextStyle(c);
        MathBox mark =
                s.glyph(
                        s.text.layout(value, style, Integer.MAX_VALUE, TextPolicy.NO_WRAP),
                        n,
                        c,
                        false);
        MathBox x =
                s.glyph(
                        s.text.layout("x", style, Integer.MAX_VALUE, TextPolicy.NO_WRAP),
                        n,
                        c,
                        false);
        float width = Math.max(body.width, mark.width);
        float y = below ? body.descent : -Math.max(0, body.ascent - x.ascent);
        MathBox result = new MathBox();
        result.add(body, (width - body.width) / 2, 0);
        result.add(mark, (width - mark.width) / 2, y);
        result.width = width;
        result.atom = body.atom;
        result.italic = body.italic;
        return result;
    }

    private static float attachment(Session s, Node n, MathLayoutContext c, MathBox b) {
        if (b.entries.size() == 1 && b.entries.get(0).piece != null) {
            MathLayout.TextPiece p = b.entries.get(0).piece;
            int count = 0;
            for (TextLayout.Line l : p.text.lines()) count += l.glyphs().size();
            if (count == 1) {
                MathLayoutContext actual =
                        c.style(MathStyle.TEXT)
                                .size(p.text.style.logicalEm)
                                .textStyle(p.text.style);
                float x;
                try {
                    x = s.font(actual, n).topAccentAttachment(p.text);
                } catch (IllegalArgumentException mismatch) {
                    throw new MathLayoutEngine.LayoutFailure("font", n, mismatch.getMessage());
                }
                if (Float.isNaN(x))
                    throw new MathLayoutEngine.LayoutFailure(
                            "font", n, "单字形缺少真实 top-accent attachment");
                return p.x + x;
            }
        }
        return b.width / 2;
    }

    private static MathBox attach(
            Session s,
            Node n,
            MathLayoutContext c,
            MathBox b,
            MathBox mark,
            float x,
            boolean below,
            boolean wide) {
        float gap =
                below
                        ? s.c(c, n, UNDERBAR_VERTICAL_GAP)
                        : wide ? s.c(c, n, OVERBAR_VERTICAL_GAP) : 0;
        // 窄 combining glyph 自带位于 base-height 上方的真实墨迹；仅超过 MATH
        // AccentBaseHeight 的 base 需要上移，不能把整个 base-height 再加一遍。
        float y =
                below
                        ? b.descent + gap + mark.ascent
                        : -Math.max(0, b.ascent - s.c(c, n, ACCENT_BASE_HEIGHT));
        if (wide && !below) y = -b.ascent - gap - mark.descent;
        float left = Math.min(0, x), right = Math.max(b.width, x + mark.width);
        MathBox out = new MathBox();
        out.add(b, -left, 0);
        out.add(mark, x - left, y);
        out.width = right - left;
        out.atom = b.atom;
        out.italic = b.italic;
        return out;
    }

    private static int accentCodepoint(String name, Node n) {
        switch (name) {
            case "acute":
            case "'":
                return 0x301;
            case "grave":
            case "`":
                return 0x300;
            case "ddot":
            case "\"":
                return 0x308;
            case "dot":
            case ".":
                return 0x307;
            case "hat":
            case "^":
                return 0x302;
            case "check":
            case "v":
                return 0x30c;
            case "breve":
            case "u":
                return 0x306;
            case "bar":
            case "=":
                return 0x304;
            case "tilde":
            case "~":
                return 0x303;
            case "mathring":
            case "r":
                return 0x30a;
            case "H":
                return 0x30b;
            case "c":
                return 0x327;
            default:
                throw failure(n, "未定义重音字形: " + name);
        }
    }

    private static MathBox wide(
            Session s, Node n, MathLayoutContext c, String name, float bodyWidth) {
        MathBox out = new MathBox();
        float t = thickness(s, n, c), w = Math.max(bodyWidth, .4f * c.em);
        float height = Math.min(.32f * c.em, .12f * c.em + .06f * w), half = t / 2;
        out.width = w;
        if (name.equals("widehat") || name.equals("widecheck")) {
            float center = name.equals("widehat") ? -height : -half,
                    edge = name.equals("widehat") ? -half : -height;
            line(out, n, c, 0, edge, w / 2, center, t);
            line(out, n, c, w / 2, center, w, edge, t);
        } else {
            // 一整周期波浪以宽度参数求几何，细分段只离散数学曲线，不接触 glyph 轮廓。
            float px = 0, py = -height / 2;
            for (int i = 1; i <= 32; i++) {
                float x = w * i / 32f,
                        y = -height / 2 - (float) Math.sin(2 * Math.PI * i / 32) * height / 2;
                line(out, n, c, px, py, x, y, t);
                px = x;
                py = y;
            }
        }
        return pathEnvelope(out);
    }

    private static MathBox circled(Session s, Node n, MathLayoutContext c, MathBox b) {
        float t = thickness(s, n, c),
                rx = Math.max(b.width / 2, .3f * c.em) + t,
                ry = Math.max((b.ascent + b.descent) / 2, .4f * c.em) + t;
        MathBox out = b.shifted(rx - b.width / 2, 0, 2 * rx);
        float cy = (b.descent - b.ascent) / 2, px = 2 * rx, py = cy;
        for (int i = 1; i <= 48; i++) {
            double a = 2 * Math.PI * i / 48;
            float x = rx + (float) Math.cos(a) * rx, y = cy + (float) Math.sin(a) * ry;
            line(out, n, c, px, py, x, y, t);
            px = x;
            py = y;
        }
        return pathEnvelope(out);
    }

    private static MathBox xArrow(Session s, Node n, MathLayoutContext c) {
        MathLayoutContext label = c.style(c.style.script());
        MathBox up = n.first == null ? new MathBox() : s.box(n.first, label),
                down = n.second == null ? new MathBox() : s.box(n.second, label.cramped());
        String name = command(n);
        if (name.equals("cduparrow") || name.equals("cddownarrow") || name.equals("cdparallel"))
            return cdVertical(s, n, c, name, up, down);
        if (name.equals("cdnone")) return new MathBox();
        float w = Math.max(1.75f * c.em, Math.max(up.width, down.width) + .6f * c.em);
        MathBox out = arrow(s, n, c, command(n), w, -s.c(c, n, AXIS_HEIGHT));
        w = out.width;
        float top = out.ascent, bottom = out.descent;
        if (n.first != null)
            out.add(up, (w - up.width) / 2, -top - s.c(c, n, UPPER_LIMIT_GAP_MIN) - up.descent);
        if (n.second != null)
            out.add(
                    down,
                    (w - down.width) / 2,
                    bottom + s.c(c, n, LOWER_LIMIT_GAP_MIN) + down.ascent);
        out.width = w;
        out.atom = Atom.REL;
        return out;
    }

    private static MathBox cdVertical(
            Session s, Node n, MathLayoutContext c, String name, MathBox left, MathBox right) {
        float t = thickness(s, n, c),
                h =
                        Math.max(
                                2 * c.em,
                                Math.max(left.ascent + left.descent, right.ascent + right.descent)
                                        + .6f * c.em);
        float axis = s.c(c, n, AXIS_HEIGHT),
                x = left.width + .3f * c.em,
                top = -axis - h / 2,
                bottom = -axis + h / 2,
                head = .25f * c.em;
        MathBox b = new MathBox();
        b.width = left.width + right.width + .6f * c.em;
        if (name.equals("cdparallel")) {
            line(b, n, c, x - .07f * c.em, top, x - .07f * c.em, bottom, t);
            line(b, n, c, x + .07f * c.em, top, x + .07f * c.em, bottom, t);
        } else {
            line(b, n, c, x, top, x, bottom, t);
            boolean up = name.equals("cduparrow");
            float y = up ? top : bottom, tail = y + (up ? head : -head);
            line(b, n, c, x - head * .65f, tail, x, y, t);
            line(b, n, c, x + head * .65f, tail, x, y, t);
        }
        b.add(left, 0, -axis + (left.ascent - left.descent) / 2);
        b.add(right, x + .3f * c.em, -axis + (right.ascent - right.descent) / 2);
        b.atom = Atom.REL;
        return pathEnvelope(b);
    }

    private static MathBox arrow(
            Session s, Node n, MathLayoutContext c, String name, float w, float y) {
        MathBox b = new MathBox();
        b.width = w;
        float t = thickness(s, n, c),
                h = .18f * c.em,
                head = Math.min(.28f * c.em, w / 3),
                d = .12f * c.em;
        boolean pair =
                name.contains("harpoons")
                        || name.contains("equilibrium")
                        || name.contains("rightleft")
                        || name.equals("xtofrom");
        boolean dbl =
                name.contains("Leftarrow")
                        || name.contains("Rightarrow")
                        || name.contains("Leftrightarrow")
                        || name.equals("Overrightarrow");
        boolean equal = name.contains("equal") && !name.contains("equilibrium");
        if (pair) {
            boolean leftTop =
                    name.contains("leftrightharpoons") || name.contains("leftequilibrium");
            boolean harp = name.contains("harpoon") || name.contains("equilibrium");
            float shortInset = name.contains("equilibrium") ? .35f * c.em : 0;
            arrowLine(
                    b,
                    n,
                    c,
                    leftTop ? shortInset : 0,
                    y - d,
                    leftTop ? w : w - shortInset,
                    leftTop,
                    !leftTop,
                    harp ? 1 : 0,
                    head,
                    h,
                    t);
            arrowLine(
                    b,
                    n,
                    c,
                    leftTop ? 0 : shortInset,
                    y + d,
                    leftTop ? w - shortInset : w,
                    !leftTop,
                    leftTop,
                    harp ? -1 : 0,
                    head,
                    h,
                    t);
        } else if (dbl || equal) {
            line(b, n, c, 0, y - d, w, y - d, t);
            line(b, n, c, 0, y + d, w, y + d, t);
            if (!equal) {
                boolean left = name.contains("Left") || name.contains("Leftright"),
                        right =
                                name.contains("Right")
                                        || name.contains("Leftright")
                                        || name.equals("Overrightarrow");
                if (left) head(b, n, c, 0, y, head, h + d, true, 0, t);
                if (right) head(b, n, c, w, y, head, h + d, false, 0, t);
            }
        } else {
            boolean left = name.contains("left"), right = !left || name.contains("leftright");
            int harp = name.contains("harpoon") ? (name.contains("down") ? -1 : 1) : 0;
            arrowLine(b, n, c, 0, y, w, left, right, harp, head, h, t);
            if (name.contains("twohead"))
                head(b, n, c, left ? head * .6f : w - head * .6f, y, head, h, left, 0, t);
            if (name.contains("mapsto")) line(b, n, c, 0, y - h, 0, y + h, t);
            if (name.contains("hook")) {
                float x = left ? w : 0, dir = left ? -1 : 1;
                float px = x, py = y;
                for (int i = 1; i <= 12; i++) {
                    double a = Math.PI * i / 12;
                    float xx = x + dir * h * (float) Math.sin(a),
                            yy = y - h + h * (float) Math.cos(a);
                    line(b, n, c, px, py, xx, yy, t);
                    px = xx;
                    py = yy;
                }
            }
        }
        return pathEnvelope(b);
    }

    private static void arrowLine(
            MathBox b,
            Node n,
            MathLayoutContext c,
            float x,
            float y,
            float end,
            boolean left,
            boolean right,
            int harp,
            float len,
            float h,
            float t) {
        line(b, n, c, x, y, end, y, t);
        if (left) head(b, n, c, x, y, len, h, true, harp, t);
        if (right) head(b, n, c, end, y, len, h, false, harp, t);
    }

    private static void head(
            MathBox b,
            Node n,
            MathLayoutContext c,
            float x,
            float y,
            float len,
            float h,
            boolean left,
            int harp,
            float t) {
        float tail = x + (left ? len : -len);
        if (harp >= 0) line(b, n, c, tail, y - h, x, y, t);
        if (harp <= 0) line(b, n, c, tail, y + h, x, y, t);
    }

    /** 通用 LINE 是端点加法向半宽；真实横向墨迹也进入盒尺寸，避免宽饰件溢出原点。 不读取 live glyph/native，也不把路径包络包装为字体字形事实。 */
    private static MathBox pathEnvelope(MathBox b) {
        float left = 0, right = b.width;
        for (MathLayout.Entry e : b.entries)
            if (e.stroke != null) {
                MathLayout.Stroke l = e.stroke;
                double dx = l.x2 - l.x1, dy = l.y2 - l.y1, length = Math.hypot(dx, dy);
                if (length == 0) continue;
                float halfX = (float) (Math.abs(dy) / length * l.width / 2);
                left = Math.min(left, Math.min(l.x1, l.x2) - halfX);
                right = Math.max(right, Math.max(l.x1, l.x2) + halfX);
            }
        return left == 0 && right == b.width ? b : b.shifted(-left, 0, right - left);
    }

    private static final String CSS_COLORS =
            "aliceblue:f0f8ff antiquewhite:faebd7 aquamarine:7fffd4 azure:f0ffff beige:f5f5dc"
                    + " bisque:ffe4c4 blanchedalmond:ffebcd blueviolet:8a2be2 burlywood:deb887"
                    + " cadetblue:5f9ea0 chartreuse:7fff00 chocolate:d2691e coral:ff7f50"
                    + " cornflowerblue:6495ed cornsilk:fff8dc crimson:dc143c darkblue:00008b"
                    + " darkcyan:008b8b darkgoldenrod:b8860b darkgreen:006400 darkgrey:a9a9a9"
                    + " darkkhaki:bdb76b darkmagenta:8b008b darkolivegreen:556b2f darkorange:ff8c00"
                    + " darkorchid:9932cc darkred:8b0000 darksalmon:e9967a darkseagreen:8fbc8f"
                    + " darkslateblue:483d8b darkslategray:2f4f4f darkslategrey:2f4f4f"
                    + " darkturquoise:00ced1 darkviolet:9400d3 deeppink:ff1493 deepskyblue:00bfff"
                    + " dimgray:696969 dimgrey:696969 dodgerblue:1e90ff firebrick:b22222"
                    + " floralwhite:fffaf0 forestgreen:228b22 gainsboro:dcdcdc ghostwhite:f8f8ff"
                    + " goldenrod:daa520 greenyellow:adff2f grey:808080 honeydew:f0fff0 hotpink:ff69b4"
                    + " indianred:cd5c5c indigo:4b0082 ivory:fffff0 khaki:f0e68c lavender:e6e6fa"
                    + " lavenderblush:fff0f5 lawngreen:7cfc00 lemonchiffon:fffacd lightblue:add8e6"
                    + " lightcoral:f08080 lightcyan:e0ffff lightgoldenrodyellow:fafad2"
                    + " lightgreen:90ee90 lightgrey:d3d3d3 lightpink:ffb6c1 lightsalmon:ffa07a"
                    + " lightseagreen:20b2aa lightskyblue:87cefa lightslategray:778899"
                    + " lightslategrey:778899 lightsteelblue:b0c4de lightyellow:ffffe0 limegreen:32cd32"
                    + " linen:faf0e6 mediumaquamarine:66cdaa mediumblue:0000cd mediumorchid:ba55d3"
                    + " mediumpurple:9370db mediumseagreen:3cb371 mediumslateblue:7b68ee"
                    + " mediumspringgreen:00fa9a mediumturquoise:48d1cc mediumvioletred:c71585"
                    + " midnightblue:191970 mintcream:f5fffa mistyrose:ffe4e1 moccasin:ffe4b5"
                    + " navajowhite:ffdead oldlace:fdf5e6 olivedrab:6b8e23 orangered:ff4500"
                    + " orchid:da70d6 palegoldenrod:eee8aa palegreen:98fb98 paleturquoise:afeeee"
                    + " palevioletred:db7093 papayawhip:ffefd5 peachpuff:ffdab9 peru:cd853f plum:dda0dd"
                    + " powderblue:b0e0e6 rosybrown:bc8f8f royalblue:4169e1 saddlebrown:8b4513"
                    + " salmon:fa8072 sandybrown:f4a460 seagreen:2e8b57 seashell:fff5ee sienna:a0522d"
                    + " skyblue:87ceeb slateblue:6a5acd slategray:708090 slategrey:708090 snow:fffafa"
                    + " springgreen:00ff7f steelblue:4682b4 tan:d2b48c thistle:d8bfd8 tomato:ff6347"
                    + " turquoise:40e0d0 wheat:f5deb3 whitesmoke:f5f5f5 yellowgreen:9acd32";

    private static int color(String css, MathLayoutContext c, Node n) {
        String value = css.toLowerCase(Locale.ROOT);
        if (value.equals("currentcolor")) return c.textStyle.color;
        if (value.equals("transparent")) return 0;
        if (value.startsWith("#")) {
            String hex = value.substring(1);
            if (hex.length() == 3)
                hex =
                        ""
                                + hex.charAt(0)
                                + hex.charAt(0)
                                + hex.charAt(1)
                                + hex.charAt(1)
                                + hex.charAt(2)
                                + hex.charAt(2);
            if (hex.length() == 6) return 0xff000000 | Integer.parseInt(hex, 16);
        }
        // CSS 具名颜色是解析值，不依赖宿主/AWT 颜色表。
        String[] names = {
            "black",
            "silver",
            "gray",
            "white",
            "maroon",
            "red",
            "purple",
            "fuchsia",
            "green",
            "lime",
            "olive",
            "yellow",
            "navy",
            "blue",
            "teal",
            "aqua",
            "orange",
            "pink",
            "brown",
            "cyan",
            "magenta",
            "darkgray",
            "lightgray",
            "gold",
            "violet",
            "rebeccapurple"
        };
        int[] colors = {
            0x000000, 0xc0c0c0, 0x808080, 0xffffff, 0x800000, 0xff0000, 0x800080, 0xff00ff,
            0x008000, 0x00ff00, 0x808000, 0xffff00, 0x000080, 0x0000ff, 0x008080, 0x00ffff,
            0xffa500, 0xffc0cb, 0xa52a2a, 0x00ffff, 0xff00ff, 0xa9a9a9, 0xd3d3d3, 0xffd700,
            0xee82ee, 0x663399
        };
        for (int i = 0; i < names.length; i++)
            if (value.equals(names[i])) return 0xff000000 | colors[i];
        for (String entry : CSS_COLORS.split(" ")) {
            int separator = entry.indexOf(':');
            if (value.equals(entry.substring(0, separator)))
                return 0xff000000 | Integer.parseInt(entry.substring(separator + 1), 16);
        }
        throw failure(n, "尚未支持 CSS 颜色: " + css);
    }
}
