package club.heiqi.uilib.ui.scene.text.math;

import static club.heiqi.uilib.ui.scene.text.math.MathContent.*;

import club.heiqi.uilib.ui.scene.text.math.MathLexer.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 只消费 Task3 的已展开 token；不执行宏、不共享全局、不接触字体或宿主。 */
final class MathParser {
    static final class Failure extends RuntimeException {
        final MathLayout.Diagnostic diagnostic;

        Failure(int start, int end, String message) {
            super(message);
            diagnostic = new MathLayout.Diagnostic("parse", start, end, message);
        }
    }

    // Unicode脚本属于语法归约，符号事实仍只查 MathSymbols 固定目录。
    private static final String SCRIPT_TOKENS =
            "₊₋₌₍₎₀₁₂₃₄₅₆₇₈₉ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓᵦᵧᵨᵩᵪ⁺⁻⁼⁽⁾⁰¹²³⁴⁵⁶⁷⁸⁹ᴬᴮᴰᴱᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᴿᵀᵁⱽᵂᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖʳˢᵗᵘᵛʷˣʸᶻᵝᵞᵟᵠᵡᶿ";
    private static final String SCRIPT_BASES =
            "+-=()0123456789aehijklmnoprstuvxβγρϕχ+-=()0123456789ABDEGHIJKLMNOPRTUVWabcdefghijklmnoprstuvwxyzβγδϕχθ";
    private static final String SUBSCRIPTS = "₊₋₌₍₎₀₁₂₃₄₅₆₇₈₉ₐₑₕᵢⱼₖₗₘₙₒₚᵣₛₜᵤᵥₓᵦᵧᵨᵩᵪ";
    private final List<Token> tokens;
    private final int sourceLength;
    private final Set<String> embeds;
    private final List<MathMacroExpander.RowTag> rowTags;
    private final List<MathMacroExpander.EnvironmentHeader> environmentHeaders;
    private int index, depth;
    private String mode = "math", stop = "";
    private boolean cell, left, singleCell;
    // middle继承活动left上下文；right仍只结束当前同组leftright row。
    private int leftDepth;

    MathParser(List<Token> tokens, int sourceLength) {
        this(
                tokens,
                sourceLength,
                new HashSet<String>(),
                Collections.<MathMacroExpander.RowTag>emptyList(),
                Collections.<MathMacroExpander.EnvironmentHeader>emptyList());
    }

    MathParser(
            List<Token> tokens,
            int sourceLength,
            List<MathMacroExpander.RowTag> rowTags,
            List<MathMacroExpander.EnvironmentHeader> headers) {
        this(tokens, sourceLength, new HashSet<String>(), rowTags, headers);
    }

    private MathParser(
            List<Token> tokens,
            int sourceLength,
            Set<String> embeds,
            List<MathMacroExpander.RowTag> rowTags,
            List<MathMacroExpander.EnvironmentHeader> headers) {
        this.tokens = tokens;
        this.sourceLength = sourceLength;
        this.embeds = embeds;
        this.rowTags = rowTags;
        this.environmentHeaders = headers;
    }

    Node parse(List<Token> tagTokens) {
        Node result = row("", false, false, false);
        if (index != tokens.size()) throw error(peek(), "多余的结构终点");
        if (!tagTokens.isEmpty()) {
            Node tag = subparse(tagTokens);
            result =
                    new Node(
                            Kind.TAG,
                            0,
                            sourceLength,
                            "",
                            null,
                            result,
                            tag,
                            null,
                            new TagSpec(true, false));
        }
        return new Node(
                result.kind,
                0,
                sourceLength,
                result.value,
                result.children,
                result.first,
                result.second,
                result.third,
                result.payload);
    }

    private Node subparse(List<Token> values) {
        MathParser p = new MathParser(values, sourceLength, embeds, rowTags, environmentHeaders);
        return p.row("", false, false, false);
    }

    private Token peek() {
        return index < tokens.size()
                ? tokens.get(index)
                : new Token("EOF", sourceLength, sourceLength, "");
    }

    private String text() {
        return peek().text;
    }

    private Token take() {
        Token t = peek();
        if (index < tokens.size()) index++;
        return t;
    }

    private void spaces() {
        while (text().equals(" ")) take();
    }

    private Failure error(Token token, String message) {
        return new Failure(token.sourceStart, token.sourceEnd, message);
    }

    private void expect(String value) {
        if (!text().equals(value)) throw error(peek(), "Expected " + value + ", got " + text());
        take();
    }

    /** 调用来源可能不单调，范围必须求 union；不把最后展开索引冒充原文范围。 */
    private Node node(
            Kind kind,
            int from,
            String value,
            List<Node> children,
            Node a,
            Node b,
            Node c,
            Payload payload) {
        int lo = from < tokens.size() ? tokens.get(from).sourceStart : sourceLength;
        int hi = lo;
        for (int n = from; n < index && n < tokens.size(); n++) {
            lo = Math.min(lo, tokens.get(n).sourceStart);
            hi = Math.max(hi, tokens.get(n).sourceEnd);
        }
        return new Node(kind, lo, hi, value, children, a, b, c, payload);
    }

    private Node row(String end, boolean inCell, boolean inLeft, boolean stopInfix) {
        if (++depth > 64) throw error(peek(), "公式嵌套超过64层");
        String oldStop = stop;
        boolean oldCell = cell, oldLeft = left;
        stop = end;
        cell = inCell;
        left = inLeft;
        int from = index;
        List<Node> body = new ArrayList<Node>();
        boolean hadInfix = false;
        while (true) {
            if (mode.equals("math")) spaces();
            String t = text();
            if (t.equals("EOF")
                    || !end.isEmpty() && t.equals(end)
                    || inCell
                            && (t.equals("&")
                                    || !singleCell && t.equals("\\\\")
                                    || t.equals("\\end"))
                    || inLeft && t.equals("\\right")) break;
            if (t.equals("}")) throw error(peek(), "多余的闭合分组");
            MathCommandCatalog.Function function = MathCommandCatalog.findFunction(t);
            if (function != null && function.infix) {
                // infix在row提前消费，仍须执行与普通函数相同的目录模式资格。
                if (mode.equals("text") ? !function.allowedInText : !function.allowedInMath)
                    throw error(peek(), "命令不允许在 " + mode + " mode: " + t);
                if (stopInfix) break;
                if (hadInfix) throw error(peek(), "同一分组只能有一个 infix 分式");
                hadInfix = true;
                int mark = index;
                take();
                MathDimension size = t.equals("\\above") ? dimension(false) : null;
                Node numerator = node(Kind.ROW, from, "", body, null, null, null, null);
                Node denominator = row(end, inCell, inLeft, true);
                if (MathCommandCatalog.findFunction(text()) != null
                        && MathCommandCatalog.findFunction(text()).infix)
                    throw error(peek(), "同一分组只能有一个 infix 分式");
                String name =
                        t.equals("\\over")
                                ? "frac"
                                : t.equals("\\choose")
                                        ? "binom"
                                        : t.equals("\\atop")
                                                ? "atop"
                                                : t.equals("\\brace")
                                                        ? "brace"
                                                        : t.equals("\\brack") ? "brack" : "above";
                body.clear();
                body.add(
                        fraction(
                                from,
                                name,
                                numerator,
                                denominator,
                                size,
                                null,
                                null,
                                "auto",
                                false));
                break;
            }
            if (leftDepth > 0 && t.equals("\\middle")) {
                if (mode.equals("text") && !function.allowedInText)
                    throw error(peek(), "命令不允许在 text mode: " + t);
                int mark = index;
                take();
                body.add(delimiter(mark, Kind.MIDDLE, "rel", 0));
                continue;
            }
            // 起始prime与^/_同样允许空base，脚本参数本身仍按primitive合同拒绝apostrophe。
            if (mode.equals("math") && (t.equals("^") || t.equals("_") || t.equals("'"))) {
                body.add(
                        postfix(
                                new Node(
                                        Kind.ROW,
                                        peek().sourceStart,
                                        peek().sourceStart,
                                        "",
                                        null,
                                        null,
                                        null,
                                        null)));
            } else body.add(mode.equals("math") ? postfix(atom()) : atom());
        }
        stop = oldStop;
        cell = oldCell;
        left = oldLeft;
        depth--;
        if (mode.equals("text")) formLigatures(body);
        return node(Kind.ROW, from, "", body, null, null, null, null);
    }

