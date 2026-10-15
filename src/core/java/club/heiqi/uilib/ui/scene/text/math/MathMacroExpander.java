package club.heiqi.uilib.ui.scene.text.math;

import club.heiqi.uilib.ui.scene.text.math.MathLexer.Token;
import club.heiqi.uilib.ui.scene.text.math.MathMacros.Definition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 自有 token 展开器。固定参照只提供行为事实，生产不执行 JS 或访问文件。 */
public final class MathMacroExpander {
    public static final int MAX_EXPAND = 1000;

    private MathMacroExpander() {}

    public static final class Diagnostic {
        public final int sourceStart, sourceEnd;
        public final String message;

        Diagnostic(Token token, String message) {
            sourceStart = token.sourceStart;
            sourceEnd = token.sourceEnd;
            this.message = message;
        }
    }

    public static final class Message {
        public final String command, text;
        public final int sourceStart, sourceEnd;

        Message(Token token, String text) {
            command = token.text;
            this.text = text;
            sourceStart = token.sourceStart;
            sourceEnd = token.sourceEnd;
        }
    }

    /** 行标签定位使用环境起始原范围和行序号，不暴露展开串索引。 */
    public static final class RowTag {
        public final String environment;
        public final int environmentStart, environmentOrdinal, row, sourceStart, sourceEnd;
        public final boolean automatic;
        public final List<Token> tokens;

        RowTag(
                String environment,
                int start,
                int ordinal,
                int row,
                Token token,
                List<Token> tokens,
                boolean automatic) {
            this.environment = environment;
            environmentStart = start;
            environmentOrdinal = ordinal;
            this.row = row;
            sourceStart = token.sourceStart;
            sourceEnd = token.sourceEnd;
            this.tokens = immutable(tokens);
            this.automatic = automatic;
        }
    }

    /** 宏读取发生时的逐环境事实；Task4 按 environmentOrdinal 验证值并构造 AST。 */
    public static final class EnvironmentHeader {
        public final String environment, arrayStretchText;
        public final int environmentOrdinal;
        public final boolean arrayStretchFromMacro;
        public final List<Token> arrayStretchTokens;

        EnvironmentHeader(
                String environment,
                int ordinal,
                String text,
                List<Token> tokens,
                boolean fromMacro) {
            this.environment = environment;
            environmentOrdinal = ordinal;
            arrayStretchText = text;
            arrayStretchTokens = immutable(tokens);
            arrayStretchFromMacro = fromMacro;
        }
    }

    public static final class Result {
        public final List<Token> tokens, tagTokens;
        public final MathMacros nextMacros;
        public final List<Diagnostic> diagnostics;
        public final List<Message> messages;
        public final List<RowTag> rowTags;
        public final List<EnvironmentHeader> environmentHeaders;

        Result(
                List<Token> tokens,
                List<Token> tag,
                MathMacros next,
                List<Diagnostic> ds,
                List<Message> ms,
                List<RowTag> rows,
                List<EnvironmentHeader> headers) {
            this.tokens = immutable(tokens);
            tagTokens = immutable(tag);
            nextMacros = next;
            diagnostics = immutable(ds);
            messages = immutable(ms);
            rowTags = immutable(rows);
            environmentHeaders = immutable(headers);
        }
    }

    static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public static Result expand(String source) {
        return expand(source, MathMacros.empty());
    }

