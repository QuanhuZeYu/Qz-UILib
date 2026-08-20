package club.heiqi.uilib.font.latex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.latex.node.LatexAccent;
import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexAtom.AtomClass;
import club.heiqi.uilib.font.latex.node.LatexBinom;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexLeftRight;
import club.heiqi.uilib.font.latex.node.LatexMatrix;
import club.heiqi.uilib.font.latex.node.LatexSpace;
import club.heiqi.uilib.font.latex.node.LatexSqrt;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/**
 * LaTeX 数学子集递归下降解析器（M1：纯解析，无布局/渲染）。
 *
 * <h3>语法范围</h3>
 * <p>规划 §7 的 L1+L2 命令：上下标、分组、分数、根号、\left/\right 伸缩括号、
 * 大运算符（\sum \int \prod \lim 及函数名）、希腊字母与运算符符号（{@link LatexSymbols}）、
 * 矩阵环境（matrix/pmatrix/bmatrix/vmatrix/cases）、\binom、重音、\text、间距命令。</p>
 *
 * <h3>容错（宽容失败，与 RichTextTagParser 语义一致）</h3>
 * <ul>
 *   <li>未闭合 `{` 视为到表达式结束；多余 `}` 忽略；</li>
 *   <li>未知命令原样保留为字面文本（反斜杠 + 命令名）；</li>
 *   <li>缺失参数（如 \frac 无参数）以空组填充；孤立的 `^`/`_` 按字面字符输出；</li>
 *   <li>\begin 无 \end 时内容解析到末尾；\right 无 \left（或反之）宽容忽略；</li>
 *   <li>数学模式空白（空格/Tab/换行）一律忽略；\text 内空白保留。</li>
 * </ul>
 */
public final class LatexParser {

    // ==================== 终止条件位掩码 ====================

    /** 遇 `}` 停止（花括号组、命令参数）。 */
    private static final int STOP_BRACE = 1;
    /** 遇 `]` 停止（\sqrt 可选根指数）。 */
    private static final int STOP_BRACKET = 2;
    /** 遇 `&` 停止（矩阵列分隔）。 */
    private static final int STOP_AMP = 4;
    /** 遇 `\\\\`（两个反斜杠）停止（矩阵行分隔）。 */
    private static final int STOP_ROW_BREAK = 8;
    /** 遇 \right 停止（\left/\right 内容）。 */
    private static final int STOP_RIGHT = 16;
    /** 遇 \end 停止（矩阵环境内容）。 */
    private static final int STOP_END_ENV = 32;

    /**
     * 解析 LaTeX 数学子集源码为 AST。
     *
     * @param source LaTeX 源码（可为 null）
     * @return 顶层节点列表；null/空输入返回空列表
     */
    public static List<LatexNode> parse(String source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return new LatexParser(source).parseTopLevel();
    }

    private final String source;
    private final int length;
    private int index;

    private LatexParser(String source) {
        this.source = source;
        this.length = source.length();
    }

    private List<LatexNode> parseTopLevel() {
        return parseList(0);
    }

    // ==================== 列表/因子/原子 ====================

