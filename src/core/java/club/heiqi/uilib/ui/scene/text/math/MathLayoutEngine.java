package club.heiqi.uilib.ui.scene.text.math;

import static club.heiqi.uilib.ui.scene.text.math.MathFont.Constant.*;

import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;
import club.heiqi.uilib.ui.scene.text.math.MathBox.Atom;
import club.heiqi.uilib.ui.scene.text.math.MathContent.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 自有数学盒主路径；不持有资源或场景状态，可由纯派生按输入变化重新求值。 */
public final class MathLayoutEngine {
    // fixed 插件按完整源公式判定行内 display；只提升 TEXT 入口，脚本请求不会被源文环境覆盖。
    private static final java.util.regex.Pattern INLINE_DISPLAY =
            java.util.regex.Pattern.compile(
                    "\\\\begin\\{(align|equation|gather|cd|alignat)\\}",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    public MathLayout layout(
            MathContent content,
            TextStyle style,
            MathStyle mathStyle,
            SceneTextMeasurer text,
            Map<String, MathEmbedMetrics> embeds) {
        return layout(content, style, mathStyle, text, embeds, 1);
    }

    /** 编号是显式文档输入；仅实际自动标签消费编号，布局没有外部副作用。 */
    public MathLayout layout(
            MathContent content,
            TextStyle style,
            MathStyle mathStyle,
            SceneTextMeasurer text,
            Map<String, MathEmbedMetrics> embeds,
            int initialEquationNumber) {
        if (initialEquationNumber < 1) throw new IllegalArgumentException("公式初始编号必须为正整数");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(mathStyle, "mathStyle");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(embeds, "embeds");
        List<MathLayout.Diagnostic> diagnostics =
                new ArrayList<MathLayout.Diagnostic>(content.diagnostics);
        if (content.root == null)
            return fallback(content, style, text, diagnostics, initialEquationNumber);
        if (content.root.kind == MathContent.Kind.ROW && content.root.children.isEmpty())
            return new MathBox().finish(diagnostics, initialEquationNumber);
        try {
            Session session = new Session(style, text, embeds, diagnostics, content.root);
            session.nextTag = initialEquationNumber;
            MathStyle effectiveStyle =
                    mathStyle == MathStyle.TEXT && INLINE_DISPLAY.matcher(content.source).find()
                            ? MathStyle.DISPLAY
                            : mathStyle;
            MathBox result = session.box(content.root, session.context(effectiveStyle));
            if (!Float.isFinite(result.width) || !Float.isFinite(result.ascent + result.descent))
                throw new LayoutFailure("layout", content.root, "公式几何超出有限范围");
            Set<String> spatialIds = new HashSet<String>();
            for (MathLayout.Entry entry : result.entries)
                if (entry.embed != null && !spatialIds.add(entry.embed.id))
                    throw new LayoutFailure(
                            "layout",
                            entry.embed.sourceStart,
                            entry.embed.sourceEnd,
                            "同一嵌入标识不能拥有多个空间: " + entry.embed.id);
            return result.finish(diagnostics, session.nextTag);
        } catch (LayoutFailure failure) {
            diagnostics.add(failure.diagnostic);
            return fallback(content, style, text, diagnostics, initialEquationNumber);
        }
    }

    private static MathLayout fallback(
            MathContent content,
            TextStyle style,
            SceneTextMeasurer text,
            List<MathLayout.Diagnostic> diagnostics,
            int initialEquationNumber) {
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
                        MathLayout.Entry.text(
                                new MathLayout.TextPiece(
                                        source, 0, 0, 0, content.source.length())).bindColor(true)),
                diagnostics,
                true,
                source,
                initialEquationNumber);
    }

    static final class LayoutFailure extends RuntimeException {
        final MathLayout.Diagnostic diagnostic;

        LayoutFailure(String stage, Node node, String message) {
            this(stage, node.start, node.end, message);
        }

        LayoutFailure(String stage, int start, int end, String message) {
            super(message);
            diagnostic = new MathLayout.Diagnostic(stage, start, end, message);
        }
    }

    static final class Session {
        final TextStyle base;
        final SceneTextMeasurer text;
        final Map<String, MathEmbedMetrics> embeds;
        final List<MathLayout.Diagnostic> diagnostics;
        final Map<TextStyle, MathFont> fonts = new HashMap<TextStyle, MathFont>();
        final float scriptPercent, scriptScriptPercent;
        int nextTag = 1;
        final Node rootBody;
        // 绑定事实随 AST 调用作用域传播；宏来源区间可能重叠，不能从区间或数值反猜。
        boolean inheritedColor = true;

        Session(
                TextStyle base,
                SceneTextMeasurer text,
                Map<String, MathEmbedMetrics> embeds,
                List<MathLayout.Diagnostic> diagnostics,
                Node rootNode) {
            this.rootBody = rootNode.kind == MathContent.Kind.TAG ? rootNode.first : rootNode;
            this.base = base;
            this.text = text;
            this.embeds = embeds;
            this.diagnostics = diagnostics;
            try {
                MathFont root = text.mathFont(mathTextStyle(base.logicalEm));
                fonts.put(mathTextStyle(base.logicalEm), root);
                scriptPercent = root == null ? 0 : root.constant(SCRIPT_PERCENT_SCALE_DOWN);
                scriptScriptPercent =
                        root == null ? 0 : root.constant(SCRIPT_SCRIPT_PERCENT_SCALE_DOWN);
            } catch (UnsupportedOperationException error) {
                throw new LayoutFailure("font", rootNode, error.getMessage());
            }
        }

        MathLayoutContext context(MathStyle style) {
            return new MathLayoutContext(
                    style,
                    "mathnormal",
                    false,
                    base,
                    base.logicalEm,
                    scriptPercent == 0 ? 100 : scriptPercent,
                    scriptScriptPercent == 0 ? 100 : scriptScriptPercent,
                    style == MathStyle.DISPLAY);
        }

        TextStyle mathTextStyle(float em) {
            return new TextStyle(
                    base.font,
                    em,
                    base.color,
                    base.candidates(),
                    base.effects,
                    base.decoration,
                    FontStyle.NORMAL);
        }

        TextStyle mathTextStyle(MathLayoutContext context) {
            return context.textStyle.withFontStyle(FontStyle.NORMAL);
        }

        TextStyle ordinaryTextStyle(MathLayoutContext context) {
            TextStyle scope = context.textStyle;
            // 数学正文的 default 角色选择真实 roman 族；显式候选/自定义 font 保留调用方覆盖，普通文字默认链不变。
            if (scope.font.equals("default") && scope.candidates() == null)
                return new TextStyle(
                        "math-text-roman",
                        scope.logicalEm,
                        scope.color,
                        null,
                        scope.effects,
                        scope.decoration,
                        scope.fontStyle);
            return scope;
        }

        float em(MathLayoutContext context) {
            return context.em;
        }

        MathFont font(MathLayoutContext context, Node node) {
            TextStyle key = mathTextStyle(context);
            if (!fonts.containsKey(key)) fonts.put(key, text.mathFont(key));
            MathFont font = fonts.get(key);
            if (font == null) throw new LayoutFailure("font", node, "当前候选链没有OpenType MATH字体");
            return font;
        }

        float c(MathLayoutContext context, Node node, MathFont.Constant constant) {
            return font(context, node).constant(constant);
        }

        MathBox box(Node node, MathLayoutContext context) {
            boolean parentColor = inheritedColor;
            if (node.kind == MathContent.Kind.COLOR
                    && !currentColor(((MathContent.ColorSpec) node.payload).css))
                inheritedColor = false;
            try {
                MathBox value = boxValue(node, context);
                int ownRule = 0;
                for (int i = 0; i < value.entries.size(); i++) {
                    MathLayout.Entry entry = value.entries.get(i);
                    if (entry.colorBound) continue;
                    boolean inherited = inheritedColor;
                    if (entry.rule != null && (node.kind == MathContent.Kind.BOX
                            || node.kind == MathContent.Kind.ENCLOSE
                                    && (node.payload instanceof MathContent.BoxSpec
                                            || node.value.equals("\\fbox")
                                            || node.value.equals("\\boxed")))) {
                        MathContent.BoxSpec spec = node.payload instanceof MathContent.BoxSpec
                                ? (MathContent.BoxSpec) node.payload : null;
                        String css = spec == null ? "currentColor"
                                : spec.background != null && ownRule == 0
                                        ? spec.background : spec.border;
                        ownRule++;
                        inherited &= currentColor(css);
                    }
                    value.entries.set(i, entry.bindColor(inherited));
                }
                return value;
            } finally {
                inheritedColor = parentColor;
            }
        }

        private static boolean currentColor(String css) {
            return css != null && css.trim().equalsIgnoreCase("currentColor");
        }

        private MathBox boxValue(Node node, MathLayoutContext context) {
            try {
                switch (node.kind) {
                    case ROW:
                        return row(node, context);
                    case FONT:
                        return box(node.first, fontContext(node, context));
                    case STYLE:
                        return node.first == null
                                ? new MathBox()
                                : box(node.first, context.declaredStyle(style(node.value)));
                    case ARRAY:
                        return MathArrayLayout.layout(this, node, context);
                    case ACCENT:
                    case OVERUNDER:
                    case XARROW:
                    case HORIZBRACE:
                    case COLOR:
                    case SIZE:
                    case CLASSIFY:
                    case PHANTOM:
                    case BOX:
                    case RAISE:
                    case RULE:
                    case MATHCHOICE:
                    case VCENTER:
                    case LAP:
                    case SMASH:
                    case ENCLOSE:
                    case PMB:
                    case HTMLMATHML:
                    case VERB:
                    case UNSUPPORTED:
                    case TAG:
                    case LINEBREAK:
                    case INTERNAL:
                        return MathDecorations.layout(this, node, context);
                    case DELIMITER:
                        return fixedDelimiter(node, context);
                    case MIDDLE:
                        return middleDelimiter(node, context);
                    case SPACE:
                        return spaceNode(node, context);
                    case EMBED:
                        return embed(node, context);
                    case TEXT:
                        return textNode(node, context);
                    case GLYPH:
                        return glyphNode(node, context);
                    case OPERATOR:
                        return operator(node, context);
                    case SCRIPTS:
                        return scripts(node, context);
                    case FRAC:
                    case GENFRAC:
                        return fraction(node, context);
                    case BINOM:
                        return binomial(node, context);
                    case DELIMITED:
                        return row(node.second, context, node);
                    case ROOT:
                        return radical(node, context);
                    default:
                        throw new LayoutFailure("layout", node, "数学节点布局尚未完成: " + node.kind);
                }
            } catch (UnsupportedOperationException error) {
                // 声明的字体能力缺口整式降级，程序错误不吞掉。
                throw new LayoutFailure("font", node, error.getMessage());
            }
        }

        /** 节点分派只选择领域阶段；尺寸、文本与符号的局部规则由各自构建步骤承担。 */
        private MathBox fixedDelimiter(Node node, MathLayoutContext context) {
            Atom group=node.payload instanceof MathContent.DelimiterSpec
                    ?atom(((MathContent.DelimiterSpec)node.payload).atomGroup):Atom.ORD;
            return delimiter(node,context,fixedDelimiterTarget(node,context),group);
        }
        private MathBox middleDelimiter(Node node, MathLayoutContext context) {
            // fixed middle先构造size1；普通wrapper不由外left重新定高。
            MathLayoutContext sized=context.style(MathStyle.TEXT);
            return delimiter(node,sized,fixedDelimiterTarget(1,sized),Atom.NONE);
        }
        private MathBox spaceNode(Node node, MathLayoutContext context) {
            font(context,node);
            MathBox value=new MathBox();
            MathContent.SpaceSpec spacing=node.payload instanceof MathContent.SpaceSpec
                    ?(MathContent.SpaceSpec)node.payload:null;
            value.width=spacing!=null&&spacing.dimension!=null
                    ?dimension(spacing.dimension,context,node):em(context)*space(node.value);
            value.atom=Atom.NONE;
            return value;
        }
        private MathBox textNode(Node node, MathLayoutContext context) {
            font(context,node);
            if(node.first!=null)return box(node.first,fontContext(node,context).textMode(true));
            return glyph(text.layout(node.value,ordinaryTextStyle(context),Integer.MAX_VALUE,TextPolicy.NO_WRAP),node,context,false);
        }
        private MathBox glyphNode(Node node, MathLayoutContext context) {
            MathContent.GlyphSpec spec=node.payload instanceof MathContent.GlyphSpec
                    ?(MathContent.GlyphSpec)node.payload:null;
            if(node.value.equals("")&&spec!=null&&spec.originalToken.equals("\\@not"))
                return MathDecorations.negation(this,node,context);
            String intrinsic=intrinsicAlphabet(spec);
            if(spec!=null&&spec.mode.equals("text")&&intrinsic==null)
                return glyph(text.layout(node.value,ordinaryTextStyle(context),Integer.MAX_VALUE,TextPolicy.NO_WRAP),node,context,false);
            String alphabet=intrinsic!=null?intrinsic:context.alphabet;
            if(spec!=null&&intrinsic==null&&context.alphabet.equals("mathnormal")&&!spec.atomGroup.equals("mathord"))alphabet="mathrm";
            MathLayoutContext glyphContext=intrinsic==null?context:intrinsicContext(context);
            String valueText=intrinsic!=null&&spec.styledFont!=null&&spec.replacement!=null
                    ?spec.replacement:intrinsic!=null?spec.originalToken:node.value;
            MathBox value=glyph(font(glyphContext,node).text(alphabet(semanticSymbol(valueText),alphabet)),node,glyphContext,true);
            value.atom=spec==null?classify(node.value):atom(spec.atomGroup);
            return value;
        }

        float dimension(MathDimension value, MathLayoutContext context, Node node) {
            if (value == null || value.blank) return 0;
            double scale;
            String unit = value.unit;
            if (unit.equals("em")) scale = context.sizeEm;
            else if (unit.equals("ex")) scale = xHeight(context.style(MathStyle.TEXT), node);
            else if (unit.equals("mu")) scale = context.em / 18;
            else {
                double points;
                if (unit.equals("pt")) points = 1;
                else if (unit.equals("mm")) points = 7227.0 / 2540;
                else if (unit.equals("cm")) points = 7227.0 / 254;
                else if (unit.equals("in")) points = 72.27;
                else if (unit.equals("bp") || unit.equals("px")) points = 803.0 / 800;
                else if (unit.equals("pc")) points = 12;
                else if (unit.equals("dd")) points = 1238.0 / 1157;
                else if (unit.equals("cc")) points = 14856.0 / 1157;
                else if (unit.equals("nd")) points = 685.0 / 642;
                else if (unit.equals("nc")) points = 1370.0 / 107;
                else if (unit.equals("sp")) points = 1.0 / 65536;
                else throw new LayoutFailure("layout", node, "未知数学单位: " + unit);
                scale = base.logicalEm / 10 * points;
            }
            float result = (float) (value.number * scale);
            if (!Float.isFinite(result)) throw new LayoutFailure("layout", node, "数学尺寸超出有限范围");
            return result;
        }

        float xHeight(MathLayoutContext context, Node node) {
            TextLayout layout = font(context, node).text("x");
            float top = Float.POSITIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
            for (TextLayout.Line line : layout.lines())
                for (TextLayout.Glyph glyph : line.glyphs()) {
                    top = Math.min(top, glyph.y + glyph.inkBounds.top);
                    bottom = Math.max(bottom, glyph.y + glyph.inkBounds.bottom);
                }
            return bottom - top;
        }

        int consumeEquationNumber(Node node) {
            if (nextTag == Integer.MAX_VALUE) throw new LayoutFailure("layout", node, "公式编号超出整数范围");
            return nextTag++;
        }

        MathBox tagNumber(Node node, MathLayoutContext context, int number) {
            return glyph(
                    text.layout(
                            "(" + number + ")",
                            ordinaryTextStyle(context.style(MathStyle.TEXT)),
                            Integer.MAX_VALUE,
                            TextPolicy.NO_WRAP),
                    node,
                    context,
                    false);
        }

        float fixedDelimiterTarget(Node node, MathLayoutContext context) {
            MathContent.DelimiterSpec spec = (MathContent.DelimiterSpec) node.payload;
            return spec == null ? context.em : fixedDelimiterTarget(spec.size, context);
        }

        float fixedDelimiterTarget(int size, MathLayoutContext context) {
            return (size == 1 ? 1.2f : size == 2 ? 1.8f : size == 3 ? 2.4f : 3) * context.em;
        }

        static Atom atom(String group) {
            if (group == null) return Atom.ORD;
            if (group.equals("mathord") || group.equals("textord") || group.equals("ord"))
                return Atom.ORD;
            if (group.equals("bin")) return Atom.BIN;
            if (group.equals("rel")) return Atom.REL;
            if (group.equals("open")) return Atom.OPEN;
            if (group.equals("close")) return Atom.CLOSE;
            if (group.equals("punct")) return Atom.PUNCT;
            if (group.equals("op")) return Atom.OP;
            if (group.equals("inner")) return Atom.INNER;
            return Atom.ORD;
        }

        MathBox glyph(
                TextLayout layout, Node node, MathLayoutContext context, boolean mathematical) {
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
            MathBox value = new MathBox();
            value.width = right - left;
            value.ascent = -top;
            value.descent = bottom;
            if (!ink && !node.value.isEmpty()) {
                value.ascent = baseline;
                value.descent = Math.max(0, layout.advanceHeight - baseline);
            }
            value.entry(MathLayout.Entry.text(
                    new MathLayout.TextPiece(layout, -left, -baseline, node.start, node.end))
                    .bindColor(inheritedColor && node.kind != MathContent.Kind.UNSUPPORTED));
            if (mathematical) value.italic = font(context, node).italicCorrection(layout);
            return value;
        }

        MathBox embed(Node node, MathLayoutContext context) {
            MathEmbedMetrics metrics = embeds.get(node.value);
            if (metrics == null) throw new LayoutFailure("layout", node, "未绑定嵌入标识: " + node.value);
            if (!metrics.valid())
                throw new LayoutFailure("layout", node, "嵌入尺寸必须有限、正值且基线在盒内: " + node.value);
            font(context, node);
            float em = em(context);
            MathBox value = new MathBox();
            value.width = metrics.widthEm * em;
            value.ascent = metrics.baselineEm * em;
            value.descent = (metrics.heightEm - metrics.baselineEm) * em;
            if (!Float.isFinite(value.width) || !Float.isFinite(value.ascent + value.descent))
                throw new LayoutFailure("layout", node, "嵌入按当前字号换算后超出有限范围: " + node.value);
            value.embed(
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

        String intrinsicAlphabet(MathContent.GlyphSpec spec) {
            if (spec == null) return null;
            if (spec.styledFont != null) return spec.styledFont;
            // Unicode letterlike holes 是数学字母的正式标量；typed replacement 的 ASCII 仅用于配合字族，不抹去原样式。
            if (spec.originalToken.codePointCount(0, spec.originalToken.length()) == 1
                    && "ℎℬℰℱℋℐℒℳℛℯℊℴℭℌℑℜℨℂℍℕℙℚℝℤ".contains(spec.originalToken)) return "mathrm";
            return null;
        }

        MathLayoutContext intrinsicContext(MathLayoutContext context) {
            TextStyle scope = context.textStyle;
            // 宽数学字母自带样式，沿根数学候选和当前颜色/字号；正文别名不覆盖它，显式根候选仍完整保留。
            return context.textStyle(
                    new TextStyle(
                            base.font,
                            scope.logicalEm,
                            scope.color,
                            scope.candidates(),
                            scope.effects,
                            scope.decoration,
                            FontStyle.NORMAL));
        }

        boolean textGlyph(Node node) {
            return node.kind == MathContent.Kind.GLYPH
                    && node.payload instanceof MathContent.GlyphSpec
                    && ((MathContent.GlyphSpec) node.payload).mode.equals("text")
                    && intrinsicAlphabet((MathContent.GlyphSpec) node.payload) == null;
        }

        boolean textRun(Node node, MathLayoutContext context) {
            return textGlyph(node)
                    || context.textMode
                            && node.kind == MathContent.Kind.SPACE
                            && (node.value.equals(" ") || node.value.equals(" "))
                            && node.payload instanceof MathContent.SpaceSpec
                            && ((MathContent.SpaceSpec) node.payload).dimension == null;
        }

        /** 行归约的实际项：绘制盒、该项的样式作用域和来源不可分开移动。 */
        private static final class RowItem {
            final MathBox box;
            final MathLayoutContext context;
            final Node source;

            RowItem(MathBox box, MathLayoutContext context, Node source) {
                this.box = box;
                this.context = context;
                this.source = source;
            }

            RowItem withBox(MathBox value) {
                return new RowItem(value, context, source);
            }
        }

        MathBox row(Node node, MathLayoutContext initial) {
            return row(node, initial, null);
        }

        MathBox row(Node node, MathLayoutContext initial, Node fence) {
            List<RowItem> items = new ArrayList<RowItem>();
            boolean multiline = node == rootBody && rootLineBreak(node.children);
            collectRow(node.children, initial, fence != null, multiline, items);
            if (fence != null) encloseRow(items, initial, fence);
            normalizeBins(items, multiline);
            if (multiline) return lines(node, initial, items);
            MathBox value = horizontal(items, 0, items.size());
            value.atom = fence == null ? Atom.ORD : Atom.INNER;
            if (fence != null) value.italic = 0;
            return value;
        }

        private void encloseRow(List<RowItem> items, MathLayoutContext context, Node fence) {
            MathBox content = new MathBox();
            for (RowItem item : items) content.add(item.box, 0, 0);
            float target = delimiterTarget(content, context, fence);
            for (int i = 0; i < items.size(); i++) {
                RowItem item = items.get(i);
                if (item.source.kind == MathContent.Kind.MIDDLE)
                    items.set(i, item.withBox(delimiter(item.source, item.context, target, Atom.REL)));
            }
            items.add(
                    0,
                    new RowItem(
                            delimiter(fence.first, context, target, Atom.OPEN), context, fence.first));
            items.add(
                    new RowItem(
                            delimiter(fence.third, context, target, Atom.CLOSE), context, fence.third));
        }

        private void normalizeBins(List<RowItem> items, boolean multiline) {
            // TeX BIN 归一化忽略显式 kern，按实际原子序列判定。
            Atom previous = Atom.NONE;
            for (int i = 0; i < items.size(); i++) {
                MathBox current = items.get(i).box;
                if (multiline && items.get(i).source.kind == MathContent.Kind.LINEBREAK) {
                    previous = Atom.NONE;
                    continue;
                }
                if (current.atom == Atom.NONE) continue;
                Atom next = Atom.NONE;
                for (int j = i + 1; j < items.size(); j++)
                    if (items.get(j).box.atom != Atom.NONE) {
                        next = items.get(j).box.atom;
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
        }

        private MathBox horizontal(List<RowItem> items, int from, int end) {
            MathBox value = new MathBox();
            float cursor = 0, left = 0, right = 0;
            Atom previous = Atom.NONE;
            for (int i = from; i < end; i++) {
                RowItem item = items.get(i);
                MathBox current = item.box;
                MathLayoutContext currentContext = item.context;
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

        private boolean rootLineBreak(List<Node> children) {
            for (Node child : children) {
                if (child.kind == MathContent.Kind.LINEBREAK) return true;
                if ((child.kind == MathContent.Kind.STYLE
                                || child.kind == MathContent.Kind.HTMLMATHML
                                || child.kind == MathContent.Kind.COLOR
                                || child.kind == MathContent.Kind.SIZE)
                        && child.first != null
                        && child.first.kind == MathContent.Kind.ROW
                        && rootLineBreak(child.first.children)) return true;
            }
            return false;
        }

        private MathBox lines(Node node, MathLayoutContext context, List<RowItem> items) {
            // CSS只把katex-html直接newline作为零高block；连续空block不创建新空行，margin按正最大/负最小折叠。
            TextLayout.Line strut = font(context, node).text("M").lines().get(0);
            float leading = Math.max(0, strut.lineHeight - strut.ascent - strut.descent) / 2;
            MathBox result = new MathBox();
            float baseline = 0, previousDepth = 0, positive = 0, negative = 0;
            boolean first = true;
            int from = 0;
            for (int end = 0; end <= items.size(); end++) {
                if (end < items.size() && items.get(end).source.kind != MathContent.Kind.LINEBREAK)
                    continue;
                if (end > from) {
                    MathBox line = horizontal(items, from, end);
                    float ascent = Math.max(line.ascent, strut.ascent + leading),
                            descent = Math.max(line.descent, strut.descent + leading);
                    for (MathLayout.TextPiece piece : line.pieces())
                        for (TextLayout.Line nativeLine : piece.text.lines()) {
                            float nativeLeading =
                                    Math.max(
                                                    0,
                                                    nativeLine.lineHeight
                                                            - nativeLine.ascent
                                                            - nativeLine.descent)
                                            / 2;
                            float localBaseline = piece.y + nativeLine.baseline;
                            ascent =
                                    Math.max(
                                            ascent,
                                            nativeLine.ascent + nativeLeading - localBaseline);
                            descent =
                                    Math.max(
                                            descent,
                                            nativeLine.descent + nativeLeading + localBaseline);
                        }
                    if (!first) baseline += previousDepth + ascent + positive + negative;
                    result.add(line, 0, baseline);
                    result.width = Math.max(result.width, line.width);
                    first = false;
                    previousDepth = descent;
                    positive = negative = 0;
                }
                if (end < items.size()) {
                    RowItem item = items.get(end);
                    MathContent.SpaceSpec spec = (MathContent.SpaceSpec) item.source.payload;
                    float gap =
                            spec.dimension == null
                                    ? 0
                                    : dimension(spec.dimension, item.context, item.source);
                    positive = Math.max(positive, gap);
                    negative = Math.min(negative, gap);
                }
                from = end + 1;
            }
            return result;
        }

        // STYLE body 与 HTMLMATHML 的显示分支是透明 fragment；普通 ROW/分组/CLASSIFY 保留原子边界。
        private void collectRow(
                List<Node> children,
                MathLayoutContext initial,
                boolean fenced,
                boolean rootLines,
                List<RowItem> items) {
            MathLayoutContext context = initial;
            for (int index = 0; index < children.size(); index++) {
                Node child = children.get(index);
                if (rootLines
                        && (child.kind == MathContent.Kind.COLOR
                                || child.kind == MathContent.Kind.SIZE)
                        && child.first != null
                        && child.first.kind == MathContent.Kind.ROW) {
                    boolean parentColor = inheritedColor;
                    if (child.kind == MathContent.Kind.COLOR
                            && !currentColor(((MathContent.ColorSpec) child.payload).css))
                        inheritedColor = false;
                    try {
                        collectRow(child.first.children, MathDecorations.scope(this, child, context),
                                fenced, true, items);
                    } finally {
                        inheritedColor = parentColor;
                    }
                    continue;
                }
                if (textRun(child, context)) {
                    StringBuilder run = new StringBuilder(child.value);
                    // 宏参数可与模板调用点反序；合并run须覆盖全部来源，不能取首尾位置。
                    int start = child.start, end = child.end;
                    while (index + 1 < children.size()
                            && textRun(children.get(index + 1), context)) {
                        Node next = children.get(++index);
                        run.append(next.value);
                        start = Math.min(start, next.start);
                        end = Math.max(end, next.end);
                    }
                    Node source =
                            new Node(
                                    MathContent.Kind.TEXT,
                                    start,
                                    end,
                                    run.toString(),
                                    null,
                                    null,
                                    null,
                                    null);
                    MathBox value =
                            glyph(
                                    text.layout(
                                            run.toString(),
                                            ordinaryTextStyle(context),
                                            Integer.MAX_VALUE,
                                            TextPolicy.NO_WRAP),
                                    source,
                                    context,
                                    false);
                    items.add(new RowItem(value, context, source));
                    continue;
                }
                if (child.kind == MathContent.Kind.STYLE && child.first == null) {
                    context = context.declaredStyle(style(child.value));
                    continue;
                }
                if (child.kind == MathContent.Kind.STYLE
                        && child.first != null
                        && child.first.kind == MathContent.Kind.ROW) {
                    collectRow(
                            child.first.children,
                            context.declaredStyle(style(child.value)),
                            fenced,
                            rootLines,
                            items);
                    continue;
                }
                if (child.kind == MathContent.Kind.HTMLMATHML
                        && child.first != null
                        && child.first.kind == MathContent.Kind.ROW) {
                    collectRow(
                            child.first.children,
                            context,
                            fenced,
                            rootLines,
                            items);
                    continue;
                }
                // 只有本层left直接可见的middle占位并共用target；nested wrapper内size1真实墨迹参与测高。
                MathBox value;
                if (fenced && child.kind == MathContent.Kind.MIDDLE) {
                    value = new MathBox();
                    value.atom = Atom.REL;
                } else value = box(child, context);
                items.add(new RowItem(value, context, child));
            }
        }

        MathLayoutContext fontContext(Node node, MathLayoutContext context) {
            MathContent.FontSpec spec =
                    node.payload instanceof MathContent.FontSpec
                            ? (MathContent.FontSpec) node.payload
                            : null;
            String name = spec == null ? node.value : spec.font;
            if (spec != null && spec.mode.equals("text")) {
                TextStyle scope = ordinaryTextStyle(context);
                FontStyle old = scope.fontStyle;
                int weight =
                        name.contains("bf") || name.equals("bold")
                                ? 700
                                : name.contains("md") ? 400 : old.weight;
                FontStyle.Posture posture =
                        name.contains("it") || name.equals("italic")
                                ? FontStyle.Posture.ITALIC
                                : name.contains("up") || name.equals("normal")
                                        ? FontStyle.Posture.NORMAL
                                        : old.posture;
                if (name.equals("emph"))
                    posture =
                            old.posture == FontStyle.Posture.ITALIC
                                    ? FontStyle.Posture.NORMAL
                                    : FontStyle.Posture.ITALIC;
                String family =
                        name.equals("textrm") || name.equals("textnormal")
                                ? "math-text-roman"
                                : name.equals("textsf")
                                        ? "math-text-sans"
                                        : name.equals("texttt") ? "math-text-mono" : scope.font;
                return context.textStyle(
                        new TextStyle(
                                family,
                                scope.logicalEm,
                                scope.color,
                                scope.candidates(),
                                scope.effects,
                                scope.decoration,
                                new FontStyle(weight, posture)));
            }
            return context.alphabet(name);
        }

        MathBox operator(Node node, MathLayoutContext context) {
            String name = node.value;
            int cp =
                    name.equals("int")
                            ? 0x222b
                            : name.equals("sum") ? 0x2211 : name.equals("prod") ? 0x220f : 0;
            MathContent.OperatorSpec spec =
                    node.payload instanceof MathContent.OperatorSpec
                            ? (MathContent.OperatorSpec) node.payload
                            : null;
            if (spec != null && spec.symbol && cp == 0 && !name.isEmpty()) cp = name.codePointAt(0);
            MathFont font = font(context, node);
            TextLayout layout =
                    cp == 0
                            ? font.text(
                                    name.startsWith("@") || name.startsWith("*")
                                            ? name.substring(1)
                                            : name)
                            // fixed op 的 smallint 没有 large successor；用 typed 原控制词区分同为 U222B 的请求。
                            : context.style == MathStyle.DISPLAY
                                            && (spec == null
                                                    || !"\\smallint".equals(spec.originalToken))
                                    ? font.displayOperator(
                                            cp, font.constant(DISPLAY_OPERATOR_MIN_HEIGHT))
                                    : font.text(new String(Character.toChars(cp)));
            MathBox value = glyph(layout, node, context, true);
            value.atom = Atom.OP;
            if (cp != 0) {
                float center = (value.descent - value.ascent) / 2;
                float delta = -c(context, node, AXIS_HEIGHT) - center;
                value = value.shifted(0, delta, value.width);
                value.atom = Atom.OP;
            }
            return value;
        }

        MathBox scripts(Node node, MathLayoutContext context) {
            MathBox base = box(node.first, context);
            MathBox upper =
                    node.second == null
                            ? null
                            : box(node.second, context.style(context.style.script()));
            MathBox lower =
                    node.third == null
                            ? null
                            : box(node.third, context.style(context.style.script()).cramped());
            MathContent.OperatorSpec operator =
                    node.first.payload instanceof MathContent.OperatorSpec
                            ? (MathContent.OperatorSpec) node.first.payload
                            : null;
            boolean limits =
                    node.value.equals("limits")
                            || node.value.equals("default")
                                    && context.style == MathStyle.DISPLAY
                                    && node.first.kind == MathContent.Kind.OPERATOR
                                    && (operator != null
                                            ? operator.defaultLimits
                                            : node.first.value.equals("sum")
                                                    || node.first.value.equals("prod")
                                                    || node.first.value.equals("lim")
                                                    || node.first.value.startsWith("*"));
            if (limits) return limits(node, context, base, upper, lower);
            MathContent.DecorationSpec brace =
                    node.first.kind == MathContent.Kind.HORIZBRACE
                                    && node.first.payload instanceof MathContent.DecorationSpec
                            ? (MathContent.DecorationSpec) node.first.payload
                            : null;
            boolean centeredUpper = brace != null && !brace.below;
            boolean centeredLower = brace != null && brace.below;
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
            if (centeredUpper && upper != null)
                up =
                        Math.max(
                                up,
                                base.ascent
                                        + Math.max(
                                                c(context, node, UPPER_LIMIT_BASELINE_RISE_MIN),
                                                c(context, node, UPPER_LIMIT_GAP_MIN)
                                                        + upper.descent));
            if (centeredLower && lower != null)
                down =
                        Math.max(
                                down,
                                base.descent
                                        + Math.max(
                                                c(context, node, LOWER_LIMIT_BASELINE_DROP_MIN),
                                                c(context, node, LOWER_LIMIT_GAP_MIN)
                                                        + lower.ascent));
            float upperX =
                    upper == null
                            ? 0
                            : centeredUpper
                                    ? (base.width - upper.width) / 2
                                    : base.width + base.italic;
            float lowerX =
                    lower == null ? 0 : centeredLower ? (base.width - lower.width) / 2 : base.width;
            float left = Math.min(0, Math.min(upperX, lowerX));
            MathBox value = new MathBox();
            value.add(base, -left, 0);
            float width = base.width - left;
            if (upper != null) {
                value.add(upper, upperX - left, -up);
                width = Math.max(width, upperX - left + upper.width);
            }
            if (lower != null) {
                value.add(lower, lowerX - left, down);
                width = Math.max(width, lowerX - left + lower.width);
            }
            value.width =
                    width
                            + (upper != null || lower != null
                                    ? c(context, node, SPACE_AFTER_SCRIPT)
                                    : 0);
            value.atom = base.atom;
            return value;
        }

        MathBox limits(
                Node node, MathLayoutContext context, MathBox base, MathBox upper, MathBox lower) {
            float upperX = upper == null ? 0 : (base.width - upper.width) / 2 + base.italic / 2;
            float lowerX = lower == null ? 0 : (base.width - lower.width) / 2 - base.italic / 2;
            float left = Math.min(0, Math.min(upperX, lowerX));
            float right =
                    Math.max(
                            base.width,
                            Math.max(
                                    upper == null ? 0 : upperX + upper.width,
                                    lower == null ? 0 : lowerX + lower.width));
            MathBox value = new MathBox();
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

        MathBox fraction(Node node, MathLayoutContext inherited) {
            MathLayoutContext context = inherited;
            MathContent.FractionSpec spec =
                    node.payload instanceof MathContent.FractionSpec
                            ? (MathContent.FractionSpec) node.payload
                            : null;
            if (spec != null
                    && spec.style != null
                    && !spec.style.isEmpty()
                    && !spec.style.equals("auto")) context = context.style(style(spec.style));
            if (node.value.equals("dfrac")) context = context.style(MathStyle.DISPLAY);
            else if (node.value.equals("tfrac")) context = context.style(MathStyle.TEXT);
            if (spec != null && !spec.hasBar) return stackFraction(node, context, spec);
            MathBox numerator = box(node.first, context.style(context.style.fraction()));
            MathBox denominator =
                    box(node.second, context.style(context.style.fraction()).cramped());
            if (spec != null && spec.continued) {
                numerator.ascent = Math.max(numerator.ascent, .85f * context.sizeEm);
                numerator.descent = Math.max(numerator.descent, .35f * context.sizeEm);
            }
            boolean display = context.style == MathStyle.DISPLAY;
            float axis = c(context, node, AXIS_HEIGHT);
            float rule =
                    spec != null && spec.thickness != null && !spec.thickness.blank
                            ? dimension(spec.thickness, context, node)
                            : c(context, node, FRACTION_RULE_THICKNESS);
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
            MathBox value = new MathBox();
            value.add(numerator, fractionX(spec, width, numerator.width), -up);
            value.add(denominator, fractionX(spec, width, denominator.width), down);
            value.rule(
                    0,
                    -axis - rule / 2,
                    width,
                    rule,
                    context.textStyle.color,
                    node.start,
                    node.end);
            value.width = width;
            value.atom = Atom.INNER;
            return fractionFences(value, node, context, spec);
        }

        float fractionX(MathContent.FractionSpec spec, float width, float childWidth) {
            return spec != null && "l".equals(spec.align)
                    ? 0
                    : spec != null && "r".equals(spec.align)
                            ? width - childWidth
                            : (width - childWidth) / 2;
        }

        MathBox stackFraction(Node node, MathLayoutContext context, MathContent.FractionSpec spec) {
            MathBox top = box(node.first, context.style(context.style.fraction())),
                    bottom = box(node.second, context.style(context.style.fraction()).cramped());
            boolean display = context.style == MathStyle.DISPLAY;
            float up =
                    c(
                            context,
                            node,
                            display ? STACK_TOP_DISPLAY_STYLE_SHIFT_UP : STACK_TOP_SHIFT_UP);
            float down =
                    c(
                            context,
                            node,
                            display
                                    ? STACK_BOTTOM_DISPLAY_STYLE_SHIFT_DOWN
                                    : STACK_BOTTOM_SHIFT_DOWN);
            float deficit =
                    c(context, node, display ? STACK_DISPLAY_STYLE_GAP_MIN : STACK_GAP_MIN)
                            - (up + down - top.descent - bottom.ascent);
            if (deficit > 0) {
                up += deficit / 2;
                down += deficit / 2;
            }
            MathBox result = new MathBox();
            result.width = Math.max(top.width, bottom.width);
            result.add(top, fractionX(spec, result.width, top.width), -up);
            result.add(bottom, fractionX(spec, result.width, bottom.width), down);
            result.atom = Atom.INNER;
            return fractionFences(result, node, context, spec);
        }

        MathBox fractionFences(
                MathBox body, Node node, MathLayoutContext context, MathContent.FractionSpec spec) {
            if (spec == null || (spec.leftDelimiter == null && spec.rightDelimiter == null))
                return body;
            float axis = c(context, node, AXIS_HEIGHT),
                    target = 2 * Math.max(body.ascent - axis, body.descent + axis);
            MathBox left = fractionFence(node, context, spec.leftDelimiter, target, Atom.OPEN),
                    right = fractionFence(node, context, spec.rightDelimiter, target, Atom.CLOSE);
            MathBox result = new MathBox();
            result.add(left, 0, 0);
            result.add(body, left.width, 0);
            result.add(right, left.width + body.width, 0);
            result.width = left.width + body.width + right.width;
            result.atom = Atom.INNER;
            return result;
        }

        MathBox fractionFence(
                Node node, MathLayoutContext context, String delimiter, float target, Atom atom) {
            if (delimiter == null || delimiter.isEmpty()) return new MathBox();
            return delimiter(
                    new Node(
                            MathContent.Kind.DELIMITER,
                            node.start,
                            node.end,
                            delimiter,
                            null,
                            null,
                            null,
                            null),
                    context,
                    target,
                    atom);
        }

        /** KaTeX0.16.21 makeLeftRightDelim：短缺量使用基准em，脚本只缩放axis与内容。 */
        float delimiterTarget(MathBox content, MathLayoutContext context, Node node) {
            float axis = c(context, node, AXIS_HEIGHT);
            float distance = Math.max(content.ascent - axis, content.descent + axis);
            float target = Math.max(distance / 500f * 901f, 2 * distance - 0.5f * base.logicalEm);
            if (!Float.isFinite(target)) throw new LayoutFailure("layout", node, "定界目标超出有限范围");
            return target;
        }

        MathBox delimiter(Node node, MathLayoutContext context, float target, Atom atom) {
            if (node.value.equals(".")) {
                MathBox invisible = new MathBox();
                // 固定KaTeX makeNullDelimiter的baseSizing重置到基准字号。
                invisible.width = 0.12f * base.logicalEm;
                invisible.atom = atom;
                return invisible;
            }
            MathBox value =
                    glyph(
                            font(context, node).stretch(node.value.codePointAt(0), target),
                            node,
                            context,
                            true);
            // TextLayout的glyph.y已含其本地baseline；glyph()已转成数学基线坐标。
            // 不复用另一侧的height：左右完整变体及assembly可具有不同实际墨迹高度。
            // 普通数学盒包络含基线空白；定界符须按有符号的真实墨迹居中。
            // 字体局部原点移动到基线一侧时，也不能把空白计入墨迹中线。
            float top = Float.POSITIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
            for (MathLayout.TextPiece piece : value.pieces()) {
                for (TextLayout.Line line : piece.text.lines()) {
                    for (TextLayout.Glyph glyph : line.glyphs()) {
                        top = Math.min(top, piece.y + glyph.y + glyph.inkBounds.top);
                        bottom = Math.max(bottom, piece.y + glyph.y + glyph.inkBounds.bottom);
                    }
                }
            }
            float center = (top + bottom) / 2;
            // 先去掉字体原始基线空白；shifted()再包络公式基线与居中的墨迹。
            value.ascent = -top;
            value.descent = bottom;
            value = value.shifted(0, -c(context, node, AXIS_HEIGHT) - center, value.width);
            value.atom = atom;
            value.italic = 0;
            return value;
        }

        MathBox binomial(Node node, MathLayoutContext inherited) {
            MathLayoutContext context = inherited;
            MathContent.FractionSpec spec =
                    node.payload instanceof MathContent.FractionSpec
                            ? (MathContent.FractionSpec) node.payload
                            : null;
            if (spec != null
                    && spec.style != null
                    && !spec.style.isEmpty()
                    && !spec.style.equals("auto")) context = context.style(style(spec.style));
            MathBox top = box(node.first, context.style(context.style.fraction()));
            MathBox bottom = box(node.second, context.style(context.style.fraction()).cramped());
            boolean display = context.style == MathStyle.DISPLAY;
            float up =
                    c(
                            context,
                            node,
                            display ? STACK_TOP_DISPLAY_STYLE_SHIFT_UP : STACK_TOP_SHIFT_UP);
            float down =
                    c(
                            context,
                            node,
                            display
                                    ? STACK_BOTTOM_DISPLAY_STYLE_SHIFT_DOWN
                                    : STACK_BOTTOM_SHIFT_DOWN);
            float gap = c(context, node, display ? STACK_DISPLAY_STYLE_GAP_MIN : STACK_GAP_MIN);
            float deficit = gap - (up + down - top.descent - bottom.ascent);
            if (deficit > 0) {
                up += deficit / 2;
                down += deficit / 2;
            }
            float width = Math.max(top.width, bottom.width);
            MathBox stack = new MathBox();
            stack.add(top, (width - top.width) / 2, -up);
            stack.add(bottom, (width - bottom.width) / 2, down);
            stack.width = width;
            // binom圆括号须完整覆盖stack，而非left/right允许的TeX短缺量。
            float axis = c(context, node, AXIS_HEIGHT);
            float target = 2 * Math.max(stack.ascent - axis, stack.descent + axis);
            if (!Float.isFinite(target)) throw new LayoutFailure("layout", node, "二项式定界目标超出有限范围");
            Node leftNode =
                    new Node(
                            MathContent.Kind.DELIMITER,
                            node.start,
                            node.end,
                            "(",
                            null,
                            null,
                            null,
                            null);
            Node rightNode =
                    new Node(
                            MathContent.Kind.DELIMITER,
                            node.start,
                            node.end,
                            ")",
                            null,
                            null,
                            null,
                            null);
            MathBox left = delimiter(leftNode, context, target, Atom.OPEN);
            MathBox right = delimiter(rightNode, context, target, Atom.CLOSE);
            MathBox value = new MathBox();
            value.add(left, 0, 0);
            value.add(stack, left.width, 0);
            value.add(right, left.width + width, 0);
            value.width = left.width + width + right.width;
            value.atom = Atom.ORD;
            return value;
        }

        MathBox radical(Node node, MathLayoutContext context) {
            MathBox radicand = box(node.first, context.cramped());
            float gap =
                    c(
                            context,
                            node,
                            context.style == MathStyle.DISPLAY
                                    ? RADICAL_DISPLAY_STYLE_VERTICAL_GAP
                                    : RADICAL_VERTICAL_GAP);
            float rule = c(context, node, RADICAL_RULE_THICKNESS);
            float target = radicand.ascent + radicand.descent + gap + rule;
            TextLayout signLayout = font(context, node).stretch(0x221a, target);
            MathBox sign = glyph(signLayout, node, context, true);
            InkBounds outline = font(context, node).outlineBounds(signLayout);
            InkBounds bar = font(context, node).radicalBarBounds(signLayout);
            float contentX = sign.width;
            TextLayout extended =
                    font(context, node).extendRadicalBar(signLayout, contentX + radicand.width);
            MathBox paintedSign = extended == null ? sign : glyph(extended, node, context, true);
            // 根号横线连接用精确轮廓，不能把需求剔除的保守空白当作顶部或右端。
            // 未提供附加度量的既有 MathFont 保持原布局能力。
            float signTop =
                    bar.known
                            ? bar.top - signLayout.lines().get(0).baseline
                            : outline.known
                                    ? outline.top - signLayout.lines().get(0).baseline
                                    : -sign.ascent;
            float signRight = outline.known ? outline.right : sign.width;
            float extra = Math.max(0, sign.ascent + sign.descent - target);
            gap += extra / 2;
            float ruleTop = -radicand.ascent - gap - rule;
            float signY =
                    bar.known
                            ? -radicand.ascent
                                    - gap
                                    - (bar.bottom - signLayout.lines().get(0).baseline)
                            : ruleTop - signTop;
            MathBox degree =
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
            MathBox value = new MathBox();
            value.add(paintedSign, signX, signY);
            value.add(radicand, signX + contentX, 0);
            if (extended == null && outline.known)
                value.stroke(
                        signX + signRight,
                        ruleTop + rule / 2,
                        signX + sign.width + radicand.width,
                        ruleTop + rule / 2,
                        rule,
                        context.textStyle.color,
                        node.start,
                        node.end);
            else if (extended == null)
                value.rule(
                        signX + sign.width,
                        ruleTop,
                        radicand.width,
                        rule,
                        context.textStyle.color,
                        node.start,
                        node.end);
            value.ascent =
                    Math.max(value.ascent, -ruleTop + c(context, node, RADICAL_EXTRA_ASCENDER));
            if (degree != null) value.add(degree, degreeX, degreeY);
            value.width =
                    Math.max(
                            Math.max(
                                    signX + sign.width + radicand.width, signX + paintedSign.width),
                            degree == null ? 0 : degreeX + degree.width);
            return value;
        }
    }

    static MathStyle style(String name) {
        if (name.equals("displaystyle") || name.equals("display")) return MathStyle.DISPLAY;
        if (name.equals("textstyle") || name.equals("text")) return MathStyle.TEXT;
        if (name.equals("scriptstyle") || name.equals("script")) return MathStyle.SCRIPT;
        return MathStyle.SCRIPT_SCRIPT;
    }

    private static float space(String name) {
        if (name.equals(",")) return 3f / 18;
        if (name.equals(":")) return 4f / 18;
        if (name.equals(";")) return 5f / 18;
        if (name.equals("!")) return -3f / 18;
        if (name.equals("quad")) return 1;
        if (name.equals("\\nobreak") || name.equals("\\allowbreak")) return 0;
        if (name.equals(" ") || name.equals(" ")) return .33333334f;
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

    // KaTeX 私用槽位只在 glyph 请求处转为正式 Unicode 语义，typed catalog/source 保持原样。
    // VS1 六组见 Unicode17 StandardizedVariants；STIX cmap14 有各自非默认 glyph，仍经同一字体整形链。
    private static String semanticSymbol(String value) {
        if (value.length() != 1) return value;
        switch (value.charAt(0)) {
            case 0xe00c:
                return "≨︀";
            case 0xe00d:
                return "≩︀";
            case 0xe01a:
                return "⊊︀";
            case 0xe01b:
                return "⊋︀";
            case 0xe017:
                return "⫋︀";
            case 0xe019:
                return "⫌︀";
            case 0xe010:
                return "⩽̸";
            case 0xe00f:
                return "⩾̸";
            case 0xe011:
                return "≦̸";
            case 0xe00e:
                return "≧̸";
            case 0xe016:
                return "⫅̸";
            case 0xe018:
                return "⫆̸";
            case 0xe006:
                return "∤";
            case 0xe007:
                return "∦";
            case 0xe131:
                return "ı";
            case 0xe237:
                return "ȷ";
            default:
                return value;
        }
    }

    private static String alphabet(String value, String alphabet) {
        int upper = 0, lower = 0, digit = 0, greekUpper = 0, greekLower = 0;
        boolean normal = alphabet.equals("mathnormal") || alphabet.equals("mathit");
        if (normal) {
            upper = 0x1d434;
            lower = 0x1d44e;
            greekLower = 0x1d6fc;
            if (alphabet.equals("mathit")) greekUpper = 0x1d6e2;
        } else if (alphabet.equals("mathbf")) {
            upper = 0x1d400;
            lower = 0x1d41a;
            digit = 0x1d7ce;
            greekUpper = 0x1d6a8;
            greekLower = 0x1d6c2;
        } else if (alphabet.equals("boldsymbol") || alphabet.equals("mathbfit")) {
            upper = 0x1d468;
            lower = 0x1d482;
            digit = 0x1d7ce;
            greekUpper = 0x1d71c;
            greekLower = 0x1d736;
        } else if (alphabet.equals("mathbb")) {
            upper = 0x1d538;
            lower = 0x1d552;
            digit = 0x1d7d8;
        } else if (alphabet.equals("mathcal") || alphabet.equals("mathscr")) {
            upper = 0x1d49c;
            if (alphabet.equals("mathscr")) lower = 0x1d4b6;
        } else if (alphabet.equals("mathfrak")) {
            upper = 0x1d504;
            lower = 0x1d51e;
        } else if (alphabet.equals("mathsf")) {
            upper = 0x1d5a0;
            lower = 0x1d5ba;
            digit = 0x1d7e2;
        } else if (alphabet.equals("mathsfit")) {
            upper = 0x1d608;
            lower = 0x1d622;
            digit = 0x1d7e2;
        } else if (alphabet.equals("mathsfbf") || alphabet.equals("mathboldsf")) {
            upper = 0x1d5d4;
            lower = 0x1d5ee;
            digit = 0x1d7ec;
            greekUpper = 0x1d756;
            greekLower = 0x1d770;
        } else if (alphabet.equals("mathboldscr")) {
            upper = 0x1d4d0;
            lower = 0x1d4ea;
        } else if (alphabet.equals("mathboldfrak")) {
            upper = 0x1d56c;
            lower = 0x1d586;
        } else if (alphabet.equals("mathboldsfit")) {
            upper = 0x1d63c;
            lower = 0x1d656;
            digit = 0x1d7ec;
            greekUpper = 0x1d790;
            greekLower = 0x1d7aa;
        } else if (alphabet.equals("mathtt")) {
            upper = 0x1d670;
            lower = 0x1d68a;
            digit = 0x1d7f6;
        }
        String capitals = "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡϴΣΤΥΦΧΨΩ",
                small = "αβγδεζηθικλμνξοπρςστυφχψω",
                symbols = "∂ϵϑϰϕϱϖ";
        StringBuilder mapped = new StringBuilder();
        for (int offset = 0; offset < value.length(); ) {
            int cp = value.codePointAt(offset);
            offset += Character.charCount(cp);
            int original = cp;
            if (cp >= 'A' && cp <= 'Z' && upper != 0) cp = upper + cp - 'A';
            else if (cp >= 'a' && cp <= 'z' && lower != 0) cp = lower + cp - 'a';
            else if (cp >= '0' && cp <= '9' && digit != 0) cp = digit + cp - '0';
            else if (greekUpper != 0 && capitals.indexOf(cp) >= 0)
                cp = greekUpper + capitals.indexOf(cp);
            else if (greekLower != 0 && small.indexOf(cp) >= 0) cp = greekLower + small.indexOf(cp);
            else if (greekLower != 0 && symbols.indexOf(cp) >= 0)
                cp = greekLower + 25 + symbols.indexOf(cp);
            else if (greekUpper != 0 && cp == 0x2207) cp = greekUpper + 25;
            if (normal && original == 'h') cp = 0x210e;
            if (normal && original == 0x0131) cp = 0x1d6a4;
            if (normal && original == 0x0237) cp = 0x1d6a5;
            if (upper == 0x1d49c) cp = hole(original, cp, "BEFHILMR", "ℬℰℱℋℐℒℳℛ");
            if (lower == 0x1d4b6) cp = hole(original, cp, "ego", "ℯℊℴ");
            if (upper == 0x1d504) cp = hole(original, cp, "CHIRZ", "ℭℌℑℜℨ");
            if (upper == 0x1d538) cp = hole(original, cp, "CHNPQRZ", "ℂℍℕℙℚℝℤ");
            mapped.appendCodePoint(cp);
        }
        return mapped.toString();
    }

    private static int hole(int original, int mapped, String letters, String replacements) {
        int index = letters.indexOf(original);
        return index < 0 ? mapped : replacements.codePointAt(index);
    }
}