    private void formLigatures(List<Node> body) {
        for (int n = 0; n + 1 < body.size(); n++) {
            Node a = body.get(n), b = body.get(n + 1);
            if (a.kind != Kind.GLYPH || b.kind != Kind.GLYPH) continue;
            String token = original(a);
            if ((!token.equals("-") && !token.equals("'") && !token.equals("\u0060"))
                    || !original(b).equals(token)) continue;
            int count =
                    token.equals("-")
                                    && n + 2 < body.size()
                                    && body.get(n + 2).kind == Kind.GLYPH
                                    && original(body.get(n + 2)).equals("-")
                            ? 3
                            : 2;
            String sequence = count == 3 ? "---" : token + token;
            MathSymbols.Entry e = MathSymbols.find("text", sequence);
            if (e == null) continue;
            // 宏实参来源可越过模板首尾来源，必须合并全部组成节点。
            int lo = a.start, hi = a.end;
            for (int part = 1; part < count; part++) {
                Node component = body.get(n + part);
                lo = Math.min(lo, component.start);
                hi = Math.max(hi, component.end);
            }
            Node joined =
                    new Node(
                            Kind.GLYPH,
                            lo,
                            hi,
                            e.replace,
                            null,
                            null,
                            null,
                            null,
                            new GlyphSpec(sequence, "text", e.group, e.font, e.replace, null));
            body.subList(n, n + count).clear();
            body.add(n, joined);
        }
    }

    private Node argument(String type, boolean optional) {
        spaces();
        if (optional && !text().equals("[")) return null;
        String previous = mode;
        if (type.equals("math") || type.equals("text") || type.equals("hbox"))
            mode = type.equals("hbox") ? "text" : type;
        int from = index;
        Node n;
        if (text().equals(optional ? "[" : "{")) {
            take();
            n = row(optional ? "]" : "}", false, false, false);
            expect(optional ? "]" : "}");
            n = node(Kind.ROW, from, "", n.children, null, null, null, null);
        } else {
            if (text().equals("EOF")) throw error(peek(), "缺少命令参数");
            MathCommandCatalog.Function f = MathCommandCatalog.findFunction(text());
            if (f != null && !f.allowedInArgument) throw error(peek(), "命令参数需要分组: " + text());
            if (++depth > 64) throw error(peek(), "公式嵌套超过64层");
            n = atom();
            depth--;
            if (!type.equals("primitive"))
                n = node(Kind.ROW, from, "", Collections.singletonList(n), null, null, null, null);
        }
        mode = previous;
        if (type.equals("hbox"))
            n = node(Kind.STYLE, from, "textstyle", null, n, null, null, new StyleSpec("text"));
        return n;
    }

    private String literal(boolean optional) {
        spaces();
        if (optional && !text().equals("[")) return null;
        String open = optional ? "[" : "{", close = optional ? "]" : "}";
        if (!text().equals(open)) {
            if (text().equals("EOF") || text().equals("}") || text().equals("&"))
                throw error(peek(), "缺少字面参数");
            return take().text;
        }
        Token start = take();
        int nesting = 1, braces = 0;
        StringBuilder value = new StringBuilder();
        while (index < tokens.size()) {
            Token t = take();
            if (optional) {
                if (t.text.equals("{")) braces++;
                else if (t.text.equals("}")) {
                    if (--braces < 0) throw error(t, "字面参数分组错配");
                }
            }
            if (!optional || braces == 0) {
                if (t.text.equals(open)) nesting++;
                if (t.text.equals(close)) {
                    nesting--;
                    if (nesting == 0) return value.toString();
                }
            }
            if (nesting + braces > 64) throw error(t, "字面参数嵌套超过64层");
            value.append(t.text);
        }
        throw error(start, "字面参数分组未闭合");
    }

    private MathDimension dimension(boolean optional) {
        spaces();
        int from = index;
        String value;
        if (optional || text().equals("{")) value = literal(optional);
        else {
            StringBuilder s = new StringBuilder();
            while (index < tokens.size()) {
                String next = s.toString() + text();
                if (!next.matches("[+-]? *[0-9.]* *[a-z]{0,2} *")) break;
                s.append(take().text);
                if (s.toString().matches(".*[a-z]{2} *")) break;
            }
            value = s.toString();
        }
        if (value == null) return null;
        try {
            return MathDimension.parse(value, !optional);
        } catch (IllegalArgumentException e) {
            throw error(from < tokens.size() ? tokens.get(from) : peek(), e.getMessage());
        }
    }

    private static final class Arg {
        Node node;
        String literal;
        MathDimension dimension;
    }

    private Arg arg(String type, boolean optional) {
        Arg a = new Arg();
        if (type.equals("size")) a.dimension = dimension(optional);
        else if (type.equals("raw") || type.equals("url") || type.equals("color")) {
            a.literal = literal(optional);
            if (type.equals("color") && a.literal != null) {
                if (!a.literal.matches("(?i)(#[a-f0-9]{3}|#?[a-f0-9]{6}|[a-z]+)"))
                    throw error(peek(), "Invalid color: " + a.literal);
                if (a.literal.matches("(?i)[a-f0-9]{6}")) a.literal = "#" + a.literal;
            }
        } else a.node = argument(type, optional);
        return a;
    }

