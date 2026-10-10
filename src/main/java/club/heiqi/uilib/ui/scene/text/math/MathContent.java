package club.heiqi.uilib.ui.scene.text.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 源码只解析一次；内部 AST 不承诺为长期公开接口，布局与主题均不修改它。 */
public final class MathContent {
    public final String source;
    public final List<MathLayout.Diagnostic> diagnostics;
    final Node root;

    private MathContent(String source, Node root, List<MathLayout.Diagnostic> diagnostics) {
        this.source = source;
        this.root = root;
        this.diagnostics =
                Collections.unmodifiableList(new ArrayList<MathLayout.Diagnostic>(diagnostics));
    }

    public static MathContent parse(String source) {
        Objects.requireNonNull(source, "source");
        Parser parser = new Parser(source);
        try {
            if (source.length() > 16384)
                throw parser.error(0, source.length(), "公式超过16384个UTF-16单位");
            Node root = parser.row(false, false);
            return new MathContent(source, root, Collections.<MathLayout.Diagnostic>emptyList());
        } catch (ParseFailure failure) {
            return new MathContent(source, null, Collections.singletonList(failure.diagnostic));
        }
    }

    enum Kind {
        ROW,
        GLYPH,
        TEXT,
        EMBED,
        FRAC,
        BINOM,
        DELIMITED,
        DELIMITER,
        MIDDLE,
        ROOT,
        SCRIPTS,
        FONT,
        STYLE,
        SPACE,
        OPERATOR
    }

    static final class Node {
        final Kind kind;
        final int start, end;
        final String value;
        final List<Node> children;
        final Node first, second, third;

        Node(
                Kind kind,
                int start,
                int end,
                String value,
                List<Node> children,
                Node first,
                Node second,
                Node third) {
            this.kind = kind;
            this.start = start;
            this.end = end;
            this.value = value;
            this.children =
                    children == null
                            ? Collections.<Node>emptyList()
                            : Collections.unmodifiableList(new ArrayList<Node>(children));
            this.first = first;
            this.second = second;
            this.third = third;
        }
    }

    private static final class ParseFailure extends RuntimeException {
        final MathLayout.Diagnostic diagnostic;

        ParseFailure(int start, int end, String message) {
            super(message);
            diagnostic = new MathLayout.Diagnostic("parse", start, end, message);
        }
    }

    private static final class Parser {
        private static final Map<String, String> SYMBOLS = symbols();
        private static final Map<String, String> DELIMITERS = delimiters();
        final String source;
        final Set<String> embeds = new HashSet<String>();
        int index, depth;
        char terminator;

        Parser(String source) {
            this.source = source;
        }

        ParseFailure error(int start, int end, String message) {
            return new ParseFailure(start, end, message);
        }

        void space() {
            while (index < source.length() && Character.isWhitespace(source.codePointAt(index)))
                index += Character.charCount(source.codePointAt(index));
        }

        /** 错误区间与控制符消费都停在码点边界，不能把合法代理对切半。 */
        int nextCodePointEnd() {
            return index == source.length()
                    ? index
                    : index + Character.charCount(source.codePointAt(index));
        }

        Node node(Kind kind, int start, String value, Node first, Node second, Node third) {
            return new Node(kind, start, index, value, null, first, second, third);
        }

        Node row(boolean grouped, boolean degree) {
            return row(grouped ? '}' : degree ? ']' : (char) 0, -1);
        }

        /** 围合结构继承当前分组终点，middle只属于直接所在的围合层。 */
        Node row(char endToken, int leftStart) {
            int start = index;
            if (++depth > 64) throw error(start, index, "公式嵌套超过64层");
            char previousTerminator = terminator;
            terminator = endToken;
            List<Node> nodes = new ArrayList<Node>();
            while (true) {
                space();
                if (index == source.length()) {
                    if (leftStart >= 0) throw error(leftStart, index, "left缺少同组right");
                    if (endToken != 0) throw error(start, index, "分组未闭合");
                    break;
                }
                char ch = source.charAt(index);
                if (ch == endToken && endToken != 0) {
                    if (leftStart >= 0) throw error(index, index + 1, "left/right不能跨分组配对");
                    index++;
                    break;
                }
                if (leftStart >= 0 && commandAt("right")) break;
                if (leftStart >= 0 && commandAt("middle")) {
                    int marker = index;
                    command();
                    nodes.add(delimiter(Kind.MIDDLE, marker));
                    continue;
                }
                if (ch == '}') throw error(index, index + 1, "多余的闭合分组");
                nodes.add(postfix(atom()));
            }
            terminator = previousTerminator;
            depth--;
            return new Node(Kind.ROW, start, index, "", nodes, null, null, null);
        }

        Node delimited(int start) {
            Node left = delimiter(Kind.DELIMITER, start);
            Node body = row(terminator, start);
            int rightStart = index;
            command();
            Node right = delimiter(Kind.DELIMITER, rightStart);
            return node(Kind.DELIMITED, start, "", left, body, right);
        }