    /**
     * 解析节点列表，直到遇到 stops 指定的终止符（不消费终止符）或 EOF。
     */
    private List<LatexNode> parseList(int stops) {
        List<LatexNode> nodes = new ArrayList<LatexNode>();
        while (index < length) {
            skipMathSpaces();
            if (index >= length) {
                break;
            }
            char ch = source.charAt(index);
            if (ch == '}') {
                if ((stops & STOP_BRACE) != 0) {
                    break;
                }
                index++; // 多余闭括号宽容忽略
                continue;
            }
            if (ch == ']') {
                if ((stops & STOP_BRACKET) != 0) {
                    break;
                }
                nodes.add(new LatexAtom("]", AtomClass.CLOSE));
                index++;
                continue;
            }
            if (ch == '&') {
                if ((stops & STOP_AMP) != 0) {
                    break;
                }
                nodes.add(new LatexAtom("&", AtomClass.ORD));
                index++;
                continue;
            }
            if (ch == '\\') {
                if ((stops & STOP_ROW_BREAK) != 0 && peekRowBreak()) {
                    break;
                }
                if ((stops & STOP_END_ENV) != 0 && peekCommand("end")) {
                    break;
                }
                if ((stops & STOP_RIGHT) != 0 && peekCommand("right")) {
                    break;
                }
                LatexNode node = parseFactor(); // 命令同样参与上下标绑定（如 \\sum_{i=1}^{n}）
                if (node != null) {
                    nodes.add(node);
                }
                continue;
            }
            if (ch == '^' || ch == '_') {
                // 孤立上下标：宽容按字面字符输出
                nodes.add(new LatexAtom(String.valueOf(ch), AtomClass.ORD));
                index++;
                continue;
            }
            LatexNode node = parseFactor();
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /** 因子 = 原子 + 可选的上下标（各至多一个）。 */
    private LatexNode parseFactor() {
        LatexNode base = parseAtom();
        if (base == null) {
            return null;
        }
        LatexNode sup = null;
        LatexNode sub = null;
        while (index < length) {
            char ch = source.charAt(index);
            if (ch == '^' && sup == null) {
                index++;
                sup = parseSupSubArgument();
                continue;
            }
            if (ch == '_' && sub == null) {
                index++;
                sub = parseSupSubArgument();
                continue;
            }
            break;
        }
        if (sup == null && sub == null) {
            return base;
        }
        return new LatexSupSub(base, sup, sub);
    }

    /** 原子：花括号组、命令或普通字符。 */
    private LatexNode parseAtom() {
        char ch = source.charAt(index);
        if (ch == '{') {
            index++;
            List<LatexNode> children = parseList(STOP_BRACE);
            if (index < length && source.charAt(index) == '}') {
                index++;
            }
            return new LatexGroup(children);
        }
        if (ch == '\\') {
            return parseCommand();
        }
        index += Character.charCount(source.codePointAt(index));
        return new LatexAtom(String.valueOf(ch), classifyChar(ch));
    }

    /**
     * 上下标参数：花括号组、单个命令（含其上下标）或单字符。
     * 遇终止符/EOF 返回 null（宽容：上下标缺省）。
     */
    private LatexNode parseSupSubArgument() {
        skipMathSpaces();
        if (index >= length) {
            return null;
        }
        char ch = source.charAt(index);
        if (ch == '}' || ch == '&' || ch == '^' || ch == '_') {
            return null;
        }
        if (ch == '\\') {
            if (peekRowBreak() || peekCommand("right") || peekCommand("end")) {
                return null;
            }
            return parseCommand();
        }
        return parseAtom(); // 单原子/组，不吞后续 ^/_（x_i^2 的 i 不绑 2）
    }

    // ==================== 命令分派 ====================

    /**
     * 解析一个命令（index 指向命令名首字符，不含反斜杠）。
     *
     * @return 命令节点；宽容忽略的命令（\right/\end 无配对）返回 null
     */
    private LatexNode parseCommand() {
        index++; // 消费反斜杠
        if (index >= length) {
            return new LatexAtom("\\", AtomClass.ORD);
        }
        char ch = source.charAt(index);
        if (!isCommandLetter(ch)) {
            index++;
            return parseEscapedChar(ch);
        }
        String name = readCommandName();
        // ---- 结构命令 ----
        if ("frac".equals(name)) {
            return parseFrac();
        }
        if ("sqrt".equals(name)) {
            return parseSqrt();
        }
        if ("left".equals(name)) {
            return parseLeftRight();
        }
        if ("right".equals(name)) {
            parseDelimiter(); // 无 \left 的 \right 宽容忽略（含其定界符）
            return null;
        }
        if ("begin".equals(name)) {
            return parseMatrix();
        }
        if ("end".equals(name)) {
            skipEnvironmentName(); // 无 \begin 的 \end 宽容忽略
            return null;
        }
        if ("binom".equals(name)) {
            return parseBinom();
        }
        if ("text".equals(name)) {
            return parseText();
        }
        if ("overline".equals(name) || "underline".equals(name)) {
            return parseStretchableAccent("underline".equals(name));
        }
        // ---- 重音 ----
        String accent = LatexSymbols.accentText(name);
        if (accent != null) {
            LatexNode base = parseSupSubArgument();
            return base == null ? new LatexAtom("\\" + name, AtomClass.ORD)
                    : new LatexAccent(accent, base, false, false);
        }
        // ---- 间距 ----
        Integer em18 = LatexSymbols.spaceEm18(name);
        if (em18 != null) {
            return new LatexSpace(em18.intValue() / 18.0D);
        }
        // ---- 大运算符符号（\sum \int \prod …：text 口径侧挂 + 轴居中） ----
        if (LatexSymbols.isBigOperator(name)) {
            String symbol = LatexSymbols.symbolText(name);
            return new LatexAtom(symbol != null ? symbol : name, AtomClass.OP,
                    LatexAtom.OperatorMode.BIG_OPERATOR);
        }
        // ---- limits 算子（\lim \max \min …：上下限恒上下堆叠，正体） ----
        if (LatexSymbols.isLimitsFunctionName(name)) {
            return new LatexAtom(name, AtomClass.OP, LatexAtom.OperatorMode.LIMITS_OPERATOR);
        }
        // ---- 符号命令 ----
        if (LatexSymbols.isSymbolCommand(name)) {
            return new LatexAtom(LatexSymbols.symbolText(name), LatexSymbols.atomClassOf(name));
        }
        // ---- 函数名（正体文本算子，无 limits） ----
        if (LatexSymbols.isFunctionName(name)) {
            return new LatexAtom(name, AtomClass.OP);
        }
        // ---- 未知命令：宽容字面保留 ----
        return new LatexAtom("\\" + name, AtomClass.ORD);
    }

    /** 非字母单字符转义（\{ \} \% \# \$ \& \_ \+ \ 等）。 */
    private LatexNode parseEscapedChar(char ch) {
        Integer em18 = LatexSymbols.spaceEm18(String.valueOf(ch));
        if (em18 != null) {
            return new LatexSpace(em18.intValue() / 18.0D); // 标点间距命令
        }
        switch (ch) {
            case '{':
                return new LatexAtom("{", AtomClass.OPEN);
            case '}':
                return new LatexAtom("}", AtomClass.CLOSE);
            case '%':
            case '#':
            case '$':
            case '&':
            case '_':
                return new LatexAtom(String.valueOf(ch), AtomClass.ORD);
            case '+':
                return new LatexAtom("+", AtomClass.BIN);
            case ' ':
            case '\t':
            case '\n':
                return new LatexSpace(3.0D / 18.0D);
            default:
                return new LatexAtom("\\" + ch, AtomClass.ORD); // 未知转义字面保留
        }
    }

    /** 命令参数：花括号组或单 token；缺失容错为空组。 */
    private LatexNode parseArgument() {
        skipMathSpaces();
        if (index >= length) {
            return emptyGroup();
        }
        char ch = source.charAt(index);
        if (ch == '{') {
            index++;
            List<LatexNode> children = parseList(STOP_BRACE);
            if (index < length && source.charAt(index) == '}') {
                index++;
            }
            return new LatexGroup(children);
        }
        if (ch == '}') {
            return emptyGroup();
        }
        if (ch == '\\') {
            if (peekRowBreak() || peekCommand("right") || peekCommand("end")) {
                return emptyGroup();
            }
            LatexNode node = parseCommand();
            return node == null ? emptyGroup() : node;
        }
        if (ch == '^' || ch == '_' || ch == '&') {
            return emptyGroup();
        }
        return parseFactor();
    }

    private static LatexGroup emptyGroup() {
        return new LatexGroup(Collections.<LatexNode>emptyList());
    }

    private LatexNode parseFrac() {
        LatexNode numerator = parseArgument();
        LatexNode denominator = parseArgument();
        return new LatexFrac(numerator, denominator);
    }

    private LatexNode parseSqrt() {
        LatexNode indexNode = null;
        skipMathSpaces();
        if (index < length && source.charAt(index) == '[') {
            index++;
            List<LatexNode> children = parseList(STOP_BRACKET);
            if (index < length && source.charAt(index) == ']') {
                index++;
            }
            indexNode = new LatexGroup(children);
        }
        LatexNode radicand = parseArgument();
        return new LatexSqrt(indexNode, radicand);
    }

    private LatexNode parseLeftRight() {
        String left = parseDelimiter();
        List<LatexNode> content = parseList(STOP_RIGHT);
        String right = null;
        if (index < length && source.charAt(index) == '\\' && peekCommand("right")) {
            index++; // 消费反斜杠
            readCommandName(); // 消费 "right"
            right = parseDelimiter();
        }
        return new LatexLeftRight(left, new LatexGroup(content), right);
    }

    /** 定界符：单字符（"." 为无形 null）或定界符命令。 */
    private String parseDelimiter() {
        skipMathSpaces();
        if (index >= length) {
            return null;
        }
        char ch = source.charAt(index);
        if (ch == '.') {
            index++;
            return null;
        }
        if (ch == '\\') {
            index++;
            if (index >= length) {
                return null;
            }
            char next = source.charAt(index);
            if (!isCommandLetter(next)) {
                index++;
                return String.valueOf(next);
            }
            String name = readCommandName();
            String text = LatexSymbols.symbolText(name);
            return text != null ? text : "\\" + name;
        }
        index++;
        return String.valueOf(ch);
    }

    // ==================== 矩阵环境 ====================

    private LatexNode parseMatrix() {
        String environment = readEnvironmentName();
        LatexMatrix.Fence fence = LatexSymbols.matrixFence(environment);
        if (fence == null) {
            fence = LatexMatrix.Fence.NONE; // 未知环境宽容按无括号矩阵
        }
        List<List<List<LatexNode>>> rows = new ArrayList<List<List<LatexNode>>>();
        while (index < length) {
            skipMathSpaces();
            if (peekCommand("end")) {
                consumeEndEnvironment();
                break;
            }
            List<List<LatexNode>> row = new ArrayList<List<LatexNode>>();
            while (index < length) {
                List<LatexNode> cell = parseList(STOP_AMP | STOP_ROW_BREAK | STOP_END_ENV);
                row.add(cell);
                if (index < length && source.charAt(index) == '&') {
                    index++;
                    skipMathSpaces();
                    continue;
                }
                break;
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
            if (peekRowBreak()) {
                index += 2; // 消费 "\\\\"
                continue;
            }
            break;
        }
        if (rows.isEmpty()) {
            rows.add(new ArrayList<List<LatexNode>>()); // 空环境容错
        }
        return new LatexMatrix(fence, rows);
    }

    private String readEnvironmentName() {
        skipMathSpaces();
        if (index < length && source.charAt(index) == '{') {
            int start = ++index;
            while (index < length && source.charAt(index) != '}') {
                index++;
            }
            String name = source.substring(start, index);
            if (index < length) {
                index++;
            }
            return name;
        }
        if (index < length && source.charAt(index) == '\\') {
            index++;
        }
        return readCommandName();
    }

    private void consumeEndEnvironment() {
        index++; // 消费反斜杠
        readCommandName(); // "end"
        skipEnvironmentName();
    }

    private void skipEnvironmentName() {
        skipMathSpaces();
        if (index < length && source.charAt(index) == '{') {
            index++;
            while (index < length && source.charAt(index) != '}') {
                index++;
            }
            if (index < length) {
                index++;
            }
        } else if (index < length && source.charAt(index) == '\\') {
            index++;
            readCommandName();
        }
    }

    // ==================== 其余结构命令 ====================

    private LatexNode parseBinom() {
        LatexNode upper = parseArgument();
        LatexNode lower = parseArgument();
        return new LatexBinom(upper, lower);
    }

    private LatexNode parseStretchableAccent(boolean below) {
        LatexNode base = parseSupSubArgument();
        return base == null ? new LatexAtom("\\" + (below ? "underline" : "overline"), AtomClass.ORD)
                : new LatexAccent(null, base, true, below);
    }

    private LatexNode parseText() {
        skipMathSpaces();
        if (index < length && source.charAt(index) == '{') {
            index++;
        }
        StringBuilder builder = new StringBuilder();
        while (index < length && source.charAt(index) != '}') {
            char ch = source.charAt(index);
            if (ch == '\\') {
                index++;
                if (index >= length) {
                    break;
                }
                char next = source.charAt(index);
                if (next == ' ') {
                    builder.append(' ');
                    index++;
                    continue;
                }
                if (!isCommandLetter(next)) {
                    String escape = LatexSymbols.textEscape(String.valueOf(next));
                    builder.append(escape != null ? escape : "\\" + next);
                    index++;
                    continue;
                }
                String name = readCommandName();
                String escape = LatexSymbols.textEscape(name);
                builder.append(escape != null ? escape : "\\" + name);
                continue;
            }
            builder.append(ch);
            index++;
        }
        if (index < length && source.charAt(index) == '}') {
            index++;
        }
        return new LatexAtom(builder.toString(), AtomClass.TEXT);
    }

    // ==================== 扫描工具 ====================

    /** 数学模式空白（空格/Tab/换行族）。 */
    private void skipMathSpaces() {
        while (index < length) {
            char ch = source.charAt(index);
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '\f'
                    || ch == '\u000B') {
                index++;
            } else {
                break;
            }
        }
    }

    /** index 是否指向 "\\\\"（矩阵换行）。 */
    private boolean peekRowBreak() {
        return index + 1 < length && source.charAt(index) == '\\' && source.charAt(index + 1) == '\\';
    }

    /** index 是否指向反斜杠 + 指定命令名（后随非字母边界）。 */
    private boolean peekCommand(String name) {
        if (source.charAt(index) != '\\') {
            return false;
        }
        int cursor = index + 1;
        int nameLength = name.length();
        if (cursor + nameLength > length) {
            return false;
        }
        if (!source.regionMatches(cursor, name, 0, nameLength)) {
            return false;
        }
        int after = cursor + nameLength;
        return after >= length || !isCommandLetter(source.charAt(after));
    }

    /** 读取 [A-Za-z]+ 命令名（index 指向首字母）。 */
    private String readCommandName() {
        int start = index;
        while (index < length && isCommandLetter(source.charAt(index))) {
            index++;
        }
        return source.substring(start, index);
    }

    private static boolean isCommandLetter(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    /** 普通字符的原子分类（TeX 8 类简化）。 */
    private static AtomClass classifyChar(char ch) {
        switch (ch) {
            case '(':
            case '[':
                return AtomClass.OPEN;
            case ')':
            case ']':
            case '!':
                return AtomClass.CLOSE;
            case ',':
            case ';':
            case ':':
                return AtomClass.PUNCT;
            case '=':
            case '<':
            case '>':
                return AtomClass.REL;
            case '+':
            case '-':
            case '*':
                return AtomClass.BIN;
            default:
                return AtomClass.ORD;
        }
    }
}
