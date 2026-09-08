package club.heiqi.uilib.font.latex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
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
 * <p>支持上下标、分组、分数、数学样式声明、根号、\left/\right 伸缩括号、
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
    // 仅本次 AST 构造期间保留 array 的完整原始列说明（含超出现有行宽的列）。
    // 最终节点自行防御拷贝，不依赖此记录；不缓存解析/布局结果，非 array 不登记。
    private IdentityHashMap<LatexMatrix, List<Character>> matrixColumnAligns;

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
        MathStyleOverride style = MathStyleOverride.INHERIT;
        while (index < length) {
            skipMathSpaces();
            if (index >= length) {
                break;
            }
            MathStyleOverride declaration = consumeStyleDeclaration();
            if (declaration != null) {
                style = declaration;
                continue;
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
                nodes.add(withStyle(new LatexAtom("]", AtomClass.CLOSE), style));
                index++;
                continue;
            }
            if (ch == '&') {
                if ((stops & STOP_AMP) != 0) {
                    break;
                }
                nodes.add(withStyle(new LatexAtom("&", AtomClass.ORD), style));
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
                    nodes.add(withStyle(node, style));
                }
                continue;
            }
            if (ch == '^' || ch == '_') {
                // 孤立上下标：宽容按字面字符输出
                nodes.add(withStyle(new LatexAtom(String.valueOf(ch), AtomClass.ORD), style));
                index++;
                continue;
            }
            LatexNode node = parseFactor();
            if (node != null) {
                nodes.add(withStyle(node, style));
            }
        }
        return nodes;
    }

    /** 消费局部声明，不生成可见节点；调用者保存本数学列表的状态。 */
    private MathStyleOverride consumeStyleDeclaration() {
        MathStyleOverride style;
        if (peekCommand("displaystyle")) {
            style = MathStyleOverride.DISPLAY;
        } else if (peekCommand("textstyle")) {
            style = MathStyleOverride.TEXT;
        } else if (peekCommand("scriptstyle")) {
            style = MathStyleOverride.SCRIPT;
        } else if (peekCommand("scriptscriptstyle")) {
            style = MathStyleOverride.SCRIPTSCRIPT;
        } else {
            return null;
        }
        index++;
        readCommandName();
        return style;
    }

    /** 裸参数宽容接受前导声明，但仍只绑定一个原子，不吞后续脚本或终止符。 */
    private MathStyleOverride consumeArgumentStyle() {
        MathStyleOverride style = MathStyleOverride.INHERIT;
        while (true) {
            skipMathSpaces();
            MathStyleOverride declaration = consumeStyleDeclaration();
            if (declaration == null) {
                return style;
            }
            style = declaration;
        }
    }

    /** 只重建当前完整因子；子树保持局部声明，避免重复覆盖结构转换后的样式。 */
    private LatexNode withStyle(LatexNode node, MathStyleOverride style) {
        if (node == null || style == MathStyleOverride.INHERIT || node.getMathStyleOverride() == style) {
            return node;
        }
        switch (node.getKind()) {
            case ATOM: {
                LatexAtom atom = (LatexAtom) node;
                LatexAtom copy = new LatexAtom(atom.getText(), atom.getAtomClass(), atom.getOperatorMode(), style);
                copy.setLimitsFlag(atom.getLimitsFlag());
                return copy;
            }
            case SUP_SUB: {
                LatexSupSub scripts = (LatexSupSub) node;
                return new LatexSupSub(scripts.getBase(), scripts.getSup(), scripts.getSub(), style);
            }
            case FRAC: {
                LatexFrac fraction = (LatexFrac) node;
                return new LatexFrac(fraction.getNumerator(), fraction.getDenominator(), style,
                        fraction.getFractionStyle());
            }
            case SQRT: {
                LatexSqrt root = (LatexSqrt) node;
                return new LatexSqrt(root.getIndex(), root.getRadicand(), style);
            }
            case GROUP:
                return new LatexGroup(((LatexGroup) node).getChildren(), style);
            case BINOM: {
                LatexBinom binom = (LatexBinom) node;
                return new LatexBinom(binom.getUpper(), binom.getLower(), style);
            }
            case SPACE:
                return new LatexSpace(((LatexSpace) node).getEmWidth(), style);
            case ACCENT: {
                LatexAccent accent = (LatexAccent) node;
                return new LatexAccent(accent.getAccentText(), accent.getBase(), accent.isStretchable(),
                        accent.isBelow(), style);
            }
            case LEFT_RIGHT: {
                LatexLeftRight fence = (LatexLeftRight) node;
                return new LatexLeftRight(fence.getLeftDelimiter(), fence.getParts(), fence.getMiddleDelimiters(),
                        fence.getRightDelimiter(), style);
            }
            case MATRIX: {
                LatexMatrix matrix = (LatexMatrix) node;
                List<Character> aligns = matrixColumnAligns == null ? null : matrixColumnAligns.get(matrix);
                LatexMatrix copy = new LatexMatrix(matrix.getFence(), matrix.getRows(), aligns, style);
                rememberColumnAligns(copy, aligns);
                return copy;
            }
            default:
                throw new IllegalArgumentException("Unsupported parser node: " + node.getKind());
        }
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
            skipMathSpaces();
            if (index >= length) {
                break;
            }
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
            if (ch == '\\' && (peekCommand("limits") || peekCommand("nolimits"))) {
                index++;
                String modifier = readCommandName();
                // 修饰只绑定本因子的算子，不能污染参数、分组或此前的算子。
                if (base instanceof LatexAtom && ((LatexAtom) base).getAtomClass() == AtomClass.OP) {
                    ((LatexAtom) base).setLimitsFlag("limits".equals(modifier)
                            ? LatexAtom.LIMITS_LIMITS : LatexAtom.LIMITS_NOLIMITS);
                }
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
        int start = index;
        index += Character.charCount(source.codePointAt(index));
        return new LatexAtom(source.substring(start, index), classifyChar(ch));
    }

    /**
     * 上下标参数：花括号组、单个命令（含其参数）或单字符。
     * 遇终止符/EOF 返回 null（宽容：上下标缺省）。
     */
    private LatexNode parseSupSubArgument() {
        MathStyleOverride style = consumeArgumentStyle();
        return withStyle(parseSupSubArgumentAtom(), style);
    }

    private LatexNode parseSupSubArgumentAtom() {
        skipMathSpaces();
        if (index >= length) {
            return null;
        }
        char ch = source.charAt(index);
        if (ch == '}' || ch == '&' || ch == '^' || ch == '_') {
            return null;
        }
        if (ch == '\\') {
            if (peekRowBreak() || peekCommand("right") || peekCommand("middle") || peekCommand("end")) {
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
        if ("frac".equals(name) || "dfrac".equals(name) || "tfrac".equals(name)) {
            LatexFrac.FractionStyle fractionStyle = "dfrac".equals(name) ? LatexFrac.FractionStyle.DISPLAY
                    : "tfrac".equals(name) ? LatexFrac.FractionStyle.TEXT : LatexFrac.FractionStyle.INHERIT;
            return parseFrac(fractionStyle);
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
        if ("limits".equals(name) || "nolimits".equals(name)) {
            // 无当前因子的孤立修饰宽容忽略。
            return null;
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
        // ---- 大运算符符号（\sum \int \prod …：行内 limits 堆叠 + 轴居中，可 \nolimits 降级） ----
        if (LatexSymbols.isBigOperator(name)) {
            String symbol = LatexSymbols.symbolText(name);
            LatexAtom atom = new LatexAtom(symbol != null ? symbol : name, AtomClass.OP,
                    LatexAtom.OperatorMode.BIG_OPERATOR);
            return atom;
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
        MathStyleOverride style = consumeArgumentStyle();
        return withStyle(parseArgumentAtom(), style);
    }

    private LatexNode parseArgumentAtom() {
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
            if (peekRowBreak() || peekCommand("right") || peekCommand("middle") || peekCommand("end")) {
                return emptyGroup();
            }
            LatexNode node = parseCommand();
            return node == null ? emptyGroup() : node;
        }
        if (ch == '^' || ch == '_' || ch == '&') {
            return emptyGroup();
        }
        return parseAtom(); // 裸参数只取一个原子，后续脚本属于外层因子
    }

    private static LatexGroup emptyGroup() {
        return new LatexGroup(Collections.<LatexNode>emptyList());
    }

    private LatexNode parseFrac(LatexFrac.FractionStyle fractionStyle) {
        LatexNode numerator = parseArgument();
        LatexNode denominator = parseArgument();
        return new LatexFrac(numerator, denominator, MathStyleOverride.INHERIT, fractionStyle);
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
        // 内容支持 \middle 分段（TeX \left...\middle...\right）：每段一个节点列表，
        // 段间记录中间定界符（"." 为 null 无形）。LatexGroup 为防御拷贝，先积累裸列表、
        // 收尾统一包装。
        List<List<LatexNode>> rawParts = new ArrayList<List<LatexNode>>();
        List<String> middles = new ArrayList<String>();
        // 分段入口样式同时保留 middle 位置的声明，即使此前声明后没有因子。
        List<MathStyleOverride> partStyles = new ArrayList<MathStyleOverride>();
        partStyles.add(MathStyleOverride.INHERIT);
        List<LatexNode> current = new ArrayList<LatexNode>();
        rawParts.add(current);
        MathStyleOverride style = MathStyleOverride.INHERIT;
        while (index < length) {
            skipMathSpaces();
            if (index >= length) {
                break;
            }
            char ch = source.charAt(index);
            if (ch == '\\') {
                if (peekCommand("right")) {
                    break;
                }
                if (peekCommand("middle")) {
                    index++; // 消费反斜杠
                    readCommandName(); // 消费 "middle"
                    middles.add(parseDelimiter());
                    current = new ArrayList<LatexNode>();
                    rawParts.add(current);
                    partStyles.add(style);
                    continue;
                }
                if (peekRowBreak() || peekCommand("end")) {
                    break; // 未闭合 \left 的宽容终止
                }
            }
            if (ch == '}') {
                index++; // 多余闭括号宽容忽略（与 parseList 同语义）
                continue;
            }
            MathStyleOverride declaration = consumeStyleDeclaration();
            if (declaration != null) {
                style = declaration;
                continue;
            }
            LatexNode node = parseFactor();
            if (node != null) {
                current.add(withStyle(node, style));
            }
        }
        List<LatexNode> parts = new ArrayList<LatexNode>(rawParts.size());
        for (int part = 0; part < rawParts.size(); part++) {
            parts.add(new LatexGroup(rawParts.get(part), partStyles.get(part)));
        }
        String right = null;
        if (index < length && source.charAt(index) == '\\' && peekCommand("right")) {
            index++; // 消费反斜杠
            readCommandName(); // 消费 "right"
            right = parseDelimiter();
        }
        return new LatexLeftRight(left, parts, middles, right);
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

    private void rememberColumnAligns(LatexMatrix matrix, List<Character> aligns) {
        if (aligns != null) {
            if (matrixColumnAligns == null) {
                matrixColumnAligns = new IdentityHashMap<LatexMatrix, List<Character>>();
            }
            matrixColumnAligns.put(matrix, aligns);
        }
    }

    private LatexNode parseMatrix() {
        String environment = readEnvironmentName();
        LatexMatrix.Fence fence = LatexSymbols.matrixFence(environment);
        if (fence == null) {
            fence = LatexMatrix.Fence.NONE; // 未知环境宽容按无括号矩阵
        }
        // array 环境支持列说明（\begin{array}{ll}）；缺失/其他环境按 fence 默认对齐
        List<Character> columnAligns = "array".equals(environment) ? parseColumnAligns() : null;
        List<List<List<LatexNode>>> rows = new ArrayList<List<List<LatexNode>>>();
        while (index < length) {
            skipMathSpaces();
            if (peekCommand("end")) {
                consumeEndEnvironment(environment);
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
            if (peekCommand("end")) {
                consumeEndEnvironment(environment);
            }
            break;
        }
        if (rows.isEmpty()) {
            rows.add(new ArrayList<List<LatexNode>>()); // 空环境容错
        }
        LatexMatrix matrix = new LatexMatrix(fence, rows, columnAligns);
        rememberColumnAligns(matrix, columnAligns);
        return matrix;
    }

    /**
     * array 列说明 {@code {lcr}}：只取 l/c/r 对齐符，其他字符（竖线等）宽容跳过；
     * 无花括号/无有效对齐符返回 null（按 fence 默认）。
     */
    private List<Character> parseColumnAligns() {
        skipMathSpaces();
        if (index >= length || source.charAt(index) != '{') {
            return null;
        }
        int start = ++index;
        int close = source.indexOf('}', start);
        if (close < 0) {
            return null;
        }
        List<Character> aligns = new ArrayList<Character>();
        for (int i = start; i < close; i++) {
            char c = source.charAt(i);
            if (c == 'l' || c == 'c' || c == 'r') {
                aligns.add(Character.valueOf(c));
            }
        }
        index = close + 1;
        return aligns.isEmpty() ? null : aligns;
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

    private void consumeEndEnvironment(String environment) {
        int start = index;
        index++; // 消费反斜杠
        readCommandName(); // "end"
        if (!environment.equals(readEnvironmentName())) {
            index = start; // 未匹配的结束符留给父环境，支持缺少内层 end 的宽容恢复
        }
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
        int depth = 1;
        while (index < length) {
            char ch = source.charAt(index);
            if (ch == '{') {
                depth++;
                index++;
                continue;
            }
            if (ch == '}') {
                index++;
                if (--depth == 0) {
                    break;
                }
                continue;
            }
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
        return builder.length() == 0 ? emptyGroup() : new LatexAtom(builder.toString(), AtomClass.TEXT);
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
        if (index >= length || source.charAt(index) != '\\') {
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