        Node delimiter(Kind kind, int marker) {
            space();
            int start = index;
            if (index == source.length()) throw error(start, start, "缺少定界token");
            String token;
            if (source.charAt(index) == (char) 92) token = String.valueOf((char) 92) + command();
            else {
                index = nextCodePointEnd();
                token = source.substring(start, index);
            }
            String value = DELIMITERS.get(token);
            if (value == null) throw error(start, index, "无效定界token: " + token);
            return node(kind, marker, value, null, null, null);
        }

        Node argument() {
            space();
            if (index == source.length()) throw error(index, index, "缺少命令参数");
            if (++depth > 64) throw error(index, index, "公式嵌套超过64层");
            Node value = atom();
            depth--;
            if (value.kind == Kind.STYLE || value.kind == Kind.SPACE)
                throw error(value.start, value.end, "参数需要数学原子或分组");
            return value;
        }

        Node group() {
            space();
            if (index == source.length() || source.charAt(index) != '{')
                throw error(index, nextCodePointEnd(), "命令需要花括号分组");
            int start = index++;
            Node value = row(true, false);
            return new Node(Kind.ROW, start, index, "", value.children, null, null, null);
        }

        Node postfix(Node base) {
            Node upper = null, lower = null;
            String limits = "default";
            boolean modified = false;
            while (true) {
                space();
                if (index == source.length()) break;
                char ch = source.charAt(index);
                if (ch == '^' || ch == '_') {
                    if (base.kind == Kind.STYLE || base.kind == Kind.SPACE)
                        throw error(index, index + 1, "脚本缺少基础原子");
                    int marker = index++;
                    if (ch == '^' && upper != null || ch == '_' && lower != null)
                        throw error(marker, index, "重复的同类脚本");
                    Node script = argument();
                    if (ch == '^') upper = script;
                    else lower = script;
                    modified = true;
                } else if (ch == '\\' && (commandAt("limits") || commandAt("nolimits"))) {
                    int marker = index;
                    String command = command();
                    if (base.kind != Kind.OPERATOR) throw error(marker, index, "上下限指令只能跟随算子");
                    if (!limits.equals("default")) throw error(marker, index, "重复的上下限指令");
                    limits = command;
                    modified = true;
                } else break;
            }
            return modified ? node(Kind.SCRIPTS, base.start, limits, base, upper, lower) : base;
        }

        boolean commandAt(String name) {
            String target = "\\" + name;
            int end = index + target.length();
            return source.startsWith(target, index)
                    && (end == source.length() || !asciiLetter(source.charAt(end)));
        }

        String command() {
            int start = ++index;
            if (index == source.length()) throw error(start - 1, index, "未完成的命令");
            if (asciiLetter(source.charAt(index))) {
                while (index < source.length() && asciiLetter(source.charAt(index))) index++;
            } else index = nextCodePointEnd();
            return source.substring(start, index);
        }

        String literalGroup() {
            space();
            int start = index;
            if (index == source.length() || source.charAt(index) != '{')
                throw error(start, nextCodePointEnd(), "命令需要花括号分组");
            index++;
            StringBuilder value = new StringBuilder();
            int nesting = 1;
            while (index < source.length()) {
                char ch = source.charAt(index++);
                if (ch == '\\'
                        && index < source.length()
                        && "{}\\".indexOf(source.charAt(index)) >= 0) {
                    value.append(source.charAt(index++));
                } else if (ch == '{') {
                    if (++nesting > 64) throw error(start, index, "文字分组嵌套超过64层");
                    value.append(ch);
                } else if (ch == '}') {
                    if (--nesting == 0) return value.toString();
                    value.append(ch);
                } else value.append(ch);
            }
            throw error(start, index, "文字分组未闭合");
        }