    public static Result expand(String source, MathMacros macros) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(macros, "macros");
        Cursor cursor = null;
        List<Token> output = new ArrayList<Token>(), tag = new ArrayList<Token>();
        List<Diagnostic> ds = new ArrayList<Diagnostic>();
        try {
            if (source.length() > MathExpansionBudget.SOURCE_UNITS)
                throw failure(new Token("", 0, source.length(), source), "公式超过16384个UTF-16单位");
            cursor = new Cursor(source, macros);
            cursor.expandSequence(output, null);
            Definition td = cursor.lookup("\\df@tag");
            if (td != null) cursor.appendAll(tag, cursor.expandList(td.body, false, false), cursor.eof);
        } catch (MathLexer.Failure failure) {
            ds.add(new Diagnostic(failure.token, failure.getMessage()));
        } finally {
            if (cursor != null) cursor.endGroups();
        }
        MathMacros next = macros;
        List<Message> messages = Collections.emptyList();
        if (cursor != null) {
            cursor.current.remove("\\df@tag");
            cursor.current.remove("\\current@color");
            cursor.current.remove("\\color");
            next = new MathMacros(cursor.current);
            messages = cursor.messages;
        }
        return new Result(
                output,
                tag,
                next,
                ds,
                messages,
                cursor == null ? Collections.<RowTag>emptyList() : cursor.rowTags,
                cursor == null
                        ? Collections.<EnvironmentHeader>emptyList()
                        : cursor.environmentHeaders);
    }

    /** Task4 可按 parser 参数模式拉取 token，位置始终是原文范围。 */
    public static final class Cursor {
        private final MathExpansionBudget budget = new MathExpansionBudget();
        private Deque<Token> input = new ArrayDeque<Token>();
        private MathLexer.Scanner scanner;
        private boolean urlMode;
        private final Map<String, Definition> current = new LinkedHashMap<String, Definition>();
        private final Deque<Map<String, Definition>> groups =
                new ArrayDeque<Map<String, Definition>>();
        private final List<Message> messages = new ArrayList<Message>();
        private final List<RowTag> rowTags = new ArrayList<RowTag>();
        private final List<EnvironmentHeader> environmentHeaders =
                new ArrayList<EnvironmentHeader>();
        private final Deque<Middle> middles = new ArrayDeque<Middle>();
        private int expansions, expansionDepth, environmentSerial;
        private boolean textMode;
        private Token active, argumentEnd;
        private final Token eof;
        private final Deque<EnvironmentScope> environments = new ArrayDeque<EnvironmentScope>();

        public Cursor(String source, MathMacros macros) {
            Objects.requireNonNull(source, "source");
            current.putAll(Objects.requireNonNull(macros, "macros").definitions);
            current.remove("\\df@tag");
            eof = new Token("EOF", source.length(), source.length(), source);
            scanner = new MathLexer.Scanner(source);
        }

        public void switchMode(boolean text) {
            textMode = text;
        }

        public Token future() {
            if (input.isEmpty() && scanner != null) {
                Token token = scanner.next(!urlMode);
                if (token != null) pushToken(token);
            }
            return input.isEmpty() ? eof : input.peekFirst();
        }

        public Token popToken() {
            Token token = future();
            if (input.isEmpty()) return eof;
            budget.work(MathExpansionBudget.weight(token), origin(token));
            budget.release(1);
            return input.removeFirst();
        }

        public void pushToken(Token token) {
            budget.enqueue(1, origin(token));
            input.addFirst(token);
        }

        private void push(List<Token> tokens) {
            budget.enqueue(tokens.size(), origin(tokens.isEmpty() ? eof : tokens.get(0)));
            for (int i = tokens.size() - 1; i >= 0; i--) input.addFirst(tokens.get(i));
        }

        private Token origin(Token token) {
            return active == null ? token : active;
        }

        private void append(List<Token> out, Token token) {
            budget.work(MathExpansionBudget.weight(token), origin(token));
            out.add(token);
        }

        private void appendAll(List<Token> out, List<Token> tokens, Token call) {
            budget.work(copyCost(tokens, call), origin(call));
            out.addAll(tokens);
        }

        private long copyCost(List<Token> tokens, Token call) {
            budget.work(tokens.size(), origin(call));
            long cost = 0;
            for (Token token : tokens) cost += MathExpansionBudget.weight(token);
            return cost;
        }

        public void consumeSpaces() {
            while (future().text.equals(" ")) popToken();
        }

        public void beginGroup() {
            if (groups.size() >= 64)
                throw failure(active == null ? future() : active, "宏作用域嵌套超过64层");
            groups.push(new LinkedHashMap<String, Definition>());
        }

        public void endGroup() {
            if (groups.isEmpty()) throw failure(active == null ? future() : active, "多余的宏作用域结束");
            for (Map.Entry<String, Definition> entry : groups.pop().entrySet()) {
                if (entry.getValue() == null) current.remove(entry.getKey());
                else current.put(entry.getKey(), entry.getValue());
            }
        }

        public void endGroups() {
            while (!groups.isEmpty()) endGroup();
        }

        private void set(String name, Definition value, boolean global) {
            if (global) {
                for (Map<String, Definition> group : groups) group.remove(name);
            } else if (!groups.isEmpty() && !groups.peek().containsKey(name))
                groups.peek().put(name, current.get(name));
            if (value == null) current.remove(name);
            else current.put(name, value);
        }

        private Definition lookup(String name) {
            if (urlMode && name.equals("~")) return null;
            if (current.containsKey(name)) return current.get(name);
            MathCommandCatalog.Macro macro = MathCommandCatalog.findMacro(name);
            if (macro == null) return null;
            if ("string".equals(macro.kind)) return Definition.string(macro.expansion);
            return new Definition(Collections.<Token>emptyList(), 0, null, name, false);
        }

        private boolean defined(String name) {
            return lookup(name) != null
                    || catalogDefined(name)
                    || name.equals("^")
                    || name.equals("_")
                    || name.equals("\\limits")
                    || name.equals("\\nolimits")
                    || name.equals("\\embed");
        }

        private boolean expandable(String name) {
            Definition d = lookup(name);
            return d != null ? !d.unexpandable : catalogExpandable(name);
        }

        private void count(int amount, Token token) {
            expansions += amount;
            if (expansions > MAX_EXPAND)
                throw failure(
                        token,
                        "Too many expansions: infinite loop or need to increase maxExpand setting");
        }

        /** 不展开参数；定界参数允许空串，且只在同组深度匹配。 */
        public List<Token> consumeArg() {
            return consumeArg(Collections.<String>emptyList());
        }

        private List<Token> consumeArg(List<String> delimiters) {
            boolean delimited = !delimiters.isEmpty();
            if (!delimited) consumeSpaces();
            List<Token> result = new ArrayList<Token>();
            Token start = future();
            int depth = 0, match = 0;
            do {
                Token token = popToken();
                if (token.text.equals("EOF"))
                    throw failure(
                            active == null ? token : active,
                            "Unexpected end of input in a macro argument");
                append(result, token);
                argumentEnd = token;
                if (token.text.equals("{")) depth++;
                else if (token.text.equals("}")) depth--;
                if (depth < 0) throw failure(token, "Extra }");
                if (depth > 64) throw failure(token, "宏参数嵌套超过64层");
                if (delimited) {
                    if ((depth == 0 || depth == 1 && delimiters.get(match).equals("{"))
                            && token.text.equals(delimiters.get(match))) {
                        match++;
                        if (match == delimiters.size()) {
                            result.subList(result.size() - match, result.size()).clear();
                            break;
                        }
                    } else match = 0;
                }
            } while (depth != 0 || delimited);
            if (!result.isEmpty()
                    && start.text.equals("{")
                    && result.get(result.size() - 1).text.equals("}")) {
                result.remove(result.size() - 1);
                result.remove(0);
            }
            return result;
        }

        private List<List<Token>> args(int n, List<List<String>> delimiters) {
            if (!delimiters.isEmpty())
                for (String expected : delimiters.get(0)) {
                    Token token = popToken();
                    if (!token.text.equals(expected))
                        throw failure(token, "Use of the macro doesn't match its definition");
                }
            List<List<Token>> values = new ArrayList<List<Token>>();
            for (int i = 0; i < n; i++)
                values.add(
                        consumeArg(
                                delimiters.isEmpty()
                                        ? Collections.<String>emptyList()
                                        : delimiters.get(i + 1)));
            return values;
        }

        private List<Token> substitute(Definition d, Token call) {
            List<List<Token>> values = args(d.numArgs, d.delimiters);
            int size = reserveSubstitution(d, values, call);
            List<Token> body = new ArrayList<Token>(size);
            for (int i = 0; i < d.body.size(); i++) {
                Token token = d.body.get(i);
                if (d.numArgs > 0 && token.text.equals("#")) {
                    Token placeholder = d.body.get(++i);
                    if (placeholder.text.equals("#")) body.add(placeholder.at(call));
                    else body.addAll(values.get(argumentNumber(placeholder, values.size(), call)));
                } else body.add(token.at(call));
            }
            return body;
        }

        /** 先计算引用数、文本复制成本并验证占位符；这一阶段不复制实参。 */
        private int reserveSubstitution(Definition d, List<List<Token>> values, Token call) {
            budget.work(d.body.size(), call);
            long[] argumentCosts = new long[values.size()];
            for (int i = 0; i < values.size(); i++) argumentCosts[i] = copyCost(values.get(i), call);
            long size = 0, cost = 0;
            for (int i = 0; i < d.body.size(); i++) {
                Token token = d.body.get(i);
                if (d.numArgs > 0 && token.text.equals("#")) {
                    if (++i == d.body.size())
                        throw failure(token.at(call), "Incomplete placeholder at end of macro body");
                    Token placeholder = d.body.get(i);
                    if (placeholder.text.equals("#")) {
                        size++;
                        cost++;
                    } else {
                        int index = argumentNumber(placeholder, values.size(), call);
                        size += values.get(index).size();
                        cost += argumentCosts[index];
                    }
                } else {
                    size++;
                    cost += MathExpansionBudget.weight(token);
                }
            }
            // 替换参数可以倍增，必须先预留完整生成体，不能在 addAll 扩容后才计次数。
            budget.generate(size, call);
            budget.work(cost, call);
            return (int) size;
        }

        private int argumentNumber(Token placeholder, int count, Token call) {
            if (placeholder.text.length() == 1) {
                int index = placeholder.text.charAt(0) - '1';
                if (index >= 0 && index < 9 && index < count) return index;
            }
            throw failure(placeholder.at(call), "Not a valid argument number");
        }

        private boolean expandOnce(boolean strict) {
            Token call = popToken(), previous = active;
            active = call;
            try {
                Definition d = call.noexpand ? null : lookup(call.text);
                Middle middle = call.noexpand || middles.isEmpty() ? null : middles.peek();
                if (middle != null && middle.applies(call.text)) {
                    count(1, call);
                    push(middle.expand(call));
                    return true;
                }
                if (d == null || strict && d.unexpandable) {
                    if (strict
                            && !call.noexpand
                            && d == null
                            && call.text.startsWith("\\")
                            && !defined(call.text))
                        throw failure(call, "Undefined control sequence: " + call.text);
                    pushToken(call);
                    return false;
                }
                count(1, call);
                push(d.function == null ? substitute(d, call) : function(d.function, call));
                return true;
            } finally {
                active = previous;
            }
        }

        public Token nextToken() {
            while (expandOnce(false)) {}
            Token token = popToken();
            return token.treatAsRelax ? token.text("\\relax") : token;
        }

        private List<Token> generated(String value, Token call) {
            List<Token> values = new ArrayList<Token>();
            MathLexer.Scanner generatedScanner = new MathLexer.Scanner(value);
            Token token;
            while ((token = generatedScanner.next(true)) != null) {
                budget.generate(1, call);
                append(values, token.at(call));
            }
            return values;
        }

        private List<Token> stringExpansion(String value, Token call) {
            return substitute(Definition.string(value), call);
        }

        private List<Token> function(String name, Token call) {
            if (name.equals("\\noexpand")) {
                Token token = popToken();
                return Collections.singletonList(
                        expandable(token.text) ? token.flags(true, true) : token);
            }
            if (name.equals("\\expandafter")) {
                Token token = popToken();
                expandOnce(true);
                return Collections.singletonList(token);
            }
            if (name.equals("\\@firstoftwo")
                    || name.equals("\\@secondoftwo")
                    || name.equals("\\TextOrMath")) {
                List<List<Token>> a = args(2, Collections.<List<String>>emptyList());
                return a.get(
                        name.equals("\\@secondoftwo") || name.equals("\\TextOrMath") && !textMode
                                ? 1
                                : 0);
            }
            if (name.equals("\\@ifnextchar")) {
                List<List<Token>> a = args(3, Collections.<List<String>>emptyList());
                consumeSpaces();
                return a.get(
                        a.get(0).size() == 1 && a.get(0).get(0).text.equals(future().text) ? 1 : 2);
            }
            if (name.equals("\\char")) return character(call);
            if (name.equals("\\newcommand")
                    || name.equals("\\renewcommand")
                    || name.equals("\\providecommand")) {
                newcommand(name, call);
                return Collections.emptyList();
            }
            if (name.equals("\\message") || name.equals("\\errmessage") || name.equals("\\show")) {
                messages.add(
                        new Message(
                                call,
                                name.equals("\\show") ? popToken().text : join(consumeArg())));
                return Collections.emptyList();
            }
            if (name.equals("\\dots")) {
                expandOnce(false);
                return generated(dots(future().text), call);
            }
            if (name.equals("\\dotso") || name.equals("\\dotsc") || name.equals("\\cdots")) {
                boolean after =
                        spaceAfterDots(future().text)
                                && !(name.equals("\\dotsc") && future().text.equals(","));
                return generated(
                        (name.equals("\\cdots") ? "\\@cdots" : "\\ldots") + (after ? "\\," : ""),
                        call);
            }
            if (name.equals("\\tag@literal")) {
                if (lookup("\\df@tag") != null) throw failure(call, "Multiple \\tag");
                return stringExpansion("\\gdef\\df@tag{\\text{#1}}", call);
            }
            if (name.equals("\\bra@ket") || name.equals("\\bra@set"))
                return braket(name.equals("\\bra@set"), call);
            throw failure(call, "未实现的 catalog 函数宏: " + name);
        }

        private void newcommand(String command, Token call) {
            List<Token> nt = consumeArg();
            if (nt.size() != 1)
                throw failure(call, "\\newcommand's first argument must be a macro name");
            String name = nt.get(0).text;
            boolean exists = defined(name);
            if (exists && command.equals("\\newcommand"))
                throw failure(call, "\\newcommand attempting to redefine " + name);
            if (!exists && command.equals("\\renewcommand"))
                throw failure(call, "\\renewcommand when command does not yet exist: " + name);
            int n = 0;
            List<Token> body = consumeArg();
            if (body.size() == 1 && body.get(0).text.equals("[")) {
                StringBuilder number = new StringBuilder();
                Token token = nextToken();
                while (!token.text.equals("]") && !token.text.equals("EOF")) {
                    number.append(token.text);
                    token = nextToken();
                }
                String value = number.toString().trim();
                if (!value.matches("[0-9]+"))
                    throw failure(call, "Invalid number of arguments: " + value);
                try {
                    n = Integer.parseInt(value);
                } catch (NumberFormatException bad) {
                    throw failure(call, "Invalid number of arguments: " + value);
                }
                if (n > MAX_EXPAND) throw failure(call, "宏参数数目超过展开资源界限");
                // 固定 KaTeX 未实现第二个 [default]，继续原样消费一个 token/分组。
                body = consumeArg();
            }
            if (!(exists && command.equals("\\providecommand"))) {
                budget.work(body.size(), call);
                set(name, new Definition(body, n, null, null, false), false);
            }
        }

        private List<Token> character(Token call) {
            Token t = popToken();
            int base = 10;
            long number;
            if (t.text.equals("'")) {
                base = 8;
                t = popToken();
            } else if (t.text.equals("\"")) {
                base = 16;
                t = popToken();
            } else if (t.text.equals(String.valueOf((char) 96))) {
                t = popToken();
                if (t.text.equals("EOF")) throw failure(call, "char missing argument");
                number = t.text.charAt(t.text.startsWith("\\") ? 1 : 0);
                return generated("\\@char{" + number + "}", call);
            }
            int digit = digit(t.text);
            if (digit < 0 || digit >= base)
                throw failure(call, "Invalid base-" + base + " digit " + t.text);
            number = digit;
            while ((digit = digit(future().text)) >= 0 && digit < base) {
                if (number > (0x10ffffL - digit) / base)
                    throw failure(call, "char codepoint 超出 Unicode 范围");
                number = number * base + digit;
                popToken();
            }
            return generated("\\@char{" + number + "}", call);
        }

        private static int digit(String text) {
            return text.length() == 1 && "0123456789abcdefABCDEF".indexOf(text.charAt(0)) >= 0
                    ? Character.digit(text.charAt(0), 16)
                    : -1;
        }

        /** primitive assignment 由宏层统一处理，Task4 不重复修改 namespace。 */
        public boolean assignment(Token token) {
            return assignment(token, false);
        }

        private boolean assignment(Token token, boolean global) {
            String command = token.text;
            Token saved = active;
            active = token;
            try {
                if (command.equals("\\global") || command.equals("\\long")) {
                    consumeSpaces();
                    Token next = nextToken();
                    if (!prefixTarget(next.text))
                        throw failure(next, "Invalid token after macro prefix");
                    return assignment(next, global || command.equals("\\global"));
                }
                if (command.equals("\\def")
                        || command.equals("\\gdef")
                        || command.equals("\\edef")
                        || command.equals("\\xdef")) {
                    define(command, token, global);
                    return true;
                }
                if (command.equals("\\let") || command.equals("\\futurelet")) {
                    String name = control(popToken());
                    Token rhs, middle = null;
                    if (command.equals("\\let")) {
                        consumeSpaces();
                        rhs = popToken();
                        if (rhs.text.equals("=")) {
                            rhs = popToken();
                            if (rhs.text.equals(" ")) rhs = popToken();
                        }
                    } else {
                        middle = popToken();
                        rhs = popToken();
                    }
                    Definition d = lookup(rhs.text);
                    if (d == null)
                        d =
                                new Definition(
                                        Collections.singletonList(rhs.flags(true, false)),
                                        0,
                                        null,
                                        null,
                                        !expandable(rhs.text));
                    set(name, d, global);
                    if (middle != null) {
                        pushToken(rhs);
                        pushToken(middle);
                    }
                    return true;
                }
                return false;
            } finally {
                active = saved;
            }
        }

        private static boolean prefixTarget(String t) {
            return t.equals("\\global")
                    || t.equals("\\long")
                    || t.equals("\\def")
                    || t.equals("\\gdef")
                    || t.equals("\\edef")
                    || t.equals("\\xdef")
                    || t.equals("\\let")
                    || t.equals("\\futurelet");
        }

        private String control(Token t) {
            if (t.text.matches("[\\\\{}$&#^_]") || t.text.equals("EOF"))
                throw failure(t, "Expected a control sequence");
            return t.text;
        }

        private void define(String command, Token call, boolean global) {
            String name = control(popToken());
            int n = 0;
            Token insert = null;
            List<List<String>> delimiters = new ArrayList<List<String>>();
            delimiters.add(new ArrayList<String>());
            while (!future().text.equals("{")) {
                Token t = popToken();
                if (t.text.equals("#")) {
                    if (future().text.equals("{")) {
                        insert = future();
                        delimiters.get(n).add("{");
                        break;
                    }
                    t = popToken();
                    if (!t.text.matches("[1-9]")) throw failure(t, "Invalid argument number");
                    if (Integer.parseInt(t.text) != n + 1)
                        throw failure(t, "Argument number out of order");
                    n++;
                    delimiters.add(new ArrayList<String>());
                } else if (t.text.equals("EOF")) throw failure(call, "Expected a macro definition");
                else delimiters.get(n).add(t.text);
            }
            List<Token> body = consumeArg();
            if (insert != null) append(body, insert);
            if (command.equals("\\edef") || command.equals("\\xdef"))
                body = expandList(body, true, false);
            budget.work(body.size(), call);
            set(
                    name,
                    new Definition(body, n, delimiters, null, false),
                    global || command.equals("\\gdef") || command.equals("\\xdef"));
        }

        private List<Token> expandList(List<Token> body, boolean strict, boolean scope) {
            if (++expansionDepth > 64) throw failure(active == null ? eof : active, "宏递归嵌套超过64层");
            Deque<Token> saved = input;
            MathLexer.Scanner savedScanner = scanner;
            budget.enqueue(body.size(), origin(body.isEmpty() ? eof : body.get(0)));
            input = new ArrayDeque<Token>(body);
            scanner = null;
            List<Token> out = new ArrayList<Token>();
            if (scope) beginGroup();
            try {
                if (strict) {
                    while (!input.isEmpty())
                        if (!expandOnce(true)) {
                            Token token = popToken();
                            // noexpand 临时保护与 let 永久快照不能混同。
                            append(out, token.treatAsRelax ? token.flags(false, false) : token);
                        }
                    count(out.size(), active == null ? eof : active);
                } else expandSequence(out, null);
            } finally {
                if (scope) endGroup();
                budget.release(input.size());
                input = saved;
                scanner = savedScanner;
                expansionDepth--;
            }
            return out;
        }

        private void expandSequence(List<Token> out, String groupEnd) {
            while (!future().text.equals("EOF")) {
                Token token = nextToken();
                if (token.text.equals("EOF")) break;
                if (groupEnd != null
                        && token.text.equals(groupEnd)
                        && !groupEnd.equals("}")
                        && !groupEnd.equals("\\endgroup")) {
                    append(out, token);
                    return;
                }
                if (textMode && (token.text.equals("$") || token.text.equals("\\("))) {
                    append(out, token);
                    textMode = false;
                    try {
                        expandSequence(out, token.text.equals("$") ? "$" : "\\)");
                    } finally {
                        textMode = true;
                    }
                    continue;
                }
                if (assignment(token)) continue;
                if (token.text.equals("{") || token.text.equals("\\begingroup")) {
                    beginGroup();
                    append(out, token);
                    expandSequence(out, token.text.equals("{") ? "}" : "\\endgroup");
                    continue;
                }
                if (token.text.equals("}") || token.text.equals("\\endgroup")) {
                    if (groupEnd == null || !token.text.equals(groupEnd))
                        throw failure(token, "分组闭合符不匹配");
                    append(out, token);
                    endGroup();
                    return;
                }
                if (environmentBoundary(token, out)) continue;
                append(out, token);
                commandArguments(this, token, out);
            }
            if (groupEnd != null)
                throw failure(out.isEmpty() ? eof : out.get(out.size() - 1), "分组未闭合");
        }

        private final class EnvironmentScope {
            final String name;
            final int cellDepth;
            final boolean singleRow, tags, autoTag;
            final Token start;
            final int ordinal;
            int row;

            EnvironmentScope(String name, Token start) {
                this.name = name;
                this.start = start;
                ordinal = environmentSerial++;
                cellDepth = groups.size();
                singleRow = name.equals("equation") || name.equals("equation*");
                tags =
                        contains(
                                "align align* alignat alignat* gather gather* equation equation*",
                                name);
                autoTag = tags && !name.endsWith("*");
                beginRow();
            }

            void beginRow() {
                if (autoTag) set("\\@eqnsw", Definition.string("1"), true);
            }

            void endRow(Token boundary) {
                if (!tags) return;
                Definition tag = lookup("\\df@tag");
                List<Token> body =
                        tag == null
                                ? Collections.<Token>emptyList()
                                : expandList(tag.body, false, false);
                Definition eqnsw = lookup("\\@eqnsw");
                boolean automatic =
                        tag == null && autoTag && eqnsw != null && join(eqnsw.body).equals("1");
                Token origin = body.isEmpty() ? boundary : body.get(0);
                rowTags.add(
                        new RowTag(
                                name, start.sourceStart, ordinal, row++, origin, body, automatic));
                if (tag != null) set("\\df@tag", null, true);
            }
        }

        /** 固定 handler 常量不读取 arraystretch；其它 parseArray 在 env group 内读取。 */
        private EnvironmentHeader arrayHeader(String name, Token call, int ordinal) {
            if (contains("smallmatrix subarray", name))
                return new EnvironmentHeader(
                        name, ordinal, "0.5", Collections.<Token>emptyList(), false);
            if (contains("cases dcases rcases drcases", name))
                return new EnvironmentHeader(
                        name, ordinal, "1.2", Collections.<Token>emptyList(), false);
            if (name.equals("CD"))
                return new EnvironmentHeader(
                        name, ordinal, "1", Collections.<Token>emptyList(), false);
            if (lookup("\\arraystretch") == null)
                return new EnvironmentHeader(
                        name, ordinal, "1", Collections.<Token>emptyList(), false);
            List<Token> tokens = expandList(generated("\\arraystretch", call), true, false);
            return new EnvironmentHeader(name, ordinal, join(tokens), tokens, true);
        }

        /** starred matrix 的 handler.fetch 窗口在 env namespace 前，且不执行 primitive。 */
        private void matrixHeader(List<Token> out, Token call) {
            Token token = nextToken();
            while (token.text.equals(" ")) token = nextToken();
            if (!token.text.equals("[")) {
                pushToken(token);
                return;
            }
            append(out, token);
            do {
                token = nextToken();
                if (token.text.equals("EOF")) throw failure(call, "matrix可选头部未闭合");
                append(out, token);
            } while (!token.text.equals("]"));
        }

        /** 只管理宏namespace，环境节点与列数语义由 Task4 消费。 */
        private boolean environmentBoundary(Token token, List<Token> out) {
            if (token.text.equals("\\begin") || token.text.equals("\\end")) {
                int declarationAt = out.size();
                append(out, token);
                int at = out.size();
                argumentTokens("text", false, token, out);
                String name = join(out.subList(at + 1, out.size() - 1));
                if (token.text.equals("\\begin")) {
                    MathCommandCatalog.Environment env = MathCommandCatalog.findEnvironment(name);
                    if (env == null) throw failure(token, "No such environment: " + name);
                    for (int i = 0; i < env.numOptionalArgs + env.numArgs; i++)
                        argumentTokens(
                                i < env.argTypes.size() ? env.argTypes.get(i) : null,
                                i < env.numOptionalArgs,
                                token,
                                out);
                    if (contains("matrix* pmatrix* bmatrix* Bmatrix* vmatrix* Vmatrix*", name))
                        matrixHeader(out, token);
                    beginGroup();
                    if (!name.equals("equation") && !name.equals("equation*"))
                        set("\\cr", Definition.string("\\\\\\relax"), false);
                    EnvironmentHeader header = arrayHeader(name, token, environmentSerial);
                    beginGroup();
                    EnvironmentScope scope = new EnvironmentScope(name, token);
                    environmentHeaders.add(header);
                    environments.push(scope);
                    out.set(declarationAt, token.environment(scope.ordinal));
                } else if (!environments.isEmpty()) {
                    EnvironmentScope env = environments.peek();
                    if (!env.name.equals(name))
                        throw failure(token, "Mismatch environment: " + env.name + " / " + name);
                    if (groups.size() != env.cellDepth) throw failure(token, "环境内分组未闭合");
                    out.set(declarationAt, token.environment(env.ordinal));
                    endGroup();
                    env.endRow(token);
                    endGroup();
                    environments.pop();
                }
                return true;
            }
            if (!environments.isEmpty()) {
                EnvironmentScope env = environments.peek();
                boolean row = token.text.equals("\\\\") && !env.singleRow;
                if ((token.text.equals("&") && !env.name.equals("CD") || row)
                        && groups.size() == env.cellDepth) {
                    endGroup();
                    beginGroup();
                    if (row) {
                        append(out, token);
                        if (!env.name.equals("CD")) rowSize(out, token);
                        env.endRow(token);
                        env.beginRow();
                        return true;
                    }
                }
            }
            return false;
        }

        /** 实际行界后首 token 必须是 [；空白和 cr 产生的 relax 阻止这个窗口。 */
        private void rowSize(List<Token> out, Token call) {
            if (future().text.equals("[")) argumentTokens("size", true, call, out);
        }

        private List<Token> expandString(List<Token> body) {
            Deque<Token> saved = input;
            MathLexer.Scanner savedScanner = scanner;
            budget.enqueue(body.size(), origin(body.isEmpty() ? eof : body.get(0)));
            input = new ArrayDeque<Token>(body);
            scanner = null;
            List<Token> out = new ArrayList<Token>();
            try {
                while (!future().text.equals("EOF")) {
                    Token token = nextToken();
                    if (!token.text.equals("EOF")) append(out, token);
                }
            } finally {
                budget.release(input.size());
                input = saved;
                scanner = savedScanner;
            }
            return out;
        }

        /** raw/color/url/size 展开宏而不执行 parser assignments；原有参数边界保留为 token。 */
        private boolean argumentTokens(String type, boolean optional, Token call, List<Token> out) {
            boolean oldURL = urlMode, oldText = textMode;
            if ("url".equals(type)) urlMode = true;
            try {
                consumeSpaces();
                if (optional && !future().text.equals("[")) return false;
                if ("primitive".equals(type)) {
                    Token token = nextToken();
                    if (token.text.equals("EOF")) throw failure(call, "缺少命令参数");
                    if (token.text.equals("{")) {
                        beginGroup();
                        append(out, token);
                        expandSequence(out, "}");
                    } else {
                        append(out, token);
                        commandArguments(this, token, out);
                    }
                    return true;
                }
                if ("size".equals(type) && !optional && !future().text.equals("{")) {
                    String value = "";
                    while (true) {
                        Token token = nextToken();
                        budget.work((long) value.length() + token.text.length(), call);
                        String candidate = value + token.text;
                        if (token.text.equals("EOF")
                                || !candidate.matches(
                                        "[-+]? *(?:[0-9]*|[0-9]+\\.[0-9]*|\\.[0-9]*)"
                                                + " *[a-z]{0,2} *")) {
                            pushToken(token);
                            break;
                        }
                        append(out, token);
                        value = candidate;
                    }
                    if (value.isEmpty()) throw failure(call, "Invalid size");
                    return true;
                }
                Token first = future();
                List<Token> body;
                if (optional) {
                    popToken();
                    body = consumeArg(Collections.singletonList("]"));
                } else body = consumeArg();
                Token end = argumentEnd;
                boolean raw =
                        "raw".equals(type)
                                || "color".equals(type)
                                || "url".equals(type)
                                || "size".equals(type);
                if ("text".equals(type) || "hbox".equals(type)) textMode = true;
                else if ("math".equals(type)) textMode = false;
                List<Token> expanded =
                        "embed".equals(type)
                                ? body
                                : raw ? expandString(body) : expandList(body, false, true);
                // 无花括号的非 primitive 参数在 KaTeX 也是一个独立参数组，桥接显式其边界。
                budget.generate(2, call);
                Token opening = first.text(optional ? "[" : "{");
                append(out, opening);
                appendAll(out, expanded, call);
                append(out, end.text(optional ? "]" : "}"));
                return true;
            } finally {
                urlMode = oldURL;
                textMode = oldText;
            }
        }

        private final class Middle {
            final List<Token> single, doubled;
            final boolean one;
            boolean used;

            Middle(List<Token> single, List<Token> doubled, boolean one) {
                this.single = single;
                this.doubled = doubled;
                this.one = one;
            }

            boolean applies(String name) {
                return !(one && used)
                        && (name.equals("|") || !doubled.isEmpty() && name.equals("\\|"));
            }

            List<Token> expand(Token token) {
                used = true;
                boolean two = token.text.equals("\\|");
                if (!two && !doubled.isEmpty() && future().text.equals("|")) {
                    popToken();
                    two = true;
                }
                return two ? doubled : single;
            }
        }

        private List<Token> braket(boolean one, Token call) {
            List<Token> left = consumeArg(),
                    single = consumeArg(),
                    doubled = consumeArg(),
                    right = consumeArg(),
                    arg = consumeArg();
            long size = (long) left.size() + arg.size() + right.size();
            budget.generate(size, call);
            budget.work(copyCost(left, call) + copyCost(arg, call) + copyCost(right, call), call);
            List<Token> body = new ArrayList<Token>((int) size);
            body.addAll(left);
            body.addAll(arg);
            body.addAll(right);
            middles.push(new Middle(single, doubled, one));
            beginGroup();
            try {
                return expandList(body, true, false);
            } finally {
                endGroup();
                middles.pop();
            }
        }
    }

    private static MathLexer.Failure failure(Token token, String message) {
        return new MathLexer.Failure(token, message);
    }

    private static String join(List<Token> tokens) {
        StringBuilder value = new StringBuilder();
        for (Token token : tokens) value.append(token.text);
        return value.toString();
    }

    private static boolean spaceAfterDots(String name) {
        return contains(
                ") ] \\r"
                        + "brack \\} \\r"
                        + "brace \\r"
                        + "angle \\r"
                        + "ceil \\r"
                        + "floor \\r"
                        + "group \\r"
                        + "moustache \\r"
                        + "ight \\bigr \\biggr \\Bigr \\Biggr $ ; . ,",
                name);
    }

    private static boolean contains(String names, String name) {
        for (String item : names.split(" ")) if (item.equals(name)) return true;
        return false;
    }

    private static String dots(String next) {
        if (next.equals(",")) return "\\dotsc";
        if (next.equals("\\DOTSI")
                || contains("\\int \\oint \\iint \\iiint \\iiiint \\idotsint", next))
            return "\\dotsi";
        if (next.equals("\\DOTSX")) return "\\dotsx";
        if (next.startsWith("\\not") || catalogDotsBinary(next)) return "\\dotsb";
        return "\\dotso";
    }

    // Catalog parser 元数据接缝；string 宏全部直接消费 Catalog expansion。
    private static boolean catalogDefined(String name) {
        return MathCommandCatalog.findFunction(name) != null
                || MathSymbols.find("math", name) != null
                || MathSymbols.find("text", name) != null
                || MathCommandCatalog.implicitCommands().contains(name);
    }

    private static boolean catalogExpandable(String name) {
        MathCommandCatalog.Function f = MathCommandCatalog.findFunction(name);
        return f != null && !f.primitive;
    }

    private static boolean catalogDotsBinary(String name) {
        // dotsByToken 是函数宏的上下文分派规则；其它符号类别仅查询 Catalog。
        if (contains(
                "+ = < > - * : \\DOTSB \\coprod \\bigvee \\bigwedge \\biguplus \\bigcap \\bigcup"
                        + " \\prod \\sum \\bigotimes \\bigoplus \\bigodot \\bigsqcup \\And"
                        + " \\longrightarrow \\Longrightarrow \\longleftarrow \\Longleftarrow"
                        + " \\longleftrightarrow \\Longleftrightarrow \\mapsto \\longmapsto"
                        + " \\hookrightarrow \\doteq \\mathbin \\mathrel \\r"
                        + "elbar \\Relbar \\xrightarrow \\xleftarrow",
                name)) return true;
        MathSymbols.Entry entry = MathSymbols.find("math", name);
        return entry != null && (entry.group.equals("bin") || entry.group.equals("rel"));
    }

    private static void commandArguments(Cursor c, Token token, List<Token> out) {
        if (token.text.equals("\\embed")) {
            c.argumentTokens("embed", false, token, out);
            return;
        }
        if (token.text.equals("\\\\")) {
            c.rowSize(out, token);
            return;
        }
        if (token.text.startsWith("\\verb") && token.text.length() > 5) return;
        MathCommandCatalog.Function f = MathCommandCatalog.findFunction(token.text);
        if (f == null) return;
        int colorArgumentAt = out.size();
        boolean optionalPresent = false;
        for (int i = 0; i < f.numOptionalArgs + f.numArgs; i++) {
            boolean optional = i < f.numOptionalArgs;
            String type = i < f.argTypes.size() ? f.argTypes.get(i) : null;
            if (type == null && f.primitive || f.type.equals("sqrt") && i == 1 && !optionalPresent)
                type = "primitive";
            boolean present = c.argumentTokens(type, optional, token, out);
            if (optional) optionalPresent = present;
        }
        // 固定 parser 只有 color（非 textcolor）产生 current@color 宏；验证/节点仍归 Task4。
        if (token.text.equals("\\color") && out.size() > colorArgumentAt + 1) {
            c.budget.work(out.size() - colorArgumentAt - 2, token);
            List<Token> body =
                    new ArrayList<Token>(out.subList(colorArgumentAt + 1, out.size() - 1));
            String color = join(body);
            if (color.matches("[a-fA-F0-9]{6}")) body.add(0, token.text("#"));
            c.set("\\current@color", new Definition(body, 0, null, null, false), false);
        }
    }
}