    private Node atom() {
        int from = index;
        Token token = peek();
        String t = token.text;
        if (t.equals("{")) return argument("primitive", false);
        if (t.equals("EOF") || t.equals("}") || t.equals("^") || t.equals("_"))
            throw error(token, "数学原子缺失");
        take();
        if (t.equals("\\embed")) {
            String id = literal(false);
            if (id.isEmpty()) throw error(token, "嵌入标识为空");
            if (!embeds.add(id)) throw error(token, "重复嵌入标识: " + id);
            return node(Kind.EMBED, from, id, null, null, null, null, null);
        }
        if (t.startsWith("\\verb") && !t.equals("\\verb")) {
            boolean star = t.startsWith("\\verb*");
            int p = star ? 6 : 5;
            if (t.length() <= p + 1) throw error(token, "verb 缺少定界文本");
            return node(
                    Kind.VERB,
                    from,
                    t.substring(p + 1, t.length() - 1),
                    null,
                    null,
                    null,
                    null,
                    new VerbSpec(star));
        }
        if (mode.equals("text") && (t.equals("$") || t.equals("\\("))) {
            String previous = mode;
            mode = "math";
            Node body = row(t.equals("$") ? "$" : "\\)", false, false, false);
            expect(t.equals("$") ? "$" : "\\)");
            mode = previous;
            return node(
                    Kind.STYLE, from, "textstyle", null, body, null, null, new StyleSpec("text"));
        }
        MathCommandCatalog.Function f = MathCommandCatalog.findFunction(t);
        if (f != null) return function(from, token, f);
        if (t.equals("&")
                || t.equals("\\\\")
                || t.equals("#")
                || t.equals("$")
                || t.equals("\\right")
                || t.equals("\\middle")) throw error(token, "结构 token 不在有效上下文: " + t);
        if (t.startsWith("\\")) {
            MathSymbols.Entry symbol = MathSymbols.find(mode, t);
            if (symbol == null) throw error(token, "Undefined control sequence: " + t);
            return glyph(from, t, symbol);
        }
        int cp = t.codePointAt(0);
        if (cp >= 0xD800 && cp <= 0xDFFF) throw error(token, "孤立UTF-16代理项");
        return literalGlyph(from, t);
    }

    private Node literalGlyph(int from, String original) {
        String normalized = original;
        if (MathSymbols.find(mode, original) == null && styledFont(original) == null)
            normalized = java.text.Normalizer.normalize(original, java.text.Normalizer.Form.NFD);
        int baseEnd = normalized.length();
        while (baseEnd > 0
                && normalized.charAt(baseEnd - 1) >= 0x300
                && normalized.charAt(baseEnd - 1) <= 0x36f) baseEnd--;
        if (baseEnd == normalized.length()) {
            MathSymbols.Entry e = MathSymbols.find(mode, original);
            if (e == null && original.codePointAt(0) < 0x80)
                throw error(tokens.get(from), "Unexpected character: " + original);
            return glyph(from, original, e);
        }
        if (baseEnd == 0) throw error(tokens.get(from), "组合重音缺少base");
        String base = normalized.substring(0, baseEnd);
        if (base.equals("i")) base = "ı";
        else if (base.equals("j")) base = "ȷ";
        Node result = glyph(from, base, MathSymbols.find(mode, base));
        GlyphSpec facts = (GlyphSpec) result.payload;
        result =
                new Node(
                        result.kind,
                        result.start,
                        result.end,
                        result.value,
                        result.children,
                        result.first,
                        result.second,
                        result.third,
                        new GlyphSpec(
                                original,
                                facts.mode,
                                facts.atomGroup,
                                facts.catalogFont,
                                facts.replacement,
                                facts.styledFont));
        String marks = "̧́̀̈̃̄̆̌̂̇̊̋";
        String[] math = {
            "acute",
            "grave",
            "ddot",
            "tilde",
            "bar",
            "breve",
            "check",
            "hat",
            "dot",
            "mathring",
            "H",
            "c"
        };
        String[] text = {"'", "\u0060", "\"", "~", "=", "u", "v", "^", ".", "r", "H", "c"};
        for (int n = baseEnd; n < normalized.length(); n++) {
            int mark = marks.indexOf(normalized.charAt(n));
            if (mark < 0) throw error(tokens.get(from), "Unknown accent: " + normalized.charAt(n));
            String command = mode.equals("text") ? text[mark] : math[mark];
            result =
                    node(
                            Kind.ACCENT,
                            from,
                            command,
                            null,
                            result,
                            null,
                            null,
                            new DecorationSpec(command, false, false, true));
        }
        return result;
    }

    private Node glyph(int from, String t, MathSymbols.Entry e) {
        boolean anonymousText = e == null && t.codePointAt(0) >= 0x80 && styledFont(t) == null;
        String group =
                anonymousText
                        ? "textord"
                        : e == null
                                ? mode.equals("text") || !Character.isLetter(t.codePointAt(0))
                                        ? "textord"
                                        : "mathord"
                                : e.group;
        String value = styledFont(t) != null || e == null || e.replace == null ? t : e.replace;
        GlyphSpec spec =
                new GlyphSpec(
                        t,
                        anonymousText ? "text" : mode,
                        group,
                        e == null ? null : e.font,
                        e == null ? t : e.replace,
                        styledFont(t));
        if (group.equals("spacing"))
            return node(
                    Kind.SPACE,
                    from,
                    value,
                    null,
                    null,
                    null,
                    null,
                    new SpaceSpec(
                            null,
                            t.equals("\\nobreak") || t.equals("\\ ") || t.equals("~"),
                            t.equals("\\allowbreak"),
                            spec));
        if (group.equals("op-token"))
            return node(
                    Kind.OPERATOR,
                    from,
                    value,
                    null,
                    null,
                    null,
                    null,
                    new OperatorSpec(t, "default", true, true, false, spec));
        return node(Kind.GLYPH, from, value, null, null, null, null, spec);
    }

    private String styledFont(String text) {
        int cp = text.codePointAt(0);
        if (cp >= 0x1D400 && cp < 0x1D6A4) {
            String[] fonts = {
                "mathbf",
                "mathit",
                "boldsymbol",
                "mathscr",
                "mathboldscr",
                "mathfrak",
                "mathbb",
                "mathboldfrak",
                "mathsf",
                "mathboldsf",
                "mathsfit",
                "mathboldsfit",
                "mathtt"
            };
            return fonts[(cp - 0x1D400) / 52];
        }
        if (cp >= 0x1D6A8 && cp < 0x1D7CA)
            return new String[] {"mathbf", "mathit", "boldsymbol", "mathboldsf", "mathboldsfit"}
                    [(cp - 0x1D6A8) / 58];
        if (cp >= 0x1D7CE && cp <= 0x1D7FF)
            return new String[] {"mathbf", "mathbb", "mathsf", "mathboldsf", "mathtt"}
                    [(cp - 0x1D7CE) / 10];
        return null;
    }