        Node atom() {
            space();
            int start = index;
            if (index == source.length()) throw error(index, index, "缺少数学原子");
            int cp = source.codePointAt(index);
            if (cp == '{') return group();
            if (cp == '^' || cp == '_' || cp == '}') throw error(index, index + 1, "数学原子缺失");
            if (cp >= 0xd800 && cp <= 0xdfff) throw error(index, index + 1, "孤立UTF-16代理项");
            if (cp != '\\') {
                index += Character.charCount(cp);
                return node(Kind.GLYPH, start, new String(Character.toChars(cp)), null, null, null);
            }
            String name = command();
            if (name.equals("left")) return delimited(start);
            if (name.equals("middle") || name.equals("right"))
                throw error(start, index, name + "缺少本组left");
            if (name.equals("frac")
                    || name.equals("dfrac")
                    || name.equals("tfrac")
                    || name.equals("binom")) {
                Node numerator = group(), denominator = group();
                return node(
                        name.equals("binom") ? Kind.BINOM : Kind.FRAC,
                        start,
                        name,
                        numerator,
                        denominator,
                        null);
            }
            if (name.equals("sqrt")) {
                space();
                Node degree = null;
                if (index < source.length() && source.charAt(index) == '[') {
                    index++;
                    degree = row(false, true);
                }
                Node radicand = group();
                return node(Kind.ROOT, start, name, radicand, degree, null);
            }
            if (name.equals("text"))
                return node(Kind.TEXT, start, literalGroup(), null, null, null);
            if (name.equals("embed")) {
                String id = literalGroup();
                if (id.isEmpty()) throw error(start, index, "嵌入标识为空");
                if (!embeds.add(id)) throw error(start, index, "重复嵌入标识: " + id);
                return node(Kind.EMBED, start, id, null, null, null);
            }
            if (name.equals("mathrm")
                    || name.equals("mathit")
                    || name.equals("mathbf")
                    || name.equals("mathnormal"))
                return node(Kind.FONT, start, name, group(), null, null);
            if (name.equals("displaystyle")
                    || name.equals("textstyle")
                    || name.equals("scriptstyle")
                    || name.equals("scriptscriptstyle"))
                return node(Kind.STYLE, start, name, null, null, null);
            if (name.equals(",")
                    || name.equals(":")
                    || name.equals(";")
                    || name.equals("!")
                    || name.equals("quad")
                    || name.equals("qquad")
                    || name.equals(" ")) return node(Kind.SPACE, start, name, null, null, null);
            if (name.equals("int")
                    || name.equals("sum")
                    || name.equals("prod")
                    || name.equals("sin")
                    || name.equals("cos")
                    || name.equals("tan")
                    || name.equals("log")
                    || name.equals("ln")
                    || name.equals("exp")
                    || name.equals("lim"))
                return node(Kind.OPERATOR, start, name, null, null, null);
            if (name.equals("operatorname")) {
                boolean limits = index < source.length() && source.charAt(index) == '*';
                if (limits) index++;
                return node(
                        Kind.OPERATOR,
                        start,
                        (limits ? "*" : "@") + literalGroup(),
                        null,
                        null,
                        null);
            }
            String symbol = SYMBOLS.get(name);
            if (symbol != null) return node(Kind.GLYPH, start, symbol, null, null, null);
            throw error(start, index, "本阶段未支持命令: " + name);
        }

        static boolean asciiLetter(char ch) {
            return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z';
        }

        /** 每项对应一个明确token；普通花括号仍是分组，必须转义后用作定界符。 */
        static Map<String, String> delimiters() {
            Map<String, String> values = new HashMap<String, String>();
            String[][] aliases = {
                {".", "."},
                {"(", "(", "lparen"},
                {")", ")", "rparen"},
                {"[", "[", "lbrack"},
                {"]", "]", "rbrack"},
                {"{", "lbrace", "{"},
                {"}", "rbrace", "}"},
                {"|", "|", "vert", "lvert", "rvert"},
                {"‖", "‖", "Vert", "lVert", "rVert", "|"},
                {"⟨", "⟨", "<", "langle", "lt"},
                {"⟩", "⟩", ">", "rangle", "gt"},
                {"⌊", "⌊", "lfloor"},
                {"⌋", "⌋", "rfloor"},
                {"⌈", "⌈", "lceil"},
                {"⌉", "⌉", "rceil"},
                {"↑", "↑", "uparrow"},
                {"↓", "↓", "downarrow"},
                {"↕", "↕", "updownarrow"},
                {"⇑", "⇑", "Uparrow"},
                {"⇓", "⇓", "Downarrow"},
                {"⇕", "⇕", "Updownarrow"}
            };
            for (String[] item : aliases) {
                String glyph = item[0];
                for (int i = 1; i < item.length; i++) {
                    String alias = item[i];
                    // 花括号及第二个bar别名只接受控制符，避免把分组/单bar改成另一语义。
                    if (!alias.equals("{")
                            && !alias.equals("}")
                            && !(glyph.equals("‖") && alias.equals("|"))
                            && !asciiLetter(alias.charAt(0))) values.put(alias, glyph);
                    if (!alias.equals(".")
                            && (asciiLetter(alias.charAt(0))
                                    || alias.equals("{")
                                    || alias.equals("}")
                                    || glyph.equals("‖") && alias.equals("|")))
                        values.put(String.valueOf((char) 92) + alias, glyph);
                }
            }
            return Collections.unmodifiableMap(values);
        }

        static Map<String, String> symbols() {
            Map<String, String> values = new HashMap<String, String>();
            String[] names = {
                "alpha",
                "beta",
                "gamma",
                "delta",
                "theta",
                "pi",
                "sigma",
                "omega",
                "infty",
                "cdot",
                "times",
                "pm",
                "leq",
                "geq",
                "neq",
                "rightarrow",
                "to"
            };
            String[] glyphs = {
                "α", "β", "γ", "δ", "θ", "π", "σ", "ω", "∞", "⋅", "×", "±", "≤", "≥", "≠", "→", "→"
            };
            for (int i = 0; i < names.length; i++) values.put(names[i], glyphs[i]);
            for (String value : new String[] {"{", "}", "_", "^", "%", "#", "$", "&", "\\"})
                values.put(value, value);
            return Collections.unmodifiableMap(values);
        }
    }
}