    private Node postfix(Node base) {
        int scriptFrom = index;
        Node upper = null, lower = null;
        String limits = "default";
        boolean changed = false;
        while (true) {
            spaces();
            String t = text();
            if (t.equals("^") || t.equals("_")) {
                Token mark = take();
                if (t.equals("^") && upper != null || t.equals("_") && lower != null)
                    throw error(mark, "重复的同类脚本");
                Node n = argument("primitive", false);
                if (t.equals("^")) upper = n;
                else lower = n;
                changed = true;
            } else if (t.equals("'")) {
                if (upper != null) throw error(peek(), "Double superscript");
                int from = index;
                List<Node> primes = new ArrayList<Node>();
                while (text().equals("'")) {
                    int p = index;
                    take();
                    primes.add(glyph(p, "\\prime", MathSymbols.find("math", "\\prime")));
                }
                if (text().equals("^")) {
                    take();
                    primes.add(argument("primitive", false));
                }
                upper = node(Kind.ROW, from, "", primes, null, null, null, null);
                changed = true;
            } else if (t.length() == 1 && SCRIPT_TOKENS.indexOf(t) >= 0) {
                int from = index;
                boolean isSub = SUBSCRIPTS.indexOf(t) >= 0;
                List<Token> script = new ArrayList<Token>();
                while (text().length() == 1
                        && SCRIPT_TOKENS.indexOf(text()) >= 0
                        && (SUBSCRIPTS.indexOf(text()) >= 0) == isSub) {
                    Token original = take();
                    String baseToken =
                            String.valueOf(
                                    SCRIPT_BASES.charAt(SCRIPT_TOKENS.indexOf(original.text)));
                    script.add(new Token(baseToken, original.sourceStart, original.sourceEnd, ""));
                }
                Node n = subparse(script);
                if (isSub) lower = n;
                else upper = n;
                changed = true;
            } else if (t.equals("\\limits") || t.equals("\\nolimits")) {
                Token mark = take();
                if (base.kind != Kind.OPERATOR) throw error(mark, "上下限指令只能跟随算子");
                OperatorSpec facts = (OperatorSpec) base.payload;
                if (!facts.originalToken.equals("\\operatorname@") || facts.alwaysHandleSupSub) {
                    limits = t.substring(1);
                    changed = true;
                }
            } else break;
        }
        if (!changed) return base;
        OperatorSpec op = base.payload instanceof OperatorSpec ? (OperatorSpec) base.payload : null;
        int lo = base.start, hi = base.end;
        for (int n = scriptFrom; n < index; n++) {
            lo = Math.min(lo, tokens.get(n).sourceStart);
            hi = Math.max(hi, tokens.get(n).sourceEnd);
        }
        return new Node(
                Kind.SCRIPTS,
                lo,
                hi,
                limits,
                null,
                base,
                upper,
                lower,
                op == null
                        ? null
                        : new OperatorSpec(
                                op.originalToken,
                                limits,
                                op.symbol,
                                op.defaultLimits,
                                op.alwaysHandleSupSub,
                                op.symbolFacts));
    }

    private Node function(int from, Token token, MathCommandCatalog.Function f) {
        String t = token.text,
                name =
                        t.startsWith("\\\\")
                                ? t.substring(2)
                                : t.startsWith("\\") ? t.substring(1) : t;
        if (mode.equals("text") ? !f.allowedInText : !f.allowedInMath)
            throw error(token, "命令不允许在 " + mode + " mode: " + t);
        if (t.equals("\\begin")) return environment(from, token);
        if (t.equals("\\end") || f.type.equals("middle") || f.type.equals("leftright-right"))
            throw error(token, "错配结构命令: " + t);
        if (f.type.equals("leftright")) {
            Node l = delimiter(from, Kind.DELIMITER, "open", 0);
            Node body;
            leftDepth++;
            try {
                body = row(stop, cell, true, false);
            } finally {
                leftDepth--;
            }
            if (!text().equals("\\right")) throw error(token, "left 缺少同组 right");
            int r = index;
            take();
            Node right = delimiter(r, Kind.DELIMITER, "close", 0);
            return node(Kind.DELIMITED, from, "", null, l, body, right, null);
        }
        if (f.type.equals("delimsizing")) {
            int size =
                    name.startsWith("Bigg")
                            ? 4
                            : name.startsWith("bigg") ? 3 : name.startsWith("Big") ? 2 : 1;
            String group =
                    name.endsWith("l")
                            ? "open"
                            : name.endsWith("r") ? "close" : name.endsWith("m") ? "rel" : "ord";
            return delimiter(from, Kind.DELIMITER, group, size);
        }
        if (f.type.equals("internal")) {
            if (!name.equals("relax")) throw error(token, "宏执行命令未在展开层消费: " + t);
            return node(Kind.INTERNAL, from, name, null, null, null, null, null);
        }
        if (t.equals("\\hline") || t.equals("\\hdashline"))
            throw error(token, t + " valid only within array environment");
        if (t.equals("\\]") || t.equals("\\)"))
            throw error(token, "Unexpected closing math delimiter: " + t);
        if (f.type.equals("styling")
                || f.type.equals("sizing")
                || f.type.equals("font") && f.numArgs == 0) {
            Node body = row(stop, cell, left, true);
            if (f.type.equals("styling"))
                return node(
                        Kind.STYLE,
                        from,
                        name,
                        null,
                        body,
                        null,
                        null,
                        new StyleSpec(name.replace("style", "")));
            if (f.type.equals("sizing"))
                return node(
                        Kind.SIZE,
                        from,
                        name,
                        null,
                        body,
                        null,
                        null,
                        new SizeSpec(sizeIndex(name)));
            return node(
                    Kind.FONT,
                    from,
                    "math" + name,
                    null,
                    body,
                    null,
                    null,
                    new FontSpec("math" + name, mode));
        }
        List<Arg> optional = new ArrayList<Arg>(), args = new ArrayList<Arg>();
        for (int n = 0; n < f.numArgs + f.numOptionalArgs; n++) {
            boolean opt = n < f.numOptionalArgs;
            String type =
                    n < f.argTypes.size()
                            ? f.argTypes.get(n)
                            : f.primitive ? "primitive" : "original";
            if (f.type.equals("sqrt") && !opt && optional.get(0).node == null) type = "primitive";
            Arg a = arg(type, opt);
            if (opt) optional.add(a);
            else args.add(a);
        }
        Node a = args.isEmpty() ? null : args.get(0).node;
        Node b = args.size() < 2 ? null : args.get(1).node;
        switch (f.type) {
            case "genfrac":
                if (name.equals("genfrac")) {
                    String l = fractionalDelimiter(args.get(0).node, "open");
                    String r = fractionalDelimiter(args.get(1).node, "close");
                    Node styleArgument = args.get(3).node;
                    Node styleToken =
                            styleArgument.children.isEmpty() ? null : styleArgument.children.get(0);
                    String s = "";
                    if (styleToken != null) {
                        if (!(styleToken.payload instanceof GlyphSpec)
                                || !((GlyphSpec) styleToken.payload).atomGroup.equals("textord"))
                            throw error(token, "Expected textord in fraction style");
                        s = styleToken.value;
                    }
                    return fraction(
                            from,
                            name,
                            args.get(4).node,
                            args.get(5).node,
                            args.get(2).dimension,
                            l,
                            r,
                            s.matches("[0-3]")
                                    ? new String[] {"display", "text", "script", "scriptscript"}
                                            [Integer.parseInt(s)]
                                    : "auto",
                            false);
                }
                if (name.equals("abovefrac"))
                    return fraction(
                            from,
                            "above",
                            a,
                            args.get(2).node,
                            args.get(1).dimension,
                            null,
                            null,
                            "auto",
                            false);
                return fraction(
                        from,
                        name,
                        a,
                        b,
                        null,
                        null,
                        null,
                        name.equals("cfrac") || name.startsWith("d")
                                ? "display"
                                : name.startsWith("t") ? "text" : "auto",
                        name.equals("cfrac"));
            case "sqrt":
                return node(Kind.ROOT, from, name, null, a, optional.get(0).node, null, null);
            case "font":
                name =
                        name.equals("Bbb")
                                ? "mathbb"
                                : name.equals("bold")
                                        ? "mathbf"
                                        : name.equals("frak") ? "mathfrak" : name;
                return node(
                        Kind.FONT,
                        from,
                        name,
                        null,
                        normalize(a),
                        null,
                        null,
                        new FontSpec(name, mode));
            case "text":
                return node(
                        Kind.TEXT,
                        from,
                        plain(a),
                        null,
                        a,
                        null,
                        null,
                        new FontSpec(name.equals("textnormal") ? "textrm" : name, "text"));
            case "hbox":
                return node(
                        Kind.TEXT,
                        from,
                        plain(a),
                        null,
                        a,
                        null,
                        null,
                        new FontSpec("textrm", "text"));
            case "textord":
                String code = plain(a);
                if (!code.matches("[0-9]+")) throw error(token, "Invalid character code");
                int cp;
                try {
                    cp = Integer.parseInt(code);
                } catch (NumberFormatException ex) {
                    throw error(token, "Invalid character code");
                }
                if (!Character.isValidCodePoint(cp) || cp >= 0xD800 && cp <= 0xDFFF)
                    throw error(token, "Invalid character code");
                return glyph(from, new String(Character.toChars(cp)), null);
            case "op":
            case "operatorname":
                MathSymbols.Entry e = MathSymbols.find("math", t);
                boolean symbol = e != null || !t.startsWith("\\");
                boolean defaultLimits =
                        name.equals("operatornamewithlimits")
                                || symbol
                                        && !name.matches("(?:i{1,3}nt|oi{1,3}nt|smallint|[∫∬∭∮∯∰])")
                                || Arrays.asList(
                                                "det", "gcd", "inf", "lim", "max", "min", "Pr",
                                                "sup")
                                        .contains(name);
                String value =
                        a != null ? plain(a) : e != null && e.replace != null ? e.replace : name;
                return node(
                        Kind.OPERATOR,
                        from,
                        value,
                        null,
                        a,
                        null,
                        null,
                        new OperatorSpec(
                                t,
                                "default",
                                symbol,
                                defaultLimits,
                                f.type.equals("operatorname") && defaultLimits,
                                e == null
                                        ? null
                                        : new GlyphSpec(
                                                t,
                                                "math",
                                                e.group,
                                                e.font,
                                                e.replace,
                                                styledFont(t))));
            case "mclass":
                if (name.equals("overset") || name.equals("underset") || name.equals("stackrel"))
                    return node(
                            Kind.OVERUNDER,
                            from,
                            name,
                            null,
                            b,
                            a,
                            null,
                            new DecorationSpec(name, name.equals("underset"), false, false));
                if (name.equals("boldsymbol") || name.equals("bm")) {
                    Node font =
                            node(
                                    Kind.FONT,
                                    from,
                                    "boldsymbol",
                                    null,
                                    a,
                                    null,
                                    null,
                                    new FontSpec("boldsymbol", mode));
                    return node(
                            Kind.CLASSIFY,
                            from,
                            "",
                            null,
                            font,
                            null,
                            null,
                            new ClassSpec(binrel(a), character(a)));
                }
                return node(
                        Kind.CLASSIFY,
                        from,
                        "",
                        null,
                        name.equals("@binrel") ? b : a,
                        null,
                        null,
                        new ClassSpec(
                                name.equals("@binrel") ? binrel(a) : name.substring(4),
                                character(name.equals("@binrel") ? b : a)));
            case "accent":
            case "accentUnder":
            case "overline":
            case "underline":
            case "horizBrace":
                boolean below =
                        f.type.equals("accentUnder")
                                || f.type.equals("underline")
                                || name.equals("underbrace");
                boolean stretch =
                        f.type.equals("accentUnder")
                                || name.startsWith("wide")
                                || name.startsWith("over")
                                || name.startsWith("under");
                Kind kind = f.type.equals("horizBrace") ? Kind.HORIZBRACE : Kind.ACCENT;
                return node(
                        kind,
                        from,
                        name,
                        null,
                        normalize(a),
                        null,
                        null,
                        new DecorationSpec(
                                name, below, stretch, !stretch || name.startsWith("wide")));
            case "xArrow":
                return node(
                        Kind.XARROW,
                        from,
                        name,
                        null,
                        a,
                        optional.get(0).node,
                        null,
                        new DecorationSpec(name, false, true, false));
            case "cdlabel":
                return node(
                        Kind.LAP,
                        from,
                        name,
                        null,
                        a,
                        null,
                        null,
                        new DecorationSpec(name, false, false, false));
            case "cdlabelparent":
                return node(Kind.VCENTER, from, name, null, a, null, null, null);
            case "color":
                String css = args.get(0).literal;
                Node colored = args.size() == 2 ? b : row(stop, cell, left, true);
                return node(Kind.COLOR, from, name, null, colored, null, null, new ColorSpec(css));
            case "kern":
                MathDimension dim = args.get(0).dimension;
                return node(
                        Kind.SPACE,
                        from,
                        name,
                        null,
                        null,
                        null,
                        null,
                        new SpaceSpec(dim, false, false));
            case "cr":
                return node(
                        Kind.LINEBREAK,
                        from,
                        name,
                        null,
                        null,
                        null,
                        null,
                        new SpaceSpec(dimension(true), false, false));
            case "rule":
                return node(
                        Kind.RULE,
                        from,
                        name,
                        null,
                        null,
                        null,
                        null,
                        new BoxSpec(
                                args.get(0).dimension,
                                args.get(1).dimension,
                                optional.get(0).dimension,
                                null,
                                null,
                                true,
                                true,
                                true,
                                true));
            case "raisebox":
                return node(
                        Kind.RAISE,
                        from,
                        name,
                        null,
                        b,
                        null,
                        null,
                        new BoxSpec(
                                null,
                                null,
                                args.get(0).dimension,
                                null,
                                null,
                                true,
                                true,
                                true,
                                true));
            case "phantom":
            case "hphantom":
            case "vphantom":
                return node(
                        Kind.PHANTOM,
                        from,
                        name,
                        null,
                        a,
                        null,
                        null,
                        new BoxSpec(
                                null,
                                null,
                                null,
                                null,
                                null,
                                !name.equals("vphantom"),
                                !name.equals("hphantom"),
                                !name.equals("hphantom"),
                                false));
            case "smash":
                String flags = optional.get(0).node == null ? "tb" : plain(optional.get(0).node);
                boolean valid = flags.matches("[tb]*");
                return node(
                        Kind.SMASH,
                        from,
                        name,
                        null,
                        a,
                        null,
                        null,
                        new BoxSpec(
                                null,
                                null,
                                null,
                                null,
                                null,
                                true,
                                !valid || !flags.contains("t"),
                                !valid || !flags.contains("b"),
                                true));
            case "lap":
                return node(
                        Kind.LAP,
                        from,
                        name,
                        null,
                        a,
                        null,
                        null,
                        new DecorationSpec(name, false, false, false));
            case "pmb":
                return node(Kind.PMB, from, name, null, a, null, null, null);
            case "vcenter":
                return node(Kind.VCENTER, from, name, null, a, null, null, null);
            case "enclose":
                if (name.equals("colorbox") || name.equals("fcolorbox")) {
                    boolean border = name.equals("fcolorbox");
                    return node(
                            Kind.BOX,
                            from,
                            name,
                            null,
                            args.get(border ? 2 : 1).node,
                            null,
                            null,
                            new BoxSpec(
                                    null,
                                    null,
                                    null,
                                    args.get(border ? 1 : 0).literal,
                                    border ? args.get(0).literal : null,
                                    true,
                                    true,
                                    true,
                                    true));
                }
                return node(
                        Kind.ENCLOSE,
                        from,
                        name,
                        null,
                        a,
                        null,
                        null,
                        new DecorationSpec(name, false, false, false));
            case "mathchoice":
                List<Node> choices = new ArrayList<Node>();
                for (Arg choice : args) choices.add(choice.node);
                return node(Kind.MATHCHOICE, from, name, choices, null, null, null, null);
            case "htmlmathml":
                return node(Kind.HTMLMATHML, from, name, null, a, b, null, null);
            case "includegraphics":
                validateGraphics(optional.get(0).literal, token); // 默认拒绝前仍验证全部参数
            case "href":
                return node(
                        Kind.UNSUPPORTED, from, t, null, null, null, null, new UnsupportedSpec(t));
            case "html":
                if (name.equals("htmlData"))
                    for (String pair : args.get(0).literal.split(",", -1))
                        if (pair.split("=", -1).length != 2)
                            throw error(token, "Error parsing key-value for htmlData");
                return node(
                        Kind.UNSUPPORTED, from, t, null, null, null, null, new UnsupportedSpec(t));
            case "verb":
                throw error(token, "verb 缺少定界文本");
            default:
                throw error(token, "未实现登记函数类型: " + f.type);
        }
    }

    private int sizeIndex(String name) {
        return Arrays.asList(
                                "tiny",
                                "sixptsize",
                                "scriptsize",
                                "footnotesize",
                                "small",
                                "normalsize",
                                "large",
                                "Large",
                                "LARGE",
                                "huge",
                                "Huge")
                        .indexOf(name)
                + 1;
    }

    private void validateGraphics(String options, Token token) {
        if (options == null) return;
        for (String pair : options.split(",")) {
            String[] v = pair.split("=", -1);
            if (v.length != 2) continue;
            String key = v[0].trim(), value = v[1].trim();
            if (key.equals("alt")) continue;
            if (!key.equals("width") && !key.equals("height") && !key.equals("totalheight"))
                throw error(token, "Invalid key: " + key);
            if (value.matches("[+-]? *([0-9]+(\\.[0-9]*)?|\\.[0-9]+)")) value += "bp";
            try {
                MathDimension.parse(value, false);
            } catch (IllegalArgumentException ex) {
                throw error(token, ex.getMessage());
            }
        }
    }

    private Node normalize(Node n) {
        return n != null && n.kind == Kind.ROW && n.children.size() == 1 ? n.children.get(0) : n;
    }

    private boolean character(Node n) {
        n = normalize(n);
        return n != null && n.kind == Kind.GLYPH;
    }

    private String binrel(Node n) {
        n = normalize(n);
        if (n != null && n.kind == Kind.ROW && !n.children.isEmpty()) n = n.children.get(0);
        if (n != null && n.payload instanceof GlyphSpec) {
            String group = ((GlyphSpec) n.payload).atomGroup;
            if (group.equals("bin") || group.equals("rel")) return group;
        }
        return "ord";
    }

    private String plain(Node n) {
        if (n == null) return "";
        if (n.kind == Kind.GLYPH || n.kind == Kind.VERB || n.kind == Kind.SPACE) return n.value;
        StringBuilder s = new StringBuilder();
        for (Node c : n.children) s.append(plain(c));
        if (n.children.isEmpty() && n.first != null) s.append(plain(n.first));
        return s.toString();
    }

    private String fractionalDelimiter(Node n, String group) {
        n = normalize(n);
        if (n != null
                && n.payload instanceof GlyphSpec
                && ((GlyphSpec) n.payload).atomGroup.equals(group))
            return n.value.equals(".") ? null : n.value;
        return null;
    }

    private Node fraction(
            int from,
            String name,
            Node numerator,
            Node denominator,
            MathDimension thickness,
            String left,
            String right,
            String style,
            boolean continued) {
        if (name.contains("binom") || name.equals("choose")) {
            left = "(";
            right = ")";
        }
        if (name.contains("brace")) {
            left = "{";
            right = "}";
        }
        if (name.contains("brack")) {
            left = "[";
            right = "]";
        }
        boolean bar =
                name.contains("frac")
                                && !name.contains("atop")
                                && !name.contains("brace")
                                && !name.contains("brack")
                        || name.equals("over")
                        || name.equals("above");
        if (thickness != null)
            bar = thickness.blank && name.equals("genfrac") || thickness.number > 0;
        Kind kind =
                name.equals("frac") || name.equals("dfrac") || name.equals("tfrac")
                        ? Kind.FRAC
                        : name.equals("binom") ? Kind.BINOM : Kind.GENFRAC;
        return node(
                kind,
                from,
                name,
                null,
                numerator,
                denominator,
                null,
                new FractionSpec(left, right, style, "c", bar, continued, thickness));
    }

    private Node delimiter(int from, Kind kind, String group, int size) {
        spaces();
        Token t = take();
        String raw = t.text;
        MathSymbols.Entry e = MathSymbols.find("math", raw);
        String value = e == null || e.replace == null ? raw : e.replace;
        if (value.equals("∣")) value = "|";
        if (value.equals("∥")) value = "‖";
        boolean allowed = raw.equals(".") || ("()[]{}|‖⟨⟩⌊⌋⌈⌉↑↓↕⇑⇓⇕/\\⟮⟯⎰⎱").contains(value);
        // 固定sized/custom delimiter把关系别名lt/gt仅在定界位置归一为角括号。
        if (raw.equals("<") || raw.equals("\\lt")) value = "⟨";
        if (raw.equals(">") || raw.equals("\\gt")) value = "⟩";
        allowed |= raw.equals("<") || raw.equals(">") || raw.equals("\\lt") || raw.equals("\\gt");
        if (!allowed || raw.equals("{") || raw.equals("}") || raw.equals("EOF"))
            throw error(t, "Invalid delimiter: " + raw);
        return node(kind, from, value, null, null, null, null, new DelimiterSpec(raw, group, size));
    }

    // 二维环境的实现集中在本 parser；MathEnvironmentSpec 只记录模式和布局语义。
    private Node environment(int from, Token begin) {
        String name = literal(false);
        MathCommandCatalog.Environment registration = MathCommandCatalog.findEnvironment(name);
        MathEnvironmentSpec spec = MathEnvironmentSpec.find(name);
        if (spec == null) throw error(begin, "No such environment: " + name);
        if (mode.equals("text") && !registration.allowedInText)
            throw error(begin, "环境不允许在text mode: " + name);
        List<Arg> parameters = new ArrayList<Arg>();
        for (int n = 0; n < registration.numArgs + registration.numOptionalArgs; n++)
            parameters.add(
                    arg(
                            n < registration.argTypes.size()
                                    ? registration.argTypes.get(n)
                                    : "original",
                            n < registration.numOptionalArgs));
        String base = name.replace("*", ""), alignment = "c";
        List<ColumnSpec> columns = new ArrayList<ColumnSpec>();
        List<Node> inserts = new ArrayList<Node>();
        int columnLimit = spec.maxColumns, declaredColumns = 0;
        boolean constrainedColumns = columnLimit > 0;
        if (base.equals("array") || base.equals("darray") || base.equals("subarray")) {
            Node declaration = parameters.get(0).node;
            List<Node> parts =
                    declaration.kind == Kind.ROW
                            ? declaration.children
                            : Collections.singletonList(declaration);
            for (int n = 0; n < parts.size(); n++) {
                Node part = parts.get(n);
                String ch = original(part);
                if (ch.equals("@") && !base.equals("subarray")) {
                    if (n + 1 >= parts.size() || parts.get(n + 1).kind != Kind.ROW)
                        throw error(begin, "@ 列插入需要分组");
                    inserts.add(parts.get(++n));
                    columns.add(
                            new ColumnSpec(ColumnSpec.Type.INSERT, null, 0, 0, inserts.size() - 1));
                } else if ((base.equals("subarray") ? "lc" : "lcr").contains(ch)
                        && ch.length() == 1)
                    columns.add(
                            new ColumnSpec(ColumnSpec.Type.ALIGN, ch, Double.NaN, Double.NaN, -1));
                else if (!base.equals("subarray") && (ch.equals("|") || ch.equals(":")))
                    columns.add(
                            new ColumnSpec(
                                    ch.equals("|") ? ColumnSpec.Type.SOLID : ColumnSpec.Type.DASHED,
                                    null,
                                    0,
                                    0,
                                    -1));
                else throw error(begin, "Unknown column alignment: " + ch);
            }
            if (base.equals("subarray") && columns.size() > 1)
                throw error(begin, "subarray can contain only one column");
        } else if (name.endsWith("*") && base.endsWith("matrix")) {
            String opt = literal(true);
            if (opt != null) {
                alignment = opt.trim();
                if (!alignment.matches("[lcr]")) throw error(begin, "Expected l or c or r");
            }
        }
        if (base.equals("alignat") || base.equals("alignedat")) {
            Node parameter = parameters.get(0).node;
            for (Node part : parameter.children)
                if (!(part.payload instanceof GlyphSpec)
                        || !((GlyphSpec) part.payload).atomGroup.equals("textord"))
                    throw error(begin, "Expected textord in alignat column count");
            String v = plain(parameter).trim();
            double pairs;
            try {
                pairs = v.isEmpty() ? 0 : Double.parseDouble(v);
            } catch (NumberFormatException ex) {
                pairs = 0;
            }
            if (Double.isNaN(pairs)) pairs = 0;
            if (Double.isInfinite(pairs) || pairs * 2 > 16384)
                throw error(begin, "Invalid alignat column count: " + v);
            declaredColumns = (int) Math.ceil(pairs * 2);
            columnLimit = (int) Math.floor(pairs * 2);
            // 非零小数的floor可为0，但仍有硬约束；只有Number0选择动态aligned。
            constrainedColumns = pairs != 0;
        }
        List<Node> flat = new ArrayList<Node>();
        List<RowSpec> rows = new ArrayList<RowSpec>();
        List<LineSpec> lines = new ArrayList<LineSpec>();
        boolean oldSingle = singleCell;
        singleCell = spec.singleRow;
        int maxColumns = 0, firstCell = 0, count = 0;
        horizontalLines(lines, 0, base.equals("CD"));
        while (true) {
            Node content = row("", true, false, false);
            if (base.equals("CD")) {
                List<Node> cd = cdCells(content, rows.size(), begin);
                flat.addAll(cd);
                count += cd.size();
            } else {
                if ((spec.separation.equals("align") || spec.separation.equals("alignat"))
                        && count % 2 == 1) {
                    List<Node> prefixed = new ArrayList<Node>();
                    prefixed.add(
                            new Node(
                                    Kind.ROW,
                                    content.start,
                                    content.start,
                                    "",
                                    null,
                                    null,
                                    null,
                                    null));
                    prefixed.addAll(content.children);
                    content =
                            new Node(
                                    Kind.ROW,
                                    content.start,
                                    content.end,
                                    "",
                                    prefixed,
                                    null,
                                    null,
                                    null);
                }
                flat.add(content);
                count++;
            }
            if (constrainedColumns && count > columnLimit)
                throw error(begin, "Too many columns in " + name);
            if (text().equals("&")) {
                take();
                continue;
            }
            if (text().equals("\\\\") && !spec.singleRow) {
                take();
                MathDimension gap = text().equals("[") ? dimension(true) : null;
                rows.add(new RowSpec(firstCell, count, gap, -1, spec.automaticTags));
                maxColumns = Math.max(maxColumns, count);
                firstCell = flat.size();
                count = 0;
                horizontalLines(lines, rows.size(), base.equals("CD"));
                continue;
            }
            if (text().equals("\\end")) {
                take();
                String endName = literal(false);
                if (!endName.equals(name)) throw error(begin, "环境不匹配: " + name + " / " + endName);
                boolean empty =
                        count == 1
                                && flat.get(firstCell).kind == Kind.ROW
                                && flat.get(firstCell).children.isEmpty();
                boolean keepEmpty =
                        spec.tags
                                || base.equals("split")
                                || base.equals("aligned")
                                || base.equals("alignedat")
                                || base.equals("gathered");
                if (empty && (!rows.isEmpty() || !keepEmpty)) flat.remove(flat.size() - 1);
                else {
                    rows.add(new RowSpec(firstCell, count, null, -1, spec.automaticTags));
                    maxColumns = Math.max(maxColumns, count);
                }
                break;
            }
            throw error(peek(), "Expected & or \\\\ or \\end in " + name);
        }
        singleCell = oldSingle;
        if (columns.isEmpty()
                && (spec.separation.equals("align") || spec.separation.equals("alignat"))) {
            boolean aligned = !constrainedColumns;
            maxColumns = Math.max(maxColumns, declaredColumns);
            for (int n = 0; n < maxColumns; n++)
                columns.add(
                        new ColumnSpec(
                                ColumnSpec.Type.ALIGN,
                                n % 2 == 0 ? "r" : "l",
                                n > 0 && n % 2 == 0 && aligned ? 1 : 0,
                                0,
                                -1));
        } else if (columns.isEmpty() && spec.separation.equals("cases")) {
            columns.add(new ColumnSpec(ColumnSpec.Type.ALIGN, "l", 0, 1, -1));
            columns.add(new ColumnSpec(ColumnSpec.Type.ALIGN, "l", 0, 0, -1));
        } else if (columns.isEmpty()) {
            for (int n = 0; n < maxColumns; n++)
                columns.add(
                        new ColumnSpec(
                                ColumnSpec.Type.ALIGN,
                                alignment,
                                base.equals("CD") ? .25 : Double.NaN,
                                base.equals("CD") ? .25 : Double.NaN,
                                -1));
        }
        // cell树仅存于flat，不在payload重复；列插入和tag追加并保存整数索引。
        for (int n = 0; n < columns.size(); n++) {
            ColumnSpec c = columns.get(n);
            if (c.type == ColumnSpec.Type.INSERT) {
                int child = flat.size();
                flat.add(inserts.get(c.insertChild));
                columns.set(n, new ColumnSpec(c.type, c.align, c.preGap, c.postGap, child));
            }
        }
        for (MathMacroExpander.RowTag tag : rowTags) {
            if (tag.environmentOrdinal != begin.environmentOrdinal || !tag.environment.equals(name))
                continue;
            if (tag.row >= rows.size()) continue;
            RowSpec previous = rows.get(tag.row);
            int child = -1;
            if (!tag.tokens.isEmpty()) {
                Node tagBody = subparse(tag.tokens);
                child = flat.size();
                flat.add(
                        new Node(
                                Kind.TAG,
                                tag.sourceStart,
                                tag.sourceEnd,
                                "",
                                null,
                                tagBody,
                                null,
                                null,
                                new TagSpec(true, false)));
            }
            rows.set(
                    tag.row,
                    new RowSpec(
                            previous.firstCell,
                            previous.cellCount,
                            previous.gapAfter,
                            child,
                            tag.automatic));
        }
        return node(
                Kind.ARRAY,
                from,
                name,
                flat,
                null,
                null,
                null,
                new ArraySpec(
                        name,
                        spec.style,
                        !constrainedColumns && spec.separation.equals("alignat")
                                ? "align"
                                : spec.separation,
                        spec.left,
                        spec.right,
                        rows,
                        columns,
                        lines,
                        arrayStretch(begin, spec),
                        spec.displayRequired,
                        spec.inlineAutoDisplay,
                        spec.addJot,
                        spec.outerSpacing,
                        spec.singleRow));
    }

    private double arrayStretch(Token begin, MathEnvironmentSpec spec) {
        if (spec.separation.equals("small")
                || spec.separation.equals("subarray")
                || spec.separation.equals("cases")
                || spec.separation.equals("CD")) return spec.stretch;
        for (MathMacroExpander.EnvironmentHeader header : environmentHeaders) {
            if (header.environmentOrdinal != begin.environmentOrdinal) continue;
            if (!header.arrayStretchFromMacro) return 1;
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile(
                                    "^\\s*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)")
                            .matcher(header.arrayStretchText);
            if (!m.find()) throw error(begin, "Invalid arraystretch: " + header.arrayStretchText);
            double value = Double.parseDouble(m.group(1));
            if (!(value > 0) || Double.isInfinite(value))
                throw error(begin, "Invalid arraystretch: " + header.arrayStretchText);
            return value;
        }
        return spec.stretch;
    }

    private void horizontalLines(List<LineSpec> lines, int row, boolean cd) {
        spaces();
        if (text().equals("\\relax")) {
            take();
            spaces();
        }
        while (text().equals("\\hline") || text().equals("\\hdashline")) {
            if (cd) throw error(peek(), "hline不在CD语法");
            lines.add(new LineSpec(row, take().text.equals("\\hdashline")));
            spaces();
        }
    }

    private String original(Node n) {
        return n.payload instanceof GlyphSpec ? ((GlyphSpec) n.payload).originalToken : n.value;
    }

    private List<Node> cdCells(Node body, int row, Token start) {
        List<Node> cells = new ArrayList<Node>(), current = new ArrayList<Node>();
        List<Node> parts = body.children;
        for (int n = 0; n < parts.size(); n++) {
            Node part = parts.get(n);
            if (!original(part).equals("@")) {
                current.add(part);
                continue;
            }
            cells.add(new Node(Kind.ROW, body.start, body.end, "", current, null, null, null));
            current = new ArrayList<Node>();
            if (++n == parts.size()) throw error(start, "CD箭头缺少方向");
            String direction = original(parts.get(n));
            if (direction.length() != 1 || "<>AV=|.".indexOf(direction) < 0)
                throw error(start, "Expected one of <>AV=|. after @");
            Node[] labels = new Node[2];
            if ("<>AV".contains(direction)) {
                for (int label = 0; label < 2; label++) {
                    List<Node> values = new ArrayList<Node>();
                    boolean closed = false;
                    while (++n < parts.size()) {
                        Node v = parts.get(n);
                        if (original(v).equals(direction)) {
                            closed = true;
                            break;
                        }
                        if (original(v).equals("@"))
                            throw error(start, "CD label缺少结束 " + direction);
                        values.add(v);
                    }
                    if (!closed) throw error(start, "CD label缺少结束 " + direction);
                    labels[label] =
                            new Node(
                                    Kind.ROW,
                                    part.start,
                                    parts.get(n).end,
                                    "",
                                    values,
                                    null,
                                    null,
                                    null);
                }
            }
            String command =
                    direction.equals(">")
                            ? "cdrightarrow"
                            : direction.equals("<")
                                    ? "cdleftarrow"
                                    : direction.equals("A")
                                            ? "cduparrow"
                                            : direction.equals("V")
                                                    ? "cddownarrow"
                                                    : direction.equals("=")
                                                            ? "cdequal"
                                                            : direction.equals("|")
                                                                    ? "cdparallel"
                                                                    : "cdnone";
            cells.add(
                    new Node(
                            Kind.XARROW,
                            part.start,
                            parts.get(n).end,
                            command,
                            null,
                            labels[0],
                            labels[1],
                            null,
                            new DecorationSpec(command, false, true, false)));
        }
        cells.add(new Node(Kind.ROW, body.start, body.end, "", current, null, null, null));
        // 固定CD交替行：对象行保留末端cell，箭头行不产生两边对象cell。
        if (row % 2 != 0) {
            if (!cells.isEmpty()) cells.remove(0);
            if (!cells.isEmpty()) cells.remove(cells.size() - 1);
        }
        return cells;
    }
}
